package com.opensynaptic.gsynjava.ui.mirror;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.data.AppRepository;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.ui.common.CardRowAdapter;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapMirrorFragment extends Fragment {
    private AppRepository repository;
    private CardRowAdapter adapter;
    private TextView tvSectionLabel;
    private TextView tvSummary;
    private TextView tvDetail;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_secondary_panel, container, false);
        repository = AppController.get(requireContext()).repository();
        adapter = new CardRowAdapter(requireContext());
        tvSectionLabel = view.findViewById(R.id.tvSectionLabel);
        tvSummary = view.findViewById(R.id.tvSummary);
        tvDetail = view.findViewById(R.id.tvDetail);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        ListView list = view.findViewById(R.id.list);
        MaterialButton button = view.findViewById(R.id.btnAction);
        list.setAdapter(adapter);
        list.setEmptyView(tvEmpty);
        button.setText("刷新设备");
        button.setOnClickListener(v -> load());
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    private void load() {
        List<Models.Device> devices = repository.getAllDevices();
        String tileUrl = AppController.get(requireContext()).preferences().getString("tile_url", "https://tile.openstreetmap.org/{z}/{x}/{y}.png");
        int mapped = 0;
        for (Models.Device device : devices) {
            if (hasCoordinates(device)) mapped++;
        }
        tvSectionLabel.setText("Map Mirror");
        tvSummary.setText("设备总数 " + devices.size() + " · 具备坐标 " + mapped + " · 目标是后续替换成真实地图视图。") ;
        tvDetail.setText("当前瓦片源: " + tileUrl + "\n本页先保持原版地图信息层结构：设备位置、状态和 transport 摘要。") ;
        tvEmpty.setText("暂无设备位置数据，等待设备上报或后续补录经纬度。") ;

        List<CardRowAdapter.Row> rows = new ArrayList<>();
        for (Models.Device device : devices) {
            boolean hasGps = hasCoordinates(device);
            rows.add(new CardRowAdapter.Row(
                    device.name == null || device.name.isEmpty() ? "Device " + device.aid : device.name,
                    hasGps
                            ? String.format(Locale.getDefault(), "lat %.5f · lng %.5f", device.lat, device.lng)
                            : "暂无坐标，当前作为列表镜像占位",
                    "AID " + device.aid + " · " + UiFormatters.upperOrFallback(device.transportType, "UDP") + " · " + UiFormatters.formatRelativeTime(device.lastSeenMs),
                    hasGps ? "MAPPED" : "NO GPS",
                    requireContext().getColor(hasGps ? R.color.gsyn_online : R.color.gsyn_warning),
                    requireContext().getColor(R.color.gsyn_on_surface)
            ));
        }
        adapter.setRows(rows);
    }

    private boolean hasCoordinates(Models.Device device) {
        return Math.abs(device.lat) > 0.000001d || Math.abs(device.lng) > 0.000001d;
    }
}

