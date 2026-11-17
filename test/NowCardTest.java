import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** Testes do cartão "Agora": as regras de sugestão (NowSuggester) e os estados do cartão (NowCard). */
public class NowCardTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    static final ZoneId ZONA = ZoneId.systemDefault();
    static final LocalDate QUA = LocalDate.of(2026, 11, 18);   // uma quarta-feira

    // ── estado "do app" ─────────────────────────────────────────────────────
    Map<String, StudyData> data = new LinkedHashMap<>();
    List<StudySession> sessions = new ArrayList<>();
    Set<String> archived = new LinkedHashSet<>();
    Map<String, Integer> goals = new LinkedHashMap<>(), goalsMin = new LinkedHashMap<>(),
                         goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>(),
                         typeGoalWeek = new LinkedHashMap<>();
    Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    Set<String> hardDays = new HashSet<>();
    Map<String, String> tipo = new LinkedHashMap<>();
    Map<String, List<String>> subFocos = new LinkedHashMap<>();
    Map<String, LocalDate> examDate = new LinkedHashMap<>();
    List<String> plano = new ArrayList<>();
    Set<String> skipped = new HashSet<>();
    StudyStats stats;

    // estado da sessão (para o cartão)
    boolean emSessao, pausaPomo, diaDificil;
    String combo;
    List<String> chamadas = new ArrayList<>();

    /** Relógio fixo na quarta às 10h (testes do NowSuggester). */
    NowCardTest() { this(Clock.fixed(QUA.atTime(10, 0).atZone(ZONA).toInstant(), ZONA)); }

    NowCardTest(Clock clock) {
        stats = new StudyStats(sessions, data.keySet(), archived, goals, goalsMin, goalsWeek,
                goalsMonth, goalDays, hardDays, clock);
    }

    String typeOf(String a) { return tipo.getOrDefault(a, "estudo"); }

    void area(String nome) { data.put(nome, new StudyData(0, Color.BLUE)); }

    static long ts(LocalDate d) { return d.atTime(9, 0).atZone(ZONA).toInstant().toEpochMilli(); }

    void sessao(String area, int min, LocalDate dia) { sessions.add(new StudySession(area, min, ts(dia), "manual")); }

    void treino(String area, String grupo, LocalDate dia) {
        sessions.add(new StudySession(area, 30, ts(dia), "manual", null, grupo));
    }

    NowSuggester suggester() {
        return new NowSuggester(new NowSuggester.Source() {
            public Map<String, StudyData> areas()       { return data; }
            public Set<String> archived()               { return archived; }
            public StudyStats stats()                   { return stats; }
            public String typeOf(String a)              { return NowCardTest.this.typeOf(a); }
            public int idealDaily(String a)             { return goals.getOrDefault(a, 0); }
            public LocalDate examDate(String a)         { return examDate.get(a); }
            public List<String> subFocos(String a)      { return subFocos.get(a); }
            public List<StudySession> sessions()        { return sessions; }
            public int typeGoalWeek(String t)           { return typeGoalWeek.getOrDefault(t, 0); }
            public int minutesByType(String t, LocalDate s, LocalDate e) {
                int m = 0;
                for (StudySession x : sessions) {
                    LocalDate d = stats.dateOf(x.getTimestamp());
                    if (typeOf(x.getSubject()).equals(t) && !d.isBefore(s) && !d.isAfter(e)) m += x.getMinutes();
                }
                return m;
            }
            public List<String> plannedAreas(int dow, int slot) { return plano; }
        });
    }

    /** "área | motivo | sub-foco" da sugestão de quarta de manhã, ou "nada". */
    String sug() {
        NowSuggester.Suggestion s = suggester().next(QUA, 0, skipped);
        return s == null ? "nada" : s.area() + " | " + s.reason() + (s.subFoco() != null ? " | " + s.subFoco() : "");
    }

    NowCard card() {
        return new NowCard(new NowCard.Source() {
            public boolean sessionInProgress()    { return emSessao; }
            public boolean pomodoroBreak()        { return pausaPomo; }
            public String selectedArea()          { return combo; }
            public Map<String, StudyData> areas() { return data; }
            public boolean isHardDay(LocalDate d) { return diaDificil; }
        }, suggester(), skipped, new NowCard.Actions() {
            public void focusMode()                    { chamadas.add("focusMode"); }
            public void start(String area, String sf)  { chamadas.add("start " + area + (sf != null ? "/" + sf : "")); }
            public void hardDay(LocalDate d)           { chamadas.add("hardDay " + d); }
            public void undoHardDay(LocalDate d)       { chamadas.add("undoHardDay " + d); }
        });
    }

    static List<String> textos(Container c) {
        List<String> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l) out.add(l.getText());
            else if (k instanceof AbstractButton b) out.add("[" + b.getText() + "]");
            if (k instanceof Container cc) out.addAll(textos(cc));
        }
        return out;
    }

    static boolean clicar(Container c, String texto) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && texto.equals(b.getText())) { b.doClick(); return true; }
            if (k instanceof Container cc && clicar(cc, texto)) return true;
        }
        return false;
    }

    public static void main(String[] args) {
        regras();
        progresso();
        cartao();
        System.out.println("NowCard: " + (total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }

    // ── NowSuggester: a ordem das regras ────────────────────────────────────
    static void regras() {
        NowCardTest t = new NowCardTest();
        eq("sem áreas: nada", "nada", t.sug());

        // 3. a área parada há mais tempo
        t.area("Cálculo"); t.area("Física"); t.area("Inglês");
        t.sessao("Cálculo", 30, QUA.minusDays(5));
        t.sessao("Física", 30, QUA.minusDays(2));
        eq("parada: a que nunca teve sessão vem primeiro", "Inglês | ainda sem nenhuma sessão registrada", t.sug());
        t.skipped.add("Inglês");
        eq("parada: pulada com 'Outra coisa' ⇒ a mais antiga", "Cálculo | faz 5 dia(s) sem isso", t.sug());
        t.archived.add("Cálculo");
        eq("parada: arquivada fica de fora", "Física | faz 2 dia(s) sem isso", t.sug());
        t.archived.clear(); t.skipped.clear();
        t.sessao("Inglês", 10, QUA); t.sessao("Física", 10, QUA); t.sessao("Cálculo", 10, QUA);
        eq("parada: todas mexidas hoje", "Cálculo | já mexeu nisso hoje", t.sug());

        // 2. tipo com meta de equilíbrio zerado na semana (vence a "parada")
        t.area("Corrida"); t.tipo.put("Corrida", "fisico");
        t.sessao("Corrida", 30, QUA.minusDays(10));   // semana passada: não conta
        t.typeGoalWeek.put("fisico", 60);
        eq("equilíbrio: físico zerado na semana", "Corrida | sua semana está sem físico", t.sug());
        t.sessao("Corrida", 20, StudyStats.weekStart(QUA));   // segunda desta semana
        eq("equilíbrio: com tempo na semana, sai (Corrida é a mais parada: segunda)", "Corrida | faz 2 dia(s) sem isso", t.sug());
        t.typeGoalWeek.clear();

        // 1b. rodízio do Físico (vence o equilíbrio)
        t.subFocos.put("Corrida", List.of("Pernas", "Costas"));
        t.treino("Corrida", "Pernas", QUA.minusDays(1));
        eq("rodízio: grupo nunca treinado", "Corrida | treinar Costas — ainda não treinou | Costas", t.sug());
        t.treino("Corrida", "costas", QUA.minusDays(4));   // sub-foco compara sem maiúsculas
        eq("rodízio: grupo parado há 4 dias", "Corrida | treinar Costas — faz 4 dias | Costas", t.sug());
        t.treino("Corrida", "Costas", QUA.minusDays(2));
        eq("rodízio: tudo treinado há menos de 3 dias ⇒ próxima regra", "Corrida | faz 1 dia(s) sem isso", t.sug());

        // 1. meta diária não batida (vence o rodízio)
        t.sessions.removeIf(s -> "Costas".equalsIgnoreCase(s.getKind()));
        t.treino("Corrida", "Costas", QUA.minusDays(9));
        t.goals.put("Cálculo", 60);   // fez 10 hoje
        eq("meta: falta o que sobra da meta", "Cálculo | faltam 50m pra meta de hoje", t.sug());
        t.goals.put("Física", 90);    // fez 10 ⇒ faltam 80 (mais que 50)
        eq("meta: a que mais falta primeiro", "Física | faltam 1h20 pra meta de hoje", t.sug());
        t.examDate.put("Cálculo", QUA.plusDays(5));
        eq("meta: prova próxima passa na frente", "Cálculo | faltam 50m pra meta de hoje · prova em 5d", t.sug());
        t.goals.put("Física", 310);   // faltam 300: o peso da prova em 5 dias ainda ganha (50 + 115×3 = 395)
        eq("meta: peso da prova (3 por dia a menos de 120)", "Cálculo | faltam 50m pra meta de hoje · prova em 5d", t.sug());
        t.goals.put("Física", 410);   // faltam 400 > 395
        eq("meta: falta muito mais ⇒ ganha mesmo sem prova", "Física | faltam 6h40 pra meta de hoje", t.sug());
        t.goals.put("Física", 90);
        t.examDate.put("Cálculo", QUA.minusDays(1));
        eq("meta: prova que já passou não conta", "Física | faltam 1h20 pra meta de hoje", t.sug());
        t.goalDays.put("Física", EnumSet.of(DayOfWeek.MONDAY));
        eq("meta: hoje não é dia da meta ⇒ fica de fora", "Cálculo | faltam 50m pra meta de hoje", t.sug());
        t.sessao("Cálculo", 50, QUA);
        eq("meta: batida ⇒ sai (volta o rodízio)", "Corrida | treinar Costas — faz 9 dias | Costas", t.sug());

        // 0. plano do turno (vence tudo)
        t.goals.put("Inglês", 30);   // fez 10: não está feito hoje
        t.plano.addAll(List.of("Física", "Inglês"));
        eq("plano: a primeira não feita hoje", "Física | está no seu plano desta manhã", t.sug());
        t.sessao("Física", 80, QUA);   // 90 min hoje: bateu a meta (o "feito hoje" não olha os dias da meta)
        eq("plano: feita hoje ⇒ a próxima do plano", "Inglês | está no seu plano desta manhã", t.sug());
        t.skipped.add("Inglês");
        eq("plano: pulada ⇒ regras seguintes", "Corrida | treinar Costas — faz 9 dias | Costas", t.sug());
        NowSuggester.Suggestion noite = t.suggester().next(QUA, 2, new HashSet<>());
        eq("plano: frase do turno", "está no seu plano desta noite", noite.reason());
    }

    // ── NowSuggester.progressText ───────────────────────────────────────────
    static void progresso() {
        NowCardTest t = new NowCardTest();
        t.area("Cálculo");
        NowSuggester s = t.suggester();
        eq("progresso: sem meta ⇒ null", null, s.progressText("Cálculo", QUA));
        t.goals.put("Cálculo", 60);
        eq("progresso: nada feito", "meta de hoje: 1h00", s.progressText("Cálculo", QUA));
        t.sessao("Cálculo", 20, QUA);
        eq("progresso: abaixo do mínimo", "20m de 1h00 hoje", s.progressText("Cálculo", QUA));
        t.goalsMin.put("Cálculo", 15);
        eq("progresso: mínimo feito", "mínimo de hoje feito · 20m de 1h00", s.progressText("Cálculo", QUA));
        t.goalDays.put("Cálculo", EnumSet.of(DayOfWeek.MONDAY));
        eq("progresso: hoje não é dia da meta ⇒ null", null, s.progressText("Cálculo", QUA));
    }

    // ── NowCard: os estados ─────────────────────────────────────────────────
    static void cartao() {
        LocalDate hoje = LocalDate.now();
        NowCardTest t = new NowCardTest(Clock.systemDefaultZone());
        t.area("Cálculo"); t.area("Física");
        NowCard c = t.card();
        eq("antes do refresh: só o título", List.of("Agora"), textos(c));

        // sessão em andamento
        t.emSessao = true; t.combo = "Cálculo";
        c.refresh();
        eq("em andamento", List.of("Agora · em andamento", "Cálculo", "[Modo foco]"), textos(c));
        clicar(c, "Modo foco");

        t.pausaPomo = true;
        c.refresh();
        eq("pausa do Pomodoro", List.of("Agora · pausa do Pomodoro", "Pausa", "[Modo foco]"), textos(c));
        t.emSessao = false; t.pausaPomo = false;

        // dia difícil
        t.diaDificil = true;
        c.refresh();
        eq("dia difícil", List.of("Agora · dia difícil", "Sem cobrança hoje. Se der vontade, qualquer minuto conta.",
                "[desfazer]"), textos(c));
        clicar(c, "desfazer");
        t.diaDificil = false;

        // sugestão + "Outra coisa" dando a volta
        c.refresh();
        eq("sugestão", List.of("Agora · ainda sem nenhuma sessão registrada", "Cálculo", "[Começar]",
                "[Outra coisa]", "[hoje tá difícil?]"), textos(c));
        clicar(c, "Começar");
        clicar(c, "Outra coisa");
        eq("Outra coisa ⇒ a próxima", "Física", textos(c).get(1));
        eq("Outra coisa guarda a área pulada", Set.of("Cálculo"), t.skipped);
        clicar(c, "Outra coisa");
        eq("acabaram as sugestões ⇒ dá a volta", "Cálculo", textos(c).get(1));
        eq("dar a volta zera as puladas", Set.of(), t.skipped);
        clicar(c, "hoje tá difícil?");

        // progresso embaixo do nome: só quando o motivo não fala da meta
        t.plano.add("Física");
        t.goals.put("Física", 60);
        c.refresh();
        List<String> tx = textos(c);
        eq("plano: motivo e progresso embaixo", true,
                tx.get(0).startsWith("Agora · está no seu plano ") && tx.get(1).equals("Física")
                && tx.get(2).equals("meta de hoje: 1h00"));
        t.plano.clear();
        c.refresh();
        tx = textos(c);
        eq("motivo de meta: sem a linha de progresso", List.of("Agora · faltam 1h00 pra meta de hoje", "Física", "[Começar]"),
                tx.subList(0, 3));

        // rodízio: nome com o sub-foco e "Começar" leva o sub-foco
        t.goals.clear();
        t.area("Corrida"); t.tipo.put("Corrida", "fisico"); t.subFocos.put("Corrida", List.of("Costas"));
        c.refresh();
        eq("rodízio: nome com sub-foco", "Corrida · Costas", textos(c).get(1));
        clicar(c, "Começar");

        // nada a sugerir
        NowCardTest v = new NowCardTest(Clock.systemDefaultZone());
        NowCard cv = v.card();
        cv.refresh();
        eq("tudo em dia", List.of("Agora", "Tudo em dia por aqui. Aproveite o descanso.", "[hoje tá difícil?]"), textos(cv));

        eq("botões ⇒ ações", List.of("focusMode", "undoHardDay " + hoje, "start Cálculo", "hardDay " + hoje,
                "start Corrida/Costas"), t.chamadas);

        // refresh não duplica
        c.refresh(); c.refresh();
        eq("refresh não duplica", 1, Collections.frequency(textos(c), "[Começar]"));
    }
}
