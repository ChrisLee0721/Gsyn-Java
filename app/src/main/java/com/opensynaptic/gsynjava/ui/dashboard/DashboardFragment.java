package com.opensynaptic.gsynjava.ui.dashboard;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.core.AppThemeConfig;
import com.opensynaptic.gsynjava.data.AppRepository;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.databinding.FragmentDashboardBinding;
import com.opensynaptic.gsynjava.transport.TransportManager;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardFragment extends Fragment
        implements TransportManager.MessageListener, TransportManager.StatsListener {

    private FragmentDashboardBinding binding;
    private AppRepository repository;
    private TransportManager transportManager;
    private int lastMsgPerSecond;

    private DashboardCardAdapter cardAdapter;
    private DashboardCardConfig cardConfig;
    private ItemTouchHelper touchHelper;
    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        AppThemeConfig.applyBgToRoot(binding.getRoot(), requireContext());
        repository       = AppController.get(requireContext()).repository();
        transportManager = AppController.get(requireContext()).transport();

        setupRecyclerView();
        setupFab();
        binding.btnExitSingleMode.setOnClickListener(v -> enterSingleDeviceMode(false));
        return binding.getRoot();
    }

    @Override public void onStart() {
        super.onStart();
        transportManager.addMessageListener(this);
        transportManager.addStatsListener(this);
        refresh();
    }

    @Override public void onStop() {
        transportManager.removeMessageListener(this);
        transportManager.removeStatsListener(this);
        super.onStop();
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ── RecyclerView + drag ───────────────────────────────────────────────────

    private void setupRecyclerView() {
        cardConfig  = DashboardCardConfig.load(requireContext());
        cardAdapter = new DashboardCardAdapter();
        cardAdapter.setItems(cardConfig.visibleCards());
        cardAdapter.setListener(new DashboardCardAdapter.Listener() {
            @Override public void onToggleSingleMode() {
                boolean current = AppController.get(requireContext()).preferences()
                        .getBoolean("single_device_mode", false);
                AppController.get(requireContext()).preferences().edit()
                        .putBoolean("single_device_mode", !current).apply();
                refresh(); // immediate effect
            }
            @Override public void onItemMoved(int from, int to) {}
            @Override public void onRemoveCustomCard(int adapterPos, DashboardCardItem item) {
                cardConfig.removeCard(item);
                cardConfig.save(requireContext());
                cardAdapter.setItems(cardConfig.visibleCards());
            }
        });

        binding.rvDashboard.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDashboard.setAdapter(cardAdapter);

        // ItemTouchHelper for drag-to-reorder
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv,
                                        @NonNull RecyclerView.ViewHolder vh) {
                // HEADER (position 0) is not draggable
                if (vh.getAdapterPosition() == 0) return 0;
                return makeMovementFlags(
                        ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override public boolean onMove(@NonNull RecyclerView rv,
                                            @NonNull RecyclerView.ViewHolder from,
                                            @NonNull RecyclerView.ViewHolder to) {
                int f = from.getAdapterPosition();
                int t = to.getAdapterPosition();
                if (f == 0 || t == 0) return false; // protect header
                cardAdapter.moveItem(f, t);
                return true;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}

            @Override public void onSelectedChanged(@Nullable RecyclerView.ViewHolder vh, int state) {
                super.onSelectedChanged(vh, state);
                if (vh != null && state == ItemTouchHelper.ACTION_STATE_DRAG) {
                    vh.itemView.setAlpha(0.85f);
                    vh.itemView.setElevation(16f);
                }
            }

            @Override public void clearView(@NonNull RecyclerView rv,
                                            @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                vh.itemView.setAlpha(1f);
                vh.itemView.setElevation(0f);
                // Persist the new order: rebuild cards list from adapter order
                persistOrderFromAdapter();
            }

            @Override public boolean isLongPressDragEnabled() { return true; }
        });
        touchHelper.attachToRecyclerView(binding.rvDashboard);
    }

    private void persistOrderFromAdapter() {
        // Rebuild cardConfig.cards from adapter's current visible order,
        // then re-add hidden cards at the end.
        List<DashboardCardItem> visible = new ArrayList<>();
        for (int i = 0; i < cardAdapter.getItemCount(); i++) visible.add(cardAdapter.getItem(i));

        List<DashboardCardItem> hidden = new ArrayList<>();
        for (DashboardCardItem c : cardConfig.cards) if (!c.visible) hidden.add(c);

        cardConfig.cards.clear();
        cardConfig.cards.addAll(visible);
        cardConfig.cards.addAll(hidden);
        cardConfig.save(requireContext());
    }

    // ── FAB: add custom sensor card ───────────────────────────────────────────

    private void setupFab() {
        binding.fabAddCard.setOnClickListener(v -> showAddSensorDialog());
    }

    private void showAddSensorDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        EditText etSensorId = new EditText(requireContext());
        etSensorId.setHint(getString(R.string.dashboard_sensor_card_id_hint));
        etSensorId.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        EditText etLabel = new EditText(requireContext());
        etLabel.setHint(getString(R.string.dashboard_sensor_card_label_hint));
        etLabel.setInputType(InputType.TYPE_CLASS_TEXT);

        layout.addView(etSensorId);
        layout.addView(etLabel);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dashboard_add_sensor_dialog_title))
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dlg, w) -> {
                    String sid   = etSensorId.getText().toString().trim().toUpperCase(Locale.ROOT);
                    String label = etLabel.getText().toString().trim();
                    if (sid.isEmpty()) {
                        Toast.makeText(requireContext(),
                                R.string.dashboard_sensor_card_id_empty_toast, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    cardConfig.addCustomSensor(sid, label.isEmpty() ? sid : label);
                    cardConfig.save(requireContext());
                    cardAdapter.setItems(cardConfig.visibleCards());
                    refresh(); // populate data immediately
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ── Single-device mode ────────────────────────────────────────────────────

    private void enterSingleDeviceMode(boolean enter) {
        if (binding == null) return;
        binding.rvDashboard.setVisibility(enter ? View.GONE : View.VISIBLE);
        binding.fabAddCard.setVisibility(enter ? View.GONE : View.VISIBLE);
        binding.singleDeviceScroll.setVisibility(enter ? View.VISIBLE : View.GONE);
    }

    private void refreshSingleDeviceView(Models.Device device,
                                         DashboardCardAdapter.Snapshot snapshot) {
        if (binding == null) return;

        boolean online = "online".equalsIgnoreCase(device.status)
                || System.currentTimeMillis() - device.lastSeenMs < 5 * 60_000L;

        // Hero section
        String name = (device.name == null || device.name.isEmpty())
                ? "Device " + device.aid : device.name;
        binding.sdDeviceName.setText(name);

        // Status pill — change background color via GradientDrawable
        int statusColor = requireContext().getColor(online ? R.color.gsyn_online : R.color.gsyn_warning);
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(60f);
        pill.setColor(statusColor);
        binding.sdStatusBadge.setBackground(pill);
        binding.sdStatusBadge.setText(online ? "● ONLINE" : "● OFFLINE");

        binding.sdAid.setText("AID " + device.aid);
        binding.sdTransport.setText(UiFormatters.upperOrFallback(device.transportType, "UDP"));
        binding.sdLastSeen.setText(getString(R.string.device_detail_last_seen)
                + UiFormatters.formatDateTime(device.lastSeenMs));

        // Quick stats
        binding.sdAlerts.setText(String.valueOf(snapshot.alerts));
        binding.sdRules.setText(String.valueOf(snapshot.rules));
        binding.sdThroughput.setText(snapshot.throughput + "/s");

        // Progress gauges with value labels
        binding.sdProgressWater.setProgressCompat(
                (int) Math.max(0, Math.min(100, snapshot.latestLevel)), true);
        binding.sdProgressHumidity.setProgressCompat(
                (int) Math.max(0, Math.min(100, snapshot.latestHum)), true);
        binding.sdLevelValue.setText(UiFormatters.trimNumber(snapshot.latestLevel) + " %");
        binding.sdHumValue.setText(UiFormatters.trimNumber(snapshot.latestHum) + " %");

        // Trend chart
        if (!snapshot.tempTrend.isEmpty()) {
            binding.sdChartTemp.setChartColor(0xFFFF7043);
            binding.sdChartTemp.setSeries(snapshot.tempTrend);
            binding.sdChartTemp.setVisibility(android.view.View.VISIBLE);
        } else {
            binding.sdChartTemp.setVisibility(android.view.View.GONE);
        }

        // Dynamic sensor grid — each reading gets its own card pair
        List<Models.SensorData> readings = repository.getLatestReadingsByDevice(device.aid);
        binding.sdSensorGrid.removeAllViews();

        if (readings.isEmpty()) {
            binding.sdNoSensors.setVisibility(android.view.View.VISIBLE);
        } else {
            binding.sdNoSensors.setVisibility(android.view.View.GONE);
            LayoutInflater inf = LayoutInflater.from(requireContext());
            int dp4 = (int) (4 * getResources().getDisplayMetrics().density);
            int dp12 = dp4 * 3;

            // Pair readings into rows of 2
            for (int i = 0; i < readings.size(); i += 2) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = dp4;
                row.setLayoutParams(rowParams);

                addSensorCard(row, readings.get(i), inf, dp12);
                if (i + 1 < readings.size()) {
                    addSensorCard(row, readings.get(i + 1), inf, dp12);
                } else {
                    // Spacer for odd count
                    android.view.View spacer = new android.view.View(requireContext());
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    spacer.setLayoutParams(sp);
                    row.addView(spacer);
                }
                binding.sdSensorGrid.addView(row);
            }
        }
    }

    private void addSensorCard(LinearLayout row, Models.SensorData data,
                                LayoutInflater inf, int margin) {
        com.google.android.material.card.MaterialCardView card =
                new com.google.android.material.card.MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cardParams.setMargins(margin / 3, 0, margin / 3, 0);
        card.setLayoutParams(cardParams);
        card.setUseCompatPadding(true);

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(margin, margin, margin, margin);

        android.widget.TextView tvId = new android.widget.TextView(requireContext());
        tvId.setText(data.sensorId);
        tvId.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
        tvId.setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium));
        inner.addView(tvId);

        android.widget.TextView tvVal = new android.widget.TextView(requireContext());
        tvVal.setText(UiFormatters.trimNumber(data.value));
        tvVal.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_DisplaySmall);
        tvVal.setTextColor(requireContext().getColorStateList(com.google.android.material.R.color.m3_sys_color_dynamic_dark_primary).getDefaultColor());
        tvVal.setTypeface(null, android.graphics.Typeface.BOLD);
        inner.addView(tvVal);

        android.widget.TextView tvUnit = new android.widget.TextView(requireContext());
        tvUnit.setText(data.unit.isEmpty() ? "—" : data.unit);
        tvUnit.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        tvUnit.setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium));
        inner.addView(tvUnit);

        android.widget.TextView tvTime = new android.widget.TextView(requireContext());
        tvTime.setText(UiFormatters.formatRelativeTime(data.timestampMs));
        tvTime.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
        tvTime.setTextColor(requireContext().getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium));
        tvTime.setPadding(0, (int)(4 * getResources().getDisplayMetrics().density), 0, 0);
        inner.addView(tvTime);

        card.addView(inner);
        row.addView(card);
    }

    // ── Data refresh ──────────────────────────────────────────────────────────

    private void refresh() {
        if (binding == null) return;

        int totalDevices = repository.getTotalDeviceCount();
        int online       = repository.getOnlineDeviceCount();
        int alerts       = repository.getUnacknowledgedAlertCount();
        int rules        = repository.getEnabledRules().size();
        Models.TransportStats stats = transportManager.getLastStats();

        // Build sensor data snapshot
        List<Models.SensorData> history = repository.querySensorData(
                System.currentTimeMillis() - 24L * 3600_000L, System.currentTimeMillis(), 50);

        DashboardCardAdapter.Snapshot snap = new DashboardCardAdapter.Snapshot();
        snap.totalDevices = totalDevices;
        snap.online       = online;
        snap.alerts       = alerts;
        snap.rules        = rules;
        snap.totalMessages= stats.totalMessages;
        snap.throughput   = lastMsgPerSecond;
        snap.latestBySensorId  = new HashMap<>();
        snap.trendBySensorId   = new HashMap<>();

        Map<String, List<Float>> trendMap = new HashMap<>();

        for (Models.SensorData item : history) {
            String sid = item.sensorId == null ? "" : item.sensorId.toUpperCase(Locale.ROOT);
            snap.latestBySensorId.putIfAbsent(sid, item); // first = most recent (DESC)

            if (!trendMap.containsKey(sid)) trendMap.put(sid, new ArrayList<>());
            List<Float> tl = trendMap.get(sid);
            if (tl != null && tl.size() < 12) tl.add(0, (float) item.value);

            if (sid.contains("TEMP") || sid.contains("TMP") || sid.equals("T1")) {
                snap.latestTemp = item.value;
                snap.tempTrend.add(0, (float) item.value);
            }
            if (sid.contains("HUM") || sid.equals("H1")) {
                snap.latestHum = item.value;
                snap.humTrend.add(0, (float) item.value);
            }
            if (sid.contains("PRES") || sid.contains("BAR") || sid.equals("P1"))
                snap.latestPressure = item.value;
            if (sid.contains("LEVEL") || sid.contains("LVL") || sid.equals("L1"))
                snap.latestLevel = item.value;
            snap.readingCount++;
        }
        snap.trendBySensorId = trendMap;

        // Build text fields
        long latestSampleMs = 0;
        for (Models.SensorData item : history)
            latestSampleMs = Math.max(latestSampleMs, item.timestampMs);
        snap.latestSampleMs = latestSampleMs;

        snap.subtitle = getString(R.string.dashboard_subtitle_format, online, totalDevices, alerts, rules);
        snap.syncStatus = latestSampleMs <= 0
                ? getString(R.string.dashboard_sync_none)
                : getString(R.string.dashboard_sync_format,
                        UiFormatters.formatDateTime(latestSampleMs),
                        UiFormatters.formatRelativeTime(latestSampleMs));
        snap.transportStatus = String.format(Locale.getDefault(),
                "UDP %s · MQTT %s · ingress %d/s · total %d",
                stats.udpConnected ? "ON" : "OFF",
                stats.mqttConnected ? "ON" : "OFF",
                stats.messagesPerSecond,
                stats.totalMessages);

        StringBuilder rawSb = new StringBuilder();
        for (Models.SensorData item : history) {
            rawSb.append("• ").append(item.sensorId).append(" = ")
                 .append(item.value).append(' ').append(item.unit)
                 .append(" · AID ").append(item.deviceAid).append('\n');
        }
        snap.latestReadingsText = rawSb.length() == 0
                ? getString(R.string.dashboard_no_readings) : rawSb.toString().trim();
        snap.recentAlertsSummary = buildRecentAlertsSummary();
        snap.opsSummary          = buildOperationsSummary();
        boolean singleModeEnabled = AppController.get(requireContext()).preferences()
                .getBoolean("single_device_mode", false);
        snap.singleModeEnabled   = singleModeEnabled;

        cardAdapter.setSnapshot(snap);

        // ── Single-device mode check ──────────────────────────────────────────
        if (singleModeEnabled) {
            enterSingleDeviceMode(true);
            List<Models.Device> devices = repository.getAllDevices();
            if (!devices.isEmpty()) {
                refreshSingleDeviceView(devices.get(0), snap);
            } else {
                // No devices yet — show placeholder
                if (binding != null) {
                    binding.sdDeviceName.setText(R.string.dashboard_single_mode_no_device);
                    binding.sdStatusBadge.setText("—");
                    binding.sdAid.setText("");
                    binding.sdTransport.setText("");
                    binding.sdLastSeen.setText(getString(R.string.dashboard_no_readings));
                    binding.sdAlerts.setText("0");
                    binding.sdRules.setText("0");
                    binding.sdThroughput.setText("0/s");
                    binding.sdProgressWater.setProgressCompat(0, false);
                    binding.sdProgressHumidity.setProgressCompat(0, false);
                    binding.sdLevelValue.setText("—");
                    binding.sdHumValue.setText("—");
                    binding.sdChartTemp.setVisibility(android.view.View.GONE);
                    binding.sdSensorGrid.removeAllViews();
                    binding.sdNoSensors.setVisibility(android.view.View.VISIBLE);
                }
            }
            return;
        }
        // Multi-device — normal card list
        enterSingleDeviceMode(false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildRecentAlertsSummary() {
        List<Models.AlertItem> items = repository.getAlerts(null, 3);
        if (items.isEmpty()) return getString(R.string.dashboard_recent_alerts_none);
        StringBuilder sb = new StringBuilder(getString(R.string.dashboard_recent_alerts_prefix));
        for (int i = 0; i < items.size(); i++) {
            Models.AlertItem item = items.get(i);
            if (i > 0) sb.append('\n');
            sb.append(item.level == 2 ? "[Critical] " : item.level == 1 ? "[Warning] " : "[Info] ")
              .append(item.message).append(" · AID ").append(item.deviceAid)
              .append(" · ").append(UiFormatters.formatRelativeTime(item.createdMs));
        }
        return sb.toString();
    }

    private String buildOperationsSummary() {
        List<Models.OperationLog> logs = repository.getOperationLogs(3);
        if (logs.isEmpty()) return getString(R.string.dashboard_recent_ops_none);
        StringBuilder sb = new StringBuilder(getString(R.string.dashboard_recent_ops_prefix));
        for (int i = 0; i < logs.size(); i++) {
            Models.OperationLog log = logs.get(i);
            if (i > 0) sb.append('\n');
            sb.append("• ").append(log.action).append(" · ")
              .append(UiFormatters.formatRelativeTime(log.timestampMs));
            if (log.details != null && !log.details.trim().isEmpty())
                sb.append(" · ").append(log.details);
        }
        return sb.toString();
    }

    // ── TransportManager listeners ────────────────────────────────────────────

    @Override public void onMessage(Models.DeviceMessage message) {
        if (getActivity() != null) getActivity().runOnUiThread(this::refresh);
    }

    @Override public void onStats(Models.TransportStats stats) {
        lastMsgPerSecond = stats.messagesPerSecond;
        if (getActivity() != null) getActivity().runOnUiThread(this::refresh);
    }
}

