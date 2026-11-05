import java.util.*;

/** Testes do estado do cronômetro (StopwatchModel) e do Pomodoro (PomodoroModel) — sem Swing. */
public class TimerModelsTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    static long agora = 1_000_000L;   // relógio falso (ms)

    static void ticks(StopwatchModel sw, int n) { for (int i = 0; i < n; i++) sw.tick(); }

    public static void main(String[] args) {
        // ── CRONÔMETRO ──────────────────────────────────────────────────────
        { StopwatchModel sw = new StopwatchModel(() -> agora);
          eq("novo: parado", false, sw.isRunning());
          eq("novo: sem tempo", false, sw.hasTime());
          eq("novo: não iniciado", false, sw.started());
          eq("novo: info vazia", " ", sw.infoText());
          eq("novo: tempo", "00:00:00", sw.elapsedText());
          sw.tick();
          eq("tick parado não conta", 0, sw.elapsedSeconds());

          eq("1º start = sessão nova", true, sw.start());
          eq("rodando", true, sw.isRunning());
          ticks(sw, 125);
          eq("líquido conta os ticks", 125, sw.elapsedSeconds());
          eq("texto do tempo", "00:02:05", sw.elapsedText());

          sw.pause();
          eq("pausa conta 1", 1, sw.pauseCount());
          sw.pause();
          eq("pausar já pausado não conta de novo", 1, sw.pauseCount());
          ticks(sw, 10);
          eq("pausado não conta", 125, sw.elapsedSeconds());
          eq("retomar não é sessão nova", false, sw.start());

          agora += 3_725_000L;   // 1h02min05s de relógio de parede
          eq("bruto = relógio desde o 1º iniciar", 3725L, sw.grossSeconds());
          eq("info", "líquido  ·  1 pausa  ·  bruto 01:02:05", sw.infoText());
          sw.pause();
          eq("info plural", "líquido  ·  2 pausas  ·  bruto 01:02:05", sw.infoText());

          sw.reset();
          eq("reset: parado", false, sw.isRunning());
          eq("reset: zera líquido", 0, sw.elapsedSeconds());
          eq("reset: zera pausas", 0, sw.pauseCount());
          eq("reset: não iniciado", false, sw.started());
          eq("reset: próximo start é sessão nova", true, sw.start());
        }

        // minutos salvos e foco mínimo
        { StopwatchModel sw = new StopwatchModel(() -> agora);
          sw.start(); ticks(sw, 89);
          eq("89s ⇒ 1 min (arredonda)", 1, sw.roundedMinutes());
          ticks(sw, 1);
          eq("90s ⇒ 2 min", 2, sw.roundedMinutes());
          eq("2 min com mínimo 5 ⇒ abaixo", true, sw.belowMinimum(5));
          eq("2 min com mínimo 2 ⇒ ok", false, sw.belowMinimum(2));
          eq("mínimo 0 vale como 1", false, sw.belowMinimum(0));
          StopwatchModel curto = new StopwatchModel(() -> agora);
          curto.start(); ticks(curto, 20);
          eq("20s com mínimo 0 ⇒ 0 min, abaixo de 1", true, curto.belowMinimum(0));
        }

        // hiperfoco: avisa uma vez por sessão
        { agora = 5_000_000L;   // (0 não serve: é o "ainda não iniciado" do modelo, como no código original)
          StopwatchModel sw = new StopwatchModel(() -> agora);
          sw.start();
          agora += 2 * 3600_000L;
          eq("hiperfoco antes das 3h", false, sw.shouldWarnHyperfocus(3));
          agora += 3600_000L;
          eq("hiperfoco às 3h", true, sw.shouldWarnHyperfocus(3));
          eq("hiperfoco só uma vez", false, sw.shouldWarnHyperfocus(3));
          eq("hiperfoco desligado (0)", false, new StopwatchModel(() -> agora).shouldWarnHyperfocus(0));
          sw.reset(); sw.start();
          agora += 3 * 3600_000L;
          eq("nova sessão avisa de novo", true, sw.shouldWarnHyperfocus(3));
          StopwatchModel nunca = new StopwatchModel(() -> agora);
          eq("não iniciado nunca avisa", false, nunca.shouldWarnHyperfocus(1));
        }

        // inatividade
        eq("idle: desligado", false, StopwatchModel.idleTooLong(10_000_000, 0, 0));
        eq("idle: antes do limite", false, StopwatchModel.idleTooLong(599_999, 0, 10));
        eq("idle: no limite", true, StopwatchModel.idleTooLong(600_000, 0, 10));
        eq("hms", "10:00:59", StopwatchModel.hms(36059));

        // ── POMODORO ────────────────────────────────────────────────────────
        { PomodoroModel p = new PomodoroModel();
          eq("novo: inativo", false, p.isActive());
          p.start(true, 25);
          eq("foco: ativo", true, p.isActive());
          eq("foco: rodando", true, p.isRunning());
          eq("foco: é foco", true, p.isFocus());
          eq("foco: restante", 1500, p.remainingSeconds());
          eq("texto restante", "25:00", p.remainingText());
          boolean acabou = false;
          for (int i = 0; i < 600; i++) acabou = p.tick();
          eq("10 min depois: não acabou", false, acabou);
          eq("minutos já feitos", 10, p.focusMinutesDone());

          eq("pausar ⇒ pausado", true, p.togglePause());
          eq("pausado continua ativo", true, p.isActive());
          eq("pausado não roda", false, p.isRunning());
          eq("tick pausado não conta", false, p.tick());
          eq("restante parado", 900, p.remainingSeconds());
          eq("retomar ⇒ não pausado", false, p.togglePause());

          for (int i = 0; i < 899; i++) acabou = p.tick();
          eq("falta 1s", false, acabou);
          eq("último tick acaba", true, p.tick());
          p.halt();
          eq("halt: inativo", false, p.isActive());
          eq("planejado guardado", 25, p.plannedMinutes());
        }
        { PomodoroModel p = new PomodoroModel();
          p.start(false, 5);
          eq("pausa: não é foco", false, p.isFocus());
          eq("pausa: minutos feitos = 0", 0, p.focusMinutesDone());
          p.start(true, 25);
          eq("start de novo zera pausa", false, p.isPaused());
        }

        // ciclo e pausa longa
        { PomodoroModel p = new PomodoroModel();
          eq("foco nº 1", 1, p.currentFocusNumber());
          for (int i = 1; i <= 3; i++) { p.completeFocus(); eq("sem pausa longa após " + i, false, p.longBreakDue()); }
          p.completeFocus();
          eq("pausa longa após 4", true, p.longBreakDue());
          eq("foco nº 5", 5, p.currentFocusNumber());
          eq("pausa longa: 3× a pausa", 30, PomodoroModel.longBreakMinutes(10));
          eq("pausa longa: no mínimo 15", 15, PomodoroModel.longBreakMinutes(3));

          p.idle(true, 25);
          eq("parada natural mantém o ciclo", 5, p.currentFocusNumber());
          eq("ocioso mostra o foco cheio", 1500, p.remainingSeconds());
          eq("ocioso: inativo", false, p.isActive());
          p.start(true, 25); p.togglePause();
          p.idle(false, 25);
          eq("parar no meio zera o ciclo", 1, p.currentFocusNumber());
          eq("parar no meio tira a pausa", false, p.isPaused());
        }

        System.out.println("Timers: " + (total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }
}
