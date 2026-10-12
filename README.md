# axo-launcher

Launcher Android minimalista, mantido pela
[Axolutions](https://www.axolutions.com.br). Tira a cor e os ícones do
celular: a tela inicial vira uma lista de texto, o aparelho fica em preto e
branco e os apps que distraem passam a custar alguns segundos de espera.

Nasceu no celular do fundador da Axolutions, um POCO X6 com HyperOS 2
(Android 15), e é onde ele é testado todo dia. Funciona como launcher comum em
qualquer Android 8+, mas algumas partes (gestos, WhatsApp duplicado) existem
para contornar comportamentos da Xiaomi.

## O que ele faz

**Três páginas**, deslizando para o lado:

1. **Foco** — relógio, data, clima, bateria, próximo compromisso da agenda, o
   "foco do dia" (toque para escrever a única coisa que importa hoje) e os apps
   que você usa de verdade, em lista de texto.
2. **Todos os apps** — em ordem alfabética, com busca.
3. **Distrações** — Instagram, X, YouTube, Netflix (ou o que você configurar),
   com o tempo de tela de hoje. Cada um só abre depois de 5 segundos de espera;
   tocar na tela desiste.

**Preto e branco com um botão de cor.** O sistema inteiro fica em tons de
cinza. Uma bolinha discreta na lateral mostra as cores enquanto você segura e
volta para o cinza quando solta (arrastar segurando muda ela de lugar).

**Contador flutuante.** Com um app de distração aberto, uma pílula no topo
mostra o tempo da sessão e o total do dia ("instagram 3:12 · hoje 47 min"). A
cada 10 minutos ela pulsa.

**O "+".** Um campo único que entende o que você digita e vira a ação certa,
na hora, enquanto você escreve. A ideia vem do
[Shapeshift](https://github.com/anishfn/shapeshift); aqui a ação é de verdade:

| você digita | vira |
|---|---|
| `76 x 2`, `18% de 3450`, `200 + 10%` | conta, com o resultado na hora |
| `me acorda às 7`, `alarme 6h30` | alarme no app Relógio |
| `timer 25 min`, `pomodoro` | timer rodando no Relógio |
| `reunião com Ana amanhã 15h` | evento no Google Agenda |
| `lembrar de pagar o boleto sexta` | lembrete com notificação |
| `anotar ideia: ...` | nota guardada no próprio launcher |
| `foco: fechar a proposta` | foco do dia na primeira página |
| `manda msg pro Felipe dizendo que...` | conversa aberta no WhatsApp com a mensagem pronta |
| `ligar pra mãe` | ligação |
| `uber pra paulista` | Uber ou Maps com o destino |
| `100 dólares em reais`, `10 km em milhas` | conversão (moeda com a cotação do dia) |
| nome de um app | abre o app |
| o resto | pesquisa no Google |

Quem decide o tipo é um classificador **offline**, por palavra-chave, que roda
a cada tecla. Quando ele fica em dúvida ("preciso ver o dentista semana que
vem"), o launcher pergunta ao **Jev** (`typesafe/jev-1.13`, via OpenRouter), um
modelo que só escolhe entre as opções oferecidas: ~0,6 s e US$ 0,000018 por
frase, com cache e teto de 300 chamadas por dia. Sem chave, tudo continua
funcionando só com o offline. Hora, data, valor e pessoa sempre saem de
extratores determinísticos; o modelo nunca inventa número.

**Gestos.** No HyperOS os gestos de navegação só funcionam com o launcher da
Xiaomi. O axo-launcher traz os próprios: deslizar da borda volta, de baixo para
cima vai para o início, de baixo para cima e segurar abre os recentes. Se o
serviço de gestos cair, os botões de navegação voltam sozinhos.

## Instalar

Não está na Play Store. A instalação é pelo `adb`, porque algumas permissões
(ligar o preto e branco, ler o tempo de uso) só podem ser concedidas por ele.

Pré-requisitos no computador: Android SDK (`platform-tools`,
`build-tools;34.0.0`, `platforms;android-34`), JDK 8 (para o `javac`) e JDK 17
(para o `d8` e o `apksigner`). Não usa Gradle.

```bash
cp secrets.properties.example secrets.properties   # opcional: chave do Jev e conta da agenda
./build.sh                                          # gera build/axo-launcher.apk
./instalar.sh                                       # instala, concede as permissões e vira o launcher padrão
```

No celular: Opções do desenvolvedor → **Depuração USB**, e na Xiaomi também
**Instalar via USB** e **Depuração USB (Configurações de segurança)**. Por
Wi-Fi: `adb pair` com o código da "Depuração por Wi-Fi" uma vez; depois o
`./conectar.sh` acha o aparelho pela rede, mesmo quando a porta muda.

Para testar o classificador do "+" no computador:

```bash
./build.sh && tools/testar.sh "me acorda às 7" "jantar com Priya sexta 20h"
```

## Configurar

Tudo fica em [`src/com/axolutions/launcher/Config.java`](src/com/axolutions/launcher/Config.java):
os apps da primeira página (por pacote, com o nome como reserva), os apps de
distração, os segundos de espera e a cidade do clima. Os papéis de parede são
gerados a partir de uma logo branca sobre fundo colorido:

```bash
python3 tools/wallpapers.py 1220 2712 tools/logo.png   # largura e altura da tela
```

## O que ele pede e por quê

| permissão | uso |
|---|---|
| `WRITE_SECURE_SETTINGS` (via adb) | liga e desliga o filtro de cinza do sistema; no HyperOS, esconde a barra de botões quando os gestos estão ativos |
| serviço de acessibilidade "Gestos do Axo" | desenha as faixas de gesto, a bolinha de cor e o contador; vê qual app está na frente para o contador e onde o teclado está para sair da frente dele. Não lê o conteúdo das telas |
| `GET_USAGE_STATS` (via adb) | tempo de tela de hoje nos apps de distração |
| agenda, contatos, telefone | criar eventos, achar o contato da mensagem, ligar; só quando você confirma no "+" |
| `INTERACT_ACROSS_USERS` (via adb) | abrir o WhatsApp duplicado da Xiaomi direto, sem o seletor "qual WhatsApp?" |

Nada sai do aparelho, exceto a frase enviada ao Jev (quando ligado), a
previsão do tempo ([Open-Meteo](https://open-meteo.com)) e a cotação
([AwesomeAPI](https://docs.awesomeapi.com.br)).

## Desfazer

Configurações → Apps → Launcher padrão → volte para o anterior, e
Configurações → Acessibilidade → desligue "Gestos do Axo" (os botões voltam na
hora). Para tirar o cinza: `adb shell settings put secure accessibility_display_daltonizer_enabled 0`.
O `instalar.sh` grava em `estado-original.txt` qual era o launcher antes.

## Licença

MIT. Veja [LICENSE](LICENSE).
