package com.jopdesign.core.sim;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
import com.jopdesign.core.toolchain.JopToolchain;

/**
 * Runs SpinalHDL RTL simulations via SBT.
 *
 * <p>Available simulation classes:
 * <ul>
 *   <li>{@code jop.system.JopCoreBramSim} — BRAM simulation with UART tracing</li>
 *   <li>{@code jop.system.JopCoreBramDebug} — Detailed debug with BMB transaction traces</li>
 *   <li>{@code jop.system.JopSmallGcBramSim} — GC stress test</li>
 *   <li>{@code jop.system.JopCoreWithSdramSim} — SDRAM timing simulation</li>
 * </ul>
 *
 * <p>Invoked via: {@code sbt "Test / runMain {simClass}"}
 */
public class RtlSimRunner {

	private static final ILog LOG = Platform.getLog(RtlSimRunner.class);

	/** Predefined RTL simulation classes. */
	public static final String[] SIM_CLASSES = {
		"jop.system.JopCoreBramSim",
		"jop.system.JopCoreBramDebug",
		"jop.system.JopSmallGcBramSim",
		"jop.system.JopCoreWithSdramSim",
	};

	/** Human-readable names for the simulation classes. */
	public static final String[] SIM_NAMES = {
		"BRAM Simulation",
		"BRAM Debug (detailed traces)",
		"GC Stress Test (BRAM)",
		"SDRAM Simulation",
	};

	private final IProject project;
	private final JopToolchain toolchain;
	private String simClass = SIM_CLASSES[0];

	public RtlSimRunner(IProject project, JopToolchain toolchain) {
		this.project = project;
		this.toolchain = toolchain;
	}

	/**
	 * Create a runner from the project's current preferences.
	 */
	public static RtlSimRunner forProject(IProject project) throws CoreException {
		JopToolchain tc = JopToolchain.forProject(project);
		return new RtlSimRunner(project, tc);
	}

	/**
	 * Set the simulation class to run.
	 *
	 * @param simClass fully qualified Scala class name
	 *        (e.g., {@code jop.system.JopCoreBramSim})
	 */
	public RtlSimRunner simClass(String simClass) {
		this.simClass = simClass;
		return this;
	}

	/**
	 * Run the RTL simulation. This can take several minutes for
	 * Verilator compilation + simulation.
	 *
	 * @param consumer callback for each line of output
	 * @param monitor progress monitor
	 * @return the process exit code (0=success)
	 */
	public int run(LineConsumer consumer, IProgressMonitor monitor) throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "RTL Simulation", 100);

		String sbtPath = JopToolchain.getSbtPath(project);
		File sbtDir = toolchain.getSbtProjectDir();

		List<String> cmd = new ArrayList<>();
		cmd.add(sbtPath);
		cmd.add("Test / runMain " + simClass);

		consumer.accept("=== RTL Simulation: " + simClass + " ===");
		consumer.accept("Working dir: " + sbtDir);
		consumer.accept("Command: " + String.join(" ", cmd));
		consumer.accept("");
		consumer.accept("(Verilator compilation may take a minute on first run)");
		consumer.accept("");

		LOG.info("RTL simulation: " + String.join(" ", cmd));

		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.directory(sbtDir);
			pb.redirectErrorStream(true);

			Process process = pb.start();

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					consumer.accept(line);
					if (sub.isCanceled()) {
						process.destroyForcibly();
						consumer.accept("--- Cancelled ---");
						return -1;
					}
				}
			}

			int exitCode = process.waitFor();
			sub.worked(100);
			return exitCode;

		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Failed to run RTL simulation: " + e.getMessage(), e));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CoreException(new Status(IStatus.CANCEL, JopCorePlugin.PLUGIN_ID,
					"Simulation interrupted"));
		}
	}
}
