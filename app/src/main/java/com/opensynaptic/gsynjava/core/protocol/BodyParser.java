package com.opensynaptic.gsynjava.core.protocol;

import com.opensynaptic.gsynjava.data.Models;

import java.nio.charset.StandardCharsets;

public final class BodyParser {
    private BodyParser() {}

    public static Models.BodyParseResult parse(byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) return null;
        return parseText(new String(bodyBytes, StandardCharsets.UTF_8));
    }

    public static Models.BodyParseResult parseText(String text) {
        if (text == null || text.isEmpty()) return null;
        int firstPipe = text.indexOf('|');
        if (firstPipe < 0) return null;
        String header = text.substring(0, firstPipe);
        String payload = text.substring(firstPipe + 1);
        int lastDot = header.lastIndexOf('.');
        if (lastDot < 0) return null;
        String tsToken = header.substring(lastDot + 1);
        String headRest = header.substring(0, lastDot);
        int firstDot = headRest.indexOf('.');
        String headerAid = firstDot < 0 ? headRest : headRest.substring(0, firstDot);
        String headerState = firstDot < 0 ? "U" : headRest.substring(firstDot + 1);

        Models.BodyParseResult result = new Models.BodyParseResult();
        result.headerAid = headerAid;
        result.headerState = headerState;
        result.tsToken = tsToken;

        for (String seg : payload.split("\\|")) {
            if (seg.isEmpty()) continue;
            Models.SensorReading reading = parseSegment(seg);
            if (reading != null) result.readings.add(reading);
        }
        return result;
    }

    private static Models.SensorReading parseSegment(String segment) {
        int gt = segment.indexOf('>');
        if (gt <= 0) return null;
        String sensorId = segment.substring(0, gt);
        String rest = segment.substring(gt + 1);
        int dot = rest.indexOf('.');
        int colon = rest.indexOf(':');
        if (dot < 0 || colon < 0 || colon <= dot) return null;
        String state = rest.substring(0, dot);
        String unit = rest.substring(dot + 1, colon);
        String b62 = rest.substring(colon + 1);
        for (String marker : new String[] {"#", "!", "@"}) {
            int idx = b62.indexOf(marker);
            if (idx >= 0) {
                b62 = b62.substring(0, idx);
            }
        }
        Models.SensorReading reading = new Models.SensorReading();
        reading.sensorId = sensorId;
        reading.state = state;
        reading.unit = unit;
        reading.rawB62 = b62;
        reading.value = Base62Codec.decodeValue(b62);
        return reading;
    }
}

