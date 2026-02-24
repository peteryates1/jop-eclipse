package com.jopdesign.tests.ui;

import static org.junit.Assert.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.jopdesign.ui.launch.JopLaunchDelegate;

/**
 * SWTBot tests for JOP debug sessions using the Simulator target.
 * Tests stepping, breakpoints, and register updates with a simple microcode program.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopSimDebugSWTBotTest {

	private static SWTWorkbenchBot bot;
	private ILaunch currentLaunch;

	// Simple microcode program: nop, nop, nop, wait
	private static final String MICROCODE_PROGRAM =
			"nop\n" +
			"nop\n" +
			"nop\n" +
			"wait\n";

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
		switchToJopDebugPerspective();
	}

	@After
	public void tearDown() throws Exception {
		if (currentLaunch != null && !currentLaunch.isTerminated()) {
			for (IDebugTarget dt : currentLaunch.getDebugTargets()) {
				try {
					dt.terminate();
				} catch (Exception e) {
					// Ignore
				}
			}
		}
		SWTBotTestUtil.deleteAllLaunchConfigs();
		bot.closeAllEditors();
		SWTBotTestUtil.deleteProject();
	}

	@Test
	public void testSimulatorDebugLaunch() throws Exception {
		IFile asmFile = createMicrocodeFile("test.asm", MICROCODE_PROGRAM);
		currentLaunch = launchSimulatorDebug("SimTest1", asmFile);

		waitForDebugTarget();

		IDebugTarget target = currentLaunch.getDebugTargets()[0];
		assertNotNull("Debug target should exist", target);
		assertFalse("Debug target should not be terminated", target.isTerminated());
	}

	@Test
	public void testSimulatorThreadSuspendedOnStart() throws Exception {
		IFile asmFile = createMicrocodeFile("test2.asm", MICROCODE_PROGRAM);
		currentLaunch = launchSimulatorDebug("SimTest2", asmFile);

		waitForDebugThread();

		IThread thread = currentLaunch.getDebugTargets()[0].getThreads()[0];
		assertTrue("Thread should be suspended initially", thread.isSuspended());
	}

	@Test
	public void testStepOver() throws Exception {
		IFile asmFile = createMicrocodeFile("test3.asm", MICROCODE_PROGRAM);
		currentLaunch = launchSimulatorDebug("SimTest3", asmFile);

		waitForDebugThread();

		IDebugTarget target = currentLaunch.getDebugTargets()[0];
		IThread thread = target.getThreads()[0];
		assertTrue("Thread should be suspended", thread.isSuspended());

		// Step Over
		thread.stepOver();

		// Wait for thread to re-suspend after step
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				return thread.isSuspended();
			}

			@Override
			public String getFailureMessage() {
				return "Thread did not re-suspend after step over";
			}
		});

		assertTrue("Thread should be suspended after step", thread.isSuspended());
	}

	@Test
	public void testMultipleSteps() throws Exception {
		IFile asmFile = createMicrocodeFile("test4.asm", MICROCODE_PROGRAM);
		currentLaunch = launchSimulatorDebug("SimTest4", asmFile);

		waitForDebugThread();

		IThread thread = currentLaunch.getDebugTargets()[0].getThreads()[0];

		// Step multiple times
		for (int i = 0; i < 3; i++) {
			assertTrue("Thread should be suspended before step " + i, thread.isSuspended());
			thread.stepOver();

			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() throws Exception {
					return thread.isSuspended();
				}

				@Override
				public String getFailureMessage() {
					return "Thread did not re-suspend after step";
				}
			});
		}

		assertTrue("Thread should be suspended after all steps", thread.isSuspended());
	}

	@Test
	public void testTerminateSimulatorSession() throws Exception {
		IFile asmFile = createMicrocodeFile("test5.asm", MICROCODE_PROGRAM);
		currentLaunch = launchSimulatorDebug("SimTest5", asmFile);

		waitForDebugTarget();

		IDebugTarget target = currentLaunch.getDebugTargets()[0];
		target.terminate();

		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				return target.isTerminated();
			}

			@Override
			public String getFailureMessage() {
				return "Debug target did not terminate";
			}
		});

		assertTrue("Should be terminated", target.isTerminated());
	}

	// ---- Helpers ----

	private IFile createMicrocodeFile(String name, String content) throws CoreException {
		IProject project = SWTBotTestUtil.getProject();
		return SWTBotTestUtil.createFile(project, name, content);
	}

	private ILaunch launchSimulatorDebug(String configName, IFile asmFile) throws CoreException {
		ILaunchConfigurationWorkingCopy wc = SWTBotTestUtil.createLaunchConfig(
				"com.jopdesign.ui.launch.jopApplication", configName);
		wc.setAttribute(JopLaunchDelegate.ATTR_TARGET_TYPE, JopLaunchDelegate.TARGET_SIMULATOR);
		wc.setAttribute(JopLaunchDelegate.ATTR_MICROCODE_FILE,
				asmFile.getFullPath().toString());
		wc.setAttribute(JopLaunchDelegate.ATTR_INITIAL_SP, 64);
		wc.setAttribute(JopLaunchDelegate.ATTR_MEM_SIZE, 1024);
		ILaunchConfiguration config = wc.doSave();
		return config.launch(ILaunchManager.DEBUG_MODE, new NullProgressMonitor());
	}

	private void waitForDebugTarget() {
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				return currentLaunch.getDebugTargets().length > 0;
			}

			@Override
			public String getFailureMessage() {
				return "Debug target did not appear";
			}
		});
	}

	private void waitForDebugThread() {
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				return currentLaunch.getDebugTargets().length > 0
						&& currentLaunch.getDebugTargets()[0].getThreads().length > 0;
			}

			@Override
			public String getFailureMessage() {
				return "Debug thread did not appear";
			}
		});
	}

	private void switchToJopDebugPerspective() {
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getActivePage();
				page.setPerspective(PlatformUI.getWorkbench()
						.getPerspectiveRegistry()
						.findPerspectiveWithId("com.jopdesign.ui.jopDebugPerspective"));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		bot.sleep(1000);
	}
}
