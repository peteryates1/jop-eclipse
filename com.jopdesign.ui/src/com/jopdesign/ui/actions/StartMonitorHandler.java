package com.jopdesign.ui.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import com.jopdesign.core.deploy.JopDeployer;

/**
 * Handler for the "Start Monitor" command.
 * Opens a UART monitor that streams serial output from the JOP board
 * to the "JOP Deploy" console. The monitor runs until explicitly stopped
 * (via the console terminate button).
 */
public class StartMonitorHandler extends AbstractHandler {

	private static final ILog LOG = Platform.getLog(StartMonitorHandler.class);

	/** Currently running monitor process, or null. */
	private volatile Process monitorProcess;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IProject project = extractProject(HandlerUtil.getCurrentSelection(event));
		if (project == null) {
			throw new ExecutionException("No JOP project selected.");
		}

		// Stop any existing monitor
		if (monitorProcess != null && monitorProcess.isAlive()) {
			monitorProcess.destroyForcibly();
			monitorProcess = null;
		}

		DeployConsole console = DeployConsole.getInstance();
		console.clear();
		console.show();

		try {
			JopDeployer deployer = JopDeployer.forProject(project);
			monitorProcess = deployer.startMonitor(console::println);

			console.setTerminateAction(() -> {
				if (monitorProcess != null && monitorProcess.isAlive()) {
					monitorProcess.destroyForcibly();
					console.println("");
					console.println("--- Monitor stopped ---");
				}
				monitorProcess = null;
			});

		} catch (CoreException e) {
			console.println("ERROR: " + e.getMessage());
			throw new ExecutionException("Failed to start monitor", e);
		}

		return null;
	}

	private IProject extractProject(ISelection selection) {
		if (!(selection instanceof IStructuredSelection structured)) return null;
		Object element = structured.getFirstElement();
		if (element instanceof IProject p) return p;
		if (element instanceof IAdaptable a) return a.getAdapter(IProject.class);
		return null;
	}
}
