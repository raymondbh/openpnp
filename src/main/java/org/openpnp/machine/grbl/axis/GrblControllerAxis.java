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
 * Enhanced grblHAL support - controller axis with bidirectional settings synchronization.
 */

package org.openpnp.machine.grbl.axis;

import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.machine.grbl.driver.GrblDriver;
import org.openpnp.machine.grbl.wizards.GrblControllerAxisConfigurationWizard;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Driver;
import org.pmw.tinylog.Logger;

/**
 * Controller axis with grblHAL settings synchronization.
 * 
 * Provides bidirectional synchronization between OpenPnP configuration and grblHAL controller:
 * - Automatic sync of resolution, feedrate, and acceleration changes to controller
 * - Pin invert settings for step ($2), direction ($3), and step enable ($4) pins
 * - Ganged motor invert settings ($8) for Y/Z axes when supported by controller
 * - Real-time settings sync from controller to OpenPnP on connection
 * - Connection-aware GUI with automatic enable/disable based on controller state
 */
public class GrblControllerAxis extends ReferenceControllerAxis {
    
    // === PIN INVERT SETTINGS ===
    
    private boolean stepPinInvert = false;      // Per-axis step pin invert ($2)
    private boolean dirPinInvert = false;       // Per-axis direction pin invert ($3)
    private boolean stepEnableInvert = false;   // Per-axis step enable invert ($4)
    private boolean gangedMotorInvert = false;  // Per-axis ganged motor invert ($8)

    // === MOTION SETTING OVERRIDES WITH AUTO-SYNC ===

    /**
     * Sets the axis resolution and automatically syncs to controller if connected.
     * 
     * @param resolution the new resolution in units per step
     */
    @Override
    public void setResolution(double resolution) {
        double oldResolution = getResolution();
        
        // Call parent setter first
        super.setResolution(resolution);
        
        // Sync to controller if resolution changed
        if (Math.abs(oldResolution - resolution) > 0.0001 && isConnected()) {
            syncStepsToController();
        }
    }
    
    /**
     * Sets the axis feedrate and automatically syncs to controller if connected.
     * 
     * @param feedratePerSecond the new feedrate per second
     */
    @Override
    public void setFeedratePerSecond(Length feedratePerSecond) {
        Length oldFeedrate = getFeedratePerSecond();
        
        // Call parent setter first
        super.setFeedratePerSecond(feedratePerSecond);
        
        // Sync to controller if feedrate changed
        if (oldFeedrate != null && !oldFeedrate.equals(feedratePerSecond) && isConnected()) {
            syncFeedrateToController();
        }
    }
    
    /**
     * Sets the axis acceleration and automatically syncs to controller if connected.
     * 
     * @param accelerationPerSecond2 the new acceleration per second squared
     */
    @Override
    public void setAccelerationPerSecond2(Length accelerationPerSecond2) {
        Length oldAcceleration = getAccelerationPerSecond2();
        
        // Call parent setter first
        super.setAccelerationPerSecond2(accelerationPerSecond2);
        
        // Sync to controller if acceleration changed
        if (oldAcceleration != null && !oldAcceleration.equals(accelerationPerSecond2) && isConnected()) {
            syncAccelerationToController();
        }
    }
    
    // === PIN INVERT PROPERTY ACCESSORS ===
    
    /**
     * Gets the step pin invert state for this axis.
     * When true, step pulses use inverted logic.
     * Corresponds to a bit in the grblHAL $2 setting.
     * 
     * @return true if step pin logic is inverted, false for normal logic
     */
    public boolean isStepPinInvert() {
        return stepPinInvert;
    }
    
    /**
     * Sets the step pin invert state for this axis.
     * When true, step pulses use inverted logic.
     * Automatically syncs to controller $2 setting if connected.
     * 
     * @param stepPinInvert true to invert step pin logic, false for normal logic
     */
    public void setStepPinInvert(boolean stepPinInvert) {
        boolean oldValue = this.stepPinInvert;
        this.stepPinInvert = stepPinInvert;
        
        // Sync to controller if changed and connected
        if (oldValue != stepPinInvert && isConnected()) {
            syncStepPinInvertToController();
        }
        
        firePropertyChange("stepPinInvert", oldValue, stepPinInvert);
    }
    
    /**
     * Gets the direction pin invert state for this axis.
     * When true, direction signals use inverted logic.
     * Corresponds to a bit in the grblHAL $3 setting.
     * 
     * @return true if direction pin logic is inverted, false for normal logic
     */
    public boolean isDirPinInvert() {
        return dirPinInvert;
    }
    
    /**
     * Sets the direction pin invert state for this axis.
     * When true, direction signals use inverted logic.
     * Automatically syncs to controller $3 setting if connected.
     * 
     * @param dirPinInvert true to invert direction pin logic, false for normal logic
     */
    public void setDirPinInvert(boolean dirPinInvert) {
        boolean oldValue = this.dirPinInvert;
        this.dirPinInvert = dirPinInvert;
        
        // Sync to controller if changed and connected
        if (oldValue != dirPinInvert && isConnected()) {
            syncDirPinInvertToController();
        }
        
        firePropertyChange("dirPinInvert", oldValue, dirPinInvert);
    }
    
    /**
     * Gets the step enable pin invert state for this axis.
     * When true, step enable signals use inverted logic.
     * Corresponds to a bit in the grblHAL $4 setting.
     * 
     * @return true if step enable pin logic is inverted, false for normal logic
     */
    public boolean isStepEnableInvert() {
        return stepEnableInvert;
    }
    
    /**
     * Sets the step enable pin invert state for this axis.
     * When true, step enable signals use inverted logic.
     * Automatically syncs to controller $4 setting if connected.
     * 
     * @param stepEnableInvert true to invert step enable pin logic, false for normal logic
     */
    public void setStepEnableInvert(boolean stepEnableInvert) {
        boolean oldValue = this.stepEnableInvert;
        this.stepEnableInvert = stepEnableInvert;
        
        // Sync to controller if changed and connected
        if (oldValue != stepEnableInvert && isConnected()) {
            syncStepEnableInvertToController();
        }
        
        firePropertyChange("stepEnableInvert", oldValue, stepEnableInvert);
    }

    /**
     * Gets the ganged motor invert state for this axis.
     * When true, secondary motor direction is inverted for dual motor axes.
     * Corresponds to a bit in the grblHAL $8 setting.
     * 
     * @return true if ganged motor direction is inverted, false for normal direction
     */
    public boolean isGangedMotorInvert() {
        return gangedMotorInvert;
    }

    /**
     * Sets the ganged motor invert state for this axis.
     * When true, secondary motor direction is inverted for dual motor axes.
     * Automatically syncs to controller $8 setting if connected.
     * 
     * @param gangedMotorInvert true to invert ganged motor direction, false for normal direction
     */
    public void setGangedMotorInvert(boolean gangedMotorInvert) {
        boolean oldValue = this.gangedMotorInvert;
        this.gangedMotorInvert = gangedMotorInvert;
        
        // Sync to controller if changed and connected
        if (oldValue != gangedMotorInvert && isConnected()) {
            syncGangedMotorInvertToController();
        }
        
        firePropertyChange("gangedMotorInvert", oldValue, gangedMotorInvert);
    }

    // === BIDIRECTIONAL SYNCHRONIZATION ===

    /**
     * Synchronizes all settings from the grblHAL controller to this axis.
     * Called automatically when connection is established to ensure consistency.
     */
    public void syncFromController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null || grblDriver.getSettingsSync() == null) {
                return;
            }
            
            Logger.info("Syncing {}-axis settings from controller to OpenPnP", getName());
            
            // Sync motion settings from controller
            syncStepsFromController();
            syncFeedrateFromController();
            syncAccelerationFromController();

            // Sync pin invert settings from controller
            syncPinInvertsFromController();
            
        } catch (Exception e) {
            Logger.warn("Failed to sync from controller for axis {}: {}", getName(), e.getMessage());
        }
    }

    /**
     * Checks if this axis should show ganged motor settings in the GUI.
     * Only Y/Z axes commonly use ganged motors and only when controller supports $8.
     * 
     * @return true if ganged motor settings should be displayed
     */
    public boolean shouldShowGangedMotorSettings() {
        // Check if this axis type commonly uses ganged motors
        String axisLetter = getLetter();
        if (axisLetter == null) {
            return false;
        }
        
        String letter = axisLetter.toUpperCase();
        boolean isCommonGangedAxis = "Y".equals(letter) || "Z".equals(letter);
        
        if (!isCommonGangedAxis) {
            return false;  // Only Y/Z axes typically have ganged motors
        }
        
        // Check if controller supports ganged motors (from cached sync result)
        GrblDriver grblDriver = getGrblDriver();
        if (grblDriver == null || grblDriver.getSettingsSync() == null) {
            return false;
        }
        
        boolean supported = grblDriver.getSettingsSync().isGangedMotorSupported();
        return supported;
    }

    // === PROPERTY SHEET INTEGRATION ===

    /**
     * Returns property sheets for this axis including parent sheets and grblHAL-specific settings.
     * Adds "Grbl Settings" tab with pin invert and motion configuration.
     * 
     * @return array of PropertySheet instances for GUI configuration
     */
    @Override
    public PropertySheet[] getPropertySheets() {
        // Get parent's property sheets first
        PropertySheet[] parentSheets = super.getPropertySheets();
        
        // Create our Grbl-specific sheet
        PropertySheet grblSheet = new PropertySheetWizardAdapter(createConfigurationWizard(), "Grbl Settings");
        
        // Combine parent sheets with our new sheet
        PropertySheet[] combinedSheets = new PropertySheet[parentSheets.length + 1];
        System.arraycopy(parentSheets, 0, combinedSheets, 0, parentSheets.length);
        combinedSheets[parentSheets.length] = grblSheet;
        
        return combinedSheets;
    }
    
    /**
     * Creates the configuration wizard for this axis.
     * 
     * @return GrblControllerAxisConfigurationWizard instance for GUI configuration
     */
    public AbstractConfigurationWizard createConfigurationWizard() {
        return new GrblControllerAxisConfigurationWizard(this);
    }

    // === PRIVATE MOTION SYNC METHODS ===

    /**
     * Synchronizes steps/mm setting to grblHAL controller.
     * Converts resolution to steps/mm and sends to appropriate $1xx setting.
     */
    private void syncStepsToController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null) {
                return;
            }
            
            double stepsPerMm = 1.0 / getResolution();
            
            // Apply smart rounding for common stepper values
            if (Math.abs(stepsPerMm - Math.round(stepsPerMm)) < 0.1) {
                stepsPerMm = Math.round(stepsPerMm);
            }
            
            int settingId = getStepsSettingId();
            if (settingId != -1) {
                grblDriver.syncSettingToController(settingId, stepsPerMm);
                Logger.info("Synced {}-axis steps/mm to controller: {}", getName(), stepsPerMm);
            }
            
        } catch (Exception e) {
            Logger.warn("Failed to sync steps/mm to controller for axis {}: {}", getName(), e.getMessage());
        }
    }
    
    /**
     * Synchronizes feedrate setting to grblHAL controller.
     * Converts feedrate to mm/min and sends to appropriate $11x setting.
     */
    private void syncFeedrateToController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null) {
                return;
            }
            
            // Convert to mm/min for grbl
            double feedratePerMin = getFeedratePerSecond()
                .convertToUnits(LengthUnit.Millimeters).getValue() * 60;
            
            int settingId = getFeedrateSettingId();
            if (settingId != -1) {
                grblDriver.syncSettingToController(settingId, feedratePerMin);
                Logger.info("Synced {}-axis feedrate to controller: {} mm/min", getName(), feedratePerMin);
            }
            
        } catch (Exception e) {
            Logger.warn("Failed to sync feedrate to controller for axis {}: {}", getName(), e.getMessage());
        }
    }
    
    /**
     * Synchronizes acceleration setting to grblHAL controller.
     * Sends acceleration value to appropriate $12x setting.
     */
    private void syncAccelerationToController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null) {
                return;
            }
            
            // Already in mm/s² for grbl
            double acceleration = getAccelerationPerSecond2()
                .convertToUnits(LengthUnit.Millimeters).getValue();
            
            int settingId = getAccelerationSettingId();
            if (settingId != -1) {
                grblDriver.syncSettingToController(settingId, acceleration);
                Logger.info("Synced {}-axis acceleration to controller: {} mm/s²", getName(), acceleration);
            }
            
        } catch (Exception e) {
            Logger.warn("Failed to sync acceleration to controller for axis {}: {}", getName(), e.getMessage());
        }
    }

    // === PRIVATE PIN INVERT SYNC METHODS ===

    /**
     * Synchronizes step pin invert setting to grblHAL controller.
     * Updates the corresponding bit in the $2 bitmask and sends to controller.
     */
    private void syncStepPinInvertToController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null) {
                return;
            }
            
            // Update the driver's bitmask for $2
            grblDriver.updateStepPinInvertBit(getAxisOffset(), stepPinInvert);
            
            Logger.info("Synced {}-axis step pin invert to controller: {}", getName(), stepPinInvert);
            
        } catch (Exception e) {
            Logger.warn("Failed to sync step pin invert to controller for axis {}: {}", getName(), e.getMessage());
        }
    }
    
    /**
     * Synchronizes direction pin invert setting to grblHAL controller.
     * Updates the corresponding bit in the $3 bitmask and sends to controller.
     */
    private void syncDirPinInvertToController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null) {
                return;
            }
            
            // Update the driver's bitmask for $3
            grblDriver.updateDirPinInvertBit(getAxisOffset(), dirPinInvert);
            
            Logger.info("Synced {}-axis dir pin invert to controller: {}", getName(), dirPinInvert);
            
        } catch (Exception e) {
            Logger.warn("Failed to sync dir pin invert to controller for axis {}: {}", getName(), e.getMessage());
        }
    }

    /**
     * Synchronizes step enable invert setting to grblHAL controller.
     * Updates the corresponding bit in the $4 bitmask and sends to controller.
     */
    private void syncStepEnableInvertToController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null) {
                return;
            }
            
            // Update the driver's bitmask for $4
            grblDriver.updateStepEnableInvertBit(getAxisOffset(), stepEnableInvert);
            
            Logger.info("Synced {}-axis step enable invert to controller: {}", getName(), stepEnableInvert);
            
        } catch (Exception e) {
            Logger.warn("Failed to sync step enable invert to controller for axis {}: {}", getName(), e.getMessage());
        }
    }

    /**
     * Synchronizes ganged motor invert setting to grblHAL controller.
     * Updates the corresponding bit in the $8 bitmask and sends to controller.
     */
    private void syncGangedMotorInvertToController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null) {
                return;
            }
            
            // Update the driver's bitmask for $8
            grblDriver.updateGangedMotorInvertBit(getAxisOffset(), gangedMotorInvert);
            
            Logger.info("Synced {}-axis ganged motor invert to controller: {}", getName(), gangedMotorInvert);
            
        } catch (Exception e) {
            Logger.warn("Failed to sync ganged motor invert to controller for axis {}: {}", getName(), e.getMessage());
        }
    }

    // === PRIVATE REVERSE SYNC METHODS ===

    /**
     * Reads steps/mm setting from controller and updates local resolution.
     * Avoids triggering sync back to controller during read operation.
     */
    private void syncStepsFromController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            int settingId = getStepsSettingId();
            
            if (settingId != -1) {
                String stepsStr = grblDriver.getSettingsSync().getControllerSetting(settingId);
                if (stepsStr != null) {
                    double stepsPerMm = Double.parseDouble(stepsStr);
                    double newResolution = 1.0 / stepsPerMm;
                    
                    // Set directly to avoid triggering sync back to controller
                    super.setResolution(newResolution);
                    Logger.info("Synced {}-axis steps/mm from controller: {} (resolution: {})", 
                        getName(), stepsPerMm, newResolution);
                }
            }
        } catch (Exception e) {
            Logger.warn("Failed to sync steps from controller for axis {}: {}", getName(), e.getMessage());
        }
    }
    
    /**
     * Reads feedrate setting from controller and updates local feedrate.
     * Avoids triggering sync back to controller during read operation.
     */
    private void syncFeedrateFromController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            int settingId = getFeedrateSettingId();
            
            if (settingId != -1) {
                String feedrateStr = grblDriver.getSettingsSync().getControllerSetting(settingId);
                if (feedrateStr != null) {
                    double feedratePerMin = Double.parseDouble(feedrateStr);
                    double feedratePerSec = feedratePerMin / 60.0; // Convert to mm/s
                    
                    Length newFeedrate = new Length(feedratePerSec, LengthUnit.Millimeters);
                    
                    // Set directly to avoid triggering sync back to controller
                    super.setFeedratePerSecond(newFeedrate);
                    Logger.info("Synced {}-axis feedrate from controller: {} mm/min", getName(), feedratePerMin);
                }
            }
        } catch (Exception e) {
            Logger.warn("Failed to sync feedrate from controller for axis {}: {}", getName(), e.getMessage());
        }
    }
    
    /**
     * Reads acceleration setting from controller and updates local acceleration.
     * Avoids triggering sync back to controller during read operation.
     */
    private void syncAccelerationFromController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            int settingId = getAccelerationSettingId();
            
            if (settingId != -1) {
                String accelStr = grblDriver.getSettingsSync().getControllerSetting(settingId);
                if (accelStr != null) {
                    double acceleration = Double.parseDouble(accelStr);
                    Length newAcceleration = new Length(acceleration, LengthUnit.Millimeters);
                    
                    // Set directly to avoid triggering sync back to controller
                    super.setAccelerationPerSecond2(newAcceleration);
                    Logger.info("Synced {}-axis acceleration from controller: {} mm/s²", getName(), acceleration);
                }
            }
        } catch (Exception e) {
            Logger.warn("Failed to sync acceleration from controller for axis {}: {}", getName(), e.getMessage());
        }
    }
    
    /**
     * Reads pin invert settings from controller and updates local properties.
     * Synchronizes $2, $3, $4, and $8 bitmasks for this axis.
     */
    private void syncPinInvertsFromController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null || grblDriver.getSettingsSync() == null) {
                return;
            }
            
            int axisOffset = getAxisOffset();
            if (axisOffset == -1) {
                return;
            }
            
            Logger.info("Syncing pin invert settings from controller for {}-axis (letter={}, offset={})", 
                    getName(), getLetter(), axisOffset);
            
            // Sync step pin invert from controller ($2)
            String stepInvertStr = grblDriver.getSettingsSync().getControllerSetting(2);
            if (stepInvertStr != null) {
                try {
                    int stepInvertMask = Integer.parseInt(stepInvertStr);
                    boolean axisStepInvert = (stepInvertMask & (1 << axisOffset)) != 0;
                    
                    // Set directly to avoid triggering sync back to controller
                    if (this.stepPinInvert != axisStepInvert) {
                        this.stepPinInvert = axisStepInvert;
                        firePropertyChange("stepPinInvert", !axisStepInvert, axisStepInvert);
                        Logger.info("Synced {}-axis step pin invert from controller: {}", getName(), axisStepInvert);
                    }
                } catch (NumberFormatException e) {
                    Logger.warn("Invalid step pin invert mask from controller: {}", stepInvertStr);
                }
            }
            
            // Sync dir pin invert from controller ($3)
            String dirInvertStr = grblDriver.getSettingsSync().getControllerSetting(3);
            if (dirInvertStr != null) {
                try {
                    int dirInvertMask = Integer.parseInt(dirInvertStr);
                    boolean axisDirInvert = (dirInvertMask & (1 << axisOffset)) != 0;
                    
                    // Set directly to avoid triggering sync back to controller
                    if (this.dirPinInvert != axisDirInvert) {
                        this.dirPinInvert = axisDirInvert;
                        firePropertyChange("dirPinInvert", !axisDirInvert, axisDirInvert);
                        Logger.info("Synced {}-axis dir pin invert from controller: {}", getName(), axisDirInvert);
                    }
                } catch (NumberFormatException e) {
                    Logger.warn("Invalid dir pin invert mask from controller: {}", dirInvertStr);
                }
            }
            
            // Sync step enable invert from controller ($4)
            String stepEnableInvertStr = grblDriver.getSettingsSync().getControllerSetting(4);
            if (stepEnableInvertStr != null) {
                try {
                    int stepEnableInvertMask = Integer.parseInt(stepEnableInvertStr);
                    boolean axisStepEnableInvert = (stepEnableInvertMask & (1 << axisOffset)) != 0;
                    
                    // Set directly to avoid triggering sync back to controller
                    if (this.stepEnableInvert != axisStepEnableInvert) {
                        this.stepEnableInvert = axisStepEnableInvert;
                        firePropertyChange("stepEnableInvert", !axisStepEnableInvert, axisStepEnableInvert);
                        Logger.info("Synced {}-axis step enable invert from controller: {}", getName(), axisStepEnableInvert);
                    }
                } catch (NumberFormatException e) {
                    Logger.warn("Invalid step enable invert mask from controller: {}", stepEnableInvertStr);
                }
            }

            // Sync ganged motor invert from controller ($8) - only if supported
            if (shouldShowGangedMotorSettings()) {
                String gangedInvertStr = grblDriver.getSettingsSync().getControllerSetting(8);
                if (gangedInvertStr != null) {
                    try {
                        int gangedInvertMask = Integer.parseInt(gangedInvertStr);
                        boolean axisGangedInvert = (gangedInvertMask & (1 << axisOffset)) != 0;
                        
                        // Set directly to avoid triggering sync back to controller
                        if (this.gangedMotorInvert != axisGangedInvert) {
                            this.gangedMotorInvert = axisGangedInvert;
                            firePropertyChange("gangedMotorInvert", !axisGangedInvert, axisGangedInvert);
                            Logger.info("Synced {}-axis ganged motor invert from controller: {}", getName(), axisGangedInvert);
                        }
                    } catch (NumberFormatException e) {
                        Logger.warn("Invalid ganged motor invert mask from controller: {}", gangedInvertStr);
                    }
                }
            }
            
        } catch (Exception e) {
            Logger.warn("Failed to sync pin inverts from controller for axis {}: {}", getName(), e.getMessage());
        }
    }
    
    // === PRIVATE HELPER METHODS ===
    
    /**
     * Gets the GrblDriver instance associated with this axis.
     * 
     * @return GrblDriver instance or null if not available or wrong driver type
     */
    private GrblDriver getGrblDriver() {
        if (getDriver() instanceof GrblDriver) {
            return (GrblDriver) getDriver();
        }
        return null;
    }
    
    /**
     * Checks if the driver is connected to a controller.
     * 
     * @return true if connected to a grblHAL controller
     */
    private boolean isConnected() {
        Driver driver = getDriver();
        if (driver instanceof GrblDriver) {
            return ((GrblDriver) driver).isConnected();
        }
        return false;
    }
    
    /**
     * Gets the grblHAL setting ID for steps/mm configuration.
     * 
     * @return setting ID ($100-$105) or -1 if invalid axis
     */
    private int getStepsSettingId() {
        return getAxisSettingId("steps");
    }
    
    /**
     * Gets the grblHAL setting ID for feedrate configuration.
     * 
     * @return setting ID ($110-$115) or -1 if invalid axis
     */
    private int getFeedrateSettingId() {
        return getAxisSettingId("feedrate");
    }
    
    /**
     * Gets the grblHAL setting ID for acceleration configuration.
     * 
     * @return setting ID ($120-$125) or -1 if invalid axis
     */
    private int getAccelerationSettingId() {
        return getAxisSettingId("acceleration");
    }
    
    /**
     * Gets the grblHAL setting ID for the specified setting type and this axis.
     * 
     * @param settingType the type of setting ("steps", "feedrate", or "acceleration")
     * @return the corresponding grblHAL setting ID or -1 if invalid
     */
    private int getAxisSettingId(String settingType) {
        int axisOffset = getAxisOffset();
        if (axisOffset == -1) {
            return -1;
        }
        
        switch (settingType.toLowerCase()) {
            case "steps":
                return 100 + axisOffset; // $100-$105 for X,Y,Z,A,B,C
            case "feedrate":
                return 110 + axisOffset; // $110-$115 for X,Y,Z,A,B,C
            case "acceleration":
                return 120 + axisOffset; // $120-$125 for X,Y,Z,A,B,C
            default:
                return -1;
        }
    }
    
    /**
     * Gets the axis offset for grblHAL setting calculations.
     * Maps axis letters (X,Y,Z,A,B,C) to array indices (0,1,2,3,4,5).
     * 
     * @return axis offset (0-5) or -1 if invalid axis letter
     */
    private int getAxisOffset() {
        switch (getLetter().toUpperCase()) {
            case "X": return 0;
            case "Y": return 1;
            case "Z": return 2;
            case "A": return 3;
            case "B": return 4;
            case "C": return 5;
            default: 
                Logger.warn("Unknown axis name for grbl settings: {}", getName());
                return -1;
        }
    }
}