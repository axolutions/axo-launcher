package com.axolutions.launcher;

import java.util.Calendar;
import java.util.EnumMap;
import java.util.Map;

/** O que a frase digitada no "+" vai virar, e os dados tirados dela. */
final class Intencao {

    enum Tipo {
        CONTA("conta", "conta de matemática para resolver agora: 22 - 13, 18% de 3450, quanto é 15 x 4"),
        CONVERSAO("conversão", "converter unidade ou moeda: 10 km em milhas, 100 dólares em reais"),
        ALARME("alarme", "despertador para tocar num horário: me acorda às 7, alarme 6h30"),
        TIMER("timer", "cronômetro ou contagem regressiva para agora: timer 10 min, pomodoro"),
        EVENTO("evento", "compromisso marcado com dia ou hora: reunião, consulta, jantar, encontro, call"),
        LEMBRETE("lembrete", "algo que a pessoa precisa fazer ou quer ser lembrada de fazer"),
        NOTA("nota", "ideia, pensamento ou anotação solta para guardar"),
        MENSAGEM("mensagem", "mandar mensagem ou falar com alguém pelo WhatsApp"),
        LIGAR("ligar", "ligar ou telefonar para alguém"),
        IR("ir para", "ir até um lugar: chamar uber, rota, como chegar, me leva"),
        APP("abrir app", null),
        FOCO("foco do dia", "definir o foco ou a prioridade principal do dia"),
        PESQUISA("pesquisar", "pergunta geral ou algo para pesquisar na internet");

        final String rotulo;
        /** criterio que o Jev recebe; null = so o offline decide */
        final String criterio;

        Tipo(String rotulo, String criterio) {
            this.rotulo = rotulo;
            this.criterio = criterio;
        }
    }

    Tipo tipo;
    float conf;
    /** "offline" ou "jev" */
    String fonte = "offline";
    /** sinal deterministico (conta, cor, conversao...): o Jev nao e consultado */
    boolean forte;
    final Map<Tipo, Float> probs = new EnumMap<>(Tipo.class);

    // ---- dados extraidos
    int hora = -1, minuto;
    /** "daqui 30 min": quando ja e um instante absoluto */
    long relativo = -1;
    Calendar data;
    boolean dataEhPadrao, horaEhPadrao;
    int duracaoSeg;
    String titulo = "";
    String pessoa;
    String mensagem;
    String contatoNome, contatoNumero;
    String destino;
    String resultado, detalhe;
    HomeActivity.Entry app;

    Intencao(Tipo tipo, float conf) {
        this.tipo = tipo;
        this.conf = conf;
    }
}
