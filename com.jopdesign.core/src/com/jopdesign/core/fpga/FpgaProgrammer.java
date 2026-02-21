package com.jopdesign.core.fpga;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.board.BoardDefinition;
import com.jopdesign.core.board.BoardRegistry;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;
import com.jopdesign.core.toolchain.JopToolchain;

/**
 * Programs FPGA bitstreams via JTAG using Quartus or Vivado.
 *
 * <p>Quartus flow: auto-generates a CDF file, then invokes
 * {@code quartus_pgm -c "USB-Blaster" -m JTAG project.cdf}.
 *
 * <p>Vivado flow: invokes {@code program_bitstream.tcl} via
 * the board's {@code run_vivado.sh} wrapper or Vivado batch mode.
 */
public class FpgaProgrammer {

	private static final ILog LOG = Platform.getLog(FpgaProgrammer.class);

	private final IProject project;
	private final JopToolchain toolchain;
	private final BoardDefinition board;

	public FpgaProgrammer(IProject project, JopToolchain toolchain, BoardDefinition board) {
		this.project = project;
		this.toolchain = toolchain;
		this.board = board;
	}

	/**
	 * Create a programmer from the project's current preferences.
	 */
	public static FpgaProgrammer forProject(IProject project) throws CoreException {
		JopToolchain tc = JopToolchain.forProject(project);
		String boardId = JopProjectPreferences.get(project, JopPreferences.BOARD_ID, "");
		if (boardId.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"No board selected. Configure a board in Project Properties > JOP > Board Configuration."));
		}
		BoardDefinition board = BoardRegistry.getBoard(boardId);
		if (board == null) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Unknown board: " + boardId));
		}
		return new FpgaProgrammer(project, tc, board);
	}

	/**
	 * Program the FPGA with the synthesized bitstream.
	 *
	 * @param monitor progress monitor
	 * @return result with console output
	 */
	public SynthesisResult program(IProgressMonitor monitor) throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "Programming FPGA", 100);

		String synthTool = board.synthTool();
		if (synthTool.isEmpty() || "none".equals(synthTool)) {
			return new SynthesisResult(false,
					"--- No synthesis tool for board " + board.name() + " (simulation only) ---\n");
		}

		if ("quartus".equals(synthTool)) {
			return programQuartus(sub);
		} else if ("vivado".equals(synthTool)) {
			return programVivado(sub);
		} else {
			return new SynthesisResult(false,
					"--- Unknown synthesis tool: " + synthTool + " ---\n");
		}
	}

	// ========================================================================
	// Quartus programming
	// ========================================================================

	private SynthesisResult programQuartus(SubMonitor sub) throws CoreException {
		sub.subTask("Programming FPGA (Quartus)");

		File fpgaDir = toolchain.getFpgaDir(board.fpgaDir());
		if (!fpgaDir.isDirectory()) {
			return new SynthesisResult(false,
					"--- FPGA directory not found: " + fpgaDir + " ---\n");
		}

		// Find the .sof bitstream
		String quartusProject = JopProjectPreferences.get(project,
				JopPreferences.QUARTUS_PROJECT, "");
		if (quartusProject.isEmpty()) {
			quartusProject = findQuartusProject(fpgaDir);
			if (quartusProject == null) {
				return new SynthesisResult(false,
						"--- No .qpf file found in " + fpgaDir + " ---\n");
			}
		}

		File outputDir = new File(fpgaDir, "output_files");
		File sofFile = new File(outputDir, quartusProject + ".sof");
		if (!sofFile.exists()) {
			return new SynthesisResult(false,
					"--- Bitstream not found: " + sofFile + "\n"
					+ "--- Run FPGA synthesis first ---\n");
		}

		// Generate CDF file
		File cdfFile = new File(fpgaDir, quartusProject + ".cdf");
		generateCdf(cdfFile, sofFile, board.fpgaDevice());

		// Run quartus_pgm
		File quartusDir = JopToolchain.getQuartusDir(project);
		List<String> cmd = new ArrayList<>();
		if (quartusDir != null && quartusDir.isDirectory()) {
			cmd.add(new File(quartusDir, "quartus_pgm").getAbsolutePath());
		} else {
			cmd.add("quartus_pgm");
		}
		cmd.add("-c");
		cmd.add("USB-Blaster");
		cmd.add("-m");
		cmd.add("JTAG");
		cmd.add(cdfFile.getAbsolutePath());

		StringBuilder output = new StringBuilder();
		output.append("=== Programming FPGA (Quartus) ===\n");
		output.append("Bitstream: ").append(sofFile).append('\n');
		output.append("Command: ").append(String.join(" ", cmd)).append('\n');
		output.append('\n');

		LOG.info("Quartus programming: " + String.join(" ", cmd));

		ProcessResult result = runProcess(cmd, fpgaDir);
		output.append(result.stdout());
		if (!result.stderr().isEmpty()) {
			output.append(result.stderr());
		}
		output.append('\n');

		sub.worked(100);

		if (result.exitCode() == 0) {
			output.append("--- FPGA programmed successfully ---\n");
			return new SynthesisResult(true, output.toString());
		} else {
			output.append("--- Programming FAILED (exit code ")
					.append(result.exitCode()).append(") ---\n");
			return new SynthesisResult(false, output.toString());
		}
	}

	/**
	 * Generate a Quartus CDF (Chain Description File) for JTAG programming.
	 */
	private void generateCdf(File cdfFile, File sofFile, String fpgaDevice) throws CoreException {
		// Strip package/speed suffix for PartName (e.g., EP4CGX150DF27I7 → EP4CGX150DF27)
		String partName = fpgaDevice;
		if (partName.length() > 4) {
			// Remove trailing speed grade characters if present
			int len = partName.length();
			if (Character.isDigit(partName.charAt(len - 1))
					&& Character.isLetter(partName.charAt(len - 2))) {
				partName = partName.substring(0, len - 2);
			}
		}

		String outputDir = sofFile.getParent();
		String sofName = sofFile.getName();

		StringBuilder cdf = new StringBuilder();
		cdf.append("/* Auto-generated CDF for JTAG programming */\n");
		cdf.append("JedecChain;\n");
		cdf.append("  FileRevision(JESD32A);\n");
		cdf.append("  DefaultMfr(6E);\n");
		cdf.append("  P ActionCode(Cfg)\n");
		cdf.append("    Device PartName(").append(partName).append(") ");
		cdf.append("Path(\"").append(outputDir).append("/\") ");
		cdf.append("File(\"").append(sofName).append("\") MfrSpec(OpMask(1));\n");
		cdf.append("ChainEnd;\n");
		cdf.append("\n");
		cdf.append("AlteraBegin;\n");
		cdf.append("  ChainType(JTAG);\n");
		cdf.append("AlteraEnd;\n");

		try {
			Files.writeString(cdfFile.toPath(), cdf.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Failed to write CDF file: " + cdfFile, e));
		}
	}

	// ========================================================================
	// Vivado programming
	// ========================================================================

	private SynthesisResult programVivado(SubMonitor sub) throws CoreException {
		sub.subTask("Programming FPGA (Vivado)");

		File fpgaDir = toolchain.getFpgaDir(board.fpgaDir());
		if (!fpgaDir.isDirectory()) {
			return new SynthesisResult(false,
					"--- FPGA directory not found: " + fpgaDir + " ---\n");
		}

		File scriptsDir = new File(fpgaDir, "scripts");
		File tclScript = findFile(fpgaDir, "scripts/program_bitstream.tcl",
				"vivado/tcl/program_bitstream.tcl", "tcl/program_bitstream.tcl");
		if (tclScript == null) {
			return new SynthesisResult(false,
					"--- program_bitstream.tcl not found in " + fpgaDir + " ---\n");
		}

		// Use run_vivado.sh wrapper if available, otherwise direct Vivado
		File runScript = new File(scriptsDir, "run_vivado.sh");
		List<String> cmd;
		if (runScript.exists()) {
			cmd = List.of("bash", runScript.getAbsolutePath(),
					tclScript.getName().contains("/") ? tclScript.getAbsolutePath()
							: tclScript.getName());
		} else {
			File vivadoDir = JopToolchain.getVivadoDir(project);
			cmd = new ArrayList<>();
			if (vivadoDir != null && vivadoDir.isDirectory()) {
				cmd.add(new File(vivadoDir, "bin/vivado").getAbsolutePath());
			} else {
				cmd.add("vivado");
			}
			cmd.add("-mode");
			cmd.add("batch");
			cmd.add("-source");
			cmd.add(tclScript.getAbsolutePath());
		}

		StringBuilder output = new StringBuilder();
		output.append("=== Programming FPGA (Vivado) ===\n");
		output.append("Script: ").append(tclScript).append('\n');
		output.append("Command: ").append(String.join(" ", cmd)).append('\n');
		output.append('\n');

		LOG.info("Vivado programming: " + String.join(" ", cmd));

		ProcessResult result = runProcess(cmd, fpgaDir);
		output.append(result.stdout());
		if (!result.stderr().isEmpty()) {
			output.append(result.stderr());
		}
		output.append('\n');

		sub.worked(100);

		if (result.exitCode() == 0) {
			output.append("--- FPGA programmed successfully ---\n");
			return new SynthesisResult(true, output.toString());
		} else {
			output.append("--- Programming FAILED (exit code ")
					.append(result.exitCode()).append(") ---\n");
			return new SynthesisResult(false, output.toString());
		}
	}

	// ========================================================================
	// Helpers
	// ========================================================================

	private String findQuartusProject(File fpgaDir) {
		File[] qpfFiles = fpgaDir.listFiles((dir, name) -> name.endsWith(".qpf"));
		if (qpfFiles != null && qpfFiles.length > 0) {
			String name = qpfFiles[0].getName();
			return name.substring(0, name.length() - 4);
		}
		return null;
	}

	private File findFile(File baseDir, String... paths) {
		for (String path : paths) {
			File f = new File(baseDir, path);
			if (f.exists()) return f;
		}
		return null;
	}

	private ProcessResult runProcess(List<String> command, File workingDir) throws CoreException {
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(workingDir);

			Process process = pb.start();

			CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
				try {
					return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				} catch (IOException e) {
					return "";
				}
			});
			CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
				try {
					return new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
				} catch (IOException e) {
					return "";
				}
			});

			int exitCode = process.waitFor();
			String stdout = stdoutFuture.join();
			String stderr = stderrFuture.join();

			return new ProcessResult(exitCode, stdout, stderr);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JopCorePlugin.PLUGIN_ID,
					"Failed to run: " + String.join(" ", command) + " — " + e.getMessage(), e));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CoreException(new Status(IStatus.CANCEL, JopCorePlugin.PLUGIN_ID,
					"Programming interrupted"));
		}
	}

	private record ProcessResult(int exitCode, String stdout, String stderr) {}
}
