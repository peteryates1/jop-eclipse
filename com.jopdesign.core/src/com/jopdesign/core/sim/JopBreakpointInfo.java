package com.jopdesign.core.sim;

/**
 * Information about an active breakpoint on a JOP target.
 *
 * @param slot    hardware breakpoint slot index
 * @param type    breakpoint type (micro PC or bytecode JPC)
 * @param address the address the breakpoint is set at
 */
public record JopBreakpointInfo(int slot, JopBreakpointType type, int address) {
}
