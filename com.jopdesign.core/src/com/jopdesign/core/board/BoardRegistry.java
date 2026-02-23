package com.jopdesign.core.board;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

/**
 * Registry of known JOP target boards, loaded from the bundled
 * {@code boards.json} resource.
 *
 * <p>Board definitions are parsed with a simple JSON parser (no external
 * dependencies) and cached on first access.
 */
public final class BoardRegistry {

	private static final ILog LOG = Platform.getLog(BoardRegistry.class);

	private static Map<String, BoardDefinition> boards;

	private BoardRegistry() {}

	/** Get all registered boards, keyed by ID */
	public static synchronized Map<String, BoardDefinition> getBoards() {
		if (boards == null) {
			boards = loadBoards();
		}
		return boards;
	}

	/** Get a board by ID, or null if not found */
	public static BoardDefinition getBoard(String id) {
		return getBoards().get(id);
	}

	/** Get all board IDs in display order */
	public static List<String> getBoardIds() {
		return new ArrayList<>(getBoards().keySet());
	}

	/** Get display names for all boards, in the same order as getBoardIds() */
	public static List<String> getBoardNames() {
		List<String> names = new ArrayList<>();
		for (BoardDefinition board : getBoards().values()) {
			names.add(board.name());
		}
		return names;
	}

	private static Map<String, BoardDefinition> loadBoards() {
		Map<String, BoardDefinition> result = new LinkedHashMap<>();
		try (InputStream is = BoardRegistry.class.getResourceAsStream("boards.json")) {
			if (is == null) {
				LOG.error("boards.json resource not found");
				return result;
			}
			String json = readFully(is);
			parseBoards(json, result);
		} catch (Exception e) {
			LOG.error("Failed to load board definitions", e);
		}
		return Collections.unmodifiableMap(result);
	}

	private static String readFully(InputStream is) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (Reader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			int ch;
			while ((ch = r.read()) != -1) {
				sb.append((char) ch);
			}
		}
		return sb.toString();
	}

	// ---- Minimal JSON parser (no external deps) ----

	/**
	 * Parse the boards JSON. Expected format:
	 * <pre>
	 * { "boards": [ { ... }, { ... } ] }
	 * </pre>
	 */
	private static void parseBoards(String json, Map<String, BoardDefinition> result) {
		// Find the "boards" array
		int arrStart = json.indexOf('[');
		int arrEnd = json.lastIndexOf(']');
		if (arrStart < 0 || arrEnd < 0) return;
		String arrContent = json.substring(arrStart + 1, arrEnd);

		// Split into individual board objects
		int depth = 0;
		int objStart = -1;
		for (int i = 0; i < arrContent.length(); i++) {
			char c = arrContent.charAt(i);
			if (c == '{') {
				if (depth == 0) objStart = i;
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0 && objStart >= 0) {
					String obj = arrContent.substring(objStart, i + 1);
					try {
						BoardDefinition board = parseBoard(obj);
						result.put(board.id(), board);
					} catch (Exception e) {
						LOG.warn("Failed to parse board definition: " + e.getMessage());
					}
					objStart = -1;
				}
			}
		}
	}

	private static BoardDefinition parseBoard(String obj) {
		return new BoardDefinition(
				getString(obj, "id"),
				getString(obj, "name"),
				getString(obj, "fpgaFamily"),
				getString(obj, "fpgaDevice"),
				getString(obj, "synthTool"),
				getString(obj, "fpgaTarget"),
				getInt(obj, "clockInputMhz", 50),
				getInt(obj, "systemClockMhz", 100),
				getString(obj, "memoryType"),
				getString(obj, "memorySize"),
				getString(obj, "bootMode"),
				getInt(obj, "uartBaud", 1000000),
				getString(obj, "topModule"),
				getString(obj, "fpgaDir"),
				getInt(obj, "methodCacheSize", 4096),
				getInt(obj, "stackBufferSize", 192),
				getBool(obj, "useOcache", true),
				getInt(obj, "ocacheWayBits", 4),
				getBool(obj, "useAcache", true),
				getInt(obj, "acacheWayBits", 4),
				getBool(obj, "enableMultiCore", false),
				getInt(obj, "cpuCount", 1),
				getBool(obj, "enableDebug", false)
		);
	}

	private static String getString(String json, String key) {
		String pattern = "\"" + key + "\"";
		int idx = json.indexOf(pattern);
		if (idx < 0) return "";
		int colonIdx = json.indexOf(':', idx + pattern.length());
		if (colonIdx < 0) return "";
		int quoteStart = json.indexOf('"', colonIdx + 1);
		if (quoteStart < 0) return "";
		// Scan for unescaped closing quote
		int quoteEnd = quoteStart + 1;
		while (quoteEnd < json.length()) {
			char c = json.charAt(quoteEnd);
			if (c == '\\') {
				quoteEnd += 2; // Skip escaped character
				continue;
			}
			if (c == '"') break;
			quoteEnd++;
		}
		if (quoteEnd >= json.length()) return "";
		String raw = json.substring(quoteStart + 1, quoteEnd);
		// Unescape common JSON escape sequences
		if (raw.indexOf('\\') < 0) return raw;
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '\\' && i + 1 < raw.length()) {
				char next = raw.charAt(++i);
				switch (next) {
					case '"', '\\', '/' -> sb.append(next);
					case 'n' -> sb.append('\n');
					case 't' -> sb.append('\t');
					case 'r' -> sb.append('\r');
					default -> { sb.append('\\'); sb.append(next); }
				}
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static int getInt(String json, String key, int defaultValue) {
		String pattern = "\"" + key + "\"";
		int idx = json.indexOf(pattern);
		if (idx < 0) return defaultValue;
		int colonIdx = json.indexOf(':', idx + pattern.length());
		if (colonIdx < 0) return defaultValue;
		// Skip whitespace after colon, read digits
		int numStart = colonIdx + 1;
		while (numStart < json.length() && Character.isWhitespace(json.charAt(numStart))) {
			numStart++;
		}
		int numEnd = numStart;
		if (numEnd < json.length() && json.charAt(numEnd) == '-') {
			numEnd++;
		}
		while (numEnd < json.length() && Character.isDigit(json.charAt(numEnd))) {
			numEnd++;
		}
		if (numEnd == numStart) return defaultValue;
		try {
			return Integer.parseInt(json.substring(numStart, numEnd));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static boolean getBool(String json, String key, boolean defaultValue) {
		String pattern = "\"" + key + "\"";
		int idx = json.indexOf(pattern);
		if (idx < 0) return defaultValue;
		int colonIdx = json.indexOf(':', idx + pattern.length());
		if (colonIdx < 0) return defaultValue;
		String rest = json.substring(colonIdx + 1).trim();
		if (rest.startsWith("true")) return true;
		if (rest.startsWith("false")) return false;
		return defaultValue;
	}
}
