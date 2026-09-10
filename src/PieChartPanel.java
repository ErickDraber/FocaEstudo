import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Donut de dois anéis: o anel interno mostra as ÁREAS (Estudo, Físico, Lazer…)
 * e o anel externo as SUB-ÁREAS (as matérias) dentro de cada área. Antes o
 * gráfico era uma fatia por matéria, sem noção de qual área ela pertencia.
 */
public class PieChartPanel extends JPanel {

    /** Uma sub-área (matéria) dentro de uma área. */
    public static final class Slice {
        public final String label; public final Color color; public final int minutes;
        public Slice(String label, Color color, int minutes) {
            this.label = label; this.color = color; this.minutes = minutes;
        }
    }

    /** Uma área (tipo) com suas sub-áreas. */
    public static final class Group {
        public final String label; public final Color color; public final int minutes;
        public final List<Slice> slices;
        public Group(String label, Color color, int minutes, List<Slice> slices) {
            this.label = label; this.color = color; this.minutes = minutes; this.slices = slices;
        }
    }

    private List<Group> groups = new ArrayList<>();

    public PieChartPanel() { setOpaque(false); }

    public void setGroups(List<Group> g) {
        this.groups = g != null ? g : new ArrayList<>();
        repaint();
    }

    private static String abbrev(String name) {
        StringBuilder ab = new StringBuilder();
        for (char c : name.toCharArray())
            if (Character.isUpperCase(c) || Character.isDigit(c)) ab.append(c);
        if (ab.length() == 0 && !name.isEmpty()) ab.append(Character.toUpperCase(name.charAt(0)));
        return ab.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String fmtTime(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return h > 0 ? h + "h" + String.format("%02d", m) : m + "min";
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth(), h = getHeight();

        int total = 0, nSlices = 0;
        for (Group gr : groups) { total += gr.minutes; nSlices += gr.slices.size(); }

        if (total <= 0) {
            g2.setColor(AppTheme.TEXT_SEC);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            String msg = "Adicione tempo para começar";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        // ── Legenda: uma linha por área + uma por sub-área ───────────────────
        int legRows = groups.size() + nSlices;
        int rowH    = Math.max(15, Math.min(20, h / 14));
        int legendH = legRows * rowH + 10;
        int chartH  = Math.max(90, h - legendH);

        int pad      = Math.max(10, Math.min(28, Math.min(w, chartH) / 10));
        int diameter = Math.max(70, Math.min(w - pad * 2, chartH - pad * 2));
        int cx = w / 2;
        int cy = pad + chartH / 2;

        double rOut = diameter / 2.0;
        double rSliceIn = rOut * 0.74;   // anel externo (sub-áreas): [0.74 .. 1.0]
        double rGroupOut = rOut * 0.70;  // anel interno (áreas):     [0.48 .. 0.70]
        double rGroupIn  = rOut * 0.48;

        // ── Anel externo: sub-áreas (matérias) ──────────────────────────────
        double ang = 90.0;
        for (Group gr : groups) {
            for (Slice sl : gr.slices) {
                if (sl.minutes <= 0) continue;
                double arc = sl.minutes / (double) total * 360.0;
                fillRing(g2, cx, cy, rSliceIn, rOut, ang, arc, sl.color);
                ang -= arc;
            }
        }
        // ── Anel interno: áreas (tipos) ─────────────────────────────────────
        ang = 90.0;
        for (Group gr : groups) {
            if (gr.minutes <= 0) continue;
            double arc = gr.minutes / (double) total * 360.0;
            fillRing(g2, cx, cy, rGroupIn, rGroupOut, ang, arc, gr.color);
            ang -= arc;
        }

        // ── Separadores entre fatias do anel externo ────────────────────────
        g2.setColor(AppTheme.SURFACE);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        ang = 90.0;
        for (Group gr : groups) {
            for (Slice sl : gr.slices) {
                if (sl.minutes <= 0) continue;
                radialLine(g2, cx, cy, rSliceIn, rOut, ang);
                ang -= sl.minutes / (double) total * 360.0;
            }
        }
        // Separadores mais grossos entre áreas (nos dois anéis)
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        ang = 90.0;
        for (Group gr : groups) {
            radialLine(g2, cx, cy, rGroupIn, rOut, ang);
            ang -= gr.minutes / (double) total * 360.0;
        }

        // ── Rótulos nas fatias grandes do anel externo ─────────────────────
        if (diameter > 110) {
            ang = 90.0;
            for (Group gr : groups) {
                for (Slice sl : gr.slices) {
                    if (sl.minutes <= 0) continue;
                    double arc = sl.minutes / (double) total * 360.0;
                    double pct = sl.minutes / (double) total * 100.0;
                    if (pct >= 6.0) {
                        double la = ang - arc / 2.0;
                        double lr = (rSliceIn + rOut) / 2.0;
                        int lx = (int) (cx + lr * Math.cos(Math.toRadians(la)));
                        int ly = (int) (cy - lr * Math.sin(Math.toRadians(la)));
                        double lum = (0.299 * sl.color.getRed() + 0.587 * sl.color.getGreen()
                                    + 0.114 * sl.color.getBlue()) / 255.0;
                        g2.setColor(lum > 0.62 ? new Color(0x222436) : Color.WHITE);
                        int fs = Math.max(9, Math.min(12, diameter / 20));
                        g2.setFont(new Font("Segoe UI", Font.BOLD, fs));
                        String t = String.format("%s %.0f%%", abbrev(sl.label), pct);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(t, lx - fm.stringWidth(t) / 2, ly + fm.getAscent() / 2);
                    }
                    ang -= arc;
                }
            }
        }

        // ── Buraco central + total ─────────────────────────────────────────
        int hole = (int) (rGroupIn * 2 - 6);
        g2.setColor(AppTheme.SURFACE);
        g2.fillOval(cx - hole / 2, cy - hole / 2, hole, hole);
        g2.setColor(AppTheme.SURFACE2);
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(cx - hole / 2, cy - hole / 2, hole, hole);

        int fs1 = Math.max(11, Math.min(15, hole / 5));
        int fs2 = Math.max(8,  Math.min(11, hole / 8));
        g2.setFont(new Font("Segoe UI", Font.BOLD, fs1));
        FontMetrics fm1 = g2.getFontMetrics();
        g2.setColor(AppTheme.TEXT_PRI);
        String totalStr = fmtTime(total);
        g2.drawString(totalStr, cx - fm1.stringWidth(totalStr) / 2, cy + fm1.getAscent() / 2 + 1);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, fs2));
        FontMetrics fm2 = g2.getFontMetrics();
        g2.setColor(AppTheme.TEXT_SEC);
        g2.drawString("Total", cx - fm2.stringWidth("Total") / 2, cy - fm1.getAscent() / 2 - 2);

        // ── Legenda ────────────────────────────────────────────────────────
        int legY = cy + (int) rOut + 12 + rowH / 2;
        int leftX = pad;
        int fsLeg = Math.max(9, Math.min(12, rowH - 4));
        int dot   = Math.max(7, Math.min(11, rowH - 6));

        for (Group gr : groups) {
            // Cabeçalho da área
            g2.setColor(gr.color);
            g2.fillRoundRect(leftX, legY - dot + 1, dot, dot, 3, 3);
            g2.setColor(AppTheme.TEXT_PRI);
            g2.setFont(new Font("Segoe UI", Font.BOLD, fsLeg));
            g2.drawString(gr.label.toUpperCase(), leftX + dot + 6, legY);
            g2.setColor(AppTheme.TEXT_SEC);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, fsLeg));
            String gt = fmtTime(gr.minutes);
            FontMetrics fmg = g2.getFontMetrics();
            g2.drawString(gt, w - pad - fmg.stringWidth(gt), legY);
            legY += rowH;

            // Sub-áreas
            for (Slice sl : gr.slices) {
                g2.setColor(sl.color);
                g2.fillOval(leftX + 10, legY - dot + 1, dot, dot);
                g2.setColor(AppTheme.TEXT_PRI);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, fsLeg));
                g2.drawString(truncate(sl.label, 16), leftX + 10 + dot + 6, legY);
                int pct = (int) Math.round(sl.minutes / (double) total * 100.0);
                String st = fmtTime(sl.minutes) + "  ·  " + pct + "%";
                g2.setColor(AppTheme.TEXT_SEC);
                FontMetrics fms = g2.getFontMetrics();
                g2.drawString(st, w - pad - fms.stringWidth(st), legY);
                legY += rowH;
            }
        }

        g2.dispose();
    }

    /** Preenche um setor de anel (entre rIn e rOut), começando em startDeg e varrendo −sweepDeg. */
    private static void fillRing(Graphics2D g2, int cx, int cy, double rIn, double rOut,
                                 double startDeg, double sweepDeg, Color color) {
        java.awt.geom.Area outer = new java.awt.geom.Area(new java.awt.geom.Arc2D.Double(
                cx - rOut, cy - rOut, rOut * 2, rOut * 2, startDeg, -sweepDeg, java.awt.geom.Arc2D.PIE));
        outer.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
                cx - rIn, cy - rIn, rIn * 2, rIn * 2)));
        g2.setColor(color);
        g2.fill(outer);
    }

    private static void radialLine(Graphics2D g2, int cx, int cy, double rIn, double rOut, double deg) {
        double rad = Math.toRadians(deg);
        double c = Math.cos(rad), s = Math.sin(rad);
        g2.draw(new java.awt.geom.Line2D.Double(
                cx + rIn * c, cy - rIn * s, cx + rOut * c, cy - rOut * s));
    }
}
