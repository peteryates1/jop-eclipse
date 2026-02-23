package com.jopdesign.core.sim;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jopdesign.core.sim.protocol.JopMessage;
import com.jopdesign.core.sim.protocol.JopMessageType;
import com.jopdesign.core.sim.protocol.JopNakCode;
import com.jopdesign.core.sim.protocol.JopProtocolCodec;
import com.jopdesign.core.sim.protocol.JopProtocolException;
import com.jopdesign.core.sim.protocol.JopTargetInfoParser;
import com.jopdesign.core.sim.transport.JopTransport;

/**
 * Base IJopTarget implementation that communicates over the binary debug
 * protocol via a {@link JopTransport}. Shared by RTL simulation and FPGA
 * hardware targets.
 *
 * <p>This handles:
 * <ul>
 *   <li>Protocol handshake (PING/PONG, QUERY_INFO, version check)</li>
 *   <li>Command encoding and response decoding</li>
 *   <li>Async HALTED notification → stateChanged() dispatch</li>
 *   <li>Register/stack/memory reads via protocol</li>
 *   <li>Hardware breakpoint management</li>
 * </ul>
 */
public class ProtocolJopTarget implements IJopTarget {

	/** Default response timeout (100ms per protocol spec). */
	private static final long DEFAULT_TIMEOUT_MS = 100;
	/** Bulk transfer timeout (500ms per protocol spec). */
	private static final long BULK_TIMEOUT_MS = 500;
	/** Connection/handshake timeout. */
	private static final long CONNECT_TIMEOUT_MS = 5000;

	private final String name;
	private final String targetTypeId;
	private final JopTransport transport;
	private final int coreId;

	private JopTargetState state = JopTargetState.NOT_STARTED;
	private JopTargetInfo targetInfo;
	private final List<IJopTargetListener> listeners = new CopyOnWriteArrayList<>();

	/**
	 * @param name         human-readable name
	 * @param targetTypeId "rtlsim" or "fpga"
	 * @param transport    the transport to use
	 * @param coreId       core to debug (0 for single-core)
	 */
	public ProtocolJopTarget(String name, String targetTypeId, JopTransport transport, int coreId) {
		this.name = name;
		this.targetTypeId = targetTypeId;
		this.transport = transport;
		this.coreId = coreId;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getTargetTypeId() {
		return targetTypeId;
	}

	@Override
	public void connect() throws JopTargetException {
		try {
			transport.open();
		} catch (JopProtocolException e) {
			throw new JopTargetException("Transport open failed: " + e.getMessage(), e);
		}

		// Set up notification listener for HALTED
		transport.setNotificationListener(this::handleNotification);

		try {
			// PING/PONG handshake
			sendAndExpect(JopMessageType.PING, JopMessageType.PONG, CONNECT_TIMEOUT_MS);

			// QUERY_INFO
			JopMessage infoResp = sendAndExpect(JopMessageType.QUERY_INFO, JopMessageType.TARGET_INFO,
					CONNECT_TIMEOUT_MS);
			targetInfo = JopTargetInfoParser.parse(infoResp.payload());

			// Version check
			if (targetInfo.protocolMajor() != 1) {
				throw new JopTargetException("Unsupported protocol version: "
						+ targetInfo.protocolMajor() + "." + targetInfo.protocolMinor()
						+ " (expected 1.x)");
			}

			// HALT the target on connect
			sendCommand(JopMessageType.HALT);
			expectAck(DEFAULT_TIMEOUT_MS);
			// Wait for HALTED notification (the read thread dispatches it)
			// Give it a moment then update state
			Thread.sleep(50);
			state = JopTargetState.SUSPENDED;
			fireStateChanged(JopTargetState.SUSPENDED, JopSuspendReason.MANUAL, -1);

		} catch (JopTargetException e) {
			transport.close();
			throw e;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			transport.close();
			throw new JopTargetException("Interrupted during connect");
		} catch (JopProtocolException e) {
			transport.close();
			throw new JopTargetException("Protocol error during connect: " + e.getMessage(), e);
		}
	}

	@Override
	public void disconnect() throws JopTargetException {
		state = JopTargetState.TERMINATED;
		transport.close();
		fireStateChanged(JopTargetState.TERMINATED, null, -1);
	}

	@Override
	public void resume() throws JopTargetException {
		checkConnected();
		try {
			sendCommand(JopMessageType.RESUME);
			expectAck(DEFAULT_TIMEOUT_MS);
			state = JopTargetState.RUNNING;
			fireStateChanged(JopTargetState.RUNNING, null, -1);
		} catch (JopProtocolException e) {
			throw wrapProtocolError("resume", e);
		}
	}

	@Override
	public void suspend() throws JopTargetException {
		checkConnected();
		try {
			sendCommand(JopMessageType.HALT);
			expectAck(DEFAULT_TIMEOUT_MS);
			// HALTED notification will update state via handleNotification
		} catch (JopProtocolException e) {
			throw wrapProtocolError("suspend", e);
		}
	}

	@Override
	public void terminate() throws JopTargetException {
		disconnect();
	}

	@Override
	public void reset() throws JopTargetException {
		checkConnected();
		try {
			sendCommand(JopMessageType.RESET);
			expectAck(DEFAULT_TIMEOUT_MS);
			// HALTED notification with RESET reason will arrive
		} catch (JopProtocolException e) {
			throw wrapProtocolError("reset", e);
		}
	}

	@Override
	public void stepMicro() throws JopTargetException {
		checkConnected();
		try {
			sendCommand(JopMessageType.STEP_MICRO);
			expectAck(DEFAULT_TIMEOUT_MS);
			// HALTED notification with STEP_COMPLETE reason will arrive
		} catch (JopProtocolException e) {
			throw wrapProtocolError("stepMicro", e);
		}
	}

	@Override
	public void stepBytecode() throws JopTargetException {
		checkConnected();
		try {
			sendCommand(JopMessageType.STEP_BYTECODE);
			expectAck(DEFAULT_TIMEOUT_MS);
			// HALTED notification with STEP_COMPLETE reason will arrive
		} catch (JopProtocolException e) {
			throw wrapProtocolError("stepBytecode", e);
		}
	}

	@Override
	public JopTargetState getState() {
		return state;
	}

	@Override
	public JopRegisters readRegisters() throws JopTargetException {
		checkConnected();
		try {
			JopMessage resp = sendAndExpect(JopMessageType.READ_REGISTERS,
					JopMessageType.REGISTERS, DEFAULT_TIMEOUT_MS);
			int[] regs = JopProtocolCodec.parseRegisters(resp.payload());

			// Base 12 registers always present, extended are optional
			int pc = regs.length > 0 ? regs[0] : 0;
			int jpc = regs.length > 1 ? regs[1] : 0;
			int a = regs.length > 2 ? regs[2] : 0;
			int b = regs.length > 3 ? regs[3] : 0;
			int sp = regs.length > 4 ? regs[4] : 0;
			int vp = regs.length > 5 ? regs[5] : 0;
			int ar = regs.length > 6 ? regs[6] : 0;
			int mulResult = regs.length > 7 ? regs[7] : 0;
			int memReadAddr = regs.length > 8 ? regs[8] : 0;
			int memWriteAddr = regs.length > 9 ? regs[9] : 0;
			int memReadData = regs.length > 10 ? regs[10] : 0;
			int memWriteData = regs.length > 11 ? regs[11] : 0;
			int flags = regs.length > 12 ? regs[12] : 0;
			int instr = regs.length > 13 ? regs[13] : 0;
			int jopd = regs.length > 14 ? regs[14] : 0;

			return new JopRegisters(a, b, pc, sp, vp, ar, jpc,
					mulResult, memReadAddr, memWriteAddr, memWriteData, memReadData,
					flags, instr, jopd);
		} catch (JopProtocolException e) {
			throw wrapProtocolError("readRegisters", e);
		}
	}

	@Override
	public JopStackData readStack() throws JopTargetException {
		checkConnected();
		try {
			// Read SP first to know how much stack to request
			JopRegisters regs = readRegisters();
			int sp = regs.sp();
			int count = Math.min(sp + 1, 256);

			byte[] payload = JopProtocolCodec.readStackPayload(0, count);
			JopMessage resp = sendAndExpect(JopMessageType.READ_STACK, payload,
					JopMessageType.STACK_DATA, BULK_TIMEOUT_MS);
			int[] values = JopProtocolCodec.parseWordArray(resp.payload());
			return new JopStackData(values, sp);
		} catch (JopProtocolException e) {
			throw wrapProtocolError("readStack", e);
		}
	}

	@Override
	public JopMemoryData readMemory(int address, int length) throws JopTargetException {
		checkConnected();
		if (length > 256) {
			throw new JopTargetException("readMemory: max 256 words per call, got " + length);
		}
		try {
			byte[] payload = JopProtocolCodec.readMemoryPayload(address, length);
			JopMessage resp = sendAndExpect(JopMessageType.READ_MEMORY, payload,
					JopMessageType.MEMORY_DATA, BULK_TIMEOUT_MS);
			int[] values = JopProtocolCodec.parseWordArray(resp.payload());
			return new JopMemoryData(address, values);
		} catch (JopProtocolException e) {
			throw wrapProtocolError("readMemory", e);
		}
	}

	@Override
	public void writeRegister(JopRegister reg, int value) throws JopTargetException {
		checkConnected();
		try {
			byte[] payload = JopProtocolCodec.writeRegisterPayload(reg.protocolId(), value);
			sendWithPayload(JopMessageType.WRITE_REGISTER, payload);
			expectAck(DEFAULT_TIMEOUT_MS);
		} catch (JopProtocolException e) {
			throw wrapProtocolError("writeRegister", e);
		}
	}

	@Override
	public void writeMemory(int address, int value) throws JopTargetException {
		checkConnected();
		try {
			byte[] payload = JopProtocolCodec.writeMemoryPayload(address, value);
			sendWithPayload(JopMessageType.WRITE_MEMORY, payload);
			expectAck(DEFAULT_TIMEOUT_MS);
		} catch (JopProtocolException e) {
			throw wrapProtocolError("writeMemory", e);
		}
	}

	@Override
	public void writeMemoryBlock(int address, int[] values) throws JopTargetException {
		if (values.length > 256) {
			throw new JopTargetException("writeMemoryBlock: max 256 words per call, got " + values.length);
		}
		checkConnected();
		try {
			byte[] payload = JopProtocolCodec.writeMemoryBlockPayload(address, values);
			sendWithPayload(JopMessageType.WRITE_MEMORY_BLOCK, payload);
			expectAck(BULK_TIMEOUT_MS);
		} catch (JopProtocolException e) {
			throw wrapProtocolError("writeMemoryBlock", e);
		}
	}

	@Override
	public int setBreakpoint(JopBreakpointType type, int address) throws JopTargetException {
		checkConnected();
		try {
			byte[] payload = JopProtocolCodec.setBreakpointPayload(type.protocolCode(), address);
			sendWithPayload(JopMessageType.SET_BREAKPOINT, payload);
			JopMessage resp = receiveResponse(DEFAULT_TIMEOUT_MS);
			if (resp.type() == JopMessageType.NAK) {
				int code = resp.payload().length > 0 ? resp.payload()[0] & 0xFF : 0xFF;
				JopNakCode nak = JopNakCode.byCode(code);
				throw new JopTargetException("setBreakpoint NAK: " + nak.description());
			}
			// ACK with 1-byte slot number
			if (resp.payload().length > 0) {
				return resp.payload()[0] & 0xFF;
			}
			return 0;
		} catch (JopProtocolException e) {
			throw wrapProtocolError("setBreakpoint", e);
		}
	}

	@Override
	public void clearBreakpoint(int slot) throws JopTargetException {
		checkConnected();
		try {
			byte[] payload = JopProtocolCodec.clearBreakpointPayload(slot);
			sendWithPayload(JopMessageType.CLEAR_BREAKPOINT, payload);
			expectAck(DEFAULT_TIMEOUT_MS);
		} catch (JopProtocolException e) {
			throw wrapProtocolError("clearBreakpoint", e);
		}
	}

	@Override
	public JopBreakpointInfo[] getBreakpoints() {
		try {
			sendCommand(JopMessageType.QUERY_BREAKPOINTS);
			JopMessage resp = receiveResponse(DEFAULT_TIMEOUT_MS);
			if (resp.type() != JopMessageType.BREAKPOINT_LIST) {
				return new JopBreakpointInfo[0];
			}
			byte[] data = resp.payload();
			int totalSlots = data.length / 6;
			// Count only active breakpoints (bit 7 of slot byte set)
			int activeCount = 0;
			for (int i = 0; i < totalSlots; i++) {
				if ((data[i * 6] & 0x80) != 0) activeCount++;
			}
			JopBreakpointInfo[] result = new JopBreakpointInfo[activeCount];
			int idx = 0;
			for (int i = 0; i < totalSlots; i++) {
				int offset = i * 6;
				int slotByte = data[offset] & 0xFF;
				if ((slotByte & 0x80) == 0) continue; // inactive slot
				int bpSlot = slotByte & 0x7F; // lower 7 bits = slot number
				int bpType = data[offset + 1] & 0xFF;
				int addr = JopProtocolCodec.getInt32(data, offset + 2);
				JopBreakpointType type = bpType == 0x01
						? JopBreakpointType.BYTECODE_JPC
						: JopBreakpointType.MICRO_PC;
				result[idx++] = new JopBreakpointInfo(bpSlot, type, addr);
			}
			return result;
		} catch (Exception e) {
			return new JopBreakpointInfo[0];
		}
	}

	@Override
	public JopTargetInfo getTargetInfo() {
		if (targetInfo == null) {
			return new JopTargetInfo(1, 0, 0, 0, "unknown", 0, 0, 0);
		}
		return targetInfo;
	}

	@Override
	public int resolveLineToAddress(int sourceLine) {
		return -1; // Hardware targets don't have source mapping
	}

	@Override
	public void provideInput(String text) throws JopTargetException {
		// UART input not supported over debug protocol
	}

	@Override
	public int getCurrentSourceLine() {
		return -1;
	}

	@Override
	public String getCurrentInstructionName() {
		return null;
	}

	@Override
	public void addListener(IJopTargetListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(IJopTargetListener listener) {
		listeners.remove(listener);
	}

	/** Access the underlying transport (for subclasses). */
	protected JopTransport getTransport() {
		return transport;
	}

	// --- Protocol helpers ---

	private void sendCommand(int type) throws JopProtocolException {
		transport.send(JopMessage.of(type, coreId));
	}

	private void sendWithPayload(int type, byte[] payload) throws JopProtocolException {
		transport.send(JopMessage.of(type, coreId, payload));
	}

	private JopMessage receiveResponse(long timeoutMs) throws JopProtocolException {
		return transport.receive(timeoutMs);
	}

	private void expectAck(long timeoutMs) throws JopProtocolException, JopTargetException {
		JopMessage resp = receiveResponse(timeoutMs);
		if (resp.type() == JopMessageType.NAK) {
			int code = resp.payload().length > 0 ? resp.payload()[0] & 0xFF : 0xFF;
			JopNakCode nak = JopNakCode.byCode(code);
			throw new JopTargetException("Command NAK: " + nak.description());
		}
		if (resp.type() != JopMessageType.ACK) {
			throw new JopProtocolException("Expected ACK, got 0x%02X".formatted(resp.type()));
		}
	}

	private JopMessage sendAndExpect(int requestType, int expectedResponseType, long timeoutMs)
			throws JopProtocolException, JopTargetException {
		sendCommand(requestType);
		return expectResponse(expectedResponseType, timeoutMs);
	}

	private JopMessage sendAndExpect(int requestType, byte[] payload, int expectedResponseType, long timeoutMs)
			throws JopProtocolException, JopTargetException {
		sendWithPayload(requestType, payload);
		return expectResponse(expectedResponseType, timeoutMs);
	}

	private JopMessage expectResponse(int expectedType, long timeoutMs)
			throws JopProtocolException, JopTargetException {
		JopMessage resp = receiveResponse(timeoutMs);
		if (resp.type() == JopMessageType.NAK) {
			int code = resp.payload().length > 0 ? resp.payload()[0] & 0xFF : 0xFF;
			JopNakCode nak = JopNakCode.byCode(code);
			throw new JopTargetException("NAK: " + nak.description());
		}
		if (resp.type() != expectedType) {
			throw new JopProtocolException(
					"Expected response type 0x%02X, got 0x%02X".formatted(expectedType, resp.type()));
		}
		return resp;
	}

	private void handleNotification(JopMessage notification) {
		if (notification.type() == JopMessageType.HALTED) {
			int[] parsed = JopProtocolCodec.parseHalted(notification.payload());
			int reasonCode = parsed[0];
			int slot = parsed[1];
			JopSuspendReason reason = JopSuspendReason.byProtocolCode(reasonCode);
			int bpSlot = (slot == 0xFF) ? -1 : slot;
			state = JopTargetState.SUSPENDED;
			fireStateChanged(JopTargetState.SUSPENDED, reason, bpSlot);
		}
	}

	private void checkConnected() throws JopTargetException {
		if (!transport.isConnected()) {
			throw new JopTargetException("Target not connected");
		}
	}

	private JopTargetException wrapProtocolError(String operation, JopProtocolException e) {
		return new JopTargetException(operation + ": " + e.getMessage(), e);
	}

	private void fireStateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
		for (IJopTargetListener l : listeners) {
			l.stateChanged(newState, reason, breakpointSlot);
		}
	}

	/** Forward output text to listeners (for subclasses like RTL sim). */
	protected void fireOutput(String text) {
		for (IJopTargetListener l : listeners) {
			l.outputProduced(text);
		}
	}
}
