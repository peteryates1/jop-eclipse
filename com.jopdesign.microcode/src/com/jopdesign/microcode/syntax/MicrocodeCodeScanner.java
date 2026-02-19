package com.jopdesign.microcode.syntax;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

/**
 * Scanner for JOP microcode code partitions. Provides syntax coloring for:
 * <ul>
 *   <li>Instructions (blue, bold) - all 65 JOP mnemonics</li>
 *   <li>Label definitions (dark red, bold) - e.g. {@code echo_loop:}</li>
 *   <li>Constants/variables (dark cyan) - identifiers used as operands</li>
 *   <li>Operators (dark red) - {@code =} and {@code ?}</li>
 *   <li>Numbers (dark magenta) - decimal, hex (0x...), negative</li>
 *   <li>Default text (black)</li>
 * </ul>
 */
public class MicrocodeCodeScanner extends RuleBasedScanner {

	// Colors shared for hover/outline if needed
	public static final int[] COLOR_INSTRUCTION = { 0, 0, 192 };       // Blue
	public static final int[] COLOR_LABEL_DEF   = { 128, 0, 0 };       // Dark red
	public static final int[] COLOR_NUMBER       = { 128, 0, 128 };     // Dark magenta
	public static final int[] COLOR_OPERATOR     = { 128, 0, 0 };       // Dark red
	public static final int[] COLOR_IDENTIFIER   = { 64, 64, 64 };      // Dark gray
	public static final int[] COLOR_DEFAULT      = { 0, 0, 0 };         // Black

	public MicrocodeCodeScanner() {
		Display display = Display.getDefault();

		IToken instructionToken = new Token(new TextAttribute(
				new Color(display, COLOR_INSTRUCTION[0], COLOR_INSTRUCTION[1], COLOR_INSTRUCTION[2]),
				null, SWT.BOLD));
		IToken labelDefToken = new Token(new TextAttribute(
				new Color(display, COLOR_LABEL_DEF[0], COLOR_LABEL_DEF[1], COLOR_LABEL_DEF[2]),
				null, SWT.BOLD));
		IToken numberToken = new Token(new TextAttribute(
				new Color(display, COLOR_NUMBER[0], COLOR_NUMBER[1], COLOR_NUMBER[2])));
		IToken operatorToken = new Token(new TextAttribute(
				new Color(display, COLOR_OPERATOR[0], COLOR_OPERATOR[1], COLOR_OPERATOR[2]),
				null, SWT.BOLD));
		IToken identifierToken = new Token(new TextAttribute(
				new Color(display, COLOR_IDENTIFIER[0], COLOR_IDENTIFIER[1], COLOR_IDENTIFIER[2])));
		IToken defaultToken = new Token(new TextAttribute(
				new Color(display, COLOR_DEFAULT[0], COLOR_DEFAULT[1], COLOR_DEFAULT[2])));

		setDefaultReturnToken(defaultToken);

		List<IRule> rules = new ArrayList<>();

		// Operator rule for = and ?
		rules.add(new OperatorRule(operatorToken));

		// Number rule: decimal, negative, and hex (0x...)
		rules.add(new MicrocodeNumberRule(numberToken));

		// Combined word + label rule: matches identifiers, instructions, and label definitions (word:)
		Set<String> instructions = new HashSet<>(MicrocodeInstructions.getInstructionNames());
		Set<String> operators = Set.of("nxt", "opd");
		rules.add(new MicrocodeWordRule(instructions, operators,
				instructionToken, operatorToken, labelDefToken, identifierToken));

		setRules(rules.toArray(new IRule[0]));
	}

	/**
	 * Combined word rule that handles identifiers, instructions, and label definitions.
	 * Reads a word, then peeks at the next character: if it's ':', the word + colon
	 * is returned as a label token. Otherwise the word is classified as an instruction,
	 * operator (nxt/opd), or plain identifier.
	 */
	private static class MicrocodeWordRule implements IRule {
		private final Set<String> instructions;
		private final Set<String> operators;
		private final IToken instructionToken;
		private final IToken operatorToken;
		private final IToken labelToken;
		private final IToken identifierToken;

		MicrocodeWordRule(Set<String> instructions, Set<String> operators,
				IToken instructionToken, IToken operatorToken, IToken labelToken, IToken identifierToken) {
			this.instructions = instructions;
			this.operators = operators;
			this.instructionToken = instructionToken;
			this.operatorToken = operatorToken;
			this.labelToken = labelToken;
			this.identifierToken = identifierToken;
		}

		@Override
		public IToken evaluate(ICharacterScanner scanner) {
			int c = scanner.read();
			if (!Character.isLetter((char) c) && c != '_') {
				scanner.unread();
				return Token.UNDEFINED;
			}

			StringBuilder word = new StringBuilder();
			word.append((char) c);

			while (true) {
				c = scanner.read();
				if (Character.isLetterOrDigit((char) c) || c == '_') {
					word.append((char) c);
				} else {
					// Peek: if colon, this is a label definition
					if (c == ':') {
						return labelToken;
					}
					// Not a colon - put back the non-word char
					scanner.unread();
					break;
				}
			}

			String w = word.toString();
			if (instructions.contains(w)) return instructionToken;
			if (operators.contains(w)) return operatorToken;
			return identifierToken;
		}
	}

	/**
	 * Rule that matches the operators = and ? used in constant and variable definitions.
	 */
	private static class OperatorRule implements IRule {
		private final IToken token;

		OperatorRule(IToken token) {
			this.token = token;
		}

		@Override
		public IToken evaluate(ICharacterScanner scanner) {
			int c = scanner.read();
			if (c == '=' || c == '?') {
				return token;
			}
			scanner.unread();
			return Token.UNDEFINED;
		}
	}

	/**
	 * Rule that matches numbers: decimal integers, negative numbers (prefixed with -),
	 * and hexadecimal (0x...).
	 */
	private static class MicrocodeNumberRule implements IRule {
		private final IToken token;

		MicrocodeNumberRule(IToken token) {
			this.token = token;
		}

		@Override
		public IToken evaluate(ICharacterScanner scanner) {
			int c = scanner.read();
			int count = 1;

			// Handle negative numbers
			if (c == '-') {
				int next = scanner.read();
				count++;
				if (!Character.isDigit((char) next)) {
					for (int i = 0; i < count; i++) scanner.unread();
					return Token.UNDEFINED;
				}
				c = next;
			}

			if (!Character.isDigit((char) c)) {
				for (int i = 0; i < count; i++) scanner.unread();
				return Token.UNDEFINED;
			}

			// Check for hex: 0x...
			if (c == '0') {
				int next = scanner.read();
				count++;
				if (next == 'x' || next == 'X') {
					// Read hex digits
					boolean hasDigits = false;
					while (true) {
						next = scanner.read();
						count++;
						if (isHexDigit((char) next)) {
							hasDigits = true;
						} else {
							scanner.unread();
							count--;
							break;
						}
					}
					if (hasDigits) return token;
					// Just "0x" with no digits - unwind
					for (int i = 0; i < count; i++) scanner.unread();
					return Token.UNDEFINED;
				} else {
					scanner.unread();
					count--;
				}
			}

			// Read remaining decimal digits
			while (true) {
				c = scanner.read();
				if (Character.isDigit((char) c)) {
					count++;
				} else {
					scanner.unread();
					break;
				}
			}
			return token;
		}

		private boolean isHexDigit(char c) {
			return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
		}
	}
}
