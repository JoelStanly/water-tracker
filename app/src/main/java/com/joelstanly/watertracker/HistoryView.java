package com.joelstanly.watertracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

/** Simple 7-day bar chart with a goal line. */
public class HistoryView extends View {
    private int[] values = new int[7];
    private String[] labels = new String[7];
    private int goal = 2000;

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barHit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final RectF r = new RectF();

    public HistoryView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bar.setColor(0xFF90CAF9);
        barHit.setColor(0xFF1E88E5);
        goalLine.setStyle(Paint.Style.STROKE);
        goalLine.setStrokeWidth(1.5f * d);
        goalLine.setColor(0xFF2E7D32);
        goalLine.setPathEffect(new DashPathEffect(new float[]{6 * d, 4 * d}, 0));
        text.setColor(0xFF607D8B);
        text.setTextSize(11 * d);
        text.setTextAlign(Paint.Align.CENTER);
        value.setColor(0xFF37474F);
        value.setTextSize(10 * d);
        value.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(int[] v, String[] l, int g) {
        values = v;
        labels = l;
        goal = g;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float bottom = h - 18 * d, top = 14 * d;
        int max = goal;
        for (int v : values) max = Math.max(max, v);
        max = (int) (max * 1.1f);
        float slot = w / 7f;
        for (int i = 0; i < 7; i++) {
            float x = slot * i + slot / 2f;
            float bh = (bottom - top) * values[i] / (float) max;
            r.set(x - slot * 0.28f, bottom - bh, x + slot * 0.28f, bottom);
            c.drawRoundRect(r, 4 * d, 4 * d, values[i] >= goal ? barHit : bar);
            if (values[i] > 0) {
                c.drawText(String.format(Locale.US, "%.1f", values[i] / 1000f), x, r.top - 3 * d, value);
            }
            if (labels[i] != null) c.drawText(labels[i], x, h - 4 * d, text);
        }
        float gy = bottom - (bottom - top) * goal / (float) max;
        c.drawLine(0, gy, w, gy, goalLine);
    }
}
