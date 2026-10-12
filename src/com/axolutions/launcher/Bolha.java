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
        posicionar();

        view = new BolhaView(ctx, this);
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
                            int[] t = tela();
                            lp.x = limitar(startX + (int) dx, t[0] - lp.width);
                            lp.y = limitar(startY + (int) dy, t[1] - lp.height);
                            wm.updateViewLayout(view, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        setGray(ctx, true);
                        view.setPressedState(false);
                        if (dragging) {
                            // guarda em proporcao da tela: arrastada com o celular deitado, ela ficava fora da tela em pe
                            int[] t = tela();
                            prefs.edit()
                                    .putFloat("bxf", lp.x / (float) Math.max(1, t[0] - lp.width))
                                    .putFloat("byf", lp.y / (float) Math.max(1, t[1] - lp.height))
                                    .apply();
                        }
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

    /** coloca a bolinha na posicao salva, sempre dentro da tela atual (chamado tambem ao girar) */
    void posicionar() {
        if (lp == null) return;
        int[] t = tela();
        int livreX = t[0] - lp.width, livreY = t[1] - lp.height;
        float fx = prefs.getFloat("bxf", (livreX - dp(20)) / (float) Math.max(1, livreX));
        float fy = prefs.getFloat("byf", 0.62f);
        lp.x = limitar(Math.round(fx * livreX), livreX);
        lp.y = limitar(Math.round(fy * livreY), livreY);
        if (view != null) {
            try {
                wm.updateViewLayout(view, lp);
            } catch (Exception ignored) {
            }
        }
    }

    /** largura e altura da tela agora (muda quando o celular gira) */
    private int[] tela() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Rect r = wm.getCurrentWindowMetrics().getBounds();
            return new int[]{r.width(), r.height()};
        }
        android.util.DisplayMetrics m = ctx.getResources().getDisplayMetrics();
        return new int[]{m.widthPixels, m.heightPixels};
    }

    private static int limitar(int v, int max) {
        return Math.max(0, Math.min(v, Math.max(0, max)));
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    static final class BolhaView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bolha dona;
        private boolean pressed;

        BolhaView(Context c, Bolha dona) {
            super(c);
            this.dona = dona;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(c.getResources().getDisplayMetrics().density * 1.5f);
        }

        @Override
        protected void onConfigurationChanged(android.content.res.Configuration c) {
            super.onConfigurationChanged(c);
            dona.posicionar();
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
