package com.opensynaptic.gsynjava.core.protocol;

public final class OsCrc {
    private OsCrc() {}

    public static int crc8(byte[] data) {
        int crc = 0x00;
        for (byte b : data) {
            crc = (crc ^ (b & 0xFF)) & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x80) != 0) {
                    crc = ((crc << 1) ^ 0x07) & 0xFF;
                } else {
                    crc = (crc << 1) & 0xFF;
                }
            }
        }
        return crc & 0xFF;
    }

    public static int crc16(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc = (crc ^ ((b & 0xFF) << 8)) & 0xFFFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }
        return crc & 0xFFFF;
    }
}

