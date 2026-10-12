package com.axolutions.launcher;

import android.content.ComponentName;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * App de banco na frente = servico de gestos fora do ar.
 * O Nubank recusa abrir ("caracteristicas suspeitas") se ve um servico de acessibilidade
 * de fora da Play Store ligado. Aqui o Axo tira o proprio servico da lista antes do banco
 * abrir (ou assim que ele aparece na frente) e poe de volta quando a tela inicial volta.
 * Enquanto isso valem os botoes de navegacao, sem bolinha e sem contador.
 */
final class ModoBanco {

    private static final String LISTA = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
    /** so religa o que o proprio modo banco desligou; desligado na mao continua desligado */
    private static final String PREF = "gestosOffPorBanco";
    private static final long VIGIA_MS = 1500;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable vigia;

    private ModoBanco() {
    }

    static boolean ehBanco(String pkg) {
        if (pkg == null) return false;
        for (String b : Config.BANCOS) if (b.equals(pkg)) return true;
        return false;
    }

    /** true se o servico estava ligado e saiu agora */
    static boolean desligar(Context c) {
        ContentResolver cr = c.getContentResolver();
        List<String> itens = itens(Settings.Secure.getString(cr, LISTA));
        ComponentName meu = new ComponentName(c, GestureService.class);
        boolean tirou = false;
        for (int i = itens.size() - 1; i >= 0; i--) {
            if (meu.equals(ComponentName.unflattenFromString(itens.get(i)))) {
                itens.remove(i);
                tirou = true;
            }
        }
        if (!tirou) return false;
        try {
            Settings.Secure.putString(cr, LISTA, String.join(":", itens));
            if (itens.isEmpty()) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        } catch (SecurityException e) {
            return false;
        }
        c.getSharedPreferences("axo", Context.MODE_PRIVATE).edit().putBoolean(PREF, true).apply();
        vigiar(c.getApplicationContext());
        return true;
    }

    /**
     * Enquanto os gestos estao fora do ar, olha (pelo tempo de uso, sem acessibilidade) qual app
     * esta na frente; saiu do banco pra qualquer outro app, religa. Antes so a tela inicial religava,
     * e quem ia do banco pra outro app pelos recentes ficava sem gestos e sem bolinha.
     */
    static void vigiar(Context app) {
        if (vigia != null) return;
        vigia = new Runnable() {
            @Override
            public void run() {
                if (!app.getSharedPreferences("axo", Context.MODE_PRIVATE).getBoolean(PREF, false)) {
                    vigia = null;
                    return;
                }
                String frente = appNaFrente(app);
                if (frente != null && !ehBanco(frente)) {
                    vigia = null;
                    religar(app);
                    return;
                }
                handler.postDelayed(this, VIGIA_MS);
            }
        };
        handler.postDelayed(vigia, VIGIA_MS);
    }

    /** ultimo app que foi pra frente nos ultimos minutos (null = nao deu pra saber) */
    static String appNaFrente(Context c) {
        try {
            UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
            long agora = System.currentTimeMillis();
            UsageEvents ev = usm.queryEvents(agora - 10 * 60_000L, agora);
            UsageEvents.Event e = new UsageEvents.Event();
            String ultimo = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) ultimo = e.getPackageName();
            }
            return ultimo;
        } catch (Exception ex) {
            return null;
        }
    }

    /** de volta na tela inicial: religa se foi o modo banco que desligou */
    static void religar(Context c) {
        if (!c.getSharedPreferences("axo", Context.MODE_PRIVATE).getBoolean(PREF, false)) return;
        ContentResolver cr = c.getContentResolver();
        List<String> itens = itens(Settings.Secure.getString(cr, LISTA));
        ComponentName meu = new ComponentName(c, GestureService.class);
        boolean tem = false;
        for (String s : itens) if (meu.equals(ComponentName.unflattenFromString(s))) tem = true;
        try {
            if (!tem) {
                itens.add(meu.flattenToString());
                Settings.Secure.putString(cr, LISTA, String.join(":", itens));
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (SecurityException e) {
            return;
        }
        c.getSharedPreferences("axo", Context.MODE_PRIVATE).edit().remove(PREF).apply();
    }

    private static List<String> itens(String v) {
        List<String> out = new ArrayList<>();
        if (v == null || v.isEmpty() || "null".equals(v)) return out;
        for (String s : v.split(":")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }
}
