package com.jopdesign.ui.launch;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog;

/**
 * Launch configuration tab for "JOP Application" configurations.
 * Provides target type selection, microcode file, and simulation parameters.
 */
public class JopLaunchTab extends AbstractLaunchConfigurationTab {

	private static final String[] TARGET_LABELS = {
		"JOP Simulator",
		"JOP RTL Simulation",
		"JOP FPGA",
		"Dummy (testing)"
	};

	private static final String[] TARGET_IDS = {
		JopLaunchDelegate.TARGET_SIMULATOR,
		JopLaunchDelegate.TARGET_RTLSIM,
		JopLaunchDelegate.TARGET_FPGA,
		JopLaunchDelegate.TARGET_DUMMY
	};

	private Combo targetCombo;
	private Text fileText;
	private Spinner spSpinner;
	private Spinner memSpinner;

	@Override
	public void createControl(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(3, false));
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		setControl(comp);

		// Target type
		Label targetLabel = new Label(comp, SWT.NONE);
		targetLabel.setText("Target type:");

		targetCombo = new Combo(comp, SWT.READ_ONLY);
		targetCombo.setItems(TARGET_LABELS);
		targetCombo.select(0);
		GridData comboData = new GridData(GridData.FILL_HORIZONTAL);
		comboData.horizontalSpan = 2;
		targetCombo.setLayoutData(comboData);
		targetCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateControlEnablement();
				updateLaunchConfigurationDialog();
			}
		});

		// Microcode file
		Label fileLabel = new Label(comp, SWT.NONE);
		fileLabel.setText("Microcode file:");

		fileText = new Text(comp, SWT.BORDER);
		fileText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		fileText.addModifyListener(e -> updateLaunchConfigurationDialog());

		Button browseBtn = new Button(comp, SWT.PUSH);
		browseBtn.setText("Browse...");
		browseBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browseFile();
			}
		});

		// Initial stack pointer
		Label spLabel = new Label(comp, SWT.NONE);
		spLabel.setText("Initial stack pointer:");

		spSpinner = new Spinner(comp, SWT.BORDER);
		spSpinner.setMinimum(0);
		spSpinner.setMaximum(1023);
		spSpinner.setSelection(64);
		spSpinner.addModifyListener(e -> updateLaunchConfigurationDialog());
		GridData spData = new GridData();
		spData.horizontalSpan = 2;
		spSpinner.setLayoutData(spData);

		// Memory size
		Label memLabel = new Label(comp, SWT.NONE);
		memLabel.setText("Memory size (words):");

		memSpinner = new Spinner(comp, SWT.BORDER);
		memSpinner.setMinimum(256);
		memSpinner.setMaximum(1048576);
		memSpinner.setSelection(1024);
		memSpinner.addModifyListener(e -> updateLaunchConfigurationDialog());
		GridData memData = new GridData();
		memData.horizontalSpan = 2;
		memSpinner.setLayoutData(memData);
	}

	private void updateControlEnablement() {
		int idx = targetCombo.getSelectionIndex();
		boolean isSimulator = (idx == 0);
		boolean isDummy = (idx == 3);
		// File, SP, and memory are relevant for simulator; not needed for dummy
		fileText.setEnabled(isSimulator);
		spSpinner.setEnabled(isSimulator);
		memSpinner.setEnabled(isSimulator);

		// Disable RTL Sim and FPGA items (index 1 and 2)
		// Note: Combo doesn't support per-item disabling, but we validate in isValid()
	}

	private void browseFile() {
		FilteredResourcesSelectionDialog dialog = new FilteredResourcesSelectionDialog(
				getShell(), false, ResourcesPlugin.getWorkspace().getRoot(), IFile.FILE);
		dialog.setTitle("Select Microcode File");
		dialog.setInitialPattern("*.asm");
		if (dialog.open() == Window.OK) {
			Object result = dialog.getFirstResult();
			if (result instanceof IFile file) {
				fileText.setText(file.getFullPath().toString());
			}
		}
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(JopLaunchDelegate.ATTR_TARGET_TYPE, JopLaunchDelegate.TARGET_SIMULATOR);
		configuration.setAttribute(JopLaunchDelegate.ATTR_MICROCODE_FILE, "");
		configuration.setAttribute(JopLaunchDelegate.ATTR_INITIAL_SP, 64);
		configuration.setAttribute(JopLaunchDelegate.ATTR_MEM_SIZE, 1024);
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			String targetType = configuration.getAttribute(
					JopLaunchDelegate.ATTR_TARGET_TYPE, JopLaunchDelegate.TARGET_SIMULATOR);
			for (int i = 0; i < TARGET_IDS.length; i++) {
				if (TARGET_IDS[i].equals(targetType)) {
					targetCombo.select(i);
					break;
				}
			}
			fileText.setText(configuration.getAttribute(
					JopLaunchDelegate.ATTR_MICROCODE_FILE, ""));
			spSpinner.setSelection(configuration.getAttribute(
					JopLaunchDelegate.ATTR_INITIAL_SP, 64));
			memSpinner.setSelection(configuration.getAttribute(
					JopLaunchDelegate.ATTR_MEM_SIZE, 1024));
			updateControlEnablement();
		} catch (Exception e) {
			// Use defaults
		}
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		int idx = targetCombo.getSelectionIndex();
		if (idx >= 0 && idx < TARGET_IDS.length) {
			configuration.setAttribute(JopLaunchDelegate.ATTR_TARGET_TYPE, TARGET_IDS[idx]);
		}
		configuration.setAttribute(JopLaunchDelegate.ATTR_MICROCODE_FILE, fileText.getText().trim());
		configuration.setAttribute(JopLaunchDelegate.ATTR_INITIAL_SP, spSpinner.getSelection());
		configuration.setAttribute(JopLaunchDelegate.ATTR_MEM_SIZE, memSpinner.getSelection());
	}

	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		setErrorMessage(null);

		int idx = targetCombo.getSelectionIndex();
		if (idx == 1 || idx == 2) {
			setErrorMessage("Selected target type is not yet implemented");
			return false;
		}

		// Simulator requires a microcode file
		if (idx == 0) {
			String file = fileText.getText().trim();
			if (file.isEmpty()) {
				setErrorMessage("Microcode file must be specified");
				return false;
			}
		}

		return true;
	}

	@Override
	public String getName() {
		return "JOP Application";
	}
}
