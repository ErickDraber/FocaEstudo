import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/** Painel de histórico de sessões exibido numa janela flutuante. Permite editar/excluir. */
public class HistoryPanel extends JDialog {

    private final List<StudySession> sessions;          // referência real da lista do app
    private final Map<String, StudyData> studyDataMap;
    private final Runnable onChange;
    private JPanel listPanel;
    private JComboBox<String> filterCombo;

    public HistoryPanel(JFrame owner, List<StudySession> sessions,
                        Map<String, StudyData> studyDataMap, Runnable onChange) {
        super(owner, "Histórico de Sessões", false);
        this.sessions     = sessions;
        this.studyDataMap = studyDataMap;
        this.onChange     = onChange != null ? onChange : () -> {};

        setSize(560, 560);
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

        refreshList();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(AppTheme.SURFACE);
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.DIVIDER),
                new EmptyBorder(14, 16, 14, 16)));

        JLabel title = new JLabel("Histórico de Sessões");
        title.setFont(AppTheme.FONT_SECTION);
        title.setForeground(AppTheme.TEXT_PRI);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.add(new JLabel("Filtrar:") {{ setFont(AppTheme.FONT_SMALL); setForeground(AppTheme.TEXT_SEC); }});

        List<String> items = new ArrayList<>();
        items.add("Todas");
        items.addAll(studyDataMap.keySet());
        filterCombo = new JComboBox<>(items.toArray(new String[0]));
        filterCombo.setFont(AppTheme.FONT_SMALL);
        filterCombo.addActionListener(e -> refreshList());
        filterRow.add(filterCombo);

        header.add(title, BorderLayout.WEST);
        header.add(filterRow, BorderLayout.EAST);
        return header;
    }

    private void refreshList() {
        String filter = filterCombo == null ? "Todas" : (String) filterCombo.getSelectedItem();
        listPanel.removeAll();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy  HH:mm");
        boolean any = false;

        List<StudySession> view = new ArrayList<>(sessions);
        view.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        for (StudySession s : view) {
            if (!"Todas".equals(filter) && !s.getSubject().equals(filter)) continue;
            any = true;

            RoundedPanel card = new RoundedPanel(12, AppTheme.SURFACE, false);
            card.setLayout(new BorderLayout(10, 0));
            card.setBorder(new EmptyBorder(10, 14, 10, 14));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, s.getNote().isEmpty() ? 74 : 94));

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

            JPanel left = new JPanel();
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setOpaque(false);
            JLabel subj = new JLabel(s.getSubject());
            subj.setFont(AppTheme.FONT_BOLD); subj.setForeground(AppTheme.TEXT_PRI);
            subj.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel date = new JLabel(sdf.format(new Date(s.getTimestamp())));
            date.setFont(AppTheme.FONT_SMALL); date.setForeground(AppTheme.TEXT_SEC);
            date.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(subj); left.add(date);
            if (!s.getNote().isEmpty()) {
                JLabel note = new JLabel("“" + trunc(s.getNote(), 60) + "”");
                note.setFont(AppTheme.FONT_SMALL); note.setForeground(AppTheme.TEXT_SEC);
                note.setAlignmentX(Component.LEFT_ALIGNMENT);
                left.add(note);
            }

            JPanel right = new JPanel();
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
            right.setOpaque(false);
            int h = s.getMinutes() / 60, m = s.getMinutes() % 60;
            String durStr = h > 0 ? String.format("%dh %02dmin", h, m) : String.format("%d min", m);
            JLabel dur = new JLabel(durStr);
            dur.setFont(AppTheme.FONT_BOLD); dur.setForeground(AppTheme.ACCENT);
            dur.setAlignmentX(Component.RIGHT_ALIGNMENT);
            String typeLabel = switch (s.getType()) {
                case "pomodoro" -> "Pomodoro";
                case "manual"   -> "Manual";
                default         -> "Cronômetro";
            };
            if (!s.getKind().isEmpty()) typeLabel += " · " + kindLabel(s.getKind());
            JLabel type = new JLabel(typeLabel);
            type.setFont(AppTheme.FONT_SMALL); type.setForeground(AppTheme.TEXT_SEC);
            type.setAlignmentX(Component.RIGHT_ALIGNMENT);

            JPanel acts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            acts.setOpaque(false);
            acts.setAlignmentX(Component.RIGHT_ALIGNMENT);
            JButton edit = miniBtn("Editar");
            JButton del  = miniBtn("Excluir");
            edit.addActionListener(e -> editSession(s));
            del.addActionListener(e -> deleteSession(s));
            acts.add(edit); acts.add(del);

            right.add(dur); right.add(type); right.add(acts);

            card.add(dot, BorderLayout.WEST);
            card.add(left, BorderLayout.CENTER);
            card.add(right, BorderLayout.EAST);

            listPanel.add(card);
            listPanel.add(Box.createVerticalStrut(6));
        }

        if (!any) {
            JComponent es = AppTheme.emptyState(
                    "Todas".equals(filter) ? "Nenhuma sessão ainda" : "Nada em " + filter,
                    "Registre tempo pelo Cronômetro, Manual ou Pomodoro.");
            es.setAlignmentX(Component.CENTER_ALIGNMENT);
            listPanel.add(es);
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private JButton miniBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(AppTheme.FONT_SMALL);
        b.setMargin(new Insets(1, 6, 1, 6));
        b.setFocusPainted(false);
        return b;
    }

    private static String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    static String kindLabel(String key) {
        return switch (key) {
            case "teoria"     -> "Teoria";
            case "exercicios" -> "Exercícios";
            case "revisao"    -> "Revisão";
            case "outro"      -> "Outro";
            default           -> key;
        };
    }

    private void deleteSession(StudySession s) {
        int r = JOptionPane.showConfirmDialog(this,
                "Excluir esta sessão de " + s.getSubject() + " (" + s.getMinutes() + " min)?\n"
              + "O tempo também será descontado do total da matéria.",
                "Excluir sessão", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        sessions.remove(s);
        adjust(s.getSubject(), -s.getMinutes());
        onChange.run();
        refreshList();
    }

    private void editSession(StudySession s) {
        JComboBox<String> cbSubj = new JComboBox<>(studyDataMap.keySet().toArray(new String[0]));
        cbSubj.setSelectedItem(s.getSubject());
        JSpinner spH = new JSpinner(new SpinnerNumberModel(s.getMinutes() / 60, 0, 48, 1));
        JSpinner spM = new JSpinner(new SpinnerNumberModel(s.getMinutes() % 60, 0, 59, 1));
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        JTextField fDate = new JTextField(sdf.format(new Date(s.getTimestamp())), 14);
        JTextField fNote = new JTextField(s.getNote(), 18);
        String[] kKeys = {"", "teoria", "exercicios", "revisao", "outro"};
        JComboBox<String> cbKind = new JComboBox<>(new String[]{"–", "Teoria", "Exercícios", "Revisão", "Outro"});
        for (int i = 0; i < kKeys.length; i++) if (kKeys[i].equals(s.getKind())) cbKind.setSelectedIndex(i);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4); c.anchor = GridBagConstraints.WEST;
        int row = 0;
        addRow(form, c, row++, "Matéria:", cbSubj);
        JPanel hm = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        hm.add(spH); hm.add(new JLabel("h")); hm.add(spM); hm.add(new JLabel("min"));
        addRow(form, c, row++, "Duração:", hm);
        addRow(form, c, row++, "Data/hora:", fDate);
        addRow(form, c, row++, "Nota:", fNote);
        addRow(form, c, row++, "Tipo:", cbKind);

        int r = JOptionPane.showConfirmDialog(this, form, "Editar sessão",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        int newMin = (int) spH.getValue() * 60 + (int) spM.getValue();
        if (newMin <= 0) { JOptionPane.showMessageDialog(this, "Duração precisa ser maior que zero."); return; }
        String newSubj = (String) cbSubj.getSelectedItem();
        long newTs = s.getTimestamp();
        try { newTs = sdf.parse(fDate.getText().trim()).getTime(); }
        catch (Exception ex) { JOptionPane.showMessageDialog(this, "Data inválida — mantida a original."); }

        int idx = sessions.indexOf(s);
        if (idx < 0) return;
        StudySession ns = new StudySession(newSubj, newMin, newTs, s.getType(),
                fNote.getText().trim(), kKeys[Math.max(0, cbKind.getSelectedIndex())]);
        sessions.set(idx, ns);

        // ajusta totais das matérias
        adjust(s.getSubject(), -s.getMinutes());
        adjust(newSubj, newMin);

        onChange.run();
        refreshList();
    }

    /** Soma delta minutos ao total da matéria (nunca abaixo de zero). */
    private void adjust(String subject, int delta) {
        StudyData sd = studyDataMap.get(subject);
        if (sd != null) sd.setMinutes(Math.max(0, sd.getMinutes() + delta));
    }

    private static void addRow(JPanel p, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label); l.setFont(AppTheme.FONT_LABEL);
        p.add(l, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(field, c);
    }
}
