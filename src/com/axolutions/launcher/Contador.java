package com.axolutions.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pilula no topo da tela enquanto um app de distracao esta aberto:
 * "instagram  3:12 · hoje 47 min". Nao pega toque. A cada 10 min de sessao ela pulsa.
 */
final class Contador {

    private final Context ctx;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, String> nomes = new HashMap<>();
    private TextView view;
    private String pkg;
    private long inicio, baseHoje;
    private int ultimoMarco;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (view == null) return;
            long sessao = System.currentTimeMillis() - inicio;
            long hoje = baseHoje + sessao;
            view.setText(String.format(Locale.US, "%s   %d:%02d  ·  hoje %s", nomes.get(pkg),
                    sessao / 60000, (sessao / 1000) % 60, minutos(hoje)));
            int marco = (int) (sessao / 600000);
            if (marco > ultimoMarco) {
                ultimoMarco = marco;
                view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(220)
                        .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(220).start()).start();
            }
            ui.postDelayed(this, 1000);
        }
    };

    Contador(Context ctx) {
        this.ctx = ctx;
        this.wm = ctx.getSystemService(WindowManager.class);
        for (Config.Item i : Config.DISTRACTIONS) for (String p : i.pkgs) nomes.put(p, i.label);
    }

    boolean vigia(String p) {
        return nomes.containsKey(p);
    }

    /** chamado a cada troca de app em primeiro plano */
    void appEmFrente(String p) {
        if (p != null && p.equals(pkg)) return;
        esconder();
        if (p == null || !vigia(p)) return;
        pkg = p;
        inicio = System.currentTimeMillis();
        ultimoMarco = 0;
        baseHoje = 0;
        mostrar();
        new Thread(() -> {
            // o uso de hoje ja inclui o comecinho desta sessao; desconta pra nao contar duas vezes
            long hoje = Uso.get(Uso.hoje(ctx), p);
            long base = Math.max(0, hoje - (System.currentTimeMillis() - inicio));
            ui.post(() -> {
                if (p.equals(pkg)) baseHoje = base;
            });
        }).start();
    }

    private void mostrar() {
        view = new TextView(ctx);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.03f);
        view.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(215, 0, 0, 0));
        g.setStroke(dp(1), Color.argb(70, 255, 255, 255));
        g.setCornerRadius(dp(30));
        view.setBackground(g);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = statusBar() + dp(6);
        try {
            wm.addView(view, lp);
        } catch (Exception e) {
            view = null;
            return;
        }
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(200).start();
        ui.post(tick);
    }

    void esconder() {
        ui.removeCallbacks(tick);
        pkg = null;
        if (view == null) return;
        try {
            wm.removeView(view);
        } catch (Exception ignored) {
        }
        view = null;
    }

    private static String minutos(long ms) {
        long min = ms / 60000;
        if (min < 60) return min + " min";
        return (min / 60) + "h" + String.format(Locale.US, "%02d", min % 60);
    }

    private int statusBar() {
        int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? ctx.getResources().getDimensionPixelSize(id) : dp(28);
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
