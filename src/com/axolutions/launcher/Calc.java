package com.axolutions.launcher;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Calculadora do "+": "76 x 2", "18% de 3450", "200 + 10%", "(2+3)^2", "1.500,50 / 3". */
final class Calc {

    private static final Pattern MILHAR = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{3})+(?![\\d.])");
    private static final Pattern VEZES = Pattern.compile("([\\d)])\\s*[x×]\\s*(?=[\\d(])");
    private static final Pattern PCT_DE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%\\s*(?:de|do|da)\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern MAIS_PCT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([+\\-])\\s*(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern SO_CONTA = Pattern.compile("^[\\d.\\s()+\\-*/^]+$");
    private static final Pattern TEM_OPERACAO = Pattern.compile("[\\d)]\\s*[+\\-*/^]\\s*[\\d(]");

    private Calc() {
    }

    /** texto ja normalizado (minusculo, sem acento) -> so numeros e operadores */
    static String normalizar(String t) {
        String s = t.trim()
                .replaceAll("^(quanto (e|da|sao|fica)|calcula(r)?|calcule|conta)\\s+", "")
                .replaceAll("[?=]+$", "")
                .trim();
        s = replaceAll(MILHAR, s, m -> m.group().replace(".", ""));
        s = s.replaceAll("(\\d),(\\d)", "$1.$2")
                .replace('÷', '/').replace('×', '*').replace('−', '-');
        s = VEZES.matcher(s).replaceAll("$1*");
        s = s.replaceAll("\\s+vezes\\s+", "*").replaceAll("\\s+mais\\s+", "+").replaceAll("\\s+menos\\s+", "-")
                .replaceAll("\\s+dividido por\\s+", "/").replaceAll("\\s+elevado a\\s+", "^");
        s = replaceAll(PCT_DE, s, m -> "(" + m.group(1) + "/100*" + m.group(2) + ")");
        s = replaceAll(MAIS_PCT, s, m -> "(" + m.group(1) + "*(1" + m.group(2) + m.group(3) + "/100))");
        return s.replace("%", "/100");
    }

    static boolean ehConta(String norm) {
        String s = normalizar(norm);
        return SO_CONTA.matcher(s).matches() && TEM_OPERACAO.matcher(s).find() && avaliar(s) != null;
    }

    /** "22 -", "15 x": so numeros e operador, faltando terminar */
    static boolean contaPelaMetade(String norm) {
        String s = normalizar(norm);
        return SO_CONTA.matcher(s).matches() && s.matches(".*\\d.*") && s.matches(".*[+\\-*/^]\\s*$");
    }

    static Double avaliar(String expr) {
        try {
            Parser p = new Parser(expr.replace(" ", ""));
            double v = p.soma();
            if (p.i != p.s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String formatar(double v) {
        DecimalFormatSymbols sy = new DecimalFormatSymbols(new Locale("pt", "BR"));
        DecimalFormat f = new DecimalFormat(Math.abs(v) >= 1e15 ? "0.######E0" : "#,##0.######", sy);
        String out = f.format(v);
        return "-0".equals(out) ? "0" : out;
    }

    /** "76*2" -> "76 × 2", pra mostrar no cartao */
    static String bonito(String expr) {
        return expr.replace("*", " × ").replace("/", " ÷ ").replace("+", " + ").replace("^", " ^ ")
                .replaceAll("(?<=[\\d)])-", " − ").replace('.', ',').replaceAll("\\s+", " ").trim();
    }

    interface Troca {
        String de(Matcher m);
    }

    private static String replaceAll(Pattern p, String s, Troca t) {
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(t.de(m)));
        m.appendTail(sb);
        return sb.toString();
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        double soma() {
            double v = produto();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '+') { i++; v += produto(); }
                else if (c == '-') { i++; v -= produto(); }
                else break;
            }
            return v;
        }

        double produto() {
            double v = potencia();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '*') { i++; v *= potencia(); }
                else if (c == '/') { i++; v /= potencia(); }
                else break;
            }
            return v;
        }

        double potencia() {
            double b = unario();
            if (i < s.length() && s.charAt(i) == '^') {
                i++;
                return Math.pow(b, potencia());
            }
            return b;
        }

        double unario() {
            if (i < s.length() && s.charAt(i) == '-') { i++; return -unario(); }
            if (i < s.length() && s.charAt(i) == '+') { i++; return unario(); }
            return atomo();
        }

        double atomo() {
            if (s.charAt(i) == '(') {
                i++;
                double v = soma();
                if (i < s.length() && s.charAt(i) == ')') i++;
                return v;
            }
            int ini = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (ini == i) throw new IllegalArgumentException("esperava numero");
            return Double.parseDouble(s.substring(ini, i));
        }
    }
}
