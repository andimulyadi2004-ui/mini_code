package com.minicode;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sesi {
    static final Pattern EXP = Pattern.compile("^export\\s+([A-Za-z_][A-Za-z0-9_]*)=(.*)$");
    static final Pattern ASG = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(\\S*)$");
    static final Pattern UNS = Pattern.compile("^unset\\s+([A-Za-z_][A-Za-z0-9_]*)$");

    final int id;
    final Term term = new Term(80, 24);
    final File home, cache;
    File cwd, lastCwd;
    final HashMap<String, String> env;
    final StringBuilder baris = new StringBuilder();
    int pos = 0;
    final ArrayList<String> riwayat = new ArrayList<>();
    int rIdx = 0;
    String draft = "";
    volatile Process proses;
    volatile OutputStream stdin;
    volatile boolean jalan, tutup;
    long ctrlCTerakhir;
    final ExecutorService tulis = Executors.newSingleThreadExecutor();
    Runnable onUpdate, onKeluar, onJudul;

    Sesi(int id, File home, File cache, Map<String, String> baseEnv) {
        this.id = id;
        this.home = home;
        this.cache = cache;
        this.env = new HashMap<>(baseEnv);
        this.cwd = home;
        out("\u001B[1;36mmini_code\u001B[0m  sesi " + id + "\r\nKetik perintah, mis. ls, cd, pwd. Tab = lengkapi.\r\n\r\n");
        prompt();
    }

    // ---------- tampilan ----------
    void out(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        synchronized (term) { term.feed(b, 0, b.length); }
        Runnable r = onUpdate;
        if (r != null) r.run();
    }

    String pendek() {
        String p = cwd.getPath(), h = home.getPath();
        if (p.equals(h)) return "~";
        if (p.startsWith(h + "/")) return "~" + p.substring(h.length());
        return p;
    }

    String promptTeks() { return "\u001B[1;32m" + pendek() + "\u001B[0m $ "; }

    void prompt() {
        baris.setLength(0);
        pos = 0;
        boolean awal;
        synchronized (term) { awal = term.cx == 0; }
        out((awal ? "" : "\r\n") + promptTeks());
        Runnable r = onJudul;
        if (r != null) r.run();
    }

    void gambar() {
        int sisa = baris.length() - pos;
        out("\r" + promptTeks() + baris + "\u001B[K" + (sisa > 0 ? "\u001B[" + sisa + "D" : ""));
    }

    // ---------- input ----------
    public void masuk(String s) {
        if (tutup) return;
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == 27) {
                if (i + 1 < n && (s.charAt(i + 1) == '[' || s.charAt(i + 1) == 'O')) {
                    int j = i + 2;
                    while (j < n && !(Character.isLetter(s.charAt(j)) || s.charAt(j) == '~')) j++;
                    if (j < n) esc(s.substring(i + 2, j + 1));
                    i = j + 1;
                } else i++;
                continue;
            }
            kar(c);
            i++;
        }
    }

    void esc(String q) {
        if (jalan) return;
        switch (q) {
            case "A": naik(); break;
            case "B": turun(); break;
            case "C": if (pos < baris.length()) { pos++; out("\u001B[C"); } break;
            case "D": if (pos > 0) { pos--; out("\u001B[D"); } break;
            case "H": case "1~": pos = 0; gambar(); break;
            case "F": case "4~": pos = baris.length(); gambar(); break;
            case "3~": if (pos < baris.length()) { baris.deleteCharAt(pos); gambar(); } break;
        }
    }

    void kar(char c) {
        switch (c) {
            case '\r': case '\n': enter(); return;
            case 127: case 8: hapusKiri(); return;
            case 3: interupsi(); return;
            case 4: eof(); return;
            case 9: tab(); return;
            case 12: out("\u001B[H\u001B[2J\u001B[3J"); if (!jalan) gambar(); return;
            case 1: if (!jalan) { pos = 0; gambar(); } return;
            case 5: if (!jalan) { pos = baris.length(); gambar(); } return;
            case 21: if (!jalan) { baris.delete(0, pos); pos = 0; gambar(); } return;
            case 11: if (!jalan) { baris.setLength(pos); gambar(); } return;
            case 23: if (!jalan) hapusKata(); return;
        }
        if (c < 32) return;
        if (jalan || pos == baris.length()) {
            baris.append(c);
            pos = baris.length();
            out(String.valueOf(c));
        } else {
            baris.insert(pos, c);
            pos++;
            gambar();
        }
    }

    void hapusKiri() {
        if (jalan) {
            if (baris.length() > 0) { baris.setLength(baris.length() - 1); pos = baris.length(); out("\b \b"); }
            return;
        }
        if (pos > 0) {
            baris.deleteCharAt(pos - 1);
            pos--;
            if (pos == baris.length()) out("\b \b"); else gambar();
        }
    }

    void hapusKata() {
        int k = pos;
        while (k > 0 && baris.charAt(k - 1) == ' ') k--;
        while (k > 0 && baris.charAt(k - 1) != ' ') k--;
        baris.delete(k, pos);
        pos = k;
        gambar();
    }

    void naik() {
        if (rIdx <= 0) return;
        if (rIdx == riwayat.size()) draft = baris.toString();
        rIdx--;
        ganti(riwayat.get(rIdx));
    }

    void turun() {
        if (rIdx >= riwayat.size()) return;
        rIdx++;
        ganti(rIdx == riwayat.size() ? draft : riwayat.get(rIdx));
    }

    void ganti(String s) {
        baris.setLength(0);
        baris.append(s);
        pos = baris.length();
        gambar();
    }

    void enter() {
        final String cmd = baris.toString();
        out("\r\n");
        baris.setLength(0);
        pos = 0;
        if (jalan) {
            final OutputStream o = stdin;
            if (o != null) tulis.execute(() -> {
                try { o.write((cmd + "\n").getBytes(StandardCharsets.UTF_8)); o.flush(); } catch (Exception ignored) {}
            });
            return;
        }
        eksekusi(cmd);
    }

    void eof() {
        if (jalan) {
            final OutputStream o = stdin;
            if (o != null) tulis.execute(() -> { try { o.close(); } catch (Exception ignored) {} });
            return;
        }
        if (baris.length() == 0) keluar();
    }

    void keluar() {
        Runnable r = onKeluar;
        if (r != null) r.run();
    }

    // ---------- perintah ----------
    static String buang(String v) {
        v = v.trim();
        if (v.length() >= 2 && (v.charAt(0) == '"' || v.charAt(0) == '\'') && v.charAt(v.length() - 1) == v.charAt(0))
            return v.substring(1, v.length() - 1);
        return v;
    }

    void eksekusi(String cmd) {
        String t = cmd.trim();
        if (t.isEmpty()) { prompt(); return; }
        if (riwayat.isEmpty() || !riwayat.get(riwayat.size() - 1).equals(t)) riwayat.add(t);
        if (riwayat.size() > 500) riwayat.remove(0);
        rIdx = riwayat.size();
        draft = "";
        if (t.equals("clear")) { out("\u001B[H\u001B[2J\u001B[3J"); prompt(); return; }
        if (t.equals("exit") || t.equals("logout")) { keluar(); return; }
        if (t.equals("cd") || t.startsWith("cd ")) { cd(t.length() > 2 ? t.substring(3) : ""); prompt(); return; }
        Matcher m = EXP.matcher(t);
        if (m.matches()) { env.put(m.group(1), buang(m.group(2))); prompt(); return; }
        m = UNS.matcher(t);
        if (m.matches()) { env.remove(m.group(1)); prompt(); return; }
        m = ASG.matcher(t);
        if (m.matches()) { env.put(m.group(1), buang(m.group(2))); prompt(); return; }
        jalankan(t);
    }

    File lokasi(String a) {
        if (a.equals("~")) return home;
        if (a.startsWith("~/")) return new File(home, a.substring(2));
        if (a.startsWith("/")) return new File(a);
        return new File(cwd, a);
    }

    void cd(String a) {
        a = buang(a);
        File t;
        if (a.isEmpty()) t = home;
        else if (a.equals("-")) t = lastCwd != null ? lastCwd : cwd;
        else t = lokasi(a);
        try { t = t.getCanonicalFile(); } catch (Exception ignored) {}
        if (t.isDirectory()) { lastCwd = cwd; cwd = t; }
        else out("cd: " + a + ": folder tidak ada\r\n");
    }

    void jalankan(final String cmd) {
        jalan = true;
        baris.setLength(0);
        pos = 0;
        final File pf = new File(cache, "pid" + id);
        pf.delete();
        new Thread(() -> {
            int kode = -1;
            try {
                ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", "echo $$ > \"$MC_PID\"\n" + cmd);
                pb.directory(cwd);
                pb.redirectErrorStream(true);
                Map<String, String> e = pb.environment();
                e.putAll(env);
                e.put("MC_PID", pf.getPath());
                Process p = pb.start();
                proses = p;
                stdin = p.getOutputStream();
                InputStream in = p.getInputStream();
                byte[] buf = new byte[4096];
                int n, prev = 0;
                while ((n = in.read(buf)) >= 0) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream(n + 16);
                    for (int i = 0; i < n; i++) {
                        int b = buf[i] & 0xFF;
                        if (b == '\n' && prev != '\r') bo.write('\r');
                        bo.write(b);
                        prev = b;
                    }
                    byte[] o = bo.toByteArray();
                    synchronized (term) { term.feed(o, 0, o.length); }
                    Runnable r = onUpdate;
                    if (r != null) r.run();
                }
                kode = p.waitFor();
            } catch (Exception ex) {
                out("\r\n[error: " + ex.getMessage() + "]\r\n");
            }
            jalan = false;
            proses = null;
            stdin = null;
            if (kode > 0) out("\r\n\u001B[2m[kode " + kode + "]\u001B[0m");
            if (!tutup) prompt();
        }).start();
    }

    // ---------- Ctrl+C ----------
    void interupsi() {
        if (!jalan) {
            out("^C\r\n");
            prompt();
            return;
        }
        long now = System.currentTimeMillis();
        final int sig = (now - ctrlCTerakhir < 2500) ? 9 : 2;
        ctrlCTerakhir = now;
        out("^C");
        new Thread(() -> kirimSinyal(sig)).start();
    }

    int bacaPid() {
        try (BufferedReader r = new BufferedReader(new FileReader(new File(cache, "pid" + id)))) {
            return Integer.parseInt(r.readLine().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    void kirimSinyal(int sig) {
        int pid = bacaPid();
        if (pid <= 0) {
            Process p = proses;
            if (p != null) p.destroy();
            return;
        }
        List<Integer> semua = turunan(pid);
        semua.add(0, pid);
        for (int i = semua.size() - 1; i >= 0; i--) android.os.Process.sendSignal(semua.get(i), sig);
    }

    static List<Integer> turunan(int root) {
        HashMap<Integer, List<Integer>> anak = new HashMap<>();
        File[] fs = new File("/proc").listFiles();
        if (fs != null) {
            for (File f : fs) {
                String nm = f.getName();
                if (!nm.matches("\\d+")) continue;
                try (BufferedReader r = new BufferedReader(new FileReader(new File(f, "stat")))) {
                    String l = r.readLine();
                    if (l == null) continue;
                    int k = l.lastIndexOf(')');
                    String[] x = l.substring(k + 2).split(" ");
                    int pp = Integer.parseInt(x[1]);
                    int me = Integer.parseInt(nm);
                    anak.computeIfAbsent(pp, z -> new ArrayList<>()).add(me);
                } catch (Exception ignored) {}
            }
        }
        ArrayList<Integer> hasil = new ArrayList<>();
        ArrayList<Integer> antre = new ArrayList<>();
        antre.add(root);
        while (!antre.isEmpty()) {
            int a = antre.remove(0);
            List<Integer> ls = anak.get(a);
            if (ls != null) {
                for (int c : ls) {
                    hasil.add(c);
                    antre.add(c);
                }
            }
        }
        return hasil;
    }

    // ---------- Tab ----------
    void tab() {
        if (jalan) return;
        String b = baris.substring(0, pos);
        int sp = b.lastIndexOf(' ');
        String kata = b.substring(sp + 1);
        int sl = kata.lastIndexOf('/');
        String dirP = sl >= 0 ? kata.substring(0, sl + 1) : "";
        String nama = kata.substring(sl + 1);
        File dir = dirP.isEmpty() ? cwd : lokasi(dirP);
        String[] ls = dir.list();
        if (ls == null) return;
        ArrayList<String> cocok = new ArrayList<>();
        for (String f : ls) if (f.startsWith(nama) && (nama.startsWith(".") || !f.startsWith("."))) cocok.add(f);
        if (cocok.isEmpty()) return;
        Collections.sort(cocok);
        String umum = cocok.get(0);
        for (String f : cocok) {
            while (!f.startsWith(umum)) umum = umum.substring(0, umum.length() - 1);
        }
        if (umum.length() > nama.length() || cocok.size() == 1) {
            String tambah = umum.substring(nama.length());
            if (cocok.size() == 1) tambah += new File(dir, cocok.get(0)).isDirectory() ? "/" : " ";
            baris.insert(pos, tambah);
            pos += tambah.length();
            gambar();
        } else {
            StringBuilder x = new StringBuilder("\r\n");
            for (String f : cocok)
                x.append(new File(dir, f).isDirectory() ? "\u001B[1;34m" + f + "/\u001B[0m  " : f + "  ");
            x.append("\r\n");
            out(x.toString());
            gambar();
        }
    }

    void tutupSesi() {
        tutup = true;
        new Thread(() -> {
            kirimSinyal(9);
            Process p = proses;
            if (p != null) p.destroy();
        }).start();
        tulis.shutdown();
    }
}
