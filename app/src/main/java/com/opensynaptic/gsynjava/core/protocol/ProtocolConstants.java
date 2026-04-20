package com.opensynaptic.gsynjava.core.protocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenSynaptic protocol constants — wire-compatible unit codes, sensor IDs, states.
 * All values must match what firmware/OSynaptic-TX sends.
 * Java mirror of lib/core/protocol_constants.dart
 */
public final class ProtocolConstants {
    private ProtocolConstants() {}

    /**
     * Sensor body format: {sid}>{state}.{unit}:{b62}
     * State codes used in sensor segments.
     * U=Normal, A=Alert, W=Warning, D=Danger, O=Offline, E=Error
     */
    public static final List<String> OS_STATES = Collections.unmodifiableList(
            Arrays.asList("U", "A", "W", "D", "O", "E")
    );

    /**
     * Node/header state codes.
     * U=Unknown, A=Active, W=Warning, D=Danger, O=Offline, E=Error, S=Sleep, I=Idle
     */
    public static final List<String> OS_NODE_STATES = Collections.unmodifiableList(
            Arrays.asList("U", "A", "W", "D", "O", "E", "S", "I")
    );

    /**
     * Standard unit strings — must match firmware unit field exactly.
     */
    public static final List<String> OS_UNITS = Collections.unmodifiableList(Arrays.asList(
            // Temperature
            "\u00b0C", "\u00b0F", "K",
            // Humidity
            "%", "%RH",
            // Pressure
            "hPa", "kPa", "Pa", "bar", "psi",
            // Voltage
            "V", "mV",
            // Current
            "A", "mA",
            // Power / Energy
            "W", "kW", "Wh", "kWh",
            // Distance / Level
            "mm", "cm", "m",
            // Volume / Flow
            "L", "mL", "m3/h",
            // Light
            "lux", "klux",
            // Gas / Air quality
            "ppm", "ppb",
            // Speed / Rotation
            "rpm", "m/s", "km/h", "rad/s",
            // Mass
            "kg", "g",
            // Sound
            "dB",
            // Frequency
            "Hz", "kHz",
            // Digital / Logic
            "bool", "cnt", "raw", "unit"
    ));

    /**
     * Standard sensor ID prefixes used by OpenSynaptic nodes.
     */
    public static final List<String> OS_SENSOR_IDS = Collections.unmodifiableList(Arrays.asList(
            // Temperature
            "TEMP", "T1", "T2", "T3", "TMP",
            // Humidity
            "HUM", "H1", "H2", "RH",
            // Pressure
            "PRES", "P1", "BAR",
            // Level / Distance
            "LVL", "L1", "LEVEL", "DIST", "D1",
            // Voltage
            "VOLT", "V1", "VBAT", "VCC",
            // Current
            "CURR", "I1", "IBAT",
            // Power
            "POWER", "PW1",
            // Light
            "LUX", "LIGHT",
            // Gas
            "CO2", "GAS", "PPM", "VOC",
            // Rotation / Speed
            "RPM", "SPEED",
            // Weight
            "WEIGHT", "W1",
            // Sound
            "NOISE", "DB1",
            // Counter / Status / Boolean
            "COUNT", "CNT", "STATUS", "ST1", "BOOL", "B1",
            // Location (Geohash)
            "GEO", "GEOHASH", "GEO1", "LOCATION"
    ));

    /**
     * Maps common sensor ID prefix → default unit string.
     */
    public static final Map<String, String> OS_SENSOR_DEFAULT_UNIT;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("TEMP",   "\u00b0C");
        m.put("T1",     "\u00b0C");
        m.put("T2",     "\u00b0C");
        m.put("T3",     "\u00b0C");
        m.put("TMP",    "\u00b0C");
        m.put("HUM",    "%RH");
        m.put("H1",     "%RH");
        m.put("H2",     "%RH");
        m.put("RH",     "%RH");
        m.put("PRES",   "hPa");
        m.put("P1",     "hPa");
        m.put("BAR",    "hPa");
        m.put("LVL",    "mm");
        m.put("L1",     "mm");
        m.put("LEVEL",  "mm");
        m.put("DIST",   "cm");
        m.put("D1",     "cm");
        m.put("VOLT",   "V");
        m.put("V1",     "V");
        m.put("VBAT",   "mV");
        m.put("VCC",    "mV");
        m.put("CURR",   "mA");
        m.put("I1",     "mA");
        m.put("IBAT",   "mA");
        m.put("POWER",  "W");
        m.put("PW1",    "W");
        m.put("LUX",    "lux");
        m.put("LIGHT",  "lux");
        m.put("CO2",    "ppm");
        m.put("GAS",    "ppm");
        m.put("PPM",    "ppm");
        m.put("VOC",    "ppb");
        m.put("RPM",    "rpm");
        m.put("SPEED",  "m/s");
        m.put("WEIGHT", "kg");
        m.put("W1",     "kg");
        m.put("NOISE",  "dB");
        m.put("DB1",    "dB");
        m.put("COUNT",  "cnt");
        m.put("CNT",    "cnt");
        m.put("STATUS", "raw");
        m.put("ST1",    "raw");
        m.put("BOOL",   "bool");
        m.put("B1",     "bool");
        OS_SENSOR_DEFAULT_UNIT = Collections.unmodifiableMap(m);
    }

    /**
     * Look up the default unit for a sensor ID prefix (case-insensitive).
     * Returns empty string if no default is known.
     */
    public static String defaultUnitFor(String sensorId) {
        if (sensorId == null || sensorId.isEmpty()) return "";
        String upper = sensorId.toUpperCase(java.util.Locale.ROOT);
        String direct = OS_SENSOR_DEFAULT_UNIT.get(upper);
        if (direct != null) return direct;
        // Try prefix match against known IDs
        for (String key : OS_SENSOR_DEFAULT_UNIT.keySet()) {
            if (upper.startsWith(key)) return OS_SENSOR_DEFAULT_UNIT.get(key);
        }
        return "";
    }
}

