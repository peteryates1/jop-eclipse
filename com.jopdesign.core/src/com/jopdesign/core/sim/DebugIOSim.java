package com.jopdesign.core.sim;

import java.util.function.Consumer;

import com.jopdesign.sys.Const;
import com.jopdesign.tools.IOSimMin;

/**
 * IOSimMin subclass that captures UART output for the debug framework
 * instead of printing to stdout.
 */
public class DebugIOSim extends IOSimMin {

	private Consumer<String> outputListener;

	public void setOutputListener(Consumer<String> listener) {
		this.outputListener = listener;
	}

	@Override
	public void write(int addr, int val) {
		if (addr == Const.IO_UART && outputListener != null) {
			outputListener.accept(String.valueOf((char) val));
		}
		super.write(addr, val);
	}
}
