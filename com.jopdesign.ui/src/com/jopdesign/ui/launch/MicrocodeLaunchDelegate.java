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
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;

import com.jopdesign.core.sim.microcode.MicrocodeParser;
import com.jopdesign.ui.JopUIPlugin;
import com.jopdesign.core.sim.microcode.MicrocodeParser.MicrocodeParseException;
import com.jopdesign.core.sim.microcode.MicrocodeProgram;
import com.jopdesign.core.sim.microcode.MicrocodeSimulator;
import com.jopdesign.microcode.debug.MicrocodeDebugTarget;

/**
 * Launch delegate for microcode debug sessions.
 * Parses the microcode file, creates a simulator and debug target.
 */
public class MicrocodeLaunchDelegate implements ILaunchConfigurationDelegate {

	public static final String ATTR_MICROCODE_FILE = "com.jopdesign.ui.launch.microcodeFile";
	public static final String ATTR_INITIAL_SP = "com.jopdesign.ui.launch.initialSP";
	public static final String ATTR_MEM_SIZE = "com.jopdesign.ui.launch.memSize";

	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		String filePath = configuration.getAttribute(ATTR_MICROCODE_FILE, "");
		int initialSP = configuration.getAttribute(ATTR_INITIAL_SP, 64);
		int memSize = configuration.getAttribute(ATTR_MEM_SIZE, 1024);

		if (filePath.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"No microcode file specified"));
		}

		// Read the source file
		String source;
		try {
			IFile wsFile = ResourcesPlugin.getWorkspace().getRoot().getFile(
					new org.eclipse.core.runtime.Path(filePath));
			if (wsFile.exists()) {
				source = Files.readString(Path.of(wsFile.getLocationURI()));
			} else {
				// Try as absolute path
				source = Files.readString(Path.of(filePath));
			}
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"Cannot read microcode file: " + filePath, e));
		}

		// Parse
		MicrocodeProgram program;
		try {
			program = new MicrocodeParser().parse(source);
		} catch (MicrocodeParseException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID,
					"Parse error: " + e.getMessage(), e));
		}

		// Create simulator
		MicrocodeSimulator simulator = new MicrocodeSimulator(1024, 256, memSize);
		simulator.load(program);
		simulator.setSP(initialSP);

		// Create debug target and add to launch
		String name = "JOP Microcode [" + Path.of(filePath).getFileName() + "]";
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, name);
		launch.addDebugTarget(target);
	}
}
