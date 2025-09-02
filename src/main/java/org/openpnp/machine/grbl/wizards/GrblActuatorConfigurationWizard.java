package org.openpnp.machine.grbl.wizards;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.machine.grbl.actuator.GrblActuator;
import org.openpnp.machine.grbl.driver.GrblDriver;
import org.pmw.tinylog.Logger;

/**
 * Configuration wizard for GrblActuator with grblHAL IO pin invert settings.
 * 
 * Features:
 * - ActuatorType selection (Output Only, Input Only, Input/Output)
 * - IO Index display (from ReferenceActuator.getIndex())
 * - Input Pin Invert checkbox ($370 bit - conditional on actuator type)
 * - Output Pin Invert checkbox ($372 bit - conditional on actuator type)
 * - Connection-aware GUI (grayed out when disconnected)
 * - Dynamic tooltips based on connection status
 */
public class GrblActuatorConfigurationWizard extends AbstractConfigurationWizard {
    
    private final GrblActuator actuator;
    
    // GUI Components
    private JComboBox<GrblActuator.ActuatorType> actuatorTypeCombo;
    private JTextField ioIndexField;
    private JCheckBox inputPinInvertCheckbox;
    private JCheckBox outputPinInvertCheckbox;
    
    // Connection tracking
    private boolean isConnected = false;
    private PropertyChangeListener connectionListener;
    
    public GrblActuatorConfigurationWizard(GrblActuator actuator) {
        this.actuator = actuator;
        
        createComponents();
        layoutComponents();
        createBindings();
        setupConnectionTracking();
        
        // Initial state update
        updateComponentStates();
    }
    
    /**
     * Create all UI components
     */
    private void createComponents() {
        // Actuator type selector
        actuatorTypeCombo = new JComboBox<>(GrblActuator.ActuatorType.values());
        actuatorTypeCombo.setToolTipText("<html>Select actuator type:<br>" +
                "• <b>Output Only</b>: M42 control commands only (vacuum pumps, LEDs)<br>" +
                "• <b>Input Only</b>: M143 read commands only (sensors)<br>" + 
                "• <b>Input/Output</b>: Both M42 control and M143 read commands</html>");
        
        // IO index field (readonly - from parent ReferenceActuator)
        ioIndexField = new JTextField(10);
        ioIndexField.setEditable(false);
        ioIndexField.setToolTipText("<html>IO pin number for M42/M143 commands<br>" +
                "This value comes from the Index property in the General tab<br>" +
                "Valid range: 0-7 for grblHAL IO pins</html>");
        
        // Input pin invert checkbox
        inputPinInvertCheckbox = new JCheckBox("Input Pin Invert ($370)");
        inputPinInvertCheckbox.setToolTipText("<html><b>Input Pin Invert ($370)</b><br>" +
                "Inverts input pin logic for M143 read commands<br>" +
                "Only available when actuator type supports input operations<br>" +
                "Requires grblHAL controller connection</html>");
        
        // Output pin invert checkbox  
        outputPinInvertCheckbox = new JCheckBox("Output Pin Invert ($372)");
        outputPinInvertCheckbox.setToolTipText("<html><b>Output Pin Invert ($372)</b><br>" +
                "Inverts output pin logic for M42 control commands<br>" +
                "Only available when actuator type supports output operations<br>" +
                "Requires grblHAL controller connection</html>");
        
        // Add change listeners
        actuatorTypeCombo.addActionListener(e -> updateCheckboxStates());
    }
    
    /**
     * Layout all components in the panel
     */
    private void layoutComponents() {
        setLayout(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        
        // Add IO settings panel
        JPanel ioSettingsPanel = createIoSettingsPanel();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        add(ioSettingsPanel, gbc);
        
        // Add spacer at bottom - samme som axis wizard
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(new JPanel(), gbc);
    }
    
    /**
     * Create the main IO settings panel
     */
    private JPanel createIoSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("IO Settings"));  // Samme pattern som axis
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        
        // Row 0: Actuator type selection
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Actuator Type:"), gbc);
        gbc.gridx = 1;
        panel.add(actuatorTypeCombo, gbc);
        gbc.gridx = 2;
        panel.add(new JLabel("<html><small>Select input/output operation mode</small></html>"), gbc);
        
        // Row 1: IO Index field
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("IO Index:"), gbc);
        gbc.gridx = 1;
        panel.add(ioIndexField, gbc);
        gbc.gridx = 2;
        panel.add(new JLabel("<html><small>IO pin number for M42/M143 commands</small></html>"), gbc);
        
        // Row 2: Input pin invert checkbox
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Input Pin ($370):"), gbc);
        gbc.gridx = 1;
        panel.add(inputPinInvertCheckbox, gbc);
        gbc.gridx = 2;
        panel.add(new JLabel("<html><small>Invert M143 read signal polarity</small></html>"), gbc);
        
        // Row 3: Output pin invert checkbox  
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Output Pin ($372):"), gbc);
        gbc.gridx = 1;
        panel.add(outputPinInvertCheckbox, gbc);
        gbc.gridx = 2;
        panel.add(new JLabel("<html><small>Invert M42 control signal polarity</small></html>"), gbc);
        
        // Connection status info - samme som axis wizard
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        JLabel statusLabel = new JLabel("<html><i>Connect to grblHAL controller to enable IO pin invert settings</i></html>");
        panel.add(statusLabel, gbc);
        
        return panel;
    }
    
    /**
     * Setup connection tracking to monitor GrblDriver connection state
     */
    private void setupConnectionTracking() {
        connectionListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("connected".equals(evt.getPropertyName())) {
                    isConnected = (Boolean) evt.getNewValue();
                    Logger.debug("GrblActuatorConfigurationWizard connection state changed: {}", isConnected);
                    updateComponentStates();
                }
            }
        };
        
        // Add listener to current driver if it's GrblDriver
        try {
            if (actuator.getDriver() instanceof GrblDriver) {
                GrblDriver driver = (GrblDriver) actuator.getDriver();
                driver.addPropertyChangeListener("connected", connectionListener);
                isConnected = driver.isConnected();
                Logger.debug("Added connection listener to GrblDriver for actuator wizard");
            }
        } catch (Exception e) {
            Logger.debug("Failed to setup connection tracking for actuator wizard: {}", e.getMessage());
            isConnected = false;
        }
    }
    
    /**
     * Update component enabled states based on connection and actuator type
     */
    private void updateComponentStates() {
        GrblActuator.ActuatorType selectedType = (GrblActuator.ActuatorType) actuatorTypeCombo.getSelectedItem();
        
        // Enable actuator type selector always (even when disconnected)
        actuatorTypeCombo.setEnabled(true);
        
        // Enable IO index field always (it's readonly anyway)
        ioIndexField.setEnabled(true);
        
        // Enable checkboxes based on connection AND actuator type
        updateCheckboxStates();
        
        // Update tooltips based on connection status
        updateTooltips();
        
        Logger.debug("Updated component states - connected: {}, type: {}", isConnected, selectedType);
    }
    
    /**
     * Update checkbox enabled states based on actuator type and connection
     */
    private void updateCheckboxStates() {
        GrblActuator.ActuatorType selectedType = (GrblActuator.ActuatorType) actuatorTypeCombo.getSelectedItem();
        
        if (selectedType != null) {
            // Input checkbox: enabled if connected AND actuator supports input
            boolean inputEnabled = isConnected && selectedType.hasInput();
            inputPinInvertCheckbox.setEnabled(inputEnabled);
            
            // Output checkbox: enabled if connected AND actuator supports output  
            boolean outputEnabled = isConnected && selectedType.hasOutput();
            outputPinInvertCheckbox.setEnabled(outputEnabled);
            
            Logger.debug("Updated checkbox states - input enabled: {}, output enabled: {}", inputEnabled, outputEnabled);
        }
    }
    
    /**
     * Update tooltips based on connection status and actuator type
     */
    private void updateTooltips() {
        GrblActuator.ActuatorType selectedType = (GrblActuator.ActuatorType) actuatorTypeCombo.getSelectedItem();
        
        if (!isConnected) {
            // Disconnected tooltips
            inputPinInvertCheckbox.setToolTipText("<html><b>Input Pin Invert ($370)</b><br>" +
                    "<font color='red'>Connect to grblHAL controller to enable input pin invert</font><br>" +
                    "Inverts input pin logic for M143 read commands</html>");
            
            outputPinInvertCheckbox.setToolTipText("<html><b>Output Pin Invert ($372)</b><br>" +
                    "<font color='red'>Connect to grblHAL controller to enable output pin invert</font><br>" +
                    "Inverts output pin logic for M42 control commands</html>");
                    
        } else if (selectedType != null) {
            // Connected tooltips - show based on actuator type support
            String inputTooltip = "<html><b>Input Pin Invert ($370)</b><br>";
            if (selectedType.hasInput()) {
                inputTooltip += "<font color='green'>Available for this actuator type</font><br>" +
                              "Inverts input pin logic for M143 read commands<br>" +
                              "Affects IO index " + actuator.getIndex() + " in $370 bitmask</html>";
            } else {
                inputTooltip += "<font color='orange'>Not available for " + selectedType + " actuator type</font><br>" +
                              "Only available for Input Only or Input/Output actuator types</html>";
            }
            inputPinInvertCheckbox.setToolTipText(inputTooltip);
            
            String outputTooltip = "<html><b>Output Pin Invert ($372)</b><br>";
            if (selectedType.hasOutput()) {
                outputTooltip += "<font color='green'>Available for this actuator type</font><br>" +
                               "Inverts output pin logic for M42 control commands<br>" +
                               "Affects IO index " + actuator.getIndex() + " in $372 bitmask</html>";
            } else {
                outputTooltip += "<font color='orange'>Not available for " + selectedType + " actuator type</font><br>" +
                               "Only available for Output Only or Input/Output actuator types</html>";
            }
            outputPinInvertCheckbox.setToolTipText(outputTooltip);
        }
    }
    
    /**
     * Cleanup method to remove listeners
     */
    @Override
    public void createBindings() {
        
        // Actuator type binding
        addWrappedBinding(actuator, "actuatorType", actuatorTypeCombo, "selectedItem");
        
        // IO index binding (readonly)
        addWrappedBinding(actuator, "index", ioIndexField, "text");
        
        // Input pin invert binding
        addWrappedBinding(actuator, "inputPinInvert", inputPinInvertCheckbox, "selected");
        
        // Output pin invert binding
        addWrappedBinding(actuator, "outputPinInvert", outputPinInvertCheckbox, "selected");
    }
}