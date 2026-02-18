package com.jopdesign.microcode.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import com.jopdesign.microcode.syntax.MicrocodeInstructions;
import com.jopdesign.microcode.syntax.MicrocodeInstructions.InstructionInfo;

/**
 * Content assist processor for JOP microcode assembly.
 * Provides completion proposals for instruction mnemonics with
 * documentation from the instruction set reference.
 */
public class MicrocodeContentAssistProcessor implements IContentAssistProcessor {

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		String prefix = getPrefix(viewer.getDocument(), offset);
		List<ICompletionProposal> proposals = new ArrayList<>();

		for (var entry : MicrocodeInstructions.getAll().entrySet()) {
			String name = entry.getKey();
			InstructionInfo info = entry.getValue();

			if (name.startsWith(prefix)) {
				String displayString = name + " - " + info.description();
				String additionalInfo = formatInstructionInfo(info);

				proposals.add(new CompletionProposal(
						name,
						offset - prefix.length(),
						prefix.length(),
						name.length(),
						null,
						displayString,
						null,
						additionalInfo));
			}
		}

		return proposals.toArray(new ICompletionProposal[0]);
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return null;
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return null;
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	private String getPrefix(IDocument document, int offset) {
		try {
			int start = offset;
			while (start > 0) {
				char c = document.getChar(start - 1);
				if (!Character.isLetterOrDigit(c) && c != '_') {
					break;
				}
				start--;
			}
			return document.get(start, offset - start);
		} catch (BadLocationException e) {
			return "";
		}
	}

	private String formatInstructionInfo(InstructionInfo info) {
		StringBuilder sb = new StringBuilder();
		sb.append(info.name()).append(" - ").append(info.description()).append("\n\n");
		sb.append("Opcode:   ").append(info.opcode()).append("\n");
		sb.append("Dataflow: ").append(info.dataflow()).append("\n");
		sb.append("Stack:    ").append(info.stackEffect()).append("\n");
		if (!"--".equals(info.jvmEquivalent())) {
			sb.append("JVM:      ").append(info.jvmEquivalent()).append("\n");
		}
		return sb.toString();
	}
}
