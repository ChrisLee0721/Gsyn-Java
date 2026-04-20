package com.opensynaptic.gsynjava.ui.dashboard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Java mirror of Flutter DashboardCardConfig.
 * Stores 7 card visibility toggles in SharedPreferences.
 * Keys mirror the Flutter "card_*" scheme.
 */
public final class DashboardCardConfig {

    private static final String PREFS_NAME = "dashboard_card_prefs";
    private static final String KEY_KPI_ROW1     = "card_kpi_row1";
    private static final String KEY_KPI_ROW2     = "card_kpi_row2";
    private static final String KEY_KPI_ROW3     = "card_kpi_row3";
    private static final String KEY_GAUGES       = "card_gauges";
    private static final String KEY_CHARTS       = "card_charts";
    private static final String KEY_ACTIVITY     = "card_activity";
    private static final String KEY_READINGS     = "card_readings";

    public boolean showKpiRow1;     // total devices + online rate
    public boolean showKpiRow2;     // active alerts + throughput
    public boolean showKpiRow3;     // enabled rules + total messages
    public boolean showGauges;      // live metrics / gauge snapshot
    public boolean showCharts;      // temperature + humidity trend charts
    public boolean showActivityFeed;// recent alerts + operations summary
    public boolean showLatestReadings; // latest sensor readings

    private DashboardCardConfig() {}

    public static DashboardCardConfig defaults() {
        return new DashboardCardConfig();
    }

    public static DashboardCardConfig load(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        DashboardCardConfig cfg = new DashboardCardConfig();
        cfg.showKpiRow1          = prefs.getBoolean(KEY_KPI_ROW1, true);
        cfg.showKpiRow2          = prefs.getBoolean(KEY_KPI_ROW2, true);
        cfg.showKpiRow3          = prefs.getBoolean(KEY_KPI_ROW3, true);
        cfg.showGauges           = prefs.getBoolean(KEY_GAUGES,   true);
        cfg.showCharts           = prefs.getBoolean(KEY_CHARTS,   true);
        cfg.showActivityFeed     = prefs.getBoolean(KEY_ACTIVITY, true);
        cfg.showLatestReadings   = prefs.getBoolean(KEY_READINGS, true);
        return cfg;
    }

    public void save(Context ctx) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_KPI_ROW1, showKpiRow1)
                .putBoolean(KEY_KPI_ROW2, showKpiRow2)
                .putBoolean(KEY_KPI_ROW3, showKpiRow3)
                .putBoolean(KEY_GAUGES,   showGauges)
                .putBoolean(KEY_CHARTS,   showCharts)
                .putBoolean(KEY_ACTIVITY, showActivityFeed)
                .putBoolean(KEY_READINGS, showLatestReadings)
                .apply();
    }
}

