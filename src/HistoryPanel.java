import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/** Painel de histórico de sessões exibido numa janela flutuante */
public class HistoryPanel extends JDialog {

    private final List<StudySession> sessions;
    private final Map<String, StudyData> studyDataMap;
    private JPanel listPanel;
    private JComboBox<String> filterCombo;

    public HistoryPanel(JFrame owner, List<StudySession> sessions, Map<String, StudyData> studyDataMap) {
        super(owner, "Histórico de Sessões", false);
        this.sessions     = new ArrayList<>(sessions);
        this.studyDataMap = studyDataMap;

        // Ordena do mais recente ao mais antigo
        this.sessions.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        setSize(520, 540);
        setLocationRelativeTo(owner);
        setResizable(true);
        getContentPane().setBackground(AppTheme.BG);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(), BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(AppTheme.BG);
        listPanel.setBorder(new EmptyBorder(8, 12, 8, 12));

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppTheme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        refreshList("Todas");
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(AppTheme.SURFACE);
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.DIVIDER),
                new EmptyBorder(14, 16, 14, 16)));

        JLabel title = new JLabel("Histórico de Sessões");
        title.setFont(AppTheme.FONT_BOLD);
        title.setForeground(AppTheme.TEXT_PRI);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.add(new JLabel("Filtrar:") {{ setFont(AppTheme.FONT_SMALL); setForeground(AppTheme.TEXT_SEC); }});

        List<String> items = new ArrayList<>();
        items.add("Todas");
        items.addAll(studyDataMap.keySet());
        filterCombo = new JComboBox<>(items.toArray(new String[0]));
        filterCombo.setFont(AppTheme.FONT_SMALL);
        filterCombo.addActionListener(e -> refreshList((String) filterCombo.getSelectedItem()));
        filterRow.add(filterCombo);

        header.add(title, BorderLayout.WEST);
        header.add(filterRow, BorderLayout.EAST);
        return header;
    }

    private void refreshList(String filter) {
        listPanel.removeAll();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy  HH:mm");
        boolean any = false;

        for (StudySession s : sessions) {
            if (!filter.equals("Todas") && !s.getSubject().equals(filter)) continue;
            any = true;

            RoundedPanel card = new RoundedPanel(12, AppTheme.SURFACE, false);
            card.setLayout(new BorderLayout(10, 0));
            card.setBorder(new EmptyBorder(10, 14, 10, 14));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

            // Dot colorido
            StudyData sd  = studyDataMap.get(s.getSubject());
            Color dotColor = sd != null ? sd.getColor() : Color.GRAY;
            JPanel dot = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(dotColor); g2.fillOval(0, 6, 12, 12); g2.dispose();
                }
                @Override public Dimension getPreferredSize() { return new Dimension(12, 24); }
            };
            dot.setOpaque(false);

            // Info esquerda
            JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
            left.setOpaque(false);
            JLabel subj = new JLabel(s.getSubject());
            subj.setFont(AppTheme.FONT_BOLD); subj.setForeground(AppTheme.TEXT_PRI);
            JLabel date = new JLabel(sdf.format(new Date(s.getTimestamp())));
            date.setFont(AppTheme.FONT_SMALL); date.setForeground(AppTheme.TEXT_SEC);
            left.add(subj); left.add(date);

            // Info direita
            JPanel right = new JPanel(new GridLayout(2, 1, 0, 2));
            right.setOpaque(false);
            int h = s.getMinutes() / 60, m = s.getMinutes() % 60;
            String durStr = h > 0 ? String.format("%dh %02dmin", h, m) : String.format("%d min", m);
            JLabel dur = new JLabel(durStr, SwingConstants.RIGHT);
            dur.setFont(AppTheme.FONT_BOLD); dur.setForeground(AppTheme.ACCENT);
            String typeLabel = switch (s.getType()) {
                case "pomodoro"  -> "Pomodoro";
                case "manual"    -> "Manual";
                default          -> "Cronômetro";
            };
            JLabel type = new JLabel(typeLabel, SwingConstants.RIGHT);
            type.setFont(AppTheme.FONT_SMALL); type.setForeground(AppTheme.TEXT_SEC);
            right.add(dur); right.add(type);

            card.add(dot, BorderLayout.WEST);
            card.add(left, BorderLayout.CENTER);
            card.add(right, BorderLayout.EAST);

            listPanel.add(card);
            listPanel.add(Box.createVerticalStrut(6));
        }

        if (!any) {
            JLabel empty = new JLabel("Nenhuma sessão registrada ainda.", SwingConstants.CENTER);
            empty.setFont(AppTheme.FONT_LABEL); empty.setForeground(AppTheme.TEXT_SEC);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            listPanel.add(Box.createVerticalStrut(40));
            listPanel.add(empty);
        }

        listPanel.revalidate();
        listPanel.repaint();
    }
}
