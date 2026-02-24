package com.jopdesign.tests.ui;

import static org.junit.Assert.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.jopdesign.ui.launch.JopLaunchDelegate;
import com.jopdesign.ui.launch.MicrocodeLaunchDelegate;

/**
 * SWTBot tests for JOP launch shortcuts (right-click > Run/Debug As).
 *
 * <p>Tests that launch shortcuts create appropriate configurations
 * programmatically. Full menu-based shortcuts are hard to test in headless
 * environments, so we focus on the programmatic API.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopLaunchShortcutSWTBotTest {

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
		SWTBotTestUtil.deleteAllLaunchConfigs();
	}

	@After
	public void tearDown() throws Exception {
		bot.closeAllEditors();
		// Terminate and clean up any launches
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		for (org.eclipse.debug.core.ILaunch launch : manager.getLaunches()) {
			if (!launch.isTerminated()) {
				for (org.eclipse.debug.core.model.IDebugTarget dt : launch.getDebugTargets()) {
					try { dt.terminate(); } catch (Exception e) { /* ignore */ }
				}
			}
			manager.removeLaunch(launch);
		}
		SWTBotTestUtil.deleteAllLaunchConfigs();
		SWTBotTestUtil.deleteProject();
	}

	@Test
	public void testMicrocodeShortcutCreatesConfig() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		IFile asmFile = SWTBotTestUtil.createFile(project, "hello.asm",
				"nop\nnop\nwait\n");

		// Invoke the microcode launch shortcut programmatically
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			try {
				com.jopdesign.ui.launch.MicrocodeLaunchShortcut shortcut =
						new com.jopdesign.ui.launch.MicrocodeLaunchShortcut();
				org.eclipse.jface.viewers.StructuredSelection selection =
						new org.eclipse.jface.viewers.StructuredSelection(asmFile);
				shortcut.launch(selection, ILaunchManager.DEBUG_MODE);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		bot.sleep(3000); // Let launch settle

		// Verify a microcode debug configuration was created
		ILaunchConfiguration[] configs = DebugPlugin.getDefault()
				.getLaunchManager().getLaunchConfigurations();
		boolean foundMicrocodeConfig = false;
		for (ILaunchConfiguration config : configs) {
			if (config.getType().getIdentifier()
					.equals("com.jopdesign.ui.launch.microcodeDebug")) {
				foundMicrocodeConfig = true;
				String file = config.getAttribute(
						MicrocodeLaunchDelegate.ATTR_MICROCODE_FILE, "");
				assertTrue("Config should reference the .asm file",
						file.contains("hello.asm"));
				break;
			}
		}
		assertTrue("Microcode debug config should have been created",
				foundMicrocodeConfig);
	}

	@Test
	public void testJopShortcutForJopFile() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		IFile jopFile = SWTBotTestUtil.createFile(project, "app.jop",
				"00000000\n");

		// Invoke the JOP launch shortcut programmatically
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			try {
				com.jopdesign.ui.launch.JopLaunchShortcut shortcut =
						new com.jopdesign.ui.launch.JopLaunchShortcut();
				org.eclipse.jface.viewers.StructuredSelection selection =
						new org.eclipse.jface.viewers.StructuredSelection(jopFile);
				shortcut.launch(selection, ILaunchManager.RUN_MODE);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		bot.sleep(3000);

		// Verify a JOP Application configuration was created with JopSim target
		ILaunchConfiguration[] configs = DebugPlugin.getDefault()
				.getLaunchManager().getLaunchConfigurations();
		boolean foundJopConfig = false;
		for (ILaunchConfiguration config : configs) {
			if (config.getType().getIdentifier()
					.equals("com.jopdesign.ui.launch.jopApplication")) {
				foundJopConfig = true;
				String targetType = config.getAttribute(
						JopLaunchDelegate.ATTR_TARGET_TYPE, "");
				assertEquals("Should be jopsim target for .jop files",
						JopLaunchDelegate.TARGET_JOPSIM, targetType);
				String jopFilePath = config.getAttribute(
						JopLaunchDelegate.ATTR_JOP_FILE, "");
				assertTrue("Config should reference the .jop file",
						jopFilePath.contains("app.jop"));
				break;
			}
		}
		assertTrue("JOP Application config should have been created",
				foundJopConfig);
	}

	@Test
	public void testJopShortcutForAsmFile() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		IFile asmFile = SWTBotTestUtil.createFile(project, "micro.asm",
				"nop\nwait\n");

		// The JopLaunchShortcut for .asm creates a simulator target config
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			try {
				com.jopdesign.ui.launch.JopLaunchShortcut shortcut =
						new com.jopdesign.ui.launch.JopLaunchShortcut();
				org.eclipse.jface.viewers.StructuredSelection selection =
						new org.eclipse.jface.viewers.StructuredSelection(asmFile);
				shortcut.launch(selection, ILaunchManager.DEBUG_MODE);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		bot.sleep(3000);

		// Verify config was created with simulator target
		ILaunchConfiguration[] configs = DebugPlugin.getDefault()
				.getLaunchManager().getLaunchConfigurations();
		boolean found = false;
		for (ILaunchConfiguration config : configs) {
			if (config.getType().getIdentifier()
					.equals("com.jopdesign.ui.launch.jopApplication")) {
				found = true;
				String targetType = config.getAttribute(
						JopLaunchDelegate.ATTR_TARGET_TYPE, "");
				assertEquals("Should be simulator target for .asm files",
						JopLaunchDelegate.TARGET_SIMULATOR, targetType);
				break;
			}
		}
		assertTrue("JOP Application config should have been created for .asm",
				found);
	}

	@Test
	public void testAsmFileCanBeOpenedInEditor() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		IFile asmFile = SWTBotTestUtil.createFile(project, "edittest.asm",
				"nop\nadd\nwait\n");

		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			try {
				IDE.openEditor(
						PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
						asmFile);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		bot.sleep(1000);

		assertNotNull("Editor should be open", bot.activeEditor());
		assertEquals("edittest.asm", bot.activeEditor().getTitle());
	}
}
