package com.opensynaptic.gsynjava.core.protocol;

/**
 * Decodes a standard Geohash string (base32) into WGS-84 lat/lng.
 * Compatible with any firmware that encodes location as a Geohash sensor reading.
 *
 * Sensor ID convention:  GEO  /  GEOHASH  /  GEO1  etc.
 * The rawB62 field of SensorReading carries the geohash string as-is.
 */
public final class GeohashDecoder {
    private GeohashDecoder() {}

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    public static double[] decode(String geohash) {
        if (geohash == null || geohash.isEmpty()) return null;
        geohash = geohash.trim().toLowerCase();

        double minLat = -90, maxLat = 90;
        double minLng = -180, maxLng = 180;
        boolean isLng = true;

        for (char c : geohash.toCharArray()) {
            int idx = BASE32.indexOf(c);
            if (idx < 0) return null; // invalid char
            for (int bits = 4; bits >= 0; bits--) {
                int bit = (idx >> bits) & 1;
                if (isLng) {
                    double mid = (minLng + maxLng) / 2;
                    if (bit == 1) minLng = mid; else maxLng = mid;
                } else {
                    double mid = (minLat + maxLat) / 2;
                    if (bit == 1) minLat = mid; else maxLat = mid;
                }
                isLng = !isLng;
            }
        }
        return new double[]{(minLat + maxLat) / 2, (minLng + maxLng) / 2};
    }

    /** Returns true if sensorId looks like a geohash location sensor. */
    public static boolean isGeoSensor(String sensorId) {
        if (sensorId == null) return false;
        String up = sensorId.toUpperCase(java.util.Locale.ROOT);
        return up.equals("GEO") || up.startsWith("GEOHASH") || up.startsWith("GEO");
    }
}

