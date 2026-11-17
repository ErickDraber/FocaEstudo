import java.awt.Color;
import java.time.LocalDate;
import java.util.*;

/**
 * O que vai para study_data.properties: as áreas (minutos + cor), metas, dias de meta, provas,
 * tipos, sub-focos, semana planejada, arquivadas, dias difíceis, recordes de sequência, os
 * ajustes (AppSettings) e o tema. Converte as coleções de/para Properties; ler e gravar o
 * arquivo é do {@link StudyStore}, e as travas (ready/loadOk) continuam no StudyTracker.
 *
 * Mexe nas MESMAS coleções do StudyTracker (não cópias). Chaves que não sabemos ler ficam em
 * {@link #unknownProps} e voltam intactas ao gravar.
 */
final class StudyDataFile {

    private final AppSettings cfg;
    private final Map<String, StudyData> studyDataMap;
    private final Map<String, Integer> goals, goalsMin, goalsWeek, goalsMonth, typeGoalWeek, streakBest;
    private final Map<String, Set<java.time.DayOfWeek>> goalDays;
    private final Map<String, LocalDate> examDate;
    private final Map<String, String> areaType;
    private final Map<String, List<String>> subFocos, weekPlan;
    private final Set<String> archived, hardDays;

    final Map<String, String> unknownProps = new LinkedHashMap<>(); // linhas do save que não soubemos ler
    int streakBestGeneral = 0;            // recorde geral
    int celebratedStreakMilestone = 0;    // maior marco já comemorado
    /** Tema lido do arquivo (null = o arquivo não diz). Quem lê aplica (AppTheme). */
    Boolean themeDark;

    StudyDataFile(AppSettings cfg, Map<String, StudyData> studyDataMap,
                  Map<String, Integer> goals, Map<String, Integer> goalsMin,
                  Map<String, Integer> goalsWeek, Map<String, Integer> goalsMonth,
                  Map<String, Set<java.time.DayOfWeek>> goalDays, Map<String, LocalDate> examDate,
                  Map<String, String> areaType, Map<String, Integer> typeGoalWeek,
                  Map<String, List<String>> subFocos, Map<String, List<String>> weekPlan,
                  Set<String> archived, Set<String> hardDays, Map<String, Integer> streakBest) {
        this.cfg = cfg;
        this.studyDataMap = studyDataMap;
        this.goals = goals; this.goalsMin = goalsMin; this.goalsWeek = goalsWeek; this.goalsMonth = goalsMonth;
        this.goalDays = goalDays; this.examDate = examDate;
        this.areaType = areaType; this.typeGoalWeek = typeGoalWeek;
        this.subFocos = subFocos; this.weekPlan = weekPlan;
        this.archived = archived; this.hardDays = hardDays; this.streakBest = streakBest;
    }

    /** Esvazia tudo o que vem do arquivo (antes de recarregar). Os ajustes têm o próprio reset. */
    void clear() {
        studyDataMap.clear();
        goals.clear(); goalsMin.clear(); goalsWeek.clear(); goalsMonth.clear(); hardDays.clear();
        goalDays.clear(); examDate.clear(); archived.clear();
        areaType.clear(); typeGoalWeek.clear(); subFocos.clear(); weekPlan.clear();
        streakBest.clear(); unknownProps.clear();
        streakBestGeneral = 0; celebratedStreakMilestone = 0;
        themeDark = null;
    }

    /** O conteúdo do arquivo. {@code dark} = tema atual. */
    Properties toProperties(boolean dark) {
        Properties p = new Properties();
        studyDataMap.forEach((k, v) -> p.setProperty(k, v.getMinutes() + "," + v.getColor().getRGB()));
        goals.forEach((k, v)      -> p.setProperty("goal_"  + k, String.valueOf(v)));
        goalsWeek.forEach((k, v)  -> p.setProperty("goalw_" + k, String.valueOf(v)));
        goalsMonth.forEach((k, v) -> p.setProperty("goalm_" + k, String.valueOf(v)));
        goalDays.forEach((k, set) -> {
            StringBuilder sb = new StringBuilder();
            for (java.time.DayOfWeek dw : set) { if (sb.length() > 0) sb.append(','); sb.append(dw.getValue()); }
            p.setProperty("goaldays_" + k, sb.toString());
        });
        examDate.forEach((k, dt) -> p.setProperty("exam_" + k, dt.toString())); // ISO yyyy-MM-dd
        areaType.forEach((k, v) -> { if (!"estudo".equals(v)) p.setProperty("type_" + k, v); });
        typeGoalWeek.forEach((k, v) -> { if (v > 0) p.setProperty("typegoalw_" + k, String.valueOf(v)); });
        subFocos.forEach((k, list) -> { if (!list.isEmpty()) p.setProperty("subfocos_" + k, String.join(",", list)); });
        goalsMin.forEach((k, v) -> { if (v > 0) p.setProperty("goalmin_" + k, String.valueOf(v)); });
        if (!hardDays.isEmpty()) p.setProperty("__hard_days__", String.join(",", hardDays));
        weekPlan.forEach((k, l) -> { if (!l.isEmpty()) p.setProperty("plan_" + k, String.join("|", l)); });
        if (!archived.isEmpty()) p.setProperty("__archived__", String.join(",", archived));
        streakBest.forEach((k, v) -> p.setProperty("streakbest_" + k, String.valueOf(v)));
        unknownProps.forEach(p::setProperty);   // preserva chaves que não soubemos ler
        p.setProperty("__streakbest_general__", String.valueOf(streakBestGeneral));
        p.setProperty("__streak_milestone__", String.valueOf(celebratedStreakMilestone));
        cfg.writeTo(p);
        p.setProperty("__theme__", dark ? "dark" : "light");

        return p;
    }

    /** Lê as chaves para as coleções (soma ao que já estiver nelas, como o load antigo). */
    void fromProperties(Properties p) {
        for (String k : p.stringPropertyNames()) {
            if (cfg.readKey(k, p.getProperty(k))) continue;   // ajustes e marcadores
            if (k.equals("__theme__")) {
                themeDark = "dark".equals(p.getProperty(k));
                continue;
            }
            if (k.startsWith("goalw_")) {
                try { goalsWeek.put(k.substring(6), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                continue;
            }
            if (k.startsWith("goalm_")) {
                try { goalsMonth.put(k.substring(6), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                continue;
            }
            if (k.equals("__streakbest_general__")) {
                try { streakBestGeneral = Integer.parseInt(p.getProperty(k)); } catch (Exception ignored) {}
                continue;
            }
            if (k.equals("__streak_milestone__")) {
                try { celebratedStreakMilestone = Integer.parseInt(p.getProperty(k)); } catch (Exception ignored) {}
                continue;
            }
            if (k.equals("__archived__")) {
                for (String a : p.getProperty(k).split(","))
                    if (!a.trim().isEmpty()) archived.add(a.trim());
                continue;
            }
            if (k.startsWith("streakbest_")) {
                try { streakBest.put(k.substring(11), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                continue;
            }
            if (k.startsWith("goaldays_")) {
                try {
                    java.util.Set<java.time.DayOfWeek> set = java.util.EnumSet.noneOf(java.time.DayOfWeek.class);
                    for (String tok : p.getProperty(k).split(","))
                        if (!tok.trim().isEmpty()) set.add(java.time.DayOfWeek.of(Integer.parseInt(tok.trim())));
                    if (!set.isEmpty() && set.size() < 7) goalDays.put(k.substring(9), set);
                } catch (Exception ignored) {}
                continue;
            }
            if (k.startsWith("exam_")) {
                try { examDate.put(k.substring(5), LocalDate.parse(p.getProperty(k))); } catch (Exception ignored) {}
                continue;
            }
            if (k.startsWith("typegoalw_")) {
                try { typeGoalWeek.put(k.substring(10), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                continue;
            }
            if (k.startsWith("subfocos_")) {
                java.util.List<String> l = new ArrayList<>();
                for (String v : p.getProperty(k).split(",")) if (!v.trim().isEmpty()) l.add(v.trim());
                if (!l.isEmpty()) subFocos.put(k.substring(9), l);
                continue;
            }
            if (k.startsWith("type_")) {
                areaType.put(k.substring(5), p.getProperty(k));
                continue;
            }
            if (k.startsWith("plan_")) {
                java.util.List<String> l = new ArrayList<>();
                for (String a : p.getProperty(k).split("\\|"))
                    if (!a.trim().isEmpty() && !l.contains(a.trim())) l.add(a.trim());
                if (!l.isEmpty()) weekPlan.put(k.substring(5), l);
                continue;
            }
            if (k.equals("__hard_days__")) {
                for (String d : p.getProperty(k).split(",")) if (!d.trim().isEmpty()) hardDays.add(d.trim());
                continue;
            }
            if (k.startsWith("goalmin_")) {
                try { goalsMin.put(k.substring(8), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                continue;
            }
            if (k.startsWith("goal_")) {
                try { goals.put(k.substring(5), Integer.parseInt(p.getProperty(k))); } catch (Exception ignored) {}
                continue;
            }
            String[] parts = p.getProperty(k).split(",");
            boolean parsed = false;
            if (parts.length == 2) {
                try {
                    studyDataMap.put(k, new StudyData(Integer.parseInt(parts[0].trim()),
                            new Color(Integer.parseInt(parts[1].trim()))));
                    parsed = true;
                } catch (Exception ignored) {}
            }
            if (!parsed) {
                // Não conseguimos interpretar esta linha: guarda-a crua para
                // reescrevê-la intacta e não perder o dado.
                unknownProps.put(k, p.getProperty(k));
            }
        }
    }
}
