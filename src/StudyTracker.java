import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.List;

public class StudyTracker extends JFrame {

    // Os dados ficam na pasta do usuário do Windows (%APPDATA%\FocaEstudo), não na
    // pasta do programa: trocar de versão não perde nada. Leitura/gravação dos arquivos,
    // backup e migração ficam no StudyStore (testado em test/StudyStoreTest.java).
    // BASE_DIR (pasta do programa) só serve para migrar os dados das versões até a 1.0.2.
    private static final File BASE_DIR = StudyStore.resolveBaseDir();
    private static final StudyStore STORE = new StudyStore(StudyStore.resolveDataDir(BASE_DIR));
    private static final File DATA_DIR = STORE.dir;
    private static File migratedFrom = null;   // != null ⇒ avisar o usuário que os dados mudaram de lugar

    private final Map<String, StudyData>  studyDataMap = new LinkedHashMap<>();
    private final List<StudySession>      sessions     = new ArrayList<>();
    private final List<ChecklistItem>     checklist    = new ArrayList<>();

    // --- UI principal ---
    private JComboBox<String> subjectComboBox;
    private JLabel        tvTotal;
    private JPanel        goalBar;
    private GaugeBar      goalProgress;
    private JLabel        tvGoalLabel, tvGoalPercent;
    private NowCard       nowCard;
    private CustomTabs    bottomTabs;

    // --- Cronômetro: estado em StopwatchModel; o Timer do Swing só dá o tique de 1 s ---
    private final StopwatchModel sw = new StopwatchModel();
    private Timer  stopwatch;
    private JLabel stopwatchLabel;
    private JLabel stopwatchInfoLabel;         // "líquido · N pausas · bruto mm:ss"
    private StyledButton startButton, stopButton, submitButton, resetStopwatchButton;

    // --- Pomodoro: estado em PomodoroModel; o Timer do Swing só dá o tique de 1 s ---
    private final PomodoroModel pomo = new PomodoroModel();
    private Timer   countdownTimer;
    private JLabel  countdownLabel, statusLabel, pomoCountLabel;
    private JSpinner focusSpinner, breakSpinner;
    private JCheckBox autoCycleCheck;
    private StyledButton startFocusButton, startBreakButton, stopCountdownButton, resetButton;

    // --- Inatividade ---
    private Timer idleTimer;
    private long  lastActivityMs = System.currentTimeMillis();
    private boolean idleAsking = false;

    // --- Ajustes + marcadores "já mostrei" (persistidos em study_data como __chave__; ver AppSettings) ---
    private final AppSettings cfg = new AppSettings();

    // --- Recorrências ---
    private Timer   reminderTimer;
    private java.awt.TrayIcon trayIcon;

    // --- Manual ---
    private JTextField hoursField, minutesField;
    private JComboBox<DayOption> manualDayCombo;

    // --- Detalhes da sessão: perguntados AO TERMINAR, não antes (menos decisões para começar) ---
    private String pendingSubFoco;   // sub-foco já escolhido (ex.: rodízio do "Agora"); vem marcado no "Como foi?"
    private static final String[] ENERGY_TEXT = {"", "Baixa", "Ok", "Alta"};
    private static final String[] KIND_LABELS = {"–", "Teoria", "Exercícios", "Revisão", "Outro"};
    private static final String[] KIND_KEYS   = {"", "teoria", "exercicios", "revisao", "outro"};

    // Sub-focos por área: tópicos / grupos musculares / hobbies. Ordem preservada.
    private final Map<String, java.util.List<String>> subFocos = new LinkedHashMap<>();

    private static String[] subFocoPreset(String type) {
        switch (type) {
            case "fisico": return new String[]{"Peito", "Costas", "Perna", "Ombro", "Braço", "Core", "Cardio"};
            case "estudo": return new String[]{"Teoria", "Exercícios", "Revisão"};
            default:       return new String[0];
        }
    }

    // --- Áreas: tipo de cada área ("estudo"|"fisico"|"lazer"|"trabalho"|"outro"). Ausente = "estudo". ---
    private final Map<String, String> areaType = new LinkedHashMap<>();
    private static final String[] TYPE_KEYS   = AreaTypes.KEYS;
    private static final String[] TYPE_LABELS = AreaTypes.LABELS;
    // Meta mínima semanal por TIPO (minutos). 0/ausente = sem meta de equilíbrio.
    private final Map<String, Integer> typeGoalWeek = new LinkedHashMap<>();

    // --- Metas (em minutos, por matéria; 0 = sem meta) ---
    private final Map<String, Integer> goals      = new LinkedHashMap<>(); // diária IDEAL
    private final Map<String, Integer> goalsMin   = new LinkedHashMap<>(); // diária MÍNIMA (ausente = = ideal)
    private final Map<String, Integer> goalsWeek  = new LinkedHashMap<>(); // semanal
    private final Map<String, Integer> goalsMonth = new LinkedHashMap<>(); // mensal
    // Dias marcados como "difícil" (ISO date): não cobram, não quebram a sequência.
    private final java.util.Set<String> hardDays = new java.util.LinkedHashSet<>();

    // Semana planejada: chave "<dia 1-7>_<turno m|t|n>" -> áreas daquele turno. Ausente = vazio.
    // Gravado como plan_1_m=PBD|FIA (versões até 1.1.0 aceitavam só uma área por turno).
    private final Map<String, java.util.List<String>> weekPlan = new LinkedHashMap<>();

    // Dias da semana em que a meta diária vale (ISO: 1=seg … 7=dom). Ausente = todos os dias.
    private final Map<String, java.util.Set<java.time.DayOfWeek>> goalDays = new LinkedHashMap<>();
    // Data de prova por matéria (opcional).
    private final Map<String, LocalDate> examDate = new LinkedHashMap<>();
    // Matérias arquivadas: somem da lista mas mantêm histórico e totais.
    private final java.util.Set<String> archived = new java.util.LinkedHashSet<>();

    // --- Streaks: melhor sequência (recorde) persistida ---
    private final Map<String, Integer> streakBest = new LinkedHashMap<>(); // recorde por matéria
    private int streakBestGeneral = 0;                                     // recorde geral
    private int celebratedStreakMilestone = 0;                             // maior marco já comemorado

    // Cálculos de progresso (minutos, metas, sequências). Lê as MESMAS coleções acima —
    // declarado depois delas de propósito (ordem de inicialização dos campos).
    private final StudyStats stats = new StudyStats(sessions, studyDataMap.keySet(), archived,
            goals, goalsMin, goalsWeek, goalsMonth, goalDays, hardDays, java.time.Clock.systemDefaultZone());

    // Criar/renomear/arquivar/apagar áreas mexendo em TODAS as coleções por área (ver Areas).
    private final Areas areas = new Areas(studyDataMap, sessions, checklist, goals, goalsMin, goalsWeek,
            goalsMonth, streakBest, goalDays, examDate, archived, areaType, subFocos, weekPlan);

    // Regras do plano da semana (turnos planejados, "Preencher pelas metas"). Mesmas coleções.
    private final WeekPlan planRules = new WeekPlan(weekPlan, studyDataMap, archived, goalDays, stats, this::typeOf);

    // --- Estado de persistência ---
    private boolean ready  = false;  // true quando a UI terminou de carregar
    private boolean loadOk = true;   // false se a leitura do save falhou (bloqueia gravação)
    private final Map<String, String> unknownProps = new LinkedHashMap<>(); // linhas do save que não soubemos ler

    public StudyTracker() {
        loadData();
        loadSessions();
        loadChecklist();

        setTitle("FocaEstudo");
        setIconImages(AppTheme.appIcons());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1150, 820);
        setMinimumSize(new Dimension(900, 680));
        setLocationRelativeTo(null);
        getContentPane().setBackground(AppTheme.BG);
        setLayout(new BorderLayout(0, 0));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { saveData(); saveSessions(); saveChecklist(); System.exit(0); }
        });

        buildUI();
        setupTimers();
        setupShortcuts();
        setupIdleWatch();
    }

    // ── ATALHOS DE TECLADO ──────────────────────────────────────────────────

    private void setupShortcuts() {
        JRootPane rp = getRootPane();
        bindKey(rp, KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), "hist",  this::showHistory);
        bindKey(rp, KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK), "theme", this::toggleTheme);
        bindKey(rp, KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK), "goal",  this::showGoalDialog);

        // Espaço = iniciar/pausar o cronômetro. Precisa ser global (um botão com
        // foco "engoliria" o espaço), por isso via KeyEventDispatcher.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_SPACE) return false;
            KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            Window fw = kfm.getFocusedWindow();
            if (fw != this && (focusBar == null || fw != focusBar)) return false;   // diálogo aberto: ignora
            if (kfm.getFocusOwner() instanceof javax.swing.text.JTextComponent) return false;
            if (focusBar != null) focusTogglePause(); else toggleStopwatch();
            return true;   // consome o espaço
        });
    }

    private static void bindKey(JRootPane rp, KeyStroke ks, String name, Runnable action) {
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        rp.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    /** Espaço: inicia se parado, pausa se rodando, retoma se pausado com tempo. */
    private void toggleStopwatch() {
        if (sw.isRunning()) stopStopwatch();
        else                                            startStopwatch();
    }

    // ── INATIVIDADE ─────────────────────────────────────────────────────────

    private void setupIdleWatch() {
        Toolkit.getDefaultToolkit().addAWTEventListener(
                e -> lastActivityMs = System.currentTimeMillis(),
                AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

        idleTimer = new Timer(20_000, e -> checkIdle());
        idleTimer.start();
    }

    private void checkIdle() {
        if (idleAsking || !sw.isRunning()) return;
        if (!StopwatchModel.idleTooLong(System.currentTimeMillis(), lastActivityMs, cfg.idleMinutes)) return;

        idleAsking = true;
        stopStopwatch();                       // pausa enquanto pergunta
        int r = JOptionPane.showConfirmDialog(dialogOwner(),
                "Sem atividade há " + cfg.idleMinutes + " min.\nAinda está estudando?",
                "Continuar?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        idleAsking = false;
        lastActivityMs = System.currentTimeMillis();
        if (r == JOptionPane.YES_OPTION) startStopwatch();   // retoma
        // NÃO: continua pausado; o usuário decide salvar ou zerar
    }

    // ── BUILD UI ────────────────────────────────────────────────────────────

    // A janela é uma barra lateral + uma tela por vez (Hoje, Semana, Progresso, Áreas).
    // Antes era tudo numa tela só: o cronômetro dominava e os cartões ficavam espremidos.
    private static final String SCR_HOJE = "hoje", SCR_SEMANA = "semana",
                                SCR_PROGRESSO = "progresso", SCR_AREAS = "areas";
    private SideNav      sideNav;
    private JPanel       screens;
    private String       currentScreen = SCR_HOJE;
    private PlannerPanel weekScreen;
    private AreasScreen  areasScreen;
    private ProgressScreen progressScreen;

    private void buildUI() {
        screens = new JPanel(new CardLayout());
        screens.setBackground(AppTheme.BG);
        screens.add(buildTodayScreen(),    SCR_HOJE);
        screens.add(buildWeekScreen(),     SCR_SEMANA);
        screens.add(buildProgressScreen(), SCR_PROGRESSO);
        screens.add(buildAreasScreen(),    SCR_AREAS);

        add(buildSideNav(), BorderLayout.WEST);
        add(screens,        BorderLayout.CENTER);
        showScreen(currentScreen);
    }

    private void showScreen(String key) {
        currentScreen = key;
        ((CardLayout) screens.getLayout()).show(screens, key);
        sideNav.select(key);
    }

    private SideNav buildSideNav() {
        sideNav = new SideNav("FocaEstudo");
        tvTotal = sideNav.subtitle();
        sideNav.addScreen(SCR_HOJE,      "Hoje",      () -> showScreen(SCR_HOJE));
        sideNav.addScreen(SCR_SEMANA,    "Semana",    () -> showScreen(SCR_SEMANA));
        sideNav.addScreen(SCR_PROGRESSO, "Progresso", () -> showScreen(SCR_PROGRESSO));
        sideNav.addScreen(SCR_AREAS,     "Áreas",     () -> showScreen(SCR_AREAS));
        sideNav.addAction("Tarefas", () -> new ChecklistPanel(this, checklist, studyDataMap, () -> {
            saveChecklist(); refreshProgressCard();
        }).setVisible(true));
        sideNav.addAction("Ajustes", this::showSettingsDialog);
        sideNav.addAction("Tema",    this::toggleTheme);
        return sideNav;
    }

    // Cabeçalho, corpo rolável, botão pequeno e fmtHM moram no ScreenKit (comuns às telas).

    // ── TELA HOJE: o que fazer agora + cronômetro ──────────────────────────

    // Ordem de leitura de cima para baixo: o que fazer agora → o resto de hoje → a semana.
    // Metas completas e Equilíbrio foram para a tela Progresso; aqui só o que importa hoje.
    private TodayPanels todayPanels;   // cabeçalho, Turnos de hoje, Metas de hoje, Esta semana

    private JPanel buildTodayScreen() {
        todayPanels = new TodayPanels(new TodayPanels.Source() {
            public Map<String, StudyData> areas()  { return studyDataMap; }
            public Set<String> archived()          { return archived; }
            public String typeOf(String area)      { return StudyTracker.this.typeOf(area); }
            public StudyStats stats()              { return stats; }
            public int idealDaily(String area)     { return goals.getOrDefault(area, 0); }
            public WeekPlan plan()                 { return planRules; }
        }, new TodayPanels.Actions() {
            public void manageGoals()        { showManageGoalsDialog(); }
            public void openPlanner()        { StudyTracker.this.openPlanner(); }
            public void fillPlan()           { fillPlanFromGoals(); }
            public void start(String area)   { startArea(area, null); }
        });
        ScreenKit.ScrollBody body = new ScreenKit.ScrollBody(new GridBagLayout());
        body.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;

        JPanel meio = new JPanel(new GridLayout(1, 2, 12, 0));
        meio.setOpaque(false);
        meio.add(cardColumn(todayPanels.shiftsCard()));
        meio.add(cardColumn(todayPanels.goalsCard()));

        c.gridy = 0; body.add(buildNowCard(), c);
        c.gridy = 1; c.insets = new Insets(12, 0, 0, 0); body.add(meio, c);
        c.gridy = 2; body.add(todayPanels.weekStrip(), c);
        c.gridy = 3; c.weighty = 1; body.add(Box.createGlue(), c);

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppTheme.BG);
        p.add(todayPanels.header(), BorderLayout.NORTH);
        p.add(ScreenKit.quietScroll(body),  BorderLayout.CENTER);
        p.add(buildBottom(),      BorderLayout.SOUTH);
        return p;
    }

    /** Cartões empilhados no topo da coluna, cada um na sua altura (sem esticar para igualar a vizinha). */
    private static JPanel cardColumn(JComponent... cards) {
        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        for (int i = 0; i < cards.length; i++) {
            c.gridy = i;
            c.insets = new Insets(i > 0 ? 12 : 0, 0, 0, 0);
            col.add(cards[i], c);
        }
        c.gridy = cards.length; c.weighty = 1; c.insets = new Insets(0, 0, 0, 0);
        col.add(Box.createGlue(), c);   // sobra de altura vai para cá
        return col;
    }

    // ── TELA SEMANA: a grade inteira, sempre à mão ─────────────────────────

    private JComponent buildWeekScreen() {
        weekScreen = new PlannerPanel(activeAreas(), areaColors(), weekPlan,
                this::onPlanChanged, this::fillPlanFromGoals);
        return weekScreen;
    }

    private List<String> activeAreas() { return areas.active(); }

    private Map<String, Color> areaColors() {
        Map<String, Color> colorMap = new LinkedHashMap<>();
        studyDataMap.forEach((k, v) -> colorMap.put(k, v.getColor()));
        return colorMap;
    }

    // ── TELA ÁREAS: criar, editar, arquivar e deletar (a tela em si fica no AreasScreen) ──

    private JPanel buildAreasScreen() {
        areasScreen = new AreasScreen(studyDataMap, archived, this::typeOf, TYPE_KEYS, TYPE_LABELS,
                new AreasScreen.Actions() {
                    public void add()                  { addNewSubject(); }
                    public void subFocos(String area)  { showSubFocosDialog(area); }
                    public void edit(String area)      { editSubjectName(area); }
                    public void color(String area)     { changeSubjectColor(area); }
                    public void zero(String area)      { zeroOutSubjectTime(area); }
                    public void archive(String area)   { archiveSubject(area); }
                    public void unarchive(String area) { unarchiveSubject(area); }
                    public void delete(String area)    { deleteSubject(area); }
                });
        return areasScreen;
    }

    // ── TELA PROGRESSO: donut, números e calendário (a tela em si fica no ProgressScreen) ──

    private JPanel buildProgressScreen() {
        progressScreen = new ProgressScreen(new ProgressScreen.Source() {
            public Map<String, StudyData> areas()          { return studyDataMap; }
            public Set<String> archived()                  { return archived; }
            public String typeOf(String area)              { return StudyTracker.this.typeOf(area); }
            public StudyStats stats()                      { return stats; }
            public int bestStreak(String area)             { return StudyTracker.this.bestStreak(area); }
            public int minutesByType(String t, LocalDate a, LocalDate b) { return minutesByTypeInRange(t, a, b); }
            public int typeGoalWeek(String type)           { return typeGoalWeek.getOrDefault(type, 0); }
            public int idealDaily(String area)             { return goals.getOrDefault(area, 0); }
            public List<String> subFocos(String area)      { return subFocos.get(area); }
            public LocalDate examDate(String area)         { return examDate.get(area); }
            public List<ChecklistItem> checklist()         { return checklist; }
            public String selectedArea()                   { return (String) subjectComboBox.getSelectedItem(); }
            public List<StudySession> sessions()           { return sessions; }
            public Map<String, Integer> goals()            { return goals; }
        }, new ProgressScreen.Actions() {
            public void history()     { showHistory(); }
            public void stats()       { new StatsPanel(StudyTracker.this, sessions, studyDataMap).setVisible(true); }
            public void manageGoals() { showManageGoalsDialog(); }
            public void typeGoals()   { showTypeGoalsDialog(); }
        });
        return progressScreen;
    }

    /** Também chamado quando a área do combo muda ou as tarefas mudam. */
    private void refreshProgressCard() { if (progressScreen != null) progressScreen.refreshProgressCard(); }

    private JPanel buildBottom() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(AppTheme.BG);
        outer.setBorder(new EmptyBorder(0, 12, 12, 12));

        // Compacto: o cronômetro parado não precisa de meia tela (a Hoje é sobre o que fazer).
        RoundedPanel card = new RoundedPanel(18, AppTheme.SURFACE);
        card.setLayout(new BorderLayout(0, 2));
        card.setBorder(new EmptyBorder(10, 16, 8, 16));

        card.add(buildSubjectRow(), BorderLayout.NORTH);

        // Tabs customizadas (sem fundo branco do JTabbedPane)
        CustomTabs tabs = new CustomTabs();
        bottomTabs = tabs;
        // Sem ⏱ ✏ 🍅: a fonte do Swing no Windows mostra esses glifos como quadrados.
        tabs.addTab("Cronômetro", buildStopwatchTab());
        tabs.addTab("Manual",     buildManualTab());
        tabs.addTab("Pomodoro",   buildPomodoroTab());
        tabs.setPreferredSize(new Dimension(0, 130));
        card.add(tabs, BorderLayout.CENTER);

        outer.add(card);
        return outer;
    }

    private JPanel buildSubjectRow() {
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
        subjectComboBox = new JComboBox<>();
        styleInput(subjectComboBox);
        subjectComboBox.setPreferredSize(new Dimension(220, 32));
        subjectComboBox.addActionListener(e -> { pendingSubFoco = null; updateGoalProgress(); refreshProgressCard(); });
        left.add(lbl); left.add(subjectComboBox);

        StyledButton btnGoal = new StyledButton("Meta", StyledButton.Variant.TEXT);
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
        goalProgress = new GaugeBar();
        goalProgress.setPreferredSize(new Dimension(0, 8));

        JPanel gpRow = new JPanel(new BorderLayout(4, 0));
        gpRow.setOpaque(false);
        gpRow.add(tvGoalLabel,   BorderLayout.WEST);
        gpRow.add(goalProgress,  BorderLayout.CENTER);
        gpRow.add(tvGoalPercent, BorderLayout.EAST);
        goalBar.add(gpRow);

        row.add(left,    BorderLayout.WEST);
        row.add(goalBar, BorderLayout.CENTER);

        wrap.add(row);
        return wrap;
    }

    // ── CARTÃO DE PROGRESSO ──────────────────────────────────────────────────

    /** Cartão "Agora" (ver NowCard; o que sugerir fica no NowSuggester). */
    private final java.util.Set<String> skippedNow = new java.util.HashSet<>();   // "Outra coisa" neste turno

    private JComponent buildNowCard() {
        NowSuggester suggester = new NowSuggester(new NowSuggester.Source() {
            public Map<String, StudyData> areas()              { return studyDataMap; }
            public Set<String> archived()                      { return archived; }
            public StudyStats stats()                          { return stats; }
            public String typeOf(String area)                  { return StudyTracker.this.typeOf(area); }
            public int idealDaily(String area)                 { return goals.getOrDefault(area, 0); }
            public LocalDate examDate(String area)             { return examDate.get(area); }
            public List<String> subFocos(String area)          { return subFocos.get(area); }
            public List<StudySession> sessions()               { return sessions; }
            public int typeGoalWeek(String type)               { return typeGoalWeek.getOrDefault(type, 0); }
            public int minutesByType(String t, LocalDate a, LocalDate b) { return minutesByTypeInRange(t, a, b); }
            public List<String> plannedAreas(int dow, int slot) { return StudyTracker.this.plannedAreas(dow, slot); }
        });
        nowCard = new NowCard(new NowCard.Source() {
            public boolean sessionInProgress()     { return StudyTracker.this.sessionInProgress(); }
            public boolean pomodoroBreak()         { return pomodoroActive() && !pomo.isFocus(); }
            public String selectedArea()           { return (String) subjectComboBox.getSelectedItem(); }
            public Map<String, StudyData> areas()  { return studyDataMap; }
            public boolean isHardDay(LocalDate d)  { return StudyTracker.this.isHardDay(d); }
        }, suggester, skippedNow, new NowCard.Actions() {
            public void focusMode()                       { enterFocusMode(true); }
            public void start(String area, String sf)     { startArea(area, sf); }
            public void hardDay(LocalDate day) {
                hardDays.add(day.toString());
                updateUI();
                toast("Ok, hoje não cobra. Descansa.");
            }
            public void undoHardDay(LocalDate day) { hardDays.remove(day.toString()); updateUI(); }
        });
        return nowCard;
    }

    private void refreshNowCard() { if (nowCard != null) nowCard.refresh(); }

    /** Seleciona a área (e o sub-foco), vai para a aba Cronômetro e liga o cronômetro: 1 clique. */
    private void startArea(String area, String subFoco) {
        // Com uma sessão contando, trocar a área mandaria o tempo já feito para a área nova.
        if (sessionInProgress() && !area.equals(subjectComboBox.getSelectedItem())) {
            showScreen(SCR_HOJE);
            toast("Termine ou zere a sessão de \"" + subjectComboBox.getSelectedItem() + "\" antes.");
            return;
        }
        subjectComboBox.setSelectedItem(area);
        pendingSubFoco = subFoco;                      // depois do combo (o listener dele limpa)
        showScreen(SCR_HOJE);                           // o cronômetro fica na tela Hoje
        if (bottomTabs != null) bottomTabs.select(0);   // aba Cronômetro
        startStopwatch();
    }

    private void showTypeGoalsDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4); c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        panel.add(dlgHint("Mínimo por semana para cada tipo (vazio = sem meta de equilíbrio)."), c);
        c.gridwidth = 1;

        JTextField[] fs = new JTextField[TYPE_KEYS.length];
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            int v = typeGoalWeek.getOrDefault(TYPE_KEYS[i], 0);
            fs[i] = new JTextField(v > 0 ? String.valueOf(v) : "", 6);
            fs[i].setFont(AppTheme.FONT_LABEL);
            addFormRow(panel, c, i + 1, TYPE_LABELS[i] + ":", fs[i]);
        }

        int r = JOptionPane.showConfirmDialog(this, panel, "Metas de equilíbrio (semana)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        try {
            for (int i = 0; i < TYPE_KEYS.length; i++) {
                int v = GoalForm.parseDuration(fs[i].getText());
                if (v > 0) typeGoalWeek.put(TYPE_KEYS[i], v); else typeGoalWeek.remove(TYPE_KEYS[i]);
            }
        } catch (IllegalArgumentException ex) {
            toast("Valor inválido — use \"90\" ou \"1h30\"."); return;
        }
        updateUI();
        toast("Metas de equilíbrio atualizadas.");
    }

    /** Aviso gentil (1x por semana) se algum tipo com meta está zerado a partir de quinta. */
    private void balanceNudge() {
        if (!ready) return;
        LocalDate today = LocalDate.now();
        if (isHardDay(today)) return;
        if (today.getDayOfWeek().getValue() < 4) return;   // só de quinta em diante
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        String semana = today.get(wf.weekBasedYear()) + "-W" + today.get(wf.weekOfWeekBasedYear());
        if (semana.equals(cfg.balanceNudgeWeek)) return;

        LocalDate ws = weekStart(today);
        List<String> faltando = new ArrayList<>();
        for (String t : TYPE_KEYS) {
            if (typeGoalWeek.getOrDefault(t, 0) <= 0) continue;
            if (minutesByTypeInRange(t, ws, today) == 0) faltando.add(typeLabel(t).toLowerCase());
        }
        if (faltando.isEmpty()) return;
        cfg.balanceNudgeWeek = semana;   // persistido no saveData() do updateUI
        Timer tt = new Timer(600, e -> toast("Sua semana está sem " + String.join(" e ", faltando)
                + ". Que tal um pouco hoje?"));
        tt.setRepeats(false); tt.start();
    }

    /** Diálogo para limpar as metas de várias matérias de uma vez. */
    private void showManageGoalsDialog() {
        List<String> ativas = new ArrayList<>();
        for (String s : studyDataMap.keySet()) if (!archived.contains(s)) ativas.add(s);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1; c.insets = new Insets(2, 2, 2, 2);
        int row = 0;

        c.gridy = row++; panel.add(dlgLabel("Marque as matérias para limpar as metas:", true), c);

        // Agrupa por tipo de área, na ordem Estudo → Físico → Lazer → Trabalho → Outro.
        Map<String, java.util.List<String>> porTipo = new LinkedHashMap<>();
        for (String t : TYPE_KEYS) porTipo.put(t, new ArrayList<>());
        for (String s : ativas) porTipo.computeIfAbsent(typeOf(s), k -> new ArrayList<>()).add(s);

        Map<String, JCheckBox> cbs = new LinkedHashMap<>();
        for (Map.Entry<String, java.util.List<String>> grupo : porTipo.entrySet()) {
            if (grupo.getValue().isEmpty()) continue;
            JLabel cab = new JLabel(typeLabel(grupo.getKey()).toUpperCase());
            cab.setFont(AppTheme.FONT_CAPTION);
            cab.setForeground(AppTheme.TEXT_MUT);
            c.gridy = row++; c.insets = new Insets(8, 2, 2, 2); panel.add(cab, c);
            c.insets = new Insets(2, 2, 2, 2);
            for (String s : grupo.getValue()) {
                int d = goals.getOrDefault(s, 0), w = weeklyGoalFor(s), mo = monthlyGoalFor(s);
                boolean temMeta = d > 0 || w > 0 || mo > 0;
                JCheckBox cb = new JCheckBox("   " + s + "   —   " + (temMeta
                        ? "Dia " + fmtHM(Math.max(0, d)) + " · Sem " + fmtHM(w) + " · Mês " + fmtHM(mo)
                        : "sem meta"));
                cb.setEnabled(temMeta);
                cbs.put(s, cb);
                c.gridy = row++; panel.add(cb, c);
            }
        }

        JButton todas = new JButton("Marcar todas com meta");
        todas.setFont(AppTheme.FONT_SMALL);
        todas.setFocusPainted(false);
        todas.addActionListener(e -> cbs.values().forEach(cb -> { if (cb.isEnabled()) cb.setSelected(true); }));
        c.gridy = row++; c.fill = GridBagConstraints.NONE; c.insets = new Insets(8, 2, 2, 2);
        panel.add(todas, c);

        int r = JOptionPane.showConfirmDialog(this, panel, "Gerenciar metas",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        int n = 0;
        for (Map.Entry<String, JCheckBox> e : cbs.entrySet()) {
            if (!e.getValue().isSelected()) continue;
            String s = e.getKey();
            goals.remove(s); goalsMin.remove(s); goalsWeek.remove(s); goalsMonth.remove(s);
            n++;
        }
        if (n == 0) { toast("Nenhuma matéria marcada."); return; }
        updateUI();
        toast(n == 1 ? "Metas de 1 matéria limpas." : "Metas de " + n + " matérias limpas.");
    }

    // ── Semana planejada + cartão "Hoje" ─────────────────────────────────────
    // O plano do dia fica na tela Hoje (externalizar o "o que vem agora"); a grade da
    // semana inteira é a tela Semana, aberta pela barra lateral ou pelo "editar semana".

    private String  todayShownKey;   // "<data>_<turno>" desenhado: o timer só redesenha quando muda
    private Timer   todayTimer;

    private void openPlanner() {
        showScreen(SCR_SEMANA);
    }

    private void onPlanChanged() {
        saveData();
        if (weekScreen != null) weekScreen.rebuild();   // a mudança pode vir do cartão Hoje
        refreshTodayCard();
        refreshNowCard();
    }

    /** Áreas planejadas para o dia (1 = seg) e turno (0..2), sem as apagadas/arquivadas. */
    private java.util.List<String> plannedAreas(int dow, int turno) { return planRules.planned(dow, turno); }

    /** Meta diária que conta como "feito": o mínimo, se houver; senão o ideal. 0 = sem meta. */
    private int dailyDoneGoal(String area)   { return stats.dailyDoneGoal(area); }

    /** Feito hoje = bateu o mínimo do dia; sem meta, basta ter mexido nisso hoje. */
    private boolean isDoneToday(String area) { return stats.isDoneToday(area); }

    /**
     * Botão "Preencher pelas metas". Se a semana já tem algo, pergunta se completa
     * (mantém tudo) ou recomeça do zero pelas metas (útil quando os dias da meta mudaram).
     */
    private void fillPlanFromGoals() {
        if (!weekPlan.isEmpty()) {
            Object[] opts = {"Completar", "Refazer do zero", "Cancelar"};
            int r = JOptionPane.showOptionDialog(this,
                    "Sua semana já tem áreas.\n\n"
                  + "Completar: mantém o que está lá e só adiciona o que falta pelas metas.\n"
                  + "Refazer do zero: apaga a semana e monta de novo só pelas metas.",
                    "Preencher pelas metas", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, opts, opts[0]);
            if (r == 1) weekPlan.clear();
            else if (r != 0) return;
        }
        int n = planRules.autofill();
        onPlanChanged();
        toast(n == 0 ? "Tudo que tem meta diária já está na semana."
                     : n == 1 ? "1 bloco colocado pelas metas." : n + " blocos colocados pelas metas.");
    }

    /** Cabeçalho + Turnos + Metas de hoje + Esta semana (ver TodayPanels). */
    private void refreshTodayCard() {
        if (todayPanels == null) return;
        todayShownKey = LocalDate.now() + "_" + PlannerPanel.slotAt(LocalTime.now());
        todayPanels.refresh();
    }

    /** Troca de turno/dia com o app aberto: redesenha "Hoje" e "Agora" (checa a cada minuto). */
    private void startTodayTimer() {
        if (todayTimer != null) return;
        todayTimer = new Timer(60_000, e -> {
            String k = LocalDate.now() + "_" + PlannerPanel.slotAt(LocalTime.now());
            if (!k.equals(todayShownKey)) { skippedNow.clear(); refreshTodayCard(); refreshNowCard(); }
        });
        todayTimer.start();
    }

    private static String fmtHM(int minutes) { return ScreenKit.fmtHM(minutes); }

    /** Quantos dias por semana a meta diária vale (7 se não houver restrição). */
    // ── TIPO DE ÁREA ────────────────────────────────────────────────────────

    private String typeOf(String area) { return areaType.getOrDefault(area, "estudo"); }

    private static int typeIndex(String key)      { return AreaTypes.index(key); }
    private static String typeLabel(String key)   { return AreaTypes.label(key); }
    private static Color typeColor(String key)    { return AreaTypes.color(key); }

    /** Minutos estudados por tipo numa janela de datas [start, end]. */
    private int minutesByTypeInRange(String type, LocalDate start, LocalDate end) {
        int total = 0;
        for (StudySession s : sessions) {
            if (!typeOf(s.getSubject()).equals(type)) continue;
            LocalDate d = dateOf(s.getTimestamp());
            if (!d.isBefore(start) && !d.isAfter(end)) total += s.getMinutes();
        }
        return total;
    }

    private int goalDaysPerWeek(String subject)                     { return stats.goalDaysPerWeek(subject); }
    private int scheduledDaysInMonth(String subject, LocalDate ref) { return stats.scheduledDaysInMonth(subject, ref); }
    private int weeklyGoalFor(String subject)                       { return stats.weeklyGoalFor(subject); }
    private int monthlyGoalFor(String subject)                      { return stats.monthlyGoalFor(subject); }

    // ── TABS ────────────────────────────────────────────────────────────────

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
        startButton.addActionListener(e  -> startStopwatch());
        stopButton.addActionListener(e   -> stopStopwatch());
        resetStopwatchButton.addActionListener(e -> resetStopwatch());
        submitButton.addActionListener(e -> submitStopwatchTime());

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

    private JPanel buildManualTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        p.setBackground(AppTheme.SURFACE);
        hoursField   = styledField("Horas");
        minutesField = styledField("Minutos");
        StyledButton addBtn = new StyledButton("Adicionar Tempo", StyledButton.Variant.FILLED);
        addBtn.addActionListener(e -> addManualTime());
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

    private JPanel buildPomodoroTab() {
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

        autoCycleCheck = new JCheckBox("Ciclos automáticos", cfg.pomoAutoCycle);
        autoCycleCheck.setOpaque(false);
        autoCycleCheck.setFont(AppTheme.FONT_SMALL);
        autoCycleCheck.setForeground(AppTheme.TEXT_SEC);
        autoCycleCheck.setToolTipText("Encadeia foco → pausa → foco sozinho; pausa longa a cada 4 focos");
        autoCycleCheck.addActionListener(e -> {
            cfg.pomoAutoCycle = autoCycleCheck.isSelected();
            saveData();
        });
        top.add(Box.createHorizontalStrut(16));
        top.add(autoCycleCheck);

        // Layout vertical centralizado: status | countdown | botões
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

        startFocusButton.addActionListener(e    -> { startCountdown(true); enterFocusMode(false); });
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
        updatePomodoroCount();
        return p;
    }

    /** "🍅 N hoje" — conta sessões do tipo pomodoro registradas hoje. */
    private void updatePomodoroCount() {
        if (pomoCountLabel == null) return;
        LocalDate today = LocalDate.now();
        int n = 0;
        for (StudySession s : sessions)
            if ("pomodoro".equals(s.getType()) && dateOf(s.getTimestamp()).equals(today)) n++;
        pomoCountLabel.setText(n == 0 ? "" : n + (n == 1 ? " foco hoje" : " focos hoje"));
    }

    // ── HELPERS UI ──────────────────────────────────────────────────────────

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text); l.setFont(AppTheme.FONT_LABEL); l.setForeground(AppTheme.TEXT_PRI); return l;
    }

    private JTextField styledField(String hint) {
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

    private void styleSpinner(JSpinner sp) {
        sp.setPreferredSize(new Dimension(74, 30));
        styleInput(sp);
    }

    // ── TIMERS ──────────────────────────────────────────────────────────────

    private void setupTimers() {
        stopwatch = new Timer(1000, e -> { sw.tick(); updateStopwatchLabel(); refreshFocusBar(); });
        countdownTimer = new Timer(1000, e -> {
            boolean acabou = pomo.tick();
            updateCountdownLabel();
            refreshFocusBar();
            if (acabou) stopCountdown(true);
        });
    }

    /**
     * Põe botões e rótulos do cronômetro e do Pomodoro de acordo com o estado dos modelos.
     * Necessário depois de rebuildUI() (trocar tema, importar backup): antes, uma sessão
     * rodando voltava com "Iniciar" ligado, "Pausar" desligado e o combo de matéria livre.
     */
    private void syncTimerControls() {
        boolean swOn = sw.isRunning();
        updateStopwatchLabel();
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
            updateCountdownLabel();
            if (pomo.isPaused()) {
                statusLabel.setText("Pausado"); statusLabel.setForeground(AppTheme.WARNING);
            } else if (pomo.isFocus()) {
                statusLabel.setText("Focando em " + subjectComboBox.getSelectedItem() + "…");
                statusLabel.setForeground(AppTheme.SUCCESS);
            } else {
                statusLabel.setText("Em pausa…"); statusLabel.setForeground(AppTheme.ACCENT);
            }
        }
        subjectComboBox.setEnabled(!swOn && !pomoOn);   // sessão contando: o tempo não pode mudar de área
    }

    // ── STOPWATCH ───────────────────────────────────────────────────────────

    private void startStopwatch() {
        if (subjectComboBox.getSelectedItem() == null) {
            toast("Selecione uma matéria!"); return;
        }
        boolean novaSessao = sw.start();   // retomar não reabre o modo foco
        lastActivityMs = System.currentTimeMillis();
        stopwatch.start();
        startButton.setText("Retomar");
        startButton.setEnabled(false); stopButton.setEnabled(true);
        submitButton.setEnabled(false); resetStopwatchButton.setEnabled(true);
        subjectComboBox.setEnabled(false);
        updateStopwatchInfo();
        refreshNowCard();
        if (novaSessao) enterFocusMode(false);
        refreshFocusBar();
    }

    private void stopStopwatch() {
        sw.pause();
        stopwatch.stop();
        startButton.setEnabled(true); stopButton.setEnabled(false);
        submitButton.setEnabled(sw.hasTime());
        resetStopwatchButton.setEnabled(sw.hasTime());
        subjectComboBox.setEnabled(true);
        updateStopwatchInfo();
        refreshFocusBar();
    }

    /** Zera SOMENTE o cronômetro (o tempo acumulado da matéria não é tocado). */
    private void resetStopwatch() {
        sw.reset();
        stopwatch.stop();
        updateStopwatchLabel();
        startButton.setText("Iniciar");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        submitButton.setEnabled(false);
        resetStopwatchButton.setEnabled(false);
        subjectComboBox.setEnabled(true);
        if (!sessionInProgress()) exitFocusMode();   // sessão terminou (salva ou zerada)
        refreshNowCard();
    }

    private void submitStopwatchTime() {
        if (!sw.hasTime()) return;
        String subject = (String) subjectComboBox.getSelectedItem();
        int mins = sw.roundedMinutes();
        if (sw.belowMinimum(cfg.minFocusMin)) {
            toast("Abaixo do foco mínimo (" + cfg.minFocusMin + " min) — não salvo.");
            return;   // mantém o tempo para o usuário continuar
        }
        addTime(subject, mins, "cronometro", askSessionDetails(subject, mins, "Sessão concluída"));
        toast(formatTime(mins * 60) + " salvo em " + subject);
        resetStopwatch();
        transitionNudge();
    }

    /** Nudge gentil de pausa entre blocos. */
    private void transitionNudge() {
        if (cfg.transitionMin <= 0) return;
        Timer t = new Timer(2600, e -> toast("Bloco salvo. Que tal " + cfg.transitionMin
                + " min de pausa? Levanta, bebe água."));
        t.setRepeats(false); t.start();
    }

    private void updateStopwatchLabel() {
        stopwatchLabel.setText(sw.elapsedText());
        updateStopwatchInfo();
    }

    /** Linha auxiliar: pausas e tempo bruto (relógio de parede) desde o 1º "Iniciar". */
    private void updateStopwatchInfo() {
        if (stopwatchInfoLabel == null) return;
        stopwatchInfoLabel.setText(sw.infoText());
        if (sw.shouldWarnHyperfocus(cfg.hyperfocusH))
            toast("Você está há " + cfg.hyperfocusH + "h seguidas. Dá um respiro?");
    }

    // ── MODO FOCO ───────────────────────────────────────────────────────────
    // Ao começar uma sessão, a janela some e fica só a faixa (FocusBar): o tempo à vista,
    // nada competindo pela atenção. Volta sozinha quando a sessão termina.

    private FocusBar focusBar;

    private boolean pomodoroActive() { return pomo.isActive(); }

    /** force = pedido explícito ("Modo foco" no cartão Agora), vale mesmo com o ajuste desligado. */
    private void enterFocusMode(boolean force) {
        if ((!cfg.focusMode && !force) || focusBar != null || !sessionInProgress()) return;
        focusBar = new FocusBar(new FocusBar.Actions() {
            public void togglePause() { focusTogglePause(); }
            public void finish()      { focusFinish(); }
            public void expand()      { exitFocusMode(); }
        });
        refreshFocusBar();
        focusBar.setVisible(true);
        setVisible(false);
    }

    private void exitFocusMode() {
        if (focusBar == null) return;
        focusBar.dispose();
        focusBar = null;
        setVisible(true);
        setState(Frame.NORMAL);
        toFront();
        refreshNowCard();
    }

    private void focusTogglePause() {
        if (pomodoroActive()) togglePauseCountdown();
        else                  toggleStopwatch();
    }

    private void focusFinish() {
        if (pomodoroActive()) {
            if (!pomo.isFocus()) { stopCountdown(false); return; }   // encerrar a pausa não perde nada
            int r = JOptionPane.showConfirmDialog(dialogOwner(),
                    "Parar este bloco agora? O tempo dele não é salvo.\n"
                  + "(Para salvar o tempo, use o cronômetro em vez do Pomodoro.)",
                    "Parar Pomodoro", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r == JOptionPane.YES_OPTION) stopCountdown(false);
        } else {
            if (sw.isRunning()) stopStopwatch();   // conta como no "Salvar Tempo": pausa e salva
            submitStopwatchTime();
        }
    }

    /** Diálogos durante o modo foco aparecem junto da faixa (a janela principal está escondida). */
    private Component dialogOwner() {
        return focusBar != null ? focusBar : this;
    }

    private void refreshFocusBar() {
        if (focusBar == null) return;
        String area = (String) subjectComboBox.getSelectedItem();
        StudyData sd = area == null ? null : studyDataMap.get(area);
        Color cor = sd != null ? sd.getColor() : AppTheme.ACCENT;
        String nome = area == null ? "" : area;
        if (pendingSubFoco != null) nome += " · " + pendingSubFoco;

        boolean pausado;
        int correndo;   // minutos da sessão atual, ainda não salvos
        String tempo, status, fim;
        if (pomodoroActive()) {
            pausado = pomo.isPaused();
            tempo = pomo.remainingText();
            fim = "Parar";
            correndo = pomo.focusMinutesDone();
            if (!pomo.isFocus()) {
                nome = "Pausa do Pomodoro"; cor = AppTheme.ACCENT; area = null;
                status = pausado ? "pausado" : "levanta, água, respira";
            } else {
                status = pausado ? "pausado" : "Pomodoro · " + pomo.currentFocusNumber() + "º foco";
            }
        } else {
            pausado = !sw.isRunning();
            tempo = sw.elapsedText();
            correndo = sw.elapsedSeconds() / 60;
            status = pausado ? "pausado" : "contando";
            fim = "Terminar";
        }

        double frac = -1;
        int ideal = area == null ? 0 : goals.getOrDefault(area, 0);
        if (ideal > 0 && isGoalDay(area, LocalDate.now())) {
            int feito = minutesOnDay(area, LocalDate.now()) + correndo;
            frac = feito / (double) ideal;
            status += "  ·  " + fmtHM(feito) + " de " + fmtHM(ideal) + " hoje";
        }
        focusBar.update(nome, cor, tempo, status, pausado, frac, fim);
    }

    // ── POMODORO ────────────────────────────────────────────────────────────

    private void startCountdown(boolean isFocus) { startCountdown(isFocus, 0); }

    private void startCountdown(boolean isFocus, int minutesOverride) {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (isFocus && subject == null) { toast("Selecione uma matéria!"); return; }
        if (countdownTimer != null) countdownTimer.stop(); // garante parado antes de reiniciar
        int minutos = minutesOverride > 0 ? minutesOverride
                : isFocus ? (int) focusSpinner.getValue()
                          : (int) breakSpinner.getValue();
        pomo.start(isFocus, minutos);
        statusLabel.setText(isFocus ? "Focando em " + subject + "…" : "Em pausa…");
        statusLabel.setForeground(isFocus ? AppTheme.SUCCESS : AppTheme.ACCENT);
        updateCountdownLabel();
        countdownTimer.start();
        startFocusButton.setEnabled(false);
        startBreakButton.setEnabled(false);
        stopCountdownButton.setEnabled(true);
        stopCountdownButton.setText("Pausar");
        resetButton.setEnabled(true);
        subjectComboBox.setEnabled(false);
        refreshNowCard();
        refreshFocusBar();
    }

    private void stopCountdown(boolean finished) {
        countdownTimer.stop();
        pomo.halt();

        if (finished) {
            Toolkit.getDefaultToolkit().beep();
            String subject = (String) subjectComboBox.getSelectedItem();
            boolean didFocus = pomo.isFocus() && subject != null;
            if (didFocus) {
                // Ciclo automático não para pra perguntar: o próximo bloco começa sozinho.
                SessionDetails d;
                if (cfg.pomoAutoCycle) d = SessionDetails.of(pendingSubFoco);
                else { exitFocusMode(); d = askSessionDetails(subject, pomo.plannedMinutes(), "Foco concluído"); }
                addTime(subject, pomo.plannedMinutes(), "pomodoro", d);
                pomo.completeFocus();
                updatePomodoroCount();
            }

            if (cfg.pomoAutoCycle) {
                if (didFocus) {
                    boolean longBreak = pomo.longBreakDue();
                    int mins = longBreak ? PomodoroModel.longBreakMinutes((int) breakSpinner.getValue()) : 0;
                    toast((longBreak ? "4 focos! Pausa longa de " + mins + " min." : "Foco concluído — pausa."));
                    startCountdown(false, mins);
                    return;                       // ciclo continua
                } else {
                    toast("Pausa concluída — de volta ao foco.");
                    if (startCountdownIfPossible(true)) return;
                }
            } else {
                exitFocusMode();   // o aviso de fim aparece com o app aberto
                if (!didFocus) showInfoDialog("Pausa concluída", "Pronto para mais uma sessão?");
            }
        }

        // Estado ocioso (não encadeou). Interrompido pelo usuário ⇒ o ciclo recomeça.
        pomo.idle(finished, (int) focusSpinner.getValue());
        statusLabel.setText("Pronto para iniciar"); statusLabel.setForeground(AppTheme.ACCENT);
        updateCountdownLabel();
        startFocusButton.setEnabled(true); startBreakButton.setEnabled(true);
        stopCountdownButton.setEnabled(false);
        stopCountdownButton.setText("Pausar");
        resetButton.setEnabled(false);
        subjectComboBox.setEnabled(true);
        if (!sessionInProgress()) exitFocusMode();
        refreshNowCard();
    }

    /** Tenta iniciar o foco no ciclo automático; devolve false se não deu (ex.: sem matéria). */
    private boolean startCountdownIfPossible(boolean isFocus) {
        if (isFocus && subjectComboBox.getSelectedItem() == null) return false;
        startCountdown(isFocus, 0);
        return pomo.isRunning();
    }

    private void togglePauseCountdown() {
        if (!pomo.togglePause()) {
            // Retomou
            countdownTimer.start();
            stopCountdownButton.setText("Pausar");
            statusLabel.setText(pomo.isFocus()
                    ? "Focando em " + subjectComboBox.getSelectedItem() + "…"
                    : "Em pausa…");
        } else {
            // Pausou
            countdownTimer.stop();
            stopCountdownButton.setText("Retomar");
            statusLabel.setText("Pausado");
            statusLabel.setForeground(AppTheme.WARNING);
        }
        refreshFocusBar();
    }

    private void updateCountdownLabel() {
        countdownLabel.setText(pomo.remainingText());
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
                DayOption sel = (DayOption) manualDayCombo.getSelectedItem();
                LocalDate today = LocalDate.now();
                LocalDate day = (sel == null || sel.day == null) ? today : sel.day;
                if (day.isAfter(today)) { toast("Não dá para registrar num dia que ainda não chegou."); return; }
                String quando = day.equals(today) ? "" : " (" + dayLabel(day, today).toLowerCase() + ")";
                addTime(subject, tot, "manual", askSessionDetails(subject, tot, "Tempo adicionado" + quando), day);
                hoursField.setText(""); minutesField.setText("");
                fillManualDays(null);   // volta para "Hoje": registrar no passado é exceção, não padrão
                toast(formatTime(tot * 60) + " adicionado a " + subject + quando);
            } else { toast("Insira um tempo válido."); }
        } catch (NumberFormatException ex) { toast("Apenas números, por favor."); }
    }

    /** Opção do combo "Dia" da aba Manual. day == null ⇒ "Outra data…". */
    private static final class DayOption {
        final LocalDate day; final String label; final boolean custom;
        DayOption(LocalDate day, String label, boolean custom) { this.day = day; this.label = label; this.custom = custom; }
        @Override public String toString() { return label; }
    }

    private static final String[] DOW_PT = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};

    /** "Hoje", "Ontem" ou "seg 02/11". */
    private static String dayLabel(LocalDate d, LocalDate today) {
        if (d.equals(today)) return "Hoje";
        if (d.equals(today.minusDays(1))) return "Ontem";
        return DOW_PT[d.getDayOfWeek().getValue() - 1] + " " + String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue())
             + (d.getYear() != today.getYear() ? "/" + d.getYear() : "");
    }

    /** Hoje + 6 dias anteriores + "Outra data…". custom != null entra na lista e fica selecionado. */
    private void fillManualDays(LocalDate custom) {
        LocalDate today = LocalDate.now();
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
        manualDayCombo.setModel(m);
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
            int r = JOptionPane.showConfirmDialog(this, pn, "Outra data", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            LocalDate d = null;
            if (r == JOptionPane.OK_OPTION) {
                d = Instant.ofEpochMilli(((Date) sp.getValue()).getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
                if (d.isAfter(today)) d = today;
            }
            fillManualDays(d);
        });
    }

    // ── DATA (tempo + sessão) ────────────────────────────────────────────────

    /** Sub-foco, energia (0 = não informada, 1..3) e nota de uma sessão. Tudo opcional. */
    private static final class SessionDetails {
        final String kind, note;
        final int energy;
        SessionDetails(String kind, String note, int energy) {
            this.kind = kind == null ? "" : kind;
            this.note = note == null ? "" : note;
            this.energy = energy;
        }
        static SessionDetails of(String kind) { return new SessionDetails(kind, "", 0); }
    }

    /**
     * "Como foi?" ao terminar: sub-foco e energia em botões de 1 clique, nota opcional.
     * Enter salva; fechar a janela também salva (o tempo nunca se perde por causa do diálogo).
     */
    private SessionDetails askSessionDetails(String subject, int minutes, String titulo) {
        if (!cfg.askDetails) return SessionDetails.of(pendingSubFoco);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1; c.insets = new Insets(2, 2, 2, 2);
        int row = 0;

        c.gridy = row++;
        panel.add(dlgLabel(fmtHM(minutes) + " de " + subject + ". Como foi?", true), c);

        java.util.List<String> sfs = subFocos.getOrDefault(subject, java.util.Collections.emptyList());
        ChoiceRow sfRow = null;
        if (!sfs.isEmpty()) {
            c.gridy = row++; c.insets = new Insets(10, 2, 2, 2);
            panel.add(dlgHint("No que você focou?"), c);
            sfRow = new ChoiceRow(sfs.toArray(new String[0]), sfs.indexOf(pendingSubFoco));
            c.gridy = row++; c.insets = new Insets(2, 2, 2, 2);
            panel.add(sfRow, c);
        }

        c.gridy = row++; c.insets = new Insets(10, 2, 2, 2);
        panel.add(dlgHint("Energia"), c);
        ChoiceRow enRow = new ChoiceRow(new String[]{ENERGY_TEXT[1], ENERGY_TEXT[2], ENERGY_TEXT[3]}, -1);
        c.gridy = row++; c.insets = new Insets(2, 2, 2, 2);
        panel.add(enRow, c);

        c.gridy = row++; c.insets = new Insets(10, 2, 2, 2);
        panel.add(dlgHint("Nota (vai para o Histórico)"), c);
        JTextField note = new JTextField(28);
        note.setFont(AppTheme.FONT_LABEL);
        c.gridy = row++; c.insets = new Insets(2, 2, 2, 2);
        panel.add(note, c);

        JOptionPane op = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"Salvar"}, "Salvar");
        JDialog dlg = op.createDialog(dialogOwner(), titulo);
        dlg.addWindowFocusListener(new WindowAdapter() {   // já dá para digitar a nota; Enter salva
            @Override public void windowGainedFocus(WindowEvent e) { note.requestFocusInWindow(); }
        });
        note.addActionListener(e -> op.setValue("Salvar"));
        dlg.setVisible(true);
        dlg.dispose();

        String kind = sfRow != null && sfRow.selected() >= 0 ? sfs.get(sfRow.selected()) : null;
        String txt = AppTheme.semHtml(note.getText().replace('|', '/').trim());   // | separa campos da sessão
        return new SessionDetails(kind, txt, enRow.selected() + 1);
    }

    /** Linha de botões em que no máximo um fica marcado; clicar no marcado desmarca. */
    private static final class ChoiceRow extends JPanel {
        private final java.util.List<JToggleButton> btns = new ArrayList<>();
        ChoiceRow(String[] labels, int initial) {
            super(new FlowLayout(FlowLayout.LEFT, 6, 0));
            for (int i = 0; i < labels.length; i++) {
                // Pintado à mão: no Nimbus o botão marcado quase não se distingue do desmarcado.
                JToggleButton b = new JToggleButton(labels[i], i == initial) {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        int w = getWidth(), h = getHeight();
                        if (isSelected()) {
                            g2.setColor(AppTheme.ACCENT);
                            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
                        } else {
                            g2.setColor(getModel().isRollover() ? AppTheme.SURFACE2 : AppTheme.SURFACE);
                            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
                            g2.setColor(AppTheme.DIVIDER);
                            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
                        }
                        g2.setFont(isSelected() ? AppTheme.FONT_BOLD : AppTheme.FONT_LABEL);
                        g2.setColor(isSelected() ? Color.WHITE : AppTheme.TEXT_PRI);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
                                (h - fm.getHeight()) / 2 + fm.getAscent());
                        g2.dispose();
                    }
                };
                b.setFont(AppTheme.FONT_BOLD);   // mede pelo negrito: o texto não muda de tamanho ao marcar
                b.setContentAreaFilled(false);
                b.setBorderPainted(false);
                b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
                b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                b.setFocusPainted(false);
                b.addActionListener(e -> { if (b.isSelected()) for (JToggleButton o : btns) if (o != b) o.setSelected(false); });
                btns.add(b);
                add(b);
            }
        }
        int selected() {
            for (int i = 0; i < btns.size(); i++) if (btns.get(i).isSelected()) return i;
            return -1;
        }
    }

    private void addTime(String subject, int minutes, String type, SessionDetails d) {
        addTime(subject, minutes, type, d, LocalDate.now());
    }

    /**
     * Registra tempo num dia. Dia passado (registro esquecido) vira uma sessão às 12:00 daquele dia;
     * metas, sequência e calendário são derivados das sessões, então já contam no dia certo.
     */
    private void addTime(String subject, int minutes, String type, SessionDetails d, LocalDate day) {
        LocalDate today = LocalDate.now();
        boolean isToday = !day.isBefore(today);
        int beforeToday = minutesOnDay(subject, today);
        long ts = isToday ? System.currentTimeMillis()
                : day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        studyDataMap.get(subject).addMinutes(minutes);
        StudySession s = new StudySession(subject, minutes, ts, type, d.note, d.kind, d.energy);
        // mantém a lista em ordem cronológica, como ela é carregada do disco
        int pos = sessions.size();
        while (pos > 0 && sessions.get(pos - 1).getTimestamp() > ts) pos--;
        sessions.add(pos, s);
        pendingSubFoco = null;
        updateUI();
        saveData(); saveSessions();

        if (isToday) goalNudge(subject, beforeToday, beforeToday + minutes);
    }

    /** Toast de incentivo: meta do dia batida agora, ou reta final. */
    private void goalNudge(String subject, int before, int after) {
        int goal = goals.getOrDefault(subject, 0);
        if (goal <= 0) return;
        final String msg;
        int alvo = dailyMinFor(subject);   // celebra ao bater o MÍNIMO
        if (before < alvo && after >= alvo) {
            msg = "Você bateu a meta de " + subject + " hoje!  (" + fmtHM(after) + ")";
        } else if (!isHardDay(LocalDate.now()) && after < alvo && alvo - after <= 15) {
            msg = "Faltam " + (alvo - after) + " min para a meta de " + subject + " hoje.";
        } else {
            return;
        }
        // atrasa um pouco para não colidir com o toast de "tempo salvo"
        Timer t = new Timer(2500, e -> toast(msg));
        t.setRepeats(false);
        t.start();
    }

    // ── PROGRESSÃO: cálculo por dia/semana/mês (derivado das sessões) ─────────

    /** Converte um timestamp em LocalDate no fuso local. */
    private static LocalDate dateOf(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // Cálculos movidos para StudyStats (testados em test/StudyStatsTest.java).
    // Estes repasses mantêm as chamadas antigas do StudyTracker funcionando sem mudança.
    private int minutesOnDay(String subject, LocalDate day)                   { return stats.minutesOnDay(subject, day); }
    private int minutesInRange(String subject, LocalDate start, LocalDate end) { return stats.minutesInRange(subject, start, end); }
    private static LocalDate weekStart(LocalDate date)  { return StudyStats.weekStart(date); }
    private static LocalDate monthStart(LocalDate date) { return StudyStats.monthStart(date); }

    // ── STREAKS (dias consecutivos batendo a meta diária) ────────────────────

    private boolean isGoalDay(String subject, LocalDate date) { return stats.isGoalDay(subject, date); }
    private int dailyGoalFor(String subject)                  { return stats.dailyGoalFor(subject); }
    private int dailyMinFor(String subject)                   { return stats.dailyMinFor(subject); }
    private boolean isHardDay(LocalDate d)                    { return stats.isHardDay(d); }
    private int currentStreak(String subject)                 { return stats.currentStreak(subject); }
    private static int streakMilestone(int streak)            { return StudyStats.streakMilestone(streak); }
    private static String streakBadge(int streak)             { return StudyStats.streakBadge(streak); }

    /** Melhor sequência registrada (recorde). subject == null ⇒ geral. */
    private int bestStreak(String subject) {
        return subject == null ? streakBestGeneral : streakBest.getOrDefault(subject, 0);
    }

    /** Recalcula sequências atuais, eleva os recordes e comemora novos marcos. */
    private void refreshStreaks() {
        int g = currentStreak(null);
        if (g > streakBestGeneral) streakBestGeneral = g;
        for (String subject : studyDataMap.keySet()) {
            int c = currentStreak(subject);
            if (c > streakBest.getOrDefault(subject, 0)) streakBest.put(subject, c);
        }
        int mk = streakMilestone(g);
        if (mk > celebratedStreakMilestone) {
            celebratedStreakMilestone = mk;
            if (ready) {
                Timer t = new Timer(400, e -> toast(mk + " dias de sequência! Continue assim."));
                t.setRepeats(false); t.start();
            }
        }
    }

    // ── METAS ───────────────────────────────────────────────────────────────

    private void showGoalDialog() {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (subject == null) { toast("Selecione uma matéria!"); return; }

        JTextField fDay   = goalField(goals.getOrDefault(subject, 0));
        JTextField fMin   = goalField(goalsMin.getOrDefault(subject, 0));
        JTextField fWeek  = goalField(goalsWeek.getOrDefault(subject, 0));
        JTextField fMonth = goalField(goalsMonth.getOrDefault(subject, 0));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addFormRow(panel, c, row++, "Meta diária (ideal):", fDay);
        addFormRow(panel, c, row++, "Mínimo aceitável:",    fMin);
        addFormRow(panel, c, row++, "Meta semanal:", fWeek);
        addFormRow(panel, c, row++, "Meta mensal:",  fMonth);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(0, 3, 8, 3);
        panel.add(dlgHint("Bater o mínimo já mantém a sequência. Vazio = mínimo igual ao ideal."), c);
        c.gridwidth = 1; c.insets = new Insets(3, 3, 3, 3);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(2, 3, 4, 3);
        panel.add(dlgHint("Minutos (\"90\") ou horas (\"1h30\").  Semana/mês em branco = calculados pela meta diária × dias."), c);

        JButton btnLimpar = new JButton("Limpar metas");
        btnLimpar.setFont(AppTheme.FONT_SMALL);
        btnLimpar.setMargin(new Insets(2, 8, 2, 8));
        btnLimpar.setFocusPainted(false);
        btnLimpar.setToolTipText("Zera meta diária, semanal e mensal desta matéria");
        btnLimpar.addActionListener(e -> { fDay.setText(""); fMin.setText(""); fWeek.setText(""); fMonth.setText(""); });
        JPanel limparRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        limparRow.add(btnLimpar);
        c.gridy = row++; c.insets = new Insets(0, 3, 10, 3);
        panel.add(limparRow, c);
        c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.insets = new Insets(3, 3, 3, 3);

        // Dias da semana em que a meta vale (para sequência não quebrar no fim de semana).
        java.util.Set<java.time.DayOfWeek> diasAtuais = goalDays.get(subject);
        String[] nomes = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
        JToggleButton[] tgDias = new JToggleButton[7];
        JPanel diasRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        for (int i = 0; i < 7; i++) {
            java.time.DayOfWeek dw = java.time.DayOfWeek.of(i + 1);
            boolean on = diasAtuais == null || diasAtuais.isEmpty() || diasAtuais.contains(dw);
            tgDias[i] = new JToggleButton(nomes[i], on);
            tgDias[i].setMargin(new Insets(2, 6, 2, 6));
            tgDias[i].setFocusPainted(false);
            diasRow.add(tgDias[i]);
        }
        java.util.function.Supplier<boolean[]> diasSel = () -> {
            boolean[] b = new boolean[7];
            for (int i = 0; i < 7; i++) b[i] = tgDias[i].isSelected();
            return b;
        };
        java.util.function.IntSupplier nDiasSel = () -> GoalForm.daysCount(diasSel.get());

        JLabel diasHint = dlgHint(nDiasSel.getAsInt() + " dia(s)/semana com meta");
        // Ao MEXER nos dias, semana e mês passam a ser diária × nº de dias.
        for (JToggleButton t : tgDias) t.addItemListener(e -> {
            int nd = nDiasSel.getAsInt();
            int d;
            try { d = GoalForm.parseDuration(fDay.getText()); } catch (Exception ex) { d = 0; }
            if (d > 0) {
                int wk = GoalForm.weekFromDaily(d, nd), mo = GoalForm.monthFromWeek(wk);
                fWeek.setText(String.valueOf(wk));
                fMonth.setText(String.valueOf(mo));
                diasHint.setText(nd + " dia(s)/semana  →  semana " + fmtHM(wk) + "  ·  mês " + fmtHM(mo));
            } else {
                diasHint.setText(nd + " dia(s)/semana. Preencha a meta diária p/ calcular semana e mês.");
            }
        });

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        panel.add(dlgLabel("Dias da meta diária", true), c);
        c.gridy = row++;
        panel.add(diasRow, c);
        c.gridy = row++; c.insets = new Insets(0, 3, 4, 3);
        panel.add(diasHint, c);
        c.gridy = row++; c.insets = new Insets(0, 3, 10, 3);
        panel.add(dlgHint("Dias desmarcados não exigem a meta e não quebram a sequência."), c);
        c.insets = new Insets(3, 3, 3, 3);

        // Data de prova (opcional).
        LocalDate examAtual = examDate.get(subject);
        JTextField fExam = new JTextField(GoalForm.examText(examAtual), 10);
        fExam.setFont(AppTheme.FONT_LABEL);
        addFormRow(panel, c, row++, "Data da prova:", fExam);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(0, 3, 12, 3);
        panel.add(dlgHint("Formato dd/mm/aaaa. Deixe vazio para não usar."), c);
        c.gridwidth = 1; c.insets = new Insets(3, 3, 3, 3);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        panel.add(dlgLabel("Sugestões de meta diária", true), c);

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (int m : new int[]{15, 30, 45, 60, 90, 120}) {
            final int mm = m;
            JButton b = new JButton(fmtHM(mm));
            b.setMargin(new Insets(2, 8, 2, 8));
            b.setFocusPainted(false);
            b.addActionListener(e -> fillGoalFields(mm, nDiasSel.getAsInt(), fDay, fWeek, fMonth));
            presets.add(b);
        }
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        panel.add(presets, c);

        int avg30  = avgDailyMinutes(subject, 30);
        int avgAct = avgActiveDayMinutes(subject, 30);
        if (avg30 > 0) {
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(suggestionRow("Sua média nos últimos 30 dias: " + fmtHM(avg30) + "/dia",
                    Math.max(5, avg30), nDiasSel, fDay, fWeek, fMonth), c);
        }
        if (avgAct > 0 && avgAct != avg30) {
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(suggestionRow("Média nos dias em que você estudou: " + fmtHM(avgAct),
                    avgAct, nDiasSel, fDay, fWeek, fMonth), c);
        }
        if (avg30 == 0 && avgAct == 0) {
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(dlgHint("Estude alguns dias e o app passa a sugerir metas pela sua média."), c);
        }

        int melhorSem = bestWeekMinutes(subject);
        if (melhorSem > 0) {
            int perDay = GoalForm.bestWeekPerDay(melhorSem, nDiasSel.getAsInt());
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(suggestionRow("Repetir sua melhor semana (" + fmtHM(melhorSem) + ")  →  " + fmtHM(perDay) + "/dia",
                    perDay, nDiasSel, fDay, fWeek, fMonth), c);
        }

        // Meta adaptativa (item 1): sobe se vem batendo folgado, baixa se vem falhando muito.
        int adapt = adaptiveGoalSuggestion(subject);
        if (adapt > 0) {
            int atual = goals.getOrDefault(subject, 0);
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(suggestionRow(
                    (adapt > atual ? "Você vem batendo com folga — que tal subir para " + fmtHM(adapt) + "?"
                                   : "Semanas puxadas — sugestão: baixar para " + fmtHM(adapt) + " e retomar o ritmo"),
                    adapt, nDiasSel, fDay, fWeek, fMonth), c);
        }

        int r = JOptionPane.showConfirmDialog(this, panel,
                "Metas de \"" + subject + "\"",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        int gDay, gWeek, gMonth;
        try {
            gDay   = GoalForm.parseDuration(fDay.getText());
            gWeek  = GoalForm.parseDuration(fWeek.getText());
            gMonth = GoalForm.parseDuration(fMonth.getText());
        } catch (IllegalArgumentException ex) {
            toast("Meta inválida — use algo como \"90\", \"1h30\" ou \"1:30\".");
            return;
        }

        // Dias da meta
        java.util.Set<java.time.DayOfWeek> dias = GoalForm.daysFrom(diasSel.get());   // null = todos os dias
        if (dias == null) goalDays.remove(subject);
        else              goalDays.put(subject, dias);

        // Data da prova
        try {
            LocalDate prova = GoalForm.parseExamDate(fExam.getText());
            if (prova == null) examDate.remove(subject); else examDate.put(subject, prova);
        } catch (IllegalArgumentException ex) {
            toast("Data da prova inválida (use dd/mm/aaaa) — ignorada.");
            examDate.remove(subject);
        }

        int gMin;
        try { gMin = GoalForm.parseDuration(fMin.getText()); } catch (IllegalArgumentException ex) { gMin = 0; }
        goals.put(subject, gDay);
        gMin = GoalForm.effectiveMin(gMin, gDay);
        if (gMin > 0) goalsMin.put(subject, gMin); else goalsMin.remove(subject);
        goalsWeek.put(subject, gWeek);
        goalsMonth.put(subject, gMonth);
        updateUI();
        toast((gDay > 0 || gWeek > 0 || gMonth > 0)
                ? "Metas de \"" + subject + "\" atualizadas."
                : "Metas de \"" + subject + "\" removidas.");
    }

    /**
     * Item 1 — meta adaptativa. Devolve uma meta diária sugerida (múltiplo de 5) ou 0.
     * Sobe ~15% se, nos últimos 14 dias agendados, a média ficou ≥ 20% acima da meta e a
     * sequência ≥ 7. Baixa ~20% se bateu a meta em menos de 40% desses dias.
     */
    private int adaptiveGoalSuggestion(String subject) { return stats.adaptiveGoalSuggestion(subject); }

    /**
     * Preenche os 3 campos a partir de uma meta diária, usando o nº de dias/semana
     * selecionados: semana = diária × nDias; mês = semana × ~4,345.
     */
    private void fillGoalFields(int daily, int nDias, JTextField fDay, JTextField fWeek, JTextField fMonth) {
        int week = GoalForm.weekFromDaily(daily, nDias);
        fDay.setText(String.valueOf(daily));
        fWeek.setText(String.valueOf(week));
        fMonth.setText(String.valueOf(GoalForm.monthFromWeek(week)));
    }

    private JPanel suggestionRow(String text, int minutes, java.util.function.IntSupplier nDias,
                                JTextField fDay, JTextField fWeek, JTextField fMonth) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(dlgHint(text));
        JButton use = new JButton("Usar");
        use.setMargin(new Insets(1, 8, 1, 8));
        use.setFont(AppTheme.FONT_SMALL);
        use.setFocusPainted(false);
        use.addActionListener(e -> fillGoalFields(minutes, nDias.getAsInt(), fDay, fWeek, fMonth));
        row.add(use);
        return row;
    }

    /** Uma linha "rótulo : campo" no formulário de metas (GridBagLayout). */
    private static void addFormRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(dlgLabel(label, false), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
        c.weightx = 0; c.fill = GridBagConstraints.NONE;
    }

    /** Média diária de estudo (min/dia) nos últimos {@code days} dias corridos. */
    private int avgDailyMinutes(String subject, int days)     { return stats.avgDailyMinutes(subject, days); }
    private int avgActiveDayMinutes(String subject, int days) { return stats.avgActiveDayMinutes(subject, days); }
    private int bestWeekMinutes(String subject)               { return stats.bestWeekMinutes(subject); }

    /** Campo de texto para meta, pré-preenchido (vazio se 0). */
    private JTextField goalField(int value) {
        JTextField f = new JTextField(value > 0 ? String.valueOf(value) : "", 6);
        f.setFont(AppTheme.FONT_LABEL);
        return f;
    }

    // Rótulos para dentro de JOptionPane (L&F do sistema, fundo claro): NÃO forçar
    // as cores do AppTheme aqui — o tema escuro deixava o texto branco e invisível.
    private static JLabel dlgLabel(String text, boolean bold) {
        JLabel l = new JLabel(text);
        l.setFont(bold ? AppTheme.FONT_BOLD : AppTheme.FONT_LABEL);
        return l;
    }

    private static JLabel dlgHint(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.FONT_SMALL);
        l.setForeground(Color.GRAY);
        return l;
    }

    private void updateGoalProgress() {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (subject == null || !goals.containsKey(subject) || goals.get(subject) <= 0) {
            goalBar.setVisible(false); return;
        }
        int goal    = goals.get(subject);
        int current = minutesOnDay(subject, LocalDate.now());
        int pct     = (int) Math.min(100, current * 100.0 / goal);
        goalBar.setVisible(true);
        int gh = current / 60, gm = current % 60, goalH = goal / 60, goalM = goal % 60;
        tvGoalLabel.setText(String.format("%dh%02dm / %dh%02dm", gh, gm, goalH, goalM));
        tvGoalPercent.setText(pct + "%");
        Color base = studyDataMap.getOrDefault(subject, new StudyData(0, AppTheme.ACCENT)).getColor();
        tvGoalPercent.setForeground(current >= goal ? AppTheme.SUCCESS : base);
        goalProgress.set(current / (double) goal, base, current >= goal);
    }

    // ── GERENCIAMENTO DE MATÉRIAS ────────────────────────────────────────────

    private void addNewSubject() {
        JTextField fName = new JTextField(18);
        JComboBox<String> cbType = tipoCombo("estudo");
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4); c.anchor = GridBagConstraints.WEST;
        addFormRow(panel, c, 0, "Nome:", fName);
        addFormRow(panel, c, 1, "Tipo:", cbType);

        int r = JOptionPane.showConfirmDialog(this, panel, "Nova área",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        String name = Areas.cleanName(fName.getText());
        if (name.isEmpty()) return;
        String type = TYPE_KEYS[Math.max(0, cbType.getSelectedIndex())];

        if (archived.contains(name)) { unarchiveSubject(name); return; }   // era arquivada → desarquiva
        if (studyDataMap.containsKey(name)) { toast("Essa área já existe!"); return; }
        areas.add(name, type);
        refreshCombo(); selectIfIdle(name); updateUI();
    }

    private void unarchiveSubject(String name) {
        if (!archived.remove(name)) return;
        refreshCombo(); selectIfIdle(name); updateUI();
        toast("\"" + name + "\" desarquivada.");
    }

    /** Tem sessão em andamento (cronômetro com tempo ou Pomodoro rodando)? */
    private boolean sessionInProgress() {
        return sw.hasTime() || sw.isRunning() || pomodoroActive();
    }

    /** Seleciona a área no combo só se não houver sessão contando: senão o tempo iria para a área errada. */
    private void selectIfIdle(String area) {
        if (!sessionInProgress()) subjectComboBox.setSelectedItem(area);
    }

    /** Arquivar/deletar a área da sessão em andamento faria o tempo ir para outra área. */
    private boolean blockIfRunning(String area) {
        if (sessionInProgress() && area.equals(subjectComboBox.getSelectedItem())) {
            toast("Termine ou zere a sessão de \"" + area + "\" antes.");
            return true;
        }
        return false;
    }

    private JComboBox<String> tipoCombo(String selectedKey) {
        String[] itens = new String[TYPE_KEYS.length];
        for (int i = 0; i < TYPE_KEYS.length; i++) itens[i] = TYPE_LABELS[i];
        JComboBox<String> cb = new JComboBox<>(itens);
        cb.setSelectedIndex(typeIndex(selectedKey));
        cb.setFont(AppTheme.FONT_LABEL);
        return cb;
    }

    private void editSubjectName(String old) {
        if (old == null || !studyDataMap.containsKey(old)) return;
        Object cur = subjectComboBox.getSelectedItem();   // a área do cronômetro não muda

        JTextField fName = new JTextField(old, 18);
        JComboBox<String> cbType = tipoCombo(typeOf(old));
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4); gc.anchor = GridBagConstraints.WEST;
        addFormRow(panel, gc, 0, "Nome:", fName);
        addFormRow(panel, gc, 1, "Tipo:", cbType);
        int rr = JOptionPane.showConfirmDialog(this, panel, "Editar área",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (rr != JOptionPane.OK_OPTION) return;

        areas.setType(old, TYPE_KEYS[Math.max(0, cbType.getSelectedIndex())]);

        switch (areas.rename(old, Areas.cleanName(fName.getText()))) {
            case EXISTS:
                toast("Esse nome já existe!");
                return;
            case UNCHANGED:   // só o tipo pode ter mudado
                refreshCombo(); subjectComboBox.setSelectedItem(cur); updateUI();
                return;
            case OK:
                String nw = Areas.cleanName(fName.getText());
                refreshCombo(); subjectComboBox.setSelectedItem(old.equals(cur) ? nw : cur);
                saveChecklist(); saveSessions(); updateUI();   // sessões agora vão junto no rename
        }
    }

    private void changeSubjectColor(String s) {
        if (s == null || !studyDataMap.containsKey(s)) return;
        Color c = JColorChooser.showDialog(this, "Cor de " + s, studyDataMap.get(s).getColor());
        if (c != null) { studyDataMap.get(s).setColor(c); updateUI(); }
    }

    private void zeroOutSubjectTime(String s) {
        if (s == null || !studyDataMap.containsKey(s)) return;
        int mins = studyDataMap.get(s).getMinutes();
        int r = JOptionPane.showConfirmDialog(this,
                "Isto zera TODO o tempo acumulado de \"" + s + "\" (" + fmtHM(mins) + ").\n"
              + "O histórico de sessões é mantido. Continuar?\n\n"
              + "Para apenas reiniciar o cronômetro, use o botão \"Zerar\" na aba Cronômetro.",
                "Zerar matéria", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) { studyDataMap.get(s).setMinutes(0); updateUI(); toast("Tempo de \"" + s + "\" zerado."); }
    }

    private void archiveSubject(String s) {
        if (s == null || !studyDataMap.containsKey(s) || blockIfRunning(s)) return;
        if (!areas.canArchive(s)) { toast("Deixe ao menos uma matéria ativa."); return; }
        int r = JOptionPane.showConfirmDialog(this,
                "Arquivar \"" + s + "\"?\nEla sai da lista, mas o tempo e o histórico são mantidos.\n"
              + "Para reativar, use \"Reativar\" na tela Áreas.",
                "Arquivar matéria", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        archived.add(s);
        refreshCombo(); updateUI();
        toast("\"" + s + "\" arquivada.");
    }

    private void deleteSubject(String s) {
        if (s == null || !studyDataMap.containsKey(s) || blockIfRunning(s)) return;
        if (!areas.canDelete(s)) { toast("Deixe ao menos uma área ativa."); return; }
        int r =JOptionPane.showConfirmDialog(this, "Deletar \"" + s + "\"? Todo o tempo será perdido.", "Deletar Matéria", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) { areas.delete(s); saveChecklist(); refreshCombo(); updateUI(); }
    }

    // ── HISTÓRICO ───────────────────────────────────────────────────────────

    private void showHistory() {
        new HistoryPanel(this, sessions, studyDataMap, () -> {
            updateUI();
            saveData();
            saveSessions();
        }).setVisible(true);
    }

    // ── EXPORTAR / IMPORTAR ─────────────────────────────────────────────────

    private void exportData() {
        saveData(); saveSessions(); saveChecklist();   // garante que o disco está atualizado
        JFileChooser fc = new JFileChooser();          // abre em Documentos
        fc.setSelectedFile(new File("focaestudo-backup-"
                + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ".txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            java.nio.file.Files.write(fc.getSelectedFile().toPath(),
                    STORE.exportText().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            toast("Backup exportado.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Falha ao exportar: " + e.getMessage());
        }
    }

    private void importData() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        if (fc.getSelectedFile().length() > StudyStore.MAX_IMPORT_BYTES) {
            JOptionPane.showMessageDialog(this, "Arquivo grande demais para ser um backup do FocaEstudo.");
            return;
        }
        try {
            String all = new String(java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            StudyStore.Backup b = StudyStore.parseBackup(all);
            if (b == null) {
                JOptionPane.showMessageDialog(this, "Arquivo não parece um backup do FocaEstudo.");
                return;
            }
            if (StudyStore.countSubjects(b.dados) == 0) {
                JOptionPane.showMessageDialog(this, "O backup não tem matérias válidas — importação cancelada.");
                return;
            }

            int r = JOptionPane.showConfirmDialog(this,
                    "Isto substitui TODas as matérias, metas e histórico atuais pelos do arquivo.\n"
                  + "Seus dados atuais vão para .bak. Continuar?",
                    "Importar dados", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;

            STORE.importBackup(b);
            reloadFromDisk();
            toast("Dados importados.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Falha ao importar: " + e.getMessage());
        }
    }

    /** Recarrega tudo dos arquivos (após importar). */
    private void reloadFromDisk() {
        studyDataMap.clear(); sessions.clear(); checklist.clear();
        goals.clear(); goalsMin.clear(); goalsWeek.clear(); goalsMonth.clear(); hardDays.clear();
        goalDays.clear(); examDate.clear(); archived.clear();
        areaType.clear(); typeGoalWeek.clear(); subFocos.clear(); weekPlan.clear();
        streakBest.clear(); unknownProps.clear();
        streakBestGeneral = 0; celebratedStreakMilestone = 0;
        // Volta os ajustes ao padrão: um backup sem alguma chave não pode herdar o valor antigo.
        cfg.reset();
        loadOk = true;

        ready = false;            // não grava nada enquanto recarrega
        loadData();               // pode trocar o tema (__theme__ do backup)
        loadSessions();
        loadChecklist();
        if (studyDataMap.isEmpty()) promptForSubjects();
        // Reconstrói a tela inteira: se o tema do backup for outro, só atualizar
        // os dados deixava a interface metade clara, metade escura.
        rebuildUI();
        ready = true;
        saveData();
    }

    // ── AJUSTES ─────────────────────────────────────────────────────────────

    private void showSettingsDialog() {
        JSpinner spFocus = new JSpinner(new SpinnerNumberModel(cfg.minFocusMin, 0, 60, 1));
        JSpinner spIdle  = new JSpinner(new SpinnerNumberModel(cfg.idleMinutes, 0, 120, 1));
        JSpinner spTrans = new JSpinner(new SpinnerNumberModel(cfg.transitionMin, 0, 30, 1));
        JSpinner spHyper = new JSpinner(new SpinnerNumberModel(cfg.hyperfocusH, 0, 12, 1));
        JCheckBox cbCycle = new JCheckBox("Encadear foco → pausa → foco automaticamente", cfg.pomoAutoCycle);
        cbCycle.setOpaque(false);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addFormRow(panel, c, row++, "Foco mínimo (min):", spFocus);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        panel.add(dlgHint("Sessões de cronômetro/pomodoro abaixo disso não são salvas."), c);
        c.gridwidth = 1;

        addFormRow(panel, c, row++, "Inatividade (min):", spIdle);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        panel.add(dlgHint("Sem mexer no PC por esse tempo, o cronômetro pausa e pergunta.  0 = desligado."), c);
        c.gridwidth = 1;

        addFormRow(panel, c, row++, "Pausa entre blocos (min):", spTrans);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        panel.add(dlgHint("Sugestão de pausa depois de salvar um bloco do cronômetro.  0 = desligado."), c);
        c.gridwidth = 1;

        addFormRow(panel, c, row++, "Aviso de hiperfoco (h):", spHyper);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        panel.add(dlgHint("Aviso pra respirar depois de N horas seguidas de cronômetro.  0 = desligado."), c);
        c.gridwidth = 1;

        JComboBox<String> cbReminder = new JComboBox<>();
        cbReminder.addItem("Desligado");
        for (int hRem = 5; hRem <= 23; hRem++) cbReminder.addItem(String.format("%02d:00", hRem));
        cbReminder.setSelectedItem(cfg.reminderHour < 0 ? "Desligado" : String.format("%02d:00", cfg.reminderHour));
        addFormRow(panel, c, row++, "Lembrete diário:", cbReminder);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        panel.add(dlgHint("Se não tiver estudado até essa hora, o app avisa (notificação do Windows)."), c);

        JCheckBox cbAsk = new JCheckBox("Ao terminar, perguntar sub-foco, energia e nota", cfg.askDetails);
        cbAsk.setOpaque(false);
        JCheckBox cbFocus = new JCheckBox("Ao começar uma sessão, recolher a janela numa faixa pequena", cfg.focusMode);
        cbFocus.setOpaque(false);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(12, 4, 2, 4);
        panel.add(dlgLabel("Modo foco", true), c);
        c.gridy = row++; c.insets = new Insets(2, 4, 4, 4);
        panel.add(cbFocus, c);
        c.gridy = row++; c.insets = new Insets(0, 4, 4, 4);
        panel.add(dlgHint("A faixa fica por cima das outras janelas, com o tempo e a meta de hoje. \"Abrir app\" volta para a tela cheia."), c);
        c.gridy = row++; c.insets = new Insets(8, 4, 4, 4);
        panel.add(cbAsk, c);
        c.gridy = row++; c.insets = new Insets(0, 4, 4, 4);
        panel.add(dlgHint("Tudo opcional: Enter salva. Desligado, salva direto (com o sub-foco sugerido, se houver)."), c);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(12, 4, 2, 4);
        panel.add(dlgLabel("Pomodoro", true), c);
        c.gridy = row++; c.insets = new Insets(2, 4, 4, 4);
        panel.add(cbCycle, c);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(12, 4, 2, 4);
        panel.add(dlgLabel("Backup", true), c);
        JPanel backupRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton btnExport = new JButton("Exportar dados…");
        JButton btnImport = new JButton("Importar dados…");
        JButton btnFolder = new JButton("Abrir pasta dos dados");
        btnExport.setFocusPainted(false); btnImport.setFocusPainted(false); btnFolder.setFocusPainted(false);
        btnExport.addActionListener(e -> exportData());
        btnImport.addActionListener(e -> importData());
        btnFolder.addActionListener(e -> {
            try { Desktop.getDesktop().open(DATA_DIR); }
            catch (Exception ex) { showInfoDialog("Pasta dos dados", DATA_DIR.getAbsolutePath()); }
        });
        backupRow.add(btnExport); backupRow.add(btnImport); backupRow.add(btnFolder);
        c.gridy = row++; c.insets = new Insets(2, 4, 4, 4);
        panel.add(backupRow, c);
        c.gridy = row++; c.insets = new Insets(0, 4, 4, 4);
        panel.add(dlgHint("Um arquivo único com matérias + histórico, para backup ou levar a outro PC."), c);

        JCheckBox cbUpdate = new JCheckBox("Avisar quando sair uma versão nova", cfg.updateCheck);
        cbUpdate.setOpaque(false);
        c.gridy = row++; c.insets = new Insets(12, 4, 2, 4);
        panel.add(dlgLabel("Atualizações", true), c);
        c.gridy = row++; c.insets = new Insets(2, 4, 4, 4);
        panel.add(cbUpdate, c);
        c.gridy = row++; c.insets = new Insets(0, 4, 4, 4);
        panel.add(dlgHint("Consulta o GitHub no máximo 1× por dia. Nenhum dado seu é enviado."), c);

        int r = JOptionPane.showConfirmDialog(this, panel, "Ajustes",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        cfg.minFocusMin   = (int) spFocus.getValue();
        cfg.idleMinutes   = (int) spIdle.getValue();
        cfg.transitionMin = (int) spTrans.getValue();
        cfg.hyperfocusH   = (int) spHyper.getValue();
        cfg.pomoAutoCycle = cbCycle.isSelected();
        cfg.focusMode     = cbFocus.isSelected();
        cfg.askDetails    = cbAsk.isSelected();
        cfg.updateCheck   = cbUpdate.isSelected();
        String rem = (String) cbReminder.getSelectedItem();
        cfg.reminderHour = (rem == null || rem.startsWith("Des")) ? -1 : Integer.parseInt(rem.substring(0, 2));
        if (autoCycleCheck != null) autoCycleCheck.setSelected(cfg.pomoAutoCycle);
        saveData();
        toast("Ajustes salvos.");
    }

    // ── TEMA ────────────────────────────────────────────────────────────────

    private void toggleTheme() {
        String[] options = {"Claro", "Escuro"};
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
        // Para os tiques antigos; o estado das sessões está nos modelos (sw, pomo).
        if (stopwatch != null) stopwatch.stop();
        if (countdownTimer != null) countdownTimer.stop();
        // buildUI() cria um combo novo: sem isso a matéria volta para o primeiro item.
        Object sel = subjectComboBox != null ? subjectComboBox.getSelectedItem() : null;

        SwingUtilities.updateComponentTreeUI(this);   // aplica a paleta nova do L&F
        getContentPane().removeAll();
        getContentPane().setBackground(AppTheme.BG);
        buildUI();

        // Reconecta listeners e restaura estado
        setupTimers();
        if (sw.isRunning())   stopwatch.start();
        if (pomo.isRunning()) countdownTimer.start();

        refreshCombo();
        if (sel != null && studyDataMap.containsKey(sel) && !archived.contains(sel))
            subjectComboBox.setSelectedItem(sel);   // o listener do combo atualiza sub-foco e metas
        updateUI();
        syncTimerControls();   // buildUI() recria as abas "paradas"; volta botões e rótulos ao estado real
        revalidate();
        repaint();
    }

    // ── UPDATE UI ───────────────────────────────────────────────────────────

    private void updateUI() {
        // Total header
        int total = studyDataMap.values().stream().mapToInt(StudyData::getMinutes).sum();
        int th = total / 60, tm = total % 60;
        tvTotal.setText(String.format("Total: %dh %02dmin", th, tm));

        updateGoalProgress();
        refreshStreaks();
        if (weekScreen != null) weekScreen.setAreas(activeAreas(), areaColors());
        if (areasScreen != null) areasScreen.refresh();
        refreshTodayCard();
        refreshNowCard();
        if (progressScreen != null) progressScreen.refresh();   // donut, calendário, Progresso, Metas, Equilíbrio
        updatePomodoroCount();
        balanceNudge();
        saveData();
    }

    private void refreshCombo() {
        Object sel = subjectComboBox.getSelectedItem();
        subjectComboBox.removeAllItems();
        // Agrupa por tipo (Estudo → Físico → Lazer → Trabalho → Outro), preservando a ordem do usuário dentro do grupo.
        for (String t : TYPE_KEYS)
            for (String s : studyDataMap.keySet())
                if (!archived.contains(s) && typeOf(s).equals(t)) subjectComboBox.addItem(s);
        // Qualquer tipo desconhecido (não deveria acontecer) entra no fim.
        for (String s : studyDataMap.keySet())
            if (!archived.contains(s) && typeIndexKnown(typeOf(s)) < 0) subjectComboBox.addItem(s);
        if (sel != null && !archived.contains(sel)) subjectComboBox.setSelectedItem(sel);
    }

    private static int typeIndexKnown(String key) {
        for (int i = 0; i < TYPE_KEYS.length; i++) if (TYPE_KEYS[i].equals(key)) return i;
        return -1;
    }

    /** Diálogo para editar os sub-focos de uma área (um por linha). */
    private void showSubFocosDialog(String area) {
        String tipo = typeOf(area);
        java.util.List<String> atuais = subFocos.getOrDefault(area, new ArrayList<>());
        JTextArea ta = new JTextArea(String.join("\n", atuais), 8, 22);
        ta.setFont(AppTheme.FONT_LABEL);

        JButton preset = new JButton("Usar sugestões de " + typeLabel(tipo));
        preset.setFont(AppTheme.FONT_SMALL);
        preset.setFocusPainted(false);
        preset.setEnabled(subFocoPreset(tipo).length > 0);
        preset.addActionListener(e -> {
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            for (String s : ta.getText().split("\n")) if (!s.trim().isEmpty()) set.add(s.trim());
            java.util.Collections.addAll(set, subFocoPreset(tipo));
            ta.setText(String.join("\n", set));
        });

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JLabel h = dlgLabel("Sub-focos de \"" + area + "\" — um por linha", true);
        panel.add(h, BorderLayout.NORTH);
        panel.add(new JScrollPane(ta), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(dlgHint("Ex.: tópicos da prova, grupos musculares, hobbies."), BorderLayout.NORTH);
        JPanel pr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4)); pr.add(preset);
        south.add(pr, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);

        int r = JOptionPane.showConfirmDialog(this, panel, "Sub-focos",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        java.util.List<String> nova = new ArrayList<>();
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (String line : ta.getText().split("\n")) {
            String v = AppTheme.semHtml(line.replace('|', '/').trim());
            if (!v.isEmpty() && vistos.add(v.toLowerCase())) nova.add(v);
        }
        if (nova.isEmpty()) subFocos.remove(area); else subFocos.put(area, nova);
        saveData();
        toast("Sub-focos de \"" + area + "\" atualizados.");
    }

    // ── PERSISTÊNCIA ────────────────────────────────────────────────────────

    private void saveData() {
        // Não grava nada até a interface terminar de carregar: a primeira chamada
        // de updateUI() disparava um save inútil que podia persistir um estado
        // incompleto caso a leitura tivesse falhado.
        if (!ready) return;
        // Se a leitura anterior falhou ou o mapa está vazio por engano, não
        // sobrescreve o arquivo — evita apagar as matérias do usuário.
        if (!loadOk || studyDataMap.isEmpty()) return;

        Properties p = new Properties();
        studyDataMap.forEach((k, v) -> p.setProperty(k, v.getMinutes() + "," + v.getColor().getRGB()));
        goals.forEach((k, v)      -> p.setProperty("goal_"  + k, String.valueOf(v)));
        goalsWeek.forEach((k, v)  -> p.setProperty("goalw_" + k, String.valueOf(v)));
        goalsMonth.forEach((k, v) -> p.setProperty("goalm_" + k, String.valueOf(v)));
        goalDays.forEach((k, set) -> {
            StringBuilder sb = new StringBuilder();
            for (java.time.DayOfWeek dw : set) { if (sb.length() > 0) sb.append(','); sb.append(dw.getValue()); }
            p.setProperty("goaldays_" + k, sb.toString());
        });
        examDate.forEach((k, dt) -> p.setProperty("exam_" + k, dt.toString())); // ISO yyyy-MM-dd
        areaType.forEach((k, v) -> { if (!"estudo".equals(v)) p.setProperty("type_" + k, v); });
        typeGoalWeek.forEach((k, v) -> { if (v > 0) p.setProperty("typegoalw_" + k, String.valueOf(v)); });
        subFocos.forEach((k, list) -> { if (!list.isEmpty()) p.setProperty("subfocos_" + k, String.join(",", list)); });
        goalsMin.forEach((k, v) -> { if (v > 0) p.setProperty("goalmin_" + k, String.valueOf(v)); });
        if (!hardDays.isEmpty()) p.setProperty("__hard_days__", String.join(",", hardDays));
        weekPlan.forEach((k, l) -> { if (!l.isEmpty()) p.setProperty("plan_" + k, String.join("|", l)); });
        if (!archived.isEmpty()) p.setProperty("__archived__", String.join(",", archived));
        streakBest.forEach((k, v) -> p.setProperty("streakbest_" + k, String.valueOf(v)));
        unknownProps.forEach(p::setProperty);   // preserva chaves que não soubemos ler
        p.setProperty("__streakbest_general__", String.valueOf(streakBestGeneral));
        p.setProperty("__streak_milestone__", String.valueOf(celebratedStreakMilestone));
        cfg.writeTo(p);
        p.setProperty("__theme__", AppTheme.dark ? "dark" : "light");

        STORE.writeData(p);
    }

    private void loadData() {
        try {
            Properties p = STORE.readData();
            if (p == null) return;   // primeiro uso de verdade
            for (String k : p.stringPropertyNames()) {
                if (cfg.readKey(k, p.getProperty(k))) continue;   // ajustes e marcadores
                if (k.equals("__theme__")) {
                    if ("dark".equals(p.getProperty(k))) AppTheme.applyDark();
                    else AppTheme.applyLight();
                    continue;
                }
                if (k.startsWith("goalw_")) {
                    try { goalsWeek.put(k.substring(6), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.startsWith("goalm_")) {
                    try { goalsMonth.put(k.substring(6), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.equals("__streakbest_general__")) {
                    try { streakBestGeneral = Integer.parseInt(p.getProperty(k)); } catch (Exception ignored) {}
                    continue;
                }
                if (k.equals("__streak_milestone__")) {
                    try { celebratedStreakMilestone = Integer.parseInt(p.getProperty(k)); } catch (Exception ignored) {}
                    continue;
                }
                if (k.equals("__archived__")) {
                    for (String a : p.getProperty(k).split(","))
                        if (!a.trim().isEmpty()) archived.add(a.trim());
                    continue;
                }
                if (k.startsWith("streakbest_")) {
                    try { streakBest.put(k.substring(11), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.startsWith("goaldays_")) {
                    try {
                        java.util.Set<java.time.DayOfWeek> set = java.util.EnumSet.noneOf(java.time.DayOfWeek.class);
                        for (String tok : p.getProperty(k).split(","))
                            if (!tok.trim().isEmpty()) set.add(java.time.DayOfWeek.of(Integer.parseInt(tok.trim())));
                        if (!set.isEmpty() && set.size() < 7) goalDays.put(k.substring(9), set);
                    } catch (Exception ignored) {}
                    continue;
                }
                if (k.startsWith("exam_")) {
                    try { examDate.put(k.substring(5), LocalDate.parse(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.startsWith("typegoalw_")) {
                    try { typeGoalWeek.put(k.substring(10), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.startsWith("subfocos_")) {
                    java.util.List<String> l = new ArrayList<>();
                    for (String v : p.getProperty(k).split(",")) if (!v.trim().isEmpty()) l.add(v.trim());
                    if (!l.isEmpty()) subFocos.put(k.substring(9), l);
                    continue;
                }
                if (k.startsWith("type_")) {
                    areaType.put(k.substring(5), p.getProperty(k));
                    continue;
                }
                if (k.startsWith("plan_")) {
                    java.util.List<String> l = new ArrayList<>();
                    for (String a : p.getProperty(k).split("\\|"))
                        if (!a.trim().isEmpty() && !l.contains(a.trim())) l.add(a.trim());
                    if (!l.isEmpty()) weekPlan.put(k.substring(5), l);
                    continue;
                }
                if (k.equals("__hard_days__")) {
                    for (String d : p.getProperty(k).split(",")) if (!d.trim().isEmpty()) hardDays.add(d.trim());
                    continue;
                }
                if (k.startsWith("goalmin_")) {
                    try { goalsMin.put(k.substring(8), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.startsWith("goal_")) {
                    try { goals.put(k.substring(5), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                String[] parts = p.getProperty(k).split(",");
                boolean parsed = false;
                if (parts.length == 2) {
                    try {
                        studyDataMap.put(k, new StudyData(Integer.parseInt(parts[0].trim()),
                                new Color(Integer.parseInt(parts[1].trim()))));
                        parsed = true;
                    } catch (Exception ignored) {}
                }
                if (!parsed) {
                    // Não conseguimos interpretar esta linha: guarda-a crua para
                    // reescrevê-la intacta e não perder o dado.
                    unknownProps.put(k, p.getProperty(k));
                }
            }
        } catch (IOException e) {
            // O arquivo existe mas não pôde ser lido: NÃO tratar como primeiro uso,
            // senão o próximo save apaga tudo.
            loadOk = false;
            e.printStackTrace();
        }
    }

    private void saveSessions() {
        if (!ready || !loadOk) return;
        STORE.writeSessions(sessions);
    }

    private void loadSessions() {
        try {
            sessions.addAll(STORE.readSessions());
        } catch (IOException e) {
            loadOk = false;   // não deixa um save posterior truncar o histórico
            e.printStackTrace();
        }
    }

    private void saveChecklist() {
        if (!ready || !loadOk) return;
        STORE.writeChecklist(checklist);
    }

    private void loadChecklist() {
        try {
            checklist.addAll(STORE.readChecklist());
        } catch (IOException e) {
            loadOk = false;   // idem: antes, falhar aqui deixava o próximo save apagar as tarefas
            e.printStackTrace();
        }
    }

    // ── UTILS ───────────────────────────────────────────────────────────────

    private void toast(String msg) {
        Window anchor = focusBar != null ? focusBar : this;   // app recolhido: aparece embaixo da faixa
        JWindow w = new JWindow(anchor);
        JLabel lbl = new JLabel(msg);
        lbl.setFont(AppTheme.FONT_LABEL);
        lbl.setForeground(Color.WHITE);
        lbl.setBorder(new EmptyBorder(9, 16, 9, 16));
        RoundedPanel panel = new RoundedPanel(12, new Color(0x2A2D45));
        panel.setLayout(new BorderLayout());
        panel.add(lbl);
        try { w.setBackground(new Color(0, 0, 0, 0)); }   // cantos arredondados sem retângulo
        catch (Exception ignored) { panel.setOpaque(true); }
        w.setContentPane(panel);
        w.pack();

        int baseX = anchor.getX() + (anchor.getWidth() - w.getWidth()) / 2;
        int endY  = focusBar != null ? anchor.getY() + anchor.getHeight() + 10
                                     : getY() + getHeight() - w.getHeight() - 56;
        int slide = 18;

        try { w.setOpacity(0f); } catch (Exception ignored) {}
        w.setLocation(baseX, endY + slide);
        w.setVisible(true);

        final long t0 = System.currentTimeMillis();
        final int IN = 180, HOLD = 2000, OUT = 320, TOTAL = IN + HOLD + OUT;
        Timer anim = new Timer(15, null);
        anim.addActionListener(e -> {
            long dt = System.currentTimeMillis() - t0;
            if (dt >= TOTAL) { anim.stop(); w.dispose(); return; }
            float op; int y;
            if (dt < IN) {                       // entra deslizando p/ cima + fade
                float p = dt / (float) IN;
                op = p; y = Math.round(endY + slide * (1 - p));
            } else if (dt < IN + HOLD) {         // segura
                op = 1f; y = endY;
            } else {                             // sai deslizando + fade
                float p = (dt - IN - HOLD) / (float) OUT;
                op = 1 - p; y = Math.round(endY - slide * p);
            }
            try { w.setOpacity(Math.max(0f, Math.min(1f, op))); } catch (Exception ignored) {}
            w.setLocation(baseX, y);
        });
        anim.start();
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
        boolean freshStart = studyDataMap.isEmpty();
        if (freshStart) promptForSubjects();
        refreshCombo();
        updateUI();               // ainda sem gravar (ready == false)
        ready = true;             // a partir daqui os saves são liberados
        if (freshStart) saveData(); // persiste as matérias criadas no primeiro uso
        setVisible(true);
        if (migratedFrom != null)
            showInfoDialog("Seus dados mudaram de lugar",
                    "A partir desta versão, seus dados ficam guardados em:\n"
                  + DATA_DIR.getAbsolutePath() + "\n\n"
                  + "Assim você pode atualizar o FocaEstudo sem perder nada.\n"
                  + "Os arquivos antigos continuam na pasta do programa, sem uso.");
        SwingUtilities.invokeLater(this::maybeShowWeeklySummary);
        setupReminder();
        startTodayTimer();
        maybeCheckForUpdate();
    }

    // ── AVISO DE VERSÃO NOVA ───────────────────────────────────────────────

    private static final String RELEASES_LATEST = "https://github.com/ErickDraber/FocaEstudo/releases/latest";

    /** No máximo 1× por dia, pergunta ao GitHub qual é a última versão. Não envia dado nenhum. */
    private void maybeCheckForUpdate() {
        String atual = System.getProperty("jpackage.app-version");   // só existe no .exe
        if (!cfg.updateCheck || atual == null) return;
        String hoje = LocalDate.now().toString();
        if (hoje.equals(cfg.updateCheckedDay)) return;
        Thread t = new Thread(() -> {
            String nova = fetchLatestVersion();
            if (nova == null) return;   // sem internet: tenta de novo na próxima abertura
            SwingUtilities.invokeLater(() -> {
                cfg.updateCheckedDay = hoje;
                saveData();
                if (compareVersions(nova, atual) > 0) showUpdateDialog(atual, nova);
            });
        }, "FocaEstudo-atualizacao");
        t.setDaemon(true);
        t.start();
    }

    /** O GitHub redireciona /releases/latest para /releases/tag/vX.Y.Z: a versão vem do endereço. */
    private static String fetchLatestVersion() {
        java.net.HttpURLConnection con = null;
        try {
            con = (java.net.HttpURLConnection) java.net.URI.create(RELEASES_LATEST).toURL().openConnection();
            con.setInstanceFollowRedirects(false);
            con.setRequestMethod("HEAD");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("User-Agent", "FocaEstudo");
            String loc = con.getHeaderField("Location");
            if (loc == null) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("/releases/tag/v?(\\d{1,4}\\.\\d{1,4}\\.\\d{1,4})$").matcher(loc);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** Compara "1.10.0" com "1.9.2" número a número. */
    private static int compareVersions(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int dx = 0, dy = 0;
            try { if (i < x.length) dx = Integer.parseInt(x[i].trim()); } catch (NumberFormatException ignored) {}
            try { if (i < y.length) dy = Integer.parseInt(y[i].trim()); } catch (NumberFormatException ignored) {}
            if (dx != dy) return Integer.compare(dx, dy);
        }
        return 0;
    }

    private void showUpdateDialog(String atual, String nova) {
        Object[] opts = {"Abrir página de download", "Agora não"};
        int r = JOptionPane.showOptionDialog(this,
                "Saiu a versão " + nova + " do FocaEstudo (você está usando a " + atual + ").\n\n"
              + "Para atualizar: baixe o zip, feche o FocaEstudo e extraia por cima da pasta atual.\n"
              + "Seus dados ficam guardados fora da pasta do programa, então nada se perde.",
                "Versão nova", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, opts, opts[0]);
        if (r != 0) return;
        try { Desktop.getDesktop().browse(java.net.URI.create(RELEASES_LATEST)); }
        catch (Exception e) { showInfoDialog("Versão nova", "Baixe em:\n" + RELEASES_LATEST); }
    }

    // ── LEMBRETE DIÁRIO (item 8) ───────────────────────────────────────────

    private void setupReminder() {
        if (java.awt.SystemTray.isSupported()) {
            try {
                trayIcon = new java.awt.TrayIcon(AppTheme.appIcon(16), "FocaEstudo");
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> {
                    if (focusBar != null) { exitFocusMode(); return; }
                    setVisible(true); setState(Frame.NORMAL); toFront();
                });
                java.awt.SystemTray.getSystemTray().add(trayIcon);
            } catch (Exception e) { trayIcon = null; }
        }
        reminderTimer = new Timer(5 * 60 * 1000, e -> checkReminder());
        reminderTimer.setInitialDelay(30 * 1000);
        reminderTimer.start();
    }

    private void checkReminder() {
        if (cfg.reminderHour < 0) return;
        LocalDate today = LocalDate.now();
        String hoje = today.toString();
        if (hoje.equals(cfg.reminderShownDay)) return;
        if (isHardDay(today)) { cfg.reminderShownDay = hoje; return; }             // dia difícil: sem lembrete
        if (java.time.LocalTime.now().getHour() < cfg.reminderHour) return;
        if (minutesOnDay(null, today) > 0) { cfg.reminderShownDay = hoje; return; } // já estudou hoje

        cfg.reminderShownDay = hoje;
        saveData();
        String msg = "Que tal uns minutos de estudo hoje? Sua sequência agradece. 🔥";
        if (trayIcon != null) {
            trayIcon.displayMessage("FocaEstudo", msg, java.awt.TrayIcon.MessageType.INFO);
        } else {
            toast(msg);
        }
    }

    // ── RESUMO SEMANAL (item 7) ─────────────────────────────────────────────

    private void maybeShowWeeklySummary() {
        if (sessions.isEmpty()) return;
        LocalDate today = LocalDate.now();
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        String semana = today.get(wf.weekBasedYear()) + "-W" + today.get(wf.weekOfWeekBasedYear());
        if (semana.equals(cfg.weeklySummaryShownWeek)) return;
        // só mostra a partir de sábado/domingo (fim de semana) ou se nunca mostrou
        if (cfg.weeklySummaryShownWeek != null
                && today.getDayOfWeek().getValue() < 6) return;

        cfg.weeklySummaryShownWeek = semana;
        saveData();

        LocalDate ini = today.minusDays(6);
        int totalSemana = minutesInRange(null, ini, today);
        if (totalSemana == 0) return;

        int totalAnterior = minutesInRange(null, ini.minusDays(7), today.minusDays(7));
        int diasMeta = 0, diasComEstudo = 0;
        String topSubj = null; int topMin = 0;
        for (String s : studyDataMap.keySet()) {
            int m = minutesInRange(s, ini, today);
            if (m > topMin) { topMin = m; topSubj = s; }
        }
        int metaDia = dailyGoalFor(null);
        for (LocalDate d = ini; !d.isAfter(today); d = d.plusDays(1)) {
            int m = minutesOnDay(null, d);
            if (m > 0) diasComEstudo++;
            if (metaDia > 0 && m >= metaDia) diasMeta++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Últimos 7 dias\n\n");
        sb.append("• Total estudado: ").append(fmtHM(totalSemana)).append('\n');
        sb.append("• Dias com estudo: ").append(diasComEstudo).append(" de 7\n");
        if (metaDia > 0) sb.append("• Meta diária batida em ").append(diasMeta).append(" de 7 dias\n");
        if (topSubj != null) sb.append("• Matéria destaque: ").append(topSubj)
                .append(" (").append(fmtHM(topMin)).append(")\n");
        if (totalAnterior > 0) {
            int d = totalSemana - totalAnterior;
            sb.append("• vs. semana anterior: ")
              .append(d >= 0 ? "▲ +" + fmtHM(d) : "▼ " + fmtHM(-d)).append('\n');
        }
        sb.append("\nBora manter o ritmo!");

        JOptionPane.showMessageDialog(this, sb.toString(),
                "Resumo da semana", JOptionPane.INFORMATION_MESSAGE);
    }

    private void promptForSubjects() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1; c.insets = new Insets(0, 0, 6, 0);

        JLabel ic = new JLabel(new ImageIcon(AppTheme.appIcon(48)));
        c.gridy = 0; c.insets = new Insets(0, 0, 10, 0); panel.add(ic, c);

        JLabel h1 = new JLabel("Bem-vindo ao FocaEstudo");
        h1.setFont(AppTheme.FONT_SECTION);
        c.gridy = 1; c.insets = new Insets(0, 0, 4, 0); panel.add(h1, c);

        JLabel h2 = new JLabel("Quais matérias você vai acompanhar? Separe por vírgula.");
        h2.setFont(AppTheme.FONT_SMALL);
        c.gridy = 2; c.insets = new Insets(0, 0, 8, 0); panel.add(h2, c);

        JTextField f = new JTextField("Cálculo, Física, Programação", 26);
        f.setFont(AppTheme.FONT_LABEL);
        f.selectAll();
        c.gridy = 3; panel.add(f, c);

        int r = JOptionPane.showConfirmDialog(this, panel, "Configuração inicial",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        String input = r == JOptionPane.OK_OPTION ? f.getText() : null;
        if (input == null || input.trim().isEmpty()) { System.exit(0); return; }
        areas.addInitial(input);
        if (studyDataMap.isEmpty()) { System.exit(0); }
    }

    public static void main(String[] args) {
        DATA_DIR.mkdirs();
        if (!STORE.acquireSingleInstanceLock()) {
            int r = JOptionPane.showConfirmDialog(null,
                    "O FocaEstudo parece já estar aberto.\n"
                  + "Abrir assim mesmo pode corromper seus dados.\n\nAbrir mesmo assim?",
                    "FocaEstudo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) System.exit(0);
        }
        migratedFrom = STORE.migrateLegacyFrom(BASE_DIR);   // depois da trava: duas instâncias não migram juntas
        AppTheme.installLookAndFeel();
        SwingUtilities.invokeLater(() -> new StudyTracker().initializeUI());
    }
}
