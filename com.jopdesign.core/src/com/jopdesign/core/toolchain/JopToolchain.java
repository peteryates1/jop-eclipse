package com.jopdesign.core.toolchain;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;

/**
 * Abstraction for locating and invoking JOP toolchain components:
 * Jopa (microcode assembler), JOPizer, PreLinker, JopSim, download script.
 */
public class JopToolchain {

	private final IPath jopHome;

	/** Library jar names required by JOPizer and PreLinker */
	private static final String[] LIB_JARS = {
		"bcel-5.2.jar",
		"jakarta-regexp-1.3.jar",
		"log4j-1.2.15.jar",
		"jgrapht-jdk1.5.jar",
	};

	public JopToolchain(IPath jopHome) {
		this.jopHome = jopHome;
	}

	/**
	 * Create a toolchain instance for a project, reading JOP_HOME from
	 * project-scoped preferences first, then falling back to workspace scope.
	 */
	public static JopToolchain forProject(IProject project) throws CoreException {
		String home = JopProjectPreferences.get(project, JopPreferences.JOP_HOME, "");
		if (home.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"JOP_HOME not configured. Set it in project properties or Window > Preferences > JOP."));
		}
		return new JopToolchain(new Path(home));
	}

	/** The JOP installation root directory */
	public IPath getJopHome() {
		return jopHome;
	}

	// ---- Microcode tools ----

	/** Path to jopa.jar (microcode assembler) */
	public File getJopaJar() {
		return jopHome.append("java/tools/jopa/dist/lib/jopa.jar").toFile();
	}

	/** Path to microcode source directory */
	public File getMicrocodeSourceDir() {
		return jopHome.append("asm/src").toFile();
	}

	/** Path to generated microcode output directory */
	public File getMicrocodeGeneratedDir() {
		return jopHome.append("asm/generated").toFile();
	}

	// ---- Java build tools ----

	/** Path to jopizer.jar (JOPizer + PreLinker) */
	public File getJopizerJar() {
		return jopHome.append("java/tools/dist/jopizer.jar").toFile();
	}

	/** Path to jopsim.jar (simulator) */
	public File getJopSimJar() {
		return jopHome.append("java/tools/dist/jopsim.jar").toFile();
	}

	/** Path to the lib directory containing dependency jars */
	public File getLibDir() {
		return jopHome.append("java/lib").toFile();
	}

	/**
	 * Build the classpath string for running JOPizer/PreLinker.
	 * Includes jopizer.jar and all dependency jars from java/lib/.
	 */
	public String getToolsClasspath() {
		StringBuilder cp = new StringBuilder();
		cp.append(getJopizerJar().getAbsolutePath());
		File libDir = getLibDir();
		for (String jar : LIB_JARS) {
			cp.append(File.pathSeparator);
			cp.append(new File(libDir, jar).getAbsolutePath());
		}
		return cp.toString();
	}

	// ---- Target runtime ----

	/** Path to compiled JOP target runtime classes */
	public File getTargetClasses() {
		return jopHome.append("java/target/classes").toFile();
	}

	/** Path to JOP runtime source (com.jopdesign.* packages) */
	public File getTargetSourceJop() {
		return jopHome.append("java/target/src/jop").toFile();
	}

	/** Path to JVM stub source (java.lang.*, java.io.* packages) */
	public File getTargetSourceJvm() {
		return jopHome.append("java/target/src/jvm").toFile();
	}

	// ---- FPGA / deploy ----

	/** Path to download script */
	public File getDownloadScript() {
		return jopHome.append("fpga/scripts/download.py").toFile();
	}

	/** Path to SpinalHDL generated Verilog output directory */
	public File getSpinalGeneratedDir() {
		return jopHome.append("spinalhdl/generated").toFile();
	}

	/** Path to the SBT project root (where build.sbt lives) */
	public File getSbtProjectDir() {
		return jopHome.toFile();
	}

	/**
	 * Get the FPGA project directory for a given board.
	 *
	 * @param fpgaDir the board's FPGA directory name (e.g., "qmtech-ep4cgx150-bram")
	 * @return the FPGA project directory under {@code fpga/}
	 */
	public File getFpgaDir(String fpgaDir) {
		return jopHome.append("fpga/" + fpgaDir).toFile();
	}

	/**
	 * Resolve the SBT executable path from project preferences.
	 * Returns the configured path, or "sbt" if not set (rely on PATH).
	 */
	public static String getSbtPath(IProject project) {
		String path = JopProjectPreferences.get(project, JopPreferences.SBT_PATH, "sbt");
		return path.isEmpty() ? "sbt" : path;
	}

	/**
	 * Resolve the Quartus bin directory from project preferences.
	 * Returns null if not configured.
	 */
	public static File getQuartusDir(IProject project) {
		String path = JopProjectPreferences.get(project, JopPreferences.QUARTUS_PATH, "");
		return path.isEmpty() ? null : new File(path);
	}

	/**
	 * Resolve the Vivado install directory from project preferences.
	 * Returns null if not configured.
	 */
	public static File getVivadoDir(IProject project) {
		String path = JopProjectPreferences.get(project, JopPreferences.VIVADO_PATH, "");
		return path.isEmpty() ? null : new File(path);
	}

	// ---- Validation ----

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
		if (!getJopaJar().isFile()) {
			return new Status(IStatus.WARNING, JopCorePlugin.PLUGIN_ID,
					"Jopa jar not found: " + getJopaJar()
					+ " (run 'make' in java/tools/jopa/ to build it)");
		}
		return Status.OK_STATUS;
	}

	/** Validate that Java build tools are available */
	public IStatus validateJavaTools() {
		if (!getJopizerJar().isFile()) {
			return new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"JOPizer jar not found: " + getJopizerJar()
					+ " (run 'make' in java/tools/ to build it)");
		}
		File libDir = getLibDir();
		for (String jar : LIB_JARS) {
			File f = new File(libDir, jar);
			if (!f.isFile()) {
				return new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
						"Required library not found: " + f);
			}
		}
		return Status.OK_STATUS;
	}
}
