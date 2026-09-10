import java.awt.*;

public class AppTheme {

    // --- Tema ativo ---
    public static boolean dark = false;

    // --- Cores dinâmicas (não final — mudam com o tema) ---
    public static Color BG          = new Color(0xF4F6FB);
    public static Color SURFACE     = new Color(0xFFFFFF);
    public static Color SURFACE2    = new Color(0xECEFF7);
    public static Color ACCENT      = new Color(0x4F6AFF);
    public static Color ACCENT_DARK = new Color(0x3450E0);
    public static Color TEXT_PRI    = new Color(0x1A1C2E);
    public static Color TEXT_SEC    = new Color(0x5A5F7A);
    public static Color TEXT_MUT    = new Color(0x9096AE);   // bem apagado: dicas, timestamps
    public static Color DIVIDER     = new Color(0xD0D6E8);
    public static Color DANGER      = new Color(0xE53935);
    public static Color SUCCESS     = new Color(0x2E7D32);
    public static Color WARNING     = new Color(0xFF8F00);
    public static Color CARD_BORDER = new Color(0xE0E4F0);

    // --- Fontes (sempre iguais) ---
    public static final Font FONT_TITLE   = new Font("Segoe UI", Font.BOLD,  20);
    public static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD,  14);
    public static final Font FONT_LABEL   = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD    = new Font("Segoe UI", Font.BOLD,  13);
    public static final Font FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_CAPTION = new Font("Segoe UI", Font.PLAIN, 10);
    public static final Font FONT_TIMER   = new Font("Segoe UI", Font.BOLD,  42);
    public static final Font FONT_TIMER_S = new Font("Segoe UI", Font.BOLD,  32);
    public static final Font FONT_MONO    = new Font("Consolas",  Font.BOLD, 38);
    public static final Font FONT_MONO_S  = new Font("Consolas",  Font.BOLD, 28);

    /** Paleta fixa das matérias */
    public static final Color[] PALETTE = {
        new Color(0xEF5350), new Color(0x42A5F5), new Color(0x66BB6A),
        new Color(0xFFA726), new Color(0xAB47BC), new Color(0x26A69A),
        new Color(0xFFCA28), new Color(0x78909C), new Color(0xEC407A),
        new Color(0x29B6F6), new Color(0x9CCC65), new Color(0xFF7043)
    };

    public static Color nextColor(int index) {
        return PALETTE[index % PALETTE.length];
    }

    /** Escurece (f&lt;1) ou clareia em direção ao branco (f&gt;1) mantendo o matiz. */
    public static Color shade(Color c, double f) {
        if (f <= 1.0) {
            f = Math.max(0.0, f);
            return new Color((int) Math.round(c.getRed()   * f),
                             (int) Math.round(c.getGreen() * f),
                             (int) Math.round(c.getBlue()  * f), c.getAlpha());
        }
        double t = Math.min(1.0, f - 1.0);
        return new Color((int) Math.round(c.getRed()   + (255 - c.getRed())   * t),
                         (int) Math.round(c.getGreen() + (255 - c.getGreen()) * t),
                         (int) Math.round(c.getBlue()  + (255 - c.getBlue())  * t), c.getAlpha());
    }

    /** Interpola linearmente entre a e b (t=0 → a, t=1 → b). */
    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return new Color((int) Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t),
                         (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                         (int) Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    /** Aplica o tema claro */
    public static void applyLight() {
        dark        = false;
        BG          = new Color(0xF4F6FB);
        SURFACE     = new Color(0xFFFFFF);
        SURFACE2    = new Color(0xECEFF7);
        ACCENT      = new Color(0x4F6AFF);
        ACCENT_DARK = new Color(0x3450E0);
        TEXT_PRI    = new Color(0x1A1C2E);
        TEXT_SEC    = new Color(0x4C5168);
        TEXT_MUT    = new Color(0x8A8FA6);
        DIVIDER     = new Color(0xD0D6E8);
        DANGER      = new Color(0xE53935);
        SUCCESS     = new Color(0x2E7D32);
        WARNING     = new Color(0xFF8F00);
        CARD_BORDER = new Color(0xE0E4F0);
        applyLafPalette();
    }

    /** Aplica o tema escuro */
    public static void applyDark() {
        dark        = true;
        BG          = new Color(0x0F1120);
        SURFACE     = new Color(0x1A1D2E);
        SURFACE2    = new Color(0x252840);
        ACCENT      = new Color(0x8B9DFF);
        ACCENT_DARK = new Color(0x5C6FDD);
        TEXT_PRI    = new Color(0xEEF0FF);
        TEXT_SEC    = new Color(0xAEB4D6);   // era 0x9298BB — pouco legível em texto pequeno
        TEXT_MUT    = new Color(0x7C82A6);
        DIVIDER     = new Color(0x323860);
        DANGER      = new Color(0xEF5350);
        SUCCESS     = new Color(0x66BB6A);
        WARNING     = new Color(0xFFA726);
        CARD_BORDER = new Color(0x2E3250);
        applyLafPalette();
    }

    /**
     * Instala o Nimbus (respeita cores do UIManager — dá pra deixar diálogos,
     * combos, spinners e scrollbars no tema). Cai no L&F do sistema se falhar.
     */
    public static void installLookAndFeel() {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo i : javax.swing.UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(i.getName())) { javax.swing.UIManager.setLookAndFeel(i.getClassName()); break; }
        } catch (Exception e) {
            try { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
        }
        applyLafPalette();
    }

    /** Ajusta a paleta do L&F ao tema atual. Reinstala o Nimbus p/ ele recalcular as cores. */
    public static void applyLafPalette() {
        Object[] p = {
            "control",                   dark ? new Color(0x1E2236) : new Color(0xF0F2F8),
            "info",                      dark ? new Color(0x252A44) : new Color(0xFFFFF0),
            "nimbusBase",                dark ? new Color(0x2C3252) : new Color(0xC7CDDE),
            "nimbusBlueGrey",            dark ? new Color(0x2C3252) : new Color(0xC7CDDE),
            "nimbusLightBackground",     dark ? new Color(0x232741) : new Color(0xFFFFFF),
            "text",                      dark ? new Color(0xEEF0FF) : new Color(0x1A1C2E),
            "controlText",               dark ? new Color(0xEEF0FF) : new Color(0x1A1C2E),
            "menuText",                  dark ? new Color(0xEEF0FF) : new Color(0x1A1C2E),
            "infoText",                  dark ? new Color(0xEEF0FF) : new Color(0x1A1C2E),
            "nimbusFocus",               ACCENT,
            "nimbusSelectionBackground", ACCENT,
            "nimbusSelection",           ACCENT,
            "nimbusDisabledText",        dark ? new Color(0x7C82A6) : new Color(0x9096AE),
            "Panel.background",          dark ? new Color(0x1E2236) : new Color(0xF0F2F8),
            "OptionPane.background",     dark ? new Color(0x1E2236) : new Color(0xF0F2F8),
            "OptionPane.messageFont",    FONT_LABEL,
            "defaultFont",               FONT_LABEL,
        };
        for (int i = 0; i < p.length; i += 2) javax.swing.UIManager.put(p[i], p[i + 1]);
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getLookAndFeel().getClass().getName());
        } catch (Exception ignored) {}
    }

    /** Ícone do app: quadrado arredondado com um "alvo" (foco). */
    public static java.awt.image.BufferedImage appIcon(int size) {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int r = Math.round(size * 0.24f);
        g.setPaint(new GradientPaint(0, 0, new Color(0x5B72FF), size, size, new Color(0x3450E0)));
        g.fillRoundRect(0, 0, size, size, r, r);
        // alvo
        g.setColor(new Color(255, 255, 255, 235));
        float sw = Math.max(1.5f, size * 0.09f);
        g.setStroke(new BasicStroke(sw));
        int pad = Math.round(size * 0.28f);
        g.drawOval(pad, pad, size - 2 * pad, size - 2 * pad);
        int dot = Math.round(size * 0.16f);
        g.fillOval((size - dot) / 2, (size - dot) / 2, dot, dot);
        g.dispose();
        return img;
    }

    public static java.util.List<java.awt.Image> appIcons() {
        java.util.List<java.awt.Image> l = new java.util.ArrayList<>();
        for (int s : new int[]{16, 20, 24, 32, 48, 64, 128}) l.add(appIcon(s));
        return l;
    }

    /** Bloco central "nada aqui ainda": título + subtítulo. */
    public static javax.swing.JComponent emptyState(String title, String subtitle) {
        javax.swing.JPanel p = new javax.swing.JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(new javax.swing.border.EmptyBorder(36, 20, 36, 20));

        javax.swing.JLabel dot = new javax.swing.JLabel("○");
        dot.setFont(new Font("Segoe UI", Font.PLAIN, 40));
        dot.setForeground(TEXT_MUT);
        dot.setAlignmentX(Component.CENTER_ALIGNMENT);

        javax.swing.JLabel t = new javax.swing.JLabel(title);
        t.setFont(FONT_SECTION); t.setForeground(TEXT_SEC);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        javax.swing.JLabel s = new javax.swing.JLabel(subtitle);
        s.setFont(FONT_SMALL); s.setForeground(TEXT_MUT);
        s.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(dot);
        p.add(javax.swing.Box.createVerticalStrut(10));
        p.add(t);
        p.add(javax.swing.Box.createVerticalStrut(4));
        p.add(s);
        return p;
    }
}
