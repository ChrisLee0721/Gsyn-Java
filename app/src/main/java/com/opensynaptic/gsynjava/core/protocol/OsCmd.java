package com.opensynaptic.gsynjava.core.protocol;

public final class OsCmd {
    public static final int DATA_FULL = 63;
    public static final int DATA_FULL_SEC = 64;
    public static final int DATA_DIFF = 170;
    public static final int DATA_DIFF_SEC = 171;
    public static final int DATA_HEART = 127;
    public static final int DATA_HEART_SEC = 128;

    public static final int ID_REQUEST = 1;
    public static final int ID_ASSIGN = 2;
    public static final int ID_POOL_REQ = 3;
    public static final int ID_POOL_RES = 4;
    public static final int HANDSHAKE_ACK = 5;
    public static final int HANDSHAKE_NACK = 6;
    public static final int PING = 9;
    public static final int PONG = 10;
    public static final int TIME_REQUEST = 11;
    public static final int TIME_RESPONSE = 12;
    public static final int SECURE_DICT_READY = 13;
    public static final int SECURE_CHANNEL_ACK = 14;

    private OsCmd() {}

    public static int normalizeDataCmd(int cmd) {
        if (cmd == DATA_FULL_SEC) return DATA_FULL;
        if (cmd == DATA_DIFF_SEC) return DATA_DIFF;
        if (cmd == DATA_HEART_SEC) return DATA_HEART;
        return cmd;
    }

    public static boolean isDataCmd(int cmd) {
        return cmd == DATA_FULL || cmd == DATA_FULL_SEC || cmd == DATA_DIFF || cmd == DATA_DIFF_SEC || cmd == DATA_HEART || cmd == DATA_HEART_SEC;
    }

    /** Returns true if cmd is a secure (encrypted) variant. */
    public static boolean isSecureCmd(int cmd) {
        return cmd == DATA_FULL_SEC || cmd == DATA_DIFF_SEC || cmd == DATA_HEART_SEC;
    }

    public static String nameOf(int cmd) {
        switch (cmd) {
            case DATA_FULL: return "DATA_FULL";
            case DATA_FULL_SEC: return "DATA_FULL_SEC";
            case DATA_DIFF: return "DATA_DIFF";
            case DATA_DIFF_SEC: return "DATA_DIFF_SEC";
            case DATA_HEART: return "DATA_HEART";
            case DATA_HEART_SEC: return "DATA_HEART_SEC";
            case ID_REQUEST: return "ID_REQUEST";
            case ID_ASSIGN: return "ID_ASSIGN";
            case ID_POOL_REQ: return "ID_POOL_REQ";
            case ID_POOL_RES: return "ID_POOL_RES";
            case HANDSHAKE_ACK: return "HANDSHAKE_ACK";
            case HANDSHAKE_NACK: return "HANDSHAKE_NACK";
            case PING: return "PING";
            case PONG: return "PONG";
            case TIME_REQUEST: return "TIME_REQUEST";
            case TIME_RESPONSE: return "TIME_RESPONSE";
            case SECURE_DICT_READY: return "SECURE_DICT_READY";
            case SECURE_CHANNEL_ACK: return "SECURE_CHANNEL_ACK";
            default: return "UNKNOWN(" + cmd + ")";
        }
    }
}

