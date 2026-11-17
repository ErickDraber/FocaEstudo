import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.time.LocalDate;
import java.util.Map;

/**
 * O fluxo das sessões: cronômetro, Pomodoro (com o ciclo automático), Manual, modo foco
 * (a faixa {@link FocusBar}) e o aviso de inatividade. Os estados ficam nos modelos
 * ({@link StopwatchModel}, {@link PomodoroModel}); os Timers do Swing só dão o tique de 1 s.
 *
 * A parte visual é o {@link TimerCard} (recriado a cada rebuildUI, por isso vem por
 * {@link #setCard}). Gravar o tempo é do {@link SessionRecorder} e o "Como foi?" do
 * {@link SessionDetailsDialog}. Os métodos mantêm os nomes de quando moravam no StudyTracker.
 */
final class SessionFlow {

    /** O que o StudyTracker faz por nós. */
    interface Host {
        String selectedArea();          // combo "Área:" (a área do cronômetro)
        void toast(String msg);
        void refreshNowCard();
        void updatePomodoroCount();
        void showInfo(String title, String msg);
        Component window();             // a janela principal (dono dos diálogos fora do modo foco)
        void hideWindow();              // modo foco: some a janela
        void showWindow();              // sai do modo foco: volta, restaurada e na frente
    }

    // --- Cronômetro: estado em StopwatchModel; o Timer do Swing só dá o tique de 1 s ---
    final StopwatchModel sw = new StopwatchModel();
    Timer stopwatch;

    // --- Pomodoro: estado em PomodoroModel; o Timer do Swing só dá o tique de 1 s ---
    final PomodoroModel pomo = new PomodoroModel();
    Timer countdownTimer;

    // --- Inatividade ---
    Timer idleTimer;
    long lastActivityMs = System.currentTimeMillis();
    boolean idleAsking = false;

    // --- Modo foco: a janela some e fica só a faixa ---
    FocusBar focusBar;

    // --- Sub-foco já escolhido (ex.: rodízio do "Agora"); vem marcado no "Como foi?" ---
    String pendingSubFoco;

    private TimerCard card;

    private final AppSettings cfg;
    private final Map<String, StudyData> data;
    private final Map<String, Integer> goals;
    private final StudyStats stats;
    private final SessionRecorder recorder;
    private final SessionDetailsDialog sessionDetails;
    private final Host host;

    SessionFlow(AppSettings cfg, Map<String, StudyData> data, Map<String, Integer> goals, StudyStats stats,
                SessionRecorder recorder, SessionDetailsDialog sessionDetails, Host host) {
        this.cfg = cfg;
        this.data = data;
        this.goals = goals;
        this.stats = stats;
        this.recorder = recorder;
        this.sessionDetails = sessionDetails;
        this.host = host;
    }

    /** O cartão da tela Hoje (chamado a cada buildUI). */
    void setCard(TimerCard card) { this.card = card; }

    /** Tem sessão em andamento (cronômetro com tempo ou Pomodoro rodando)? */
    boolean sessionInProgress() {
        return sw.hasTime() || sw.isRunning() || pomodoroActive();
    }

    boolean pomodoroActive() { return pomo.isActive(); }

    /** Pausa do Pomodoro em andamento (o cartão "Agora" mostra outra coisa). */
    boolean pomodoroBreak() { return pomodoroActive() && !pomo.isFocus(); }

    /** Antes de reconstruir a tela: para os tiques antigos (o estado fica nos modelos). */
    void stopTicks() {
        if (stopwatch != null) stopwatch.stop();
        if (countdownTimer != null) countdownTimer.stop();
    }

    /** Depois de reconstruir: tiques novos, religados se a sessão estava rodando. */
    void restartTicks() {
        setupTimers();
        if (sw.isRunning())   stopwatch.start();
        if (pomo.isRunning()) countdownTimer.start();
    }

    static String formatTime(int totalSeconds) {
        int h = totalSeconds / 3600, m = (totalSeconds % 3600) / 60;
        if (h > 0) return h + "h " + m + "min";
        return m + " min";
    }

    /** Espaço: inicia se parado, pausa se rodando, retoma se pausado com tempo. */
    void toggleStopwatch() {
        if (sw.isRunning()) stopStopwatch();
        else                                            startStopwatch();
    }

    // ── INATIVIDADE ─────────────────────────────────────────────────────────

    void setupIdleWatch() {
        Toolkit.getDefaultToolkit().addAWTEventListener(
                e -> lastActivityMs = System.currentTimeMillis(),
                AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

        idleTimer = new Timer(20_000, e -> checkIdle());
        idleTimer.start();
    }

    void checkIdle() {
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

    // ── TIMERS ──────────────────────────────────────────────────────────────

    void setupTimers() {
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
    void syncTimerControls() {
        updateStopwatchLabel();
        card.sync(sw, pomo);
    }

    // ── STOPWATCH ───────────────────────────────────────────────────────────

    void startStopwatch() {
        if (host.selectedArea() == null) {
            host.toast("Selecione uma matéria!"); return;
        }
        boolean novaSessao = sw.start();   // retomar não reabre o modo foco
        lastActivityMs = System.currentTimeMillis();
        stopwatch.start();
        card.stopwatchRunning();
        updateStopwatchInfo();
        host.refreshNowCard();
        if (novaSessao) enterFocusMode(false);
        refreshFocusBar();
    }

    void stopStopwatch() {
        sw.pause();
        stopwatch.stop();
        card.stopwatchPaused(sw.hasTime());
        updateStopwatchInfo();
        refreshFocusBar();
    }

    /** Zera SOMENTE o cronômetro (o tempo acumulado da matéria não é tocado). */
    void resetStopwatch() {
        sw.reset();
        stopwatch.stop();
        updateStopwatchLabel();
        card.stopwatchCleared();
        if (!sessionInProgress()) exitFocusMode();   // sessão terminou (salva ou zerada)
        host.refreshNowCard();
    }

    void submitStopwatchTime() {
        if (!sw.hasTime()) return;
        String subject = host.selectedArea();
        int mins = sw.roundedMinutes();
        if (sw.belowMinimum(cfg.minFocusMin)) {
            host.toast("Abaixo do foco mínimo (" + cfg.minFocusMin + " min) — não salvo.");
            return;   // mantém o tempo para o usuário continuar
        }
        addTime(subject, mins, "cronometro", askSessionDetails(subject, mins, "Sessão concluída"));
        host.toast(formatTime(mins * 60) + " salvo em " + subject);
        resetStopwatch();
        transitionNudge();
    }

    /** Nudge gentil de pausa entre blocos. */
    void transitionNudge() {
        if (cfg.transitionMin <= 0) return;
        Timer t = new Timer(2600, e -> host.toast("Bloco salvo. Que tal " + cfg.transitionMin
                + " min de pausa? Levanta, bebe água."));
        t.setRepeats(false); t.start();
    }

    void updateStopwatchLabel() {
        card.setStopwatchText(sw.elapsedText());
        updateStopwatchInfo();
    }

    /** Linha auxiliar: pausas e tempo bruto (relógio de parede) desde o 1º "Iniciar". */
    void updateStopwatchInfo() {
        if (card == null) return;
        card.setStopwatchInfo(sw.infoText());
        if (sw.shouldWarnHyperfocus(cfg.hyperfocusH))
            host.toast("Você está há " + cfg.hyperfocusH + "h seguidas. Dá um respiro?");
    }

    // ── MODO FOCO ───────────────────────────────────────────────────────────
    // Ao começar uma sessão, a janela some e fica só a faixa (FocusBar): o tempo à vista,
    // nada competindo pela atenção. Volta sozinha quando a sessão termina.

    /** force = pedido explícito ("Modo foco" no cartão Agora), vale mesmo com o ajuste desligado. */
    void enterFocusMode(boolean force) {
        if ((!cfg.focusMode && !force) || focusBar != null || !sessionInProgress()) return;
        focusBar = new FocusBar(new FocusBar.Actions() {
            public void togglePause() { focusTogglePause(); }
            public void finish()      { focusFinish(); }
            public void expand()      { exitFocusMode(); }
        });
        refreshFocusBar();
        focusBar.setVisible(true);
        host.hideWindow();
    }

    void exitFocusMode() {
        if (focusBar == null) return;
        focusBar.dispose();
        focusBar = null;
        host.showWindow();
        host.refreshNowCard();
    }

    void focusTogglePause() {
        if (pomodoroActive()) togglePauseCountdown();
        else                  toggleStopwatch();
    }

    void focusFinish() {
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
    Component dialogOwner() {
        return focusBar != null ? focusBar : host.window();
    }

    void refreshFocusBar() {
        if (focusBar == null) return;
        String area = host.selectedArea();
        StudyData sd = area == null ? null : data.get(area);
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
        if (ideal > 0 && stats.isGoalDay(area, LocalDate.now())) {
            int feito = stats.minutesOnDay(area, LocalDate.now()) + correndo;
            frac = feito / (double) ideal;
            status += "  ·  " + ScreenKit.fmtHM(feito) + " de " + ScreenKit.fmtHM(ideal) + " hoje";
        }
        focusBar.update(nome, cor, tempo, status, pausado, frac, fim);
    }

    // ── POMODORO ────────────────────────────────────────────────────────────

    void startCountdown(boolean isFocus) { startCountdown(isFocus, 0); }

    void startCountdown(boolean isFocus, int minutesOverride) {
        String subject = host.selectedArea();
        if (isFocus && subject == null) { host.toast("Selecione uma matéria!"); return; }
        if (countdownTimer != null) countdownTimer.stop(); // garante parado antes de reiniciar
        int minutos = minutesOverride > 0 ? minutesOverride
                : isFocus ? card.focusMinutes()
                          : card.breakMinutes();
        pomo.start(isFocus, minutos);
        card.pomodoroStarted(isFocus, subject);
        updateCountdownLabel();
        countdownTimer.start();
        host.refreshNowCard();
        refreshFocusBar();
    }

    void stopCountdown(boolean finished) {
        countdownTimer.stop();
        pomo.halt();

        if (finished) {
            Toolkit.getDefaultToolkit().beep();
            String subject = host.selectedArea();
            boolean didFocus = pomo.isFocus() && subject != null;
            if (didFocus) {
                // Ciclo automático não para pra perguntar: o próximo bloco começa sozinho.
                SessionDetailsDialog.Details d;
                if (cfg.pomoAutoCycle) d = SessionDetailsDialog.Details.of(pendingSubFoco);
                else { exitFocusMode(); d = askSessionDetails(subject, pomo.plannedMinutes(), "Foco concluído"); }
                addTime(subject, pomo.plannedMinutes(), "pomodoro", d);
                pomo.completeFocus();
                host.updatePomodoroCount();
            }

            if (cfg.pomoAutoCycle) {
                if (didFocus) {
                    boolean longBreak = pomo.longBreakDue();
                    int mins = longBreak ? PomodoroModel.longBreakMinutes(card.breakMinutes()) : 0;
                    host.toast((longBreak ? "4 focos! Pausa longa de " + mins + " min." : "Foco concluído — pausa."));
                    startCountdown(false, mins);
                    return;                       // ciclo continua
                } else {
                    host.toast("Pausa concluída — de volta ao foco.");
                    if (startCountdownIfPossible(true)) return;
                }
            } else {
                exitFocusMode();   // o aviso de fim aparece com o app aberto
                if (!didFocus) host.showInfo("Pausa concluída", "Pronto para mais uma sessão?");
            }
        }

        // Estado ocioso (não encadeou). Interrompido pelo usuário ⇒ o ciclo recomeça.
        pomo.idle(finished, card.focusMinutes());
        updateCountdownLabel();
        card.pomodoroIdle();
        if (!sessionInProgress()) exitFocusMode();
        host.refreshNowCard();
    }

    /** Tenta iniciar o foco no ciclo automático; devolve false se não deu (ex.: sem matéria). */
    boolean startCountdownIfPossible(boolean isFocus) {
        if (isFocus && host.selectedArea() == null) return false;
        startCountdown(isFocus, 0);
        return pomo.isRunning();
    }

    void togglePauseCountdown() {
        if (!pomo.togglePause()) {
            // Retomou
            countdownTimer.start();
            card.pomodoroResumed(pomo.isFocus(), host.selectedArea());
        } else {
            // Pausou
            countdownTimer.stop();
            card.pomodoroPaused();
        }
        refreshFocusBar();
    }

    void updateCountdownLabel() {
        card.setCountdown(pomo.remainingText());
    }

    // ── MANUAL ──────────────────────────────────────────────────────────────

    void addManualTime() {
        String subject = host.selectedArea();
        if (subject == null) { host.toast("Selecione uma matéria!"); return; }
        try {
            int tot = card.manualMinutes();
            if (tot > 0) {
                LocalDate today = LocalDate.now();
                LocalDate day = card.manualDay();
                if (day.isAfter(today)) { host.toast("Não dá para registrar num dia que ainda não chegou."); return; }
                String quando = day.equals(today) ? "" : " (" + TimerCard.dayLabel(day, today).toLowerCase() + ")";
                addTime(subject, tot, "manual", askSessionDetails(subject, tot, "Tempo adicionado" + quando), day);
                card.clearManual();   // volta para "Hoje": registrar no passado é exceção, não padrão
                host.toast(formatTime(tot * 60) + " adicionado a " + subject + quando);
            } else { host.toast("Insira um tempo válido."); }
        } catch (NumberFormatException ex) { host.toast("Apenas números, por favor."); }
    }

    // ── DATA (tempo + sessão) ────────────────────────────────────────────────

    /** "Como foi?" (ver SessionDetailsDialog). No modo foco, aparece junto da faixa. */
    SessionDetailsDialog.Details askSessionDetails(String subject, int minutes, String titulo) {
        return sessionDetails.ask(dialogOwner(), subject, minutes, titulo, pendingSubFoco);
    }

    /** Registra o tempo hoje (ver SessionRecorder). */
    void addTime(String subject, int minutes, String type, SessionDetailsDialog.Details d) {
        recorder.add(subject, minutes, type, d);
    }

    /** Registra o tempo num dia (passado vira sessão às 12:00; ver SessionRecorder). */
    void addTime(String subject, int minutes, String type, SessionDetailsDialog.Details d, LocalDate day) {
        recorder.add(subject, minutes, type, d, day);
    }
}
