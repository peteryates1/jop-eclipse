package com.jopdesign.microcode.debug;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;

import com.jopdesign.core.sim.microcode.ISimulatorListener;
import com.jopdesign.core.sim.microcode.MicrocodeSimulator;
import com.jopdesign.core.sim.microcode.SimulatorState;

/**
 * Debug target wrapping the {@link MicrocodeSimulator}.
 * Bridges the Eclipse debug framework with the microcode execution engine.
 */
public class MicrocodeDebugTarget implements IDebugTarget {

	public static final String MODEL_ID = "com.jopdesign.microcode.debug";

	private final ILaunch launch;
	private final MicrocodeSimulator simulator;
	private final MicrocodeThread thread;
	private final String name;

	public MicrocodeDebugTarget(ILaunch launch, MicrocodeSimulator simulator, String name) {
		this.launch = launch;
		this.simulator = simulator;
		this.name = name;
		this.thread = new MicrocodeThread(this);

		// Listen for simulator state changes
		simulator.addListener(new ISimulatorListener() {
			@Override
			public void stateChanged(SimulatorState newState) {
				handleStateChange(newState);
			}

			@Override
			public void outputProduced(String text) {
				// Output handled by launch delegate (console)
			}
		});

		// Register as breakpoint listener
		IBreakpointManager bpManager = DebugPlugin.getDefault().getBreakpointManager();
		bpManager.addBreakpointListener(this);

		// Install existing breakpoints
		IBreakpoint[] breakpoints = bpManager.getBreakpoints(MODEL_ID);
		for (IBreakpoint bp : breakpoints) {
			breakpointAdded(bp);
		}

		// Fire creation event
		fireEvent(new DebugEvent(this, DebugEvent.CREATE));
	}

	private void handleStateChange(SimulatorState newState) {
		switch (newState) {
			case SUSPENDED -> {
				fireEvent(new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT));
			}
			case TERMINATED -> {
				fireEvent(new DebugEvent(thread, DebugEvent.TERMINATE));
				fireEvent(new DebugEvent(this, DebugEvent.TERMINATE));
				DebugPlugin.getDefault().getBreakpointManager().removeBreakpointListener(this);
			}
			case RUNNING -> {
				fireEvent(new DebugEvent(thread, DebugEvent.RESUME, DebugEvent.CLIENT_REQUEST));
			}
			default -> { }
		}
	}

	private void fireEvent(DebugEvent event) {
		DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[] { event });
	}

	// --- Public API ---

	public MicrocodeSimulator getSimulator() {
		return simulator;
	}

	public void stepOver() throws DebugException {
		fireEvent(new DebugEvent(thread, DebugEvent.RESUME, DebugEvent.STEP_OVER));
		simulator.stepOver();
	}

	// --- IDebugTarget ---

	@Override
	public String getName() {
		return name;
	}

	@Override
	public IProcess getProcess() {
		return null; // No OS process
	}

	@Override
	public IThread[] getThreads() {
		return new IThread[] { thread };
	}

	@Override
	public boolean hasThreads() {
		return true;
	}

	@Override
	public boolean supportsBreakpoint(IBreakpoint breakpoint) {
		return breakpoint instanceof MicrocodeLineBreakpoint;
	}

	@Override
	public ILaunch getLaunch() {
		return launch;
	}

	@Override
	public boolean canTerminate() {
		return !isTerminated();
	}

	@Override
	public boolean isTerminated() {
		return simulator.getState() == SimulatorState.TERMINATED;
	}

	@Override
	public void terminate() throws DebugException {
		simulator.terminate();
	}

	@Override
	public boolean canResume() {
		SimulatorState state = simulator.getState();
		return state == SimulatorState.SUSPENDED || state == SimulatorState.NOT_STARTED;
	}

	@Override
	public boolean canSuspend() {
		return simulator.getState() == SimulatorState.RUNNING;
	}

	@Override
	public boolean isSuspended() {
		SimulatorState state = simulator.getState();
		return state == SimulatorState.SUSPENDED || state == SimulatorState.NOT_STARTED;
	}

	@Override
	public void resume() throws DebugException {
		simulator.resume();
	}

	@Override
	public void suspend() throws DebugException {
		simulator.suspend();
	}

	// --- IBreakpointListener ---

	@Override
	public void breakpointAdded(IBreakpoint breakpoint) {
		if (breakpoint instanceof MicrocodeLineBreakpoint lineBp) {
			try {
				if (lineBp.isEnabled()) {
					simulator.addBreakpoint(lineBp.getLineNumber());
				}
			} catch (Exception e) {
				// Ignore
			}
		}
	}

	@Override
	public void breakpointRemoved(IBreakpoint breakpoint, IMarkerDelta delta) {
		if (breakpoint instanceof MicrocodeLineBreakpoint lineBp) {
			try {
				simulator.removeBreakpoint(lineBp.getLineNumber());
			} catch (Exception e) {
				// Ignore
			}
		}
	}

	@Override
	public void breakpointChanged(IBreakpoint breakpoint, IMarkerDelta delta) {
		if (breakpoint instanceof MicrocodeLineBreakpoint lineBp) {
			try {
				if (lineBp.isEnabled()) {
					simulator.addBreakpoint(lineBp.getLineNumber());
				} else {
					simulator.removeBreakpoint(lineBp.getLineNumber());
				}
			} catch (Exception e) {
				// Ignore
			}
		}
	}

	// --- IDebugTarget memory support ---

	@Override
	public boolean supportsStorageRetrieval() {
		return false;
	}

	@Override
	public IMemoryBlock getMemoryBlock(long startAddress, long length) throws DebugException {
		throw new DebugException(new Status(IStatus.ERROR, MODEL_ID, "Not supported"));
	}

	// --- IDebugTarget disconnect ---

	@Override
	public boolean canDisconnect() {
		return false;
	}

	@Override
	public void disconnect() throws DebugException {
		// Not supported
	}

	@Override
	public boolean isDisconnected() {
		return false;
	}

	@Override
	public String getModelIdentifier() {
		return MODEL_ID;
	}

	@Override
	public IDebugTarget getDebugTarget() {
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		return null;
	}
}
