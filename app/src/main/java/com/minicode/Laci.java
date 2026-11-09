package com.minicode;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

public class Laci extends FrameLayout {
    View scrim, panel;
    int lebar, slop;
    boolean terbuka, geser;
    float downX, downY, startX, basePos;

    public Laci(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    public void pasang(View p, int w) {
        lebar = w;
        panel = p;
        p.setClickable(true);
        scrim = new View(getContext());
        scrim.setBackgroundColor(0xFF000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(GONE);
        scrim.setOnClickListener(v -> selesai(false));
        addView(scrim, new LayoutParams(-1, -1));
        addView(p, new LayoutParams(w, -1));
        p.setTranslationX(-w);
    }

    void set(float tx) {
        tx = Math.max(-lebar, Math.min(0, tx));
        panel.setTranslationX(tx);
        float pr = 1f + tx / lebar;
        scrim.setAlpha(0.55f * pr);
        scrim.setVisibility(pr > 0.01f ? VISIBLE : GONE);
    }

    void selesai(boolean buka) {
        terbuka = buka;
        ValueAnimator va = ValueAnimator.ofFloat(panel.getTranslationX(), buka ? 0 : -lebar);
        va.setDuration(180);
        va.addUpdateListener(a -> set((float) a.getAnimatedValue()));
        va.start();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                geser = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!geser && Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 2) {
                    if ((!terbuka && dx > 0) || (terbuka && dx < 0)) {
                        geser = true;
                        basePos = panel.getTranslationX();
                        startX = e.getX();
                        return true;
                    }
                }
                break;
            }
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!geser) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                set(basePos + (e.getX() - startX));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                geser = false;
                selesai(panel.getTranslationX() > -lebar * 0.6f);
                return true;
        }
        return true;
    }
}
