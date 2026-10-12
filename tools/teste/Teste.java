package com.axolutions.launcher;

import java.text.Normalizer;
import java.util.Calendar;

/** Roda frases pelo classificador e extratores na JVM do PC: tools/testar.sh */
public class Teste {
    public static void main(String[] a) {
        String[] frases = a.length > 0 ? a : new String[]{
                "76 x 2", "18% de 3450", "200 + 10%", "22 -", "me acorda às 8h", "me acorda 6:30 pra academia",
                "alarme 7 e meia", "me acorda 6", "timer 25 min", "10 minutos de pausa", "pomodoro",
                "reunião com Sanderson amanhã 15h", "jantar com Priya sexta 20h", "lembrar de pagar o boleto sexta",
                "me lembra de ligar pro contador daqui 30 min", "dentista dia 20 às 14h", "preciso ver o dentista semana que vem",
                "anotar ideia: app de bilheteria com LP", "nota: comprar cabo usb", "foco: fechar proposta GXA",
                "manda msg pro Felipe dizendo que atraso 10 min", "ligar pra mãe", "uber pra paulista", "como chegar no ibirapuera",
                "100 dólares em reais", "10 km em milhas", "30 graus em fahrenheit", "quem ganhou o jogo ontem?",
                "pesquisar receita de bolo", "daily às 13h", "call com cliente às 3 da tarde"};
        for (String f : frases) {
            String texto = Normalizer.normalize(f, Normalizer.Form.NFC);
            String t = Offline.norm(texto);
            Intencao it = Offline.classificar(t, null);
            if (it.tipo != Intencao.Tipo.MENSAGEM && it.tipo != Intencao.Tipo.LIGAR) Offline.preencher(it, texto, t, null);
            StringBuilder sb = new StringBuilder();
            if (it.resultado != null) sb.append(" res=").append(it.resultado);
            if (it.hora >= 0) sb.append(String.format(" %02d:%02d%s", it.hora, it.minuto, it.horaEhPadrao ? "(p)" : ""));
            if (it.data != null) sb.append(" data=").append(it.data.get(Calendar.DAY_OF_MONTH)).append("/")
                    .append(it.data.get(Calendar.MONTH) + 1).append(it.dataEhPadrao ? "(p)" : "");
            if (it.duracaoSeg > 0) sb.append(" dur=").append(it.duracaoSeg);
            if (!it.titulo.isEmpty()) sb.append(" titulo=[").append(it.titulo).append("]");
            if (it.pessoa != null) sb.append(" pessoa=").append(it.pessoa);
            if (it.destino != null) sb.append(" destino=[").append(it.destino).append("]");
            System.out.printf("%-48s %-10s %.2f%s%s%n", f, it.tipo.name(), it.conf, it.forte ? "!" : "", sb);
        }
    }
}
