package com.opensynaptic.gsynjava.ui.settings;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.core.AppThemeConfig;
import com.opensynaptic.gsynjava.databinding.FragmentSettingsBinding;
import com.opensynaptic.gsynjava.transport.TransportManager;
import com.opensynaptic.gsynjava.ui.SecondaryActivity;
import com.opensynaptic.gsynjava.ui.dashboard.DashboardCardConfig;

import java.util.Locale;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        AppThemeConfig.applyBgToRoot(binding.getRoot(), requireContext());
        loadPrefs();
        buildAccentChips();
        buildBgChips();
        loadCardConfig();
        binding.btnSave.setOnClickListener(v -> savePrefs());
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ── Accent colour chip group ───────────────────────────────────────────

    private void buildAccentChips() {
        if (binding == null) return;
        AppThemeConfig.ThemePreset current = AppThemeConfig.loadThemePreset(requireContext());
        binding.chipGroupAccent.removeAllViews();
        int selectedId = View.NO_ID;
        for (AppThemeConfig.ThemePreset preset : AppThemeConfig.ThemePreset.values()) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(preset.label);
            chip.setCheckable(true);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(preset.color());
            dot.setSize(40, 40);
            chip.setChipIcon(dot);
            chip.setChipIconSize(40f);
            // Use click listener to avoid double-fire from OnCheckedChangeListener
            chip.setOnClickListener(v -> AppThemeConfig.saveThemePreset(requireContext(), preset));
            binding.chipGroupAccent.addView(chip);
            if (preset == current) selectedId = chip.getId();
        }
        if (selectedId != View.NO_ID) binding.chipGroupAccent.check(selectedId);
    }

    // ── Background preset chip group ───────────────────────────────────────

    private void buildBgChips() {
        if (binding == null) return;
        AppThemeConfig.BgPreset current = AppThemeConfig.loadBgPreset(requireContext());
        binding.chipGroupBg.removeAllViews();
        int selectedId = View.NO_ID;
        for (AppThemeConfig.BgPreset preset : AppThemeConfig.BgPreset.values()) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(preset.label);
            chip.setCheckable(true);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(preset.bgColor());
            dot.setStroke(2, android.graphics.Color.GRAY);
            dot.setSize(40, 40);
            chip.setChipIcon(dot);
            chip.setChipIconSize(40f);
            chip.setOnClickListener(v -> AppThemeConfig.saveBgPreset(requireContext(), preset));
            binding.chipGroupBg.addView(chip);
            if (preset == current) selectedId = chip.getId();
        }
        if (selectedId != View.NO_ID) binding.chipGroupBg.check(selectedId);
    }

    // ── Dashboard card visibility toggles ─────────────────────────────────

    private void loadCardConfig() {
        if (binding == null) return;
        DashboardCardConfig cfg = DashboardCardConfig.load(requireContext());
        binding.switchCardKpiRow1.setChecked(cfg.showKpiRow1);
        binding.switchCardKpiRow2.setChecked(cfg.showKpiRow2);
        binding.switchCardKpiRow3.setChecked(cfg.showKpiRow3);
        binding.switchCardGauges.setChecked(cfg.showGauges);
        binding.switchCardCharts.setChecked(cfg.showCharts);
        binding.switchCardActivity.setChecked(cfg.showActivityFeed);
        binding.switchCardReadings.setChecked(cfg.showLatestReadings);
    }

    private void saveCardConfig() {
        if (binding == null) return;
        DashboardCardConfig cfg = DashboardCardConfig.defaults();
        cfg.showKpiRow1        = binding.switchCardKpiRow1.isChecked();
        cfg.showKpiRow2        = binding.switchCardKpiRow2.isChecked();
        cfg.showKpiRow3        = binding.switchCardKpiRow3.isChecked();
        cfg.showGauges         = binding.switchCardGauges.isChecked();
        cfg.showCharts         = binding.switchCardCharts.isChecked();
        cfg.showActivityFeed   = binding.switchCardActivity.isChecked();
        cfg.showLatestReadings = binding.switchCardReadings.isChecked();
        cfg.save(requireContext());
    }

    // ── Prefs ──────────────────────────────────────────────────────────────

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
        // Save card config
        saveCardConfig();

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

        // Recreate activity to apply any accent/bg theme change
        requireActivity().recreate();
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

