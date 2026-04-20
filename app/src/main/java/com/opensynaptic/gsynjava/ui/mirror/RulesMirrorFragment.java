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
        button.setText("新建快速规则");
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

        tvSectionLabel.setText("Rules Mirror");
        tvSummary.setText("规则数 " + rules.size() + " · 最近操作日志 " + logs.size() + "。点击规则切换启停，长按删除。") ;
        tvDetail.setText("保持与原版 rules 页面一致的重点：阈值条件、动作类型、启停状态、操作记录。") ;
        tvEmpty.setText("暂无规则，点击上方按钮创建一条快速阈值规则。") ;

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
        Toast.makeText(requireContext(), "规则已切换为" + (!rule.enabled ? "启用" : "停用"), Toast.LENGTH_SHORT).show();
        load();
    }

    private boolean deleteRuleIfNeeded(int position) {
        if (position < 0 || position >= cachedRules.size()) return false;
        Models.Rule rule = cachedRules.get(position);
        repository.deleteRule(rule.id);
        repository.logOperation("DELETE_RULE", "ruleId=" + rule.id + " name=" + rule.name);
        Toast.makeText(requireContext(), "已删除规则: " + rule.name, Toast.LENGTH_SHORT).show();
        load();
        return true;
    }

    private void showCreateRuleDialog() {
        EditText sensorInput = new EditText(requireContext());
        sensorInput.setHint("传感器 ID，例如 TEMP");
        sensorInput.setText("TEMP");

        EditText thresholdInput = new EditText(requireContext());
        thresholdInput.setHint("阈值，例如 50");
        thresholdInput.setText("50");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);
        layout.addView(sensorInput);
        layout.addView(thresholdInput);

        new AlertDialog.Builder(requireContext())
                .setTitle("新建快速规则")
                .setMessage("创建一个最接近原版 rules 配置页的阈值规则。")
                .setView(layout)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> {
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


