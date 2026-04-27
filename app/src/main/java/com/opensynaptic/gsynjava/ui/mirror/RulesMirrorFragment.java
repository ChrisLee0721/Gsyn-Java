package com.opensynaptic.gsynjava.ui.mirror;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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

public class RulesMirrorFragment extends Fragment {
    private AppRepository repository;
    private CardRowAdapter adapter;
    private TextView tvSectionLabel;
    private TextView tvSummary;
    private TextView tvDetail;
    private TextView tvEmpty;
    private final List<Models.Rule> cachedRules = new ArrayList<>();

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
        list.setOnItemClickListener((parent, v, position, id) -> toggleRuleIfNeeded(position));
        list.setOnItemLongClickListener((parent, v, position, id) -> deleteRuleIfNeeded(position));
        button.setText(R.string.mirror_rules_action);
        button.setOnClickListener(v -> showCreateRuleDialog());
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    private void load() {
        List<Models.Rule> rules = repository.getAllRules();
        List<Models.OperationLog> logs = repository.getOperationLogs(30);
        cachedRules.clear();
        cachedRules.addAll(rules);

        tvSectionLabel.setText(R.string.mirror_rules_title);
        tvSummary.setText(getString(R.string.rules_summary_format, rules.size(), logs.size()));
        tvDetail.setText(R.string.rules_detail);
        tvEmpty.setText(R.string.rules_empty_local);

        List<CardRowAdapter.Row> rows = new ArrayList<>();
        for (Models.Rule rule : rules) {
            rows.add(new CardRowAdapter.Row(
                    rule.name,
                    (rule.sensorIdFilter == null ? "*" : rule.sensorIdFilter) + " " + rule.operator + " " + UiFormatters.trimNumber(rule.threshold) + " → " + rule.actionType,
                    "AID " + (rule.deviceAidFilter == null ? "ALL" : rule.deviceAidFilter) + " · cooldown " + (rule.cooldownMs / 1000L) + "s",
                    rule.enabled ? "ENABLED" : "DISABLED",
                    requireContext().getColor(rule.enabled ? R.color.gsyn_online : R.color.gsyn_warning),
                    requireContext().getColor(R.color.gsyn_on_surface)
            ));
        }
        for (Models.OperationLog log : logs) {
            rows.add(new CardRowAdapter.Row(
                    log.action,
                    log.details,
                    UiFormatters.formatDateTime(log.timestampMs),
                    "LOG",
                    requireContext().getColor(R.color.gsyn_info),
                    requireContext().getColor(R.color.gsyn_on_surface)
            ));
        }
        adapter.setRows(rows);
    }

    private void toggleRuleIfNeeded(int position) {
        if (position < 0 || position >= cachedRules.size()) return;
        Models.Rule rule = cachedRules.get(position);
        repository.toggleRule(rule.id, !rule.enabled);
        repository.logOperation("TOGGLE_RULE", "ruleId=" + rule.id + " enabled=" + !rule.enabled);
        Toast.makeText(requireContext(),
                getString(R.string.rules_toggle_toast, !rule.enabled ? getString(R.string.rules_toggle_enabled) : getString(R.string.rules_toggle_disabled)),
                Toast.LENGTH_SHORT).show();
        load();
    }

    private boolean deleteRuleIfNeeded(int position) {
        if (position < 0 || position >= cachedRules.size()) return false;
        Models.Rule rule = cachedRules.get(position);
        repository.deleteRule(rule.id);
        repository.logOperation("DELETE_RULE", "ruleId=" + rule.id + " name=" + rule.name);
        Toast.makeText(requireContext(), getString(R.string.rules_delete_toast, rule.name), Toast.LENGTH_SHORT).show();
        load();
        return true;
    }

    private void showCreateRuleDialog() {
        EditText sensorInput = new EditText(requireContext());
        sensorInput.setHint(getString(R.string.mirror_rules_sensor_hint));
        sensorInput.setText("TEMP");

        EditText thresholdInput = new EditText(requireContext());
        thresholdInput.setHint(getString(R.string.mirror_rules_threshold_hint));
        thresholdInput.setText("50");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);
        layout.addView(sensorInput);
        layout.addView(thresholdInput);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mirror_rules_dialog_title)
                .setMessage(R.string.mirror_rules_dialog_message)
                .setView(layout)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_create, (dialog, which) -> {
                    Models.Rule rule = new Models.Rule();
                    rule.name = sensorInput.getText().toString().trim() + " threshold";
                    rule.sensorIdFilter = sensorInput.getText().toString().trim();
                    rule.operator = ">";
                    try {
                        rule.threshold = Double.parseDouble(thresholdInput.getText().toString().trim());
                    } catch (Exception ex) {
                        rule.threshold = 50;
                    }
                    rule.actionType = "create_alert";
                    repository.saveRule(rule);
                    repository.logOperation("CREATE_RULE", "name=" + rule.name + " threshold=" + rule.threshold);
                    load();
                })
                .show();
    }
}


