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
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;


import com.jopdesign.core.sim.IJopTarget;
import com.jopdesign.core.sim.IJopTargetListener;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.core.sim.JopTargetState;

/**
 * Debug target backed by an {@link IJopTarget}.
 * Bridges any JOP target implementation to the Eclipse debug framework.
 */
public class JopDebugTarget implements IDebugTarget {

	public static final String MODEL_ID = "com.jopdesign.debug";

	private final ILaunch launch;
	private final IJopTarget target;
	private final JopThread thread;
	private final String name;

	/** Tracks whether a step operation is in progress for correct event detail. */
	private volatile boolean stepping;

	public JopDebugTarget(ILaunch launch, IJopTarget target, String name) {
		this.launch = launch;
		this.target = target;
		this.name = name;
		this.thread = new JopThread(this);

		// Listen for target state changes
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState) {
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

		// Install existing breakpoints (shared MicrocodeLineBreakpoint type)
		IBreakpoint[] breakpoints = bpManager.getBreakpoints(MicrocodeDebugTarget.MODEL_ID);
		for (IBreakpoint bp : breakpoints) {
			breakpointAdded(bp);
		}

		// Fire creation event
		fireEvent(new DebugEvent(this, DebugEvent.CREATE));
	}

	private void handleStateChange(JopTargetState newState) {
		switch (newState) {
			case SUSPENDED -> {
				int detail = stepping ? DebugEvent.STEP_END : DebugEvent.BREAKPOINT;
				stepping = false;
				fireEvent(new DebugEvent(thread, DebugEvent.SUSPEND, detail));
			}
			case TERMINATED -> {
				stepping = false;
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

	public IJopTarget getTarget() {
		return target;
	}

	public void stepOver() throws DebugException {
		stepping = true;
		fireEvent(new DebugEvent(thread, DebugEvent.RESUME, DebugEvent.STEP_OVER));
		try {
			target.stepMicro();
		} catch (JopTargetException e) {
			stepping = false;
			throw new DebugException(new Status(IStatus.ERROR, MODEL_ID, e.getMessage(), e));
		}
	}

	public void stepInto() throws DebugException {
		stepping = true;
		fireEvent(new DebugEvent(thread, DebugEvent.RESUME, DebugEvent.STEP_INTO));
		try {
			target.stepBytecode();
		} catch (JopTargetException e) {
			stepping = false;
			throw new DebugException(new Status(IStatus.ERROR, MODEL_ID, e.getMessage(), e));
		}
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
		return target.getState() == JopTargetState.TERMINATED;
	}

	@Override
	public void terminate() throws DebugException {
		try {
			target.terminate();
		} catch (JopTargetException e) {
			throw new DebugException(new Status(IStatus.ERROR, MODEL_ID, e.getMessage(), e));
		}
	}

	@Override
	public boolean canResume() {
		JopTargetState s = target.getState();
		return s == JopTargetState.SUSPENDED || s == JopTargetState.NOT_STARTED;
	}

	@Override
	public boolean canSuspend() {
		return target.getState() == JopTargetState.RUNNING;
	}

	@Override
	public boolean isSuspended() {
		JopTargetState s = target.getState();
		return s == JopTargetState.SUSPENDED || s == JopTargetState.NOT_STARTED;
	}

	@Override
	public void resume() throws DebugException {
		try {
			target.resume();
		} catch (JopTargetException e) {
			throw new DebugException(new Status(IStatus.ERROR, MODEL_ID, e.getMessage(), e));
		}
	}

	@Override
	public void suspend() throws DebugException {
		try {
			target.suspend();
		} catch (JopTargetException e) {
			throw new DebugException(new Status(IStatus.ERROR, MODEL_ID, e.getMessage(), e));
		}
	}

	// --- IBreakpointListener ---

	@Override
	public void breakpointAdded(IBreakpoint breakpoint) {
		if (breakpoint instanceof MicrocodeLineBreakpoint lineBp) {
			try {
				if (lineBp.isEnabled()) {
					target.addBreakpoint(lineBp.getLineNumber());
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
				target.removeBreakpoint(lineBp.getLineNumber());
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
					target.addBreakpoint(lineBp.getLineNumber());
				} else {
					target.removeBreakpoint(lineBp.getLineNumber());
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
