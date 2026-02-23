package com.jopdesign.core.nature;

import java.io.File;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.jopdesign.core.classpath.JopClasspathContainer;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;

/**
 * JOP project nature. Added alongside the Java nature to mark a project
 * as targeting the JOP processor. Automatically associates the JOP builder
 * and swaps the JRE System Library for the JOP System Library on the
 * build path.
 */
public class JopNature implements IProjectNature {

	private static final ILog LOG = Platform.getLog(JopNature.class);

	public static final String NATURE_ID = "com.jopdesign.core.jopNature";
	public static final String BUILDER_ID = "com.jopdesign.core.jopBuilder";

	/** Path prefix for the standard JRE classpath container */
	private static final String JRE_CONTAINER_PREFIX = "org.eclipse.jdt.launching.JRE_CONTAINER";

	private IProject project;

	@Override
	public void configure() throws CoreException {
		// Add the JOP builder
		IProjectDescription desc = project.getDescription();
		ICommand[] commands = desc.getBuildSpec();

		boolean hasBuilder = false;
		for (ICommand command : commands) {
			if (command.getBuilderName().equals(BUILDER_ID)) {
				hasBuilder = true;
				break;
			}
		}

		if (!hasBuilder) {
			ICommand[] newCommands = new ICommand[commands.length + 1];
			System.arraycopy(commands, 0, newCommands, 0, commands.length);
			ICommand jopCommand = desc.newCommand();
			jopCommand.setBuilderName(BUILDER_ID);
			newCommands[commands.length] = jopCommand;
			desc.setBuildSpec(newCommands);
			project.setDescription(desc, null);
		}

		// Swap JRE System Library → JOP System Library
		swapJreToJop();

		// Set JDT compiler compliance to 1.8 (minimum JDT supports) for IDE
		// editing. The actual JOP build uses external JDK 1.6 javac.
		setCompilerCompliance("1.8");

		// Link JOP_HOME/asm/ into the project for microcode source access
		linkAsmFolder();
	}

	@Override
	public void deconfigure() throws CoreException {
		// Remove the JOP builder
		IProjectDescription desc = project.getDescription();
		ICommand[] commands = desc.getBuildSpec();

		int count = 0;
		for (ICommand command : commands) {
			if (!command.getBuilderName().equals(BUILDER_ID)) {
				count++;
			}
		}

		ICommand[] newCommands = new ICommand[count];
		int j = 0;
		for (ICommand command : commands) {
			if (!command.getBuilderName().equals(BUILDER_ID)) {
				newCommands[j++] = command;
			}
		}
		desc.setBuildSpec(newCommands);
		project.setDescription(desc, null);

		// Swap JOP System Library → JRE System Library
		swapJopToJre();

		// Remove project-specific compiler compliance (revert to workspace default)
		clearCompilerCompliance();

		// Remove linked asm/ folder if present
		unlinkAsmFolder();
	}

	@Override
	public IProject getProject() {
		return project;
	}

	@Override
	public void setProject(IProject project) {
		this.project = project;
	}

	/**
	 * Replace the JRE System Library with the JOP System Library on the
	 * project's build path.
	 */
	private void swapJreToJop() {
		try {
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject == null || !javaProject.exists()) return;

			IClasspathEntry[] entries = javaProject.getRawClasspath();
			boolean changed = false;
			boolean hasJop = false;
			for (int i = 0; i < entries.length; i++) {
				if (isJopContainer(entries[i])) {
					hasJop = true;
				} else if (isJreContainer(entries[i])) {
					entries[i] = JavaCore.newContainerEntry(
							new Path(JopClasspathContainer.CONTAINER_ID));
					changed = true;
					hasJop = true;
					break;
				}
			}
			if (changed) {
				javaProject.setRawClasspath(entries, null);
			} else if (!hasJop) {
				// No JRE container found to replace — append JOP container
				IClasspathEntry[] newEntries = new IClasspathEntry[entries.length + 1];
				System.arraycopy(entries, 0, newEntries, 0, entries.length);
				newEntries[entries.length] = JavaCore.newContainerEntry(
						new Path(JopClasspathContainer.CONTAINER_ID));
				javaProject.setRawClasspath(newEntries, null);
			}
		} catch (CoreException e) {
			LOG.warn("Failed to swap JRE → JOP on classpath: " + e.getMessage());
		}
	}

	/**
	 * Replace the JOP System Library with the default JRE System Library on
	 * the project's build path.
	 */
	private void swapJopToJre() {
		try {
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject == null || !javaProject.exists()) return;

			IClasspathEntry[] entries = javaProject.getRawClasspath();
			boolean changed = false;
			for (int i = 0; i < entries.length; i++) {
				if (isJopContainer(entries[i])) {
					entries[i] = JavaCore.newContainerEntry(
							new Path(JRE_CONTAINER_PREFIX));
					changed = true;
					break;
				}
			}
			if (changed) {
				javaProject.setRawClasspath(entries, null);
			}
		} catch (CoreException e) {
			LOG.warn("Failed to swap JOP → JRE on classpath: " + e.getMessage());
		}
	}

	/**
	 * Set project-specific JDT compiler compliance for IDE editing.
	 * The actual JOP build uses an external JDK 1.6 javac.
	 */
	private void setCompilerCompliance(String level) {
		try {
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject == null || !javaProject.exists()) return;

			javaProject.setOption(JavaCore.COMPILER_COMPLIANCE, level);
			javaProject.setOption(JavaCore.COMPILER_SOURCE, level);
			javaProject.setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, level);
		} catch (Exception e) {
			LOG.warn("Failed to set compiler compliance: " + e.getMessage());
		}
	}

	/**
	 * Remove project-specific compiler compliance, reverting to workspace default.
	 */
	private void clearCompilerCompliance() {
		try {
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject == null || !javaProject.exists()) return;

			java.util.Map<String, String> options = javaProject.getOptions(false);
			options.remove(JavaCore.COMPILER_COMPLIANCE);
			options.remove(JavaCore.COMPILER_SOURCE);
			options.remove(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM);
			javaProject.setOptions(options);
		} catch (Exception e) {
			LOG.warn("Failed to clear compiler compliance: " + e.getMessage());
		}
	}

	/**
	 * Create a linked folder {@code asm/} pointing to {@code JOP_HOME/asm/}
	 * so that microcode sources are visible in the Package Explorer.
	 */
	private void linkAsmFolder() {
		try {
			String jopHome = JopProjectPreferences.get(project, JopPreferences.JOP_HOME, "");
			if (jopHome.isEmpty()) return;

			File asmDir = new File(jopHome, "asm");
			if (!asmDir.isDirectory()) return;

			IFolder asmFolder = project.getFolder("asm");
			if (!asmFolder.exists()) {
				asmFolder.createLink(new Path(asmDir.getAbsolutePath()),
						IResource.NONE, null);
				LOG.info("Linked asm/ → " + asmDir.getAbsolutePath());
			}
		} catch (Exception e) {
			LOG.warn("Failed to link asm/ folder: " + e.getMessage());
		}
	}

	/**
	 * Remove the linked {@code asm/} folder if it was created by us (i.e. is a linked resource).
	 */
	private void unlinkAsmFolder() {
		try {
			IFolder asmFolder = project.getFolder("asm");
			if (asmFolder.exists() && asmFolder.isLinked()) {
				asmFolder.delete(true, null);
				LOG.info("Removed linked asm/ folder");
			}
		} catch (Exception e) {
			LOG.warn("Failed to remove linked asm/ folder: " + e.getMessage());
		}
	}

	private static boolean isJreContainer(IClasspathEntry entry) {
		return entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER
				&& entry.getPath().segment(0).equals(JRE_CONTAINER_PREFIX);
	}

	private static boolean isJopContainer(IClasspathEntry entry) {
		return entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER
				&& entry.getPath().segment(0).equals(JopClasspathContainer.CONTAINER_ID);
	}

	/**
	 * Check whether a project has the JOP nature.
	 */
	public static boolean hasNature(IProject project) {
		try {
			return project.isOpen() && project.hasNature(NATURE_ID);
		} catch (CoreException e) {
			return false;
		}
	}

	/**
	 * Add JOP nature to a project. The project must already have the Java nature.
	 */
	public static void addNature(IProject project) throws CoreException {
		if (hasNature(project)) {
			return;
		}
		IProjectDescription desc = project.getDescription();
		String[] natures = desc.getNatureIds();
		String[] newNatures = new String[natures.length + 1];
		System.arraycopy(natures, 0, newNatures, 0, natures.length);
		newNatures[natures.length] = NATURE_ID;
		desc.setNatureIds(newNatures);
		project.setDescription(desc, null);
	}

	/**
	 * Remove JOP nature from a project.
	 */
	public static void removeNature(IProject project) throws CoreException {
		if (!hasNature(project)) {
			return;
		}
		IProjectDescription desc = project.getDescription();
		String[] natures = desc.getNatureIds();
		String[] newNatures = new String[natures.length - 1];
		int j = 0;
		for (String nature : natures) {
			if (!nature.equals(NATURE_ID)) {
				newNatures[j++] = nature;
			}
		}
		desc.setNatureIds(newNatures);
		project.setDescription(desc, null);
	}
}
