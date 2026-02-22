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
		"JOP Bytecode Simulator",
		"JOP RTL Simulation",
		"JOP FPGA",
		"Dummy (testing)"
	};

	private static final String[] TARGET_IDS = {
		JopLaunchDelegate.TARGET_SIMULATOR,
		JopLaunchDelegate.TARGET_JOPSIM,
		JopLaunchDelegate.TARGET_RTLSIM,
		JopLaunchDelegate.TARGET_FPGA,
		JopLaunchDelegate.TARGET_DUMMY
	};

	private static final int IDX_SIMULATOR = 0;
	private static final int IDX_JOPSIM = 1;
	private static final int IDX_RTLSIM = 2;
	private static final int IDX_FPGA = 3;
	private static final int IDX_DUMMY = 4;

	private Combo targetCombo;
	private Text fileText;
	private Spinner spSpinner;
	private Spinner memSpinner;
	private Text jopFileText;
	private Text linkFileText;

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

		// .jop binary file (for JOP Bytecode Simulator)
		Label jopFileLabel = new Label(comp, SWT.NONE);
		jopFileLabel.setText("JOP binary file (.jop):");

		jopFileText = new Text(comp, SWT.BORDER);
		jopFileText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		jopFileText.addModifyListener(e -> updateLaunchConfigurationDialog());

		Button jopBrowseBtn = new Button(comp, SWT.PUSH);
		jopBrowseBtn.setText("Browse...");
		jopBrowseBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browseJopFile();
			}
		});

		// .link.txt symbol file (optional, for JOP Bytecode Simulator)
		Label linkFileLabel = new Label(comp, SWT.NONE);
		linkFileLabel.setText("Link file (.link.txt):");

		linkFileText = new Text(comp, SWT.BORDER);
		linkFileText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		linkFileText.addModifyListener(e -> updateLaunchConfigurationDialog());

		Button linkBrowseBtn = new Button(comp, SWT.PUSH);
		linkBrowseBtn.setText("Browse...");
		linkBrowseBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browseLinkFile();
			}
		});
	}

	private void updateControlEnablement() {
		int idx = targetCombo.getSelectionIndex();
		boolean isSimulator = (idx == IDX_SIMULATOR);
		boolean isJopSim = (idx == IDX_JOPSIM);

		// Microcode simulator fields
		fileText.setEnabled(isSimulator);
		spSpinner.setEnabled(isSimulator);
		memSpinner.setEnabled(isSimulator);

		// JopSim bytecode simulator fields
		jopFileText.setEnabled(isJopSim);
		linkFileText.setEnabled(isJopSim);
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

	private void browseJopFile() {
		FilteredResourcesSelectionDialog dialog = new FilteredResourcesSelectionDialog(
				getShell(), false, ResourcesPlugin.getWorkspace().getRoot(), IFile.FILE);
		dialog.setTitle("Select JOP Binary File");
		dialog.setInitialPattern("*.jop");
		if (dialog.open() == Window.OK) {
			Object result = dialog.getFirstResult();
			if (result instanceof IFile file) {
				jopFileText.setText(file.getFullPath().toString());
			}
		}
	}

	private void browseLinkFile() {
		FilteredResourcesSelectionDialog dialog = new FilteredResourcesSelectionDialog(
				getShell(), false, ResourcesPlugin.getWorkspace().getRoot(), IFile.FILE);
		dialog.setTitle("Select Link File");
		dialog.setInitialPattern("*.link.txt");
		if (dialog.open() == Window.OK) {
			Object result = dialog.getFirstResult();
			if (result instanceof IFile file) {
				linkFileText.setText(file.getFullPath().toString());
			}
		}
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(JopLaunchDelegate.ATTR_TARGET_TYPE, JopLaunchDelegate.TARGET_SIMULATOR);
		configuration.setAttribute(JopLaunchDelegate.ATTR_MICROCODE_FILE, "");
		configuration.setAttribute(JopLaunchDelegate.ATTR_INITIAL_SP, 64);
		configuration.setAttribute(JopLaunchDelegate.ATTR_MEM_SIZE, 1024);
		configuration.setAttribute(JopLaunchDelegate.ATTR_JOP_FILE, "");
		configuration.setAttribute(JopLaunchDelegate.ATTR_LINK_FILE, "");
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
			jopFileText.setText(configuration.getAttribute(
					JopLaunchDelegate.ATTR_JOP_FILE, ""));
			linkFileText.setText(configuration.getAttribute(
					JopLaunchDelegate.ATTR_LINK_FILE, ""));
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
		configuration.setAttribute(JopLaunchDelegate.ATTR_JOP_FILE, jopFileText.getText().trim());
		configuration.setAttribute(JopLaunchDelegate.ATTR_LINK_FILE, linkFileText.getText().trim());
	}

	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		setErrorMessage(null);

		int idx = targetCombo.getSelectionIndex();
		if (idx == IDX_RTLSIM || idx == IDX_FPGA) {
			setErrorMessage("Selected target type is not yet implemented");
			return false;
		}

		// Microcode simulator requires a microcode file
		if (idx == IDX_SIMULATOR) {
			String file = fileText.getText().trim();
			if (file.isEmpty()) {
				setErrorMessage("Microcode file must be specified");
				return false;
			}
		}

		// JopSim bytecode simulator requires a .jop file
		if (idx == IDX_JOPSIM) {
			String file = jopFileText.getText().trim();
			if (file.isEmpty()) {
				setErrorMessage("JOP binary file must be specified");
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
