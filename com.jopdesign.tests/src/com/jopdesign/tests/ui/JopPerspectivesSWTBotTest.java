package com.jopdesign.tests.ui;

import static org.junit.Assert.*;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * SWTBot tests for JOP perspectives.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopPerspectivesSWTBotTest {

	private static SWTWorkbenchBot bot;
	private static String originalPerspectiveId;

	@BeforeClass
	public static void initBot() {
		SWTBotPreferences.TIMEOUT = 15000;
		bot = new SWTWorkbenchBot();
		SWTBotTestUtil.closeWelcome(bot);
		// Remember original perspective to restore later
		originalPerspectiveId = getPerspectiveId();
	}

	@AfterClass
	public static void restorePerspective() {
		if (originalPerspectiveId != null) {
			switchToPerspective(originalPerspectiveId);
		}
	}

	@Test
	public void testOpenJopPerspective() {
		switchToPerspective("com.jopdesign.ui.jopPerspective");

		// Verify key views are present in the JOP perspective
		assertViewOpen("Package Explorer");
		assertViewOpen("Outline");
	}

	@Test
	public void testOpenJopDebugPerspective() {
		switchToPerspective("com.jopdesign.ui.jopDebugPerspective");

		// Verify debug-specific views are present
		assertViewOpen("Debug");
		assertViewOpen("JOP Registers");
		assertViewOpen("JOP Stack");
		assertViewOpen("JOP Memory");
		assertViewOpen("Breakpoints");
		// Console may not show by title if it's behind a tab in headless mode;
		// verify via the view reference instead
		assertViewExists("org.eclipse.ui.console.ConsoleView");
	}

	@Test
	public void testSwitchBetweenPerspectives() {
		// Open JOP perspective
		switchToPerspective("com.jopdesign.ui.jopPerspective");
		assertEquals("com.jopdesign.ui.jopPerspective", getPerspectiveId());

		// Switch to JOP Debug
		switchToPerspective("com.jopdesign.ui.jopDebugPerspective");
		assertEquals("com.jopdesign.ui.jopDebugPerspective", getPerspectiveId());

		// Switch back to JOP
		switchToPerspective("com.jopdesign.ui.jopPerspective");
		assertEquals("com.jopdesign.ui.jopPerspective", getPerspectiveId());
	}

	@Test
	public void testJopPerspectiveAvailableInMenu() {
		// The JOP perspectives should be listed in Window > Perspective > Open Perspective
		bot.menu("Window").menu("Perspective").menu("Open Perspective").menu("Other...").click();
		bot.waitUntil(org.eclipse.swtbot.swt.finder.waits.Conditions
				.shellIsActive("Open Perspective"));

		// Find "JOP" in the perspective list
		boolean foundJop = false;
		boolean foundJopDebug = false;
		try {
			bot.table().getTableItem("JOP");
			foundJop = true;
		} catch (WidgetNotFoundException e) {
			// not found
		}
		try {
			bot.table().getTableItem("JOP Debug");
			foundJopDebug = true;
		} catch (WidgetNotFoundException e) {
			// not found
		}
		bot.button("Cancel").click();

		assertTrue("JOP perspective should be available", foundJop);
		assertTrue("JOP Debug perspective should be available", foundJopDebug);
	}

	// ---- Helpers ----

	private void assertViewOpen(String viewTitle) {
		try {
			SWTBotView view = bot.viewByTitle(viewTitle);
			assertNotNull("View '" + viewTitle + "' should be open", view);
		} catch (WidgetNotFoundException e) {
			fail("Expected view '" + viewTitle + "' to be open in the current perspective");
		}
	}

	private void assertViewExists(String viewId) {
		final boolean[] found = { false };
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			org.eclipse.ui.IViewReference[] views = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage().getViewReferences();
			for (org.eclipse.ui.IViewReference ref : views) {
				if (ref.getId().equals(viewId)) {
					found[0] = true;
					break;
				}
			}
		});
		assertTrue("View with ID '" + viewId + "' should exist in perspective", found[0]);
	}

	private static String getPerspectiveId() {
		final String[] id = { null };
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
			if (page != null && page.getPerspective() != null) {
				id[0] = page.getPerspective().getId();
			}
		});
		return id[0];
	}

	private static void switchToPerspective(String perspectiveId) {
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getActivePage();
				page.setPerspective(PlatformUI.getWorkbench()
						.getPerspectiveRegistry().findPerspectiveWithId(perspectiveId));
			} catch (Exception e) {
				throw new RuntimeException("Failed to switch perspective: " + e.getMessage(), e);
			}
		});
		bot.sleep(1000); // Let perspective settle
	}
}
