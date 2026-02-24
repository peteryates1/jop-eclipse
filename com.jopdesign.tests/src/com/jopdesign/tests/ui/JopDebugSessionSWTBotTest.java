package com.jopdesign.tests.ui;

import static org.junit.Assert.*;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
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
 * SWTBot tests for JOP debug sessions using the Dummy target.
 * The Dummy target requires no hardware and provides canned responses.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopDebugSessionSWTBotTest {

	private static SWTWorkbenchBot bot;
	private ILaunch currentLaunch;

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
		// Terminate any running debug sessions
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
		// Dismiss any perspective switch dialogs
		dismissPerspectiveSwitch();
	}

	@Test
	public void testDummyDebugLaunch() throws Exception {
		currentLaunch = launchDummyDebug("DummyTest1");

		// Wait for debug target to appear
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

		IDebugTarget target = currentLaunch.getDebugTargets()[0];
		assertNotNull("Debug target should be present", target);
		assertFalse("Debug target should not be terminated", target.isTerminated());
	}

	@Test
	public void testDummyDebugHasThread() throws Exception {
		currentLaunch = launchDummyDebug("DummyTest2");

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

		assertEquals("Should have one thread", 1,
				currentLaunch.getDebugTargets()[0].getThreads().length);
	}

	@Test
	public void testDummyDebugTerminate() throws Exception {
		currentLaunch = launchDummyDebug("DummyTest3");

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

		assertTrue("Debug target should be terminated", target.isTerminated());
	}

	@Test
	public void testDummyDebugResumeSuspend() throws Exception {
		currentLaunch = launchDummyDebug("DummyTest4");

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

		IDebugTarget target = currentLaunch.getDebugTargets()[0];

		// Dummy target should start suspended
		assertTrue("Thread should be initially suspended",
				target.getThreads()[0].isSuspended());

		// Resume — Dummy resumes then immediately re-suspends
		target.getThreads()[0].resume();

		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				return target.getThreads()[0].isSuspended();
			}

			@Override
			public String getFailureMessage() {
				return "Thread did not re-suspend after resume";
			}
		});

		assertTrue("Thread should be re-suspended after Dummy resume",
				target.getThreads()[0].isSuspended());
	}

	@Test
	public void testDebugViewShowsTarget() throws Exception {
		// Switch to JOP Debug perspective first
		switchToJopDebugPerspective();

		currentLaunch = launchDummyDebug("DummyTest5");

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

		bot.sleep(2000); // Let views refresh

		// Debug view should show the target
		SWTBotView debugView = bot.viewByTitle("Debug");
		assertNotNull("Debug view should be visible", debugView);
	}

	@Test
	public void testRegistersViewAvailable() throws Exception {
		switchToJopDebugPerspective();

		currentLaunch = launchDummyDebug("DummyTest6");

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

		bot.sleep(2000);

		SWTBotView registersView = bot.viewByTitle("JOP Registers");
		assertNotNull("JOP Registers view should be open", registersView);
	}

	@Test
	public void testStackViewAvailable() throws Exception {
		switchToJopDebugPerspective();

		currentLaunch = launchDummyDebug("DummyTest7");

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

		bot.sleep(2000);

		SWTBotView stackView = bot.viewByTitle("JOP Stack");
		assertNotNull("JOP Stack view should be open", stackView);
	}

	// ---- Helpers ----

	private ILaunch launchDummyDebug(String configName) throws CoreException {
		ILaunchConfigurationWorkingCopy wc = SWTBotTestUtil.createLaunchConfig(
				"com.jopdesign.ui.launch.jopApplication", configName);
		wc.setAttribute(JopLaunchDelegate.ATTR_TARGET_TYPE, JopLaunchDelegate.TARGET_DUMMY);
		ILaunchConfiguration config = wc.doSave();
		return config.launch(ILaunchManager.DEBUG_MODE,
				new org.eclipse.core.runtime.NullProgressMonitor());
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

	private void dismissPerspectiveSwitch() {
		try {
			org.eclipse.swtbot.swt.finder.widgets.SWTBotShell shell =
					bot.shell("Confirm Perspective Switch");
			shell.activate();
			bot.button("No").click();
		} catch (Exception e) {
			// No dialog, OK
		}
	}
}
