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

/**
 * Represents a discrepancy between OpenPnP configuration and grbl controller settings
 */
public class SettingDiscrepancy {
    private final int settingId;
    private final String settingName;
    private final String category;
    private final double openPnpValue;
    private final double controllerValue;
    
    public SettingDiscrepancy(int settingId, String settingName, String category, 
                             double openPnpValue, double controllerValue) {
        this.settingId = settingId;
        this.settingName = settingName;
        this.category = category;
        this.openPnpValue = openPnpValue;
        this.controllerValue = controllerValue;
    }
    
    // Getters
    public int getSettingId() { return settingId; }
    public String getSettingName() { return settingName; }
    public String getCategory() { return category; }
    public double getOpenPnpValue() { return openPnpValue; }
    public double getControllerValue() { return controllerValue; }
    
    public String getFormattedOpenPnpValue() { return String.format("%.3f", openPnpValue); }
    public String getFormattedControllerValue() { return String.format("%.3f", controllerValue); }
    public String getFormattedDifference() { return String.format("%.3f", Math.abs(openPnpValue - controllerValue)); }
    
    @Override
    public String toString() {
        return String.format("%s $%d (%s): OpenPnP=%.3f, Controller=%.3f", 
            category, settingId, settingName, openPnpValue, controllerValue);
    }
}