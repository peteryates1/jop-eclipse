package com.jopdesign.ui.actions;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.deploy.JopDeployer;

/**
 * Handler for the "Download Application" command.
 * Sends the built .jop file to the board via serial port.
 */
public class DownloadAppHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IProject project = extractProject(HandlerUtil.getCurrentSelection(event));
		if (project == null) {
			throw new ExecutionException("No JOP project selected.");
		}

		Job job = new Job("Download JOP Application — " + project.getName()) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				DeployConsole console = DeployConsole.getInstance();
				console.clear();
				console.show();

				try {
					JopDeployer deployer = JopDeployer.forProject(project);
					File jopFile = deployer.findJopFile();

					if (jopFile == null) {
						console.println("ERROR: No .jop file found.");
						console.println("Build the Java application first (configure main class in JOP properties).");
						return new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
								"No .jop file found. Build the application first.");
					}

					console.println("Downloading " + jopFile.getName() + " to board...");
					console.println("");

					int exitCode = deployer.download(jopFile, console::println, monitor);

					if (exitCode == 0) {
						console.println("");
						console.println("=== Download completed successfully ===");
						return Status.OK_STATUS;
					} else {
						console.println("");
						console.println("=== Download FAILED (exit code " + exitCode + ") ===");
						return new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
								"Download failed. See console for details.");
					}
				} catch (CoreException e) {
					console.println("ERROR: " + e.getMessage());
					return e.getStatus();
				}
			}
		};

		job.setUser(true);
		job.schedule();
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
