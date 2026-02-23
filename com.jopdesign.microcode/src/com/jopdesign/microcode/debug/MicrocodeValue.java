package com.jopdesign.microcode.debug;

import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;

/**
 * A value in the microcode debug model, displayed as "decimal (0xHEX)".
 */
public class MicrocodeValue implements IValue {

	private final MicrocodeDebugTarget target;
	private final int value;

	public MicrocodeValue(MicrocodeDebugTarget target, int value) {
		this.target = target;
		this.value = value;
	}

	@Override
	public String getReferenceTypeName() {
		return "int";
	}

	@Override
	public String getValueString() {
		return String.format("%d (0x%08X)", value, value);
	}

	@Override
	public boolean isAllocated() {
		return true;
	}

	@Override
	public IVariable[] getVariables() {
		return new IVariable[0];
	}

	@Override
	public boolean hasVariables() {
		return false;
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
		return Platform.getAdapterManager().getAdapter(this, adapter);
	}
}
