package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.jopdesign.core.board.BoardDefinition;
import com.jopdesign.core.board.BoardRegistry;

/**
 * Tests for {@link BoardRegistry} — loading and querying board definitions.
 */
public class BoardRegistryTest {

	@Test
	public void testBoardsLoaded() {
		Map<String, BoardDefinition> boards = BoardRegistry.getBoards();
		assertNotNull(boards);
		assertFalse("boards.json should contain at least one board", boards.isEmpty());
	}

	@Test
	public void testExpectedBoardCount() {
		// boards.json defines 7 boards
		assertEquals(7, BoardRegistry.getBoards().size());
	}

	@Test
	public void testGetBoardById() {
		BoardDefinition bram = BoardRegistry.getBoard("qmtech-ep4cgx150-bram");
		assertNotNull("QMTECH BRAM board should exist", bram);
		assertEquals("QMTECH EP4CGX150 (BRAM)", bram.name());
		assertEquals("Cyclone IV GX", bram.fpgaFamily());
		assertEquals("quartus", bram.synthTool());
		assertEquals("altera", bram.fpgaTarget());
	}

	@Test
	public void testGetBoardByIdNotFound() {
		assertNull(BoardRegistry.getBoard("nonexistent-board"));
	}

	@Test
	public void testBoardIds() {
		List<String> ids = BoardRegistry.getBoardIds();
		assertTrue(ids.contains("qmtech-ep4cgx150-bram"));
		assertTrue(ids.contains("qmtech-ep4cgx150-sdram"));
		assertTrue(ids.contains("alchitry-au-ddr3"));
		assertTrue(ids.contains("cyc5000-sdram"));
		assertTrue(ids.contains("cyc1000"));
		assertTrue(ids.contains("max1000"));
		assertTrue(ids.contains("minimal"));
	}

	@Test
	public void testBoardNames() {
		List<String> names = BoardRegistry.getBoardNames();
		assertEquals(7, names.size());
		assertTrue(names.contains("QMTECH EP4CGX150 (BRAM)"));
		assertTrue(names.contains("Minimal (Simulation)"));
	}

	@Test
	public void testAlchitryAuBoard() {
		BoardDefinition au = BoardRegistry.getBoard("alchitry-au-ddr3");
		assertNotNull(au);
		assertEquals("Artix-7", au.fpgaFamily());
		assertEquals("vivado", au.synthTool());
		assertEquals("xilinx", au.fpgaTarget());
		assertEquals("ddr3", au.memoryType());
		assertEquals(100, au.clockInputMhz());
		assertEquals(4096, au.methodCacheSize());
	}

	@Test
	public void testMinimalBoard() {
		BoardDefinition minimal = BoardRegistry.getBoard("minimal");
		assertNotNull(minimal);
		assertEquals("bram", minimal.memoryType());
		assertEquals(1024, minimal.methodCacheSize());
		assertEquals(16, minimal.stackBufferSize());
		assertFalse(minimal.useOcache());
		assertFalse(minimal.useAcache());
	}

	@Test
	public void testBoardCmpDefaults() {
		for (BoardDefinition board : BoardRegistry.getBoards().values()) {
			assertFalse("All predefined boards should default to single-core",
					board.enableMultiCore());
			assertEquals(1, board.cpuCount());
			assertFalse(board.enableDebug());
		}
	}

	@Test
	public void testBoardsAreImmutable() {
		Map<String, BoardDefinition> boards = BoardRegistry.getBoards();
		try {
			boards.put("fake", null);
			fail("Board map should be unmodifiable");
		} catch (UnsupportedOperationException e) {
			// expected
		}
	}
}
