package com.opensynaptic.gsynjava.core.protocol;

import com.opensynaptic.gsynjava.data.Models;

public final class PacketDecoder {
    private PacketDecoder() {}

    public static Models.PacketMeta decode(byte[] packet) {
        if (packet == null || packet.length < 16) return null;
        Models.PacketMeta meta = new Models.PacketMeta();
        meta.cmd = packet[0] & 0xFF;
        meta.routeCount = packet[1] & 0xFF;
        meta.aid = ((packet[2] & 0xFF) << 24) | ((packet[3] & 0xFF) << 16) | ((packet[4] & 0xFF) << 8) | (packet[5] & 0xFF);
        meta.tid = packet[6] & 0xFF;
        meta.tsSec = ((long) (packet[9] & 0xFF) << 24) | ((long) (packet[10] & 0xFF) << 16) | ((long) (packet[11] & 0xFF) << 8) | ((long) (packet[12] & 0xFF));
        meta.bodyOffset = 13;
        meta.bodyLen = packet.length - 13 - 3;
        if (meta.bodyLen < 0) return null;
        byte[] body = new byte[meta.bodyLen];
        if (meta.bodyLen > 0) {
            System.arraycopy(packet, meta.bodyOffset, body, 0, meta.bodyLen);
        }
        int gotCrc8 = packet[packet.length - 3] & 0xFF;
        int expCrc8 = meta.bodyLen > 0 ? OsCrc.crc8(body) : 0;
        meta.crc8Ok = gotCrc8 == expCrc8;
        int gotCrc16 = ((packet[packet.length - 2] & 0xFF) << 8) | (packet[packet.length - 1] & 0xFF);
        byte[] for16 = new byte[packet.length - 2];
        System.arraycopy(packet, 0, for16, 0, packet.length - 2);
        meta.crc16Ok = gotCrc16 == OsCrc.crc16(for16);
        return meta;
    }
}

