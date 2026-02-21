package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import org.junit.Test;

import com.jopdesign.core.sim.microcode.MicrocodeParser;
import com.jopdesign.core.sim.microcode.MicrocodeParser.MicrocodeParseException;
import com.jopdesign.core.sim.microcode.MicrocodeProgram;

/**
 * Tests for {@link MicrocodeParser}.
 */
public class MicrocodeParserTest {

	private final MicrocodeParser parser = new MicrocodeParser();

	@Test
	public void testParseConstant() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("io_uart = -111\nnop");
		assertEquals(Integer.valueOf(-111), prog.constants().get("io_uart"));
		assertEquals(1, prog.statements().size());
	}

	@Test
	public void testParseHexConstant() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("mask = 0xFF\nnop");
		assertEquals(Integer.valueOf(0xFF), prog.constants().get("mask"));
	}

	@Test
	public void testParseVariable() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("count ?\ntemp ?\nnop");
		assertEquals(Integer.valueOf(0), prog.variables().get("count"));
		assertEquals(Integer.valueOf(1), prog.variables().get("temp"));
	}

	@Test
	public void testParseLabel() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("start:\nnop\nloop:\nadd");
		assertEquals(Integer.valueOf(0), prog.labels().get("start"));
		assertEquals(Integer.valueOf(1), prog.labels().get("loop"));
		assertEquals(2, prog.statements().size());
	}

	@Test
	public void testParseInstruction() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("nop\nadd\npop");
		assertEquals(3, prog.statements().size());
		assertEquals("nop", prog.statements().get(0).mnemonic());
		assertEquals("add", prog.statements().get(1).mnemonic());
		assertEquals("pop", prog.statements().get(2).mnemonic());
	}

	@Test
	public void testInstructionWithOperand() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("ldm 5\nstm 10");
		assertEquals(2, prog.statements().size());
		assertEquals(5, prog.statements().get(0).resolvedOperand());
		assertEquals(10, prog.statements().get(1).resolvedOperand());
	}

	@Test
	public void testLabelResolution() throws MicrocodeParseException {
		// jmp target: offset = target_index - (current_index + 1) = 2 - (0+1) = 1
		MicrocodeProgram prog = parser.parse("jmp target\nnop\ntarget:\nnop");
		assertEquals(1, prog.statements().get(0).resolvedOperand());
	}

	@Test
	public void testBranchLabelResolution() throws MicrocodeParseException {
		// bz loop: statement 0=bz, 1=nop, 2=nop; loop points at index 0
		// offset = 0 - (0+1) = -1
		MicrocodeProgram prog = parser.parse("loop:\nbz loop\nnop\nnop");
		assertEquals(-1, prog.statements().get(0).resolvedOperand());
	}

	@Test
	public void testCommentStripping() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("// this is a comment\nnop // inline comment\nadd");
		assertEquals(2, prog.statements().size());
	}

	@Test
	public void testBlankLines() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("\nnop\n\nadd\n\n");
		assertEquals(2, prog.statements().size());
	}

	@Test
	public void testStatementToLineMapping() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("// header\nnop\n\nadd");
		assertEquals(Integer.valueOf(2), prog.statementToLine().get(0));
		assertEquals(Integer.valueOf(4), prog.statementToLine().get(1));
	}

	@Test
	public void testLineToStatementMapping() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("nop\nadd\npop");
		assertEquals(Integer.valueOf(0), prog.lineToStatement().get(1));
		assertEquals(Integer.valueOf(1), prog.lineToStatement().get(2));
		assertEquals(Integer.valueOf(2), prog.lineToStatement().get(3));
	}

	@Test(expected = MicrocodeParseException.class)
	public void testPreprocessorDirectiveError() throws MicrocodeParseException {
		parser.parse("#ifdef FOO\nnop\n#endif");
	}

	@Test(expected = MicrocodeParseException.class)
	public void testUnknownInstruction() throws MicrocodeParseException {
		parser.parse("foobar");
	}

	@Test(expected = MicrocodeParseException.class)
	public void testUnresolvedOperand() throws MicrocodeParseException {
		parser.parse("jmp nowhere");
	}

	@Test
	public void testConstantOperandResolution() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("io_uart = -111\nldm io_uart");
		// Constant reference resolves to the constant value
		assertEquals(-111, prog.statements().get(0).resolvedOperand());
	}

	@Test
	public void testVariableOperandResolution() throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse("count ?\nstm count");
		// Variable reference resolves to scratchpad slot 0
		assertEquals(0, prog.statements().get(0).resolvedOperand());
	}

	@Test
	public void testEchoLikeProgram() throws MicrocodeParseException {
		String source = """
				io_status = -1
				io_uart = -2
				io_uart_rx = -3

				echo:
					ldi io_status    // load status constant
					stmra            // set read address
					ldmrd            // read status
					ldi 2
					and              // mask RX ready bit
					bz echo          // loop if no data
					nop              // delay slot 1
					nop              // delay slot 2
				""";
		MicrocodeProgram prog = parser.parse(source);
		assertTrue(prog.statements().size() > 0);
		assertEquals(Integer.valueOf(0), prog.labels().get("echo"));
		assertEquals(Integer.valueOf(-1), prog.constants().get("io_status"));
	}
}
