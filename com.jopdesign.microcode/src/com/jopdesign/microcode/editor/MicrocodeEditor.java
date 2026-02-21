package com.jopdesign.microcode.editor;

import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

/**
 * Editor for JOP microcode assembly (.asm) files.
 * Provides syntax highlighting, content assist, hover help,
 * and an outline view for the JOP microcode instruction set.
 */
public class MicrocodeEditor extends TextEditor {

	public static final String EDITOR_ID = "com.jopdesign.microcode.editor";

	private MicrocodeOutlinePage outlinePage;

	public MicrocodeEditor() {
		setSourceViewerConfiguration(new MicrocodeSourceViewerConfiguration());
		setDocumentProvider(new MicrocodeDocumentProvider());
	}

	@Override
	protected void initializeEditor() {
		super.initializeEditor();
		setEditorContextMenuId("#MicrocodeEditorContext");
		setRulerContextMenuId("#MicrocodeRulerContext");
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (IContentOutlinePage.class.equals(adapter)) {
			if (outlinePage == null) {
				outlinePage = new MicrocodeOutlinePage(this);
			}
			return (T) outlinePage;
		}
		return super.getAdapter(adapter);
	}

	@Override
	protected void editorSaved() {
		super.editorSaved();
		if (outlinePage != null) {
			outlinePage.update();
		}
	}
}
