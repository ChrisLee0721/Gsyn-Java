package com.opensynaptic.gsynjava.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.databinding.FragmentSettingsBinding;
import com.opensynaptic.gsynjava.transport.TransportManager;
import com.opensynaptic.gsynjava.ui.SecondaryActivity;

import java.util.Locale;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        loadPrefs();
        binding.btnSave.setOnClickListener(v -> savePrefs());
        binding.btnOpenMap.setOnClickListener(v -> startActivity(SecondaryActivity.intent(requireContext(), SecondaryActivity.MODE_MAP, R.string.title_map)));
        binding.btnOpenHistory.setOnClickListener(v -> startActivity(SecondaryActivity.intent(requireContext(), SecondaryActivity.MODE_HISTORY, R.string.title_history)));
        binding.btnOpenRules.setOnClickListener(v -> startActivity(SecondaryActivity.intent(requireContext(), SecondaryActivity.MODE_RULES, R.string.title_rules)));
        binding.btnOpenHealth.setOnClickListener(v -> startActivity(SecondaryActivity.intent(requireContext(), SecondaryActivity.MODE_HEALTH, R.string.title_health)));
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void loadPrefs() {
        var prefs = AppController.get(requireContext()).preferences();
        binding.etUdpHost.setText(prefs.getString("udp_host", "0.0.0.0"));
        binding.etUdpPort.setText(String.valueOf(prefs.getInt("udp_port", 9876)));
        binding.switchUdp.setChecked(prefs.getBoolean("udp_enabled", true));
        binding.etMqttBroker.setText(prefs.getString("mqtt_broker", "localhost"));
        binding.etMqttPort.setText(String.valueOf(prefs.getInt("mqtt_port", 1883)));
        binding.etMqttTopic.setText(prefs.getString("mqtt_topic", "opensynaptic/#"));
        binding.switchMqtt.setChecked(prefs.getBoolean("mqtt_enabled", false));
        binding.etTileUrl.setText(prefs.getString("tile_url", "https://tile.openstreetmap.org/{z}/{x}/{y}.png"));
        refreshTransportStatus();
    }

    private void savePrefs() {
        var prefs = AppController.get(requireContext()).preferences();
        prefs.edit()
                .putString("udp_host", textOf(binding.etUdpHost).trim())
                .putInt("udp_port", parseInt(textOf(binding.etUdpPort), 9876))
                .putBoolean("udp_enabled", binding.switchUdp.isChecked())
                .putString("mqtt_broker", textOf(binding.etMqttBroker).trim())
                .putInt("mqtt_port", parseInt(textOf(binding.etMqttPort), 1883))
                .putString("mqtt_topic", textOf(binding.etMqttTopic).trim())
                .putBoolean("mqtt_enabled", binding.switchMqtt.isChecked())
                .putString("tile_url", textOf(binding.etTileUrl).trim())
                .apply();

        TransportManager manager = AppController.get(requireContext()).transport();
        try {
            if (binding.switchUdp.isChecked()) {
                manager.startUdp(textOf(binding.etUdpHost).trim(), parseInt(textOf(binding.etUdpPort), 9876));
            } else {
                manager.stopUdp();
            }
            if (binding.switchMqtt.isChecked()) {
                manager.connectMqtt(textOf(binding.etMqttBroker).trim(), parseInt(textOf(binding.etMqttPort), 1883), textOf(binding.etMqttTopic).trim());
            } else {
                manager.disconnectMqtt();
            }
            refreshTransportStatus();
            Toast.makeText(requireContext(), "设置已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(requireContext(), "应用连接设置失败: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshTransportStatus() {
        if (binding == null) return;
        var controller = AppController.get(requireContext());
        TransportManager manager = AppController.get(requireContext()).transport();
        int devices = controller.repository().getTotalDeviceCount();
        int alerts = controller.repository().getUnacknowledgedAlertCount();
        int rules = controller.repository().getEnabledRules().size();
        long dbBytes = controller.repository().getDatabaseSizeBytes();
        binding.tvTransportStatus.setText(getString(
                R.string.settings_transport_status_format,
                manager.isUdpRunning() ? "已启用" : "未启用",
                manager.isMqttConnected() ? "已连接" : "未连接",
                textOf(binding.etTileUrl).trim()
        ));
        binding.tvDeviceCount.setText(String.valueOf(devices));
        binding.tvAlertCount.setText(String.valueOf(alerts));
        binding.tvRuleCount.setText(String.valueOf(rules));
        binding.tvDbSize.setText(formatBytes(dbBytes));
        binding.tvRuntimeHint.setText(getString(
                R.string.settings_runtime_hint_format,
                manager.isUdpRunning() ? "UDP ON" : "UDP OFF",
                manager.isMqttConnected() ? "MQTT ON" : "MQTT OFF",
                devices,
                rules
        ));
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private String textOf(android.widget.TextView view) {
        return view.getText() == null ? "" : view.getText().toString();
    }
}

