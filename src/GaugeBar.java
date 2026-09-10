import javax.swing.*;
import java.awt.*;

/**
 * Barra de progresso desenhada à mão: o trilho é uma tinta bem clara da cor da
 * meta e o preenchido vai do tom escuro (início) ao tom base (perto da meta),
 * dando "níveis da mesma cor" para indicar o quanto falta. Substitui o
 * JProgressBar, cujas cores o Nimbus ignorava.
 */
public class GaugeBar extends JComponent {

    private double frac = 0;
    private Color  base = AppTheme.ACCENT;
    private boolean met = false;

    public GaugeBar() { setOpaque(false); }

    public void set(double frac, Color base, boolean met) {
        this.frac = Double.isNaN(frac) ? 0 : Math.max(0, Math.min(1, frac));
        if (base != null) this.base = base;
        this.met = met;
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(80, 8); }
    @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, 8); }
    @Override public Dimension getMinimumSize()   { return new Dimension(24, 8); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight(), r = h;

        // Trilho: tinta clara do mesmo matiz (o "quanto falta").
        g2.setColor(AppTheme.mix(base, AppTheme.SURFACE, 0.85));
        g2.fillRoundRect(0, 0, w, h, r, r);

        int fw = (int) Math.round(w * frac);
        if (fw > 0) {
            Color dark  = AppTheme.shade(base, 0.55);
            Color light = met ? base : AppTheme.mix(base, Color.WHITE, 0.30);
            g2.setPaint(new GradientPaint(0, 0, dark, Math.max(1, w), 0, light));
            Shape old = g2.getClip();
            g2.clipRect(0, 0, fw, h);
            g2.fillRoundRect(0, 0, w, h, r, r);
            g2.setClip(old);
        }
        g2.dispose();
    }
}
