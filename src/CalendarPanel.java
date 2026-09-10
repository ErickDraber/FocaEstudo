import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class CalendarPanel extends JPanel {

    private final List<StudySession>   sessions;
    private final Map<String, Integer> goals;
    private final Map<String, Color>   subjectColors;

    private Calendar displayed;
    private int      selectedDay = -1;
    private int      hoverDay    = -1;
    private JPanel   gridPanel;
    private JLabel   monthLabel;
    private JPanel   dayDetailPanel;  // painel de detalhes do dia clicado

    public CalendarPanel(List<StudySession> sessions,
                         Map<String, Integer> goals,
                         Map<String, Color> subjectColors) {
        this.sessions      = sessions;
        this.goals         = goals;
        this.subjectColors = subjectColors;
        this.displayed     = Calendar.getInstance();

        setOpaque(false);
        setLayout(new BorderLayout(0, 8));

        add(buildHeader(),    BorderLayout.NORTH);

        gridPanel = new JPanel();
        gridPanel.setOpaque(false);

        dayDetailPanel = new JPanel();
        dayDetailPanel.setOpaque(false);
        dayDetailPanel.setLayout(new BoxLayout(dayDetailPanel, BoxLayout.Y_AXIS));
        dayDetailPanel.setVisible(false);

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        north.add(gridPanel, BorderLayout.NORTH);
        north.add(buildHeatLegend(), BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(north,          BorderLayout.NORTH);
        center.add(dayDetailPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        rebuild();
    }

    public void refresh() {
        subjectColors.clear();
        rebuild();
    }

    public void updateColors(Map<String, Color> colors) {
        subjectColors.clear();
        subjectColors.putAll(colors);
        rebuild();
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        h.setBorder(new EmptyBorder(0, 0, 6, 0));

        monthLabel = new JLabel("", SwingConstants.CENTER);
        monthLabel.setFont(AppTheme.FONT_BOLD);
        monthLabel.setForeground(AppTheme.TEXT_PRI);

        JButton prev = arrowBtn("‹");
        JButton next = arrowBtn("›");
        prev.addActionListener(e -> { displayed.add(Calendar.MONTH, -1); rebuild(); });
        next.addActionListener(e -> { displayed.add(Calendar.MONTH, +1); rebuild(); });

        h.add(prev,       BorderLayout.WEST);
        h.add(monthLabel, BorderLayout.CENTER);
        h.add(next,       BorderLayout.EAST);
        return h;
    }

    private JButton arrowBtn(String txt) {
        JButton b = new JButton(txt);
        b.setFont(new Font("Segoe UI", Font.BOLD, 16));
        b.setForeground(AppTheme.ACCENT);
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setForeground(AppTheme.ACCENT_DARK); }
            @Override public void mouseExited(MouseEvent e)  { b.setForeground(AppTheme.ACCENT); }
        });
        return b;
    }

    // ── Grid ─────────────────────────────────────────────────────────────────

    private void rebuild() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", new Locale("pt", "BR"));
        String ms = sdf.format(displayed.getTime());
        monthLabel.setText(Character.toUpperCase(ms.charAt(0)) + ms.substring(1));

        gridPanel.removeAll();
        gridPanel.setLayout(new GridLayout(0, 7, 3, 3));

        // Cabeçalho
        for (String d : new String[]{"Dom","Seg","Ter","Qua","Qui","Sex","Sáb"}) {
            JLabel l = new JLabel(d, SwingConstants.CENTER);
            l.setFont(new Font("Segoe UI", Font.BOLD, 10));
            l.setForeground(AppTheme.TEXT_SEC);
            gridPanel.add(l);
        }

        Calendar cal = (Calendar) displayed.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDow    = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int totalGoal   = goals.values().stream().mapToInt(Integer::intValue).sum();
        Calendar today  = Calendar.getInstance();

        // Agrupa minutos por dia
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyyMMdd");
        Map<String, Integer> minsByDay = new LinkedHashMap<>();
        // Agrupa minutos por dia e matéria: "yyyyMMdd_materia" -> minutos
        Map<String, Integer> minsByDaySubj = new LinkedHashMap<>();

        for (StudySession s : sessions) {
            Calendar sc = Calendar.getInstance();
            sc.setTimeInMillis(s.getTimestamp());
            if (sc.get(Calendar.YEAR)  == displayed.get(Calendar.YEAR) &&
                sc.get(Calendar.MONTH) == displayed.get(Calendar.MONTH)) {
                String key     = dayFmt.format(new Date(s.getTimestamp()));
                String subjKey = key + "_" + s.getSubject();
                minsByDay.merge(key, s.getMinutes(), Integer::sum);
                minsByDaySubj.merge(subjKey, s.getMinutes(), Integer::sum);
            }
        }

        // Células vazias
        for (int i = 0; i < firstDow; i++) gridPanel.add(new JLabel(""));

        // Dias
        for (int day = 1; day <= daysInMonth; day++) {
            cal.set(Calendar.DAY_OF_MONTH, day);
            String key = dayFmt.format(cal.getTime());
            int mins   = minsByDay.getOrDefault(key, 0);
            boolean isToday = cal.get(Calendar.YEAR)  == today.get(Calendar.YEAR)
                           && cal.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                           && cal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH);

            final int finalDay = day;
            final String finalKey = key;
            JPanel cell = buildDayCell(day, mins, totalGoal, isToday);
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cell.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hoverDay = finalDay; gridPanel.repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hoverDay = -1;       gridPanel.repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (selectedDay == finalDay) {
                        // Desclica
                        selectedDay = -1;
                        dayDetailPanel.setVisible(false);
                        dayDetailPanel.removeAll();
                        dayDetailPanel.revalidate();
                        dayDetailPanel.repaint();
                    } else {
                        selectedDay = finalDay;
                        showDayDetail(finalDay, finalKey, minsByDaySubj, totalGoal);
                    }
                    gridPanel.repaint();
                }
            });
            gridPanel.add(cell);
        }

        gridPanel.revalidate();
        gridPanel.repaint();
        selectedDay = -1;
        dayDetailPanel.setVisible(false);
    }

    // ── Célula ───────────────────────────────────────────────────────────────

    private JPanel buildDayCell(int day, int mins, int goalMins, boolean isToday) {
        boolean studied  = mins > 0;
        boolean metGoal  = goalMins > 0 && mins >= goalMins;
        boolean missGoal = goalMins > 0 && studied && mins < goalMins;

        // Mapa de consistência: intensidade do azul conforme o tempo estudado
        // (proporcional à meta, ou a faixas fixas quando não há meta).
        final int level = heatLevel(mins, goalMins);
        final Color bgColor = heatColor(level);
        final boolean darkBg = level >= 3;

        JPanel cell = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (isToday) {
                    g2.setColor(AppTheme.WARNING);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 8, 8);
                }
                if (day == selectedDay) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.drawRoundRect(2, 2, getWidth()-4, getHeight()-4, 6, 6);
                } else if (day == hoverDay) {
                    g2.setColor(new Color(255, 255, 255, 40));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(new Color(255, 255, 255, 90));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 8, 8);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setBorder(new EmptyBorder(3, 3, 3, 3));
        cell.setPreferredSize(new Dimension(0, 54));

        // Número do dia
        Color textColor = darkBg ? Color.WHITE : AppTheme.TEXT_PRI;
        JLabel dayLbl = new JLabel(String.valueOf(day), SwingConstants.CENTER);
        dayLbl.setFont(new Font("Segoe UI", isToday ? Font.BOLD : Font.PLAIN, 11));
        dayLbl.setForeground(isToday && !studied ? AppTheme.WARNING : textColor);
        dayLbl.setAlignmentX(CENTER_ALIGNMENT);
        cell.add(dayLbl);

        if (studied) {
            // Tempo
            int h = mins / 60, m = mins % 60;
            String t = h > 0 ? h + "h" + String.format("%02d", m) : m + "min";
            JLabel timeLbl = new JLabel(t, SwingConstants.CENTER);
            timeLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
            timeLbl.setForeground(darkBg ? Color.WHITE : AppTheme.TEXT_PRI);
            timeLbl.setAlignmentX(CENTER_ALIGNMENT);
            cell.add(timeLbl);

            // Barra de progresso (só se tem meta)
            if (goalMins > 0) {
                int pct = (int) Math.min(100, mins * 100.0 / goalMins);
                JPanel bar = buildMiniBar(pct, metGoal);
                bar.setAlignmentX(CENTER_ALIGNMENT);
                cell.add(Box.createVerticalStrut(2));
                cell.add(bar);

                JLabel icon = new JLabel(metGoal ? "✅" : "❌", SwingConstants.CENTER);
                icon.setFont(new Font("Segoe UI", Font.PLAIN, 8));
                icon.setAlignmentX(CENTER_ALIGNMENT);
                cell.add(icon);
            }
        }

        return cell;
    }

    /** Legenda "menos ▢▢▢▢▢ mais" do mapa de consistência. */
    private JComponent buildHeatLegend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 2));
        p.setOpaque(false);
        JLabel less = new JLabel("menos");
        less.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        less.setForeground(AppTheme.TEXT_MUT);
        p.add(less);
        for (int lv = 0; lv <= 4; lv++) {
            final Color col = heatColor(lv);
            JPanel sw = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(col);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                    g2.dispose();
                }
                @Override public Dimension getPreferredSize() { return new Dimension(12, 12); }
            };
            sw.setOpaque(false);
            p.add(sw);
        }
        JLabel more = new JLabel("mais");
        more.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        more.setForeground(AppTheme.TEXT_MUT);
        p.add(more);
        return p;
    }

    /** 0 = nada; 1..4 = intensidade crescente. Usa a meta se houver, senão faixas fixas. */
    private static int heatLevel(int mins, int goalMins) {
        if (mins <= 0) return 0;
        if (goalMins > 0) {
            double r = mins / (double) goalMins;
            return r >= 1.5 ? 4 : r >= 1.0 ? 3 : r >= 0.5 ? 2 : 1;
        }
        return mins >= 120 ? 4 : mins >= 60 ? 3 : mins >= 30 ? 2 : 1;
    }

    private static Color heatColor(int level) {
        if (level <= 0) return AppTheme.SURFACE2;
        Color base = new Color(0x4F6AFF);
        float[] mix = {0f, 0.22f, 0.45f, 0.72f, 1f};
        return blend(AppTheme.SURFACE2, base, mix[level]);
    }

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    private JPanel buildMiniBar(int pct, boolean hit) {
        JPanel bar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255,255,255,60));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                int fw = (int)(getWidth() * pct / 100.0);
                g2.setColor(hit ? AppTheme.SUCCESS : Color.WHITE);
                g2.fillRoundRect(0, 0, fw, getHeight(), 4, 4);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(40, 4); }
            @Override public Dimension getMaximumSize()   { return new Dimension(200, 4); }
        };
        bar.setOpaque(false);
        return bar;
    }

    // ── Painel de detalhe do dia ─────────────────────────────────────────────

    private void showDayDetail(int day, String key,
                               Map<String, Integer> minsByDaySubj,
                               int totalGoal) {
        dayDetailPanel.removeAll();
        dayDetailPanel.setVisible(true);

        // Título
        JLabel title = new JLabel("Dia " + day, SwingConstants.LEFT);
        title.setFont(AppTheme.FONT_BOLD);
        title.setForeground(AppTheme.TEXT_PRI);
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(8, 0, 6, 0));
        dayDetailPanel.add(title);

        // Separador
        JSeparator sep = new JSeparator();
        sep.setForeground(AppTheme.DIVIDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        dayDetailPanel.add(sep);
        dayDetailPanel.add(Box.createVerticalStrut(6));

        // Matérias estudadas nesse dia
        boolean any = false;
        int totalMins = 0;
        for (Map.Entry<String, Integer> e : minsByDaySubj.entrySet()) {
            if (!e.getKey().startsWith(key + "_")) continue;
            String subject = e.getKey().substring(key.length() + 1);
            int mins       = e.getValue();
            totalMins     += mins;
            int goal       = goals.getOrDefault(subject, 0);
            Color color    = subjectColors.getOrDefault(subject, AppTheme.ACCENT);
            dayDetailPanel.add(buildSubjectRow(subject, mins, goal, color));
            dayDetailPanel.add(Box.createVerticalStrut(6));
            any = true;
        }

        if (!any) {
            JLabel empty = new JLabel("Nenhuma sessão registrada neste dia.");
            empty.setFont(AppTheme.FONT_SMALL);
            empty.setForeground(AppTheme.TEXT_SEC);
            empty.setAlignmentX(LEFT_ALIGNMENT);
            dayDetailPanel.add(empty);
        } else {
            // Total do dia
            dayDetailPanel.add(Box.createVerticalStrut(2));
            JSeparator sep2 = new JSeparator();
            sep2.setForeground(AppTheme.DIVIDER);
            sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            dayDetailPanel.add(sep2);
            dayDetailPanel.add(Box.createVerticalStrut(4));

            int th = totalMins / 60, tm = totalMins % 60;
            JPanel totalRow = new JPanel(new BorderLayout());
            totalRow.setOpaque(false);
            totalRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            JLabel totalLbl = new JLabel("Total do dia");
            totalLbl.setFont(AppTheme.FONT_BOLD); totalLbl.setForeground(AppTheme.TEXT_PRI);
            JLabel totalTime = new JLabel(String.format("%dh %02dmin", th, tm));
            totalTime.setFont(AppTheme.FONT_BOLD); totalTime.setForeground(AppTheme.ACCENT);
            totalRow.add(totalLbl,  BorderLayout.WEST);
            totalRow.add(totalTime, BorderLayout.EAST);
            totalRow.setAlignmentX(LEFT_ALIGNMENT);
            dayDetailPanel.add(totalRow);
        }

        dayDetailPanel.revalidate();
        dayDetailPanel.repaint();
    }

    private JPanel buildSubjectRow(String subject, int mins, int goalMins, Color color) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setAlignmentX(LEFT_ALIGNMENT);

        // Linha superior: dot + nome + tempo
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        // Dot colorido
        JPanel dot = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color); g2.fillOval(0, 3, 10, 10); g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(14, 16); }
        };
        dot.setOpaque(false);

        JLabel nameLbl = new JLabel(subject);
        nameLbl.setFont(AppTheme.FONT_LABEL); nameLbl.setForeground(AppTheme.TEXT_PRI);

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        nameRow.setOpaque(false);
        nameRow.add(dot); nameRow.add(nameLbl);

        int h = mins / 60, m = mins % 60;
        JLabel timeLbl = new JLabel(h > 0 ? String.format("%dh %02dmin", h, m) : m + " min");
        timeLbl.setFont(AppTheme.FONT_BOLD); timeLbl.setForeground(color);

        top.add(nameRow, BorderLayout.WEST);
        top.add(timeLbl, BorderLayout.EAST);
        row.add(top);

        // Barra de progresso (se tem meta)
        if (goalMins > 0) {
            int pct = (int) Math.min(100, mins * 100.0 / goalMins);
            boolean hit = mins >= goalMins;

            row.add(Box.createVerticalStrut(3));

            // Label meta
            JPanel metaRow = new JPanel(new BorderLayout(4, 0));
            metaRow.setOpaque(false);
            metaRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
            int gH = goalMins / 60, gM = goalMins % 60;
            JLabel metaLbl = new JLabel(String.format("Meta: %dh %02dmin", gH, gM));
            metaLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10)); metaLbl.setForeground(AppTheme.TEXT_SEC);
            JLabel pctLbl = new JLabel(pct + "%");
            pctLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
            pctLbl.setForeground(hit ? AppTheme.SUCCESS : AppTheme.ACCENT);
            metaRow.add(metaLbl, BorderLayout.WEST);
            metaRow.add(pctLbl,  BorderLayout.EAST);
            row.add(metaRow);

            // Barra
            final Color barColor = hit ? AppTheme.SUCCESS : color;
            final int   finalPct = pct;
            JPanel bar = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppTheme.DIVIDER);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                    int fw = (int)(getWidth() * finalPct / 100.0);
                    g2.setColor(barColor);
                    g2.fillRoundRect(0, 0, fw, getHeight(), 4, 4);
                    g2.dispose();
                }
                @Override public Dimension getPreferredSize() { return new Dimension(0, 6); }
                @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, 6); }
            };
            bar.setOpaque(false);
            bar.setAlignmentX(LEFT_ALIGNMENT);
            row.add(Box.createVerticalStrut(2));
            row.add(bar);
        }

        return row;
    }
}
