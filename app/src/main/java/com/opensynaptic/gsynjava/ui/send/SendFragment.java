package com.opensynaptic.gsynjava.ui.send;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.core.AppThemeConfig;
import com.opensynaptic.gsynjava.core.protocol.PacketBuilder;
import com.opensynaptic.gsynjava.core.protocol.ProtocolConstants;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.databinding.FragmentSendBinding;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SendFragment extends Fragment {
    private FragmentSendBinding binding;
    private final List<String> logs = new ArrayList<>();
    private final List<MultiSensorRow> multiRows = new ArrayList<>();

    /** Holds refs to the 3 dynamically-added spinner/edittext views in a multi-sensor row. */
    private static class MultiSensorRow {
        final View root;
        final Spinner spSid;
        final Spinner spUnit;
        final Spinner spState;
        final TextInputEditText etVal;

        MultiSensorRow(View root, Spinner sid, Spinner unit, Spinner state, TextInputEditText val) {
            this.root    = root;
            this.spSid   = sid;
            this.spUnit  = unit;
            this.spState = state;
            this.etVal   = val;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSendBinding.inflate(inflater, container, false);
        AppThemeConfig.applyBgToRoot(binding.getRoot(), requireContext());

        setupDeviceSpinner();
        setupDataTabSpinners();
        setupTabs();
        setupControlButtons();
        setupDataButtons();
        setupRawButton();
        setupCmdRef();
        updateRouteSummary();

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ── Device spinner ─────────────────────────────────────────────────────

    private void setupDeviceSpinner() {
        List<Models.Device> devices = AppController.get(requireContext()).repository().getAllDevices();
        List<String> labels = new ArrayList<>();
        labels.add("手动输入 (不自动填充)");
        for (Models.Device d : devices) {
            labels.add("AID " + d.aid + (d.name != null && !d.name.isEmpty() ? "  " + d.name : ""));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, labels);
        binding.spinnerDevice.setAdapter(adapter);
        binding.spinnerDevice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos > 0 && pos - 1 < devices.size()) {
                    Models.Device d = devices.get(pos - 1);
                    setText(binding.etAid, String.valueOf(d.aid));
                }
                updateRouteSummary();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }

    // ── Data-tab dropdowns ─────────────────────────────────────────────────

    private void setupDataTabSpinners() {
        // Sensor IDs
        ArrayAdapter<String> sidAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item,
                ProtocolConstants.OS_SENSOR_IDS.toArray(new String[0]));
        binding.spinnerSensorId.setAdapter(sidAdapter);
        binding.spinnerSensorId.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                String sid = ProtocolConstants.OS_SENSOR_IDS.get(pos);
                String defUnit = ProtocolConstants.defaultUnitFor(sid);
                if (!defUnit.isEmpty()) {
                    int idx = ProtocolConstants.OS_UNITS.indexOf(defUnit);
                    if (idx >= 0) binding.spinnerUnit.setSelection(idx);
                }
                updateSinglePreview();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        // Units
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item,
                ProtocolConstants.OS_UNITS.toArray(new String[0]));
        binding.spinnerUnit.setAdapter(unitAdapter);
        binding.spinnerUnit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { updateSinglePreview(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        // States
        ArrayAdapter<String> stateAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item,
                ProtocolConstants.OS_STATES.toArray(new String[0]));
        binding.spinnerState.setAdapter(stateAdapter);
    }

    // ── Tab switching ──────────────────────────────────────────────────────

    private void setupTabs() {
        TabLayout tabs = binding.tabLayout;
        tabs.addTab(tabs.newTab().setText("控制"));
        tabs.addTab(tabs.newTab().setText("数据"));
        tabs.addTab(tabs.newTab().setText("原始"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { switchTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        switchTab(0);
    }

    private void switchTab(int pos) {
        binding.tabControl.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
        binding.tabData.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
        binding.tabRaw.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
    }

    // ── Control tab buttons ────────────────────────────────────────────────

    private void setupControlButtons() {
        binding.btnSendPing.setOnClickListener(v ->
                sendAndLog("PING", PacketBuilder.buildPing(parseInt(textOf(binding.etSeq), 0))));
        binding.btnSendPong.setOnClickListener(v ->
                sendAndLog("PONG", PacketBuilder.buildPong(parseInt(textOf(binding.etSeq), 0))));
        binding.btnSendIdRequest.setOnClickListener(v ->
                sendAndLog("ID_REQUEST", PacketBuilder.buildIdRequest(parseInt(textOf(binding.etSeq), 0))));
        binding.btnSendIdAssign.setOnClickListener(v ->
                sendAndLog("ID_ASSIGN", PacketBuilder.buildIdAssign(parseInt(textOf(binding.etAid), 1))));
        binding.btnSendTimeRequest.setOnClickListener(v ->
                sendAndLog("TIME_REQUEST", PacketBuilder.buildTimeRequest(parseInt(textOf(binding.etSeq), 0))));
        binding.btnSendHsAck.setOnClickListener(v ->
                sendAndLog("HANDSHAKE_ACK", PacketBuilder.buildHandshakeAck(parseInt(textOf(binding.etSeq), 0))));
        binding.btnSendHsNack.setOnClickListener(v ->
                sendAndLog("HANDSHAKE_NACK", PacketBuilder.buildHandshakeNack(parseInt(textOf(binding.etSeq), 0))));
        binding.btnSendSecureDict.setOnClickListener(v ->
                sendAndLog("SECURE_DICT_READY", PacketBuilder.buildSecureDictReady(parseInt(textOf(binding.etSeq), 0))));
    }

    // ── Data tab buttons ───────────────────────────────────────────────────

    private void setupDataButtons() {
        // Single sensor
        binding.btnSendSingle.setOnClickListener(v -> {
            int aid = parseInt(textOf(binding.etAid), 1);
            int tid = parseInt(textOf(binding.etTid), 1);
            String sid   = (String) binding.spinnerSensorId.getSelectedItem();
            String unit  = (String) binding.spinnerUnit.getSelectedItem();
            String state = (String) binding.spinnerState.getSelectedItem();
            double val   = parseDouble(textOf(binding.etValue), 0.0);
            byte[] frame = PacketBuilder.buildSensorPacket(aid, tid,
                    System.currentTimeMillis() / 1000L, sid, unit, val);
            sendAndLog("DATA_FULL[" + sid + "]", frame);
            updateSinglePreview();
        });

        // Multi-sensor: add row
        binding.btnAddSensor.setOnClickListener(v -> addMultiSensorRow());

        // Multi-sensor: send
        binding.btnSendMulti.setOnClickListener(v -> {
            if (multiRows.isEmpty()) {
                Toast.makeText(requireContext(), "请先添加传感器行", Toast.LENGTH_SHORT).show();
                return;
            }
            int aid = parseInt(textOf(binding.etAid), 1);
            int tid = parseInt(textOf(binding.etTid), 1);
            long ts = System.currentTimeMillis() / 1000L;
            List<PacketBuilder.SensorEntry> entries = new ArrayList<>();
            for (MultiSensorRow row : multiRows) {
                String sid   = (String) row.spSid.getSelectedItem();
                String unit  = (String) row.spUnit.getSelectedItem();
                String state = (String) row.spState.getSelectedItem();
                double val   = parseDouble(row.etVal.getText() != null ? row.etVal.getText().toString() : "0", 0.0);
                entries.add(new PacketBuilder.SensorEntry(sid, unit, state, val));
            }
            byte[] frame = PacketBuilder.buildMultiSensorPacket(aid, tid, ts, entries);
            sendAndLog("DATA_FULL[multi×" + entries.size() + "]", frame);
        });
    }

    private void addMultiSensorRow() {
        if (binding == null) return;
        // Build a horizontal row: SID spinner + unit spinner + state spinner + value EditText + remove button
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 0);

        Spinner spSid   = makeSpinner(ProtocolConstants.OS_SENSOR_IDS.toArray(new String[0]));
        Spinner spUnit  = makeSpinner(ProtocolConstants.OS_UNITS.toArray(new String[0]));
        Spinner spState = makeSpinner(ProtocolConstants.OS_STATES.toArray(new String[0]));

        TextInputEditText etVal = new TextInputEditText(requireContext());
        etVal.setHint("值");
        etVal.setText("0.0");
        LinearLayout.LayoutParams valParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        valParams.setMarginStart(6);
        etVal.setLayoutParams(valParams);
        etVal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);

        com.google.android.material.button.MaterialButton btnRemove = new com.google.android.material.button.MaterialButton(requireContext());
        btnRemove.setText("✕");
        btnRemove.setTextSize(10);
        LinearLayout.LayoutParams rmParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rmParams.setMarginStart(4);
        btnRemove.setLayoutParams(rmParams);

        // Auto-select default unit when sensor ID changes
        spSid.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                String defUnit = ProtocolConstants.defaultUnitFor(ProtocolConstants.OS_SENSOR_IDS.get(pos));
                if (!defUnit.isEmpty()) {
                    int idx = ProtocolConstants.OS_UNITS.indexOf(defUnit);
                    if (idx >= 0) spUnit.setSelection(idx);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(0, (int)(48 * getResources().getDisplayMetrics().density), 1f);
        spSid.setLayoutParams(spinnerParams);
        spUnit.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(48 * getResources().getDisplayMetrics().density), 1f));
        spState.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(48 * getResources().getDisplayMetrics().density), 0.6f));

        row.addView(spSid);
        row.addView(spUnit);
        row.addView(spState);
        row.addView(etVal);
        row.addView(btnRemove);

        MultiSensorRow msr = new MultiSensorRow(row, spSid, spUnit, spState, etVal);
        multiRows.add(msr);
        binding.containerMultiSensor.addView(row);

        btnRemove.setOnClickListener(v -> {
            multiRows.remove(msr);
            binding.containerMultiSensor.removeView(row);
        });
    }

    private Spinner makeSpinner(String[] items) {
        Spinner sp = new Spinner(requireContext());
        sp.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, items));
        return sp;
    }

    // ── Raw tab ────────────────────────────────────────────────────────────

    private void setupRawButton() {
        binding.btnSendRaw.setOnClickListener(v -> {
            String hex = textOf(binding.etRaw).trim();
            if (hex.isEmpty()) {
                Toast.makeText(requireContext(), "请输入 Raw HEX", Toast.LENGTH_SHORT).show();
                return;
            }
            byte[] frame = PacketBuilder.buildRawHex(hex);
            sendAndLog("RAW_HEX", frame);
        });
    }

    private void setupCmdRef() {
        if (binding == null) return;
        binding.tvCmdRef.setText(
                "CMD  |  HEX  |  描述\n" +
                "─────────────────────────────────────────\n" +
                "PING              | 0x01 | 链路探测\n" +
                "PONG              | 0x02 | 探测回复\n" +
                "ID_REQUEST        | 0x03 | 申请 AID\n" +
                "ID_RESPONSE       | 0x04 | AID 响应\n" +
                "ID_ASSIGN         | 0x05 | 分配 AID\n" +
                "TIME_REQUEST      | 0x07 | 时间同步请求\n" +
                "TIME_RESPONSE     | 0x08 | 时间同步响应\n" +
                "HANDSHAKE_ACK     | 0x09 | 握手确认\n" +
                "HANDSHAKE_NACK    | 0x0A | 握手拒绝\n" +
                "DATA_FULL         | 0x20 | 完整数据帧\n" +
                "DATA_DIFF         | 0x21 | 差量数据帧\n" +
                "DATA_HEART        | 0x22 | 心跳帧(无载荷)\n" +
                "DATA_FULL_SEC     | 0x23 | 加密完整帧\n" +
                "DATA_DIFF_SEC     | 0x24 | 加密差量帧\n" +
                "DATA_HEART_SEC    | 0x25 | 加密心跳帧\n" +
                "SECURE_DICT_READY | 0x10 | 密钥就绪通知\n" +
                "─────────────────────────────────────────\n" +
                "Body: {aid}.{state}.{ts_b64}|{sid}>{state}.{unit}:{b62}|…\n" +
                "Header: CMD(1) routeCount(1) AID(4) TID(1) TS(6) → body → CRC8(1) CRC16(2)"
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void sendAndLog(String label, byte[] frame) {
        if (binding == null) return;
        if (frame == null) {
            Toast.makeText(requireContext(), "命令构建失败", Toast.LENGTH_SHORT).show();
            return;
        }
        int aid  = parseInt(textOf(binding.etAid), 1);
        String host = textOf(binding.etIp).trim();
        int port = parseInt(textOf(binding.etPort), 9876);
        boolean ok = AppController.get(requireContext()).transport()
                .sendCommand(frame, aid, host, port);
        AppController.get(requireContext()).repository()
                .logOperation("SEND_CMD", label + " → AID:" + aid + " " + host + ":" + port + " ok=" + ok);
        String entry = DateFormat.getTimeInstance().format(new Date())
                + "  " + label + "  " + (ok ? "✓ OK" : "✗ FAIL") + "  len=" + frame.length;
        logs.add(0, entry);
        if (logs.size() > 20) logs.remove(logs.size() - 1);
        binding.tvLastResult.setText("最近: " + entry);
        binding.tvLog.setText(android.text.TextUtils.join("\n", logs));
        Toast.makeText(requireContext(), ok ? "发送成功" : "发送失败", Toast.LENGTH_SHORT).show();
        updateRouteSummary();
    }

    private void updateRouteSummary() {
        if (binding == null) return;
        binding.tvRouteSummary.setText(String.format(Locale.getDefault(),
                "路由：AID %s · TID %s · SEQ %s · %s:%s",
                textOf(binding.etAid), textOf(binding.etTid), textOf(binding.etSeq),
                textOf(binding.etIp), textOf(binding.etPort)));
    }

    private void updateSinglePreview() {
        if (binding == null || binding.spinnerSensorId.getSelectedItem() == null) return;
        String sid   = (String) binding.spinnerSensorId.getSelectedItem();
        String unit  = (String) binding.spinnerUnit.getSelectedItem();
        String state = (String) binding.spinnerState.getSelectedItem();
        String val   = textOf(binding.etValue);
        binding.tvSinglePreview.setText(
                textOf(binding.etAid) + "." + state + ".{ts}|" + sid + ">" + state + "." + unit + ":{b62}|  val=" + val);
    }

    private void setText(android.widget.EditText et, String text) {
        if (et != null) et.setText(text);
    }

    private String textOf(android.widget.TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString();
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; }
    }

    private double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value.trim()); } catch (Exception e) { return fallback; }
    }
}

