import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.Map;

public class PieChartPanel extends JPanel {
    private final Map<String, StudyData> data;

    public PieChartPanel(Map<String, StudyData> data) {
        this.data = data;
        setOpaque(false);
    }

    private String abbrev(String name) {
        StringBuilder ab = new StringBuilder();
        for (char c : name.toCharArray())
            if (Character.isUpperCase(c) || Character.isDigit(c)) ab.append(c);
        if (ab.length() == 0 && !name.isEmpty()) ab.append(Character.toUpperCase(name.charAt(0)));
        return ab.toString();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int totalMinutes = data.values().stream().mapToInt(StudyData::getMinutes).sum();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth(), h = getHeight();

        // ── Sem dados ────────────────────────────────────────────────────────
        if (totalMinutes == 0) {
            g2.setColor(AppTheme.TEXT_SEC);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            String msg = "Adicione tempo para começar";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        // ── Dimensões dinâmicas ───────────────────────────────────────────────
        // Legenda: 2 colunas, cada matéria numa linha de ~18px
        int nItems   = (int) data.values().stream().filter(d -> d.getMinutes() > 0).count();
        int nRows    = (int) Math.ceil(nItems / 2.0);
        int rowH     = Math.max(16, Math.min(22, h / 12));
        int legendH  = nRows * rowH + 12;
        int chartH   = h - legendH;

        int pad      = Math.max(12, Math.min(32, Math.min(w, chartH) / 10));
        int diameter = Math.min(w - pad * 2, chartH - pad * 2);
        diameter     = Math.max(60, diameter);
        int cx       = w / 2;
        int cy       = pad + chartH / 2;

        // ── Fatias ───────────────────────────────────────────────────────────
        double startAngle = 90.0;
        for (Map.Entry<String, StudyData> entry : data.entrySet()) {
            StudyData sd = entry.getValue();
            if (sd.getMinutes() <= 0) continue;
            double arc = (double) sd.getMinutes() / totalMinutes * 360.0;
            double pct = (double) sd.getMinutes() / totalMinutes * 100.0;

            // Sombra suave
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillArc(cx - diameter/2 + 2, cy - diameter/2 + 3, diameter, diameter,
                       (int) startAngle, -(int) Math.ceil(arc));

            // Fatia
            g2.setColor(sd.getColor());
            g2.fillArc(cx - diameter/2, cy - diameter/2, diameter, diameter,
                       (int) startAngle, -(int) Math.ceil(arc));

            // Separador entre fatias
            g2.setColor(AppTheme.SURFACE);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2.drawArc(cx - diameter/2, cy - diameter/2, diameter, diameter,
                       (int) startAngle, -(int) Math.ceil(arc));

            // Label dentro da fatia (só se >= 5% e diâmetro suficiente)
            if (pct >= 5.0 && diameter > 100) {
                double labelAngle  = startAngle - arc / 2.0;
                double labelRadius = diameter * 0.31;
                int lx = (int) (cx + labelRadius * Math.cos(Math.toRadians(labelAngle)));
                int ly = (int) (cy - labelRadius * Math.sin(Math.toRadians(labelAngle)));

                double lum = (0.299 * sd.getColor().getRed()
                            + 0.587 * sd.getColor().getGreen()
                            + 0.114 * sd.getColor().getBlue()) / 255.0;
                g2.setColor(lum > 0.6 ? new Color(0x222436) : Color.WHITE);

                int fontSize = Math.max(9, Math.min(12, diameter / 18));
                g2.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
                String label = String.format("%s %.0f%%", abbrev(entry.getKey()), pct);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, lx - fm.stringWidth(label) / 2, ly + fm.getAscent() / 2);
            }

            startAngle -= arc;
        }

        // ── Buraco central ────────────────────────────────────────────────────
        int hole  = (int) (diameter * 0.42);
        g2.setColor(AppTheme.SURFACE);
        g2.fillOval(cx - hole/2, cy - hole/2, hole, hole);

        // Anel interno sutil
        g2.setColor(AppTheme.SURFACE2);
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(cx - hole/2, cy - hole/2, hole, hole);

        // ── Texto central ─────────────────────────────────────────────────────
        int th = totalMinutes / 60, tm = totalMinutes % 60;
        String totalStr   = String.format("%dh %02dm", th, tm);
        String totalLabel = "Total";

        int fs1 = Math.max(10, Math.min(14, hole / 5));
        int fs2 = Math.max(8,  Math.min(11, hole / 7));

        g2.setFont(new Font("Segoe UI", Font.BOLD, fs1));
        FontMetrics fm1 = g2.getFontMetrics();
        g2.setColor(AppTheme.TEXT_PRI);
        g2.drawString(totalStr, cx - fm1.stringWidth(totalStr) / 2, cy + fm1.getAscent() / 2 + 2);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, fs2));
        FontMetrics fm2 = g2.getFontMetrics();
        g2.setColor(AppTheme.TEXT_SEC);
        g2.drawString(totalLabel, cx - fm2.stringWidth(totalLabel) / 2, cy - fm1.getAscent() / 2 - 2);

        // ── Legenda ───────────────────────────────────────────────────────────
        int legTop   = cy + diameter/2 + 10;
        int col1X    = pad;
        int col2X    = w / 2 + 4;
        int col      = 0;
        int legY     = legTop + rowH / 2;

        int fmLeg    = Math.max(9, Math.min(12, rowH - 4));
        int dotSize  = Math.max(7, Math.min(11, rowH - 6));

        for (Map.Entry<String, StudyData> entry : data.entrySet()) {
            StudyData sd = entry.getValue();
            if (sd.getMinutes() <= 0) continue;

            int curX = col == 0 ? col1X : col2X;

            // Dot
            g2.setColor(sd.getColor());
            g2.fillOval(curX, legY - dotSize + 1, dotSize, dotSize);

            // Nome
            g2.setColor(AppTheme.TEXT_PRI);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, fmLeg));
            int maxChars = col == 0 ? 10 : 9;
            g2.drawString(truncate(entry.getKey(), maxChars), curX + dotSize + 5, legY);

            // Tempo — alinhado à direita da coluna
            int eh = sd.getMinutes() / 60, em = sd.getMinutes() % 60;
            String timeStr = eh > 0
                    ? String.format("%dh%02d", eh, em)
                    : em + "min";
            g2.setFont(new Font("Segoe UI", Font.BOLD, fmLeg));
            g2.setColor(AppTheme.ACCENT);
            FontMetrics fmT = g2.getFontMetrics();
            int timeX = col == 0
                    ? col2X - fmT.stringWidth(timeStr) - 10
                    : w - pad - fmT.stringWidth(timeStr);
            g2.drawString(timeStr, timeX, legY);

            col++;
            if (col == 2) { col = 0; legY += rowH; }
        }

        g2.dispose();
    }
}