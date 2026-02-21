package com.jopdesign.tests.ui;

import static org.junit.Assert.*;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.jopdesign.core.nature.JopNature;

/**
 * SWTBot integration tests for the JOP Eclipse plugin suite.
 *
 * <p>Tests create a real Java project, toggle the JOP nature,
 * and exercise the board configuration property page.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopProjectSWTBotTest {

	private static SWTWorkbenchBot bot;
	private static final String PROJECT_NAME = "JopTestProject";

	@BeforeClass
	public static void initBot() {
		SWTBotPreferences.TIMEOUT = 15000; // 15s timeout for widget lookups
		bot = new SWTWorkbenchBot();
		// Close the Welcome tab if present
		try {
			bot.viewByTitle("Welcome").close();
		} catch (WidgetNotFoundException e) {
			// no welcome view — fine
		}
	}

	@Before
	public void setUp() throws Exception {
		createJavaProject();
	}

	@After
	public void tearDown() throws Exception {
		deleteProject();
	}

	@Test
	public void testToggleJopNature() throws Exception {
		IProject project = getProject();
		assertFalse("Project should not have JOP nature initially",
				project.hasNature(JopNature.NATURE_ID));

		// Add JOP nature programmatically
		addJopNature(project);
		assertTrue("Project should have JOP nature after toggle",
				project.hasNature(JopNature.NATURE_ID));

		// Remove it
		removeJopNature(project);
		assertFalse("Project should not have JOP nature after removal",
				project.hasNature(JopNature.NATURE_ID));
	}

	@Test
	public void testOpenProjectProperties() throws Exception {
		addJopNature(getProject());

		openProjectProperties();

		// Navigate to JOP page
		bot.tree().getTreeItem("JOP").select();
		// Verify the JOP property page loaded (it should have a JOP_HOME field)
		assertNotNull(bot.textWithLabel("JOP Home:"));

		bot.activeShell().close();
	}

	@Test
	public void testBoardConfigurationPage() throws Exception {
		addJopNature(getProject());

		openProjectProperties();

		// Navigate to Board Configuration sub-page
		SWTBotTreeItem jopNode = bot.tree().getTreeItem("JOP");
		jopNode.expand();
		jopNode.getNode("Board Configuration").select();

		// Verify board combo exists and has entries
		assertNotNull("Board combo should exist",
				bot.comboBoxInGroup("Board Selection"));
		assertTrue("Board combo should have items",
				bot.comboBoxInGroup("Board Selection").itemCount() > 0);

		// Select the minimal board
		bot.comboBoxInGroup("Board Selection").setSelection("Minimal (Simulation)");

		// Verify cache group has spinners
		assertNotNull(bot.spinnerInGroup("Cache Configuration", 0));

		// Verify CMP group exists
		assertNotNull(bot.checkBoxInGroup("Multi-Core / CMP", 0));

		// Verify IO Peripherals group exists
		assertNotNull(bot.checkBoxInGroup("IO Peripherals", 0));

		bot.activeShell().close();
	}

	@Test
	public void testBoardSelectionChangesDefaults() throws Exception {
		addJopNature(getProject());

		openProjectProperties();

		SWTBotTreeItem jopNode = bot.tree().getTreeItem("JOP");
		jopNode.expand();
		jopNode.getNode("Board Configuration").select();

		// Select QMTECH BRAM board (8192 method cache, 128 stack buffer)
		bot.comboBoxInGroup("Board Selection").setSelection(
				"QMTECH EP4CGX150 (BRAM)");

		// The method cache spinner (first spinner in Cache group) should be 8192
		int methodCache = bot.spinnerInGroup("Cache Configuration", 0)
				.getSelection();
		assertEquals("QMTECH BRAM should set method cache to 8192",
				8192, methodCache);

		// Now switch to Minimal board (1024 method cache)
		bot.comboBoxInGroup("Board Selection").setSelection(
				"Minimal (Simulation)");

		methodCache = bot.spinnerInGroup("Cache Configuration", 0)
				.getSelection();
		assertEquals("Minimal should set method cache to 1024",
				1024, methodCache);

		bot.activeShell().close();
	}

	@Test
	public void testJopContextMenu() throws Exception {
		addJopNature(getProject());

		SWTBotTreeItem projectNode = selectProject();

		// The JOP submenu should be visible for JOP projects
		assertTrue("JOP submenu should appear in context menu",
				hasContextMenu(projectNode, "JOP"));
	}

	// ---- Helpers ----

	private void openProjectProperties() throws Exception {
		final IProject project = getProject();

		// Open properties dialog programmatically — context menus are unreliable
		// in headless SWTBot test runners
		org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> {
			org.eclipse.swt.widgets.Shell parentShell = org.eclipse.ui.PlatformUI
					.getWorkbench().getActiveWorkbenchWindow().getShell();
			org.eclipse.ui.internal.dialogs.PropertyDialog dialog =
					org.eclipse.ui.internal.dialogs.PropertyDialog
							.createDialogOn(parentShell, null, project);
			// open() blocks, so run it async
			org.eclipse.swt.widgets.Display.getDefault().asyncExec(dialog::open);
		});

		bot.sleep(2000);

		// Find the properties shell
		SWTBotShell propsShell = null;
		for (SWTBotShell shell : bot.shells()) {
			if (shell.getText().contains("Properties")) {
				propsShell = shell;
				break;
			}
		}
		assertNotNull("Properties dialog should have opened", propsShell);
		propsShell.activate();
	}

	private void createJavaProject() throws CoreException {
		IProject project = ResourcesPlugin.getWorkspace().getRoot()
				.getProject(PROJECT_NAME);
		if (!project.exists()) {
			IProjectDescription desc = ResourcesPlugin.getWorkspace()
					.newProjectDescription(PROJECT_NAME);
			desc.setNatureIds(new String[]{"org.eclipse.jdt.core.javanature"});
			project.create(desc, new NullProgressMonitor());
			project.open(new NullProgressMonitor());
		}
	}

	private void deleteProject() throws CoreException {
		IProject project = ResourcesPlugin.getWorkspace().getRoot()
				.getProject(PROJECT_NAME);
		if (project.exists()) {
			project.delete(true, true, new NullProgressMonitor());
		}
	}

	private IProject getProject() {
		return ResourcesPlugin.getWorkspace().getRoot()
				.getProject(PROJECT_NAME);
	}

	private void addJopNature(IProject project) throws CoreException {
		IProjectDescription desc = project.getDescription();
		String[] natures = desc.getNatureIds();
		String[] newNatures = new String[natures.length + 1];
		System.arraycopy(natures, 0, newNatures, 0, natures.length);
		newNatures[natures.length] = JopNature.NATURE_ID;
		desc.setNatureIds(newNatures);
		project.setDescription(desc, new NullProgressMonitor());
	}

	private void removeJopNature(IProject project) throws CoreException {
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

	private SWTBotTreeItem selectProject() {
		// Try Package Explorer first, then Project Explorer
		String viewTitle = null;
		for (String title : new String[]{"Package Explorer", "Project Explorer"}) {
			try {
				bot.viewByTitle(title);
				viewTitle = title;
				break;
			} catch (WidgetNotFoundException e) {
				// try next
			}
		}
		if (viewTitle == null) {
			// Open Project Explorer via command if no navigator is visible
			bot.menu("Window").menu("Show View").menu("Other...").click();
			SWTBotShell viewShell = bot.shell("Show View");
			viewShell.activate();
			bot.text().setText("Project Explorer");
			bot.tree().getTreeItem("General").expand()
					.getNode("Project Explorer").select();
			bot.button("Open").click();
			viewTitle = "Project Explorer";
		}
		return bot.viewByTitle(viewTitle).bot()
				.tree().getTreeItem(PROJECT_NAME).select();
	}

	private boolean hasContextMenu(SWTBotTreeItem item, String menuLabel) {
		try {
			item.contextMenu(menuLabel);
			return true;
		} catch (WidgetNotFoundException e) {
			return false;
		}
	}
}
