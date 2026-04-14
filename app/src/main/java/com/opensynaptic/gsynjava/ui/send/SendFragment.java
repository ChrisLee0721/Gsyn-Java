package com.opensynaptic.gsynjava.ui.send;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.core.protocol.PacketBuilder;
import com.opensynaptic.gsynjava.databinding.FragmentSendBinding;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SendFragment extends Fragment {
    private FragmentSendBinding binding;
    private final List<String> logs = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSendBinding.inflate(inflater, container, false);
        binding.spinnerCommand.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, new String[] {"PING", "PONG", "ID_REQUEST", "TIME_REQUEST", "ID_ASSIGN", "DATA_FULL_SENSOR", "RAW_HEX"}));
        binding.spinnerCommand.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateCommandInfo(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateCommandInfo(); }
        };
        binding.etAid.addTextChangedListener(watcher);
        binding.etTid.addTextChangedListener(watcher);
        binding.etSeq.addTextChangedListener(watcher);
        binding.etIp.addTextChangedListener(watcher);
        binding.etPort.addTextChangedListener(watcher);
        binding.etSensorId.addTextChangedListener(watcher);
        binding.etUnit.addTextChangedListener(watcher);
        binding.etValue.addTextChangedListener(watcher);
        binding.etRaw.addTextChangedListener(watcher);
        binding.btnSend.setOnClickListener(v -> sendSelected());
        updateCommandInfo();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void sendSelected() {
        if (binding == null) return;
        String cmd = String.valueOf(binding.spinnerCommand.getSelectedItem());
        int aid = parseInt(textOf(binding.etAid), 1);
        int tid = parseInt(textOf(binding.etTid), 1);
        int seq = parseInt(textOf(binding.etSeq), 0);
        String host = textOf(binding.etIp).trim();
        int port = parseInt(textOf(binding.etPort), 9876);
        byte[] frame;
        switch (cmd) {
            case "PING": frame = PacketBuilder.buildPing(seq); break;
            case "PONG": frame = PacketBuilder.buildPong(seq); break;
            case "ID_REQUEST": frame = PacketBuilder.buildIdRequest(seq); break;
            case "TIME_REQUEST": frame = PacketBuilder.buildTimeRequest(seq); break;
            case "ID_ASSIGN": frame = PacketBuilder.buildIdAssign(aid); break;
            case "DATA_FULL_SENSOR":
                frame = PacketBuilder.buildSensorPacket(aid, tid, System.currentTimeMillis() / 1000L,
                        textOf(binding.etSensorId).trim(),
                        textOf(binding.etUnit).trim(),
                        parseDouble(textOf(binding.etValue), 0.0));
                break;
            case "RAW_HEX":
                frame = PacketBuilder.buildRawHex(textOf(binding.etRaw));
                break;
            default:
                frame = null;
        }
        if (frame == null) {
            Toast.makeText(requireContext(), "命令构建失败，请检查输入", Toast.LENGTH_LONG).show();
            return;
        }
        boolean ok = AppController.get(requireContext()).transport().sendCommand(frame, aid, host, port);
        AppController.get(requireContext()).repository().logOperation("SEND_CMD", cmd + " -> AID:" + aid + " host=" + host + ":" + port + " ok=" + ok);
        logs.add(0, DateFormat.getTimeInstance().format(new Date()) + "  " + cmd + "  " + (ok ? "OK" : "FAIL") + "  len=" + frame.length);
        if (logs.size() > 20) logs.remove(logs.size() - 1);
        binding.tvLog.setText(android.text.TextUtils.join("\n", logs));
        binding.tvLastResult.setText(String.format(Locale.getDefault(), "最近一次发送：%s · %s · %s · payload=%d bytes", DateFormat.getDateTimeInstance().format(new Date()), cmd, ok ? "成功" : "失败", frame.length));
        updateCommandInfo();
        Toast.makeText(requireContext(), ok ? "发送成功" : "发送失败", Toast.LENGTH_SHORT).show();
    }

    private void updateCommandInfo() {
        if (binding == null) return;
        String cmd = String.valueOf(binding.spinnerCommand.getSelectedItem());
        String hint;
        String preview;
        switch (cmd) {
            case "PING":
                hint = "发送链路探测帧，目标节点应返回 PONG。";
                preview = "预览：PING(seq=" + binding.etSeq.getText() + ")";
                break;
            case "PONG":
                hint = "回复心跳探测，通常用于握手或回路验证。";
                preview = "预览：PONG(seq=" + binding.etSeq.getText() + ")";
                break;
            case "ID_REQUEST":
                hint = "请求服务器为节点分配 AID。";
                preview = "预览：ID_REQUEST(seq=" + binding.etSeq.getText() + ")";
                break;
            case "TIME_REQUEST":
                hint = "请求时间同步。";
                preview = "预览：TIME_REQUEST(seq=" + binding.etSeq.getText() + ")";
                break;
            case "ID_ASSIGN":
                hint = "为目标设备写入指定 AID。";
                preview = "预览：ID_ASSIGN(aid=" + binding.etAid.getText() + ")";
                break;
            case "DATA_FULL_SENSOR":
                hint = "构建完整单传感器数据帧，最接近原版 send 页面默认数据发送流程。";
                preview = "预览：" + binding.etAid.getText() + ".U.{ts}|" + binding.etSensorId.getText() + ">U." + binding.etUnit.getText() + ":{b62}|";
                break;
            case "RAW_HEX":
            default:
                hint = "直接发送原始 HEX 帧，适合调试完整 OpenSynaptic 指令集。";
                preview = "预览：" + binding.etRaw.getText();
                break;
        }
        binding.tvCommandHint.setText(hint);
        binding.tvCommandPreview.setText(preview);
        binding.tvRouteSummary.setText(String.format(Locale.getDefault(), "路由：AID %s · TID %s · SEQ %s · %s:%s", textOf(binding.etAid), textOf(binding.etTid), textOf(binding.etSeq), textOf(binding.etIp), textOf(binding.etPort)));
        binding.tvPayloadSummary.setText(buildPayloadSummary(cmd));
        if (logs.isEmpty()) {
            binding.tvLog.setText("发送日志\n\n尚未下发命令，可先在这里构建控制帧、数据帧或 Raw HEX 调试报文。");
        }
    }

    private String buildPayloadSummary(String cmd) {
        if ("DATA_FULL_SENSOR".equals(cmd)) {
            return String.format(Locale.getDefault(), "载荷：%s / %s / value=%s", textOf(binding.etSensorId), textOf(binding.etUnit), textOf(binding.etValue));
        }
        if ("RAW_HEX".equals(cmd)) {
            String raw = textOf(binding.etRaw).trim();
            return "载荷：Raw HEX " + (raw.isEmpty() ? "未填写" : raw);
        }
        return "载荷：系统命令帧 · " + cmd + " · 适合链路/注册/时间同步调试";
    }

    private String textOf(android.widget.TextView view) {
        return view.getText() == null ? "" : view.getText().toString();
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; }
    }

    private double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value.trim()); } catch (Exception e) { return fallback; }
    }
}

