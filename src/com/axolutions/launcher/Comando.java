package com.axolutions.launcher;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.axolutions.launcher.Intencao.Tipo;

/**
 * O "+": um campo so, que vira o cartao do que a frase quer dizer (ideia do Shapeshift).
 * Aqui a acao e de verdade: alarme no Relogio, evento no Google Agenda, nota no proprio Axo.
 */
final class Comando extends FrameLayout {

    private static final Locale BR = new Locale("pt", "BR");
    private static final String[] DICAS = {
            "76 x 2", "me acorda às 7", "timer 25 min", "reunião com Sanderson amanhã 15h",
            "lembrar de pagar o boleto sexta", "anotar ideia: ", "manda msg pro ", "100 dólares em reais",
    };

    private final HomeActivity home;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private final LinearLayout coluna;
    private final EditText campo;
    private final LinearLayout cartao, corpo, rodape, vazio;
    private final TextView rotulo, fonte;

    // maquina "calma" do Shapeshift: o cartao nao pisca a cada tecla
    private Tipo atual, desafiante, desenhado;
    private int vitorias;
    private Intencao corrente;
    private Runnable jevTarefa;

    Comando(HomeActivity home) {
        super(home);
        this.home = home;
        this.prefs = home.getSharedPreferences("axo", Context.MODE_PRIVATE);
        setBackgroundColor(Color.argb(242, 0, 0, 0));
        setClickable(true);
        setVisibility(GONE);

        ScrollView sv = new ScrollView(home);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(OVER_SCROLL_NEVER);
        addView(sv, new LayoutParams(-1, -1));

        coluna = new LinearLayout(home);
        coluna.setOrientation(LinearLayout.VERTICAL);
        coluna.setPadding(dp(22), dp(40), dp(22), dp(24));
        sv.addView(coluna);

        LinearLayout topo = new LinearLayout(home);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        campo = new EditText(home);
        campo.setHint("o que você precisa?");
        campo.setHintTextColor(Color.argb(100, 255, 255, 255));
        campo.setTextColor(Color.WHITE);
        campo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        campo.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        campo.setBackground(null);
        campo.setPadding(dp(2), dp(8), 0, dp(8));
        campo.setSingleLine(true);
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        campo.setImeOptions(EditorInfo.IME_ACTION_GO);
        campo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { render(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        campo.setOnEditorActionListener((v, id, ev) -> {
            if (corrente != null) executar(corrente);
            return true;
        });
        topo.addView(campo, new LinearLayout.LayoutParams(0, -2, 1));
        TextView fechar = texto("×", 30, 170, "sans-serif-thin");
        fechar.setPadding(dp(14), 0, dp(4), dp(4));
        fechar.setOnClickListener(v -> fechar());
        topo.addView(fechar);
        coluna.addView(topo);

        View linha = new View(home);
        linha.setBackgroundColor(Color.argb(45, 255, 255, 255));
        coluna.addView(linha, new LinearLayout.LayoutParams(-1, 1));

        cartao = new LinearLayout(home);
        cartao.setOrientation(LinearLayout.VERTICAL);
        cartao.setPadding(dp(20), dp(16), dp(20), dp(18));
        GradientDrawable bgCartao = new GradientDrawable();
        bgCartao.setColor(Color.argb(255, 16, 16, 16));
        bgCartao.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        bgCartao.setCornerRadius(dp(22));
        cartao.setBackground(bgCartao);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(18);
        cartao.setVisibility(GONE);

        LinearLayout cab = new LinearLayout(home);
        cab.setGravity(Gravity.CENTER_VERTICAL);
        rotulo = texto("", 11, 150, "sans-serif-medium");
        rotulo.setLetterSpacing(0.22f);
        cab.addView(rotulo, new LinearLayout.LayoutParams(0, -2, 1));
        fonte = texto("", 10, 110, "sans-serif-medium");
        fonte.setLetterSpacing(0.15f);
        cab.addView(fonte);
        cartao.addView(cab);

        corpo = new LinearLayout(home);
        corpo.setOrientation(LinearLayout.VERTICAL);
        corpo.setPadding(0, dp(12), 0, 0);
        cartao.addView(corpo);

        rodape = new LinearLayout(home);
        rodape.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        rodape.setPadding(0, dp(18), 0, 0);
        cartao.addView(rodape);
        coluna.addView(cartao, clp);

        vazio = new LinearLayout(home);
        vazio.setOrientation(LinearLayout.VERTICAL);
        vazio.setPadding(0, dp(22), 0, 0);
        coluna.addView(vazio);
    }

    // ------------------------------------------------------------------ abrir/fechar

    boolean aberto() {
        return getVisibility() == VISIBLE;
    }

    void abrir() {
        setAlpha(0f);
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(160).start();
        campo.setText("");
        render();
        campo.requestFocus();
        ui.postDelayed(() -> teclado().showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT), 120);
        if (Conversao.cotacaoVelha(prefs)) bg.execute(() -> Conversao.atualizarCotacoes(prefs));
    }

    void fechar() {
        teclado().hideSoftInputFromWindow(campo.getWindowToken(), 0);
        ui.removeCallbacks(jevTarefa);
        setVisibility(GONE);
        campo.setText("");
    }

    void setInsets(int top, int bottom) {
        coluna.setPadding(dp(22), top + dp(18), dp(22), bottom + dp(24));
    }

    private InputMethodManager teclado() {
        return getContext().getSystemService(InputMethodManager.class);
    }

    // ------------------------------------------------------------------ entender

    private String chaveAtual() {
        return Offline.norm(Normalizer.normalize(campo.getText().toString(), Normalizer.Form.NFC)).trim().replaceAll("\\s+", " ");
    }

    private void render() {
        String texto = Normalizer.normalize(campo.getText().toString(), Normalizer.Form.NFC);
        String t = Offline.norm(texto);
        String chave = t.trim().replaceAll("\\s+", " ");
        if (chave.isEmpty()) {
            atual = desafiante = desenhado = null;
            corrente = null;
            TransitionManager.beginDelayedTransition(coluna, new AutoTransition().setDuration(150));
            cartao.setVisibility(GONE);
            vazio.setVisibility(VISIBLE);
            desenharVazio();
            return;
        }
        vazio.setVisibility(GONE);

        Intencao off = Offline.classificar(t, home.todosApps());
        Tipo cand = off.tipo;
        float conf = off.conf;
        boolean doJev = false;
        Jev.Resposta j = Jev.doCache(chave);
        if (j != null && j.tipo != null && !off.forte && j.conf >= 0.4f && j.conf >= off.conf) {
            cand = j.tipo;
            conf = j.conf;
            doJev = true;
        }
        boolean atualSemApoio = atual != null && !off.probs.containsKey(atual) && !(j != null && j.tipo == atual);
        decidir(cand, conf, doJev, atualSemApoio);
        if (atual == Tipo.APP && off.app == null) atual = Tipo.PESQUISA;

        Intencao it = new Intencao(atual, conf);
        it.fonte = (atual == cand && doJev) ? "jev" : "offline";
        it.app = off.app;
        Offline.preencher(it, texto, t, getContext());
        corrente = it;
        desenhar(it, t);
        agendarJev(chave, texto, off);
    }

    private void decidir(Tipo cand, float conf, boolean doJev, boolean atualSemApoio) {
        if (atual == null || cand == atual) {
            atual = cand;
            desafiante = null;
            return;
        }
        // o fallback "pesquisar" cede na hora; o Jev, que e a opiniao mais esperta, tambem
        if (conf >= 0.85f || (atual == Tipo.PESQUISA && conf >= 0.4f) || (doJev && conf >= 0.4f)) {
            atual = cand;
            desafiante = null;
            return;
        }
        vitorias = desafiante == cand ? vitorias + 1 : 1;
        desafiante = cand;
        if (vitorias >= 2 && (conf >= 0.4f || atualSemApoio)) {
            atual = cand;
            desafiante = null;
        }
    }

    private void agendarJev(String chave, String texto, Intencao off) {
        ui.removeCallbacks(jevTarefa);
        if (!Jev.ligado() || off.forte || off.conf >= 0.6f) return;
        if (chave.split(" ").length < 2 || Jev.doCache(chave) != null) return;
        if (Jev.chamadasHoje(prefs) >= Jev.TETO_DIA) return;
        jevTarefa = () -> bg.execute(() -> {
            Jev.Resposta r = Jev.classificar(prefs, chave, texto);
            if (r != null) ui.post(() -> {
                if (aberto() && chaveAtual().equals(chave)) render();
            });
        });
        ui.postDelayed(jevTarefa, 450);
    }

    // ------------------------------------------------------------------ desenhar

    private void desenhar(Intencao it, String t) {
        TransitionManager.beginDelayedTransition(coluna, new AutoTransition().setDuration(170));
        cartao.setVisibility(VISIBLE);
        boolean mudou = it.tipo != desenhado;
        rotulo.setText(it.tipo.rotulo.toUpperCase(BR));
        fonte.setText("jev".equals(it.fonte) ? "JEV" : "");
        corpo.removeAllViews();
        rodape.removeAllViews();

        switch (it.tipo) {
            case CONTA:
                corpo.addView(grande(it.resultado != null ? it.resultado : "…", 50));
                corpo.addView(detalhe(it.detalhe));
                acao("copiar", it.resultado != null);
                break;
            case CONVERSAO: {
                Conversao.Pedido p = Conversao.ler(t.trim());
                if (p == null) {
                    corpo.addView(grande("…", 40));
                    corpo.addView(detalhe("ex.: 100 dólares em reais, 10 km em milhas"));
                    acao("copiar", false);
                    break;
                }
                String[] r = Conversao.calcular(p, prefs);
                if (r[0] == null) {
                    bg.execute(() -> {
                        if (Conversao.atualizarCotacoes(prefs)) ui.post(this::render);
                    });
                }
                it.resultado = r[0];
                it.detalhe = r[1];
                corpo.addView(grande(r[0] != null ? r[0] : "…", 40));
                corpo.addView(detalhe(r[1]));
                acao("copiar", r[0] != null);
                break;
            }
            case ALARME: {
                Calendar alvo = proximoAlarme(it);
                corpo.addView(grande(alvo != null ? hhmm(alvo) : "--:--", 56));
                LinearLayout cs = chips();
                if (alvo != null) cs.addView(chip(diaRelativo(alvo), false));
                if (!it.titulo.isEmpty()) cs.addView(chip(it.titulo, false));
                if (alvo != null && it.data != null && it.relativo < 0 && !mesmoDia(alvo, it.data)) {
                    cs.addView(chip("o relógio só cria o próximo", true));
                }
                if (alvo == null) cs.addView(chip("+ horário", true));
                corpo.addView(cs);
                acao("criar alarme", alvo != null);
                break;
            }
            case TIMER:
                corpo.addView(grande(it.duracaoSeg > 0 ? duracao(it.duracaoSeg) : "--:--", 56));
                if (!it.titulo.isEmpty()) {
                    LinearLayout cs = chips();
                    cs.addView(chip(it.titulo, false));
                    corpo.addView(cs);
                }
                acao("iniciar timer", it.duracaoSeg > 0);
                break;
            case EVENTO:
            case LEMBRETE: {
                corpo.addView(titulo(it.titulo.isEmpty() ? "…" : it.titulo));
                LinearLayout cs = chips();
                if (it.data != null) cs.addView(chip(diaRelativo(it.data), it.dataEhPadrao));
                if (it.hora >= 0) cs.addView(chip(String.format(BR, "%02d:%02d", it.hora, it.minuto), it.horaEhPadrao));
                else cs.addView(chip(it.tipo == Tipo.EVENTO ? "dia inteiro" : "+ horário", true));
                if (it.pessoa != null) cs.addView(chip(it.pessoa, false));
                corpo.addView(cs);
                acao(it.tipo == Tipo.EVENTO ? "criar evento" : "criar lembrete", !it.titulo.isEmpty());
                break;
            }
            case NOTA:
                corpo.addView(titulo(it.titulo.isEmpty() ? "…" : it.titulo));
                corpo.addView(detalhe("fica nas notas do axo"));
                acao("salvar nota", !it.titulo.isEmpty());
                break;
            case FOCO:
                corpo.addView(titulo(it.titulo.isEmpty() ? "…" : it.titulo));
                corpo.addView(detalhe("aparece embaixo do relógio, só hoje"));
                acao("definir foco", !it.titulo.isEmpty());
                break;
            case MENSAGEM:
            case LIGAR: {
                boolean achou = it.contatoNome != null;
                corpo.addView(titulo(achou ? it.contatoNome : (it.pessoa == null || it.pessoa.isEmpty() ? "quem?" : it.pessoa)));
                corpo.addView(detalhe(achou ? it.contatoNumero : (it.pessoa == null || it.pessoa.isEmpty() ? "" : "não achei nos contatos")));
                if (it.mensagem != null && !it.mensagem.isEmpty()) {
                    LinearLayout cs = chips();
                    cs.addView(chip("“" + it.mensagem + "”", false));
                    corpo.addView(cs);
                }
                if (it.tipo == Tipo.MENSAGEM) acao(achou ? "abrir conversa" : "abrir whatsapp", true);
                else acao("ligar", achou);
                break;
            }
            case IR:
                corpo.addView(titulo(it.destino == null || it.destino.isEmpty() ? "pra onde?" : it.destino));
                boolean temDestino = it.destino != null && !it.destino.isEmpty();
                if (temDestino) secundario("uber", () -> irUber(it.destino));
                acao("maps", temDestino);
                break;
            case APP:
                corpo.addView(grande(it.app != null ? it.app.label : "…", 30));
                acao("abrir", it.app != null);
                break;
            case PESQUISA:
                corpo.addView(titulo(it.titulo.isEmpty() ? campo.getText().toString().trim() : it.titulo));
                corpo.addView(detalhe("no google"));
                acao("pesquisar", true);
                break;
        }

        if (mudou) {
            corpo.setAlpha(0f);
            corpo.animate().alpha(1f).setDuration(200).start();
            desenhado = it.tipo;
        }
    }

    private void acao(String rotuloBotao, boolean ativo) {
        TextView b = texto(rotuloBotao + "  ↵", 14, 255, "sans-serif-medium");
        b.setTextColor(ativo ? Color.BLACK : Color.argb(120, 0, 0, 0));
        b.setShadowLayer(0, 0, 0, 0);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(ativo ? Color.WHITE : Color.argb(90, 255, 255, 255));
        g.setCornerRadius(dp(40));
        b.setBackground(g);
        b.setEnabled(ativo);
        b.setOnClickListener(v -> {
            if (corrente != null) executar(corrente);
        });
        rodape.addView(b);
    }

    private void secundario(String rotuloBotao, Runnable r) {
        TextView b = texto(rotuloBotao, 14, 230, "sans-serif-medium");
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setStroke(dp(1), Color.argb(110, 255, 255, 255));
        g.setCornerRadius(dp(40));
        b.setBackground(g);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(10);
        rodape.addView(b, lp);
    }

    // ------------------------------------------------------------------ executar

    private void executar(Intencao it) {
        if (it == null) return;
        try {
            switch (it.tipo) {
                case CONTA:
                    if (it.resultado == null) return;
                    copiar(it.resultado);
                    historico(it.detalhe + " = " + it.resultado);
                    limpar("copiado");
                    break;
                case CONVERSAO:
                    if (it.resultado == null) return;
                    copiar(it.resultado);
                    historico(it.detalhe.split(" · ")[0] + " = " + it.resultado);
                    limpar("copiado");
                    break;
                case ALARME: {
                    Calendar alvo = proximoAlarme(it);
                    if (alvo == null) return;
                    Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, alvo.get(Calendar.HOUR_OF_DAY))
                            .putExtra(AlarmClock.EXTRA_MINUTES, alvo.get(Calendar.MINUTE))
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                    if (!it.titulo.isEmpty()) i.putExtra(AlarmClock.EXTRA_MESSAGE, it.titulo);
                    iniciar(i);
                    historico("alarme · " + hhmm(alvo) + " " + diaRelativo(alvo));
                    limpar("alarme criado · " + hhmm(alvo));
                    break;
                }
                case TIMER: {
                    if (it.duracaoSeg <= 0) return;
                    Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, it.duracaoSeg)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                    if (!it.titulo.isEmpty()) i.putExtra(AlarmClock.EXTRA_MESSAGE, it.titulo);
                    iniciar(i);
                    historico("timer · " + duracao(it.duracaoSeg) + (it.titulo.isEmpty() ? "" : " · " + it.titulo));
                    limpar("timer rodando · " + duracao(it.duracaoSeg));
                    break;
                }
                case EVENTO:
                case LEMBRETE: {
                    if (it.titulo.isEmpty()) return;
                    String quando = diaRelativo(it.data) + (it.hora >= 0 ? String.format(BR, " %02d:%02d", it.hora, it.minuto) : "");
                    if (criarNaAgenda(it)) {
                        historico(it.tipo.rotulo + " · " + it.titulo + " · " + quando);
                        limpar(it.tipo.rotulo + " na agenda · " + quando);
                    }
                    break;
                }
                case NOTA:
                    if (it.titulo.isEmpty()) return;
                    salvarNota(it.titulo);
                    limpar("nota salva");
                    break;
                case FOCO:
                    if (it.titulo.isEmpty()) return;
                    home.definirFoco(it.titulo);
                    historico("foco · " + it.titulo);
                    fechar();
                    break;
                case MENSAGEM: {
                    if (it.contatoNumero != null) {
                        String url = "https://wa.me/" + Offline.numeroWhats(it.contatoNumero)
                                + (it.mensagem != null && !it.mensagem.isEmpty() ? "?text=" + URLEncoder.encode(it.mensagem, "UTF-8") : "");
                        iniciar(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        historico("mensagem · " + it.contatoNome);
                    } else {
                        Intent w = getContext().getPackageManager().getLaunchIntentForPackage("com.whatsapp");
                        if (w != null) iniciar(w);
                    }
                    fechar();
                    break;
                }
                case LIGAR: {
                    if (it.contatoNumero == null) return;
                    boolean pode = getContext().checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
                    iniciar(new Intent(pode ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(it.contatoNumero))));
                    historico("ligação · " + it.contatoNome);
                    fechar();
                    break;
                }
                case IR:
                    if (it.destino == null || it.destino.isEmpty()) return;
                    iniciar(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(it.destino))));
                    fechar();
                    break;
                case APP:
                    if (it.app == null) return;
                    fechar();
                    home.abrirEntry(it.app);
                    break;
                case PESQUISA: {
                    String q = it.titulo.isEmpty() ? campo.getText().toString().trim() : it.titulo;
                    if (q.isEmpty()) return;
                    iniciar(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))));
                    fechar();
                    break;
                }
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "não deu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void irUber(String destino) {
        Intent uber = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "uber://?action=setPickup&pickup=my_location&dropoff[formatted_address]=" + Uri.encode(destino)));
        try {
            iniciar(uber);
        } catch (Exception e) {
            Intent app = getContext().getPackageManager().getLaunchIntentForPackage("com.ubercab");
            if (app != null) iniciar(app);
        }
        fechar();
    }

    private void iniciar(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
    }

    private void limpar(String aviso) {
        Toast.makeText(getContext(), aviso, Toast.LENGTH_SHORT).show();
        campo.setText("");
    }

    private void copiar(String s) {
        ClipboardManager cm = getContext().getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("axo", s));
    }

    /** proxima vez que o horario acontece (a intent do Relogio so sabe hora e minuto) */
    private static Calendar proximoAlarme(Intencao it) {
        if (it.relativo > 0) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(it.relativo);
            return c;
        }
        if (it.hora < 0) return null;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, it.hora);
        c.set(Calendar.MINUTE, it.minuto);
        c.set(Calendar.SECOND, 0);
        if (!c.after(Calendar.getInstance())) c.add(Calendar.DAY_OF_YEAR, 1);
        return c;
    }

    /** grava direto no calendario principal do Google; sem permissao, abre a tela do Agenda */
    private boolean criarNaAgenda(Intencao it) {
        boolean diaInteiro = it.hora < 0;
        Calendar ini = (Calendar) it.data.clone();
        if (!diaInteiro) {
            ini.set(Calendar.HOUR_OF_DAY, it.hora);
            ini.set(Calendar.MINUTE, it.minuto);
            ini.set(Calendar.SECOND, 0);
            ini.set(Calendar.MILLISECOND, 0);
        }
        long dur = it.tipo == Tipo.LEMBRETE ? 15 * 60_000L : 60 * 60_000L;
        ContentResolver cr = getContext().getContentResolver();
        long cal = getContext().checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                ? calendarioPrincipal(cr) : -1;
        if (cal < 0) {
            Intent i = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, it.titulo)
                    .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, diaInteiro)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ini.getTimeInMillis())
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ini.getTimeInMillis() + dur);
            iniciar(i);
            fechar();
            return false;
        }
        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.CALENDAR_ID, cal);
        v.put(CalendarContract.Events.TITLE, it.titulo);
        if (diaInteiro) {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.clear();
            utc.set(ini.get(Calendar.YEAR), ini.get(Calendar.MONTH), ini.get(Calendar.DAY_OF_MONTH));
            v.put(CalendarContract.Events.DTSTART, utc.getTimeInMillis());
            v.put(CalendarContract.Events.DTEND, utc.getTimeInMillis() + 86_400_000L);
            v.put(CalendarContract.Events.ALL_DAY, 1);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
        } else {
            v.put(CalendarContract.Events.DTSTART, ini.getTimeInMillis());
            v.put(CalendarContract.Events.DTEND, ini.getTimeInMillis() + dur);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        }
        Uri u = cr.insert(CalendarContract.Events.CONTENT_URI, v);
        if (u == null) throw new IllegalStateException("agenda recusou");
        if (!diaInteiro) {
            ContentValues r = new ContentValues();
            r.put(CalendarContract.Reminders.EVENT_ID, ContentUris.parseId(u));
            r.put(CalendarContract.Reminders.MINUTES, it.tipo == Tipo.LEMBRETE ? 0 : 30);
            r.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
            cr.insert(CalendarContract.Reminders.CONTENT_URI, r);
        }
        return true;
    }

    /** o calendario principal da conta do Config (o "dono" e a propria conta), senao o melhor do Google */
    private static long calendarioPrincipal(ContentResolver cr) {
        String[] proj = {CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE, CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.OWNER_ACCOUNT};
        long melhor = -1;
        int melhorPts = -1;
        try (Cursor c = cr.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (c.getInt(3) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue;
                int pts = 0;
                if (Config.CALENDAR_ACCOUNT.equalsIgnoreCase(c.getString(5))) pts += 20;
                if (Config.CALENDAR_ACCOUNT.equalsIgnoreCase(c.getString(6))) pts += 10;
                if ("com.google".equals(c.getString(1))) pts += 2;
                if (c.getInt(2) == 1) pts += 4;
                if (c.getInt(4) == 1) pts += 1;
                if (pts > melhorPts) {
                    melhorPts = pts;
                    melhor = c.getLong(0);
                }
            }
        } catch (Exception ignored) {
        }
        return melhor;
    }

    // ------------------------------------------------------------------ historico e notas

    private String hoje() {
        return new SimpleDateFormat("yyyyMMdd", BR).format(new Date());
    }

    private JSONArray lista(String chave) {
        try {
            return new JSONArray(prefs.getString(chave, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void historico(String linha) {
        try {
            JSONArray a = lista("hist");
            JSONArray out = new JSONArray();
            out.put(new JSONObject().put("d", hoje()).put("t", linha));
            for (int i = 0; i < a.length() && out.length() < 40; i++) {
                if (hoje().equals(a.getJSONObject(i).optString("d"))) out.put(a.get(i));
            }
            prefs.edit().putString("hist", out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void salvarNota(String t) {
        try {
            JSONArray a = lista("notas");
            JSONArray out = new JSONArray();
            out.put(new JSONObject().put("t", t).put("at", System.currentTimeMillis()));
            for (int i = 0; i < a.length(); i++) out.put(a.get(i));
            prefs.edit().putString("notas", out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void remover(String chave, int idx) {
        JSONArray a = lista(chave), out = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (i != idx) out.put(a.opt(i));
        prefs.edit().putString(chave, out.toString()).apply();
        TransitionManager.beginDelayedTransition(coluna, new AutoTransition().setDuration(150));
        desenharVazio();
    }

    private void desenharVazio() {
        vazio.removeAllViews();
        JSONArray hist = lista("hist"), notas = lista("notas");
        boolean temHist = false;
        for (int i = 0; i < hist.length(); i++) {
            JSONObject o = hist.optJSONObject(i);
            if (o == null || !hoje().equals(o.optString("d"))) continue;
            if (!temHist) {
                vazio.addView(secao("HOJE"));
                temHist = true;
            }
            final int idx = i;
            vazio.addView(linhaLista(o.optString("t"), null, () -> remover("hist", idx), null));
        }
        if (notas.length() > 0) {
            vazio.addView(secao("NOTAS"));
            for (int i = 0; i < notas.length(); i++) {
                JSONObject o = notas.optJSONObject(i);
                if (o == null) continue;
                final int idx = i;
                final String txt = o.optString("t");
                String quando = new SimpleDateFormat("d MMM", BR).format(new Date(o.optLong("at")));
                vazio.addView(linhaLista(txt, quando, () -> remover("notas", idx), () -> {
                    copiar(txt);
                    Toast.makeText(getContext(), "nota copiada", Toast.LENGTH_SHORT).show();
                }));
            }
        }
        if (!temHist && notas.length() == 0) {
            vazio.addView(secao("EXPERIMENTE"));
            for (String d : DICAS) {
                TextView t = texto(d, 17, 170, "sans-serif-light");
                t.setPadding(dp(2), dp(9), 0, dp(9));
                t.setOnClickListener(v -> {
                    campo.setText(d);
                    campo.setSelection(campo.getText().length());
                });
                vazio.addView(t);
            }
        }
        int n = Jev.chamadasHoje(prefs);
        if (n > 0) {
            TextView rod = texto(String.format(BR, "jev hoje: %d de %d · US$ %.4f no total", n, Jev.TETO_DIA,
                    prefs.getFloat("jevCusto", 0f)), 11, 90, "sans-serif");
            rod.setPadding(dp(2), dp(30), 0, 0);
            vazio.addView(rod);
        }
    }

    private TextView secao(String s) {
        TextView t = texto(s, 11, 120, "sans-serif-medium");
        t.setLetterSpacing(0.22f);
        t.setPadding(dp(2), dp(18), 0, dp(6));
        return t;
    }

    private View linhaLista(String txt, String sub, Runnable tirar, Runnable tocar) {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = texto(txt, 16, 225, "sans-serif-light");
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        if (sub != null) {
            TextView s = texto(sub, 11, 100, "sans-serif");
            s.setPadding(0, dp(3), 0, 0);
            col.addView(s);
        }
        col.setPadding(dp(2), dp(9), 0, dp(9));
        if (tocar != null) col.setOnClickListener(v -> tocar.run());
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        TextView x = texto("×", 20, 110, "sans-serif-light");
        x.setPadding(dp(16), dp(6), dp(4), dp(6));
        x.setOnClickListener(v -> tirar.run());
        r.addView(x);
        return r;
    }

    // ------------------------------------------------------------------ pecas visuais

    private TextView texto(String s, float sp, int alpha, String familia) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.argb(alpha, 255, 255, 255));
        t.setTypeface(Typeface.create(familia, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView grande(String s, float sp) {
        TextView t = texto(s, sp, 255, "sans-serif-thin");
        t.setPadding(0, dp(2), 0, dp(4));
        return t;
    }

    private TextView titulo(String s) {
        TextView t = texto(s, 22, 245, "sans-serif-light");
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(2), 0, dp(4));
        return t;
    }

    private TextView detalhe(String s) {
        TextView t = texto(s == null ? "" : s, 13, 130, "sans-serif");
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private LinearLayout chips() {
        LinearLayout l = new LinearLayout(getContext());
        l.setPadding(0, dp(12), 0, 0);
        return l;
    }

    private TextView chip(String s, boolean padrao) {
        TextView t = texto(s, 13, padrao ? 120 : 220, "sans-serif");
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setStroke(dp(1), Color.argb(padrao ? 50 : 90, 255, 255, 255));
        g.setCornerRadius(dp(30));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private static String hhmm(Calendar c) {
        return String.format(BR, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private static String duracao(int seg) {
        int h = seg / 3600, m = (seg % 3600) / 60, s = seg % 60;
        return h > 0 ? String.format(BR, "%d:%02d:%02d", h, m, s) : String.format(BR, "%02d:%02d", m, s);
    }

    private static boolean mesmoDia(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static String diaRelativo(Calendar c) {
        Calendar hoje = Offline.hoje();
        Calendar d = (Calendar) c.clone();
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        long dias = Math.round((d.getTimeInMillis() - hoje.getTimeInMillis()) / 86_400_000.0);
        String sem = new SimpleDateFormat("EEE", BR).format(c.getTime()).replace(".", "");
        if (dias == 0) return "hoje · " + sem;
        if (dias == 1) return "amanhã · " + sem;
        if (dias > 1 && dias < 7) return sem + " · " + new SimpleDateFormat("d/MM", BR).format(c.getTime());
        return new SimpleDateFormat("d 'de' MMM", BR).format(c.getTime());
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
