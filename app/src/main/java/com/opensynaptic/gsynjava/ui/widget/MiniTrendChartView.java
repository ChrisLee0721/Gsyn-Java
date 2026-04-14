package com.opensynaptic.gsynjava.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MiniTrendChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> points = new ArrayList<>();
    private String title = "Trend";

    public MiniTrendChartView(Context context) {
        super(context);
        init();
    }

    public MiniTrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MiniTrendChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        linePaint.setColor(Color.parseColor("#5AC8FA"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setColor(Color.argb(40, 90, 200, 250));
        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(Color.argb(50, 255, 255, 255));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);

        pointPaint.setColor(linePaint.getColor());
        pointPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.argb(220, 255, 255, 255));
        textPaint.setTextSize(12f * density);
    }

    public void setSeries(List<Float> values) {
        points.clear();
        if (values != null) points.addAll(values);
        invalidate();
    }

    public void setChartColor(int color) {
        linePaint.setColor(color);
        fillPaint.setColor(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)));
        pointPaint.setColor(color);
        invalidate();
    }

    public void setTitle(String title) {
        this.title = title == null ? "Trend" : title;
        setContentDescription(this.title);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        float left = 16f * density;
        float titleBaseline = 18f * density;
        float top = 32f * density;
        float right = width - 16f * density;
        float bottom = height - 16f * density;

        if (!title.isEmpty()) {
            canvas.drawText(title, left, titleBaseline, textPaint);
        }

        for (int i = 0; i < 4; i++) {
            float y = top + i * ((bottom - top) / 3f);
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        if (points.isEmpty()) return;
        float min = points.get(0);
        float max = points.get(0);
        for (Float point : points) {
            if (point < min) min = point;
            if (point > max) max = point;
        }
        if (Math.abs(max - min) < 0.0001f) {
            max += 1f;
            min -= 1f;
        }

        Path line = new Path();
        Path fill = new Path();
        float lastX = left;
        float lastY = bottom;
        for (int i = 0; i < points.size(); i++) {
            float x = left + (right - left) * i / Math.max(1, points.size() - 1);
            float normalized = (points.get(i) - min) / (max - min);
            float y = bottom - normalized * (bottom - top);
            lastX = x;
            lastY = y;
            if (i == 0) {
                line.moveTo(x, y);
                fill.moveTo(x, bottom);
                fill.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                fill.lineTo(x, y);
            }
            canvas.drawCircle(x, y, 2.5f * density, pointPaint);
        }
        fill.lineTo(right, bottom);
        fill.close();
        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(line, linePaint);
        canvas.drawCircle(lastX, lastY, 4f * density, pointPaint);
    }
}

