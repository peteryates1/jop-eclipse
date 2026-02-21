# JOP Eclipse Tooling

Eclipse plugin suite for [JOP](https://www.jopdesign.com/) (Java Optimized Processor) development. Provides an integrated environment for editing microcode, building Java applications, synthesising FPGA bitstreams, and deploying to hardware.

## Current State

### Working

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
  - JOP Runtime classpath container (adds `target/classes` to Java build path)
  - PreLinker invocation: merges project + runtime classes, preprocesses bytecodes
  - JOPizer invocation: produces `.jop` binary for JOP processor
  - Error parsing for PreLinker/JOPizer with problem markers
  - Configurable main class and output directory in project properties
- **Board Configuration** (Phase 3)
  - 7 predefined board definitions in JSON (QMTECH, Alchitry Au, CYC5000, CYC1000, MAX1000, minimal)
  - Board selection dropdown with auto-populated FPGA info and defaults
  - JopConfig parameter editor: method cache, stack buffer, object/array cache with validation
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
  - Ready for hardware-side CMP integration (BmbSys cpuId/cpuCnt, Java Scheduler arrays)
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
- **JOP Nature & Builder** (wired to microcode + Java toolchain)
- **JOP Perspective** (Package Explorer, Outline, Problems/Console layout)
- **Project Properties** (JOP_HOME, serial port, microcode defines, main class, output dir, board config)
- **Workspace Preferences** (Window > Preferences > JOP: includes FPGA tool paths)
- **Toggle JOP Nature** via project context menu (Configure > Toggle JOP Nature)

### Plugin Structure

| Module | Bundle ID | Purpose |
|--------|-----------|---------|
| `com.jopdesign.core` | Core | Nature, Builder, Preferences, Toolchain, FPGA synthesis |
| `com.jopdesign.microcode` | Editor | Microcode assembly editor with full IDE support |
| `com.jopdesign.ui` | UI | Perspective, property pages, nature toggle, FPGA synthesis command |
| `com.jopdesign.feature` | Feature | Eclipse feature for p2 distribution |
| `com.jopdesign.site` | Site | p2 update site |
| `com.jopdesign.target` | Target | Target platform definition (Eclipse 2025-12) |

## Build & Install

Requires Java 21 and Maven 3.9+.

```bash
# Build
mvn clean verify

# Build, install to Eclipse, and launch
./install-and-test.sh

# Skip build, just reinstall and launch
./install-and-test.sh --skip-build

# Build and install only (no launch)
./install-and-test.sh --install-only
```

The install script copies plugin jars to the p2 pool at `/opt/.p2/pool/plugins/` and registers them in Eclipse's `bundles.info`.

## JOP Project References

| Path | Description |
|------|-------------|
| `/home/peter/git/jop` | Original VHDL JOP by Martin Schoeberl (canonical reference) |
| `/home/peter/git/jopmin` | Minimal JOP configuration attempt |
| `/home/peter/workspaces/ai/jop` | **JOP-SpinalHDL** - active SpinalHDL/Scala refactor (primary target) |

---

## Next Steps: Full Build Integration

The goal is to drive the entire JOP build pipeline from Eclipse: edit code, configure hardware, build, synthesise, program FPGA, upload application, and monitor output.

### Build Pipeline Overview

```
                    HARDWARE SIDE                          SOFTWARE SIDE
                    =============                          =============

  Microcode (.asm)                               Java source (.java)
        |                                              |
        v                                              v
  gcc -E (preprocessor)                          javac (JDK 1.6 target)
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

### Phase 1: Microcode Build (Jopa Integration)

Wire the JOP Builder to invoke the microcode assembler.

**Toolchain steps:**
1. **Preprocess**: `gcc -x c -E -C -P -D{JVM_TYPE} src/jvm.asm > generated/jvmgen.asm`
2. **Assemble**: `java -jar jopa.jar -s generated -d generated jvmgen.asm`

**Outputs:** `JumpTableData.scala`, `mem_rom.dat`, `mem_ram.dat`, `rom.mif`

**Eclipse integration needed:**
- [x] Configure JOP_HOME (path to jop-spinal project) in project properties
- [x] Invoke gcc preprocessor with configurable defines (`-DSERIAL`, `-DSIMULATION`, etc.)
- [x] Invoke Jopa assembler (jar at `java/tools/jopa/dist/lib/jopa.jar`)
- [x] Error markers from Jopa output parsed and shown in Problems view
- [ ] Console output for build progress

### Phase 2: Java Application Build (JOPizer Integration)

**Toolchain steps:**
1. **Compile**: `javac -source 1.6 -target 1.6` against JOP runtime + JDK stubs
2. **PreLink**: `java com.jopdesign.build.PreLinker -c classes -o pp MainClass`
3. **JOPize**: `java -Dmgci=false com.jopdesign.build.JOPizer -cp pp -o App.jop MainClass`

**Key classpaths:**
- JOP runtime: `java/target/src/common/`, `java/target/src/jdk_base/`, `java/target/src/jvm/`
- Build tools: `bcel-5.2.jar`, `jakarta-regexp-1.3.jar`, `log4j-1.2.15.jar`
- Jopa: `java/tools/jopa/dist/lib/jopa.jar`
- JOPizer/PreLinker: `java/tools/dist/jopizer.jar`

**Eclipse integration needed:**
- [x] JOP classpath container (JOP runtime + JDK stubs on Java build path)
- [x] Builder step: PreLinker invocation after javac
- [x] Builder step: JOPizer invocation to produce `.jop` file
- [x] Configurable main class in project properties
- [x] Error parsing for PreLinker/JOPizer output

### Phase 3: BSP / Board Configuration

Central configuration UI for selecting and configuring target hardware.

**Board definitions need:**

| Property | Examples | Notes |
|----------|----------|-------|
| Board name | QMTECH EP4CGX150, Alchitry Au V2, MAX1000 | Dropdown selection |
| FPGA family | Cyclone IV, Artix-7, MAX10 | Drives tool selection |
| FPGA device | EP4CGX150DF27I7, XC7A35T, 10M08SAU169C8G | For synthesis constraints |
| Synth tool | Quartus / Vivado | Must be configured in preferences |
| Clock input | 50 MHz | Board-specific |
| System clock | 100 MHz | PLL-derived |
| Memory type | BRAM / SDRAM / DDR3 | Determines top-level module |
| Memory size | 32KB (BRAM), 16MB (SDRAM), 256MB (DDR3) | |
| Boot mode | BRAM (embedded) / Serial | Determines JumpTable variant |
| UART baud | 1000000 | For serial boot and monitor |
| Serial port | /dev/ttyUSB0 | Host-side |

**JOP hardware configuration (drives SpinalHDL generation):**

| Parameter | Range | Default | Description |
|-----------|-------|---------|-------------|
| `methodCacheSize` | 1024-8192 | 4096 | Bytecode method cache (bytes) |
| `stackBufferSize` | 16-128 | 64 | On-chip stack buffer (words) |
| `useOcache` | true/false | true | Object field cache |
| `ocacheWayBits` | 2-4 | 4 | Object cache associativity (2^n ways) |
| `useAcache` | true/false | true | Array element cache |
| `acacheWayBits` | 2-4 | 4 | Array cache associativity (2^n ways) |
| `enableMultiCore` | true/false | false | CMP support (future) |
| `enableDebug` | true/false | false | Debug instrumentation |

**Predefined board configs (from JopConfig.scala):**
- `ep4cgx150df27` - QMTECH Cyclone IV (8KB cache, 128-word stack)
- `alchitryAu` - Xilinx Artix-7 (4KB cache, 64-word stack)
- `max1000` - Intel MAX10 (2KB cache, 32-word stack)
- `cyc5000` - Cyclone V (8KB cache, 128-word stack)
- `cyc1000` - Cyclone 10 LP (2KB cache, 32-word stack)
- `minimal` - Testing/simulation (1KB cache, no object/array cache)

**Eclipse integration needed:**
- [x] Board configuration wizard / property page
- [x] Board definition files (XML/JSON) that can be extended for new boards
- [ ] Pin assignment viewer (read-only, from .qsf/.xdc files)
- [x] JopConfig parameter editor with validation
- [ ] IO peripheral selection (future: GPIO, SPI, I2C, etc.)
- [x] Generate SpinalHDL config Scala from UI selections

### Phase 4: FPGA Synthesis Integration

**Quartus flow (Intel/Altera):**
```bash
quartus_map --read_settings_files=on jop_bram
quartus_fit --read_settings_files=on jop_bram
quartus_asm jop_bram
quartus_sta jop_bram
```

**Vivado flow (Xilinx):**
```bash
vivado -mode batch -source create_project.tcl
vivado -mode batch -source build_bitstream.tcl
```

**Eclipse integration needed:**
- [x] Workspace preference: Quartus install path (e.g. `/opt/intelFPGA_lite/23.1std/quartus/bin`)
- [x] Workspace preference: Vivado install path (e.g. `/opt/Xilinx/Vivado/2024.1/bin`)
- [x] Builder step: SpinalHDL generation (`sbt runMain jop.system.JopBramTopVerilog`)
- [x] Builder step: Invoke Quartus or Vivado synthesis
- [ ] Parse synthesis reports for resource usage, timing, warnings
- [x] Console view for synthesis progress
- [x] SBT install path preference

### Phase 5: Deploy & Monitor

**Programming:**
- Quartus: `quartus_pgm -c "USB-Blaster" -m JTAG project.cdf`
- Vivado: `vivado -mode batch -source program.tcl`

**Serial upload (.jop file):**
- Python: `download.py App.jop /dev/ttyUSB0` (byte-by-byte with echo verification)
- Protocol: 4-byte words MSB-first, 1 Mbaud, 8-N-1

**UART monitor:**
- `monitor.py /dev/ttyUSB0 1000000`

**Eclipse integration needed:**
- [ ] Launch configuration: "JOP Application" (program FPGA + upload + monitor)
- [ ] Launch configuration: "JOP BRAM" (program FPGA with embedded app)
- [ ] Serial console view (jSerialComm library, replaces old RXTX)
- [x] Serial port selection and baud rate config
- [x] Upload progress indicator
- [ ] Download protocol implementation in Java (replaces Python script)

**Implemented via context menu commands (right-click JOP project > JOP):**
- [x] Program FPGA (Quartus CDF generation + quartus_pgm, Vivado TCL)
- [x] Download Application (invokes download.py with configured serial port)
- [x] Start Monitor (invokes monitor.py with streaming console output)
- [x] Shared "JOP Deploy" console with terminate support

### Phase 6: Simulation & Debug

**JopSim (Java-level simulation):**
```bash
java -cp jopsim.jar com.jopdesign.tools.JopSim -link App.jop.link.txt App.jop
```

**Verilator (RTL simulation via SpinalHDL):**
```bash
sbt "testOnly jop.test.JopCoreBramSim"
```

**Eclipse integration needed:**
- [x] Launch configuration: "JOP Simulation" (JopSim) — implemented as context menu command
- [x] Cycle count display and basic profiling
- [ ] Breakpoint support via JopSim instrumentation (future)
- [x] RTL simulation launch (sbt "Test / runMain") — implemented as context menu command

### Phase 7: Multi-Core / CMP (Future)

The original JOP supported 2-4 core CMP with TDMA memory arbitration. JOP-SpinalHDL has infrastructure (`cpuId`, `cpuCnt` parameters in BmbSys) but not full implementation yet.

**Eclipse integration:**
- [x] Core count selection in board config (1-8)
- [x] Memory arbiter selection (TDMA, Priority, Round-Robin)
- [x] CMP parameters in ScalaConfigGenerator and config preview
- [ ] Per-core debug/monitor views (requires hardware CMP integration)
- [ ] Shared memory synchronisation visualisation (requires hardware CMP integration)

### Phase 8: IO / Peripheral Configuration

Currently JOP-SpinalHDL has UART and system controller. Future peripherals (definitions ready, hardware implementation pending):

- [x] GPIO (accent on real-time guarantees) — definition with 32-bit bidirectional, atomic set/clear/toggle
- [x] SPI master — definition with configurable CPOL/CPHA, clock divider, chip select
- [x] I2C master — definition with 7-bit addressing, prescaler, start/stop generation
- [x] Timer/counter with interrupt — definition with 2 channels, compare match, auto-reload
- [ ] Ethernet MAC (from original JOP)
- [ ] Custom hardware accelerators

**Eclipse integration:**
- [x] IO peripheral picker in board config (checkboxes + slot assignment, conflict detection)
- [x] Generated Java driver stubs for selected peripherals (HardwareObject pattern)
- [x] Address map visualisation (shows slots, peripherals, register summaries)
- [x] Pin assignment constraint generation (Quartus .qsf and Vivado .xdc templates)

---

## Architecture Notes

### JOP-SpinalHDL Source Layout

```
jop/
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
  fpga/
    qmtech-ep4cgx150-bram/   # Quartus project (BRAM, Cyclone IV)
    qmtech-ep4cgx150-sdram/  # Quartus project (SDRAM, Cyclone IV)
    alchitry-au/             # Vivado project (DDR3, Artix-7)
  java/
    tools/jopa/              # Microcode assembler
    tools/src/               # JOPizer, PreLinker, JopSim
    target/src/              # JOP runtime + JDK stubs
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
```

### Microcode Instruction Set

65 instructions defined in `microcode.md`. Key categories:
- **Stack**: `nop`, `pop`, `and`, `or`, `xor`, `add`, `sub`, `dup`, `swap`, ...
- **Memory**: `ld`, `st`, `ldm`, `ldi`, `stm`, `stmra`, `stmwd`, `stmraf`, ...
- **Control**: `bnz`, `bz`, `jbr`, `wait`, `jmp`, ...
- **Special**: `stgf`, `stpf`, `stcp`, `ldmrd`, `star`, `stps`, `stidx`, ...

### External Tool Dependencies

| Tool | Purpose | Required For |
|------|---------|--------------|
| Java 21 | Plugin build (Tycho/Maven) | Building this plugin |
| Java 8+ | JOP toolchain (Jopa, JOPizer) | Microcode & Java builds |
| GCC | Microcode preprocessing | Phase 1 |
| SBT | SpinalHDL/Scala build | Phase 4 |
| Quartus Prime | Intel FPGA synthesis | Phase 4 (Intel boards) |
| Vivado | Xilinx FPGA synthesis | Phase 4 (Xilinx boards) |
| Python 3 | Serial download/monitor scripts | Phase 5 (until replaced) |
| jSerialComm | Java serial library (planned) | Phase 5 |
