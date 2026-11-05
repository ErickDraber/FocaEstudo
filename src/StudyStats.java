import java.time.*;
import java.util.*;

/**
 * Cálculos de progresso: minutos por dia/semana/mês, metas, sequências (streaks)
 * e sugestão adaptativa de meta. Só LÊ o estado — não grava, não mexe em tela.
 *
 * Recebe as MESMAS coleções do StudyTracker (não cópias). Como lá todas são
 * {@code final} e só mudam por put/remove/clear (inclusive no reloadFromDisk),
 * esta classe sempre enxerga o estado atual sem precisar ser avisada.
 *
 * O {@link Clock} existe para os testes fixarem "hoje"; no app é o relógio do sistema.
 */
final class StudyStats {

    private final List<StudySession> sessions;
    private final Collection<String> subjects;            // chaves do studyDataMap (visão viva)
    private final Set<String> archived;
    private final Map<String, Integer> goals;             // diária IDEAL
    private final Map<String, Integer> goalsMin;          // diária MÍNIMA (ausente = ideal)
    private final Map<String, Integer> goalsWeek;
    private final Map<String, Integer> goalsMonth;
    private final Map<String, Set<DayOfWeek>> goalDays;   // ausente/vazio = todos os dias
    private final Set<String> hardDays;                   // ISO date: dia difícil não cobra nem quebra
    private final Clock clock;

    StudyStats(List<StudySession> sessions, Collection<String> subjects, Set<String> archived,
               Map<String, Integer> goals, Map<String, Integer> goalsMin,
               Map<String, Integer> goalsWeek, Map<String, Integer> goalsMonth,
               Map<String, Set<DayOfWeek>> goalDays, Set<String> hardDays, Clock clock) {
        this.sessions = sessions;   this.subjects = subjects;   this.archived = archived;
        this.goals = goals;         this.goalsMin = goalsMin;
        this.goalsWeek = goalsWeek; this.goalsMonth = goalsMonth;
        this.goalDays = goalDays;   this.hardDays = hardDays;
        this.clock = clock;
    }

    LocalDate today() { return LocalDate.now(clock); }

    // ── DATAS ───────────────────────────────────────────────────────────────

    /** Converte um timestamp em LocalDate no fuso do relógio. */
    LocalDate dateOf(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(clock.getZone()).toLocalDate();
    }

    /** Segunda-feira da semana de 'date' (semana começa na segunda). */
    static LocalDate weekStart(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1);
    }

    /** Primeiro dia do mês de 'date'. */
    static LocalDate monthStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    // ── MINUTOS (derivados das sessões) ─────────────────────────────────────

    /** Minutos estudados num dia. subject == null ⇒ geral (todas as matérias). */
    int minutesOnDay(String subject, LocalDate day) {
        int total = 0;
        for (StudySession s : sessions) {
            if (subject != null && !s.getSubject().equals(subject)) continue;
            if (dateOf(s.getTimestamp()).equals(day)) total += s.getMinutes();
        }
        return total;
    }

    /** Minutos num intervalo [start, end] inclusivo. subject == null ⇒ geral. */
    int minutesInRange(String subject, LocalDate start, LocalDate end) {
        int total = 0;
        for (StudySession s : sessions) {
            if (subject != null && !s.getSubject().equals(subject)) continue;
            LocalDate d = dateOf(s.getTimestamp());
            if (!d.isBefore(start) && !d.isAfter(end)) total += s.getMinutes();
        }
        return total;
    }

    /** Média diária de estudo (min/dia) nos últimos {@code days} dias corridos. */
    int avgDailyMinutes(String subject, int days) {
        LocalDate today = today();
        return minutesInRange(subject, today.minusDays(days - 1), today) / days;
    }

    /** Média por dia efetivamente estudado nos últimos {@code days} dias. */
    int avgActiveDayMinutes(String subject, int days) {
        LocalDate today = today();
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
    int bestWeekMinutes(String subject) {
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

    // ── METAS ───────────────────────────────────────────────────────────────

    /** A meta diária vale nesse dia? (GERAL e matérias sem configuração ⇒ todo dia). */
    boolean isGoalDay(String subject, LocalDate date) {
        if (subject == null) return true;
        Set<DayOfWeek> days = goalDays.get(subject);
        return days == null || days.isEmpty() || days.contains(date.getDayOfWeek());
    }

    boolean isHardDay(LocalDate d) { return hardDays.contains(d.toString()); }

    /** Quantos dias por semana a meta diária vale (7 se não houver restrição). */
    int goalDaysPerWeek(String subject) {
        Set<DayOfWeek> d = subject == null ? null : goalDays.get(subject);
        return d == null || d.isEmpty() ? 7 : d.size();
    }

    /** Dias agendados (que exigem meta) dentro do mês de 'ref'. */
    int scheduledDaysInMonth(String subject, LocalDate ref) {
        int n = 0;
        LocalDate d = ref.withDayOfMonth(1);
        for (int i = 0; i < d.lengthOfMonth(); i++, d = d.plusDays(1))
            if (isGoalDay(subject, d)) n++;
        return n;
    }

    /** Meta diária. subject == null ⇒ geral (soma das metas diárias de todas). */
    int dailyGoalFor(String subject) {
        if (subject == null) {
            int sum = 0;
            for (int g : goals.values()) sum += g;
            return sum;
        }
        return goals.getOrDefault(subject, 0);
    }

    /** Meta diária MÍNIMA (bater isso já mantém a sequência). Ausente ⇒ igual ao ideal. */
    int dailyMinFor(String subject) {
        if (subject == null) {
            int sum = 0;
            for (String s : subjects)
                if (!archived.contains(s) && goals.getOrDefault(s, 0) > 0) sum += dailyMinFor(s);
            return sum;
        }
        int ideal = goals.getOrDefault(subject, 0);
        if (ideal <= 0) return 0;
        return Math.max(1, Math.min(ideal, goalsMin.getOrDefault(subject, ideal)));
    }

    /** Meta que conta como "feito hoje": o mínimo, se houver; senão o ideal. */
    int dailyDoneGoal(String area) {
        int min = goalsMin.getOrDefault(area, 0);
        return min > 0 ? min : goals.getOrDefault(area, 0);
    }

    /** Feito hoje = bateu o mínimo do dia; sem meta, basta ter mexido nisso hoje. */
    boolean isDoneToday(String area) {
        int feito = minutesOnDay(area, today()), meta = dailyDoneGoal(area);
        return meta > 0 ? feito >= meta : feito > 0;
    }

    /**
     * Meta semanal. Se o usuário definiu um valor, ele manda. Senão, deriva de
     * meta_diária × dias/semana em que a meta vale (assim mudar os dias muda a meta).
     */
    int weeklyGoalFor(String subject) {
        if (subject == null) {
            int s = 0;
            for (String k : subjects) if (!archived.contains(k)) s += weeklyGoalFor(k);
            return s;
        }
        int stored = goalsWeek.getOrDefault(subject, 0);
        if (stored > 0) return stored;
        int daily = goals.getOrDefault(subject, 0);
        return daily > 0 ? daily * goalDaysPerWeek(subject) : 0;
    }

    /** Meta mensal. Definida pelo usuário manda; senão, meta_diária × dias agendados no mês. */
    int monthlyGoalFor(String subject) {
        if (subject == null) {
            int s = 0;
            for (String k : subjects) if (!archived.contains(k)) s += monthlyGoalFor(k);
            return s;
        }
        int stored = goalsMonth.getOrDefault(subject, 0);
        if (stored > 0) return stored;
        int daily = goals.getOrDefault(subject, 0);
        return daily > 0 ? daily * scheduledDaysInMonth(subject, today()) : 0;
    }

    /** Saldo da semana até 'today': minutos feitos − meta diária × dias agendados até aqui. */
    int weeklyBalance(String subject, LocalDate today) {
        int daily = dailyGoalFor(subject);
        if (daily <= 0) return 0;
        int diasAgendados = 0;
        for (LocalDate d = weekStart(today); !d.isAfter(today); d = d.plusDays(1))
            if (isGoalDay(subject, d)) diasAgendados++;
        return minutesInRange(subject, weekStart(today), today) - daily * diasAgendados;
    }

    // ── STREAKS (dias consecutivos batendo a meta diária) ────────────────────

    /**
     * Sequência atual: dias consecutivos (terminando hoje) com minutos ≥ meta diária.
     * Graça: se hoje ainda não bateu, a sequência não quebra — conta-se a partir de ontem.
     * Folga: 1 dia abaixo da meta é tolerado a cada janela de 7 dias da sequência.
     */
    int currentStreak(String subject) {
        int goal = dailyMinFor(subject);   // basta bater o MÍNIMO
        if (goal <= 0) return 0;
        LocalDate cursor = today();
        while (!isGoalDay(subject, cursor) || isHardDay(cursor)) cursor = cursor.minusDays(1);
        if (minutesOnDay(subject, cursor) < goal) {                            // graça: pula p/ o anterior agendado
            do { cursor = cursor.minusDays(1); } while (!isGoalDay(subject, cursor) || isHardDay(cursor));
        }
        if (minutesOnDay(subject, cursor) < goal) return 0;                     // nem esse bateu

        int streak = 0, missesNaJanela = 0;
        ArrayDeque<Boolean> janela = new ArrayDeque<>();                         // últimos 7 dias contados
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
    static int streakMilestone(int streak) {
        int mk = 0;
        for (int m : new int[]{7, 30, 100, 365}) if (streak >= m) mk = m;
        return mk;
    }

    /** Selo do marco para exibir na linha de sequência. */
    static String streakBadge(int streak) {
        int mk = streakMilestone(streak);
        return mk == 0 ? "" : "  · marco de " + mk;
    }

    // ── SUGESTÃO ADAPTATIVA ─────────────────────────────────────────────────

    /**
     * Sugere ajustar a meta diária olhando os últimos 14 dias agendados:
     * sequência ≥ 7 e média ≥ 120% ⇒ sobe ~15%; bateu < 40% dos dias ⇒ baixa ~20%.
     * Devolve 0 quando não há o que sugerir.
     */
    int adaptiveGoalSuggestion(String subject) {
        int goal = goals.getOrDefault(subject, 0);
        if (goal <= 0) return 0;
        LocalDate today = today();
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
}
