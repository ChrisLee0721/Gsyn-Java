package com.opensynaptic.gsynjava.ui.mirror;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.data.AppRepository;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.transport.TransportManager;
import com.opensynaptic.gsynjava.ui.common.CardRowAdapter;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HealthMirrorFragment extends Fragment implements TransportManager.StatsListener {
    private AppRepository repository;
    private TransportManager transportManager;
    private CardRowAdapter adapter;
    private TextView tvSectionLabel;
    private TextView tvSummary;
    private TextView tvDetail;
    private TextView tvEmpty;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_secondary_panel, container, false);
        repository       = AppController.get(requireContext()).repository();
        transportManager = AppController.get(requireContext()).transport();
        adapter          = new CardRowAdapter(requireContext());
        tvSectionLabel   = view.findViewById(R.id.tvSectionLabel);
        tvSummary        = view.findViewById(R.id.tvSummary);
        tvDetail         = view.findViewById(R.id.tvDetail);
        tvEmpty          = view.findViewById(R.id.tvEmpty);
        ListView list    = view.findViewById(R.id.list);
        MaterialButton button = view.findViewById(R.id.btnAction);
        list.setAdapter(adapter);
        list.setEmptyView(tvEmpty);
        button.setText(R.string.mirror_health_action);
        button.setOnClickListener(v -> prune());
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        transportManager.addStatsListener(this);
        load();
    }

    @Override
    public void onStop() {
        super.onStop();
        transportManager.removeStatsListener(this);
    }

    // ── StatsListener — called every second by TransportManager ──────────────
    @Override
    public void onStats(Models.TransportStats stats) {
        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                if (getView() == null) return;
                long dbKb = repository.getDatabaseSizeBytes() / 1024L;
                tvSummary.setText(getString(R.string.mirror_health_summary_format,
                        adapter.getCount(), stats.messagesPerSecond, stats.totalMessages));
                tvDetail.setText(getString(R.string.mirror_health_detail_format,
                        stats.udpConnected
                                ? getString(R.string.transport_enabled)
                                : getString(R.string.transport_disabled),
                        stats.mqttConnected
                                ? getString(R.string.transport_connected)
                                : getString(R.string.transport_disconnected),
                        dbKb));
            });
        }
    }

    private void load() {
        executor.execute(() -> {
            List<Models.Device> devices = repository.getAllDevices();
            Models.TransportStats stats = transportManager.getLastStats();
            long dbKb = repository.getDatabaseSizeBytes() / 1024L;

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (getView() == null) return;
                tvSectionLabel.setText(R.string.mirror_health_title);
                tvSummary.setText(getString(R.string.mirror_health_summary_format,
                        devices.size(), stats.messagesPerSecond, stats.totalMessages));
                tvDetail.setText(getString(R.string.mirror_health_detail_format,
                        stats.udpConnected
                                ? getString(R.string.transport_enabled)
                                : getString(R.string.transport_disabled),
                        stats.mqttConnected
                                ? getString(R.string.transport_connected)
                                : getString(R.string.transport_disconnected),
                        dbKb));
                tvEmpty.setText(R.string.mirror_health_empty);

                List<CardRowAdapter.Row> rows = new ArrayList<>();
                long cutoff = System.currentTimeMillis() - 5 * 60_000L;
                for (Models.Device device : devices) {
                    boolean online = "online".equalsIgnoreCase(device.status)
                            || device.lastSeenMs > cutoff;
                    rows.add(new CardRowAdapter.Row(
                            device.name == null || device.name.isEmpty()
                                    ? "Device " + device.aid : device.name,
                            "AID " + device.aid + " · "
                                    + UiFormatters.upperOrFallback(device.transportType, "UDP"),
                            getString(R.string.mirror_health_last_seen_format,
                                    UiFormatters.formatRelativeTime(device.lastSeenMs)),
                            online ? "ONLINE" : "OFFLINE",
                            requireContext().getColor(online ? R.color.gsyn_online : R.color.gsyn_warning),
                            requireContext().getColor(R.color.gsyn_on_surface)
                    ));
                }
                adapter.setRows(rows);
            });
        });
    }

    private void prune() {
        executor.execute(() -> {
            int deleted = repository.pruneOldData(7);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(),
                        getString(R.string.mirror_health_prune_ok, deleted),
                        Toast.LENGTH_LONG).show();
                load();
            });
        });
    }
}
