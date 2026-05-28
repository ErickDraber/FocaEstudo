import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Botão moderno com hover, variantes filled/outlined/danger */
public class StyledButton extends JButton {

    public enum Variant { FILLED, OUTLINED, TONAL, DANGER, TEXT }

    private final Variant variant;
    private boolean hovered = false;
    private float alpha = 1f;

    public StyledButton(String text, Variant variant) {
        super(text);
        this.variant = variant;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFont(AppTheme.FONT_LABEL);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        int py = 6, px = 16;
        setBorder(BorderFactory.createEmptyBorder(py, px, py, px));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
        });
    }

    public StyledButton(String text) { this(text, Variant.FILLED); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight(), r = 10;

        switch (variant) {
            case FILLED: {
                Color base = isEnabled() ? AppTheme.ACCENT : new Color(0xCCCCCC);
                Color fill = hovered ? AppTheme.ACCENT_DARK : base;
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, r, r);
                g2.setColor(Color.WHITE);
                break;
            }
            case TONAL: {
                Color fill = hovered ? new Color(0xDDE3FF) : new Color(0xEBEEFF);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, r, r);
                g2.setColor(AppTheme.ACCENT);
                break;
            }
            case OUTLINED: {
                if (hovered) { g2.setColor(new Color(0xEBEEFF)); g2.fillRoundRect(0,0,w,h,r,r); }
                g2.setColor(isEnabled() ? AppTheme.ACCENT : AppTheme.DIVIDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, w - 2, h - 2, r, r);
                g2.setColor(isEnabled() ? AppTheme.ACCENT : AppTheme.TEXT_SEC);
                break;
            }
            case DANGER: {
                Color fill = hovered ? new Color(0xFFEBEB) : new Color(0xFFF5F5);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, r, r);
                g2.setColor(AppTheme.DANGER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, w-2, h-2, r, r);
                g2.setColor(AppTheme.DANGER);
                break;
            }
            case TEXT: {
                if (hovered) { g2.setColor(new Color(0xEBEEFF)); g2.fillRoundRect(0,0,w,h,r,r); }
                g2.setColor(AppTheme.ACCENT);
                break;
            }
        }

        FontMetrics fm = g2.getFontMetrics(getFont());
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.setFont(getFont());
        if (!isEnabled()) g2.setColor(AppTheme.TEXT_SEC);
        g2.drawString(getText(), tx, ty);
        g2.dispose();
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(d.height, 34));
    }
}
