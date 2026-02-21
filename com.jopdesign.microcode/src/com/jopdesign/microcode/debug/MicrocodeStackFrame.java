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

import com.jopdesign.core.sim.microcode.MicrocodeSimulator;
import com.jopdesign.core.sim.microcode.MicrocodeStatement;

/**
 * Stack frame for the microcode debug model. Shows registers and stack contents.
 */
public class MicrocodeStackFrame implements IStackFrame {

	private final MicrocodeThread thread;
	private final MicrocodeDebugTarget target;

	public MicrocodeStackFrame(MicrocodeThread thread, MicrocodeDebugTarget target) {
		this.thread = thread;
		this.target = target;
	}

	@Override
	public IThread getThread() {
		return thread;
	}

	@Override
	public IVariable[] getVariables() {
		MicrocodeSimulator sim = target.getSimulator();
		List<IVariable> vars = new ArrayList<>();

		// Registers
		vars.add(new MicrocodeVariable(target, "A (TOS)", sim.getA()));
		vars.add(new MicrocodeVariable(target, "B (NOS)", sim.getB()));
		vars.add(new MicrocodeVariable(target, "pc", sim.getPC()));
		vars.add(new MicrocodeVariable(target, "sp", sim.getSP()));
		vars.add(new MicrocodeVariable(target, "vp", sim.getVP()));
		vars.add(new MicrocodeVariable(target, "ar", sim.getAR()));
		vars.add(new MicrocodeVariable(target, "jpc", sim.getJPC()));

		// Stack contents (show up to sp or a reasonable limit)
		int sp = sim.getSP();
		int limit = Math.min(sp + 1, 32);
		for (int i = 0; i < limit; i++) {
			vars.add(new MicrocodeVariable(target, "stack[" + i + "]", sim.getStackValue(i)));
		}

		return vars.toArray(new IVariable[0]);
	}

	@Override
	public boolean hasVariables() {
		return true;
	}

	@Override
	public int getLineNumber() {
		return target.getSimulator().getCurrentSourceLine();
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
		MicrocodeSimulator sim = target.getSimulator();
		MicrocodeStatement stmt = sim.getCurrentStatement();
		int line = sim.getCurrentSourceLine();
		if (stmt != null) {
			return stmt.mnemonic() + " (line " + line + ")";
		}
		return "microcode (line " + line + ")";
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
		return false;
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
		// No nested levels
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
		return null;
	}
}
