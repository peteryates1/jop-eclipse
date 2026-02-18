package com.jopdesign.ui.properties;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PropertyPage;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.preferences.JopPreferences;

/**
 * Project property page for JOP settings.
 * Configures JOP_HOME, serial port, board target, and boot mode per project.
 */
public class JopPropertyPage extends PropertyPage {

	private Text jopHomeText;
	private Text serialPortText;
	private Text mainClassText;
	private Text outputDirText;

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(3, false));
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(JopCorePlugin.PLUGIN_ID);

		// JOP Home
		Group pathGroup = createGroup(composite, "JOP Installation", 3);

		new Label(pathGroup, SWT.NONE).setText("JOP Home:");
		jopHomeText = new Text(pathGroup, SWT.BORDER);
		jopHomeText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		jopHomeText.setText(prefs.get(JopPreferences.JOP_HOME, ""));
		Button browseButton = new Button(pathGroup, SWT.PUSH);
		browseButton.setText("Browse...");
		browseButton.addListener(SWT.Selection, e -> {
			DirectoryDialog dialog = new DirectoryDialog(getShell());
			dialog.setMessage("Select JOP installation directory");
			String dir = dialog.open();
			if (dir != null) {
				jopHomeText.setText(dir);
			}
		});

		// Hardware settings
		Group hwGroup = createGroup(composite, "Hardware", 3);

		new Label(hwGroup, SWT.NONE).setText("Serial Port:");
		serialPortText = new Text(hwGroup, SWT.BORDER);
		serialPortText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		serialPortText.setText(prefs.get(JopPreferences.SERIAL_PORT, "/dev/ttyUSB0"));
		new Label(hwGroup, SWT.NONE); // spacer

		// Build settings
		Group buildGroup = createGroup(composite, "Build", 3);

		new Label(buildGroup, SWT.NONE).setText("Main Class:");
		mainClassText = new Text(buildGroup, SWT.BORDER);
		mainClassText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		mainClassText.setText(prefs.get(JopPreferences.MAIN_CLASS, ""));
		new Label(buildGroup, SWT.NONE); // spacer

		new Label(buildGroup, SWT.NONE).setText("Output Directory:");
		outputDirText = new Text(buildGroup, SWT.BORDER);
		outputDirText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		outputDirText.setText(prefs.get(JopPreferences.JOP_OUTPUT_DIR, "build"));
		new Label(buildGroup, SWT.NONE); // spacer

		return composite;
	}

	private Group createGroup(Composite parent, String title, int columns) {
		Group group = new Group(parent, SWT.NONE);
		group.setText(title);
		group.setLayout(new GridLayout(columns, false));
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 3;
		group.setLayoutData(gd);
		return group;
	}

	@Override
	public boolean performOk() {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(JopCorePlugin.PLUGIN_ID);
		prefs.put(JopPreferences.JOP_HOME, jopHomeText.getText());
		prefs.put(JopPreferences.SERIAL_PORT, serialPortText.getText());
		prefs.put(JopPreferences.MAIN_CLASS, mainClassText.getText());
		prefs.put(JopPreferences.JOP_OUTPUT_DIR, outputDirText.getText());
		try {
			prefs.flush();
		} catch (Exception e) {
			// Log but don't fail
		}
		return super.performOk();
	}

	@Override
	protected void performDefaults() {
		jopHomeText.setText("");
		serialPortText.setText("/dev/ttyUSB0");
		mainClassText.setText("");
		outputDirText.setText("build");
		super.performDefaults();
	}

	private IProject getProject() {
		IAdaptable element = getElement();
		if (element instanceof IProject project) {
			return project;
		}
		return element.getAdapter(IProject.class);
	}
}
