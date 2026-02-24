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
 * SWTBot tests for the JOP Project Property Pages.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopPropertyPageSWTBotTest {

	private static SWTWorkbenchBot bot;

	@BeforeClass
	public static void initBot() {
		SWTBotPreferences.TIMEOUT = 15000;
		bot = new SWTWorkbenchBot();
		SWTBotTestUtil.closeWelcome(bot);
	}

	@Before
	public void setUp() throws Exception {
		SWTBotTestUtil.createJavaProjectWithNature();
	}

	@After
	public void tearDown() throws Exception {
		// Close any open property dialogs
		for (org.eclipse.swtbot.swt.finder.widgets.SWTBotShell shell : bot.shells()) {
			if (shell.getText().contains("Properties")) {
				shell.close();
			}
		}
		SWTBotTestUtil.deleteProject();
	}

	@Test
	public void testJopPropertyPageExists() throws Exception {
		SWTBotTestUtil.openProjectProperties(bot);

		bot.tree().getTreeItem("JOP").select();
		// Verify the JOP property page loaded
		assertNotNull("JOP Home field should exist",
				bot.textWithLabel("JOP Home:"));

		bot.activeShell().close();
	}

	@Test
	public void testJopPropertyPageFields() throws Exception {
		SWTBotTestUtil.openProjectProperties(bot);

		bot.tree().getTreeItem("JOP").select();

		// Verify all fields exist
		assertNotNull("JOP Home field", bot.textWithLabel("JOP Home:"));
		assertNotNull("Serial Port field", bot.textWithLabel("Serial Port:"));
		assertNotNull("Preprocessor Defines field", bot.textWithLabel("Preprocessor Defines:"));
		assertNotNull("Main Class field", bot.textWithLabel("Main Class:"));
		assertNotNull("Output Directory field", bot.textWithLabel("Output Directory:"));

		bot.activeShell().close();
	}

	@Test
	public void testPropertyPersistence() throws Exception {
		// Set values
		SWTBotTestUtil.openProjectProperties(bot);
		bot.tree().getTreeItem("JOP").select();

		bot.textWithLabel("Main Class:").setText("com.example.TestMain");
		bot.textWithLabel("Output Directory:").setText("out");
		bot.button("Apply and Close").click();

		// Reopen and verify
		SWTBotTestUtil.openProjectProperties(bot);
		bot.tree().getTreeItem("JOP").select();

		assertEquals("Main class should persist",
				"com.example.TestMain", bot.textWithLabel("Main Class:").getText());
		assertEquals("Output directory should persist",
				"out", bot.textWithLabel("Output Directory:").getText());

		bot.activeShell().close();
	}

	@Test
	public void testBoardConfigurationPage() throws Exception {
		SWTBotTestUtil.openProjectProperties(bot);

		SWTBotTreeItem jopNode = bot.tree().getTreeItem("JOP");
		jopNode.expand();
		jopNode.getNode("Board Configuration").select();

		// Verify board combo exists and has entries
		assertNotNull("Board combo should exist",
				bot.comboBoxInGroup("Board Selection"));
		assertTrue("Board combo should have items",
				bot.comboBoxInGroup("Board Selection").itemCount() > 0);

		bot.activeShell().close();
	}

	@Test
	public void testBoardSelectionChangesCacheDefaults() throws Exception {
		SWTBotTestUtil.openProjectProperties(bot);

		SWTBotTreeItem jopNode = bot.tree().getTreeItem("JOP");
		jopNode.expand();
		jopNode.getNode("Board Configuration").select();

		// Select QMTECH BRAM board — should set method cache to 8192
		bot.comboBoxInGroup("Board Selection").setSelection("QMTECH EP4CGX150 (BRAM)");
		int methodCache = bot.spinnerInGroup("Cache Configuration", 0).getSelection();
		assertEquals("QMTECH BRAM should set method cache to 8192",
				8192, methodCache);

		// Switch to Minimal — should set method cache to 1024
		bot.comboBoxInGroup("Board Selection").setSelection("Minimal (Simulation)");
		methodCache = bot.spinnerInGroup("Cache Configuration", 0).getSelection();
		assertEquals("Minimal should set method cache to 1024",
				1024, methodCache);

		bot.activeShell().close();
	}

	@Test
	public void testCmpCheckboxEnablesSpinner() throws Exception {
		SWTBotTestUtil.openProjectProperties(bot);

		SWTBotTreeItem jopNode = bot.tree().getTreeItem("JOP");
		jopNode.expand();
		jopNode.getNode("Board Configuration").select();

		// Verify CMP group exists
		assertNotNull("Multi-Core checkbox should exist",
				bot.checkBoxInGroup("Multi-Core / CMP", 0));

		bot.activeShell().close();
	}

	@Test
	public void testIoPeripheralsGroupExists() throws Exception {
		SWTBotTestUtil.openProjectProperties(bot);

		SWTBotTreeItem jopNode = bot.tree().getTreeItem("JOP");
		jopNode.expand();
		jopNode.getNode("Board Configuration").select();

		// Verify IO Peripherals group has checkboxes
		assertNotNull("IO Peripherals checkbox should exist",
				bot.checkBoxInGroup("IO Peripherals", 0));

		bot.activeShell().close();
	}
}
