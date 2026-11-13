package com.akemi.wristspritz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class SpritzView extends View {
    public interface Listener {
        void onTapCenter();
        void onLongPressCenter();
    }

    private final Paint bgPaint = new Paint();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pivotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ctxSidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ctxCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint metaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final GestureDetector detector;

    private String currentWord = "WristSpritz";
    private String prevContext = "";
    private String nextContext = "";
    private int wpm = 300;
    private boolean isPlaying = false;
    private String timeLeftStr = "";
    private float baseTextPx;
    private final float density;

    public SpritzView(Context ctx, float fontSp, final Listener listener) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        float scaledDensity = ctx.getResources().getDisplayMetrics().scaledDensity;
        baseTextPx = fontSp * scaledDensity;

        bgPaint.setColor(Color.BLACK);

        linePaint.setColor(Color.parseColor("#CBD5E1"));
        linePaint.setStrokeWidth(1.5f * density);

        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setColor(Color.parseColor("#CBD5E1"));
        circlePaint.setStrokeWidth(1.5f * density);

        Typeface boldSans = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        Typeface regSans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

        textPaint.setTypeface(boldSans);
        textPaint.setColor(Color.WHITE);

        pivotPaint.setTypeface(boldSans);
        pivotPaint.setColor(Color.parseColor("#EF4444"));

        ctxSidePaint.setTypeface(regSans);
        ctxSidePaint.setColor(Color.parseColor("#64748B"));
        ctxSidePaint.setTextSize(10.5f * scaledDensity);

        ctxCenterPaint.setTypeface(boldSans);
        ctxCenterPaint.setColor(Color.parseColor("#E2E8F0"));
        ctxCenterPaint.setTextSize(10.5f * scaledDensity);
        ctxCenterPaint.setTextAlign(Paint.Align.CENTER);

        metaPaint.setTypeface(regSans);
        metaPaint.setColor(Color.parseColor("#475569"));
        metaPaint.setTextSize(9.5f * scaledDensity);
        metaPaint.setTextAlign(Paint.Align.CENTER);

        detector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) { if (listener != null) listener.onTapCenter(); return true; }
            @Override
            public void onLongPress(MotionEvent e) { if (listener != null) listener.onLongPressCenter(); }
        });
    }

    public void setFontSp(float sp) {
        baseTextPx = sp * getResources().getDisplayMetrics().scaledDensity;
        invalidate();
    }

    public void updateState(String word, String prevCtx, String nextCtx, int wpm, boolean playing, String timeLeft) {
        this.currentWord = (word == null || word.isEmpty()) ? "•" : word;
        this.prevContext = prevCtx != null ? prevCtx : "";
        this.nextContext = nextCtx != null ? nextCtx : "";
        this.wpm = wpm;
        this.isPlaying = playing;
        this.timeLeftStr = timeLeft;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return detector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    public static int getOrpIndex(String w) {
        int len = w.length();
        if (len <= 1) return 0;
        if (len <= 5) return 1;
        if (len <= 9) return 2;
        if (len <= 13) return 3;
        return 4;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bgPaint);

        float cx = w * 0.43f;
        float cy = h * 0.47f;
        float boxH = baseTextPx * 1.44f;
        float topY = cy - boxH * 0.54f;
        float botY = cy + boxH * 0.54f;
        float rCirc = 5f * density;

        // Linea superiore interrotta dal cerchio mirino
        canvas.drawLine(w * 0.13f, topY, cx - rCirc, topY, linePaint);
        canvas.drawCircle(cx, topY, rCirc, circlePaint);
        canvas.drawLine(cx + rCirc, topY, w * 0.87f, topY, linePaint);

        // Linea inferiore + tacca focale
        canvas.drawLine(w * 0.13f, botY, w * 0.87f, botY, linePaint);
        canvas.drawLine(cx, botY - 4.5f * density, cx, botY, linePaint);

        // Parola Spritz centrata sulla lettera rossa ORP
        textPaint.setTextSize(baseTextPx);
        pivotPaint.setTextSize(baseTextPx);
        float totalW = textPaint.measureText(currentWord);
        float maxW = w * 0.78f;
        if (totalW > maxW && totalW > 0) {
            float sc = baseTextPx * (maxW / totalW);
            textPaint.setTextSize(sc);
            pivotPaint.setTextSize(sc);
        }

        int orp = Math.min(getOrpIndex(currentWord), currentWord.length() - 1);
        String left = currentWord.substring(0, orp);
        String pivot = currentWord.substring(orp, orp + 1);
        String right = currentWord.substring(orp + 1);

        float leftW = textPaint.measureText(left);
        float pivotW = pivotPaint.measureText(pivot);
        float startX = cx - leftW - (pivotW / 2f);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;

        canvas.drawText(left, startX, baseline, textPaint);
        canvas.drawText(pivot, startX + leftW, baseline, pivotPaint);
        canvas.drawText(right, startX + leftW + pivotW, baseline, textPaint);

        // Preview del testo: [parole prima]  [parola attuale]  [parole dopo]
        float ctxY = botY + 15f * density;
        float midX = w / 2f;
        float cwWidth = ctxCenterPaint.measureText(currentWord);
        float gap = 5f * density;

        canvas.save();
        canvas.clipRect(w * 0.13f, botY + 2f * density, w * 0.87f, ctxY + 6f * density);
        canvas.drawText(currentWord, midX, ctxY, ctxCenterPaint);
        if (!prevContext.isEmpty()) {
            ctxSidePaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(prevContext, midX - (cwWidth / 2f) - gap, ctxY, ctxSidePaint);
        }
        if (!nextContext.isEmpty()) {
            ctxSidePaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(nextContext, midX + (cwWidth / 2f) + gap, ctxY, ctxSidePaint);
        }
        canvas.restore();

        // Stato minimale sotto la preview
        String state = (isPlaying ? "PLAY" : "PAUSA") + "  ·  " + wpm + " WPM  ·  " + timeLeftStr;
        canvas.drawText(state, midX, ctxY + 14f * density, metaPaint);
    }
}
