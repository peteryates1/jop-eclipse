package com.jopdesign.ui.actions;

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
import com.jopdesign.core.fpga.FpgaProgrammer;
import com.jopdesign.core.fpga.SynthesisResult;

/**
 * Handler for the "Program FPGA" command.
 * Programs the FPGA with the synthesized bitstream via JTAG.
 */
public class ProgramFpgaHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IProject project = extractProject(HandlerUtil.getCurrentSelection(event));
		if (project == null) {
			throw new ExecutionException("No JOP project selected.");
		}

		Job job = new Job("Program FPGA — " + project.getName()) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				DeployConsole console = DeployConsole.getInstance();
				console.clear();
				console.show();

				try {
					console.println("Programming FPGA for " + project.getName() + "...");
					console.println("");

					FpgaProgrammer programmer = FpgaProgrammer.forProject(project);
					SynthesisResult result = programmer.program(monitor);

					console.println(result.output());

					if (result.success()) {
						console.println("=== FPGA programmed successfully ===");
						return Status.OK_STATUS;
					} else {
						console.println("=== FPGA programming FAILED ===");
						return new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
								"FPGA programming failed. See console for details.");
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
