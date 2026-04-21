package com.opensynaptic.gsynjava.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;
import com.opensynaptic.gsynjava.ui.widget.MiniTrendChartView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RecyclerView adapter for the configurable Dashboard card list.
 * Supports 8 built-in card types + unlimited CUSTOM_SENSOR cards.
 * Cards can be drag-reordered (except HEADER which is always first).
 */
public class DashboardCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // View type constants
    static final int TYPE_HEADER         = 0;
    static final int TYPE_KPI_ROW1       = 1;
    static final int TYPE_KPI_ROW2       = 2;
    static final int TYPE_KPI_ROW3       = 3;
    static final int TYPE_GAUGES         = 4;
    static final int TYPE_CHARTS         = 5;
    static final int TYPE_ACTIVITY       = 6;
    static final int TYPE_LATEST_READINGS= 7;
    static final int TYPE_CUSTOM_SENSOR  = 8;

    /** Live data snapshot populated by DashboardFragment.refresh(). */
    public static class Snapshot {
        public int totalDevices, online, alerts, rules;
        public long totalMessages;
        public int throughput;
        public double latestTemp, latestHum, latestPressure, latestLevel;
        public int readingCount;
        public long latestSampleMs;
        public String subtitle     = "";
        public String syncStatus   = "";
        public String transportStatus = "";
        public String recentAlertsSummary = "";
        public String opsSummary   = "";
        public String latestReadingsText  = "";
        public boolean singleModeEnabled  = false;        public List<Float> tempTrend = new ArrayList<>();
        public List<Float> humTrend  = new ArrayList<>();
        /** Latest reading per sensorId (case-insensitive key) */
        public Map<String, Models.SensorData> latestBySensorId;
        /** Latest readings history per sensorId for trend chart */
        public Map<String, List<Float>> trendBySensorId;
    }

    public interface Listener {
        void onToggleSingleMode();
        void onItemMoved(int fromPos, int toPos);
        void onRemoveCustomCard(int adapterPosition, DashboardCardItem item);
    }

    private final List<DashboardCardItem> items = new ArrayList<>();
    private Snapshot snapshot = new Snapshot();
    private Listener listener;

    public void setItems(List<DashboardCardItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void setSnapshot(Snapshot s) {
        this.snapshot = s;
        notifyDataSetChanged();
    }

    public void setListener(Listener l) { this.listener = l; }

    public DashboardCardItem getItem(int pos) { return items.get(pos); }

    /** Called by ItemTouchHelper during drag. */
    public void moveItem(int from, int to) {
        DashboardCardItem item = items.remove(from);
        items.add(to, item);
        notifyItemMoved(from, to);
        if (listener != null) listener.onItemMoved(from, to);
    }

    // ── Adapter overrides ─────────────────────────────────────────────────────

    @Override public int getItemCount() { return items.size(); }

    @Override
    public int getItemViewType(int position) {
        switch (items.get(position).type) {
            case HEADER:          return TYPE_HEADER;
            case KPI_ROW1:        return TYPE_KPI_ROW1;
            case KPI_ROW2:        return TYPE_KPI_ROW2;
            case KPI_ROW3:        return TYPE_KPI_ROW3;
            case GAUGES:          return TYPE_GAUGES;
            case CHARTS:          return TYPE_CHARTS;
            case ACTIVITY:        return TYPE_ACTIVITY;
            case LATEST_READINGS: return TYPE_LATEST_READINGS;
            case CUSTOM_SENSOR:   return TYPE_CUSTOM_SENSOR;
            default:              return TYPE_HEADER;
        }
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderVH(inf.inflate(R.layout.item_dashboard_header, parent, false));
            case TYPE_KPI_ROW1: case TYPE_KPI_ROW2: case TYPE_KPI_ROW3:
                return new KpiRowVH(inf.inflate(R.layout.item_dashboard_kpi_row, parent, false));
            case TYPE_GAUGES:
                return new GaugesVH(inf.inflate(R.layout.item_dashboard_gauges, parent, false));
            case TYPE_CHARTS:
                return new ChartsVH(inf.inflate(R.layout.item_dashboard_charts, parent, false));
            case TYPE_ACTIVITY:
                return new ActivityVH(inf.inflate(R.layout.item_dashboard_activity, parent, false));
            case TYPE_LATEST_READINGS:
                return new ReadingsVH(inf.inflate(R.layout.item_dashboard_readings, parent, false));
            case TYPE_CUSTOM_SENSOR:
                return new CustomSensorVH(inf.inflate(R.layout.item_dashboard_custom_sensor, parent, false));
            default:
                return new HeaderVH(inf.inflate(R.layout.item_dashboard_header, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int vt = getItemViewType(position);
        switch (vt) {
            case TYPE_HEADER:
                ((HeaderVH) holder).bind(snapshot, listener);
                break;
            case TYPE_KPI_ROW1:
                ((KpiRowVH) holder).bind(
                        holder.itemView.getContext().getString(R.string.dashboard_label_total_devices),
                        String.valueOf(snapshot.totalDevices),
                        holder.itemView.getContext().getString(R.string.dashboard_label_online_rate),
                        String.format(Locale.getDefault(), "%.1f%%",
                                snapshot.totalDevices == 0 ? 0 : snapshot.online * 100.0 / snapshot.totalDevices)
                );
                break;
            case TYPE_KPI_ROW2:
                ((KpiRowVH) holder).bind(
                        holder.itemView.getContext().getString(R.string.dashboard_label_active_alerts),
                        String.valueOf(snapshot.alerts),
                        holder.itemView.getContext().getString(R.string.dashboard_label_throughput),
                        String.format(Locale.getDefault(), "%d msg/s", snapshot.throughput)
                );
                break;
            case TYPE_KPI_ROW3:
                ((KpiRowVH) holder).bind(
                        holder.itemView.getContext().getString(R.string.dashboard_label_rules),
                        String.valueOf(snapshot.rules),
                        holder.itemView.getContext().getString(R.string.dashboard_label_cumulative),
                        String.valueOf(snapshot.totalMessages)
                );
                break;
            case TYPE_GAUGES:
                ((GaugesVH) holder).bind(snapshot);
                break;
            case TYPE_CHARTS:
                ((ChartsVH) holder).bind(snapshot);
                break;
            case TYPE_ACTIVITY:
                ((ActivityVH) holder).bind(snapshot);
                break;
            case TYPE_LATEST_READINGS:
                ((ReadingsVH) holder).bind(snapshot);
                break;
            case TYPE_CUSTOM_SENSOR:
                DashboardCardItem item = items.get(position);
                ((CustomSensorVH) holder).bind(item, snapshot, position);
                break;
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvSubtitle, tvSyncStatus, tvTransportStatus;
        final com.google.android.material.button.MaterialButton btnToggle;
        HeaderVH(View v) {
            super(v);
            tvSubtitle        = v.findViewById(R.id.tvSubtitle);
            tvSyncStatus      = v.findViewById(R.id.tvSyncStatus);
            tvTransportStatus = v.findViewById(R.id.tvTransportStatus);
            btnToggle         = v.findViewById(R.id.btnToggleSingleMode);
        }
        void bind(Snapshot s, Listener listener) {
            tvSubtitle.setText(s.subtitle);
            tvSyncStatus.setText(s.syncStatus);
            tvTransportStatus.setText(s.transportStatus);
            if (btnToggle != null) {
                btnToggle.setText(s.singleModeEnabled
                        ? itemView.getContext().getString(R.string.dashboard_single_mode_toggle_on)
                        : itemView.getContext().getString(R.string.dashboard_single_mode_toggle_off));
                btnToggle.setOnClickListener(v -> { if (listener != null) listener.onToggleSingleMode(); });
            }
        }
    }

    static class KpiRowVH extends RecyclerView.ViewHolder {
        final TextView tvLabelLeft, tvValueLeft, tvLabelRight, tvValueRight;
        KpiRowVH(View v) {
            super(v);
            tvLabelLeft  = v.findViewById(R.id.tvLabelLeft);
            tvValueLeft  = v.findViewById(R.id.tvValueLeft);
            tvLabelRight = v.findViewById(R.id.tvLabelRight);
            tvValueRight = v.findViewById(R.id.tvValueRight);
        }
        void bind(String labelL, String valueL, String labelR, String valueR) {
            tvLabelLeft.setText(labelL);
            tvValueLeft.setText(valueL);
            tvLabelRight.setText(labelR);
            tvValueRight.setText(valueR);
        }
    }

    static class GaugesVH extends RecyclerView.ViewHolder {
        final TextView tvLiveMetrics;
        final LinearProgressIndicator progressWater, progressHumidity;
        GaugesVH(View v) {
            super(v);
            tvLiveMetrics   = v.findViewById(R.id.tvLiveMetrics);
            progressWater   = v.findViewById(R.id.progressWater);
            progressHumidity= v.findViewById(R.id.progressHumidity);
        }
        void bind(Snapshot s) {
            tvLiveMetrics.setText(itemView.getContext().getString(R.string.dashboard_live_metrics_format,
                    UiFormatters.trimNumber(s.latestTemp),
                    UiFormatters.trimNumber(s.latestHum),
                    UiFormatters.trimNumber(s.latestPressure),
                    UiFormatters.trimNumber(s.latestLevel),
                    s.readingCount));
            progressWater.setProgressCompat((int) Math.max(0, Math.min(100, s.latestLevel)), true);
            progressHumidity.setProgressCompat((int) Math.max(0, Math.min(100, s.latestHum)), true);
        }
    }

    static class ChartsVH extends RecyclerView.ViewHolder {
        final MiniTrendChartView chartTemp, chartHumidity;
        ChartsVH(View v) {
            super(v);
            chartTemp     = v.findViewById(R.id.chartTemp);
            chartHumidity = v.findViewById(R.id.chartHumidity);
        }
        void bind(Snapshot s) {
            chartTemp.setChartColor(0xFFFF7043);
            chartHumidity.setChartColor(0xFF42A5F5);
            if (!s.tempTrend.isEmpty()) chartTemp.setSeries(s.tempTrend);
            if (!s.humTrend.isEmpty())  chartHumidity.setSeries(s.humTrend);
        }
    }

    static class ActivityVH extends RecyclerView.ViewHolder {
        final TextView tvRecentAlerts, tvOpsSummary;
        ActivityVH(View v) {
            super(v);
            tvRecentAlerts = v.findViewById(R.id.tvRecentAlerts);
            tvOpsSummary   = v.findViewById(R.id.tvOpsSummary);
        }
        void bind(Snapshot s) {
            tvRecentAlerts.setText(s.recentAlertsSummary);
            tvOpsSummary.setText(s.opsSummary);
        }
    }

    static class ReadingsVH extends RecyclerView.ViewHolder {
        final TextView tvLatestReadings;
        ReadingsVH(View v) {
            super(v);
            tvLatestReadings = v.findViewById(R.id.tvLatestReadings);
        }
        void bind(Snapshot s) { tvLatestReadings.setText(s.latestReadingsText); }
    }

    class CustomSensorVH extends RecyclerView.ViewHolder {
        final TextView tvSensorLabel, tvSensorId, tvSensorValue, tvSensorMeta;
        final MiniTrendChartView chartSensor;
        CustomSensorVH(View v) {
            super(v);
            tvSensorLabel = v.findViewById(R.id.tvSensorLabel);
            tvSensorId    = v.findViewById(R.id.tvSensorId);
            tvSensorValue = v.findViewById(R.id.tvSensorValue);
            tvSensorMeta  = v.findViewById(R.id.tvSensorMeta);
            chartSensor   = v.findViewById(R.id.chartSensor);
            v.findViewById(R.id.btnRemoveSensor).setOnClickListener(btn -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_ID && listener != null) {
                    listener.onRemoveCustomCard(pos, items.get(pos));
                }
            });
        }
        void bind(DashboardCardItem item, Snapshot s, int position) {
            tvSensorLabel.setText(item.label.isEmpty() ? item.sensorId : item.label);
            tvSensorId.setText("ID: " + item.sensorId);

            Models.SensorData data = null;
            if (s.latestBySensorId != null) {
                data = s.latestBySensorId.get(item.sensorId.toUpperCase());
                if (data == null) data = s.latestBySensorId.get(item.sensorId);
            }

            if (data != null) {
                tvSensorValue.setText(String.format(Locale.getDefault(), "%s %s",
                        UiFormatters.trimNumber(data.value), data.unit));
                tvSensorMeta.setText(itemView.getContext().getString(
                        R.string.dashboard_sensor_card_last_seen,
                        UiFormatters.formatRelativeTime(data.timestampMs)));
                // trend
                if (s.trendBySensorId != null) {
                    List<Float> trend = s.trendBySensorId.get(item.sensorId.toUpperCase());
                    if (trend == null) trend = s.trendBySensorId.get(item.sensorId);
                    if (trend != null && !trend.isEmpty()) {
                        chartSensor.setSeries(trend);
                        chartSensor.setVisibility(View.VISIBLE);
                    } else {
                        chartSensor.setVisibility(View.GONE);
                    }
                }
            } else {
                tvSensorValue.setText("—");
                tvSensorMeta.setText(itemView.getContext().getString(
                        R.string.dashboard_sensor_card_no_data));
                chartSensor.setVisibility(View.GONE);
            }
        }
    }
}

