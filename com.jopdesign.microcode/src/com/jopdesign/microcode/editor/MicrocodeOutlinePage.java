package com.jopdesign.microcode.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

/**
 * Outline page for microcode assembly files. Shows:
 * <ul>
 *   <li>Labels (e.g. {@code echo_loop:})</li>
 *   <li>Constants (e.g. {@code io_uart = -111})</li>
 *   <li>Variables (e.g. {@code a ?})</li>
 * </ul>
 * Clicking an entry navigates to its position in the editor.
 */
public class MicrocodeOutlinePage extends ContentOutlinePage {

	private final ITextEditor editor;
	private IDocument document;

	/** An element shown in the outline tree. */
	public static record OutlineElement(String name, ElementType type, int offset, int length, int line) {}

	public enum ElementType { LABEL, CONSTANT, VARIABLE }

	// Patterns for parsing (use [ \t]* instead of \s* to avoid matching across lines)
	private static final Pattern LABEL_PATTERN = Pattern.compile("^[ \\t]*(\\w+)[ \\t]*:", Pattern.MULTILINE);
	private static final Pattern CONST_PATTERN = Pattern.compile("^[ \\t]*(\\w+)[ \\t]*=[ \\t]*(-?\\w+)", Pattern.MULTILINE);
	private static final Pattern VAR_PATTERN = Pattern.compile("^[ \\t]*(\\w+)[ \\t]+\\?", Pattern.MULTILINE);

	public MicrocodeOutlinePage(ITextEditor editor) {
		this.editor = editor;
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		TreeViewer viewer = getTreeViewer();
		viewer.setContentProvider(new OutlineContentProvider());
		viewer.setLabelProvider(new OutlineLabelProvider());
		viewer.addSelectionChangedListener(this::handleSelection);
		update();
	}

	/** Re-parse the document and refresh the outline. */
	public void update() {
		this.document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
		TreeViewer viewer = getTreeViewer();
		if (viewer != null && viewer.getControl() != null && !viewer.getControl().isDisposed()) {
			viewer.setInput(parseDocument());
			viewer.expandAll();
		}
	}

	private List<OutlineElement> parseDocument() {
		List<OutlineElement> elements = new ArrayList<>();
		if (document == null) return elements;

		String text = document.get();

		// Find labels
		Matcher m = LABEL_PATTERN.matcher(text);
		while (m.find()) {
			if (isInComment(text, m.start(1))) continue;
			int line = lineAt(m.start(1));
			elements.add(new OutlineElement(m.group(1), ElementType.LABEL, m.start(1), m.end() - m.start(1), line));
		}

		// Find constants
		m = CONST_PATTERN.matcher(text);
		while (m.find()) {
			if (isInComment(text, m.start(1))) continue;
			int line = lineAt(m.start(1));
			elements.add(new OutlineElement(
					m.group(1) + " = " + m.group(2), ElementType.CONSTANT, m.start(1), m.end() - m.start(1), line));
		}

		// Find variables
		m = VAR_PATTERN.matcher(text);
		while (m.find()) {
			if (isInComment(text, m.start(1))) continue;
			int line = lineAt(m.start(1));
			elements.add(new OutlineElement(m.group(1), ElementType.VARIABLE, m.start(1), m.end() - m.start(1), line));
		}

		// Sort by offset
		elements.sort((a, b) -> Integer.compare(a.offset(), b.offset()));
		return elements;
	}

	private boolean isInComment(String text, int offset) {
		// Check if this offset is preceded by // on the same line
		int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
		String prefix = text.substring(lineStart, offset);
		return prefix.contains("//");
	}

	private int lineAt(int offset) {
		try {
			return document.getLineOfOffset(offset) + 1;
		} catch (BadLocationException e) {
			return 0;
		}
	}

	private void handleSelection(SelectionChangedEvent event) {
		StructuredSelection sel = (StructuredSelection) event.getSelection();
		if (sel.isEmpty()) return;
		OutlineElement element = (OutlineElement) sel.getFirstElement();
		editor.selectAndReveal(element.offset(), element.length());
	}

	private static class OutlineContentProvider implements ITreeContentProvider {
		@Override
		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof List<?> list) {
				return list.toArray();
			}
			return new Object[0];
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			return new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return false;
		}
	}

	private static class OutlineLabelProvider extends LabelProvider {
		@Override
		public String getText(Object element) {
			if (element instanceof OutlineElement e) {
				return switch (e.type()) {
					case LABEL -> e.name() + ":";
					case CONSTANT -> e.name();
					case VARIABLE -> e.name() + " ?";
				};
			}
			return super.getText(element);
		}
	}
}
