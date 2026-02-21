package com.jopdesign.ui.editors;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.editors.text.TextEditor;

/**
 * Read-only text editor for JOP application image (.jop) files.
 * Provides syntax highlighting for {@code //} line comments.
 */
public class JopTextEditor extends TextEditor {

	public JopTextEditor() {
		super();
		setSourceViewerConfiguration(new JopSourceViewerConfiguration());
	}

	@Override
	public boolean isEditable() {
		return false;
	}

	private static class JopSourceViewerConfiguration extends SourceViewerConfiguration {

		@Override
		public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
			PresentationReconciler reconciler = new PresentationReconciler();

			RuleBasedScanner scanner = new RuleBasedScanner();
			Color commentColor = Display.getDefault().getSystemColor(SWT.COLOR_DARK_GREEN);
			IToken commentToken = new Token(new TextAttribute(commentColor));
			scanner.setRules(new IRule[] {
				new EndOfLineRule("//", commentToken),
			});

			DefaultDamagerRepairer dr = new DefaultDamagerRepairer(scanner);
			reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
			reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);

			return reconciler;
		}
	}
}
