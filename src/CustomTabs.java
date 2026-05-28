import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Painel de abas customizado — combina com o tema do app.
 * Substitui o JTabbedPane padrão do Swing.
 */
public class CustomTabs extends JPanel {

    private final List<String>  titles   = new ArrayList<>();
    private final List<JPanel>  panels   = new ArrayList<>();
    private int                 selected = 0;

    private final JPanel tabBar;
    private final JPanel content;
    private final List<TabButton> buttons = new ArrayList<>();

    public CustomTabs() {
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);

        tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabBar.setOpaque(false);
        tabBar.setBorder(new EmptyBorder(0, 0, 8, 0));

        content = new JPanel(new CardLayout());
        content.setOpaque(false);

        add(tabBar,   BorderLayout.NORTH);
        add(content,  BorderLayout.CENTER);
    }

    public void addTab(String title, JPanel panel) {
        titles.add(title);
        panels.add(panel);
        panel.setOpaque(false);

        int index = titles.size() - 1;
        TabButton btn = new TabButton(title, index);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { select(index); }
        });
        buttons.add(btn);
        tabBar.add(btn);
        content.add(panel, title);

        if (index == 0) select(0);
    }

    public void select(int index) {
        selected = index;
        buttons.forEach(b -> b.setActive(b.index == selected));
        ((CardLayout) content.getLayout()).show(content, titles.get(selected));
        tabBar.revalidate(); tabBar.repaint();
    }

    /** Botão de aba individual */
    private static class TabButton extends JPanel {
        final int    index;
        final String title;
        boolean      active  = false;
        boolean      hovered = false;

        TabButton(String title, int index) {
            this.title = title;
            this.index = index;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(6, 14, 6, 14));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            });
        }

        void setActive(boolean a) { active = a; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            if (active) {
                // Fundo com cor do accent translúcido
                Color fill = new Color(
                        AppTheme.ACCENT.getRed(),
                        AppTheme.ACCENT.getGreen(),
                        AppTheme.ACCENT.getBlue(), 28);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
                // Borda inferior colorida
                g2.setColor(AppTheme.ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(6, h - 2, w - 6, h - 2);
            } else if (hovered) {
                Color fill = new Color(
                        AppTheme.TEXT_SEC.getRed(),
                        AppTheme.TEXT_SEC.getGreen(),
                        AppTheme.TEXT_SEC.getBlue(), 18);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
            }

            // Texto
            g2.setFont(active
                    ? new Font("Segoe UI", Font.BOLD,  13)
                    : new Font("Segoe UI", Font.PLAIN, 13));
            g2.setColor(active ? AppTheme.ACCENT : AppTheme.TEXT_SEC);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title,
                    (w - fm.stringWidth(title)) / 2,
                    (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(new Font("Segoe UI", Font.BOLD, 13));
            int w = fm.stringWidth(title) + 28;
            return new Dimension(w, 34);
        }
    }
}
