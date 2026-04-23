package com.opensynaptic.gsynjava.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppRepository {
    private static AppRepository instance;
    private final Context context;
    private final AppDatabaseHelper dbHelper;

    private AppRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new AppDatabaseHelper(this.context);
    }

    public static synchronized AppRepository get(Context context) {
        if (instance == null) instance = new AppRepository(context);
        return instance;
    }

    public synchronized void upsertDevice(Models.Device device) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query("devices", new String[] {"id","lat","lng"}, "aid=?", new String[] {String.valueOf(device.aid)}, null, null, null);
        ContentValues values = new ContentValues();
        values.put("aid", device.aid);
        values.put("name", valueOrEmpty(device.name));
        values.put("type", valueOrEmpty(device.type, "sensor"));
        values.put("status", valueOrEmpty(device.status, "offline"));
        values.put("transport_type", valueOrEmpty(device.transportType, "udp"));
        values.put("last_seen_ms", device.lastSeenMs);
        try {
            if (c.moveToFirst()) {
                // Preserve existing coordinates if the new message carries no location
                double existingLat = c.getDouble(c.getColumnIndexOrThrow("lat"));
                double existingLng = c.getDouble(c.getColumnIndexOrThrow("lng"));
                boolean hasNewCoords = Math.abs(device.lat) > 1e-7 || Math.abs(device.lng) > 1e-7;
                values.put("lat", hasNewCoords ? device.lat : existingLat);
                values.put("lng", hasNewCoords ? device.lng : existingLng);
                db.update("devices", values, "aid=?", new String[] {String.valueOf(device.aid)});
            } else {
                values.put("lat", device.lat);
                values.put("lng", device.lng);
                db.insert("devices", null, values);
            }
        } finally {
            c.close();
        }
    }

    public synchronized List<Models.Device> getAllDevices() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("devices", null, null, null, null, null, "last_seen_ms DESC");
        try {
            List<Models.Device> items = new ArrayList<>();
            while (c.moveToNext()) items.add(mapDevice(c));
            return items;
        } finally {
            c.close();
        }
    }

    public synchronized int getTotalDeviceCount() {
        return singleInt("SELECT COUNT(*) FROM devices");
    }

    public synchronized int getOnlineDeviceCount() {
        long cutoff = System.currentTimeMillis() - 5 * 60_000L;
        return singleInt("SELECT COUNT(*) FROM devices WHERE last_seen_ms > ?", String.valueOf(cutoff));
    }

    public synchronized void insertSensorData(Models.SensorData data) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("device_aid", data.deviceAid);
        values.put("sensor_id", data.sensorId);
        values.put("unit", data.unit);
        values.put("value", data.value);
        values.put("raw_b62", data.rawB62);
        values.put("timestamp_ms", data.timestampMs);
        db.insert("sensor_data", null, values);
    }

    public synchronized List<Models.SensorData> getLatestReadingsByDevice(int aid) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM sensor_data WHERE id IN (SELECT MAX(id) FROM sensor_data WHERE device_aid = ? GROUP BY sensor_id)", new String[] {String.valueOf(aid)});
        try {
            List<Models.SensorData> items = new ArrayList<>();
            while (c.moveToNext()) items.add(mapSensorData(c));
            return items;
        } finally {
            c.close();
        }
    }

    /** Batch version: returns latest readings for ALL devices in one query — avoids N+1. */
    public synchronized Map<Integer, List<Models.SensorData>> getAllLatestReadings() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM sensor_data WHERE id IN "
                + "(SELECT MAX(id) FROM sensor_data GROUP BY device_aid, sensor_id)", null);
        try {
            Map<Integer, List<Models.SensorData>> map = new HashMap<>();
            while (c.moveToNext()) {
                Models.SensorData d = mapSensorData(c);
                if (!map.containsKey(d.deviceAid)) map.put(d.deviceAid, new ArrayList<>());
                map.get(d.deviceAid).add(d);
            }
            return map;
        } finally {
            c.close();
        }
    }

    public synchronized List<Models.SensorData> querySensorData(long fromMs, long toMs, int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("sensor_data", null, "timestamp_ms >= ? AND timestamp_ms <= ?", new String[] {String.valueOf(fromMs), String.valueOf(toMs)}, null, null, "timestamp_ms DESC", String.valueOf(limit));
        try {
            List<Models.SensorData> items = new ArrayList<>();
            while (c.moveToNext()) items.add(mapSensorData(c));
            return items;
        } finally {
            c.close();
        }
    }

    public synchronized long insertAlert(Models.AlertItem alert) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("device_aid", alert.deviceAid);
        values.put("sensor_id", alert.sensorId);
        values.put("level", alert.level);
        values.put("message", alert.message);
        values.put("acknowledged", alert.acknowledged ? 1 : 0);
        values.put("created_ms", alert.createdMs);
        return db.insert("alerts", null, values);
    }

    public synchronized List<Models.AlertItem> getAlerts(Integer level, int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("alerts", null, level == null ? null : "level=?", level == null ? null : new String[] {String.valueOf(level)}, null, null, "created_ms DESC", String.valueOf(limit));
        try {
            List<Models.AlertItem> items = new ArrayList<>();
            while (c.moveToNext()) items.add(mapAlert(c));
            return items;
        } finally {
            c.close();
        }
    }

    public synchronized int getUnacknowledgedAlertCount() {
        return singleInt("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0");
    }

    public synchronized void acknowledgeAlert(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("acknowledged", 1);
        db.update("alerts", values, "id=?", new String[] {String.valueOf(id)});
    }

    public synchronized int acknowledgeAllAlerts() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("acknowledged", 1);
        return db.update("alerts", values, "acknowledged=0", null);
    }

    public synchronized int deleteAlert(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete("alerts", "id=?", new String[] {String.valueOf(id)});
    }

    public synchronized int deleteAcknowledgedAlerts() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete("alerts", "acknowledged=1", null);
    }

    /** Returns int[3]: [criticalCount, warningCount, infoCount] in a single query. */
    public synchronized int[] getAlertCountsByLevel() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT level, COUNT(*) FROM alerts GROUP BY level", null);
        int[] counts = new int[3];
        try {
            while (c.moveToNext()) {
                int lvl = c.getInt(0);
                int cnt = c.getInt(1);
                if (lvl >= 0 && lvl <= 2) counts[lvl] = cnt;
            }
        } finally {
            c.close();
        }
        return counts;
    }

    public synchronized List<Models.Rule> getAllRules() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("rules", null, null, null, null, null, "id ASC");
        try {
            List<Models.Rule> items = new ArrayList<>();
            while (c.moveToNext()) items.add(mapRule(c));
            return items;
        } finally {
            c.close();
        }
    }

    public synchronized List<Models.Rule> getEnabledRules() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("rules", null, "enabled=1", null, null, null, "id ASC");
        try {
            List<Models.Rule> items = new ArrayList<>();
            while (c.moveToNext()) items.add(mapRule(c));
            return items;
        } finally {
            c.close();
        }
    }

    public synchronized long saveRule(Models.Rule rule) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", rule.name);
        if (rule.deviceAidFilter == null) values.putNull("device_aid_filter"); else values.put("device_aid_filter", rule.deviceAidFilter);
        if (rule.sensorIdFilter == null) values.putNull("sensor_id_filter"); else values.put("sensor_id_filter", rule.sensorIdFilter);
        values.put("operator", rule.operator);
        values.put("threshold", rule.threshold);
        values.put("action_type", rule.actionType);
        values.put("action_payload", rule.actionPayload);
        values.put("enabled", rule.enabled ? 1 : 0);
        values.put("cooldown_ms", rule.cooldownMs);
        if (rule.id > 0) {
            db.update("rules", values, "id=?", new String[] {String.valueOf(rule.id)});
            return rule.id;
        }
        return db.insert("rules", null, values);
    }

    public synchronized void toggleRule(long ruleId, boolean enabled) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        db.update("rules", values, "id=?", new String[] {String.valueOf(ruleId)});
    }

    public synchronized void deleteRule(long ruleId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("rules", "id=?", new String[] {String.valueOf(ruleId)});
    }

    public synchronized void seedDefaultRuleIfEmpty() {
        if (!getAllRules().isEmpty()) return;
        Models.Rule rule = new Models.Rule();
        rule.name = "TEMP > 50 create_alert";
        rule.sensorIdFilter = "TEMP";
        rule.operator = ">";
        rule.threshold = 50;
        rule.actionType = "create_alert";
        saveRule(rule);
        logOperation("SEED_RULE", "Created default TEMP threshold rule");
    }

    public synchronized void logOperation(String action, String details) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("user", "system");
        values.put("action", action);
        values.put("details", details == null ? "" : details);
        values.put("timestamp_ms", System.currentTimeMillis());
        db.insert("operation_logs", null, values);
    }

    public synchronized List<Models.OperationLog> getOperationLogs(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("operation_logs", null, null, null, null, null, "timestamp_ms DESC", String.valueOf(limit));
        try {
            List<Models.OperationLog> items = new ArrayList<>();
            while (c.moveToNext()) items.add(mapLog(c));
            return items;
        } finally {
            c.close();
        }
    }

    public synchronized long getDatabaseSizeBytes() {
        return context.getDatabasePath(AppDatabaseHelper.DB_NAME).length();
    }

    public synchronized int pruneOldData(int retentionDays) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 3600L * 1000L;
        return db.delete("sensor_data", "timestamp_ms < ?", new String[] {String.valueOf(cutoff)});
    }

    public synchronized File exportHistoryCsv() throws IOException {
        long now = System.currentTimeMillis();
        List<Models.SensorData> rows = querySensorData(now - 24L * 3600L * 1000L, now, 500);
        return exportHistoryCsv(rows);
    }

    public synchronized File exportHistoryCsv(long fromMs, long toMs, int limit) throws IOException {
        List<Models.SensorData> rows = querySensorData(fromMs, toMs, limit);
        return exportHistoryCsv(rows);
    }

    private File exportHistoryCsv(List<Models.SensorData> rows) throws IOException {
        long now = System.currentTimeMillis();
        File file = new File(context.getExternalFilesDir(null), "export_" + now + ".csv");
        DateFormat df = DateFormat.getDateTimeInstance();
        try (FileWriter writer = new FileWriter(file)) {
            writer.append("datetime,timestamp_ms,device_aid,sensor_id,value,unit\n");
            for (Models.SensorData item : rows) {
                writer.append(df.format(new Date(item.timestampMs))).append(',')
                        .append(String.valueOf(item.timestampMs)).append(',')
                        .append(String.valueOf(item.deviceAid)).append(',')
                        .append(item.sensorId).append(',')
                        .append(String.valueOf(item.value)).append(',')
                        .append(item.unit).append('\n');
            }
        }
        return file;
    }

    private int singleInt(String sql, String... args) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(sql, args);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static Models.Device mapDevice(Cursor c) {
        Models.Device d = new Models.Device();
        d.id = c.getLong(c.getColumnIndexOrThrow("id"));
        d.aid = c.getInt(c.getColumnIndexOrThrow("aid"));
        d.name = c.getString(c.getColumnIndexOrThrow("name"));
        d.type = c.getString(c.getColumnIndexOrThrow("type"));
        d.lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
        d.lng = c.getDouble(c.getColumnIndexOrThrow("lng"));
        d.status = c.getString(c.getColumnIndexOrThrow("status"));
        d.transportType = c.getString(c.getColumnIndexOrThrow("transport_type"));
        d.lastSeenMs = c.getLong(c.getColumnIndexOrThrow("last_seen_ms"));
        return d;
    }

    private static Models.SensorData mapSensorData(Cursor c) {
        Models.SensorData d = new Models.SensorData();
        d.id = c.getLong(c.getColumnIndexOrThrow("id"));
        d.deviceAid = c.getInt(c.getColumnIndexOrThrow("device_aid"));
        d.sensorId = c.getString(c.getColumnIndexOrThrow("sensor_id"));
        d.unit = c.getString(c.getColumnIndexOrThrow("unit"));
        d.value = c.getDouble(c.getColumnIndexOrThrow("value"));
        d.rawB62 = c.getString(c.getColumnIndexOrThrow("raw_b62"));
        d.timestampMs = c.getLong(c.getColumnIndexOrThrow("timestamp_ms"));
        return d;
    }

    private static Models.AlertItem mapAlert(Cursor c) {
        Models.AlertItem a = new Models.AlertItem();
        a.id = c.getLong(c.getColumnIndexOrThrow("id"));
        a.deviceAid = c.getInt(c.getColumnIndexOrThrow("device_aid"));
        a.sensorId = c.getString(c.getColumnIndexOrThrow("sensor_id"));
        a.level = c.getInt(c.getColumnIndexOrThrow("level"));
        a.message = c.getString(c.getColumnIndexOrThrow("message"));
        a.acknowledged = c.getInt(c.getColumnIndexOrThrow("acknowledged")) == 1;
        a.createdMs = c.getLong(c.getColumnIndexOrThrow("created_ms"));
        return a;
    }

    private static Models.Rule mapRule(Cursor c) {
        Models.Rule rule = new Models.Rule();
        rule.id = c.getLong(c.getColumnIndexOrThrow("id"));
        rule.name = c.getString(c.getColumnIndexOrThrow("name"));
        int aidIndex = c.getColumnIndexOrThrow("device_aid_filter");
        rule.deviceAidFilter = c.isNull(aidIndex) ? null : c.getInt(aidIndex);
        int sensorIndex = c.getColumnIndexOrThrow("sensor_id_filter");
        rule.sensorIdFilter = c.isNull(sensorIndex) ? null : c.getString(sensorIndex);
        rule.operator = c.getString(c.getColumnIndexOrThrow("operator"));
        rule.threshold = c.getDouble(c.getColumnIndexOrThrow("threshold"));
        rule.actionType = c.getString(c.getColumnIndexOrThrow("action_type"));
        rule.actionPayload = c.getString(c.getColumnIndexOrThrow("action_payload"));
        rule.enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1;
        rule.cooldownMs = c.getLong(c.getColumnIndexOrThrow("cooldown_ms"));
        return rule;
    }

    private static Models.OperationLog mapLog(Cursor c) {
        Models.OperationLog log = new Models.OperationLog();
        log.id = c.getLong(c.getColumnIndexOrThrow("id"));
        log.user = c.getString(c.getColumnIndexOrThrow("user"));
        log.action = c.getString(c.getColumnIndexOrThrow("action"));
        log.details = c.getString(c.getColumnIndexOrThrow("details"));
        log.timestampMs = c.getLong(c.getColumnIndexOrThrow("timestamp_ms"));
        return log;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String valueOrEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}

