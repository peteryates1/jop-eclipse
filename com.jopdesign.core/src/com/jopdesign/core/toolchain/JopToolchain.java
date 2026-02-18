package com.jopdesign.core.toolchain;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import com.jopdesign.core.JopCorePlugin;

/**
 * Abstraction for locating and invoking JOP toolchain components:
 * Jopa (microcode assembler), JOPizer, PreLinker, JopSim, download script.
 */
public class JopToolchain {

	private final IPath jopHome;

	public JopToolchain(IPath jopHome) {
		this.jopHome = jopHome;
	}

	/**
	 * Create a toolchain instance for a project, reading JOP_HOME from project preferences.
	 */
	public static JopToolchain forProject(IProject project) throws CoreException {
		// TODO: Read JOP_HOME from project-scoped preferences
		throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
				"JOP_HOME not configured for project " + project.getName()));
	}

	/** Path to jopa.jar (microcode assembler) */
	public File getJopaJar() {
		return jopHome.append("java/tools/dist/jopa.jar").toFile();
	}

	/** Path to jopizer.jar (bytecode converter) */
	public File getJopizerJar() {
		return jopHome.append("java/tools/dist/jopizer.jar").toFile();
	}

	/** Path to jopsim.jar (simulator) */
	public File getJopSimJar() {
		return jopHome.append("java/tools/dist/jopsim.jar").toFile();
	}

	/** Path to JOP target runtime classes */
	public File getTargetClasses() {
		return jopHome.append("java/target/classes").toFile();
	}

	/** Path to microcode source directory */
	public File getMicrocodeSourceDir() {
		return jopHome.append("asm/src").toFile();
	}

	/** Path to generated microcode output directory */
	public File getMicrocodeGeneratedDir() {
		return jopHome.append("asm/generated").toFile();
	}

	/** Path to download script */
	public File getDownloadScript() {
		return jopHome.append("fpga/download.py").toFile();
	}

	/** Validate that JOP_HOME points to a valid JOP installation */
	public IStatus validate() {
		if (!jopHome.toFile().isDirectory()) {
			return new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"JOP_HOME directory does not exist: " + jopHome);
		}
		if (!getMicrocodeSourceDir().isDirectory()) {
			return new Status(IStatus.WARNING, JopCorePlugin.PLUGIN_ID,
					"Microcode source directory not found: " + getMicrocodeSourceDir());
		}
		return Status.OK_STATUS;
	}
}
