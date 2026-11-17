import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/** Testes da tela Progresso: textos puros, donut, os três cartões montados e os botões. */
public class ProgressScreenTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    // ── estado "do app" ─────────────────────────────────────────────────────
    Map<String, StudyData> data = new LinkedHashMap<>();
    List<StudySession> sessions = new ArrayList<>();
    List<ChecklistItem> checklist = new ArrayList<>();
    Set<String> archived = new LinkedHashSet<>();
    Map<String, Integer> goals = new LinkedHashMap<>(), goalsMin = new LinkedHashMap<>(),
                         goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>(),
                         typeGoalWeek = new LinkedHashMap<>();
    Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    Set<String> hardDays = new HashSet<>();
    Map<String, String> tipo = new LinkedHashMap<>();
    Map<String, List<String>> subFocos = new LinkedHashMap<>();
    Map<String, LocalDate> examDate = new LinkedHashMap<>();
    String sel;
    List<String> chamadas = new ArrayList<>();
    StudyStats stats = new StudyStats(sessions, data.keySet(), archived, goals, goalsMin, goalsWeek,
            goalsMonth, goalDays, hardDays, Clock.systemDefaultZone());

    String typeOf(String a) { return tipo.getOrDefault(a, "estudo"); }

    void area(String nome, int min) { data.put(nome, new StudyData(min, Color.BLUE)); }

    void sessao(String area, int min) {
        sessions.add(new StudySession(area, min, System.currentTimeMillis(), "manual"));
        data.get(area).addMinutes(min);
    }

    ProgressScreen tela() {
        return new ProgressScreen(new ProgressScreen.Source() {
            public Map<String, StudyData> areas()     { return data; }
            public Set<String> archived()             { return archived; }
            public String typeOf(String a)            { return ProgressScreenTest.this.typeOf(a); }
            public StudyStats stats()                 { return stats; }
            public int bestStreak(String a)           { return a == null ? 9 : 4; }
            public int minutesByType(String t, LocalDate s, LocalDate e) {
                int m = 0;
                for (StudySession x : sessions) if (typeOf(x.getSubject()).equals(t)) m += x.getMinutes();
                return m;
            }
            public int typeGoalWeek(String t)         { return typeGoalWeek.getOrDefault(t, 0); }
            public int idealDaily(String a)           { return goals.getOrDefault(a, 0); }
            public List<String> subFocos(String a)    { return subFocos.get(a); }
            public LocalDate examDate(String a)       { return examDate.get(a); }
            public List<ChecklistItem> checklist()    { return checklist; }
            public String selectedArea()              { return sel; }
            public List<StudySession> sessions()      { return sessions; }
            public Map<String, Integer> goals()       { return goals; }
        }, new ProgressScreen.Actions() {
            public void history()     { chamadas.add("history"); }
            public void stats()       { chamadas.add("stats"); }
            public void manageGoals() { chamadas.add("manageGoals"); }
            public void typeGoals()   { chamadas.add("typeGoals"); }
        });
    }

    /** Textos de todos os JLabel e botões, na ordem em que aparecem. */
    static List<String> textos(Container c) {
        List<String> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l) out.add(l.getText());
            else if (k instanceof AbstractButton b) out.add("[" + b.getText() + "]");
            if (k instanceof Container cc) out.addAll(textos(cc));
        }
        return out;
    }

    static void clicar(Container c, String texto) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && texto.equals(b.getText())) { b.doClick(); return; }
            if (k instanceof Container cc) clicar(cc, texto);
        }
    }

    static String comeca(List<String> tx, String prefixo) {
        for (String s : tx) if (s != null && s.startsWith(prefixo)) return s;
        return null;
    }

    public static void main(String[] args) {
        // ── textos puros ───────────────────────────────────────────────────
        eq("metas: nenhuma ⇒ sem meta", "sem meta", ProgressScreen.goalsText(0, 0, 0));
        eq("metas: as três", "Dia 30m  ·  Sem 3h30  ·  Mês 15h00", ProgressScreen.goalsText(30, 210, 900));
        eq("metas: diária negativa vira 0", "Dia 0m  ·  Sem 2h00  ·  Mês 0m", ProgressScreen.goalsText(-5, 120, 0));

        eq("sequência: sem meta", "defina uma meta diária para ativar a sequência",
                ProgressScreen.streakText(0, 5, 9, false));
        eq("sequência: zerada", "comece hoje para iniciar a sequência", ProgressScreen.streakText(30, 0, 9, false));
        eq("sequência: 1 dia, feita hoje", "Sequência: 1 dia   ·   recorde 4", ProgressScreen.streakText(30, 1, 4, true));
        eq("sequência: 2 dias, falta hoje", "Sequência: 2 dias   ·   recorde 4   (estude hoje para manter)",
                ProgressScreen.streakText(30, 2, 4, false));
        eq("sequência: marco de 7 dias leva o selo", true,
                ProgressScreen.streakText(30, 7, 7, true).startsWith("Sequência: 7 dias" + StudyStats.streakBadge(7)));

        // ── donut ──────────────────────────────────────────────────────────
        ProgressScreenTest d = new ProgressScreenTest();
        d.area("Cálculo", 60); d.area("Corrida", 40); d.area("Física", 0); d.area("Violão", 20); d.area("Inglês", 30);
        d.tipo.put("Corrida", "fisico"); d.tipo.put("Violão", "lazer");
        List<PieChartPanel.Group> gs = ProgressScreen.pieGroups(d.data, d::typeOf);
        StringBuilder sb = new StringBuilder();
        for (PieChartPanel.Group g : gs) {
            sb.append(g.label).append('=').append(g.minutes).append('[');
            for (PieChartPanel.Slice s : g.slices) sb.append(s.label).append(':').append(s.minutes).append(' ');
            sb.append("] ");
        }
        eq("donut: um grupo por tipo, na ordem; área com 0 min fica de fora",
                "Estudo=90[Cálculo:60 Inglês:30 ] Físico=40[Corrida:40 ] Lazer=20[Violão:20 ] ", sb.toString());
        eq("donut: cor do grupo é a do tipo", AreaTypes.color("fisico"), gs.get(1).color);
        eq("donut: sem tempo ⇒ vazio", 0, ProgressScreen.pieGroups(new LinkedHashMap<>(), d::typeOf).size());

        // ── tela vazia ─────────────────────────────────────────────────────
        ProgressScreenTest v = new ProgressScreenTest();
        ProgressScreen telaVazia = v.tela();
        telaVazia.refresh();
        List<String> tv = textos(telaVazia);
        eq("vazia: equilíbrio mostra a dica", true,
                tv.contains("Registre tempo em Estudo, Físico ou Lazer para ver o equilíbrio."));
        eq("vazia: GERAL sem meta", true, tv.contains("— sem meta"));
        eq("vazia: sem área selecionada, só o bloco GERAL", 1, Collections.frequency(tv, "GERAL"));

        // ── tela com dados ─────────────────────────────────────────────────
        ProgressScreenTest t = new ProgressScreenTest();
        t.area("Cálculo", 0); t.area("Corrida", 0); t.area("Antiga", 0);
        t.tipo.put("Corrida", "fisico");
        t.archived.add("Antiga");
        t.goals.put("Cálculo", 30);
        t.sessao("Cálculo", 30);
        t.typeGoalWeek.put("fisico", 60);
        t.subFocos.put("Cálculo", List.of("Listas", "Provas"));
        LocalDate prova = LocalDate.now().plusDays(2);
        t.examDate.put("Cálculo", prova);
        t.checklist.add(new ChecklistItem("Cálculo", "Lista 1", 20, true));
        t.checklist.add(new ChecklistItem("Cálculo", "Lista 2", 20, false));
        t.sel = "Cálculo";

        ProgressScreen tela = t.tela();
        eq("antes do refresh: cartões vazios", false, textos(tela).contains("GERAL"));
        tela.refresh();
        List<String> tx = textos(tela);

        eq("cabeçalho", true, tx.contains("Progresso") && tx.contains("[Histórico]") && tx.contains("[Estatísticas]"));
        eq("títulos dos cartões", true, tx.contains("Metas") && tx.contains("Equilíbrio · esta semana"));

        // Equilíbrio: Estudo (30 min, sem meta) e Físico (0 de 1h, com meta); os outros tipos não aparecem
        int iEst = tx.indexOf("Estudo");
        eq("equilíbrio: Estudo com o tempo da semana", "30m", tx.get(iEst + 1));
        int iFis = tx.indexOf("Físico");
        eq("equilíbrio: Físico com meta mostra atual / meta", "0m / 1h00", tx.get(iFis + 1));
        eq("equilíbrio: tipo sem uso e sem meta não aparece", false, tx.contains("Lazer"));

        // Metas: agrupadas por tipo, arquivada fora, sub-focos embaixo
        eq("metas: cabeçalhos por tipo", true, tx.contains("ESTUDO") && tx.contains("FÍSICO"));
        eq("metas: área arquivada fora", false, tx.contains("Antiga"));
        int iMeta = tx.indexOf("ESTUDO");
        eq("metas: linha da área", "Cálculo", tx.get(iMeta + 1));
        eq("metas: texto da linha começa pela diária", true, tx.get(iMeta + 2).startsWith("Dia 30m  ·  Sem "));
        eq("metas: sub-focos", "      Listas · Provas", tx.get(iMeta + 3));
        eq("metas: área sem meta", "sem meta", tx.get(tx.indexOf("FÍSICO") + 2));

        // Progresso: GERAL + área selecionada
        eq("progresso: GERAL e depois o bloco da área", true, tx.indexOf("GERAL") >= 0 && tx.indexOf("GERAL") < tx.indexOf("Cálculo"));
        eq("progresso: sequência da área (recorde vem do Source)", "Sequência: 1 dia   ·   recorde 4",
                comeca(tx, "Sequência: 1 dia   ·   recorde 4"));
        eq("progresso: sequência geral usa o recorde geral", true, comeca(tx, "Sequência: 1 dia   ·   recorde 9") != null);
        String quando = prova.format(DateTimeFormatter.ofPattern("dd/MM"));
        String exam = comeca(tx, "Prova em");
        eq("progresso: prova em 2 dias", true, exam != null && exam.startsWith("Prova em 2 dias (" + quando + ")"));
        eq("progresso: prova mostra o plano de tarefas", true, exam != null && exam.endsWith("plano 1/2"));
        eq("progresso: barra do dia feita", true, tx.contains("30m / 30m   feito · 100%"));
        eq("progresso: saldo da semana", true, comeca(tx, "semana: ") != null);

        // botões
        clicar(tela, "Histórico"); clicar(tela, "Estatísticas"); clicar(tela, "Gerenciar…"); clicar(tela, "Metas…");
        eq("botões ⇒ ações", List.of("history", "stats", "manageGoals", "typeGoals"), t.chamadas);

        // refresh de novo não duplica; trocar a área do combo só mexe no cartão Progresso
        tela.refresh();
        List<String> tx2 = textos(tela);
        eq("refresh não duplica", Collections.frequency(tx, "GERAL"), Collections.frequency(tx2, "GERAL"));
        eq("refresh não duplica metas", 1, Collections.frequency(tx2, "ESTUDO"));
        t.sel = null;
        tela.refreshProgressCard();
        eq("sem área no combo: some o bloco da área", null, comeca(textos(tela), "Prova em"));
        t.sel = "Corrida";
        tela.refreshProgressCard();
        List<String> tx3 = textos(tela);
        eq("área sem meta: dica da sequência", true, tx3.contains("defina uma meta diária para ativar a sequência"));
        eq("área sem prova: sem linha de prova", null, comeca(tx3, "Prova "));

        // prova passada
        t.sel = "Cálculo";
        t.examDate.put("Cálculo", LocalDate.now().minusDays(1));
        tela.refreshProgressCard();
        eq("prova passada", "Prova de Cálculo foi em " + LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM")),
                comeca(textos(tela), "Prova de"));

        System.out.println("ProgressScreen: " + (total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }
}
