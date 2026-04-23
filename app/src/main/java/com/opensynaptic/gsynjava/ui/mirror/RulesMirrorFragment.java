package com.opensynaptic.gsynjava.ui.mirror;

import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RulesMirrorFragment extends Fragment {
    private AppRepository repository;
    private CardRowAdapter adapter;
    private TextView tvSectionLabel;
    private TextView tvSummary;
    private TextView tvDetail;
    private TextView tvEmpty;
    private final List<Models.Rule> cachedRules = new ArrayList<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_secondary_panel, container, false);
        repository     = AppController.get(requireContext()).repository();
        adapter        = new CardRowAdapter(requireContext());
        tvSectionLabel = view.findViewById(R.id.tvSectionLabel);
        tvSummary      = view.findViewById(R.id.tvSummary);
        tvDetail       = view.findViewById(R.id.tvDetail);
        tvEmpty        = view.findViewById(R.id.tvEmpty);
        ListView list  = view.findViewById(R.id.list);
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

    // ── Async load ────────────────────────────────────────────────────────────
    private void load() {
        executor.execute(() -> {
            List<Models.Rule>        rules = repository.getAllRules();
            List<Models.OperationLog> logs = repository.getOperationLogs(30);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (getView() == null) return;
                cachedRules.clear();
                cachedRules.addAll(rules);

                tvSectionLabel.setText(R.string.mirror_rules_title);
                tvSummary.setText(getString(R.string.mirror_rules_summary_format, rules.size(), logs.size()));
                tvDetail.setText(R.string.mirror_rules_detail);
                tvEmpty.setText(R.string.mirror_rules_empty);

                List<CardRowAdapter.Row> rows = new ArrayList<>();
                for (Models.Rule rule : rules) {
                    rows.add(new CardRowAdapter.Row(
                            rule.name,
                            (rule.sensorIdFilter == null ? "*" : rule.sensorIdFilter)
                                    + " " + rule.operator + " "
                                    + UiFormatters.trimNumber(rule.threshold)
                                    + " → " + rule.actionType,
                            getString(R.string.mirror_rules_meta_format,
                                    rule.deviceAidFilter == null ? "ALL" : String.valueOf(rule.deviceAidFilter),
                                    rule.cooldownMs / 1000L),
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
            });
        });
    }

    private void toggleRuleIfNeeded(int position) {
        if (position < 0 || position >= cachedRules.size()) return;
        Models.Rule rule = cachedRules.get(position);
        boolean next = !rule.enabled;
        executor.execute(() -> {
            repository.toggleRule(rule.id, next);
            repository.logOperation("TOGGLE_RULE", "ruleId=" + rule.id + " enabled=" + next);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(),
                        getString(R.string.mirror_rules_toggle_format,
                                next ? getString(R.string.mirror_rules_state_enabled)
                                     : getString(R.string.mirror_rules_state_disabled)),
                        Toast.LENGTH_SHORT).show();
                load();
            });
        });
    }

    private boolean deleteRuleIfNeeded(int position) {
        if (position < 0 || position >= cachedRules.size()) return false;
        Models.Rule rule = cachedRules.get(position);
        executor.execute(() -> {
            repository.deleteRule(rule.id);
            repository.logOperation("DELETE_RULE", "ruleId=" + rule.id + " name=" + rule.name);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(),
                        getString(R.string.mirror_rules_delete_ok, rule.name), Toast.LENGTH_SHORT).show();
                load();
            });
        });
        return true;
    }

    // ── Enhanced rule creation dialog ─────────────────────────────────────────
    private void showCreateRuleDialog() {
        int dp = (int) (getResources().getDisplayMetrics().density);

        // Sensor ID
        EditText etSensor = new EditText(requireContext());
        etSensor.setHint(getString(R.string.mirror_rules_sensor_hint));
        etSensor.setText("TEMP");

        // Operator spinner
        Spinner spOperator = new Spinner(requireContext());
        spOperator.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{">", "<", ">=", "<=", "==", "!="}));

        // Threshold
        EditText etThreshold = new EditText(requireContext());
        etThreshold.setHint(getString(R.string.mirror_rules_threshold_hint));
        etThreshold.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etThreshold.setText("50");

        // Alert level spinner
        Spinner spLevel = new Spinner(requireContext());
        spLevel.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Critical (2)", "Warning (1)", "Info (0)"}));

        // Cooldown
        EditText etCooldown = new EditText(requireContext());
        etCooldown.setHint(getString(R.string.rules_dialog_cooldown_hint));
        etCooldown.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etCooldown.setText("60");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = 16 * dp;
        layout.setPadding(pad, pad, pad, 0);

        addLabel(layout, getString(R.string.mirror_rules_sensor_hint));
        layout.addView(etSensor);
        addLabel(layout, getString(R.string.rules_dialog_operator_label));
        layout.addView(spOperator);
        addLabel(layout, getString(R.string.mirror_rules_threshold_hint));
        layout.addView(etThreshold);
        addLabel(layout, getString(R.string.rules_dialog_level_label));
        layout.addView(spLevel);
        addLabel(layout, getString(R.string.rules_dialog_cooldown_hint));
        layout.addView(etCooldown);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mirror_rules_dialog_title)
                .setView(layout)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_create, (dialog, which) -> {
                    String sensor    = etSensor.getText().toString().trim();
                    String operator  = (String) spOperator.getSelectedItem();
                    int    levelIdx  = spLevel.getSelectedItemPosition();
                    int    alertLevel = 2 - levelIdx; // Critical=2, Warning=1, Info=0
                    double threshold;
                    long   cooldown;
                    try { threshold = Double.parseDouble(etThreshold.getText().toString().trim()); }
                    catch (Exception e) { threshold = 50; }
                    try { cooldown  = Long.parseLong(etCooldown.getText().toString().trim()) * 1000L; }
                    catch (Exception e) { cooldown  = 60_000L; }

                    Models.Rule rule = new Models.Rule();
                    rule.name         = sensor + " " + operator + " " + UiFormatters.trimNumber(threshold);
                    rule.sensorIdFilter = sensor.isEmpty() ? null : sensor;
                    rule.operator     = operator;
                    rule.threshold    = threshold;
                    rule.cooldownMs   = cooldown;
                    rule.actionType   = "create_alert";
                    rule.actionPayload = "{\"level\":" + alertLevel + "}";

                    executor.execute(() -> {
                        repository.saveRule(rule);
                        repository.logOperation("CREATE_RULE", "name=" + rule.name
                                + " op=" + rule.operator + " threshold=" + rule.threshold);
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(this::load);
                    });
                })
                .show();
    }

    private void addLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }
}

