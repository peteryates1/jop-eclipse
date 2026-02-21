# JOP Debug Protocol Specification

## Overview

This document defines the debug interface requirements for JOP hardware
(FPGA and RTL simulation). The protocol runs over USB-serial and enables
Eclipse to halt, step, inspect, and control a running JOP processor.

The same protocol is used for both FPGA hardware and RTL simulation targets.
For RTL sim the transport may be a socket/pipe instead of serial, but the
message format is identical.

For CMP (multi-core) configurations, a single debug interface multiplexes
access to all cores via a core ID field in each request.

## Transport

- USB-serial, 1Mbaud or higher
- 8N1 framing
- Binary protocol (no text encoding overhead)
- Request/response with asynchronous notifications from target
- Little-endian byte order (matches JOP memory)

## Message Format

All messages are binary with the following structure:

```
+--------+--------+--------+--------+--------+--- ... ---+--------+
| SYNC   | TYPE   | LEN_LO | LEN_HI | CORE   | PAYLOAD   | CRC8   |
+--------+--------+--------+--------+--------+--- ... ---+--------+
```

- **SYNC** (1 byte): `0xA5` — frame synchronization
- **TYPE** (1 byte): message type (see below)
- **LEN** (2 bytes): payload length in bytes (little-endian, 0 if no payload)
- **CORE** (1 byte): core ID (0 for single-core, 0..N-1 for CMP)
- **PAYLOAD** (variable): type-specific data
- **CRC8** (1 byte): CRC-8/MAXIM over TYPE+LEN+CORE+PAYLOAD

Total overhead per message: 6 bytes.

## Message Types

### Host → Target (Requests)

| Type | Name            | Payload | Description                        |
|------|-----------------|---------|------------------------------------|
| 0x01 | HALT            | none    | Halt the CPU                       |
| 0x02 | RESUME          | none    | Resume execution                   |
| 0x03 | STEP_MICRO      | none    | Execute one microcode instruction  |
| 0x04 | STEP_BYTECODE   | none    | Execute until JPC changes          |
| 0x05 | RESET           | none    | Reset the CPU                      |
| 0x06 | QUERY_STATUS    | none    | Query halted/running state         |
| 0x10 | READ_REGISTERS  | none    | Read all processor registers       |
| 0x11 | READ_STACK      | 4      | Read stack words                   |
| 0x12 | READ_MEMORY     | 8      | Read memory block                  |
| 0x13 | WRITE_REGISTER  | 5      | Write a single register            |
| 0x14 | WRITE_MEMORY    | 8      | Write a single memory word         |
| 0x20 | SET_BREAKPOINT  | 5      | Set a hardware breakpoint          |
| 0x21 | CLEAR_BREAKPOINT| 1      | Clear a breakpoint by slot         |
| 0x22 | QUERY_BREAKPOINTS| none   | List active breakpoints            |
| 0xF0 | PING            | none    | Connection test                    |
| 0xF1 | QUERY_INFO      | none    | Query target capabilities          |

### Target → Host (Responses)

| Type | Name            | Payload | Description                        |
|------|-----------------|---------|------------------------------------|
| 0x80 | ACK             | 0       | Success, no data                   |
| 0x81 | NAK             | 1       | Error (error code in payload)      |
| 0x82 | REGISTERS       | 48      | Register dump (response to 0x10)   |
| 0x83 | STACK_DATA      | variable| Stack words (response to 0x11)     |
| 0x84 | MEMORY_DATA     | variable| Memory words (response to 0x12)    |
| 0x85 | STATUS          | 2       | CPU status (response to 0x06)      |
| 0x86 | BREAKPOINT_LIST | variable| Active breakpoints (resp to 0x22)  |
| 0x87 | TARGET_INFO     | variable| Capabilities (response to 0xF1)    |
| 0x88 | PONG            | none    | Response to PING                   |

### Target → Host (Asynchronous Notifications)

| Type | Name            | Payload | Description                        |
|------|-----------------|---------|------------------------------------|
| 0xC0 | HALTED          | 3       | CPU halted (breakpoint/step/fault) |

## Payload Definitions

### HALT (0x01)
No payload. Target halts the CPU at the next instruction boundary.
Due to pipeline depth (3 stages for microcode, 4 for bytecode), the actual
halt PC may be a few instructions past the point where HALT was received.
The HALTED notification reports the exact PC where execution stopped.

### RESUME (0x02)
No payload. Target resumes execution from current PC. If a breakpoint is
set at the current PC, the target must single-step past it before resuming.

### STEP_MICRO (0x03)
No payload. Execute exactly one microcode instruction, then halt.
Target responds with ACK, then sends HALTED notification when the step
completes. The HALTED payload contains the new PC.

### STEP_BYTECODE (0x04)
No payload. Execute microcode instructions until JPC changes (i.e. one
complete bytecode execution), then halt. Target responds with ACK, then
sends HALTED notification.

### RESET (0x05)
No payload. Reset the CPU to initial state. Target responds with ACK,
then sends HALTED notification (CPU is halted after reset at PC=0).

### QUERY_STATUS (0x06)
No payload. Target responds with STATUS.

### READ_REGISTERS (0x10)
No payload. Target responds with REGISTERS containing all processor state.
Only valid when CPU is halted (NAK with error 0x01 if running).

### READ_STACK (0x11)
Payload (4 bytes):
```
+--------+--------+--------+--------+
| OFFSET | OFFSET | COUNT  | COUNT  |
| LO     | HI     | LO     | HI     |
+--------+--------+--------+--------+
```
- OFFSET: stack offset (word index from bottom, little-endian)
- COUNT: number of 32-bit words to read (little-endian)

Target responds with STACK_DATA containing COUNT 32-bit words.
Only valid when halted.

### READ_MEMORY (0x12)
Payload (8 bytes):
```
+--------+--------+--------+--------+--------+--------+--------+--------+
| ADDR                               | COUNT                            |
| (32-bit LE)                        | (32-bit LE)                      |
+--------+--------+--------+--------+--------+--------+--------+--------+
```
- ADDR: memory word address (32-bit, little-endian)
- COUNT: number of 32-bit words to read

Target responds with MEMORY_DATA. Only valid when halted.
Maximum COUNT per request: 256 words (1KB).

### WRITE_REGISTER (0x13)
Payload (5 bytes):
```
+--------+--------+--------+--------+--------+
| REG_ID | VALUE (32-bit LE)                  |
+--------+--------+--------+--------+--------+
```
REG_ID values — see register table below. Target responds with ACK.
Only valid when halted.

### WRITE_MEMORY (0x14)
Payload (8 bytes):
```
+--------+--------+--------+--------+--------+--------+--------+--------+
| ADDR (32-bit LE)                   | VALUE (32-bit LE)                |
+--------+--------+--------+--------+--------+--------+--------+--------+
```
Write a single 32-bit word to memory. Target responds with ACK.
Only valid when halted.

### SET_BREAKPOINT (0x20)
Payload (5 bytes):
```
+--------+--------+--------+--------+--------+
| BP_TYPE| ADDRESS (32-bit LE)                |
+--------+--------+--------+--------+--------+
```
- BP_TYPE: 0x00 = microcode PC breakpoint, 0x01 = bytecode JPC breakpoint
- ADDRESS: PC or JPC value to break on

Target responds with ACK if a breakpoint slot is available, or NAK with
error 0x02 (no free slots) if all hardware breakpoints are in use.
The ACK payload (1 byte) contains the assigned slot number (0..N-1).

Hardware should support 2-4 simultaneous breakpoints. Eclipse manages
the mapping from user breakpoints to hardware slots.

### CLEAR_BREAKPOINT (0x21)
Payload (1 byte): breakpoint slot number to clear.
Target responds with ACK.

### QUERY_BREAKPOINTS (0x22)
No payload. Target responds with BREAKPOINT_LIST.

### PING (0xF0)
No payload. Target responds with PONG. Used for connection detection
and keepalive.

### QUERY_INFO (0xF1)
No payload. Target responds with TARGET_INFO containing hardware
capabilities so Eclipse can adapt its UI.

## Response Payloads

### REGISTERS (0x82)
48 bytes — twelve 32-bit registers in fixed order:
```
Offset  Register       REG_ID
0       PC             0x00    microcode program counter
4       JPC            0x01    Java bytecode program counter
8       A (TOS)        0x02    top of stack
12      B (NOS)        0x03    next on stack
16      SP             0x04    stack pointer
20      VP             0x05    variable pointer
24      AR             0x06    address register
28      MUL_RESULT     0x07    multiply result
32      MEM_RD_ADDR    0x08    memory read address
36      MEM_WR_ADDR    0x09    memory write address
40      MEM_RD_DATA    0x0A    memory read data
44      MEM_WR_DATA    0x0B    memory write data
```

### STACK_DATA (0x83)
Variable length: COUNT * 4 bytes of 32-bit words, little-endian.

### MEMORY_DATA (0x84)
Variable length: COUNT * 4 bytes of 32-bit words, little-endian.

### STATUS (0x85)
2 bytes:
```
+--------+--------+
| STATE  | REASON |
+--------+--------+
```
- STATE: 0x00 = running, 0x01 = halted
- REASON (if halted): 0x00 = manual halt, 0x01 = breakpoint,
  0x02 = step complete, 0x03 = reset, 0x04 = fault

### HALTED (0xC0) — Asynchronous Notification
3 bytes:
```
+--------+--------+--------+
| REASON | DATA_LO| DATA_HI|
+--------+--------+--------+
```
- REASON: same as STATUS reason codes
- DATA: breakpoint slot number (if REASON=breakpoint), otherwise 0

Sent unsolicited by the target whenever the CPU transitions from running
to halted. Eclipse uses this to update the debug view immediately rather
than polling. After receiving HALTED, Eclipse typically issues
READ_REGISTERS to get the full processor state.

### BREAKPOINT_LIST (0x86)
Variable: N * 6 bytes, one entry per active breakpoint:
```
+--------+--------+--------+--------+--------+--------+
| SLOT   | TYPE   | ADDRESS (32-bit LE)                |
+--------+--------+--------+--------+--------+--------+
```

### TARGET_INFO (0x87)
Variable length, structured as tag-value pairs:
```
+--------+--------+--- ... ---+
| TAG    | LEN    | VALUE     |
+--------+--------+--- ... ---+
```
Tags:
- 0x01: NUM_CORES (1 byte) — number of CPU cores
- 0x02: NUM_BREAKPOINTS (1 byte) — hardware breakpoint slots per core
- 0x03: STACK_DEPTH (2 bytes LE) — on-chip stack depth in words
- 0x04: MEMORY_SIZE (4 bytes LE) — total memory in words
- 0x05: MICRO_PIPELINE_DEPTH (1 byte) — microcode pipeline stages
- 0x06: BYTECODE_PIPELINE_DEPTH (1 byte) — bytecode pipeline stages
- 0x07: VERSION (variable) — firmware/RTL version string (UTF-8)

### NAK Error Codes

| Code | Meaning                                |
|------|----------------------------------------|
| 0x01 | CPU not halted (register/memory read while running) |
| 0x02 | No free breakpoint slots               |
| 0x03 | Invalid register ID                    |
| 0x04 | Invalid memory address                 |
| 0x05 | Invalid breakpoint slot                |
| 0xFF | Unknown/internal error                 |

## Timing and Flow

### Typical Debug Session
```
Host                          Target
  |                              |
  |--- PING ------------------->|
  |<-- PONG --------------------|
  |--- QUERY_INFO -------------->|
  |<-- TARGET_INFO --------------|
  |--- HALT ------------------->|
  |<-- ACK ---------------------|
  |<-- HALTED (reason=manual) --|  (async notification)
  |--- READ_REGISTERS --------->|
  |<-- REGISTERS ---------------|
  |--- READ_STACK 0,128 ------->|
  |<-- STACK_DATA ---------------|
  |--- SET_BREAKPOINT PC=42 --->|
  |<-- ACK (slot=0) ------------|
  |--- RESUME ------------------>|
  |<-- ACK ---------------------|
  |         ... running ...      |
  |<-- HALTED (reason=bp, 0) ---|  (async: hit breakpoint)
  |--- READ_REGISTERS --------->|
  |<-- REGISTERS ---------------|
  |--- STEP_MICRO -------------->|
  |<-- ACK ---------------------|
  |<-- HALTED (reason=step) ----|
  |--- READ_REGISTERS --------->|
  |<-- REGISTERS ---------------|
```

### Response Timeout
Host should use a 500ms timeout for responses. If no response is received,
the host may retry once, then report a communication error.

### Async Notification Handling
The host must be prepared to receive a HALTED notification at any time
after RESUME, even interleaved with a response to another command. The
SYNC byte (0xA5) and TYPE field distinguish notifications from responses.

## CMP (Multi-Core) Considerations

- Each request includes a CORE field selecting which core to operate on
- HALT/RESUME/STEP operate on individual cores
- HALTED notifications include the core ID in the CORE field of the header
- READ_MEMORY accesses shared memory (CORE field ignored for memory ops)
- READ_STACK/READ_REGISTERS are per-core
- Breakpoints are per-core
- QUERY_INFO returns global info (NUM_CORES) plus per-core capabilities

## Implementation Notes for Hardware

### Minimal Implementation
A minimal debug interface needs only:
- PING/PONG (connection test)
- HALT/RESUME (execution control)
- STEP_MICRO (single stepping)
- READ_REGISTERS (state inspection)
- HALTED notification (async halt notification)

This is enough for Eclipse to provide basic step-through debugging.
Everything else (breakpoints, memory read, stack read, bytecode stepping)
can be added incrementally.

### FPGA Resource Estimate
- Debug controller FSM: ~200-400 LUTs
- Serial UART (1Mbaud): ~100 LUTs
- CRC8 calculation: ~30 LUTs
- Per breakpoint comparator: ~40 LUTs
- Register mux/readout: ~200 LUTs
- Total estimate: ~600-900 LUTs for a 4-breakpoint single-core implementation

### RTL Simulation Target
For Verilator/GHDL simulation, the debug interface can be implemented as
a C++/VHDL testbench module that speaks the same protocol over a Unix
socket or named pipe. This allows Eclipse to debug RTL simulations using
the same code path as FPGA hardware.
