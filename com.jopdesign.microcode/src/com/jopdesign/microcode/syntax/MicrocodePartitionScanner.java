package com.jopdesign.microcode.syntax;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.Token;

/**
 * Partition scanner for JOP microcode assembly. Divides the document into:
 * <ul>
 *   <li>Comments (// to end of line)</li>
 *   <li>Preprocessor directives (# to end of line)</li>
 *   <li>Default (code)</li>
 * </ul>
 */
public class MicrocodePartitionScanner extends RuleBasedPartitionScanner {

	public static final String MICROCODE_PARTITIONING = "__microcode_partitioning";

	public static final String MICROCODE_COMMENT = "__microcode_comment";
	public static final String MICROCODE_PREPROCESSOR = "__microcode_preprocessor";

	public static final String[] PARTITION_TYPES = {
		MICROCODE_COMMENT,
		MICROCODE_PREPROCESSOR
	};

	public MicrocodePartitionScanner() {
		IToken commentToken = new Token(MICROCODE_COMMENT);
		IToken preprocessorToken = new Token(MICROCODE_PREPROCESSOR);

		setPredicateRules(new IPredicateRule[] {
			new EndOfLineRule("//", commentToken),
			new EndOfLineRule("#", preprocessorToken),
		});
	}
}
