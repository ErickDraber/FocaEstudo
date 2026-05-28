import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class PieChartPanel extends JPanel {
    private final Map<String, StudyData> data;

    public PieChartPanel(Map<String, StudyData> data) {
        this.data = data;
        setBackground(AppTheme.SURFACE);
    }

    private String getAbbreviation(String name) {
        StringBuilder ab = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c) || Character.isDigit(c)) ab.append(c);
        }
        if (ab.length() == 0 && !name.isEmpty()) ab.append(Character.toUpperCase(name.charAt(0)));
        return ab.toString();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int totalMinutes = data.values().stream().mapToInt(StudyData::getMinutes).sum();
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        if (totalMinutes == 0) {
            g2.setColor(AppTheme.TEXT_SEC);
            g2.setFont(AppTheme.FONT_LABEL);
            String msg = "Adicione tempo de estudo para começar.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            return;
        }

        int pad      = 40;
        int diameter = Math.min(w, h) - pad * 2;
        int x        = (w - diameter) / 2;
        int y        = (h - diameter) / 2;
        double startAngle = 90.0;

        for (Map.Entry<String, StudyData> entry : data.entrySet()) {
            StudyData sd = entry.getValue();
            if (sd.getMinutes() <= 0) continue;
            double arcAngle  = (double) sd.getMinutes() / totalMinutes * 360.0;
            double pct       = (double) sd.getMinutes() / totalMinutes * 100.0;

            // Fatia
            g2.setColor(sd.getColor());
            g2.fillArc(x, y, diameter, diameter, (int) startAngle, (int) -Math.ceil(arcAngle));

            // Borda fina entre fatias
            g2.setColor(AppTheme.SURFACE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawArc(x, y, diameter, diameter, (int) startAngle, (int) -Math.ceil(arcAngle));

            // Label — só se fatia >= 4%
            if (pct >= 4.0) {
                double labelAngle  = startAngle - (arcAngle / 2.0);
                double labelRadius = diameter * 0.33;
                int lx = (int) (x + diameter / 2.0 + labelRadius * Math.cos(Math.toRadians(labelAngle)));
                int ly = (int) (y + diameter / 2.0 - labelRadius * Math.sin(Math.toRadians(labelAngle)));

                double lum = (0.299 * sd.getColor().getRed()
                            + 0.587 * sd.getColor().getGreen()
                            + 0.114 * sd.getColor().getBlue()) / 255.0;
                g2.setColor(lum > 0.55 ? new Color(0x1A1C2E) : Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String label = String.format("%s %.0f%%", getAbbreviation(entry.getKey()), pct);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, lx - fm.stringWidth(label) / 2, ly + fm.getAscent() / 2);
            }

            startAngle -= arcAngle;
        }

        // Buraco central (donut)
        int hole   = (int) (diameter * 0.38);
        int holeX  = x + (diameter - hole) / 2;
        int holeY  = y + (diameter - hole) / 2;
        g2.setColor(AppTheme.SURFACE);
        g2.fillOval(holeX, holeY, hole, hole);

        // Total no centro
        int th = totalMinutes / 60, tm = totalMinutes % 60;
        String totalStr  = String.format("%dh %02dm", th, tm);
        String totalLabel = "Total";
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        FontMetrics fm1 = g2.getFontMetrics();
        g2.setColor(AppTheme.TEXT_PRI);
        g2.drawString(totalStr,  x + diameter/2 - fm1.stringWidth(totalStr)/2,  y + diameter/2 + 6);
        g2.setFont(AppTheme.FONT_SMALL);
        FontMetrics fm2 = g2.getFontMetrics();
        g2.setColor(AppTheme.TEXT_SEC);
        g2.drawString(totalLabel, x + diameter/2 - fm2.stringWidth(totalLabel)/2, y + diameter/2 - 8);
    }
}
