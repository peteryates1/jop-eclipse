package com.jopdesign.core.builder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;
import com.jopdesign.core.toolchain.JopToolchain;

/**
 * JOP incremental project builder. Orchestrates the JOP build pipeline:
 * <ol>
 *   <li>Microcode: .asm → gcc preprocessor → Jopa → ROM/RAM/jump table data</li>
 *   <li>Java: .class → PreLinker → JOPizer → .jop</li>
 * </ol>
 */
public class JopBuilder extends IncrementalProjectBuilder {

	public static final String BUILDER_ID = "com.jopdesign.core.jopBuilder";
	public static final String MARKER_TYPE = "com.jopdesign.core.microcodeProblem";

	/** Lines to strip from GCC preprocessor output (GCC preamble artifacts) */
	private static final int GCC_HEADER_LINES = 35;

	private static final ILog LOG = Platform.getLog(JopBuilder.class);

	@Override
	protected IProject[] build(int kind, Map<String, String> args,
			IProgressMonitor monitor) throws CoreException {

		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		getProject().deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);

		// Clean JOP build output directory
		String outputDirName = JopProjectPreferences.get(getProject(),
				JopPreferences.JOP_OUTPUT_DIR, "build");
		IFolder outputFolder = getProject().getFolder(outputDirName);
		if (outputFolder.exists()) {
			outputFolder.delete(true, monitor);
		}
	}

	private void fullBuild(IProgressMonitor monitor) throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "JOP Build", 100);

		// Get toolchain
		JopToolchain toolchain;
		try {
			toolchain = JopToolchain.forProject(getProject());
		} catch (CoreException e) {
			// JOP_HOME not configured — skip silently, user may not have set it yet
			LOG.info("JOP build skipped: " + e.getMessage());
			return;
		}

		IStatus validation = toolchain.validate();
		if (validation.getSeverity() == IStatus.ERROR) {
			getProject().deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
			addMarker(getProject(), validation.getMessage(), -1, IMarker.SEVERITY_ERROR);
			return;
		}
		if (validation.getSeverity() == IStatus.WARNING) {
			LOG.warn(validation.getMessage());
		}

		// Clear old markers
		getProject().deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);

		// Phase 1: Microcode build
		buildMicrocode(toolchain, sub.split(30));
		if (sub.isCanceled()) return;

		// Phase 2: Java build (PreLinker + JOPizer)
		buildJava(toolchain, sub.split(70));
	}

	private void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor)
			throws CoreException {
		boolean[] microcodeChanged = { false };
		boolean[] javaChanged = { false };
		delta.accept(new IResourceDeltaVisitor() {
			@Override
			public boolean visit(IResourceDelta d) {
				IResource resource = d.getResource();
				if (resource instanceof IFile file) {
					String ext = file.getFileExtension();
					if ("asm".equals(ext) || "inc".equals(ext) || "mic".equals(ext)) {
						microcodeChanged[0] = true;
					} else if ("java".equals(ext) || "class".equals(ext)) {
						javaChanged[0] = true;
					}
				}
				return true;
			}
		});

		if (microcodeChanged[0] || javaChanged[0]) {
			// Rebuild everything — microcode and Java builds are quick
			fullBuild(monitor);
		}
	}

	// ========================================================================
	// Microcode build (Phase 1)
	// ========================================================================

	private void buildMicrocode(JopToolchain toolchain, IProgressMonitor monitor)
			throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "Building microcode", 100);

		File asmSrc = toolchain.getMicrocodeSourceDir();
		File asmGen = toolchain.getMicrocodeGeneratedDir();
		asmGen.mkdirs();

		File sourceFile = new File(asmSrc, "jvm.asm");
		if (!sourceFile.exists()) {
			addMarker(getProject(),
					"Microcode source not found: " + sourceFile.getAbsolutePath(),
					-1, IMarker.SEVERITY_ERROR);
			return;
		}

		// Step 1: Preprocess with GCC
		sub.subTask("Preprocessing microcode (gcc -E)");
		boolean ok = preprocessMicrocode(toolchain, sourceFile, asmGen);
		sub.worked(40);
		if (!ok || sub.isCanceled()) return;

		// Step 2: Assemble with Jopa
		sub.subTask("Assembling microcode (Jopa)");
		assembleMicrocode(toolchain, asmGen);
		sub.worked(60);
	}

	private boolean preprocessMicrocode(JopToolchain toolchain, File sourceFile,
			File genDir) throws CoreException {
		String defines = JopProjectPreferences.get(getProject(),
				JopPreferences.MICROCODE_DEFINES, "SERIAL");

		List<String> cmd = new ArrayList<>();
		cmd.add("gcc");
		cmd.add("-x");
		cmd.add("c");
		cmd.add("-E");
		cmd.add("-C");
		cmd.add("-P");
		for (String define : defines.split(",")) {
			define = define.trim();
			if (!define.isEmpty()) {
				cmd.add("-D" + define);
			}
		}
		cmd.add(sourceFile.getAbsolutePath());

		LOG.info("JOP microcode preprocess: " + String.join(" ", cmd));

		ProcessResult result = runProcess(cmd, sourceFile.getParentFile());

		if (result.exitCode != 0) {
			addMarker(getProject(),
					"GCC preprocessing failed (exit " + result.exitCode + "): "
					+ firstLine(result.stderr),
					-1, IMarker.SEVERITY_ERROR);
			return false;
		}

		// Strip first N lines (GCC inserts blank lines even with -P)
		String output = result.stdout;
		String[] lines = output.split("\n", -1);
		int skipLines = Math.min(GCC_HEADER_LINES, lines.length);

		StringBuilder sb = new StringBuilder();
		for (int i = skipLines; i < lines.length; i++) {
			sb.append(lines[i]).append('\n');
		}

		File preprocessed = new File(genDir, "jvm.asm");
		try {
			Files.writeString(preprocessed.toPath(), sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Failed to write preprocessed microcode: " + preprocessed, e));
		}

		return true;
	}

	private void assembleMicrocode(JopToolchain toolchain, File genDir) throws CoreException {
		File jopaJar = toolchain.getJopaJar();
		if (!jopaJar.isFile()) {
			addMarker(getProject(),
					"Jopa assembler not found: " + jopaJar.getAbsolutePath()
					+ " — run 'make' in java/tools/jopa/",
					-1, IMarker.SEVERITY_ERROR);
			return;
		}

		List<String> cmd = new ArrayList<>();
		cmd.add("java");
		cmd.add("-jar");
		cmd.add(jopaJar.getAbsolutePath());
		cmd.add("-s");
		cmd.add(genDir.getAbsolutePath());
		cmd.add("-d");
		cmd.add(genDir.getAbsolutePath());
		cmd.add("jvm.asm");

		LOG.info("JOP microcode assemble: " + String.join(" ", cmd));

		ProcessResult result = runProcess(cmd, genDir);

		String allOutput = result.stdout;
		if (!result.stderr.isEmpty()) {
			allOutput = allOutput + "\n" + result.stderr;
		}
		parseJopaOutput(allOutput);

		if (result.exitCode != 0) {
			addMarker(getProject(),
					"Jopa assembly failed (exit " + result.exitCode + ")",
					-1, IMarker.SEVERITY_ERROR);
		} else {
			LOG.info("JOP microcode assembly completed successfully");
		}
	}

	private void parseJopaOutput(String output) {
		IFile asmFile = findFileInProject("asm/src/jvm.asm", "jvm.asm", "src/jvm.asm");

		for (String line : output.split("\n")) {
			line = line.trim();
			if (line.isEmpty()) continue;

			if (line.contains("Instructions implemented") || line.startsWith("Generated")) {
				LOG.info("Jopa: " + line);
				continue;
			}

			if (containsErrorKeyword(line)) {
				int lineNum = -1;
				String message = line;

				int spaceIdx = line.indexOf(' ');
				if (spaceIdx > 0) {
					try {
						lineNum = Integer.parseInt(line.substring(0, spaceIdx));
						message = line.substring(spaceIdx + 1).trim();
					} catch (NumberFormatException e) {
						// No line number prefix
					}
				}

				IResource target = asmFile != null ? asmFile : getProject();
				addMarker(target, "Jopa: " + message, lineNum, IMarker.SEVERITY_ERROR);
			}
		}
	}

	// ========================================================================
	// Java build (Phase 2): PreLinker + JOPizer
	// ========================================================================

	/**
	 * Build the Java application: PreLinker → JOPizer → .jop file.
	 *
	 * <p>Operates on the classes already compiled by JDT (the Java builder runs
	 * before us). Merges the project's compiled classes with the JOP target
	 * runtime classes, then runs PreLinker and JOPizer.
	 */
	private void buildJava(JopToolchain toolchain, IProgressMonitor monitor) throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "Building JOP application", 100);

		// Check main class is configured
		String mainClass = JopProjectPreferences.get(getProject(),
				JopPreferences.MAIN_CLASS, "");
		if (mainClass.isEmpty()) {
			LOG.info("JOP Java build skipped: no main class configured");
			return;
		}

		// Validate Java tools
		IStatus toolsStatus = toolchain.validateJavaTools();
		if (!toolsStatus.isOK()) {
			addMarker(getProject(), toolsStatus.getMessage(), -1, IMarker.SEVERITY_ERROR);
			return;
		}

		// Get JDT output location (compiled .class files)
		File projectClassesDir = getJdtOutputDir();
		if (projectClassesDir == null || !projectClassesDir.isDirectory()) {
			addMarker(getProject(),
					"No compiled classes found. Build the Java project first.",
					-1, IMarker.SEVERITY_ERROR);
			return;
		}

		// Prepare build output directory
		String outputDirName = JopProjectPreferences.get(getProject(),
				JopPreferences.JOP_OUTPUT_DIR, "build");
		File projectRoot = getProject().getLocation().toFile();
		File buildDir = new File(projectRoot, outputDirName);
		File stagingDir = new File(buildDir, "classes");
		File ppDir = new File(buildDir, "pp");

		// Step 1: Stage classes (merge JOP runtime + project classes)
		sub.subTask("Staging classes");
		stageClasses(toolchain, projectClassesDir, stagingDir);
		sub.worked(20);
		if (sub.isCanceled()) return;

		// Step 2: PreLinker
		sub.subTask("PreLinking (" + mainClass + ")");
		boolean ok = runPreLinker(toolchain, stagingDir, ppDir, mainClass);
		sub.worked(40);
		if (!ok || sub.isCanceled()) return;

		// Step 3: JOPizer
		String simpleClassName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
		File jopFile = new File(buildDir, simpleClassName + ".jop");
		sub.subTask("JOPizing → " + jopFile.getName());
		runJOPizer(toolchain, new File(ppDir, "classes"), jopFile, mainClass);
		sub.worked(40);

		// Refresh so Eclipse sees the generated files
		getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
	}

	/**
	 * Get the JDT compiler output directory for this project.
	 */
	private File getJdtOutputDir() {
		try {
			IJavaProject javaProject = JavaCore.create(getProject());
			if (javaProject == null || !javaProject.exists()) {
				return null;
			}
			IPath outputLocation = javaProject.getOutputLocation();
			IFolder outputFolder = getProject().getWorkspace().getRoot().getFolder(outputLocation);
			if (outputFolder.getLocation() != null) {
				return outputFolder.getLocation().toFile();
			}
		} catch (CoreException e) {
			LOG.error("Failed to get JDT output location", e);
		}
		return null;
	}

	/**
	 * Merge JOP target runtime classes and project-compiled classes into a
	 * single staging directory for PreLinker.
	 */
	private void stageClasses(JopToolchain toolchain, File projectClasses, File stagingDir)
			throws CoreException {
		try {
			// Clean staging dir
			if (stagingDir.exists()) {
				deleteRecursive(stagingDir.toPath());
			}
			stagingDir.mkdirs();

			// Copy JOP target runtime classes first
			File targetClasses = toolchain.getTargetClasses();
			if (targetClasses.isDirectory()) {
				copyDirectory(targetClasses.toPath(), stagingDir.toPath());
			} else {
				LOG.warn("JOP target classes not found at " + targetClasses
						+ " — run 'make' in java/target/");
			}

			// Copy project classes on top (overrides any duplicates)
			copyDirectory(projectClasses.toPath(), stagingDir.toPath());

		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Failed to stage classes for PreLinker", e));
		}
	}

	/**
	 * Run the PreLinker on compiled classes.
	 *
	 * <p>Command: {@code java -cp <toolsCP> com.jopdesign.build.PreLinker
	 * -c <classesDir> -o <ppDir> <mainClass>}
	 *
	 * <p>PreLinker expects the main class in slash-separated format
	 * (e.g., {@code test/HelloWorld}).
	 *
	 * @return true if PreLinker succeeded
	 */
	private boolean runPreLinker(JopToolchain toolchain, File classesDir,
			File ppDir, String mainClass) throws CoreException {
		ppDir.mkdirs();

		// PreLinker wants slash-separated class name
		String slashClass = mainClass.replace('.', '/');

		List<String> cmd = new ArrayList<>();
		cmd.add("java");
		cmd.add("-classpath");
		cmd.add(toolchain.getToolsClasspath());
		cmd.add("com.jopdesign.build.PreLinker");
		cmd.add("-c");
		cmd.add(classesDir.getAbsolutePath());
		cmd.add("-o");
		cmd.add(ppDir.getAbsolutePath());
		cmd.add(slashClass);

		LOG.info("JOP PreLinker: " + String.join(" ", cmd));

		ProcessResult result = runProcess(cmd, classesDir);

		// Parse output for errors
		parseToolOutput(result, "PreLinker");

		if (result.exitCode != 0) {
			addMarker(getProject(),
					"PreLinker failed (exit " + result.exitCode + "): "
					+ firstLine(result.stderr.isEmpty() ? result.stdout : result.stderr),
					-1, IMarker.SEVERITY_ERROR);
			return false;
		}

		LOG.info("JOP PreLinker completed successfully");
		return true;
	}

	/**
	 * Run JOPizer to produce the .jop binary.
	 *
	 * <p>Command: {@code java -Dmgci=false -cp <toolsCP>
	 * com.jopdesign.build.JOPizer -cp <ppClasses> -o <outFile> <mainClass>}
	 *
	 * <p>JOPizer expects the main class in dot-separated format
	 * (e.g., {@code test.HelloWorld}).
	 */
	private void runJOPizer(JopToolchain toolchain, File ppClassesDir,
			File jopFile, String mainClass) throws CoreException {
		List<String> cmd = new ArrayList<>();
		cmd.add("java");
		cmd.add("-Dmgci=false");
		cmd.add("-classpath");
		cmd.add(toolchain.getToolsClasspath());
		cmd.add("com.jopdesign.build.JOPizer");
		cmd.add("-cp");
		cmd.add(ppClassesDir.getAbsolutePath());
		cmd.add("-o");
		cmd.add(jopFile.getAbsolutePath());
		cmd.add(mainClass);

		LOG.info("JOP JOPizer: " + String.join(" ", cmd));

		ProcessResult result = runProcess(cmd, ppClassesDir);

		// Parse output for errors
		parseToolOutput(result, "JOPizer");

		if (result.exitCode != 0) {
			addMarker(getProject(),
					"JOPizer failed (exit " + result.exitCode + "): "
					+ firstLine(result.stderr.isEmpty() ? result.stdout : result.stderr),
					-1, IMarker.SEVERITY_ERROR);
		} else {
			LOG.info("JOP application built: " + jopFile.getAbsolutePath());
		}
	}

	/**
	 * Parse PreLinker/JOPizer output for error messages.
	 *
	 * <p>These tools report errors via:
	 * <ul>
	 *   <li>stderr messages (usage, configuration errors)</li>
	 *   <li>stdout "Error: ..." messages (e.g., "no main() method found")</li>
	 *   <li>Java exception stack traces</li>
	 * </ul>
	 */
	private void parseToolOutput(ProcessResult result, String toolName) {
		String combined = result.stdout;
		if (!result.stderr.isEmpty()) {
			combined = combined + "\n" + result.stderr;
		}

		for (String line : combined.split("\n")) {
			line = line.trim();
			if (line.isEmpty()) continue;

			// Log info lines
			if (!containsErrorKeyword(line) && !line.startsWith("at ") && !line.startsWith("Caused by:")) {
				if (line.contains("KB") || line.contains("methods") || line.contains("classes")) {
					LOG.info(toolName + ": " + line);
				}
				continue;
			}

			// Report errors (but skip stack trace continuation lines)
			if (line.startsWith("at ") || line.startsWith("Caused by:")) {
				continue;
			}

			addMarker(getProject(), toolName + ": " + line, -1, IMarker.SEVERITY_ERROR);
		}
	}

	// ========================================================================
	// Shared utilities
	// ========================================================================

	private boolean containsErrorKeyword(String line) {
		String lower = line.toLowerCase();
		return lower.contains("error")
				|| lower.contains("exception")
				|| lower.contains("not found")
				|| lower.contains("not defined")
				|| lower.contains("already defined")
				|| lower.contains("address too far")
				|| lower.contains("operand wrong")
				|| lower.contains("too many")
				|| lower.contains("missing");
	}

	private IFile findFileInProject(String... candidates) {
		for (String path : candidates) {
			IFile file = getProject().getFile(path);
			if (file.exists()) {
				return file;
			}
		}
		return null;
	}

	// ---- Process execution ----

	private ProcessResult runProcess(List<String> command, File workingDir) throws CoreException {
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(workingDir);

			Process process = pb.start();

			// Read stdout and stderr in parallel to avoid deadlock
			CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
				try {
					return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				} catch (IOException e) {
					return "";
				}
			});
			CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
				try {
					return new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
				} catch (IOException e) {
					return "";
				}
			});

			int exitCode = process.waitFor();
			String stdout = stdoutFuture.join();
			String stderr = stderrFuture.join();

			return new ProcessResult(exitCode, stdout, stderr);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Failed to run: " + String.join(" ", command) + " — " + e.getMessage(), e));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CoreException(new Status(IStatus.CANCEL, JopCorePlugin.PLUGIN_ID,
					"Build interrupted"));
		}
	}

	private record ProcessResult(int exitCode, String stdout, String stderr) {}

	// ---- File helpers ----

	private static void copyDirectory(Path source, Path target) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
					throws IOException {
				Files.createDirectories(target.resolve(source.relativize(dir)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
				Files.copy(file, target.resolve(source.relativize(file)),
						StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteRecursive(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path d, IOException exc)
					throws IOException {
				Files.delete(d);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	// ---- Marker helpers ----

	private void addMarker(IResource resource, String message, int lineNumber, int severity) {
		try {
			IMarker marker = resource.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
			if (lineNumber > 0) {
				marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			}
		} catch (CoreException e) {
			LOG.error("Failed to create marker: " + message, e);
		}
	}

	private static String firstLine(String text) {
		if (text == null || text.isEmpty()) return "(no output)";
		int nl = text.indexOf('\n');
		return nl >= 0 ? text.substring(0, nl).trim() : text.trim();
	}
}
