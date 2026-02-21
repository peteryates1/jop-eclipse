# JOP Eclipse Tooling

Eclipse plugin suite for [JOP](https://www.jopdesign.com/) (Java Optimized Processor) development. Provides an integrated environment for editing microcode, building Java applications, synthesising FPGA bitstreams, and deploying to hardware.

## Current State

### Working

- **JOP System Library**
  - Replaces the JRE System Library on the build path (`K_DEFAULT_SYSTEM`)
  - Projects compile against JOP's runtime (`java.lang.*`, `java.io.*` stubs + `com.jopdesign.sys.*`, `com.jopdesign.io.*`, `joprt.*`)
  - Immediate compile-time feedback when using unsupported APIs (e.g. `java.util.ArrayList`)
  - JRE restored automatically when JOP nature is removed
- **JOP Nature & Builder**
  - Toggle JOP Nature via Configure context menu
  - Swaps JRE <-> JOP System Library on classpath
  - Sets JDT compliance to 1.8 for IDE editing (actual build uses JDK 1.6)
  - Incremental builder: microcode assembly, JDK 1.6 compilation, PreLinker, JOPizer
- **Microcode Assembly Editor** (`.asm` files)
  - Syntax highlighting: instructions, labels, constants, variables, operators, numbers (hex/dec)
  - Content assist (Ctrl+Space) with all 65 JOP microcode instructions and documentation
  - Hover help showing opcode, dataflow, stack effect, and JVM bytecode equivalent
  - Outline view with labels, constants, and variables for navigation
  - Comment and preprocessor directive highlighting
- **Microcode Build** (Phase 1)
  - GCC preprocessor invocation with configurable defines (`-DSERIAL`, `-DSIMULATION`, etc.)
  - Jopa assembler invocation producing ROM/RAM data, jump tables, Scala output
  - Error parsing with problem markers in the Problems view
  - Incremental build: only rebuilds when `.asm`/`.inc`/`.mic` files change
  - Project-scoped preferences with workspace-level fallback
- **Java Application Build** (Phase 2)
  - External JDK 1.6 compilation (`javac -source 1.6 -target 1.6 -bootclasspath`)
  - JOP's BCEL 5.2 requires class file version 50.0 (Java 6) -- modern JDT cannot produce these
  - PreLinker invocation: merges project + runtime classes, preprocesses bytecodes
  - JOPizer invocation: produces `.jop` binary for JOP processor
  - Error parsing for javac/PreLinker/JOPizer with problem markers
  - Configurable main class and output directory in project properties
- **JOP File Viewer** (`.jop` files)
  - Text content type with read-only editor
  - `//` comment syntax highlighting (green)
  - Opens automatically instead of triggering Marketplace dialog
- **Board Configuration** (Phase 3)
  - 7 predefined board definitions in JSON (QMTECH, Alchitry Au, CYC5000, CYC1000, MAX1000, minimal)
  - Board selection dropdown with auto-populated FPGA info and defaults
  - JopConfig parameter editor: method cache, stack buffer, object/array cache with validation
  - Stack buffer: 256 words total, 0-31 variables, 32-63 constants, 64-255 usable (192 words default)
  - Memory type and boot mode selection
  - Live SpinalHDL config preview (generates Scala `JopConfig(...)` snippet)
  - ScalaConfigGenerator for programmatic access to board config
- **FPGA Synthesis** (Phase 4)
  - SpinalHDL Verilog generation via SBT (`sbt runMain {TopModule}Verilog`)
  - Quartus flow: analysis, fitter, assembler, timing analysis (4-step pipeline)
  - Vivado flow: clock wizard, MIG, project creation, bitstream (TCL script-driven)
  - Auto-selects synthesis tool based on board configuration
  - Quartus project auto-detection from `.qpf` files
  - Vivado `run_vivado.sh` wrapper support
  - Background Job execution with progress reporting
  - Dedicated "JOP FPGA Synthesis" console for output
  - "Synthesize FPGA" context menu command on JOP projects
  - Configurable SBT, Quartus, and Vivado paths in workspace preferences
- **Deploy & Monitor** (Phase 5)
  - FPGA programming via JTAG: Quartus (auto-generated CDF, USB-Blaster) and Vivado (TCL scripts)
  - Serial download of .jop files to board (delegates to download.py with echo verification)
  - Download-and-monitor mode (download.py -e flag)
  - UART monitor with real-time streaming to Eclipse console (delegates to monitor.py)
  - Shared "JOP Deploy" console with terminate support for stopping the monitor
  - "JOP" context submenu on JOP projects: Synthesize FPGA, Program FPGA, Download Application, Start Monitor
  - Auto-detection of .jop file from build output directory
  - Configurable serial port and baud rate per board
- **Simulation & Debug** (Phase 6)
  - JopSim (Java-level simulation): runs built .jop files in a bytecode interpreter
  - Statistics parsing: instruction count, cycle count, CPI extracted from output
  - Symbol file (.jop.link.txt) support for debug tracing
  - Configurable trace level (0=off, 1=method entry/exit, 2=+fields, 3=full bytecode)
  - RTL simulation via SpinalHDL/Verilator (4 predefined sim classes)
  - Dedicated "JOP Simulation" console for sim output
  - "Run JopSim" and "Run RTL Simulation" in the JOP context submenu
- **Multi-Core / CMP Configuration** (Phase 7)
  - Enable Multi-Core checkbox with CPU core count spinner (1-8)
  - Memory arbiter type selection (TDMA, Priority, Round-Robin)
  - Debug instrumentation toggle
  - CMP parameters in SpinalHDL config preview and ScalaConfigGenerator output
  - Board definitions extended with enableMultiCore, cpuCount, enableDebug fields
- **IO / Peripheral Configuration** (Phase 8)
  - Peripheral definition model with register maps, pin requirements, and SpinalHDL class names
  - 6 predefined peripherals: System Controller (slot 0, fixed), UART (slot 1, fixed), GPIO, SPI Master, I2C Master, Timer/Counter
  - IO peripheral picker in board config: checkboxes for optional peripherals with slot assignment (2 or 3)
  - Slot conflict detection with validation error display
  - Live IO address map showing allocated and available slots with register summaries
  - Java driver stub generator: produces HardwareObject-based drivers with register fields and accessor methods
  - Pin constraint template generation for Quartus (.qsf) and Vivado (.xdc)
  - IO peripheral config reflected in SpinalHDL config preview
  - "Generate IO Drivers" context menu command
- **JOP Perspective** (Package Explorer, Outline, Problems/Console, Java wizard shortcuts)
- **Project Properties** (JOP_HOME, JDK 1.6 Home, serial port, microcode defines, main class, output dir, board config)
- **Workspace Preferences** (Window > Preferences > JOP: includes FPGA tool paths)
- **Toggle JOP Nature** via project context menu (Configure > Toggle JOP Nature)
- **Test Suite** (99 tests: JUnit unit tests + SWTBot UI tests)

### Plugin Structure

| Module | Bundle ID | Purpose |
|--------|-----------|---------|
| `com.jopdesign.core` | Core | Nature, Builder, Preferences, Toolchain, Classpath, FPGA synthesis |
| `com.jopdesign.microcode` | Editor | Microcode assembly editor with full IDE support |
| `com.jopdesign.ui` | UI | Perspective, property pages, nature toggle, .jop editor, commands |
| `com.jopdesign.tests` | Tests | JUnit and SWTBot test suite |
| `com.jopdesign.feature` | Feature | Eclipse feature for p2 distribution |
| `com.jopdesign.site` | Site | p2 update site |
| `com.jopdesign.target` | Target | Target platform definition (Eclipse 2025-12) |

## Build & Install

Requires Java 21 and Maven 3.9+.

```bash
# Build (runs all 99 tests)
mvn clean verify

# Build, install to Eclipse, and launch
./install-and-test.sh

# Skip build, just reinstall and launch
./install-and-test.sh --skip-build

# Build and install only (no launch)
./install-and-test.sh --install-only
```

The install script creates a writable overlay at `~/eclipse-jop/` with JOP plugin jars and configuration, leaving the base Eclipse installation unmodified.

## Build Pipeline

```
                    HARDWARE SIDE                          SOFTWARE SIDE
                    =============                          =============

  Microcode (.asm)                               Java source (.java)
        |                                              |
        v                                              v
  gcc -E (preprocessor)                          javac 1.6 (external JDK)
        |                                              |
        v                                              v
  Jopa assembler                                 PreLinker
        |                                              |
        v                                              v
  JumpTableData.scala                            JOPizer
  mem_rom.dat, mem_ram.dat                             |
        |                                              v
        v                                        HelloWorld.jop
  SpinalHDL (sbt)                                      |
        |                                              |
        v                                              |
  Verilog (.v)                                         |
        |                                              |
        v                                              |
  Quartus / Vivado                                     |
        |                                              |
        v                                              v
  Bitstream (.sof/.bit) -----> FPGA <----- Serial upload (1 Mbaud)
                                |
                                v
                          UART monitor
```

## External Tool Dependencies

| Tool | Purpose | Required For |
|------|---------|--------------|
| Java 21 | Plugin build (Tycho/Maven) | Building this plugin |
| JDK 1.6 | JOP application compilation | Java build (javac -source 1.6 -target 1.6) |
| GCC | Microcode preprocessing | Microcode build |
| SBT | SpinalHDL/Scala build | FPGA synthesis, RTL simulation |
| Verilator | RTL simulation backend | RTL simulation |
| Quartus Prime | Intel FPGA synthesis | FPGA synthesis (Intel boards) |
| Vivado | Xilinx FPGA synthesis | FPGA synthesis (Xilinx boards) |
| Python 3 | Serial download/monitor scripts | Deploy & monitor |

## Architecture Notes

### JOP-SpinalHDL Source Layout

```
jop-spinalhdl/
  spinalhdl/src/main/scala/jop/
    JopConfig.scala          # Board & feature configuration
    JopPipeline.scala        # Pipeline stage integration
    pipeline/                # BytecodeFetch, Fetch, Decode, Stack stages
    memory/                  # BmbMemoryController, MethodCache, ObjectCache, SDRAM
    ddr3/                    # MIG wrapper, LRU cache, DDR3 bridge
    io/                      # BmbUart (UART), BmbSys (timers, watchdog, interrupts)
    core/                    # Multiplier, barrel shifter
    system/                  # FPGA top-levels: JopBramTop, JopSdramTop, JopDdr3Top
    types/                   # JopTypes, JopConstants
    utils/                   # JopFileLoader (.jop parser)
  asm/
    src/jvm.asm              # Main JVM microcode implementation
    generated/               # Jopa output (JumpTableData.scala, .dat, .mif)
    generated/serial/        # Serial variant (SerialJumpTableData.scala)
  fpga/
    qmtech-ep4cgx150-bram/   # Quartus project (BRAM, Cyclone IV)
    qmtech-ep4cgx150-sdram/  # Quartus project (SDRAM, Cyclone IV)
    alchitry-au/             # Vivado project (DDR3, Artix-7)
  java/
    tools/jopa/              # Microcode assembler
    tools/src/               # JOPizer, PreLinker, JopSim
    target/src/              # JOP runtime + JDK stubs
    target/classes/          # Compiled JOP runtime (Java 6 class files)
    apps/                    # Test applications (Smallest, Small)
  build.sbt                  # SBT build (Scala 2.13, SpinalHDL 1.12.2)
```

### Memory Map

```
0x00000000 - 0x3FFFFFFF  Main memory (BRAM/SDRAM/DDR3)
0x40000000 - 0x7FFFFFFF  Reserved
0x80000000 - 0xBFFFFFFF  Scratch pad
0xC0000000 - 0xFFFFFFFF  I/O space
  Slave 0 (0x00-0x0F)    System: counter, timer, watchdog, exceptions, CPU ID
  Slave 1 (0x10-0x1F)    UART: status, TX/RX data (16-entry FIFOs)
  Slave 2 (0x20-0x2F)    Configurable (GPIO, SPI, I2C, Timer)
  Slave 3 (0x30-0x3F)    Configurable (GPIO, SPI, I2C, Timer)
```

### Microcode Instruction Set

65 instructions defined in `microcode.md`. Key categories:
- **Stack**: `nop`, `pop`, `and`, `or`, `xor`, `add`, `sub`, `dup`, `swap`, ...
- **Memory**: `ld`, `st`, `ldm`, `ldi`, `stm`, `stmra`, `stmwd`, `stmraf`, ...
- **Control**: `bnz`, `bz`, `jbr`, `wait`, `jmp`, ...
- **Special**: `stgf`, `stpf`, `stcp`, `ldmrd`, `star`, `stps`, `stidx`, ...

### On-Chip Stack Buffer

The JOP stack buffer is 256 words of on-chip RAM:
- Slots 0-31: local variables (frame pointer relative)
- Slots 32-63: constants (method constants area)
- Slots 64-255: operand stack (192 usable words)

The `stackBufferSize` parameter refers to the usable stack portion (default 192).
