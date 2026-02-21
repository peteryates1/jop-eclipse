package com.jopdesign.core.fpga;

/**
 * Result of an FPGA synthesis operation.
 *
 * @param success whether the operation completed without errors
 * @param output  combined console output (stdout + stderr) from all tools
 */
public record SynthesisResult(boolean success, String output) {
}
