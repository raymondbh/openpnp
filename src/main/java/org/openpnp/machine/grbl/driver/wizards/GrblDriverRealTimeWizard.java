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
 * Real-time status monitoring wizard for grblHAL controller debugging and setup.
 */

package org.openpnp.machine.grbl.driver.wizards;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.machine.grbl.driver.GrblDriver;
import org.pmw.tinylog.Logger;

/**
 * Real-time status monitoring wizard for GrblDriver.
 * 
 * <p>Provides live monitoring of grblHAL controller status including:</p>
 * <ul>
 * <li>Limit switch states for all axes (X, Y, Z, A, B, C) using grbl ? command</li>
 * <li>Probe input status for tool length measurement and probing operations</li>
 * <li>Connection-aware monitoring that automatically starts/stops with connection state</li>
 * <li>500ms polling interval to balance responsiveness with controller load</li>
 * </ul>
 * 
 * <p>Future expansion areas planned:</p>
 * <ul>
 * <li>Machine state monitoring (Idle/Run/Hold/Alarm status display)</li>
 * <li>Live position display (work and machine coordinates)</li>
 * <li>Feed rate and spindle speed monitoring</li>
 * <li>Planner buffer and RX buffer status levels</li>
 * </ul>
 */
public class GrblDriverRealTimeWizard extends AbstractConfigurationWizard {
    
    private final GrblDriver driver;
    private boolean isConnected;
    
    // === LIVE LIMIT SWITCH STATUS COMPONENTS ===
    private JPanel limitStatusPanel;
    private JCheckBox enableLimitMonitoringCheckbox;
    private JLabel xLimitStatus;
    private JLabel yLimitStatus;
    private JLabel zLimitStatus;
    private JLabel aLimitStatus;
    private JLabel bLimitStatus;
    private JLabel cLimitStatus;
    private Timer limitStatusTimer;
    private boolean limitMonitoringEnabled = false;
    
    /**
     * Constructs a new real-time status monitoring wizard.
     * Sets up connection tracking and initializes monitoring components.
     * 
     * @param driver the GrblDriver to monitor
     */
    public GrblDriverRealTimeWizard(GrblDriver driver) {
        this.driver = driver;
        this.isConnected = driver.isConnected();
        
        addRealTimeStatusToPanel();
        setupConnectionTracking();
        updateComponentStates();
        
        Logger.info("Created GrblDriverRealTimeWizard for driver: {}", driver.getClass().getSimpleName());
    }
    
    /**
     * No property bindings needed for real-time monitoring wizard.
     * This wizard only displays live status without modifying driver properties.
     */
    @Override
    public void createBindings() {
        // No bindings needed - this is a monitoring-only wizard
        Logger.debug("GrblDriverRealTimeWizard - no property bindings required");
    }
    
    /**
     * Adds real-time status monitoring panels to the main configuration panel.
     * Creates limit switch monitoring and information sections.
     */
    private void addRealTimeStatusToPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        JPanel statusPanel = new JPanel(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        
        // Add limit switch status panel
        limitStatusPanel = createLimitStatusPanel();
        gbc.gridx = 0;
        gbc.gridy = 0;
        statusPanel.add(limitStatusPanel, gbc);
        
        // Information text about real-time monitoring
        JLabel infoLabel = new JLabel("<html><div style='width: 500px;'>" +
                "<h3>Real-time Status Monitoring</h3>" +
                "<p>This tab provides live monitoring of grblHAL controller status using real-time queries (?). " +
                "Enable monitoring features only when needed to reduce controller communication load.</p>" +
                "<p><b>Available Features:</b></p>" +
                "<ul>" +
                "<li><b>Limit Switch Monitor</b> - Live status of all limit switches and probe input</li>" +
                "</ul>" +
                "<p><b>Future Features:</b></p>" +
                "<ul>" +
                "<li>Machine State Monitor - Idle/Run/Hold/Alarm status display</li>" +
                "<li>Position Display - Live work and machine position coordinates</li>" +
                "<li>Feed Rate Monitor - Current feed rate and spindle speed</li>" +
                "<li>Buffer Status - Planner buffer and RX buffer levels</li>" +
                "</ul>" +
                "</div></html>");
        infoLabel.setVerticalAlignment(JLabel.TOP);
        
        gbc.gridy = 1;
        gbc.weighty = 0.0;
        statusPanel.add(infoLabel, gbc);
        
        // Add fill space at bottom
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        statusPanel.add(new JPanel(), gbc);
        
        mainPanel.add(statusPanel, BorderLayout.CENTER);
        
        // Add to contentPanel (AbstractConfigurationWizard's panel)
        contentPanel.add(mainPanel);
    }
    
    /**
     * Creates live limit switch status monitoring panel.
     * 
     * <p>Provides real-time monitoring of limit switch states for debugging
     * and machine setup. Uses grbl real-time status reports (?) to poll
     * pin states and display visual indicators.</p>
     * 
     * @return configured panel with live status indicators
     */
    private JPanel createLimitStatusPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Live Limit Switch Status"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Enable/disable monitoring checkbox
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 8;
        enableLimitMonitoringCheckbox = new JCheckBox("Enable live monitoring (500ms polling)");
        enableLimitMonitoringCheckbox.addActionListener(e -> toggleLimitMonitoring());
        panel.add(enableLimitMonitoringCheckbox, gbc);
        
        // Reset for individual status indicators
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        
        // Row 1: X, Y, Z
        // X Limit
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("X Limit:"), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        xLimitStatus = createStatusLabel();
        panel.add(xLimitStatus, gbc);
        
        // Spacing column
        gbc.gridx = 2;
        gbc.insets = new Insets(5, 20, 5, 20); // Wider spacing
        panel.add(new JLabel(""), gbc);
        gbc.insets = new Insets(5, 5, 5, 5); // Reset insets
        
        // Y Limit
        gbc.gridx = 3;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Y Limit:"), gbc);
        gbc.gridx = 4;
        gbc.anchor = GridBagConstraints.WEST;
        yLimitStatus = createStatusLabel();
        panel.add(yLimitStatus, gbc);
        
        // Spacing column
        gbc.gridx = 5;
        gbc.insets = new Insets(5, 20, 5, 20);
        panel.add(new JLabel(""), gbc);
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Z Limit
        gbc.gridx = 6;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Z Limit:"), gbc);
        gbc.gridx = 7;
        gbc.anchor = GridBagConstraints.WEST;
        zLimitStatus = createStatusLabel();
        panel.add(zLimitStatus, gbc);
        
        // Row 2: A, B, C
        gbc.gridy = 2;
        
        // A Limit
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("A Limit:"), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        aLimitStatus = createStatusLabel();
        panel.add(aLimitStatus, gbc);
        
        // Spacing
        gbc.gridx = 2;
        gbc.insets = new Insets(5, 20, 5, 20);
        panel.add(new JLabel(""), gbc);
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // B Limit
        gbc.gridx = 3;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("B Limit:"), gbc);
        gbc.gridx = 4;
        gbc.anchor = GridBagConstraints.WEST;
        bLimitStatus = createStatusLabel();
        panel.add(bLimitStatus, gbc);
        
        // Spacing
        gbc.gridx = 5;
        gbc.insets = new Insets(5, 20, 5, 20);
        panel.add(new JLabel(""), gbc);
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // C Limit
        gbc.gridx = 6;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("C Limit:"), gbc);
        gbc.gridx = 7;
        gbc.anchor = GridBagConstraints.WEST;
        cLimitStatus = createStatusLabel();
        panel.add(cLimitStatus, gbc);
        
        // Initialize all status labels to "not connected" state
        updateLimitStatusDisplay(null);
        
        return panel;
    }
    
    /**
     * Creates a status label with consistent formatting.
     * 
     * @return configured JLabel for status display
     */
    private JLabel createStatusLabel() {
        JLabel label = new JLabel("● UNKNOWN");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setForeground(Color.GRAY);
        return label;
    }
    
    /**
     * Toggles limit switch monitoring on/off.
     * Starts or stops the polling timer based on checkbox state.
     */
    private void toggleLimitMonitoring() {
        limitMonitoringEnabled = enableLimitMonitoringCheckbox.isSelected();
        
        if (limitMonitoringEnabled && isConnected) {
            startLimitStatusPolling();
            Logger.info("Started limit switch monitoring (500ms polling)");
        } else {
            stopLimitStatusPolling();
            Logger.info("Stopped limit switch monitoring");
        }
        
        // Update status display
        if (!limitMonitoringEnabled) {
            updateLimitStatusDisplay(null);
        }
    }
    
    /**
     * Starts limit status polling timer.
     * Polls controller every 500ms for real-time status updates.
     */
    private void startLimitStatusPolling() {
        // Stop existing timer if running
        stopLimitStatusPolling();
        
        // Create new timer for 500ms polling
        limitStatusTimer = new Timer(500, e -> updateLimitStatusFromController());
        limitStatusTimer.start();
        Logger.debug("Limit status polling timer started");
    }
    
    /**
     * Stops limit status polling timer.
     */
    private void stopLimitStatusPolling() {
        if (limitStatusTimer != null) {
            limitStatusTimer.stop();
            limitStatusTimer = null;
            Logger.debug("Limit status polling timer stopped");
        }
    }
    
    /**
     * Updates limit status from controller using real-time status query.
     * Called by polling timer every 500ms when monitoring is enabled.
     */
    private void updateLimitStatusFromController() {
        if (!isConnected || !limitMonitoringEnabled) {
            return;
        }
        
        try {
            GrblDriver.LimitSwitchStatus status = driver.getLimitSwitchStatus();
            
            // Update GUI on EDT thread
            SwingUtilities.invokeLater(() -> updateLimitStatusDisplay(status));
            
        } catch (Exception e) {
            // Don't spam logs during polling - just update display to show error
            SwingUtilities.invokeLater(() -> updateLimitStatusDisplay(null));
        }
    }
    
    /**
     * Updates limit status display with current switch states.
     * 
     * @param status current limit switch status, or null if unavailable
     */
    private void updateLimitStatusDisplay(GrblDriver.LimitSwitchStatus status) {
        if (status == null) {
            // No status available - show disconnected/unknown state
            updateStatusLabel(xLimitStatus, false, false);
            updateStatusLabel(yLimitStatus, false, false);
            updateStatusLabel(zLimitStatus, false, false);
            updateStatusLabel(aLimitStatus, false, false);
            updateStatusLabel(bLimitStatus, false, false);
            updateStatusLabel(cLimitStatus, false, false);
            return;
        }
        
        // Update each status label with current state
        updateStatusLabel(xLimitStatus, true, status.xTriggered);
        updateStatusLabel(yLimitStatus, true, status.yTriggered);
        updateStatusLabel(zLimitStatus, true, status.zTriggered);
        updateStatusLabel(aLimitStatus, true, status.aTriggered);
        updateStatusLabel(bLimitStatus, true, status.bTriggered);
        updateStatusLabel(cLimitStatus, true, status.cTriggered);
    }
    
    /**
     * Updates individual status label with color and text.
     * 
     * @param label the status label to update
     * @param connected true if controller is connected and monitoring
     * @param triggered true if the limit switch is currently triggered
     */
    private void updateStatusLabel(JLabel label, boolean connected, boolean triggered) {
        if (!connected) {
            label.setText("● UNKNOWN");
            label.setForeground(Color.GRAY);
            return;
        }
        
        if (triggered) {
            label.setText("● TRIGGERED");
            label.setForeground(Color.RED);
        } else {
            label.setText("● OK");
            label.setForeground(new Color(0, 150, 0)); // Dark green
        }
    }
    
    /**
     * Sets up connection tracking to monitor GrblDriver connection state.
     * Establishes property change listener for automatic GUI updates when connection changes.
     */
    private void setupConnectionTracking() {
        driver.addPropertyChangeListener("connected", evt -> {
            SwingUtilities.invokeLater(() -> {
                this.isConnected = (Boolean) evt.getNewValue();
                updateComponentStates();
                
                // Stop monitoring if disconnected
                if (!isConnected && limitMonitoringEnabled) {
                    enableLimitMonitoringCheckbox.setSelected(false);
                    toggleLimitMonitoring();
                }
            });
        });
    }
    
    /**
     * Updates component enabled states based on connection status.
     * Called when connection state changes.
     */
    private void updateComponentStates() {
        boolean canMonitor = isConnected;
        
        if (enableLimitMonitoringCheckbox != null) {
            enableLimitMonitoringCheckbox.setEnabled(canMonitor);
            
            // Update tooltip
            if (canMonitor) {
                enableLimitMonitoringCheckbox.setToolTipText(
                    "Enable real-time monitoring of limit switch states (grbl ? command polling)"
                );
            } else {
                enableLimitMonitoringCheckbox.setToolTipText(
                    "Connect to grblHAL controller to enable live limit monitoring"
                );
            }
        }
    }
    
    /**
     * Cleans up resources when wizard is disposed.
     * Stops polling timer to prevent memory leaks.
     */
    @Override
    public void dispose() {
        // Stop limit monitoring
        stopLimitStatusPolling();
        
        Logger.debug("GrblDriverRealTimeWizard disposed");
        
        super.dispose();
    }
}