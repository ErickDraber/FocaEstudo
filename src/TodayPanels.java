import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.List;

/**
 * As partes da tela Hoje que mostram o dia e a semana (o cartão "Agora" é o NowCard e as abas do
 * cronômetro ficam no StudyTracker):
 * <ul>
 *   <li>{@link #header()}: "Quarta-feira, 18 de novembro" + "N dos últimos 7 dias";</li>
 *   <li>{@link #shiftsCard()}: "Turnos de hoje" (manhã/tarde/noite com as áreas planejadas);</li>
 *   <li>{@link #goalsCard()}: "Metas de hoje" (só as áreas com meta hoje, com barra);</li>
 *   <li>{@link #weekStrip()}: "Esta semana" (o plano da semana à vista, sem abrir a grade).</li>
 * </ul>
 * O StudyTracker posiciona cada uma na tela; {@link #refresh()} atualiza as quatro juntas.
 */
final class TodayPanels {

    /** De onde lê. Quem implementa é o StudyTracker. */
    interface Source {
        Map<String, StudyData> areas();
        Set<String> archived();
        String typeOf(String area);
        StudyStats stats();
        /** Meta diária IDEAL definida na área (0 = sem meta). */
        int idealDaily(String area);
        WeekPlan plan();
    }

    interface Actions {
        void manageGoals();
        /** Abre a tela Semana. */
        void openPlanner();
        /** "Preencher pelas metas". */
        void fillPlan();
        /** Clique numa área do turno: começa o cronômetro nela. */
        void start(String area);
    }

    private final Source src;
    private final Actions actions;
    private final JPanel header, shiftsCard, goalsCard, weekStrip;
    private JLabel dateLabel, consistency;
    private JPanel shiftsBody, goalsBody, weekBody;

    TodayPanels(Source src, Actions actions) {
        this.src = src; this.actions = actions;
        header     = buildHeader();
        shiftsCard = buildShiftsCard();
        goalsCard  = buildGoalsCard();
        weekStrip  = buildWeekStrip();
    }

    JPanel header()     { return header; }
    JPanel shiftsCard() { return shiftsCard; }
    JPanel goalsCard()  { return goalsCard; }
    JPanel weekStrip()  { return weekStrip; }

    /** Atualiza cabeçalho, metas de hoje, faixa da semana e turnos (nessa ordem, como antes). */
    void refresh() {
        refreshHeader();
        refreshGoals();
        refreshWeek();
        refreshShifts();
    }

    /** Cartão com título à esquerda e botão de texto opcional à direita. */
    private static RoundedPanel card(String titulo, String botao, Runnable acao, JPanel body, int padV) {
        RoundedPanel card = new RoundedPanel(18, AppTheme.SURFACE);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(padV, 16, padV, 16));
        JLabel title = new JLabel(titulo);
        title.setFont(AppTheme.FONT_SECTION);
        title.setForeground(AppTheme.TEXT_PRI);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(title, BorderLayout.WEST);
        if (botao != null) {
            StyledButton b = new StyledButton(botao, StyledButton.Variant.TEXT);
            b.setFont(AppTheme.FONT_SMALL);
            b.addActionListener(e -> acao.run());
            head.add(b, BorderLayout.EAST);
        }
        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private static JPanel boxY() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    // ── CABEÇALHO ───────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        dateLabel = new JLabel(" ");
        dateLabel.setFont(AppTheme.FONT_SECTION);
        dateLabel.setForeground(AppTheme.TEXT_PRI);
        consistency = new JLabel(" ");
        consistency.setFont(AppTheme.FONT_SMALL);
        consistency.setToolTipText("Dias com algum tempo registrado (ou marcados como difíceis). Não zera: só olha a última semana.");
        RoundedPanel pill = new RoundedPanel(12, AppTheme.SURFACE2, false);
        pill.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        pill.setBorder(new EmptyBorder(4, 12, 4, 12));
        pill.add(consistency);

        JPanel h = new JPanel(new BorderLayout(12, 0));
        h.setBackground(AppTheme.SURFACE);
        h.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.DIVIDER),
                new EmptyBorder(12, 16, 12, 16)));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(pill);
        h.add(dateLabel, BorderLayout.WEST);
        h.add(right,     BorderLayout.EAST);
        return h;
    }

    /** "Sábado, 3 de outubro". */
    static String dateText(LocalDate d) {
        String s = d.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR")));
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Dias com tempo registrado (ou difíceis) nos últimos 7 dias, contando hoje (janela móvel: nunca zera de uma vez). */
    int activeDaysLast7(LocalDate hoje) {
        StudyStats st = src.stats();
        int dias = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate dd = hoje.minusDays(i);
            if (st.isHardDay(dd) || st.minutesOnDay(null, dd) > 0) dias++;
        }
        return dias;
    }

    private void refreshHeader() {
        LocalDate hoje = LocalDate.now();
        dateLabel.setText(dateText(hoje));
        int dias = activeDaysLast7(hoje);
        consistency.setText(dias == 0 ? "Um bloco hoje já conta" : dias + " dos últimos 7 dias");
        consistency.setForeground(dias == 0 ? AppTheme.TEXT_SEC : AppTheme.SUCCESS);
    }

    // ── METAS DE HOJE: só as áreas com meta hoje, com barra ────────────────

    private JPanel buildGoalsCard() {
        goalsBody = boxY();
        return card("Metas de hoje", "editar metas", actions::manageGoals, goalsBody, 14);
    }

    private void refreshGoals() {
        goalsBody.removeAll();
        StudyStats st = src.stats();
        LocalDate hoje = LocalDate.now();
        boolean any = false;
        if (!st.isHardDay(hoje))   // dia difícil: nenhuma barra cobrando
        for (String t : AreaTypes.KEYS)
            for (String a : src.areas().keySet()) {
                if (src.archived().contains(a) || !src.typeOf(a).equals(t)) continue;
                int ideal = src.idealDaily(a);
                if (ideal <= 0 || !st.isGoalDay(a, hoje)) continue;
                if (any) goalsBody.add(Box.createVerticalStrut(8));
                any = true;
                goalsBody.add(goalRow(a, st.minutesOnDay(a, hoje), st.dailyMinFor(a), ideal));
            }
        if (!any) {
            JLabel l = new JLabel(st.isHardDay(hoje) ? "Dia difícil: nada é cobrado hoje." : "Nenhuma meta para hoje.");
            l.setFont(AppTheme.FONT_SMALL); l.setForeground(AppTheme.TEXT_SEC);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            goalsBody.add(l);
        }
        goalsBody.revalidate();
        goalsBody.repaint();
    }

    /** "feito", "40m · mínimo feito" ou "40m / 1h30". */
    static String goalText(int feito, int minimo, int ideal) {
        return feito >= ideal ? "feito"
             : feito >= minimo ? ScreenKit.fmtHM(feito) + " · mínimo feito"
             : ScreenKit.fmtHM(feito) + " / " + ScreenKit.fmtHM(ideal);
    }

    /** Nome · texto da meta e a barra na cor da área. */
    private JComponent goalRow(String area, int feito, int minimo, int ideal) {
        Color cor = src.areas().get(area).getColor();
        JPanel row = new JPanel(new BorderLayout(0, 3));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nome = new JLabel(area, PlannerPanel.dot(cor), SwingConstants.LEFT);
        nome.setIconTextGap(6);
        nome.setFont(AppTheme.FONT_LABEL);
        nome.setForeground(AppTheme.TEXT_PRI);
        JLabel val = new JLabel(goalText(feito, minimo, ideal));
        val.setFont(AppTheme.FONT_SMALL);
        val.setForeground(feito >= minimo ? AppTheme.SUCCESS : AppTheme.TEXT_SEC);
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.add(nome, BorderLayout.WEST);
        top.add(val,  BorderLayout.EAST);

        GaugeBar bar = new GaugeBar();
        bar.setPreferredSize(new Dimension(0, 8));
        bar.set(feito / (double) ideal, cor, feito >= ideal);
        row.add(top, BorderLayout.NORTH);
        row.add(bar, BorderLayout.CENTER);
        return row;
    }

    // ── ESTA SEMANA: o plano da semana à vista, sem abrir a grade ──────────

    private JPanel buildWeekStrip() {
        weekBody = new JPanel(new GridLayout(1, 7, 6, 0));
        weekBody.setOpaque(false);
        return card("Esta semana", "editar semana", actions::openPlanner, weekBody, 12);
    }

    private void refreshWeek() {
        weekBody.removeAll();
        StudyStats st = src.stats();
        LocalDate hoje = LocalDate.now(), seg = StudyStats.weekStart(hoje);
        for (int d = 1; d <= 7; d++) {
            LocalDate dia = seg.plusDays(d - 1);
            boolean eHoje = dia.equals(hoje);
            JPanel col = eHoje ? new RoundedPanel(10, AppTheme.SURFACE2, false) : new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            col.setBorder(new EmptyBorder(4, 4, 6, 4));
            col.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            col.setToolTipText("Abrir a semana");
            col.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { actions.openPlanner(); }
            });

            JLabel nome = new JLabel(PlannerPanel.DOW[d - 1], SwingConstants.CENTER);
            nome.setFont(eHoje ? AppTheme.FONT_BOLD : AppTheme.FONT_SMALL);
            nome.setForeground(eHoje ? AppTheme.ACCENT : AppTheme.TEXT_SEC);
            nome.setAlignmentX(Component.CENTER_ALIGNMENT);
            col.add(nome);
            col.add(Box.createVerticalStrut(4));

            // Bolinha cheia = já fez algo dessa área no dia; anel = planejado (ainda não).
            JPanel dots = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 1));
            dots.setOpaque(false);
            LinkedHashSet<String> doDia = new LinkedHashSet<>();
            for (int s = 0; s < 3; s++) doDia.addAll(src.plan().planned(d, s));
            for (String a : doDia) {
                boolean fez = !dia.isAfter(hoje) && st.minutesOnDay(a, dia) > 0;
                JLabel dot = new JLabel(dayDot(src.areas().get(a).getColor(), fez));
                dot.setToolTipText(a + (fez ? " · feito" : ""));
                dots.add(dot);
            }
            if (doDia.isEmpty()) {
                JLabel l = new JLabel("livre");
                l.setFont(AppTheme.FONT_CAPTION); l.setForeground(AppTheme.TEXT_MUT);
                dots.add(l);
            }
            dots.setAlignmentX(Component.CENTER_ALIGNMENT);
            col.add(dots);
            weekBody.add(col);
        }
        weekBody.revalidate();
        weekBody.repaint();
    }

    private static Icon dayDot(Color c, boolean cheio) {
        return new Icon() {
            public int getIconWidth()  { return 10; }
            public int getIconHeight() { return 10; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c);
                if (cheio) g2.fillOval(x, y, 10, 10);
                else { g2.setStroke(new BasicStroke(1.6f)); g2.drawOval(x + 1, y + 1, 8, 8); }
                g2.dispose();
            }
        };
    }

    // ── TURNOS DE HOJE ──────────────────────────────────────────────────────

    private JPanel buildShiftsCard() {
        shiftsBody = boxY();
        return card("Turnos de hoje", null, null, shiftsBody, 14);
    }

    private void refreshShifts() {
        shiftsBody.removeAll();
        LocalDate hoje = LocalDate.now();
        int dow = hoje.getDayOfWeek().getValue();
        int agora = PlannerPanel.slotAt(LocalTime.now());

        if (src.plan().dayIsEmpty(dow)) {
            JPanel p = new JPanel(new BorderLayout(10, 0));
            p.setOpaque(false);
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel l = new JLabel("Nada planejado para hoje.");
            l.setFont(AppTheme.FONT_LABEL); l.setForeground(AppTheme.TEXT_SEC);
            StyledButton fill = new StyledButton("Preencher pelas metas", StyledButton.Variant.TONAL);
            fill.setFont(AppTheme.FONT_SMALL);
            fill.addActionListener(e -> actions.fillPlan());
            p.add(l, BorderLayout.CENTER);
            p.add(fill, BorderLayout.EAST);
            shiftsBody.add(p);
        } else {
            for (int s = 0; s < 3; s++) {
                if (s > 0) shiftsBody.add(Box.createVerticalStrut(4));
                shiftsBody.add(shiftRow(dow, s, agora));
            }
        }
        shiftsBody.revalidate();
        shiftsBody.repaint();
    }

    private JComponent shiftRow(int dow, int turno, int agora) {
        boolean atual = turno == agora, passou = turno < agora;
        List<String> areas = src.plan().planned(dow, turno);

        JPanel row = atual ? new RoundedPanel(10, AppTheme.SURFACE2, false) : new JPanel();
        row.setLayout(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(4, 8, 4, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nome = new JLabel(PlannerPanel.SLOT_LABEL[turno] + (atual ? " · agora" : ""));
        nome.setFont(atual ? AppTheme.FONT_BOLD : AppTheme.FONT_SMALL);
        nome.setForeground(atual ? AppTheme.ACCENT : passou ? AppTheme.TEXT_MUT : AppTheme.TEXT_SEC);
        nome.setPreferredSize(new Dimension(96, 22));
        row.add(nome, BorderLayout.WEST);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        chips.setOpaque(false);
        if (areas.isEmpty()) {
            JLabel l = new JLabel("livre");
            l.setFont(AppTheme.FONT_SMALL); l.setForeground(AppTheme.TEXT_MUT);
            chips.add(l);
        }
        for (String a : areas) chips.add(chip(a, src.stats().isDoneToday(a), passou));
        row.add(chips, BorderLayout.CENTER);
        return row;
    }

    /** Nome da área com a bolinha da cor ("· feito" se já bateu o mínimo). Clicar começa o cronômetro nela. */
    private JComponent chip(String area, boolean feito, boolean passou) {
        StudyData sd = src.areas().get(area);
        JLabel l = new JLabel(area);
        l.setFont(AppTheme.FONT_LABEL);
        if (feito) {
            l.setText(area + " · feito");   // sem ✓: a fonte do Swing no Windows mostra um quadrado
            l.setForeground(AppTheme.SUCCESS);
        } else {
            l.setIcon(PlannerPanel.dot(sd != null ? sd.getColor() : AppTheme.ACCENT));
            l.setIconTextGap(5);
            l.setForeground(passou ? AppTheme.TEXT_SEC : AppTheme.TEXT_PRI);
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.setToolTipText("Começar " + area + " agora");
            l.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { actions.start(area); }
            });
        }
        return l;
    }
}
