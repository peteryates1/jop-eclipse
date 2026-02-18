package com.jopdesign.core.builder;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * JOP incremental project builder. Orchestrates the JOP build pipeline:
 * <ol>
 *   <li>Microcode: .asm → preprocessor → Jopa → ROM/RAM data</li>
 *   <li>Java: .class → PreLinker → JOPizer → .jop</li>
 * </ol>
 */
public class JopBuilder extends IncrementalProjectBuilder {

	public static final String BUILDER_ID = "com.jopdesign.core.jopBuilder";

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
		// TODO: Clean generated .jop files, ROM data, preprocessed output
	}

	private void fullBuild(IProgressMonitor monitor) throws CoreException {
		monitor.beginTask("JOP Full Build", 4);
		try {
			// Step 1: Preprocess microcode (.asm through cpp)
			monitor.subTask("Preprocessing microcode");
			// TODO: invoke gcc -E -C -P on .asm files
			monitor.worked(1);

			// Step 2: Assemble microcode (Jopa)
			monitor.subTask("Assembling microcode");
			// TODO: invoke Jopa to generate ROM/RAM data
			monitor.worked(1);

			// Step 3: PreLink Java classes
			monitor.subTask("PreLinking Java classes");
			// TODO: invoke PreLinker on compiled .class files
			monitor.worked(1);

			// Step 4: JOPize
			monitor.subTask("JOPizing");
			// TODO: invoke JOPizer to produce .jop output
			monitor.worked(1);
		} finally {
			monitor.done();
		}
	}

	private void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor)
			throws CoreException {
		// TODO: Check delta for .asm and .class changes, rebuild as needed
		// For now, fall back to full build
		fullBuild(monitor);
	}
}
