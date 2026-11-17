import java.awt.Color;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/** Testes do que vai para study_data.properties (StudyDataFile): gravar, ler, ida e volta. */
public class StudyDataFileTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    // ── as coleções "do app" ────────────────────────────────────────────────
    final AppSettings cfg = new AppSettings();
    final Map<String, StudyData> data = new LinkedHashMap<>();
    final Map<String, Integer> goals = new LinkedHashMap<>(), goalsMin = new LinkedHashMap<>(),
            goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>(),
            typeGoalWeek = new LinkedHashMap<>(), streakBest = new LinkedHashMap<>();
    final Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    final Map<String, LocalDate> examDate = new LinkedHashMap<>();
    final Map<String, String> areaType = new LinkedHashMap<>();
    final Map<String, List<String>> subFocos = new LinkedHashMap<>(), weekPlan = new LinkedHashMap<>();
    final Set<String> archived = new LinkedHashSet<>(), hardDays = new LinkedHashSet<>();
    final StudyDataFile f = new StudyDataFile(cfg, data, goals, goalsMin, goalsWeek, goalsMonth, goalDays,
            examDate, areaType, typeGoalWeek, subFocos, weekPlan, archived, hardDays, streakBest);

    static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return p;
    }

    /** Só as chaves que não são ajustes (__xxx__ do AppSettings), ordenadas. */
    static TreeMap<String, String> semAjustes(Properties p) {
        Properties ajustes = new Properties();
        new AppSettings().writeTo(ajustes);
        TreeMap<String, String> m = new TreeMap<>();
        for (String k : p.stringPropertyNames()) if (!ajustes.containsKey(k)) m.put(k, p.getProperty(k));
        return m;
    }

    public static void main(String[] args) {
        gravar();
        omitePadroes();
        ler();
        lerInvalidos();
        idaEVolta();
        limpar();
        System.out.println("StudyDataFile: " + (total - falhas) + "/" + total + " testes passaram");
        System.exit(falhas > 0 ? 1 : 0);
    }

    static void gravar() {
        StudyDataFileTest t = new StudyDataFileTest();
        t.data.put("Calculo", new StudyData(125, new Color(0x112233)));
        t.goals.put("Calculo", 60);
        t.goalsMin.put("Calculo", 30);
        t.goalsWeek.put("Calculo", 300);
        t.goalsMonth.put("Calculo", 1200);
        t.goalDays.put("Calculo", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        t.examDate.put("Calculo", LocalDate.of(2027, 3, 5));
        t.areaType.put("Calculo", "fisico");
        t.typeGoalWeek.put("fisico", 150);
        t.subFocos.put("Calculo", List.of("Listas", "Teoria"));
        t.weekPlan.put("1_m", List.of("Calculo", "Treino"));
        t.archived.add("Velha");
        t.archived.add("Antiga");
        t.hardDays.add("2026-11-10");
        t.hardDays.add("2026-11-12");
        t.streakBest.put("Calculo", 7);
        t.f.unknownProps.put("coisa_nova", "x|y");
        t.f.streakBestGeneral = 9;
        t.f.celebratedStreakMilestone = 7;
        t.cfg.minFocusMin = 4;

        Properties p = t.f.toProperties(true);
        TreeMap<String, String> esperado = new TreeMap<>(Map.ofEntries(
                Map.entry("Calculo", "125," + new Color(0x112233).getRGB()),
                Map.entry("goal_Calculo", "60"),
                Map.entry("goalmin_Calculo", "30"),
                Map.entry("goalw_Calculo", "300"),
                Map.entry("goalm_Calculo", "1200"),
                Map.entry("goaldays_Calculo", "1,3,5"),
                Map.entry("exam_Calculo", "2027-03-05"),
                Map.entry("type_Calculo", "fisico"),
                Map.entry("typegoalw_fisico", "150"),
                Map.entry("subfocos_Calculo", "Listas,Teoria"),
                Map.entry("plan_1_m", "Calculo|Treino"),
                Map.entry("__archived__", "Velha,Antiga"),
                Map.entry("__hard_days__", "2026-11-10,2026-11-12"),
                Map.entry("streakbest_Calculo", "7"),
                Map.entry("coisa_nova", "x|y"),
                Map.entry("__streakbest_general__", "9"),
                Map.entry("__streak_milestone__", "7"),
                Map.entry("__theme__", "dark")));
        eq("todas as chaves", esperado, semAjustes(p));
        eq("ajustes vão junto", "4", p.getProperty("__min_focus__"));
        eq("tema claro", "light", t.f.toProperties(false).getProperty("__theme__"));
    }

    static void omitePadroes() {
        StudyDataFileTest t = new StudyDataFileTest();
        t.data.put("A", new StudyData(0, Color.RED));
        t.areaType.put("A", "estudo");          // padrão: não grava
        t.typeGoalWeek.put("estudo", 0);         // zero: não grava
        t.goalsMin.put("A", 0);
        t.subFocos.put("A", new ArrayList<>());  // vazio: não grava
        t.weekPlan.put("2_t", new ArrayList<>());
        TreeMap<String, String> m = semAjustes(t.f.toProperties(false));
        eq("só a área, recordes e tema", new TreeSet<>(List.of("A", "__streakbest_general__", "__streak_milestone__", "__theme__")),
                m.keySet());
        eq("recorde zero", "0", m.get("__streakbest_general__"));
        eq("vazios: sem __archived__ / __hard_days__", false, m.containsKey("__archived__") || m.containsKey("__hard_days__"));
    }

    static void ler() {
        StudyDataFileTest t = new StudyDataFileTest();
        t.f.fromProperties(props(
                "Calculo", "125,-15584170",
                "Treino", " 30 , -65536 ",
                "goal_Calculo", "60", "goalmin_Calculo", "30", "goalw_Calculo", "300", "goalm_Calculo", "1200",
                "goaldays_Calculo", "1, 3,5,",
                "exam_Calculo", "2027-03-05",
                "type_Treino", "fisico", "typegoalw_fisico", "150",
                "subfocos_Treino", " Perna, ,Costas ",
                "plan_1_m", "Calculo| Treino |Calculo||",
                "__archived__", "Velha, Antiga,",
                "__hard_days__", "2026-11-10,,2026-11-12",
                "streakbest_Calculo", "7",
                "__streakbest_general__", "9", "__streak_milestone__", "7",
                "__theme__", "light",
                "__min_focus__", "4"));
        eq("área: minutos", 125, t.data.get("Calculo").getMinutes());
        eq("área: cor", -15584170, t.data.get("Calculo").getColor().getRGB());
        eq("área com espaços", 30, t.data.get("Treino").getMinutes());
        eq("meta ideal", 60, t.goals.get("Calculo"));
        eq("meta mínima", 30, t.goalsMin.get("Calculo"));
        eq("meta semanal", 300, t.goalsWeek.get("Calculo"));
        eq("meta mensal", 1200, t.goalsMonth.get("Calculo"));
        eq("dias da meta", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), t.goalDays.get("Calculo"));
        eq("prova", LocalDate.of(2027, 3, 5), t.examDate.get("Calculo"));
        eq("tipo", "fisico", t.areaType.get("Treino"));
        eq("meta do tipo", 150, t.typeGoalWeek.get("fisico"));
        eq("sub-focos limpos", List.of("Perna", "Costas"), t.subFocos.get("Treino"));
        eq("plano sem repetir", List.of("Calculo", "Treino"), t.weekPlan.get("1_m"));
        eq("arquivadas", new LinkedHashSet<>(List.of("Velha", "Antiga")), t.archived);
        eq("dias difíceis", Set.of("2026-11-10", "2026-11-12"), t.hardDays);
        eq("recorde da área", 7, t.streakBest.get("Calculo"));
        eq("recorde geral", 9, t.f.streakBestGeneral);
        eq("marco", 7, t.f.celebratedStreakMilestone);
        eq("tema", false, t.f.themeDark);
        eq("ajuste lido", 4, t.cfg.minFocusMin);
        eq("nada desconhecido", Map.of(), t.f.unknownProps);
        eq("áreas: só as 2", Set.of("Calculo", "Treino"), t.data.keySet());

        StudyDataFileTest e = new StudyDataFileTest();
        e.f.fromProperties(props("__theme__", "dark"));
        eq("tema escuro", true, e.f.themeDark);
        StudyDataFileTest n = new StudyDataFileTest();
        n.f.fromProperties(props("A", "1,2"));
        eq("sem tema no arquivo: null", null, n.f.themeDark);
        n.f.fromProperties(props("__theme__", "azul"));
        eq("tema estranho = claro", false, n.f.themeDark);
    }

    static void lerInvalidos() {
        StudyDataFileTest t = new StudyDataFileTest();
        t.f.fromProperties(props(
                "goal_A", "x", "goalw_A", "", "goalm_A", "1.5", "goalmin_A", "-",
                "goaldays_A", "1,2,3,4,5,6,7",   // todos os dias = sem restrição
                "goaldays_B", "1,9",              // dia inválido: ignora a linha toda
                "goaldays_C", "",
                "exam_A", "05/03/2027",
                "typegoalw_t", "?", "streakbest_A", "?",
                "__streakbest_general__", "?", "__streak_milestone__", "?",
                "subfocos_A", " , ", "plan_1_m", "|",
                "Area3", "1,2,3", "AreaTxt", "muito,tempo", "Solta", "abc"));
        eq("metas inválidas ignoradas", true, t.goals.isEmpty() && t.goalsWeek.isEmpty() && t.goalsMonth.isEmpty() && t.goalsMin.isEmpty());
        eq("dias inválidos/7 dias ignorados", Map.of(), t.goalDays);
        eq("prova inválida", Map.of(), t.examDate);
        eq("números inválidos", true, t.typeGoalWeek.isEmpty() && t.streakBest.isEmpty()
                && t.f.streakBestGeneral == 0 && t.f.celebratedStreakMilestone == 0);
        eq("listas vazias não entram", true, t.subFocos.isEmpty() && t.weekPlan.isEmpty());
        eq("sem áreas", Map.of(), t.data);
        eq("linhas que não são área ficam guardadas",
                Map.of("Area3", "1,2,3", "AreaTxt", "muito,tempo", "Solta", "abc"), t.f.unknownProps);
        eq("e voltam ao gravar", "muito,tempo", t.f.toProperties(false).getProperty("AreaTxt"));
    }

    static void idaEVolta() {
        StudyDataFileTest a = new StudyDataFileTest();
        a.f.fromProperties(props(
                "Calculo", "125,-15584170", "Treino", "30,-65536",
                "goal_Calculo", "60", "goalmin_Calculo", "30", "goalw_Calculo", "300", "goalm_Calculo", "1200",
                "goaldays_Calculo", "1,3,5", "exam_Calculo", "2027-03-05",
                "type_Treino", "fisico", "typegoalw_fisico", "150", "subfocos_Treino", "Perna,Costas",
                "plan_1_m", "Calculo|Treino", "__archived__", "Velha", "__hard_days__", "2026-11-10",
                "streakbest_Calculo", "7", "__streakbest_general__", "9", "__streak_milestone__", "7",
                "chave_futura", "valor", "__theme__", "dark", "__min_focus__", "4", "__idle_min__", "10"));
        Properties p1 = a.f.toProperties(true);
        StudyDataFileTest b = new StudyDataFileTest();
        b.f.fromProperties(p1);
        Properties p2 = b.f.toProperties(b.f.themeDark);
        eq("ida e volta: igual", new TreeMap<>(p1), new TreeMap<>(p2));
        eq("ida e volta: chave futura", "valor", p2.getProperty("chave_futura"));
        eq("ida e volta: ajuste", "10", p2.getProperty("__idle_min__"));
    }

    static void limpar() {
        StudyDataFileTest t = new StudyDataFileTest();
        t.f.fromProperties(props("A", "1,2", "goal_A", "5", "goalmin_A", "3", "goalw_A", "9", "goalm_A", "9",
                "goaldays_A", "1", "exam_A", "2027-01-01", "type_A", "x", "typegoalw_x", "4",
                "subfocos_A", "s", "plan_1_m", "A", "__archived__", "B", "__hard_days__", "2026-01-01",
                "streakbest_A", "2", "__streakbest_general__", "3", "__streak_milestone__", "3",
                "lixo", "?", "__theme__", "dark"));
        t.f.clear();
        eq("limpa tudo", true, t.data.isEmpty() && t.goals.isEmpty() && t.goalsMin.isEmpty() && t.goalsWeek.isEmpty()
                && t.goalsMonth.isEmpty() && t.goalDays.isEmpty() && t.examDate.isEmpty() && t.areaType.isEmpty()
                && t.typeGoalWeek.isEmpty() && t.subFocos.isEmpty() && t.weekPlan.isEmpty() && t.archived.isEmpty()
                && t.hardDays.isEmpty() && t.streakBest.isEmpty() && t.f.unknownProps.isEmpty());
        eq("zera recordes", 0, t.f.streakBestGeneral + t.f.celebratedStreakMilestone);
        eq("esquece o tema", null, t.f.themeDark);
    }
}
