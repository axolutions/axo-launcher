package com.axolutions.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * A Xiaomi so processa gestos com o launcher dela. Aqui o Axo faz os gestos:
 * borda esquerda/direita = voltar, baixo pra cima = inicio, baixo pra cima e segurar = recentes.
 * Trava de seguranca: enquanto o servico vive a barra fica escondida; se ele cair, os botoes voltam.
 */
public class GestureService extends AccessibilityService {

    private static final String FSG = "force_fsg_nav_bar";
    private static final String HIDE_LINE = "hide_gesture_line";

    /** a bolinha de cor mora aqui quando os gestos estao ligados */
    static volatile boolean ativo;
    private static GestureService instancia;

    private WindowManager wm;
    private final List<View> strips = new ArrayList<>();
    private Bolha bolha;
    private Contador contador;
    private int sideH, sideY;
    /** topo do teclado na tela; -1 = fechado */
    private int tecladoTopo = -1;

    /** mostra ou esconde a bolinha conforme a preferencia "bubble" */
    static void atualizarBolha() {
        GestureService s = instancia;
        if (s == null) return;
        s.getMainExecutor().execute(() -> {
            // com teclado aberto a bolinha sai, pra nao cair em cima das teclas
            boolean quer = s.getSharedPreferences("axo", MODE_PRIVATE).getBoolean("bubble", true) && s.tecladoTopo < 0;
            if (quer && !s.bolha.visivel()) s.bolha.mostrar();
            else if (!quer && s.bolha.visivel()) s.bolha.esconder();
        });
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = getSystemService(WindowManager.class);
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        int edge = dp(12);

        // laterais: so o miolo da altura, pra nao brigar com a cortina de notificacoes nem com o gesto de baixo
        sideH = (int) (sh * 0.62f);
        sideY = (int) (sh * 0.20f);
        addStrip(edge, sideH, 0, sideY, Gravity.TOP | Gravity.START, new BackTouch(+1));
        addStrip(edge, sideH, 0, sideY, Gravity.TOP | Gravity.END, new BackTouch(-1));
        addStrip(sw, dp(12), 0, 0, Gravity.BOTTOM | Gravity.START, new HomeTouch());

        setNavBarHidden(true);

        bolha = new Bolha(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        contador = new Contador(this);
        instancia = this;
        ativo = true;
        atualizarBolha();
        // o guardiao fica de pe (segura a prioridade do processo); so a bolinha dele sai
        ColorBubbleService.start(this);
    }

    private void addStrip(int w, int h, int x, int y, int gravity, View.OnTouchListener touch) {
        View v = new View(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.x = x;
        lp.y = y;
        v.setOnTouchListener(touch);
        wm.addView(v, lp);
        strips.add(v);
    }

    private void setNavBarHidden(boolean hidden) {
        try {
            Settings.Global.putInt(getContentResolver(), FSG, hidden ? 1 : 0);
            // o tracinho fica: sem ele o sistema cola o teclado no fundo da tela
            Settings.Global.putInt(getContentResolver(), HIDE_LINE, 0);
        } catch (SecurityException ignored) {
        }
    }

    private void teardown() {
        ativo = false;
        ColorBubbleService.atualizar();
        instancia = null;
        if (bolha != null) bolha.esconder();
        if (contador != null) contador.esconder();
        setNavBarHidden(false);
        for (View v : strips) {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }
        strips.clear();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        teardown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int topo = -1;
        String app = null;
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    Rect r = new Rect();
                    w.getBoundsInScreen(r);
                    if (r.height() > 0) topo = r.top;
                } else if (w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && w.isActive() && app == null) {
                    AccessibilityNodeInfo root = w.getRoot();
                    if (root != null && root.getPackageName() != null) app = root.getPackageName().toString();
                }
            }
        } catch (Exception ignored) {
        }
        // banco na frente: o servico sai do ar (o Nubank bloqueia com ele ligado); a tela inicial religa.
        // O pacote do evento pega o banco mesmo com um dialogo de outro app (localizacao do Google) por cima.
        CharSequence doEvento = event != null ? event.getPackageName() : null;
        if (ModoBanco.ehBanco(app) || (doEvento != null && ModoBanco.ehBanco(doEvento.toString()))) {
            if (!ModoBanco.desligar(this)) disableSelf();
            return;
        }
        // cortina de notificacao, teclado e o proprio Axo por cima nao contam como troca de app
        if (app != null && contador != null && !app.equals(getPackageName())) contador.appEmFrente(app);
        else if (app != null && app.equals(getPackageName()) && contador != null) contador.appEmFrente(null);
        if (topo != tecladoTopo) {
            tecladoTopo = topo;
            aplicarTeclado();
        }
    }

    /** teclado aberto: a faixa de baixo some e as laterais param acima dele */
    private void aplicarTeclado() {
        if (strips.size() < 3) return;
        View baixo = strips.get(2);
        baixo.setVisibility(tecladoTopo >= 0 ? View.GONE : View.VISIBLE);
        int h = tecladoTopo >= 0 ? Math.min(sideH, tecladoTopo - sideY - dp(12)) : sideH;
        for (int i = 0; i < 2; i++) {
            View v = strips.get(i);
            if (h < dp(80)) {
                v.setVisibility(View.GONE);
                continue;
            }
            v.setVisibility(View.VISIBLE);
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            if (lp.height != h) {
                lp.height = h;
                wm.updateViewLayout(v, lp);
            }
        }
        atualizarBolha();
    }

    @Override
    public void onInterrupt() {
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** dir = +1 na borda esquerda (puxa pra direita), -1 na direita */
    private class BackTouch implements View.OnTouchListener {
        final int dir;
        float downX;
        boolean armed;

        BackTouch(int dir) {
            this.dir = dir;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    armed = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    boolean now = (e.getRawX() - downX) * dir > dp(48);
                    if (now && !armed) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    armed = now;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (armed) performGlobalAction(GLOBAL_ACTION_BACK);
                    armed = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    armed = false;
                    return true;
            }
            return false;
        }
    }

    /** evento em coordenadas de tela: a faixa e minuscula, as coordenadas locais saem dela */
    private static MotionEvent raw(MotionEvent e) {
        MotionEvent c = MotionEvent.obtain(e);
        c.setLocation(e.getRawX(), e.getRawY());
        return c;
    }

    private class HomeTouch implements View.OnTouchListener {
        float downY;
        /** onde o dedo estava quando o "segurar" comecou a contar; -1 = nao esta contando */
        float holdY = -1;
        boolean recentsFired;
        android.view.VelocityTracker vel;
        final Runnable holdForRecents = new Runnable() {
            @Override
            public void run() {
                recentsFired = true;
                strips.get(2).performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                boolean ok = performGlobalAction(GLOBAL_ACTION_RECENTS);
                android.util.Log.d("Axo", "gesto: recentes pedido, sistema aceitou=" + ok);
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            float up = downY - e.getRawY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = e.getRawY();
                    holdY = -1;
                    recentsFired = false;
                    if (vel != null) vel.recycle();
                    vel = android.view.VelocityTracker.obtain();
                    vel.addMovement(raw(e));
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (vel != null) vel.addMovement(raw(e));
                    // subiu o suficiente e parou: agenda os recentes. So reagenda se o dedo andar
                    // de verdade; a tremida natural do dedo zerava a contagem e os recentes nunca abriam
                    if (recentsFired) return true;
                    if (up <= dp(90)) {
                        v.removeCallbacks(holdForRecents);
                        holdY = -1;
                    } else if (holdY < 0 || Math.abs(e.getRawY() - holdY) > dp(28)) {
                        v.removeCallbacks(holdForRecents);
                        holdY = e.getRawY();
                        v.postDelayed(holdForRecents, 250);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.removeCallbacks(holdForRecents);
                    // velocidade na hora de soltar: arrastou rapido = inicio; subiu e freou antes de soltar = recentes
                    float vy = 0;
                    if (vel != null) {
                        vel.addMovement(raw(e));
                        vel.computeCurrentVelocity(1000);
                        vy = -vel.getYVelocity();
                        vel.recycle();
                        vel = null;
                    }
                    android.util.Log.d("Axo", "gesto baixo: subiu " + (int) up + "px, " + (int) vy + "px/s, recentes=" + recentsFired);
                    if (!recentsFired && up > dp(90) && vy < dp(300)) {
                        recentsFired = true;
                        performGlobalAction(GLOBAL_ACTION_RECENTS);
                    } else if (!recentsFired && up > dp(40)) performGlobalAction(GLOBAL_ACTION_HOME);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(holdForRecents);
                    return true;
            }
            return false;
        }
    }
}
