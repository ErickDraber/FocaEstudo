import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.*;
import java.util.*;
import java.util.List;

/**
 * Testes do fluxo das sessões (SessionFlow): cronômetro, Pomodoro com e sem ciclo, Manual,
 * modo foco e a troca de tela (stopTicks/restartTicks). Os tiques são dados à mão (sem esperar
 * 1 s); o TimerCard é o de verdade; o StudyTracker é um Host falso que anota o que pediram.
 * O "Como foi?" fica desligado (askDetails = false): ele tem a própria suíte (SessionRecorderTest).
 */
public class SessionFlowTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    // ── estado "do app" ─────────────────────────────────────────────────────
    final AppSettings cfg = new AppSettings();
    final Map<String, StudyData> data = new LinkedHashMap<>();
    final List<StudySession> sessions = new ArrayList<>();
    final Map<String, Integer> goals = new LinkedHashMap<>();
    final StudyStats stats;
    final List<String> toasts = new ArrayList<>(), chamadas = new ArrayList<>();
    final JFrame janela = new JFrame();
    String area = "Calculo";
    final SessionFlow flow;
    final TimerCard card;

    SessionFlowTest() {
        Clock clock = Clock.systemDefaultZone();
        stats = new StudyStats(sessions, data.keySet(), new HashSet<>(), goals, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashSet<>(), clock);
        data.put("Calculo", new StudyData(0, Color.BLUE));
        data.put("Treino", new StudyData(0, Color.RED));
        cfg.askDetails = false;
        cfg.focusMode = false;
        cfg.transitionMin = 0;
        cfg.minFocusMin = 1;
        SessionFlow[] f = {null};
        SessionRecorder rec = new SessionRecorder(data, sessions, goals, stats, clock, new SessionRecorder.Host() {
            public void recorded()        { f[0].pendingSubFoco = null; chamadas.add("recorded"); }
            public void toast(String msg) { toasts.add(msg); }
        });
        flow = new SessionFlow(cfg, data, goals, stats, rec, new SessionDetailsDialog(cfg, new HashMap<>()),
                new SessionFlow.Host() {
            public String selectedArea()                   { return area; }
            public void toast(String msg)                  { toasts.add(msg); }
            public void refreshNowCard()                   { chamadas.add("now"); }
            public void updatePomodoroCount()              { chamadas.add("pomoCount"); }
            public void showInfo(String title, String msg) { chamadas.add("info " + title + ": " + msg); }
            public Component window()                      { return janela; }
            public void hideWindow()                       { chamadas.add("hide"); }
            public void showWindow()                       { chamadas.add("show"); }
        });
        f[0] = flow;
        card = new TimerCard(new TimerCard.Actions() {
            public void areaChanged() {}
            public void goalDialog() {}
            public void startStopwatch()      { flow.startStopwatch(); }
            public void pauseStopwatch()      { flow.stopStopwatch(); }
            public void resetStopwatch()      { flow.resetStopwatch(); }
            public void submitStopwatch()     { flow.submitStopwatchTime(); }
            public void addManual()           { flow.addManualTime(); }
            public void startFocus()          { flow.startCountdown(true); }
            public void startBreak()          { flow.startCountdown(false); }
            public void togglePausePomodoro() { flow.togglePauseCountdown(); }
            public void resetPomodoro()       { flow.stopCountdown(false); }
            public void autoCycleChanged(boolean on) {}
        }, false);
        flow.setCard(card);
        flow.setupTimers();
    }

    static void tique(Timer t, int n) {
        for (int i = 0; i < n; i++)
            for (ActionListener l : t.getActionListeners()) l.actionPerformed(new ActionEvent(t, 0, ""));
    }

    StudySession ultima() { return sessions.get(sessions.size() - 1); }

    static Object campo(Object o, String n) {
        try { var c = o.getClass().getDeclaredField(n); c.setAccessible(true); return c.get(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    static void texto(TimerCard c, String campo, String t) { ((JTextField) campo(c, campo)).setText(t); }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            formatTime();
            cronometro();
            salvarCronometro();
            pomodoroComCiclo();
            pausaLonga();
            pomodoroSemCiclo();
            pausarPomodoro();
            manual();
            modoFoco();
            trocaDeTela();
        });
        avisoDePausa();
        System.out.println("SessionFlow: " + (total - falhas) + "/" + total + " testes passaram");
        System.exit(falhas > 0 ? 1 : 0);
    }

    static void formatTime() {
        eq("0 s", "0 min", SessionFlow.formatTime(0));
        eq("59 s", "0 min", SessionFlow.formatTime(59));
        eq("1 min", "1 min", SessionFlow.formatTime(60));
        eq("1h exata", "1h 0min", SessionFlow.formatTime(3600));
        eq("1h30", "1h 30min", SessionFlow.formatTime(5400));
    }

    static void cronometro() {
        SessionFlowTest t = new SessionFlowTest();
        eq("começa parado", false, t.flow.sessionInProgress());
        eq("sem pausa do pomodoro", false, t.flow.pomodoroBreak());

        t.area = null;
        t.flow.startStopwatch();
        eq("sem área: aviso", List.of("Selecione uma matéria!"), t.toasts);
        eq("sem área: não conta", false, t.flow.sw.isRunning());
        eq("sem área: tique parado", false, t.flow.stopwatch.isRunning());

        t.area = "Calculo";
        t.toasts.clear();
        t.flow.startStopwatch();
        eq("conta", true, t.flow.sw.isRunning());
        eq("tique ligado", true, t.flow.stopwatch.isRunning());
        eq("em andamento", true, t.flow.sessionInProgress());
        eq("avisa o cartão Agora", true, t.chamadas.contains("now"));
        eq("modo foco desligado: sem faixa", null, t.flow.focusBar);

        tique(t.flow.stopwatch, 90);
        eq("tempo", "00:01:30", t.flow.sw.elapsedText());
        t.flow.stopStopwatch();
        eq("pausado", false, t.flow.sw.isRunning());
        eq("pausado: tique parado", false, t.flow.stopwatch.isRunning());
        eq("pausado ainda é sessão", true, t.flow.sessionInProgress());

        t.flow.toggleStopwatch();
        eq("espaço retoma", true, t.flow.sw.isRunning());
        t.flow.toggleStopwatch();
        eq("espaço pausa", false, t.flow.sw.isRunning());

        t.flow.resetStopwatch();
        eq("zerado", "00:00:00", t.flow.sw.elapsedText());
        eq("zerado: sem sessão", false, t.flow.sessionInProgress());
        eq("zerar não grava", 0, t.sessions.size());
    }

    static void salvarCronometro() {
        SessionFlowTest t = new SessionFlowTest();
        t.flow.submitStopwatchTime();
        eq("sem tempo: nada", List.of(), t.toasts);

        t.cfg.minFocusMin = 5;
        t.flow.startStopwatch();
        tique(t.flow.stopwatch, 4 * 60 + 29);   // arredonda para 4
        t.flow.submitStopwatchTime();
        eq("abaixo do mínimo: aviso", List.of("Abaixo do foco mínimo (5 min) — não salvo."), t.toasts);
        eq("abaixo do mínimo: não grava", 0, t.sessions.size());
        eq("abaixo do mínimo: guarda o tempo", true, t.flow.sw.hasTime());

        t.cfg.minFocusMin = 1;
        t.toasts.clear();
        t.flow.pendingSubFoco = "Listas";
        t.flow.focusFinish();   // pela faixa: pausa e salva
        eq("gravou", 1, t.sessions.size());
        eq("minutos arredondados", 4, t.ultima().getMinutes());
        eq("tipo", "cronometro", t.ultima().getType());
        eq("sub-foco pendente vai junto", "Listas", t.ultima().getKind());
        eq("total da área", 4, t.data.get("Calculo").getMinutes());
        eq("toast", List.of("4 min salvo em Calculo"), t.toasts);
        eq("zerou", false, t.flow.sessionInProgress());
        eq("parou", false, t.flow.sw.isRunning());
        eq("pendente limpo", null, t.flow.pendingSubFoco);
    }

    static void pomodoroComCiclo() {
        SessionFlowTest t = new SessionFlowTest();
        t.cfg.pomoAutoCycle = true;
        t.area = null;
        t.flow.startCountdown(true);
        eq("pomodoro sem área: aviso", List.of("Selecione uma matéria!"), t.toasts);
        eq("pomodoro sem área: parado", false, t.flow.pomodoroActive());

        t.area = "Calculo";
        t.toasts.clear();
        t.flow.startCountdown(true, 1);
        eq("foco rodando", true, t.flow.pomo.isRunning());
        eq("é foco", true, t.flow.pomo.isFocus());
        eq("tique ligado", true, t.flow.countdownTimer.isRunning());
        eq("em andamento", true, t.flow.sessionInProgress());

        tique(t.flow.countdownTimer, 60);
        eq("foco gravado", 1, t.sessions.size());
        eq("tipo pomodoro", "pomodoro", t.ultima().getType());
        eq("minutos do bloco", 1, t.ultima().getMinutes());
        eq("conta os pomodoros", true, t.chamadas.contains("pomoCount"));
        eq("aviso da pausa", List.of("Foco concluído — pausa."), t.toasts);
        eq("pausa começou sozinha", true, t.flow.pomodoroBreak());
        eq("pausa = minutos do cartão", "05:00", t.flow.pomo.remainingText());

        t.toasts.clear();
        tique(t.flow.countdownTimer, 5 * 60);
        eq("aviso da volta", List.of("Pausa concluída — de volta ao foco."), t.toasts);
        eq("foco recomeçou", true, t.flow.pomo.isFocus() && t.flow.pomo.isRunning());
        eq("foco = minutos do cartão", "25:00", t.flow.pomo.remainingText());
        eq("nada gravado pela pausa", 1, t.sessions.size());

        // pausa acaba sem área: não recomeça, fica parado
        t.flow.stopCountdown(false);
        t.flow.startCountdown(false, 1);
        t.area = null;
        tique(t.flow.countdownTimer, 60);
        eq("sem área: não recomeça", false, t.flow.pomodoroActive());
        eq("sem área: tique parado", false, t.flow.countdownTimer.isRunning());

        // interrompido
        t.area = "Calculo";
        t.flow.startCountdown(true, 10);
        tique(t.flow.countdownTimer, 100);
        t.flow.stopCountdown(false);
        eq("interrompido: parado", false, t.flow.pomodoroActive());
        eq("interrompido: não grava", 1, t.sessions.size());
        eq("interrompido: sem sessão", false, t.flow.sessionInProgress());
    }

    static void pausaLonga() {
        SessionFlowTest t = new SessionFlowTest();
        t.cfg.pomoAutoCycle = true;
        for (int i = 1; i <= 4; i++) {
            t.flow.startCountdown(true, 1);
            tique(t.flow.countdownTimer, 60);
        }
        eq("4 focos gravados", 4, t.sessions.size());
        eq("4º foco: pausa longa", "4 focos! Pausa longa de 15 min.", t.toasts.get(t.toasts.size() - 1));
        eq("pausa longa rodando", "15:00", t.flow.pomo.remainingText());
    }

    static void pomodoroSemCiclo() {
        SessionFlowTest t = new SessionFlowTest();
        t.flow.startCountdown(true, 1);
        tique(t.flow.countdownTimer, 60);
        eq("sem ciclo: grava", 1, t.sessions.size());
        eq("sem ciclo: para", false, t.flow.pomodoroActive());
        eq("sem ciclo: sem toast", List.of(), t.toasts);
        eq("sem ciclo: sem diálogo de pausa", false, t.chamadas.stream().anyMatch(c -> c.startsWith("info")));

        t.flow.startCountdown(false, 1);
        tique(t.flow.countdownTimer, 60);
        eq("fim da pausa: avisa", true, t.chamadas.contains("info Pausa concluída: Pronto para mais uma sessão?"));
        eq("fim da pausa: parado", false, t.flow.pomodoroActive());
    }

    static void pausarPomodoro() {
        SessionFlowTest t = new SessionFlowTest();
        t.flow.startCountdown(true, 25);
        tique(t.flow.countdownTimer, 60);
        t.flow.togglePauseCountdown();
        eq("pausou", true, t.flow.pomo.isPaused());
        eq("pausou: tique parado", false, t.flow.countdownTimer.isRunning());
        eq("pausado ainda é sessão", true, t.flow.sessionInProgress());
        t.flow.focusTogglePause();   // a faixa pausa/retoma o Pomodoro quando ele está ativo
        eq("retomou", false, t.flow.pomo.isPaused());
        eq("retomou: tique ligado", true, t.flow.countdownTimer.isRunning());
        eq("resta", "24:00", t.flow.pomo.remainingText());
    }

    static void manual() {
        SessionFlowTest t = new SessionFlowTest();
        t.area = "Treino";
        texto(t.card, "hoursField", "");
        texto(t.card, "minutesField", "");
        t.flow.addManualTime();
        texto(t.card, "hoursField", "x");
        t.flow.addManualTime();
        eq("erros", List.of("Insira um tempo válido.", "Apenas números, por favor."), t.toasts);

        t.toasts.clear();
        texto(t.card, "hoursField", "1");
        texto(t.card, "minutesField", "5");
        t.flow.addManualTime();
        eq("hoje: grava", 65, t.ultima().getMinutes());
        eq("hoje: tipo", "manual", t.ultima().getType());
        eq("hoje: toast", List.of("1h 5min adicionado a Treino"), t.toasts);
        eq("hoje: limpa", "", ((JTextField) campo(t.card, "minutesField")).getText());

        t.toasts.clear();
        ((JComboBox<?>) campo(t.card, "manualDayCombo")).setSelectedIndex(1);   // ontem
        texto(t.card, "minutesField", "40");
        t.flow.addManualTime();
        LocalDate ontem = LocalDate.now().minusDays(1);
        StudySession s = t.sessions.get(0);
        eq("ontem: vai antes na lista", 40, s.getMinutes());
        eq("ontem: meio-dia", ontem.atTime(12, 0),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(s.getTimestamp()), ZoneId.systemDefault()));
        eq("ontem: toast", List.of("40 min adicionado a Treino (ontem)"), t.toasts);
        eq("ontem: volta para hoje", LocalDate.now(), t.card.manualDay());
        eq("total da área", 105, t.data.get("Treino").getMinutes());

        t.toasts.clear();
        t.area = null;
        t.flow.addManualTime();
        eq("sem área", List.of("Selecione uma matéria!"), t.toasts);
    }

    static void modoFoco() {
        SessionFlowTest t = new SessionFlowTest();
        t.flow.enterFocusMode(true);
        eq("sem sessão: não entra", null, t.flow.focusBar);

        t.cfg.focusMode = true;
        t.flow.startStopwatch();
        eq("nova sessão: entra", true, t.flow.focusBar != null);
        eq("esconde a janela", true, t.chamadas.contains("hide"));
        eq("diálogos vão para a faixa", t.flow.focusBar, t.flow.dialogOwner());

        t.flow.focusTogglePause();
        eq("faixa pausa o cronômetro", false, t.flow.sw.isRunning());
        t.flow.focusTogglePause();
        eq("faixa retoma", true, t.flow.sw.isRunning());

        t.chamadas.clear();
        t.flow.exitFocusMode();
        eq("expandir: sem faixa", null, t.flow.focusBar);
        eq("expandir: mostra a janela", List.of("show", "now"), t.chamadas);
        eq("expandir: sessão segue", true, t.flow.sw.isRunning());
        eq("diálogos de volta à janela", t.janela, t.flow.dialogOwner());

        t.flow.stopStopwatch();
        t.flow.startStopwatch();
        eq("retomar não reabre a faixa", null, t.flow.focusBar);

        t.cfg.minFocusMin = 5;
        t.flow.enterFocusMode(true);
        t.flow.focusFinish();   // abaixo do mínimo: não salva, mas fica pausado
        eq("terminar abaixo do mínimo: pausa", false, t.flow.sw.isRunning());
        eq("terminar abaixo do mínimo: faixa fica", true, t.flow.focusBar != null);
        t.cfg.minFocusMin = 1;
        t.flow.startStopwatch();
        t.chamadas.clear();
        tique(t.flow.stopwatch, 120);
        t.flow.focusFinish();
        eq("terminar pela faixa: grava", 1, t.sessions.size());
        eq("terminar pela faixa: sai do modo foco", null, t.flow.focusBar);
        eq("terminar pela faixa: mostra a janela", true, t.chamadas.contains("show"));

        // ciclo automático na faixa: o foco acaba, a pausa começa e a faixa continua
        t.cfg.pomoAutoCycle = true;
        t.flow.startCountdown(true, 1);
        t.flow.enterFocusMode(false);
        tique(t.flow.countdownTimer, 60);
        eq("ciclo: faixa continua", true, t.flow.focusBar != null);
        eq("ciclo: pausa na faixa", true, t.flow.pomodoroBreak());
        t.flow.focusFinish();   // encerrar a pausa: sem perguntar
        eq("encerrar a pausa: sai da faixa", null, t.flow.focusBar);
        eq("encerrar a pausa: parado", false, t.flow.pomodoroActive());
        t.janela.dispose();
    }

    static void trocaDeTela() {
        SessionFlowTest t = new SessionFlowTest();
        t.flow.startStopwatch();
        Timer velho = t.flow.stopwatch;
        t.flow.stopTicks();
        eq("para o tique antigo", false, velho.isRunning());
        t.flow.restartTicks();
        eq("tique novo", true, t.flow.stopwatch != velho);
        eq("tique novo ligado (sessão rodando)", true, t.flow.stopwatch.isRunning());
        eq("pomodoro parado continua parado", false, t.flow.countdownTimer.isRunning());
        tique(t.flow.stopwatch, 7);
        eq("o tique novo conta", "00:00:07", t.flow.sw.elapsedText());
        t.flow.stopTicks();
    }

    /** Fora do EDT: o aviso de pausa entre blocos vem 2,6 s depois de salvar. */
    static void avisoDePausa() throws Exception {
        SessionFlowTest[] t = {null};
        SwingUtilities.invokeAndWait(() -> {
            t[0] = new SessionFlowTest();
            t[0].cfg.transitionMin = 5;
            t[0].flow.startStopwatch();
            tique(t[0].flow.stopwatch, 60);
            t[0].flow.submitStopwatchTime();
        });
        Thread.sleep(3200);
        SwingUtilities.invokeAndWait(() -> eq("aviso de pausa", List.of("1 min salvo em Calculo",
                "Bloco salvo. Que tal 5 min de pausa? Levanta, bebe água."), t[0].toasts));
    }
}
