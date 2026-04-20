package com.opensynaptic.gsynjava.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppDatabaseHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "gsyn_java.db";
    public static final int DB_VERSION = 1;

    public AppDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE devices (id INTEGER PRIMARY KEY AUTOINCREMENT, aid INTEGER UNIQUE NOT NULL, name TEXT NOT NULL DEFAULT '', type TEXT NOT NULL DEFAULT 'sensor', lat REAL DEFAULT 0.0, lng REAL DEFAULT 0.0, status TEXT NOT NULL DEFAULT 'offline', transport_type TEXT NOT NULL DEFAULT 'udp', last_seen_ms INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE sensor_data (id INTEGER PRIMARY KEY AUTOINCREMENT, device_aid INTEGER NOT NULL, sensor_id TEXT NOT NULL, unit TEXT NOT NULL DEFAULT '', value REAL NOT NULL, raw_b62 TEXT DEFAULT '', timestamp_ms INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_sensor_data_aid_ts ON sensor_data(device_aid, timestamp_ms)");
        db.execSQL("CREATE TABLE alerts (id INTEGER PRIMARY KEY AUTOINCREMENT, device_aid INTEGER NOT NULL, sensor_id TEXT NOT NULL DEFAULT '', level INTEGER NOT NULL DEFAULT 0, message TEXT NOT NULL DEFAULT '', acknowledged INTEGER NOT NULL DEFAULT 0, created_ms INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_alerts_aid_level ON alerts(device_aid, level)");
        db.execSQL("CREATE TABLE rules (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL DEFAULT '', device_aid_filter INTEGER DEFAULT NULL, sensor_id_filter TEXT DEFAULT NULL, operator TEXT NOT NULL DEFAULT '>', threshold REAL NOT NULL DEFAULT 0.0, action_type TEXT NOT NULL DEFAULT 'create_alert', action_payload TEXT NOT NULL DEFAULT '{}', enabled INTEGER NOT NULL DEFAULT 1, cooldown_ms INTEGER NOT NULL DEFAULT 60000)");
        db.execSQL("CREATE TABLE operation_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, user TEXT NOT NULL DEFAULT 'system', action TEXT NOT NULL DEFAULT '', details TEXT NOT NULL DEFAULT '', timestamp_ms INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'viewer', created_ms INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE dashboard_layout (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, layout_json TEXT NOT NULL DEFAULT '{}')");
        db.execSQL("CREATE TABLE pending_commands (id INTEGER PRIMARY KEY AUTOINCREMENT, device_aid INTEGER NOT NULL, frame_hex TEXT NOT NULL, created_ms INTEGER NOT NULL)");
        db.execSQL("INSERT INTO users(username, password_hash, role, created_ms) VALUES ('admin', '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918', 'admin', strftime('%s','now') * 1000)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No-op for v1 mirror.
    }
}

