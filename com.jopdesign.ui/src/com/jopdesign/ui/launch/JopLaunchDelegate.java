package com.jopdesign.ui.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import com.jopdesign.core.sim.DummyJopTarget;
import com.jopdesign.core.sim.FpgaJopTarget;
import com.jopdesign.core.sim.IJopTarget;
import com.jopdesign.core.sim.IJopTargetListener;
import com.jopdesign.core.sim.JopSimJopTarget;
import com.jopdesign.core.sim.JopSuspendReason;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.core.sim.JopTargetState;
import com.jopdesign.core.sim.RtlSimJopTarget;
import com.jopdesign.core.sim.SimulatorJopTarget;
import com.jopdesign.core.sim.microcode.MicrocodeParser;
import com.jopdesign.ui.JopUIPlugin;
import com.jopdesign.core.sim.microcode.MicrocodeParser.MicrocodeParseException;
import com.jopdesign.core.sim.microcode.MicrocodeProgram;
import com.jopdesign.microcode.debug.JopDebugTarget;

/**
 * Launch delegate for "JOP Application" configurations.
 * Supports multiple target types: simulator, dummy, and (future) rtlsim/fpga.
 */
public class JopLaunchDelegate implements ILaunchConfigurationDelegate {

	public static final String ATTR_TARGET_TYPE = "com.jopdesign.ui.launch.targetType";
	public static final String ATTR_MICROCODE_FILE = "com.jopdesign.ui.launch.jop.microcodeFile";
	public static final String ATTR_INITIAL_SP = "com.jopdesign.ui.launch.jop.initialSP";
	public static final String ATTR_MEM_SIZE = "com.jopdesign.ui.launch.jop.memSize";

	public static final String TARGET_SIMULATOR = "simulator";
	public static final String TARGET_JOPSIM = "jopsim";
	public static final String TARGET_RTLSIM = "rtlsim";
	public static final String TARGET_FPGA = "fpga";
	public static final String TARGET_DUMMY = "dummy";

	public static final String ATTR_JOP_FILE = "com.jopdesign.ui.launch.jopFile";
	public static final String ATTR_LINK_FILE = "com.jopdesign.ui.launch.linkFile";

	public static final String ATTR_SBT_PROJECT_DIR = "com.jopdesign.ui.launch.sbtProjectDir";
	public static final String ATTR_SBT_PATH = "com.jopdesign.ui.launch.sbtPath";
	public static final String ATTR_DEBUG_PORT = "com.jopdesign.ui.launch.debugPort";
	public static final String ATTR_SERIAL_PORT = "com.jopdesign.ui.launch.serialPort";
	public static final String ATTR_BAUD_RATE = "com.jopdesign.ui.launch.baudRate";

	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		String targetType = configuration.getAttribute(ATTR_TARGET_TYPE, TARGET_SIMULATOR);

		IJopTarget target = createTarget(configuration, targetType);

		try {
			target.connect();
		} catch (JopTargetException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"Failed to connect to target: " + e.getMessage(), e));
		}

		String filePath = configuration.getAttribute(ATTR_MICROCODE_FILE, "");
		String launchName = "JOP Application [" + targetType + "]";
		if (!filePath.isEmpty()) {
			launchName = "JOP [" + Path.of(filePath).getFileName() + " / " + targetType + "]";
		}

		if (ILaunchManager.DEBUG_MODE.equals(mode)) {
			// Debug mode: create debug target with source locator
			JopSourceLocator locator = new JopSourceLocator();
			locator.initializeDefaults(configuration);
			launch.setSourceLocator(locator);
			JopDebugTarget debugTarget = new JopDebugTarget(launch, target, launchName);
			launch.addDebugTarget(debugTarget);
			debugTarget.fireInitialSuspendIfNeeded();
		} else {
			// Run mode: add debug target (so launch is trackable/terminable),
			// then resume and stream output to console
			JopDebugTarget debugTarget = new JopDebugTarget(launch, target, launchName);
			launch.addDebugTarget(debugTarget);

			MessageConsole console = findOrCreateConsole(launchName);
			MessageConsoleStream stream = console.newMessageStream();

			target.addListener(new IJopTargetListener() {
				@Override
				public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
					if (newState == JopTargetState.TERMINATED) {
						stream.println("[Terminated]");
						try {
							stream.close();
						} catch (IOException e) {
							// Ignore
						}
					}
				}

				@Override
				public void outputProduced(String text) {
					stream.print(text);
				}
			});

			try {
				target.resume();
			} catch (JopTargetException e) {
				throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
						"Failed to resume target: " + e.getMessage(), e));
			}
		}
	}

	private IJopTarget createTarget(ILaunchConfiguration configuration, String targetType)
			throws CoreException {
		return switch (targetType) {
			case TARGET_SIMULATOR -> createSimulatorTarget(configuration);
			case TARGET_JOPSIM -> createJopSimTarget(configuration);
			case TARGET_RTLSIM -> createRtlSimTarget(configuration);
			case TARGET_FPGA -> createFpgaTarget(configuration);
			case TARGET_DUMMY -> new DummyJopTarget();
			default -> throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"Unknown target type: " + targetType));
		};
	}

	private SimulatorJopTarget createSimulatorTarget(ILaunchConfiguration configuration)
			throws CoreException {
		String filePath = configuration.getAttribute(ATTR_MICROCODE_FILE, "");
		int initialSP = configuration.getAttribute(ATTR_INITIAL_SP, 64);
		int memSize = configuration.getAttribute(ATTR_MEM_SIZE, 1024);

		if (filePath.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"No microcode file specified"));
		}

		String source;
		try {
			IFile wsFile = ResourcesPlugin.getWorkspace().getRoot().getFile(
					new org.eclipse.core.runtime.Path(filePath));
			if (wsFile.exists()) {
				source = Files.readString(Path.of(wsFile.getLocationURI()));
			} else {
				source = Files.readString(Path.of(filePath));
			}
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"Cannot read microcode file: " + filePath, e));
		}

		MicrocodeProgram program;
		try {
			program = new MicrocodeParser().parse(source);
		} catch (MicrocodeParseException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"Parse error: " + e.getMessage(), e));
		}

		return new SimulatorJopTarget(program, 1024, memSize, initialSP);
	}

	private JopSimJopTarget createJopSimTarget(ILaunchConfiguration configuration)
			throws CoreException {
		String jopFilePath = configuration.getAttribute(ATTR_JOP_FILE, "");
		String linkFilePath = configuration.getAttribute(ATTR_LINK_FILE, "");

		if (jopFilePath.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"No .jop file specified"));
		}

		// Resolve workspace path to filesystem path if needed
		String resolvedJopFile = resolveFilePath(jopFilePath);

		String resolvedLinkFile = null;
		if (!linkFilePath.isEmpty()) {
			resolvedLinkFile = resolveFilePath(linkFilePath);
		}

		return new JopSimJopTarget(resolvedJopFile, resolvedLinkFile);
	}

	private RtlSimJopTarget createRtlSimTarget(ILaunchConfiguration configuration)
			throws CoreException {
		String sbtProjectDir = configuration.getAttribute(ATTR_SBT_PROJECT_DIR, "");
		String sbtPath = configuration.getAttribute(ATTR_SBT_PATH, "sbt");
		int debugPort = configuration.getAttribute(ATTR_DEBUG_PORT, 4567);

		if (sbtProjectDir.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"No SBT project directory specified for RTL simulation"));
		}

		return new RtlSimJopTarget(sbtProjectDir, sbtPath, debugPort);
	}

	private FpgaJopTarget createFpgaTarget(ILaunchConfiguration configuration)
			throws CoreException {
		String serialPort = configuration.getAttribute(ATTR_SERIAL_PORT, "");
		int baudRate = configuration.getAttribute(ATTR_BAUD_RATE, 1_000_000);

		if (serialPort.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"No serial port specified for FPGA target"));
		}

		return new FpgaJopTarget(serialPort, baudRate);
	}

	private String resolveFilePath(String path) {
		try {
			IFile wsFile = ResourcesPlugin.getWorkspace().getRoot().getFile(
					new org.eclipse.core.runtime.Path(path));
			if (wsFile.exists()) {
				return wsFile.getLocation().toOSString();
			}
		} catch (Exception e) {
			// Not a workspace path, use as-is
		}
		return path;
	}

	private MessageConsole findOrCreateConsole(String name) {
		IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
		for (IConsole existing : manager.getConsoles()) {
			if (name.equals(existing.getName()) && existing instanceof MessageConsole mc) {
				return mc;
			}
		}
		MessageConsole console = new MessageConsole(name, null);
		manager.addConsoles(new IConsole[] { console });
		return console;
	}
}
