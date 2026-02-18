package com.jopdesign.ui.perspectives;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

/**
 * Perspective factory for the JOP development perspective.
 * Arranges Package Explorer, microcode editor area, Outline, Console,
 * and Problems views in a layout suited for JOP development.
 */
public class JopPerspectiveFactory implements IPerspectiveFactory {

	public static final String PERSPECTIVE_ID = "com.jopdesign.ui.jopPerspective";

	@Override
	public void createInitialLayout(IPageLayout layout) {
		String editorArea = layout.getEditorArea();

		// Left: Package Explorer
		IFolderLayout left = layout.createFolder("left", IPageLayout.LEFT, 0.2f, editorArea);
		left.addView("org.eclipse.jdt.ui.PackageExplorer");

		// Right: Outline
		IFolderLayout right = layout.createFolder("right", IPageLayout.RIGHT, 0.75f, editorArea);
		right.addView(IPageLayout.ID_OUTLINE);

		// Bottom: Problems, Console, Properties
		IFolderLayout bottom = layout.createFolder("bottom", IPageLayout.BOTTOM, 0.7f, editorArea);
		bottom.addView(IPageLayout.ID_PROBLEM_VIEW);
		bottom.addView("org.eclipse.ui.console.ConsoleView");
		bottom.addView(IPageLayout.ID_PROP_SHEET);

		// Shortcuts
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.JavaProjectWizard");
		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.file");
		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.folder");

		layout.addPerspectiveShortcut("org.eclipse.jdt.ui.JavaPerspective");
		layout.addPerspectiveShortcut("org.eclipse.debug.ui.DebugPerspective");
	}
}
