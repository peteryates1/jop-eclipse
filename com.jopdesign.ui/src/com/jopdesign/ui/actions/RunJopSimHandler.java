package com.jopdesign.ui.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.sim.JopSimRunner;
import com.jopdesign.core.sim.SimResult;

/**
 * Handler for the "Run JopSim" command.
 * Runs the JOP Java-level simulator on the project's built .jop file.
 * Output is streamed to the "JOP Simulation" console.
 */
public class RunJopSimHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IProject project = extractProject(HandlerUtil.getCurrentSelection(event));
		if (project == null) {
			throw new ExecutionException("No JOP project selected.");
		}

		Job job = new Job("JopSim — " + project.getName()) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				SimConsole console = SimConsole.getInstance();
				console.clear();
				console.show();

				try {
					JopSimRunner runner = JopSimRunner.forProject(project);
					SimResult result = runner.run(console::println, monitor);

					console.println("");
					if (result.success()) {
						console.println("=== Simulation completed: " + result.oneLine() + " ===");
						return Status.OK_STATUS;
					} else {
						console.println("=== Simulation FAILED ===");
						return new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
								"JopSim failed. See console for details.");
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
