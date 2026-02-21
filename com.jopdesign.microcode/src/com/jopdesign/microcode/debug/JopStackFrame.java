package com.jopdesign.microcode.debug;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IRegisterGroup;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;

import com.jopdesign.core.sim.IJopTarget;
import com.jopdesign.core.sim.JopRegisters;
import com.jopdesign.core.sim.JopStackData;
import com.jopdesign.core.sim.JopTargetException;

/**
 * Stack frame for the unified JOP debug model. Shows all processor registers
 * and stack contents from the underlying {@link IJopTarget}.
 */
public class JopStackFrame implements IStackFrame {

	private final JopThread thread;
	private final JopDebugTarget target;

	public JopStackFrame(JopThread thread, JopDebugTarget target) {
		this.thread = thread;
		this.target = target;
	}

	@Override
	public IThread getThread() {
		return thread;
	}

	@Override
	public IVariable[] getVariables() {
		IJopTarget jopTarget = target.getTarget();
		List<IVariable> vars = new ArrayList<>();

		try {
			JopRegisters regs = jopTarget.readRegisters();

			// Registers
			vars.add(new JopVariable(target, "A (TOS)", regs.a()));
			vars.add(new JopVariable(target, "B (NOS)", regs.b()));
			vars.add(new JopVariable(target, "pc", regs.pc()));
			vars.add(new JopVariable(target, "sp", regs.sp()));
			vars.add(new JopVariable(target, "vp", regs.vp()));
			vars.add(new JopVariable(target, "ar", regs.ar()));
			vars.add(new JopVariable(target, "jpc", regs.jpc()));
			vars.add(new JopVariable(target, "mulA", regs.mulA()));
			vars.add(new JopVariable(target, "mulB", regs.mulB()));
			vars.add(new JopVariable(target, "mulResultLo", (int) regs.mulResult()));
			vars.add(new JopVariable(target, "mulResultHi", (int) (regs.mulResult() >>> 32)));
			vars.add(new JopVariable(target, "memReadAddr", regs.memReadAddr()));
			vars.add(new JopVariable(target, "memWriteAddr", regs.memWriteAddr()));
			vars.add(new JopVariable(target, "memWriteData", regs.memWriteData()));
			vars.add(new JopVariable(target, "memReadData", regs.memReadData()));

			// Stack contents
			JopStackData stackData = jopTarget.readStack();
			int limit = Math.min(stackData.values().length, 32);
			for (int i = 0; i < limit; i++) {
				vars.add(new JopVariable(target, "stack[" + i + "]", stackData.values()[i]));
			}
		} catch (JopTargetException e) {
			// Return whatever we have
		}

		return vars.toArray(new IVariable[0]);
	}

	@Override
	public boolean hasVariables() {
		return true;
	}

	@Override
	public int getLineNumber() {
		return target.getTarget().getCurrentSourceLine();
	}

	@Override
	public int getCharStart() {
		return -1;
	}

	@Override
	public int getCharEnd() {
		return -1;
	}

	@Override
	public String getName() {
		IJopTarget jopTarget = target.getTarget();
		String mnemonic = jopTarget.getCurrentInstructionName();
		int line = jopTarget.getCurrentSourceLine();
		if (mnemonic != null) {
			return mnemonic + " (line " + line + ")";
		}
		return "jop (line " + line + ")";
	}

	@Override
	public IRegisterGroup[] getRegisterGroups() {
		return new IRegisterGroup[0];
	}

	@Override
	public boolean hasRegisterGroups() {
		return false;
	}

	@Override
	public boolean canStepInto() {
		return thread.canStepInto();
	}

	@Override
	public boolean canStepOver() {
		return thread.canStepOver();
	}

	@Override
	public boolean canStepReturn() {
		return false;
	}

	@Override
	public boolean isStepping() {
		return thread.isStepping();
	}

	@Override
	public void stepInto() throws DebugException {
		thread.stepInto();
	}

	@Override
	public void stepOver() throws DebugException {
		thread.stepOver();
	}

	@Override
	public void stepReturn() throws DebugException {
		// No nested levels
	}

	@Override
	public boolean canResume() {
		return thread.canResume();
	}

	@Override
	public boolean canSuspend() {
		return thread.canSuspend();
	}

	@Override
	public boolean isSuspended() {
		return thread.isSuspended();
	}

	@Override
	public void resume() throws DebugException {
		thread.resume();
	}

	@Override
	public void suspend() throws DebugException {
		thread.suspend();
	}

	@Override
	public boolean canTerminate() {
		return thread.canTerminate();
	}

	@Override
	public boolean isTerminated() {
		return thread.isTerminated();
	}

	@Override
	public void terminate() throws DebugException {
		thread.terminate();
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
		return null;
	}
}
