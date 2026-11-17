import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.*;
import java.util.Calendar;
import java.util.Date;

/**
 * O cartão de baixo da tela Hoje: "Área:" + Meta + barra de meta e as abas Cronômetro, Manual e
 * Pomodoro. Só a parte visual: botões, rótulos e campos. O que acontece ao clicar (salvar tempo,
 * ciclo do Pomodoro, modo foco) continua no StudyTracker, que chama os métodos daqui para pôr os
 * botões no estado certo.
 */
final class TimerCard {

    interface Actions {
        /** Trocou a área no combo. */
        void areaChanged();
        void goalDialog();
        void startStopwatch();
        void pauseStopwatch();
        void resetStopwatch();
        void submitStopwatch();
        void addManual();
        /** Botão "Foco" do Pomodoro. */
        void startFocus();
        /** Botão "Descanso". */
        void startBreak();
        void togglePausePomodoro();
        void resetPomodoro();
        void autoCycleChanged(boolean on);
    }

    private final Actions actions;
    private final JPanel panel;

    private JComboBox<String> areaCombo;
    private CustomTabs tabs;
    private JPanel goalBar;
    private GaugeBar goalProgress;
    private JLabel goalLabel, goalPercent;

    private JLabel stopwatchLabel, stopwatchInfoLabel;
    private StyledButton startButton, stopButton, submitButton, resetStopwatchButton;

    private JLabel countdownLabel, statusLabel, pomoCountLabel;
    private JSpinner focusSpinner, breakSpinner;
    private JCheckBox autoCycleCheck;
    private StyledButton startFocusButton, startBreakButton, stopCountdownButton, resetButton;

    private JTextField hoursField, minutesField;
    private JComboBox<DayOption> manualDayCombo;

    TimerCard(Actions actions, boolean autoCycle) {
        this.actions = actions;
        panel = build(autoCycle);
    }

    JPanel panel() { return panel; }

    /** O combo de área (o StudyTracker ainda o usa em vários lugares: mesma instância). */
    JComboBox<String> areaCombo() { return areaCombo; }

    /** 0 = Cronômetro, 1 = Manual, 2 = Pomodoro. */
    void selectTab(int i) { tabs.select(i); }

    private JPanel build(boolean autoCycle) {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(AppTheme.BG);
        outer.setBorder(new EmptyBorder(0, 12, 12, 12));

        // Compacto: o cronômetro parado não precisa de meia tela (a Hoje é sobre o que fazer).
        RoundedPanel card = new RoundedPanel(18, AppTheme.SURFACE);
        card.setLayout(new BorderLayout(0, 2));
        card.setBorder(new EmptyBorder(10, 16, 8, 16));

        card.add(buildAreaRow(), BorderLayout.NORTH);

        // Tabs customizadas (sem fundo branco do JTabbedPane)
        tabs = new CustomTabs();
        // Sem ⏱ ✏ 🍅: a fonte do Swing no Windows mostra esses glifos como quadrados.
        tabs.addTab("Cronômetro", buildStopwatchTab());
        tabs.addTab("Manual",     buildManualTab());
        tabs.addTab("Pomodoro",   buildPomodoroTab(autoCycle));
        tabs.setPreferredSize(new Dimension(0, 130));
        card.add(tabs, BorderLayout.CENTER);

        outer.add(card);
        return outer;
    }

    // ── ÁREA + META ─────────────────────────────────────────────────────────

    private JPanel buildAreaRow() {
        // [Área + Meta + barra de meta]. Nota, sub-foco e energia são perguntados ao terminar.
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(0, 0, 4, 0));

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel lbl = new JLabel("Área:");
        lbl.setFont(AppTheme.FONT_BOLD);
        lbl.setForeground(AppTheme.TEXT_PRI);
        areaCombo = new JComboBox<>();
        styleInput(areaCombo);
        areaCombo.setPreferredSize(new Dimension(220, 32));
        areaCombo.addActionListener(e -> actions.areaChanged());
        left.add(lbl); left.add(areaCombo);

        StyledButton btnGoal = new StyledButton("Meta", StyledButton.Variant.TEXT);
        btnGoal.setFont(AppTheme.FONT_SMALL);
        btnGoal.addActionListener(e -> actions.goalDialog());
        left.add(btnGoal);

        // Barra de meta (oculta por padrão)
        goalBar = new JPanel(new BorderLayout(8, 0));
        goalBar.setOpaque(false);
        goalBar.setVisible(false);

        goalLabel   = new JLabel("Meta: 0h");
        goalLabel.setFont(AppTheme.FONT_SMALL); goalLabel.setForeground(AppTheme.TEXT_SEC);
        goalPercent = new JLabel("0%");
        goalPercent.setFont(AppTheme.FONT_SMALL); goalPercent.setForeground(AppTheme.ACCENT);
        goalProgress = new GaugeBar();
        goalProgress.setPreferredSize(new Dimension(0, 8));

        JPanel gpRow = new JPanel(new BorderLayout(4, 0));
        gpRow.setOpaque(false);
        gpRow.add(goalLabel,    BorderLayout.WEST);
        gpRow.add(goalProgress, BorderLayout.CENTER);
        gpRow.add(goalPercent,  BorderLayout.EAST);
        goalBar.add(gpRow);

        row.add(left,    BorderLayout.WEST);
        row.add(goalBar, BorderLayout.CENTER);

        wrap.add(row);
        return wrap;
    }

    /** "0h40m / 1h30m". */
    static String goalText(int current, int goal) {
        return String.format("%dh%02dm / %dh%02dm", current / 60, current % 60, goal / 60, goal % 60);
    }

    /** Porcentagem da meta de hoje, no máximo 100. */
    static int goalPercent(int current, int goal) {
        return (int) Math.min(100, current * 100.0 / goal);
    }

    void hideGoal() { goalBar.setVisible(false); }

    /** Mostra a barra: feito hoje / meta (minutos), na cor da área. */
    void showGoal(int current, int goal, Color base) {
        goalBar.setVisible(true);
        goalLabel.setText(goalText(current, goal));
        goalPercent.setText(goalPercent(current, goal) + "%");
        goalPercent.setForeground(current >= goal ? AppTheme.SUCCESS : base);
        goalProgress.set(current / (double) goal, base, current >= goal);
    }

    // ── CRONÔMETRO ──────────────────────────────────────────────────────────

    private JPanel buildStopwatchTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(AppTheme.SURFACE);

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(0, 6, 0, 0));

        stopwatchLabel = new JLabel("00:00:00", SwingConstants.LEFT);
        stopwatchLabel.setFont(AppTheme.FONT_MONO_S);
        stopwatchLabel.setForeground(AppTheme.TEXT_PRI);
        stopwatchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        stopwatchInfoLabel = new JLabel(" ", SwingConstants.LEFT);
        stopwatchInfoLabel.setFont(AppTheme.FONT_SMALL);
        stopwatchInfoLabel.setForeground(AppTheme.TEXT_SEC);
        stopwatchInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        head.add(stopwatchLabel);
        head.add(stopwatchInfoLabel);

        startButton  = new StyledButton("Iniciar",        StyledButton.Variant.FILLED);
        stopButton   = new StyledButton("Pausar",         StyledButton.Variant.OUTLINED);
        resetStopwatchButton = new StyledButton("Zerar",  StyledButton.Variant.TEXT);
        submitButton = new StyledButton("Salvar Tempo",   StyledButton.Variant.TONAL);
        stopButton.setEnabled(false); submitButton.setEnabled(false); resetStopwatchButton.setEnabled(false);
        startButton.addActionListener(e  -> actions.startStopwatch());
        stopButton.addActionListener(e   -> actions.pauseStopwatch());
        resetStopwatchButton.addActionListener(e -> actions.resetStopwatch());
        submitButton.addActionListener(e -> actions.submitStopwatch());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(startButton); btns.add(stopButton);
        btns.add(resetStopwatchButton); btns.add(submitButton);

        JPanel line = new JPanel(new BorderLayout(12, 0));
        line.setOpaque(false);
        line.add(head, BorderLayout.WEST);
        line.add(btns, BorderLayout.EAST);
        p.add(line, BorderLayout.NORTH);
        return p;
    }

    void setStopwatchText(String elapsed) { stopwatchLabel.setText(elapsed); }
    void setStopwatchInfo(String info)    { stopwatchInfoLabel.setText(info); }

    /** Cronômetro contando. */
    void stopwatchRunning() {
        startButton.setText("Retomar");
        startButton.setEnabled(false); stopButton.setEnabled(true);
        submitButton.setEnabled(false); resetStopwatchButton.setEnabled(true);
        areaCombo.setEnabled(false);
    }

    /** Cronômetro pausado (com ou sem tempo para salvar). */
    void stopwatchPaused(boolean hasTime) {
        startButton.setEnabled(true); stopButton.setEnabled(false);
        submitButton.setEnabled(hasTime);
        resetStopwatchButton.setEnabled(hasTime);
        areaCombo.setEnabled(true);
    }

    /** Cronômetro zerado. */
    void stopwatchCleared() {
        startButton.setText("Iniciar");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        submitButton.setEnabled(false);
        resetStopwatchButton.setEnabled(false);
        areaCombo.setEnabled(true);
    }

    // ── MANUAL ──────────────────────────────────────────────────────────────

    private JPanel buildManualTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        p.setBackground(AppTheme.SURFACE);
        hoursField   = styledField("Horas");
        minutesField = styledField("Minutos");
        StyledButton addBtn = new StyledButton("Adicionar Tempo", StyledButton.Variant.FILLED);
        addBtn.addActionListener(e -> actions.addManual());
        manualDayCombo = new JComboBox<>();
        manualDayCombo.setFont(AppTheme.FONT_SMALL);
        manualDayCombo.setToolTipText("Dia em que o tempo foi feito. Esqueceu de registrar? Escolha o dia certo.");
        fillManualDays(LocalDate.now());
        // Os rótulos são relativos a hoje: refaz a lista ao abrir (o app pode passar da meia-noite aberto).
        manualDayCombo.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                DayOption sel = (DayOption) manualDayCombo.getSelectedItem();
                fillManualDays(sel != null && sel.custom ? sel.day : null);
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });
        manualDayCombo.addActionListener(e -> {
            DayOption sel = (DayOption) manualDayCombo.getSelectedItem();
            if (sel != null && sel.day == null) pickOtherManualDay();
        });
        p.add(new JLabel("Dia:")     {{ setFont(AppTheme.FONT_LABEL); setForeground(AppTheme.TEXT_PRI); }});
        p.add(manualDayCombo);
        p.add(new JLabel("Horas:")   {{ setFont(AppTheme.FONT_LABEL); setForeground(AppTheme.TEXT_PRI); }});
        p.add(hoursField);
        p.add(new JLabel("Minutos:") {{ setFont(AppTheme.FONT_LABEL); setForeground(AppTheme.TEXT_PRI); }});
        p.add(minutesField);
        p.add(Box.createHorizontalStrut(12));
        p.add(addBtn);
        return p;
    }

    /** Horas e minutos digitados → total em minutos. Vazio = 0; texto que não é número ⇒ NumberFormatException. */
    static int parseManual(String horas, String minutos) {
        int h = horas.trim().isEmpty()   ? 0 : Integer.parseInt(horas.trim());
        int m = minutos.trim().isEmpty() ? 0 : Integer.parseInt(minutos.trim());
        return h * 60 + m;
    }

    /** Total digitado na aba Manual, em minutos (ver {@link #parseManual}). */
    int manualMinutes() { return parseManual(hoursField.getText(), minutesField.getText()); }

    /** Dia escolhido no combo "Dia" (hoje se nada escolhido). */
    LocalDate manualDay() {
        DayOption sel = (DayOption) manualDayCombo.getSelectedItem();
        LocalDate today = LocalDate.now();
        return (sel == null || sel.day == null) ? today : sel.day;
    }

    /** Depois de adicionar: limpa os campos e volta para "Hoje" (registrar no passado é exceção). */
    void clearManual() {
        hoursField.setText(""); minutesField.setText("");
        fillManualDays(null);
    }

    /** Opção do combo "Dia" da aba Manual. day == null ⇒ "Outra data…". */
    static final class DayOption {
        final LocalDate day; final String label; final boolean custom;
        DayOption(LocalDate day, String label, boolean custom) { this.day = day; this.label = label; this.custom = custom; }
        @Override public String toString() { return label; }
    }

    private static final String[] DOW_PT = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};

    /** "Hoje", "Ontem" ou "seg 02/11". */
    static String dayLabel(LocalDate d, LocalDate today) {
        if (d.equals(today)) return "Hoje";
        if (d.equals(today.minusDays(1))) return "Ontem";
        return DOW_PT[d.getDayOfWeek().getValue() - 1] + " " + String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue())
             + (d.getYear() != today.getYear() ? "/" + d.getYear() : "");
    }

    /** Hoje + 6 dias anteriores + "Outra data…". custom != null entra na lista e fica selecionado. */
    static DefaultComboBoxModel<DayOption> manualDays(LocalDate custom, LocalDate today) {
        DefaultComboBoxModel<DayOption> m = new DefaultComboBoxModel<>();
        DayOption select = null;
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(i);
            DayOption o = new DayOption(d, dayLabel(d, today), false);
            m.addElement(o);
            if (i == 0 || d.equals(custom)) select = o;
        }
        if (custom != null && !custom.isAfter(today) && custom.isBefore(today.minusDays(6))) {
            select = new DayOption(custom, dayLabel(custom, today), true);
            m.addElement(select);
        }
        m.addElement(new DayOption(null, "Outra data…", false));
        m.setSelectedItem(select);
        return m;
    }

    private void fillManualDays(LocalDate custom) {
        manualDayCombo.setModel(manualDays(custom, LocalDate.now()));
    }

    /** Pede uma data qualquer (até hoje). Cancelar volta para "Hoje". */
    private void pickOtherManualDay() {
        LocalDate today = LocalDate.now();
        Date max = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date ini = Date.from(today.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant());
        JSpinner sp = new JSpinner(new SpinnerDateModel(ini, null, max, Calendar.DAY_OF_MONTH));
        sp.setEditor(new JSpinner.DateEditor(sp, "dd/MM/yyyy"));
        JPanel pn = new JPanel(new BorderLayout(0, 6));
        pn.add(new JLabel("Em que dia foi?"), BorderLayout.NORTH);
        pn.add(sp, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> {
            int r = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(panel), pn, "Outra data",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            LocalDate d = null;
            if (r == JOptionPane.OK_OPTION) {
                d = Instant.ofEpochMilli(((Date) sp.getValue()).getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
                if (d.isAfter(today)) d = today;
            }
            fillManualDays(d);
        });
    }

    // ── POMODORO ────────────────────────────────────────────────────────────

    private JPanel buildPomodoroTab(boolean autoCycle) {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(AppTheme.SURFACE);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        top.setBackground(AppTheme.SURFACE);
        focusSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 120, 1));
        breakSpinner = new JSpinner(new SpinnerNumberModel(5,  1,  60, 1));
        styleSpinner(focusSpinner); styleSpinner(breakSpinner);

        top.add(lbl("Foco (min):")); top.add(focusSpinner);
        top.add(Box.createHorizontalStrut(16));
        top.add(lbl("Pausa (min):")); top.add(breakSpinner);

        autoCycleCheck = new JCheckBox("Ciclos automáticos", autoCycle);
        autoCycleCheck.setOpaque(false);
        autoCycleCheck.setFont(AppTheme.FONT_SMALL);
        autoCycleCheck.setForeground(AppTheme.TEXT_SEC);
        autoCycleCheck.setToolTipText("Encadeia foco → pausa → foco sozinho; pausa longa a cada 4 focos");
        autoCycleCheck.addActionListener(e -> actions.autoCycleChanged(autoCycleCheck.isSelected()));
        top.add(Box.createHorizontalStrut(16));
        top.add(autoCycleCheck);

        // Layout vertical: countdown | status | focos de hoje
        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.setOpaque(false);

        statusLabel = new JLabel("Pronto para iniciar", SwingConstants.CENTER);
        statusLabel.setFont(AppTheme.FONT_SMALL);
        statusLabel.setForeground(AppTheme.ACCENT);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        countdownLabel = new JLabel("25:00", SwingConstants.CENTER);
        countdownLabel.setFont(AppTheme.FONT_MONO_S);
        countdownLabel.setForeground(AppTheme.TEXT_PRI);
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        pomoCountLabel = new JLabel("", SwingConstants.CENTER);
        pomoCountLabel.setFont(AppTheme.FONT_SMALL);
        pomoCountLabel.setForeground(AppTheme.TEXT_SEC);
        pomoCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Foco: inicia sessão de foco
        startFocusButton    = new StyledButton("Foco",   StyledButton.Variant.FILLED);
        // Descanso: inicia pausa/descanso
        startBreakButton    = new StyledButton("Descanso", StyledButton.Variant.TONAL);
        // Pausar/Retomar: pausa e retoma o timer atual
        stopCountdownButton = new StyledButton("Pausar", StyledButton.Variant.OUTLINED);
        // Resetar: cancela tudo
        resetButton         = new StyledButton("Resetar", StyledButton.Variant.TEXT);

        stopCountdownButton.setEnabled(false);
        resetButton.setEnabled(false);
        // Descanso sempre disponível, não depende de ter rodado Foco antes
        startBreakButton.setEnabled(true);

        startFocusButton.addActionListener(e    -> actions.startFocus());
        startBreakButton.addActionListener(e    -> actions.startBreak());
        stopCountdownButton.addActionListener(e -> actions.togglePausePomodoro());
        resetButton.addActionListener(e         -> actions.resetPomodoro());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.setOpaque(false);
        buttons.add(startFocusButton);
        buttons.add(startBreakButton);
        buttons.add(stopCountdownButton);
        buttons.add(resetButton);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        countdownLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        countdownLabel.setHorizontalAlignment(SwingConstants.LEFT);
        pomoCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mid.setBorder(new EmptyBorder(0, 6, 0, 0));
        mid.add(countdownLabel);
        mid.add(statusLabel);
        mid.add(pomoCountLabel);
        buttons.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JPanel line = new JPanel(new BorderLayout(12, 0));
        line.setOpaque(false);
        line.setBorder(new EmptyBorder(6, 0, 0, 0));
        line.add(mid,     BorderLayout.WEST);
        line.add(buttons, BorderLayout.EAST);
        p.add(top,  BorderLayout.NORTH);
        p.add(line, BorderLayout.CENTER);
        return p;
    }

    int focusMinutes() { return (int) focusSpinner.getValue(); }
    int breakMinutes() { return (int) breakSpinner.getValue(); }
    void setAutoCycle(boolean on) { autoCycleCheck.setSelected(on); }

    void setCountdown(String text) { countdownLabel.setText(text); }

    /** "" (nenhum), "1 foco hoje", "3 focos hoje". */
    static String pomoCountText(int n) {
        return n == 0 ? "" : n + (n == 1 ? " foco hoje" : " focos hoje");
    }

    void setPomoCount(int n) { pomoCountLabel.setText(pomoCountText(n)); }

    /** "Focando em X…" (foco) ou "Em pausa…". */
    static String runningStatus(boolean isFocus, Object area) {
        return isFocus ? "Focando em " + area + "…" : "Em pausa…";
    }

    /** Pomodoro começou (foco ou pausa). */
    void pomodoroStarted(boolean isFocus, String area) {
        statusLabel.setText(runningStatus(isFocus, area));
        statusLabel.setForeground(isFocus ? AppTheme.SUCCESS : AppTheme.ACCENT);
        startFocusButton.setEnabled(false);
        startBreakButton.setEnabled(false);
        stopCountdownButton.setEnabled(true);
        stopCountdownButton.setText("Pausar");
        resetButton.setEnabled(true);
        areaCombo.setEnabled(false);
    }

    /** Pomodoro parado, pronto para iniciar. */
    void pomodoroIdle() {
        statusLabel.setText("Pronto para iniciar"); statusLabel.setForeground(AppTheme.ACCENT);
        startFocusButton.setEnabled(true); startBreakButton.setEnabled(true);
        stopCountdownButton.setEnabled(false);
        stopCountdownButton.setText("Pausar");
        resetButton.setEnabled(false);
        areaCombo.setEnabled(true);
    }

    void pomodoroPaused() {
        stopCountdownButton.setText("Retomar");
        statusLabel.setText("Pausado");
        statusLabel.setForeground(AppTheme.WARNING);
    }

    /** Retomou (a cor do status fica como estava, igual a antes). */
    void pomodoroResumed(boolean isFocus, Object area) {
        stopCountdownButton.setText("Pausar");
        statusLabel.setText(runningStatus(isFocus, area));
    }

    /**
     * Põe botões e rótulos de acordo com o estado dos modelos. Necessário depois de recriar a tela
     * (trocar tema, importar backup): sem isso, uma sessão rodando voltava com "Iniciar" ligado.
     * O texto do cronômetro é posto antes por quem chama.
     */
    void sync(StopwatchModel sw, PomodoroModel pomo) {
        boolean swOn = sw.isRunning();
        startButton.setText(sw.started() ? "Retomar" : "Iniciar");
        startButton.setEnabled(!swOn);
        stopButton.setEnabled(swOn);
        submitButton.setEnabled(!swOn && sw.hasTime());
        resetStopwatchButton.setEnabled(swOn || sw.hasTime());

        boolean pomoOn = pomo.isActive();
        startFocusButton.setEnabled(!pomoOn);
        startBreakButton.setEnabled(!pomoOn);
        stopCountdownButton.setEnabled(pomoOn);
        stopCountdownButton.setText(pomo.isPaused() ? "Retomar" : "Pausar");
        resetButton.setEnabled(pomoOn);
        if (pomoOn) {
            setCountdown(pomo.remainingText());
            if (pomo.isPaused()) {
                statusLabel.setText("Pausado"); statusLabel.setForeground(AppTheme.WARNING);
            } else if (pomo.isFocus()) {
                statusLabel.setText(runningStatus(true, areaCombo.getSelectedItem()));
                statusLabel.setForeground(AppTheme.SUCCESS);
            } else {
                statusLabel.setText("Em pausa…"); statusLabel.setForeground(AppTheme.ACCENT);
            }
        }
        areaCombo.setEnabled(!swOn && !pomoOn);   // sessão contando: o tempo não pode mudar de área
    }

    // ── AJUDANTES ───────────────────────────────────────────────────────────

    private static JLabel lbl(String text) {
        JLabel l = new JLabel(text); l.setFont(AppTheme.FONT_LABEL); l.setForeground(AppTheme.TEXT_PRI); return l;
    }

    private static JTextField styledField(String hint) {
        JTextField f = new JTextField(6);
        f.setToolTipText(hint);
        styleInput(f);
        return f;
    }

    /** Padroniza a fonte dos campos. O Nimbus cuida das cores conforme o tema. */
    static void styleInput(JComponent c) {
        c.setFont(AppTheme.FONT_LABEL);
        if (c instanceof JSpinner) {
            JComponent ed = ((JSpinner) c).getEditor();
            if (ed instanceof JSpinner.DefaultEditor)
                ((JSpinner.DefaultEditor) ed).getTextField().setFont(AppTheme.FONT_LABEL);
        }
    }

    private static void styleSpinner(JSpinner sp) {
        sp.setPreferredSize(new Dimension(74, 30));
        styleInput(sp);
    }
}
