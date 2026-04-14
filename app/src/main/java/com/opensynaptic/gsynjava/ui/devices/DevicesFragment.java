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
import java.util.Locale;

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
        binding.tvSummary.setText(String.format(Locale.getDefault(), "已发现 %d 台设备 · 在线 %d 台 · 当前显示 %d 台", allDevices.size(), onlineCount, filtered.size()));
        binding.tvTotalDevices.setText(String.valueOf(allDevices.size()));
        binding.tvOnlineDevices.setText(String.valueOf(onlineCount));
        binding.tvOfflineDevices.setText(String.valueOf(offlineCount));
        binding.tvFilteredDevices.setText(String.valueOf(filtered.size()));
        binding.tvEmpty.setText(q.isEmpty() ? "还没有设备数据，先去 Settings 启动 UDP/MQTT 监听。" : "没有匹配的设备，请换个关键词试试。");
        adapter.setRows(toRows(filtered));
    }

    private void showDeviceDetails(Models.Device device) {
        List<Models.SensorData> readings = repository.getLatestReadingsByDevice(device.aid);
        StringBuilder sb = new StringBuilder();
        sb.append("AID: ").append(device.aid).append('\n')
                .append("名称: ").append(device.name).append('\n')
                .append("类型: ").append(UiFormatters.upperOrFallback(device.type, "sensor")).append('\n')
                .append("状态: ").append(device.status).append('\n')
                .append("传输: ").append(device.transportType).append('\n')
                .append("位置: ").append(device.lat).append(", ").append(device.lng).append('\n')
                .append("最后活动: ").append(UiFormatters.formatDateTime(device.lastSeenMs)).append(" (")
                .append(UiFormatters.formatRelativeTime(device.lastSeenMs)).append(")\n\n最新读数:\n");
        for (Models.SensorData data : readings) {
            sb.append("• ").append(data.sensorId).append(" = ").append(UiFormatters.trimNumber(data.value)).append(' ').append(data.unit).append(" · ").append(UiFormatters.formatDateTime(data.timestampMs)).append('\n');
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(device.name == null || device.name.isEmpty() ? "Device " + device.aid : device.name)
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
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
                    sensorSummary + "\n" + UiFormatters.upperOrFallback(d.type, "SENSOR") + " · 最近活跃 " + UiFormatters.formatRelativeTime(d.lastSeenMs),
                    "AID " + d.aid + " · " + UiFormatters.upperOrFallback(d.transportType, "UDP") + " · " + UiFormatters.formatDateTime(d.lastSeenMs),
                    online ? "ONLINE" : "OFFLINE",
                    requireContext().getColor(online ? R.color.gsyn_online : R.color.gsyn_warning),
                    requireContext().getColor(R.color.gsyn_on_surface)
            ));
        }
        return rows;
    }
}


