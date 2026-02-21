package com.jopdesign.core.fpga;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Drives the FPGA synthesis pipeline:
 * <ol>
 *   <li>SpinalHDL Verilog generation via SBT</li>
 *   <li>FPGA synthesis via Quartus (Intel) or Vivado (Xilinx)</li>
 * </ol>
 *
 * <p>Each step is run as an external process with progress reporting
 * through an {@link IProgressMonitor}. Output is collected and returned
 * via {@link SynthesisResult} for display in the console.
 */
public class FpgaSynthesizer {

	private static final ILog LOG = Platform.getLog(FpgaSynthesizer.class);

	private final IProject project;
	private final JopToolchain toolchain;
	private final BoardDefinition board;

	public FpgaSynthesizer(IProject project, JopToolchain toolchain, BoardDefinition board) {
		this.project = project;
		this.toolchain = toolchain;
		this.board = board;
	}

	/**
	 * Create a synthesizer from the project's current preferences.
	 *
	 * @throws CoreException if JOP_HOME is not configured or no board is selected
	 */
	public static FpgaSynthesizer forProject(IProject project) throws CoreException {
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
		return new FpgaSynthesizer(project, tc, board);
	}

	/**
	 * Run the full synthesis pipeline: SpinalHDL generation + FPGA synthesis.
	 *
	 * @param monitor progress monitor (cancelled check between stages)
	 * @return result containing console output and success/failure status
	 */
	public SynthesisResult synthesize(IProgressMonitor monitor) throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "FPGA Synthesis", 100);
		StringBuilder output = new StringBuilder();

		// Step 1: SpinalHDL Verilog generation
		sub.subTask("Generating Verilog (SpinalHDL / SBT)");
		SynthesisResult spinalResult = runSpinalHdl(sub.split(40));
		output.append(spinalResult.output());
		if (!spinalResult.success()) {
			return new SynthesisResult(false, output.toString());
		}
		if (sub.isCanceled()) {
			return new SynthesisResult(false, output.toString() + "\n--- Cancelled ---\n");
		}

		// Step 2: FPGA synthesis
		String synthTool = board.synthTool();
		if (synthTool.isEmpty() || "none".equals(synthTool)) {
			output.append("\n--- No synthesis tool for this board (simulation only) ---\n");
			return new SynthesisResult(true, output.toString());
		}

		if ("quartus".equals(synthTool)) {
			sub.subTask("Synthesizing (Quartus)");
			SynthesisResult quartusResult = runQuartus(sub.split(60));
			output.append(quartusResult.output());
			return new SynthesisResult(quartusResult.success(), output.toString());
		} else if ("vivado".equals(synthTool)) {
			sub.subTask("Synthesizing (Vivado)");
			SynthesisResult vivadoResult = runVivado(sub.split(60));
			output.append(vivadoResult.output());
			return new SynthesisResult(vivadoResult.success(), output.toString());
		} else {
			output.append("\n--- Unknown synthesis tool: ").append(synthTool).append(" ---\n");
			return new SynthesisResult(false, output.toString());
		}
	}

	/**
	 * Run only the SpinalHDL Verilog generation step.
	 */
	public SynthesisResult runSpinalHdl(IProgressMonitor monitor) throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "SpinalHDL generation", 100);

		String topModule = board.topModule();
		if (topModule.isEmpty()) {
			return new SynthesisResult(false,
					"--- No top module defined for board " + board.name() + " ---\n");
		}

		String sbtPath = JopToolchain.getSbtPath(project);
		String runMainClass = topModule + "Verilog";

		List<String> cmd = new ArrayList<>();
		cmd.add(sbtPath);
		cmd.add("runMain " + runMainClass);

		File workDir = toolchain.getSbtProjectDir();
		LOG.info("SpinalHDL generation: " + String.join(" ", cmd) + " in " + workDir);

		StringBuilder output = new StringBuilder();
		output.append("=== SpinalHDL Verilog Generation ===\n");
		output.append("Command: ").append(String.join(" ", cmd)).append('\n');
		output.append("Working dir: ").append(workDir).append('\n');
		output.append('\n');

		ProcessResult result = runProcess(cmd, workDir);
		output.append(result.stdout());
		if (!result.stderr().isEmpty()) {
			output.append(result.stderr());
		}
		output.append('\n');

		boolean success = result.exitCode() == 0;
		if (success) {
			output.append("--- SpinalHDL generation completed successfully ---\n");
		} else {
			output.append("--- SpinalHDL generation FAILED (exit code ")
					.append(result.exitCode()).append(") ---\n");
		}

		sub.worked(100);
		return new SynthesisResult(success, output.toString());
	}

	/**
	 * Run the Quartus synthesis flow: map → fit → asm → sta.
	 */
	public SynthesisResult runQuartus(IProgressMonitor monitor) throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "Quartus synthesis", 100);

		File quartusDir = JopToolchain.getQuartusDir(project);
		File fpgaDir = toolchain.getFpgaDir(board.fpgaDir());

		if (!fpgaDir.isDirectory()) {
			return new SynthesisResult(false,
					"--- FPGA project directory not found: " + fpgaDir + " ---\n");
		}

		// Determine the Quartus project name from the QUARTUS_PROJECT preference or fpgaDir
		String quartusProject = JopProjectPreferences.get(project,
				JopPreferences.QUARTUS_PROJECT, "");
		if (quartusProject.isEmpty()) {
			// Derive from fpgaDir name: e.g., "qmtech-ep4cgx150-bram" → look for .qpf
			quartusProject = findQuartusProject(fpgaDir);
			if (quartusProject == null) {
				return new SynthesisResult(false,
						"--- No .qpf file found in " + fpgaDir + ". Set Quartus Project in properties. ---\n");
			}
		}

		StringBuilder output = new StringBuilder();
		output.append("=== Quartus Synthesis ===\n");
		output.append("Project: ").append(quartusProject).append('\n');
		output.append("Directory: ").append(fpgaDir).append('\n');
		output.append('\n');

		// Quartus flow: map → fit → asm → sta
		String[][] steps = {
			{ "quartus_map", "--read_settings_files=on", quartusProject },
			{ "quartus_fit", "--read_settings_files=on", quartusProject },
			{ "quartus_asm", quartusProject },
			{ "quartus_sta", quartusProject },
		};

		String[] stepNames = { "Analysis & Synthesis", "Fitter", "Assembler", "Timing Analysis" };
		int workPerStep = 100 / steps.length;

		for (int i = 0; i < steps.length; i++) {
			if (sub.isCanceled()) {
				output.append("\n--- Cancelled ---\n");
				return new SynthesisResult(false, output.toString());
			}

			sub.subTask("Quartus: " + stepNames[i]);
			output.append("--- ").append(stepNames[i]).append(" ---\n");

			List<String> cmd = buildQuartusCommand(quartusDir, steps[i]);
			LOG.info("Quartus " + stepNames[i] + ": " + String.join(" ", cmd));

			ProcessResult result = runProcess(cmd, fpgaDir);
			output.append(result.stdout());
			if (!result.stderr().isEmpty()) {
				output.append(result.stderr());
			}
			output.append('\n');

			if (result.exitCode() != 0) {
				output.append("--- ").append(stepNames[i])
						.append(" FAILED (exit code ").append(result.exitCode()).append(") ---\n");
				return new SynthesisResult(false, output.toString());
			}

			sub.worked(workPerStep);
		}

		output.append("--- Quartus synthesis completed successfully ---\n");

		// Report output file location
		File outputFiles = new File(fpgaDir, "output_files");
		File sofFile = new File(outputFiles, quartusProject + ".sof");
		if (sofFile.exists()) {
			output.append("Bitstream: ").append(sofFile.getAbsolutePath()).append('\n');
		}

		return new SynthesisResult(true, output.toString());
	}

	/**
	 * Run the Vivado synthesis flow using TCL scripts.
	 */
	public SynthesisResult runVivado(IProgressMonitor monitor) throws CoreException {
		SubMonitor sub = SubMonitor.convert(monitor, "Vivado synthesis", 100);

		File vivadoDir = JopToolchain.getVivadoDir(project);
		File fpgaDir = toolchain.getFpgaDir(board.fpgaDir());

		if (!fpgaDir.isDirectory()) {
			return new SynthesisResult(false,
					"--- FPGA project directory not found: " + fpgaDir + " ---\n");
		}

		StringBuilder output = new StringBuilder();
		output.append("=== Vivado Synthesis ===\n");
		output.append("Directory: ").append(fpgaDir).append('\n');
		output.append('\n');

		File scriptsDir = new File(fpgaDir, "scripts");

		// Vivado flow: create IP cores, create project, build bitstream
		// Not all steps are always needed — check which scripts exist
		String[][] steps = {
			{ "create_clk_wiz.tcl", "Creating clock wizard IP" },
			{ "create_mig.tcl", "Creating MIG IP" },
			{ "create_project.tcl", "Creating Vivado project" },
			{ "build_bitstream.tcl", "Building bitstream" },
		};

		int stepCount = 0;
		for (String[] step : steps) {
			if (new File(scriptsDir, step[0]).exists()) {
				stepCount++;
			}
		}
		if (stepCount == 0) {
			return new SynthesisResult(false,
					"--- No Vivado TCL scripts found in " + scriptsDir + " ---\n");
		}

		int workPerStep = 100 / stepCount;

		for (String[] step : steps) {
			File tclScript = new File(scriptsDir, step[0]);
			if (!tclScript.exists()) continue;

			if (sub.isCanceled()) {
				output.append("\n--- Cancelled ---\n");
				return new SynthesisResult(false, output.toString());
			}

			sub.subTask("Vivado: " + step[1]);
			output.append("--- ").append(step[1]).append(" ---\n");

			// Check for run_vivado.sh wrapper first
			File runScript = new File(scriptsDir, "run_vivado.sh");
			List<String> cmd;
			if (runScript.exists()) {
				cmd = List.of("bash", runScript.getAbsolutePath(), step[0]);
			} else {
				cmd = buildVivadoCommand(vivadoDir, tclScript);
			}

			LOG.info("Vivado " + step[1] + ": " + String.join(" ", cmd));

			ProcessResult result = runProcess(cmd, fpgaDir);
			output.append(result.stdout());
			if (!result.stderr().isEmpty()) {
				output.append(result.stderr());
			}
			output.append('\n');

			if (result.exitCode() != 0) {
				output.append("--- ").append(step[1])
						.append(" FAILED (exit code ").append(result.exitCode()).append(") ---\n");
				return new SynthesisResult(false, output.toString());
			}

			sub.worked(workPerStep);
		}

		output.append("--- Vivado synthesis completed successfully ---\n");
		return new SynthesisResult(true, output.toString());
	}

	// ========================================================================
	// Helpers
	// ========================================================================

	/**
	 * Build a Quartus command line, resolving the tool from the configured
	 * Quartus install directory or falling back to PATH.
	 */
	private List<String> buildQuartusCommand(File quartusDir, String[] args) {
		List<String> cmd = new ArrayList<>();
		String tool = args[0];
		if (quartusDir != null && quartusDir.isDirectory()) {
			cmd.add(new File(quartusDir, "bin/" + tool).getAbsolutePath());
		} else {
			cmd.add(tool);
		}
		for (int i = 1; i < args.length; i++) {
			cmd.add(args[i]);
		}
		return cmd;
	}

	/**
	 * Build a Vivado batch-mode command line.
	 */
	private List<String> buildVivadoCommand(File vivadoDir, File tclScript) {
		List<String> cmd = new ArrayList<>();
		if (vivadoDir != null && vivadoDir.isDirectory()) {
			cmd.add(new File(vivadoDir, "bin/vivado").getAbsolutePath());
		} else {
			cmd.add("vivado");
		}
		cmd.add("-mode");
		cmd.add("batch");
		cmd.add("-source");
		cmd.add(tclScript.getAbsolutePath());
		return cmd;
	}

	/**
	 * Find the Quartus project name by looking for a .qpf file in the FPGA dir.
	 */
	private String findQuartusProject(File fpgaDir) {
		File[] qpfFiles = fpgaDir.listFiles((dir, name) -> name.endsWith(".qpf"));
		if (qpfFiles != null && qpfFiles.length > 0) {
			String name = qpfFiles[0].getName();
			return name.substring(0, name.length() - 4); // strip .qpf
		}
		return null;
	}

	/**
	 * Run an external process, collecting stdout and stderr.
	 */
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
					"Synthesis interrupted"));
		}
	}

	private record ProcessResult(int exitCode, String stdout, String stderr) {}
}
