package com.jopdesign.core.io;

import java.util.List;
import java.util.Map;

/**
 * Generates Java driver stub source code for JOP IO peripherals.
 *
 * <p>Generated drivers follow the JOP {@code HardwareObject} pattern:
 * volatile fields mapped to IO register addresses, accessed through
 * the {@code IOFactory} singleton. Each generated class extends
 * {@code com.jopdesign.sys.HardwareObject}.
 *
 * <p>Example output for GPIO:
 * <pre>
 * public class GpioDevice extends HardwareObject {
 *     public volatile int dir;
 *     public volatile int out;
 *     public volatile int in;
 *     ...
 * }
 * </pre>
 */
public final class DriverStubGenerator {

	private DriverStubGenerator() {}

	/**
	 * Generate a complete Java driver source file for a peripheral.
	 *
	 * @param peripheral the peripheral definition
	 * @param slot the assigned IO slave slot (0-3)
	 * @param packageName the Java package for the generated class
	 * @return compilable Java source code
	 */
	public static String generate(PeripheralDefinition peripheral, int slot,
			String packageName) {
		StringBuilder sb = new StringBuilder();

		// Package declaration
		sb.append("package ").append(packageName).append(";\n\n");

		// Imports
		sb.append("import com.jopdesign.sys.HardwareObject;\n");
		sb.append("import com.jopdesign.sys.IOFactory;\n\n");

		// Class javadoc
		sb.append("/**\n");
		sb.append(" * JOP hardware driver for ").append(peripheral.name()).append(".\n");
		sb.append(" *\n");
		sb.append(" * <p>").append(peripheral.description()).append("\n");
		sb.append(" *\n");
		sb.append(" * <p>IO Address Range: ").append(peripheral.addressRange(slot)).append("\n");
		sb.append(" * (Slave slot ").append(slot).append(")\n");
		sb.append(" *\n");
		sb.append(" * <p><b>Auto-generated stub.</b> Extend with application-specific methods.\n");
		sb.append(" */\n");

		// Class declaration
		sb.append("public class ").append(peripheral.driverClassName());
		sb.append(" extends HardwareObject {\n\n");

		// Register fields
		List<PeripheralRegister> regs = peripheral.registers();
		int prevOffset = -1;
		for (PeripheralRegister reg : regs) {
			// Add padding fields for gaps in the register map
			for (int gap = prevOffset + 1; gap < reg.offset(); gap++) {
				sb.append("\t/** Reserved (offset ").append(gap).append(") */\n");
				sb.append("\tpublic volatile int _reserved").append(gap).append(";\n\n");
			}

			sb.append("\t/** ").append(reg.description());
			sb.append(" [").append(reg.access()).append("]");
			sb.append(" */\n");
			sb.append("\tpublic volatile int ").append(toFieldName(reg.name()));
			sb.append(";\n\n");
			prevOffset = reg.offset();
		}

		// Factory method
		sb.append("\t// ---- Factory ----\n\n");
		sb.append("\tprivate static ").append(peripheral.driverClassName());
		sb.append(" instance;\n\n");

		sb.append("\t/**\n");
		sb.append("\t * Get the singleton ").append(peripheral.name());
		sb.append(" device instance.\n");
		sb.append("\t *\n");
		sb.append("\t * @return the ").append(peripheral.driverClassName());
		sb.append(" mapped to IO slot ").append(slot).append("\n");
		sb.append("\t */\n");
		sb.append("\tpublic static ").append(peripheral.driverClassName());
		sb.append(" getInstance() {\n");
		sb.append("\t\tif (instance == null) {\n");
		sb.append("\t\t\tinstance = (").append(peripheral.driverClassName());
		sb.append(") IOFactory.getFactory().createHardwareObject(\n");
		sb.append("\t\t\t\t\t\"").append(packageName).append(".")
				.append(peripheral.driverClassName()).append("\",\n");
		sb.append("\t\t\t\t\t0x").append(String.format("%02x", slot * 16));
		sb.append(");\n");
		sb.append("\t\t}\n");
		sb.append("\t\treturn instance;\n");
		sb.append("\t}\n");

		// Convenience methods for common register patterns
		generateConvenienceMethods(sb, peripheral);

		sb.append("}\n");
		return sb.toString();
	}

	/**
	 * Generate driver stubs for all enabled peripherals.
	 *
	 * @param enabledPeripherals map of peripheral ID to assigned slot
	 * @param packageName target Java package
	 * @return map of class name to generated source code
	 */
	public static Map<String, String> generateAll(
			Map<String, Integer> enabledPeripherals, String packageName) {
		Map<String, String> result = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : enabledPeripherals.entrySet()) {
			PeripheralDefinition p = PeripheralRegistry.getPeripheral(entry.getKey());
			if (p != null && !p.fixed()) {
				String source = generate(p, entry.getValue(), packageName);
				result.put(p.driverClassName() + ".java", source);
			}
		}
		return result;
	}

	/**
	 * Generate pin constraint snippets for the selected peripherals.
	 *
	 * @param enabledPeripherals map of peripheral ID to assigned slot
	 * @param synthTool "quartus" or "vivado"
	 * @return constraint file content (.qsf or .xdc format)
	 */
	public static String generatePinConstraints(
			Map<String, Integer> enabledPeripherals, String synthTool) {
		StringBuilder sb = new StringBuilder();

		if ("vivado".equals(synthTool)) {
			sb.append("# JOP IO Peripheral Pin Constraints (XDC format)\n");
			sb.append("# Auto-generated — assign PACKAGE_PIN values for your board\n\n");
			for (Map.Entry<String, Integer> entry : enabledPeripherals.entrySet()) {
				PeripheralDefinition p = PeripheralRegistry.getPeripheral(entry.getKey());
				if (p == null || p.pins().isEmpty()) continue;
				sb.append("# ---- ").append(p.name())
						.append(" (slot ").append(entry.getValue()).append(") ----\n");
				for (var pin : p.pins()) {
					sb.append("# set_property PACKAGE_PIN <PIN> [get_ports {")
							.append(pin.name()).append("}]\n");
					sb.append("# set_property IOSTANDARD LVCMOS33 [get_ports {")
							.append(pin.name()).append("}]\n");
				}
				sb.append('\n');
			}
		} else {
			sb.append("# JOP IO Peripheral Pin Constraints (QSF format)\n");
			sb.append("# Auto-generated — assign PIN_xx values for your board\n\n");
			for (Map.Entry<String, Integer> entry : enabledPeripherals.entrySet()) {
				PeripheralDefinition p = PeripheralRegistry.getPeripheral(entry.getKey());
				if (p == null || p.pins().isEmpty()) continue;
				sb.append("# ---- ").append(p.name())
						.append(" (slot ").append(entry.getValue()).append(") ----\n");
				for (var pin : p.pins()) {
					sb.append("# set_location_assignment PIN_xx -to ")
							.append(pin.name()).append('\n');
					sb.append("# set_instance_assignment -name IO_STANDARD \"3.3-V LVCMOS\" -to ")
							.append(pin.name()).append('\n');
				}
				sb.append('\n');
			}
		}

		return sb.toString();
	}

	private static void generateConvenienceMethods(StringBuilder sb,
			PeripheralDefinition peripheral) {
		// Generate typed accessor methods for registers with clear semantics
		for (PeripheralRegister reg : peripheral.registers()) {
			String fieldName = toFieldName(reg.name());
			String methodSuffix = toCamelCase(reg.name());

			if ("R".equals(reg.access())) {
				sb.append("\n\t/** Read ").append(reg.name())
						.append(": ").append(reg.description()).append(" */\n");
				sb.append("\tpublic int read").append(methodSuffix).append("() {\n");
				sb.append("\t\treturn ").append(fieldName).append(";\n");
				sb.append("\t}\n");
			} else if ("W".equals(reg.access())) {
				sb.append("\n\t/** Write ").append(reg.name())
						.append(": ").append(reg.description()).append(" */\n");
				sb.append("\tpublic void write").append(methodSuffix)
						.append("(int value) {\n");
				sb.append("\t\t").append(fieldName).append(" = value;\n");
				sb.append("\t}\n");
			}
		}
	}

	/** Convert register name (e.g., "TX_DATA") to Java field name (e.g., "txData"). */
	static String toFieldName(String registerName) {
		String lower = registerName.toLowerCase();
		StringBuilder sb = new StringBuilder();
		boolean nextUpper = false;
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			if (c == '_') {
				nextUpper = true;
			} else {
				sb.append(nextUpper ? Character.toUpperCase(c) : c);
				nextUpper = false;
			}
		}
		return sb.toString();
	}

	/** Convert register name (e.g., "TX_DATA") to CamelCase (e.g., "TxData"). */
	static String toCamelCase(String registerName) {
		String lower = registerName.toLowerCase();
		StringBuilder sb = new StringBuilder();
		boolean nextUpper = true;
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			if (c == '_') {
				nextUpper = true;
			} else {
				sb.append(nextUpper ? Character.toUpperCase(c) : c);
				nextUpper = false;
			}
		}
		return sb.toString();
	}
}
