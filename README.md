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
- **JOP Nature & Builder** (builder stubs, not yet wired to toolchain)
- **JOP Perspective** (Package Explorer, Outline, Problems/Console layout)
- **Project Properties** (JOP_HOME, serial port, main class, output dir)
- **Workspace Preferences** (Window > Preferences > JOP)
- **Toggle JOP Nature** via project context menu (Configure > Toggle JOP Nature)

### Plugin Structure

| Module | Bundle ID | Purpose |
|--------|-----------|---------|
| `com.jopdesign.core` | Core | Nature, Builder, Preferences, Toolchain abstraction |
| `com.jopdesign.microcode` | Editor | Microcode assembly editor with full IDE support |
| `com.jopdesign.ui` | UI | Perspective, property pages, nature toggle |
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
- [ ] Configure JOP_HOME (path to jop-spinal project) in project properties
- [ ] Invoke gcc preprocessor with configurable defines (`-DSERIAL`, `-DSIMULATION`, etc.)
- [ ] Invoke Jopa assembler (jar at `java/tools/jopa/dist/lib/jopa.jar`)
- [ ] Error markers from Jopa output parsed and shown in Problems view
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
- [ ] JOP classpath container (JOP runtime + JDK stubs on Java build path)
- [ ] Builder step: PreLinker invocation after javac
- [ ] Builder step: JOPizer invocation to produce `.jop` file
- [ ] Configurable main class in project properties
- [ ] Error parsing for PreLinker/JOPizer output

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
- [ ] Board configuration wizard / property page
- [ ] Board definition files (XML/JSON) that can be extended for new boards
- [ ] Pin assignment viewer (read-only, from .qsf/.xdc files)
- [ ] JopConfig parameter editor with validation
- [ ] IO peripheral selection (future: GPIO, SPI, I2C, etc.)
- [ ] Generate SpinalHDL config Scala from UI selections

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
- [ ] Workspace preference: Quartus install path (e.g. `/opt/intelFPGA_lite/23.1std/quartus/bin`)
- [ ] Workspace preference: Vivado install path (e.g. `/opt/Xilinx/Vivado/2024.1/bin`)
- [ ] Builder step: SpinalHDL generation (`sbt runMain jop.system.JopBramTopVerilog`)
- [ ] Builder step: Invoke Quartus or Vivado synthesis
- [ ] Parse synthesis reports for resource usage, timing, warnings
- [ ] Console view for synthesis progress
- [ ] SBT install path preference

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
- [ ] Serial port selection and baud rate config
- [ ] Upload progress indicator
- [ ] Download protocol implementation in Java (replaces Python script)

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
- [ ] Launch configuration: "JOP Simulation" (JopSim)
- [ ] Cycle count display and basic profiling
- [ ] Breakpoint support via JopSim instrumentation (future)
- [ ] RTL simulation launch (sbt test, future)

### Phase 7: Multi-Core / CMP (Future)

The original JOP supported 2-4 core CMP with TDMA memory arbitration. JOP-SpinalHDL has infrastructure (`cpuId`, `cpuCnt` parameters in BmbSys) but not full implementation yet.

**When implemented, Eclipse will need:**
- [ ] Core count selection in board config (1-N)
- [ ] Memory arbiter selection (TDMA, priority, fairness)
- [ ] Per-core debug/monitor views
- [ ] Shared memory synchronisation visualisation

### Phase 8: IO / Peripheral Configuration (Future)

Currently JOP-SpinalHDL has UART and system controller. Future peripherals:

- [ ] GPIO (accent on real-time guarantees)
- [ ] SPI master/slave
- [ ] I2C
- [ ] Timer/counter with interrupt
- [ ] Ethernet MAC (from original JOP)
- [ ] Custom hardware accelerators

**Eclipse will need:**
- [ ] IO peripheral picker in board config (checkboxes + address assignment)
- [ ] Generated Java driver stubs for selected peripherals
- [ ] Address map visualisation
- [ ] Pin assignment constraint generation

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
