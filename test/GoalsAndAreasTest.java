import java.awt.Color;
import java.time.*;
import java.util.*;

/** Testes das regras dos formulários de meta (GoalForm) e das operações de área (Areas). */
public class GoalsAndAreasTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    static Object erroDe(Runnable r) {
        try { r.run(); return "sem erro"; } catch (IllegalArgumentException e) { return "IllegalArgumentException"; }
    }

    // ── estado "do app" que o Areas manipula ────────────────────────────────
    Map<String, StudyData> studyDataMap = new LinkedHashMap<>();
    List<StudySession> sessions = new ArrayList<>();
    List<ChecklistItem> checklist = new ArrayList<>();
    Map<String, Integer> goals = new LinkedHashMap<>(), goalsMin = new LinkedHashMap<>(),
                         goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>(),
                         streakBest = new LinkedHashMap<>();
    Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    Map<String, LocalDate> examDate = new LinkedHashMap<>();
    Set<String> archived = new LinkedHashSet<>();
    Map<String, String> areaType = new LinkedHashMap<>();
    Map<String, List<String>> subFocos = new LinkedHashMap<>();
    Map<String, List<String>> weekPlan = new LinkedHashMap<>();

    Areas areas() {
        return new Areas(studyDataMap, sessions, checklist, goals, goalsMin, goalsWeek, goalsMonth,
                streakBest, goalDays, examDate, archived, areaType, subFocos, weekPlan);
    }

    /** Uma área com dado em TODAS as coleções. */
    void areaCompleta(String a) {
        studyDataMap.put(a, new StudyData(100, Color.RED));
        sessions.add(new StudySession(a, 30, 1000L, "manual", "nota", "cap 1", 2));
        checklist.add(new ChecklistItem(a, "Lista 1", 20, false));
        goals.put(a, 60); goalsMin.put(a, 20); goalsWeek.put(a, 300); goalsMonth.put(a, 1200);
        streakBest.put(a, 9);
        goalDays.put(a, EnumSet.of(DayOfWeek.MONDAY));
        examDate.put(a, LocalDate.of(2026, 12, 1));
        areaType.put(a, "fisico");
        subFocos.put(a, new ArrayList<>(Arrays.asList("Peito", "Costas")));
        weekPlan.computeIfAbsent("1_m", k -> new ArrayList<>()).add(a);
    }

    public static void main(String[] args) {
        // ── GoalForm: durações ──────────────────────────────────────────────
        eq("90", 90, GoalForm.parseDuration("90"));
        eq("90m", 90, GoalForm.parseDuration("90m"));
        eq("90 min", 90, GoalForm.parseDuration("90 min"));
        eq("1h", 60, GoalForm.parseDuration("1h"));
        eq("1h30", 90, GoalForm.parseDuration("1h30"));
        eq("1h30m", 90, GoalForm.parseDuration("1h30m"));
        eq("1:30", 90, GoalForm.parseDuration("1:30"));
        eq("1,5h", 90, GoalForm.parseDuration("1,5h"));
        eq("2 h", 120, GoalForm.parseDuration("2 h"));
        eq("1.5 (minutos)", 2, GoalForm.parseDuration("1.5"));
        eq("vazio", 0, GoalForm.parseDuration("  "));
        eq("null", 0, GoalForm.parseDuration(null));
        eq("negativo vira 0", 0, GoalForm.parseDuration("-30"));
        eq("texto inválido", "IllegalArgumentException", erroDe(() -> GoalForm.parseDuration("abc")));
        eq("hora inválida", "IllegalArgumentException", erroDe(() -> GoalForm.parseDuration("xh")));

        // ── GoalForm: derivadas ─────────────────────────────────────────────
        eq("semana = diária × dias", 150, GoalForm.weekFromDaily(30, 5));
        eq("mês = semana × 30/7", 643, GoalForm.monthFromWeek(150));
        eq("nº de dias: nenhum marcado conta 7", 7, GoalForm.daysCount(new boolean[7]));
        eq("nº de dias: 3", 3, GoalForm.daysCount(new boolean[]{true, false, true, false, true, false, false}));
        eq("dias: nenhum ⇒ todos (null)", null, GoalForm.daysFrom(new boolean[7]));
        boolean[] todos = new boolean[7]; Arrays.fill(todos, true);
        eq("dias: todos ⇒ null", null, GoalForm.daysFrom(todos));
        eq("dias: seg/qua", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                GoalForm.daysFrom(new boolean[]{true, false, true, false, false, false, false}));
        eq("mínimo menor que o ideal fica", 20, GoalForm.effectiveMin(20, 60));
        eq("mínimo igual ao ideal some", 0, GoalForm.effectiveMin(60, 60));
        eq("mínimo maior que o ideal some", 0, GoalForm.effectiveMin(90, 60));
        eq("mínimo sem ideal some", 0, GoalForm.effectiveMin(20, 0));
        eq("melhor semana por dia", 60, GoalForm.bestWeekPerDay(300, 5));
        eq("melhor semana: no mínimo 5", 5, GoalForm.bestWeekPerDay(20, 7));
        eq("melhor semana: 0 dias vale 1", 300, GoalForm.bestWeekPerDay(300, 0));

        // ── GoalForm: data da prova ─────────────────────────────────────────
        eq("prova vazia ⇒ null", null, GoalForm.parseExamDate("  "));
        eq("prova dd/mm/aaaa", LocalDate.of(2026, 12, 1), GoalForm.parseExamDate(" 01/12/2026 "));
        eq("prova inválida", "IllegalArgumentException", erroDe(() -> GoalForm.parseExamDate("2026-12-01")));
        eq("texto da prova", "01/12/2026", GoalForm.examText(LocalDate.of(2026, 12, 1)));
        eq("texto sem prova", "", GoalForm.examText(null));

        // ── Areas: nome ─────────────────────────────────────────────────────
        eq("nome: | vira /", "A/B", Areas.cleanName("  A|B "));
        eq("nome: html neutralizado", "‹html>x", Areas.cleanName("<html>x"));

        // ── Areas: criar ────────────────────────────────────────────────────
        { GoalsAndAreasTest t = new GoalsAndAreasTest(); Areas a = t.areas();
          a.add("Cálculo", "estudo");
          a.add("Academia", "fisico");
          eq("add: ordem", Arrays.asList("Cálculo", "Academia"), new ArrayList<>(t.studyDataMap.keySet()));
          eq("add: tipo estudo não é gravado", false, t.areaType.containsKey("Cálculo"));
          eq("add: outro tipo é gravado", "fisico", t.areaType.get("Academia"));
          eq("add: cores diferentes", false, t.studyDataMap.get("Cálculo").getColor().equals(t.studyDataMap.get("Academia").getColor()));
          eq("add: começa com 0 min", 0, t.studyDataMap.get("Academia").getMinutes());
          eq("lista inicial", Arrays.asList("X", "Y"), a.addInitial(" X , ,Y, X "));
          eq("lista inicial: não duplica", 4, t.studyDataMap.size());
        }

        // ── Areas: ativas, arquivar, apagar ─────────────────────────────────
        { GoalsAndAreasTest t = new GoalsAndAreasTest(); Areas a = t.areas();
          a.add("A", "estudo"); a.add("B", "estudo");
          eq("ativas", Arrays.asList("A", "B"), a.active());
          eq("pode arquivar com 2 ativas", true, a.canArchive("A"));
          t.archived.add("A");
          eq("ativas sem arquivada", Collections.singletonList("B"), a.active());
          eq("não arquiva a última ativa", false, a.canArchive("B"));
          eq("não apaga a última ativa", false, a.canDelete("B"));
          eq("apaga arquivada mesmo com 1 ativa", true, a.canDelete("A"));
        }

        // ── Areas: renomear leva TUDO junto ─────────────────────────────────
        { GoalsAndAreasTest t = new GoalsAndAreasTest();
          t.areaCompleta("Calculo"); t.areaCompleta("Física"); t.areaCompleta("Zeta");
          t.archived.add("Calculo");
          t.weekPlan.get("1_m").add("Outra");
          Areas a = t.areas();
          eq("rename: ok", Areas.Rename.OK, a.rename("Calculo", "Cálculo"));
          eq("rename: mantém a ordem", Arrays.asList("Cálculo", "Física", "Zeta"), new ArrayList<>(t.studyDataMap.keySet()));
          eq("rename: tempo", 100, t.studyDataMap.get("Cálculo").getMinutes());
          eq("rename: meta", 60, t.goals.get("Cálculo"));
          eq("rename: mínimo", 20, t.goalsMin.get("Cálculo"));
          eq("rename: semanal", 300, t.goalsWeek.get("Cálculo"));
          eq("rename: mensal", 1200, t.goalsMonth.get("Cálculo"));
          eq("rename: recorde", 9, t.streakBest.get("Cálculo"));
          eq("rename: dias da meta", EnumSet.of(DayOfWeek.MONDAY), t.goalDays.get("Cálculo"));
          eq("rename: prova", LocalDate.of(2026, 12, 1), t.examDate.get("Cálculo"));
          eq("rename: arquivada continua arquivada", true, t.archived.contains("Cálculo") && !t.archived.contains("Calculo"));
          eq("rename: tipo", "fisico", t.areaType.get("Cálculo"));
          eq("rename: sub-focos", Arrays.asList("Peito", "Costas"), t.subFocos.get("Cálculo"));
          eq("rename: plano da semana", Arrays.asList("Cálculo", "Física", "Zeta", "Outra"), t.weekPlan.get("1_m"));
          eq("rename: tarefa", "Cálculo", t.checklist.get(0).subject);
          eq("rename: sessões vão junto (antes ficavam órfãs)", "Cálculo", t.sessions.get(0).getSubject());
          StudySession s = t.sessions.get(0);
          eq("rename: sessão mantém os outros campos", "30|1000|manual|nota|cap 1|2",
                  s.getMinutes() + "|" + s.getTimestamp() + "|" + s.getType() + "|" + s.getNote() + "|" + s.getKind() + "|" + s.getEnergy());
          eq("rename: outras sessões intactas", "Física", t.sessions.get(1).getSubject());
          for (Map<String, ?> m : Arrays.<Map<String, ?>>asList(t.studyDataMap, t.goals, t.goalsMin, t.goalsWeek,
                  t.goalsMonth, t.streakBest, t.goalDays, t.examDate, t.areaType, t.subFocos))
              eq("rename: nome antigo some de todas as coleções", false, m.containsKey("Calculo"));

          eq("rename: nome que já existe", Areas.Rename.EXISTS, a.rename("Física", "Zeta"));
          eq("rename: mesmo nome", Areas.Rename.UNCHANGED, a.rename("Física", "Física"));
          eq("rename: vazio", Areas.Rename.UNCHANGED, a.rename("Física", ""));
          eq("rename: área inexistente", Areas.Rename.UNCHANGED, a.rename("Nada", "Algo"));
          eq("rename recusado não mexe", Arrays.asList("Cálculo", "Física", "Zeta"), new ArrayList<>(t.studyDataMap.keySet()));
        }

        // ── Areas: apagar tira de TUDO (menos o histórico de sessões) ───────
        { GoalsAndAreasTest t = new GoalsAndAreasTest();
          t.areaCompleta("A"); t.areaCompleta("B");
          t.weekPlan.put("2_t", new ArrayList<>(Collections.singletonList("A")));
          t.archived.add("A");
          t.areas().delete("A");
          for (Map<String, ?> m : Arrays.<Map<String, ?>>asList(t.studyDataMap, t.goals, t.goalsMin, t.goalsWeek,
                  t.goalsMonth, t.streakBest, t.goalDays, t.examDate, t.areaType, t.subFocos))
              eq("delete: some de todas as coleções", false, m.containsKey("A"));
          eq("delete: sai das arquivadas", false, t.archived.contains("A"));
          eq("delete: sai do plano", Collections.singletonList("B"), t.weekPlan.get("1_m"));
          eq("delete: turno que ficou vazio some", false, t.weekPlan.containsKey("2_t"));
          eq("delete: tarefas da área somem", 1, t.checklist.size());
          eq("delete: sessões ficam (comportamento atual)", 2, t.sessions.size());
          eq("delete: a outra área fica", true, t.studyDataMap.containsKey("B"));
        }

        // ── Areas: tipo ─────────────────────────────────────────────────────
        { GoalsAndAreasTest t = new GoalsAndAreasTest(); Areas a = t.areas();
          a.add("A", "lazer");
          a.setType("A", "estudo");
          eq("tipo estudo = padrão (some do mapa)", false, t.areaType.containsKey("A"));
          a.setType("A", "trabalho");
          eq("tipo trabalho", "trabalho", t.areaType.get("A"));
        }

        System.out.println("Metas/Áreas: " + (total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }
}
