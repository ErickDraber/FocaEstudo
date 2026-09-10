import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Janela de tarefas/checklist por matéria. */
public class ChecklistPanel extends JDialog {

    private final List<ChecklistItem> items;
    private final Map<String, StudyData> studyDataMap;
    private final Runnable onChange;
    private JPanel listPanel;
    private JComboBox<String> filterCombo;
    private JTextField addText;
    private JSpinner addEst;

    public ChecklistPanel(JFrame owner, List<ChecklistItem> items,
                          Map<String, StudyData> studyDataMap, Runnable onChange) {
        super(owner, "Tarefas", false);
        this.items = items;
        this.studyDataMap = studyDataMap;
        this.onChange = onChange != null ? onChange : () -> {};

        setSize(520, 560);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(AppTheme.BG);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(AppTheme.BG);
        listPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JScrollPane sc = new JScrollPane(listPanel);
        sc.setBorder(null);
        sc.getViewport().setBackground(AppTheme.BG);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        add(sc, BorderLayout.CENTER);

        add(buildAddRow(), BorderLayout.SOUTH);
        refresh();
    }

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout(12, 0));
        h.setBackground(AppTheme.SURFACE);
        h.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.DIVIDER),
                new EmptyBorder(14, 16, 14, 16)));
        JLabel title = new JLabel("Tarefas por matéria");
        title.setFont(AppTheme.FONT_SECTION); title.setForeground(AppTheme.TEXT_PRI);

        JPanel fr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        fr.setOpaque(false);
        fr.add(new JLabel("Matéria:") {{ setFont(AppTheme.FONT_SMALL); setForeground(AppTheme.TEXT_SEC); }});
        List<String> its = new ArrayList<>(studyDataMap.keySet());
        filterCombo = new JComboBox<>(its.toArray(new String[0]));
        filterCombo.setFont(AppTheme.FONT_SMALL);
        filterCombo.addActionListener(e -> refresh());
        fr.add(filterCombo);

        h.add(title, BorderLayout.WEST);
        h.add(fr, BorderLayout.EAST);
        return h;
    }

    private JPanel buildAddRow() {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(AppTheme.SURFACE);
        p.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.DIVIDER),
                new EmptyBorder(10, 12, 10, 12)));
        addText = new JTextField();
        addText.setToolTipText("Nova tarefa (ex.: \"Lista 4 - integrais\")");
        addEst = new JSpinner(new SpinnerNumberModel(0, 0, 600, 15));
        addEst.setToolTipText("Estimativa em minutos (0 = sem)");
        ((JSpinner.DefaultEditor) addEst.getEditor()).getTextField().setColumns(4);
        JButton add = new JButton("Adicionar");
        add.setFocusPainted(false);
        Runnable doAdd = () -> {
            String t = addText.getText().trim();
            String subj = (String) filterCombo.getSelectedItem();
            if (t.isEmpty() || subj == null) return;
            items.add(new ChecklistItem(subj, t, (int) addEst.getValue(), false));
            addText.setText(""); addEst.setValue(0);
            onChange.run(); refresh();
        };
        add.addActionListener(e -> doAdd.run());
        addText.addActionListener(e -> doAdd.run());

        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rightBox.setOpaque(false);
        rightBox.add(new JLabel("min:") {{ setFont(AppTheme.FONT_SMALL); setForeground(AppTheme.TEXT_SEC); }});
        rightBox.add(addEst);
        rightBox.add(add);

        p.add(addText, BorderLayout.CENTER);
        p.add(rightBox, BorderLayout.EAST);
        return p;
    }

    private void refresh() {
        listPanel.removeAll();
        String subj = (String) filterCombo.getSelectedItem();
        int pend = 0, pendMin = 0;
        boolean any = false;

        for (ChecklistItem it : new ArrayList<>(items)) {
            if (!it.subject.equals(subj)) continue;
            any = true;
            if (!it.done) { pend++; pendMin += it.estMin; }

            RoundedPanel card = new RoundedPanel(10, AppTheme.SURFACE, false);
            card.setLayout(new BorderLayout(8, 0));
            card.setBorder(new EmptyBorder(6, 12, 6, 12));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

            JCheckBox cb = new JCheckBox("", it.done);
            cb.setOpaque(false);
            cb.addActionListener(e -> { it.done = cb.isSelected(); onChange.run(); refresh(); });

            JLabel txt = new JLabel(it.text + (it.estMin > 0 ? "  (" + fmt(it.estMin) + ")" : ""));
            txt.setFont(AppTheme.FONT_LABEL);
            txt.setForeground(it.done ? AppTheme.TEXT_SEC : AppTheme.TEXT_PRI);
            if (it.done) txt.setText("<html><strike>" + escape(txt.getText()) + "</strike></html>");

            JButton del = new JButton("✕");
            del.setMargin(new Insets(0, 6, 0, 6));
            del.setFocusPainted(false);
            del.setToolTipText("Remover");
            del.addActionListener(e -> { items.remove(it); onChange.run(); refresh(); });

            card.add(cb, BorderLayout.WEST);
            card.add(txt, BorderLayout.CENTER);
            card.add(del, BorderLayout.EAST);
            listPanel.add(card);
            listPanel.add(Box.createVerticalStrut(5));
        }

        if (!any) {
            JComponent es = AppTheme.emptyState("Nenhuma tarefa em " + subj,
                    "Adicione a primeira no campo abaixo ↓");
            es.setAlignmentX(Component.CENTER_ALIGNMENT);
            listPanel.add(es);
        } else {
            JLabel resumo = new JLabel(pend == 0 ? "Tudo feito! 🎉"
                    : pend + " pendente(s)" + (pendMin > 0 ? "  ·  ~" + fmt(pendMin) + " estimados" : ""));
            resumo.setFont(AppTheme.FONT_SMALL); resumo.setForeground(AppTheme.TEXT_SEC);
            resumo.setAlignmentX(Component.CENTER_ALIGNMENT);
            resumo.setBorder(new EmptyBorder(8, 0, 0, 0));
            listPanel.add(resumo);
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private static String fmt(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return h > 0 ? h + "h" + String.format("%02d", m) : m + "min";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
