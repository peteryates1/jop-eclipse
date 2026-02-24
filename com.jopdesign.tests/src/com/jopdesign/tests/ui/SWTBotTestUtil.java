package com.jopdesign.tests.ui;

import static org.junit.Assert.assertNotNull;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

import com.jopdesign.core.nature.JopNature;

/**
 * Shared SWTBot test utilities for JOP Eclipse plugin UI tests.
 */
public class SWTBotTestUtil {

	public static final String PROJECT_NAME = "JopTestProject";

	/**
	 * Creates a Java project with the given name.
	 */
	public static IProject createJavaProject(String name) throws CoreException {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (!project.exists()) {
			IProjectDescription desc = ResourcesPlugin.getWorkspace()
					.newProjectDescription(name);
			desc.setNatureIds(new String[] { "org.eclipse.jdt.core.javanature" });
			project.create(desc, new NullProgressMonitor());
			project.open(new NullProgressMonitor());
		}
		return project;
	}

	/**
	 * Creates a Java project with the default name and JOP nature.
	 */
	public static IProject createJavaProjectWithNature() throws CoreException {
		IProject project = createJavaProject(PROJECT_NAME);
		addJopNature(project);
		return project;
	}

	/**
	 * Deletes the project with the given name.
	 */
	public static void deleteProject(String name) throws CoreException {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (project.exists()) {
			project.delete(true, true, new NullProgressMonitor());
		}
	}

	/**
	 * Deletes the default test project.
	 */
	public static void deleteProject() throws CoreException {
		deleteProject(PROJECT_NAME);
	}

	/**
	 * Gets the default test project.
	 */
	public static IProject getProject() {
		return ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
	}

	/**
	 * Adds JOP nature to a project.
	 */
	public static void addJopNature(IProject project) throws CoreException {
		IProjectDescription desc = project.getDescription();
		String[] natures = desc.getNatureIds();
		for (String n : natures) {
			if (n.equals(JopNature.NATURE_ID)) return; // already has it
		}
		String[] newNatures = new String[natures.length + 1];
		System.arraycopy(natures, 0, newNatures, 0, natures.length);
		newNatures[natures.length] = JopNature.NATURE_ID;
		desc.setNatureIds(newNatures);
		project.setDescription(desc, new NullProgressMonitor());
	}

	/**
	 * Removes JOP nature from a project.
	 */
	public static void removeJopNature(IProject project) throws CoreException {
		IProjectDescription desc = project.getDescription();
		String[] natures = desc.getNatureIds();
		java.util.List<String> newNatures = new java.util.ArrayList<>();
		for (String n : natures) {
			if (!n.equals(JopNature.NATURE_ID)) {
				newNatures.add(n);
			}
		}
		desc.setNatureIds(newNatures.toArray(new String[0]));
		project.setDescription(desc, new NullProgressMonitor());
	}

	/**
	 * Creates a file in the project with the given content.
	 */
	public static IFile createFile(IProject project, String name, String content)
			throws CoreException {
		// Ensure parent folders exist
		String[] segments = name.split("/");
		if (segments.length > 1) {
			StringBuilder path = new StringBuilder();
			for (int i = 0; i < segments.length - 1; i++) {
				if (i > 0) path.append("/");
				path.append(segments[i]);
				IFolder folder = project.getFolder(path.toString());
				if (!folder.exists()) {
					folder.create(true, true, new NullProgressMonitor());
				}
			}
		}

		IFile file = project.getFile(name);
		if (file.exists()) {
			file.setContents(
					new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
					true, false, new NullProgressMonitor());
		} else {
			file.create(
					new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
					true, new NullProgressMonitor());
		}
		return file;
	}

	/**
	 * Selects the default project in Package/Project Explorer.
	 */
	public static SWTBotTreeItem selectProject(SWTWorkbenchBot bot) {
		return selectProject(bot, PROJECT_NAME);
	}

	/**
	 * Selects the named project in Package/Project Explorer.
	 */
	public static SWTBotTreeItem selectProject(SWTWorkbenchBot bot, String projectName) {
		String viewTitle = null;
		for (String title : new String[] { "Package Explorer", "Project Explorer" }) {
			try {
				bot.viewByTitle(title);
				viewTitle = title;
				break;
			} catch (WidgetNotFoundException e) {
				// try next
			}
		}
		if (viewTitle == null) {
			// Open Project Explorer programmatically (menus unreliable in headless mode)
			org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> {
				try {
					org.eclipse.ui.PlatformUI.getWorkbench()
							.getActiveWorkbenchWindow().getActivePage()
							.showView("org.eclipse.ui.navigator.ProjectExplorer");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			bot.sleep(1000);
			viewTitle = "Project Explorer";
		}
		return bot.viewByTitle(viewTitle).bot()
				.tree().getTreeItem(projectName).select();
	}

	/**
	 * Opens the project properties dialog programmatically.
	 * Context menus are unreliable in headless SWTBot test runners.
	 */
	public static void openProjectProperties(SWTWorkbenchBot bot) throws Exception {
		openProjectProperties(bot, getProject());
	}

	/**
	 * Opens the project properties dialog for the given project.
	 */
	public static void openProjectProperties(SWTWorkbenchBot bot, IProject project)
			throws Exception {
		org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> {
			org.eclipse.swt.widgets.Shell parentShell = org.eclipse.ui.PlatformUI
					.getWorkbench().getActiveWorkbenchWindow().getShell();
			org.eclipse.ui.internal.dialogs.PropertyDialog dialog =
					org.eclipse.ui.internal.dialogs.PropertyDialog
							.createDialogOn(parentShell, null, project);
			org.eclipse.swt.widgets.Display.getDefault().asyncExec(dialog::open);
		});
		bot.sleep(2000);
		SWTBotShell propsShell = findShellContaining(bot, "Properties");
		assertNotNull("Properties dialog should have opened", propsShell);
		propsShell.activate();
	}

	/**
	 * Checks if a context menu item exists on a tree item.
	 */
	public static boolean hasContextMenu(SWTBotTreeItem item, String menuLabel) {
		try {
			item.contextMenu(menuLabel);
			return true;
		} catch (WidgetNotFoundException e) {
			return false;
		}
	}

	/**
	 * Finds a shell whose title contains the given text.
	 */
	public static SWTBotShell findShellContaining(SWTWorkbenchBot bot, String titlePart) {
		for (SWTBotShell shell : bot.shells()) {
			if (shell.getText().contains(titlePart)) {
				return shell;
			}
		}
		return null;
	}

	/**
	 * Opens the Debug Configurations dialog programmatically.
	 */
	public static void openDebugConfigurations(SWTWorkbenchBot bot) {
		org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
			org.eclipse.swt.widgets.Shell shell = org.eclipse.ui.PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getShell();
			org.eclipse.debug.ui.DebugUITools.openLaunchConfigurationDialogOnGroup(
					shell,
					new org.eclipse.jface.viewers.StructuredSelection(),
					org.eclipse.debug.ui.IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP);
		});
		bot.sleep(2000);
		SWTBotShell shell = findShellContaining(bot, "Debug Configurations");
		if (shell == null) {
			shell = findShellContaining(bot, "Launch Configurations");
		}
		assertNotNull("Debug Configurations dialog should have opened", shell);
		shell.activate();
	}

	/**
	 * Opens the Run Configurations dialog programmatically.
	 */
	public static void openRunConfigurations(SWTWorkbenchBot bot) {
		org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
			org.eclipse.swt.widgets.Shell shell = org.eclipse.ui.PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getShell();
			org.eclipse.debug.ui.DebugUITools.openLaunchConfigurationDialogOnGroup(
					shell,
					new org.eclipse.jface.viewers.StructuredSelection(),
					org.eclipse.debug.ui.IDebugUIConstants.ID_RUN_LAUNCH_GROUP);
		});
		bot.sleep(2000);
		SWTBotShell shell = findShellContaining(bot, "Run Configurations");
		if (shell == null) {
			shell = findShellContaining(bot, "Launch Configurations");
		}
		assertNotNull("Run Configurations dialog should have opened", shell);
		shell.activate();
	}

	/**
	 * Creates a launch configuration of the given type.
	 */
	public static ILaunchConfigurationWorkingCopy createLaunchConfig(
			String typeId, String name) throws CoreException {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = manager.getLaunchConfigurationType(typeId);
		return type.newInstance(null, name);
	}

	/**
	 * Closes the Welcome tab if present (idempotent).
	 */
	public static void closeWelcome(SWTWorkbenchBot bot) {
		try {
			bot.viewByTitle("Welcome").close();
		} catch (WidgetNotFoundException e) {
			// no welcome view
		}
	}

	/**
	 * Deletes all launch configurations (cleanup).
	 */
	public static void deleteAllLaunchConfigs() throws CoreException {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		for (ILaunchConfiguration config : manager.getLaunchConfigurations()) {
			config.delete();
		}
	}

	/**
	 * Ensures we're in the Java perspective (has Package Explorer).
	 * Useful before tests that need to select projects in a navigator view.
	 */
	public static void ensureJavaPerspective() {
		org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> {
			try {
				org.eclipse.ui.IWorkbenchPage page = org.eclipse.ui.PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getActivePage();
				page.setPerspective(org.eclipse.ui.PlatformUI.getWorkbench()
						.getPerspectiveRegistry()
						.findPerspectiveWithId("org.eclipse.jdt.ui.JavaPerspective"));
			} catch (Exception e) {
				// If Java perspective not available, try Resource perspective
				try {
					org.eclipse.ui.IWorkbenchPage page = org.eclipse.ui.PlatformUI.getWorkbench()
							.getActiveWorkbenchWindow().getActivePage();
					page.setPerspective(org.eclipse.ui.PlatformUI.getWorkbench()
							.getPerspectiveRegistry()
							.findPerspectiveWithId("org.eclipse.ui.resourcePerspective"));
				} catch (Exception e2) {
					// ignore — fallback to whatever perspective is active
				}
			}
		});
	}

	private SWTBotTestUtil() {} // utility class
}
