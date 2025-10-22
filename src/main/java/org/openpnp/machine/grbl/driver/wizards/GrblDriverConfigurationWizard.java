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
 * Enhanced grblHAL support - driver configuration wizard with bidirectional settings sync.
 */

package org.openpnp.machine.grbl.driver.wizards;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.machine.grbl.driver.GrblDriver;
import org.openpnp.machine.grbl.wizards.GrblSettingsSync;
import org.pmw.tinylog.Logger;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

/**
 * Configuration wizard for GrblDriver with grblHAL settings synchronization.
 * 
 * Features:
 * - Step timing settings ($0/$1) for pulse width and idle delay
 * - Homing configuration ($22-$27) with direction and multi-pass support  
 * - Limits settings ($5/$20/$21/$130-$132) for soft/hard limits and travel
 * - grblHAL homing passes ($44-$46) for advanced homing sequences
 * - Connection-aware GUI components grayed out when disconnected
 * - Bidirectional synchronization with grblHAL controller settings
 * - Automatic Apply button functionality through property bindings
 */
public class GrblDriverConfigurationWizard extends AbstractConfigurationWizard {
    
    private final GrblDriver driver;
    private GrblSettingsSync settingsSync;
    private boolean isConnected;
    
    // === STEP TIMING COMPONENTS ($0/$1) ===
    
    private JSpinner stepPulseSpinner;         // $0 - step pulse width in microseconds
    private JSpinner stepIdleDelaySpinner;     // $1 - step idle delay in milliseconds
    
    // === HOMING COMPONENTS ($22-$27, $44-$46) ===
    
    private JCheckBox homingEnableCheckbox;    // $22 - enable homing cycle
    private JCheckBox homingInvertXCheckbox;   // $23 bit 0 - X-axis homing direction
    private JCheckBox homingInvertYCheckbox;   // $23 bit 1 - Y-axis homing direction  
    private JCheckBox homingInvertZCheckbox;   // $23 bit 2 - Z-axis homing direction
    
    // grblHAL multi-pass homing checkboxes (3x3 grid)
    private JCheckBox homingPass1XCheckbox;    // $44 bit 0 - X in pass 1
    private JCheckBox homingPass1YCheckbox;    // $44 bit 1 - Y in pass 1
    private JCheckBox homingPass1ZCheckbox;    // $44 bit 2 - Z in pass 1
    private JCheckBox homingPass2XCheckbox;    // $45 bit 0 - X in pass 2
    private JCheckBox homingPass2YCheckbox;    // $45 bit 1 - Y in pass 2
    private JCheckBox homingPass2ZCheckbox;    // $45 bit 2 - Z in pass 2
    private JCheckBox homingPass3XCheckbox;    // $46 bit 0 - X in pass 3
    private JCheckBox homingPass3YCheckbox;    // $46 bit 1 - Y in pass 3
    private JCheckBox homingPass3ZCheckbox;    // $46 bit 2 - Z in pass 3
    
    // Homing speed and timing settings
    private JSpinner homingFeedRateSpinner;    // $24 - homing feed rate mm/min
    private JSpinner homingSeekRateSpinner;    // $25 - homing seek rate mm/min
    private JSpinner homingDebounceSpinner;    // $26 - homing debounce ms
    private JSpinner homingPulloffSpinner;     // $27 - homing pull-off distance mm
    
    // === LIMITS COMPONENTS ($5/$20/$21/$130-$132) ===
    
    private JCheckBox softLimitsCheckbox;      // $20 - enable soft limits
    private JCheckBox hardLimitsCheckbox;      // $21 - enable hard limits
    
    // Limit pin invert checkboxes ($5 bitmask)
    private JCheckBox limitInvertXCheckbox;    // $5 bit 0 - X limit pin invert
    private JCheckBox limitInvertYCheckbox;    // $5 bit 1 - Y limit pin invert
    private JCheckBox limitInvertZCheckbox;    // $5 bit 2 - Z limit pin invert
    private JCheckBox limitInvertACheckbox;    // $5 bit 3 - A limit pin invert
    private JCheckBox limitInvertBCheckbox;    // $5 bit 4 - B limit pin invert
    private JCheckBox limitInvertCCheckbox;    // $5 bit 5 - C limit pin invert
    
    // Max travel distance settings
    private JSpinner xMaxTravelSpinner;        // $130 - X max travel distance mm
    private JSpinner yMaxTravelSpinner;        // $131 - Y max travel distance mm
    private JSpinner zMaxTravelSpinner;        // $132 - Z max travel distance mm
    
    /**
     * Constructs a new GrblDriver configuration wizard.
     * Sets up connection tracking and initializes component states.
     * 
     * @param driver the GrblDriver to configure
     */
    public GrblDriverConfigurationWizard(GrblDriver driver) {
        this.driver = driver;
        this.settingsSync = driver.getSettingsSync();
        this.isConnected = driver.isConnected();
        
        addGrblSettingsToPanel();
        setupConnectionTracking();
        updateComponentStates();
        
        Logger.info("Created GrblDriverConfigurationWizard for driver: {}", driver.getClass().getSimpleName());
    }

    /**
     * Creates property bindings between GUI components and driver properties.
     * Enables automatic Apply button functionality through OpenPnP binding system.
     */
    @Override
    public void createBindings() {
        Logger.info("Creating GrblDriverConfigurationWizard bindings...");
        
        if (homingEnableCheckbox != null) {
            try {
                // Step timing bindings
                addWrappedBinding(driver, "stepPulse", stepPulseSpinner, "value");
                addWrappedBinding(driver, "stepIdleDelay", stepIdleDelaySpinner, "value");

                // Homing bindings
                addWrappedBinding(driver, "homingEnabled", homingEnableCheckbox, "selected");
                addWrappedBinding(driver, "homingInvertX", homingInvertXCheckbox, "selected");
                addWrappedBinding(driver, "homingInvertY", homingInvertYCheckbox, "selected");
                addWrappedBinding(driver, "homingInvertZ", homingInvertZCheckbox, "selected");
                
                // grblHAL multi-pass homing bindings
                addWrappedBinding(driver, "homingPass1X", homingPass1XCheckbox, "selected");
                addWrappedBinding(driver, "homingPass1Y", homingPass1YCheckbox, "selected");
                addWrappedBinding(driver, "homingPass1Z", homingPass1ZCheckbox, "selected");
                addWrappedBinding(driver, "homingPass2X", homingPass2XCheckbox, "selected");
                addWrappedBinding(driver, "homingPass2Y", homingPass2YCheckbox, "selected");
                addWrappedBinding(driver, "homingPass2Z", homingPass2ZCheckbox, "selected");
                addWrappedBinding(driver, "homingPass3X", homingPass3XCheckbox, "selected");
                addWrappedBinding(driver, "homingPass3Y", homingPass3YCheckbox, "selected");
                addWrappedBinding(driver, "homingPass3Z", homingPass3ZCheckbox, "selected");
                
                // Homing speed and timing bindings
                addWrappedBinding(driver, "homingFeedRate", homingFeedRateSpinner, "value");
                addWrappedBinding(driver, "homingSeekRate", homingSeekRateSpinner, "value");
                addWrappedBinding(driver, "homingDebounce", homingDebounceSpinner, "value");
                addWrappedBinding(driver, "homingPulloff", homingPulloffSpinner, "value");
                
                // Limits bindings
                addWrappedBinding(driver, "softLimitsEnabled", softLimitsCheckbox, "selected");
                addWrappedBinding(driver, "hardLimitsEnabled", hardLimitsCheckbox, "selected");
                addWrappedBinding(driver, "limitInvertX", limitInvertXCheckbox, "selected");
                addWrappedBinding(driver, "limitInvertY", limitInvertYCheckbox, "selected");
                addWrappedBinding(driver, "limitInvertZ", limitInvertZCheckbox, "selected");
                addWrappedBinding(driver, "limitInvertA", limitInvertACheckbox, "selected");
                addWrappedBinding(driver, "limitInvertB", limitInvertBCheckbox, "selected");
                addWrappedBinding(driver, "limitInvertC", limitInvertCCheckbox, "selected");
                
                // Max travel bindings
                addWrappedBinding(driver, "XMaxTravel", xMaxTravelSpinner, "value");
                addWrappedBinding(driver, "YMaxTravel", yMaxTravelSpinner, "value");
                addWrappedBinding(driver, "ZMaxTravel", zMaxTravelSpinner, "value");
                
                Logger.info("All GrblDriver bindings created successfully");
                
            } catch (Exception e) {
                Logger.warn("Failed to create GrblDriver bindings: {}", e.getMessage());
            }
        }
    }

    /**
     * Loads settings from the model and syncs from controller if connected.
     * Called when wizard is opened or refreshed.
     */
    @Override
    public void loadFromModel() {
        Logger.debug("GrblDriverConfigurationWizard.loadFromModel() called");
        
        if (settingsSync != null && isConnected) {
            syncControllerToDriverProperties();
        }
        
        super.loadFromModel();
        
        Logger.debug("loadFromModel() completed");
    }

    /**
     * Saves settings to the model and syncs to controller if connected.
     * Called when Apply button is pressed.
     */
    @Override
    public void saveToModel() {
        Logger.debug("GrblDriverConfigurationWizard.saveToModel() called");
        
        super.saveToModel();
        
        if (settingsSync != null && isConnected) {
            syncDriverPropertiesToController();
        }
        
        Logger.debug("saveToModel() completed");
    }

    /**
     * Adds grblHAL-specific settings panels to the main configuration panel.
     * Creates step timing, homing, and limits configuration sections.
     */
    private void addGrblSettingsToPanel() {
        initializeGrblComponents();
        
        JPanel grblMainPanel = new JPanel(new BorderLayout());
        
        // Create vertical panel for all settings
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        
        // Add Step Timing panel ($0/$1)
        JPanel stepTimingPanel = createStepTimingPanel();
        gbc.gridx = 0;
        gbc.gridy = 0;
        settingsPanel.add(stepTimingPanel, gbc);

        // Add Homing settings panel ($22-$27, $44-$46)
        JPanel homingPanel = createGrblHomingPanel();
        gbc.gridy = 1;
        settingsPanel.add(homingPanel, gbc);
        
        // Add Limits settings panel ($5/$20/$21/$130-$132)
        JPanel limitPanel = createGrblLimitPanel();
        gbc.gridy = 2;
        settingsPanel.add(limitPanel, gbc);
        
        // Add fill space at bottom to push content to top
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        settingsPanel.add(new JPanel(), gbc);
        
        grblMainPanel.add(settingsPanel, BorderLayout.CENTER);
        contentPanel.add(grblMainPanel);
    }

    /**
     * Initializes all grblHAL UI components with appropriate models, sizes and tooltips.
     */
    private void initializeGrblComponents() {
        java.awt.Dimension spinnerSize = new java.awt.Dimension(100, 25);

        // Step timing settings ($0/$1)
        stepPulseSpinner = new JSpinner(new SpinnerNumberModel(5.0, 1.0, 1000.0, 0.1));
        stepPulseSpinner.setPreferredSize(spinnerSize);
        JSpinner.NumberEditor stepPulseEditor = new JSpinner.NumberEditor(stepPulseSpinner, "0.0");
        stepPulseSpinner.setEditor(stepPulseEditor);
        
        stepIdleDelaySpinner = new JSpinner(new SpinnerNumberModel(25, 0, 65535, 1));
        stepIdleDelaySpinner.setPreferredSize(spinnerSize);

        // Homing enable and direction ($22/$23)
        homingEnableCheckbox = new JCheckBox("Enable Homing Cycle");
        homingInvertXCheckbox = new JCheckBox("Invert X-axis");
        homingInvertYCheckbox = new JCheckBox("Invert Y-axis");
        homingInvertZCheckbox = new JCheckBox("Invert Z-axis");
        
        // grblHAL multi-pass homing ($44-$46)
        homingPass1XCheckbox = new JCheckBox("X");
        homingPass1YCheckbox = new JCheckBox("Y");
        homingPass1ZCheckbox = new JCheckBox("Z");
        homingPass2XCheckbox = new JCheckBox("X");
        homingPass2YCheckbox = new JCheckBox("Y");
        homingPass2ZCheckbox = new JCheckBox("Z");
        homingPass3XCheckbox = new JCheckBox("X");
        homingPass3YCheckbox = new JCheckBox("Y");
        homingPass3ZCheckbox = new JCheckBox("Z");
        
        // Homing speeds and timing ($24-$27)
        homingFeedRateSpinner = new JSpinner(new SpinnerNumberModel(25.0, 1.0, 10000.0, 1.0));
        homingFeedRateSpinner.setPreferredSize(spinnerSize);
        homingSeekRateSpinner = new JSpinner(new SpinnerNumberModel(500.0, 1.0, 50000.0, 10.0));
        homingSeekRateSpinner.setPreferredSize(spinnerSize);
        homingDebounceSpinner = new JSpinner(new SpinnerNumberModel(250, 1, 10000, 1));
        homingDebounceSpinner.setPreferredSize(spinnerSize);
        homingPulloffSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 100.0, 0.1));
        homingPulloffSpinner.setPreferredSize(spinnerSize);
        
        // Limits enable ($20/$21)
        softLimitsCheckbox = new JCheckBox("Enable Soft Limits");
        hardLimitsCheckbox = new JCheckBox("Enable Hard Limits");

        // Limit pin invert ($5 bitmask)
        limitInvertXCheckbox = new JCheckBox("X");
        limitInvertYCheckbox = new JCheckBox("Y");
        limitInvertZCheckbox = new JCheckBox("Z");
        limitInvertACheckbox = new JCheckBox("A");
        limitInvertBCheckbox = new JCheckBox("B");
        limitInvertCCheckbox = new JCheckBox("C");
        
        // Max travel distances ($130-$132)
        xMaxTravelSpinner = new JSpinner(new SpinnerNumberModel(200.0, 1.0, 10000.0, 1.0));
        xMaxTravelSpinner.setPreferredSize(spinnerSize);
        yMaxTravelSpinner = new JSpinner(new SpinnerNumberModel(200.0, 1.0, 10000.0, 1.0));
        yMaxTravelSpinner.setPreferredSize(spinnerSize);
        zMaxTravelSpinner = new JSpinner(new SpinnerNumberModel(200.0, 1.0, 10000.0, 1.0));
        zMaxTravelSpinner.setPreferredSize(spinnerSize);
        
        updateTooltips();
    }

    /**
     * Creates the step timing settings panel for $0 and $1 configuration.
     * Uses FormLayout for professional appearance and full-width layout.
     * 
     * @return JPanel with step pulse and idle delay controls
     */
    private JPanel createStepTimingPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Step Timing Settings"));
        
        panel.setLayout(new FormLayout(
                new ColumnSpec[] {
                    FormSpecs.RELATED_GAP_COLSPEC,
                    ColumnSpec.decode("max(120dlu;default)"),
                    FormSpecs.RELATED_GAP_COLSPEC,
                    FormSpecs.DEFAULT_COLSPEC,
                    FormSpecs.RELATED_GAP_COLSPEC,
                    ColumnSpec.decode("30dlu"),
                },
                new RowSpec[] {
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                }));
        
        panel.add(new JLabel("Step Pulse:"), "2, 2, right, default");
        panel.add(stepPulseSpinner, "4, 2, fill, default");
        panel.add(new JLabel("μs"), "6, 2");
        
        panel.add(new JLabel("Step Idle Delay:"), "2, 4, right, default");
        panel.add(stepIdleDelaySpinner, "4, 4, fill, default");
        panel.add(new JLabel("ms"), "6, 4");
        
        return panel;
    }

    /**
     * Creates the homing settings panel for $22-$27 and $44-$46 configuration.
     * Uses FormLayout for professional appearance and full-width layout.
     * 
     * @return JPanel with homing enable, direction, passes, and timing controls
     */
    private JPanel createGrblHomingPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Grbl Homing Settings"));
        
        panel.setLayout(new FormLayout(
                new ColumnSpec[] {
                    FormSpecs.RELATED_GAP_COLSPEC,
                    ColumnSpec.decode("max(120dlu;default)"),
                    FormSpecs.RELATED_GAP_COLSPEC,
                    FormSpecs.DEFAULT_COLSPEC,
                    FormSpecs.RELATED_GAP_COLSPEC,
                    FormSpecs.DEFAULT_COLSPEC,
                    FormSpecs.RELATED_GAP_COLSPEC,
                    FormSpecs.DEFAULT_COLSPEC,
                    FormSpecs.RELATED_GAP_COLSPEC,
                    ColumnSpec.decode("40dlu"),
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
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                }));
        
        int row = 2;
        
        panel.add(homingEnableCheckbox, "2, " + row + ", 7, 1");
        row += 2;
        
        panel.add(new JLabel("Homing Direction:"), "2, " + row);
        row += 2;
        panel.add(homingInvertXCheckbox, "4, " + row);
        panel.add(homingInvertYCheckbox, "6, " + row);
        panel.add(homingInvertZCheckbox, "8, " + row);
        row += 2;
        
        panel.add(new JLabel("Homing Passes (grblHAL):"), "2, " + row);
        row += 2;
        
        panel.add(new JLabel(" X"), "4, " + row);
        panel.add(new JLabel(" Y"), "6, " + row);
        panel.add(new JLabel(" Z"), "8, " + row);
        row += 2;
        
        panel.add(new JLabel("Pass 1:"), "2, " + row + ", right, default");
        panel.add(homingPass1XCheckbox, "4, " + row);
        panel.add(homingPass1YCheckbox, "6, " + row);
        panel.add(homingPass1ZCheckbox, "8, " + row);
        row += 2;
        
        panel.add(new JLabel("Pass 2:"), "2, " + row + ", right, default");
        panel.add(homingPass2XCheckbox, "4, " + row);
        panel.add(homingPass2YCheckbox, "6, " + row);
        panel.add(homingPass2ZCheckbox, "8, " + row);
        row += 2;
        
        panel.add(new JLabel("Pass 3:"), "2, " + row + ", right, default");
        panel.add(homingPass3XCheckbox, "4, " + row);
        panel.add(homingPass3YCheckbox, "6, " + row);
        panel.add(homingPass3ZCheckbox, "8, " + row);
        row += 2;
        
        panel.add(new JLabel("Feed Rate:"), "2, " + row + ", right, default");
        panel.add(homingFeedRateSpinner, "4, " + row + ", fill, default");
        panel.add(new JLabel("mm/min"), "6, " + row);
        row += 2;
        
        panel.add(new JLabel("Seek Rate:"), "2, " + row + ", right, default");
        panel.add(homingSeekRateSpinner, "4, " + row + ", fill, default");
        panel.add(new JLabel("mm/min"), "6, " + row);
        row += 2;
        
        panel.add(new JLabel("Debounce:"), "2, " + row + ", right, default");
        panel.add(homingDebounceSpinner, "4, " + row + ", fill, default");
        panel.add(new JLabel("ms"), "6, " + row);
        row += 2;
        
        panel.add(new JLabel("Pull-off:"), "2, " + row + ", right, default");
        panel.add(homingPulloffSpinner, "4, " + row + ", fill, default");
        panel.add(new JLabel("mm"), "6, " + row);
        
        return panel;
    }

    /**
     * Creates the limits settings panel for $5, $20-$21, and $130-$132 configuration.
     * Uses FormLayout for professional appearance and full-width layout.
     * 
     * @return JPanel with soft/hard limits, pin invert, and max travel controls
     */
    private JPanel createGrblLimitPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Grbl Limits Settings"));
        
        panel.setLayout(new FormLayout(
                new ColumnSpec[] {
                    FormSpecs.RELATED_GAP_COLSPEC,
                    ColumnSpec.decode("max(120dlu;default)"),
                    FormSpecs.RELATED_GAP_COLSPEC,
                    ColumnSpec.decode("400dlu:grow"),  // INCREASED WIDTH for checkbox panel
                    FormSpecs.RELATED_GAP_COLSPEC,
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
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                    FormSpecs.DEFAULT_ROWSPEC,
                    FormSpecs.RELATED_GAP_ROWSPEC,
                }));
        
        int row = 2;
        
        panel.add(softLimitsCheckbox, "2, " + row + ", 3, 1");
        row += 2;
        panel.add(hardLimitsCheckbox, "2, " + row + ", 3, 1");
        row += 2;
        
        panel.add(new JLabel("Limit Pin Invert:"), "2, " + row + ", right, default");
        
        // Use simple horizontal BoxLayout for checkboxes
        JPanel invertPanel = new JPanel();
        invertPanel.setLayout(new javax.swing.BoxLayout(invertPanel, javax.swing.BoxLayout.X_AXIS));
        
        invertPanel.add(limitInvertXCheckbox);
        invertPanel.add(javax.swing.Box.createHorizontalStrut(10));
        invertPanel.add(limitInvertYCheckbox);
        invertPanel.add(javax.swing.Box.createHorizontalStrut(10));
        invertPanel.add(limitInvertZCheckbox);
        invertPanel.add(javax.swing.Box.createHorizontalStrut(10));
        invertPanel.add(limitInvertACheckbox);
        invertPanel.add(javax.swing.Box.createHorizontalStrut(10));
        invertPanel.add(limitInvertBCheckbox);
        invertPanel.add(javax.swing.Box.createHorizontalStrut(10));
        invertPanel.add(limitInvertCCheckbox);
        invertPanel.add(javax.swing.Box.createHorizontalGlue()); // Fill remaining space
        
        panel.add(invertPanel, "4, " + row);
        row += 2;
        
        // Back to normal column spec for spinners
        panel.add(new JLabel("X Max Travel:"), "2, " + row + ", right, default");
        
        JPanel xTravelPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        xTravelPanel.add(xMaxTravelSpinner);
        xTravelPanel.add(new JLabel("mm"));
        panel.add(xTravelPanel, "4, " + row);
        row += 2;
        
        panel.add(new JLabel("Y Max Travel:"), "2, " + row + ", right, default");
        JPanel yTravelPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        yTravelPanel.add(yMaxTravelSpinner);
        yTravelPanel.add(new JLabel("mm"));
        panel.add(yTravelPanel, "4, " + row);
        row += 2;
        
        panel.add(new JLabel("Z Max Travel:"), "2, " + row + ", right, default");
        JPanel zTravelPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        zTravelPanel.add(zMaxTravelSpinner);
        zTravelPanel.add(new JLabel("mm"));
        panel.add(zTravelPanel, "4, " + row);
        
        return panel;
    }

    // === PRIVATE CONNECTION AND SYNC METHODS ===

    /**
     * Sets up connection tracking to monitor GrblDriver connection state.
     */
    private void setupConnectionTracking() {
        driver.addPropertyChangeListener("connected", evt -> {
            SwingUtilities.invokeLater(() -> {
                this.isConnected = (Boolean) evt.getNewValue();
                updateComponentStates();
                updateTooltips();
                
                if (isConnected && settingsSync != null) {
                    syncControllerToDriverProperties();
                    loadFromModel();
                }
            });
        });
    }

    /**
     * Updates component enabled states based on connection status.
     */
    private void updateComponentStates() {
        boolean enabled = isConnected && settingsSync != null;
        
        if (homingEnableCheckbox != null) {
            stepPulseSpinner.setEnabled(enabled);
            stepIdleDelaySpinner.setEnabled(enabled);
            homingEnableCheckbox.setEnabled(enabled);
            homingInvertXCheckbox.setEnabled(enabled);
            homingInvertYCheckbox.setEnabled(enabled);
            homingInvertZCheckbox.setEnabled(enabled);
            homingPass1XCheckbox.setEnabled(enabled);
            homingPass1YCheckbox.setEnabled(enabled);
            homingPass1ZCheckbox.setEnabled(enabled);
            homingPass2XCheckbox.setEnabled(enabled);
            homingPass2YCheckbox.setEnabled(enabled);
            homingPass2ZCheckbox.setEnabled(enabled);
            homingPass3XCheckbox.setEnabled(enabled);
            homingPass3YCheckbox.setEnabled(enabled);
            homingPass3ZCheckbox.setEnabled(enabled);
            homingFeedRateSpinner.setEnabled(enabled);
            homingSeekRateSpinner.setEnabled(enabled);
            homingDebounceSpinner.setEnabled(enabled);
            homingPulloffSpinner.setEnabled(enabled);
            softLimitsCheckbox.setEnabled(enabled);
            hardLimitsCheckbox.setEnabled(enabled);
            limitInvertXCheckbox.setEnabled(enabled);
            limitInvertYCheckbox.setEnabled(enabled);
            limitInvertZCheckbox.setEnabled(enabled);
            limitInvertACheckbox.setEnabled(enabled);
            limitInvertBCheckbox.setEnabled(enabled);
            limitInvertCCheckbox.setEnabled(enabled);
            xMaxTravelSpinner.setEnabled(enabled);
            yMaxTravelSpinner.setEnabled(enabled);
            zMaxTravelSpinner.setEnabled(enabled);
        }
    }

    /**
     * Updates tooltips to reflect current connection status.
     */
    private void updateTooltips() {
        if (!isConnected) {
            setDisconnectedTooltips();
        } else {
            setConnectedTooltips();
        }
    }

    private void setDisconnectedTooltips() {
        stepPulseSpinner.setToolTipText("<html><b>Step Pulse ($0)</b><br>" +
                "<font color='red'>Connect to grblHAL controller</font></html>");
        stepIdleDelaySpinner.setToolTipText("<html><b>Step Idle Delay ($1)</b><br>" +
                "<font color='red'>Connect to grblHAL controller</font></html>");
    }

    private void setConnectedTooltips() {
        stepPulseSpinner.setToolTipText("<html><b>Step Pulse ($0)</b><br>" +
                "<font color='green'>Available - connected</font></html>");
        stepIdleDelaySpinner.setToolTipText("<html><b>Step Idle Delay ($1)</b><br>" +
                "<font color='green'>Available - connected</font></html>");
    }

    /**
     * Synchronizes settings from grblHAL controller to driver properties.
     */
    private void syncControllerToDriverProperties() {
        try {
            Logger.info("Loading controller settings...");
            
            String stepPulseStr = settingsSync.getControllerSetting(0);
            String stepIdleDelayStr = settingsSync.getControllerSetting(1);
            String homingEnabledStr = settingsSync.getControllerSetting(22);
            String homingDirectionStr = settingsSync.getControllerSetting(23);
            String homingFeedRateStr = settingsSync.getControllerSetting(24);
            String homingSeekRateStr = settingsSync.getControllerSetting(25);
            String homingDebounceStr = settingsSync.getControllerSetting(26);
            String homingPulloffStr = settingsSync.getControllerSetting(27);
            String homingPass1Str = settingsSync.getControllerSetting(44);
            String homingPass2Str = settingsSync.getControllerSetting(45);
            String homingPass3Str = settingsSync.getControllerSetting(46);
            String limitPinInvertStr = settingsSync.getControllerSetting(5);
            String softLimitsStr = settingsSync.getControllerSetting(20);
            String hardLimitsStr = settingsSync.getControllerSetting(21);
            String xMaxTravelStr = settingsSync.getControllerSetting(130);
            String yMaxTravelStr = settingsSync.getControllerSetting(131);
            String zMaxTravelStr = settingsSync.getControllerSetting(132);
            
            updateDoubleProperty(stepPulseStr, "stepPulse", driver::setStepPulse);
            updateIntProperty(stepIdleDelayStr, "stepIdleDelay", driver::setStepIdleDelay);
            
            if (homingEnabledStr != null) {
                driver.setHomingEnabled("1".equals(homingEnabledStr));
            }
            
            updateIntProperty(homingDirectionStr, "homingDirection", driver::setHomingDirectionMask);
            updateDoubleProperty(homingFeedRateStr, "homingFeedRate", driver::setHomingFeedRate);
            updateDoubleProperty(homingSeekRateStr, "homingSeekRate", driver::setHomingSeekRate);
            updateIntProperty(homingDebounceStr, "homingDebounce", driver::setHomingDebounce);
            updateDoubleProperty(homingPulloffStr, "homingPulloff", driver::setHomingPulloff);
            updateIntProperty(homingPass1Str, "homingPass1", driver::setHomingPass1);
            updateIntProperty(homingPass2Str, "homingPass2", driver::setHomingPass2);
            updateIntProperty(homingPass3Str, "homingPass3", driver::setHomingPass3);
            
            if (softLimitsStr != null) {
                driver.setSoftLimitsEnabled("1".equals(softLimitsStr));
            }
            if (hardLimitsStr != null) {
                driver.setHardLimitsEnabled("1".equals(hardLimitsStr));
            }
            
            updateIntProperty(limitPinInvertStr, "limitPinInvert", driver::setLimitPinInvertMask);
            updateDoubleProperty(xMaxTravelStr, "xMaxTravel", driver::setXMaxTravel);
            updateDoubleProperty(yMaxTravelStr, "yMaxTravel", driver::setYMaxTravel);
            updateDoubleProperty(zMaxTravelStr, "zMaxTravel", driver::setZMaxTravel);
            
            Logger.info("Controller settings loaded");
            
        } catch (Exception e) {
            Logger.warn("Failed to sync controller settings: {}", e.getMessage());
        }
    }

    /**
     * Synchronizes settings from driver properties to grblHAL controller.
     */
    private void syncDriverPropertiesToController() {
        try {
            Logger.info("Syncing driver properties to controller...");
            
            int settingsWritten = 0;
            
            settingsWritten += writeSettingIfChanged(0, String.valueOf(driver.getStepPulse()), "stepPulse");
            settingsWritten += writeSettingIfChanged(1, String.valueOf(driver.getStepIdleDelay()), "stepIdleDelay");
            settingsWritten += writeSettingIfChanged(22, driver.isHomingEnabled() ? "1" : "0", "homingEnabled");
            settingsWritten += writeSettingIfChanged(23, String.valueOf(driver.getHomingDirectionMask()), "homingDirection");
            settingsWritten += writeSettingIfChanged(24, String.valueOf(driver.getHomingFeedRate()), "homingFeedRate");
            settingsWritten += writeSettingIfChanged(25, String.valueOf(driver.getHomingSeekRate()), "homingSeekRate");
            settingsWritten += writeSettingIfChanged(26, String.valueOf(driver.getHomingDebounce()), "homingDebounce");
            settingsWritten += writeSettingIfChanged(27, String.valueOf(driver.getHomingPulloff()), "homingPulloff");
            settingsWritten += writeSettingIfChanged(44, String.valueOf(driver.getHomingPass1()), "homingPass1");
            settingsWritten += writeSettingIfChanged(45, String.valueOf(driver.getHomingPass2()), "homingPass2");
            settingsWritten += writeSettingIfChanged(46, String.valueOf(driver.getHomingPass3()), "homingPass3");
            settingsWritten += writeSettingIfChanged(20, driver.isSoftLimitsEnabled() ? "1" : "0", "softLimits");
            settingsWritten += writeSettingIfChanged(21, driver.isHardLimitsEnabled() ? "1" : "0", "hardLimits");
            settingsWritten += writeSettingIfChanged(5, String.valueOf(driver.getLimitPinInvertMask()), "limitPinInvert");
            settingsWritten += writeSettingIfChanged(130, String.valueOf(driver.getXMaxTravel()), "xMaxTravel");
            settingsWritten += writeSettingIfChanged(131, String.valueOf(driver.getYMaxTravel()), "yMaxTravel");
            settingsWritten += writeSettingIfChanged(132, String.valueOf(driver.getZMaxTravel()), "zMaxTravel");
            
            if (settingsWritten > 0) {
                Logger.info("Wrote {} settings to controller", settingsWritten);
            } else {
                Logger.info("No settings changed");
            }
            
        } catch (Exception e) {
            Logger.warn("Failed to sync driver properties: {}", e.getMessage());
        }
    }

    private void updateIntProperty(String valueStr, String propertyName, java.util.function.IntConsumer setter) {
        if (valueStr != null) {
            try {
                int value = Integer.parseInt(valueStr);
                setter.accept(value);
            } catch (NumberFormatException e) {
                Logger.warn("Invalid {} value: {}", propertyName, valueStr);
            }
        }
    }

    private void updateDoubleProperty(String valueStr, String propertyName, java.util.function.DoubleConsumer setter) {
        if (valueStr != null) {
            try {
                double value = Double.parseDouble(valueStr);
                setter.accept(value);
            } catch (NumberFormatException e) {
                Logger.warn("Invalid {} value: {}", propertyName, valueStr);
            }
        }
    }

    private int writeSettingIfChanged(int settingId, String newValue, String settingName) {
        try {
            String currentValue = settingsSync.getControllerSetting(settingId);
            
            if (!valuesEqual(currentValue, newValue)) {
                settingsSync.writeSettingToController(settingId, newValue);
                Logger.debug("Updated {}: {} -> {}", settingName, currentValue, newValue);
                return 1;
            }
            
            return 0;
            
        } catch (Exception e) {
            Logger.warn("Failed to write setting ${}: {}", settingId, e.getMessage());
            return 0;
        }
    }
    
    private boolean valuesEqual(String value1, String value2) {
        if (value1 == null || value2 == null) {
            return value1 == value2;
        }
        
        try {
            double num1 = Double.parseDouble(value1);
            double num2 = Double.parseDouble(value2);
            return Math.abs(num1 - num2) < 0.001;
        } catch (NumberFormatException e) {
            return value1.equals(value2);
        }
    }
}