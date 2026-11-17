import java.time.DayOfWeek;
import java.util.*;
import java.util.function.Function;

/**
 * Regras do plano da semana, sem Swing. O plano é o mapa "<dia 1-7>_<turno m|t|n>" → áreas
 * daquele turno (o MESMO mapa do StudyTracker, não uma cópia; ausente = vazio).
 */
final class WeekPlan {

    private final Map<String, List<String>> plan;
    private final Map<String, StudyData> areas;
    private final Set<String> archived;
    private final Map<String, Set<DayOfWeek>> goalDays;
    private final StudyStats stats;
    private final Function<String, String> typeOf;

    WeekPlan(Map<String, List<String>> plan, Map<String, StudyData> areas, Set<String> archived,
             Map<String, Set<DayOfWeek>> goalDays, StudyStats stats, Function<String, String> typeOf) {
        this.plan = plan; this.areas = areas; this.archived = archived;
        this.goalDays = goalDays; this.stats = stats; this.typeOf = typeOf;
    }

    private List<String> slot(int dow, int turno) {
        return plan.getOrDefault(dow + "_" + PlannerPanel.SLOT[turno], Collections.emptyList());
    }

    /** Áreas planejadas para o dia (1 = seg) e turno (0..2), sem as apagadas/arquivadas. */
    List<String> planned(int dow, int turno) {
        List<String> out = new ArrayList<>();
        for (String a : slot(dow, turno))
            if (areas.containsKey(a) && !archived.contains(a)) out.add(a);
        return out;
    }

    /** O dia tem alguma área planejada (em qualquer turno)? */
    boolean dayIsEmpty(int dow) {
        for (int s = 0; s < 3; s++) if (!planned(dow, s).isEmpty()) return false;
        return true;
    }

    /** A área já está em algum turno do dia? (conta o que está gravado, mesmo arquivada) */
    boolean dayHasArea(int dow, String area) {
        for (int s = 0; s < 3; s++) if (slot(dow, s).contains(area)) return true;
        return false;
    }

    /**
     * Turno para colocar a área no dia: o que ela mais ocupa nos outros dias (rotina previsível);
     * se ainda não tem nenhum, o turno mais vazio daquele dia (empate: o mais cedo).
     */
    int pickSlot(String area, int dow) {
        int[] habito = new int[3];
        for (int d = 1; d <= 7; d++)
            for (int s = 0; s < 3; s++)
                if (slot(d, s).contains(area)) habito[s]++;
        int best = -1;
        for (int s = 0; s < 3; s++) if (habito[s] > 0 && (best < 0 || habito[s] > habito[best])) best = s;
        if (best >= 0) return best;
        int vazio = 0;
        for (int s = 1; s < 3; s++)
            if (slot(dow, s).size() < slot(dow, vazio).size()) vazio = s;
        return vazio;
    }

    /**
     * "Preencher pelas metas": coloca cada área com meta diária em todos os dias da meta (sem dias
     * marcados = todos), uma vez por dia, sem apagar nada. Ordem fixa — tipo, depois nome — porque o
     * mapa de áreas vem do .properties em ordem aleatória. Devolve quantos blocos colocou.
     */
    int autofill() {
        List<String> ordem = new ArrayList<>();
        for (String t : AreaTypes.KEYS) {
            List<String> doTipo = new ArrayList<>();
            for (String a : areas.keySet())
                if (!archived.contains(a) && typeOf.apply(a).equals(t)) doTipo.add(a);
            doTipo.sort(String.CASE_INSENSITIVE_ORDER);
            ordem.addAll(doTipo);
        }
        int colocados = 0;
        for (String a : ordem) {
            if (stats.dailyDoneGoal(a) <= 0) continue;
            Set<DayOfWeek> dias = goalDays.get(a);
            for (int d = 1; d <= 7; d++) {
                if (dias != null && !dias.isEmpty() && !dias.contains(DayOfWeek.of(d))) continue;
                if (dayHasArea(d, a)) continue;
                plan.computeIfAbsent(d + "_" + PlannerPanel.SLOT[pickSlot(a, d)], k -> new ArrayList<>()).add(a);
                colocados++;
            }
        }
        return colocados;
    }
}
