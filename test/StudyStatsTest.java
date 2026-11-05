import java.time.*;
import java.util.*;

/**
 * Testes da lógica de progresso (StudyStats) — sem framework, roda com:
 *   test\rodar_testes.bat
 * Data fixa dos testes: quarta-feira, 04/11/2026, 12h (UTC).
 */
public class StudyStatsTest {

    static final ZoneId ZONA = ZoneOffset.UTC;
    static final LocalDate HOJE = LocalDate.of(2026, 11, 4);   // quarta
    static final Clock RELOGIO = Clock.fixed(HOJE.atTime(12, 0).atZone(ZONA).toInstant(), ZONA);

    static int falhas = 0, total = 0;

    // ── estado "do app" que o StudyStats lê ─────────────────────────────────
    List<StudySession> sessions = new ArrayList<>();
    Map<String, Object> studyDataMap = new LinkedHashMap<>();   // só as chaves importam
    Set<String> archived = new LinkedHashSet<>();
    Map<String, Integer> goals = new LinkedHashMap<>(), goalsMin = new LinkedHashMap<>(),
                         goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>();
    Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    Set<String> hardDays = new LinkedHashSet<>();

    StudyStats stats() {
        return new StudyStats(sessions, studyDataMap.keySet(), archived, goals, goalsMin,
                goalsWeek, goalsMonth, goalDays, hardDays, RELOGIO);
    }

    void area(String nome, int metaDiaria) { studyDataMap.put(nome, null); if (metaDiaria > 0) goals.put(nome, metaDiaria); }
    void estudo(String area, int min, LocalDate dia) {
        sessions.add(new StudySession(area, min, dia.atTime(12, 0).atZone(ZONA).toInstant().toEpochMilli(), "manual"));
    }
    static LocalDate d(int diasAtras) { return HOJE.minusDays(diasAtras); }

    // ── mini framework ──────────────────────────────────────────────────────
    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    public static void main(String[] args) {
        // Minutos por dia / intervalo
        { StudyStatsTest t = new StudyStatsTest();
          t.area("A", 30); t.area("B", 0);
          t.estudo("A", 20, d(0)); t.estudo("A", 15, d(0)); t.estudo("B", 10, d(0)); t.estudo("A", 40, d(3));
          StudyStats s = t.stats();
          eq("minutesOnDay A hoje", 35, s.minutesOnDay("A", HOJE));
          eq("minutesOnDay geral hoje", 45, s.minutesOnDay(null, HOJE));
          eq("minutesInRange A 0..3", 75, s.minutesInRange("A", d(3), HOJE));
          eq("minutesInRange A 0..2", 35, s.minutesInRange("A", d(2), HOJE));
          // o StudyStats lê as MESMAS coleções: o que entra depois aparece
          t.estudo("A", 5, d(0));
          eq("coleções vivas", 40, s.minutesOnDay("A", HOJE));
        }

        // Datas
        eq("weekStart (qua→seg)", LocalDate.of(2026, 11, 2), StudyStats.weekStart(HOJE));
        eq("weekStart (seg→seg)", LocalDate.of(2026, 11, 2), StudyStats.weekStart(LocalDate.of(2026, 11, 2)));
        eq("weekStart (dom→seg)", LocalDate.of(2026, 11, 2), StudyStats.weekStart(LocalDate.of(2026, 11, 8)));
        eq("monthStart", LocalDate.of(2026, 11, 1), StudyStats.monthStart(HOJE));

        // Dias de meta e metas diárias
        { StudyStatsTest t = new StudyStatsTest();
          t.area("A", 60); t.area("B", 30); t.area("C", 45); t.area("Z", 0);
          t.goalsMin.put("A", 20); t.goalsMin.put("C", 90);
          t.archived.add("B");
          t.goalDays.put("A", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
          StudyStats s = t.stats();
          eq("isGoalDay geral", true, s.isGoalDay(null, HOJE.plusDays(2)));
          eq("isGoalDay A quarta", true, s.isGoalDay("A", HOJE));
          eq("isGoalDay A quinta", false, s.isGoalDay("A", HOJE.plusDays(1)));
          eq("isGoalDay sem restrição", true, s.isGoalDay("B", HOJE.plusDays(1)));
          eq("dailyGoalFor A", 60, s.dailyGoalFor("A"));
          eq("dailyGoalFor geral (soma tudo, inclusive arquivada)", 135, s.dailyGoalFor(null));
          eq("dailyMinFor A (mínimo definido)", 20, s.dailyMinFor("A"));
          eq("dailyMinFor C (mínimo > ideal ⇒ ideal)", 45, s.dailyMinFor("C"));
          eq("dailyMinFor Z (sem meta)", 0, s.dailyMinFor("Z"));
          eq("dailyMinFor geral (sem arquivadas)", 65, s.dailyMinFor(null));
          eq("dailyDoneGoal A", 20, s.dailyDoneGoal("A"));
          eq("dailyDoneGoal B", 30, s.dailyDoneGoal("B"));
          eq("goalDaysPerWeek A", 2, s.goalDaysPerWeek("A"));
          eq("goalDaysPerWeek B", 7, s.goalDaysPerWeek("B"));
          t.hardDays.add(HOJE.toString());
          eq("isHardDay", true, s.isHardDay(HOJE));
          eq("isHardDay ontem", false, s.isHardDay(d(1)));
        }

        // isDoneToday
        { StudyStatsTest t = new StudyStatsTest();
          t.area("A", 30); t.area("B", 0);
          t.goalsMin.put("A", 10);
          StudyStats s = t.stats();
          eq("isDoneToday sem nada", false, s.isDoneToday("A"));
          t.estudo("A", 10, HOJE);
          eq("isDoneToday bateu mínimo", true, s.isDoneToday("A"));
          eq("isDoneToday sem meta e sem estudo", false, s.isDoneToday("B"));
          t.estudo("B", 1, HOJE);
          eq("isDoneToday sem meta, mexeu hoje", true, s.isDoneToday("B"));
        }

        // Sequência (streak)
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          for (int i = 0; i < 3; i++) t.estudo("A", 30, d(i));
          eq("streak 3 dias seguidos", 3, t.stats().currentStreak("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          for (int i = 1; i <= 3; i++) t.estudo("A", 30, d(i));
          eq("streak: graça (hoje ainda não bateu)", 3, t.stats().currentStreak("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          t.estudo("A", 30, d(0)); t.estudo("A", 30, d(1)); t.estudo("A", 30, d(3)); t.estudo("A", 30, d(4));
          eq("streak: 1 folga em 7 dias é tolerada", 5, t.stats().currentStreak("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          t.estudo("A", 30, d(0)); t.estudo("A", 30, d(3));
          eq("streak: 2 falhas seguidas quebram (e a folga do fim é aparada)", 1, t.stats().currentStreak("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          t.estudo("A", 30, d(0)); t.estudo("A", 30, d(2)); t.hardDays.add(d(1).toString());
          eq("streak: dia difícil não cobra nem quebra", 2, t.stats().currentStreak("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          t.goalDays.put("A", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
          t.estudo("A", 30, d(0)); t.estudo("A", 30, d(2)); t.estudo("A", 30, d(7));
          eq("streak: só conta os dias da meta", 3, t.stats().currentStreak("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30); t.goalsMin.put("A", 10);
          t.estudo("A", 10, d(0)); t.estudo("A", 10, d(1));
          eq("streak: basta o mínimo", 2, t.stats().currentStreak("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 0); t.estudo("A", 30, d(0));
          eq("streak: sem meta ⇒ 0", 0, t.stats().currentStreak("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30); t.estudo("A", 30, d(2));
          eq("streak: ontem e hoje sem estudo ⇒ 0", 0, t.stats().currentStreak("A")); }

        // Marcos
        eq("streakMilestone 6", 0, StudyStats.streakMilestone(6));
        eq("streakMilestone 7", 7, StudyStats.streakMilestone(7));
        eq("streakMilestone 120", 100, StudyStats.streakMilestone(120));
        eq("streakBadge 0", "", StudyStats.streakBadge(3));
        eq("streakBadge 30", "  · marco de 30", StudyStats.streakBadge(45));

        // Metas semanal / mensal / saldo
        { StudyStatsTest t = new StudyStatsTest();
          t.area("A", 30); t.area("B", 20); t.area("C", 10); t.archived.add("C");
          t.goalDays.put("A", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
          t.goalsWeek.put("B", 500);
          StudyStats s = t.stats();
          eq("weeklyGoalFor A derivada", 90, s.weeklyGoalFor("A"));
          eq("weeklyGoalFor B definida manda", 500, s.weeklyGoalFor("B"));
          eq("weeklyGoalFor geral (sem arquivadas)", 590, s.weeklyGoalFor(null));
          eq("scheduledDaysInMonth A (nov/2026: 13 seg/qua/sex)", 13, s.scheduledDaysInMonth("A", HOJE));
          eq("monthlyGoalFor A", 390, s.monthlyGoalFor("A"));
          eq("monthlyGoalFor B (30 dias)", 600, s.monthlyGoalFor("B"));
          eq("scheduledDaysInMonth B fev/2027 (28, não vaza p/ março)", 28, s.scheduledDaysInMonth("B", LocalDate.of(2027, 2, 10)));
          eq("scheduledDaysInMonth B fev/2028 bissexto", 29, s.scheduledDaysInMonth("B", LocalDate.of(2028, 2, 10)));
          eq("scheduledDaysInMonth B dez/2026 (31)", 31, s.scheduledDaysInMonth("B", LocalDate.of(2026, 12, 5)));
          t.goalsMonth.put("B", 1000);
          eq("monthlyGoalFor B definida manda", 1000, s.monthlyGoalFor("B"));
        }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          t.estudo("A", 30, d(2)); t.estudo("A", 60, d(0));      // seg 30, ter 0, qua 60
          eq("weeklyBalance em dia", 0, t.stats().weeklyBalance("A", HOJE));
          t.estudo("A", 15, d(1));
          eq("weeklyBalance positivo", 15, t.stats().weeklyBalance("A", HOJE));
          eq("weeklyBalance sem meta", 0, t.stats().weeklyBalance("X", HOJE)); }

        // Médias e recorde semanal
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          t.estudo("A", 60, d(0)); t.estudo("A", 30, d(1)); t.estudo("A", 300, d(20));
          StudyStats s = t.stats();
          eq("avgDailyMinutes 7 dias", 12, s.avgDailyMinutes("A", 7));
          eq("avgActiveDayMinutes 7 dias", 45, s.avgActiveDayMinutes("A", 7));
          eq("avgActiveDayMinutes sem estudo", 0, s.avgActiveDayMinutes("B", 7));
          eq("bestWeekMinutes", 300, s.bestWeekMinutes("A")); }

        // Sugestão adaptativa de meta
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          eq("adaptive: batendo pouco ⇒ baixa p/ 25", 25, t.stats().adaptiveGoalSuggestion("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          for (int i = 0; i < 14; i++) t.estudo("A", 40, d(i));
          eq("adaptive: sequência ≥7 e média ≥120% ⇒ sobe p/ 35", 35, t.stats().adaptiveGoalSuggestion("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 30);
          for (int i = 0; i < 14; i++) t.estudo("A", 30, d(i));
          eq("adaptive: estável ⇒ 0", 0, t.stats().adaptiveGoalSuggestion("A")); }
        { StudyStatsTest t = new StudyStatsTest(); t.area("A", 0);
          eq("adaptive: sem meta ⇒ 0", 0, t.stats().adaptiveGoalSuggestion("A")); }

        System.out.println((total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }
}
