package com.opensynaptic.gsynjava.data;

import java.util.ArrayList;
import java.util.List;

public final class Models {
    private Models() {}

    public static class Device {
        public long id;
        public int aid;
        public String name = "";
        public String type = "sensor";
        public double lat;
        public double lng;
        public String status = "offline";
        public String transportType = "udp";
        public long lastSeenMs;
    }

    public static class SensorData {
        public long id;
        public int deviceAid;
        public String sensorId = "";
        public String unit = "";
        public double value;
        public String rawB62 = "";
        public long timestampMs;
    }

    public static class AlertItem {
        public long id;
        public int deviceAid;
        public String sensorId = "";
        public int level;
        public String message = "";
        public boolean acknowledged;
        public long createdMs;
    }

    public static class Rule {
        public long id;
        public String name = "";
        public Integer deviceAidFilter;
        public String sensorIdFilter;
        public String operator = ">";
        public double threshold;
        public String actionType = "create_alert";
        public String actionPayload = "{}";
        public boolean enabled = true;
        public long cooldownMs = 60000L;

        public boolean evaluate(double sensorValue) {
            switch (operator) {
                case ">": return sensorValue > threshold;
                case "<": return sensorValue < threshold;
                case ">=": return sensorValue >= threshold;
                case "<=": return sensorValue <= threshold;
                case "==": return sensorValue == threshold;
                case "!=": return sensorValue != threshold;
                default: return false;
            }
        }
    }

    public static class OperationLog {
        public long id;
        public String user = "system";
        public String action = "";
        public String details = "";
        public long timestampMs;
    }

    public static class SensorReading {
        public String sensorId = "";
        public String unit = "";
        public double value;
        public String state = "U";
        public String rawB62 = "";
    }

    public static class DeviceMessage {
        public int cmd;
        public int deviceAid;
        public int tid;
        public long timestampSec;
        public String nodeId = "";
        public String nodeState = "U";
        public String transportType = "udp";
        public List<SensorReading> readings = new ArrayList<>();
        public byte[] rawFrame;
    }

    public static class PacketMeta {
        public int cmd;
        public int routeCount;
        public int aid;
        public int tid;
        public long tsSec;
        public int bodyOffset;
        public int bodyLen;
        public boolean crc8Ok;
        public boolean crc16Ok;
    }

    public static class BodyParseResult {
        public String headerAid = "";
        public String headerState = "U";
        public String tsToken = "";
        public List<SensorReading> readings = new ArrayList<>();
    }

    public static class TransportStats {
        public boolean udpConnected;
        public boolean mqttConnected;
        public int messagesPerSecond;
        public int totalMessages;
    }
}

