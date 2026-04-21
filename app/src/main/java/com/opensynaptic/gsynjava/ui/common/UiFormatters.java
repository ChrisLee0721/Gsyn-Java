package com.opensynaptic.gsynjava.ui.common;

import android.text.format.DateUtils;

import com.opensynaptic.gsynjava.data.Models;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class UiFormatters {
    private static final DateFormat DATE_TIME = DateFormat.getDateTimeInstance();

    private UiFormatters() {}

    public static String formatDateTime(long ms) {
        if (ms <= 0) return "N/A";
        synchronized (DATE_TIME) {
            return DATE_TIME.format(new Date(ms));
        }
    }

    public static String formatRelativeTime(long ms) {
        if (ms <= 0) return "—";
        return DateUtils.getRelativeTimeSpanString(
                ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE).toString();
    }

    public static String formatSensorSummary(List<Models.SensorData> readings) {
        if (readings == null || readings.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Models.SensorData data : readings) {
            if (data == null) continue;
            if (count > 0) sb.append("  ·  ");
            sb.append(safe(data.sensorId).isEmpty() ? "Sensor" : data.sensorId)
                    .append(' ')
                    .append(trimNumber(data.value));
            if (!safe(data.unit).isEmpty()) sb.append(' ').append(data.unit);
            count++;
            if (count >= 3) break;
        }
        if (readings.size() > count) sb.append("  ·  +").append(readings.size() - count).append(" more");
        return sb.toString();
    }

    public static String trimNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001d) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static String upperOrFallback(String value, String fallback) {
        String safe = safe(value);
        return safe.isEmpty() ? fallback : safe.toUpperCase(Locale.ROOT);
    }
}
