package com.jopdesign.core.classpath;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;

import com.jopdesign.core.toolchain.JopToolchain;

/**
 * Classpath container that provides JOP target runtime classes to the
 * Java build path. This enables content assist, navigation, and
 * compilation against the JOP runtime (com.jopdesign.sys.Native,
 * com.jopdesign.io.*, etc.) and JVM stubs (java.lang.*, java.io.*).
 *
 * <p>Add to a project's build path via:
 * <pre>
 *   Project Properties > Java Build Path > Libraries > Add Library > JOP Runtime
 * </pre>
 * or programmatically with container path {@value #CONTAINER_ID}.
 */
public class JopClasspathContainer implements IClasspathContainer {

	public static final String CONTAINER_ID = "com.jopdesign.core.JOP_CLASSPATH_CONTAINER";

	private final IPath containerPath;
	private final JopToolchain toolchain;

	public JopClasspathContainer(IPath containerPath, JopToolchain toolchain) {
		this.containerPath = containerPath;
		this.toolchain = toolchain;
	}

	@Override
	public IClasspathEntry[] getClasspathEntries() {
		List<IClasspathEntry> entries = new ArrayList<>();

		File targetClasses = toolchain.getTargetClasses();
		if (targetClasses.isDirectory()) {
			// Add compiled target classes as a library entry.
			// Attach JOP runtime source for navigation.
			IPath classesPath = new Path(targetClasses.getAbsolutePath());
			IPath sourcePath = null;
			File jopSrc = toolchain.getTargetSourceJop();
			if (jopSrc.isDirectory()) {
				sourcePath = new Path(jopSrc.getAbsolutePath());
			}
			entries.add(JavaCore.newLibraryEntry(classesPath, sourcePath, null));
		}

		return entries.toArray(new IClasspathEntry[0]);
	}

	@Override
	public String getDescription() {
		return "JOP Runtime";
	}

	@Override
	public int getKind() {
		return K_APPLICATION;
	}

	@Override
	public IPath getPath() {
		return containerPath;
	}
}
