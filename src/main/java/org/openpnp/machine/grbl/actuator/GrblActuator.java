package org.openpnp.machine.grbl.actuator;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.machine.grbl.driver.GrblDriver;
import org.openpnp.machine.grbl.wizards.GrblActuatorConfigurationWizard;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.model.Configuration;
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
        
        @Override
        public String toString() { 
            return displayName; 
        }
        
        public boolean hasOutput() { 
            return hasOutput; 
        }
        
        public boolean hasInput() { 
            return hasInput; 
        }
    }
    
    // === PROPERTIES ===
    
    private ActuatorType actuatorType = ActuatorType.OUTPUT_ONLY;  // Default for most PnP actuators
    private boolean inputPinInvert = false;    // $370 bit - active if actuatorType.hasInput()
    private boolean outputPinInvert = false;   // $372 bit - active if actuatorType.hasOutput()
    
    // Connection tracking
    private PropertyChangeListener driverConnectionListener;
    
    // === INITIALIZATION ===
    
    public GrblActuator() {
        super();
        setupConnectionTracking();
    }
    
    /**
     * Setup connection tracking to monitor GrblDriver connection state
     */
    private void setupConnectionTracking() {
        driverConnectionListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("connected".equals(evt.getPropertyName())) {
                    boolean connected = (Boolean) evt.getNewValue();
                    Logger.debug("GrblActuator {} connection state changed: {}", getName(), connected);
                    
                    if (connected) {
                        // Sync from controller when connected
                        syncFromController();
                    }
                }
            }
        };
        
        // Add listener to driver when available
        Configuration.get().addPropertyChangeListener("driver", evt -> {
            if (evt.getNewValue() instanceof GrblDriver) {
                GrblDriver driver = (GrblDriver) evt.getNewValue();
                driver.addPropertyChangeListener("connected", driverConnectionListener);
                Logger.debug("Added connection listener to GrblDriver for actuator {}", getName());
            }
        });
    }
    
    // === ACTUATOR TYPE ===
    
    public ActuatorType getActuatorType() {
        return actuatorType;
    }
    
    public void setActuatorType(ActuatorType actuatorType) {
        ActuatorType oldValue = this.actuatorType;
        this.actuatorType = actuatorType;
        firePropertyChange("actuatorType", oldValue, actuatorType);
        
        Logger.debug("Actuator {} type changed to: {}", getName(), actuatorType);
    }
    
    // === INPUT PIN INVERT ($370) ===
    
    public boolean isInputPinInvert() {
        return inputPinInvert;
    }
    
    public void setInputPinInvert(boolean inputPinInvert) {
        boolean oldValue = this.inputPinInvert;
        this.inputPinInvert = inputPinInvert;
        
        // Sync to controller if connected and actuator supports input
        if (oldValue != inputPinInvert && isConnected() && actuatorType.hasInput()) {
            syncInputPinInvertToController();
        }
        
        firePropertyChange("inputPinInvert", oldValue, inputPinInvert);
    }
    
    // === OUTPUT PIN INVERT ($372) ===
    
    public boolean isOutputPinInvert() {
        return outputPinInvert;
    }
    
    public void setOutputPinInvert(boolean outputPinInvert) {
        boolean oldValue = this.outputPinInvert;
        this.outputPinInvert = outputPinInvert;
        
        // Sync to controller if connected and actuator supports output
        if (oldValue != outputPinInvert && isConnected() && actuatorType.hasOutput()) {
            syncOutputPinInvertToController();
        }
        
        firePropertyChange("outputPinInvert", oldValue, outputPinInvert);
    }
    
    // === CONNECTION MANAGEMENT ===
    
    /**
     * Check if actuator is connected to a GrblDriver
     */
    public boolean isConnected() {
        GrblDriver grblDriver = getGrblDriver();
        return grblDriver != null && grblDriver.isConnected();
    }
    
    /**
     * Get the GrblDriver instance for this actuator
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
    
    // === CONTROLLER SYNC METHODS ===
    
    /**
     * Sync input pin invert setting to grblHAL controller ($370)
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
     * Sync output pin invert setting to grblHAL controller ($372)
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
     * Sync both input and output pin invert settings from controller
     * Called when connection is established
     */
    private void syncFromController() {
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
     * Sync input pin invert from controller ($370)
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
     * Sync output pin invert from controller ($372)
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
    
    // === GUI INTEGRATION ===
    
    @Override
    public PropertySheet[] getPropertySheets() {
        // Get parent sheets from ReferenceActuator
        PropertySheet[] parentSheets = super.getPropertySheets();
        
        // Add our grblHAL-specific sheet
        return Collect.concat(
            parentSheets,
            new PropertySheet[] {
                new PropertySheetWizardAdapter(new GrblActuatorConfigurationWizard(this), "Grbl Settings")
            }
        );
    }
    
    // === CLEANUP ===
    
    @Override
    protected void finalize() throws Throwable {
        // Remove connection listener on cleanup
        if (driverConnectionListener != null && getDriver() instanceof GrblDriver) {
            ((GrblDriver) getDriver()).removePropertyChangeListener("connected", driverConnectionListener);
        }
        super.finalize();
    }
}