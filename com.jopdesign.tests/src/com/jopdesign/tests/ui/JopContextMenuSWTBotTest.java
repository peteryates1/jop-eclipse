package com.jopdesign.tests.ui;

import static org.junit.Assert.*;

import org.eclipse.core.resources.IProject;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * SWTBot tests for JOP context menu commands.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopContextMenuSWTBotTest {

	private static SWTWorkbenchBot bot;

	@BeforeClass
	public static void initBot() {
		SWTBotPreferences.TIMEOUT = 15000;
		bot = new SWTWorkbenchBot();
		SWTBotTestUtil.closeWelcome(bot);
		// Ensure we're in a perspective with Package Explorer
		SWTBotTestUtil.ensureJavaPerspective();
	}

	@Before
	public void setUp() throws Exception {
		SWTBotTestUtil.createJavaProject(SWTBotTestUtil.PROJECT_NAME);
	}

	@After
	public void tearDown() throws Exception {
		SWTBotTestUtil.deleteProject();
	}

	@Test
	public void testJopSubmenuAppearsForJopProjects() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		SWTBotTestUtil.addJopNature(project);

		SWTBotTreeItem projectNode = SWTBotTestUtil.selectProject(bot);
		assertTrue("JOP submenu should appear for JOP-nature project",
				SWTBotTestUtil.hasContextMenu(projectNode, "JOP"));
	}

	@Test
	public void testJopSubmenuNotShownForNonJopProjects() throws Exception {
		// Project without JOP nature
		SWTBotTreeItem projectNode = SWTBotTestUtil.selectProject(bot);
		assertFalse("JOP submenu should NOT appear for non-JOP project",
				SWTBotTestUtil.hasContextMenu(projectNode, "JOP"));
	}

	@Test
	public void testAllContextMenuCommandsPresent() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		SWTBotTestUtil.addJopNature(project);

		SWTBotTreeItem projectNode = SWTBotTestUtil.selectProject(bot);
		org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu jopMenu =
				projectNode.contextMenu("JOP");

		// Verify all 7 commands are present in the JOP submenu
		assertNotNull("Synthesize FPGA", jopMenu.menu("Synthesize FPGA"));
		assertNotNull("Program FPGA", jopMenu.menu("Program FPGA"));
		assertNotNull("Download Application", jopMenu.menu("Download Application"));
		assertNotNull("Start Monitor", jopMenu.menu("Start Monitor"));
		assertNotNull("Run JopSim", jopMenu.menu("Run JopSim"));
		assertNotNull("Run RTL Simulation", jopMenu.menu("Run RTL Simulation"));
		assertNotNull("Generate IO Drivers", jopMenu.menu("Generate IO Drivers"));
	}

	@Test
	public void testConfigureJopNatureToggle() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		assertFalse("Project should not have JOP nature initially",
				project.hasNature(com.jopdesign.core.nature.JopNature.NATURE_ID));

		// Add JOP nature
		SWTBotTestUtil.addJopNature(project);
		assertTrue("Project should have JOP nature after add",
				project.hasNature(com.jopdesign.core.nature.JopNature.NATURE_ID));

		// Remove JOP nature
		SWTBotTestUtil.removeJopNature(project);
		assertFalse("Project should not have JOP nature after removal",
				project.hasNature(com.jopdesign.core.nature.JopNature.NATURE_ID));
	}
}
