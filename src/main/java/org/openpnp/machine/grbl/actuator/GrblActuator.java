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

package org.openpnp.machine.grbl.actuator;

import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.machine.grbl.driver.GrblDriver;
import org.openpnp.machine.grbl.wizards.GrblActuatorConfigurationWizard;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.util.Collect;
import org.pmw.tinylog.Logger;

/**
 * Enhanced ReferenceActuator with grblHAL IO pin invert support.
 * Provides per-actuator control of input and output pin invert settings
 * via $370 (input) and $372 (output) bitmasks.
 * 
 * Features:
 * - ActuatorType selection (Output Only, Input Only, Input/Output)
 * - Input pin invert ($370 bit for M143 read commands)  
 * - Output pin invert ($372 bit for M42 control commands)
 * - Auto-sync to grblHAL controller settings
 * - Connection-aware GUI
 */
public class GrblActuator extends ReferenceActuator {
    
    /**
     * Actuator type determines which pin invert settings are available
     */
    public enum ActuatorType {
        OUTPUT_ONLY("Output Only", true, false),      // M42 control only (vacuum pumps, LEDs)
        INPUT_ONLY("Input Only", false, true),        // M143 read only (sensors)  
        INPUT_OUTPUT("Input/Output", true, true);     // Both M42 and M143 (smart actuators)
        
        private final String displayName;
        private final boolean hasOutput;
        private final boolean hasInput;
        
        ActuatorType(String displayName, boolean hasOutput, boolean hasInput) {
            this.displayName = displayName;
            this.hasOutput = hasOutput;
            this.hasInput = hasInput;
        }
        
        /**
         * Returns the display name for this actuator type.
         * 
         * @return human-readable name for GUI display
         */
        @Override
        public String toString() { 
            return displayName; 
        }
        
        /**
         * Checks if this actuator type supports output operations.
         * Output operations use M42 commands and $372 pin invert setting.
         * 
         * @return true if output operations are supported
         */
        public boolean hasOutput() { 
            return hasOutput; 
        }
        
        /**
         * Checks if this actuator type supports input operations.
         * Input operations use M143 commands and $370 pin invert setting.
         * 
         * @return true if input operations are supported
         */
        public boolean hasInput() { 
            return hasInput; 
        }
    }
    
    // === PROPERTIES ===
    
    private ActuatorType actuatorType = ActuatorType.OUTPUT_ONLY;  // Default for most PnP actuators
    private boolean inputPinInvert = false;    // $370 bit - active if actuatorType.hasInput()
    private boolean outputPinInvert = false;   // $372 bit - active if actuatorType.hasOutput()
    
    // === INITIALIZATION ===
    
    public GrblActuator() {
        super();
    }
    
    /**
     * Returns property sheets for this actuator including parent sheets and grblHAL-specific settings.
     * Adds "Grbl Settings" tab with pin invert configuration wizard.
     * 
     * @return array of PropertySheet instances for GUI configuration
    */
    public ActuatorType getActuatorType() {
        return actuatorType;
    }
    
    /**
     * Sets the actuator type which determines available pin invert options.
     * Changes which checkboxes are enabled in the configuration wizard.
     * 
     * @param actuatorType the actuator type (Output Only, Input Only, or Input/Output)
     */
    public void setActuatorType(ActuatorType actuatorType) {
        ActuatorType oldValue = this.actuatorType;
        this.actuatorType = actuatorType;
        firePropertyChange("actuatorType", oldValue, actuatorType);
        
        Logger.debug("Actuator {} type changed to: {}", getName(), actuatorType);
    }
    
    /**
     * Gets the input pin invert state for this actuator.
     * When true, input readings from M143 commands are inverted.
     * Corresponds to a bit in the grblHAL $370 setting.
     * 
     * @return true if input pin logic is inverted, false for normal logic
     */   
    public boolean isInputPinInvert() {
        return inputPinInvert;
    }
    
    /**
     * Sets the input pin invert state for this actuator.
     * When true, input readings from M143 commands will be inverted.
     * Automatically syncs to controller $370 setting if connected and actuator supports input.
     * 
     * @param inputPinInvert true to invert input pin logic, false for normal logic
     */
    public void setInputPinInvert(boolean inputPinInvert) {
        boolean oldValue = this.inputPinInvert;
        this.inputPinInvert = inputPinInvert;
        
        // Sync to controller if connected and actuator supports input
        if (oldValue != inputPinInvert && isConnected() && actuatorType.hasInput()) {
            syncInputPinInvertToController();
        }
        
        firePropertyChange("inputPinInvert", oldValue, inputPinInvert);
    }
    
    /**
     * Gets the output pin invert state for this actuator.
     * When true, M42 commands use inverted logic (1=OFF, 0=ON).
     * Corresponds to a bit in the grblHAL $372 setting.
     * 
     * @return true if output pin logic is inverted, false for normal logic
     */
    public boolean isOutputPinInvert() {
        return outputPinInvert;
    }
    
    /**
     * Sets the output pin invert state for this actuator.
     * When true, M42 commands will use inverted logic (1=OFF, 0=ON).
     * Automatically syncs to controller $372 setting if connected and actuator supports output.
     * 
     * @param outputPinInvert true to invert output pin logic, false for normal logic
     */
    public void setOutputPinInvert(boolean outputPinInvert) {
        boolean oldValue = this.outputPinInvert;
        this.outputPinInvert = outputPinInvert;
        
        // Sync to controller if connected and actuator supports output
        if (oldValue != outputPinInvert && isConnected() && actuatorType.hasOutput()) {
            syncOutputPinInvertToController();
        }
        
        firePropertyChange("outputPinInvert", oldValue, outputPinInvert);
    }
        
    /**
     * Checks if this actuator is connected to a grblHAL controller.
     * Used to determine if settings can be synchronized to the controller.
     * 
     * @return true if connected to a GrblDriver that is connected to controller
     */
    public boolean isConnected() {
        GrblDriver grblDriver = getGrblDriver();
        return grblDriver != null && grblDriver.isConnected();
    }
    
    /**
     * Gets the GrblDriver instance associated with this actuator.
     * Used internally for controller communication and settings sync.
     * 
     * @return GrblDriver instance or null if not available or wrong driver type
     */
    private GrblDriver getGrblDriver() {
        try {
            if (getDriver() instanceof GrblDriver) {
                return (GrblDriver) getDriver();
            }
        } catch (Exception e) {
            Logger.debug("Failed to get GrblDriver for actuator {}: {}", getName(), e.getMessage());
        }
        return null;
    }
    
    /**
     * Synchronizes input pin invert setting to the grblHAL controller.
     * Updates the corresponding bit in the $370 bitmask and sends to controller.
     */
    private void syncInputPinInvertToController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null) {
                Logger.warn("Cannot sync input pin invert - no GrblDriver available for actuator {}", getName());
                return;
            }
            
            int ioIndex = getIndex();
            if (ioIndex < 0 || ioIndex > 7) {
                Logger.warn("Cannot sync input pin invert - invalid IO index {} for actuator {}", ioIndex, getName());
                return;
            }
            
            // Update the driver's bitmask for $370
            grblDriver.updateInputPinInvertBit(ioIndex, inputPinInvert);
            
            Logger.info("Synced actuator {} input pin invert to controller: {} (IO index {})", getName(), inputPinInvert, ioIndex);
            
        } catch (Exception e) {
            Logger.warn("Failed to sync input pin invert to controller for actuator {}: {}", getName(), e.getMessage());
        }
    }
    
    /**
     * Synchronizes output pin invert setting to the grblHAL controller.
     * Updates the corresponding bit in the $372 bitmask and sends to controller.
     */
    private void syncOutputPinInvertToController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null) {
                Logger.warn("Cannot sync output pin invert - no GrblDriver available for actuator {}", getName());
                return;
            }
            
            int ioIndex = getIndex();
            if (ioIndex < 0 || ioIndex > 7) {
                Logger.warn("Cannot sync output pin invert - invalid IO index {} for actuator {}", ioIndex, getName());
                return;
            }
            
            // Update the driver's bitmask for $372
            grblDriver.updateOutputPinInvertBit(ioIndex, outputPinInvert);
            
            Logger.info("Synced actuator {} output pin invert to controller: {} (IO index {})", getName(), outputPinInvert, ioIndex);
            
        } catch (Exception e) {
            Logger.warn("Failed to sync output pin invert to controller for actuator {}: {}", getName(), e.getMessage());
        }
    }
    
    /**
     * Synchronizes pin invert settings from the grblHAL controller to this actuator.
     * Reads $370 and $372 settings and updates local properties accordingly.
     * Called automatically when connection is established.
     */
    public void syncFromController() {
        try {
            GrblDriver grblDriver = getGrblDriver();
            if (grblDriver == null || grblDriver.getSettingsSync() == null) {
                Logger.debug("Cannot sync from controller - no driver or settings sync available for actuator {}", getName());
                return;
            }
            
            int ioIndex = getIndex();
            if (ioIndex < 0 || ioIndex > 7) {
                Logger.debug("Cannot sync from controller - invalid IO index {} for actuator {}", ioIndex, getName());
                return;
            }
            
            // Sync input pin invert from controller ($370) if actuator supports input
            if (actuatorType.hasInput()) {
                syncInputPinInvertFromController(grblDriver, ioIndex);
            }
            
            // Sync output pin invert from controller ($372) if actuator supports output
            if (actuatorType.hasOutput()) {
                syncOutputPinInvertFromController(grblDriver, ioIndex);
            }
            
        } catch (Exception e) {
            Logger.warn("Failed to sync pin inverts from controller for actuator {}: {}", getName(), e.getMessage());
        }
    }
    
    /**
     * Reads input pin invert setting from controller $370 and updates local property.
     * 
     * @param grblDriver the driver instance to read from
     * @param ioIndex the IO index (0-7) for this actuator
     */
    private void syncInputPinInvertFromController(GrblDriver grblDriver, int ioIndex) {
        String inputInvertStr = grblDriver.getSettingsSync().getControllerSetting(370);
        try {
            if (inputInvertStr != null) {
                int inputInvertMask = Integer.parseInt(inputInvertStr);
                boolean actuatorInputInvert = (inputInvertMask & (1 << ioIndex)) != 0;
                
                // Set directly to avoid triggering sync back to controller
                if (this.inputPinInvert != actuatorInputInvert) {
                    this.inputPinInvert = actuatorInputInvert;
                    firePropertyChange("inputPinInvert", !actuatorInputInvert, actuatorInputInvert);
                    Logger.info("Synced actuator {} input pin invert from controller: {} (IO index {})", getName(), actuatorInputInvert, ioIndex);
                }
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid input pin invert mask from controller for actuator {}: {}", getName(), inputInvertStr);
        }
    }
    
    /**
     * Reads output pin invert setting from controller $372 and updates local property.
     * 
     * @param grblDriver the driver instance to read from  
     * @param ioIndex the IO index (0-7) for this actuator
     */
    private void syncOutputPinInvertFromController(GrblDriver grblDriver, int ioIndex) {
        String outputInvertStr = grblDriver.getSettingsSync().getControllerSetting(372);
        try {
            if (outputInvertStr != null) {
                int outputInvertMask = Integer.parseInt(outputInvertStr);
                boolean actuatorOutputInvert = (outputInvertMask & (1 << ioIndex)) != 0;
                
                // Set directly to avoid triggering sync back to controller
                if (this.outputPinInvert != actuatorOutputInvert) {
                    this.outputPinInvert = actuatorOutputInvert;
                    firePropertyChange("outputPinInvert", !actuatorOutputInvert, actuatorOutputInvert);
                    Logger.info("Synced actuator {} output pin invert from controller: {} (IO index {})", getName(), actuatorOutputInvert, ioIndex);
                }
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid output pin invert mask from controller for actuator {}: {}", getName(), outputInvertStr);
        }
    }
    
    /**
     * Returns property sheets for this actuator including parent sheets and grblHAL-specific settings.
     * Adds "Grbl Settings" tab with pin invert configuration wizard.
     * 
     * @return array of PropertySheet instances for GUI configuration
     */
    @Override
    public PropertySheet[] getPropertySheets() {
        // Get parent sheets from ReferenceActuator
        PropertySheet[] parentSheets = super.getPropertySheets();
        
        return Collect.concat(
            parentSheets,
            new PropertySheet[] {
                new PropertySheetWizardAdapter(new GrblActuatorConfigurationWizard(getMachine(), this), "Grbl Settings")
            }
        );
    }
}