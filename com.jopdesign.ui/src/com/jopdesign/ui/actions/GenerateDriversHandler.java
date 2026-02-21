package com.jopdesign.ui.actions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
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
import org.eclipse.ui.handlers.HandlerUtil;

import com.jopdesign.core.io.DriverStubGenerator;
import com.jopdesign.core.io.PeripheralDefinition;
import com.jopdesign.core.io.PeripheralRegistry;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;

/**
 * Handler for the "Generate IO Drivers" command.
 *
 * <p>Generates Java driver stub source files for all enabled IO peripherals
 * into the project's source folder. Also generates pin constraint templates.
 */
public class GenerateDriversHandler extends AbstractHandler {

	private static final ILog LOG = Platform.getLog(GenerateDriversHandler.class);
	private static final String DRIVER_PACKAGE = "jop.io";
	private static final String DRIVER_FOLDER = "src/jop/io";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (!(selection instanceof IStructuredSelection structured)) return null;

		Object element = structured.getFirstElement();
		IProject project = null;
		if (element instanceof IProject p) {
			project = p;
		} else if (element instanceof IAdaptable adaptable) {
			project = adaptable.getAdapter(IProject.class);
		}
		if (project == null) return null;

		final IProject proj = project;

		Job job = new Job("Generate JOP IO Drivers") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					Map<String, Integer> enabled = getEnabledPeripherals(proj);
					if (enabled.isEmpty()) {
						return new Status(IStatus.INFO,
								"com.jopdesign.ui",
								"No IO peripherals enabled. Configure peripherals in Board Configuration.");
					}

					monitor.beginTask("Generating IO drivers", enabled.size() + 1);

					// Generate driver sources
					Map<String, String> sources = DriverStubGenerator.generateAll(
							enabled, DRIVER_PACKAGE);

					// Create output folder
					IFolder folder = proj.getFolder(DRIVER_FOLDER);
					createFolders(folder, monitor);

					for (Map.Entry<String, String> entry : sources.entrySet()) {
						IFile file = folder.getFile(entry.getKey());
						InputStream content = new ByteArrayInputStream(
								entry.getValue().getBytes(StandardCharsets.UTF_8));
						if (file.exists()) {
							file.setContents(content, true, true, monitor);
						} else {
							file.create(content, true, monitor);
						}
						monitor.worked(1);
					}

					// Generate pin constraints
					String boardId = JopProjectPreferences.get(proj,
							JopPreferences.BOARD_ID, "");
					String synthTool = "";
					if (!boardId.isEmpty()) {
						var board = com.jopdesign.core.board.BoardRegistry.getBoard(boardId);
						if (board != null) {
							synthTool = board.synthTool();
						}
					}
					String constraints = DriverStubGenerator.generatePinConstraints(
							enabled, synthTool);
					String ext = "vivado".equals(synthTool) ? "xdc" : "qsf";
					IFile constraintFile = proj.getFile(
							"generated/io_pins." + ext);
					createFolders(proj.getFolder("generated"), monitor);
					InputStream constraintContent = new ByteArrayInputStream(
							constraints.getBytes(StandardCharsets.UTF_8));
					if (constraintFile.exists()) {
						constraintFile.setContents(constraintContent,
								true, true, monitor);
					} else {
						constraintFile.create(constraintContent, true, monitor);
					}
					monitor.worked(1);

					monitor.done();
					return Status.OK_STATUS;

				} catch (CoreException e) {
					LOG.error("Failed to generate IO drivers", e);
					return e.getStatus();
				}
			}
		};
		job.setRule(proj);
		job.schedule();

		return null;
	}

	private Map<String, Integer> getEnabledPeripherals(IProject project) {
		Map<String, Integer> result = new LinkedHashMap<>();
		String peripherals = JopProjectPreferences.get(project,
				JopPreferences.IO_PERIPHERALS, "");
		if (peripherals.isEmpty()) return result;

		for (String id : peripherals.split(",")) {
			id = id.trim();
			if (id.isEmpty()) continue;
			PeripheralDefinition p = PeripheralRegistry.getPeripheral(id);
			if (p == null || p.fixed()) continue;
			String slotVal = JopProjectPreferences.get(project,
					JopPreferences.IO_SLOT_PREFIX + id, "");
			int slot = p.defaultSlot();
			if (!slotVal.isEmpty()) {
				try {
					slot = Integer.parseInt(slotVal);
				} catch (NumberFormatException e) {
					// use default
				}
			}
			result.put(id, slot);
		}
		return result;
	}

	private void createFolders(IFolder folder, IProgressMonitor monitor)
			throws CoreException {
		if (!folder.exists()) {
			if (folder.getParent() instanceof IFolder parent && !parent.exists()) {
				createFolders(parent, monitor);
			}
			folder.create(true, true, monitor);
		}
	}
}
