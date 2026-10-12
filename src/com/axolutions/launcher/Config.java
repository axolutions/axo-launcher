package com.axolutions.launcher;

/**
 * O que aparece em cada pagina. Cada item tenta os pacotes na ordem, depois
 * procura pelo nome do app; "clone" = WhatsApp duplicado (perfil paralelo).
 */
final class Config {

    static final class Item {
        final String label;
        final String[] pkgs;
        final String[] names;
        final boolean clone;
        final String url;
        final String special;
        final boolean gapBefore;

        Item(String label, String[] pkgs, String[] names, boolean clone, String url, String special, boolean gapBefore) {
            this.label = label;
            this.pkgs = pkgs;
            this.names = names;
            this.clone = clone;
            this.url = url;
            this.special = special;
            this.gapBefore = gapBefore;
        }
    }

    private static Item app(String label, String... pkgs) {
        return new Item(label, pkgs, new String[]{label}, false, null, null, false);
    }

    private static Item gap(Item i) {
        return new Item(i.label, i.pkgs, i.names, i.clone, i.url, i.special, true);
    }

    static final Item[] FOCUS = {
            app("whatsapp", "com.whatsapp"),
            new Item("whatsapp 2", new String[]{"com.whatsapp"}, new String[]{}, true, null, null, false),
            app("whatsapp business", "com.whatsapp.w4b"),

            gap(app("gmail", "com.google.android.gm")),
            new Item("workana", new String[]{}, new String[]{"workana"}, false, "https://www.workana.com/dashboard", null, false),
            app("github", "com.github.android"),
            app("claude", "com.anthropic.claude"),
            app("gemini", "com.google.android.apps.bard"),

            gap(app("nubank", "com.nu.production")),
            app("mercado pago", "com.mercadopago.wallet"),

            gap(app("uber", "com.ubercab")),
            app("cittamobi", "br.com.cittabus", "com.cittamobi"),

            gap(new Item("pesquisar no google", new String[]{}, new String[]{}, false, null, "search", false)),
            new Item("calculadora", new String[]{"com.google.android.calculator", "com.sec.android.app.popupcalculator",
                    "com.miui.calculator", "com.oneplus.calculator", "com.android.calculator2"},
                    new String[]{"calculadora", "calculator"}, false, null, null, false),
            new Item("relógio", new String[]{"com.google.android.deskclock", "com.sec.android.app.clockpackage",
                    "com.android.deskclock", "com.oneplus.deskclock"},
                    new String[]{"relógio", "clock"}, false, null, null, false),
    };

    static final Item[] DISTRACTIONS = {
            app("instagram", "com.instagram.android"),
            new Item("x", new String[]{"com.twitter.android"}, new String[]{"twitter"}, false, null, null, false),
            app("youtube", "com.google.android.youtube"),
            app("netflix", "com.netflix.mediaclient"),
    };

    /**
     * apps de banco: com eles na frente os gestos, a bolinha e o contador saem do ar
     * (ver ModoBanco). Pacote que nao estiver instalado nao atrapalha.
     */
    static final String[] BANCOS = {
            "com.nu.production", "com.mercadopago.wallet", "br.com.intermedium",
            "com.itau", "br.com.bradesco", "br.com.bb.android", "br.com.gabba.Caixa",
            "com.c6bank.app", "com.santander.app", "com.picpay", "br.com.uol.ps.myaccount",
    };

    /** segundos de espera antes de abrir um app de distracao */
    static final int DISTRACTION_DELAY = 5;

    /** conta Google onde o "+" cria eventos e lembretes (vem do secrets.properties; vazio = calendario principal) */
    static final String CALENDAR_ACCOUNT = Secrets.CALENDAR_ACCOUNT;

    /** Sao Paulo, pro clima */
    static final double LAT = -23.55, LON = -46.63;

    /** sobe quando os papeis de parede mudarem, pra reaplicar sozinho */
    static final int WALLPAPER_VERSION = 1;

    private Config() {
    }
}
