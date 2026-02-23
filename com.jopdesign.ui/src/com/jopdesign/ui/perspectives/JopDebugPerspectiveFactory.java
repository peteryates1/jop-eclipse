package com.jopdesign.ui.perspectives;

import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

/**
 * Perspective factory for the JOP Debug perspective.
 * Arranges Debug, editor, JOP Registers, JOP Stack, Console, Variables,
 * and Breakpoints views.
 */
public class JopDebugPerspectiveFactory implements IPerspectiveFactory {

	public static final String PERSPECTIVE_ID = "com.jopdesign.ui.jopDebugPerspective";

	@Override
	public void createInitialLayout(IPageLayout layout) {
		String editorArea = layout.getEditorArea();

		// Left (25%): Debug view (thread/frame tree)
		IFolderLayout left = layout.createFolder("left", IPageLayout.LEFT, 0.25f, editorArea);
		left.addView(IDebugUIConstants.ID_DEBUG_VIEW);

		// Right (35%): JOP Registers + Outline (tabbed)
		IFolderLayout right = layout.createFolder("right", IPageLayout.RIGHT, 0.65f, editorArea);
		right.addView("com.jopdesign.ui.views.registersView");
		right.addView(IPageLayout.ID_OUTLINE);

		// Bottom-left: JOP Stack + Breakpoints (tabbed)
		IFolderLayout bottomLeft = layout.createFolder("bottomLeft", IPageLayout.BOTTOM, 0.65f, editorArea);
		bottomLeft.addView("com.jopdesign.ui.views.stackView");
		bottomLeft.addView(IDebugUIConstants.ID_BREAKPOINT_VIEW);

		// Bottom-right: Console + Variables + Memory (tabbed)
		IFolderLayout bottomRight = layout.createFolder("bottomRight", IPageLayout.RIGHT, 0.5f, "bottomLeft");
		bottomRight.addView("org.eclipse.ui.console.ConsoleView");
		bottomRight.addView(IDebugUIConstants.ID_VARIABLE_VIEW);
		bottomRight.addView("com.jopdesign.ui.views.memoryView");

		// Show view shortcuts
		layout.addShowViewShortcut(IDebugUIConstants.ID_DEBUG_VIEW);
		layout.addShowViewShortcut(IDebugUIConstants.ID_VARIABLE_VIEW);
		layout.addShowViewShortcut(IDebugUIConstants.ID_BREAKPOINT_VIEW);
		layout.addShowViewShortcut("org.eclipse.ui.console.ConsoleView");
		layout.addShowViewShortcut("com.jopdesign.ui.views.registersView");
		layout.addShowViewShortcut("com.jopdesign.ui.views.stackView");
		layout.addShowViewShortcut("com.jopdesign.ui.views.memoryView");

		// Perspective shortcuts
		layout.addPerspectiveShortcut("com.jopdesign.ui.jopPerspective");
		layout.addPerspectiveShortcut("org.eclipse.debug.ui.DebugPerspective");
		layout.addPerspectiveShortcut("org.eclipse.jdt.ui.JavaPerspective");

		// Action sets
		layout.addActionSet(IDebugUIConstants.LAUNCH_ACTION_SET);
		layout.addActionSet(IPageLayout.ID_NAVIGATE_ACTION_SET);
	}
}
