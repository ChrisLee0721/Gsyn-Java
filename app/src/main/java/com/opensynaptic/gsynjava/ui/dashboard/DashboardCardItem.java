package com.opensynaptic.gsynjava.ui.dashboard;

import androidx.annotation.NonNull;

/** Represents one configurable card slot on the Dashboard. */
public class DashboardCardItem {

    public enum Type {
        HEADER,          // fixed header — always first, not draggable
        KPI_ROW1,        // Total Devices + Online Rate
        KPI_ROW2,        // Active Alerts + Throughput
        KPI_ROW3,        // Active Rules + Cumulative Traffic
        GAUGES,          // Live Metrics / Gauges
        CHARTS,          // Temperature + Humidity trend charts
        ACTIVITY,        // Activity Feed
        LATEST_READINGS, // Latest Live Readings
        CUSTOM_SENSOR    // User-defined sensor card (specific sensorId)
    }

    public Type type;
    public boolean visible = true;
    public int order;
    public String sensorId = ""; // only for CUSTOM_SENSOR
    public String label    = ""; // display label for CUSTOM_SENSOR

    public DashboardCardItem() {}

    public DashboardCardItem(Type type, int order) {
        this.type  = type;
        this.order = order;
    }

    public static DashboardCardItem customSensor(String sensorId, String label, int order) {
        DashboardCardItem item = new DashboardCardItem();
        item.type     = Type.CUSTOM_SENSOR;
        item.sensorId = sensorId;
        item.label    = label.isEmpty() ? sensorId : label;
        item.order    = order;
        item.visible  = true;
        return item;
    }

    /** Returns true if this item can be dragged to reorder. */
    public boolean isDraggable() {
        return type != Type.HEADER;
    }

    @NonNull
    @Override
    public String toString() {
        return "DashboardCardItem{type=" + type + ", visible=" + visible + ", order=" + order + ", sensorId='" + sensorId + "'}";
    }
}

