package com.jopdesign.microcode.debug;

import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;

import com.jopdesign.core.sim.JopTargetState;

/**
 * Single thread representing JOP execution in the unified debug model.
 */
public class JopThread implements IThread {

	private final JopDebugTarget target;
	private JopStackFrame cachedFrame;

	public JopThread(JopDebugTarget target) {
		this.target = target;
	}

	/**
	 * Returns the cached stack frame (creating it if needed).
	 * Reusing the same object lets Eclipse track the instruction pointer annotation.
	 */
	JopStackFrame getCachedFrame() {
		if (cachedFrame == null) {
			cachedFrame = new JopStackFrame(this, target);
		}
		return cachedFrame;
	}

	@Override
	public String getName() {
		return "JOP Execution";
	}

	@Override
	public int getPriority() {
		return 0;
	}

	@Override
	public IStackFrame[] getStackFrames() {
		if (isSuspended()) {
			return new IStackFrame[] { getCachedFrame() };
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
			return getCachedFrame();
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
		return target.getTarget().getState() == JopTargetState.RUNNING;
	}

	@Override
	public boolean isSuspended() {
		JopTargetState state = target.getTarget().getState();
		return state == JopTargetState.SUSPENDED || state == JopTargetState.NOT_STARTED;
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
		return isSuspended();
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
		target.stepInto();
	}

	@Override
	public void stepOver() throws DebugException {
		target.stepOver();
	}

	@Override
	public void stepReturn() throws DebugException {
		// No nested levels
	}

	@Override
	public boolean canTerminate() {
		return !isTerminated();
	}

	@Override
	public boolean isTerminated() {
		return target.getTarget().getState() == JopTargetState.TERMINATED;
	}

	@Override
	public void terminate() throws DebugException {
		target.terminate();
	}

	@Override
	public String getModelIdentifier() {
		return JopDebugTarget.MODEL_ID;
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
