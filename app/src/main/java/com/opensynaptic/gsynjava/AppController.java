package com.opensynaptic.gsynjava;

import android.content.Context;
import android.content.SharedPreferences;

import com.opensynaptic.gsynjava.data.AppRepository;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.rules.RulesEngine;
import com.opensynaptic.gsynjava.transport.TransportManager;

public class AppController implements TransportManager.MessageListener {
    private static AppController instance;

    private final Context context;
    private final SharedPreferences preferences;
    private final AppRepository repository;
    private final TransportManager transportManager;
    private final RulesEngine rulesEngine;

    private AppController(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences("gsyn_java_prefs", Context.MODE_PRIVATE);
        this.repository = AppRepository.get(this.context);
        this.transportManager = TransportManager.get(this.context);
        this.rulesEngine = new RulesEngine(repository, transportManager);
        this.transportManager.addMessageListener(this);
        repository.seedDefaultRuleIfEmpty();
    }

    public static synchronized AppController get(Context context) {
        if (instance == null) instance = new AppController(context);
        return instance;
    }

    public AppRepository repository() {
        return repository;
    }

    public TransportManager transport() {
        return transportManager;
    }

    public SharedPreferences preferences() {
        return preferences;
    }

    @Override
    public void onMessage(Models.DeviceMessage message) {
        Models.Device device = new Models.Device();
        device.aid = message.deviceAid;
        device.name = message.nodeId == null || message.nodeId.isEmpty() ? "Device " + message.deviceAid : message.nodeId;
        device.status = "online";
        device.transportType = message.transportType;
        device.lastSeenMs = System.currentTimeMillis();
        repository.upsertDevice(device);

        for (Models.SensorReading reading : message.readings) {
            Models.SensorData data = new Models.SensorData();
            data.deviceAid = message.deviceAid;
            data.sensorId = reading.sensorId;
            data.unit = reading.unit;
            data.value = reading.value;
            data.rawB62 = reading.rawB62;
            data.timestampMs = message.timestampSec * 1000L;
            repository.insertSensorData(data);
        }

        String udpHost = preferences.getString("udp_host", "127.0.0.1");
        int udpPort = preferences.getInt("udp_port", 9876);
        rulesEngine.evaluate(message, udpHost, udpPort);
    }
}

