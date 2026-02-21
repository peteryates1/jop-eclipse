package com.jopdesign.core.sim.microcode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses microcode assembly source text into a {@link MicrocodeProgram}.
 * <p>
 * Recognized line types:
 * <ul>
 *   <li>Constants: {@code name = value} (decimal, hex 0x, negative)</li>
 *   <li>Variables: {@code name ?} (allocated sequential scratchpad slots from 0)</li>
 *   <li>Labels: {@code name:}</li>
 *   <li>Instructions: {@code mnemonic [operand]}</li>
 *   <li>Comments: {@code // ...}</li>
 *   <li>Blank lines</li>
 * </ul>
 * Operands are resolved: label refs → statement indices, constant refs → values,
 * variable refs → scratchpad slots.
 */
public class MicrocodeParser {

	private static final Pattern CONST_PATTERN = Pattern.compile("^[ \\t]*(\\w+)[ \\t]*=[ \\t]*(-?(?:0[xX][0-9a-fA-F]+|\\d+))");
	private static final Pattern VAR_PATTERN = Pattern.compile("^[ \\t]*(\\w+)[ \\t]+\\?[ \\t]*$");
	private static final Pattern LABEL_PATTERN = Pattern.compile("^[ \\t]*(\\w+)[ \\t]*:[ \\t]*$");
	private static final Pattern INSTRUCTION_PATTERN = Pattern.compile("^[ \\t]*(\\w+)(?:[ \\t]+(.+?))?[ \\t]*$");

	/** Known instruction mnemonics (the 65-instruction JOP ISA). */
	private static final Set<String> MNEMONICS = Set.of(
			// Pop instructions
			"pop", "and", "or", "xor", "add", "sub",
			"st0", "st1", "st2", "st3", "st", "stmi",
			"stvp", "stjpc", "star", "stsp",
			"ushr", "shl", "shr",
			"stm",
			"stmul", "stmwa", "stmra", "stmwd",
			"stald", "stast", "stgf", "stpf", "stcp", "stbcrd", "stidx", "stps",
			"stmrac", "stmraf", "stmwdf", "stpfr",
			"bz", "bnz",
			// Push instructions
			"ldm", "ldi",
			"ldmrd", "ldmul", "ldbcstart",
			"ld0", "ld1", "ld2", "ld3", "ld", "ldmi",
			"ldsp", "ldvp", "ldjpc",
			"ld_opd_8u", "ld_opd_8s", "ld_opd_16u", "ld_opd_16s",
			"dup",
			// NOP instructions
			"nop", "wait", "jbr",
			"stgs", "cinval", "atmstart", "atmend",
			"jmp"
	);

	/** Offset in scratchpad memory where constants are stored. */
	public static final int CONSTANT_OFFSET = 32;

	/**
	 * Parse microcode source text into a program.
	 *
	 * @param source the full source text
	 * @return the parsed program
	 * @throws MicrocodeParseException if the source contains errors
	 */
	public MicrocodeProgram parse(String source) throws MicrocodeParseException {
		String[] lines = source.split("\\r?\\n", -1);

		Map<String, Integer> constants = new LinkedHashMap<>();
		Map<String, Integer> variables = new LinkedHashMap<>();
		Map<String, Integer> labels = new LinkedHashMap<>();
		List<RawInstruction> rawInstructions = new ArrayList<>();
		int nextVarSlot = 0;
		int nextConstSlot = 0;

		// First pass: collect constants, variables, labels, and raw instructions
		for (int i = 0; i < lines.length; i++) {
			int lineNum = i + 1;
			String line = stripComment(lines[i]);
			if (line.isBlank()) continue;

			// Check for preprocessor directives
			if (line.trim().startsWith("#")) {
				throw new MicrocodeParseException(lineNum,
						"Preprocessor directive found — preprocess with gcc -E first");
			}

			Matcher m;

			// Try constant
			m = CONST_PATTERN.matcher(line);
			if (m.matches()) {
				String name = m.group(1);
				if (MNEMONICS.contains(name)) {
					throw new MicrocodeParseException(lineNum,
							"Constant name '" + name + "' conflicts with instruction mnemonic");
				}
				int value = parseIntLiteral(m.group(2), lineNum);
				constants.put(name, value);
				continue;
			}

			// Try variable
			m = VAR_PATTERN.matcher(line);
			if (m.matches()) {
				String name = m.group(1);
				if (MNEMONICS.contains(name)) {
					throw new MicrocodeParseException(lineNum,
							"Variable name '" + name + "' conflicts with instruction mnemonic");
				}
				variables.put(name, nextVarSlot++);
				continue;
			}

			// Try label (label on its own line)
			m = LABEL_PATTERN.matcher(line);
			if (m.matches()) {
				String name = m.group(1);
				// Label points to the NEXT instruction that will be parsed
				labels.put(name, rawInstructions.size());
				continue;
			}

			// Try instruction
			m = INSTRUCTION_PATTERN.matcher(line);
			if (m.matches()) {
				String mnemonic = m.group(1);
				String operand = m.group(2);

				// Check if it's a label with trailing instruction (e.g. "loop: nop")
				if (mnemonic.endsWith(":")) {
					String labelName = mnemonic.substring(0, mnemonic.length() - 1);
					labels.put(labelName, rawInstructions.size());
					// The rest is the instruction
					if (operand != null) {
						String rest = operand.trim();
						Matcher instrMatcher = INSTRUCTION_PATTERN.matcher(rest);
						if (instrMatcher.matches()) {
							mnemonic = instrMatcher.group(1);
							operand = instrMatcher.group(2);
						} else {
							throw new MicrocodeParseException(lineNum,
									"Cannot parse instruction after label: " + rest);
						}
					} else {
						continue; // Just a label, no instruction
					}
				}

				if (!MNEMONICS.contains(mnemonic)) {
					throw new MicrocodeParseException(lineNum,
							"Unknown instruction: " + mnemonic);
				}
				rawInstructions.add(new RawInstruction(mnemonic, operand, lineNum));
				continue;
			}

			throw new MicrocodeParseException(lineNum, "Cannot parse line: " + lines[i].trim());
		}

		// Map constant names to their slot indices (0-based, for ldi instruction)
		Map<String, Integer> constantSlots = new LinkedHashMap<>();
		for (var entry : constants.entrySet()) {
			constantSlots.put(entry.getKey(), nextConstSlot++);
		}

		// Second pass: resolve operands
		List<MicrocodeStatement> statements = new ArrayList<>();
		Map<Integer, Integer> statementToLine = new HashMap<>();
		Map<Integer, Integer> lineToStatement = new HashMap<>();

		for (int idx = 0; idx < rawInstructions.size(); idx++) {
			RawInstruction raw = rawInstructions.get(idx);
			int resolved = resolveOperand(raw, idx, labels, constants, variables,
					constantSlots, rawInstructions.size());
			statements.add(new MicrocodeStatement(raw.mnemonic, raw.operand, resolved, raw.lineNum));
			statementToLine.put(idx, raw.lineNum);
			lineToStatement.put(raw.lineNum, idx);
		}

		return new MicrocodeProgram(statements, labels, constants, variables,
				statementToLine, lineToStatement);
	}

	private int resolveOperand(RawInstruction raw, int stmtIndex,
			Map<String, Integer> labels, Map<String, Integer> constants,
			Map<String, Integer> variables, Map<String, Integer> constantSlots,
			int totalStatements) throws MicrocodeParseException {
		if (raw.operand == null || raw.operand.isBlank()) {
			return 0;
		}

		String op = raw.operand.trim();

		// Try numeric literal
		try {
			return parseIntLiteral(op, raw.lineNum);
		} catch (MicrocodeParseException e) {
			// Not a literal, try symbolic
		}

		// Try label reference (for branch/jump instructions)
		if (labels.containsKey(op)) {
			int target = labels.get(op);
			if ("bz".equals(raw.mnemonic) || "bnz".equals(raw.mnemonic) || "jmp".equals(raw.mnemonic)) {
				// Branch offset is relative: target - (current + 1)
				return target - (stmtIndex + 1);
			}
			return target;
		}

		// Try constant reference
		if (constants.containsKey(op)) {
			// For ldi: resolve to slot index (simulator adds CONSTANT_OFFSET)
			if ("ldi".equals(raw.mnemonic)) {
				return constantSlots.get(op);
			}
			// For other instructions: resolve to the constant's value
			return constants.get(op);
		}

		// Try variable reference (returns scratchpad slot)
		if (variables.containsKey(op)) {
			return variables.get(op);
		}

		throw new MicrocodeParseException(raw.lineNum,
				"Unresolved operand: " + op);
	}

	private static String stripComment(String line) {
		int idx = line.indexOf("//");
		if (idx >= 0) {
			return line.substring(0, idx);
		}
		return line;
	}

	private static int parseIntLiteral(String text, int lineNum) throws MicrocodeParseException {
		try {
			text = text.trim();
			if (text.startsWith("0x") || text.startsWith("0X")) {
				return Integer.parseUnsignedInt(text.substring(2), 16);
			}
			if (text.startsWith("-0x") || text.startsWith("-0X")) {
				return -Integer.parseUnsignedInt(text.substring(3), 16);
			}
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			throw new MicrocodeParseException(lineNum, "Invalid number: " + text);
		}
	}

	private record RawInstruction(String mnemonic, String operand, int lineNum) {}

	/** Exception thrown when microcode source cannot be parsed. */
	public static class MicrocodeParseException extends Exception {
		private static final long serialVersionUID = 1L;
		private final int line;

		public MicrocodeParseException(int line, String message) {
			super("Line " + line + ": " + message);
			this.line = line;
		}

		public int getLine() {
			return line;
		}
	}
}
