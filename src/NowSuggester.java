import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * O que sugerir no cartão "Agora", sem Swing. Uma sugestão só, na ordem:
 * <ol>
 *   <li>o que está no plano deste turno (a decisão já foi tomada, é só seguir);</li>
 *   <li>meta diária de hoje não batida (prova mais próxima e o que mais falta primeiro);</li>
 *   <li>rodízio: grupo (sub-foco) de uma área de Físico parado há 3+ dias ou nunca treinado;</li>
 *   <li>tipo com meta de equilíbrio zerado na semana;</li>
 *   <li>a área parada há mais tempo.</li>
 * </ol>
 * Áreas arquivadas e as descartadas com "Outra coisa" ({@code skipped}) ficam de fora.
 */
final class NowSuggester {

    /** Área, motivo (vai no título do cartão) e sub-foco (só no rodízio; senão null). */
    record Suggestion(String area, String reason, String subFoco) {}

    /** De onde lê. Quem implementa é o StudyTracker (repasses de uma linha). */
    interface Source {
        Map<String, StudyData> areas();
        Set<String> archived();
        StudyStats stats();
        String typeOf(String area);
        /** Meta diária IDEAL definida na área (0 = sem meta). */
        int idealDaily(String area);
        LocalDate examDate(String area);
        /** Sub-focos da área (pode ser null). */
        List<String> subFocos(String area);
        List<StudySession> sessions();
        /** Meta semanal de equilíbrio do tipo (0 = sem meta). */
        int typeGoalWeek(String type);
        int minutesByType(String type, LocalDate start, LocalDate end);
        /** Áreas planejadas para o dia (ISO 1–7) e turno (índice de PlannerPanel.SLOT). */
        List<String> plannedAreas(int dayOfWeek, int slot);
    }

    private final Source src;

    NowSuggester(Source src) { this.src = src; }

    /** A sugestão para {@code today} no turno {@code slot}, ou null se não houver nenhuma. */
    Suggestion next(LocalDate today, int slot, Set<String> skipped) {
        StudyStats st = src.stats();
        Map<String, StudyData> areas = src.areas();
        Set<String> archived = src.archived();

        // 0. o que a pessoa já planejou para este turno: a decisão já foi tomada, é só seguir
        for (String a : src.plannedAreas(today.getDayOfWeek().getValue(), slot))
            if (!st.isDoneToday(a) && !skipped.contains(a))
                return new Suggestion(a, "está no seu plano " + PlannerPanel.SLOT_PHRASE[slot], null);

        // 1. meta diária de hoje não batida (prioriza prova mais próxima e o que falta)
        String best = null; long bestScore = Long.MIN_VALUE; String reason = null;
        for (String s : areas.keySet()) {
            if (archived.contains(s) || skipped.contains(s)) continue;
            int goal = src.idealDaily(s);
            if (goal <= 0 || !st.isGoalDay(s, today)) continue;
            int done = st.minutesOnDay(s, today);
            if (done >= goal) continue;
            long score = goal - done;
            LocalDate exam = src.examDate(s);
            String extra = "";
            if (exam != null) {
                long d = ChronoUnit.DAYS.between(today, exam);
                if (d >= 0) { score += (120 - Math.min(110, d)) * 3L; extra = " · prova em " + d + "d"; }
            }
            if (score > bestScore) {
                bestScore = score; best = s;
                reason = "faltam " + ScreenKit.fmtHM(goal - done) + " pra meta de hoje" + extra;
            }
        }
        if (best != null) return new Suggestion(best, reason, null);

        // 1b. rodízio: área de Físico com grupo (sub-foco) parado há mais tempo
        String rArea = null, rSf = null; long oldestSf = Long.MAX_VALUE;
        for (String s : areas.keySet()) {
            if (archived.contains(s) || skipped.contains(s) || !"fisico".equals(src.typeOf(s))) continue;
            List<String> grupos = src.subFocos(s);
            if (grupos == null) continue;
            for (String grupo : grupos) {
                long last = 0;
                for (StudySession se : src.sessions())
                    if (se.getSubject().equals(s) && grupo.equalsIgnoreCase(se.getKind()))
                        last = Math.max(last, se.getTimestamp());
                if (last < oldestSf) { oldestSf = last; rArea = s; rSf = grupo; }
            }
        }
        if (rArea != null) {
            long dias = oldestSf == 0 ? -1 : ChronoUnit.DAYS.between(st.dateOf(oldestSf), today);
            if (oldestSf == 0 || dias >= 3)
                return new Suggestion(rArea,
                        oldestSf == 0 ? "treinar " + rSf + " — ainda não treinou" : "treinar " + rSf + " — faz " + dias + " dias",
                        rSf);
        }

        // 2. tipo com meta de equilíbrio zerada na semana
        LocalDate ws = StudyStats.weekStart(today);
        for (String t : AreaTypes.KEYS) {
            if (src.typeGoalWeek(t) <= 0) continue;
            if (src.minutesByType(t, ws, today) > 0) continue;
            for (String s : areas.keySet())
                if (!archived.contains(s) && !skipped.contains(s) && src.typeOf(s).equals(t))
                    return new Suggestion(s, "sua semana está sem " + AreaTypes.label(t).toLowerCase(), null);
        }

        // 3. área sem atividade há mais tempo
        String stale = null; long oldest = Long.MAX_VALUE;
        for (String s : areas.keySet()) {
            if (archived.contains(s) || skipped.contains(s)) continue;
            long last = 0;
            for (StudySession se : src.sessions()) if (se.getSubject().equals(s)) last = Math.max(last, se.getTimestamp());
            if (last < oldest) { oldest = last; stale = s; }
        }
        if (stale != null) {
            long dias = oldest == 0 ? -1 : ChronoUnit.DAYS.between(st.dateOf(oldest), today);
            return new Suggestion(stale, dias < 0 ? "ainda sem nenhuma sessão registrada"
                    : dias == 0 ? "já mexeu nisso hoje" : "faz " + dias + " dia(s) sem isso", null);
        }
        return null;
    }

    /** "40m de 1h30 hoje" (ou "mínimo de hoje feito · …"); null se a área não tem meta hoje. */
    String progressText(String area, LocalDate today) {
        StudyStats st = src.stats();
        int ideal = src.idealDaily(area);
        if (ideal <= 0 || !st.isGoalDay(area, today)) return null;
        int feito = st.minutesOnDay(area, today);
        if (feito <= 0) return "meta de hoje: " + ScreenKit.fmtHM(ideal);
        if (feito >= st.dailyMinFor(area))
            return "mínimo de hoje feito · " + ScreenKit.fmtHM(feito) + " de " + ScreenKit.fmtHM(ideal);
        return ScreenKit.fmtHM(feito) + " de " + ScreenKit.fmtHM(ideal) + " hoje";
    }
}
