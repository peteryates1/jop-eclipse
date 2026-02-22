package com.jopdesign.core.sim;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.jopdesign.tools.JopSim;

/**
 * IJopTarget implementation that wraps a {@link JopSim} bytecode interpreter.
 *
 * <p>JopSim is a bytecode-level JOP simulator (no microcode pipeline).
 * This target provides debug control (step, breakpoint, suspend/resume)
 * over the JopSim execution loop.
 */
public class JopSimJopTarget implements IJopTarget {

	private static final int MAX_BREAKPOINTS = 4;

	private final String jopFile;
	private final String linkFile;

	private JopSim jopSim;
	private DebugIOSim io;
	private JopTargetState state = JopTargetState.NOT_STARTED;
	private volatile boolean halted = true;
	private volatile boolean terminated = false;
	private volatile JopSuspendReason pendingReason;
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition haltCondition = lock.newCondition();
	private Thread workerThread;
	private final JopBreakpointInfo[] bpSlots = new JopBreakpointInfo[MAX_BREAKPOINTS];
	private final List<IJopTargetListener> listeners = new CopyOnWriteArrayList<>();

	/**
	 * @param jopFile  path to the .jop binary file
	 * @param linkFile path to the .link.txt symbol file, or null
	 */
	public JopSimJopTarget(String jopFile, String linkFile) {
		this.jopFile = jopFile;
		this.linkFile = linkFile;
	}

	@Override
	public String getName() {
		return "JOP Bytecode Simulator";
	}

	@Override
	public String getTargetTypeId() {
		return "jopsim";
	}

	@Override
	public void connect() throws JopTargetException {
		try {
			JopSim.setExit(false);
			io = new DebugIOSim();
			io.setCpuId(0);
			io.setOutputListener(text -> fireOutputProduced(text));
			jopSim = new JopSim(jopFile, io, 0);
			jopSim.initCache();
			jopSim.start();
			halted = true;
			state = JopTargetState.SUSPENDED;
			startWorkerThread();
			fireStateChanged(JopTargetState.SUSPENDED, JopSuspendReason.RESET, -1);
		} catch (Exception e) {
			throw new JopTargetException("Failed to connect to JopSim: " + e.getMessage(), e);
		}
	}

	@Override
	public void disconnect() throws JopTargetException {
		halted = true;
		terminated = true;
		if (workerThread != null) {
			workerThread.interrupt();
			lock.lock();
			try {
				haltCondition.signalAll();
			} finally {
				lock.unlock();
			}
			try {
				workerThread.join(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			workerThread = null;
		}
		state = JopTargetState.TERMINATED;
		fireStateChanged(JopTargetState.TERMINATED, null, -1);
	}

	@Override
	public void resume() throws JopTargetException {
		checkConnected();
		JopSim.setExit(false); // Reset static flag in case another session set it
		lock.lock();
		try {
			halted = false;
			haltCondition.signalAll();
		} finally {
			lock.unlock();
		}
		state = JopTargetState.RUNNING;
		fireStateChanged(JopTargetState.RUNNING, null, -1);
	}

	@Override
	public void suspend() throws JopTargetException {
		checkConnected();
		halted = true;
		pendingReason = JopSuspendReason.MANUAL;
		// Wait for worker thread to park
		lock.lock();
		try {
			// Worker will park on the condition, so give it a moment
			// We signal in case it's waiting
			haltCondition.signalAll();
		} finally {
			lock.unlock();
		}
		// Brief wait for the worker to notice the halt flag
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		state = JopTargetState.SUSPENDED;
		fireStateChanged(JopTargetState.SUSPENDED, JopSuspendReason.MANUAL, -1);
	}

	@Override
	public void terminate() throws JopTargetException {
		disconnect();
	}

	@Override
	public void reset() throws JopTargetException {
		checkConnected();
		// Stop worker
		halted = true;
		lock.lock();
		try {
			haltCondition.signalAll();
		} finally {
			lock.unlock();
		}
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// Recreate JopSim
		JopSim.setExit(false);
		jopSim = new JopSim(jopFile, io, 0);
		jopSim.initCache();
		jopSim.start();
		state = JopTargetState.SUSPENDED;
		fireStateChanged(JopTargetState.SUSPENDED, JopSuspendReason.RESET, -1);
	}

	@Override
	public void stepMicro() throws JopTargetException {
		checkConnected();
		JopSim.setExit(false); // Reset static flag in case another session set it
		executeOneInstruction();
		if (state != JopTargetState.TERMINATED) {
			state = JopTargetState.SUSPENDED;
			fireStateChanged(JopTargetState.SUSPENDED, JopSuspendReason.STEP_COMPLETE, -1);
		}
	}

	@Override
	public void stepBytecode() throws JopTargetException {
		// JopSim has no microcode; both step types execute one bytecode
		stepMicro();
	}

	@Override
	public JopTargetState getState() {
		return state;
	}

	@Override
	public JopRegisters readRegisters() throws JopTargetException {
		checkConnected();
		int sp = jopSim.getSP();
		int a = sp >= 0 ? jopSim.getStackValue(sp) : 0;
		int b = sp >= 1 ? jopSim.getStackValue(sp - 1) : 0;
		return new JopRegisters(
				a,          // A (TOS)
				b,          // B (NOS)
				0,          // PC (no microcode PC)
				sp,         // SP
				jopSim.getVP(), // VP
				0,          // AR (no address register)
				computeAbsoluteJPC(), // JPC
				0,          // mulResult
				0, 0, 0, 0, // mem registers not exposed
				0, 0, 0     // extended registers not available
		);
	}

	@Override
	public JopStackData readStack() throws JopTargetException {
		checkConnected();
		int sp = jopSim.getSP();
		int count = Math.min(sp + 1, 256);
		int[] values = new int[count];
		for (int i = 0; i < count; i++) {
			values[i] = jopSim.getStackValue(i);
		}
		return new JopStackData(values, sp);
	}

	@Override
	public JopMemoryData readMemory(int address, int length) throws JopTargetException {
		checkConnected();
		int[] values = new int[length];
		for (int i = 0; i < length; i++) {
			values[i] = jopSim.getMemValue(address + i);
		}
		return new JopMemoryData(address, values);
	}

	@Override
	public void writeRegister(JopRegister reg, int value) throws JopTargetException {
		checkConnected();
		switch (reg) {
			case SP -> jopSim.setSP(value);
			case VP -> jopSim.setVP(value);
			case PC -> jopSim.setPC(value);
			default -> throw new JopTargetException("Register not writable: " + reg.displayName());
		}
	}

	@Override
	public void writeMemory(int address, int value) throws JopTargetException {
		checkConnected();
		jopSim.setMemValue(address, value);
	}

	@Override
	public int setBreakpoint(JopBreakpointType type, int address) throws JopTargetException {
		if (type == JopBreakpointType.MICRO_PC) {
			throw new JopTargetException("JopSim does not support microcode breakpoints");
		}
		for (int i = 0; i < MAX_BREAKPOINTS; i++) {
			if (bpSlots[i] == null) {
				bpSlots[i] = new JopBreakpointInfo(i, type, address);
				return i;
			}
		}
		throw new JopTargetException("All " + MAX_BREAKPOINTS + " breakpoint slots are in use");
	}

	@Override
	public void clearBreakpoint(int slot) throws JopTargetException {
		if (slot < 0 || slot >= MAX_BREAKPOINTS) {
			throw new JopTargetException("Invalid breakpoint slot: " + slot);
		}
		bpSlots[slot] = null;
	}

	@Override
	public JopBreakpointInfo[] getBreakpoints() {
		int count = 0;
		for (JopBreakpointInfo bp : bpSlots) {
			if (bp != null) count++;
		}
		JopBreakpointInfo[] result = new JopBreakpointInfo[count];
		int idx = 0;
		for (JopBreakpointInfo bp : bpSlots) {
			if (bp != null) result[idx++] = bp;
		}
		return result;
	}

	@Override
	public JopTargetInfo getTargetInfo() {
		return new JopTargetInfo(1, MAX_BREAKPOINTS, 64 * 1024, 1024 * 1024, "jopsim", 1, 0, 0);
	}

	@Override
	public int resolveLineToAddress(int sourceLine) {
		return -1;
	}

	@Override
	public void provideInput(String text) throws JopTargetException {
		// JopSim reads from System.in; no easy way to inject without
		// modifying IOSimMin's read() path. No-op for now.
	}

	@Override
	public int getCurrentSourceLine() {
		return -1;
	}

	@Override
	public String getCurrentInstructionName() {
		if (jopSim == null) return null;
		try {
			int pc = jopSim.getPC();
			int bytecode = jopSim.readBytecode(pc);
			return com.jopdesign.tools.JopInstr.name(bytecode);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public void addListener(IJopTargetListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(IJopTargetListener listener) {
		listeners.remove(listener);
	}

	// --- Internal helpers ---

	private int computeAbsoluteJPC() {
		if (jopSim == null) return 0;
		try {
			int mp = jopSim.getMP();
			int mtabEntry = jopSim.readMemPublic(mp);
			int methodStart = mtabEntry >>> 10;
			int offsetInMethod = jopSim.corrPc(jopSim.getPC());
			return methodStart + offsetInMethod;
		} catch (Exception e) {
			return 0;
		}
	}

	private void executeOneInstruction() {
		// Skip timing delay cycles (localCnt > 0 means interpret() just decrements)
		do {
			if (!jopSim.interpretOne()) {
				state = JopTargetState.TERMINATED;
				fireStateChanged(JopTargetState.TERMINATED, null, -1);
				return;
			}
		} while (jopSim.getLocalCnt() > 0);
	}

	/**
	 * Check if the current JPC matches any breakpoint.
	 * @return the slot index of the matching breakpoint, or -1 if none
	 */
	private int checkBreakpoints() {
		int currentJPC = computeAbsoluteJPC();
		for (int i = 0; i < MAX_BREAKPOINTS; i++) {
			if (bpSlots[i] != null && bpSlots[i].address() == currentJPC) {
				return i;
			}
		}
		return -1;
	}

	private void startWorkerThread() {
		workerThread = new Thread(() -> {
			while (!terminated && !Thread.currentThread().isInterrupted()) {
				// Wait while halted
				lock.lock();
				try {
					while (halted && !terminated) {
						try {
							haltCondition.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							return;
						}
					}
				} finally {
					lock.unlock();
				}

				if (terminated || Thread.currentThread().isInterrupted()) {
					break;
				}

				// Run until halted, breakpoint, or exit
				JopSim.setExit(false); // Ensure static flag is clear for this run
				while (!halted && !terminated) {
					if (!jopSim.interpretOne()) {
						// Program exited
						state = JopTargetState.TERMINATED;
						fireStateChanged(JopTargetState.TERMINATED, null, -1);
						return;
					}

					// Only check breakpoints after real instructions (not timing delays)
					if (jopSim.getLocalCnt() == 0) {
						int hitSlot = checkBreakpoints();
						if (hitSlot >= 0) {
							halted = true;
							state = JopTargetState.SUSPENDED;
							fireStateChanged(JopTargetState.SUSPENDED, JopSuspendReason.BREAKPOINT, hitSlot);
							break;
						}
					}
				}

				// If halted by suspend() (not breakpoint)
				if (halted && state == JopTargetState.RUNNING) {
					state = JopTargetState.SUSPENDED;
					fireStateChanged(JopTargetState.SUSPENDED, JopSuspendReason.MANUAL, -1);
				}
			}
		}, "JopSim-Worker");
		workerThread.setDaemon(true);
		workerThread.start();
	}

	private void checkConnected() throws JopTargetException {
		if (jopSim == null) {
			throw new JopTargetException("Target not connected");
		}
	}

	private void fireStateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
		for (IJopTargetListener l : listeners) {
			l.stateChanged(newState, reason, breakpointSlot);
		}
	}

	private void fireOutputProduced(String text) {
		for (IJopTargetListener l : listeners) {
			l.outputProduced(text);
		}
	}
}
