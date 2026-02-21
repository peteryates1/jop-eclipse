package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import org.junit.Test;

import com.jopdesign.core.board.BoardDefinition;

/**
 * Tests for {@link BoardDefinition} record validation.
 */
public class BoardDefinitionTest {

	@Test
	public void testValidBoardDefinition() {
		BoardDefinition board = createBoard(4096, 64);
		assertEquals("test-board", board.id());
		assertEquals("Test Board", board.name());
		assertEquals(4096, board.methodCacheSize());
		assertEquals(64, board.stackBufferSize());
	}

	@Test
	public void testMinimumValues() {
		BoardDefinition board = createBoard(256, 8);
		assertEquals(256, board.methodCacheSize());
		assertEquals(8, board.stackBufferSize());
	}

	@Test
	public void testMaximumValues() {
		BoardDefinition board = createBoard(16384, 192);
		assertEquals(16384, board.methodCacheSize());
		assertEquals(192, board.stackBufferSize());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMethodCacheTooSmall() {
		createBoard(128, 64);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMethodCacheTooLarge() {
		createBoard(32768, 64);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStackBufferTooSmall() {
		createBoard(4096, 4);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStackBufferTooLarge() {
		createBoard(4096, 512);
	}

	@Test
	public void testCmpFields() {
		BoardDefinition board = createBoard(4096, 64);
		assertFalse(board.enableMultiCore());
		assertEquals(1, board.cpuCount());
		assertFalse(board.enableDebug());
	}

	private BoardDefinition createBoard(int methodCacheSize, int stackBufferSize) {
		return new BoardDefinition(
				"test-board", "Test Board",
				"Cyclone IV GX", "EP4CGX150DF27I7",
				"quartus", "altera",
				50, 100,
				"bram", "32KB", "bram",
				1000000,
				"jop.system.JopBramTopVerilog",
				"fpga/test-board",
				methodCacheSize, stackBufferSize,
				true, 4, true, 4,
				false, 1, false);
	}
}
