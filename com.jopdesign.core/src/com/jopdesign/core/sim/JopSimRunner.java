package com.jopdesign.core.sim;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.deploy.JopDeployer.LineConsumer;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;
import com.jopdesign.core.toolchain.JopToolchain;

/**
 * Runs JopSim (Java-level JOP simulator) on a built .jop application.
 *
 * <p>JopSim is a pure Java bytecode interpreter that simulates the JOP
 * processor. It produces instruction counts, cycle counts, CPI, memory
 * access statistics, and optional method-level traces.
 *
 * <p>Usage:
 * <pre>
 * java -cp jopsim.jar [-Dtrace=N] com.jopdesign.tools.JopSim
 *     [-link file.link.txt] binary.jop [maxInstructions]
 * </pre>
 */
public class JopSimRunner {

	private static final ILog LOG = Platform.getLog(JopSimRunner.class);

	private final IProject project;
	private final JopToolchain toolchain;

	/** Trace level: 0=off, 1=method entry/exit, 2=+fields, 3=full bytecode */
	private int traceLevel = 0;
	/** Maximum instructions to simulate (0=unlimited) */
	private int maxInstructions = 0;

	public JopSimRunner(IProject project, JopToolchain toolchain) {
		this.project = project;
		this.toolchain = toolchain;
	}

	/**
	 * Create a runner from the project's current preferences.
	 */
	public static JopSimRunner forProject(IProject project) throws CoreException {
		JopToolchain tc = JopToolchain.forProject(project);
		return new JopSimRunner(project, tc);
	}

	public JopSimRunner traceLevel(int level) {
		this.traceLevel = level;
		return this;
	}

	public JopSimRunner maxInstructions(int max) {
		this.maxInstructions = max;
		return this;
	}

	/**
	 * Run JopSim on the project's built .jop file.
	 *
	 * @param consumer callback for each line of output
	 * @param monitor progress monitor
	 * @return simulation result with parsed statistics
	 */
	public SimResult run(LineConsumer consumer, IProgressMonitor monitor) throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "Running JopSim", 100);

		// Validate jopsim.jar exists
		File jopSimJar = toolchain.getJopSimJar();
		if (!jopSimJar.isFile()) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"jopsim.jar not found: " + jopSimJar
					+ " — run 'make' in java/tools/"));
		}

		// Find .jop file
		File jopFile = findJopFile();
		if (jopFile == null) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"No .jop file found. Build the Java application first "
					+ "(configure main class in JOP properties)."));
		}

		// Look for .link.txt symbol file
		File linkFile = new File(jopFile.getParent(),
				jopFile.getName() + ".link.txt");

		// Build command
		List<String> cmd = new ArrayList<>();
		cmd.add("java");
		if (traceLevel > 0) {
			cmd.add("-Dtrace=" + traceLevel);
		}
		cmd.add("-cp");
		cmd.add(jopSimJar.getAbsolutePath());
		cmd.add("com.jopdesign.tools.JopSim");
		if (linkFile.isFile()) {
			cmd.add("-link");
			cmd.add(linkFile.getAbsolutePath());
		}
		cmd.add(jopFile.getAbsolutePath());
		if (maxInstructions > 0) {
			cmd.add(String.valueOf(maxInstructions));
		}

		consumer.accept("=== JopSim: " + jopFile.getName() + " ===");
		if (linkFile.isFile()) {
			consumer.accept("Symbols: " + linkFile.getName());
		}
		if (traceLevel > 0) {
			consumer.accept("Trace level: " + traceLevel);
		}
		consumer.accept("Command: " + String.join(" ", cmd));
		consumer.accept("");

		LOG.info("JopSim: " + String.join(" ", cmd));

		// Run and collect output for statistics parsing
		SimResult result = runAndParse(cmd, jopFile.getParentFile(), consumer, sub);

		sub.worked(100);
		return result;
	}

	private SimResult runAndParse(List<String> command, File workingDir,
			LineConsumer consumer, SubMonitor sub) throws CoreException {
		List<String> allLines = new ArrayList<>();
		Process process = null;

		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(workingDir);
			pb.redirectErrorStream(true);

			process = pb.start();

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					consumer.accept(line);
					allLines.add(line);
					if (sub.isCanceled()) {
						process.destroyForcibly();
						consumer.accept("--- Cancelled ---");
						return new SimResult(false, 0, 0, 0.0, "");
					}
				}
			}

			int exitCode = process.waitFor();
			boolean success = exitCode == 0;

			// Parse statistics from output
			return parseStatistics(allLines, success);

		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Failed to run JopSim: " + e.getMessage(), e));
		} catch (InterruptedException e) {
			if (process != null) {
				process.destroyForcibly();
			}
			Thread.currentThread().interrupt();
			throw new CoreException(new Status(IStatus.CANCEL, JopCorePlugin.PLUGIN_ID,
					"Simulation interrupted"));
		}
	}

	// Patterns for parsing JopSim statistics output
	private static final Pattern INSTR_PATTERN =
			Pattern.compile("(\\d+)\\s+instructions");
	private static final Pattern CYCLE_PATTERN =
			Pattern.compile("(\\d+)\\s+cycles");
	private static final Pattern CPI_PATTERN =
			Pattern.compile("CPI:\\s*([\\d.]+)");

	private SimResult parseStatistics(List<String> lines, boolean success) {
		long instructions = 0;
		long cycles = 0;
		double cpi = 0.0;
		StringBuilder summary = new StringBuilder();

		boolean inStats = false;
		for (String line : lines) {
			// Detect statistics section (usually near end of output)
			Matcher m;

			m = INSTR_PATTERN.matcher(line);
			if (m.find()) {
				instructions = Long.parseLong(m.group(1));
				inStats = true;
			}

			m = CYCLE_PATTERN.matcher(line);
			if (m.find()) {
				cycles = Long.parseLong(m.group(1));
			}

			m = CPI_PATTERN.matcher(line);
			if (m.find()) {
				cpi = Double.parseDouble(m.group(1));
			}

			// Collect summary lines (memory stats, etc.)
			if (inStats) {
				summary.append(line).append('\n');
			}
		}

		return new SimResult(success, instructions, cycles, cpi, summary.toString());
	}

	private File findJopFile() {
		String mainClass = JopProjectPreferences.get(project,
				JopPreferences.MAIN_CLASS, "");
		if (mainClass.isEmpty()) return null;

		String outputDirName = JopProjectPreferences.get(project,
				JopPreferences.JOP_OUTPUT_DIR, "build");
		String simpleClassName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
		File projectRoot = project.getLocation().toFile();
		File jopFile = new File(new File(projectRoot, outputDirName),
				simpleClassName + ".jop");
		return jopFile.exists() ? jopFile : null;
	}
}
