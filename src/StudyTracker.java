import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class StudyTracker extends JFrame {

    private static final String SAVE_FILE    = "study_data.properties";
    private static final String SESSION_FILE = "study_sessions.properties";

    private final Map<String, StudyData>  studyDataMap = new LinkedHashMap<>();
    private final List<StudySession>      sessions     = new ArrayList<>();

    // --- UI principal ---
    private PieChartPanel chartPanel;
    private JPanel        summaryPanel;
    private JComboBox<String> subjectComboBox;
    private JLabel        tvTotal;
    private JPanel        goalBar;
    private JProgressBar  goalProgress;
    private JLabel        tvGoalLabel, tvGoalPercent;

    // --- Cronômetro ---
    private Timer  stopwatch;
    private int    elapsedSeconds = 0;
    private JLabel stopwatchLabel;
    private StyledButton startButton, stopButton, submitButton;

    // --- Pomodoro ---
    private Timer   countdownTimer;
    private int     countdownSecondsRemaining = 0;
    private boolean wasFocusSession = false;
    private int     plannedMinutes  = 0;
    private boolean isPaused        = false;
    private JLabel  countdownLabel, statusLabel;
    private JSpinner focusSpinner, breakSpinner;
    private StyledButton startFocusButton, startBreakButton, stopCountdownButton, resetButton;

    // --- Manual ---
    private JTextField hoursField, minutesField;

    // --- Metas ---
    private final Map<String, Integer> goals = new LinkedHashMap<>();

    public StudyTracker() {
        loadData();
        loadSessions();

        setTitle("FocaEstudo");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1050, 740);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(AppTheme.BG);
        setLayout(new BorderLayout(0, 0));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { saveData(); saveSessions(); System.exit(0); }
        });

        buildUI();
        setupTimers();
    }

    // ── BUILD UI ────────────────────────────────────────────────────────────

    private void buildUI() {
        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildBottom(),    BorderLayout.SOUTH);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(AppTheme.SURFACE);
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.DIVIDER),
                new EmptyBorder(12, 20, 12, 16)));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setOpaque(false);
        JLabel title = new JLabel("FocaEstudo");
        title.setFont(AppTheme.FONT_TITLE);
        title.setForeground(AppTheme.TEXT_PRI);
        tvTotal = new JLabel("Total: 0h 00min");
        tvTotal.setFont(AppTheme.FONT_SMALL);
        tvTotal.setForeground(AppTheme.TEXT_SEC);
        left.add(title); left.add(tvTotal);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        StyledButton btnHistory = new StyledButton("Histórico", StyledButton.Variant.TEXT);
        btnHistory.addActionListener(e -> showHistory());

        StyledButton btnTheme = new StyledButton("Tema", StyledButton.Variant.TEXT);
        btnTheme.addActionListener(e -> toggleTheme());

        right.add(btnHistory);
        right.add(btnTheme);

        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JSplitPane buildCenter() {
        // Lado esquerdo: gráfico donut
        JPanel chartWrapper = new JPanel(new BorderLayout());
        chartWrapper.setBackground(AppTheme.BG);
        chartWrapper.setBorder(new EmptyBorder(12, 12, 6, 6));
        chartPanel = new PieChartPanel(studyDataMap);
        chartPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        RoundedPanel chartCard = new RoundedPanel(18, AppTheme.SURFACE);
        chartCard.setLayout(new BorderLayout());
        chartCard.add(chartPanel);
        chartWrapper.add(chartCard);

        // Lado direito: resumo
        JPanel summaryWrapper = new JPanel(new BorderLayout());
        summaryWrapper.setBackground(AppTheme.BG);
        summaryWrapper.setBorder(new EmptyBorder(12, 6, 6, 12));
        RoundedPanel summaryCard = new RoundedPanel(18, AppTheme.SURFACE);
        summaryCard.setLayout(new BorderLayout());
        summaryCard.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel summaryTitle = new JLabel("Resumo do Estudo");
        summaryTitle.setFont(AppTheme.FONT_BOLD);
        summaryTitle.setForeground(AppTheme.TEXT_PRI);
        summaryTitle.setBorder(new EmptyBorder(0, 0, 10, 0));

        summaryPanel = new JPanel();
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
        summaryPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(summaryPanel);
        scroll.setBorder(null); scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        summaryCard.add(summaryTitle, BorderLayout.NORTH);
        summaryCard.add(scroll,       BorderLayout.CENTER);
        summaryWrapper.add(summaryCard);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartWrapper, summaryWrapper);
        split.setDividerLocation(580);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(AppTheme.BG);
        return split;
    }

    private JPanel buildBottom() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(AppTheme.BG);
        outer.setBorder(new EmptyBorder(0, 12, 12, 12));

        RoundedPanel card = new RoundedPanel(18, AppTheme.SURFACE);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        card.add(buildSubjectRow(), BorderLayout.NORTH);

        // Tabs customizadas (sem fundo branco do JTabbedPane)
        CustomTabs tabs = new CustomTabs();
        tabs.addTab("⏱  Cronômetro",       buildStopwatchTab());
        tabs.addTab("✏  Manual",           buildManualTab());
        tabs.addTab("🍅  Pomodoro",         buildPomodoroTab());
        tabs.setPreferredSize(new Dimension(0, 175));
        card.add(tabs, BorderLayout.CENTER);

        card.add(buildManagementRow(), BorderLayout.SOUTH);

        outer.add(card);
        return outer;
    }

    private JPanel buildSubjectRow() {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel lbl = new JLabel("Matéria:");
        lbl.setFont(AppTheme.FONT_SMALL);
        lbl.setForeground(AppTheme.TEXT_SEC);
        subjectComboBox = new JComboBox<>();
        subjectComboBox.setFont(AppTheme.FONT_LABEL);
        subjectComboBox.setPreferredSize(new Dimension(200, 30));
        subjectComboBox.addActionListener(e -> updateGoalProgress());
        left.add(lbl); left.add(subjectComboBox);

        StyledButton btnGoal = new StyledButton("🎯 Meta", StyledButton.Variant.TEXT);
        btnGoal.setFont(AppTheme.FONT_SMALL);
        btnGoal.addActionListener(e -> showGoalDialog());
        left.add(btnGoal);

        // Barra de meta (oculta por padrão)
        goalBar = new JPanel(new BorderLayout(8, 0));
        goalBar.setOpaque(false);
        goalBar.setVisible(false);

        tvGoalLabel   = new JLabel("Meta: 0h");
        tvGoalLabel.setFont(AppTheme.FONT_SMALL); tvGoalLabel.setForeground(AppTheme.TEXT_SEC);
        tvGoalPercent = new JLabel("0%");
        tvGoalPercent.setFont(AppTheme.FONT_SMALL); tvGoalPercent.setForeground(AppTheme.ACCENT);
        goalProgress = new JProgressBar(0, 100);
        goalProgress.setStringPainted(false);
        goalProgress.setForeground(AppTheme.ACCENT);
        goalProgress.setBackground(AppTheme.SURFACE2);
        goalProgress.setPreferredSize(new Dimension(0, 6));
        goalProgress.setBorderPainted(false);

        JPanel gpRow = new JPanel(new BorderLayout(4, 0));
        gpRow.setOpaque(false);
        gpRow.add(tvGoalLabel,   BorderLayout.WEST);
        gpRow.add(goalProgress,  BorderLayout.CENTER);
        gpRow.add(tvGoalPercent, BorderLayout.EAST);
        goalBar.add(gpRow);

        row.add(left,    BorderLayout.WEST);
        row.add(goalBar, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildManagementRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8, 0, 0, 0));

        StyledButton btnAdd    = new StyledButton("+ Matéria",  StyledButton.Variant.TONAL);
        StyledButton btnEdit   = new StyledButton("Renomear",   StyledButton.Variant.OUTLINED);
        StyledButton btnColor  = new StyledButton("Cor",        StyledButton.Variant.OUTLINED);
        StyledButton btnZero   = new StyledButton("Zerar",      StyledButton.Variant.OUTLINED);
        StyledButton btnDelete = new StyledButton("Deletar",    StyledButton.Variant.DANGER);

        btnAdd.addActionListener(e    -> addNewSubject());
        btnEdit.addActionListener(e   -> editSubjectName());
        btnColor.addActionListener(e  -> changeSubjectColor());
        btnZero.addActionListener(e   -> zeroOutSubjectTime());
        btnDelete.addActionListener(e -> deleteSubject());

        for (StyledButton b : new StyledButton[]{btnAdd, btnEdit, btnColor, btnZero, btnDelete}) row.add(b);
        return row;
    }

    // ── TABS ────────────────────────────────────────────────────────────────

    private JPanel buildStopwatchTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 12));
        p.setBackground(AppTheme.SURFACE);
        stopwatchLabel = new JLabel("00:00:00");
        stopwatchLabel.setFont(AppTheme.FONT_MONO_S);
        stopwatchLabel.setForeground(AppTheme.TEXT_PRI);
        startButton  = new StyledButton("Iniciar",        StyledButton.Variant.FILLED);
        stopButton   = new StyledButton("Parar",          StyledButton.Variant.OUTLINED);
        submitButton = new StyledButton("Salvar Tempo",   StyledButton.Variant.TONAL);
        stopButton.setEnabled(false); submitButton.setEnabled(false);
        startButton.addActionListener(e  -> startStopwatch());
        stopButton.addActionListener(e   -> stopStopwatch());
        submitButton.addActionListener(e -> submitStopwatchTime());
        p.add(stopwatchLabel); p.add(startButton); p.add(stopButton); p.add(submitButton);
        return p;
    }

    private JPanel buildManualTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 18));
        p.setBackground(AppTheme.SURFACE);
        hoursField   = styledField("Horas");
        minutesField = styledField("Minutos");
        StyledButton addBtn = new StyledButton("Adicionar Tempo", StyledButton.Variant.FILLED);
        addBtn.addActionListener(e -> addManualTime());
        p.add(new JLabel("Horas:")   {{ setFont(AppTheme.FONT_LABEL); setForeground(AppTheme.TEXT_SEC); }});
        p.add(hoursField);
        p.add(new JLabel("Minutos:") {{ setFont(AppTheme.FONT_LABEL); setForeground(AppTheme.TEXT_SEC); }});
        p.add(minutesField);
        p.add(Box.createHorizontalStrut(12));
        p.add(addBtn);
        return p;
    }

    private JPanel buildPomodoroTab() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(AppTheme.SURFACE);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        top.setBackground(AppTheme.SURFACE);
        focusSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 120, 1));
        breakSpinner = new JSpinner(new SpinnerNumberModel(5,  1,  60, 1));
        styleSpinner(focusSpinner); styleSpinner(breakSpinner);

        top.add(lbl("Foco (min):")); top.add(focusSpinner);
        top.add(Box.createHorizontalStrut(16));
        top.add(lbl("Pausa (min):")); top.add(breakSpinner);

        // Layout vertical centralizado: status | countdown | botões
        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.setOpaque(false);

        statusLabel = new JLabel("Pronto para iniciar", SwingConstants.CENTER);
        statusLabel.setFont(AppTheme.FONT_SMALL);
        statusLabel.setForeground(AppTheme.ACCENT);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        countdownLabel = new JLabel("25:00", SwingConstants.CENTER);
        countdownLabel.setFont(AppTheme.FONT_TIMER_S);
        countdownLabel.setForeground(AppTheme.TEXT_PRI);
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Foco: inicia sessão de foco
        startFocusButton    = new StyledButton("▶ Foco",   StyledButton.Variant.FILLED);
        // Descanso: inicia pausa/descanso
        startBreakButton    = new StyledButton("☕ Descanso", StyledButton.Variant.TONAL);
        // Pausar/Retomar: pausa e retoma o timer atual
        stopCountdownButton = new StyledButton("⏸ Pausar", StyledButton.Variant.OUTLINED);
        // Resetar: cancela tudo
        resetButton         = new StyledButton("↺ Resetar", StyledButton.Variant.TEXT);

        stopCountdownButton.setEnabled(false);
        resetButton.setEnabled(false);
        // Descanso sempre disponível, não depende de ter rodado Foco antes
        startBreakButton.setEnabled(true);

        startFocusButton.addActionListener(e    -> startCountdown(true));
        startBreakButton.addActionListener(e    -> startCountdown(false));
        stopCountdownButton.addActionListener(e -> togglePauseCountdown());
        resetButton.addActionListener(e         -> stopCountdown(false));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.setOpaque(false);
        buttons.add(startFocusButton);
        buttons.add(startBreakButton);
        buttons.add(stopCountdownButton);
        buttons.add(resetButton);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

        mid.add(Box.createVerticalStrut(2));
        mid.add(statusLabel);
        mid.add(countdownLabel);
        mid.add(Box.createVerticalStrut(4));
        mid.add(buttons);

        p.add(top,    BorderLayout.NORTH);
        p.add(mid,    BorderLayout.CENTER);
        return p;
    }

    // ── HELPERS UI ──────────────────────────────────────────────────────────

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text); l.setFont(AppTheme.FONT_SMALL); l.setForeground(AppTheme.TEXT_SEC); return l;
    }

    private JTextField styledField(String hint) {
        JTextField f = new JTextField(6);
        f.setFont(AppTheme.FONT_LABEL);
        f.setToolTipText(hint);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.DIVIDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)));
        return f;
    }

    private void styleSpinner(JSpinner sp) {
        sp.setFont(AppTheme.FONT_LABEL);
        sp.setPreferredSize(new Dimension(65, 28));
        ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setFont(AppTheme.FONT_LABEL);
    }

    // ── TIMERS ──────────────────────────────────────────────────────────────

    private void setupTimers() {
        stopwatch = new Timer(1000, e -> { elapsedSeconds++; updateStopwatchLabel(); });
        countdownTimer = new Timer(1000, e -> {
            countdownSecondsRemaining--;
            updateCountdownLabel();
            if (countdownSecondsRemaining <= 0) stopCountdown(true);
        });
    }

    // ── STOPWATCH ───────────────────────────────────────────────────────────

    private void startStopwatch() {
        if (subjectComboBox.getSelectedItem() == null) {
            toast("Selecione uma matéria!"); return;
        }
        stopwatch.start();
        startButton.setEnabled(false); stopButton.setEnabled(true);
        submitButton.setEnabled(false); subjectComboBox.setEnabled(false);
    }

    private void stopStopwatch() {
        stopwatch.stop();
        startButton.setEnabled(true); stopButton.setEnabled(false);
        submitButton.setEnabled(elapsedSeconds > 0); subjectComboBox.setEnabled(true);
    }

    private void submitStopwatchTime() {
        if (elapsedSeconds == 0) return;
        String subject = (String) subjectComboBox.getSelectedItem();
        int mins = (int) Math.round(elapsedSeconds / 60.0);
        if (mins > 0) {
            addTime(subject, mins, "cronometro");
            toast(formatTime(mins * 60) + " salvo em " + subject);
        } else {
            toast("Tempo muito curto para registrar.");
        }
        elapsedSeconds = 0; updateStopwatchLabel(); submitButton.setEnabled(false);
    }

    private void updateStopwatchLabel() {
        int h = elapsedSeconds / 3600, m = (elapsedSeconds % 3600) / 60, s = elapsedSeconds % 60;
        stopwatchLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    // ── POMODORO ────────────────────────────────────────────────────────────

    private void startCountdown(boolean isFocus) {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (isFocus && subject == null) { toast("Selecione uma matéria!"); return; }
        if (countdownTimer != null) countdownTimer.stop(); // garante parado antes de reiniciar
        isPaused = false;
        wasFocusSession = isFocus;
        plannedMinutes  = isFocus
                ? (int) focusSpinner.getValue()
                : (int) breakSpinner.getValue();
        countdownSecondsRemaining = plannedMinutes * 60;
        statusLabel.setText(isFocus ? "Focando em " + subject + "…" : "Em pausa…");
        statusLabel.setForeground(isFocus ? AppTheme.SUCCESS : AppTheme.ACCENT);
        updateCountdownLabel();
        isPaused = false;
        countdownTimer.start();
        startFocusButton.setEnabled(false);
        startBreakButton.setEnabled(false);
        stopCountdownButton.setEnabled(true);
        stopCountdownButton.setText("⏸ Pausar");
        resetButton.setEnabled(true);
        subjectComboBox.setEnabled(false);
    }

    private void stopCountdown(boolean finished) {
        countdownTimer.stop();
        if (finished) {
            Toolkit.getDefaultToolkit().beep();
            String subject = (String) subjectComboBox.getSelectedItem();
            if (wasFocusSession && subject != null) {
                addTime(subject, plannedMinutes, "pomodoro");
                showInfoDialog("✅ Foco concluído!", plannedMinutes + " min adicionados a " + subject + ".");
            } else {
                showInfoDialog("☕ Pausa concluída!", "Pronto para mais uma sessão?");
            }
        }
        statusLabel.setText("Pronto para iniciar"); statusLabel.setForeground(AppTheme.ACCENT);
        countdownSecondsRemaining = (int) focusSpinner.getValue() * 60;
        updateCountdownLabel();
        isPaused = false;
        startFocusButton.setEnabled(true); startBreakButton.setEnabled(true);
        stopCountdownButton.setEnabled(false);
        stopCountdownButton.setText("⏸ Pausar");
        resetButton.setEnabled(false);
        subjectComboBox.setEnabled(true);
    }

    private void togglePauseCountdown() {
        if (isPaused) {
            // Retomar
            isPaused = false;
            countdownTimer.start();
            stopCountdownButton.setText("⏸ Pausar");
            statusLabel.setText(wasFocusSession
                    ? "Focando em " + subjectComboBox.getSelectedItem() + "…"
                    : "Em pausa…");
        } else {
            // Pausar
            isPaused = true;
            countdownTimer.stop();
            stopCountdownButton.setText("▶ Retomar");
            statusLabel.setText("⏸ Pausado");
            statusLabel.setForeground(AppTheme.WARNING);
        }
    }

    private void updateCountdownLabel() {
        int m = countdownSecondsRemaining / 60, s = countdownSecondsRemaining % 60;
        countdownLabel.setText(String.format("%02d:%02d", m, s));
    }

    // ── MANUAL ──────────────────────────────────────────────────────────────

    private void addManualTime() {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (subject == null) { toast("Selecione uma matéria!"); return; }
        try {
            int h   = hoursField.getText().trim().isEmpty()   ? 0 : Integer.parseInt(hoursField.getText().trim());
            int m   = minutesField.getText().trim().isEmpty() ? 0 : Integer.parseInt(minutesField.getText().trim());
            int tot = h * 60 + m;
            if (tot > 0) {
                addTime(subject, tot, "manual");
                hoursField.setText(""); minutesField.setText("");
                toast(formatTime(tot * 60) + " adicionado a " + subject);
            } else { toast("Insira um tempo válido."); }
        } catch (NumberFormatException ex) { toast("Apenas números, por favor."); }
    }

    // ── DATA (tempo + sessão) ────────────────────────────────────────────────

    private void addTime(String subject, int minutes, String type) {
        studyDataMap.get(subject).addMinutes(minutes);
        sessions.add(new StudySession(subject, minutes, System.currentTimeMillis(), type));
        updateUI();
        saveData(); saveSessions();
    }

    // ── METAS ───────────────────────────────────────────────────────────────

    private void showGoalDialog() {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (subject == null) { toast("Selecione uma matéria!"); return; }
        int cur = goals.getOrDefault(subject, 0);
        String input = JOptionPane.showInputDialog(this,
                "Meta diária de estudo para \"" + subject + "\" (em minutos, 0 = sem meta):",
                cur > 0 ? String.valueOf(cur) : "");
        if (input == null) return;
        try {
            int goal = input.trim().isEmpty() ? 0 : Integer.parseInt(input.trim());
            goals.put(subject, goal);
            updateGoalProgress();
            if (goal > 0) toast("Meta: " + goal + " min/dia para " + subject);
        } catch (NumberFormatException ex) { toast("Insira apenas números."); }
    }

    private void updateGoalProgress() {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (subject == null || !goals.containsKey(subject) || goals.get(subject) <= 0) {
            goalBar.setVisible(false); return;
        }
        int goal    = goals.get(subject);
        int current = studyDataMap.getOrDefault(subject, new StudyData(0, Color.GRAY)).getMinutes();
        int pct     = (int) Math.min(100, current * 100.0 / goal);
        goalBar.setVisible(true);
        int gh = current / 60, gm = current % 60, goalH = goal / 60, goalM = goal % 60;
        tvGoalLabel.setText(String.format("%dh%02dm / %dh%02dm", gh, gm, goalH, goalM));
        tvGoalPercent.setText(pct + "%");
        goalProgress.setValue(pct);
    }

    // ── GERENCIAMENTO DE MATÉRIAS ────────────────────────────────────────────

    private void addNewSubject() {
        String name = JOptionPane.showInputDialog(this, "Nome da nova matéria:", "Adicionar Matéria", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        if (studyDataMap.containsKey(name)) { toast("Essa matéria já existe!"); return; }
        studyDataMap.put(name, new StudyData(0, AppTheme.nextColor(studyDataMap.size())));
        refreshCombo(); subjectComboBox.setSelectedItem(name); updateUI();
    }

    private void editSubjectName() {
        String old = (String) subjectComboBox.getSelectedItem(); if (old == null) return;
        String nw = JOptionPane.showInputDialog(this, "Novo nome:", old);
        if (nw == null || nw.trim().isEmpty() || nw.trim().equals(old)) return;
        nw = nw.trim();
        if (studyDataMap.containsKey(nw)) { toast("Esse nome já existe!"); return; }
        StudyData d = studyDataMap.remove(old);
        // Reconstrói mantendo ordem
        Map<String, StudyData> tmp = new LinkedHashMap<>();
        for (Map.Entry<String, StudyData> e : studyDataMap.entrySet()) tmp.put(e.getKey(), e.getValue());
        studyDataMap.clear();
        for (Map.Entry<String, StudyData> e : tmp.entrySet()) {
            studyDataMap.put(e.getKey().equals(old) ? nw : e.getKey(), e.getValue());
        }
        studyDataMap.put(nw, d);
        if (goals.containsKey(old)) { goals.put(nw, goals.remove(old)); }
        refreshCombo(); subjectComboBox.setSelectedItem(nw); updateUI();
    }

    private void changeSubjectColor() {
        String s = (String) subjectComboBox.getSelectedItem(); if (s == null) return;
        Color c = JColorChooser.showDialog(this, "Cor de " + s, studyDataMap.get(s).getColor());
        if (c != null) { studyDataMap.get(s).setColor(c); updateUI(); }
    }

    private void zeroOutSubjectTime() {
        String s = (String) subjectComboBox.getSelectedItem(); if (s == null) return;
        int r = JOptionPane.showConfirmDialog(this, "Zerar tempo de \"" + s + "\"?", "Confirmar", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) { studyDataMap.get(s).setMinutes(0); updateUI(); toast("Tempo zerado."); }
    }

    private void deleteSubject() {
        String s = (String) subjectComboBox.getSelectedItem(); if (s == null) return;
        int r = JOptionPane.showConfirmDialog(this, "Deletar \"" + s + "\"? Todo o tempo será perdido.", "Deletar Matéria", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) { studyDataMap.remove(s); goals.remove(s); refreshCombo(); updateUI(); }
    }

    // ── HISTÓRICO ───────────────────────────────────────────────────────────

    private void showHistory() {
        new HistoryPanel(this, sessions, studyDataMap).setVisible(true);
    }

    // ── TEMA ────────────────────────────────────────────────────────────────

    private void toggleTheme() {
        String[] options = {"☀  Claro", "🌙  Escuro"};
        int current = AppTheme.dark ? 1 : 0;
        int choice = JOptionPane.showOptionDialog(this,
                "Escolha o tema da interface:",
                "Tema",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, options, options[current]);
        if (choice < 0) return;
        if (choice == 0) AppTheme.applyLight();
        else             AppTheme.applyDark();
        rebuildUI();
    }

    private void rebuildUI() {
        // Salva estado dos timers
        boolean swRunning = stopwatch != null && stopwatch.isRunning();
        boolean cdRunning = countdownTimer != null && countdownTimer.isRunning();
        if (swRunning) stopwatch.stop();
        if (cdRunning) countdownTimer.stop();

        getContentPane().removeAll();
        getContentPane().setBackground(AppTheme.BG);
        buildUI();

        // Reconecta listeners e restaura estado
        setupTimers();
        if (swRunning) stopwatch.start();
        if (cdRunning) countdownTimer.start();

        refreshCombo();
        updateUI();
        revalidate();
        repaint();
    }

    // ── UPDATE UI ───────────────────────────────────────────────────────────

    private void updateUI() {
        // Total header
        int total = studyDataMap.values().stream().mapToInt(StudyData::getMinutes).sum();
        int th = total / 60, tm = total % 60;
        tvTotal.setText(String.format("Total: %dh %02dmin", th, tm));

        // Resumo lateral
        summaryPanel.removeAll();
        for (Map.Entry<String, StudyData> e : studyDataMap.entrySet()) {
            summaryPanel.add(buildSummaryRow(e.getKey(), e.getValue()));
            summaryPanel.add(Box.createVerticalStrut(6));
        }
        summaryPanel.add(Box.createVerticalStrut(4));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(AppTheme.DIVIDER);
        summaryPanel.add(sep);
        summaryPanel.add(Box.createVerticalStrut(6));
        summaryPanel.add(buildSummaryRow("TOTAL", new StudyData(total, AppTheme.TEXT_PRI)));
        summaryPanel.revalidate(); summaryPanel.repaint();

        chartPanel.repaint();
        updateGoalProgress();
        saveData();
    }

    private JPanel buildSummaryRow(String name, StudyData sd) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JPanel dotName = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dotName.setOpaque(false);

        JPanel dot = new JPanel() {
            final Color c = sd.getColor();
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c); g2.fillOval(0, 4, 10, 10); g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(10, 18); }
        };
        dot.setOpaque(false);

        JLabel lblName = new JLabel(name);
        lblName.setFont(name.equals("TOTAL") ? AppTheme.FONT_BOLD : AppTheme.FONT_LABEL);
        lblName.setForeground(AppTheme.TEXT_PRI);

        dotName.add(dot); dotName.add(lblName);

        int h = sd.getMinutes() / 60, m = sd.getMinutes() % 60;
        JLabel lblTime = new JLabel(String.format("%dh %02dmin", h, m));
        lblTime.setFont(name.equals("TOTAL") ? AppTheme.FONT_BOLD : AppTheme.FONT_SMALL);
        lblTime.setForeground(name.equals("TOTAL") ? AppTheme.ACCENT : AppTheme.TEXT_SEC);

        row.add(dotName, BorderLayout.WEST);
        row.add(lblTime, BorderLayout.EAST);
        return row;
    }

    private void refreshCombo() {
        subjectComboBox.removeAllItems();
        studyDataMap.keySet().forEach(subjectComboBox::addItem);
    }

    // ── PERSISTÊNCIA ────────────────────────────────────────────────────────

    private void saveData() {
        Properties p = new Properties();
        studyDataMap.forEach((k, v) -> p.setProperty(k, v.getMinutes() + "," + v.getColor().getRGB()));
        goals.forEach((k, v) -> p.setProperty("goal_" + k, String.valueOf(v)));
        try (FileOutputStream out = new FileOutputStream(SAVE_FILE)) {
            p.setProperty("__theme__", AppTheme.dark ? "dark" : "light");
            p.store(out, "FocaEstudo Data");
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadData() {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(SAVE_FILE)) {
            p.load(in);
            for (String k : p.stringPropertyNames()) {
                if (k.equals("__theme__")) {
                    if ("dark".equals(p.getProperty(k))) AppTheme.applyDark();
                    else AppTheme.applyLight();
                    continue;
                }
                if (k.startsWith("goal_")) {
                    try { goals.put(k.substring(5), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                String[] parts = p.getProperty(k).split(",");
                if (parts.length == 2) {
                    try { studyDataMap.put(k, new StudyData(Integer.parseInt(parts[0]), new Color(Integer.parseInt(parts[1])))); }
                    catch (Exception ignored) {}
                }
            }
        } catch (IOException e) { /* primeiro uso */ }
    }

    private void saveSessions() {
        Properties p = new Properties();
        for (int i = 0; i < sessions.size(); i++) {
            p.setProperty("s" + i, sessions.get(i).serialize());
        }
        try (FileOutputStream out = new FileOutputStream(SESSION_FILE)) {
            p.store(out, "FocaEstudo Sessions");
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadSessions() {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(SESSION_FILE)) {
            p.load(in);
            List<StudySession> tmp = new ArrayList<>();
            for (String k : p.stringPropertyNames()) {
                StudySession s = StudySession.deserialize(p.getProperty(k));
                if (s != null) tmp.add(s);
            }
            tmp.sort(Comparator.comparingLong(StudySession::getTimestamp));
            sessions.addAll(tmp);
        } catch (IOException e) { /* primeiro uso */ }
    }

    // ── UTILS ───────────────────────────────────────────────────────────────

    private void toast(String msg) {
        JWindow w = new JWindow(this);
        JLabel lbl = new JLabel("  " + msg + "  ");
        lbl.setFont(AppTheme.FONT_LABEL);
        lbl.setForeground(Color.WHITE);
        lbl.setBorder(new EmptyBorder(8, 14, 8, 14));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(0x2A2D45));
        panel.setBorder(BorderFactory.createLineBorder(new Color(0x3A3D55), 1));
        panel.add(lbl);
        w.setContentPane(panel);
        w.pack();
        w.setLocation(getX() + (getWidth() - w.getWidth()) / 2, getY() + getHeight() - w.getHeight() - 60);
        w.setVisible(true);
        new Timer(2200, e -> { w.dispose(); ((Timer) e.getSource()).stop(); }).start();
    }

    private void showInfoDialog(String title, String msg) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatTime(int totalSeconds) {
        int h = totalSeconds / 3600, m = (totalSeconds % 3600) / 60;
        if (h > 0) return h + "h " + m + "min";
        return m + " min";
    }

    private void initializeUI() {
        if (studyDataMap.isEmpty()) promptForSubjects();
        refreshCombo(); updateUI(); setVisible(true);
    }

    private void promptForSubjects() {
        String input = JOptionPane.showInputDialog(this,
                "Bem-vindo ao FocaEstudo!\nDigite suas matérias separadas por vírgula:",
                "Configuração Inicial", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.trim().isEmpty()) { System.exit(0); return; }
        int idx = 0;
        for (String s : input.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) studyDataMap.put(t, new StudyData(0, AppTheme.nextColor(idx++)));
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new StudyTracker().initializeUI());
    }
}
