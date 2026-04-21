package com.opensynaptic.gsynjava.ui.devices;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.opensynaptic.gsynjava.ui.common.CardRowAdapter;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;

import java.util.ArrayList;
import java.util.List;

public class DevicesFragment extends Fragment {
    private FragmentDevicesBinding binding;
    private AppRepository repository;
    private final List<Models.Device> allDevices = new ArrayList<>();
    private final List<Models.Device> visibleDevices = new ArrayList<>();
    private CardRowAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDevicesBinding.inflate(inflater, container, false);
        repository = AppController.get(requireContext()).repository();
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
        load();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void load() {
        allDevices.clear();
        allDevices.addAll(repository.getAllDevices());
        filter();
        if (binding != null) binding.swipeRefresh.setRefreshing(false);
    }

    private void filter() {
        if (binding == null) return;
        String q = binding.etSearch.getText() == null ? "" : binding.etSearch.getText().toString().trim().toLowerCase();
        List<Models.Device> filtered = new ArrayList<>();
        for (Models.Device d : allDevices) {
            if (q.isEmpty() || String.valueOf(d.aid).contains(q) || (d.name != null && d.name.toLowerCase().contains(q))) filtered.add(d);
        }
        visibleDevices.clear();
        visibleDevices.addAll(filtered);
        int onlineCount = repository.getOnlineDeviceCount();
        int offlineCount = Math.max(0, allDevices.size() - onlineCount);
        binding.tvSummary.setText(getString(R.string.devices_summary_format, allDevices.size(), onlineCount, filtered.size()));
        binding.tvTotalDevices.setText(String.valueOf(allDevices.size()));
        binding.tvOnlineDevices.setText(String.valueOf(onlineCount));
        binding.tvOfflineDevices.setText(String.valueOf(offlineCount));
        binding.tvFilteredDevices.setText(String.valueOf(filtered.size()));
        binding.tvEmpty.setText(q.isEmpty() ? getString(R.string.devices_empty_no_data) : getString(R.string.devices_empty_no_match));
        adapter.setRows(toRows(filtered));
    }

    private void showDeviceDetails(Models.Device device) {
        List<Models.SensorData> readings = repository.getLatestReadingsByDevice(device.aid);
        StringBuilder sb = new StringBuilder();
        sb.append("AID: ").append(device.aid).append('\n')
                .append(getString(R.string.device_detail_name)).append(device.name).append('\n')
                .append(getString(R.string.device_detail_type)).append(UiFormatters.upperOrFallback(device.type, "sensor")).append('\n')
                .append(getString(R.string.device_detail_status)).append(device.status).append('\n')
                .append(getString(R.string.device_detail_transport)).append(device.transportType).append('\n')
                .append(getString(R.string.device_detail_location)).append(device.lat).append(", ").append(device.lng).append('\n')
                .append(getString(R.string.device_detail_last_seen)).append(UiFormatters.formatDateTime(device.lastSeenMs)).append(" (")
                .append(UiFormatters.formatRelativeTime(device.lastSeenMs)).append(")")
                .append(getString(R.string.device_detail_readings));
        for (Models.SensorData data : readings) {
            sb.append("• ").append(data.sensorId).append(" = ").append(UiFormatters.trimNumber(data.value)).append(' ').append(data.unit).append(" · ").append(UiFormatters.formatDateTime(data.timestampMs)).append('\n');
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(device.name == null || device.name.isEmpty() ? "Device " + device.aid : device.name)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.device_detail_close, null)
                .show();
    }

    private List<CardRowAdapter.Row> toRows(List<Models.Device> devices) {
        List<CardRowAdapter.Row> rows = new ArrayList<>();
        for (Models.Device d : devices) {
            boolean online = "online".equalsIgnoreCase(d.status) || System.currentTimeMillis() - d.lastSeenMs < 5 * 60_000L;
            List<Models.SensorData> readings = repository.getLatestReadingsByDevice(d.aid);
            String sensorSummary = UiFormatters.formatSensorSummary(readings);
            rows.add(new CardRowAdapter.Row(
                    d.name == null || d.name.isEmpty() ? "Device " + d.aid : d.name,
                    sensorSummary + "\n" + UiFormatters.upperOrFallback(d.type, "SENSOR") + " · " + getString(R.string.device_row_last_active) + UiFormatters.formatRelativeTime(d.lastSeenMs),
                    "AID " + d.aid + " · " + UiFormatters.upperOrFallback(d.transportType, "UDP") + " · " + UiFormatters.formatDateTime(d.lastSeenMs),
                    online ? "ONLINE" : "OFFLINE",
                    requireContext().getColor(online ? R.color.gsyn_online : R.color.gsyn_warning),
                    requireContext().getColor(R.color.gsyn_on_surface)
            ));
        }
        return rows;
    }
}


