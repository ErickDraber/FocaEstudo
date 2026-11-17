import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** Testes dos diálogos de metas (sem abrir janela: preenche o formulário e chama apply()). */
public class GoalDialogsTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    static final ZoneId ZONA = ZoneId.systemDefault();

    // ── estado "do app" ─────────────────────────────────────────────────────
    Map<String, StudyData> data = new LinkedHashMap<>();
    List<StudySession> sessions = new ArrayList<>();
    Set<String> archived = new LinkedHashSet<>();
    Map<String, Integer> goals = new LinkedHashMap<>(), goalsMin = new LinkedHashMap<>(),
                         goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>(),
                         typeGoalWeek = new LinkedHashMap<>();
    Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    Map<String, LocalDate> examDate = new LinkedHashMap<>();
    Set<String> hardDays = new HashSet<>();
    Map<String, String> tipo = new LinkedHashMap<>();
    StudyStats stats = new StudyStats(sessions, data.keySet(), archived, goals, goalsMin, goalsWeek,
            goalsMonth, goalDays, hardDays, Clock.systemDefaultZone());
    AppSettings cfg = new AppSettings();
    List<String> chamadas = new ArrayList<>();

    GoalDialogs dlg = new GoalDialogs(null, data, archived, goals, goalsMin, goalsWeek, goalsMonth,
            goalDays, examDate, typeGoalWeek, cfg, stats, new GoalDialogs.Host() {
        public void changed()             { chamadas.add("changed"); }
        public void toast(String msg)     { chamadas.add("toast " + msg); }
        public String typeOf(String area) { return tipo.getOrDefault(area, "estudo"); }
        public int minutesByType(String t, LocalDate a, LocalDate b) {
            int s = 0;
            for (StudySession x : sessions) {
                LocalDate d = Instant.ofEpochMilli(x.getTimestamp()).atZone(ZONA).toLocalDate();
                if (typeOf(x.getSubject()).equals(t) && !d.isBefore(a) && !d.isAfter(b)) s += x.getMinutes();
            }
            return s;
        }
    });

    void area(String nome) { data.put(nome, new StudyData(0, Color.BLUE)); }

    void sessao(String area, int min, LocalDate dia) {
        sessions.add(new StudySession(area, min, dia.atTime(9, 0).atZone(ZONA).toInstant().toEpochMilli(), "manual"));
    }

    static List<String> textos(Container c) {
        List<String> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l) out.add(l.getText());
            else if (k instanceof AbstractButton b) out.add("[" + b.getText() + "]");
            if (k instanceof Container cc && !(k instanceof AbstractButton)) out.addAll(textos(cc));
        }
        return out;
    }

    static AbstractButton botao(Container c, String texto) {
        List<AbstractButton> achados = new ArrayList<>();
        juntar(c, texto, achados);
        if (achados.size() != 1) throw new IllegalStateException(achados.size() + " botões " + texto);
        return achados.get(0);
    }

    public static void main(String[] args) {
        editorPreenchido();
        editorBotoes();
        editorSugestoes();
        editorGravar();
        gerenciar();
        equilibrio();
        aviso();
        System.out.println("GoalDialogs: " + (total - falhas) + "/" + total + " testes passaram");
        System.exit(falhas > 0 ? 1 : 0);
    }

    // ── metas de uma área ───────────────────────────────────────────────────

    static void editorPreenchido() {
        GoalDialogsTest t = new GoalDialogsTest();
        t.dlg.editGoals(null);
        eq("sem área: só avisa", List.of("toast Selecione uma matéria!"), t.chamadas);

        t.area("Cálculo");
        t.goals.put("Cálculo", 60); t.goalsMin.put("Cálculo", 20); t.goalsWeek.put("Cálculo", 300);
        t.goalDays.put("Cálculo", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        t.examDate.put("Cálculo", LocalDate.of(2026, 12, 3));
        GoalDialogs.GoalEditor e = t.dlg.new GoalEditor("Cálculo");
        eq("diária", "60", e.fDay.getText());
        eq("mínimo", "20", e.fMin.getText());
        eq("semana", "300", e.fWeek.getText());
        eq("mês vazio (0)", "", e.fMonth.getText());
        eq("prova", "03/12/2026", e.fExam.getText());
        StringBuilder dias = new StringBuilder();
        for (JToggleButton b : e.tgDias) dias.append(b.isSelected() ? '1' : '0');
        eq("dias marcados", "1010100", dias.toString());
        eq("dica dos dias", "3 dia(s)/semana com meta", e.diasHint.getText());

        GoalDialogs.GoalEditor v = t.dlg.new GoalEditor("Nova");
        StringBuilder todos = new StringBuilder();
        for (JToggleButton b : v.tgDias) todos.append(b.isSelected() ? '1' : '0');
        eq("sem dias salvos: todos marcados", "1111111", todos.toString());
        eq("área nova: campos vazios", "||||", v.fDay.getText() + "|" + v.fMin.getText() + "|"
                + v.fWeek.getText() + "|" + v.fMonth.getText() + "|" + v.fExam.getText());
        eq("área nova sem estudo: dica da média", true,
                textos(v.panel).contains("Estude alguns dias e o app passa a sugerir metas pela sua média."));
        eq("presets", List.of("[15m]", "[30m]", "[45m]", "[1h00]", "[1h30]", "[2h00]"),
                textos(v.panel).stream().filter(s -> s.matches("\\[\\d.*\\]")).toList());
    }

    static void editorBotoes() {
        GoalDialogsTest t = new GoalDialogsTest();
        t.area("A");
        t.goals.put("A", 30); t.goalsMin.put("A", 10); t.goalsWeek.put("A", 200); t.goalsMonth.put("A", 900);
        GoalDialogs.GoalEditor e = t.dlg.new GoalEditor("A");

        botao(e.panel, "Limpar metas").doClick();
        eq("Limpar zera os 4 campos", "|||", e.fDay.getText() + "|" + e.fMin.getText() + "|"
                + e.fWeek.getText() + "|" + e.fMonth.getText());

        // sem diária: mexer nos dias só muda a dica
        e.tgDias[6].doClick();
        eq("dia sem diária: dica", "6 dia(s)/semana. Preencha a meta diária p/ calcular semana e mês.", e.diasHint.getText());
        eq("dia sem diária: semana intacta", "", e.fWeek.getText());

        // preset com 6 dias: semana = 45×6, mês = 270×30/7 arredondado
        botao(e.panel, "45m").doClick();
        eq("preset: diária", "45", e.fDay.getText());
        eq("preset: semana", "270", e.fWeek.getText());
        eq("preset: mês", "1157", e.fMonth.getText());

        // com diária: desmarcar sábado recalcula
        e.tgDias[5].doClick();
        eq("5 dias: semana", "225", e.fWeek.getText());
        eq("5 dias: mês", "964", e.fMonth.getText());
        eq("5 dias: dica", "5 dia(s)/semana  →  semana 3h45  ·  mês 16h04", e.diasHint.getText());

        e.fDay.setText("1h");
        e.tgDias[5].doClick();
        eq("diária em horas também conta", "360", e.fWeek.getText());
    }

    static void editorSugestoes() {
        GoalDialogsTest t = new GoalDialogsTest();
        t.area("A");
        LocalDate hoje = LocalDate.now();
        t.sessao("A", 60, hoje.minusDays(1));
        t.sessao("A", 90, hoje.minusDays(2));
        GoalDialogs.GoalEditor e = t.dlg.new GoalEditor("A");
        List<String> tx = textos(e.panel);
        String media = "Sua média nos últimos 30 dias: " + ScreenKit.fmtHM(t.stats.avgDailyMinutes("A", 30)) + "/dia";
        eq("média 30 dias", true, tx.contains(media));
        eq("média dos dias estudados", true, tx.contains("Média nos dias em que você estudou: 1h15"));
        eq("sem a dica de 'estude alguns dias'", false,
                tx.contains("Estude alguns dias e o app passa a sugerir metas pela sua média."));
        int best = t.stats.bestWeekMinutes("A");
        eq("melhor semana", true, tx.contains("Repetir sua melhor semana (" + ScreenKit.fmtHM(best) + ")  →  "
                + ScreenKit.fmtHM(GoalForm.bestWeekPerDay(best, 7)) + "/dia"));

        // "Usar" da média dos dias estudados: 75/dia × 7
        int usar = 0;
        for (String s : tx) if (s.equals("[Usar]")) usar++;
        eq("um Usar por sugestão", 3, usar);
        // o 2º "Usar" é o da média dos dias estudados
        List<AbstractButton> usos = new ArrayList<>();
        juntar(e.panel, "Usar", usos);
        usos.get(1).doClick();
        eq("Usar: diária", "75", e.fDay.getText());
        eq("Usar: semana", "525", e.fWeek.getText());
    }

    static void juntar(Container c, String texto, List<AbstractButton> out) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && texto.equals(b.getText())) out.add(b);
            else if (k instanceof Container cc) juntar(cc, texto, out);
        }
    }

    static void editorGravar() {
        GoalDialogsTest t = new GoalDialogsTest();
        t.area("A");
        t.goalDays.put("A", EnumSet.of(DayOfWeek.MONDAY));
        GoalDialogs.GoalEditor e = t.dlg.new GoalEditor("A");
        e.tgDias[1].doClick();   // + terça (antes de digitar: mexer nos dias recalcula semana/mês)
        e.fDay.setText("1h30"); e.fMin.setText("45"); e.fWeek.setText(""); e.fMonth.setText("10h");
        e.fExam.setText("05/01/2027");
        e.apply();
        eq("grava diária", 90, t.goals.get("A"));
        eq("grava mínimo", 45, t.goalsMin.get("A"));
        eq("semana vazia = 0", 0, t.goalsWeek.get("A"));
        eq("grava mês", 600, t.goalsMonth.get("A"));
        eq("grava dias", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), t.goalDays.get("A"));
        eq("grava prova", LocalDate.of(2027, 1, 5), t.examDate.get("A"));
        eq("avisa", List.of("changed", "toast Metas de \"A\" atualizadas."), t.chamadas);

        // mínimo ≥ ideal some; todos os dias = sem entrada; prova vazia some
        t.chamadas.clear();
        GoalDialogs.GoalEditor f = t.dlg.new GoalEditor("A");
        f.fMin.setText("2h");
        for (JToggleButton b : f.tgDias) if (!b.isSelected()) b.doClick();
        f.fExam.setText("");
        f.apply();
        eq("mínimo maior que o ideal: removido", false, t.goalsMin.containsKey("A"));
        eq("7 dias: sem entrada", false, t.goalDays.containsKey("A"));
        eq("prova vazia: removida", false, t.examDate.containsKey("A"));

        // prova inválida: avisa, remove e grava o resto
        t.chamadas.clear();
        t.examDate.put("A", LocalDate.of(2027, 1, 5));
        GoalDialogs.GoalEditor g = t.dlg.new GoalEditor("A");
        g.fExam.setText("5 de janeiro");
        g.fMin.setText("abc");   // mínimo inválido vira 0, sem aviso
        g.apply();
        eq("prova inválida: avisos", List.of("toast Data da prova inválida (use dd/mm/aaaa) — ignorada.",
                "changed", "toast Metas de \"A\" atualizadas."), t.chamadas);
        eq("prova inválida: removida", false, t.examDate.containsKey("A"));

        // meta inválida: só avisa, nada muda (nem os dias)
        t.chamadas.clear();
        GoalDialogs.GoalEditor h = t.dlg.new GoalEditor("A");
        h.tgDias[0].doClick();
        h.fWeek.setText("xyz");
        h.apply();
        eq("meta inválida: só o aviso",
                List.of("toast Meta inválida — use algo como \"90\", \"1h30\" ou \"1:30\"."), t.chamadas);
        eq("meta inválida: dias intactos", false, t.goalDays.containsKey("A"));

        // tudo vazio: "removidas"
        t.chamadas.clear();
        GoalDialogs.GoalEditor z = t.dlg.new GoalEditor("A");
        botao(z.panel, "Limpar metas").doClick();
        z.apply();
        eq("tudo vazio: removidas", List.of("changed", "toast Metas de \"A\" removidas."), t.chamadas);
        eq("tudo vazio: diária 0 fica gravada", 0, t.goals.get("A"));
    }

    // ── gerenciar metas ─────────────────────────────────────────────────────

    static void gerenciar() {
        eq("linha com meta", "   Cálculo   —   Dia 30m · Sem 3h30 · Mês 15h00",
                GoalDialogs.manageRowText("Cálculo", 30, 210, 900));
        eq("linha sem meta", "   Violão   —   sem meta", GoalDialogs.manageRowText("Violão", 0, 0, 0));
        eq("só semana conta como meta", "   X   —   Dia 0m · Sem 1h00 · Mês 0m", GoalDialogs.manageRowText("X", 0, 60, 0));

        GoalDialogsTest t = new GoalDialogsTest();
        for (String a : List.of("Treino", "Cálculo", "Violão", "Velha", "Física")) t.area(a);
        t.tipo.put("Treino", "fisico"); t.tipo.put("Violão", "lazer");
        t.archived.add("Velha");
        t.goals.put("Cálculo", 30); t.goalsWeek.put("Cálculo", 210); t.goalsMonth.put("Cálculo", 900);
        t.goals.put("Treino", 45); t.goalsMin.put("Treino", 20); t.goalsWeek.put("Treino", 135); t.goalsMonth.put("Treino", 600);
        t.goals.put("Velha", 10);
        GoalDialogs.ManageForm f = t.dlg.new ManageForm();
        eq("lista por tipo, sem arquivadas", List.of(
                "Marque as matérias para limpar as metas:",
                "ESTUDO", "[   Cálculo   —   Dia 30m · Sem 3h30 · Mês 15h00]", "[   Física   —   sem meta]",
                "FÍSICO", "[   Treino   —   Dia 45m · Sem 2h15 · Mês 10h00]",
                "LAZER", "[   Violão   —   sem meta]",
                "[Marcar todas com meta]"), textos(f.panel));
        eq("sem meta: desligada", false, f.cbs.get("Física").isEnabled());

        f.apply();
        eq("nada marcado", List.of("toast Nenhuma matéria marcada."), t.chamadas);

        f.todas.doClick();
        eq("Marcar todas: só as com meta", "true false true false",
                f.cbs.get("Cálculo").isSelected() + " " + f.cbs.get("Física").isSelected() + " "
              + f.cbs.get("Treino").isSelected() + " " + f.cbs.get("Violão").isSelected());
        t.chamadas.clear();
        f.apply();
        eq("limpa as 4 metas", "{} {} {} {}",
                without(t.goals, "Velha") + " " + t.goalsMin + " " + t.goalsWeek + " " + t.goalsMonth);
        eq("arquivada intocada", 10, t.goals.get("Velha"));
        eq("plural", List.of("changed", "toast Metas de 2 matérias limpas."), t.chamadas);

        GoalDialogsTest u = new GoalDialogsTest();
        u.area("A"); u.goals.put("A", 30);
        GoalDialogs.ManageForm g = u.dlg.new ManageForm();
        g.cbs.get("A").setSelected(true);
        g.apply();
        eq("singular", List.of("changed", "toast Metas de 1 matéria limpas."), u.chamadas);
    }

    static Map<String, Integer> without(Map<String, Integer> m, String k) {
        Map<String, Integer> c = new LinkedHashMap<>(m); c.remove(k); return c;
    }

    // ── metas de equilíbrio ─────────────────────────────────────────────────

    static void equilibrio() {
        GoalDialogsTest t = new GoalDialogsTest();
        t.typeGoalWeek.put("fisico", 120);
        GoalDialogs.TypeForm f = t.dlg.new TypeForm();
        eq("rótulos", List.of("Mínimo por semana para cada tipo (vazio = sem meta de equilíbrio).",
                "Estudo:", "Físico:", "Lazer:", "Trabalho:", "Outro:"), textos(f.panel));
        eq("preenchido", "|120|||", f.fs[0].getText() + "|" + f.fs[1].getText() + "|" + f.fs[2].getText()
                + "|" + f.fs[3].getText() + "|" + f.fs[4].getText());
        f.fs[1].setText(""); f.fs[2].setText("1h30"); f.fs[4].setText("0");
        f.apply();
        eq("grava", "{lazer=90}", t.typeGoalWeek.toString());
        eq("avisa", List.of("changed", "toast Metas de equilíbrio atualizadas."), t.chamadas);

        t.chamadas.clear();
        GoalDialogs.TypeForm g = t.dlg.new TypeForm();
        g.fs[0].setText("40"); g.fs[3].setText("nada");
        g.apply();
        eq("inválido: avisa", List.of("toast Valor inválido — use \"90\" ou \"1h30\"."), t.chamadas);
        eq("inválido: os anteriores ao erro já gravaram (igual a antes)", "{lazer=90, estudo=40}", t.typeGoalWeek.toString());
    }

    // ── aviso de equilíbrio ─────────────────────────────────────────────────

    static void aviso() {
        LocalDate qui = LocalDate.of(2026, 11, 19), qua = qui.minusDays(1);
        Map<String, Integer> metas = new LinkedHashMap<>();
        metas.put("lazer", 60); metas.put("fisico", 90); metas.put("outro", 0);
        Map<String, Integer> feito = new HashMap<>();
        java.util.function.ToIntBiFunction<String, LocalDate> min = (t, ws) -> {
            eq("semana começa na segunda", LocalDate.of(2026, 11, 16), ws);
            return feito.getOrDefault(t, 0);
        };
        eq("quarta: não", null, GoalDialogs.nudgeMissing(qua, false, null, metas, min));
        eq("dia difícil: não", null, GoalDialogs.nudgeMissing(qui, true, null, metas, min));
        eq("já avisado na semana: não", null, GoalDialogs.nudgeMissing(qui, false, "2026-W47", metas, min));
        eq("faltando, na ordem dos tipos", List.of("físico", "lazer"),
                GoalDialogs.nudgeMissing(qui, false, "2026-W46", metas, min));
        feito.put("fisico", 30);
        eq("domingo, só lazer", List.of("lazer"), GoalDialogs.nudgeMissing(qui.plusDays(3), false, null, metas, min));
        feito.put("lazer", 1);
        eq("nada faltando", List.of(), GoalDialogs.nudgeMissing(qui, false, null, metas, min));
        eq("texto", "Sua semana está sem físico e lazer. Que tal um pouco hoje?",
                GoalDialogs.nudgeText(List.of("físico", "lazer")));

        // a classe: marca a semana quando há tipo faltando (hoje de verdade)
        GoalDialogsTest t = new GoalDialogsTest();
        t.typeGoalWeek.put("lazer", 60);
        t.dlg.balanceNudge();
        boolean dia = LocalDate.now().getDayOfWeek().getValue() >= 4;
        eq("balanceNudge marca a semana só de quinta em diante",
                dia ? Notices.weekKey(LocalDate.now()) : null, t.cfg.balanceNudgeWeek);
        eq("sem meta de equilíbrio: não marca", null, new GoalDialogsTest().cfgAfterNudge());
    }

    String cfgAfterNudge() { dlg.balanceNudge(); return cfg.balanceNudgeWeek; }
}
