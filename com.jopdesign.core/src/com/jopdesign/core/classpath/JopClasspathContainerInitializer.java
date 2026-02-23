package com.jopdesign.core.classpath;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.jopdesign.core.toolchain.JopToolchain;

/**
 * Initializes the JOP Runtime classpath container for Java projects.
 *
 * <p>When a project's {@code .classpath} contains an entry like:
 * <pre>
 *   &lt;classpathentry kind="con"
 *     path="com.jopdesign.core.JOP_CLASSPATH_CONTAINER"/&gt;
 * </pre>
 * this initializer resolves it by reading JOP_HOME from the project's
 * preferences and providing the target runtime classes.
 */
public class JopClasspathContainerInitializer extends ClasspathContainerInitializer {

	private static final ILog LOG = Platform.getLog(JopClasspathContainerInitializer.class);

	@Override
	public void initialize(IPath containerPath, IJavaProject project) throws CoreException {
		JopToolchain toolchain;
		try {
			toolchain = JopToolchain.forProject(project.getProject());
		} catch (CoreException e) {
			LOG.warn("JOP classpath container: " + e.getMessage());
			// Set an empty container so the user sees a clear error in the classpath
			IClasspathContainer errorContainer = new IClasspathContainer() {
				@Override public IClasspathEntry[] getClasspathEntries() { return new IClasspathEntry[0]; }
				@Override public String getDescription() { return "JOP Runtime (JOP_HOME not configured)"; }
				@Override public int getKind() { return K_APPLICATION; }
				@Override public IPath getPath() { return containerPath; }
			};
			JavaCore.setClasspathContainer(
					containerPath,
					new IJavaProject[] { project },
					new IClasspathContainer[] { errorContainer },
					null);
			return;
		}

		JopClasspathContainer container = new JopClasspathContainer(containerPath, toolchain);
		JavaCore.setClasspathContainer(
				containerPath,
				new IJavaProject[] { project },
				new JopClasspathContainer[] { container },
				null);
	}

	@Override
	public boolean canUpdateClasspathContainer(IPath containerPath, IJavaProject project) {
		return true;
	}

	@Override
	public void requestClasspathContainerUpdate(IPath containerPath,
			IJavaProject project, org.eclipse.jdt.core.IClasspathContainer containerSuggestion)
			throws CoreException {
		// Re-initialize to pick up any JOP_HOME changes
		initialize(containerPath, project);
	}
}
