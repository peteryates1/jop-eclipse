package com.jopdesign.core.sim;

/**
 * Snapshot of a region of JOP memory.
 *
 * @param startAddress starting address of the memory region
 * @param values       memory values starting at startAddress
 */
public record JopMemoryData(int startAddress, int[] values) {
}
