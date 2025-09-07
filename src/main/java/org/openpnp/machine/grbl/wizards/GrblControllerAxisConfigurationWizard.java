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
 * Enhanced grblHAL support - controller axis configuration wizard with bidirectional settings sync.
 */

package org.openpnp.machine.grbl.wizards;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.machine.grbl.axis.GrblControllerAxis;
import org.openpnp.machine.grbl.driver.GrblDriver;
import org.pmw.tinylog.Logger;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

/**
 * Configuration wizard for GrblControllerAxis with grblHAL pin invert settings.
 * 
 * Features:
 * - Step Pin Invert checkbox ($2 bit per axis)
 * - Direction Pin Invert checkbox ($3 bit per axis) 
 * - Step Enable Invert checkbox ($4 bit per axis)
 * - Ganged Motor Invert checkbox ($8 bit for Y/Z axes when supported)
 * - Connection-aware GUI components grayed out when disconnected
 * - Dynamic tooltips with connection status and grblHAL setting references
 * - Bidirectional synchronization with grblHAL controller settings
 * - Automatic Apply button functionality through property bindings
 */
public class GrblControllerAxisConfigurationWizard extends AbstractConfigurationWizard {
    
    private final GrblControllerAxis axis;
    private boolean isConnected = false;
    
    // GUI Components
    private JPanel pinInvertPanel;
    private JCheckBox stepPinInvertCheckbox;
    private JCheckBox dirPinInvertCheckbox; 
    private JCheckBox stepEnableInvertCheckbox;
    private JCheckBox gangedMotorInvertCheckbox;
    
    // Connection tracking
    private PropertyChangeListener connectionListener;
    
    /**
     * Constructs a new GrblControllerAxis configuration wizard.
     * Sets up connection tracking and initializes component states.
     * 
     * @param axis the GrblControllerAxis to configure
     */
    public GrblControllerAxisConfigurationWizard(GrblControllerAxis axis) {
        this.axis = axis;
        
        createUi();
        createBindings();
        setupConnectionTracking();
        updateComponentStates();
        
        Logger.info("Created GrblControllerAxisConfigurationWizard for axis: {}", axis.getName());
    }
    
    /**
     * Creates the main user interface with pin invert settings panel.
     */
    private void createUi() {
        createPinInvertPanel();
        contentPanel.add(pinInvertPanel);
    }
    
    /**
     * Creates all GUI components with appropriate tooltips and configurations.
     */
    private void createComponents() {
        stepPinInvertCheckbox = new JCheckBox("Invert");
        dirPinInvertCheckbox = new JCheckBox("Invert");  
        stepEnableInvertCheckbox = new JCheckBox("Invert");
        
        // Conditionally create ganged motor checkbox for Y/Z axes
        if (axis.shouldShowGangedMotorSettings()) {
            gangedMotorInvertCheckbox = new JCheckBox("Invert");
        }
    }
    
    /**
     * Arranges components using FormLayout in a professional compact layout.
     */
    private void layoutComponents() {
        int currentRow = 2;
        
        // Step pin invert ($2)
        pinInvertPanel.add(new JLabel("Step Pin:"), "2, " + currentRow + ", right, default");
        pinInvertPanel.add(stepPinInvertCheckbox, "4, " + currentRow + ", fill, default");
        currentRow += 2;
        
        // Direction pin invert ($3)
        pinInvertPanel.add(new JLabel("Direction Pin:"), "2, " + currentRow + ", right, default");
        pinInvertPanel.add(dirPinInvertCheckbox, "4, " + currentRow + ", fill, default");
        currentRow += 2;
        
        // Step enable invert ($4)
        pinInvertPanel.add(new JLabel("Step Enable Pin:"), "2, " + currentRow + ", right, default");
        pinInvertPanel.add(stepEnableInvertCheckbox, "4, " + currentRow + ", fill, default");
        currentRow += 2;
        
        // Ganged motor invert ($8) - only for Y/Z axes when supported
        if (gangedMotorInvertCheckbox != null) {
            pinInvertPanel.add(new JLabel("Ganged Motor:"), "2, " + currentRow + ", right, default");
            pinInvertPanel.add(gangedMotorInvertCheckbox, "4, " + currentRow + ", fill, default");
        }
    }

    /**
     * Creates the main pin invert settings panel with titled border and form layout.
     */
    private void createPinInvertPanel() {
        pinInvertPanel = new JPanel();
        pinInvertPanel.setBorder(new TitledBorder("Grbl Settings - " + axis.getLetter() + " Axis"));
        
        // Calculate number of rows needed
        int numSettings = 3; // Step, Dir, Enable
        if (axis.shouldShowGangedMotorSettings()) {
            numSettings++; // Add Ganged Motor
        }
        
        // Create row specs dynamically
        RowSpec[] rowSpecs = new RowSpec[numSettings * 2 + 1]; // +1 for initial gap
        rowSpecs[0] = FormSpecs.RELATED_GAP_ROWSPEC;
        for (int i = 1; i < rowSpecs.length; i += 2) {
            rowSpecs[i] = FormSpecs.DEFAULT_ROWSPEC;
            if (i + 1 < rowSpecs.length) {
                rowSpecs[i + 1] = FormSpecs.RELATED_GAP_ROWSPEC;
            }
        }
        
        pinInvertPanel.setLayout(new FormLayout(
                new ColumnSpec[] {
                    FormSpecs.RELATED_GAP_COLSPEC,
                    ColumnSpec.decode("max(70dlu;default)"),
                    FormSpecs.RELATED_GAP_COLSPEC, 
                    FormSpecs.DEFAULT_COLSPEC,
                },
                rowSpecs));
        
        createComponents();
        layoutComponents();
    }

    /**
     * Setup connection tracking to monitor GrblDriver connection state.
     * Establishes property change listener for automatic GUI updates when connection changes.
     */
    private void setupConnectionTracking() {
        GrblDriver grblDriver = getGrblDriver();
        if (grblDriver == null) {
            Logger.warn("Cannot setup connection tracking - no GrblDriver available for axis {}", axis.getName());
            isConnected = false;
            return;
        }
        
        // Track initial connection state
        isConnected = grblDriver.isConnected();
        
        // If already connected, sync from controller
        if (isConnected) {
            Logger.info("Already connected - syncing pin invert settings for axis {}", axis.getName());
            axis.syncFromController();
        }
        
        // Listen for connection changes
        connectionListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("connected".equals(evt.getPropertyName())) {
                    boolean newConnectionState = (Boolean) evt.getNewValue();
                    if (newConnectionState != isConnected) {
                        isConnected = newConnectionState;
                        
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            if (isConnected) {
                                Logger.info("Connected to controller - syncing pin invert settings for axis {}", axis.getName());
                                axis.syncFromController();
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
     * Updates component enabled states based on connection status.
     * Called when connection state changes.
     */
    private void updateComponentStates() {
        boolean enabled = isConnected;
        
        stepPinInvertCheckbox.setEnabled(enabled);
        dirPinInvertCheckbox.setEnabled(enabled);
        stepEnableInvertCheckbox.setEnabled(enabled);
        
        if (gangedMotorInvertCheckbox != null) {
            gangedMotorInvertCheckbox.setEnabled(enabled);
        }

        updateTooltips();
    }
    
    /**
     * Updates tooltips to reflect current connection status and grblHAL setting references.
     * Provides contextual help and status information to users.
     */
    private void updateTooltips() {
        if (!isConnected) {
            // Disconnected tooltips - red color for error state
            stepPinInvertCheckbox.setToolTipText("<html><b>Step Pin Invert ($2)</b><br>" +
                    "<font color='red'>Connect to grblHAL controller to enable step pin invert</font><br>" +
                    "Inverts step pin logic for " + axis.getLetter() + "-axis<br>" +
                    "Use if stepper driver requires inverted step signal</html>");
            
            dirPinInvertCheckbox.setToolTipText("<html><b>Direction Pin Invert ($3)</b><br>" +
                    "<font color='red'>Connect to grblHAL controller to enable direction pin invert</font><br>" +
                    "Inverts direction pin logic for " + axis.getLetter() + "-axis<br>" +
                    "Use to reverse movement direction</html>");
            
            stepEnableInvertCheckbox.setToolTipText("<html><b>Step Enable Pin Invert ($4)</b><br>" +
                    "<font color='red'>Connect to grblHAL controller to enable step enable invert</font><br>" +
                    "Inverts step enable pin logic for " + axis.getLetter() + "-axis<br>" +
                    "Use if stepper driver requires inverted enable signal</html>");
            
            if (gangedMotorInvertCheckbox != null) {
                gangedMotorInvertCheckbox.setToolTipText("<html><b>Ganged Motor Invert ($8)</b><br>" +
                        "<font color='red'>Connect to grblHAL controller to enable ganged motor invert</font><br>" +
                        "Inverts secondary motor direction for " + axis.getLetter() + "-axis<br>" +
                        "Use if secondary stepper motor is wired backwards</html>");
            }
        } else {
            // Connected tooltips - green color for available features
            stepPinInvertCheckbox.setToolTipText("<html><b>Step Pin Invert ($2)</b><br>" +
                    "<font color='green'>Available - connected to grblHAL controller</font><br>" +
                    "Inverts step pin logic for " + axis.getLetter() + "-axis<br>" +
                    "Use if stepper driver requires inverted step signal</html>");
            
            dirPinInvertCheckbox.setToolTipText("<html><b>Direction Pin Invert ($3)</b><br>" +
                    "<font color='green'>Available - connected to grblHAL controller</font><br>" +
                    "Inverts direction pin logic for " + axis.getLetter() + "-axis<br>" +
                    "Use to reverse movement direction</html>");
            
            stepEnableInvertCheckbox.setToolTipText("<html><b>Step Enable Pin Invert ($4)</b><br>" +
                    "<font color='green'>Available - connected to grblHAL controller</font><br>" +
                    "Inverts step enable pin logic for " + axis.getLetter() + "-axis<br>" +
                    "Use if stepper driver requires inverted enable signal</html>");
            
            if (gangedMotorInvertCheckbox != null) {
                gangedMotorInvertCheckbox.setToolTipText("<html><b>Ganged Motor Invert ($8)</b><br>" +
                        "<font color='green'>Available - connected to grblHAL controller</font><br>" +
                        "Inverts secondary motor direction for " + axis.getLetter() + "-axis<br>" +
                        "Use if secondary stepper motor is wired backwards</html>");
            }
        }
    }
        
    /**
     * Gets the GrblDriver instance associated with this axis.
     * 
     * @return GrblDriver instance or null if not available or wrong driver type
     */
    private GrblDriver getGrblDriver() {
        if (axis.getDriver() instanceof GrblDriver) {
            return (GrblDriver) axis.getDriver();
        }
        return null;
    }
    
    /**
     * Creates property bindings between GUI components and axis properties.
     * Enables automatic Apply button functionality through OpenPnP binding system.
     */
    @Override
    public void createBindings() {
        addWrappedBinding(axis, "stepPinInvert", stepPinInvertCheckbox, "selected");
        addWrappedBinding(axis, "dirPinInvert", dirPinInvertCheckbox, "selected");
        addWrappedBinding(axis, "stepEnableInvert", stepEnableInvertCheckbox, "selected");

        if (gangedMotorInvertCheckbox != null) {
            addWrappedBinding(axis, "gangedMotorInvert", gangedMotorInvertCheckbox, "selected");
        }
        
        Logger.info("Created property bindings for GrblControllerAxis: {}", axis.getName());
    }
    
    /**
     * Cleans up resources when wizard is disposed.
     * Removes property change listeners to prevent memory leaks.
     */
    @Override
    public void dispose() {
        if (connectionListener != null && getGrblDriver() != null) {
            getGrblDriver().removePropertyChangeListener(connectionListener);
        }
        super.dispose();
    }
}