package com.opensynaptic.gsynjava.transport;

import android.content.Context;
import android.util.Log;

import com.opensynaptic.gsynjava.core.protocol.BodyParser;
import com.opensynaptic.gsynjava.core.protocol.DiffEngine;
import com.opensynaptic.gsynjava.core.protocol.OsCmd;
import com.opensynaptic.gsynjava.core.protocol.PacketDecoder;
import com.opensynaptic.gsynjava.data.Models;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TransportManager {
    public interface MessageListener {
        void onMessage(Models.DeviceMessage message);
    }

    public interface StatsListener {
        void onStats(Models.TransportStats stats);
    }

    private static TransportManager instance;
    private final List<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<StatsListener> statsListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private DatagramSocket udpSocket;
    private Thread udpThread;
    private volatile boolean udpRunning;
    private volatile boolean mqttConnected;
    private MqttClient mqttClient;
    private final DiffEngine diffEngine = new DiffEngine();

    private int totalMessages;
    private int messagesThisSecond;
    private Models.TransportStats lastStats = new Models.TransportStats();

    private TransportManager(Context context) {
        scheduler.scheduleAtFixedRate(this::emitStats, 1, 1, TimeUnit.SECONDS);
    }

    public static synchronized TransportManager get(Context context) {
        if (instance == null) instance = new TransportManager(context.getApplicationContext());
        return instance;
    }

    public void addMessageListener(MessageListener listener) {
        if (!messageListeners.contains(listener)) messageListeners.add(listener);
    }

    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    public void addStatsListener(StatsListener listener) {
        if (!statsListeners.contains(listener)) statsListeners.add(listener);
    }

    public void removeStatsListener(StatsListener listener) {
        statsListeners.remove(listener);
    }

    public synchronized boolean isUdpRunning() {
        return udpRunning;
    }

    public synchronized boolean isMqttConnected() {
        return mqttConnected;
    }

    public synchronized Models.TransportStats getLastStats() {
        return lastStats;
    }

    public synchronized void startUdp(String host, int port) throws Exception {
        stopUdp();
        udpSocket = new DatagramSocket(port, InetAddress.getByName(host));
        udpRunning = true;
        udpThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (udpRunning && udpSocket != null && !udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                    decodeIncoming(data, "udp");
                } catch (Exception ex) {
                    if (udpRunning) Log.w("TransportManager", "UDP receive stopped", ex);
                }
            }
        }, "gsyn-udp-listener");
        udpThread.start();
        emitStats();
    }

    public synchronized void stopUdp() {
        udpRunning = false;
        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }
        udpThread = null;
        diffEngine.clear(); // clear DIFF/HEART template cache on stop
        emitStats();
    }

    public synchronized void connectMqtt(String broker, int port, String topic) throws MqttException {
        disconnectMqtt();
        mqttClient = new MqttClient("tcp://" + broker + ":" + port, "gsyn-java-" + UUID.randomUUID(), new MemoryPersistence());
        mqttClient.setCallback(new MqttCallback() {
            @Override public void connectionLost(Throwable cause) { mqttConnected = false; emitStats(); }
            @Override public void messageArrived(String topic, MqttMessage message) { decodeIncoming(message.getPayload(), "mqtt"); }
            @Override public void deliveryComplete(IMqttDeliveryToken token) {}
        });
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        mqttClient.connect(options);
        mqttClient.subscribe(topic == null || topic.isEmpty() ? "opensynaptic/#" : topic);
        mqttConnected = true;
        emitStats();
    }

    public synchronized void disconnectMqtt() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) mqttClient.disconnect();
        } catch (Exception ignored) {
        }
        mqttClient = null;
        mqttConnected = false;
        diffEngine.clear(); // clear DIFF/HEART template cache on disconnect
        emitStats();
    }

    public synchronized boolean sendCommand(byte[] frame, int deviceAid, String udpHost, int udpPort) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.publish("opensynaptic/cmd/" + deviceAid, new MqttMessage(frame));
                return true;
            }
            DatagramSocket socket = new DatagramSocket();
            socket.send(new DatagramPacket(frame, frame.length, InetAddress.getByName(udpHost), udpPort));
            socket.close();
            return true;
        } catch (Exception ex) {
            Log.w("TransportManager", "sendCommand failed", ex);
            return false;
        }
    }

    private void decodeIncoming(byte[] data, String transportType) {
        Models.PacketMeta meta = PacketDecoder.decode(data);
        if (meta == null || !meta.crc16Ok || !meta.crc8Ok) return;
        if (!OsCmd.isDataCmd(meta.cmd)) return;

        // Extract raw body bytes
        byte[] body = new byte[meta.bodyLen];
        if (meta.bodyLen > 0) {
            System.arraycopy(data, meta.bodyOffset, body, 0, meta.bodyLen);
        }

        // Run through DiffEngine to handle FULL/HEART/DIFF template logic
        String bodyText = diffEngine.processPacket(meta.cmd, meta.aid, meta.tid, body);
        if (bodyText == null) return;

        Models.BodyParseResult parsed = BodyParser.parseText(bodyText);
        if (parsed == null || parsed.readings.isEmpty()) return;

        Models.DeviceMessage message = new Models.DeviceMessage();
        message.cmd = meta.cmd;
        message.deviceAid = meta.aid;
        message.tid = meta.tid;
        message.timestampSec = meta.tsSec;
        message.nodeId = parsed.headerAid;
        message.nodeState = parsed.headerState;
        message.transportType = transportType;
        message.readings.addAll(parsed.readings);
        message.rawFrame = data;

        totalMessages++;
        messagesThisSecond++;
        for (MessageListener listener : messageListeners) {
            listener.onMessage(message);
        }
    }

    private synchronized void emitStats() {
        Models.TransportStats stats = new Models.TransportStats();
        stats.udpConnected = udpRunning;
        stats.mqttConnected = mqttConnected;
        stats.messagesPerSecond = messagesThisSecond;
        stats.totalMessages = totalMessages;
        lastStats = stats;
        messagesThisSecond = 0;
        for (StatsListener listener : statsListeners) {
            listener.onStats(stats);
        }
    }
}

