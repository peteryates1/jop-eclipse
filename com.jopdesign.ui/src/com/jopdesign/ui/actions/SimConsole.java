package com.jopdesign.ui.actions;

import java.io.IOException;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

/**
 * Shared "JOP Simulation" console for JopSim and RTL simulation output.
 */
final class SimConsole {

	private static final String CONSOLE_NAME = "JOP Simulation";

	private final MessageConsole console;
	private MessageConsoleStream stream;

	private static SimConsole instance;

	private SimConsole(MessageConsole console) {
		this.console = console;
		this.stream = console.newMessageStream();
	}

	static synchronized SimConsole getInstance() {
		if (instance != null && !instance.isDisposed()) {
			return instance;
		}

		IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
		for (IConsole existing : manager.getConsoles()) {
			if (CONSOLE_NAME.equals(existing.getName()) && existing instanceof MessageConsole mc) {
				instance = new SimConsole(mc);
				return instance;
			}
		}

		MessageConsole mc = new MessageConsole(CONSOLE_NAME, null);
		manager.addConsoles(new IConsole[] { mc });
		instance = new SimConsole(mc);
		return instance;
	}

	void println(String message) {
		try {
			if (stream.isClosed()) {
				stream = console.newMessageStream();
			}
			stream.println(message);
		} catch (Exception e) {
			// Console may have been disposed
		}
	}

	void clear() {
		console.clearConsole();
		try {
			if (!stream.isClosed()) {
				stream.close();
			}
		} catch (IOException ignored) {
		}
		stream = console.newMessageStream();
	}

	void show() {
		ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
	}

	private boolean isDisposed() {
		try {
			IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
			for (IConsole c : manager.getConsoles()) {
				if (c == console) return false;
			}
			return true;
		} catch (Exception e) {
			return true;
		}
	}
}
