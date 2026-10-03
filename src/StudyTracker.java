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
    // pasta do programa: trocar de versão não perde nada e o .bat e o .exe usam o
    // mesmo save. BASE_DIR (pasta do programa) só serve para migrar os dados das
    // versões até a 1.0.2, que gravavam ao lado do programa.
    private static final File BASE_DIR       = resolveBaseDir();
    private static final File DATA_DIR       = resolveDataDir();
    private static final File SAVE_FILE      = new File(DATA_DIR, "study_data.properties");
    private static final File SESSION_FILE   = new File(DATA_DIR, "study_sessions.properties");
    private static final File CHECKLIST_FILE = new File(DATA_DIR, "study_checklist.properties");
    private static final String MIGRATED_MARKER = "DADOS_MOVIDOS.txt";
    private static File migratedFrom = null;   // != null ⇒ avisar o usuário que os dados mudaram de lugar

    private static File resolveDataDir() {
        String custom = System.getProperty("focaestudo.dados");   // testes: java -Dfocaestudo.dados=<pasta>
        if (custom != null && !custom.trim().isEmpty()) return new File(custom.trim());
        String appData = System.getenv("APPDATA");
        if (appData != null && new File(appData).isDirectory()) return new File(appData, "FocaEstudo");
        return BASE_DIR;   // fora do Windows: continua ao lado do programa
    }

    /**
     * Copia (não move) os dados da pasta do programa para DATA_DIR, uma vez só.
     * Nunca sobrescreve: se DATA_DIR já tem dados, não mexe em nada.
     */
    private static void migrateLegacyData() {
        if (DATA_DIR.getAbsoluteFile().equals(BASE_DIR.getAbsoluteFile())) return;
        if (SAVE_FILE.exists()) return;
        if (new File(BASE_DIR, MIGRATED_MARKER).exists()) return;
        if (!new File(BASE_DIR, SAVE_FILE.getName()).isFile()) return;   // nada a migrar
        try {
            // study_data por último: é a existência dele que marca a migração como feita.
            for (File f : new File[]{SESSION_FILE, CHECKLIST_FILE, SAVE_FILE})
                for (String suf : new String[]{".bak", ""}) {
                    File src = new File(BASE_DIR, f.getName() + suf);
                    if (src.isFile())
                        java.nio.file.Files.copy(src.toPath(), new File(DATA_DIR, src.getName()).toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                }
        } catch (IOException e) {
            e.printStackTrace();   // sem study_data em DATA_DIR, a próxima abertura tenta de novo
            return;
        }
        migratedFrom = BASE_DIR;
        try {
            java.nio.file.Files.write(new File(BASE_DIR, MIGRATED_MARKER).toPath(), (
                    "A partir da versão 1.1.0, o FocaEstudo guarda seus dados em:\r\n"
                  + "    " + DATA_DIR.getAbsolutePath() + "\r\n\r\n"
                  + "Os arquivos study_*.properties desta pasta são a cópia antiga e não são mais usados.\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {}   // sem permissão de escrita: só não deixa o aviso
    }

    /** Descobre a raiz do projeto a partir da localização das classes (…/bin ⇒ raiz). */
    private static File resolveBaseDir() {
        // Empacotado pelo jpackage (FocaEstudo.exe): dados ao lado do .exe, não em …/app
        String exe = System.getProperty("jpackage.app-path");
        if (exe != null) {
            File dir = new File(exe).getParentFile();
            if (dir != null && dir.isDirectory()) return dir;
        }
        try {
            File loc = new File(StudyTracker.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = loc.isFile() ? loc.getParentFile() : loc;   // …/bin  ou  …/app.jar
            if (dir != null && "bin".equalsIgnoreCase(dir.getName()) && dir.getParentFile() != null)
                dir = dir.getParentFile();
            if (dir != null && dir.isDirectory()) return dir;
        } catch (Exception ignored) {}
        return new File(System.getProperty("user.dir"));
    }

    private final Map<String, StudyData>  studyDataMap = new LinkedHashMap<>();
    private final List<StudySession>      sessions     = new ArrayList<>();
    private final List<ChecklistItem>     checklist    = new ArrayList<>();

    // --- UI principal ---
    private PieChartPanel chartPanel;
    private CalendarPanel  calendarPanel;
    private JPanel        summaryPanel;
    private JComboBox<String> subjectComboBox;
    private JLabel        tvTotal;
    private JPanel        goalBar;
    private GaugeBar      goalProgress;
    private JLabel        tvGoalLabel, tvGoalPercent;
    private RoundedPanel  progressCard;
    private JPanel        progressBody;
    private RoundedPanel  goalsCard;
    private JPanel        goalsBody;
    private RoundedPanel  balanceCard;
    private JPanel        balanceBody;
    private RoundedPanel  nowCard;
    private JPanel        nowBody;
    private CustomTabs    bottomTabs;
    private boolean       hyperfocusWarned = false;

    // --- Cronômetro ---
    private Timer  stopwatch;
    private int    elapsedSeconds = 0;         // tempo LÍQUIDO (só conta rodando)
    private JLabel stopwatchLabel;
    private JLabel stopwatchInfoLabel;         // "líquido · N pausas · bruto mm:ss"
    private long   stopwatchGrossStartMs = 0;  // 0 = ainda não iniciado
    private int    stopwatchPauseCount    = 0;
    private StyledButton startButton, stopButton, submitButton, resetStopwatchButton;

    // --- Pomodoro ---
    private Timer   countdownTimer;
    private int     countdownSecondsRemaining = 0;
    private boolean wasFocusSession = false;
    private int     plannedMinutes  = 0;
    private boolean isPaused        = false;
    private int     pomoCycleFocos  = 0;       // focos concluídos no ciclo atual (p/ pausa longa)
    private JLabel  countdownLabel, statusLabel, pomoCountLabel;
    private JSpinner focusSpinner, breakSpinner;
    private JCheckBox autoCycleCheck;
    private StyledButton startFocusButton, startBreakButton, stopCountdownButton, resetButton;

    // --- Inatividade ---
    private Timer idleTimer;
    private long  lastActivityMs = System.currentTimeMillis();
    private boolean idleAsking = false;

    // --- Ajustes (persistidos em study_data como __chave__) ---
    private int     setMinFocusMin   = 1;      // não salva sessão de cronômetro/pomodoro abaixo disso
    private int     setIdleMinutes   = 10;     // 0 = desativado
    private boolean setPomoAutoCycle = false;  // encadeia foco → pausa → foco automaticamente
    private int     setReminderHour  = -1;     // hora do lembrete diário (0-23); -1 = desligado
    private int     setTransitionMin = 5;      // pausa sugerida entre blocos (min); 0 = desligado
    private int     setHyperfocusH   = 3;      // aviso de hiperfoco após N h seguidas; 0 = desligado
    private boolean setUpdateCheck   = true;   // consulta o GitHub (1×/dia) se saiu versão nova
    private String  updateCheckedDay = null;   // ISO date da última consulta que deu certo

    // --- Estado de recorrências ---
    private String  weeklySummaryShownWeek = null; // ISO "2026-W37" da última vez que mostrou o resumo
    private String  reminderShownDay       = null; // ISO date do último lembrete mostrado
    private Timer   reminderTimer;
    private java.awt.TrayIcon trayIcon;

    // --- Manual ---
    private JTextField hoursField, minutesField;

    // --- Nota + tipo de atividade aplicados à próxima sessão salva ---
    private JTextField noteField;
    private JComboBox<String> kindCombo;
    private JComboBox<String> energyCombo;
    private static final String[] ENERGY_LABELS = {"–", "😴 baixa", "🙂 ok", "🔥 alta"};
    private static final String[] ENERGY_ICON   = {"", "😴", "🙂", "🔥"};
    private static final String[] KIND_LABELS = {"–", "Teoria", "Exercícios", "Revisão", "Outro"};
    private static final String[] KIND_KEYS   = {"", "teoria", "exercicios", "revisao", "outro"};
    private static final String KIND_MANAGE = "＋ gerenciar sub-focos…";

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
    private static final String[] TYPE_KEYS   = {"estudo", "fisico", "lazer", "trabalho", "outro"};
    private static final String[] TYPE_LABELS = {"Estudo", "Físico", "Lazer", "Trabalho", "Outro"};
    private static final String[] TYPE_ICONS  = {"📚", "💪", "🎮", "💼", "•"};
    // Meta mínima semanal por TIPO (minutos). 0/ausente = sem meta de equilíbrio.
    private final Map<String, Integer> typeGoalWeek = new LinkedHashMap<>();
    private String balanceNudgeWeek = null;   // ISO "2026-W37" da última vez que avisou desequilíbrio

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
            if (kfm.getFocusedWindow() != this) return false;               // diálogo aberto: ignora
            if (kfm.getFocusOwner() instanceof javax.swing.text.JTextComponent) return false;
            toggleStopwatch();
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
        if (stopwatch != null && stopwatch.isRunning()) stopStopwatch();
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
        if (idleAsking || setIdleMinutes <= 0) return;
        if (stopwatch == null || !stopwatch.isRunning()) return;
        long idleMs = System.currentTimeMillis() - lastActivityMs;
        if (idleMs < setIdleMinutes * 60_000L) return;

        idleAsking = true;
        stopStopwatch();                       // pausa enquanto pergunta
        int r = JOptionPane.showConfirmDialog(this,
                "Sem atividade há " + setIdleMinutes + " min.\nAinda está estudando?",
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
    private JPanel       areasBody;

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

    /** Cabeçalho das telas: título + dica + botões à direita (opcionais). */
    private JPanel screenHeader(String title, String hint, JComponent... actions) {
        JPanel h = new JPanel(new BorderLayout(12, 0));
        h.setBackground(AppTheme.SURFACE);
        h.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.DIVIDER),
                new EmptyBorder(12, 16, 12, 16)));
        JPanel txt = new JPanel(new GridLayout(hint != null ? 2 : 1, 1, 0, 2));
        txt.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(AppTheme.FONT_SECTION); t.setForeground(AppTheme.TEXT_PRI);
        txt.add(t);
        if (hint != null) {
            JLabel l = new JLabel(hint);
            l.setFont(AppTheme.FONT_SMALL); l.setForeground(AppTheme.TEXT_SEC);
            txt.add(l);
        }
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        for (JComponent a : actions) btns.add(a);
        h.add(txt,  BorderLayout.WEST);
        h.add(btns, BorderLayout.EAST);
        return h;
    }

    /** Painel que acompanha a largura do scroll (sem rolagem horizontal). */
    private static class ScrollBody extends JPanel implements Scrollable {
        ScrollBody(LayoutManager lm) { super(lm); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 32; }
        public boolean getScrollableTracksViewportWidth()  { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static JScrollPane quietScroll(JComponent body) {
        JScrollPane sp = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // ── TELA HOJE: o que fazer agora + cronômetro ──────────────────────────

    private JPanel buildTodayScreen() {
        // Duas colunas: plano do dia e sugestão à esquerda; metas e equilíbrio à direita.
        ScrollBody cols = new ScrollBody(new GridLayout(1, 2, 12, 0));
        cols.setBorder(new EmptyBorder(12, 12, 12, 12));
        cols.add(cardColumn(buildTodayCard(), buildNowCard()));
        cols.add(cardColumn(buildGoalsCard(), buildBalanceCard()));

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppTheme.BG);
        p.add(quietScroll(cols), BorderLayout.CENTER);
        p.add(buildBottom(),     BorderLayout.SOUTH);
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

    private List<String> activeAreas() {
        List<String> areas = new ArrayList<>();
        for (String s : studyDataMap.keySet()) if (!archived.contains(s)) areas.add(s);
        return areas;
    }

    private Map<String, Color> areaColors() {
        Map<String, Color> colorMap = new LinkedHashMap<>();
        studyDataMap.forEach((k, v) -> colorMap.put(k, v.getColor()));
        return colorMap;
    }

    // ── TELA ÁREAS: criar, editar, arquivar e deletar ──────────────────────
    // Esses botões ficavam sempre à vista embaixo do cronômetro (com o "Deletar" vermelho a
    // um clique). Aqui cada área tem os seus, e mexer numa área não troca a do cronômetro.

    private JPanel buildAreasScreen() {
        StyledButton add = new StyledButton("+ Área", StyledButton.Variant.FILLED);
        add.addActionListener(e -> addNewSubject());
        areasBody = new ScrollBody(null);
        areasBody.setLayout(new BoxLayout(areasBody, BoxLayout.Y_AXIS));
        areasBody.setBorder(new EmptyBorder(12, 16, 16, 16));

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppTheme.BG);
        p.add(screenHeader("Áreas", "Suas áreas da vida, por tipo. O histórico de cada uma fica guardado.", add),
                BorderLayout.NORTH);
        p.add(quietScroll(areasBody), BorderLayout.CENTER);
        return p;
    }

    private void refreshAreasScreen() {
        if (areasBody == null) return;
        areasBody.removeAll();
        for (int t = 0; t < TYPE_KEYS.length; t++) {
            List<String> doTipo = new ArrayList<>();
            for (String s : studyDataMap.keySet())
                if (!archived.contains(s) && typeOf(s).equals(TYPE_KEYS[t])) doTipo.add(s);
            if (doTipo.isEmpty()) continue;
            areasBody.add(areasSection(TYPE_LABELS[t]));
            for (String s : doTipo) areasBody.add(areaRow(s, false));
        }
        if (!archived.isEmpty()) {
            areasBody.add(areasSection("Arquivadas"));
            for (String s : archived) if (studyDataMap.containsKey(s)) areasBody.add(areaRow(s, true));
        }
        areasBody.revalidate();
        areasBody.repaint();
    }

    private JComponent areasSection(String label) {
        JLabel l = new JLabel(label);
        l.setFont(AppTheme.FONT_BOLD);
        l.setForeground(AppTheme.TEXT_SEC);
        l.setBorder(new EmptyBorder(areasBody.getComponentCount() == 0 ? 0 : 14, 4, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JComponent areaRow(String area, boolean arquivada) {
        StudyData sd = studyDataMap.get(area);
        RoundedPanel row = new RoundedPanel(12, AppTheme.SURFACE);
        row.setLayout(new GridBagLayout());   // nome e botões centralizados na mesma linha
        row.setBorder(new EmptyBorder(4, 12, 4, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JLabel nome = new JLabel(area, PlannerPanel.dot(sd.getColor()), SwingConstants.LEFT);
        nome.setIconTextGap(8);
        nome.setFont(AppTheme.FONT_LABEL);
        nome.setForeground(arquivada ? AppTheme.TEXT_SEC : AppTheme.TEXT_PRI);
        JLabel total = new JLabel(fmtHM(sd.getMinutes()));
        total.setFont(AppTheme.FONT_SMALL);
        total.setForeground(AppTheme.TEXT_SEC);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(nome); left.add(total);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btns.setOpaque(false);
        if (arquivada) {
            btns.add(smallBtn("Reativar", StyledButton.Variant.TEXT, () -> unarchiveSubject(area)));
        } else {
            btns.add(smallBtn("Editar",   StyledButton.Variant.TEXT, () -> editSubjectName(area)));
            btns.add(smallBtn("Cor",      StyledButton.Variant.TEXT, () -> changeSubjectColor(area)));
            btns.add(smallBtn("Zerar",    StyledButton.Variant.TEXT, () -> zeroOutSubjectTime(area)));
            btns.add(smallBtn("Arquivar", StyledButton.Variant.TEXT, () -> archiveSubject(area)));
        }
        btns.add(smallBtn("Deletar", StyledButton.Variant.TEXT, () -> deleteSubject(area)));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.weightx = 1; row.add(left, c);
        c.gridx = 1; c.weightx = 0; row.add(btns, c);

        JPanel wrap = new JPanel(new BorderLayout());   // espaço entre as linhas
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(0, 0, 6, 0));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        wrap.add(row);
        return wrap;
    }

    private static StyledButton smallBtn(String text, StyledButton.Variant v, Runnable r) {
        StyledButton b = new StyledButton(text, v);
        b.setFont(AppTheme.FONT_SMALL);
        b.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        b.addActionListener(e -> r.run());
        return b;
    }

    // ── TELA PROGRESSO: donut, números e calendário ────────────────────────

    private JPanel buildProgressScreen() {
        StyledButton hist = new StyledButton("Histórico", StyledButton.Variant.TEXT);
        hist.addActionListener(e -> showHistory());
        StyledButton stats = new StyledButton("Estatísticas", StyledButton.Variant.TEXT);
        stats.addActionListener(e -> new StatsPanel(this, sessions, studyDataMap).setVisible(true));

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppTheme.BG);
        p.add(screenHeader("Progresso", null, hist, stats), BorderLayout.NORTH);
        p.add(buildCenter(), BorderLayout.CENTER);
        return p;
    }

    private JSplitPane buildCenter() {
        // ── Lado esquerdo: gráfico donut (topo) + progresso (base) ────────────
        // Antes o progresso ia no BorderLayout.SOUTH e, com várias matérias,
        // espremia o donut até ele sumir. Agora os dois ficam num split vertical
        // que garante espaço ao gráfico e deixa o usuário ajustar a divisão.
        JPanel chartWrapper = new JPanel(new BorderLayout());
        chartWrapper.setBackground(AppTheme.BG);
        chartWrapper.setBorder(new EmptyBorder(12, 12, 6, 6));
        chartPanel = new PieChartPanel();
        chartPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        RoundedPanel chartCard = new RoundedPanel(18, AppTheme.SURFACE);
        chartCard.setLayout(new BorderLayout());
        chartCard.add(chartPanel);
        chartCard.setMinimumSize(new Dimension(160, 200));

        JPanel leftBottom = new JPanel();
        leftBottom.setLayout(new BoxLayout(leftBottom, BoxLayout.Y_AXIS));
        leftBottom.setOpaque(false);
        // Hoje, Agora, Metas e Equilíbrio foram para a tela Hoje; aqui fica só o Progresso.
        JComponent pc = buildProgressCard(); pc.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftBottom.add(pc);
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
        leftSplit.setResizeWeight(0.46);   // gráfico em cima, metas+progresso embaixo (com scroll)
        leftSplit.setDividerLocation(0.46);
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

        // Monta mapa de cores das matérias
        Map<String, Color> colorMap = new LinkedHashMap<>();
        studyDataMap.forEach((k, v) -> colorMap.put(k, v.getColor()));
        calendarPanel = new CalendarPanel(sessions, goals, colorMap);

        JScrollPane calScroll = new JScrollPane(calendarPanel);
        calScroll.setBorder(null); calScroll.setOpaque(false);
        calScroll.getViewport().setOpaque(false);
        calScroll.getVerticalScrollBar().setUnitIncrement(12);

        calCard.add(calScroll, BorderLayout.CENTER);
        calWrapper.add(calCard);

        // Resumo agora fica abaixo do gráfico (panel menor)
        summaryPanel = new JPanel();
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
        summaryPanel.setOpaque(false);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartWrapper, calWrapper);
        split.setResizeWeight(0.45);
        split.setDividerLocation(0.45);
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
        bottomTabs = tabs;
        // Sem ⏱ ✏ 🍅: a fonte do Swing no Windows mostra esses glifos como quadrados.
        tabs.addTab("Cronômetro", buildStopwatchTab());
        tabs.addTab("Manual",     buildManualTab());
        tabs.addTab("Pomodoro",   buildPomodoroTab());
        tabs.setPreferredSize(new Dimension(0, 175));
        card.add(tabs, BorderLayout.CENTER);

        outer.add(card);
        return outer;
    }

    private JPanel buildSubjectRow() {
        // Duas linhas: [Matéria + Meta + barra de meta]  /  [Nota + Tipo]
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel lbl = new JLabel("Matéria:");
        lbl.setFont(AppTheme.FONT_BOLD);
        lbl.setForeground(AppTheme.TEXT_PRI);
        subjectComboBox = new JComboBox<>();
        styleInput(subjectComboBox);
        subjectComboBox.setPreferredSize(new Dimension(220, 32));
        subjectComboBox.addActionListener(e -> { updateGoalProgress(); refreshProgressCard(); refreshKindCombo(); });
        left.add(lbl); left.add(subjectComboBox);

        StyledButton btnGoal = new StyledButton("Meta", StyledButton.Variant.TEXT);
        btnGoal.setFont(AppTheme.FONT_SMALL);
        btnGoal.addActionListener(e -> showGoalDialog());
        left.add(btnGoal);

        // Linha 2: Nota + Tipo
        JPanel noteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        noteRow.setOpaque(false);
        noteRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        noteRow.setBorder(new EmptyBorder(6, 0, 0, 0));
        JLabel noteLbl = new JLabel("Nota:");
        noteLbl.setFont(AppTheme.FONT_BOLD); noteLbl.setForeground(AppTheme.TEXT_PRI);
        noteField = new JTextField(24);
        styleInput(noteField);
        noteField.setToolTipText("Opcional: o que você estudou nesta sessão (vai para o Histórico)");
        JLabel kindLbl = new JLabel("Tipo:");
        kindLbl.setFont(AppTheme.FONT_BOLD); kindLbl.setForeground(AppTheme.TEXT_PRI);
        kindCombo = new JComboBox<>();
        styleInput(kindCombo);
        kindCombo.setToolTipText("Sub-foco desta sessão (tópico / grupo muscular / hobby)");
        kindCombo.addActionListener(e -> {
            if (KIND_MANAGE.equals(kindCombo.getSelectedItem())) {
                String a = (String) subjectComboBox.getSelectedItem();
                if (a != null) showSubFocosDialog(a);
                kindCombo.setSelectedIndex(0);
            }
        });
        kindLbl.setText("Sub-foco:");
        JLabel enLbl = new JLabel("Energia:");
        enLbl.setFont(AppTheme.FONT_BOLD); enLbl.setForeground(AppTheme.TEXT_PRI);
        energyCombo = new JComboBox<>(ENERGY_LABELS);
        styleInput(energyCombo);
        energyCombo.setToolTipText("Como estava sua energia nesta sessão");
        noteRow.add(noteLbl); noteRow.add(noteField);
        noteRow.add(Box.createHorizontalStrut(6));
        noteRow.add(kindLbl); noteRow.add(kindCombo);
        noteRow.add(Box.createHorizontalStrut(6));
        noteRow.add(enLbl); noteRow.add(energyCombo);

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
        wrap.add(noteRow);
        return wrap;
    }

    // ── CARTÃO DE PROGRESSO ──────────────────────────────────────────────────

    /** Cartão "Metas" — visão rápida das metas de todas as matérias. */
    /** Cartão "Agora" — uma sugestão só + botão Começar. */
    private JComponent buildNowCard() {
        nowCard = new RoundedPanel(18, AppTheme.SURFACE);
        nowCard.setLayout(new BorderLayout(0, 8));
        nowCard.setBorder(new EmptyBorder(14, 16, 14, 16));
        JLabel title = new JLabel("Agora");
        title.setFont(AppTheme.FONT_SECTION);
        title.setForeground(AppTheme.TEXT_PRI);
        nowCard.add(title, BorderLayout.NORTH);
        nowBody = new JPanel(new BorderLayout(10, 0));
        nowBody.setOpaque(false);
        nowCard.add(nowBody, BorderLayout.CENTER);
        return nowCard;
    }

    private void refreshNowCard() {
        if (nowBody == null) return;
        nowBody.removeAll();
        LocalDate hoje = LocalDate.now();

        if (isHardDay(hoje)) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setOpaque(false);
            JLabel l = new JLabel("😮‍💨 Dia difícil — sem cobrança hoje.");
            l.setFont(AppTheme.FONT_LABEL); l.setForeground(AppTheme.TEXT_SEC);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            StyledButton undo = new StyledButton("desfazer", StyledButton.Variant.TEXT);
            undo.setFont(AppTheme.FONT_SMALL);
            undo.setAlignmentX(Component.LEFT_ALIGNMENT);
            undo.addActionListener(e -> { hardDays.remove(hoje.toString()); updateUI(); });
            p.add(l); p.add(undo);
            nowBody.add(p, BorderLayout.CENTER);
            nowBody.revalidate(); nowBody.repaint();
            return;
        }

        String[] sug = nextSuggestion();
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setOpaque(false);
        StyledButton hard = new StyledButton("hoje tá difícil?", StyledButton.Variant.TEXT);
        hard.setFont(AppTheme.FONT_SMALL);
        hard.addActionListener(e -> {
            hardDays.add(hoje.toString());
            updateUI();
            toast("Ok, hoje não cobra. Descansa. 🫶");
        });
        south.add(hard);
        nowBody.add(south, BorderLayout.SOUTH);

        if (sug == null) {
            JLabel l = new JLabel("Tudo em dia por aqui. 🎉");
            l.setFont(AppTheme.FONT_LABEL); l.setForeground(AppTheme.TEXT_SEC);
            nowBody.add(l, BorderLayout.CENTER);
        } else {
            JPanel txt = new JPanel();
            txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
            txt.setOpaque(false);
            StudyData sd = studyDataMap.get(sug[0]);   // bolinha da cor no lugar do emoji (vira quadrado no Windows)
            JLabel nm = new JLabel(sug[0], PlannerPanel.dot(sd != null ? sd.getColor() : AppTheme.ACCENT), SwingConstants.LEFT);
            nm.setIconTextGap(6);
            nm.setFont(AppTheme.FONT_BOLD); nm.setForeground(AppTheme.TEXT_PRI);
            nm.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel rs = new JLabel(sug[1]);
            rs.setFont(AppTheme.FONT_SMALL); rs.setForeground(AppTheme.TEXT_SEC);
            rs.setAlignmentX(Component.LEFT_ALIGNMENT);
            txt.add(nm); txt.add(Box.createVerticalStrut(2)); txt.add(rs);

            StyledButton go = new StyledButton("Começar", StyledButton.Variant.FILLED);
            go.setFont(AppTheme.FONT_SMALL);
            final String area = sug[0];
            final String sf = sug.length > 2 ? sug[2] : null;
            go.addActionListener(e -> startArea(area, sf));
            JPanel goWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));   // não estica na altura
            goWrap.setOpaque(false);
            goWrap.add(go);
            nowBody.add(txt,    BorderLayout.CENTER);
            nowBody.add(goWrap, BorderLayout.EAST);
        }
        nowBody.revalidate();
        nowBody.repaint();
    }

    /** Seleciona a área (e o sub-foco), vai para a aba Cronômetro e liga o cronômetro: 1 clique. */
    private void startArea(String area, String subFoco) {
        subjectComboBox.setSelectedItem(area);        // dispara refreshKindCombo
        if (subFoco != null && kindCombo != null) kindCombo.setSelectedItem(subFoco);
        showScreen(SCR_HOJE);                           // o cronômetro fica na tela Hoje
        if (bottomTabs != null) bottomTabs.select(0);   // aba Cronômetro
        startStopwatch();
    }

    /** {área, motivo} da coisa mais relevante pra fazer agora, ou null. */
    private String[] nextSuggestion() {
        LocalDate today = LocalDate.now();
        // 0. o que a pessoa já planejou para este turno: a decisão já foi tomada, é só seguir
        int turno = PlannerPanel.slotAt(LocalTime.now());
        for (String a : plannedAreas(today.getDayOfWeek().getValue(), turno))
            if (!isDoneToday(a)) return new String[]{a, "está no seu plano " + PlannerPanel.SLOT_PHRASE[turno]};

        // 1. meta diária de hoje não batida (prioriza prova mais próxima e o que falta)
        String best = null; long bestScore = Long.MIN_VALUE; String reason = null;
        for (String s : studyDataMap.keySet()) {
            if (archived.contains(s)) continue;
            int goal = goals.getOrDefault(s, 0);
            if (goal <= 0 || !isGoalDay(s, today)) continue;
            int done = minutesOnDay(s, today);
            if (done >= goal) continue;
            long score = goal - done;
            LocalDate exam = examDate.get(s);
            String extra = "";
            if (exam != null) {
                long d = java.time.temporal.ChronoUnit.DAYS.between(today, exam);
                if (d >= 0) { score += (120 - Math.min(110, d)) * 3L; extra = " · prova em " + d + "d"; }
            }
            if (score > bestScore) {
                bestScore = score; best = s;
                reason = "faltam " + fmtHM(goal - done) + " pra meta de hoje" + extra;
            }
        }
        if (best != null) return new String[]{best, reason};

        // 1b. rodízio: área de Físico com grupo (sub-foco) parado há mais tempo
        String rArea = null, rSf = null; long oldestSf = Long.MAX_VALUE;
        for (String s : studyDataMap.keySet()) {
            if (archived.contains(s) || !"fisico".equals(typeOf(s))) continue;
            for (String grupo : subFocos.getOrDefault(s, java.util.Collections.emptyList())) {
                long last = 0;
                for (StudySession se : sessions)
                    if (se.getSubject().equals(s) && grupo.equalsIgnoreCase(se.getKind()))
                        last = Math.max(last, se.getTimestamp());
                if (last < oldestSf) { oldestSf = last; rArea = s; rSf = grupo; }
            }
        }
        if (rArea != null) {
            long dias = oldestSf == 0 ? -1
                    : java.time.temporal.ChronoUnit.DAYS.between(dateOf(oldestSf), today);
            if (oldestSf == 0 || dias >= 3)
                return new String[]{rArea,
                        oldestSf == 0 ? "treinar " + rSf + " — ainda não treinou" : "treinar " + rSf + " — faz " + dias + " dias",
                        rSf};
        }

        // 2. tipo com meta de equilíbrio zerada na semana
        LocalDate ws = weekStart(today);
        for (String t : TYPE_KEYS) {
            if (typeGoalWeek.getOrDefault(t, 0) <= 0) continue;
            if (minutesByTypeInRange(t, ws, today) > 0) continue;
            for (String s : studyDataMap.keySet())
                if (!archived.contains(s) && typeOf(s).equals(t))
                    return new String[]{s, "sua semana está sem " + typeLabel(t).toLowerCase()};
        }

        // 3. área sem atividade há mais tempo
        String stale = null; long oldest = Long.MAX_VALUE;
        for (String s : studyDataMap.keySet()) {
            if (archived.contains(s)) continue;
            long last = 0;
            for (StudySession se : sessions) if (se.getSubject().equals(s)) last = Math.max(last, se.getTimestamp());
            if (last < oldest) { oldest = last; stale = s; }
        }
        if (stale != null) {
            long dias = oldest == 0 ? -1 : java.time.temporal.ChronoUnit.DAYS.between(dateOf(oldest), today);
            return new String[]{stale, dias < 0 ? "ainda sem nenhuma sessão registrada"
                    : dias == 0 ? "já mexeu nisso hoje" : "faz " + dias + " dia(s) sem isso"};
        }
        return null;
    }

    /** Cartão "Equilíbrio" — tempo da semana por tipo de área (Estudo/Físico/Lazer/Trabalho). */
    private JComponent buildBalanceCard() {
        balanceCard = new RoundedPanel(18, AppTheme.SURFACE);
        balanceCard.setLayout(new BorderLayout(0, 8));
        balanceCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel title = new JLabel("Equilíbrio · esta semana");
        title.setFont(AppTheme.FONT_SECTION);
        title.setForeground(AppTheme.TEXT_PRI);

        StyledButton metas = new StyledButton("Metas…", StyledButton.Variant.TEXT);
        metas.setFont(AppTheme.FONT_SMALL);
        metas.addActionListener(e -> showTypeGoalsDialog());

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(title, BorderLayout.WEST);
        head.add(metas, BorderLayout.EAST);
        balanceCard.add(head, BorderLayout.NORTH);

        balanceBody = new JPanel();
        balanceBody.setLayout(new BoxLayout(balanceBody, BoxLayout.Y_AXIS));
        balanceBody.setOpaque(false);
        balanceCard.add(balanceBody, BorderLayout.CENTER);
        return balanceCard;
    }

    private void refreshBalanceCard() {
        if (balanceBody == null) return;
        balanceBody.removeAll();
        LocalDate today = LocalDate.now();
        LocalDate ws = weekStart(today);

        int[] mins = new int[TYPE_KEYS.length];
        for (int i = 0; i < TYPE_KEYS.length; i++) mins[i] = minutesByTypeInRange(TYPE_KEYS[i], ws, today);
        int ref = 1;
        for (int i = 0; i < TYPE_KEYS.length; i++)
            ref = Math.max(ref, Math.max(mins[i], typeGoalWeek.getOrDefault(TYPE_KEYS[i], 0)));

        boolean any = false;
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            int goalW = typeGoalWeek.getOrDefault(TYPE_KEYS[i], 0);
            if (mins[i] == 0 && goalW == 0) continue;   // tipo sem uso e sem meta: não mostra
            any = true;
            balanceBody.add(balanceRow(TYPE_KEYS[i], mins[i], goalW, ref));
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

    private JPanel balanceRow(String type, int mins, int goalW, int ref) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel name = new JLabel(typeIcon(type) + " " + typeLabel(type));
        name.setFont(AppTheme.FONT_SMALL);
        name.setForeground(AppTheme.TEXT_SEC);
        name.setPreferredSize(new Dimension(96, 16));

        final Color col = typeColor(type);
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

        String txt = goalW > 0 ? fmtHM(mins) + " / " + fmtHM(goalW) : fmtHM(mins);
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
            addFormRow(panel, c, i + 1, TYPE_ICONS[i] + "  " + TYPE_LABELS[i] + ":", fs[i]);
        }

        int r = JOptionPane.showConfirmDialog(this, panel, "Metas de equilíbrio (semana)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        try {
            for (int i = 0; i < TYPE_KEYS.length; i++) {
                int v = parseDuration(fs[i].getText());
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
        if (semana.equals(balanceNudgeWeek)) return;

        LocalDate ws = weekStart(today);
        List<String> faltando = new ArrayList<>();
        for (String t : TYPE_KEYS) {
            if (typeGoalWeek.getOrDefault(t, 0) <= 0) continue;
            if (minutesByTypeInRange(t, ws, today) == 0) faltando.add(typeLabel(t).toLowerCase());
        }
        if (faltando.isEmpty()) return;
        balanceNudgeWeek = semana;   // persistido no saveData() do updateUI
        Timer tt = new Timer(600, e -> toast("Sua semana está sem " + String.join(" e ", faltando)
                + ". Que tal um pouco hoje? 🙂"));
        tt.setRepeats(false); tt.start();
    }

    private JComponent buildGoalsCard() {
        goalsCard = new RoundedPanel(18, AppTheme.SURFACE);
        goalsCard.setLayout(new BorderLayout(0, 8));
        goalsCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel title = new JLabel("Metas");
        title.setFont(AppTheme.FONT_SECTION);
        title.setForeground(AppTheme.TEXT_PRI);

        StyledButton manage = new StyledButton("Gerenciar…", StyledButton.Variant.TEXT);
        manage.setFont(AppTheme.FONT_SMALL);
        manage.addActionListener(e -> showManageGoalsDialog());

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(title,  BorderLayout.WEST);
        head.add(manage, BorderLayout.EAST);
        goalsCard.add(head, BorderLayout.NORTH);

        goalsBody = new JPanel();
        goalsBody.setLayout(new BoxLayout(goalsBody, BoxLayout.Y_AXIS));
        goalsBody.setOpaque(false);
        goalsCard.add(goalsBody, BorderLayout.CENTER);

        return goalsCard;
    }

    /** Metas agrupadas por tipo de área (Estudo / Físico / Lazer / …), com os sub-focos de cada área. */
    private void refreshGoalsCard() {
        if (goalsBody == null) return;
        goalsBody.removeAll();

        // Agrupa as áreas ativas por tipo, preservando a ordem do usuário dentro de cada grupo.
        Map<String, java.util.List<String>> porTipo = new LinkedHashMap<>();
        for (String t : TYPE_KEYS) porTipo.put(t, new ArrayList<>());
        for (String s : studyDataMap.keySet()) {
            if (archived.contains(s)) continue;
            porTipo.computeIfAbsent(typeOf(s), k -> new ArrayList<>()).add(s);
        }

        boolean primeiro = true;
        for (Map.Entry<String, java.util.List<String>> grupo : porTipo.entrySet()) {
            if (grupo.getValue().isEmpty()) continue;
            String tipo = grupo.getKey();

            if (!primeiro) goalsBody.add(Box.createVerticalStrut(8));
            primeiro = false;

            JPanel cab = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            cab.setOpaque(false);
            cab.setAlignmentX(Component.LEFT_ALIGNMENT);
            cab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
            cab.setBorder(new EmptyBorder(0, 0, 2, 0));
            cab.add(colorDot(typeColor(tipo)));
            JLabel cabL = new JLabel(typeLabel(tipo).toUpperCase());
            cabL.setFont(AppTheme.FONT_CAPTION);
            cabL.setForeground(AppTheme.TEXT_MUT);
            cab.add(cabL);
            goalsBody.add(cab);

            for (String s : grupo.getValue()) {
                int d = goals.getOrDefault(s, 0);
                int w = weeklyGoalFor(s);
                int m = monthlyGoalFor(s);

                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(false);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

                JPanel dotName = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                dotName.setOpaque(false);
                dotName.add(colorDot(studyDataMap.get(s).getColor()));
                JLabel nm = new JLabel(s);
                nm.setFont(AppTheme.FONT_LABEL);
                nm.setForeground(AppTheme.TEXT_PRI);
                dotName.add(nm);

                JLabel val = new JLabel(d <= 0 && w <= 0 && m <= 0
                        ? "sem meta"
                        : "Dia " + fmtHM(Math.max(0, d)) + "  ·  Sem " + fmtHM(w) + "  ·  Mês " + fmtHM(m));
                val.setFont(AppTheme.FONT_SMALL);
                val.setForeground(d <= 0 && w <= 0 && m <= 0 ? AppTheme.TEXT_MUT : AppTheme.TEXT_SEC);
                val.setHorizontalAlignment(SwingConstants.RIGHT);

                row.add(dotName, BorderLayout.WEST);
                row.add(val,     BorderLayout.EAST);
                goalsBody.add(row);

                java.util.List<String> sf = subFocos.get(s);
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

    private JPanel  todayBody;
    private JLabel  todayTitle;
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
    private java.util.List<String> plannedAreas(int dow, int turno) {
        java.util.List<String> out = new ArrayList<>();
        for (String a : weekPlan.getOrDefault(dow + "_" + PlannerPanel.SLOT[turno], java.util.Collections.emptyList()))
            if (studyDataMap.containsKey(a) && !archived.contains(a)) out.add(a);
        return out;
    }

    /** Meta diária que conta como "feito": o mínimo, se houver; senão o ideal. 0 = sem meta. */
    private int dailyDoneGoal(String area) {
        int min = goalsMin.getOrDefault(area, 0);
        return min > 0 ? min : goals.getOrDefault(area, 0);
    }

    /** Feito hoje = bateu o mínimo do dia; sem meta, basta ter mexido nisso hoje. */
    private boolean isDoneToday(String area) {
        int feito = minutesOnDay(area, LocalDate.now()), meta = dailyDoneGoal(area);
        return meta > 0 ? feito >= meta : feito > 0;
    }

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
        int n = autofillWeekPlan();
        onPlanChanged();
        toast(n == 0 ? "Tudo que tem meta diária já está na semana."
                     : n == 1 ? "1 bloco colocado pelas metas." : n + " blocos colocados pelas metas.");
    }

    /**
     * Coloca cada área com meta diária em todos os dias da meta (sem dias marcados = todos),
     * uma vez por dia, sem apagar nada. Turno: o que a área já ocupa em outros dias (rotina
     * previsível); se ainda não tem, o turno mais vazio daquele dia. Devolve quantos colocou.
     */
    private int autofillWeekPlan() {
        // Ordem fixa (tipo, depois nome): o mapa de áreas vem do .properties em ordem aleatória.
        java.util.List<String> ordem = new ArrayList<>();
        for (String t : TYPE_KEYS) {
            java.util.List<String> doTipo = new ArrayList<>();
            for (String a : studyDataMap.keySet())
                if (!archived.contains(a) && typeOf(a).equals(t)) doTipo.add(a);
            java.util.Collections.sort(doTipo, String.CASE_INSENSITIVE_ORDER);
            ordem.addAll(doTipo);
        }
        int colocados = 0;
        for (String a : ordem) {
            if (dailyDoneGoal(a) <= 0) continue;
            java.util.Set<java.time.DayOfWeek> dias = goalDays.get(a);
            for (int d = 1; d <= 7; d++) {
                if (dias != null && !dias.isEmpty() && !dias.contains(java.time.DayOfWeek.of(d))) continue;
                if (dayHasArea(d, a)) continue;
                weekPlan.computeIfAbsent(d + "_" + PlannerPanel.SLOT[pickSlot(a, d)], k -> new ArrayList<>()).add(a);
                colocados++;
            }
        }
        return colocados;
    }

    private boolean dayHasArea(int dow, String area) {
        for (String sl : PlannerPanel.SLOT)
            if (weekPlan.getOrDefault(dow + "_" + sl, java.util.Collections.emptyList()).contains(area)) return true;
        return false;
    }

    private int pickSlot(String area, int dow) {
        int[] habito = new int[3];
        for (int d = 1; d <= 7; d++)
            for (int s = 0; s < 3; s++)
                if (weekPlan.getOrDefault(d + "_" + PlannerPanel.SLOT[s], java.util.Collections.emptyList()).contains(area))
                    habito[s]++;
        int best = -1;
        for (int s = 0; s < 3; s++) if (habito[s] > 0 && (best < 0 || habito[s] > habito[best])) best = s;
        if (best >= 0) return best;
        int vazio = 0;
        for (int s = 1; s < 3; s++)
            if (weekPlan.getOrDefault(dow + "_" + PlannerPanel.SLOT[s], java.util.Collections.emptyList()).size()
              < weekPlan.getOrDefault(dow + "_" + PlannerPanel.SLOT[vazio], java.util.Collections.emptyList()).size())
                vazio = s;
        return vazio;
    }

    private JComponent buildTodayCard() {
        RoundedPanel card = new RoundedPanel(18, AppTheme.SURFACE);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        todayTitle = new JLabel("Hoje");
        todayTitle.setFont(AppTheme.FONT_SECTION);
        todayTitle.setForeground(AppTheme.TEXT_PRI);
        StyledButton edit = new StyledButton("editar semana", StyledButton.Variant.TEXT);
        edit.setFont(AppTheme.FONT_SMALL);
        edit.addActionListener(e -> openPlanner());
        head.add(todayTitle, BorderLayout.WEST);
        head.add(edit, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        todayBody = new JPanel();
        todayBody.setLayout(new BoxLayout(todayBody, BoxLayout.Y_AXIS));
        todayBody.setOpaque(false);
        card.add(todayBody, BorderLayout.CENTER);
        return card;
    }

    private void refreshTodayCard() {
        if (todayBody == null) return;
        todayBody.removeAll();
        LocalDate hoje = LocalDate.now();
        int dow = hoje.getDayOfWeek().getValue();
        int agora = PlannerPanel.slotAt(LocalTime.now());
        todayShownKey = hoje + "_" + agora;
        todayTitle.setText("Hoje · " + PlannerPanel.DOW_LONG[dow - 1]);

        boolean vazio = true;
        for (int s = 0; s < 3; s++) if (!plannedAreas(dow, s).isEmpty()) vazio = false;
        if (vazio) {
            JPanel p = new JPanel(new BorderLayout(10, 0));
            p.setOpaque(false);
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel l = new JLabel("Nada planejado para hoje.");
            l.setFont(AppTheme.FONT_LABEL); l.setForeground(AppTheme.TEXT_SEC);
            StyledButton fill = new StyledButton("Preencher pelas metas", StyledButton.Variant.TONAL);
            fill.setFont(AppTheme.FONT_SMALL);
            fill.addActionListener(e -> fillPlanFromGoals());
            p.add(l, BorderLayout.CENTER);
            p.add(fill, BorderLayout.EAST);
            todayBody.add(p);
        } else {
            for (int s = 0; s < 3; s++) {
                if (s > 0) todayBody.add(Box.createVerticalStrut(4));
                todayBody.add(todaySlotRow(dow, s, agora));
            }
        }
        todayBody.revalidate();
        todayBody.repaint();
    }

    private JComponent todaySlotRow(int dow, int turno, int agora) {
        boolean atual = turno == agora, passou = turno < agora;
        java.util.List<String> areas = plannedAreas(dow, turno);

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
        String proxima = null;
        if (areas.isEmpty()) {
            JLabel l = new JLabel("livre");
            l.setFont(AppTheme.FONT_SMALL); l.setForeground(AppTheme.TEXT_MUT);
            chips.add(l);
        }
        for (String a : areas) {
            boolean feito = isDoneToday(a);
            if (!feito && proxima == null) proxima = a;
            chips.add(todayChip(a, feito, passou));
        }
        row.add(chips, BorderLayout.CENTER);

        if (atual && proxima != null) {
            StyledButton go = new StyledButton("Começar", StyledButton.Variant.FILLED);
            go.setFont(AppTheme.FONT_SMALL);
            go.setToolTipText("Começar " + proxima + " agora");
            final String area = proxima;
            go.addActionListener(e -> startArea(area, null));
            row.add(go, BorderLayout.EAST);
        }
        return row;
    }

    /** Nome da área com a bolinha da cor ("· feito" se já bateu o mínimo). Clicar começa o cronômetro nela. */
    private JComponent todayChip(String area, boolean feito, boolean passou) {
        StudyData sd = studyDataMap.get(area);
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
                @Override public void mouseClicked(MouseEvent e) { startArea(area, null); }
            });
        }
        return l;
    }

    /** Troca de turno/dia com o app aberto: redesenha "Hoje" e "Agora" (checa a cada minuto). */
    private void startTodayTimer() {
        if (todayTimer != null) return;
        todayTimer = new Timer(60_000, e -> {
            String k = LocalDate.now() + "_" + PlannerPanel.slotAt(LocalTime.now());
            if (!k.equals(todayShownKey)) { refreshTodayCard(); refreshNowCard(); }
        });
        todayTimer.start();
    }

    private JComponent buildProgressCard() {
        progressCard = new RoundedPanel(18, AppTheme.SURFACE);
        progressCard.setLayout(new BorderLayout(0, 8));
        progressCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel title = new JLabel("Progresso");
        title.setFont(AppTheme.FONT_SECTION);
        title.setForeground(AppTheme.TEXT_PRI);
        progressCard.add(title, BorderLayout.NORTH);

        progressBody = new JPanel();
        progressBody.setLayout(new BoxLayout(progressBody, BoxLayout.Y_AXIS));
        progressBody.setOpaque(false);
        progressCard.add(progressBody, BorderLayout.CENTER);

        return progressCard;
    }

    /** Repopula o cartão: bloco GERAL + bloco da matéria selecionada. */
    private void refreshProgressCard() {
        if (progressBody == null) return;
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
        String subject = (String) subjectComboBox.getSelectedItem();
        if (subject != null) {
            Color dot = studyDataMap.getOrDefault(subject, new StudyData(0, AppTheme.TEXT_SEC)).getColor();
            progressBody.add(sectionHeader(subject, dot));
            progressBody.add(streakInfoLine(subject));
            if (examDate.get(subject) != null) {
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
        Color base = subject == null
                ? AppTheme.ACCENT
                : studyDataMap.getOrDefault(subject, new StudyData(0, AppTheme.ACCENT)).getColor();
        progressBody.add(periodRow("Dia",    minutesOnDay(subject, today),                         dailyGoalFor(subject),   base));
        progressBody.add(Box.createVerticalStrut(4));
        progressBody.add(periodRow("Semana", minutesInRange(subject, weekStart(today),  today),    weeklyGoalFor(subject),  base));
        progressBody.add(Box.createVerticalStrut(4));
        progressBody.add(periodRow("Mês",    minutesInRange(subject, monthStart(today), today),    monthlyGoalFor(subject), base));
        progressBody.add(Box.createVerticalStrut(4));
        progressBody.add(insightLine(subject, today));
    }

    /** Saldo da semana (min): estudado − meta_diária × dias agendados já decorridos. */
    private int weeklyBalance(String subject, LocalDate today) {
        int daily = dailyGoalFor(subject);
        if (daily <= 0) return 0;
        int diasAgendados = 0;
        for (LocalDate d = weekStart(today); !d.isAfter(today); d = d.plusDays(1))
            if (isGoalDay(subject, d)) diasAgendados++;
        return minutesInRange(subject, weekStart(today), today) - daily * diasAgendados;
    }

    /** Linha de insights: saldo da semana + comparação com o mês passado. */
    private JPanel insightLine(String subject, LocalDate today) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        StringBuilder sb = new StringBuilder();
        if (dailyGoalFor(subject) > 0) {
            int bal = weeklyBalance(subject, today);
            sb.append(bal >= 0 ? "semana: +" + fmtHM(bal) + " sobre a meta"
                               : "semana: faltam " + fmtHM(-bal) + " p/ a meta");
        }
        int thisM = minutesInRange(subject, monthStart(today), today);
        LocalDate lmEnd = today.minusMonths(1);
        int lastM = minutesInRange(subject, lmEnd.withDayOfMonth(1), lmEnd);
        if (thisM > 0 || lastM > 0) {
            if (sb.length() > 0) sb.append("   ·   ");
            int d = thisM - lastM;
            sb.append("mês: ").append(fmtHM(thisM)).append(
                    d == 0 ? " (= mês passado)"
                  : d  > 0 ? " (▲ " + fmtHM(d)  + " vs. mês passado)"
                           : " (▼ " + fmtHM(-d) + " vs. mês passado)");
        }

        JLabel l = new JLabel(sb.length() == 0 ? " " : sb.toString());
        l.setFont(AppTheme.FONT_SMALL);
        l.setForeground(AppTheme.TEXT_SEC);
        p.add(l);
        return p;
    }

    /** Linha de contagem regressiva de prova (só quando há data marcada). */
    private JPanel examLine(String subject, LocalDate today) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        LocalDate exam = examDate.get(subject);
        JLabel l = new JLabel();
        l.setFont(AppTheme.FONT_SMALL);
        if (exam == null) { l.setText(" "); p.add(l); return p; }

        long dias = java.time.temporal.ChronoUnit.DAYS.between(today, exam);
        String quando = exam.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
        if (dias < 0) {
            l.setText("📅 prova de " + subject + " foi em " + quando);
            l.setForeground(AppTheme.TEXT_SEC);
        } else {
            int daily = dailyGoalFor(subject);
            int agendadosRestantes = 0;
            for (LocalDate d = today.plusDays(1); !d.isAfter(exam); d = d.plusDays(1))
                if (isGoalDay(subject, d)) agendadosRestantes++;
            String base = "📅 prova em " + (dias == 0 ? "HOJE" : dias + (dias == 1 ? " dia" : " dias"))
                        + " (" + quando + ")";
            if (daily > 0 && dias > 0)
                base += "  ·  no ritmo da meta: ~" + fmtHM(daily * agendadosRestantes) + " até lá";
            int total = 0, feitos = 0;
            for (ChecklistItem it : checklist)
                if (it.subject.equals(subject)) { total++; if (it.done) feitos++; }
            if (total > 0)
                base += "  ·  plano " + feitos + "/" + total + (feitos == total ? " ✓" : "");
            l.setText(base);
            l.setForeground(dias <= 3 ? AppTheme.WARNING : AppTheme.ACCENT);
        }
        p.add(l);
        return p;
    }

    /** Cabeçalho de bloco: ponto colorido opcional + nome em negrito. */
    private JPanel sectionHeader(String name, Color dotColor) {
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

    /** Linha de sequência: 🔥 N dias · recorde M (ou dica quando não há streak). */
    private JPanel streakInfoLine(String subject) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        int goal   = dailyGoalFor(subject);
        int streak = currentStreak(subject);
        int best   = bestStreak(subject);

        JLabel l = new JLabel();
        l.setFont(AppTheme.FONT_SMALL);
        if (goal <= 0) {
            l.setText("defina uma meta diária para ativar a sequência");
            l.setForeground(AppTheme.TEXT_SEC);
        } else if (streak <= 0) {
            l.setText("comece hoje para iniciar a sequência");
            l.setForeground(AppTheme.TEXT_SEC);
        } else {
            boolean metToday = minutesOnDay(subject, LocalDate.now()) >= goal;
            String txt = "🔥 " + streak + (streak == 1 ? " dia" : " dias") + streakBadge(streak)
                       + "   ·   recorde " + best;
            if (!metToday) txt += "   (estude hoje para manter)";
            l.setText(txt);
            l.setForeground(metToday ? AppTheme.WARNING : AppTheme.TEXT_SEC);
        }
        p.add(l);
        return p;
    }

    /** Uma barra de período: rótulo · barra · "atual / meta  pct%". A barra usa
     *  a cor da meta em níveis (tom escuro → claro) para mostrar o quanto falta. */
    private JPanel periodRow(String label, int current, int goal, Color base) {
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
            val.setText(fmtHM(current) + " / " + fmtHM(goal) + "   " + (met ? "✓ " : "") + pct + "%");
            val.setForeground(met ? AppTheme.SUCCESS : AppTheme.TEXT_PRI);
        }

        row.add(lbl, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        row.add(val, BorderLayout.EAST);
        return row;
    }

    private JComponent colorDot(Color c) {
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

    /** Formata minutos: "3h10" quando há horas, senão "52m". */
    private static String fmtHM(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return h > 0 ? String.format("%dh%02d", h, m) : m + "m";
    }

    /** Quantos dias por semana a meta diária vale (7 se não houver restrição). */
    // ── TIPO DE ÁREA ────────────────────────────────────────────────────────

    private String typeOf(String area) { return areaType.getOrDefault(area, "estudo"); }

    private static int typeIndex(String key) {
        for (int i = 0; i < TYPE_KEYS.length; i++) if (TYPE_KEYS[i].equals(key)) return i;
        return 0;
    }
    private static String typeLabel(String key) { return TYPE_LABELS[typeIndex(key)]; }
    private static String typeIcon(String key)  { return TYPE_ICONS[typeIndex(key)]; }
    private static Color typeColor(String key) {
        switch (key) {
            case "fisico":   return new Color(0x66BB6A);
            case "lazer":    return new Color(0xAB47BC);
            case "trabalho": return new Color(0xFFA726);
            case "outro":    return new Color(0x8FA0B8);
            default:         return AppTheme.ACCENT;   // estudo
        }
    }

    /** Monta o donut: um grupo por área (tipo) com suas matérias como sub-áreas. */
    private java.util.List<PieChartPanel.Group> buildPieGroups() {
        java.util.List<PieChartPanel.Group> out = new ArrayList<>();
        for (String type : TYPE_KEYS) {
            java.util.List<PieChartPanel.Slice> slices = new ArrayList<>();
            int sum = 0;
            for (Map.Entry<String, StudyData> e : studyDataMap.entrySet()) {
                if (!typeOf(e.getKey()).equals(type)) continue;
                int min = e.getValue().getMinutes();
                if (min <= 0) continue;
                slices.add(new PieChartPanel.Slice(e.getKey(), e.getValue().getColor(), min));
                sum += min;
            }
            if (sum > 0) out.add(new PieChartPanel.Group(typeLabel(type), typeColor(type), sum, slices));
        }
        return out;
    }

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

    private int goalDaysPerWeek(String subject) {
        java.util.Set<java.time.DayOfWeek> d = subject == null ? null : goalDays.get(subject);
        return d == null || d.isEmpty() ? 7 : d.size();
    }

    /** Dias agendados (que exigem meta) dentro do mês de 'ref'. */
    private int scheduledDaysInMonth(String subject, LocalDate ref) {
        int n = 0;
        LocalDate d = ref.withDayOfMonth(1);
        for (int i = 0; i < d.lengthOfMonth(); i++, d = d.plusDays(1))
            if (isGoalDay(subject, d)) n++;
        return n;
    }

    /**
     * Meta semanal. Se o usuário definiu um valor, ele manda. Senão, deriva de
     * meta_diária × dias/semana em que a meta vale (assim mudar os dias muda a meta).
     */
    private int weeklyGoalFor(String subject) {
        if (subject == null) {
            int s = 0;
            for (String k : studyDataMap.keySet()) if (!archived.contains(k)) s += weeklyGoalFor(k);
            return s;
        }
        int stored = goalsWeek.getOrDefault(subject, 0);
        if (stored > 0) return stored;
        int daily = goals.getOrDefault(subject, 0);
        return daily > 0 ? daily * goalDaysPerWeek(subject) : 0;
    }

    /** Meta mensal. Definida pelo usuário manda; senão, meta_diária × dias agendados no mês. */
    private int monthlyGoalFor(String subject) {
        if (subject == null) {
            int s = 0;
            for (String k : studyDataMap.keySet()) if (!archived.contains(k)) s += monthlyGoalFor(k);
            return s;
        }
        int stored = goalsMonth.getOrDefault(subject, 0);
        if (stored > 0) return stored;
        int daily = goals.getOrDefault(subject, 0);
        return daily > 0 ? daily * scheduledDaysInMonth(subject, LocalDate.now()) : 0;
    }

    // ── TABS ────────────────────────────────────────────────────────────────

    private JPanel buildStopwatchTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(AppTheme.SURFACE);

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(10, 0, 0, 0));

        stopwatchLabel = new JLabel("00:00:00", SwingConstants.CENTER);
        stopwatchLabel.setFont(AppTheme.FONT_MONO_S);
        stopwatchLabel.setForeground(AppTheme.TEXT_PRI);
        stopwatchLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        stopwatchInfoLabel = new JLabel(" ", SwingConstants.CENTER);
        stopwatchInfoLabel.setFont(AppTheme.FONT_SMALL);
        stopwatchInfoLabel.setForeground(AppTheme.TEXT_SEC);
        stopwatchInfoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

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

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        btns.setOpaque(false);
        btns.add(startButton); btns.add(stopButton);
        btns.add(resetStopwatchButton); btns.add(submitButton);

        p.add(head, BorderLayout.CENTER);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildManualTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 18));
        p.setBackground(AppTheme.SURFACE);
        hoursField   = styledField("Horas");
        minutesField = styledField("Minutos");
        StyledButton addBtn = new StyledButton("Adicionar Tempo", StyledButton.Variant.FILLED);
        addBtn.addActionListener(e -> addManualTime());
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

        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        top.setBackground(AppTheme.SURFACE);
        focusSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 120, 1));
        breakSpinner = new JSpinner(new SpinnerNumberModel(5,  1,  60, 1));
        styleSpinner(focusSpinner); styleSpinner(breakSpinner);

        top.add(lbl("Foco (min):")); top.add(focusSpinner);
        top.add(Box.createHorizontalStrut(16));
        top.add(lbl("Pausa (min):")); top.add(breakSpinner);

        autoCycleCheck = new JCheckBox("Ciclos automáticos", setPomoAutoCycle);
        autoCycleCheck.setOpaque(false);
        autoCycleCheck.setFont(AppTheme.FONT_SMALL);
        autoCycleCheck.setForeground(AppTheme.TEXT_SEC);
        autoCycleCheck.setToolTipText("Encadeia foco → pausa → foco sozinho; pausa longa a cada 4 focos");
        autoCycleCheck.addActionListener(e -> {
            setPomoAutoCycle = autoCycleCheck.isSelected();
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
        countdownLabel.setFont(AppTheme.FONT_TIMER_S);
        countdownLabel.setForeground(AppTheme.TEXT_PRI);
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        pomoCountLabel = new JLabel("", SwingConstants.CENTER);
        pomoCountLabel.setFont(AppTheme.FONT_SMALL);
        pomoCountLabel.setForeground(AppTheme.TEXT_SEC);
        pomoCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

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
        mid.add(pomoCountLabel);
        mid.add(Box.createVerticalStrut(4));
        mid.add(buttons);

        p.add(top,    BorderLayout.NORTH);
        p.add(mid,    BorderLayout.CENTER);
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
        pomoCountLabel.setText(n == 0 ? "" : "🍅  " + n + (n == 1 ? " foco hoje" : " focos hoje"));
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
        if (stopwatchGrossStartMs == 0) stopwatchGrossStartMs = System.currentTimeMillis();
        lastActivityMs = System.currentTimeMillis();
        stopwatch.start();
        startButton.setText("Retomar");
        startButton.setEnabled(false); stopButton.setEnabled(true);
        submitButton.setEnabled(false); resetStopwatchButton.setEnabled(true);
        subjectComboBox.setEnabled(false);
        updateStopwatchInfo();
    }

    private void stopStopwatch() {
        if (stopwatch.isRunning()) stopwatchPauseCount++;
        stopwatch.stop();
        startButton.setEnabled(true); stopButton.setEnabled(false);
        submitButton.setEnabled(elapsedSeconds > 0);
        resetStopwatchButton.setEnabled(elapsedSeconds > 0);
        subjectComboBox.setEnabled(true);
        updateStopwatchInfo();
    }

    /** Zera SOMENTE o cronômetro (o tempo acumulado da matéria não é tocado). */
    private void resetStopwatch() {
        stopwatch.stop();
        elapsedSeconds = 0;
        stopwatchGrossStartMs = 0;
        hyperfocusWarned = false;
        stopwatchPauseCount = 0;
        updateStopwatchLabel();
        updateStopwatchInfo();
        startButton.setText("Iniciar");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        submitButton.setEnabled(false);
        resetStopwatchButton.setEnabled(false);
        subjectComboBox.setEnabled(true);
    }

    private void submitStopwatchTime() {
        if (elapsedSeconds == 0) return;
        String subject = (String) subjectComboBox.getSelectedItem();
        int mins = (int) Math.round(elapsedSeconds / 60.0);
        if (mins < Math.max(1, setMinFocusMin)) {
            toast("Abaixo do foco mínimo (" + setMinFocusMin + " min) — não salvo.");
            return;   // mantém o tempo para o usuário continuar
        }
        addTime(subject, mins, "cronometro");
        toast(formatTime(mins * 60) + " salvo em " + subject);
        resetStopwatch();
        transitionNudge();
    }

    /** Nudge gentil de pausa entre blocos. */
    private void transitionNudge() {
        if (setTransitionMin <= 0) return;
        Timer t = new Timer(2600, e -> toast("Bloco salvo. Que tal " + setTransitionMin
                + " min de pausa? Levanta, água. 💧"));
        t.setRepeats(false); t.start();
    }

    private void updateStopwatchLabel() {
        int h = elapsedSeconds / 3600, m = (elapsedSeconds % 3600) / 60, s = elapsedSeconds % 60;
        stopwatchLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
        updateStopwatchInfo();
    }

    /** Linha auxiliar: pausas e tempo bruto (relógio de parede) desde o 1º "Iniciar". */
    private void updateStopwatchInfo() {
        if (stopwatchInfoLabel == null) return;
        if (stopwatchGrossStartMs == 0) { stopwatchInfoLabel.setText(" "); return; }
        long grossSec = (System.currentTimeMillis() - stopwatchGrossStartMs) / 1000;
        String bruto = String.format("%02d:%02d:%02d",
                grossSec / 3600, (grossSec % 3600) / 60, grossSec % 60);
        String pausas = stopwatchPauseCount == 1 ? "1 pausa" : stopwatchPauseCount + " pausas";
        stopwatchInfoLabel.setText("líquido  ·  " + pausas + "  ·  bruto " + bruto);

        if (setHyperfocusH > 0 && !hyperfocusWarned && grossSec >= setHyperfocusH * 3600L) {
            hyperfocusWarned = true;
            toast("Você está há " + setHyperfocusH + "h seguidas. Dá um respiro? 🌱");
        }
    }

    // ── POMODORO ────────────────────────────────────────────────────────────

    private void startCountdown(boolean isFocus) { startCountdown(isFocus, 0); }

    private void startCountdown(boolean isFocus, int minutesOverride) {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (isFocus && subject == null) { toast("Selecione uma matéria!"); return; }
        if (countdownTimer != null) countdownTimer.stop(); // garante parado antes de reiniciar
        isPaused = false;
        wasFocusSession = isFocus;
        plannedMinutes  = minutesOverride > 0 ? minutesOverride
                : isFocus ? (int) focusSpinner.getValue()
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
            boolean didFocus = wasFocusSession && subject != null;
            if (didFocus) {
                addTime(subject, plannedMinutes, "pomodoro");
                pomoCycleFocos++;
                updatePomodoroCount();
            }

            if (setPomoAutoCycle) {
                if (didFocus) {
                    boolean longBreak = pomoCycleFocos % 4 == 0;
                    int mins = longBreak ? Math.max(15, (int) breakSpinner.getValue() * 3) : 0;
                    toast((longBreak ? "4 focos! Pausa longa de " + mins + " min." : "Foco concluído — pausa."));
                    startCountdown(false, mins);
                    return;                       // ciclo continua
                } else {
                    toast("Pausa concluída — de volta ao foco.");
                    if (startCountdownIfPossible(true)) return;
                }
            } else {
                if (didFocus) showInfoDialog("✅ Foco concluído!",
                        plannedMinutes + " min adicionados a " + subject + ".");
                else          showInfoDialog("☕ Pausa concluída!", "Pronto para mais uma sessão?");
            }
        }

        // Estado ocioso (não encadeou)
        if (!finished) pomoCycleFocos = 0;
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

    /** Tenta iniciar o foco no ciclo automático; devolve false se não deu (ex.: sem matéria). */
    private boolean startCountdownIfPossible(boolean isFocus) {
        if (isFocus && subjectComboBox.getSelectedItem() == null) return false;
        startCountdown(isFocus, 0);
        return countdownTimer.isRunning();
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
        LocalDate today = LocalDate.now();
        int beforeToday = minutesOnDay(subject, today);

        String note = noteField == null ? "" : noteField.getText().trim();
        String kind = "";
        if (kindCombo != null) {
            Object sel = kindCombo.getSelectedItem();
            if (sel != null && !"–".equals(sel) && !KIND_MANAGE.equals(sel)) kind = sel.toString();
        }
        int energy = energyCombo == null ? 0 : Math.max(0, energyCombo.getSelectedIndex());
        studyDataMap.get(subject).addMinutes(minutes);
        sessions.add(new StudySession(subject, minutes, System.currentTimeMillis(), type, note, kind, energy));
        if (noteField != null) noteField.setText("");
        if (kindCombo != null && kindCombo.getItemCount() > 0) kindCombo.setSelectedIndex(0);
        if (energyCombo != null) energyCombo.setSelectedIndex(0);
        updateUI();
        saveData(); saveSessions();

        goalNudge(subject, beforeToday, beforeToday + minutes);
    }

    /** Toast de incentivo: meta do dia batida agora, ou reta final. */
    private void goalNudge(String subject, int before, int after) {
        int goal = goals.getOrDefault(subject, 0);
        if (goal <= 0) return;
        final String msg;
        int alvo = dailyMinFor(subject);   // celebra ao bater o MÍNIMO
        if (before < alvo && after >= alvo) {
            msg = "🎯 Você bateu a meta de " + subject + " hoje!  (" + fmtHM(after) + ")";
        } else if (!isHardDay(LocalDate.now()) && after < alvo && alvo - after <= 15) {
            msg = "Faltam " + (alvo - after) + " min para a meta de " + subject + " hoje 💪";
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

    /** Minutos estudados num dia. subject == null ⇒ geral (todas as matérias). */
    private int minutesOnDay(String subject, LocalDate day) {
        int total = 0;
        for (StudySession s : sessions) {
            if (subject != null && !s.getSubject().equals(subject)) continue;
            if (dateOf(s.getTimestamp()).equals(day)) total += s.getMinutes();
        }
        return total;
    }

    /** Minutos num intervalo [start, end] inclusivo. subject == null ⇒ geral. */
    private int minutesInRange(String subject, LocalDate start, LocalDate end) {
        int total = 0;
        for (StudySession s : sessions) {
            if (subject != null && !s.getSubject().equals(subject)) continue;
            LocalDate d = dateOf(s.getTimestamp());
            if (!d.isBefore(start) && !d.isAfter(end)) total += s.getMinutes();
        }
        return total;
    }

    /** Segunda-feira da semana de 'date' (semana começa na segunda). */
    private static LocalDate weekStart(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1);
    }

    /** Primeiro dia do mês de 'date'. */
    private static LocalDate monthStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    // ── STREAKS (dias consecutivos batendo a meta diária) ────────────────────

    /** A meta diária vale nesse dia? (GERAL e matérias sem configuração ⇒ todo dia). */
    private boolean isGoalDay(String subject, LocalDate date) {
        if (subject == null) return true;
        java.util.Set<java.time.DayOfWeek> days = goalDays.get(subject);
        return days == null || days.isEmpty() || days.contains(date.getDayOfWeek());
    }

    /** Meta diária. subject == null ⇒ geral (soma das metas diárias de todas). */
    private int dailyGoalFor(String subject) {
        if (subject == null) {
            int sum = 0;
            for (int g : goals.values()) sum += g;
            return sum;
        }
        return goals.getOrDefault(subject, 0);
    }

    /** Meta diária MÍNIMA (bater isso já mantém a sequência). Ausente ⇒ igual ao ideal. */
    private int dailyMinFor(String subject) {
        if (subject == null) {
            int sum = 0;
            for (String s : studyDataMap.keySet())
                if (!archived.contains(s) && goals.getOrDefault(s, 0) > 0) sum += dailyMinFor(s);
            return sum;
        }
        int ideal = goals.getOrDefault(subject, 0);
        if (ideal <= 0) return 0;
        return Math.max(1, Math.min(ideal, goalsMin.getOrDefault(subject, ideal)));
    }

    private boolean isHardDay(LocalDate d) { return hardDays.contains(d.toString()); }

    /**
     * Sequência atual: dias consecutivos (terminando hoje) com minutos ≥ meta diária.
     * Graça: se hoje ainda não bateu, a sequência não quebra — conta-se a partir de ontem.
     * Folga: 1 dia abaixo da meta é tolerado a cada janela de 7 dias da sequência.
     */
    private int currentStreak(String subject) {
        int goal = dailyMinFor(subject);   // basta bater o MÍNIMO
        if (goal <= 0) return 0;
        LocalDate cursor = LocalDate.now();
        while (!isGoalDay(subject, cursor) || isHardDay(cursor)) cursor = cursor.minusDays(1);
        if (minutesOnDay(subject, cursor) < goal) {                            // graça: pula p/ o anterior agendado
            do { cursor = cursor.minusDays(1); } while (!isGoalDay(subject, cursor) || isHardDay(cursor));
        }
        if (minutesOnDay(subject, cursor) < goal) return 0;                     // nem esse bateu

        int streak = 0, missesNaJanela = 0;
        java.util.ArrayDeque<Boolean> janela = new java.util.ArrayDeque<>();    // últimos 7 dias contados
        while (streak < 3650) {
            if (!isGoalDay(subject, cursor) || isHardDay(cursor)) { cursor = cursor.minusDays(1); continue; }
            boolean bateu = minutesOnDay(subject, cursor) >= goal;
            if (!bateu && missesNaJanela >= 1) break;                           // 2ª falha em 7 dias
            streak++;
            janela.addFirst(!bateu);
            if (!bateu) missesNaJanela++;
            if (janela.size() > 7 && Boolean.TRUE.equals(janela.removeLast())) missesNaJanela--;
            cursor = cursor.minusDays(1);
        }
        // a sequência não pode terminar numa folga: apara as falhas do fim
        while (!janela.isEmpty() && Boolean.TRUE.equals(janela.peekFirst())) {
            janela.pollFirst(); streak--;
        }
        return streak;
    }

    /** Marco de sequência alcançado (0, 7, 30, 100, 365). */
    private static int streakMilestone(int streak) {
        int mk = 0;
        for (int m : new int[]{7, 30, 100, 365}) if (streak >= m) mk = m;
        return mk;
    }

    /** Selo do marco para exibir na linha de sequência. */
    private static String streakBadge(int streak) {
        int mk = streakMilestone(streak);
        return mk == 0 ? "" : mk >= 365 ? "  🏆" : mk >= 100 ? "  🥇" : mk >= 30 ? "  🥈" : "  🥉";
    }

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
                Timer t = new Timer(400, e -> toast("🎉 " + mk + " dias de sequência! Continue assim."));
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
        java.util.function.IntSupplier nDiasSel = () -> {
            int n = 0;
            for (JToggleButton t : tgDias) if (t.isSelected()) n++;
            return n == 0 ? 7 : n;
        };

        JLabel diasHint = dlgHint(nDiasSel.getAsInt() + " dia(s)/semana com meta");
        // Ao MEXER nos dias, semana e mês passam a ser diária × nº de dias.
        for (JToggleButton t : tgDias) t.addItemListener(e -> {
            int nd = nDiasSel.getAsInt();
            int d;
            try { d = parseDuration(fDay.getText()); } catch (Exception ex) { d = 0; }
            if (d > 0) {
                int wk = d * nd, mo = (int) Math.round(wk * 30.0 / 7.0);
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
        JTextField fExam = new JTextField(
                examAtual == null ? "" : examAtual.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), 10);
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
            int perDay = Math.max(5, melhorSem / Math.max(1, nDiasSel.getAsInt()));
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
            gDay   = parseDuration(fDay.getText());
            gWeek  = parseDuration(fWeek.getText());
            gMonth = parseDuration(fMonth.getText());
        } catch (IllegalArgumentException ex) {
            toast("Meta inválida — use algo como \"90\", \"1h30\" ou \"1:30\".");
            return;
        }

        // Dias da meta
        java.util.Set<java.time.DayOfWeek> dias = java.util.EnumSet.noneOf(java.time.DayOfWeek.class);
        for (int i = 0; i < 7; i++) if (tgDias[i].isSelected()) dias.add(java.time.DayOfWeek.of(i + 1));
        if (dias.isEmpty() || dias.size() == 7) goalDays.remove(subject);
        else                                    goalDays.put(subject, dias);

        // Data da prova
        String et = fExam.getText().trim();
        if (et.isEmpty()) {
            examDate.remove(subject);
        } else {
            try {
                examDate.put(subject, LocalDate.parse(et,
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            } catch (Exception ex) {
                toast("Data da prova inválida (use dd/mm/aaaa) — ignorada.");
                examDate.remove(subject);
            }
        }

        int gMin;
        try { gMin = parseDuration(fMin.getText()); } catch (IllegalArgumentException ex) { gMin = 0; }
        goals.put(subject, gDay);
        if (gMin > 0 && gMin < gDay) goalsMin.put(subject, gMin); else goalsMin.remove(subject);
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
    private int adaptiveGoalSuggestion(String subject) {
        int goal = goals.getOrDefault(subject, 0);
        if (goal <= 0) return 0;
        LocalDate today = LocalDate.now();
        int agendados = 0, batidos = 0, somaMin = 0;
        LocalDate d = today;
        while (agendados < 14) {
            if (isGoalDay(subject, d)) {
                agendados++;
                int m = minutesOnDay(subject, d);
                somaMin += m;
                if (m >= goal) batidos++;
            }
            d = d.minusDays(1);
            if (d.isBefore(today.minusDays(90))) break;
        }
        if (agendados < 5) return 0;
        double media = somaMin / (double) agendados;
        double taxa  = batidos / (double) agendados;

        if (currentStreak(subject) >= 7 && media >= goal * 1.2) {
            int novo = (int) (Math.round(goal * 1.15 / 5.0) * 5);
            return novo > goal ? novo : 0;
        }
        if (taxa < 0.4) {
            int novo = Math.max(5, (int) (Math.round(goal * 0.8 / 5.0) * 5));
            return novo < goal ? novo : 0;
        }
        return 0;
    }

    /**
     * Preenche os 3 campos a partir de uma meta diária, usando o nº de dias/semana
     * selecionados: semana = diária × nDias; mês = semana × ~4,345.
     */
    private void fillGoalFields(int daily, int nDias, JTextField fDay, JTextField fWeek, JTextField fMonth) {
        int week = daily * nDias;
        fDay.setText(String.valueOf(daily));
        fWeek.setText(String.valueOf(week));
        fMonth.setText(String.valueOf((int) Math.round(week * 30.0 / 7.0)));
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
    private int avgDailyMinutes(String subject, int days) {
        LocalDate today = LocalDate.now();
        return minutesInRange(subject, today.minusDays(days - 1), today) / days;
    }

    /** Média por dia efetivamente estudado nos últimos {@code days} dias. */
    private int avgActiveDayMinutes(String subject, int days) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1);
        Set<LocalDate> daysStudied = new HashSet<>();
        int total = 0;
        for (StudySession s : sessions) {
            if (subject != null && !s.getSubject().equals(subject)) continue;
            LocalDate d = dateOf(s.getTimestamp());
            if (d.isBefore(start) || d.isAfter(today)) continue;
            daysStudied.add(d);
            total += s.getMinutes();
        }
        return daysStudied.isEmpty() ? 0 : total / daysStudied.size();
    }

    /** Minutos da melhor semana ISO da matéria em todo o histórico. */
    private int bestWeekMinutes(String subject) {
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        Map<String, Integer> porSemana = new HashMap<>();
        for (StudySession s : sessions) {
            if (subject != null && !s.getSubject().equals(subject)) continue;
            LocalDate d = dateOf(s.getTimestamp());
            String k = d.get(wf.weekBasedYear()) + "-" + d.get(wf.weekOfWeekBasedYear());
            porSemana.merge(k, s.getMinutes(), Integer::sum);
        }
        int max = 0;
        for (int v : porSemana.values()) max = Math.max(max, v);
        return max;
    }

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

    /**
     * Lê uma duração em minutos. Aceita: "90", "90m", "1h", "1h30", "1h30m",
     * "1:30", "1,5h", "2 h". Vazio ⇒ 0. Negativo ⇒ 0.
     * @throws IllegalArgumentException se o texto não for reconhecido.
     */
    private static int parseDuration(String text) {
        if (text == null) return 0;
        String t = text.trim().toLowerCase().replace(" ", "").replace(',', '.');
        if (t.isEmpty()) return 0;
        try {
            if (t.contains(":")) {                       // h:mm
                String[] p = t.split(":", 2);
                int h = p[0].isEmpty() ? 0 : Integer.parseInt(p[0]);
                int m = p[1].isEmpty() ? 0 : Integer.parseInt(p[1]);
                return Math.max(0, h * 60 + m);
            }
            t = t.replace("min", "m");
            if (t.contains("h") || t.endsWith("m")) {    // 1h / 1h30 / 45m / 1.5h
                int hIdx = t.indexOf('h');
                double h = 0; int m = 0;
                if (hIdx >= 0) {
                    String hs = t.substring(0, hIdx);
                    if (!hs.isEmpty()) h = Double.parseDouble(hs);
                    String rest = t.substring(hIdx + 1).replace("m", "");
                    if (!rest.isEmpty()) m = Integer.parseInt(rest);
                } else {
                    String ms = t.replace("m", "");
                    if (!ms.isEmpty()) m = Integer.parseInt(ms);
                }
                return Math.max(0, (int) Math.round(h * 60) + m);
            }
            if (t.contains(".")) return Math.max(0, (int) Math.round(Double.parseDouble(t)));
            return Math.max(0, Integer.parseInt(t));     // número puro = minutos
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("duração inválida: " + text);
        }
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
        String name = AppTheme.semHtml(fName.getText().replace('|', '/').trim());   // | separa áreas na semana
        if (name.isEmpty()) return;
        String type = TYPE_KEYS[Math.max(0, cbType.getSelectedIndex())];

        if (archived.contains(name)) { unarchiveSubject(name); return; }   // era arquivada → desarquiva
        if (studyDataMap.containsKey(name)) { toast("Essa área já existe!"); return; }
        studyDataMap.put(name, new StudyData(0, AppTheme.nextColor(studyDataMap.size())));
        if (!"estudo".equals(type)) areaType.put(name, type);
        refreshCombo(); selectIfIdle(name); updateUI();
    }

    private void unarchiveSubject(String name) {
        if (!archived.remove(name)) return;
        refreshCombo(); selectIfIdle(name); updateUI();
        toast("\"" + name + "\" desarquivada.");
    }

    /** Tem sessão em andamento (cronômetro com tempo ou Pomodoro rodando)? */
    private boolean sessionInProgress() {
        return elapsedSeconds > 0 || (stopwatch != null && stopwatch.isRunning())
            || (countdownTimer != null && countdownTimer.isRunning());
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
        for (int i = 0; i < TYPE_KEYS.length; i++) itens[i] = TYPE_ICONS[i] + "  " + TYPE_LABELS[i];
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

        String novoTipo = TYPE_KEYS[Math.max(0, cbType.getSelectedIndex())];
        if ("estudo".equals(novoTipo)) areaType.remove(old); else areaType.put(old, novoTipo);

        String nw = AppTheme.semHtml(fName.getText().replace('|', '/').trim());
        if (nw.isEmpty() || nw.equals(old)) { refreshCombo(); subjectComboBox.setSelectedItem(cur); updateUI(); return; }
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
        if (goals.containsKey(old))      { goals.put(nw, goals.remove(old)); }
        if (goalsMin.containsKey(old))   { goalsMin.put(nw, goalsMin.remove(old)); }
        if (goalsWeek.containsKey(old))  { goalsWeek.put(nw, goalsWeek.remove(old)); }
        if (goalsMonth.containsKey(old)) { goalsMonth.put(nw, goalsMonth.remove(old)); }
        if (streakBest.containsKey(old)) { streakBest.put(nw, streakBest.remove(old)); }
        if (goalDays.containsKey(old))   { goalDays.put(nw, goalDays.remove(old)); }
        if (archived.remove(old))        { archived.add(nw); }
        if (areaType.containsKey(old))   { areaType.put(nw, areaType.remove(old)); }
        if (subFocos.containsKey(old))   { subFocos.put(nw, subFocos.remove(old)); }
        weekPlan.values().forEach(l -> l.replaceAll(a -> old.equals(a) ? nw : a));
        for (ChecklistItem it : checklist) if (it.subject.equals(old)) it.subject = nw;
        if (examDate.containsKey(old))   { examDate.put(nw, examDate.remove(old)); }
        refreshCombo(); subjectComboBox.setSelectedItem(old.equals(cur) ? nw : cur); saveChecklist(); updateUI();
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
        long ativas = studyDataMap.keySet().stream().filter(k -> !archived.contains(k)).count();
        if (ativas <= 1) { toast("Deixe ao menos uma matéria ativa."); return; }
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
        if (!archived.contains(s)
                && studyDataMap.keySet().stream().filter(k -> !archived.contains(k)).count() <= 1) {
            toast("Deixe ao menos uma área ativa."); return;
        }
        int r =JOptionPane.showConfirmDialog(this, "Deletar \"" + s + "\"? Todo o tempo será perdido.", "Deletar Matéria", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) { studyDataMap.remove(s); goals.remove(s); goalsMin.remove(s); goalsWeek.remove(s); goalsMonth.remove(s); streakBest.remove(s); goalDays.remove(s); examDate.remove(s); archived.remove(s); areaType.remove(s); subFocos.remove(s); weekPlan.values().forEach(l -> l.remove(s)); weekPlan.values().removeIf(java.util.List::isEmpty); checklist.removeIf(it -> it.subject.equals(s)); saveChecklist(); refreshCombo(); updateUI(); }
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

    private static final String EXPORT_SEP_DATA     = "===== FOCAESTUDO DADOS =====";
    private static final String EXPORT_SEP_SESSIONS  = "===== FOCAESTUDO SESSOES =====";
    private static final String EXPORT_SEP_TAREFAS   = "===== FOCAESTUDO TAREFAS =====";
    private static final long   MAX_IMPORT_BYTES     = 20L * 1024 * 1024;   // backups reais têm poucos KB

    private void exportData() {
        saveData(); saveSessions(); saveChecklist();   // garante que o disco está atualizado
        JFileChooser fc = new JFileChooser();          // abre em Documentos
        fc.setSelectedFile(new File("focaestudo-backup-"
                + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ".txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String dados    = SAVE_FILE.exists()      ? readText(SAVE_FILE)      : "";
            String sessoes  = SESSION_FILE.exists()   ? readText(SESSION_FILE)   : "";
            String tarefas  = CHECKLIST_FILE.exists() ? readText(CHECKLIST_FILE) : "";
            String out = EXPORT_SEP_DATA + "\n" + dados + "\n"
                       + EXPORT_SEP_SESSIONS + "\n" + sessoes + "\n"
                       + EXPORT_SEP_TAREFAS + "\n" + tarefas + "\n";
            java.nio.file.Files.write(fc.getSelectedFile().toPath(),
                    out.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            toast("Backup exportado.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Falha ao exportar: " + e.getMessage());
        }
    }

    private void importData() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        if (fc.getSelectedFile().length() > MAX_IMPORT_BYTES) {
            JOptionPane.showMessageDialog(this, "Arquivo grande demais para ser um backup do FocaEstudo.");
            return;
        }
        try {
            String all = new String(java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            int iData = all.indexOf(EXPORT_SEP_DATA);
            int iSess = all.indexOf(EXPORT_SEP_SESSIONS);
            int iTar  = all.indexOf(EXPORT_SEP_TAREFAS);
            if (iData < 0 || iSess < 0 || iSess < iData) {
                JOptionPane.showMessageDialog(this, "Arquivo não parece um backup do FocaEstudo.");
                return;
            }
            String dados   = all.substring(iData + EXPORT_SEP_DATA.length(), iSess).trim();
            String sessoes = iTar > iSess
                    ? all.substring(iSess + EXPORT_SEP_SESSIONS.length(), iTar).trim()
                    : all.substring(iSess + EXPORT_SEP_SESSIONS.length()).trim();
            String tarefas = iTar > iSess ? all.substring(iTar + EXPORT_SEP_TAREFAS.length()).trim() : "";

            Properties test = new Properties();
            test.load(new java.io.StringReader(dados));
            long materias = test.stringPropertyNames().stream().filter(k -> {
                String v = test.getProperty(k);
                if (v == null || !v.contains(",")) return false;
                try {
                    String[] pp = v.split(",");
                    Integer.parseInt(pp[0].trim());
                    Integer.parseInt(pp[1].trim());
                    return true;
                } catch (Exception ex) { return false; }
            }).count();
            if (materias == 0) {
                JOptionPane.showMessageDialog(this, "O backup não tem matérias válidas — importação cancelada.");
                return;
            }

            int r = JOptionPane.showConfirmDialog(this,
                    "Isto substitui TODas as matérias, metas e histórico atuais pelos do arquivo.\n"
                  + "Seus dados atuais vão para .bak. Continuar?",
                    "Importar dados", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;

            backup(SAVE_FILE); backup(SESSION_FILE); backup(CHECKLIST_FILE);
            java.nio.file.Files.write(SAVE_FILE.toPath(),    dados.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.write(SESSION_FILE.toPath(), sessoes.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.write(CHECKLIST_FILE.toPath(), tarefas.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            reloadFromDisk();
            toast("Dados importados.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Falha ao importar: " + e.getMessage());
        }
    }

    private static String readText(File f) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.ISO_8859_1);
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
        setMinFocusMin = 1; setIdleMinutes = 10; setPomoAutoCycle = false;
        setReminderHour = -1; setTransitionMin = 5; setHyperfocusH = 3;
        setUpdateCheck = true; updateCheckedDay = null;
        weeklySummaryShownWeek = null; reminderShownDay = null; balanceNudgeWeek = null;
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
        JSpinner spFocus = new JSpinner(new SpinnerNumberModel(setMinFocusMin, 0, 60, 1));
        JSpinner spIdle  = new JSpinner(new SpinnerNumberModel(setIdleMinutes, 0, 120, 1));
        JSpinner spTrans = new JSpinner(new SpinnerNumberModel(setTransitionMin, 0, 30, 1));
        JSpinner spHyper = new JSpinner(new SpinnerNumberModel(setHyperfocusH, 0, 12, 1));
        JCheckBox cbCycle = new JCheckBox("Encadear foco → pausa → foco automaticamente", setPomoAutoCycle);
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
        cbReminder.setSelectedItem(setReminderHour < 0 ? "Desligado" : String.format("%02d:00", setReminderHour));
        addFormRow(panel, c, row++, "Lembrete diário:", cbReminder);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        panel.add(dlgHint("Se não tiver estudado até essa hora, o app avisa (notificação do Windows)."), c);

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

        JCheckBox cbUpdate = new JCheckBox("Avisar quando sair uma versão nova", setUpdateCheck);
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

        setMinFocusMin   = (int) spFocus.getValue();
        setIdleMinutes   = (int) spIdle.getValue();
        setTransitionMin = (int) spTrans.getValue();
        setHyperfocusH   = (int) spHyper.getValue();
        setPomoAutoCycle = cbCycle.isSelected();
        setUpdateCheck   = cbUpdate.isSelected();
        String rem = (String) cbReminder.getSelectedItem();
        setReminderHour = (rem == null || rem.startsWith("Des")) ? -1 : Integer.parseInt(rem.substring(0, 2));
        if (autoCycleCheck != null) autoCycleCheck.setSelected(setPomoAutoCycle);
        saveData();
        toast("Ajustes salvos.");
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
        // buildUI() cria um combo novo: sem isso a matéria volta para o primeiro item.
        Object sel = subjectComboBox != null ? subjectComboBox.getSelectedItem() : null;

        SwingUtilities.updateComponentTreeUI(this);   // aplica a paleta nova do L&F
        getContentPane().removeAll();
        getContentPane().setBackground(AppTheme.BG);
        buildUI();

        // Reconecta listeners e restaura estado
        setupTimers();
        if (swRunning) stopwatch.start();
        if (cdRunning) countdownTimer.start();

        refreshCombo();
        if (sel != null && studyDataMap.containsKey(sel) && !archived.contains(sel))
            subjectComboBox.setSelectedItem(sel);   // o listener do combo atualiza sub-foco e metas
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

        chartPanel.setGroups(buildPieGroups());
        if (calendarPanel != null) {
            Map<String, Color> colorMap = new LinkedHashMap<>();
            studyDataMap.forEach((k, v) -> colorMap.put(k, v.getColor()));
            calendarPanel.updateColors(colorMap);
        }
        updateGoalProgress();
        refreshStreaks();
        if (weekScreen != null) weekScreen.setAreas(activeAreas(), areaColors());
        refreshAreasScreen();
        refreshTodayCard();
        refreshNowCard();
        refreshBalanceCard();
        refreshGoalsCard();
        refreshProgressCard();
        updatePomodoroCount();
        balanceNudge();
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
        refreshKindCombo();
    }

    private static int typeIndexKnown(String key) {
        for (int i = 0; i < TYPE_KEYS.length; i++) if (TYPE_KEYS[i].equals(key)) return i;
        return -1;
    }

    /** Popula o combo de sub-foco com os sub-focos da área ativa. */
    private void refreshKindCombo() {
        if (kindCombo == null) return;
        String area = (String) subjectComboBox.getSelectedItem();
        ActionListener[] ls = kindCombo.getActionListeners();
        for (ActionListener l : ls) kindCombo.removeActionListener(l);
        kindCombo.removeAllItems();
        kindCombo.addItem("–");
        if (area != null)
            for (String sf : subFocos.getOrDefault(area, java.util.Collections.emptyList())) kindCombo.addItem(sf);
        kindCombo.addItem(KIND_MANAGE);
        kindCombo.setSelectedIndex(0);
        for (ActionListener l : ls) kindCombo.addActionListener(l);
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
        refreshKindCombo();
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
        if (balanceNudgeWeek != null) p.setProperty("__balance_nudge_week__", balanceNudgeWeek);
        if (!archived.isEmpty()) p.setProperty("__archived__", String.join(",", archived));
        streakBest.forEach((k, v) -> p.setProperty("streakbest_" + k, String.valueOf(v)));
        unknownProps.forEach(p::setProperty);   // preserva chaves que não soubemos ler
        p.setProperty("__streakbest_general__", String.valueOf(streakBestGeneral));
        p.setProperty("__streak_milestone__", String.valueOf(celebratedStreakMilestone));
        p.setProperty("__min_focus__",      String.valueOf(setMinFocusMin));
        p.setProperty("__idle_min__",       String.valueOf(setIdleMinutes));
        p.setProperty("__pomo_autocycle__", String.valueOf(setPomoAutoCycle));
        p.setProperty("__reminder_hour__", String.valueOf(setReminderHour));
        p.setProperty("__transition_min__", String.valueOf(setTransitionMin));
        p.setProperty("__hyperfocus_h__",   String.valueOf(setHyperfocusH));
        p.setProperty("__update_check__",   String.valueOf(setUpdateCheck));
        if (updateCheckedDay != null) p.setProperty("__update_checked_day__", updateCheckedDay);
        if (weeklySummaryShownWeek != null) p.setProperty("__week_summary_shown__", weeklySummaryShownWeek);
        if (reminderShownDay != null)       p.setProperty("__reminder_shown_day__", reminderShownDay);
        p.setProperty("__theme__", AppTheme.dark ? "dark" : "light");

        backup(SAVE_FILE);
        storeAtomic(p, SAVE_FILE, "FocaEstudo Data");
    }

    /**
     * Grava as Properties de forma atômica: escreve num arquivo temporário, força
     * para disco e só então renomeia por cima do original. Se faltar luz no meio,
     * o arquivo bom continua intacto.
     */
    private static void storeAtomic(Properties p, File target, String header) {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                p.store(out, header);
                out.flush();
                out.getFD().sync();
            }
            try {
                java.nio.file.Files.move(tmp.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                java.nio.file.Files.move(tmp.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
            tmp.delete();
        }
    }

    /** Lê Properties já neutralizando HTML em chaves e valores (ver AppTheme.semHtml). */
    private static Properties loadSemHtml(InputStream in) throws IOException {
        Properties raw = new Properties();
        raw.load(in);
        Properties p = new Properties();
        for (String k : raw.stringPropertyNames())
            p.setProperty(AppTheme.semHtml(k), AppTheme.semHtml(raw.getProperty(k)));
        return p;
    }

    /** Copia arquivo para <nome>.bak antes de sobrescrever. */
    private static void backup(File f) {
        if (!f.isFile()) return;
        try (FileInputStream in = new FileInputStream(f);
             FileOutputStream out = new FileOutputStream(new File(f.getParentFile(), f.getName() + ".bak"))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException ignored) {}
    }

    private void loadData() {
        if (!SAVE_FILE.exists()) return;   // primeiro uso de verdade
        try (FileInputStream in = new FileInputStream(SAVE_FILE)) {
            Properties p = loadSemHtml(in);
            for (String k : p.stringPropertyNames()) {
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
                if (k.equals("__min_focus__")) {
                    try { setMinFocusMin = Math.max(0, Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.equals("__idle_min__")) {
                    try { setIdleMinutes = Math.max(0, Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.equals("__pomo_autocycle__")) {
                    setPomoAutoCycle = "true".equalsIgnoreCase(p.getProperty(k));
                    continue;
                }
                if (k.equals("__reminder_hour__")) {
                    try { setReminderHour = Integer.parseInt(p.getProperty(k)); } catch (Exception ignored) {}
                    continue;
                }
                if (k.equals("__transition_min__")) {
                    try { setTransitionMin = Math.max(0, Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.equals("__hyperfocus_h__")) {
                    try { setHyperfocusH = Math.max(0, Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                    continue;
                }
                if (k.equals("__update_check__")) {
                    setUpdateCheck = !"false".equalsIgnoreCase(p.getProperty(k));
                    continue;
                }
                if (k.equals("__update_checked_day__")) { updateCheckedDay = p.getProperty(k); continue; }
                if (k.equals("__week_summary_shown__")) { weeklySummaryShownWeek = p.getProperty(k); continue; }
                if (k.equals("__reminder_shown_day__")) { reminderShownDay = p.getProperty(k); continue; }
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
                if (k.equals("__balance_nudge_week__")) { balanceNudgeWeek = p.getProperty(k); continue; }
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
        Properties p = new Properties();
        for (int i = 0; i < sessions.size(); i++) {
            p.setProperty("s" + i, sessions.get(i).serialize());
        }
        backup(SESSION_FILE);
        storeAtomic(p, SESSION_FILE, "FocaEstudo Sessions");
    }

    private void loadSessions() {
        Properties p = new Properties();
        if (!SESSION_FILE.exists()) return;   // primeiro uso
        try (FileInputStream in = new FileInputStream(SESSION_FILE)) {
            p.load(in);
            List<StudySession> tmp = new ArrayList<>();
            for (String k : p.stringPropertyNames()) {
                StudySession s = StudySession.deserialize(p.getProperty(k));
                if (s != null) tmp.add(s);
            }
            tmp.sort(Comparator.comparingLong(StudySession::getTimestamp));
            sessions.addAll(tmp);
        } catch (IOException e) {
            loadOk = false;   // não deixa um save posterior truncar o histórico
            e.printStackTrace();
        }
    }

    private void saveChecklist() {
        if (!ready || !loadOk) return;
        Properties p = new Properties();
        for (int i = 0; i < checklist.size(); i++) p.setProperty("c" + i, checklist.get(i).serialize());
        backup(CHECKLIST_FILE);
        storeAtomic(p, CHECKLIST_FILE, "FocaEstudo Checklist");
    }

    private void loadChecklist() {
        if (!CHECKLIST_FILE.exists()) return;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(CHECKLIST_FILE)) {
            p.load(in);
            for (String k : p.stringPropertyNames()) {
                ChecklistItem it = ChecklistItem.deserialize(p.getProperty(k));
                if (it != null) checklist.add(it);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ── UTILS ───────────────────────────────────────────────────────────────

    private void toast(String msg) {
        JWindow w = new JWindow(this);
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

        int baseX = getX() + (getWidth() - w.getWidth()) / 2;
        int endY  = getY() + getHeight() - w.getHeight() - 56;
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
        if (!setUpdateCheck || atual == null) return;
        String hoje = LocalDate.now().toString();
        if (hoje.equals(updateCheckedDay)) return;
        Thread t = new Thread(() -> {
            String nova = fetchLatestVersion();
            if (nova == null) return;   // sem internet: tenta de novo na próxima abertura
            SwingUtilities.invokeLater(() -> {
                updateCheckedDay = hoje;
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
                trayIcon.addActionListener(e -> { setVisible(true); setState(Frame.NORMAL); toFront(); });
                java.awt.SystemTray.getSystemTray().add(trayIcon);
            } catch (Exception e) { trayIcon = null; }
        }
        reminderTimer = new Timer(5 * 60 * 1000, e -> checkReminder());
        reminderTimer.setInitialDelay(30 * 1000);
        reminderTimer.start();
    }

    private void checkReminder() {
        if (setReminderHour < 0) return;
        LocalDate today = LocalDate.now();
        String hoje = today.toString();
        if (hoje.equals(reminderShownDay)) return;
        if (isHardDay(today)) { reminderShownDay = hoje; return; }             // dia difícil: sem lembrete
        if (java.time.LocalTime.now().getHour() < setReminderHour) return;
        if (minutesOnDay(null, today) > 0) { reminderShownDay = hoje; return; } // já estudou hoje

        reminderShownDay = hoje;
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
        if (semana.equals(weeklySummaryShownWeek)) return;
        // só mostra a partir de sábado/domingo (fim de semana) ou se nunca mostrou
        if (weeklySummaryShownWeek != null
                && today.getDayOfWeek().getValue() < 6) return;

        weeklySummaryShownWeek = semana;
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
        sb.append("\nBora manter o ritmo! 💪");

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

        JLabel h1 = new JLabel("Bem-vindo ao FocaEstudo 👋");
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
        int idx = 0;
        for (String s : input.split(",")) {
            String t = AppTheme.semHtml(s.trim());
            if (!t.isEmpty() && !studyDataMap.containsKey(t))
                studyDataMap.put(t, new StudyData(0, AppTheme.nextColor(idx++)));
        }
        if (studyDataMap.isEmpty()) { System.exit(0); }
    }

    // Mantido vivo pela duração do processo: o SO libera o lock quando o processo termina.
    @SuppressWarnings("unused")
    private static java.nio.channels.FileLock instanceLock;
    private static final File LOCK_FILE = new File(DATA_DIR, "focaestudo.lock");

    /**
     * Garante uma única instância: duas cópias gravando o mesmo save corrompem os dados.
     * Usa lock de arquivo na pasta dos dados (antes era uma porta TCP local, que abria
     * um ponto de escuta desnecessário e podia ser ocupada por outro programa).
     */
    private static boolean acquireSingleInstanceLock() {
        try {
            java.nio.channels.FileChannel ch = new RandomAccessFile(LOCK_FILE, "rw").getChannel();
            instanceLock = ch.tryLock();
            return instanceLock != null;   // null ⇒ outra instância segura o lock
        } catch (java.nio.channels.OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            return true;    // pasta sem permissão de escrita: não dá para checar, segue
        }
    }

    public static void main(String[] args) {
        DATA_DIR.mkdirs();
        if (!acquireSingleInstanceLock()) {
            int r = JOptionPane.showConfirmDialog(null,
                    "O FocaEstudo parece já estar aberto.\n"
                  + "Abrir assim mesmo pode corromper seus dados.\n\nAbrir mesmo assim?",
                    "FocaEstudo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) System.exit(0);
        }
        migrateLegacyData();   // depois da trava: duas instâncias não migram ao mesmo tempo
        AppTheme.installLookAndFeel();
        SwingUtilities.invokeLater(() -> new StudyTracker().initializeUI());
    }
}
