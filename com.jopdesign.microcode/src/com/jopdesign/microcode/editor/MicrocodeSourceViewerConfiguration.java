package com.jopdesign.microcode.editor;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

import com.jopdesign.microcode.syntax.MicrocodeCodeScanner;
import com.jopdesign.microcode.syntax.MicrocodePartitionScanner;

/**
 * Source viewer configuration for the microcode editor.
 * Sets up syntax highlighting, content assist, and hover support.
 */
public class MicrocodeSourceViewerConfiguration extends SourceViewerConfiguration {

	private MicrocodeCodeScanner codeScanner;

	@Override
	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return new String[] {
			IDocument.DEFAULT_CONTENT_TYPE,
			MicrocodePartitionScanner.MICROCODE_COMMENT,
			MicrocodePartitionScanner.MICROCODE_PREPROCESSOR,
		};
	}

	@Override
	public String getConfiguredDocumentPartitioning(ISourceViewer sourceViewer) {
		return MicrocodePartitionScanner.MICROCODE_PARTITIONING;
	}

	@Override
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));

		// Code partition: instruction highlighting
		DefaultDamagerRepairer codeDR = new DefaultDamagerRepairer(getCodeScanner());
		reconciler.setDamager(codeDR, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(codeDR, IDocument.DEFAULT_CONTENT_TYPE);

		// Comment partition: green italic
		Display display = Display.getDefault();
		Color commentColor = new Color(display, 63, 127, 95);
		DefaultDamagerRepairer commentDR = new DefaultDamagerRepairer(
				new SingleTokenScanner(new Token(new TextAttribute(commentColor, null, SWT.ITALIC))));
		reconciler.setDamager(commentDR, MicrocodePartitionScanner.MICROCODE_COMMENT);
		reconciler.setRepairer(commentDR, MicrocodePartitionScanner.MICROCODE_COMMENT);

		// Preprocessor partition: dark gray bold
		Color preprocColor = new Color(display, 100, 100, 100);
		DefaultDamagerRepairer preprocDR = new DefaultDamagerRepairer(
				new SingleTokenScanner(new Token(new TextAttribute(preprocColor, null, SWT.BOLD))));
		reconciler.setDamager(preprocDR, MicrocodePartitionScanner.MICROCODE_PREPROCESSOR);
		reconciler.setRepairer(preprocDR, MicrocodePartitionScanner.MICROCODE_PREPROCESSOR);

		return reconciler;
	}

	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		ContentAssistant assistant = new ContentAssistant();
		assistant.setContentAssistProcessor(
				new MicrocodeContentAssistProcessor(),
				IDocument.DEFAULT_CONTENT_TYPE);
		assistant.enableAutoActivation(true);
		assistant.setAutoActivationDelay(200);
		assistant.setProposalPopupOrientation(IContentAssistant.PROPOSAL_OVERLAY);
		assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer));
		return assistant;
	}

	@Override
	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
		if (IDocument.DEFAULT_CONTENT_TYPE.equals(contentType)) {
			return new MicrocodeTextHover();
		}
		return null;
	}

	private MicrocodeCodeScanner getCodeScanner() {
		if (codeScanner == null) {
			codeScanner = new MicrocodeCodeScanner();
		}
		return codeScanner;
	}
}
