package com.opensynaptic.gsynjava.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.data.AppRepository;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.databinding.FragmentDashboardBinding;
import com.opensynaptic.gsynjava.transport.TransportManager;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;
import com.opensynaptic.gsynjava.ui.SecondaryActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment implements TransportManager.MessageListener, TransportManager.StatsListener {
    private FragmentDashboardBinding binding;
    private AppRepository repository;
    private TransportManager transportManager;
    private int lastMsgPerSecond;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        repository = AppController.get(requireContext()).repository();
        transportManager = AppController.get(requireContext()).transport();
        binding.btnMap.setOnClickListener(v -> startActivity(SecondaryActivity.intent(requireContext(), SecondaryActivity.MODE_MAP, R.string.title_map)));
        binding.btnHistory.setOnClickListener(v -> startActivity(SecondaryActivity.intent(requireContext(), SecondaryActivity.MODE_HISTORY, R.string.title_history)));
        binding.btnRules.setOnClickListener(v -> startActivity(SecondaryActivity.intent(requireContext(), SecondaryActivity.MODE_RULES, R.string.title_rules)));
        binding.btnHealth.setOnClickListener(v -> startActivity(SecondaryActivity.intent(requireContext(), SecondaryActivity.MODE_HEALTH, R.string.title_health)));
        binding.btnRefresh.setOnClickListener(v -> refresh());
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        transportManager.addMessageListener(this);
        transportManager.addStatsListener(this);
        refresh();
    }

    @Override
    public void onStop() {
        transportManager.removeMessageListener(this);
        transportManager.removeStatsListener(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void refresh() {
        if (binding == null) return;
        int totalDevices = repository.getTotalDeviceCount();
        int online = repository.getOnlineDeviceCount();
        int alerts = repository.getUnacknowledgedAlertCount();
        Models.TransportStats stats = transportManager.getLastStats();
        double onlineRate = totalDevices == 0 ? 0 : (online * 100.0 / totalDevices);
        binding.tvTotalDevicesValue.setText(String.valueOf(totalDevices));
        binding.tvOnlineRateValue.setText(String.format(Locale.getDefault(), "%.1f%%", onlineRate));
        binding.tvActiveAlertsValue.setText(String.valueOf(alerts));
        binding.tvThroughputValue.setText(String.format(Locale.getDefault(), "%d msg/s", lastMsgPerSecond));
        binding.tvRulesValue.setText(String.valueOf(repository.getEnabledRules().size()));
        binding.tvTotalMessagesValue.setText(String.valueOf(stats.totalMessages));
        binding.tvSubtitle.setText(String.format(Locale.getDefault(), "在线设备 %d / %d · 未确认告警 %d · 规则 %d 条", online, totalDevices, alerts, repository.getEnabledRules().size()));

        List<Models.SensorData> history = repository.querySensorData(System.currentTimeMillis() - 24L * 3600L * 1000L, System.currentTimeMillis(), 12);
        StringBuilder sb = new StringBuilder();
        double latestTemp = 0, latestHum = 0, latestPressure = 0, latestLevel = 0;
        int readingCount = 0;
        long latestSampleMs = 0L;
        List<Float> tempTrend = new ArrayList<>();
        List<Float> humTrend = new ArrayList<>();
        for (Models.SensorData item : history) {
            latestSampleMs = Math.max(latestSampleMs, item.timestampMs);
            String sid = item.sensorId == null ? "" : item.sensorId.toUpperCase();
            if (sid.contains("TEMP") || sid.contains("TMP") || sid.equals("T1")) {
                latestTemp = item.value;
                tempTrend.add(0, (float) item.value);
            }
            if (sid.contains("HUM") || sid.equals("H1")) {
                latestHum = item.value;
                humTrend.add(0, (float) item.value);
            }
            if (sid.contains("PRES") || sid.contains("BAR") || sid.equals("P1")) latestPressure = item.value;
            if (sid.contains("LEVEL") || sid.contains("LVL") || sid.equals("L1")) latestLevel = item.value;
            sb.append("• ").append(item.sensorId).append(" = ").append(item.value).append(' ').append(item.unit).append(" · AID ").append(item.deviceAid).append('\n');
            readingCount++;
        }
        binding.tvLiveMetrics.setText(String.format(
                Locale.getDefault(),
                "温度: %s °C\n湿度: %s %%\n压力: %s hPa\n液位: %s %%\n最近采样窗口: %d 条",
                UiFormatters.trimNumber(latestTemp),
                UiFormatters.trimNumber(latestHum),
                UiFormatters.trimNumber(latestPressure),
                UiFormatters.trimNumber(latestLevel),
                readingCount
        ));
        binding.progressWater.setProgressCompat((int) Math.max(0, Math.min(100, latestLevel)), true);
        binding.progressHumidity.setProgressCompat((int) Math.max(0, Math.min(100, latestHum)), true);
        binding.chartTemp.setTitle("Temperature Trend");
        binding.chartHumidity.setTitle("Humidity Trend");
        binding.chartTemp.setChartColor(0xFFFF7043);
        binding.chartHumidity.setChartColor(0xFF42A5F5);
        binding.chartTemp.setSeries(tempTrend.isEmpty() ? createPlaceholderTrend((float) latestTemp, 22f, 24f, 25f, 23.5f, 26f, 27f) : tempTrend);
        binding.chartHumidity.setSeries(humTrend.isEmpty() ? createPlaceholderTrend((float) latestHum, 45f, 49f, 53f, 50f, 56f, 60f) : humTrend);
        binding.tvSyncStatus.setText(latestSampleMs <= 0
                ? "最后同步：暂无采样"
                : "最后同步：" + UiFormatters.formatDateTime(latestSampleMs) + " · " + UiFormatters.formatRelativeTime(latestSampleMs));
        binding.tvTransportStatus.setText(String.format(
                Locale.getDefault(),
                "UDP %s · MQTT %s · ingress %d/s · total %d",
                stats.udpConnected ? "ON" : "OFF",
                stats.mqttConnected ? "ON" : "OFF",
                stats.messagesPerSecond,
                stats.totalMessages
        ));
        binding.tvRecentAlerts.setText(buildRecentAlertsSummary());
        binding.tvOpsSummary.setText(buildOperationsSummary());
        binding.tvLatestReadings.setText(sb.length() == 0 ? "暂无实时读数，先去 Settings 开启 UDP/MQTT 监听。" : sb.toString().trim());
    }

    private String buildRecentAlertsSummary() {
        List<Models.AlertItem> items = repository.getAlerts(null, 3);
        if (items.isEmpty()) {
            return "近期告警：暂无活动告警，系统状态平稳。";
        }
        StringBuilder sb = new StringBuilder("近期告警：");
        for (int i = 0; i < items.size(); i++) {
            Models.AlertItem item = items.get(i);
            if (i > 0) sb.append('\n');
            sb.append(item.level == 2 ? "[Critical] " : item.level == 1 ? "[Warning] " : "[Info] ")
                    .append(item.message)
                    .append(" · AID ").append(item.deviceAid)
                    .append(" · ").append(UiFormatters.formatRelativeTime(item.createdMs));
        }
        return sb.toString();
    }

    private String buildOperationsSummary() {
        List<Models.OperationLog> logs = repository.getOperationLogs(3);
        if (logs.isEmpty()) {
            return "近期操作：暂无本地操作记录。";
        }
        StringBuilder sb = new StringBuilder("近期操作：");
        for (int i = 0; i < logs.size(); i++) {
            Models.OperationLog log = logs.get(i);
            if (i > 0) sb.append('\n');
            sb.append("• ")
                    .append(log.action)
                    .append(" · ")
                    .append(UiFormatters.formatRelativeTime(log.timestampMs));
            if (log.details != null && !log.details.trim().isEmpty()) {
                sb.append(" · ").append(log.details);
            }
        }
        return sb.toString();
    }

    private List<Float> createPlaceholderTrend(float latest, float... fallback) {
        List<Float> result = new ArrayList<>();
        if (latest > 0) {
            result.add(Math.max(0f, latest - 2f));
            result.add(Math.max(0f, latest - 1f));
            result.add(latest);
            result.add(latest + 0.5f);
            result.add(Math.max(0f, latest - 0.3f));
            result.add(latest + 1f);
        } else {
            for (float value : fallback) {
                result.add(value);
            }
        }
        return result;
    }

    @Override
    public void onMessage(Models.DeviceMessage message) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(this::refresh);
    }

    @Override
    public void onStats(Models.TransportStats stats) {
        lastMsgPerSecond = stats.messagesPerSecond;
        if (getActivity() == null) return;
        getActivity().runOnUiThread(this::refresh);
    }
}

