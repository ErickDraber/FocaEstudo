import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Planejador semanal simples: grade Seg–Dom × Manhã/Tarde/Noite. Clique numa célula p/ escolher a área. */
public class PlannerPanel extends JDialog {

    static final String[] DOW  = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
    static final String[] SLOT = {"m", "t", "n"};
    static final String[] SLOT_LABEL = {"Manhã", "Tarde", "Noite"};

    private final List<String> areas;
    private final Map<String, Color> colors;
    private final Map<String, String> plan;   // "1_m" -> nome da área
    private final Runnable onChange;
    private final Runnable onAutofill;
    private JPanel grid;

    public PlannerPanel(JFrame owner, List<String> areas, Map<String, Color> colors,
                        Map<String, String> plan, Runnable onChange, Runnable onAutofill) {
        super(owner, "Planejador da semana", false);
        this.areas = areas;
        this.colors = colors;
        this.plan = plan;
        this.onChange = onChange != null ? onChange : () -> {};
        this.onAutofill = onAutofill;

        setSize(720, 420);
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
        JLabel t = new JLabel("Planejador da semana");
        t.setFont(AppTheme.FONT_SECTION); t.setForeground(AppTheme.TEXT_PRI);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        JButton fill = new JButton("Preencher pelos dias da meta");
        fill.setFont(AppTheme.FONT_SMALL); fill.setFocusPainted(false);
        fill.setEnabled(onAutofill != null);
        fill.addActionListener(e -> { if (onAutofill != null) onAutofill.run(); rebuild(); });
        JButton clr = new JButton("Limpar tudo");
        clr.setFont(AppTheme.FONT_SMALL); clr.setFocusPainted(false);
        clr.addActionListener(e -> { plan.clear(); onChange.run(); rebuild(); });
        btns.add(fill); btns.add(clr);

        h.add(t, BorderLayout.WEST);
        h.add(btns, BorderLayout.EAST);
        return h;
    }

    void rebuild() {
        grid.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(2, 2, 2, 2);
        c.weightx = 1; c.weighty = 1;

        // canto vazio
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        grid.add(cornerLabel(""), c);
        // cabeçalho dos dias
        LocalDateHelper today = new LocalDateHelper();
        for (int d = 0; d < 7; d++) {
            c.gridx = d + 1; c.gridy = 0; c.weightx = 1;
            grid.add(cornerLabel(DOW[d] + (today.isTodayDow(d + 1) ? " •" : "")), c);
        }
        // linhas dos slots
        for (int s = 0; s < 3; s++) {
            c.gridx = 0; c.gridy = s + 1; c.weightx = 0;
            grid.add(cornerLabel(SLOT_LABEL[s]), c);
            for (int d = 0; d < 7; d++) {
                c.gridx = d + 1; c.gridy = s + 1; c.weightx = 1;
                grid.add(cell((d + 1) + "_" + SLOT[s]), c);
            }
        }
        grid.revalidate();
        grid.repaint();
    }

    private JComponent cornerLabel(String txt) {
        JLabel l = new JLabel(txt, SwingConstants.CENTER);
        l.setFont(AppTheme.FONT_SMALL);
        l.setForeground(AppTheme.TEXT_SEC);
        return l;
    }

    private JComponent cell(String key) {
        String area = plan.get(key);
        final boolean filled = area != null && areas.contains(area);
        Color base = filled ? colors.getOrDefault(area, AppTheme.ACCENT) : AppTheme.SURFACE2;

        JButton b = new JButton(filled ? area : "+");
        b.setFont(filled ? AppTheme.FONT_SMALL : AppTheme.FONT_LABEL);
        b.setForeground(filled ? Color.WHITE : AppTheme.TEXT_MUT);
        b.setBackground(base);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(6, 6, 6, 6));
        b.setOpaque(true);
        b.addActionListener(e -> {
            JPopupMenu m = new JPopupMenu();
            for (String a : areas) {
                JMenuItem mi = new JMenuItem(a);
                mi.addActionListener(x -> { plan.put(key, a); onChange.run(); rebuild(); });
                m.add(mi);
            }
            if (filled) {
                m.addSeparator();
                JMenuItem clr = new JMenuItem("Limpar");
                clr.addActionListener(x -> { plan.remove(key); onChange.run(); rebuild(); });
                m.add(clr);
            }
            m.show(b, 0, b.getHeight());
        });
        return b;
    }

    /** Evita depender de import direto de java.time no cabeçalho da classe. */
    private static final class LocalDateHelper {
        private final int todayDow = java.time.LocalDate.now().getDayOfWeek().getValue();
        boolean isTodayDow(int isoDow) { return isoDow == todayDow; }
    }
}
