import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Operações sobre as áreas (matérias), sem Swing: criar, renomear, mudar o tipo,
 * arquivar e apagar — mexendo em TODAS as coleções que guardam algo por área.
 *
 * Recebe as MESMAS coleções do StudyTracker (não cópias), como o StudyStats.
 * Se uma coleção nova "por área" for criada no StudyTracker, ela precisa entrar
 * aqui em rename() e delete() — os testes em GoalsAndAreasTest conferem cada uma.
 */
final class Areas {

    private final Map<String, StudyData> studyDataMap;
    private final List<StudySession> sessions;
    private final List<ChecklistItem> checklist;
    private final Map<String, Integer> goals, goalsMin, goalsWeek, goalsMonth, streakBest;
    private final Map<String, Set<DayOfWeek>> goalDays;
    private final Map<String, LocalDate> examDate;
    private final Set<String> archived;
    private final Map<String, String> areaType;
    private final Map<String, List<String>> subFocos;
    private final Map<String, List<String>> weekPlan;

    Areas(Map<String, StudyData> studyDataMap, List<StudySession> sessions, List<ChecklistItem> checklist,
          Map<String, Integer> goals, Map<String, Integer> goalsMin, Map<String, Integer> goalsWeek,
          Map<String, Integer> goalsMonth, Map<String, Integer> streakBest,
          Map<String, Set<DayOfWeek>> goalDays, Map<String, LocalDate> examDate, Set<String> archived,
          Map<String, String> areaType, Map<String, List<String>> subFocos, Map<String, List<String>> weekPlan) {
        this.studyDataMap = studyDataMap; this.sessions = sessions; this.checklist = checklist;
        this.goals = goals; this.goalsMin = goalsMin; this.goalsWeek = goalsWeek; this.goalsMonth = goalsMonth;
        this.streakBest = streakBest; this.goalDays = goalDays; this.examDate = examDate;
        this.archived = archived; this.areaType = areaType; this.subFocos = subFocos; this.weekPlan = weekPlan;
    }

    /** Nome digitado ⇒ nome guardado: sem espaços nas pontas, sem '|' (separa áreas no plano), sem HTML. */
    static String cleanName(String raw) {
        return AppTheme.semHtml(raw.replace('|', '/').trim());
    }

    List<String> active() {
        List<String> out = new ArrayList<>();
        for (String s : studyDataMap.keySet()) if (!archived.contains(s)) out.add(s);
        return out;
    }

    /** Sempre sobra ao menos uma área ativa. */
    boolean canArchive(String s) { return active().size() > 1; }

    /** Arquivada pode sempre; ativa só se não for a última. */
    boolean canDelete(String s)  { return archived.contains(s) || active().size() > 1; }

    /** Cria a área (o chamador já checou nome vazio/existente/arquivado). */
    void add(String name, String type) {
        studyDataMap.put(name, new StudyData(0, AppTheme.nextColor(studyDataMap.size())));
        setType(name, type);
    }

    /** Primeiro uso: "Cálculo, Física" ⇒ cria as que faltam. @return as criadas, na ordem. */
    List<String> addInitial(String input) {
        List<String> criadas = new ArrayList<>();
        for (String s : input.split(",")) {
            String t = AppTheme.semHtml(s.trim());
            if (!t.isEmpty() && !studyDataMap.containsKey(t)) {
                studyDataMap.put(t, new StudyData(0, AppTheme.nextColor(studyDataMap.size())));
                criadas.add(t);
            }
        }
        return criadas;
    }

    /** "estudo" é o padrão e não é gravado. */
    void setType(String area, String type) {
        if ("estudo".equals(type)) areaType.remove(area); else areaType.put(area, type);
    }

    enum Rename { OK, UNCHANGED, EXISTS }

    /** Renomeia a área em todas as coleções (mesmo comportamento do editSubjectName antigo). */
    Rename rename(String old, String nw) {
        if (old == null || !studyDataMap.containsKey(old) || nw == null || nw.isEmpty() || nw.equals(old))
            return Rename.UNCHANGED;
        if (studyDataMap.containsKey(nw)) return Rename.EXISTS;

        studyDataMap.put(nw, studyDataMap.remove(old));

        for (Map<String, ?> m : Arrays.<Map<String, ?>>asList(goals, goalsMin, goalsWeek, goalsMonth, streakBest,
                goalDays, examDate, areaType, subFocos))
            move(m, old, nw);
        if (archived.remove(old)) archived.add(nw);
        weekPlan.values().forEach(l -> l.replaceAll(a -> old.equals(a) ? nw : a));
        for (ChecklistItem it : checklist) if (it.subject.equals(old)) it.subject = nw;
        return Rename.OK;
    }

    @SuppressWarnings("unchecked")
    private static <V> void move(Map<String, V> m, String old, String nw) {
        if (m.containsKey(old)) m.put(nw, m.remove(old));
    }

    /**
     * Apaga a área de todas as coleções, inclusive tarefas e plano da semana.
     * As SESSÕES do histórico ficam (comportamento de antes: contam no total geral).
     */
    void delete(String s) {
        studyDataMap.remove(s);
        for (Map<String, ?> m : Arrays.<Map<String, ?>>asList(goals, goalsMin, goalsWeek, goalsMonth, streakBest,
                goalDays, examDate, areaType, subFocos))
            m.remove(s);
        archived.remove(s);
        weekPlan.values().forEach(l -> l.remove(s));
        weekPlan.values().removeIf(List::isEmpty);
        checklist.removeIf(it -> it.subject.equals(s));
    }
}
