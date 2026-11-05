import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;

/**
 * Fumaça do cronômetro e do Pomodoro: aciona os métodos internos com o app aberto,
 * disparando os tiques à mão (sem esperar o relógio). Inclui trocar o tema (rebuildUI)
 * no meio da sessão — o bug corrigido em 05/11/2026.
 */
public class FumacaTimers extends Fumaca {

    static void tick(String timerField, int n) throws Exception {
        edt(() -> {
            Timer t = (Timer) get(timerField);
            t.stop();   // determinístico: só os tiques disparados aqui
            for (int i = 0; i < n; i++)
                for (ActionListener l : t.getActionListeners()) l.actionPerformed(new ActionEvent(t, 0, ""));
        });
    }
    static boolean en(String f) { return ((JComponent) get(f)).isEnabled(); }
    static String txt(String f) { Object o = get(f); return o instanceof JLabel ? ((JLabel) o).getText() : ((AbstractButton) o).getText(); }
    static int nSessions() { return ((List<?>) get("sessions")).size(); }

    public static void main(String[] a) throws Exception {
        abrirApp("Teste=0,-16776961\ngoal_Teste=30", null, null);
        int base = nSessions();

        // ── Cronômetro ──
        edt(() -> call("startStopwatch"));
        tick("stopwatch", 125);
        edt(() -> {
            eq("cronômetro: rótulo após 125 tiques", "00:02:05", txt("stopwatchLabel"));
            eq("cronômetro: Pausar ligado", true, en("stopButton"));
            eq("cronômetro: Iniciar desligado", false, en("startButton"));
            eq("cronômetro: combo travado", false, en("subjectComboBox"));
        });
        edt(() -> ((Timer) get("stopwatch")).start());   // volta a "rodar" de verdade antes do rebuild
        edt(() -> call("rebuildUI"));                      // = trocar o tema no meio da sessão
        edt(() -> {
            eq("após rebuild: rótulo mantido", "00:02:05", txt("stopwatchLabel"));
            eq("após rebuild: Pausar ligado", true, en("stopButton"));
            eq("após rebuild: Iniciar desligado", false, en("startButton"));
            eq("após rebuild: texto Retomar", "Retomar", txt("startButton"));
            eq("após rebuild: combo travado", false, en("subjectComboBox"));
            eq("após rebuild: tique novo rodando", true, ((Timer) get("stopwatch")).isRunning());
        });
        edt(() -> call("stopStopwatch"));
        edt(() -> {
            eq("pausado: Salvar ligado", true, en("submitButton"));
            eq("pausado: combo livre", true, en("subjectComboBox"));
            eq("pausado: info com 1 pausa", true, txt("stopwatchInfoLabel").contains("1 pausa"));
        });
        edt(() -> call("submitStopwatchTime"));
        edt(() -> {
            eq("salvou 1 sessão", base + 1, nSessions());
            List<?> ss = (List<?>) get("sessions");
            StudySession s = (StudySession) ss.get(ss.size() - 1);
            eq("sessão: 2 min de cronômetro", "2 cronometro", s.getMinutes() + " " + s.getType());
            eq("cronômetro zerado", "00:00:00", txt("stopwatchLabel"));
            eq("texto Iniciar de novo", "Iniciar", txt("startButton"));
        });

        // ── Pomodoro: foco de 1 min até o fim ──
        edt(() -> call("startCountdown", true, 1));
        edt(() -> {
            eq("pomodoro: 01:00", "01:00", txt("countdownLabel"));
            eq("pomodoro: combo travado", false, en("subjectComboBox"));
        });
        tick("countdownTimer", 60);
        edt(() -> {
            eq("pomodoro: salvou ao terminar", base + 2, nSessions());
            List<?> ss = (List<?>) get("sessions");
            StudySession s = (StudySession) ss.get(ss.size() - 1);
            eq("sessão: 1 min de pomodoro", "1 pomodoro", s.getMinutes() + " " + s.getType());
            eq("pomodoro ocioso: Foco ligado", true, en("startFocusButton"));
            eq("pomodoro ocioso: combo livre", true, en("subjectComboBox"));
        });

        // ── Pomodoro pausado + rebuild ──
        edt(() -> call("startCountdown", true, 25));
        tick("countdownTimer", 61);
        edt(() -> call("togglePauseCountdown"));
        edt(() -> call("rebuildUI"));
        edt(() -> {
            eq("pomodoro pausado após rebuild: botão Retomar", "Retomar", txt("stopCountdownButton"));
            eq("pomodoro pausado após rebuild: status", "Pausado", txt("statusLabel"));
            eq("pomodoro pausado após rebuild: tempo mantido", "23:59", txt("countdownLabel"));
            eq("pomodoro pausado após rebuild: Foco desligado", false, en("startFocusButton"));
            eq("pomodoro pausado: tique não roda", false, ((Timer) get("countdownTimer")).isRunning());
        });
        edt(() -> call("togglePauseCountdown"));
        edt(() -> eq("retomado: tique rodando", true, ((Timer) get("countdownTimer")).isRunning()));
        edt(() -> call("stopCountdown", false));
        edt(() -> {
            eq("parado: não salvou o bloco interrompido", base + 2, nSessions());
            eq("parado: Foco ligado", true, en("startFocusButton"));
            eq("parado: sem sessão em andamento", false, call("sessionInProgress"));
        });

        fim("Fumaça timers");
    }
}
