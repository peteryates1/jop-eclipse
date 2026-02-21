package com.jopdesign.ui.launch;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog;

/**
 * Launch configuration tab for selecting a microcode file and simulation parameters.
 */
public class MicrocodeLaunchTab extends AbstractLaunchConfigurationTab {

	private Text fileText;
	private Spinner spSpinner;
	private Spinner memSpinner;

	@Override
	public void createControl(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(3, false));
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		setControl(comp);

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
		configuration.setAttribute(MicrocodeLaunchDelegate.ATTR_MICROCODE_FILE, "");
		configuration.setAttribute(MicrocodeLaunchDelegate.ATTR_INITIAL_SP, 64);
		configuration.setAttribute(MicrocodeLaunchDelegate.ATTR_MEM_SIZE, 1024);
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			fileText.setText(configuration.getAttribute(
					MicrocodeLaunchDelegate.ATTR_MICROCODE_FILE, ""));
			spSpinner.setSelection(configuration.getAttribute(
					MicrocodeLaunchDelegate.ATTR_INITIAL_SP, 64));
			memSpinner.setSelection(configuration.getAttribute(
					MicrocodeLaunchDelegate.ATTR_MEM_SIZE, 1024));
		} catch (Exception e) {
			// Use defaults
		}
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(MicrocodeLaunchDelegate.ATTR_MICROCODE_FILE, fileText.getText().trim());
		configuration.setAttribute(MicrocodeLaunchDelegate.ATTR_INITIAL_SP, spSpinner.getSelection());
		configuration.setAttribute(MicrocodeLaunchDelegate.ATTR_MEM_SIZE, memSpinner.getSelection());
	}

	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		setErrorMessage(null);
		String file = fileText.getText().trim();
		if (file.isEmpty()) {
			setErrorMessage("Microcode file must be specified");
			return false;
		}
		return true;
	}

	@Override
	public String getName() {
		return "Microcode";
	}
}
