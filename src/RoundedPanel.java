import javax.swing.*;
import java.awt.*;

/** Painel com cantos arredondados e sombra suave */
public class RoundedPanel extends JPanel {
    private final int radius;
    private final Color bgColor;
    private final boolean shadow;

    public RoundedPanel(int radius, Color bgColor, boolean shadow) {
        this.radius  = radius;
        this.bgColor = bgColor;
        this.shadow  = shadow;
        setOpaque(false);
    }

    public RoundedPanel(int radius, Color bgColor) {
        this(radius, bgColor, true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        if (shadow) {
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(3, 5, w - 6, h - 4, radius + 4, radius + 4);
            g2.setColor(new Color(0, 0, 0, 10));
            g2.fillRoundRect(2, 3, w - 4, h - 3, radius + 2, radius + 2);
        }

        g2.setColor(bgColor);
        g2.fillRoundRect(0, 0, w - 2, h - 4, radius, radius);

        g2.setColor(AppTheme.CARD_BORDER);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(0, 0, w - 3, h - 5, radius, radius);

        g2.dispose();
        super.paintComponent(g);
    }
}
