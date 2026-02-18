package com.jopdesign.microcode.syntax;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.NumberRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

/**
 * Scanner for JOP microcode code partitions. Provides syntax coloring for:
 * <ul>
 *   <li>Instructions (blue, bold)</li>
 *   <li>Labels (dark red)</li>
 *   <li>Numbers/constants (dark magenta)</li>
 *   <li>Default text (black)</li>
 * </ul>
 */
public class MicrocodeCodeScanner extends RuleBasedScanner {

	public MicrocodeCodeScanner() {
		Display display = Display.getDefault();

		// Color definitions
		Color instructionColor = new Color(display, 0, 0, 192);       // Blue
		Color numberColor = new Color(display, 128, 0, 128);          // Dark magenta
		Color defaultColor = new Color(display, 0, 0, 0);             // Black
		Color labelColor = new Color(display, 128, 0, 0);             // Dark red

		IToken instructionToken = new Token(new TextAttribute(instructionColor, null, SWT.BOLD));
		IToken numberToken = new Token(new TextAttribute(numberColor));
		IToken defaultToken = new Token(new TextAttribute(defaultColor));
		IToken labelToken = new Token(new TextAttribute(labelColor, null, SWT.BOLD));

		setDefaultReturnToken(defaultToken);

		List<IRule> rules = new ArrayList<>();

		// Word rule for instructions and labels
		WordRule wordRule = new WordRule(new MicrocodeWordDetector(), defaultToken);
		for (String instruction : MicrocodeInstructions.getInstructionNames()) {
			wordRule.addWord(instruction, instructionToken);
		}
		rules.add(wordRule);

		// Number rule for numeric constants
		rules.add(new NumberRule(numberToken));

		setRules(rules.toArray(new IRule[0]));
	}

	/**
	 * Detector for microcode words (instructions, labels, constants).
	 * Words can contain letters, digits, and underscores.
	 */
	private static class MicrocodeWordDetector implements IWordDetector {
		@Override
		public boolean isWordStart(char c) {
			return Character.isLetter(c) || c == '_';
		}

		@Override
		public boolean isWordPart(char c) {
			return Character.isLetterOrDigit(c) || c == '_';
		}
	}
}
