import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** Janela de estatísticas: dia da semana, horário, evolução mensal e tipo de atividade. */
public class StatsPanel extends JDialog {

    private final List<StudySession> sessions;

    public StatsPanel(JFrame owner, List<StudySession> sessions, Map<String, StudyData> studyDataMap) {
        super(owner, "Estatísticas", false);
        this.sessions = sessions;

        setSize(560, 620);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(AppTheme.BG);
        setLayout(new BorderLayout());

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(AppTheme.BG);
        body.setBorder(new EmptyBorder(16, 18, 16, 18));

        if (sessions.isEmpty()) {
            body.add(AppTheme.emptyState("Sem dados ainda", "As estatísticas aparecem quando você registrar sessões."));
        } else {
            body.add(section("Tempo por dia da semana (últimas 8 semanas)"));
            body.add(weekdayChart());
            body.add(Box.createVerticalStrut(18));

            body.add(section("Melhor horário do dia"));
            body.add(hourChart());
            body.add(Box.createVerticalStrut(18));

            body.add(section("Evolução mensal (últimos 6 meses)"));
            body.add(monthChart());
            body.add(Box.createVerticalStrut(18));

            body.add(section("Por sub-foco"));
            body.add(kindChart());

            if (sessions.stream().anyMatch(s -> s.getEnergy() > 0)) {
                body.add(Box.createVerticalStrut(18));
                body.add(section("Energia por horário"));
                body.add(energyChart());
            }
        }

        JScrollPane sc = new JScrollPane(body);
        sc.setBorder(null);
        sc.getViewport().setBackground(AppTheme.BG);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        add(sc, BorderLayout.CENTER);
    }

    // ── Gráficos ────────────────────────────────────────────────────────────

    private JComponent weekdayChart() {
        int[] mins = new int[7];
        LocalDate limite = LocalDate.now().minusWeeks(8);
        for (StudySession s : sessions) {
            LocalDate d = Instant.ofEpochMilli(s.getTimestamp()).atZone(ZoneId.systemDefault()).toLocalDate();
            if (d.isBefore(limite)) continue;
            mins[d.getDayOfWeek().getValue() - 1] += s.getMinutes();
        }
        String[] nomes = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
        return bars(nomes, mins, AppTheme.ACCENT);
    }

    private JComponent hourChart() {
        int[] mins = new int[24];
        for (StudySession s : sessions) {
            int hr = Instant.ofEpochMilli(s.getTimestamp()).atZone(ZoneId.systemDefault()).getHour();
            mins[hr] += s.getMinutes();
        }
        String[] nomes = new String[24];
        for (int i = 0; i < 24; i++) nomes[i] = String.format("%02dh", i);
        return bars(nomes, mins, new Color(0x26A69A));
    }

    private JComponent monthChart() {
        LocalDate now = LocalDate.now();
        String[] nomes = new String[6];
        int[] mins = new int[6];
        for (int i = 0; i < 6; i++) {
            LocalDate m = now.minusMonths(5 - i);
            nomes[i] = m.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, new Locale("pt", "BR"))
                       + "/" + (m.getYear() % 100);
        }
        for (StudySession s : sessions) {
            LocalDate d = Instant.ofEpochMilli(s.getTimestamp()).atZone(ZoneId.systemDefault()).toLocalDate();
            for (int i = 0; i < 6; i++) {
                LocalDate m = now.minusMonths(5 - i);
                if (d.getYear() == m.getYear() && d.getMonthValue() == m.getMonthValue()) mins[i] += s.getMinutes();
            }
        }
        return bars(nomes, mins, new Color(0xAB47BC));
    }

    private JComponent kindChart() {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (StudySession s : sessions) {
            String k = s.getKind() == null ? "" : s.getKind();
            map.merge(k.isEmpty() ? "" : HistoryPanel.kindLabel(k), s.getMinutes(), Integer::sum);
        }
        // ordena por tempo desc, "sem tipo" por último
        List<Map.Entry<String, Integer>> es = new ArrayList<>(map.entrySet());
        es.sort((a, b) -> {
            if (a.getKey().isEmpty()) return 1;
            if (b.getKey().isEmpty()) return -1;
            return Integer.compare(b.getValue(), a.getValue());
        });
        int n = Math.min(es.size(), 10);
        String[] nomes = new String[n];
        int[] mins = new int[n];
        for (int i = 0; i < n; i++) {
            nomes[i] = es.get(i).getKey().isEmpty() ? "Sem sub-foco" : es.get(i).getKey();
            mins[i] = es.get(i).getValue();
        }
        return bars(nomes, mins, new Color(0xFFA726));
    }

    /** Energia média por faixa de horário (manhã/tarde/noite/madrugada). */
    private JComponent energyChart() {
        String[] faixas = {"Madrugada", "Manhã", "Tarde", "Noite"};
        int[] soma = new int[4], cont = new int[4];
        for (StudySession s : sessions) {
            if (s.getEnergy() <= 0) continue;
            int hr = Instant.ofEpochMilli(s.getTimestamp()).atZone(ZoneId.systemDefault()).getHour();
            int f = hr < 5 ? 0 : hr < 12 ? 1 : hr < 18 ? 2 : 3;
            soma[f] += s.getEnergy(); cont[f]++;
        }
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        String[] icons = {"", "😴", "🙂", "🔥"};
        for (int i = 0; i < 4; i++) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            JLabel l = new JLabel(faixas[i]);
            l.setFont(AppTheme.FONT_SMALL); l.setForeground(AppTheme.TEXT_SEC);
            l.setPreferredSize(new Dimension(90, 16));
            String v;
            if (cont[i] == 0) v = "—";
            else {
                double avg = soma[i] / (double) cont[i];
                int rounded = (int) Math.round(avg);
                v = icons[Math.max(1, Math.min(3, rounded))]
                        + "  (" + (cont[i] == 1 ? "1 sessão" : cont[i] + " sessões") + ")";
            }
            JLabel val = new JLabel(v);
            val.setFont(AppTheme.FONT_LABEL);
            val.setForeground(cont[i] == 0 ? AppTheme.TEXT_MUT : AppTheme.TEXT_PRI);
            row.add(l, BorderLayout.WEST);
            row.add(val, BorderLayout.CENTER);
            p.add(row);
            p.add(Box.createVerticalStrut(4));
        }
        return p;
    }

    /** Uma coluna de barras horizontais rótulo · barra · tempo. */
    private JComponent bars(String[] labels, int[] values, Color color) {
        int max = 1;
        for (int v : values) max = Math.max(max, v);

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int i = 0; i < labels.length; i++) {
            final int v = values[i], m = max;
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

            JLabel l = new JLabel(labels[i]);
            l.setFont(AppTheme.FONT_SMALL); l.setForeground(AppTheme.TEXT_SEC);
            l.setPreferredSize(new Dimension(64, 16));

            JPanel bar = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppTheme.SURFACE2);
                    g2.fillRoundRect(0, 4, getWidth(), getHeight() - 8, 6, 6);
                    int w = (int) (getWidth() * (v / (double) m));
                    g2.setColor(color);
                    g2.fillRoundRect(0, 4, Math.max(v > 0 ? 3 : 0, w), getHeight() - 8, 6, 6);
                    g2.dispose();
                }
                @Override public Dimension getPreferredSize() { return new Dimension(10, 18); }
            };
            bar.setOpaque(false);

            JLabel val = new JLabel(fmt(v), SwingConstants.RIGHT);
            val.setFont(AppTheme.FONT_SMALL);
            val.setForeground(v > 0 ? AppTheme.TEXT_PRI : AppTheme.TEXT_SEC);
            val.setPreferredSize(new Dimension(70, 16));

            row.add(l, BorderLayout.WEST);
            row.add(bar, BorderLayout.CENTER);
            row.add(val, BorderLayout.EAST);
            p.add(row);
            p.add(Box.createVerticalStrut(4));
        }
        return p;
    }

    private static String fmt(int minutes) {
        if (minutes <= 0) return "—";
        int h = minutes / 60, m = minutes % 60;
        return h > 0 ? h + "h" + String.format("%02d", m) : m + "min";
    }

    private JComponent section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.FONT_SECTION);
        l.setForeground(AppTheme.TEXT_PRI);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }

    private JComponent bigLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.FONT_LABEL);
        l.setForeground(AppTheme.TEXT_SEC);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
}
