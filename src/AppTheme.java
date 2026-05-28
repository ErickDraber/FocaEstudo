import java.awt.*;

public class AppTheme {

    // --- Paleta principal ---
    public static final Color BG          = new Color(0xF4F6FB);
    public static final Color SURFACE     = new Color(0xFFFFFF);
    public static final Color SURFACE2    = new Color(0xECEFF7);
    public static final Color ACCENT      = new Color(0x4F6AFF);
    public static final Color ACCENT_DARK = new Color(0x3450E0);
    public static final Color TEXT_PRI    = new Color(0x1A1C2E);
    public static final Color TEXT_SEC    = new Color(0x5A5F7A);
    public static final Color DIVIDER     = new Color(0xD0D6E8);
    public static final Color DANGER      = new Color(0xE53935);
    public static final Color SUCCESS     = new Color(0x2E7D32);
    public static final Color WARNING     = new Color(0xFF8F00);
    public static final Color CARD_BORDER = new Color(0xE0E4F0);

    // --- Fontes ---
    public static final Font FONT_TITLE   = new Font("Segoe UI", Font.BOLD,   20);
    public static final Font FONT_LABEL   = new Font("Segoe UI", Font.PLAIN,  13);
    public static final Font FONT_BOLD    = new Font("Segoe UI", Font.BOLD,   13);
    public static final Font FONT_SMALL   = new Font("Segoe UI", Font.PLAIN,  11);
    public static final Font FONT_TIMER   = new Font("Segoe UI", Font.BOLD,   42);
    public static final Font FONT_TIMER_S = new Font("Segoe UI", Font.BOLD,   32);
    public static final Font FONT_MONO    = new Font("Consolas",  Font.BOLD,  38);
    public static final Font FONT_MONO_S  = new Font("Consolas",  Font.BOLD,  28);

    /** Palete fixa de cores para as matérias */
    public static final Color[] PALETTE = {
        new Color(0xEF5350), new Color(0x42A5F5), new Color(0x66BB6A),
        new Color(0xFFA726), new Color(0xAB47BC), new Color(0x26A69A),
        new Color(0xFFCA28), new Color(0x78909C), new Color(0xEC407A),
        new Color(0x29B6F6), new Color(0x9CCC65), new Color(0xFF7043)
    };

    public static Color nextColor(int index) {
        return PALETTE[index % PALETTE.length];
    }
}
