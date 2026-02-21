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
import com.jopdesign.core.sim.RtlSimRunner;

/**
 * Handler for the "Run RTL Simulation" command.
 * Runs a SpinalHDL/Verilator RTL simulation via SBT.
 * Defaults to JopCoreBramSim; the simulation class can be changed
 * in the USE_SIMULATOR preference.
 */
public class RunRtlSimHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IProject project = extractProject(HandlerUtil.getCurrentSelection(event));
		if (project == null) {
			throw new ExecutionException("No JOP project selected.");
		}

		Job job = new Job("RTL Simulation — " + project.getName()) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				SimConsole console = SimConsole.getInstance();
				console.clear();
				console.show();

				try {
					RtlSimRunner runner = RtlSimRunner.forProject(project);
					// Default to BRAM simulation
					int exitCode = runner.run(console::println, monitor);

					console.println("");
					if (exitCode == 0) {
						console.println("=== RTL simulation completed successfully ===");
						return Status.OK_STATUS;
					} else {
						console.println("=== RTL simulation FAILED (exit code "
								+ exitCode + ") ===");
						return new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
								"RTL simulation failed. See console for details.");
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
