package com.jopdesign.microcode.debug;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;

/**
 * A variable in the unified JOP debug model (register, stack slot, etc.).
 */
public class JopVariable implements IVariable {

	private final JopDebugTarget target;
	private final String name;
	private final int value;

	public JopVariable(JopDebugTarget target, String name, int value) {
		this.target = target;
		this.name = name;
		this.value = value;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getReferenceTypeName() {
		return "int";
	}

	@Override
	public IValue getValue() {
		return new JopValue(target, value);
	}

	@Override
	public boolean hasValueChanged() {
		return false;
	}

	@Override
	public void setValue(String expression) throws DebugException {
		// Read-only
	}

	@Override
	public void setValue(IValue value) throws DebugException {
		// Read-only
	}

	@Override
	public boolean supportsValueModification() {
		return false;
	}

	@Override
	public boolean verifyValue(String expression) {
		return false;
	}

	@Override
	public boolean verifyValue(IValue value) {
		return false;
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
