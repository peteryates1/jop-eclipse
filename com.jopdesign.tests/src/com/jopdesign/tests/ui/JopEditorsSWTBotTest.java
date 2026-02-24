package com.jopdesign.tests.ui;

import static org.junit.Assert.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * SWTBot tests for JOP editors (MicrocodeEditor and JopTextEditor).
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JopEditorsSWTBotTest {

	private static SWTWorkbenchBot bot;

	@BeforeClass
	public static void initBot() {
		SWTBotPreferences.TIMEOUT = 15000;
		bot = new SWTWorkbenchBot();
		SWTBotTestUtil.closeWelcome(bot);
	}

	@Before
	public void setUp() throws Exception {
		SWTBotTestUtil.createJavaProjectWithNature();
	}

	@After
	public void tearDown() throws Exception {
		bot.closeAllEditors();
		SWTBotTestUtil.deleteProject();
	}

	@Test
	public void testAsmFileOpensMicrocodeEditor() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		IFile file = SWTBotTestUtil.createFile(project, "test.asm",
				"nop\nnop\nwait\n");

		openFileInEditor(file);
		bot.sleep(1000);

		SWTBotEditor editor = bot.activeEditor();
		assertNotNull("Editor should be open", editor);
		assertEquals("Editor title should match filename", "test.asm", editor.getTitle());

		// Verify it's the MicrocodeEditor
		String editorId = getActiveEditorId();
		assertEquals("Should open MicrocodeEditor for .asm files",
				"com.jopdesign.microcode.editor", editorId);
	}

	@Test
	public void testMicFileOpensMicrocodeEditor() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		IFile file = SWTBotTestUtil.createFile(project, "test.mic",
				"nop\nadd\nwait\n");

		openFileInEditor(file);
		bot.sleep(1000);

		SWTBotEditor editor = bot.activeEditor();
		assertNotNull("Editor should be open", editor);

		String editorId = getActiveEditorId();
		assertEquals("Should open MicrocodeEditor for .mic files",
				"com.jopdesign.microcode.editor", editorId);
	}

	@Test
	public void testJopFileOpensJopTextEditor() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		IFile file = SWTBotTestUtil.createFile(project, "test.jop",
				"// JOP binary output\n00000000\n");

		openFileInEditor(file);
		bot.sleep(1000);

		SWTBotEditor editor = bot.activeEditor();
		assertNotNull("Editor should be open", editor);
		assertEquals("Editor title should match filename", "test.jop", editor.getTitle());

		String editorId = getActiveEditorId();
		assertEquals("Should open JopTextEditor for .jop files",
				"com.jopdesign.ui.editors.jopTextEditor", editorId);
	}

	@Test
	public void testMicrocodeEditorHasStyledText() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		IFile file = SWTBotTestUtil.createFile(project, "styled.asm",
				"// comment\nnop\nadd\n");

		openFileInEditor(file);
		bot.sleep(1000);

		SWTBotEditor editor = bot.activeEditor();
		assertNotNull("Editor should be open", editor);

		// The editor should contain a StyledText widget (text editor with syntax coloring)
		assertNotNull("Editor should have a styled text widget",
				editor.bot().styledText());
	}

	@Test
	public void testJopTextEditorHasStyledText() throws Exception {
		IProject project = SWTBotTestUtil.getProject();
		IFile file = SWTBotTestUtil.createFile(project, "output.jop",
				"// Generated JOP binary\n00000000\n");

		openFileInEditor(file);
		bot.sleep(1000);

		SWTBotEditor editor = bot.activeEditor();
		assertNotNull("Editor should be open", editor);

		assertNotNull("JOP text editor should have a styled text widget",
				editor.bot().styledText());
	}

	// ---- Helpers ----

	private void openFileInEditor(IFile file) {
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			try {
				IDE.openEditor(
						PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
						file);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private String getActiveEditorId() {
		final String[] id = { null };
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			IEditorPart editor = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage().getActiveEditor();
			if (editor != null) {
				id[0] = editor.getEditorSite().getId();
			}
		});
		return id[0];
	}
}
