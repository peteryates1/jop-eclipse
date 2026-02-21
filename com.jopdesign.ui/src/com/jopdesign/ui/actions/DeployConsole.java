package com.jopdesign.ui.actions;

import java.io.IOException;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

/**
 * Shared "JOP Deploy" console used by all deploy-related handlers.
 *
 * <p>Provides convenience methods for printing, clearing, showing,
 * and registering a terminate action for long-running operations
 * like the UART monitor.
 */
final class DeployConsole {

	private static final String CONSOLE_NAME = "JOP Deploy";

	private final MessageConsole console;
	private MessageConsoleStream stream;
	private volatile Runnable terminateAction;

	private static DeployConsole instance;

	private DeployConsole(MessageConsole console) {
		this.console = console;
		this.stream = console.newMessageStream();
	}

	/**
	 * Get or create the singleton deploy console.
	 */
	static synchronized DeployConsole getInstance() {
		if (instance != null && !instance.isDisposed()) {
			return instance;
		}

		IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
		for (IConsole existing : manager.getConsoles()) {
			if (CONSOLE_NAME.equals(existing.getName()) && existing instanceof MessageConsole mc) {
				instance = new DeployConsole(mc);
				return instance;
			}
		}

		MessageConsole mc = new MessageConsole(CONSOLE_NAME, null);
		manager.addConsoles(new IConsole[] { mc });
		instance = new DeployConsole(mc);
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
		// Stop any running terminate action
		if (terminateAction != null) {
			terminateAction.run();
			terminateAction = null;
		}
		console.clearConsole();
		// Get a fresh stream after clearing
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

	/**
	 * Register a runnable to be called when the user wants to stop the
	 * current operation (e.g., kill the monitor process).
	 */
	void setTerminateAction(Runnable action) {
		this.terminateAction = action;
	}

	private boolean isDisposed() {
		try {
			// Check if the console is still registered
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
