/*
 * Copyright (C) 2025 Raymond B. Hansen <raymondbh@gmail.com>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 * 
 * Enhanced grblHAL support - bidirectional settings sync and pick & place optimization.
 */

package org.openpnp.machine.grbl.wizards;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.openpnp.machine.grbl.actuator.GrblActuator;
import org.openpnp.machine.grbl.driver.GrblDriver;
import org.openpnp.machine.reference.wizards.ReferenceActuatorConfigurationWizard;
import org.openpnp.spi.base.AbstractMachine;
import org.pmw.tinylog.Logger;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

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
public class GrblActuatorConfigurationWizard extends ReferenceActuatorConfigurationWizard {
        
    // GUI Components
    private JPanel ioSettingsPanel;
    private JComboBox<GrblActuator.ActuatorType> actuatorTypeCombo;
    private JTextField ioIndexField;
    private JCheckBox inputPinInvertCheckbox;
    private JCheckBox outputPinInvertCheckbox;
    
    // Connection tracking
    private boolean isConnected = false;
    private PropertyChangeListener connectionListener;
    

    /**
     * Constructs a new GrblActuator configuration wizard.
     * Sets up connection tracking and initializes component states.
     * 
     * @param machine the machine instance containing the actuator
     * @param actuator the GrblActuator to configure
     */
    public GrblActuatorConfigurationWizard(AbstractMachine machine, GrblActuator actuator) {
        super(machine, actuator);  // Call parent constructor
        setupConnectionTracking();
        updateComponentStates();
    }
    
    /**
     * Creates all GUI components with appropriate tooltips and configurations.
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
        inputPinInvertCheckbox = new JCheckBox("Invert");
        inputPinInvertCheckbox.setToolTipText("<html><b>Input Pin Invert ($370)</b><br>" +
                "Inverts input pin logic for M143 read commands<br>" +
                "Only available when actuator type supports input operations<br>" +
                "Requires grblHAL controller connection</html>");
        
        // Output pin invert checkbox  
        outputPinInvertCheckbox = new JCheckBox("Invert");
        outputPinInvertCheckbox.setToolTipText("<html><b>Output Pin Invert ($372)</b><br>" +
                "Inverts output pin logic for M42 control commands<br>" +
                "Only available when actuator type supports output operations<br>" +
                "Requires grblHAL controller connection</html>");
    }
     
    /**
     * Arranges components using FormLayout in a professional grid structure.
     */
    private void layoutComponents() {
        ioSettingsPanel.add(new JLabel("Actuator Type:"), "2, 2, right, default");
        ioSettingsPanel.add(actuatorTypeCombo, "4, 2, fill, default");
        
        ioSettingsPanel.add(new JLabel("IO Index:"), "2, 4, right, default");
        ioSettingsPanel.add(ioIndexField, "4, 4, fill, default");
        
        ioSettingsPanel.add(new JLabel("Input Pin:"), "2, 6, right, default");
        ioSettingsPanel.add(inputPinInvertCheckbox, "4, 6, fill, default");
        
        ioSettingsPanel.add(new JLabel("Output Pin:"), "2, 8, right, default");
        ioSettingsPanel.add(outputPinInvertCheckbox, "4, 8, fill, default");
    }

    /**
     * Creates the main IO settings panel with titled border and form layout.
     */
    private void createIoSettingsPanel() {
        ioSettingsPanel = new JPanel();
        ioSettingsPanel.setBorder(new TitledBorder("IO Settings"));
        ioSettingsPanel.setLayout(new FormLayout(
                new ColumnSpec[] {
                    FormSpecs.RELATED_GAP_COLSPEC,
                    ColumnSpec.decode("max(70dlu;default)"),
                    FormSpecs.RELATED_GAP_COLSPEC, 
                    FormSpecs.DEFAULT_COLSPEC,
                },
                new RowSpec[] {
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                }));
        
        createComponents();
        layoutComponents();
    }

    /**
     * Setup connection tracking to monitor GrblDriver connection state.
     * Establishes property change listener for automatic GUI updates when connection changes.
     */
    private void setupConnectionTracking() {
        // Check if actuator is NOT GrblActuator
        if (!(actuator instanceof GrblActuator)) {
            Logger.warn("Actuator is not GrblActuator in wizard - cannot setup connection tracking");
            isConnected = false;
            return;
        }
        
        GrblActuator grblActuator = (GrblActuator) actuator;
        
        // Check if driver is GrblDriver
        if (!(grblActuator.getDriver() instanceof GrblDriver)) {
            Logger.warn("Actuator driver is not GrblDriver: {}", 
                       grblActuator.getDriver() != null ? grblActuator.getDriver().getClass().getSimpleName() : "null");
            isConnected = false;
            return;
        }
        
        GrblDriver grblDriver = (GrblDriver) grblActuator.getDriver();
        
        // Track initial connection state
        isConnected = grblDriver.isConnected();
        
        // If already connected, sync from controller and update GUI
        if (isConnected) {
            Logger.info("Already connected - syncing actuator {} from controller", grblActuator.getName());
            grblActuator.syncFromController();
        }
        
        // Listen for connection changes
        connectionListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("connected".equals(evt.getPropertyName())) {
                    boolean newConnectionState = (Boolean) evt.getNewValue();
                    if (newConnectionState != isConnected) {
                        isConnected = newConnectionState;
                        //Logger.debug("GrblActuatorConfigurationWizard connection state changed: {}", isConnected);
                        
                        // Use SwingUtilities to avoid EDT issues
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            if (isConnected) {
                                // Sync from controller when connection established
                                grblActuator.syncFromController();
                            }
                            updateComponentStates();
                        });
                    }
                }
            }
        };
        
        grblDriver.addPropertyChangeListener(connectionListener);
    }

    /**
     * Updates component enabled states based on connection status and actuator type.
     * Called when connection state changes or actuator type is modified.
     */
    private void updateComponentStates() {
        
        // Enable actuator type selector always (even when disconnected)
        actuatorTypeCombo.setEnabled(true);
        
        // Enable IO index field always (it's readonly anyway)
        ioIndexField.setEnabled(true);
        
        // Update tooltips based on connection status
        updateTooltips();
        
        // Enable checkboxes based on connection AND actuator type - ONLY CALL ONCE
        updateCheckboxStates();
    }
    
    /**
     * Updates checkbox enabled states based on actuator type capabilities and connection status.
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
        }
    }
    
    /**
     * Updates tooltips to reflect current connection status and actuator type capabilities.
     * Provides contextual help and status information to users.
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
                              "Inverts input pin logic for M143 read commands<br>";
            } else {
                inputTooltip += "<font color='orange'>Not available for " + selectedType + " actuator type</font><br>" +
                              "Only available for Input Only or Input/Output actuator types</html>";
            }
            inputPinInvertCheckbox.setToolTipText(inputTooltip);
            
            String outputTooltip = "<html><b>Output Pin Invert ($372)</b><br>";
            if (selectedType.hasOutput()) {
                outputTooltip += "<font color='green'>Available for this actuator type</font><br>" +
                               "Inverts output pin logic for M42 control commands<br>";
            } else {
                outputTooltip += "<font color='orange'>Not available for " + selectedType + " actuator type</font><br>" +
                               "Only available for Output Only or Input/Output actuator types</html>";
            }
            outputPinInvertCheckbox.setToolTipText(outputTooltip);
        }
    }
    
    /**
     * Creates property bindings between GUI components and actuator properties.
     * Enables automatic Apply button functionality through OpenPnP binding system.
     */
    @Override
    public void createBindings() {
        
        // Add our grblHAL-specific bindings
        addWrappedBinding(actuator, "actuatorType", actuatorTypeCombo, "selectedItem");
        addWrappedBinding(actuator, "index", ioIndexField, "text");
        addWrappedBinding(actuator, "inputPinInvert", inputPinInvertCheckbox, "selected");
        addWrappedBinding(actuator, "outputPinInvert", outputPinInvertCheckbox, "selected");
        
        // Add action listener AFTER bindings are set up
        actuatorTypeCombo.addActionListener(e -> {
            updateCheckboxStates();
        });
    }

    /**
     * Creates the custom UI replacing the parent wizard's standard UI.
     * Implements grblHAL-specific controls for IO pin invert configuration.
     * 
     * @param machine the machine instance (required by parent class)
     */
    @Override 
    protected void createUi(AbstractMachine machine) {
        
        // Add our grblHAL-specific IO settings panel
        createIoSettingsPanel();
        contentPanel.add(ioSettingsPanel);
    }
    
    /**
     * Cleans up resources when wizard is disposed.
     * Removes property change listeners to prevent memory leaks.
     */
    @Override
    public void dispose() {
        // Remove connection listener to prevent memory leaks
        if (connectionListener != null && actuator instanceof GrblActuator) {
            GrblActuator grblActuator = (GrblActuator) actuator;
            if (grblActuator.getDriver() instanceof GrblDriver) {
                GrblDriver grblDriver = (GrblDriver) grblActuator.getDriver();
                grblDriver.removePropertyChangeListener(connectionListener);
            }
        }
        super.dispose();
    }
}