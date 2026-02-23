package com.jopdesign.microcode.debug;

import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;

import com.jopdesign.core.sim.microcode.SimulatorState;

/**
 * Single thread representing microcode execution.
 */
public class MicrocodeThread implements IThread {

	private final MicrocodeDebugTarget target;

	public MicrocodeThread(MicrocodeDebugTarget target) {
		this.target = target;
	}

	@Override
	public String getName() {
		return "Microcode Execution";
	}

	@Override
	public int getPriority() {
		return 0;
	}

	@Override
	public IStackFrame[] getStackFrames() {
		if (isSuspended()) {
			return new IStackFrame[] { new MicrocodeStackFrame(this, target) };
		}
		return new IStackFrame[0];
	}

	@Override
	public boolean hasStackFrames() {
		return isSuspended();
	}

	@Override
	public IStackFrame getTopStackFrame() {
		if (isSuspended()) {
			return new MicrocodeStackFrame(this, target);
		}
		return null;
	}

	@Override
	public IBreakpoint[] getBreakpoints() {
		return new IBreakpoint[0];
	}

	@Override
	public boolean canResume() {
		return isSuspended();
	}

	@Override
	public boolean canSuspend() {
		return target.getSimulator().getState() == SimulatorState.RUNNING;
	}

	@Override
	public boolean isSuspended() {
		SimulatorState state = target.getSimulator().getState();
		return state == SimulatorState.SUSPENDED || state == SimulatorState.NOT_STARTED;
	}

	@Override
	public void resume() throws DebugException {
		target.resume();
	}

	@Override
	public void suspend() throws DebugException {
		target.suspend();
	}

	@Override
	public boolean canStepInto() {
		return false;
	}

	@Override
	public boolean canStepOver() {
		return isSuspended();
	}

	@Override
	public boolean canStepReturn() {
		return false;
	}

	@Override
	public boolean isStepping() {
		return false;
	}

	@Override
	public void stepInto() throws DebugException {
		// No nested levels in microcode
	}

	@Override
	public void stepOver() throws DebugException {
		target.stepOver();
	}

	@Override
	public void stepReturn() throws DebugException {
		// No nested levels in microcode
	}

	@Override
	public boolean canTerminate() {
		return !isTerminated();
	}

	@Override
	public boolean isTerminated() {
		return target.getSimulator().getState() == SimulatorState.TERMINATED;
	}

	@Override
	public void terminate() throws DebugException {
		target.terminate();
	}

	@Override
	public String getModelIdentifier() {
		return MicrocodeDebugTarget.MODEL_ID;
	}

	@Override
	public IDebugTarget getDebugTarget() {
		return target;
	}

	@Override
	public ILaunch getLaunch() {
		return target.getLaunch();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (adapter == IDebugTarget.class) {
			return (T) getDebugTarget();
		}
		if (adapter == ILaunch.class) {
			return (T) getLaunch();
		}
		return Platform.getAdapterManager().getAdapter(this, adapter);
	}
}
