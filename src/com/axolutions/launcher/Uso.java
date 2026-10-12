package com.axolutions.launcher;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tempo de tela hoje nos apps de distracao (precisa de GET_USAGE_STATS, concedida via adb). */
final class Uso {

    private Uso() {
    }

    static Set<String> pacotesDistracao() {
        Set<String> s = new HashSet<>();
        for (Config.Item i : Config.DISTRACTIONS) Collections.addAll(s, i.pkgs);
        return s;
    }

    /** ms em primeiro plano hoje, somando os eventos de abrir/fechar activity */
    static Map<String, Long> hoje(Context ctx) {
        Map<String, Long> out = new HashMap<>();
        Set<String> watch = pacotesDistracao();
        try {
            UsageStatsManager usm = ctx.getSystemService(UsageStatsManager.class);
            long start = Offline.hoje().getTimeInMillis(), now = System.currentTimeMillis();
            UsageEvents ev = usm.queryEvents(start, now);
            Map<String, Long> openSince = new HashMap<>();
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                String p = e.getPackageName();
                if (!watch.contains(p)) continue;
                int t = e.getEventType();
                if (t == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (!openSince.containsKey(p)) openSince.put(p, e.getTimeStamp());
                } else if (t == UsageEvents.Event.ACTIVITY_PAUSED || t == UsageEvents.Event.ACTIVITY_STOPPED) {
                    Long s = openSince.remove(p);
                    if (s == null) s = out.containsKey(p) ? null : start;
                    if (s != null) out.put(p, get(out, p) + (e.getTimeStamp() - s));
                }
            }
            for (Map.Entry<String, Long> o : openSince.entrySet()) {
                out.put(o.getKey(), get(out, o.getKey()) + (now - o.getValue()));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static long get(Map<String, Long> m, String p) {
        Long v = m.get(p);
        return v == null ? 0 : v;
    }
}
