package com.jopdesign.core.sim;

/**
 * Snapshot of JOP stack contents.
 *
 * @param values stack values from index 0 up to (but not including) the stack pointer
 * @param sp     current stack pointer
 */
public record JopStackData(int[] values, int sp) {
}
