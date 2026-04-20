package com.opensynaptic.gsynjava.core.protocol;

import com.opensynaptic.gsynjava.data.Models;

import org.junit.Assert;
import org.junit.Test;

public class ProtocolCodecTest {
    @Test
    public void base62_roundtrip() {
        long[] values = {0, 1, 61, 62, 123456, -98765};
        for (long value : values) {
            Assert.assertEquals(value, Base62Codec.decode(Base62Codec.encode(value)));
        }
    }

    @Test
    public void timestamp_roundtrip() {
        long ts = 1712345678L;
        Assert.assertEquals(ts, Base62Codec.decodeTimestamp(Base62Codec.encodeTimestamp(ts)));
    }

    @Test
    public void packet_build_and_decode() {
        byte[] frame = PacketBuilder.buildSensorPacket(1, 7, 1712345678L, "TEMP", "Cel", 25.5);
        Assert.assertNotNull(frame);
        Models.PacketMeta meta = PacketDecoder.decode(frame);
        Assert.assertNotNull(meta);
        Assert.assertTrue(meta.crc8Ok);
        Assert.assertTrue(meta.crc16Ok);
        Assert.assertEquals(1, meta.aid);
        Assert.assertEquals(7, meta.tid);
    }

    @Test
    public void body_parse_sensor() {
        Models.BodyParseResult result = BodyParser.parseText("1.U.AABlqg4|TEMP>U.Cel:44D|HUM>U.%:52h|");
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.readings.size());
        Assert.assertEquals("TEMP", result.readings.get(0).sensorId);
    }

    @Test
    public void diff_engine_full_heart_roundtrip() {
        DiffEngine engine = new DiffEngine();
        // A FULL body with two sensors
        String fullBody = "1.U.AABlqg4|TEMP>U.Cel:44D|HUM>U.%:52h|";
        byte[] bodyBytes = fullBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Process FULL — should learn template and return the text
        String result = engine.processPacket(OsCmd.DATA_FULL, 1, 1, bodyBytes);
        Assert.assertNotNull("FULL must return body text", result);
        Assert.assertTrue(result.contains("TEMP"));
        Assert.assertTrue(result.contains("HUM"));
        Assert.assertEquals(1, engine.templateCount());

        // Process HEART with empty body — should reconstruct from template
        String heartResult = engine.processPacket(OsCmd.DATA_HEART, 1, 1, new byte[0]);
        Assert.assertNotNull("HEART must reconstruct from cache", heartResult);
        Assert.assertTrue(heartResult.contains("TEMP"));
    }

    @Test
    public void diff_engine_clear() {
        DiffEngine engine = new DiffEngine();
        byte[] body = "1.U.AABlqg4|TEMP>U.Cel:44D|".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        engine.processPacket(OsCmd.DATA_FULL, 2, 0, body);
        Assert.assertEquals(1, engine.templateCount());
        engine.clear();
        Assert.assertEquals(0, engine.templateCount());
        // HEART after clear should return null
        Assert.assertNull(engine.processPacket(OsCmd.DATA_HEART, 2, 0, new byte[0]));
    }

    @Test
    public void protocol_constants_default_unit() {
        Assert.assertEquals("\u00b0C", ProtocolConstants.defaultUnitFor("TEMP"));
        Assert.assertEquals("%RH", ProtocolConstants.defaultUnitFor("HUM"));
        Assert.assertEquals("hPa", ProtocolConstants.defaultUnitFor("PRES"));
        Assert.assertEquals("", ProtocolConstants.defaultUnitFor("UNKNOWN_XYZ"));
    }

    @Test
    public void os_cmd_is_secure() {
        Assert.assertTrue(OsCmd.isSecureCmd(OsCmd.DATA_FULL_SEC));
        Assert.assertTrue(OsCmd.isSecureCmd(OsCmd.DATA_DIFF_SEC));
        Assert.assertTrue(OsCmd.isSecureCmd(OsCmd.DATA_HEART_SEC));
        Assert.assertFalse(OsCmd.isSecureCmd(OsCmd.DATA_FULL));
        Assert.assertFalse(OsCmd.isSecureCmd(OsCmd.DATA_DIFF));
    }
}

