package com.jopdesign.core.deploy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import com.jopdesign.core.board.BoardDefinition;
import com.jopdesign.core.board.BoardRegistry;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;
import com.jopdesign.core.toolchain.JopToolchain;

/**
 * Handles serial deployment of JOP applications:
 * <ul>
 *   <li>Download: sends .jop file to the board via UART (delegates to download.py)</li>
 *   <li>Monitor: streams UART output (delegates to monitor.py)</li>
 * </ul>
 *
 * <p>Both operations stream output line-by-line to a {@link LineConsumer}
 * callback for real-time display in the Eclipse console.
 */
public class JopDeployer {

	private static final ILog LOG = Platform.getLog(JopDeployer.class);

	private final IProject project;
	private final JopToolchain toolchain;
	private final BoardDefinition board;

	/**
	 * Callback interface for streaming console output.
	 */
	@FunctionalInterface
	public interface LineConsumer {
		void accept(String line);
	}

	public JopDeployer(IProject project, JopToolchain toolchain, BoardDefinition board) {
		this.project = project;
		this.toolchain = toolchain;
		this.board = board;
	}

	/**
	 * Create a deployer from the project's current preferences.
	 */
	public static JopDeployer forProject(IProject project) throws CoreException {
		JopToolchain tc = JopToolchain.forProject(project);
		String boardId = JopProjectPreferences.get(project, JopPreferences.BOARD_ID, "");
		if (boardId.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"No board selected. Configure a board in Project Properties > JOP > Board Configuration."));
		}
		BoardDefinition bd = BoardRegistry.getBoard(boardId);
		if (bd == null) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Unknown board: " + boardId));
		}
		return new JopDeployer(project, tc, bd);
	}

	/**
	 * Download a .jop file to the board via serial port.
	 *
	 * <p>Invokes the Python download.py script, streaming output to the
	 * provided line consumer. Returns the process exit code.
	 *
	 * @param jopFile the .jop file to download
	 * @param consumer callback for each line of output
	 * @param monitor progress monitor
	 * @return 0 on success, non-zero on failure
	 */
	public int download(File jopFile, LineConsumer consumer, IProgressMonitor monitor)
			throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "Downloading application", 100);

		if (!jopFile.exists()) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"JOP file not found: " + jopFile));
		}

		File downloadScript = toolchain.getDownloadScript();
		if (!downloadScript.exists()) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Download script not found: " + downloadScript
					+ " — check JOP_HOME is set correctly"));
		}

		String serialPort = JopProjectPreferences.get(project,
				JopPreferences.SERIAL_PORT, "/dev/ttyUSB0");
		int baud = board.uartBaud();

		List<String> cmd = new ArrayList<>();
		cmd.add("python3");
		cmd.add(downloadScript.getAbsolutePath());
		cmd.add(jopFile.getAbsolutePath());
		cmd.add(serialPort);
		cmd.add(String.valueOf(baud));

		consumer.accept("=== Downloading " + jopFile.getName() + " ===");
		consumer.accept("Serial port: " + serialPort + " @ " + baud + " baud");
		consumer.accept("Command: " + String.join(" ", cmd));
		consumer.accept("");

		LOG.info("JOP download: " + String.join(" ", cmd));

		return runStreaming(cmd, downloadScript.getParentFile(), consumer, sub);
	}

	/**
	 * Download a .jop file and immediately start monitoring.
	 *
	 * <p>Invokes download.py with the {@code -e} flag, which continues
	 * monitoring UART output after the download completes.
	 *
	 * @param jopFile the .jop file to download
	 * @param consumer callback for each line of output
	 * @param monitor progress monitor (check isCanceled to stop monitoring)
	 * @return the running process (caller can destroy to stop)
	 */
	public Process downloadAndMonitor(File jopFile, LineConsumer consumer,
			IProgressMonitor monitor) throws CoreException {
		if (!jopFile.exists()) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"JOP file not found: " + jopFile));
		}

		File downloadScript = toolchain.getDownloadScript();
		if (!downloadScript.exists()) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Download script not found: " + downloadScript));
		}

		String serialPort = JopProjectPreferences.get(project,
				JopPreferences.SERIAL_PORT, "/dev/ttyUSB0");
		int baud = board.uartBaud();

		List<String> cmd = new ArrayList<>();
		cmd.add("python3");
		cmd.add(downloadScript.getAbsolutePath());
		cmd.add("-e"); // Continue monitoring after download
		cmd.add(jopFile.getAbsolutePath());
		cmd.add(serialPort);
		cmd.add(String.valueOf(baud));

		consumer.accept("=== Download + Monitor: " + jopFile.getName() + " ===");
		consumer.accept("Serial port: " + serialPort + " @ " + baud + " baud");
		consumer.accept("Command: " + String.join(" ", cmd));
		consumer.accept("");

		LOG.info("JOP download+monitor: " + String.join(" ", cmd));

		return startStreaming(cmd, downloadScript.getParentFile(), consumer);
	}

	/**
	 * Start the UART monitor.
	 *
	 * <p>Invokes the Python monitor.py script. The returned Process runs
	 * indefinitely until explicitly destroyed by the caller. Output is
	 * streamed to the consumer on a background thread.
	 *
	 * @param consumer callback for each line of output
	 * @return the running monitor process
	 */
	public Process startMonitor(LineConsumer consumer) throws CoreException {
		File monitorScript = findMonitorScript();
		if (monitorScript == null) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Monitor script not found in JOP_HOME/fpga/scripts/"));
		}

		String serialPort = JopProjectPreferences.get(project,
				JopPreferences.SERIAL_PORT, "/dev/ttyUSB0");
		int baud = board.uartBaud();

		List<String> cmd = new ArrayList<>();
		cmd.add("python3");
		cmd.add(monitorScript.getAbsolutePath());
		cmd.add(serialPort);
		cmd.add(String.valueOf(baud));

		consumer.accept("=== UART Monitor ===");
		consumer.accept("Serial port: " + serialPort + " @ " + baud + " baud");
		consumer.accept("Command: " + String.join(" ", cmd));
		consumer.accept("");

		LOG.info("JOP monitor: " + String.join(" ", cmd));

		return startStreaming(cmd, monitorScript.getParentFile(), consumer);
	}

	/**
	 * Find the .jop file in the project's build output directory.
	 *
	 * @return the .jop file, or null if not found
	 */
	public File findJopFile() {
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

	// ========================================================================
	// Process management
	// ========================================================================

	/**
	 * Run a process, streaming stdout and stderr to the consumer,
	 * waiting for completion. Returns exit code.
	 */
	private int runStreaming(List<String> command, File workingDir,
			LineConsumer consumer, SubMonitor sub) throws CoreException {
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(workingDir);
			pb.redirectErrorStream(true);

			Process process = pb.start();

			// Stream output line-by-line
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

			return process.waitFor();
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Failed to run: " + String.join(" ", command) + " — " + e.getMessage(), e));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CoreException(new Status(IStatus.CANCEL, JopCorePlugin.PLUGIN_ID,
					"Operation interrupted"));
		}
	}

	/**
	 * Start a long-running process, streaming output on a daemon thread.
	 * Returns the Process so the caller can destroy it when done.
	 */
	private Process startStreaming(List<String> command, File workingDir,
			LineConsumer consumer) throws CoreException {
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(workingDir);
			pb.redirectErrorStream(true);

			Process process = pb.start();

			// Stream output on daemon thread
			Thread reader = new Thread(() -> {
				try (BufferedReader br = new BufferedReader(
						new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = br.readLine()) != null) {
						consumer.accept(line);
					}
				} catch (IOException e) {
					consumer.accept("--- I/O error: " + e.getMessage() + " ---");
				}
			}, "JOP-Serial-Reader");
			reader.setDaemon(true);
			reader.start();

			return process;
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Failed to start: " + String.join(" ", command) + " — " + e.getMessage(), e));
		}
	}

	private File findMonitorScript() {
		File scripts = toolchain.getJopHome().append("fpga/scripts").toFile();
		File f = new File(scripts, "monitor.py");
		return f.exists() ? f : null;
	}
}
