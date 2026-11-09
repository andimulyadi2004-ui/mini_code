package com.minicode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.nio.charset.StandardCharsets;

public class TermView extends View {
    public interface Ujung {
        void kirim(byte[] b);
        void ukuran(int c, int r);
    }

    static final int DEF_FG = 0xFFE6E6E6, DEF_BG = 0xFF0B0A10;
    Term emu = new Term(80, 24);
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint bgp = new Paint();
    final StringBuilder sb2 = new StringBuilder();
    float cellW = 10, cellH = 20, base = 16;
    int scrollOff = 0;
    float lastY, downY;
    boolean moved;
    public boolean ctrl = false;
    public Runnable ctrlDipakai;
    public Ujung ujung;

    public TermView(Context c, float px) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        paint.setTypeface(Typeface.MONOSPACE);
        setTextSizePx(px);
    }

    public void setTextSizePx(float px) {
        paint.setTextSize(px);
        cellW = paint.measureText("M");
        Paint.FontMetrics fm = paint.getFontMetrics();
        cellH = (float) Math.ceil(fm.descent - fm.ascent);
        base = -fm.ascent;
        hitung();
        invalidate();
    }

    void hitung() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        int c = Math.max(10, (int) (getWidth() / cellW));
        int r = Math.max(4, (int) (getHeight() / cellH));
        synchronized (emu) { emu.resize(c, r); }
        if (ujung != null) ujung.ukuran(c, r);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        hitung();
    }

    public void feed(byte[] b, int off, int len) {
        synchronized (emu) { emu.feed(b, off, len); }
        postInvalidate();
    }

    public void feedText(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        feed(b, 0, b.length);
    }

    // ---------- input ----------
    void kirimRaw(byte[] b) {
        scrollOff = 0;
        if (ujung != null) ujung.kirim(b);
        invalidate();
    }

    public void kunci(String s) { kirimRaw(s.getBytes(StandardCharsets.UTF_8)); }

    public void kunciByte(int v) { kirimRaw(new byte[]{(byte) v}); }

    public void panah(char d) { kunci((emu.appCursor ? "\u001BO" : "\u001B[") + d); }

    int ctrlMap(int c) {
        if (c >= 'a' && c <= 'z') return c - 'a' + 1;
        if (c >= 'A' && c <= 'Z') return c - 'A' + 1;
        switch (c) {
            case '[': return 27;
            case '\\': return 28;
            case ']': return 29;
            case '^': return 30;
            case '_': return 31;
            case '@': case ' ': return 0;
            case '?': return 127;
        }
        return -1;
    }

    public void teks(String s) {
        if (s.isEmpty()) return;
        s = s.replace('\n', '\r');
        if (ctrl) {
            int k = ctrlMap(s.charAt(0));
            ctrl = false;
            if (ctrlDipakai != null) ctrlDipakai.run();
            if (k >= 0) {
                kunciByte(k);
                s = s.substring(1);
            }
        }
        if (!s.isEmpty()) kunci(s);
    }

    boolean tombolKey(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_MULTIPLE && e.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN
                && e.getCharacters() != null) {
            teks(e.getCharacters());
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER: kunci("\r"); return true;
            case KeyEvent.KEYCODE_DEL: kunciByte(127); return true;
            case KeyEvent.KEYCODE_FORWARD_DEL: kunci("\u001B[3~"); return true;
            case KeyEvent.KEYCODE_TAB: kunciByte(9); return true;
            case KeyEvent.KEYCODE_ESCAPE: kunciByte(27); return true;
            case KeyEvent.KEYCODE_DPAD_UP: panah('A'); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: panah('B'); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: panah('C'); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: panah('D'); return true;
            case KeyEvent.KEYCODE_MOVE_HOME: panah('H'); return true;
            case KeyEvent.KEYCODE_MOVE_END: panah('F'); return true;
            case KeyEvent.KEYCODE_PAGE_UP: kunci("\u001B[5~"); return true;
            case KeyEvent.KEYCODE_PAGE_DOWN: kunci("\u001B[6~"); return true;
        }
        int u = e.getUnicodeChar(e.getMetaState() & ~(KeyEvent.META_CTRL_MASK | KeyEvent.META_ALT_MASK));
        if (u <= 0) return false;
        boolean c = e.isCtrlPressed(), a = e.isAltPressed();
        if (c) {
            int k = ctrlMap(u);
            if (k >= 0) {
                if (a) kunci("\u001B");
                kunciByte(k);
                return true;
            }
        }
        if (a) kunci("\u001B");
        teks(new String(Character.toChars(u)));
        return true;
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        return tombolKey(e) || super.onKeyDown(k, e);
    }

    @Override public boolean onCheckIsTextEditor() { return true; }

    @Override public InputConnection onCreateInputConnection(EditorInfo o) {
        o.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        o.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_ACTION_NONE;
        return new BaseInputConnection(this, false) {
            @Override public boolean commitText(CharSequence t, int n) { teks(t.toString()); return true; }
            @Override public boolean setComposingText(CharSequence t, int n) { return true; }
            @Override public boolean finishComposingText() { return true; }
            @Override public boolean deleteSurroundingText(int b, int a) {
                for (int i = 0; i < b; i++) kunciByte(127);
                return true;
            }
            @Override public boolean sendKeyEvent(KeyEvent e) { return tombolKey(e); }
            @Override public boolean performEditorAction(int a) { kunci("\r"); return true; }
        };
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        float y = ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downY = lastY = y;
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!moved && Math.abs(y - downY) > 24) moved = true;
                if (moved) {
                    int lines = (int) ((y - lastY) / cellH);
                    if (lines != 0) {
                        lastY += lines * cellH;
                        int max;
                        synchronized (emu) { max = emu.sb.size(); }
                        scrollOff = Math.max(0, Math.min(max, scrollOff + lines));
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    requestFocus();
                    InputMethodManager imm = (InputMethodManager) getContext()
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(this, 0);
                }
                return true;
        }
        return true;
    }

    // ---------- gambar ----------
    int g(int[][] L, int bo, int k, int c) {
        if (L != null) return c < L[k].length ? L[k][c] : (k == 0 ? 32 : 0);
        switch (k) {
            case 0: return emu.ch[bo + c];
            case 1: return emu.fg[bo + c];
            case 2: return emu.bg[bo + c];
            default: return emu.fl[bo + c];
        }
    }

    int rf(int f, int b, int fl, boolean cur) {
        boolean rev = ((fl & 4) != 0) ^ cur;
        int ff = f == 0 ? DEF_FG : f, bb = b == 0 ? DEF_BG : b;
        return rev ? bb : ff;
    }

    int rb(int f, int b, int fl, boolean cur) {
        boolean rev = ((fl & 4) != 0) ^ cur;
        int ff = f == 0 ? DEF_FG : f, bb = b == 0 ? DEF_BG : b;
        return rev ? ff : bb;
    }

    @Override protected void onDraw(Canvas cv) {
        cv.drawColor(DEF_BG);
        synchronized (emu) {
            int sbn = emu.sb.size(), rows = emu.rows, cols = emu.cols;
            int so = Math.min(scrollOff, sbn);
            int first = sbn - so;
            for (int r = 0; r < rows; r++) {
                int idx = first + r;
                if (idx >= sbn + rows) break;
                int[][] L = idx < sbn ? emu.sb.get(idx) : null;
                int bo = (idx - sbn) * cols;
                float y = r * cellH;
                boolean curRow = so == 0 && emu.curVisible && idx - sbn == emu.cy;
                int c = 0;
                while (c < cols) {
                    boolean cur = curRow && c == emu.cx;
                    int f0 = g(L, bo, 1, c), b0 = g(L, bo, 2, c), l0 = g(L, bo, 3, c);
                    int xf = rf(f0, b0, l0, cur), xb = rb(f0, b0, l0, cur), stl = l0 & 3;
                    int e = c + 1;
                    if (!cur) {
                        while (e < cols && !(curRow && e == emu.cx)) {
                            int f1 = g(L, bo, 1, e), b1 = g(L, bo, 2, e), l1 = g(L, bo, 3, e);
                            if (rf(f1, b1, l1, false) != xf || rb(f1, b1, l1, false) != xb
                                    || (l1 & 3) != stl) break;
                            e++;
                        }
                    }
                    if (xb != DEF_BG) {
                        bgp.setColor(xb);
                        cv.drawRect(c * cellW, y, e * cellW, y + cellH, bgp);
                    }
                    sb2.setLength(0);
                    boolean ada = false;
                    for (int k = c; k < e; k++) {
                        int cp = g(L, bo, 0, k);
                        if (cp > 32) ada = true;
                        sb2.appendCodePoint(cp <= 0 ? 32 : cp);
                    }
                    if (ada) {
                        paint.setColor(xf);
                        paint.setFakeBoldText((stl & 1) != 0);
                        paint.setUnderlineText((stl & 2) != 0);
                        cv.drawText(sb2.toString(), c * cellW, y + base, paint);
                    }
                    c = e;
                }
            }
        }
    }
}
