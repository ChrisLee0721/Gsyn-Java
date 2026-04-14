package com.opensynaptic.gsynjava.ui.common;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.card.MaterialCardView;
import com.opensynaptic.gsynjava.R;

import java.util.ArrayList;
import java.util.List;

public class CardRowAdapter extends BaseAdapter {
    public static class Row {
        public final String title;
        public final String subtitle;
        public final String meta;
        public final String badge;
        public final int badgeColor;
        public final int badgeTextColor;

        public Row(String title, String subtitle, String meta, String badge, @ColorInt int badgeColor, @ColorInt int badgeTextColor) {
            this.title = title;
            this.subtitle = subtitle;
            this.meta = meta;
            this.badge = badge;
            this.badgeColor = badgeColor;
            this.badgeTextColor = badgeTextColor;
        }
    }

    private final LayoutInflater inflater;
    private final List<Row> rows = new ArrayList<>();

    public CardRowAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setRows(List<Row> items) {
        rows.clear();
        if (items != null) rows.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_card_row, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        Row row = getItem(position);
        holder.title.setText(row.title);
        bindText(holder.subtitle, row.subtitle);
        bindText(holder.meta, row.meta);
        bindBadge(holder.badge, row.badge, row.badgeColor, row.badgeTextColor);
        holder.card.setStrokeColor(ColorUtils.setAlphaComponent(row.badgeColor, 110));
        return convertView;
    }

    private void bindText(TextView view, String text) {
        if (text == null || text.trim().isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(text);
        }
    }

    private void bindBadge(TextView view, String text, @ColorInt int bgColor, @ColorInt int textColor) {
        if (text == null || text.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(text);
        view.setTextColor(textColor);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ColorUtils.setAlphaComponent(bgColor, 50));
        bg.setCornerRadius(view.getResources().getDisplayMetrics().density * 14f);
        bg.setStroke(Math.max(1, (int) view.getResources().getDisplayMetrics().density), ColorUtils.setAlphaComponent(bgColor, 130));
        view.setBackground(bg);
    }

    private static class ViewHolder {
        final MaterialCardView card;
        final TextView title;
        final TextView subtitle;
        final TextView meta;
        final TextView badge;

        ViewHolder(View view) {
            card = view.findViewById(R.id.cardRoot);
            title = view.findViewById(R.id.tvTitle);
            subtitle = view.findViewById(R.id.tvSubtitle);
            meta = view.findViewById(R.id.tvMeta);
            badge = view.findViewById(R.id.tvBadge);
        }
    }
}

