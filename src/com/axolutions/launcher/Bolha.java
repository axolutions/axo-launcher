package com.axolutions.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

/**
 * Bolinha flutuante: enquanto o dedo esta nela, a tela fica colorida.
 * Soltou, volta pro preto e branco. Arrastar segurando muda ela de lugar.
 *
 * Quem desenha e o servico de gestos (janela de acessibilidade), que nao depende
 * da permissao de sobreposicao que a Xiaomi zera a cada atualizacao do app.
 */
final class Bolha {

    private static final String DALTONIZER = "accessibility_display_daltonizer";
    private static final String DALTONIZER_ON = "accessibility_display_daltonizer_enabled";

    private final Context ctx;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private final int tipoJanela;
    private BolhaView view;
    private WindowManager.LayoutParams lp;

    Bolha(Context ctx, int tipoJanela) {
        this.ctx = ctx;
        this.tipoJanela = tipoJanela;
        this.wm = ctx.getSystemService(WindowManager.class);
        this.prefs = ctx.getSharedPreferences("axo", Context.MODE_PRIVATE);
    }

    /** true = preto e branco. Precisa de WRITE_SECURE_SETTINGS (concedida via adb). */
    static boolean setGray(Context c, boolean gray) {
        try {
            ContentResolver cr = c.getContentResolver();
            Settings.Secure.putInt(cr, DALTONIZER_ON, gray ? 1 : 0);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    boolean visivel() {
        return view != null;
    }

    void mostrar() {
        if (view != null) return;
        try {
            Settings.Secure.putInt(ctx.getContentResolver(), DALTONIZER, 0); // 0 = monocromatico
        } catch (SecurityException ignored) {
        }
        setGray(ctx, true);

        int size = dp(46);
        lp = new WindowManager.LayoutParams(size, size, tipoJanela,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        int sw = ctx.getResources().getDisplayMetrics().widthPixels;
        int sh = ctx.getResources().getDisplayMetrics().heightPixels;
        lp.x = prefs.getInt("bx", sw - size - dp(20));
        lp.y = prefs.getInt("by", (int) (sh * 0.62));

        view = new BolhaView(ctx);
        view.setOnTouchListener(new View.OnTouchListener() {
            final int slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
            float downX, downY;
            int startX, startY;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        dragging = false;
                        setGray(ctx, false);
                        view.setPressedState(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > slop * 2) dragging = true;
                        if (dragging) {
                            lp.x = startX + (int) dx;
                            lp.y = startY + (int) dy;
                            wm.updateViewLayout(view, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        setGray(ctx, true);
                        view.setPressedState(false);
                        if (dragging) prefs.edit().putInt("bx", lp.x).putInt("by", lp.y).apply();
                        return true;
                }
                return false;
            }
        });
        wm.addView(view, lp);
    }

    void esconder() {
        if (view == null) return;
        try {
            wm.removeView(view);
        } catch (Exception ignored) {
        }
        view = null;
        setGray(ctx, true);
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    static final class BolhaView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean pressed;

        BolhaView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(c.getResources().getDisplayMetrics().density * 1.5f);
        }

        void setPressedState(boolean p) {
            pressed = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy);
            if (pressed) {
                // roxo da Axo, so aparece quando a cor esta ligada
                dot.setColor(0xCC5D0EC1);
                c.drawCircle(cx, cy, r * 0.62f, dot);
                ring.setColor(0xEEFFFFFF);
                c.drawCircle(cx, cy, r * 0.62f, ring);
            } else {
                dot.setColor(0x55000000);
                c.drawCircle(cx, cy, r * 0.42f, dot);
                ring.setColor(0x99FFFFFF);
                c.drawCircle(cx, cy, r * 0.42f, ring);
            }
        }
    }
}
