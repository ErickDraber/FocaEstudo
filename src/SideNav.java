import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Barra de navegação lateral: título + total no topo, telas (Hoje, Semana…) e,
 * embaixo, atalhos que abrem janelas (Tarefas, Ajustes, Tema).
 */
public class SideNav extends JPanel {

    private final JPanel top    = new JPanel();
    private final JPanel bottom = new JPanel();
    private final Map<String, Item> screens = new LinkedHashMap<>();
    private final JLabel subtitle;

    public SideNav(String title) {
        setLayout(new BorderLayout());
        setBackground(AppTheme.SURFACE);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, AppTheme.DIVIDER));
        setPreferredSize(new Dimension(168, 0));

        JPanel head = new JPanel(new GridLayout(2, 1, 0, 2));
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(16, 18, 14, 12));
        JLabel t = new JLabel(title);
        t.setFont(AppTheme.FONT_TITLE);
        t.setForeground(AppTheme.TEXT_PRI);
        subtitle = new JLabel(" ");
        subtitle.setFont(AppTheme.FONT_SMALL);
        subtitle.setForeground(AppTheme.TEXT_SEC);
        head.add(t); head.add(subtitle);

        for (JPanel p : new JPanel[]{top, bottom}) {
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setOpaque(false);
            p.setBorder(new EmptyBorder(0, 10, 0, 10));
        }
        bottom.setBorder(new EmptyBorder(0, 10, 12, 10));

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(top, BorderLayout.NORTH);

        add(head,   BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /** Texto pequeno embaixo do título (ex.: total geral). */
    public JLabel subtitle() { return subtitle; }

    /** Tela: fica marcada enquanto está aberta. */
    public void addScreen(String key, String label, Runnable onClick) {
        Item it = new Item(label, true, onClick);
        screens.put(key, it);
        top.add(it);
        top.add(Box.createVerticalStrut(2));
    }

    /** Atalho que abre uma janela: nunca fica marcado. */
    public void addAction(String label, Runnable onClick) {
        Item it = new Item(label, false, onClick);
        bottom.add(it);
        bottom.add(Box.createVerticalStrut(2));
    }

    public void select(String key) {
        screens.forEach((k, it) -> it.setActive(k.equals(key)));
    }

    private static class Item extends JPanel {
        final String label;
        final boolean screen;
        boolean active, hovered;

        Item(String label, boolean screen, Runnable onClick) {
            this.label = label;
            this.screen = screen;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName(label);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { if (onClick != null) onClick.run(); }
            });
        }

        void setActive(boolean a) { active = a; repaint(); }

        @Override public Dimension getPreferredSize() { return new Dimension(140, 36); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (active || hovered) {
                Color base = active ? AppTheme.ACCENT : AppTheme.TEXT_SEC;
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), active ? 34 : 18));
                g2.fillRoundRect(0, 0, w, h, 10, 10);
            }
            if (active) {   // marcador à esquerda: dá para ver de relance em que tela se está
                g2.setColor(AppTheme.ACCENT);
                g2.fillRoundRect(0, 8, 3, h - 16, 3, 3);
            }
            g2.setFont(active ? AppTheme.FONT_BOLD : AppTheme.FONT_LABEL);
            g2.setColor(active ? AppTheme.ACCENT : screen ? AppTheme.TEXT_PRI : AppTheme.TEXT_SEC);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, 14, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
