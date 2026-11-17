import java.awt.Color;

/** Tipos de área (Estudo, Físico, Lazer, Trabalho, Outro): chave gravada, rótulo e cor. */
final class AreaTypes {

    private AreaTypes() {}

    static final String[] KEYS   = {"estudo", "fisico", "lazer", "trabalho", "outro"};
    static final String[] LABELS = {"Estudo", "Físico", "Lazer", "Trabalho", "Outro"};

    /** Posição do tipo em KEYS; desconhecido ⇒ 0 (Estudo). */
    static int index(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return 0;
    }

    static String label(String key) { return LABELS[index(key)]; }

    static Color color(String key) {
        switch (key) {
            case "fisico":   return new Color(0x66BB6A);
            case "lazer":    return new Color(0xAB47BC);
            case "trabalho": return new Color(0xFFA726);
            case "outro":    return new Color(0x8FA0B8);
            default:         return AppTheme.ACCENT;   // estudo
        }
    }
}
