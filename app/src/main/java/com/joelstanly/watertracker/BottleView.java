package com.joelstanly.watertracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** A 1 L bottle. Tap or drag to where the water level is now. */
public class BottleView extends View {
    public interface Listener {
        void onLevelPicked(int ml);
    }

    private int level = Store.BOTTLE_ML;
    private int preview = -1;
    private Listener listener;

    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint water = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewText = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF body = new RectF();
    private final RectF neck = new RectF();
    private final RectF capRect = new RectF();
    private final Path bodyPath = new Path();
    private final float d;

    public BottleView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(3 * d);
        outline.setColor(0xFF1565C0);
        water.setColor(0xFF42A5F5);
        glass.setColor(0xFFE3F2FD);
        cap.setColor(0xFF1565C0);
        tick.setColor(0x661565C0);
        tick.setStrokeWidth(1.5f * d);
        label.setColor(0xFF607D8B);
        label.setTextSize(12 * d);
        big.setColor(0xFFFFFFFF);
        big.setTextSize(20 * d);
        big.setFakeBoldText(true);
        big.setTextAlign(Paint.Align.CENTER);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(2.5f * d);
        dash.setColor(0xFFE65100);
        dash.setPathEffect(new DashPathEffect(new float[]{10 * d, 6 * d}, 0));
        previewText.setColor(0xFFE65100);
        previewText.setTextSize(15 * d);
        previewText.setFakeBoldText(true);
        setClickable(true);
        setContentDescription("Water bottle. Tap where the water level is now.");
    }

    public void setListener(Listener l) { listener = l; }

    public void setLevel(int ml) {
        level = ml;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float bw = Math.min(w * 0.42f, h * 0.48f);
        float cx = w / 2f;
        capRect.set(cx - bw * 0.22f, h * 0.01f, cx + bw * 0.22f, h * 0.07f);
        neck.set(cx - bw * 0.18f, h * 0.065f, cx + bw * 0.18f, h * 0.16f);
        body.set(cx - bw / 2f, h * 0.14f, cx + bw / 2f, h * 0.985f);
        bodyPath.reset();
        float r = bw * 0.22f;
        bodyPath.addRoundRect(body, r, r, Path.Direction.CW);
    }

    private float yFor(int ml) {
        float top = body.top + body.height() * 0.06f;
        float bottom = body.bottom - 2 * d;
        return bottom - (bottom - top) * (ml / (float) Store.BOTTLE_ML);
    }

    private int mlFor(float y) {
        float top = body.top + body.height() * 0.06f;
        float bottom = body.bottom - 2 * d;
        float frac = (bottom - y) / (bottom - top);
        frac = Math.max(0f, Math.min(1f, frac));
        return Math.round(frac * Store.BOTTLE_ML / 50f) * 50;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRoundRect(capRect, 4 * d, 4 * d, cap);
        c.drawRect(neck, glass);
        c.drawRect(neck, outline);

        c.save();
        c.clipPath(bodyPath);
        c.drawRect(body, glass);
        float wy = yFor(level);
        c.drawRect(body.left, wy, body.right, body.bottom, water);
        for (int ml = 100; ml < Store.BOTTLE_ML; ml += 100) {
            float y = yFor(ml);
            float len = (ml % 500 == 0) ? body.width() * 0.3f : body.width() * 0.15f;
            c.drawLine(body.right - len, y, body.right, y, tick);
        }
        c.restore();
        c.drawPath(bodyPath, outline);

        for (int ml = 200; ml <= Store.BOTTLE_ML; ml += 200) {
            float y = yFor(ml);
            c.drawText(ml == 1000 ? "1 L" : String.valueOf(ml), body.right + 8 * d, y + 4 * d, label);
        }

        String txt = level + " ml";
        float ty = Math.max(wy + 30 * d, body.top + 40 * d);
        if (level < 120) {
            big.setColor(0xFF1565C0);
            ty = wy - 12 * d;
        } else {
            big.setColor(0xFFFFFFFF);
        }
        c.drawText(txt, body.centerX(), Math.min(ty, body.bottom - 12 * d), big);

        if (preview >= 0) {
            float py = yFor(preview);
            c.drawLine(body.left - 14 * d, py, body.right + 4 * d, py, dash);
            String msg;
            if (preview < level) msg = "drank " + (level - preview) + " ml";
            else if (preview > level) msg = "top up";
            else msg = "no change";
            float tw = previewText.measureText(msg);
            c.drawText(msg, Math.max(4 * d, body.left - tw - 18 * d), py + 5 * d, previewText);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                preview = mlFor(e.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                preview = mlFor(e.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                int picked = mlFor(e.getY());
                preview = -1;
                invalidate();
                performClick();
                if (listener != null) listener.onLevelPicked(picked);
                return true;
            case MotionEvent.ACTION_CANCEL:
                preview = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
