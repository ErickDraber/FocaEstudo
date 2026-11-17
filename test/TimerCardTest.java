import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/** Testes do cartão de baixo da tela Hoje (área + meta + abas Cronômetro / Manual / Pomodoro). */
public class TimerCardTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    final List<String> chamadas = new ArrayList<>();

    TimerCard card(boolean autoCycle) {
        return new TimerCard(new TimerCard.Actions() {
            public void areaChanged()          { chamadas.add("areaChanged"); }
            public void goalDialog()           { chamadas.add("goalDialog"); }
            public void startStopwatch()       { chamadas.add("startStopwatch"); }
            public void pauseStopwatch()       { chamadas.add("pauseStopwatch"); }
            public void resetStopwatch()       { chamadas.add("resetStopwatch"); }
            public void submitStopwatch()      { chamadas.add("submitStopwatch"); }
            public void addManual()            { chamadas.add("addManual"); }
            public void startFocus()           { chamadas.add("startFocus"); }
            public void startBreak()           { chamadas.add("startBreak"); }
            public void togglePausePomodoro()  { chamadas.add("togglePausePomodoro"); }
            public void resetPomodoro()        { chamadas.add("resetPomodoro"); }
            public void autoCycleChanged(boolean on) { chamadas.add("autoCycle " + on); }
        }, autoCycle);
    }

    static Object campo(Object alvo, String n) {
        try { Field f = alvo.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(alvo); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    static boolean en(TimerCard c, String f) { return ((JComponent) campo(c, f)).isEnabled(); }
    static String txt(TimerCard c, String f) {
        Object o = campo(c, f);
        return o instanceof JLabel l ? l.getText() : ((AbstractButton) o).getText();
    }
    static Color cor(TimerCard c, String f) { return ((JComponent) campo(c, f)).getForeground(); }

    /** Estado dos botões do cronômetro: Iniciar/Pausar/Zerar/Salvar ligados (1) ou não (0), + combo. */
    static String swBotoes(TimerCard c) {
        return txt(c, "startButton") + " " + b(en(c, "startButton")) + b(en(c, "stopButton"))
             + b(en(c, "resetStopwatchButton")) + b(en(c, "submitButton")) + " combo" + b(c.areaCombo().isEnabled());
    }
    /** Estado do Pomodoro: Foco/Descanso/Pausar/Resetar, texto do Pausar, status, combo. */
    static String pomoBotoes(TimerCard c) {
        return b(en(c, "startFocusButton")) + b(en(c, "startBreakButton")) + b(en(c, "stopCountdownButton"))
             + b(en(c, "resetButton")) + " " + txt(c, "stopCountdownButton") + " | " + txt(c, "statusLabel")
             + " combo" + b(c.areaCombo().isEnabled());
    }
    static String b(boolean x) { return x ? "1" : "0"; }

    static boolean clicar(Container c, String texto) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && texto.equals(b.getText())) { b.doClick(); return true; }
            if (k instanceof Container cc && clicar(cc, texto)) return true;
        }
        return false;
    }

    static List<String> textos(Container c) {
        List<String> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l) out.add(l.getText());
            else if (k instanceof AbstractButton b) out.add("[" + b.getText() + "]");
            if (k instanceof Container cc) out.addAll(textos(cc));
        }
        return out;
    }

    public static void main(String[] args) {
        regras();
        cronometro();
        pomodoro();
        manual();
        resto();
        System.out.println("TimerCard: " + (total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }

    // ── regras sem tela ─────────────────────────────────────────────────────
    static void regras() {
        eq("goalText", "0h40m / 1h30m", TimerCard.goalText(40, 90));
        eq("goalText horas", "2h05m / 1h00m", TimerCard.goalText(125, 60));
        eq("goalPercent", 44, TimerCard.goalPercent(40, 90));
        eq("goalPercent no máximo 100", 100, TimerCard.goalPercent(200, 90));
        eq("parseManual", 95, TimerCard.parseManual(" 1 ", "35"));
        eq("parseManual vazio = 0", 20, TimerCard.parseManual("", " 20 "));
        eq("parseManual tudo vazio", 0, TimerCard.parseManual("  ", ""));
        boolean erro = false;
        try { TimerCard.parseManual("1h", "0"); } catch (NumberFormatException e) { erro = true; }
        eq("parseManual: texto ⇒ NumberFormatException", true, erro);
        eq("pomoCount 0", "", TimerCard.pomoCountText(0));
        eq("pomoCount 1", "1 foco hoje", TimerCard.pomoCountText(1));
        eq("pomoCount 3", "3 focos hoje", TimerCard.pomoCountText(3));
        eq("status foco", "Focando em Cálculo…", TimerCard.runningStatus(true, "Cálculo"));
        eq("status pausa", "Em pausa…", TimerCard.runningStatus(false, "Cálculo"));

        LocalDate hoje = LocalDate.of(2026, 11, 18);   // quarta
        eq("dayLabel hoje", "Hoje", TimerCard.dayLabel(hoje, hoje));
        eq("dayLabel ontem", "Ontem", TimerCard.dayLabel(hoje.minusDays(1), hoje));
        eq("dayLabel semana", "seg 16/11", TimerCard.dayLabel(hoje.minusDays(2), hoje));
        eq("dayLabel outro ano", "qua 31/12/2025", TimerCard.dayLabel(LocalDate.of(2025, 12, 31), hoje));

        DefaultComboBoxModel<TimerCard.DayOption> m = TimerCard.manualDays(null, hoje);
        eq("dias: hoje + 6 + Outra data", 8, m.getSize());
        eq("dias: começa em Hoje", "Hoje", m.getElementAt(0).toString());
        eq("dias: termina em Outra data…", "Outra data…", m.getElementAt(7).toString());
        eq("dias: Outra data sem dia", null, m.getElementAt(7).day);
        eq("dias: selecionado Hoje", "Hoje", m.getSelectedItem().toString());
        m = TimerCard.manualDays(hoje.minusDays(3), hoje);
        eq("dias: dia da lista selecionado", hoje.minusDays(3), ((TimerCard.DayOption) m.getSelectedItem()).day);
        eq("dias: sem item extra", 8, m.getSize());
        m = TimerCard.manualDays(hoje.minusDays(10), hoje);
        eq("dias: dia antigo entra antes de Outra data", 9, m.getSize());
        eq("dias: antigo selecionado e marcado", true, ((TimerCard.DayOption) m.getSelectedItem()).custom);
        eq("dias: rótulo do antigo", "dom 08/11", m.getElementAt(7).toString());
        m = TimerCard.manualDays(hoje.plusDays(2), hoje);
        eq("dias: futuro não entra (fica Hoje)", "Hoje", m.getSelectedItem().toString());
        eq("dias: futuro sem item extra", 8, m.getSize());
    }

    // ── cronômetro ──────────────────────────────────────────────────────────
    static void cronometro() {
        TimerCardTest t = new TimerCardTest();
        TimerCard c = t.card(false);
        eq("início", "Iniciar 1000 combo1", swBotoes(c));
        eq("início: rótulo", "00:00:00", txt(c, "stopwatchLabel"));
        c.stopwatchRunning();
        eq("contando", "Retomar 0110 combo0", swBotoes(c));
        c.stopwatchPaused(true);
        eq("pausado com tempo", "Retomar 1011 combo1", swBotoes(c));
        c.stopwatchPaused(false);
        eq("pausado sem tempo", "Retomar 1000 combo1", swBotoes(c));
        c.stopwatchCleared();
        eq("zerado", "Iniciar 1000 combo1", swBotoes(c));
        c.setStopwatchText("00:02:05"); c.setStopwatchInfo("1 pausa");
        eq("rótulos", "00:02:05 / 1 pausa", txt(c, "stopwatchLabel") + " / " + txt(c, "stopwatchInfoLabel"));

        // sync a partir do modelo (o que acontece ao trocar o tema)
        long[] agora = {1_000_000};
        StopwatchModel sw = new StopwatchModel(() -> agora[0]);
        PomodoroModel pomo = new PomodoroModel();
        TimerCard d = t.card(false);
        d.sync(sw, pomo);
        eq("sync parado", "Iniciar 1000 combo1", swBotoes(d));
        sw.start();
        d.sync(sw, pomo);
        eq("sync contando no 1º segundo: Zerar já ligado", "Retomar 0110 combo0", swBotoes(d));
        sw.tick();
        d.sync(sw, pomo);
        eq("sync contando", "Retomar 0110 combo0", swBotoes(d));
        sw.pause();
        d.sync(sw, pomo);
        eq("sync pausado", "Retomar 1011 combo1", swBotoes(d));

        clicar(c.panel(), "Iniciar"); clicar(c.panel(), "Salvar Tempo");   // Salvar desligado: não chama
        c.stopwatchPaused(true);
        clicar(c.panel(), "Pausar"); clicar(c.panel(), "Zerar"); clicar(c.panel(), "Salvar Tempo");
        eq("botões ⇒ ações", List.of("startStopwatch", "resetStopwatch", "submitStopwatch"), t.chamadas);
    }

    // ── pomodoro ────────────────────────────────────────────────────────────
    static void pomodoro() {
        TimerCardTest t = new TimerCardTest();
        TimerCard c = t.card(true);
        eq("início", "1100 Pausar | Pronto para iniciar combo1", pomoBotoes(c));
        eq("início: 25:00", "25:00", txt(c, "countdownLabel"));
        eq("spinners", "25/5", c.focusMinutes() + "/" + c.breakMinutes());
        c.pomodoroStarted(true, "Cálculo");
        eq("foco", "0011 Pausar | Focando em Cálculo… combo0", pomoBotoes(c));
        eq("foco: verde", AppTheme.SUCCESS, cor(c, "statusLabel"));
        c.pomodoroPaused();
        eq("pausado", "0011 Retomar | Pausado combo0", pomoBotoes(c));
        eq("pausado: amarelo", AppTheme.WARNING, cor(c, "statusLabel"));
        c.pomodoroResumed(true, "Cálculo");
        eq("retomado", "0011 Pausar | Focando em Cálculo… combo0", pomoBotoes(c));
        eq("retomado: a cor não muda (como antes)", AppTheme.WARNING, cor(c, "statusLabel"));
        c.pomodoroStarted(false, "Cálculo");
        eq("pausa do pomodoro", "0011 Pausar | Em pausa… combo0", pomoBotoes(c));
        eq("pausa: cor de destaque", AppTheme.ACCENT, cor(c, "statusLabel"));
        c.pomodoroIdle();
        eq("ocioso", "1100 Pausar | Pronto para iniciar combo1", pomoBotoes(c));
        c.setCountdown("01:00"); c.setPomoCount(2);
        eq("rótulos", "01:00 / 2 focos hoje", txt(c, "countdownLabel") + " / " + txt(c, "pomoCountLabel"));

        // sync
        PomodoroModel pomo = new PomodoroModel();
        StopwatchModel sw = new StopwatchModel();
        TimerCard d = t.card(false);
        d.areaCombo().addItem("Física");
        pomo.start(true, 24); pomo.tick();
        d.sync(sw, pomo);
        eq("sync foco", "0011 Pausar | Focando em Física… combo0", pomoBotoes(d));
        eq("sync foco: tempo", "23:59", txt(d, "countdownLabel"));
        pomo.togglePause();
        d.sync(sw, pomo);
        eq("sync pausado", "0011 Retomar | Pausado combo0", pomoBotoes(d));
        pomo.togglePause(); pomo.halt(); pomo.idle(false, 25); pomo.start(false, 5);
        d.sync(sw, pomo);
        eq("sync pausa", "0011 Pausar | Em pausa… combo0", pomoBotoes(d));

        t.chamadas.clear();
        c.pomodoroIdle();
        clicar(c.panel(), "Foco"); clicar(c.panel(), "Descanso");
        c.pomodoroStarted(true, "X");
        ((AbstractButton) campo(c, "stopCountdownButton")).doClick(); clicar(c.panel(), "Resetar");   // há dois "Pausar"
        ((JCheckBox) campo(c, "autoCycleCheck")).doClick();
        eq("botões ⇒ ações", List.of("startFocus", "startBreak", "togglePausePomodoro", "resetPomodoro", "autoCycle false"),
                t.chamadas);
        c.setAutoCycle(true);
        eq("setAutoCycle", true, ((JCheckBox) campo(c, "autoCycleCheck")).isSelected());
        eq("autoCycle inicial desligado", false, ((JCheckBox) campo(t.card(false), "autoCycleCheck")).isSelected());
    }

    // ── manual ──────────────────────────────────────────────────────────────
    static void manual() {
        TimerCardTest t = new TimerCardTest();
        TimerCard c = t.card(false);
        ((JTextField) campo(c, "hoursField")).setText("1");
        ((JTextField) campo(c, "minutesField")).setText("15");
        eq("manualMinutes", 75, c.manualMinutes());
        eq("manualDay: hoje", LocalDate.now(), c.manualDay());
        @SuppressWarnings("unchecked")
        JComboBox<TimerCard.DayOption> combo = (JComboBox<TimerCard.DayOption>) campo(c, "manualDayCombo");
        combo.setSelectedIndex(1);
        eq("manualDay: ontem", LocalDate.now().minusDays(1), c.manualDay());
        c.clearManual();
        eq("clearManual: campos vazios", "|", ((JTextField) campo(c, "hoursField")).getText() + "|"
                + ((JTextField) campo(c, "minutesField")).getText());
        eq("clearManual: volta para hoje", LocalDate.now(), c.manualDay());
        clicar(c.panel(), "Adicionar Tempo");
        eq("Adicionar Tempo ⇒ ação", List.of("addManual"), t.chamadas);
    }

    // ── área, meta, abas ────────────────────────────────────────────────────
    static void resto() {
        TimerCardTest t = new TimerCardTest();
        TimerCard c = t.card(false);
        JPanel bar = (JPanel) campo(c, "goalBar");
        eq("meta oculta no início", false, bar.isVisible());
        c.showGoal(40, 90, Color.BLUE);
        eq("meta: visível", true, bar.isVisible());
        eq("meta: textos", "0h40m / 1h30m 44%", txt(c, "goalLabel") + " " + txt(c, "goalPercent"));
        eq("meta: % na cor da área", Color.BLUE, cor(c, "goalPercent"));
        c.showGoal(95, 90, Color.BLUE);
        eq("meta batida: verde e 100%", AppTheme.SUCCESS + " 100%", cor(c, "goalPercent") + " " + txt(c, "goalPercent"));
        c.hideGoal();
        eq("meta: oculta de novo", false, bar.isVisible());

        c.areaCombo().addItem("A");   // primeiro item seleciona ⇒ dispara o listener
        c.areaCombo().addItem("B");
        c.areaCombo().setSelectedItem("B");
        clicar(c.panel(), "Meta");
        eq("combo e Meta ⇒ ações", List.of("areaChanged", "areaChanged", "goalDialog"), t.chamadas);

        List<String> todos = textos(c.panel());
        eq("rótulos das abas", true, todos.containsAll(List.of("Área:", "Dia:", "Horas:", "Minutos:", "Foco (min):", "Pausa (min):")));
        CustomTabs tabs = (CustomTabs) campo(c, "tabs");
        c.selectTab(2);
        eq("selectTab", 2, campo(tabs, "selected"));
    }
}
