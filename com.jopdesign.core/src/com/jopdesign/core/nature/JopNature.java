package com.jopdesign.core.nature;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

/**
 * JOP project nature. Added alongside the Java nature to mark a project
 * as targeting the JOP processor. Automatically associates the JOP builder.
 */
public class JopNature implements IProjectNature {

	public static final String NATURE_ID = "com.jopdesign.core.jopNature";
	public static final String BUILDER_ID = "com.jopdesign.core.jopBuilder";

	private IProject project;

	@Override
	public void configure() throws CoreException {
		IProjectDescription desc = project.getDescription();
		ICommand[] commands = desc.getBuildSpec();

		for (ICommand command : commands) {
			if (command.getBuilderName().equals(BUILDER_ID)) {
				return;
			}
		}

		ICommand[] newCommands = new ICommand[commands.length + 1];
		System.arraycopy(commands, 0, newCommands, 0, commands.length);
		ICommand jopCommand = desc.newCommand();
		jopCommand.setBuilderName(BUILDER_ID);
		newCommands[commands.length] = jopCommand;
		desc.setBuildSpec(newCommands);
		project.setDescription(desc, null);
	}

	@Override
	public void deconfigure() throws CoreException {
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
