package com.opensynaptic.gsynjava.core.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenSynaptic DIFF/HEART template engine.
 * Java mirror of lib/protocol/codec/diff_engine.dart
 *
 * <p>Handles three packet types:
 * <ul>
 *   <li>DATA_FULL  — learn template + cache values, return body text</li>
 *   <li>DATA_HEART — reconstruct from cached template + values (no payload changes)</li>
 *   <li>DATA_DIFF  — read bitmask, update changed slots, reconstruct</li>
 * </ul>
 */
public class DiffEngine {

    // ------- inner template state -------

    private static final class TemplateState {
        /** Signature with {TS} and \x01 placeholders for each value slot. */
        String signature;
        /** Cached binary value slots (UTF-8 bytes). */
        List<byte[]> valsBin;

        TemplateState(String signature, List<byte[]> valsBin) {
            this.signature = signature;
            this.valsBin = valsBin;
        }
    }

    /** cache[aidStr][tidStr] → TemplateState */
    private final Map<String, Map<String, TemplateState>> cache = new HashMap<>();

    // ------- public API -------

    /**
     * Process a complete raw packet body and return the decoded full body text.
     *
     * @param cmd   packet command byte
     * @param aid   source device ID
     * @param tid   transaction/template ID
     * @param body  raw body bytes (already sliced from the packet)
     * @return decoded body text, or {@code null} on error / cache miss
     */
    public String processPacket(int cmd, int aid, int tid, byte[] body) {
        int baseCmd = OsCmd.normalizeDataCmd(cmd);
        String aidStr = String.valueOf(aid);
        String tidStr = String.format("%02d", tid);

        switch (baseCmd) {
            case OsCmd.DATA_FULL:
                return handleFull(aidStr, tidStr, body);
            case OsCmd.DATA_HEART:
                return handleHeart(aidStr, tidStr);
            case OsCmd.DATA_DIFF:
                return handleDiff(aidStr, tidStr, body);
            default:
                return null;
        }
    }

    /** Clear all cached templates (e.g. on reconnect). */
    public void clear() {
        cache.clear();
    }

    /** Returns the total number of cached templates (for diagnostics). */
    public int templateCount() {
        int count = 0;
        for (Map<String, TemplateState> m : cache.values()) count += m.size();
        return count;
    }

    // ------- packet handlers -------

    private String handleFull(String aidStr, String tidStr, byte[] body) {
        if (body == null || body.length == 0) return null;
        String text = new String(body, StandardCharsets.UTF_8);
        DecompResult decomp = decompose(text);
        if (decomp == null) return null;

        Map<String, TemplateState> aidMap = cache.computeIfAbsent(aidStr, k -> new HashMap<>());
        aidMap.put(tidStr, new TemplateState(decomp.signature, decomp.valsBin));
        return text;
    }

    private String handleHeart(String aidStr, String tidStr) {
        TemplateState state = getState(aidStr, tidStr);
        if (state == null || state.valsBin.isEmpty()) return null;
        return reconstruct(state.signature, state.valsBin);
    }

    private String handleDiff(String aidStr, String tidStr, byte[] body) {
        TemplateState state = getState(aidStr, tidStr);
        if (state == null || state.valsBin.isEmpty()) return null;

        int numVals = state.valsBin.size();
        int maskLen = (numVals + 7) / 8;
        if (body == null || body.length < maskLen) return null;

        // Read big-endian bitmask
        int mask = 0;
        for (int i = 0; i < maskLen; i++) {
            mask = (mask << 8) | (body[i] & 0xFF);
        }

        int off = maskLen;
        for (int i = 0; i < numVals; i++) {
            if (((mask >> i) & 1) == 1) {
                if (off >= body.length) return null;
                int vLen = body[off] & 0xFF;
                off++;
                if (off + vLen > body.length) return null;
                byte[] val = new byte[vLen];
                System.arraycopy(body, off, val, 0, vLen);
                state.valsBin.set(i, val);
                off += vLen;
            }
        }
        return reconstruct(state.signature, state.valsBin);
    }

    // ------- decompose / reconstruct -------

    private static final class DecompResult {
        final String signature;
        final List<byte[]> valsBin;

        DecompResult(String signature, List<byte[]> valsBin) {
            this.signature = signature;
            this.valsBin = valsBin;
        }
    }

    /**
     * Decompose a FULL body text into a signature + value slots.
     * Mirrors _decompose_for_receive() / _decompose() from diff_engine.dart.
     *
     * Body format after stripping optional prefix up to ';':
     *   {aid}.{status}.{ts_b64}|{sid}>{state}.{unit}:{b62}|...
     */
    private DecompResult decompose(String text) {
        String work = text;

        // Strip any optional prefix before ';'
        int semi = work.indexOf(';');
        if (semi >= 0) work = work.substring(semi + 1);

        // Split at first '|'
        int pipe = work.indexOf('|');
        if (pipe < 0) return null;

        String head = work.substring(0, pipe);
        String payload = work.substring(pipe + 1);

        // Replace ts_token in header with {TS} placeholder
        int lastDot = head.lastIndexOf('.');
        if (lastDot < 0) return null;
        String hBase = head.substring(0, lastDot);

        List<String> sigSegments = new ArrayList<>();
        List<byte[]> valsBin = new ArrayList<>();

        for (String seg : payload.split("\\|")) {
            if (seg.isEmpty()) continue;

            int gt = seg.indexOf('>');
            int colon = seg.indexOf(':');
            if (gt >= 0 && colon >= 0 && colon > gt) {
                String tag = seg.substring(0, gt);
                String content = seg.substring(gt + 1);
                int colonInContent = content.indexOf(':');
                if (colonInContent < 0) continue;
                String meta = content.substring(0, colonInContent);
                String val  = content.substring(colonInContent + 1);

                // Slot 1: meta (state.unit), Slot 2: value (b62)
                sigSegments.add(tag + ">\u0001:\u0001");
                valsBin.add(meta.getBytes(StandardCharsets.UTF_8));
                valsBin.add(val.getBytes(StandardCharsets.UTF_8));
            } else {
                sigSegments.add(seg);
            }
        }

        // signature = "{hBase}.{TS}|{seg1}|{seg2}|..."
        StringBuilder sigSb = new StringBuilder(hBase).append(".{TS}");
        for (String s : sigSegments) {
            sigSb.append('|').append(s);
        }
        return new DecompResult(sigSb.toString(), valsBin);
    }

    /**
     * Reconstruct full body text from signature + cached value slots.
     */
    private String reconstruct(String signature, List<byte[]> valsBin) {
        long nowSec = System.currentTimeMillis() / 1000L;
        String result = signature.replace("{TS}", Base62Codec.encodeTimestamp(nowSec));

        // Replace each \x01 placeholder with the corresponding value
        for (byte[] val : valsBin) {
            String valStr = new String(val, StandardCharsets.UTF_8);
            int idx = result.indexOf('\u0001');
            if (idx < 0) break;
            result = result.substring(0, idx) + valStr + result.substring(idx + 1);
        }
        return result;
    }

    // ------- helpers -------

    private TemplateState getState(String aidStr, String tidStr) {
        Map<String, TemplateState> aidMap = cache.get(aidStr);
        if (aidMap == null) return null;
        return aidMap.get(tidStr);
    }
}

