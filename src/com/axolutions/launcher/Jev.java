package com.axolutions.launcher;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import com.axolutions.launcher.Intencao.Tipo;

/**
 * Jev (typesafe/jev-1.13) via OpenRouter, igual ao Mordomo: devolve uma probabilidade
 * por opcao, entao so responde um dos tipos oferecidos. Medido em 12/10/2026:
 * ~0,6 s e US$ 0,000018 por chamada.
 *
 * So e chamado quando o offline fica em duvida. Cache por frase, teto diario,
 * e qualquer falha devolve null (fica o offline).
 */
final class Jev {

    private static final String URL_JEV = "https://openrouter.ai/api/alpha/decisions";
    private static final String MODELO = "typesafe/jev-1.13";
    static final int TETO_DIA = 300;
    private static final int TIMEOUT_MS = 1800;

    static final class Resposta {
        Tipo tipo;
        float conf;
    }

    private static final Map<String, Resposta> CACHE = new LinkedHashMap<String, Resposta>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Resposta> e) {
            return size() > 120;
        }
    };

    private Jev() {
    }

    static boolean ligado() {
        return Secrets.OPENROUTER_API_KEY != null && Secrets.OPENROUTER_API_KEY.length() >= 20;
    }

    static synchronized Resposta doCache(String chave) {
        return CACHE.get(chave);
    }

    private static String hoje() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    static int chamadasHoje(SharedPreferences prefs) {
        return hoje().equals(prefs.getString("jevDia", "")) ? prefs.getInt("jevN", 0) : 0;
    }

    /** bloqueante; chamar fora da thread de UI */
    static Resposta classificar(SharedPreferences prefs, String chave, String texto) {
        synchronized (Jev.class) {
            if (CACHE.containsKey(chave)) return CACHE.get(chave);
            if (chamadasHoje(prefs) >= TETO_DIA) return null;
            prefs.edit().putString("jevDia", hoje()).putInt("jevN", chamadasHoje(prefs) + 1).apply();
        }
        try {
            JSONObject criterios = new JSONObject();
            for (Tipo t : Tipo.values()) if (t.criterio != null) criterios.put(t.name().toLowerCase(Locale.ROOT), t.criterio);
            criterios.put("nenhum", "curto, vago ou incompleto demais para saber ainda");
            JSONObject pergunta = new JSONObject()
                    .put("type", "choice")
                    .put("instructions", "No celular de uma pessoa no Brasil, o que ela quer fazer com esta frase digitada na tela inicial?")
                    .put("criteria", criterios);
            JSONObject body = new JSONObject()
                    .put("model", MODELO)
                    .put("state", new JSONObject().put("mensagem", texto))
                    .put("questions", new JSONObject().put("tipo", pergunta));

            HttpURLConnection c = (HttpURLConnection) new URL(URL_JEV).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("authorization", "Bearer " + Secrets.OPENROUTER_API_KEY);
            c.setRequestProperty("content-type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() != 200) return null;
            String resp;
            try (InputStream in = c.getInputStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
                resp = s.hasNext() ? s.next() : "";
            }
            JSONObject j = new JSONObject(resp);
            JSONObject tipo = j.getJSONObject("answers").getJSONObject("tipo");
            JSONObject usage = j.optJSONObject("usage");
            if (usage != null) {
                prefs.edit().putFloat("jevCusto", prefs.getFloat("jevCusto", 0f) + (float) usage.optDouble("cost", 0)).apply();
            }
            Resposta r = new Resposta();
            String escolha = tipo.optString("choice", "nenhum");
            for (Tipo t : Tipo.values()) if (t.name().toLowerCase(Locale.ROOT).equals(escolha)) r.tipo = t;
            r.conf = (float) tipo.optDouble("confidence", 0);
            synchronized (Jev.class) {
                CACHE.put(chave, r);
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }
}
