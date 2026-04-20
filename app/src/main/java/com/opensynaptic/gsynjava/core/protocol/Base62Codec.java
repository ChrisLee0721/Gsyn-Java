package com.opensynaptic.gsynjava.core.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base62Codec {
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final int VALUE_SCALE = 10000;

    private Base62Codec() {}

    public static String encode(long value) {
        if (value == 0) return "0";
        boolean negative = value < 0;
        long n = negative ? -value : value;
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            sb.append(ALPHABET.charAt((int) (n % 62)));
            n /= 62;
        }
        if (negative) sb.append('-');
        return sb.reverse().toString();
    }

    public static long decode(String value) {
        if (value == null || value.isEmpty()) return 0;
        boolean negative = value.charAt(0) == '-';
        int start = negative ? 1 : 0;
        long result = 0;
        for (int i = start; i < value.length(); i++) {
            int idx = ALPHABET.indexOf(value.charAt(i));
            if (idx < 0) return 0;
            result = result * 62 + idx;
        }
        return negative ? -result : result;
    }

    public static String encodeValue(double sensorValue) {
        return encode(Math.round(sensorValue * VALUE_SCALE));
    }

    public static double decodeValue(String b62) {
        return decode(b62) / (double) VALUE_SCALE;
    }

    public static String encodeTimestamp(long tsSec) {
        byte[] bytes = new byte[] {
                0, 0,
                (byte) ((tsSec >> 24) & 0xFF),
                (byte) ((tsSec >> 16) & 0xFF),
                (byte) ((tsSec >> 8) & 0xFF),
                (byte) (tsSec & 0xFF)
        };
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static long decodeTimestamp(String token) {
        try {
            String padded = token;
            while (padded.length() % 4 != 0) padded += "=";
            byte[] bytes = Base64.getUrlDecoder().decode(padded.getBytes(StandardCharsets.UTF_8));
            if (bytes.length < 6) return 0;
            return ((long) (bytes[2] & 0xFF) << 24)
                    | ((long) (bytes[3] & 0xFF) << 16)
                    | ((long) (bytes[4] & 0xFF) << 8)
                    | ((long) (bytes[5] & 0xFF));
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }
}

