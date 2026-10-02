import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Grade da semana: Seg–Dom × Manhã/Tarde/Noite. Cada turno pode ter várias áreas.
 * Clique num turno para marcar/desmarcar áreas. O dia de hoje fica no card "Hoje" da tela principal;
 * esta janela é só para editar.
 */
public class PlannerPanel extends JDialog {

    static final String[] DOW        = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
    static final String[] DOW_LONG   = {"segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo"};
    static final String[] SLOT       = {"m", "t", "n"};
    static final String[] SLOT_LABEL = {"Manhã", "Tarde", "Noite"};
    static final String[] SLOT_PHRASE = {"desta manhã", "desta tarde", "desta noite"};

    /** Turno de um horário: manhã 5h–12h, tarde 12h–18h, noite 18h–5h. */
    static int slotAt(java.time.LocalTime t) {
        int h = t.getHour();
        return h >= 5 && h < 12 ? 0 : h >= 12 && h < 18 ? 1 : 2;
    }

    private final List<String> areas;
    private final Map<String, Color> colors;
    private final Map<String, List<String>> plan;   // "1_m" -> áreas, na ordem em que foram postas
    private final Runnable onChange;
    private final Runnable onAutofill;
    private final JPanel grid;

    public PlannerPanel(JFrame owner, List<String> areas, Map<String, Color> colors,
                        Map<String, List<String>> plan, Runnable onChange, Runnable onAutofill) {
        super(owner, "Semana", false);
        this.areas = areas;
        this.colors = colors;
        this.plan = plan;
        this.onChange = onChange != null ? onChange : () -> {};
        this.onAutofill = onAutofill;

        setSize(820, 460);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(AppTheme.BG);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(), BorderLayout.NORTH);

        grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(12, 14, 14, 14));
        add(grid, BorderLayout.CENTER);

        rebuild();
    }

    private JComponent buildHeader() {
        JPanel h = new JPanel(new BorderLayout(12, 0));
        h.setBackground(AppTheme.SURFACE);
        h.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.DIVIDER),
                new EmptyBorder(12, 16, 12, 16)));

        JPanel txt = new JPanel(new GridLayout(2, 1, 0, 2));
        txt.setOpaque(false);
        JLabel t = new JLabel("Semana");
        t.setFont(AppTheme.FONT_SECTION); t.setForeground(AppTheme.TEXT_PRI);
        JLabel hint = new JLabel("Clique num turno para escolher as áreas. Pode ter mais de uma.");
        hint.setFont(AppTheme.FONT_SMALL); hint.setForeground(AppTheme.TEXT_SEC);
        txt.add(t); txt.add(hint);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        JButton fill = new JButton("Preencher pelas metas");
        fill.setFont(AppTheme.FONT_SMALL); fill.setFocusPainted(false);
        fill.setToolTipText("Põe cada área com meta diária nos dias da meta, sem repetir no mesmo dia");
        fill.setEnabled(onAutofill != null);
        fill.addActionListener(e -> { if (onAutofill != null) onAutofill.run(); rebuild(); });
        JButton clr = new JButton("Limpar tudo");
        clr.setFont(AppTheme.FONT_SMALL); clr.setFocusPainted(false);
        clr.addActionListener(e -> {
            if (plan.isEmpty()) return;
            int r = JOptionPane.showConfirmDialog(this, "Tirar todas as áreas da semana?",
                    "Limpar semana", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;
            plan.clear(); onChange.run(); rebuild();
        });
        btns.add(fill); btns.add(clr);

        h.add(txt, BorderLayout.WEST);
        h.add(btns, BorderLayout.EAST);
        return h;
    }

    void rebuild() {
        grid.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(2, 2, 2, 2);

        int hoje = java.time.LocalDate.now().getDayOfWeek().getValue();
        int agora = slotAt(java.time.LocalTime.now());

        c.gridx = 0; c.gridy = 0; c.weightx = 0; c.weighty = 0;
        grid.add(headLabel("", false), c);
        for (int d = 0; d < 7; d++) {
            c.gridx = d + 1; c.weightx = 1;
            grid.add(headLabel(DOW[d], d + 1 == hoje), c);
        }
        for (int s = 0; s < 3; s++) {
            c.gridx = 0; c.gridy = s + 1; c.weightx = 0; c.weighty = 1;
            grid.add(headLabel(SLOT_LABEL[s], false), c);
            for (int d = 0; d < 7; d++) {
                c.gridx = d + 1; c.weightx = 1;
                grid.add(cell((d + 1) + "_" + SLOT[s], d + 1 == hoje && s == agora), c);
            }
        }
        grid.revalidate();
        grid.repaint();
    }

    private JComponent headLabel(String txt, boolean hoje) {
        JLabel l = new JLabel(hoje ? txt + " · hoje" : txt, SwingConstants.CENTER);
        l.setFont(hoje ? AppTheme.FONT_BOLD : AppTheme.FONT_SMALL);
        l.setForeground(hoje ? AppTheme.ACCENT : AppTheme.TEXT_SEC);
        return l;
    }

    private List<String> cellAreas(String key) {
        List<String> out = new ArrayList<>();
        for (String a : plan.getOrDefault(key, Collections.emptyList())) if (areas.contains(a)) out.add(a);
        return out;
    }

    private JComponent cell(String key, boolean agora) {
        List<String> noTurno = cellAreas(key);

        RoundedPanel box = new RoundedPanel(10, AppTheme.SURFACE2, false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(agora ? new CompoundBorder(new LineBorder(AppTheme.ACCENT, 2, true), new EmptyBorder(4, 6, 4, 6))
                            : new EmptyBorder(6, 8, 6, 8));
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (noTurno.isEmpty()) {
            JLabel plus = new JLabel("+");
            plus.setFont(AppTheme.FONT_LABEL); plus.setForeground(AppTheme.TEXT_MUT);
            box.add(plus);
        }
        for (String a : noTurno) {
            JLabel l = new JLabel(a, dot(colors.getOrDefault(a, AppTheme.ACCENT)), SwingConstants.LEFT);
            l.setFont(AppTheme.FONT_SMALL);
            l.setForeground(AppTheme.TEXT_PRI);
            l.setIconTextGap(5);
            box.add(l);
        }
        box.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { showCellMenu(box, key); }
        });
        return box;
    }

    private void showCellMenu(JComponent anchor, String key) {
        JPopupMenu m = new JPopupMenu();
        List<String> atuais = cellAreas(key);
        for (String a : areas) {
            JCheckBoxMenuItem mi = new JCheckBoxMenuItem(a, atuais.contains(a));
            mi.setIcon(dot(colors.getOrDefault(a, AppTheme.ACCENT)));
            mi.addActionListener(x -> {
                List<String> l = plan.computeIfAbsent(key, k -> new ArrayList<>());
                if (!l.remove(a)) l.add(a);
                if (l.isEmpty()) plan.remove(key);
                onChange.run(); rebuild();
            });
            m.add(mi);
        }
        if (!atuais.isEmpty()) {
            m.addSeparator();
            JMenuItem clr = new JMenuItem("Limpar turno");
            clr.addActionListener(x -> { plan.remove(key); onChange.run(); rebuild(); });
            m.add(clr);
        }
        m.show(anchor, 0, anchor.getHeight());
    }

    /** Bolinha na cor da área (ícone, para não depender de HTML no texto). */
    static Icon dot(Color c) {
        return new Icon() {
            public int getIconWidth()  { return 9; }
            public int getIconHeight() { return 9; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c); g2.fillOval(x, y, 9, 9); g2.dispose();
            }
        };
    }
}
