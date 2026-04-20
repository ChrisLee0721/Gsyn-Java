package com.opensynaptic.gsynjava.ui.common;

import com.opensynaptic.gsynjava.data.Models;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class UiFormatters {
    private static final DateFormat DATE_TIME = DateFormat.getDateTimeInstance();

    private UiFormatters() {}

    public static String formatDateTime(long ms) {
        if (ms <= 0) return "暂无时间";
        synchronized (DATE_TIME) {
            return DATE_TIME.format(new Date(ms));
        }
    }

    public static String formatRelativeTime(long ms) {
        if (ms <= 0) return "从未上报";
        long diff = Math.max(0L, System.currentTimeMillis() - ms);
        long minutes = diff / 60_000L;
        long hours = diff / 3_600_000L;
        long days = diff / 86_400_000L;
        if (diff < 60_000L) return "刚刚更新";
        if (minutes < 60) return minutes + " 分钟前";
        if (hours < 24) return hours + " 小时前";
        return days + " 天前";
    }

    public static String formatSensorSummary(List<Models.SensorData> readings) {
        if (readings == null || readings.isEmpty()) return "暂无最近读数";
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

