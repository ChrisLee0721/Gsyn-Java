package com.opensynaptic.gsynjava.ui.mirror;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
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
import com.opensynaptic.gsynjava.ui.common.CardRowAdapter;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryMirrorFragment extends Fragment {

    /** Time-range options: label, window in ms, max rows */
    private static final long[] RANGE_MS  = {
            3600_000L, 6 * 3600_000L, 24 * 3600_000L, 7 * 24 * 3600_000L
    };
    private static final int[]  RANGE_LIMIT = {200, 500, 500, 1000};

    private AppRepository repository;
    private CardRowAdapter adapter;
    private TextView tvSectionLabel;
    private TextView tvSummary;
    private TextView tvDetail;
    private TextView tvEmpty;
    private MaterialButton btnExport;
    private int selectedRangeIndex = 2; // default: 24 h

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_secondary_panel, container, false);
        repository = AppController.get(requireContext()).repository();
        adapter = new CardRowAdapter(requireContext());
        tvSectionLabel = view.findViewById(R.id.tvSectionLabel);
        tvSummary      = view.findViewById(R.id.tvSummary);
        tvDetail       = view.findViewById(R.id.tvDetail);
        tvEmpty        = view.findViewById(R.id.tvEmpty);
        ListView list  = view.findViewById(R.id.list);
        btnExport      = view.findViewById(R.id.btnAction);
        list.setAdapter(adapter);
        list.setEmptyView(tvEmpty);
        btnExport.setText(R.string.mirror_history_action);
        btnExport.setOnClickListener(v -> exportCsv());

        // Inject a time-range Spinner above the export button
        Spinner spinner = new Spinner(requireContext());
        spinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        getString(R.string.history_range_1h),
                        getString(R.string.history_range_6h),
                        getString(R.string.history_range_24h),
                        getString(R.string.history_range_7d)
                }));
        spinner.setSelection(selectedRangeIndex);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        spinner.setLayoutParams(lp);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedRangeIndex = pos;
                load();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        // Insert spinner between tvDetail card and btnExport
        LinearLayout root = (LinearLayout) view;
        int btnIndex = root.indexOfChild(btnExport);
        root.addView(spinner, btnIndex);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    private void load() {
        long now   = System.currentTimeMillis();
        long from  = now - RANGE_MS[selectedRangeIndex];
        int  limit = RANGE_LIMIT[selectedRangeIndex];

        executor.execute(() -> {
            List<Models.SensorData> rows = repository.querySensorData(from, now, limit);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (getView() == null) return;
                tvSectionLabel.setText(R.string.mirror_history_title);
                tvSummary.setText(getString(R.string.mirror_history_summary_format, rows.size()));
                tvDetail.setText(getString(R.string.mirror_history_detail_format,
                        rows.isEmpty() ? getString(R.string.fmt_no_data)
                                : UiFormatters.formatDateTime(rows.get(0).timestampMs)));
                tvEmpty.setText(R.string.mirror_history_empty);
                List<CardRowAdapter.Row> cards = new ArrayList<>();
                for (Models.SensorData row : rows) {
                    cards.add(new CardRowAdapter.Row(
                            row.sensorId + " · " + UiFormatters.trimNumber(row.value)
                                    + (UiFormatters.safe(row.unit).isEmpty() ? "" : " " + row.unit),
                            getString(R.string.mirror_history_device_format, row.deviceAid),
                            UiFormatters.formatDateTime(row.timestampMs),
                            UiFormatters.upperOrFallback(row.sensorId, "DATA"),
                            requireContext().getColor(R.color.gsyn_info),
                            requireContext().getColor(R.color.gsyn_on_surface)
                    ));
                }
                adapter.setRows(cards);
            });
        });
    }

    private void exportCsv() {
        btnExport.setEnabled(false);
        long now  = System.currentTimeMillis();
        long from = now - RANGE_MS[selectedRangeIndex];
        int  limit = RANGE_LIMIT[selectedRangeIndex];

        executor.execute(() -> {
            try {
                File file = repository.exportHistoryCsv(from, now, limit);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    btnExport.setEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.mirror_history_export_ok, file.getAbsolutePath()),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception ex) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    btnExport.setEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.mirror_history_export_fail, ex.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}

