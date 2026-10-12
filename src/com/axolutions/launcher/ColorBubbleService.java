package com.axolutions.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;

/**
 * Guardiao do Axo, sempre ligado em primeiro plano.
 *
 * 1. Prioridade: com ele de pe o HyperOS para de matar o processo na limpeza de memoria.
 *    Matar o processo derrubava o servico de gestos, o Android o marcava como "crashed"
 *    e nao religava: ficava a linha de gestos sem nenhum gesto funcionando.
 * 2. Se o servico de gestos cair mesmo assim, ele religa; nao deu, devolve os botoes.
 * 3. Plano B da bolinha, quando o servico de gestos esta desligado (sobreposicao comum,
 *    precisa de SYSTEM_ALERT_WINDOW, que a Xiaomi zera a cada update). Fora do ar no modo banco.
 */
public class ColorBubbleService extends Service {

    private static final String CHANNEL = "bolha";
    private static final String FSG = "force_fsg_nav_bar";
    private static final long CICLO_MS = 3000;

    private static ColorBubbleService instancia;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Bolha bolha;
    /** ciclos seguidos com a linha de gestos ligada e o servico fora do ar */
    private int semGestos;

    private final Runnable ciclo = new Runnable() {
        @Override
        public void run() {
            vigiarGestos();
            atualizarBolha();
            handler.postDelayed(this, CICLO_MS);
        }
    };

    public static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, ColorBubbleService.class));
        } catch (Exception ignored) {
        }
        atualizar();
    }

    /** a bolinha foi desligada no menu: o guardiao continua, so a bolinha sai */
    public static void stop(Context c) {
        atualizar();
        if (GestureService.ativo) GestureService.atualizarBolha();
    }

    /** reavalia na hora quem desenha a bolinha (o servico de gestos conectou ou caiu) */
    static void atualizar() {
        ColorBubbleService s = instancia;
        if (s != null) s.handler.post(s::atualizarBolha);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Axo ativo", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Axo ativo")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
        instancia = this;
        handler.post(ciclo);
    }

    private void atualizarBolha() {
        boolean quer = !GestureService.ativo
                && !getSharedPreferences("axo", MODE_PRIVATE).getBoolean("gestosOffPorBanco", false)
                && getSharedPreferences("axo", MODE_PRIVATE).getBoolean("bubble", true)
                && Settings.canDrawOverlays(this);
        if (quer && bolha == null) {
            try {
                bolha = new Bolha(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                bolha.mostrar();
            } catch (Exception e) {
                bolha = null;
            }
        } else if (!quer && bolha != null) {
            bolha.esconder();
            bolha = null;
        }
    }

    /**
     * Linha de gestos ligada sem o servico atendendo = celular sem navegacao.
     * ~6 s assim: regrava a lista de acessibilidade (tira a marca de "crashed" e o Android religa).
     * ~12 s e nada: devolve os botoes.
     */
    private void vigiarGestos() {
        ContentResolver cr = getContentResolver();
        if (GestureService.ativo || Settings.Global.getInt(cr, FSG, 0) != 1) {
            semGestos = 0;
            return;
        }
        semGestos++;
        try {
            if (semGestos == 2 && servicoNaLista(cr)) {
                String lista = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, lista);
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                android.util.Log.w("Axo", "guardiao: servico de gestos fora do ar, religando");
            } else if (semGestos >= 4) {
                Settings.Global.putInt(cr, FSG, 0);
                semGestos = 0;
                android.util.Log.w("Axo", "guardiao: gestos nao voltaram, botoes de volta");
            }
        } catch (SecurityException ignored) {
        }
    }

    private boolean servicoNaLista(ContentResolver cr) {
        String v = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (v == null) return false;
        ComponentName meu = new ComponentName(this, GestureService.class);
        for (String s : v.split(":")) if (meu.equals(ComponentName.unflattenFromString(s.trim()))) return true;
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        instancia = null;
        if (bolha != null) bolha.esconder();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
