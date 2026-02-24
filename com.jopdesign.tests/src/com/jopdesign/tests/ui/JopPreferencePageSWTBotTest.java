package com.jopdesign.tests.ui;

import static org.junit.Assert.*;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * SWTBot tests for the JOP Preference Page (Preferences > JOP).
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopPreferencePageSWTBotTest {

	private static SWTWorkbenchBot bot;

	@BeforeClass
	public static void initBot() {
		SWTBotPreferences.TIMEOUT = 15000;
		bot = new SWTWorkbenchBot();
		SWTBotTestUtil.closeWelcome(bot);
	}

	@After
	public void closeDialogs() {
		// Close any open preference dialogs
		for (SWTBotShell shell : bot.shells()) {
			if (shell.getText().contains("Preferences")) {
				shell.close();
			}
		}
	}

	@Test
	public void testPreferencePageExists() {
		openJopPreferencePage();

		// Verify the JOP preference page loaded — check for JOP Home field
		assertNotNull("JOP Home field should exist",
				bot.textWithLabel("JOP Home:"));

		bot.button("Cancel").click();
	}

	@Test
	public void testPreferencePageFields() {
		openJopPreferencePage();

		// Verify all expected fields exist
		assertNotNull("JOP Home field", bot.textWithLabel("JOP Home:"));
		assertNotNull("JDK 1.6 Home field", bot.textWithLabel("JDK 1.6 Home:"));
		assertNotNull("Default Serial Port field", bot.textWithLabel("Default Serial Port:"));
		assertNotNull("Default Board Target field", bot.textWithLabel("Default Board Target:"));
		assertNotNull("Microcode Defines field", bot.textWithLabel("Microcode Defines:"));
		assertNotNull("SBT Path field", bot.textWithLabel("SBT Path:"));
		assertNotNull("Quartus Install Directory field", bot.textWithLabel("Quartus Install Directory:"));
		assertNotNull("Vivado Install Directory field", bot.textWithLabel("Vivado Install Directory:"));

		bot.button("Cancel").click();
	}

	@Test
	public void testSetAndPersistJopHome() {
		String testPath = "/tmp/jop-test-home";

		// Set JOP Home via the preference page
		openJopPreferencePage();
		bot.textWithLabel("JOP Home:").setText(testPath);
		bot.button("Apply and Close").click();
		bot.sleep(500);

		// Verify persistence through the Eclipse preferences API
		String stored = org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE
				.getNode(com.jopdesign.core.JopCorePlugin.PLUGIN_ID)
				.get(com.jopdesign.core.preferences.JopPreferences.JOP_HOME, "");
		assertEquals("JOP Home should persist in preferences store",
				testPath, stored);

		// Clear it back directly via the preferences API
		org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE
				.getNode(com.jopdesign.core.JopCorePlugin.PLUGIN_ID)
				.put(com.jopdesign.core.preferences.JopPreferences.JOP_HOME, "");
	}

	@Test
	public void testRestoreDefaults() {
		// Set a non-default value
		openJopPreferencePage();
		bot.textWithLabel("Default Serial Port:").setText("/dev/ttyTEST");
		bot.button("Apply and Close").click();
		bot.sleep(500);

		// Reopen and restore defaults
		openJopPreferencePage();
		bot.button("Restore Defaults").click();

		// After restore, serial port should be back to the default: /dev/ttyUSB0
		String serialPort = bot.textWithLabel("Default Serial Port:").getText();
		assertEquals("Serial port should be restored to default",
				"/dev/ttyUSB0", serialPort);

		bot.button("Apply and Close").click();
	}

	// ---- Helpers ----

	private void openJopPreferencePage() {
		// Open preferences programmatically (menus unreliable in headless mode)
		org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
			org.eclipse.ui.dialogs.PreferencesUtil.createPreferenceDialogOn(
					org.eclipse.ui.PlatformUI.getWorkbench()
							.getActiveWorkbenchWindow().getShell(),
					"com.jopdesign.ui.preferences.jopPreferencePage",
					null, null).open();
		});
		bot.sleep(2000);
		SWTBotShell prefsShell = SWTBotTestUtil.findShellContaining(bot, "Preferences");
		assertNotNull("Preferences dialog should have opened", prefsShell);
		prefsShell.activate();
		// JOP page should already be selected, but ensure it
		try {
			bot.tree().getTreeItem("JOP").select();
		} catch (org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException e) {
			// Already on JOP page, no tree visible
		}
		bot.sleep(500);
	}
}
