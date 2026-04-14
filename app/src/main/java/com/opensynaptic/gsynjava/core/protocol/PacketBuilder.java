package com.opensynaptic.gsynjava.core.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class PacketBuilder {
    private PacketBuilder() {}

    public static byte[] buildPacket(int cmd, int aid, int tid, long tsSec, byte[] body) {
        if (body == null) body = new byte[0];
        int frameLen = 13 + body.length + 3;
        if (frameLen > 512) return null;
        byte[] out = new byte[frameLen];
        int off = 0;
        out[off++] = (byte) cmd;
        out[off++] = 1;
        out[off++] = (byte) ((aid >> 24) & 0xFF);
        out[off++] = (byte) ((aid >> 16) & 0xFF);
        out[off++] = (byte) ((aid >> 8) & 0xFF);
        out[off++] = (byte) (aid & 0xFF);
        out[off++] = (byte) tid;
        out[off++] = 0;
        out[off++] = 0;
        out[off++] = (byte) ((tsSec >> 24) & 0xFF);
        out[off++] = (byte) ((tsSec >> 16) & 0xFF);
        out[off++] = (byte) ((tsSec >> 8) & 0xFF);
        out[off++] = (byte) (tsSec & 0xFF);
        System.arraycopy(body, 0, out, off, body.length);
        off += body.length;
        out[off++] = (byte) OsCrc.crc8(body);
        byte[] for16 = new byte[off];
        System.arraycopy(out, 0, for16, 0, off);
        int crc16 = OsCrc.crc16(for16);
        out[off++] = (byte) ((crc16 >> 8) & 0xFF);
        out[off] = (byte) (crc16 & 0xFF);
        return out;
    }

    public static byte[] buildSensorPacket(int aid, int tid, long tsSec, String sensorId, String unit, double value) {
        String body = aid + ".U." + Base62Codec.encodeTimestamp(tsSec) + "|" + sensorId + ">U." + unit + ":" + Base62Codec.encodeValue(value) + "|";
        return buildPacket(OsCmd.DATA_FULL, aid, tid, tsSec, body.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] buildMultiSensorPacket(int aid, int tid, long tsSec, String nodeId, String nodeState, List<Map<String, Object>> sensors) {
        StringBuilder sb = new StringBuilder();
        sb.append(nodeId).append('.').append(nodeState).append('.').append(Base62Codec.encodeTimestamp(tsSec)).append('|');
        for (Map<String, Object> sensor : sensors) {
            String sid = String.valueOf(sensor.get("sensor_id"));
            String unit = String.valueOf(sensor.get("unit"));
            String state = String.valueOf(sensor.getOrDefault("state", "U"));
            double value = ((Number) sensor.get("value")).doubleValue();
            sb.append(sid).append('>').append(state).append('.').append(unit).append(':').append(Base62Codec.encodeValue(value)).append('|');
        }
        return buildPacket(OsCmd.DATA_FULL, aid, tid, tsSec, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] buildPing(int seq) {
        return new byte[] {(byte) OsCmd.PING, (byte) ((seq >> 8) & 0xFF), (byte) (seq & 0xFF)};
    }

    public static byte[] buildPong(int seq) {
        return new byte[] {(byte) OsCmd.PONG, (byte) ((seq >> 8) & 0xFF), (byte) (seq & 0xFF)};
    }

    public static byte[] buildIdRequest(int seq) {
        return new byte[] {(byte) OsCmd.ID_REQUEST, (byte) ((seq >> 8) & 0xFF), (byte) (seq & 0xFF)};
    }

    public static byte[] buildTimeRequest(int seq) {
        return new byte[] {(byte) OsCmd.TIME_REQUEST, (byte) ((seq >> 8) & 0xFF), (byte) (seq & 0xFF)};
    }

    public static byte[] buildIdAssign(int aid) {
        return new byte[] {(byte) OsCmd.ID_ASSIGN, (byte) ((aid >> 24) & 0xFF), (byte) ((aid >> 16) & 0xFF), (byte) ((aid >> 8) & 0xFF), (byte) (aid & 0xFF)};
    }

    public static byte[] buildRawHex(String hex) {
        String cleaned = hex.replaceAll("\\s+", "");
        if (cleaned.isEmpty() || cleaned.length() % 2 != 0) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < cleaned.length(); i += 2) {
            out.write(Integer.parseInt(cleaned.substring(i, i + 2), 16));
        }
        return out.toByteArray();
    }
}

