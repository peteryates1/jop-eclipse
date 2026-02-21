package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.junit.Test;

import com.jopdesign.core.classpath.JopClasspathContainer;
import com.jopdesign.core.toolchain.JopToolchain;

/**
 * Tests for {@link JopClasspathContainer}.
 */
public class JopClasspathContainerTest {

	@Test
	public void testKindIsDefaultSystem() {
		JopToolchain tc = new JopToolchain(new Path("/dummy/jop"));
		JopClasspathContainer container = new JopClasspathContainer(
				new Path(JopClasspathContainer.CONTAINER_ID), tc);

		assertEquals(IClasspathContainer.K_DEFAULT_SYSTEM, container.getKind());
	}

	@Test
	public void testDescription() {
		JopToolchain tc = new JopToolchain(new Path("/dummy/jop"));
		JopClasspathContainer container = new JopClasspathContainer(
				new Path(JopClasspathContainer.CONTAINER_ID), tc);

		assertEquals("JOP System Library", container.getDescription());
	}

	@Test
	public void testContainerPath() {
		JopToolchain tc = new JopToolchain(new Path("/dummy/jop"));
		JopClasspathContainer container = new JopClasspathContainer(
				new Path(JopClasspathContainer.CONTAINER_ID), tc);

		assertEquals(JopClasspathContainer.CONTAINER_ID,
				container.getPath().toString());
	}

	@Test
	public void testEmptyEntriesWhenDirMissing() {
		JopToolchain tc = new JopToolchain(new Path("/nonexistent/jop"));
		JopClasspathContainer container = new JopClasspathContainer(
				new Path(JopClasspathContainer.CONTAINER_ID), tc);

		assertEquals(0, container.getClasspathEntries().length);
	}
}
