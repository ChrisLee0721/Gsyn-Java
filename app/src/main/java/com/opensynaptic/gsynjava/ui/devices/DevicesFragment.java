package com.opensynaptic.gsynjava.ui.devices;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
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
import com.opensynaptic.gsynjava.databinding.FragmentDevicesBinding;
import com.opensynaptic.gsynjava.transport.TransportManager;
import com.opensynaptic.gsynjava.ui.common.CardRowAdapter;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DevicesFragment extends Fragment implements TransportManager.MessageListener {
    private FragmentDevicesBinding binding;
    private AppRepository repository;
    private TransportManager transportManager;
    /** Full device list from last DB load — never mutated by search. */
    private List<Models.Device> allDevices = new ArrayList<>();
    /** Filtered list that maps to adapter positions. */
    private List<Models.Device> visibleDevices = new ArrayList<>();
    /** Batch sensor cache from last async load. */
    private Map<Integer, List<Models.SensorData>> sensorCache = Collections.emptyMap();
    private CardRowAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDevicesBinding.inflate(inflater, container, false);
        repository = AppController.get(requireContext()).repository();
        transportManager = TransportManager.get(requireContext());
        adapter = new CardRowAdapter(requireContext());
        binding.list.setAdapter(adapter);
        binding.list.setEmptyView(binding.tvEmpty);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        binding.list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleDevices.size()) {
                showDeviceDetails(visibleDevices.get(position));
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        transportManager.addMessageListener(this);
        load();
    }

    @Override
    public void onStop() {
        super.onStop();
        transportManager.removeMessageListener(this);
    }

    @Override
    public void onMessage(Models.DeviceMessage message) {
        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread(this::load);
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        if (binding == null) return;
        executor.execute(() -> {
            List<Models.Device> devices = repository.getAllDevices();
            Map<Integer, List<Models.SensorData>> sensors = repository.getAllLatestReadings();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                allDevices = devices;
                sensorCache = sensors;
                filter();
                binding.swipeRefresh.setRefreshing(false);
            });
        });
    }

    private void filter() {
        if (binding == null) return;
        String q = binding.etSearch.getText() == null ? ""
                : binding.etSearch.getText().toString().trim().toLowerCase();
        List<Models.Device> filtered = new ArrayList<>();
        for (Models.Device d : allDevices) {
            if (q.isEmpty() || String.valueOf(d.aid).contains(q)
                    || (d.name != null && d.name.toLowerCase().contains(q))) {
                filtered.add(d);
            }
        }
        visibleDevices = filtered;

        long cutoff = System.currentTimeMillis() - 5 * 60_000L;
        int onlineCount = 0;
        for (Models.Device d : allDevices) {
            if ("online".equalsIgnoreCase(d.status) || d.lastSeenMs > cutoff) onlineCount++;
        }
        int offlineCount = Math.max(0, allDevices.size() - onlineCount);

        binding.tvSummary.setText(getString(R.string.devices_summary_format,
                allDevices.size(), onlineCount, filtered.size()));
        binding.tvTotalDevices.setText(String.valueOf(allDevices.size()));
        binding.tvOnlineDevices.setText(String.valueOf(onlineCount));
        binding.tvOfflineDevices.setText(String.valueOf(offlineCount));
        binding.tvFilteredDevices.setText(String.valueOf(filtered.size()));
        binding.tvEmpty.setText(q.isEmpty()
                ? getString(R.string.devices_empty_no_data)
                : getString(R.string.devices_empty_no_match));
        adapter.setRows(toRows(filtered));
    }

    private void showDeviceDetails(Models.Device device) {
        List<Models.SensorData> readings = sensorCache.getOrDefault(device.aid, Collections.emptyList());
        if (readings == null) readings = Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append("AID: ").append(device.aid).append('\n')
                .append(getString(R.string.device_detail_name)).append(device.name).append('\n')
                .append(getString(R.string.device_detail_type))
                        .append(UiFormatters.upperOrFallback(device.type, "SENSOR")).append('\n')
                .append(getString(R.string.device_detail_status)).append(device.status).append('\n')
                .append(getString(R.string.device_detail_transport)).append(device.transportType).append('\n')
                .append(getString(R.string.device_detail_location))
                        .append(device.lat).append(", ").append(device.lng).append('\n')
                .append(getString(R.string.device_detail_last_seen))
                        .append(UiFormatters.formatDateTime(device.lastSeenMs))
                        .append(" (").append(UiFormatters.formatRelativeTime(device.lastSeenMs)).append(")")
                .append(getString(R.string.device_detail_readings));
        for (Models.SensorData data : readings) {
            sb.append("• ").append(data.sensorId).append(" = ")
                    .append(UiFormatters.trimNumber(data.value)).append(' ').append(data.unit)
                    .append(" · ").append(UiFormatters.formatDateTime(data.timestampMs)).append('\n');
        }
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(device.name == null || device.name.isEmpty()
                        ? "Device " + device.aid : device.name)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.device_detail_close, null)
                .create();
        dialog.show();
        // Make message scrollable for devices with many sensors
        android.widget.TextView msgView = dialog.findViewById(android.R.id.message);
        if (msgView != null) {
            msgView.setMovementMethod(ScrollingMovementMethod.getInstance());
            msgView.setMaxLines(20);
        }
    }

    private List<CardRowAdapter.Row> toRows(List<Models.Device> devices) {
        List<CardRowAdapter.Row> rows = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - 5 * 60_000L;
        for (Models.Device d : devices) {
            boolean online = "online".equalsIgnoreCase(d.status) || d.lastSeenMs > cutoff;
            List<Models.SensorData> readings = sensorCache.getOrDefault(d.aid, Collections.emptyList());
            String sensorSummary = UiFormatters.formatSensorSummary(readings);
            rows.add(new CardRowAdapter.Row(
                    d.name == null || d.name.isEmpty() ? "Device " + d.aid : d.name,
                    sensorSummary + "\n" + UiFormatters.upperOrFallback(d.type, "SENSOR")
                            + " · " + getString(R.string.device_row_last_active)
                            + UiFormatters.formatRelativeTime(d.lastSeenMs),
                    "AID " + d.aid + " · " + UiFormatters.upperOrFallback(d.transportType, "UDP")
                            + " · " + UiFormatters.formatDateTime(d.lastSeenMs),
                    online ? "ONLINE" : "OFFLINE",
                    requireContext().getColor(online ? R.color.gsyn_online : R.color.gsyn_warning),
                    requireContext().getColor(R.color.gsyn_on_surface)
            ));
        }
        return rows;
    }
}

