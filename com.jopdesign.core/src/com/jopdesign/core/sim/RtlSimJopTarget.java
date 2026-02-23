package com.jopdesign.core.sim;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

import com.jopdesign.core.sim.transport.JopTcpTransport;

/**
 * IJopTarget that debugs an RTL simulation via TCP socket.
 *
 * <p>This launches the JopDebugSim simulation process (via SBT),
 * waits for the TCP debug port to become available, then connects
 * and uses the debug protocol for all operations.
 *
 * <p>The simulation class is {@code jop.system.JopDebugSim} which
 * opens a TCP server on the configured port (default 4567).
 */
public class RtlSimJopTarget extends ProtocolJopTarget {

	private static final ILog LOG = Platform.getLog(RtlSimJopTarget.class);

	private static final String DEBUG_SIM_CLASS = "jop.system.JopDebugSim";
	private static final int DEFAULT_PORT = 4567;
	private static final String DEFAULT_HOST = "localhost";
	private static final int CONNECT_RETRY_DELAY_MS = 1000;
	private static final int MAX_CONNECT_RETRIES = 300; // 5 minutes max wait for SBT compilation

	private final String sbtProjectDir;
	private final String sbtPath;
	private final int port;
	private Process simProcess;
	private Thread outputThread;
	private Thread shutdownHook;

	/**
	 * @param sbtProjectDir  path to the SpinalHDL SBT project directory
	 * @param sbtPath        path to the SBT executable
	 * @param port           TCP port for debug connection (default 4567)
	 */
	public RtlSimJopTarget(String sbtProjectDir, String sbtPath, int port) {
		super("RTL Simulation", "rtlsim", new JopTcpTransport(DEFAULT_HOST, port), 0);
		this.sbtProjectDir = sbtProjectDir;
		this.sbtPath = sbtPath;
		this.port = port;
	}

	/** Create with default port 4567. */
	public RtlSimJopTarget(String sbtProjectDir, String sbtPath) {
		this(sbtProjectDir, sbtPath, DEFAULT_PORT);
	}

	@Override
	public void connect() throws JopTargetException {
		// Launch the simulation process
		launchSimProcess();

		// Wait for TCP server to become available, then connect
		try {
			connectWithRetry();
		} catch (JopTargetException e) {
			// Connection failed — kill the SBT process so port is freed
			stopSimProcess();
			throw e;
		}
	}

	@Override
	public void disconnect() throws JopTargetException {
		try {
			super.disconnect();
		} finally {
			stopSimProcess();
		}
	}

	private void launchSimProcess() throws JopTargetException {
		List<String> cmd = new ArrayList<>();
		cmd.add(sbtPath);
		cmd.add("Test / runMain " + DEBUG_SIM_CLASS + " " + port);

		LOG.info("Launching RTL debug simulation: " + String.join(" ", cmd));

		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.directory(new File(sbtProjectDir));
			pb.redirectErrorStream(true);
			simProcess = pb.start();

			// Register shutdown hook to kill process tree on JVM exit
			shutdownHook = new Thread(this::destroyProcessTree, "RtlSim-Shutdown");
			Runtime.getRuntime().addShutdownHook(shutdownHook);

			// Capture and forward simulation output
			outputThread = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(simProcess.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = reader.readLine()) != null) {
						LOG.info("[RTL Sim] " + line);
						fireSimOutput(line + "\n");
					}
				} catch (IOException e) {
					if (simProcess != null && simProcess.isAlive()) {
						LOG.warn("RTL sim output reader error: " + e.getMessage());
					}
				}
			}, "RtlSim-Output");
			outputThread.setDaemon(true);
			outputThread.start();

		} catch (IOException e) {
			throw new JopTargetException("Failed to launch RTL simulation: " + e.getMessage(), e);
		}
	}

	private void connectWithRetry() throws JopTargetException {
		for (int attempt = 0; attempt < MAX_CONNECT_RETRIES; attempt++) {
			// Check if simulation process is still alive
			if (simProcess != null && !simProcess.isAlive()) {
				int exitCode = simProcess.exitValue();
				throw new JopTargetException(
						"RTL simulation process exited unexpectedly with code " + exitCode);
			}

			try {
				super.connect();
				LOG.info("Connected to RTL debug simulation on port " + port);
				return;
			} catch (JopTargetException e) {
				if (attempt < MAX_CONNECT_RETRIES - 1) {
					try {
						Thread.sleep(CONNECT_RETRY_DELAY_MS);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						throw new JopTargetException("Interrupted while waiting for simulation to start");
					}
				} else {
					throw new JopTargetException(
							"Failed to connect to RTL simulation after " + MAX_CONNECT_RETRIES
							+ " attempts: " + e.getMessage(), e);
				}
			}
		}
	}

	private void stopSimProcess() {
		// Remove shutdown hook (no longer needed if we're cleaning up explicitly)
		if (shutdownHook != null) {
			try {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			} catch (IllegalStateException e) {
				// JVM is already shutting down — hook will run on its own
			}
			shutdownHook = null;
		}
		if (outputThread != null) {
			outputThread.interrupt();
			outputThread = null;
		}
		destroyProcessTree();
	}

	/**
	 * Kill the SBT process and all its descendants (child JVMs).
	 * SBT forks a child JVM for "runMain" which holds the TCP server socket.
	 * {@code destroyForcibly()} alone only kills the SBT wrapper, leaving
	 * the child JVM running and holding the port.
	 */
	private void destroyProcessTree() {
		if (simProcess == null) {
			return;
		}
		// Destroy descendants first (the forked JVM running JopDebugSim),
		// then the SBT process itself
		try {
			simProcess.descendants().forEach(ph -> {
				try {
					ph.destroyForcibly();
				} catch (Exception e) {
					// Best effort
				}
			});
		} catch (Exception e) {
			// descendants() may fail if process already exited
		}
		simProcess.destroyForcibly();
		try {
			simProcess.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		simProcess = null;
	}

	private void fireSimOutput(String text) {
		fireOutput(text);
	}
}
