import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Peças comuns às telas (Hoje, Progresso, Áreas…): cabeçalho, corpo rolável, botão pequeno
 * e o formato de minutos. Saíram do StudyTracker para que uma tela possa morar na sua
 * própria classe sem depender dele.
 */
final class ScreenKit {

    private ScreenKit() {}

    /** Cabeçalho das telas: título + dica + botões à direita (opcionais). */
    static JPanel screenHeader(String title, String hint, JComponent... actions) {
        JPanel h = new JPanel(new BorderLayout(12, 0));
        h.setBackground(AppTheme.SURFACE);
        h.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.DIVIDER),
                new EmptyBorder(12, 16, 12, 16)));
        JPanel txt = new JPanel(new GridLayout(hint != null ? 2 : 1, 1, 0, 2));
        txt.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(AppTheme.FONT_SECTION); t.setForeground(AppTheme.TEXT_PRI);
        txt.add(t);
        if (hint != null) {
            JLabel l = new JLabel(hint);
            l.setFont(AppTheme.FONT_SMALL); l.setForeground(AppTheme.TEXT_SEC);
            txt.add(l);
        }
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        for (JComponent a : actions) btns.add(a);
        h.add(txt,  BorderLayout.WEST);
        h.add(btns, BorderLayout.EAST);
        return h;
    }

    /** Painel que acompanha a largura do scroll (sem rolagem horizontal). */
    static class ScrollBody extends JPanel implements Scrollable {
        ScrollBody(LayoutManager lm) { super(lm); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 32; }
        public boolean getScrollableTracksViewportWidth()  { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static JScrollPane quietScroll(JComponent body) {
        JScrollPane sp = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    static StyledButton smallBtn(String text, StyledButton.Variant v, Runnable r) {
        StyledButton b = new StyledButton(text, v);
        b.setFont(AppTheme.FONT_SMALL);
        b.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        b.addActionListener(e -> r.run());
        return b;
    }

    /** Formata minutos: "3h10" quando há horas, senão "52m". */
    static String fmtHM(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return h > 0 ? String.format("%dh%02d", h, m) : m + "m";
    }
}
