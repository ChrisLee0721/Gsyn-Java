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
}

