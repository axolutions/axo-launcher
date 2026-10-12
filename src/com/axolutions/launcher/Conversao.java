package com.axolutions.launcher;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "10 km em milhas", "30 graus em fahrenheit", "100 dolares em reais". */
final class Conversao {

    static final class Unidade {
        final String cat, nome;
        /** fator pra unidade base da categoria; moeda: codigo da cotacao */
        final double fator;
        final String codigo;

        Unidade(String cat, double fator, String nome, String codigo) {
            this.cat = cat;
            this.fator = fator;
            this.nome = nome;
            this.codigo = codigo;
        }
    }

    private static final Map<String, Unidade> U = new HashMap<>();

    private static void un(String cat, double fator, String nome, String... apelidos) {
        Unidade u = new Unidade(cat, fator, nome, null);
        for (String a : apelidos) U.put(a, u);
    }

    private static void moeda(String codigo, String nome, String... apelidos) {
        Unidade u = new Unidade("moeda", 0, nome, codigo);
        for (String a : apelidos) U.put(a, u);
    }

    static {
        un("comp", 1000, "km", "km", "quilometro", "quilometros", "kilometro", "kilometros");
        un("comp", 1, "m", "m", "metro", "metros");
        un("comp", 0.01, "cm", "cm", "centimetro", "centimetros");
        un("comp", 0.001, "mm", "mm", "milimetro", "milimetros");
        un("comp", 1609.344, "milhas", "mi", "milha", "milhas", "mile", "miles");
        un("comp", 0.3048, "pés", "ft", "pe", "pes", "feet", "foot");
        un("comp", 0.0254, "polegadas", "in", "pol", "polegada", "polegadas", "inch", "inches");
        un("massa", 1, "kg", "kg", "quilo", "quilos", "kilo", "kilos", "quilograma", "quilogramas");
        un("massa", 0.001, "g", "g", "grama", "gramas");
        un("massa", 0.45359237, "lb", "lb", "lbs", "libra", "libras", "pound", "pounds");
        un("massa", 0.0283495, "oz", "oz", "onca", "oncas", "ounce", "ounces");
        un("vol", 1, "L", "l", "litro", "litros");
        un("vol", 0.001, "ml", "ml", "mililitro", "mililitros");
        un("vol", 3.78541, "galões", "gal", "galao", "galoes", "gallon", "gallons");
        un("vel", 1, "km/h", "km/h", "kmh", "kph");
        un("vel", 1.609344, "mph", "mph");
        un("temp", 0, "°C", "c", "°c", "celsius", "graus", "grau", "graus celsius");
        un("temp", 0, "°F", "f", "°f", "fahrenheit", "graus fahrenheit");
        un("temp", 0, "K", "k", "kelvin");
        moeda("BRL", "reais", "real", "reais", "brl", "r$");
        moeda("USD", "dólares", "dolar", "dolares", "usd", "us$", "dollar", "dollars");
        moeda("EUR", "euros", "euro", "euros", "eur", "€");
        moeda("GBP", "libras esterlinas", "gbp", "libra esterlina", "libras esterlinas", "£");
        moeda("ARS", "pesos argentinos", "ars", "peso argentino", "pesos argentinos", "pesos");
        moeda("BTC", "bitcoins", "btc", "bitcoin", "bitcoins");
    }

    private static final Pattern P = Pattern.compile(
            "^(?:quanto (?:e|da|sao|fica|custa|vale)\\s+)?(?:(r\\$|us\\$|€|£)\\s*)?(-?\\d+(?:[.,]\\d+)*)\\s*(.*?)\\s+(?:em|para|pra|to|in|->|=)\\s+(.+?)\\??$");

    /** pedido entendido: quantidade e as duas unidades */
    static final class Pedido {
        double valor;
        Unidade de, para;
    }

    static Pedido ler(String norm) {
        Matcher m = P.matcher(norm.trim());
        if (!m.matches()) return null;
        String de = m.group(3).trim();
        if (de.isEmpty() && m.group(1) != null) de = m.group(1);
        Unidade a = U.get(de), b = U.get(m.group(4).trim());
        if (a == null || b == null || !a.cat.equals(b.cat) || a == b) return null;
        Pedido p = new Pedido();
        String n = m.group(2);
        n = n.contains(",") ? n.replace(".", "").replace(",", ".") : n.replaceAll("\\.(?=\\d{3}(\\D|$))", "");
        try {
            p.valor = Double.parseDouble(n);
        } catch (NumberFormatException e) {
            return null;
        }
        p.de = a;
        p.para = b;
        return p;
    }

    /** {resultado grande, linha de detalhe}; moeda sem cotacao em cache devolve null no resultado */
    static String[] calcular(Pedido p, SharedPreferences prefs) {
        double out;
        String detalhe = Calc.formatar(p.valor) + " " + p.de.nome;
        switch (p.de.cat) {
            case "temp":
                double k = toKelvin(p.valor, p.de.nome);
                out = fromKelvin(k, p.para.nome);
                break;
            case "moeda":
                Double a = taxa(prefs, p.de.codigo), b = taxa(prefs, p.para.codigo);
                if (a == null || b == null) return new String[]{null, detalhe + " · buscando cotação…"};
                out = p.valor * a / b;
                long at = prefs.getLong("fxAt", 0);
                detalhe += " · cotação de " + new SimpleDateFormat("HH:mm", Locale.US).format(new Date(at));
                return new String[]{dinheiro(out, p.para.codigo), detalhe};
            default:
                out = p.valor * p.de.fator / p.para.fator;
        }
        return new String[]{Calc.formatar(arredonda(out)) + " " + p.para.nome, detalhe};
    }

    private static double arredonda(double v) {
        return Math.abs(v) >= 100 ? Math.round(v * 100) / 100.0 : Math.round(v * 10000) / 10000.0;
    }

    private static double toKelvin(double v, String u) {
        if (u.equals("°C")) return v + 273.15;
        if (u.equals("°F")) return (v - 32) * 5 / 9 + 273.15;
        return v;
    }

    private static double fromKelvin(double k, String u) {
        if (u.equals("°C")) return k - 273.15;
        if (u.equals("°F")) return (k - 273.15) * 9 / 5 + 32;
        return k;
    }

    static String dinheiro(double v, String codigo) {
        DecimalFormat f = new DecimalFormat(codigo.equals("BTC") ? "#,##0.########" : "#,##0.00",
                new DecimalFormatSymbols(new Locale("pt", "BR")));
        String simbolo;
        switch (codigo) {
            case "BRL": simbolo = "R$ "; break;
            case "USD": simbolo = "US$ "; break;
            case "EUR": simbolo = "€ "; break;
            case "GBP": simbolo = "£ "; break;
            default: simbolo = codigo + " ";
        }
        return simbolo + f.format(v);
    }

    /** reais por 1 unidade da moeda */
    private static Double taxa(SharedPreferences prefs, String codigo) {
        if ("BRL".equals(codigo)) return 1.0;
        float v = prefs.getFloat("fx" + codigo, -1);
        return v > 0 ? (double) v : null;
    }

    static boolean cotacaoVelha(SharedPreferences prefs) {
        return System.currentTimeMillis() - prefs.getLong("fxAt", 0) > 60 * 60 * 1000;
    }

    /** bloqueante: chamar fora da thread de UI. Fonte: AwesomeAPI (gratis, sem chave). */
    static boolean atualizarCotacoes(SharedPreferences prefs) {
        try {
            URL u = new URL("https://economia.awesomeapi.com.br/json/last/USD-BRL,EUR-BRL,GBP-BRL,ARS-BRL,BTC-BRL");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            String body;
            try (InputStream in = c.getInputStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
                body = s.hasNext() ? s.next() : "";
            }
            JSONObject j = new JSONObject(body);
            SharedPreferences.Editor e = prefs.edit();
            for (String code : new String[]{"USD", "EUR", "GBP", "ARS", "BTC"}) {
                JSONObject o = j.optJSONObject(code + "BRL");
                if (o != null) e.putFloat("fx" + code, (float) o.getDouble("bid"));
            }
            e.putLong("fxAt", System.currentTimeMillis()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Conversao() {
    }
}
