package com.opensynaptic.gsynjava.ui.alerts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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

public class AlertsFragment extends Fragment {
    private FragmentAlertsBinding binding;
    private AppRepository repository;
    private List<Models.AlertItem> currentAlerts = new ArrayList<>();
    private CardRowAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAlertsBinding.inflate(inflater, container, false);
        repository = AppController.get(requireContext()).repository();
        adapter = new CardRowAdapter(requireContext());
        binding.list.setAdapter(adapter);
        binding.list.setEmptyView(binding.tvEmpty);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.spinnerLevel.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.alerts_filter_all), "Critical", "Warning", "Info"}));
        binding.spinnerLevel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { load(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        binding.list.setOnItemClickListener((parent, view, position, id) -> {
            Models.AlertItem alert = currentAlerts.get(position);
            if (!alert.acknowledged) {
                repository.acknowledgeAlert(alert.id);
                Toast.makeText(requireContext(), R.string.alerts_acked_toast, Toast.LENGTH_SHORT).show();
                load();
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
        if (binding == null) return;
        int pos = binding.spinnerLevel.getSelectedItemPosition();
        Integer level = null;
        if (pos == 1) level = 2;
        else if (pos == 2) level = 1;
        else if (pos == 3) level = 0;
        currentAlerts = repository.getAlerts(level, 200);
        int critical = repository.getAlerts(2, 200).size();
        int warning = repository.getAlerts(1, 200).size();
        int info = repository.getAlerts(0, 200).size();
        int unacked = repository.getUnacknowledgedAlertCount();
        binding.tvSummary.setText(getString(R.string.alerts_summary_format, critical, warning, info, currentAlerts.size()));
        binding.tvCriticalCount.setText(String.valueOf(critical));
        binding.tvWarningCount.setText(String.valueOf(warning));
        binding.tvInfoCount.setText(String.valueOf(info));
        binding.tvUnackedCount.setText(String.valueOf(unacked));
        binding.tvFilterState.setText(getString(R.string.alerts_filter_state_format, filterLabelForPosition(pos), unacked));
        binding.tvEmpty.setText(R.string.alerts_empty);
        List<CardRowAdapter.Row> rows = new ArrayList<>();
        for (Models.AlertItem a : currentAlerts) {
            String lv = a.level == 2 ? "CRITICAL" : a.level == 1 ? "WARNING" : "INFO";
            int color = requireContext().getColor(a.level == 2 ? R.color.gsyn_danger : a.level == 1 ? R.color.gsyn_warning : R.color.gsyn_info);
            rows.add(new CardRowAdapter.Row(
                    a.message,
                    "AID " + a.deviceAid + " · Sensor " + UiFormatters.upperOrFallback(a.sensorId, "N/A") + " · " + UiFormatters.formatRelativeTime(a.createdMs),
                    DateFormat.getDateTimeInstance().format(new Date(a.createdMs)) + (a.acknowledged ? getString(R.string.alerts_acked_label) : getString(R.string.alerts_tap_to_ack)),
                    a.acknowledged ? lv + " · ACK" : lv,
                    color,
                    requireContext().getColor(R.color.gsyn_on_surface)
            ));
        }
        adapter.setRows(rows);
        binding.swipeRefresh.setRefreshing(false);
    }

    private String filterLabelForPosition(int pos) {
        if (pos == 1) return "Critical";
        if (pos == 2) return "Warning";
        if (pos == 3) return "Info";
        return getString(R.string.alerts_filter_all);
    }
}

