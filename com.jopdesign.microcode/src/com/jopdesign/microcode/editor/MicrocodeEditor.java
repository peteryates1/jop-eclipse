package com.jopdesign.microcode.editor;

import org.eclipse.ui.editors.text.TextEditor;

/**
 * Editor for JOP microcode assembly (.asm) files.
 * Provides syntax highlighting, content assist, and hover help
 * for the JOP microcode instruction set.
 */
public class MicrocodeEditor extends TextEditor {

	public static final String EDITOR_ID = "com.jopdesign.microcode.editor";

	public MicrocodeEditor() {
		setSourceViewerConfiguration(new MicrocodeSourceViewerConfiguration());
		setDocumentProvider(new MicrocodeDocumentProvider());
	}

	@Override
	protected void initializeEditor() {
		super.initializeEditor();
		setEditorContextMenuId("#MicrocodeEditorContext");
	}
}
