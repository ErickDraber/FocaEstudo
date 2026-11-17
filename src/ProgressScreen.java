import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * Tela Progresso: donut por tipo de área, cartões Progresso / Metas / Equilíbrio e o calendário.
 *
 * A tela só MOSTRA: lê os dados pela {@link Source} e repassa os botões pela {@link Actions}
 * (os diálogos ficam no StudyTracker). {@link #refresh()} remonta tudo a partir do estado atual.
 */
final class ProgressScreen extends JPanel {

    /** De onde a tela lê. Quem implementa é o StudyTracker (repasses de uma linha). */
    interface Source {
        Map<String, StudyData> areas();
        Set<String> archived();
        String typeOf(String area);
        StudyStats stats();
        /** Recorde de sequência; area == null ⇒ geral. */
        int bestStreak(String area);
        int minutesByType(String type, LocalDate start, LocalDate end);
        /** Meta semanal de equilíbrio do tipo (0 = sem meta). */
        int typeGoalWeek(String type);
        /** Meta diária IDEAL definida na área (0 = sem meta). */
        int idealDaily(String area);
        /** Sub-focos da área (pode ser null). */
        List<String> subFocos(String area);
        LocalDate examDate(String area);
        List<ChecklistItem> checklist();
        /** Área escolhida no combo da tela Hoje (pode ser null). */
        String selectedArea();
        List<StudySession> sessions();
        Map<String, Integer> goals();
    }

    /** O que cada botão faz. Quem implementa é o StudyTracker. */
    interface Actions {
        void history();
        void stats();
        void manageGoals();
        void typeGoals();
    }

    private final Source src;
    private final PieChartPanel chartPanel;
    private final CalendarPanel calendarPanel;
    private JPanel progressBody, goalsBody, balanceBody;

    ProgressScreen(Source src, Actions actions) {
        super(new BorderLayout());
        this.src = src;
        StyledButton hist = new StyledButton("Histórico", StyledButton.Variant.TEXT);
        hist.addActionListener(e -> actions.history());
        StyledButton stats = new StyledButton("Estatísticas", StyledButton.Variant.TEXT);
        stats.addActionListener(e -> actions.stats());

        chartPanel = new PieChartPanel();
        calendarPanel = new CalendarPanel(src.sessions(), src.goals(), colorMap());

        setBackground(AppTheme.BG);
        add(ScreenKit.screenHeader("Progresso", null, hist, stats), BorderLayout.NORTH);
        add(buildCenter(actions), BorderLayout.CENTER);
    }

    /** Remonta donut, cores do calendário e os três cartões. */
    void refresh() {
        chartPanel.setGroups(pieGroups(src.areas(), src::typeOf));
        calendarPanel.updateColors(colorMap());
        refreshBalanceCard();
        refreshGoalsCard();
        refreshProgressCard();
    }

    private Map<String, Color> colorMap() {
        Map<String, Color> colorMap = new LinkedHashMap<>();
        src.areas().forEach((k, v) -> colorMap.put(k, v.getColor()));
        return colorMap;
    }

    private JSplitPane buildCenter(Actions actions) {
        // ── Lado esquerdo: gráfico donut (topo) + progresso (base) ────────────
        // Antes o progresso ia no BorderLayout.SOUTH e, com várias matérias,
        // espremia o donut até ele sumir. Agora os dois ficam num split vertical
        // que garante espaço ao gráfico e deixa o usuário ajustar a divisão.
        JPanel chartWrapper = new JPanel(new BorderLayout());
        chartWrapper.setBackground(AppTheme.BG);
        chartWrapper.setBorder(new EmptyBorder(12, 12, 6, 6));
        chartPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        RoundedPanel chartCard = new RoundedPanel(18, AppTheme.SURFACE);
        chartCard.setLayout(new BorderLayout());
        chartCard.add(chartPanel);
        chartCard.setMinimumSize(new Dimension(160, 200));

        JPanel leftBottom = new JPanel();
        leftBottom.setLayout(new BoxLayout(leftBottom, BoxLayout.Y_AXIS));
        leftBottom.setOpaque(false);
        // Visão de longo prazo: números do período, metas completas (dia/semana/mês) e equilíbrio.
        JComponent pc = buildProgressCard();                    pc.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent gc = buildGoalsCard(actions::manageGoals);   gc.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent bc = buildBalanceCard(actions::typeGoals);   bc.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftBottom.add(pc);
        leftBottom.add(Box.createVerticalStrut(12));
        leftBottom.add(gc);
        leftBottom.add(Box.createVerticalStrut(12));
        leftBottom.add(bc);
        leftBottom.add(Box.createVerticalGlue());

        JScrollPane progressScroll = new JScrollPane(leftBottom,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        progressScroll.setBorder(null);
        progressScroll.setOpaque(false);
        progressScroll.getViewport().setOpaque(false);
        progressScroll.getVerticalScrollBar().setUnitIncrement(12);
        progressScroll.setMinimumSize(new Dimension(0, 90));

        JPanel progressHolder = new JPanel(new BorderLayout());
        progressHolder.setOpaque(false);
        progressHolder.setBorder(new EmptyBorder(12, 0, 0, 0));
        progressHolder.add(progressScroll, BorderLayout.CENTER);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartCard, progressHolder);
        leftSplit.setResizeWeight(0.36);   // gráfico em cima, progresso+metas+equilíbrio embaixo (com scroll)
        leftSplit.setDividerLocation(0.36);
        leftSplit.setDividerSize(4);
        leftSplit.setBorder(null);
        leftSplit.setOpaque(false);
        chartWrapper.add(leftSplit, BorderLayout.CENTER);

        // Lado direito: calendário
        JPanel calWrapper = new JPanel(new BorderLayout());
        calWrapper.setBackground(AppTheme.BG);
        calWrapper.setBorder(new EmptyBorder(12, 6, 6, 12));
        RoundedPanel calCard = new RoundedPanel(18, AppTheme.SURFACE);
        calCard.setLayout(new BorderLayout());
        calCard.setBorder(new EmptyBorder(12, 12, 12, 12));

        JScrollPane calScroll = new JScrollPane(calendarPanel);
        calScroll.setBorder(null); calScroll.setOpaque(false);
        calScroll.getViewport().setOpaque(false);
        calScroll.getVerticalScrollBar().setUnitIncrement(12);

        calCard.add(calScroll, BorderLayout.CENTER);
        calWrapper.add(calCard);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartWrapper, calWrapper);
        split.setResizeWeight(0.45);
        split.setDividerLocation(0.45);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(AppTheme.BG);
        return split;
    }

    /** Cartão com título à esquerda e um botão de texto opcional à direita; o corpo vai em {@code body}. */
    private static RoundedPanel card(String titulo, String botao, Runnable acao, JPanel body) {
        RoundedPanel card = new RoundedPanel(18, AppTheme.SURFACE);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel title = new JLabel(titulo);
        title.setFont(AppTheme.FONT_SECTION);
        title.setForeground(AppTheme.TEXT_PRI);
        if (botao == null) {
            card.add(title, BorderLayout.NORTH);
        } else {
            StyledButton b = new StyledButton(botao, StyledButton.Variant.TEXT);
            b.setFont(AppTheme.FONT_SMALL);
            b.addActionListener(e -> acao.run());
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(title, BorderLayout.WEST);
            head.add(b,     BorderLayout.EAST);
            card.add(head, BorderLayout.NORTH);
        }
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ── DONUT ───────────────────────────────────────────────────────────────

    /** Um grupo por tipo (na ordem dos tipos) com suas áreas como fatias; áreas com 0 min ficam de fora. */
    static List<PieChartPanel.Group> pieGroups(Map<String, StudyData> areas,
                                               java.util.function.Function<String, String> typeOf) {
        List<PieChartPanel.Group> out = new ArrayList<>();
        for (String type : AreaTypes.KEYS) {
            List<PieChartPanel.Slice> slices = new ArrayList<>();
            int sum = 0;
            for (Map.Entry<String, StudyData> e : areas.entrySet()) {
                if (!typeOf.apply(e.getKey()).equals(type)) continue;
                int min = e.getValue().getMinutes();
                if (min <= 0) continue;
                slices.add(new PieChartPanel.Slice(e.getKey(), e.getValue().getColor(), min));
                sum += min;
            }
            if (sum > 0) out.add(new PieChartPanel.Group(AreaTypes.label(type), AreaTypes.color(type), sum, slices));
        }
        return out;
    }

    // ── CARTÃO EQUILÍBRIO: tempo da semana por tipo de área ─────────────────

    private JComponent buildBalanceCard(Runnable typeGoals) {
        balanceBody = new JPanel();
        return card("Equilíbrio · esta semana", "Metas…", typeGoals, balanceBody);
    }

    private void refreshBalanceCard() {
        balanceBody.removeAll();
        LocalDate today = LocalDate.now();
        LocalDate ws = StudyStats.weekStart(today);
        String[] keys = AreaTypes.KEYS;

        int[] mins = new int[keys.length];
        for (int i = 0; i < keys.length; i++) mins[i] = src.minutesByType(keys[i], ws, today);
        int ref = 1;
        for (int i = 0; i < keys.length; i++)
            ref = Math.max(ref, Math.max(mins[i], src.typeGoalWeek(keys[i])));

        boolean any = false;
        for (int i = 0; i < keys.length; i++) {
            int goalW = src.typeGoalWeek(keys[i]);
            if (mins[i] == 0 && goalW == 0) continue;   // tipo sem uso e sem meta: não mostra
            any = true;
            balanceBody.add(balanceRow(keys[i], mins[i], goalW, ref));
            balanceBody.add(Box.createVerticalStrut(5));
        }
        if (!any) {
            JLabel l = new JLabel("Registre tempo em Estudo, Físico ou Lazer para ver o equilíbrio.");
            l.setFont(AppTheme.FONT_SMALL); l.setForeground(AppTheme.TEXT_MUT);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            balanceBody.add(l);
        }
        balanceBody.revalidate();
        balanceBody.repaint();
    }

    private static JPanel balanceRow(String type, int mins, int goalW, int ref) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel name = new JLabel(AreaTypes.label(type));
        name.setFont(AppTheme.FONT_SMALL);
        name.setForeground(AppTheme.TEXT_SEC);
        name.setPreferredSize(new Dimension(96, 16));

        final Color col = AreaTypes.color(type);
        final int mn = mins, rf = Math.max(1, ref), gw = goalW;
        JPanel bar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppTheme.SURFACE2);
                g2.fillRoundRect(0, 5, getWidth(), getHeight() - 10, 6, 6);
                int w = (int) (getWidth() * Math.min(1.0, mn / (double) rf));
                g2.setColor(col);
                g2.fillRoundRect(0, 5, Math.max(mn > 0 ? 3 : 0, w), getHeight() - 10, 6, 6);
                if (gw > 0) {   // marca da meta
                    int mx = (int) (getWidth() * Math.min(1.0, gw / (double) rf));
                    g2.setColor(AppTheme.TEXT_SEC);
                    g2.fillRect(Math.min(getWidth() - 2, mx), 2, 2, getHeight() - 4);
                }
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(10, 18); }
        };
        bar.setOpaque(false);

        String txt = goalW > 0 ? ScreenKit.fmtHM(mins) + " / " + ScreenKit.fmtHM(goalW) : ScreenKit.fmtHM(mins);
        JLabel val = new JLabel(txt, SwingConstants.RIGHT);
        val.setFont(AppTheme.FONT_SMALL);
        val.setForeground(goalW > 0 && mins >= goalW ? AppTheme.SUCCESS
                : goalW > 0 && mins == 0 ? AppTheme.WARNING : AppTheme.TEXT_SEC);
        val.setPreferredSize(new Dimension(90, 16));

        row.add(name, BorderLayout.WEST);
        row.add(bar,  BorderLayout.CENTER);
        row.add(val,  BorderLayout.EAST);
        return row;
    }

    // ── CARTÃO METAS: agrupadas por tipo, com os sub-focos de cada área ─────

    private JComponent buildGoalsCard(Runnable manageGoals) {
        goalsBody = new JPanel();
        return card("Metas", "Gerenciar…", manageGoals, goalsBody);
    }

    private void refreshGoalsCard() {
        goalsBody.removeAll();
        StudyStats st = src.stats();

        // Agrupa as áreas ativas por tipo, preservando a ordem do usuário dentro de cada grupo.
        Map<String, List<String>> porTipo = new LinkedHashMap<>();
        for (String t : AreaTypes.KEYS) porTipo.put(t, new ArrayList<>());
        for (String s : src.areas().keySet()) {
            if (src.archived().contains(s)) continue;
            porTipo.computeIfAbsent(src.typeOf(s), k -> new ArrayList<>()).add(s);
        }

        boolean primeiro = true;
        for (Map.Entry<String, List<String>> grupo : porTipo.entrySet()) {
            if (grupo.getValue().isEmpty()) continue;
            String tipo = grupo.getKey();

            if (!primeiro) goalsBody.add(Box.createVerticalStrut(8));
            primeiro = false;

            JPanel cab = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            cab.setOpaque(false);
            cab.setAlignmentX(Component.LEFT_ALIGNMENT);
            cab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
            cab.setBorder(new EmptyBorder(0, 0, 2, 0));
            cab.add(colorDot(AreaTypes.color(tipo)));
            JLabel cabL = new JLabel(AreaTypes.label(tipo).toUpperCase());
            cabL.setFont(AppTheme.FONT_CAPTION);
            cabL.setForeground(AppTheme.TEXT_MUT);
            cab.add(cabL);
            goalsBody.add(cab);

            for (String s : grupo.getValue()) {
                int d = src.idealDaily(s);
                int w = st.weeklyGoalFor(s);
                int m = st.monthlyGoalFor(s);

                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(false);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

                JPanel dotName = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                dotName.setOpaque(false);
                dotName.add(colorDot(src.areas().get(s).getColor()));
                JLabel nm = new JLabel(s);
                nm.setFont(AppTheme.FONT_LABEL);
                nm.setForeground(AppTheme.TEXT_PRI);
                dotName.add(nm);

                JLabel val = new JLabel(goalsText(d, w, m));
                val.setFont(AppTheme.FONT_SMALL);
                val.setForeground(d <= 0 && w <= 0 && m <= 0 ? AppTheme.TEXT_MUT : AppTheme.TEXT_SEC);
                val.setHorizontalAlignment(SwingConstants.RIGHT);

                row.add(dotName, BorderLayout.WEST);
                row.add(val,     BorderLayout.EAST);
                goalsBody.add(row);

                List<String> sf = src.subFocos(s);
                if (sf != null && !sf.isEmpty()) {
                    JLabel sub = new JLabel("      " + String.join(" · ", sf));
                    sub.setFont(AppTheme.FONT_CAPTION);
                    sub.setForeground(AppTheme.TEXT_MUT);
                    sub.setAlignmentX(Component.LEFT_ALIGNMENT);
                    goalsBody.add(sub);
                }
                goalsBody.add(Box.createVerticalStrut(4));
            }
        }
        goalsBody.revalidate();
        goalsBody.repaint();
    }

    /** "Dia 30m  ·  Sem 3h30  ·  Mês 15h00", ou "sem meta" se nenhuma das três existir. */
    static String goalsText(int day, int week, int month) {
        return day <= 0 && week <= 0 && month <= 0
                ? "sem meta"
                : "Dia " + ScreenKit.fmtHM(Math.max(0, day)) + "  ·  Sem " + ScreenKit.fmtHM(week)
                  + "  ·  Mês " + ScreenKit.fmtHM(month);
    }

    // ── CARTÃO PROGRESSO: bloco GERAL + bloco da área selecionada ───────────

    private JComponent buildProgressCard() {
        progressBody = new JPanel();
        return card("Progresso", null, null, progressBody);
    }

    /** Repopula o cartão (também chamado quando a área do combo muda ou as tarefas mudam). */
    void refreshProgressCard() {
        progressBody.removeAll();
        LocalDate today = LocalDate.now();

        // Bloco GERAL (metas = soma automática das matérias)
        progressBody.add(sectionHeader("GERAL", null));
        progressBody.add(streakInfoLine(null));
        progressBody.add(Box.createVerticalStrut(4));
        addPeriodRows(null, today);

        // Divisor
        progressBody.add(Box.createVerticalStrut(8));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(AppTheme.DIVIDER);
        progressBody.add(sep);
        progressBody.add(Box.createVerticalStrut(8));

        // Bloco da matéria selecionada
        String subject = src.selectedArea();
        if (subject != null) {
            Color dot = src.areas().getOrDefault(subject, new StudyData(0, AppTheme.TEXT_SEC)).getColor();
            progressBody.add(sectionHeader(subject, dot));
            progressBody.add(streakInfoLine(subject));
            if (src.examDate(subject) != null) {
                progressBody.add(Box.createVerticalStrut(2));
                progressBody.add(examLine(subject, today));
            }
            progressBody.add(Box.createVerticalStrut(4));
            addPeriodRows(subject, today);
        }

        progressBody.revalidate();
        progressBody.repaint();
    }

    private void addPeriodRows(String subject, LocalDate today) {
        StudyStats st = src.stats();
        Color base = subject == null
                ? AppTheme.ACCENT
                : src.areas().getOrDefault(subject, new StudyData(0, AppTheme.ACCENT)).getColor();
        progressBody.add(periodRow("Dia",    st.minutesOnDay(subject, today),                                  st.dailyGoalFor(subject),   base));
        progressBody.add(Box.createVerticalStrut(4));
        progressBody.add(periodRow("Semana", st.minutesInRange(subject, StudyStats.weekStart(today),  today), st.weeklyGoalFor(subject),  base));
        progressBody.add(Box.createVerticalStrut(4));
        progressBody.add(periodRow("Mês",    st.minutesInRange(subject, StudyStats.monthStart(today), today), st.monthlyGoalFor(subject), base));
        progressBody.add(Box.createVerticalStrut(4));
        progressBody.add(oneLine(insightText(subject, today), AppTheme.TEXT_SEC));
    }

    /** Saldo da semana + comparação com o mês passado (" " quando não há o que dizer). */
    String insightText(String subject, LocalDate today) {
        StudyStats st = src.stats();
        StringBuilder sb = new StringBuilder();
        if (st.dailyGoalFor(subject) > 0) {
            int bal = st.weeklyBalance(subject, today);
            sb.append(bal >= 0 ? "semana: +" + ScreenKit.fmtHM(bal) + " sobre a meta"
                               : "semana: faltam " + ScreenKit.fmtHM(-bal) + " p/ a meta");
        }
        int thisM = st.minutesInRange(subject, StudyStats.monthStart(today), today);
        LocalDate lmEnd = today.minusMonths(1);
        int lastM = st.minutesInRange(subject, lmEnd.withDayOfMonth(1), lmEnd);
        if (thisM > 0 || lastM > 0) {
            if (sb.length() > 0) sb.append("   ·   ");
            int d = thisM - lastM;
            sb.append("mês: ").append(ScreenKit.fmtHM(thisM)).append(
                    d == 0 ? " (= mês passado)"
                  : d  > 0 ? " (▲ " + ScreenKit.fmtHM(d)  + " vs. mês passado)"
                           : " (▼ " + ScreenKit.fmtHM(-d) + " vs. mês passado)");
        }
        return sb.length() == 0 ? " " : sb.toString();
    }

    /** Linha de contagem regressiva de prova (só quando há data marcada). */
    private JPanel examLine(String subject, LocalDate today) {
        LocalDate exam = src.examDate(subject);
        if (exam == null) return oneLine(" ", null);
        long dias = java.time.temporal.ChronoUnit.DAYS.between(today, exam);
        Color cor = dias < 0 ? AppTheme.TEXT_SEC : dias <= 3 ? AppTheme.WARNING : AppTheme.ACCENT;
        return oneLine(examText(subject, today), cor);
    }

    String examText(String subject, LocalDate today) {
        LocalDate exam = src.examDate(subject);
        if (exam == null) return " ";
        long dias = java.time.temporal.ChronoUnit.DAYS.between(today, exam);
        String quando = exam.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
        if (dias < 0) return "Prova de " + subject + " foi em " + quando;

        StudyStats st = src.stats();
        int daily = st.dailyGoalFor(subject);
        int agendadosRestantes = 0;
        for (LocalDate d = today.plusDays(1); !d.isAfter(exam); d = d.plusDays(1))
            if (st.isGoalDay(subject, d)) agendadosRestantes++;
        String base = "Prova em " + (dias == 0 ? "HOJE" : dias + (dias == 1 ? " dia" : " dias"))
                    + " (" + quando + ")";
        if (daily > 0 && dias > 0)
            base += "  ·  no ritmo da meta: ~" + ScreenKit.fmtHM(daily * agendadosRestantes) + " até lá";
        int total = 0, feitos = 0;
        for (ChecklistItem it : src.checklist())
            if (it.subject.equals(subject)) { total++; if (it.done) feitos++; }
        if (total > 0)
            base += "  ·  plano " + feitos + "/" + total + (feitos == total ? " · tudo feito" : "");
        return base;
    }

    /** Linha de sequência: N dias · recorde M (ou dica quando não há sequência). */
    private JPanel streakInfoLine(String subject) {
        StudyStats st = src.stats();
        int goal   = st.dailyGoalFor(subject);
        int streak = st.currentStreak(subject);
        boolean metToday = goal > 0 && streak > 0 && st.minutesOnDay(subject, LocalDate.now()) >= goal;
        return oneLine(streakText(goal, streak, src.bestStreak(subject), metToday),
                metToday ? AppTheme.WARNING : AppTheme.TEXT_SEC);
    }

    static String streakText(int goal, int streak, int best, boolean metToday) {
        if (goal <= 0)   return "defina uma meta diária para ativar a sequência";
        if (streak <= 0) return "comece hoje para iniciar a sequência";
        String txt = "Sequência: " + streak + (streak == 1 ? " dia" : " dias") + StudyStats.streakBadge(streak)
                   + "   ·   recorde " + best;
        if (!metToday) txt += "   (estude hoje para manter)";
        return txt;
    }

    /** Uma linha de texto pequeno, alinhada à esquerda (cor null = padrão do L&F). */
    private static JPanel oneLine(String text, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.FONT_SMALL);
        if (color != null) l.setForeground(color);
        p.add(l);
        return p;
    }

    /** Cabeçalho de bloco: ponto colorido opcional + nome em negrito. */
    private static JPanel sectionHeader(String name, Color dotColor) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        if (dotColor != null) p.add(colorDot(dotColor));
        JLabel l = new JLabel(name);
        l.setFont(AppTheme.FONT_BOLD);
        l.setForeground(AppTheme.TEXT_PRI);
        p.add(l);
        return p;
    }

    /** Uma barra de período: rótulo · barra · "atual / meta  pct%". A barra usa
     *  a cor da meta em níveis (tom escuro → claro) para mostrar o quanto falta. */
    private static JPanel periodRow(String label, int current, int goal, Color base) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel lbl = new JLabel(label);
        lbl.setFont(AppTheme.FONT_SMALL);
        lbl.setForeground(AppTheme.TEXT_SEC);
        lbl.setPreferredSize(new Dimension(56, 18));

        GaugeBar bar = new GaugeBar();
        bar.setPreferredSize(new Dimension(0, 8));

        JLabel val = new JLabel();
        val.setFont(AppTheme.FONT_SMALL);
        val.setHorizontalAlignment(SwingConstants.RIGHT);
        val.setPreferredSize(new Dimension(120, 18));

        if (goal <= 0) {
            bar.set(0, AppTheme.TEXT_MUT, false);
            val.setText("— sem meta");
            val.setForeground(AppTheme.TEXT_SEC);
        } else {
            double frac = current / (double) goal;
            boolean met = current >= goal;
            int pct = (int) Math.min(100, frac * 100.0);
            bar.set(frac, base, met);
            val.setText(ScreenKit.fmtHM(current) + " / " + ScreenKit.fmtHM(goal) + "   " + (met ? "feito · " : "") + pct + "%");
            val.setForeground(met ? AppTheme.SUCCESS : AppTheme.TEXT_PRI);
        }

        row.add(lbl, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        row.add(val, BorderLayout.EAST);
        return row;
    }

    private static JComponent colorDot(Color c) {
        JPanel dot = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c); g2.fillOval(0, 4, 10, 10); g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(10, 18); }
        };
        dot.setOpaque(false);
        return dot;
    }
}
