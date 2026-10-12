package com.axolutions.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.WindowManager;

/**
 * Plano B da bolinha, so quando o servico de gestos esta desligado: desenha como
 * sobreposicao comum (precisa de SYSTEM_ALERT_WINDOW, que a Xiaomi zera a cada update).
 */
public class ColorBubbleService extends Service {

    private static final String CHANNEL = "bolha";
    private Bolha bolha;

    public static void start(Context c) {
        if (GestureService.ativo) {
            GestureService.atualizarBolha();
            return;
        }
        if (!Settings.canDrawOverlays(c)) return;
        if (!c.getSharedPreferences("axo", MODE_PRIVATE).getBoolean("bubble", true)) return;
        try {
            c.startForegroundService(new Intent(c, ColorBubbleService.class));
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, ColorBubbleService.class));
        if (GestureService.ativo) GestureService.atualizarBolha();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Botão de cor", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Segure o ponto para ver em cores")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
        if (GestureService.ativo || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        bolha = new Bolha(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        bolha.mostrar();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (bolha != null) bolha.esconder();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
