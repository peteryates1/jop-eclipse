package com.jopdesign.microcode.editor;

import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;

/**
 * Simple scanner that returns a single token for the entire partition.
 * Used for comment and preprocessor partitions where the whole region
 * has uniform styling.
 */
public class SingleTokenScanner extends RuleBasedScanner {

	public SingleTokenScanner(IToken token) {
		setDefaultReturnToken(token);
	}
}
