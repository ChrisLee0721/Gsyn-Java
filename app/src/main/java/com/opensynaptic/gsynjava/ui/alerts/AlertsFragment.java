package com.opensynaptic.gsynjava.ui.alerts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.data.AppRepository;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.databinding.FragmentAlertsBinding;
import com.opensynaptic.gsynjava.ui.common.CardRowAdapter;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.opensynaptic.gsynjava.transport.TransportManager;

public class AlertsFragment extends Fragment implements TransportManager.MessageListener {

    // ── Level constants (spinner position → alert level value) ───────────────
    private static final int[] SPINNER_TO_LEVEL = {-1, 2, 1, 0}; // -1 = all

    private FragmentAlertsBinding binding;
    private AppRepository repository;
    private TransportManager transportManager;
    /** Full list from last DB load — never mutated by search filter. */
    private List<Models.AlertItem> allLoaded = new ArrayList<>();
    /** Filtered/displayed list — position maps to adapter rows for click handling. */
    private List<Models.AlertItem> currentAlerts = new ArrayList<>();
    private CardRowAdapter adapter;
    private String searchQuery = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAlertsBinding.inflate(inflater, container, false);
        repository = AppController.get(requireContext()).repository();
        transportManager = TransportManager.get(requireContext());
        adapter = new CardRowAdapter(requireContext());
        binding.list.setAdapter(adapter);
        binding.list.setEmptyView(binding.tvEmpty);
        binding.swipeRefresh.setOnRefreshListener(this::load);

        // Level spinner
        binding.spinnerLevel.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.alerts_filter_all), "Critical", "Warning", "Info"}));
        binding.spinnerLevel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { load(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        // Search
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                searchQuery = newText == null ? "" : newText.trim().toLowerCase(Locale.getDefault());
                applyFilterAndRender();
                return true;
            }
        });

        // Single-tap: acknowledge
        binding.list.setOnItemClickListener((parent, view, position, id) -> {
            Models.AlertItem alert = currentAlerts.get(position);
            if (!alert.acknowledged) {
                executor.execute(() -> {
                    repository.acknowledgeAlert(alert.id);
                    if (isAdded()) requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), R.string.alerts_acked_toast, Toast.LENGTH_SHORT).show();
                        load();
                    });
                });
            }
        });

        // Long-press: delete
        binding.list.setOnItemLongClickListener((parent, view, position, id) -> {
            Models.AlertItem alert = currentAlerts.get(position);
            executor.execute(() -> {
                repository.deleteAlert(alert.id);
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), R.string.alerts_delete_toast, Toast.LENGTH_SHORT).show();
                    load();
                });
            });
            return true;
        });

        // Batch acknowledge all
        binding.btnAckAll.setOnClickListener(v -> executor.execute(() -> {
            repository.acknowledgeAllAlerts();
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), R.string.alerts_ack_all_toast, Toast.LENGTH_SHORT).show();
                load();
            });
        }));

        // Clear acknowledged
        binding.btnClearAcked.setOnClickListener(v -> executor.execute(() -> {
            int deleted = repository.deleteAcknowledgedAlerts();
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(),
                        getString(R.string.alerts_clear_acked_toast, deleted), Toast.LENGTH_SHORT).show();
                load();
            });
        }));

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

    // ── Async data load ──────────────────────────────────────────────────────
    private void load() {
        if (binding == null) return;
        int pos = binding.spinnerLevel.getSelectedItemPosition();
        Integer level = (pos > 0 && pos < SPINNER_TO_LEVEL.length) ? SPINNER_TO_LEVEL[pos] : null;

        executor.execute(() -> {
            int[] counts = repository.getAlertCountsByLevel();
            int info     = counts[0];
            int warning  = counts[1];
            int critical = counts[2];
            int unacked  = repository.getUnacknowledgedAlertCount();
            List<Models.AlertItem> all = repository.getAlerts(level, 200);

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                allLoaded = all;                  // keep full copy untouched
                currentAlerts = new ArrayList<>(allLoaded); // reset before filter
                binding.tvSummary.setText(getString(R.string.alerts_summary_format,
                        critical, warning, info, all.size()));
                binding.tvCriticalCount.setText(String.valueOf(critical));
                binding.tvWarningCount.setText(String.valueOf(warning));
                binding.tvInfoCount.setText(String.valueOf(info));
                binding.tvUnackedCount.setText(String.valueOf(unacked));
                binding.tvFilterState.setText(getString(R.string.alerts_filter_state_format,
                        filterLabelForPosition(pos), unacked));
                binding.tvEmpty.setText(R.string.alerts_empty);
                applyFilterAndRender();
                binding.swipeRefresh.setRefreshing(false);
                updateAlertsBadge(unacked);
            });
        });
    }

    /** Apply keyword filter on top of allLoaded and push to adapter. */
    private void applyFilterAndRender() {
        if (binding == null) return;
        List<CardRowAdapter.Row> rows = new ArrayList<>();
        List<Models.AlertItem> filtered = new ArrayList<>();
        for (Models.AlertItem a : allLoaded) {          // always iterate the full set
            if (!searchQuery.isEmpty()) {
                String haystack = (a.message + " " + a.sensorId + " " + a.deviceAid)
                        .toLowerCase(Locale.getDefault());
                if (!haystack.contains(searchQuery)) continue;
            }
            filtered.add(a);
            String lv = a.level == 2 ? "CRITICAL" : a.level == 1 ? "WARNING" : "INFO";
            int color = requireContext().getColor(
                    a.level == 2 ? R.color.gsyn_danger
                    : a.level == 1 ? R.color.gsyn_warning
                    : R.color.gsyn_info);
            rows.add(new CardRowAdapter.Row(
                    a.message,
                    "AID " + a.deviceAid + " · Sensor "
                            + UiFormatters.upperOrFallback(a.sensorId, "N/A")
                            + " · " + UiFormatters.formatRelativeTime(a.createdMs),
                    DateFormat.getDateTimeInstance().format(new Date(a.createdMs))
                            + (a.acknowledged
                               ? getString(R.string.alerts_acked_label)
                               : getString(R.string.alerts_tap_to_ack)
                                 + "  " + getString(R.string.alerts_long_press_hint)),
                    a.acknowledged ? lv + " · ACK" : lv,
                    color,
                    requireContext().getColor(R.color.gsyn_on_surface)
            ));
        }
        currentAlerts = filtered;                       // only filtered view stored here
        adapter.setRows(rows);
    }

    // ── Bottom-nav badge ─────────────────────────────────────────────────────
    private void updateAlertsBadge(int unacked) {
        if (getActivity() == null) return;
        BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
        if (bottomNav == null) return;
        if (unacked > 0) {
            BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_alerts);
            badge.setNumber(unacked);
            badge.setVisible(true);
        } else {
            bottomNav.removeBadge(R.id.nav_alerts);
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private String filterLabelForPosition(int pos) {
        if (pos == 1) return "Critical";
        if (pos == 2) return "Warning";
        if (pos == 3) return "Info";
        return getString(R.string.alerts_filter_all);
    }
}
