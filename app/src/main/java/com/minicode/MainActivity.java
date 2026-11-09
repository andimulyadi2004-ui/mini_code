package com.minicode;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends Activity {
    static final int BG = 0xFF15131C, MAKS = 100;
    boolean tv, siap;
    float f = 1f, dens, fontSp = 12f;
    final ArrayList<Sesi> daftarSesi = new ArrayList<>();
    volatile Sesi sesiAktifObj;
    int aktif = -1, penghitung = 0;
    TermView tvw;
    Laci laci;
    LinearLayout daftarView;
    TextView jumlah;
    Button btnCtrl;
    File home, cache;
    final HashMap<String, String> envDasar = new HashMap<>();
    final Handler ui = new Handler(Looper.getMainLooper());

    int dp(float v) { return (int) (v * f * dens); }

    float px() {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSp * f, getResources().getDisplayMetrics());
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    StateListDrawable kotak(int warna, int r) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(warna);
        normal.setCornerRadius(dp(r));
        GradientDrawable fokus = new GradientDrawable();
        fokus.setColor(warna);
        fokus.setCornerRadius(dp(r));
        fokus.setStroke(dp(3), Color.WHITE);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, fokus);
        s.addState(new int[]{}, normal);
        return s;
    }

    Button tombol(String teks, int warna, int warnaTeks, View.OnClickListener o) {
        Button b = new Button(this);
        b.setText(teks);
        b.setAllCaps(false);
        b.setTextColor(warnaTeks);
        b.setTextSize(15 * f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setBackground(kotak(warna, 12));
        b.setOnClickListener(o);
        return b;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dens = getResources().getDisplayMetrics().density;
        UiModeManager um = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        tv = um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        f = tv ? 1.35f : 1f;
        fontSp = tv ? 14f : 12f;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        home = new File(getFilesDir(), "home");
        new File(home, "bin").mkdirs();
        cache = getCacheDir();
        String pt = System.getenv("PATH");
        if (pt == null) pt = "/system/bin";
        envDasar.putAll(System.getenv());
        envDasar.put("HOME", home.getPath());
        envDasar.put("TMPDIR", cache.getPath());
        envDasar.put("TERM", "xterm-256color");
        envDasar.put("LANG", "en_US.UTF-8");
        envDasar.put("PATH", new File(home, "bin").getPath() + ":" + pt);
        tampilIntro();
    }

    // ---------- intro ----------
    void tampilIntro() {
        FrameLayout fl = new FrameLayout(this);
        fl.setBackgroundColor(BG);
        LinearLayout kol = new LinearLayout(this);
        kol.setOrientation(LinearLayout.VERTICAL);
        kol.setGravity(Gravity.CENTER);

        final ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.logo);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(30));
            }
        });
        iv.setClipToOutline(true);
        kol.addView(iv, new LinearLayout.LayoutParams(dp(140), dp(140)));

        final TextView nama = new TextView(this);
        nama.setText("mini_code");
        nama.setTextSize(36 * f);
        nama.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        nama.setPadding(0, dp(18), 0, 0);
        nama.getPaint().setShader(new LinearGradient(0, 0, dp(230), 0,
                new int[]{0xFFFF7A59, 0xFFFFC857, 0xFF3DDC97, 0xFF4CC9F0}, null, Shader.TileMode.CLAMP));
        kol.addView(nama, new LinearLayout.LayoutParams(-2, -2));

        final TextView sub = new TextView(this);
        sub.setText("terminal ringan untuk HP & TV");
        sub.setTextColor(0xFF9A93AA);
        sub.setTextSize(14 * f);
        sub.setPadding(0, dp(6), 0, 0);
        kol.addView(sub, new LinearLayout.LayoutParams(-2, -2));

        fl.addView(kol, new FrameLayout.LayoutParams(-1, -1));
        setContentView(fl);

        iv.setAlpha(0f);
        iv.setScaleX(0.6f);
        iv.setScaleY(0.6f);
        iv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700)
                .setInterpolator(new OvershootInterpolator()).start();
        nama.setAlpha(0f);
        nama.animate().alpha(1f).setStartDelay(450).setDuration(600).start();
        sub.setAlpha(0f);
        sub.animate().alpha(1f).setStartDelay(800).setDuration(600).start();
        fl.setOnClickListener(v -> tampilUtama());
        ui.postDelayed(this::tampilUtama, 2200);
    }

    // ---------- layar utama ----------
    void tampilUtama() {
        if (siap) return;
        siap = true;

        tvw = new TermView(this, px());
        tvw.ujung = new TermView.Ujung() {
            @Override public void kirim(byte[] b) {
                Sesi s = sesiAktifObj;
                if (s != null) s.masuk(new String(b, StandardCharsets.UTF_8));
            }
            @Override public void ukuran(int c, int r) {}
        };
        tvw.ctrlDipakai = this::perbaruiCtrl;
        tvw.setOnFocusChangeListener((v, ada) -> {
            if (ada && tv) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(tvw, 0);
            }
        });

        LinearLayout isi = new LinearLayout(this);
        isi.setOrientation(LinearLayout.VERTICAL);
        isi.setBackgroundColor(BG);
        isi.addView(tvw, new LinearLayout.LayoutParams(-1, 0, 1f));
        isi.addView(barisTombol(), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout panel = buatPanel();
        if (tv) {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setBackgroundColor(BG);
            root.addView(panel, new LinearLayout.LayoutParams(dp(280), -1));
            root.addView(isi, new LinearLayout.LayoutParams(0, -1, 1f));
            setContentView(root);
        } else {
            laci = new Laci(this);
            laci.setBackgroundColor(BG);
            laci.addView(isi, new FrameLayout.LayoutParams(-1, -1));
            laci.pasang(panel, dp(290));
            setContentView(laci);
        }
        sesiBaru();
        tvw.requestFocus();
        if (!tv) tvw.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && tvw != null) imm.showSoftInput(tvw, 0);
        }, 350);
    }

    LinearLayout buatPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(0xFF1B1824);
        p.setPadding(dp(14), dp(tv ? 20 : 40), dp(14), dp(14));

        TextView judul = new TextView(this);
        judul.setText("mini_code");
        judul.setTextSize(24 * f);
        judul.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        judul.getPaint().setShader(new LinearGradient(0, 0, dp(150), 0,
                new int[]{0xFFFF7A59, 0xFFFFC857, 0xFF3DDC97, 0xFF4CC9F0}, null, Shader.TileMode.CLAMP));
        p.addView(judul, new LinearLayout.LayoutParams(-2, -2));

        jumlah = new TextView(this);
        jumlah.setTextColor(0xFF9A93AA);
        jumlah.setTextSize(13 * f);
        jumlah.setPadding(0, dp(4), 0, dp(10));
        p.addView(jumlah, new LinearLayout.LayoutParams(-2, -2));

        Button baru = tombol("+ Sesi baru", 0xFF3DDC97, 0xFF1A1620, v -> sesiBaru());
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(-1, dp(48));
        pb.bottomMargin = dp(8);
        p.addView(baru, pb);

        ScrollView sv = new ScrollView(this);
        daftarView = new LinearLayout(this);
        daftarView.setOrientation(LinearLayout.VERTICAL);
        sv.addView(daftarView, new FrameLayout.LayoutParams(-1, -2));
        p.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button izin = tombol("Izin aplikasi", 0xFF4CC9F0, 0xFF1A1620, v -> dialogIzin());
        LinearLayout.LayoutParams pi = new LinearLayout.LayoutParams(-1, dp(48));
        pi.topMargin = dp(8);
        p.addView(izin, pi);
        return p;
    }

    void refreshDaftar() {
        if (daftarView == null) return;
        daftarView.removeAllViews();
        for (int i = 0; i < daftarSesi.size(); i++) {
            final int idx = i;
            Sesi s = daftarSesi.get(i);
            boolean on = i == aktif;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button b = tombol(s.id + "   " + s.pendek(), on ? 0xFFFFC857 : 0xFF2A2536,
                    on ? 0xFF1A1620 : Color.WHITE, v -> pilih(idx));
            b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            b.setSingleLine(true);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setPadding(dp(12), 0, dp(8), 0);
            Button x = tombol("×", 0xFF3A3547, Color.WHITE, v -> tutupSesiKe(idx));
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(46), 1f);
            p1.setMargins(0, dp(3), dp(4), dp(3));
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(dp(46), dp(46));
            p2.setMargins(0, dp(3), 0, dp(3));
            row.addView(b, p1);
            row.addView(x, p2);
            daftarView.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        jumlah.setText("Sesi " + daftarSesi.size() + "/" + MAKS);
    }

    // ---------- sesi ----------
    void sesiBaru() {
        if (daftarSesi.size() >= MAKS) { toast("Maksimal " + MAKS + " sesi"); return; }
        final Sesi s = new Sesi(++penghitung, home, cache, envDasar);
        s.onUpdate = () -> { if (sesiAktifObj == s && tvw != null) tvw.postInvalidate(); };
        s.onJudul = () -> ui.post(this::refreshDaftar);
        s.onKeluar = () -> ui.post(() -> {
            int i = daftarSesi.indexOf(s);
            if (i >= 0) tutupSesiKe(i);
        });
        daftarSesi.add(s);
        pilih(daftarSesi.size() - 1);
    }

    void pilih(int i) {
        if (i < 0 || i >= daftarSesi.size()) return;
        aktif = i;
        Sesi s = daftarSesi.get(i);
        sesiAktifObj = s;
        if (tvw != null) {
            tvw.emu = s.term;
            tvw.scrollOff = 0;
            tvw.hitung();
            tvw.invalidate();
            tvw.requestFocus();
        }
        refreshDaftar();
        if (laci != null && laci.terbuka) laci.selesai(false);
    }

    void tutupSesiKe(int i) {
        if (i < 0 || i >= daftarSesi.size()) return;
        Sesi lama = sesiAktifObj;
        Sesi s = daftarSesi.remove(i);
        s.tutupSesi();
        if (daftarSesi.isEmpty()) { sesiBaru(); return; }
        if (s == lama) pilih(Math.min(i, daftarSesi.size() - 1));
        else { aktif = daftarSesi.indexOf(lama); refreshDaftar(); }
    }

    // ---------- tombol bawah ----------
    void perbaruiCtrl() {
        if (btnCtrl == null || tvw == null) return;
        btnCtrl.setBackground(kotak(tvw.ctrl ? 0xFFFFC857 : 0xFF3A3547, 10));
        btnCtrl.setTextColor(tvw.ctrl ? 0xFF1A1620 : Color.WHITE);
    }

    Button tambah(LinearLayout row, String t, View.OnClickListener o) {
        Button b = tombol(t, 0xFF3A3547, Color.WHITE, o);
        b.setTextSize(14 * f);
        b.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(42));
        p.setMargins(dp(3), dp(6), dp(3), dp(6));
        row.addView(b, p);
        return b;
    }

    HorizontalScrollView barisTombol() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(0xFF1E1A27);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), 0, dp(4), 0);
        hs.addView(row);

        if (!tv) tambah(row, "☰ Sesi", v -> { if (laci != null) laci.selesai(true); });
        tambah(row, "Tab", v -> tvw.kunciByte(9));
        btnCtrl = tambah(row, "Ctrl", v -> { tvw.ctrl = !tvw.ctrl; perbaruiCtrl(); });
        tambah(row, "^C", v -> tvw.kunciByte(3));
        tambah(row, "←", v -> tvw.panah('D'));
        tambah(row, "↑", v -> tvw.panah('A'));
        tambah(row, "↓", v -> tvw.panah('B'));
        tambah(row, "→", v -> tvw.panah('C'));
        tambah(row, "Home", v -> tvw.panah('H'));
        tambah(row, "End", v -> tvw.panah('F'));
        tambah(row, "|", v -> tvw.kunci("|"));
        tambah(row, "~", v -> tvw.kunci("~"));
        tambah(row, "/", v -> tvw.kunci("/"));
        tambah(row, "-", v -> tvw.kunci("-"));
        tambah(row, "Tempel", v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData d = cm == null ? null : cm.getPrimaryClip();
            if (d != null && d.getItemCount() > 0)
                tvw.kunci(d.getItemAt(0).coerceToText(this).toString().replace('\n', '\r'));
        });
        tambah(row, "A−", v -> { fontSp = Math.max(8, fontSp - 1); tvw.setTextSizePx(px()); });
        tambah(row, "A+", v -> { fontSp = Math.min(30, fontSp + 1); tvw.setTextSizePx(px()); });
        return hs;
    }

    // ---------- izin ----------
    boolean ok(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    boolean ada(int i) {
        switch (i) {
            case 0: return ok(Manifest.permission.CAMERA);
            case 1: return ok(Manifest.permission.RECORD_AUDIO);
            case 2: return Build.VERSION.SDK_INT >= 33 ? ok(Manifest.permission.READ_MEDIA_IMAGES)
                    : ok(Manifest.permission.READ_EXTERNAL_STORAGE);
            case 3: return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager()
                    : ok(Manifest.permission.READ_EXTERNAL_STORAGE);
            default: return Build.VERSION.SDK_INT < 33 || ok(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    void minta(int i) {
        switch (i) {
            case 0: requestPermissions(new String[]{Manifest.permission.CAMERA}, 1); break;
            case 1: requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1); break;
            case 2:
                if (Build.VERSION.SDK_INT >= 33)
                    requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO}, 1);
                else
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                break;
            case 3:
                if (Build.VERSION.SDK_INT >= 30)
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                else
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                break;
            default:
                if (Build.VERSION.SDK_INT >= 33)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    void dialogIzin() {
        final String[] nama = {"Kamera", "Mikrofon (rekam suara)", "Foto, video & audio",
                "Semua file (akses penuh)", "Notifikasi"};
        String[] tampil = new String[nama.length];
        for (int i = 0; i < nama.length; i++) tampil[i] = (ada(i) ? "✓  " : "✗  ") + nama[i];
        new AlertDialog.Builder(this).setTitle("Izin aplikasi")
                .setItems(tampil, (d, w) -> minta(w))
                .setNegativeButton("Tutup", null).show();
    }

    @Override public void onBackPressed() {
        if (!siap) { super.onBackPressed(); return; }
        if (laci != null && laci.terbuka) { laci.selesai(false); return; }
        if (tv && tvw != null && tvw.hasFocus() && daftarView != null
                && aktif >= 0 && aktif < daftarView.getChildCount()) {
            ((LinearLayout) daftarView.getChildAt(aktif)).getChildAt(0).requestFocus();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        for (Sesi s : daftarSesi) s.tutupSesi();
        super.onDestroy();
    }
}
