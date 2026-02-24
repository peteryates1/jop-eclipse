package com.jopdesign.tests.ui;

import static org.junit.Assert.*;

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

/**
 * SWTBot tests for the JOP Launch Configuration Tab (JOP Application and Microcode Debug).
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopLaunchTabSWTBotTest {

	private static SWTWorkbenchBot bot;

	@BeforeClass
	public static void initBot() {
		SWTBotPreferences.TIMEOUT = 15000;
		bot = new SWTWorkbenchBot();
		SWTBotTestUtil.closeWelcome(bot);
	}

	@Before
	public void setUp() throws Exception {
		SWTBotTestUtil.deleteAllLaunchConfigs();
	}

	@After
	public void tearDown() throws Exception {
		// Close any debug/run config dialogs
		for (SWTBotShell shell : bot.shells()) {
			String title = shell.getText();
			if (title.contains("Debug Configurations") || title.contains("Run Configurations")) {
				shell.close();
			}
		}
		SWTBotTestUtil.deleteAllLaunchConfigs();
	}

	@Test
	public void testJopApplicationConfigTypeExists() {
		SWTBotTestUtil.openDebugConfigurations(bot);

		// Expand "JOP Application" config type in the tree
		SWTBotTreeItem jopApp = bot.tree().getTreeItem("JOP Application");
		assertNotNull("JOP Application config type should exist", jopApp);

		bot.button("Close").click();
	}

	@Test
	public void testMicrocodeDebugConfigTypeExists() {
		SWTBotTestUtil.openDebugConfigurations(bot);

		SWTBotTreeItem mcDebug = bot.tree().getTreeItem("JOP Microcode Debug");
		assertNotNull("JOP Microcode Debug config type should exist", mcDebug);

		bot.button("Close").click();
	}

	@Test
	public void testCreateNewJopApplicationConfig() {
		SWTBotTestUtil.openDebugConfigurations(bot);

		// Double-click to create new config
		bot.tree().getTreeItem("JOP Application").doubleClick();
		bot.sleep(1000);

		// Verify target type combo is present with all options
		assertNotNull("Target type combo should exist", bot.comboBox());
		String[] items = bot.comboBox().items();
		assertEquals("Should have 5 target types", 5, items.length);
		assertEquals("JOP Simulator", items[0]);
		assertEquals("JOP Bytecode Simulator", items[1]);
		assertEquals("JOP RTL Simulation", items[2]);
		assertEquals("JOP FPGA", items[3]);
		assertEquals("Dummy (testing)", items[4]);

		bot.button("Close").click();
		// If prompted to save, dismiss
		dismissSaveDialog();
	}

	@Test
	public void testSimulatorTargetFields() {
		SWTBotTestUtil.openDebugConfigurations(bot);
		bot.tree().getTreeItem("JOP Application").doubleClick();
		bot.sleep(1000);

		// Default is "JOP Simulator" (index 0)
		bot.comboBox().setSelection("JOP Simulator");
		bot.sleep(500);

		// Microcode file, initial SP, and memory size should be enabled
		assertTrue("Microcode file should be enabled",
				bot.textWithLabel("Microcode file:").isEnabled());
		assertTrue("Initial SP spinner should be enabled",
				bot.spinnerWithLabel("Initial stack pointer:").isEnabled());
		assertTrue("Memory size spinner should be enabled",
				bot.spinnerWithLabel("Memory size (words):").isEnabled());

		// JopSim fields should be disabled
		assertFalse("JOP binary file should be disabled",
				bot.textWithLabel("JOP binary file (.jop):").isEnabled());
		assertFalse("Link file should be disabled",
				bot.textWithLabel("Link file (.link.txt):").isEnabled());

		// RTL sim fields should be disabled
		assertFalse("SBT project dir should be disabled",
				bot.textWithLabel("SBT project directory:").isEnabled());
		assertFalse("SBT executable should be disabled",
				bot.textWithLabel("SBT executable:").isEnabled());
		assertFalse("Debug port should be disabled",
				bot.spinnerWithLabel("Debug port:").isEnabled());

		// FPGA fields should be disabled
		assertFalse("Serial port should be disabled",
				bot.textWithLabel("Serial port:").isEnabled());
		assertFalse("Baud rate should be disabled",
				bot.spinnerWithLabel("Baud rate:").isEnabled());

		bot.button("Close").click();
		dismissSaveDialog();
	}

	@Test
	public void testJopSimTargetFields() {
		SWTBotTestUtil.openDebugConfigurations(bot);
		bot.tree().getTreeItem("JOP Application").doubleClick();
		bot.sleep(1000);

		bot.comboBox().setSelection("JOP Bytecode Simulator");
		bot.sleep(500);

		// JopSim fields should be enabled
		assertTrue("JOP binary file should be enabled",
				bot.textWithLabel("JOP binary file (.jop):").isEnabled());
		assertTrue("Link file should be enabled",
				bot.textWithLabel("Link file (.link.txt):").isEnabled());

		// Simulator-specific fields should be disabled
		assertFalse("Initial SP should be disabled",
				bot.spinnerWithLabel("Initial stack pointer:").isEnabled());
		assertFalse("Memory size should be disabled",
				bot.spinnerWithLabel("Memory size (words):").isEnabled());
		assertFalse("Microcode file should be disabled",
				bot.textWithLabel("Microcode file:").isEnabled());

		bot.button("Close").click();
		dismissSaveDialog();
	}

	@Test
	public void testRtlSimTargetFields() {
		SWTBotTestUtil.openDebugConfigurations(bot);
		bot.tree().getTreeItem("JOP Application").doubleClick();
		bot.sleep(1000);

		bot.comboBox().setSelection("JOP RTL Simulation");
		bot.sleep(500);

		// RTL sim fields should be enabled
		assertTrue("SBT project dir should be enabled",
				bot.textWithLabel("SBT project directory:").isEnabled());
		assertTrue("SBT executable should be enabled",
				bot.textWithLabel("SBT executable:").isEnabled());
		assertTrue("Debug port should be enabled",
				bot.spinnerWithLabel("Debug port:").isEnabled());
		// Microcode file is also enabled for RTL sim (for source mapping)
		assertTrue("Microcode file should be enabled for RTL sim",
				bot.textWithLabel("Microcode file:").isEnabled());

		// JopSim and FPGA fields should be disabled
		assertFalse("JOP binary file should be disabled",
				bot.textWithLabel("JOP binary file (.jop):").isEnabled());
		assertFalse("Serial port should be disabled",
				bot.textWithLabel("Serial port:").isEnabled());

		bot.button("Close").click();
		dismissSaveDialog();
	}

	@Test
	public void testFpgaTargetFields() {
		SWTBotTestUtil.openDebugConfigurations(bot);
		bot.tree().getTreeItem("JOP Application").doubleClick();
		bot.sleep(1000);

		bot.comboBox().setSelection("JOP FPGA");
		bot.sleep(500);

		// FPGA fields should be enabled
		assertTrue("Serial port should be enabled",
				bot.textWithLabel("Serial port:").isEnabled());
		assertTrue("Baud rate should be enabled",
				bot.spinnerWithLabel("Baud rate:").isEnabled());
		// Microcode file is also enabled for FPGA (for source mapping)
		assertTrue("Microcode file should be enabled for FPGA",
				bot.textWithLabel("Microcode file:").isEnabled());

		// JopSim and RTL sim fields should be disabled
		assertFalse("JOP binary file should be disabled",
				bot.textWithLabel("JOP binary file (.jop):").isEnabled());
		assertFalse("SBT project dir should be disabled",
				bot.textWithLabel("SBT project directory:").isEnabled());

		bot.button("Close").click();
		dismissSaveDialog();
	}

	@Test
	public void testDummyTargetFieldsAllDisabled() {
		SWTBotTestUtil.openDebugConfigurations(bot);
		bot.tree().getTreeItem("JOP Application").doubleClick();
		bot.sleep(1000);

		bot.comboBox().setSelection("Dummy (testing)");
		bot.sleep(500);

		// For Dummy target, most fields should be disabled
		assertFalse("Microcode file should be disabled",
				bot.textWithLabel("Microcode file:").isEnabled());
		assertFalse("Initial SP should be disabled",
				bot.spinnerWithLabel("Initial stack pointer:").isEnabled());
		assertFalse("Memory size should be disabled",
				bot.spinnerWithLabel("Memory size (words):").isEnabled());
		assertFalse("JOP binary file should be disabled",
				bot.textWithLabel("JOP binary file (.jop):").isEnabled());
		assertFalse("SBT project dir should be disabled",
				bot.textWithLabel("SBT project directory:").isEnabled());
		assertFalse("Serial port should be disabled",
				bot.textWithLabel("Serial port:").isEnabled());

		bot.button("Close").click();
		dismissSaveDialog();
	}

	@Test
	public void testMicrocodeDebugTab() {
		SWTBotTestUtil.openDebugConfigurations(bot);
		bot.tree().getTreeItem("JOP Microcode Debug").doubleClick();
		bot.sleep(1000);

		// Microcode debug tab should have microcode file, initial SP, memory size
		assertNotNull("Microcode file field should exist",
				bot.textWithLabel("Microcode file:"));
		assertNotNull("Initial SP spinner should exist",
				bot.spinnerWithLabel("Initial stack pointer:"));
		assertNotNull("Memory size spinner should exist",
				bot.spinnerWithLabel("Memory size (words):"));

		bot.button("Close").click();
		dismissSaveDialog();
	}

	@Test
	public void testDefaultValues() {
		SWTBotTestUtil.openDebugConfigurations(bot);
		bot.tree().getTreeItem("JOP Application").doubleClick();
		bot.sleep(1000);

		// Check defaults
		assertEquals("Default target should be JOP Simulator",
				"JOP Simulator", bot.comboBox().getText());
		assertEquals("Default initial SP should be 64",
				64, bot.spinnerWithLabel("Initial stack pointer:").getSelection());
		assertEquals("Default memory size should be 1024",
				1024, bot.spinnerWithLabel("Memory size (words):").getSelection());
		assertEquals("Default debug port should be 4567",
				4567, bot.spinnerWithLabel("Debug port:").getSelection());
		assertEquals("Default baud rate should be 1000000",
				1000000, bot.spinnerWithLabel("Baud rate:").getSelection());

		bot.button("Close").click();
		dismissSaveDialog();
	}

	// ---- Helpers ----

	private void dismissSaveDialog() {
		try {
			SWTBotShell saveShell = bot.shell("Save Resource");
			saveShell.activate();
			bot.button("Don't Save").click();
		} catch (WidgetNotFoundException e) {
			// No save dialog, OK
		}
		try {
			SWTBotShell saveShell = bot.shell("Debug Configurations");
			saveShell.close();
		} catch (WidgetNotFoundException e) {
			// Already closed
		}
	}
}
