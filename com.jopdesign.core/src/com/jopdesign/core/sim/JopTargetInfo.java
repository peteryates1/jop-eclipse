package com.jopdesign.core.sim;

/**
 * Capability and configuration information about a JOP target.
 *
 * @param numCores       number of JOP cores
 * @param numBreakpoints number of hardware breakpoint slots
 * @param stackDepth     stack depth in words
 * @param memorySize     memory size in words
 * @param version        target version string
 */
public record JopTargetInfo(int numCores, int numBreakpoints, int stackDepth, int memorySize, String version) {
}
