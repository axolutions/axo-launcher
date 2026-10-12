package com.axolutions.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Collator;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends Activity {

    private static final Locale BR = new Locale("pt", "BR");

    /** um item clicavel: app instalado, link ou acao especial */
    static final class Entry {
        String label;
        LauncherActivityInfo info;
        String url;
        String special;
        boolean gapBefore;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private LauncherApps launcherApps;
    private SharedPreferences prefs;

    private Pager pager;
    private LinearLayout dots;
    private final List<View> pages = new ArrayList<>();

    private TextView clock, date, info, event, focus;
    private LinearLayout focusList, allList, distractList;
    private TextView distractTotal;
    private EditText search;
    private FrameLayout countdown;
    private Comando comando;

    private final List<Entry> focusEntries = new ArrayList<>();
    private final List<Entry> others = new ArrayList<>();
    private final List<Entry> distractions = new ArrayList<>();
    private final Map<String, Long> usageToday = new HashMap<>();

    private int topInset, bottomInset;
    private Runnable countdownTick;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
            updateBattery(i);
        }
    };

    private final LauncherApps.Callback appsChanged = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String p, UserHandle u) { reloadApps(); }
        @Override public void onPackageAdded(String p, UserHandle u) { reloadApps(); }
        @Override public void onPackageChanged(String p, UserHandle u) { reloadApps(); }
        @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { reloadApps(); }
        @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { reloadApps(); }
    };

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        launcherApps = getSystemService(LauncherApps.class);
        prefs = getSharedPreferences("axo", MODE_PRIVATE);

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root = new FrameLayout(this);
        pager = new Pager(this);
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));

        pages.add(buildFocusPage());
        pages.add(buildAllPage());
        pages.add(buildDistractionPage());
        for (View p : pages) pager.addPage(p);

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < pages.size(); i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Color.WHITE);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(5), dp(5));
            lp.setMargins(dp(5), 0, dp(5), 0);
            dots.addView(d, lp);
        }
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-1, dp(20), Gravity.BOTTOM);
        root.addView(dots, dlp);
        pager.listener = this::onPageChanged;
        onPageChanged(0);

        comando = new Comando(this);
        root.addView(comando, new FrameLayout.LayoutParams(-1, -1));

        countdown = buildCountdown();
        root.addView(countdown, new FrameLayout.LayoutParams(-1, -1));

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            topInset = ins.getSystemWindowInsetTop();
            bottomInset = ins.getSystemWindowInsetBottom();
            for (View p : pages) {
                View content = ((ViewGroup) p).getChildAt(0);
                if (p instanceof ScrollView) {
                    content.setPadding(content.getPaddingLeft(), topInset + dp(22), content.getPaddingRight(), bottomInset + dp(30));
                } else {
                    p.setPadding(p.getPaddingLeft(), topInset + dp(22), p.getPaddingRight(), bottomInset + dp(30));
                }
            }
            comando.setInsets(topInset, bottomInset);
            ((FrameLayout.LayoutParams) dots.getLayoutParams()).bottomMargin = bottomInset + dp(4);
            dots.requestLayout();
            return ins.consumeSystemWindowInsets();
        });

        setContentView(root);
        launcherApps.registerCallback(appsChanged, ui);
        reloadApps();

        if (prefs.getInt("wallv", 0) < Config.WALLPAPER_VERSION) applyWallpaper();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        updateClock();
        updateBattery(registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
        updateFocus();
        updateEvent();
        updateUsage();
        updateWeather(false);
        ColorBubbleService.start(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(tick);
        } catch (Exception ignored) {
        }
        cancelCountdown();
    }

    @Override
    protected void onDestroy() {
        launcherApps.unregisterCallback(appsChanged);
        bg.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // botao home com o launcher ja aberto: volta pra primeira pagina
        cancelCountdown();
        if (comando.aberto()) comando.fechar();
        search.setText("");
        hideKeyboard();
        pager.goTo(0, true);
    }

    @Override
    public void onBackPressed() {
        if (countdown.getVisibility() == View.VISIBLE) {
            cancelCountdown();
            return;
        }
        if (comando.aberto()) {
            comando.fechar();
            return;
        }
        hideKeyboard();
        pager.goTo(0, true);
    }

    private void onPageChanged(int page) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            dots.getChildAt(i).setAlpha(i == page ? 0.9f : 0.3f);
        }
        if (page != 1) hideKeyboard();
        if (page == 2) updateUsage();
    }

    // ------------------------------------------------------------------ paginas

    private View buildFocusPage() {
        ScrollView sv = scroller();
        LinearLayout col = column();
        sv.addView(col);

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.CENTER_VERTICAL);
        clock = text("", 62, 255, "sans-serif-thin");
        clock.setOnClickListener(v -> open(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        clock.setOnLongClickListener(v -> {
            showMenu();
            return true;
        });
        clockRow.addView(clock, new LinearLayout.LayoutParams(0, -2, 1));
        TextView plus = text("+", 30, 235, "sans-serif-thin");
        plus.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(1), Color.argb(110, 255, 255, 255));
        plus.setBackground(ring);
        plus.setOnClickListener(v -> comando.abrir());
        clockRow.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        col.addView(clockRow);

        date = text("", 16, 210, "sans-serif-light");
        date.setPadding(dp(3), dp(4), 0, 0);
        date.setOnClickListener(v -> openCalendar());
        col.addView(date);

        info = text("", 13, 150, "sans-serif");
        info.setPadding(dp(3), dp(10), 0, 0);
        info.setLetterSpacing(0.02f);
        info.setLineSpacing(dp(4), 1f);
        col.addView(info);

        event = text("", 14, 220, "sans-serif-light");
        event.setPadding(dp(3), dp(12), 0, 0);
        event.setOnClickListener(v -> openCalendar());
        col.addView(event);

        focus = text("", 15, 220, "sans-serif-light");
        focus.setPadding(dp(3), dp(12), 0, dp(4));
        focus.setOnClickListener(v -> editFocus());
        col.addView(focus);

        focusList = new LinearLayout(this);
        focusList.setOrientation(LinearLayout.VERTICAL);
        focusList.setPadding(0, dp(18), 0, 0);
        col.addView(focusList);
        return sv;
    }

    private View buildAllPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(30), 0, dp(30), 0);

        search = new EditText(this);
        search.setHint("buscar app");
        search.setHintTextColor(Color.argb(110, 255, 255, 255));
        search.setTextColor(Color.WHITE);
        search.setTextSize(20);
        search.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        GradientDrawable line = new GradientDrawable();
        line.setColor(Color.TRANSPARENT);
        line.setStroke(0, Color.TRANSPARENT);
        search.setBackground(line);
        search.setPadding(dp(3), dp(6), 0, dp(14));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { renderAll(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        search.setOnEditorActionListener((v, id, ev) -> {
            for (int i = 0; i < allList.getChildCount(); i++) {
                Object tag = allList.getChildAt(i).getTag();
                if (tag instanceof Entry) {
                    launch((Entry) tag, allList.getChildAt(i));
                    return true;
                }
            }
            return true;
        });
        page.addView(search);

        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(50, 255, 255, 255));
        page.addView(divider, new LinearLayout.LayoutParams(-1, 1));

        ScrollView sv = scroller();
        allList = new LinearLayout(this);
        allList.setOrientation(LinearLayout.VERTICAL);
        allList.setPadding(0, dp(10), 0, dp(40));
        sv.addView(allList);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        return page;
    }

    private View buildDistractionPage() {
        ScrollView sv = scroller();
        LinearLayout col = column();
        sv.addView(col);

        TextView title = text("DISTRAÇÕES", 12, 140, "sans-serif-medium");
        title.setLetterSpacing(0.25f);
        title.setPadding(dp(3), 0, 0, 0);
        col.addView(title);

        distractTotal = text("", 44, 255, "sans-serif-thin");
        distractTotal.setPadding(0, dp(8), 0, 0);
        col.addView(distractTotal);

        TextView sub = text("de tela hoje nesses apps", 13, 150, "sans-serif");
        sub.setPadding(dp(3), dp(2), 0, 0);
        col.addView(sub);

        distractList = new LinearLayout(this);
        distractList.setOrientation(LinearLayout.VERTICAL);
        distractList.setPadding(0, dp(34), 0, 0);
        col.addView(distractList);

        TextView hint = text("cada um abre depois de " + Config.DISTRACTION_DELAY + " segundos. dá tempo de desistir.",
                12, 110, "sans-serif");
        hint.setPadding(dp(3), dp(30), 0, 0);
        col.addView(hint);
        return sv;
    }

    private FrameLayout buildCountdown() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.argb(235, 0, 0, 0));
        f.setVisibility(View.GONE);
        f.setClickable(true);
        f.setOnClickListener(v -> cancelCountdown());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextView nome = text("", 26, 230, "sans-serif-light");
        TextView num = text("", 96, 255, "sans-serif-thin");
        TextView c = text("toque para desistir", 13, 140, "sans-serif");
        c.setPadding(0, dp(16), 0, 0);
        for (TextView t : new TextView[]{nome, num, c}) {
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        f.addView(col, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    // ------------------------------------------------------------------ apps

    private void reloadApps() {
        List<LauncherActivityInfo> all = new ArrayList<>();
        for (UserHandle u : launcherApps.getProfiles()) {
            all.addAll(launcherApps.getActivityList(null, u));
        }
        Set<String> used = new HashSet<>();

        focusList.removeAllViews();
        focusEntries.clear();
        for (Config.Item item : Config.FOCUS) {
            Entry e = resolve(item, all, used);
            if (e == null) continue;
            focusEntries.add(e);
            if (e.gapBefore && focusList.getChildCount() > 0) focusList.addView(new View(this), new LinearLayout.LayoutParams(1, dp(10)));
            focusList.addView(row(e, 20));
        }

        distractions.clear();
        for (Config.Item item : Config.DISTRACTIONS) {
            Entry e = resolve(item, all, used);
            if (e != null) distractions.add(e);
        }
        renderDistractions();

        others.clear();
        String me = getPackageName();
        for (LauncherActivityInfo i : all) {
            if (i.getComponentName().getPackageName().equals(me)) continue;
            if (used.contains(key(i))) continue;
            Entry e = new Entry();
            e.info = i;
            e.label = i.getLabel().toString().trim().toLowerCase(BR);
            if (!i.getUser().equals(Process.myUserHandle())) e.label += " (2)";
            others.add(e);
        }
        final Collator col = Collator.getInstance(BR);
        col.setStrength(Collator.PRIMARY);
        Collections.sort(others, (a, b) -> col.compare(a.label, b.label));
        renderAll();
    }

    private Entry resolve(Config.Item item, List<LauncherActivityInfo> all, Set<String> used) {
        Entry e = new Entry();
        e.label = item.label;
        e.gapBefore = item.gapBefore;
        if (item.special != null) {
            e.special = item.special;
            return e;
        }
        UserHandle me = Process.myUserHandle();
        for (String pkg : item.pkgs) {
            for (LauncherActivityInfo i : all) {
                boolean clone = !i.getUser().equals(me);
                if (clone == item.clone && i.getComponentName().getPackageName().equals(pkg) && !used.contains(key(i))) {
                    e.info = i;
                    used.add(key(i));
                    return e;
                }
            }
        }
        for (String name : item.names) {
            String n = norm(name);
            for (LauncherActivityInfo i : all) {
                boolean clone = !i.getUser().equals(me);
                if (clone == item.clone && norm(i.getLabel().toString()).equals(n) && !used.contains(key(i))) {
                    e.info = i;
                    used.add(key(i));
                    return e;
                }
            }
        }
        if (item.url != null) {
            e.url = item.url;
            return e;
        }
        return null;
    }

    private static String key(LauncherActivityInfo i) {
        return i.getComponentName().flattenToString() + "#" + i.getUser().hashCode();
    }

    private static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(BR).trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private void renderAll() {
        allList.removeAllViews();
        String q = search == null ? "" : norm(search.getText().toString());
        String lastLetter = null;
        for (Entry e : others) {
            if (!q.isEmpty() && !norm(e.label).contains(q)) continue;
            if (q.isEmpty()) {
                String letter = norm(e.label).isEmpty() ? "#" : norm(e.label).substring(0, 1).toUpperCase(BR);
                if (!Character.isLetter(letter.charAt(0))) letter = "#";
                if (!letter.equals(lastLetter)) {
                    TextView h = text(letter, 12, 110, "sans-serif-medium");
                    h.setPadding(dp(3), dp(lastLetter == null ? 4 : 18), 0, dp(2));
                    allList.addView(h);
                    lastLetter = letter;
                }
            }
            allList.addView(row(e, 19));
        }
    }

    private void renderDistractions() {
        distractList.removeAllViews();
        long total = 0;
        for (Entry e : distractions) {
            long ms = e.info == null ? 0 : usage(e.info.getComponentName().getPackageName());
            total += ms;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setTag(e);
            r.setBackground(ripple());
            r.setPadding(dp(3), dp(10), dp(3), dp(10));
            TextView name = text(e.label, 23, 235, "sans-serif-light");
            r.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(text(ms > 0 ? fmtDuration(ms) : "", 14, 140, "sans-serif"));
            r.setOnClickListener(v -> startCountdown(e));
            distractList.addView(r);
        }
        distractTotal.setText(total > 0 ? fmtDuration(total) : "0 min");
    }

    private TextView row(Entry e, int sp) {
        TextView t = text(e.label, sp, 235, "sans-serif-light");
        t.setPadding(dp(3), dp(sp >= 20 ? 6 : 8), dp(3), dp(sp >= 20 ? 6 : 8));
        t.setBackground(ripple());
        t.setTag(e);
        t.setOnClickListener(v -> launch(e, v));
        t.setOnLongClickListener(v -> {
            if (e.info != null) {
                launcherApps.startAppDetailsActivity(e.info.getComponentName(), e.info.getUser(), null, null);
            }
            return true;
        });
        return t;
    }

    private void launch(Entry e, View from) {
        hideKeyboard();
        try {
            if (e.info != null && e.info.getUser().equals(Process.myUserHandle())) {
                // a Xiaomi pergunta "qual WhatsApp?" quando um launcher de fora abre um app duplicado;
                // esse extra e o que o seletor dela manda depois da escolha, entao ela nao pergunta de novo
                Intent i = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(e.info.getComponentName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        .putExtra("android.intent.extra.xspace_userid_selected", true);
                i.setSourceBounds(bounds(from));
                startActivity(i);
            } else if (e.info != null) {
                if (!startInClone(e, from)) {
                    launcherApps.startMainActivity(e.info.getComponentName(), e.info.getUser(), bounds(from), null);
                }
            } else if (e.url != null) {
                open(new Intent(Intent.ACTION_VIEW, Uri.parse(e.url)));
            } else if ("search".equals(e.special)) {
                openSearch();
            }
        } catch (Exception ex) {
            android.widget.Toast.makeText(this, "não consegui abrir " + e.label, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * WhatsApp duplicado: abre direto no perfil clone com o mesmo extra do seletor da Xiaomi.
     * Usa startActivityAsUser (precisa de INTERACT_ACROSS_USERS, concedida via adb).
     */
    private boolean startInClone(Entry e, View from) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(e.info.getComponentName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    .putExtra("android.intent.extra.xspace_userid_selected", true);
            i.setSourceBounds(bounds(from));
            java.lang.reflect.Method m = Context.class.getMethod("startActivityAsUser", Intent.class, UserHandle.class);
            m.invoke(this, i, e.info.getUser());
            return true;
        } catch (Throwable t) {
            android.util.Log.w("Axo", "clone direto falhou", t);
            return false;
        }
    }

    /** tudo que o "+" pode abrir pelo nome (so apps instalados de verdade) */
    List<Entry> todosApps() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : focusEntries) if (e.info != null) out.add(e);
        out.addAll(others);
        for (Entry e : distractions) if (e.info != null) out.add(e);
        return out;
    }

    /** abrir pelo "+": distracao continua passando pela contagem */
    void abrirEntry(Entry e) {
        if (distractions.contains(e)) startCountdown(e);
        else launch(e, null);
    }

    void definirFoco(String f) {
        prefs.edit().putString("focus", f).putString("focusDay", today()).apply();
        updateFocus();
    }

    private void openSearch() {
        Intent google = getPackageManager().getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
        Intent[] tries = {
                new Intent("android.search.action.GLOBAL_SEARCH").setPackage("com.google.android.googlequicksearchbox"),
                google,
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/")),
        };
        for (Intent i : tries) {
            if (i == null) continue;
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ distracoes

    private void startCountdown(Entry e) {
        LinearLayout col = (LinearLayout) countdown.getChildAt(0);
        final TextView name = (TextView) col.getChildAt(0);
        final TextView num = (TextView) col.getChildAt(1);
        name.setText(e.label);
        final int[] left = {Config.DISTRACTION_DELAY};
        num.setText(String.valueOf(left[0]));
        countdown.setAlpha(0f);
        countdown.setVisibility(View.VISIBLE);
        countdown.animate().alpha(1f).setDuration(180).start();
        countdownTick = new Runnable() {
            @Override
            public void run() {
                left[0]--;
                if (left[0] <= 0) {
                    countdown.setVisibility(View.GONE);
                    countdownTick = null;
                    launch(e, null);
                    return;
                }
                num.setText(String.valueOf(left[0]));
                ui.postDelayed(this, 1000);
            }
        };
        ui.postDelayed(countdownTick, 1000);
    }

    private void cancelCountdown() {
        if (countdownTick != null) ui.removeCallbacks(countdownTick);
        countdownTick = null;
        if (countdown != null) countdown.setVisibility(View.GONE);
    }

    private void updateUsage() {
        bg.execute(() -> {
            Map<String, Long> m = Uso.hoje(this);
            ui.post(() -> {
                usageToday.clear();
                usageToday.putAll(m);
                renderDistractions();
                updateInfoLine();
            });
        });
    }

    private long usage(String pkg) {
        Long v = usageToday.get(pkg);
        return v == null ? 0 : v;
    }

    private static String fmtDuration(long ms) {
        long min = ms / 60000;
        if (min < 1) return "< 1 min";
        if (min < 60) return min + " min";
        return (min / 60) + "h" + (min % 60 > 0 ? String.format(BR, " %02d", min % 60) : "");
    }

    // ------------------------------------------------------------------ widgets

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        clock.setText(new SimpleDateFormat("HH:mm", BR).format(now.getTime()));
        String d = new SimpleDateFormat("EEEE, d 'de' MMMM", BR).format(now.getTime());
        date.setText(d.replace("-feira", ""));
    }

    private int battery = -1;
    private boolean charging;

    private void updateBattery(Intent i) {
        if (i == null || !Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
            i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
        if (i == null) return;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level >= 0) battery = Math.round(level * 100f / scale);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        updateInfoLine();
    }

    private void updateInfoLine() {
        // linha 1: clima; linha 2: bateria e tempo em distracoes
        List<String> second = new ArrayList<>();
        if (battery >= 0) second.add((charging ? "carregando " : "bateria ") + battery + "%");
        long total = 0;
        for (Long v : usageToday.values()) total += v;
        if (total >= 60000) second.add("distrações " + fmtDuration(total));
        String wx = prefs.getString("wx", "");
        String line2 = join("  ·  ", second);
        info.setText(wx.isEmpty() ? line2 : wx + "\n" + line2);
    }

    private void updateWeather(boolean force) {
        long at = prefs.getLong("wxAt", 0);
        if (!force && System.currentTimeMillis() - at < 30 * 60 * 1000) {
            updateInfoLine();
            return;
        }
        bg.execute(() -> {
            try {
                URL u = new URL(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                                + "&current=temperature_2m,weather_code"
                                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                                + "&timezone=America%%2FSao_Paulo&forecast_days=1",
                        Config.LAT, Config.LON));
                HttpURLConnection con = (HttpURLConnection) u.openConnection();
                con.setConnectTimeout(6000);
                con.setReadTimeout(6000);
                String body;
                try (InputStream in = con.getInputStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
                    body = s.hasNext() ? s.next() : "";
                }
                JSONObject j = new JSONObject(body);
                JSONObject cur = j.getJSONObject("current");
                JSONObject daily = j.getJSONObject("daily");
                int temp = (int) Math.round(cur.getDouble("temperature_2m"));
                int max = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
                int min = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
                int rain = daily.getJSONArray("precipitation_probability_max").optInt(0, 0);
                String txt = temp + "° " + weatherText(cur.getInt("weather_code"))
                        + "  ·  " + min + "°/" + max + "°"
                        + (rain >= 30 ? "  ·  chuva " + rain + "%" : "");
                prefs.edit().putString("wx", txt).putLong("wxAt", System.currentTimeMillis()).apply();
                ui.post(this::updateInfoLine);
            } catch (Exception ignored) {
            }
        });
    }

    private static String weatherText(int code) {
        if (code == 0) return "céu limpo";
        if (code <= 2) return "sol entre nuvens";
        if (code == 3) return "nublado";
        if (code <= 48) return "neblina";
        if (code <= 57) return "garoa";
        if (code <= 67) return "chuva";
        if (code <= 77) return "neve";
        if (code <= 82) return "pancadas de chuva";
        return "tempestade";
    }

    private void updateEvent() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            event.setVisibility(View.GONE);
            return;
        }
        bg.execute(() -> {
            String txt = nextEvent();
            ui.post(() -> {
                event.setText(txt);
                event.setVisibility(txt == null ? View.GONE : View.VISIBLE);
            });
        });
    }

    private String nextEvent() {
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 30 * 60 * 1000);
        ContentUris.appendId(b, now + 36L * 3600 * 1000);
        String[] proj = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY};
        String allDayToday = null;
        try (Cursor c = getContentResolver().query(b.build(), proj, CalendarContract.Instances.VISIBLE + "=1", null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (c == null) return null;
            Calendar today = Calendar.getInstance();
            while (c.moveToNext()) {
                String title = c.getString(0);
                long begin = c.getLong(1);
                boolean allDay = c.getInt(2) == 1;
                if (title == null || title.trim().isEmpty()) continue;
                if (allDay) {
                    if (allDayToday == null) allDayToday = "hoje  ·  " + title.trim();
                    continue;
                }
                if (begin < now - 15 * 60 * 1000) continue;
                Calendar bc = Calendar.getInstance();
                bc.setTimeInMillis(begin);
                String when = new SimpleDateFormat("HH:mm", BR).format(bc.getTime());
                if (bc.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR)) when = "amanhã " + when;
                return "▸  " + when + "  " + title.trim();
            }
        } catch (Exception ignored) {
        }
        return allDayToday;
    }

    private void openCalendar() {
        Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
        ContentUris.appendId(b, System.currentTimeMillis());
        open(new Intent(Intent.ACTION_VIEW, b.build()));
    }

    private String today() {
        return new SimpleDateFormat("yyyyMMdd", BR).format(new java.util.Date());
    }

    private void updateFocus() {
        String f = prefs.getString("focus", "");
        if (today().equals(prefs.getString("focusDay", "")) && !f.isEmpty()) {
            focus.setText("foco  ·  " + f);
            focus.setTextColor(Color.argb(235, 255, 255, 255));
        } else {
            focus.setText("qual é o foco de hoje?");
            focus.setTextColor(Color.argb(120, 255, 255, 255));
        }
    }

    private void editFocus() {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (today().equals(prefs.getString("focusDay", ""))) in.setText(prefs.getString("focus", ""));
        in.setSelection(in.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(in);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("A única coisa que importa hoje")
                .setView(box)
                .setPositiveButton("salvar", (dlg, w) -> {
                    prefs.edit().putString("focus", in.getText().toString().trim()).putString("focusDay", today()).apply();
                    updateFocus();
                })
                .setNegativeButton("cancelar", null)
                .create();
        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    // ------------------------------------------------------------------ menu e papel de parede

    private void showMenu() {
        final boolean bubbleOn = prefs.getBoolean("bubble", true);
        String[] items = {
                "configurações do android",
                "reaplicar papel de parede",
                bubbleOn ? "esconder botão de cor" : "mostrar botão de cor",
                "atualizar clima",
                "trocar de launcher",
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: open(new Intent(Settings.ACTION_SETTINGS)); break;
                        case 1: applyWallpaper(); break;
                        case 2:
                            prefs.edit().putBoolean("bubble", !bubbleOn).apply();
                            if (bubbleOn) ColorBubbleService.stop(this); else ColorBubbleService.start(this);
                            break;
                        case 3: updateWeather(true); break;
                        case 4: open(new Intent(Settings.ACTION_HOME_SETTINGS)); break;
                    }
                })
                .show();
    }

    private void applyWallpaper() {
        bg.execute(() -> {
            try {
                WallpaperManager wm = WallpaperManager.getInstance(this);
                try (InputStream in = getAssets().open("wall_home.png")) {
                    wm.setStream(in, null, true, WallpaperManager.FLAG_SYSTEM);
                }
                try (InputStream in = getAssets().open("wall_lock.png")) {
                    wm.setStream(in, null, true, WallpaperManager.FLAG_LOCK);
                }
                prefs.edit().putInt("wallv", Config.WALLPAPER_VERSION).apply();
            } catch (Exception ignored) {
            }
        });
    }

    // ------------------------------------------------------------------ helpers de view

    private ScrollView scroller() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return sv;
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(30), 0, dp(30), 0);
        return col;
    }

    private TextView text(String s, float sp, int alpha, String family) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.argb(alpha, 255, 255, 255));
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setShadowLayer(dp(5), 0, dp(1), 0x99000000);
        t.setIncludeFontPadding(false);
        return t;
    }

    private android.graphics.drawable.Drawable ripple() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return getDrawable(tv.resourceId);
    }

    private android.graphics.Rect bounds(View v) {
        if (v == null) return null;
        int[] p = new int[2];
        v.getLocationOnScreen(p);
        return new android.graphics.Rect(p[0], p[1], p[0] + v.getWidth(), p[1] + v.getHeight());
    }

    private void open(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void hideKeyboard() {
        if (search == null) return;
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String join(String sep, List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ pager

    interface PageListener {
        void onPage(int page);
    }

    /** paginacao horizontal que sempre encaixa numa pagina inteira */
    static class Pager extends HorizontalScrollView {
        final LinearLayout strip;
        PageListener listener;
        int page = 0;

        Pager(Context c) {
            super(c);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setFillViewport(true);
            strip = new LinearLayout(c);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            addView(strip, new LayoutParams(-2, -1));
        }

        void addPage(View v) {
            strip.addView(v, new LinearLayout.LayoutParams(getResources().getDisplayMetrics().widthPixels, -1));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            for (int i = 0; i < strip.getChildCount(); i++) {
                strip.getChildAt(i).getLayoutParams().width = w;
            }
            strip.requestLayout();
            post(() -> scrollTo(page * w, 0));
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            boolean r = super.dispatchTouchEvent(e);
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                int w = getWidth();
                int dx = getScrollX() - page * w;
                int target = page;
                if (dx > w * 0.15f) target = page + 1;
                else if (dx < -w * 0.15f) target = page - 1;
                goTo(target, true);
            }
            return r;
        }

        @Override
        public void fling(int velocityX) {
            // o encaixe e feito no dispatchTouchEvent
        }

        @Override
        public boolean onKeyDown(int code, KeyEvent e) {
            return false;
        }

        void goTo(int p, boolean smooth) {
            p = Math.max(0, Math.min(p, strip.getChildCount() - 1));
            boolean changed = p != page;
            page = p;
            if (smooth) smoothScrollTo(p * getWidth(), 0);
            else scrollTo(p * getWidth(), 0);
            if (changed && listener != null) listener.onPage(p);
        }
    }
}
