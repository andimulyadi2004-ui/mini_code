package com.minicode;

import java.util.ArrayList;
import java.util.Arrays;

public class Term {
    static final int SB_MAX = 1500;
    static final int[] PAL = new int[256];
    static {
        int[] b = {0x000000, 0xCD3131, 0x0DBC79, 0xE5E510, 0x2472C8, 0xBC3FBC, 0x11A8CD, 0xE5E5E5,
                0x666666, 0xF14C4C, 0x23D18B, 0xF5F543, 0x3B8EEA, 0xD670D6, 0x29B8DB, 0xFFFFFF};
        for (int i = 0; i < 16; i++) PAL[i] = 0xFF000000 | b[i];
        for (int i = 16; i < 232; i++) {
            int v = i - 16, r = v / 36, g = (v / 6) % 6, bl = v % 6;
            PAL[i] = 0xFF000000 | (lv(r) << 16) | (lv(g) << 8) | lv(bl);
        }
        for (int i = 232; i < 256; i++) {
            int g = 8 + (i - 232) * 10;
            PAL[i] = 0xFF000000 | (g << 16) | (g << 8) | g;
        }
    }
    static int lv(int x) { return x == 0 ? 0 : 55 + x * 40; }

    int cols, rows;
    int[] ch, fg, bg, fl;
    int cx, cy, top, bot;
    boolean wrap, alt, curVisible = true, appCursor;
    int curFg, curBg, curFl;
    int svx, svy;
    int[] aCh, aFg, aBg, aFl;
    int aCx, aCy;
    final ArrayList<int[][]> sb = new ArrayList<>();

    int st = 0;
    final int[] par = new int[16];
    int np, priv;
    boolean havePar;
    int utfRem, utfCp;

    Term(int c, int r) {
        cols = c;
        rows = r;
        ch = new int[c * r];
        fg = new int[c * r];
        bg = new int[c * r];
        fl = new int[c * r];
        Arrays.fill(ch, 32);
        top = 0;
        bot = r - 1;
    }

    synchronized void resize(int nc, int nr) {
        if (nc == cols && nr == rows) return;
        int shift = 0;
        if (!alt && cy >= nr) shift = cy - nr + 1;
        for (int i = 0; i < shift; i++) pushLine(i);
        ch = rs(ch, nc, nr, 32, shift);
        fg = rs(fg, nc, nr, 0, shift);
        bg = rs(bg, nc, nr, 0, shift);
        fl = rs(fl, nc, nr, 0, shift);
        if (aCh != null) {
            aCh = rs(aCh, nc, nr, 32, 0);
            aFg = rs(aFg, nc, nr, 0, 0);
            aBg = rs(aBg, nc, nr, 0, 0);
            aFl = rs(aFl, nc, nr, 0, 0);
        }
        cols = nc;
        rows = nr;
        cy = Math.max(0, cy - shift);
        cy = Math.min(cy, nr - 1);
        cx = Math.min(cx, nc - 1);
        svx = Math.min(svx, nc - 1);
        svy = Math.min(svy, nr - 1);
        top = 0;
        bot = nr - 1;
        wrap = false;
    }

    int[] rs(int[] a, int nc, int nr, int fill, int shift) {
        int[] n = new int[nc * nr];
        Arrays.fill(n, fill);
        for (int r = 0; r < nr; r++) {
            int sr = r + shift;
            if (sr >= rows) break;
            System.arraycopy(a, sr * cols, n, r * nc, Math.min(cols, nc));
        }
        return n;
    }

    void pushLine(int row) {
        sb.add(new int[][]{
                Arrays.copyOfRange(ch, row * cols, (row + 1) * cols),
                Arrays.copyOfRange(fg, row * cols, (row + 1) * cols),
                Arrays.copyOfRange(bg, row * cols, (row + 1) * cols),
                Arrays.copyOfRange(fl, row * cols, (row + 1) * cols)});
        if (sb.size() > SB_MAX) sb.remove(0);
    }

    void blank(int from, int to) {
        int f2 = curFl & 4;
        for (int i = from; i < to; i++) {
            ch[i] = 32;
            fg[i] = curFg;
            bg[i] = curBg;
            fl[i] = f2;
        }
    }

    void mv(int src, int dst, int len) {
        if (len <= 0) return;
        System.arraycopy(ch, src, ch, dst, len);
        System.arraycopy(fg, src, fg, dst, len);
        System.arraycopy(bg, src, bg, dst, len);
        System.arraycopy(fl, src, fl, dst, len);
    }

    void scrollUp(int k, boolean push) {
        k = Math.min(k, bot - top + 1);
        for (int i = 0; i < k; i++) {
            if (push && top == 0 && !alt) pushLine(0);
            mv((top + 1) * cols, top * cols, (bot - top) * cols);
            blank(bot * cols, (bot + 1) * cols);
        }
    }

    void scrollDown(int k) {
        k = Math.min(k, bot - top + 1);
        for (int i = 0; i < k; i++) {
            mv(top * cols, (top + 1) * cols, (bot - top) * cols);
            blank(top * cols, (top + 1) * cols);
        }
    }

    void index() {
        if (cy == bot) scrollUp(1, true);
        else if (cy < rows - 1) cy++;
    }

    void revIndex() {
        if (cy == top) scrollDown(1);
        else if (cy > 0) cy--;
    }

    void put(int cp) {
        if (wrap) {
            cx = 0;
            index();
            wrap = false;
        }
        int i = cy * cols + cx;
        ch[i] = cp;
        fg[i] = curFg;
        bg[i] = curBg;
        fl[i] = curFl;
        if (cx < cols - 1) cx++;
        else wrap = true;
    }

    void enterAlt() {
        if (alt) return;
        aCh = ch; aFg = fg; aBg = bg; aFl = fl;
        aCx = cx; aCy = cy;
        ch = new int[cols * rows];
        fg = new int[cols * rows];
        bg = new int[cols * rows];
        fl = new int[cols * rows];
        Arrays.fill(ch, 32);
        alt = true;
        top = 0;
        bot = rows - 1;
    }

    void exitAlt() {
        if (!alt) return;
        ch = aCh; fg = aFg; bg = aBg; fl = aFl;
        cx = Math.min(aCx, cols - 1);
        cy = Math.min(aCy, rows - 1);
        aCh = aFg = aBg = aFl = null;
        alt = false;
        top = 0;
        bot = rows - 1;
    }

    void reset() {
        if (alt) exitAlt();
        curFg = curBg = curFl = 0;
        Arrays.fill(ch, 32);
        Arrays.fill(fg, 0);
        Arrays.fill(bg, 0);
        Arrays.fill(fl, 0);
        cx = cy = 0;
        top = 0;
        bot = rows - 1;
        wrap = false;
        curVisible = true;
        appCursor = false;
    }

    void feed(byte[] b, int off, int len) {
        for (int i = off; i < off + len; i++) handle(b[i] & 0xFF);
    }

    void handle(int c) {
        switch (st) {
            case 0: normal(c); break;
            case 1: esc(c); break;
            case 2: csi(c); break;
            case 3:
                if (c == 7) st = 0;
                else if (c == 27) st = 5;
                break;
            case 4: st = 0; break;
            case 5:
                if (c == '\\') st = 0;
                else { st = 1; esc(c); }
                break;
        }
    }

    void normal(int c) {
        if (c >= 0x80) { utf(c); return; }
        utfRem = 0;
        switch (c) {
            case 27: st = 1; return;
            case 8: if (cx > 0) cx--; wrap = false; return;
            case 9: cx = Math.min(((cx / 8) + 1) * 8, cols - 1); return;
            case 10: case 11: case 12: wrap = false; index(); return;
            case 13: cx = 0; wrap = false; return;
        }
        if (c >= 32) put(c);
    }

    void utf(int c) {
        if ((c & 0xC0) == 0x80) {
            if (utfRem > 0) {
                utfCp = (utfCp << 6) | (c & 0x3F);
                if (--utfRem == 0 && utfCp <= 0x10FFFF && !(utfCp >= 0xD800 && utfCp <= 0xDFFF)) put(utfCp);
            }
            return;
        }
        if ((c & 0xE0) == 0xC0) { utfCp = c & 0x1F; utfRem = 1; }
        else if ((c & 0xF0) == 0xE0) { utfCp = c & 0x0F; utfRem = 2; }
        else if ((c & 0xF8) == 0xF0) { utfCp = c & 0x07; utfRem = 3; }
        else utfRem = 0;
    }

    void esc(int c) {
        st = 0;
        switch (c) {
            case '[':
                st = 2;
                np = 0;
                havePar = false;
                priv = 0;
                Arrays.fill(par, 0);
                break;
            case ']': st = 3; break;
            case '(': case ')': case '*': case '+': case '#': st = 4; break;
            case '7': svx = cx; svy = cy; break;
            case '8': cx = Math.min(svx, cols - 1); cy = Math.min(svy, rows - 1); break;
            case 'M': revIndex(); break;
            case 'D': index(); break;
            case 'E': cx = 0; index(); break;
            case 'c': reset(); break;
        }
    }

    int nParams() { return (havePar || np > 0) ? np + 1 : 0; }

    int p(int i, int def) { return (i < nParams() && par[i] > 0) ? par[i] : def; }

    void csi(int c) {
        if (c >= '0' && c <= '9') { par[np] = par[np] * 10 + (c - '0'); havePar = true; return; }
        if (c == ';') { if (np < par.length - 1) np++; par[np] = 0; havePar = true; return; }
        if (c == '?' || c == '>' || c == '<' || c == '=') { priv = c; return; }
        if (c >= 0x20 && c <= 0x2F) return;
        if (c >= 0x40 && c <= 0x7E) { run(c); st = 0; return; }
        if (c == 27) st = 1;
    }

    void run(int c) {
        int n = nParams();
        switch (c) {
            case 'A': cy = Math.max(0, cy - p(0, 1)); wrap = false; break;
            case 'B': cy = Math.min(rows - 1, cy + p(0, 1)); wrap = false; break;
            case 'C': cx = Math.min(cols - 1, cx + p(0, 1)); wrap = false; break;
            case 'D': cx = Math.max(0, cx - p(0, 1)); wrap = false; break;
            case 'E': cx = 0; cy = Math.min(rows - 1, cy + p(0, 1)); wrap = false; break;
            case 'F': cx = 0; cy = Math.max(0, cy - p(0, 1)); wrap = false; break;
            case 'G': case '`': cx = Math.min(cols - 1, p(0, 1) - 1); wrap = false; break;
            case 'd': cy = Math.min(rows - 1, p(0, 1) - 1); wrap = false; break;
            case 'H': case 'f':
                cy = Math.min(rows - 1, p(0, 1) - 1);
                cx = Math.min(cols - 1, p(1, 1) - 1);
                wrap = false;
                break;
            case 'J': {
                int m = n > 0 ? par[0] : 0;
                int cur = cy * cols + cx;
                if (m == 0) blank(cur, rows * cols);
                else if (m == 1) blank(0, cur + 1);
                else { blank(0, rows * cols); if (m == 3) sb.clear(); }
                break;
            }
            case 'K': {
                int m = n > 0 ? par[0] : 0;
                int rs0 = cy * cols;
                if (m == 0) blank(rs0 + cx, rs0 + cols);
                else if (m == 1) blank(rs0, rs0 + cx + 1);
                else blank(rs0, rs0 + cols);
                break;
            }
            case 'L':
                if (cy >= top && cy <= bot) { int t = top; top = cy; scrollDown(p(0, 1)); top = t; }
                cx = 0; wrap = false;
                break;
            case 'M':
                if (cy >= top && cy <= bot) { int t = top; top = cy; scrollUp(p(0, 1), false); top = t; }
                cx = 0; wrap = false;
                break;
            case 'P': {
                int k = Math.min(p(0, 1), cols - cx), b0 = cy * cols;
                mv(b0 + cx + k, b0 + cx, cols - cx - k);
                blank(b0 + cols - k, b0 + cols);
                break;
            }
            case '@': {
                int k = Math.min(p(0, 1), cols - cx), b0 = cy * cols;
                mv(b0 + cx, b0 + cx + k, cols - cx - k);
                blank(b0 + cx, b0 + cx + k);
                break;
            }
            case 'X': {
                int k = Math.min(p(0, 1), cols - cx), b0 = cy * cols;
                blank(b0 + cx, b0 + cx + k);
                break;
            }
            case 'S': scrollUp(p(0, 1), true); break;
            case 'T': scrollDown(p(0, 1)); break;
            case 'm': sgr(); break;
            case 'r': {
                int t = p(0, 1) - 1, b = p(1, rows) - 1;
                if (t < 0) t = 0;
                if (b > rows - 1) b = rows - 1;
                if (t < b) { top = t; bot = b; } else { top = 0; bot = rows - 1; }
                cx = 0; cy = 0; wrap = false;
                break;
            }
            case 's': svx = cx; svy = cy; break;
            case 'u': cx = Math.min(svx, cols - 1); cy = Math.min(svy, rows - 1); break;
            case 'h': case 'l':
                if (priv == '?') {
                    boolean on = c == 'h';
                    for (int i = 0; i < n; i++) {
                        switch (par[i]) {
                            case 1: appCursor = on; break;
                            case 25: curVisible = on; break;
                            case 47: case 1047: if (on) enterAlt(); else exitAlt(); break;
                            case 1049:
                                if (on) { svx = cx; svy = cy; enterAlt(); }
                                else { exitAlt(); cx = Math.min(svx, cols - 1); cy = Math.min(svy, rows - 1); }
                                break;
                        }
                    }
                }
                break;
        }
    }

    void sgr() {
        int n = nParams();
        if (n == 0) { curFg = curBg = curFl = 0; return; }
        for (int i = 0; i < n; i++) {
            int v = par[i];
            if (v == 0) { curFg = curBg = curFl = 0; }
            else if (v == 1) curFl |= 1;
            else if (v == 4) curFl |= 2;
            else if (v == 7) curFl |= 4;
            else if (v == 22) curFl &= ~1;
            else if (v == 24) curFl &= ~2;
            else if (v == 27) curFl &= ~4;
            else if (v >= 30 && v <= 37) curFg = PAL[v - 30];
            else if (v == 39) curFg = 0;
            else if (v >= 40 && v <= 47) curBg = PAL[v - 40];
            else if (v == 49) curBg = 0;
            else if (v >= 90 && v <= 97) curFg = PAL[v - 90 + 8];
            else if (v >= 100 && v <= 107) curBg = PAL[v - 100 + 8];
            else if (v == 38 || v == 48) {
                int col = 0;
                boolean ok = false;
                if (i + 2 < n && par[i + 1] == 5) {
                    col = PAL[par[i + 2] & 255];
                    i += 2;
                    ok = true;
                } else if (i + 4 < n && par[i + 1] == 2) {
                    col = 0xFF000000 | ((par[i + 2] & 255) << 16) | ((par[i + 3] & 255) << 8) | (par[i + 4] & 255);
                    i += 4;
                    ok = true;
                }
                if (ok) { if (v == 38) curFg = col; else curBg = col; }
            }
        }
    }
}
