package com.jopdesign.ui.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.fpga.FpgaSynthesizer;
import com.jopdesign.core.fpga.SynthesisResult;

/**
 * Handler for the "Synthesize FPGA" command.
 *
 * <p>Runs the full FPGA synthesis pipeline (SpinalHDL + Quartus/Vivado)
 * as a background Job with progress reporting. Output is written to
 * a dedicated "JOP FPGA Synthesis" console.
 */
public class SynthesizeFpgaHandler extends AbstractHandler {

	private static final ILog LOG = Platform.getLog(SynthesizeFpgaHandler.class);
	private static final String CONSOLE_NAME = "JOP FPGA Synthesis";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		IProject project = extractProject(selection);

		if (project == null) {
			throw new ExecutionException("No JOP project selected.");
		}

		Job job = new Job("FPGA Synthesis — " + project.getName()) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				MessageConsole console = findOrCreateConsole();
				console.clearConsole();
				MessageConsoleStream stream = console.newMessageStream();

				// Show the console
				ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);

				try {
					stream.println("Starting FPGA synthesis for " + project.getName() + "...");
					stream.println();

					FpgaSynthesizer synth = FpgaSynthesizer.forProject(project);
					SynthesisResult result = synth.synthesize(monitor);

					stream.println(result.output());

					if (result.success()) {
						stream.println("=== FPGA synthesis completed successfully ===");
						return Status.OK_STATUS;
					} else {
						stream.println("=== FPGA synthesis FAILED ===");
						return new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
								"FPGA synthesis failed. See console for details.");
					}
				} catch (CoreException e) {
					try {
						stream.println("ERROR: " + e.getMessage());
					} catch (Exception ignored) {
						// stream may be closed
					}
					return e.getStatus();
				} finally {
					try {
						stream.close();
					} catch (Exception ignored) {
					}
				}
			}
		};

		job.setUser(true);
		job.schedule();
		return null;
	}

	private IProject extractProject(ISelection selection) {
		if (!(selection instanceof IStructuredSelection structured)) {
			return null;
		}
		Object element = structured.getFirstElement();
		if (element instanceof IProject p) {
			return p;
		}
		if (element instanceof IAdaptable adaptable) {
			return adaptable.getAdapter(IProject.class);
		}
		return null;
	}

	private MessageConsole findOrCreateConsole() {
		IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
		for (IConsole existing : manager.getConsoles()) {
			if (CONSOLE_NAME.equals(existing.getName()) && existing instanceof MessageConsole mc) {
				return mc;
			}
		}
		MessageConsole console = new MessageConsole(CONSOLE_NAME, null);
		manager.addConsoles(new IConsole[] { console });
		return console;
	}
}
