package com.opensynaptic.gsynjava.rules;

import com.opensynaptic.gsynjava.core.protocol.PacketBuilder;
import com.opensynaptic.gsynjava.data.AppRepository;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.transport.TransportManager;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RulesEngine {
    private final AppRepository repository;
    private final TransportManager transportManager;
    private final Map<Long, Long> lastTriggered = new HashMap<>();

    public RulesEngine(AppRepository repository, TransportManager transportManager) {
        this.repository = repository;
        this.transportManager = transportManager;
    }

    public void evaluate(Models.DeviceMessage message, String udpHost, int udpPort) {
        List<Models.Rule> rules = repository.getEnabledRules();
        if (rules.isEmpty()) return;
        for (Models.SensorReading reading : message.readings) {
            for (Models.Rule rule : rules) {
                if (rule.deviceAidFilter != null && rule.deviceAidFilter != message.deviceAid) continue;
                if (rule.sensorIdFilter != null && !rule.sensorIdFilter.isEmpty() && !rule.sensorIdFilter.equalsIgnoreCase(reading.sensorId)) continue;
                if (!rule.evaluate(reading.value)) continue;
                long now = System.currentTimeMillis();
                Long last = lastTriggered.get(rule.id);
                if (last != null && now - last < rule.cooldownMs) continue;
                lastTriggered.put(rule.id, now);
                execute(rule, message, reading, udpHost, udpPort);
            }
        }
    }

    private void execute(Models.Rule rule, Models.DeviceMessage message, Models.SensorReading reading, String udpHost, int udpPort) {
        if ("create_alert".equals(rule.actionType)) {
            Models.AlertItem alert = new Models.AlertItem();
            alert.deviceAid = message.deviceAid;
            alert.sensorId = reading.sensorId;
            alert.level = reading.value > rule.threshold * 1.5 ? 2 : 1;
            alert.message = "Rule \"" + rule.name + "\": " + reading.sensorId + "=" + reading.value + " " + reading.unit + " " + rule.operator + " " + rule.threshold;
            alert.createdMs = System.currentTimeMillis();
            repository.insertAlert(alert);
        } else if ("send_command".equals(rule.actionType)) {
            try {
                JSONObject json = new JSONObject(rule.actionPayload == null ? "{}" : rule.actionPayload);
                int targetAid = json.optInt("target_aid", message.deviceAid);
                String sensorId = json.optString("sensor_id", "CMD");
                String unit = json.optString("unit", "");
                double value = json.optDouble("value", 0.0);
                byte[] frame = PacketBuilder.buildSensorPacket(targetAid, 1, System.currentTimeMillis() / 1000L, sensorId, unit, value);
                if (frame != null) transportManager.sendCommand(frame, targetAid, udpHost, udpPort);
            } catch (Exception ignored) {
            }
        }
        repository.logOperation("rule_triggered", "Rule \"" + rule.name + "\" triggered on device=" + message.deviceAid + " sensor=" + reading.sensorId + " action=" + rule.actionType);
    }
}

