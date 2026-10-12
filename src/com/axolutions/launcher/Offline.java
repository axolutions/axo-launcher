package com.axolutions.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.axolutions.launcher.Intencao.Tipo;

/**
 * Classificador por palavra-chave e extratores deterministicos (porte da ideia do
 * Mordomo/Shapeshift). O modelo, quando entra, so decide o tipo; os numeros saem daqui.
 *
 * Tudo roda sobre o texto normalizado (minusculo, sem acento), que tem o mesmo
 * comprimento do original em NFC: os indices batem e da pra recortar o original.
 */
final class Offline {

    private Offline() {
    }

    static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static Pattern p(String re) {
        return Pattern.compile(re);
    }

    // ------------------------------------------------------------------ gatilhos

    static final Pattern ALARME = p("\\b(me acord[ae]r?|acorda(r)? (as|a|com)|despertador|alarme|despertar|me desperta)\\b");
    static final Pattern TIMER = p("\\b(timer|temporizador|cronometro|cronometra|pomodoro|contagem regressiva)\\b");
    static final Pattern TIMER_SO = p("^(\\d+\\s*(s|seg|segundos?|min|mins|minutos?|h|horas?)|meia hora|uma hora)(\\s+de\\s+\\w+)?$");
    static final Pattern LEMBRETE = p("^(me )?(lembra(r)?|lembre|lembrete)( de| que| da| do)?\\b[:\\s]*|\\b(me lembr[ae]|nao (posso )?esquece(r)?|lembrete)\\b");
    static final Pattern PRECISO = p("^(preciso|tenho que|tenho de)\\b");
    static final Pattern EVENTO = p("\\b(reuniao|consulta|jantar|almoco|cafe com|encontro|call|aniversario|festa|evento|marcar|agendar|dentista|medico|entrevista|aula|prova|voo|show|cinema|daily|meet|visita)\\b");
    static final Pattern MARCAR = p("^(marcar|agendar|marca|agenda)\\s+");
    static final Pattern NOTA = p("^(anota(r|e)?|anotacao|nota|ideia|salva(r)? (uma )?nota)(\\s+(uma |a )?(ideia|nota))?\\b[:\\s-]*");
    static final Pattern MENSAGEM = p("\\b(manda(r)?|envia(r)?|mande|envie)\\s+(uma |um )?(msg|mensagem|zap|whats|whatsapp)\\b|\\b(msg|mensagem|zap|whats)\\s+(pro|pra|para)\\b|^(fala(r)?|conversa(r)?) com\\b");
    static final Pattern LIGAR = p("^(liga|ligar|ligue|telefona(r)?|telefone)\\b");
    static final Pattern IR = p("\\b(uber|taxi|como (eu )?chego|como chegar|rota (pra|para|ate)|me leva|navega(r)?)\\b|^(ir|vou) (pra|para|ate|no|na)\\b|^99\\b");
    static final Pattern FOCO = p("^(foco|prioridade|meta do dia|foco do dia|foco de hoje)\\s*[:\\-]\\s*|^foco\\s+(e\\s+)?");
    static final Pattern PESQUISA = p("^(pesquisa(r)?|google|busca(r)?|procura(r)?)\\b\\s*(no google\\s*)?");
    static final Pattern PERGUNTA = p("\\?$|^(o que|qual|quais|quem|como|quando|onde|por que|porque|quanto custa|quantos|quantas)\\b");
    static final Pattern ABRIR = p("^(abre|abrir|abra)\\s+(o |a )?");

    // ------------------------------------------------------------------ extratores

    static final Pattern DAQUI = p("\\b(?:daqui|dentro de)\\s+(?:a\\s+)?(\\d+|uma?|meia)\\s*(min|mins|minutos?|h|hs|horas?)\\b|\\bem\\s+(\\d+)\\s*(min|mins|minutos?|h|horas?)\\b");
    static final Pattern H_DOIS_PONTOS = p("(?:\\b(?:as|a|pras|para as|ate as)\\s+)?\\b(\\d{1,2})\\s*:\\s*(\\d{2})\\b(?:\\s*(?:h|hs|horas?)\\b)?");
    static final Pattern H_H = p("(?:\\b(?:as|a|pras|para as|ate as)\\s+)?\\b(\\d{1,2})\\s*(?:h|hs|horas?)(?:\\s*(?:e\\s+)?(meia|\\d{1,2})(?:\\s*(?:min|minutos?))?)?\\b");
    static final Pattern H_AS = p("\\b(?:as|pras|para as|ate as)\\s+(\\d{1,2})(?:\\s+e\\s+(meia|\\d{1,2}))?\\b(?!\\s*(?:min|minutos?|dias?|semanas?|%|/|:))");
    static final Pattern H_MEIO = p("\\b(?:ao\\s+|as\\s+|a\\s+)?(meio[- ]?dia|meia[- ]?noite)(?:\\s+e\\s+(meia|\\d{1,2}))?\\b");
    /** so no alarme: "alarme 7 e meia", "me acorda 6" */
    static final Pattern H_NU = p("\\b(\\d{1,2})(?:\\s+e\\s+(meia|\\d{1,2}))?\\b(?!\\s*(?:min|minutos?|dias?|%|/))");
    static final Pattern PERIODO = p("\\b(?:da|de|a)\\s+(manha|tarde|noite|madrugada)\\b");

    static final Pattern D_REL = p("\\b(?:(?:para|pra|de)\\s+)?(depois de amanha|amanha|hoje)\\b");
    static final Pattern D_SEM = p("\\b(?:(?:na|no|nesta|neste|essa|esse|esta|este|proxima|proximo)\\s+)?(segunda|terca|quarta|quinta|sexta|sabado|domingo)(?:[- ]feira)?(\\s+que vem)?\\b");
    static final Pattern D_DIA = p("\\b(?:no\\s+)?dia\\s+(\\d{1,2})(?:\\s+de\\s+(janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro))?\\b");
    static final Pattern D_BARRA = p("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b");
    static final Pattern D_SEMANA = p("\\b(?:na\\s+|no\\s+)?(semana que vem|proxima semana|mes que vem|proximo mes)\\b");

    static final Pattern DUR_HM = p("\\b(\\d+)\\s*(?:h|hs|horas?)\\s*(?:e\\s*)?(\\d+)\\s*(?:m|min|mins|minutos?)?\\b");
    static final Pattern DUR = p("\\b(\\d+)\\s*(h|hs|horas?|m|min|mins|minutos?|s|seg|segundos?)\\b");
    static final Pattern DUR_TEXTO = p("\\b(meia hora|uma hora|um minuto)\\b");

    static final Pattern PESSOA = Pattern.compile("(?i:\\bcom)\\s+(?:o\\s+|a\\s+)?(\\p{Lu}[\\p{L}']+(?:\\s+\\p{Lu}[\\p{L}']+)?)");
    static final Pattern CONECTOR_INI = p("^(de|do|da|pra|pro|para|as|a|o|e|que|no|na|em|me|ao|um|uma)\\b\\s*");
    static final Pattern CONECTOR_FIM = p("\\s*\\b(de|do|da|pra|pro|para|as|a|o|e|no|na|em|ate|ao|que)$");

    private static final String[] MESES = {"janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho",
            "agosto", "setembro", "outubro", "novembro", "dezembro"};
    private static final String[] SEMANA = {"domingo", "segunda", "terca", "quarta", "quinta", "sexta", "sabado"};

    // ------------------------------------------------------------------ classificacao

    static Intencao classificar(String t, List<HomeActivity.Entry> apps) {
        String tt = t.trim();
        Map<Tipo, Integer> pts = new EnumMap<>(Tipo.class);
        boolean forte = false;

        if (Calc.ehConta(tt)) { add(pts, Tipo.CONTA, 14); forte = true; }
        else if (Calc.contaPelaMetade(tt)) add(pts, Tipo.CONTA, 9);
        if (Conversao.ler(tt) != null) { add(pts, Tipo.CONVERSAO, 14); forte = true; }
        if (NOTA.matcher(tt).find()) { add(pts, Tipo.NOTA, 14); forte = true; }
        if (FOCO.matcher(tt).find()) { add(pts, Tipo.FOCO, 14); forte = true; }
        if (ALARME.matcher(tt).find()) add(pts, Tipo.ALARME, 12);
        if (TIMER.matcher(tt).find()) add(pts, Tipo.TIMER, 12);
        else if (TIMER_SO.matcher(tt).matches()) add(pts, Tipo.TIMER, 10);
        if (LIGAR.matcher(tt).find()) add(pts, Tipo.LIGAR, 13);
        if (MENSAGEM.matcher(tt).find()) add(pts, Tipo.MENSAGEM, 13);
        if (IR.matcher(tt).find()) add(pts, Tipo.IR, 11);

        boolean quando = temQuando(tt);
        if (EVENTO.matcher(tt).find()) add(pts, Tipo.EVENTO, quando ? 11 : 7);
        if (LEMBRETE.matcher(tt).find()) add(pts, Tipo.LEMBRETE, quando ? 12 : 10);
        else if (PRECISO.matcher(tt).find()) add(pts, Tipo.LEMBRETE, quando ? 8 : 5);
        if (quando && pts.isEmpty()) {
            add(pts, Tipo.EVENTO, 6);
            add(pts, Tipo.LEMBRETE, 5);
        }
        if (PESQUISA.matcher(tt).find()) add(pts, Tipo.PESQUISA, 13);
        else if (PERGUNTA.matcher(tt).find()) add(pts, Tipo.PESQUISA, 7);

        HomeActivity.Entry app = null;
        int appPts = 0;
        if (apps != null && tt.length() >= 2) {
            Matcher ab = ABRIR.matcher(tt);
            boolean pediuAbrir = ab.find();
            String q = pediuAbrir ? tt.substring(ab.end()).trim() : tt;
            for (HomeActivity.Entry e : apps) {
                String l = norm(e.label);
                int s = 0;
                if (l.equals(q)) s = 14;
                else if (q.length() >= 3 && l.startsWith(q)) s = pediuAbrir ? 12 : (q.length() >= 4 ? 6 : 0);
                if (s > appPts) {
                    appPts = s;
                    app = e;
                }
            }
            if (appPts > 0) add(pts, Tipo.APP, appPts);
        }

        Tipo top = null, segundo = null;
        for (Map.Entry<Tipo, Integer> e : pts.entrySet()) {
            if (top == null || e.getValue() > pts.get(top)) {
                segundo = top;
                top = e.getKey();
            } else if (segundo == null || e.getValue() > pts.get(segundo)) {
                segundo = e.getKey();
            }
        }
        Intencao it;
        int s1 = top == null ? 0 : pts.get(top);
        if (top == null || s1 < 5) {
            it = new Intencao(Tipo.PESQUISA, 0.3f);
        } else {
            int s2 = segundo == null ? 0 : pts.get(segundo);
            float conf = Math.min(1f, s1 / 14f) * (s1 / (float) (s1 + s2));
            it = new Intencao(top, conf);
        }
        int soma = 0;
        for (int v : pts.values()) soma += v;
        for (Map.Entry<Tipo, Integer> e : pts.entrySet()) it.probs.put(e.getKey(), e.getValue() / (float) soma);
        it.forte = forte;
        it.app = app;
        return it;
    }

    private static void add(Map<Tipo, Integer> m, Tipo t, int v) {
        Integer cur = m.get(t);
        m.put(t, cur == null ? v : Math.max(cur, v));
    }

    static boolean temQuando(String t) {
        return DAQUI.matcher(t).find() || hora(t, new boolean[t.length()]) != null || data(t, new boolean[t.length()]) != null;
    }

    // ------------------------------------------------------------------ preenchimento

    /**
     * Preenche os dados do tipo ja decidido. texto = original em NFC, t = norm(texto).
     */
    static void preencher(Intencao it, String texto, String t, Context ctx) {
        boolean[] corte = new boolean[t.length()];
        Calendar agora = Calendar.getInstance();
        switch (it.tipo) {
            case CONTA: {
                String s = Calc.normalizar(t);
                Double v = Calc.avaliar(s);
                it.resultado = v != null ? Calc.formatar(v) : null;
                it.detalhe = Calc.bonito(s);
                break;
            }
            case TIMER: {
                it.duracaoSeg = duracao(t, corte);
                cortar(TIMER, t, corte);
                it.titulo = sobra(texto, t, corte);
                break;
            }
            case ALARME: {
                cortar(ALARME, t, corte);
                lerQuando(it, t, corte, agora);
                if (it.hora < 0 && it.relativo < 0) {
                    Matcher n = H_NU.matcher(t);
                    if (n.find() && Integer.parseInt(n.group(1)) <= 23) {
                        it.hora = Integer.parseInt(n.group(1));
                        it.minuto = minutos(n.group(2));
                        marcar(corte, n.start(), n.end());
                    }
                }
                it.titulo = sobra(texto, t, corte);
                break;
            }
            case EVENTO:
            case LEMBRETE: {
                cortar(LEMBRETE, t, corte);
                cortar(PRECISO, t, corte);
                cortar(MARCAR, t, corte);
                lerQuando(it, t, corte, agora);
                Matcher pm = PESSOA.matcher(texto);
                if (pm.find()) it.pessoa = pm.group(1);
                it.titulo = sobra(texto, t, corte);
                completarQuando(it, agora);
                break;
            }
            case NOTA:
                cortar(NOTA, t, corte);
                it.titulo = sobra(texto, t, corte, false);
                break;
            case FOCO:
                cortar(FOCO, t, corte);
                it.titulo = sobra(texto, t, corte, false);
                break;
            case MENSAGEM:
            case LIGAR: {
                Matcher m = (it.tipo == Tipo.LIGAR ? LIGAR : MENSAGEM).matcher(t);
                int fim = m.find() ? m.end() : 0;
                String resto = texto.substring(fim).trim();
                String rn = norm(resto);
                Matcher sep = Pattern.compile(":|\\s+(dizendo|falando|perguntando|avisando|que)\\s+").matcher(rn);
                String alvo = resto;
                if (sep.find()) {
                    alvo = resto.substring(0, sep.start());
                    it.mensagem = resto.substring(sep.end()).trim();
                }
                String an = norm(alvo);
                Matcher c;
                while ((c = CONECTOR_INI.matcher(an)).find() && c.end() > 0 && c.end() < an.length()) {
                    alvo = alvo.substring(c.end());
                    an = an.substring(c.end());
                }
                it.pessoa = alvo.trim();
                if (!it.pessoa.isEmpty()) {
                    String[] ct = contato(ctx, it.pessoa);
                    if (ct != null) {
                        it.contatoNome = ct[0];
                        it.contatoNumero = ct[1];
                    }
                }
                break;
            }
            case IR: {
                Matcher m = IR.matcher(t);
                int fim = m.find() ? m.end() : 0;
                String resto = texto.substring(fim);
                String rn = norm(resto);
                Matcher pra = Pattern.compile("\\b(pra|para|ate|no|na|pro)\\s+").matcher(rn);
                if (pra.find()) resto = resto.substring(pra.end());
                it.destino = resto.trim();
                break;
            }
            case PESQUISA:
                cortar(PESQUISA, t, corte);
                it.titulo = sobra(texto, t, corte, false);
                break;
            case CONVERSAO:
            case APP:
                break;
        }
    }

    /** hora, data e "daqui X" -> campos do it; marca o que usou em corte */
    private static void lerQuando(Intencao it, String t, boolean[] corte, Calendar agora) {
        Matcher d = DAQUI.matcher(t);
        if (d.find()) {
            String n = d.group(1) != null ? d.group(1) : d.group(3);
            String u = d.group(2) != null ? d.group(2) : d.group(4);
            int qtd = "meia".equals(n) ? 30 : ("um".equals(n) || "uma".equals(n)) ? 1 : Integer.parseInt(n);
            int min = u.startsWith("h") ? ("meia".equals(n) ? 30 : qtd * 60) : qtd;
            it.relativo = System.currentTimeMillis() + min * 60_000L;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(it.relativo);
            it.hora = c.get(Calendar.HOUR_OF_DAY);
            it.minuto = c.get(Calendar.MINUTE);
            it.data = c;
            marcar(corte, d.start(), d.end());
            return;
        }
        int[] h = hora(t, corte);
        if (h != null) {
            it.hora = h[0];
            it.minuto = h[1];
        }
        Calendar dt = data(t, corte);
        if (dt != null) it.data = dt;
    }

    /** completa o que faltou com padroes sensatos (marcados como padrao pra UI mostrar esmaecido) */
    private static void completarQuando(Intencao it, Calendar agora) {
        if (it.relativo > 0) return;
        boolean lembrete = it.tipo == Tipo.LEMBRETE;
        if (it.data == null && it.hora < 0) {
            if (lembrete) {
                it.hora = 9;
                it.minuto = 0;
                it.horaEhPadrao = true;
                it.data = hoje();
                if (agora.get(Calendar.HOUR_OF_DAY) >= 9) it.data.add(Calendar.DAY_OF_YEAR, 1);
            } else {
                it.data = hoje();
            }
            it.dataEhPadrao = true;
        } else if (it.data == null) {
            it.data = hoje();
            Calendar alvo = (Calendar) it.data.clone();
            alvo.set(Calendar.HOUR_OF_DAY, it.hora);
            alvo.set(Calendar.MINUTE, it.minuto);
            if (alvo.before(agora)) it.data.add(Calendar.DAY_OF_YEAR, 1);
            it.dataEhPadrao = true;
        } else if (it.hora < 0 && lembrete) {
            it.hora = 9;
            it.minuto = 0;
            it.horaEhPadrao = true;
        }
    }

    static Calendar hoje() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** {hora, minuto} ou null */
    static int[] hora(String t, boolean[] corte) {
        int h = -1, m = 0, ini = -1, fim = -1;
        Matcher x;
        if ((x = H_DOIS_PONTOS.matcher(t)).find()) {
            h = Integer.parseInt(x.group(1));
            m = Integer.parseInt(x.group(2));
            ini = x.start();
            fim = x.end();
        } else if ((x = H_H.matcher(t)).find()) {
            h = Integer.parseInt(x.group(1));
            m = minutos(x.group(2));
            ini = x.start();
            fim = x.end();
        } else if ((x = H_AS.matcher(t)).find()) {
            h = Integer.parseInt(x.group(1));
            m = minutos(x.group(2));
            ini = x.start();
            fim = x.end();
        } else if ((x = H_MEIO.matcher(t)).find()) {
            h = x.group(1).startsWith("meio") ? 12 : 0;
            m = minutos(x.group(2));
            ini = x.start();
            fim = x.end();
        }
        if (h < 0 || h > 23 || m > 59) return null;
        Matcher p = PERIODO.matcher(t);
        if (p.find()) {
            String per = p.group(1);
            if ((per.equals("tarde") || per.equals("noite")) && h < 12) h += 12;
            if (per.equals("noite") && h == 24) h = 0;
            marcar(corte, p.start(), p.end());
        }
        marcar(corte, ini, fim);
        return new int[]{h, m};
    }

    private static int minutos(String g) {
        if (g == null) return 0;
        return g.equals("meia") ? 30 : Integer.parseInt(g);
    }

    static Calendar data(String t, boolean[] corte) {
        Calendar c = hoje();
        Matcher x;
        if ((x = D_REL.matcher(t)).find()) {
            String g = x.group(1);
            c.add(Calendar.DAY_OF_YEAR, g.startsWith("depois") ? 2 : g.equals("amanha") ? 1 : 0);
        } else if ((x = D_SEM.matcher(t)).find()) {
            int alvo = 0;
            for (int i = 0; i < SEMANA.length; i++) if (SEMANA[i].equals(x.group(1))) alvo = i + 1;
            int delta = (alvo - c.get(Calendar.DAY_OF_WEEK) + 7) % 7;
            boolean proxima = x.group().startsWith("proxim") || x.group(2) != null;
            if (delta == 0 && proxima) delta = 7;
            c.add(Calendar.DAY_OF_YEAR, delta);
        } else if ((x = D_DIA.matcher(t)).find()) {
            int dia = Integer.parseInt(x.group(1));
            if (dia < 1 || dia > 31) return null;
            if (x.group(2) != null) {
                int mes = 0;
                for (int i = 0; i < MESES.length; i++) if (MESES[i].equals(x.group(2))) mes = i;
                c.set(Calendar.MONTH, mes);
                c.set(Calendar.DAY_OF_MONTH, Math.min(dia, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
                if (c.before(hoje())) c.add(Calendar.YEAR, 1);
            } else {
                if (dia < c.get(Calendar.DAY_OF_MONTH)) c.add(Calendar.MONTH, 1);
                c.set(Calendar.DAY_OF_MONTH, Math.min(dia, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
            }
        } else if ((x = D_BARRA.matcher(t)).find()) {
            int dia = Integer.parseInt(x.group(1)), mes = Integer.parseInt(x.group(2));
            if (dia < 1 || dia > 31 || mes < 1 || mes > 12) return null;
            c.set(Calendar.MONTH, mes - 1);
            c.set(Calendar.DAY_OF_MONTH, Math.min(dia, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
            if (x.group(3) != null) {
                int ano = Integer.parseInt(x.group(3));
                c.set(Calendar.YEAR, ano < 100 ? 2000 + ano : ano);
            } else if (c.before(hoje())) {
                c.add(Calendar.YEAR, 1);
            }
        } else if ((x = D_SEMANA.matcher(t)).find()) {
            c.add(x.group(1).contains("mes") ? Calendar.MONTH : Calendar.DAY_OF_YEAR, x.group(1).contains("mes") ? 1 : 7);
        } else {
            return null;
        }
        marcar(corte, x.start(), x.end());
        return c;
    }

    static int duracao(String t, boolean[] corte) {
        Matcher x;
        int seg = 0;
        if ((x = DUR_HM.matcher(t)).find()) {
            seg = Integer.parseInt(x.group(1)) * 3600 + Integer.parseInt(x.group(2)) * 60;
        } else if ((x = DUR.matcher(t)).find()) {
            int n = Integer.parseInt(x.group(1));
            String u = x.group(2);
            seg = u.startsWith("h") ? n * 3600 : u.startsWith("s") ? n : n * 60;
        } else if ((x = DUR_TEXTO.matcher(t)).find()) {
            seg = x.group(1).equals("meia hora") ? 1800 : x.group(1).equals("uma hora") ? 3600 : 60;
        } else if (t.contains("pomodoro")) {
            return 25 * 60;
        } else {
            return 0;
        }
        marcar(corte, x.start(), x.end());
        return seg;
    }

    private static void cortar(Pattern p, String t, boolean[] corte) {
        Matcher m = p.matcher(t);
        while (m.find()) marcar(corte, m.start(), m.end());
    }

    private static void marcar(boolean[] corte, int ini, int fim) {
        for (int i = Math.max(0, ini); i < Math.min(corte.length, fim); i++) corte[i] = true;
    }

    private static String sobra(String texto, String t, boolean[] corte) {
        return sobra(texto, t, corte, true);
    }

    /** o texto original sem os pedacos usados, sem conectores soltos nas pontas */
    private static String sobra(String texto, String t, boolean[] corte, boolean limparConectores) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texto.length() && i < corte.length; i++) sb.append(corte[i] ? ' ' : texto.charAt(i));
        String s = sb.toString().replaceAll("\\s+", " ").trim();
        if (limparConectores) {
            for (int guard = 0; guard < 6; guard++) {
                String n = norm(s);
                Matcher a = CONECTOR_INI.matcher(n), b = CONECTOR_FIM.matcher(n);
                if (a.find() && a.end() > 0 && a.end() < s.length()) s = s.substring(a.end()).trim();
                else if (b.find() && b.start() > 0) s = s.substring(0, b.start()).trim();
                else break;
            }
            s = s.replaceAll("^[,.:;\\-]+|[,.:;\\-]+$", "").trim();
        }
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(new Locale("pt", "BR")) + s.substring(1);
    }

    // ------------------------------------------------------------------ contatos

    private static List<String[]> contatos;
    private static long contatosAt;

    /** {nome, numero} do contato que melhor bate com o nome digitado */
    static String[] contato(Context ctx, String nome) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null;
        carregarContatos(ctx);
        String q = norm(nome).trim();
        if (q.isEmpty()) return null;
        String[] palavras = q.split("\\s+");
        String[] melhor = null;
        int melhorPts = 0;
        for (String[] c : contatos) {
            String n = norm(c[0]);
            int pts = 0;
            if (n.equals(q)) pts = 30;
            else if (n.startsWith(q)) pts = 20;
            else {
                for (String w : n.split("\\s+")) if (w.equals(palavras[0])) pts = Math.max(pts, 15);
                if (pts == 0 && n.contains(q)) pts = 8;
            }
            if (pts > 0 && "1".equals(c[2])) pts += 3;
            if (pts > 0 && "1".equals(c[3])) pts += 2;
            if (pts > melhorPts) {
                melhorPts = pts;
                melhor = c;
            }
        }
        return melhor;
    }

    private static synchronized void carregarContatos(Context ctx) {
        if (contatos != null && System.currentTimeMillis() - contatosAt < 5 * 60_000) return;
        List<String[]> out = new ArrayList<>();
        String[] proj = {Phone.DISPLAY_NAME, Phone.NUMBER, Phone.STARRED, Phone.TYPE};
        try (Cursor c = ctx.getContentResolver().query(Phone.CONTENT_URI, proj, null, null, Phone.DISPLAY_NAME + " ASC")) {
            while (c != null && c.moveToNext()) {
                String nome = c.getString(0), num = c.getString(1);
                if (nome == null || num == null) continue;
                boolean celular = c.getInt(3) == Phone.TYPE_MOBILE;
                out.add(new String[]{nome, num, c.getInt(2) == 1 ? "1" : "0", celular ? "1" : "0"});
            }
        } catch (Exception ignored) {
        }
        contatos = out;
        contatosAt = System.currentTimeMillis();
    }

    /** so digitos, com 55 na frente se for numero brasileiro sem DDI */
    static String numeroWhats(String numero) {
        String d = numero.replaceAll("\\D", "");
        if (d.startsWith("0")) d = d.replaceFirst("^0+", "");
        if (d.length() == 10 || d.length() == 11) d = "55" + d;
        return d;
    }
}
