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
import com.opensynaptic.gsynjava.ui.common.CardRowAdapter;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HistoryMirrorFragment extends Fragment {
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
        button.setText(R.string.mirror_history_action);
        button.setOnClickListener(v -> exportCsv());
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    private void load() {
        long now = System.currentTimeMillis();
        List<Models.SensorData> rows = repository.querySensorData(now - 24L * 3600L * 1000L, now, 500);
        tvSectionLabel.setText(R.string.mirror_history_title);
        tvSummary.setText(getString(R.string.mirror_history_summary_format, rows.size()));
        tvDetail.setText(getString(R.string.mirror_history_detail_format, rows.isEmpty() ? getString(R.string.fmt_no_data) : UiFormatters.formatDateTime(rows.get(0).timestampMs)));
        tvEmpty.setText(R.string.mirror_history_empty);

        List<CardRowAdapter.Row> cards = new ArrayList<>();
        for (Models.SensorData row : rows) {
            cards.add(new CardRowAdapter.Row(
                    row.sensorId + " · " + UiFormatters.trimNumber(row.value) + (UiFormatters.safe(row.unit).isEmpty() ? "" : " " + row.unit),
                    getString(R.string.mirror_history_device_format, row.deviceAid),
                    UiFormatters.formatDateTime(row.timestampMs),
                    UiFormatters.upperOrFallback(row.sensorId, "DATA"),
                    requireContext().getColor(R.color.gsyn_info),
                    requireContext().getColor(R.color.gsyn_on_surface)
            ));
        }
        adapter.setRows(cards);
    }

    private void exportCsv() {
        try {
            File file = repository.exportHistoryCsv();
            Toast.makeText(requireContext(), getString(R.string.mirror_history_export_ok, file.getAbsolutePath()), Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(requireContext(), getString(R.string.mirror_history_export_fail, ex.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
}

