package com.jopdesign.core.sim.microcode;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A parsed microcode program ready for simulation.
 *
 * @param statements      executable instructions in order
 * @param labels          label name to statement index
 * @param constants       constant name to value
 * @param variables       variable name to scratchpad slot
 * @param statementToLine statement index to 1-based source line
 * @param lineToStatement 1-based source line to statement index
 */
public record MicrocodeProgram(
		List<MicrocodeStatement> statements,
		Map<String, Integer> labels,
		Map<String, Integer> constants,
		Map<String, Integer> variables,
		Map<Integer, Integer> statementToLine,
		Map<Integer, Integer> lineToStatement
) {
	public MicrocodeProgram {
		statements = Collections.unmodifiableList(statements);
		labels = Collections.unmodifiableMap(labels);
		constants = Collections.unmodifiableMap(constants);
		variables = Collections.unmodifiableMap(variables);
		statementToLine = Collections.unmodifiableMap(statementToLine);
		lineToStatement = Collections.unmodifiableMap(lineToStatement);
	}
}
