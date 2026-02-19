package com.jopdesign.microcode.editor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;

import com.jopdesign.microcode.syntax.MicrocodeInstructions;
import com.jopdesign.microcode.syntax.MicrocodeInstructions.InstructionInfo;

/**
 * Provides hover information for JOP microcode instructions.
 * When the mouse hovers over an instruction mnemonic, displays
 * the opcode, dataflow, stack effect, and JVM equivalent.
 */
public class MicrocodeTextHover implements ITextHover {

	@Override
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		IDocument document = textViewer.getDocument();
		try {
			String word = document.get(hoverRegion.getOffset(), hoverRegion.getLength());
			InstructionInfo info = MicrocodeInstructions.getInstruction(word);
			if (info != null) {
				return formatHover(info);
			}
		} catch (BadLocationException e) {
			// ignore
		}
		return null;
	}

	@Override
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		IDocument document = textViewer.getDocument();
		try {
			// Find word boundaries
			int start = offset;
			while (start > 0) {
				char c = document.getChar(start - 1);
				if (!Character.isLetterOrDigit(c) && c != '_') break;
				start--;
			}
			int end = offset;
			int length = document.getLength();
			while (end < length) {
				char c = document.getChar(end);
				if (!Character.isLetterOrDigit(c) && c != '_') break;
				end++;
			}
			if (end > start) {
				return new Region(start, end - start);
			}
		} catch (BadLocationException e) {
			// ignore
		}
		return new Region(offset, 0);
	}

	private String formatHover(InstructionInfo info) {
		StringBuilder sb = new StringBuilder();
		sb.append(info.name()).append(" - ").append(info.description()).append("\n\n");
		sb.append("Opcode:       ").append(info.opcode()).append("\n");
		sb.append("Dataflow:     ").append(info.dataflow()).append("\n");
		sb.append("Stack effect: ").append(info.stackEffect()).append("\n");
		if (!"--".equals(info.jvmEquivalent())) {
			sb.append("JVM bytecode: ").append(info.jvmEquivalent()).append("\n");
		}
		return sb.toString();
	}
}
