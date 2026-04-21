package com.opensynaptic.gsynjava.ui.dashboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores an ordered list of DashboardCardItems in SharedPreferences as JSON.
 * Supports drag-to-reorder, visibility toggles, and custom sensor cards.
 */
public final class DashboardCardConfig {

    private static final String PREFS_NAME  = "dashboard_card_prefs";
    private static final String KEY_CARDS   = "cards_json_v2";

    private static final String K_TYPE      = "type";
    private static final String K_VISIBLE   = "visible";
    private static final String K_ORDER     = "order";
    private static final String K_SENSOR_ID = "sensorId";
    private static final String K_LABEL     = "label";

    public List<DashboardCardItem> cards = new ArrayList<>();

    private DashboardCardConfig() {}

    public static List<DashboardCardItem> defaultCards() {
        List<DashboardCardItem> list = new ArrayList<>();
        list.add(new DashboardCardItem(DashboardCardItem.Type.HEADER,          0));
        list.add(new DashboardCardItem(DashboardCardItem.Type.KPI_ROW1,        1));
        list.add(new DashboardCardItem(DashboardCardItem.Type.KPI_ROW2,        2));
        list.add(new DashboardCardItem(DashboardCardItem.Type.KPI_ROW3,        3));
        list.add(new DashboardCardItem(DashboardCardItem.Type.GAUGES,          4));
        list.add(new DashboardCardItem(DashboardCardItem.Type.CHARTS,          5));
        list.add(new DashboardCardItem(DashboardCardItem.Type.ACTIVITY,        6));
        list.add(new DashboardCardItem(DashboardCardItem.Type.LATEST_READINGS, 7));
        return list;
    }

    public static DashboardCardConfig load(Context ctx) {
        DashboardCardConfig cfg = new DashboardCardConfig();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CARDS, null);
        if (json == null) {
            cfg.cards = defaultCards();
            return cfg;
        }
        try {
            JSONArray arr = new JSONArray(json);
            List<DashboardCardItem> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                DashboardCardItem item = new DashboardCardItem();
                try {
                    item.type = DashboardCardItem.Type.valueOf(o.getString(K_TYPE));
                } catch (IllegalArgumentException e) { continue; }
                item.visible  = o.optBoolean(K_VISIBLE, true);
                item.order    = o.optInt(K_ORDER, i);
                item.sensorId = o.optString(K_SENSOR_ID, "");
                item.label    = o.optString(K_LABEL, item.sensorId);
                list.add(item);
            }
            ensureHeader(list);
            cfg.cards = list;
        } catch (JSONException e) {
            cfg.cards = defaultCards();
        }
        return cfg;
    }

    public void save(Context ctx) {
        for (int i = 0; i < cards.size(); i++) cards.get(i).order = i;
        JSONArray arr = new JSONArray();
        for (DashboardCardItem item : cards) {
            JSONObject o = new JSONObject();
            try {
                o.put(K_TYPE,      item.type.name());
                o.put(K_VISIBLE,   item.visible);
                o.put(K_ORDER,     item.order);
                o.put(K_SENSOR_ID, item.sensorId);
                o.put(K_LABEL,     item.label);
            } catch (JSONException ignored) {}
            arr.put(o);
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CARDS, arr.toString()).apply();
    }

    public List<DashboardCardItem> visibleCards() {
        List<DashboardCardItem> result = new ArrayList<>();
        for (DashboardCardItem c : cards) if (c.visible) result.add(c);
        Collections.sort(result, (a, b) -> Integer.compare(a.order, b.order));
        return result;
    }

    public boolean isVisible(DashboardCardItem.Type type) {
        for (DashboardCardItem c : cards) if (c.type == type) return c.visible;
        return true;
    }

    public void setVisible(DashboardCardItem.Type type, boolean visible) {
        for (DashboardCardItem c : cards) {
            if (c.type == type) { c.visible = visible; return; }
        }
    }

    public void moveCard(int fromPos, int toPos) {
        if (fromPos == toPos) return;
        DashboardCardItem item = cards.remove(fromPos);
        cards.add(toPos, item);
    }

    public void addCustomSensor(String sensorId, String label) {
        cards.add(DashboardCardItem.customSensor(sensorId, label, cards.size()));
    }

    public void removeCard(DashboardCardItem item) { cards.remove(item); }

    private static void ensureHeader(List<DashboardCardItem> list) {
        boolean has = false;
        for (DashboardCardItem c : list) if (c.type == DashboardCardItem.Type.HEADER) { has = true; break; }
        if (!has) { list.add(0, new DashboardCardItem(DashboardCardItem.Type.HEADER, 0)); return; }
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).type == DashboardCardItem.Type.HEADER) {
                list.add(0, list.remove(i)); break;
            }
        }
    }
}
