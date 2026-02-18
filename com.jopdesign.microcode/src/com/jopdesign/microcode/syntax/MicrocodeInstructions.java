package com.jopdesign.microcode.syntax;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * JOP microcode instruction definitions for syntax highlighting, content assist,
 * and hover help. Derived from Instruction.java and microcode.md.
 */
public final class MicrocodeInstructions {

	private MicrocodeInstructions() {}

	/** Instruction metadata for editor support. */
	public static record InstructionInfo(
			String name,
			String description,
			String opcode,
			String dataflow,
			String jvmEquivalent,
			StackEffect stackEffect) {
	}

	public enum StackEffect { PUSH, POP, NOP }

	private static final Map<String, InstructionInfo> INSTRUCTIONS = new LinkedHashMap<>();

	static {
		// Pop instructions (consume stack)
		pop("pop",     "Pop the top operand stack value",                    "0000000000", "B -> A, stack[sp] -> B, sp-1 -> sp", "pop");
		pop("and",     "Boolean AND int",                                    "0000000001", "A & B -> A, stack[sp] -> B, sp-1 -> sp", "iand");
		pop("or",      "Boolean OR int",                                     "0000000010", "A | B -> A, stack[sp] -> B, sp-1 -> sp", "ior");
		pop("xor",     "Boolean XOR int",                                    "0000000011", "A ^ B -> A, stack[sp] -> B, sp-1 -> sp", "ixor");
		pop("add",     "Add int",                                            "0000000100", "A+B -> A, stack[sp] -> B, sp-1 -> sp", "iadd");
		pop("sub",     "Subtract int",                                       "0000000101", "A-B -> A, stack[sp] -> B, sp-1 -> sp", "isub");

		pop("st0",     "Store into local variable 0",                        "00000100nn", "A -> stack[vp+0]", "istore_0");
		pop("st1",     "Store into local variable 1",                        "00000100nn", "A -> stack[vp+1]", "istore_1");
		pop("st2",     "Store into local variable 2",                        "00000100nn", "A -> stack[vp+2]", "istore_2");
		pop("st3",     "Store into local variable 3",                        "00000100nn", "A -> stack[vp+3]", "istore_3");
		pop("st",      "Store into local variable (bytecode operand)",       "0000010100", "A -> stack[vp+opd]", "istore");
		pop("stmi",    "Store in local memory indirect (via ar)",            "0000010101", "A -> stack[ar]", "--");

		pop("stvp",    "Store variable pointer",                             "0000011000", "A -> vp", "--");
		pop("stjpc",   "Store Java program counter",                         "0000011001", "A -> jpc", "--");
		pop("star",    "Store address register",                             "0000011010", "A -> ar", "--");
		pop("stsp",    "Store stack pointer",                                "0000011011", "A -> sp", "--");

		pop("ushr",    "Logical shift right int",                            "0000011100", "B >>> A -> A", "iushr");
		pop("shl",     "Shift left int",                                     "0000011101", "B << A -> A", "ishl");
		pop("shr",     "Arithmetic shift right int",                         "0000011110", "B >> A -> A", "ishr");

		pop("stm",     "Store in local memory (5-bit address)",              "00001nnnnn", "A -> stack[n]", "--");

		pop("stmul",   "Start multiply (store operands to multiplier)",      "0001000000", "A -> mula, B -> mulb", "--");
		pop("stmwa",   "Store memory write address",                         "0001000001", "A -> memwra", "--");
		pop("stmra",   "Store memory read address",                          "0001000010", "A -> memrda", "--");
		pop("stmwd",   "Store memory write data",                            "0001000011", "A -> memwrd", "--");
		pop("stald",   "Start array load",                                   "0001000100", "A -> memidx, B -> memptr", "xaload");
		pop("stast",   "Start array store",                                  "0001000101", "A -> memval", "xastore");
		pop("stgf",    "Start getfield",                                     "0001000110", "A -> memptr", "getfield");
		pop("stpf",    "Start putfield",                                     "0001000111", "A -> memval, B -> memptr", "putfield");
		pop("stcp",    "Start copy step",                                    "0001001000", "A -> memidx, B -> memsrc", "--");
		pop("stbcrd",  "Start bytecode read (DMA to method cache)",          "0001001001", "A -> membcr", "--");
		pop("stidx",   "Store index for native field access",               "0001001010", "A -> memidx", "--");
		pop("stps",    "Start putstatic",                                    "0001001011", "A -> memval", "putstatic");
		pop("stmrac",  "Start memory constant read",                         "0001001100", "A -> memrda", "--");
		pop("stmraf",  "Start memory read through full assoc. cache",        "0001001101", "A -> memrda", "--");
		pop("stmwdf",  "Start memory write through full assoc. cache",       "0001001110", "A -> memwrd", "--");
		pop("stpfr",   "Start putfield (reference check)",                   "0001001111", "A -> memval, B -> memptr", "--");

		// Push instructions (produce stack)
		push("ldm",         "Load from local memory (5-bit address)",        "00101nnnnn", "stack[n] -> A", "--");
		push("ldi",         "Load from constants area (5-bit, n+32)",        "00110nnnnn", "stack[n+32] -> A", "--");

		push("ldmrd",       "Load memory read data",                         "0011100000", "memrdd -> A", "--");
		push("ldmul",       "Load multiplier result",                        "0011100001", "mulr -> A", "imul");
		push("ldbcstart",   "Load method start address in cache",            "0011100010", "bcstart -> A", "--");

		push("ld0",         "Load local variable 0",                         "00111010nn", "stack[vp+0] -> A", "iload_0");
		push("ld1",         "Load local variable 1",                         "00111010nn", "stack[vp+1] -> A", "iload_1");
		push("ld2",         "Load local variable 2",                         "00111010nn", "stack[vp+2] -> A", "iload_2");
		push("ld3",         "Load local variable 3",                         "00111010nn", "stack[vp+3] -> A", "iload_3");
		push("ld",          "Load local variable (bytecode operand)",        "0011101100", "stack[vp+opd] -> A", "iload");
		push("ldmi",        "Load from local memory indirect (via ar)",      "0011101101", "stack[ar] -> A", "--");

		push("ldsp",        "Load stack pointer",                            "0011110000", "sp -> A", "--");
		push("ldvp",        "Load variable pointer",                         "0011110001", "vp -> A", "--");
		push("ldjpc",       "Load Java program counter",                     "0011110010", "jpc -> A", "--");

		push("ld_opd_8u",   "Load 8-bit bytecode operand unsigned",          "0011110100", "opd -> A", "--");
		push("ld_opd_8s",   "Load 8-bit bytecode operand signed",            "0011110101", "opd -> A", "bipush");
		push("ld_opd_16u",  "Load 16-bit bytecode operand unsigned",         "0011110110", "opd_16 -> A", "--");
		push("ld_opd_16s",  "Load 16-bit bytecode operand signed",           "0011110111", "opd_16 -> A", "sipush");

		push("dup",         "Duplicate top operand stack value",             "0011111000", "A -> B, B -> stack[sp+1], sp+1 -> sp", "dup");

		// NOP-type instructions (no stack pointer change)
		nop("nop",       "No operation",                                     "0100000000", "-", "nop");
		nop("wait",      "Wait for memory completion",                       "0100000001", "-", "--");
		nop("jbr",       "Conditional bytecode branch/goto",                 "0100000010", "-", "if*/goto");

		nop("stgs",      "Start getstatic",                                  "0100010000", "opd_16 -> memptr", "getstatic");
		nop("cinval",    "Invalidate data cache",                            "0100010001", "-", "--");
		nop("atmstart",  "Start atomic arbiter operation",                   "0100010010", "-", "--");
		nop("atmend",    "End atomic arbiter operation",                     "0100010011", "-", "--");

		// Branch instructions
		pop("bz",        "Branch if zero (6-bit signed offset, 2 delay slots)", "0110nnnnnn", "if A=0 then pc+offset+1 -> pc", "--");
		pop("bnz",       "Branch if not zero (6-bit signed offset, 2 delay slots)", "0111nnnnnn", "if A!=0 then pc+offset+1 -> pc", "--");

		// Jump instruction
		nop("jmp",       "Unconditional jump (9-bit signed offset)",         "1nnnnnnnnn", "pc+offset+1 -> pc", "--");
	}

	private static void pop(String name, String desc, String opcode, String dataflow, String jvm) {
		INSTRUCTIONS.put(name, new InstructionInfo(name, desc, opcode, dataflow, jvm, StackEffect.POP));
	}

	private static void push(String name, String desc, String opcode, String dataflow, String jvm) {
		INSTRUCTIONS.put(name, new InstructionInfo(name, desc, opcode, dataflow, jvm, StackEffect.PUSH));
	}

	private static void nop(String name, String desc, String opcode, String dataflow, String jvm) {
		INSTRUCTIONS.put(name, new InstructionInfo(name, desc, opcode, dataflow, jvm, StackEffect.NOP));
	}

	/** All instruction names for syntax highlighting. */
	public static Set<String> getInstructionNames() {
		return Collections.unmodifiableSet(INSTRUCTIONS.keySet());
	}

	/** Get instruction info for content assist/hover. */
	public static InstructionInfo getInstruction(String name) {
		return INSTRUCTIONS.get(name);
	}

	/** All instructions. */
	public static Map<String, InstructionInfo> getAll() {
		return Collections.unmodifiableMap(INSTRUCTIONS);
	}

	/** C preprocessor directives recognized in microcode source. */
	public static final String[] PREPROCESSOR_DIRECTIVES = {
		"#define", "#undef", "#ifdef", "#ifndef", "#if", "#elif",
		"#else", "#endif", "#include", "#pragma"
	};
}
