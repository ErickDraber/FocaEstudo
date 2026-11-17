import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** Testes de gravar o tempo (SessionRecorder) e do "Como foi?" (SessionDetailsDialog). */
public class SessionRecorderTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    static final ZoneId ZONA = ZoneId.systemDefault();
    static final LocalDate QUA = LocalDate.of(2026, 11, 18);
    static final Instant AGORA = QUA.atTime(15, 30).atZone(ZONA).toInstant();

    // ── estado "do app" ─────────────────────────────────────────────────────
    Map<String, StudyData> data = new LinkedHashMap<>();
    List<StudySession> sessions = new ArrayList<>();
    Set<String> archived = new LinkedHashSet<>();
    Map<String, Integer> goals = new LinkedHashMap<>(), goalsMin = new LinkedHashMap<>(),
                         goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>();
    Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    Set<String> hardDays = new HashSet<>();
    Clock clock;
    StudyStats stats;
    List<String> chamadas = Collections.synchronizedList(new ArrayList<>());
    SessionRecorder rec;

    SessionRecorderTest(Clock clock) {
        this.clock = clock;
        stats = new StudyStats(sessions, data.keySet(), archived, goals, goalsMin, goalsWeek, goalsMonth,
                goalDays, hardDays, clock);
        rec = new SessionRecorder(data, sessions, goals, stats, clock, new SessionRecorder.Host() {
            public void recorded()        { chamadas.add("recorded " + sessions.size()); }
            public void toast(String msg) { chamadas.add("toast " + msg); }
        });
    }

    SessionRecorderTest() { this(Clock.fixed(AGORA, ZONA)); }

    void area(String nome, int min) { data.put(nome, new StudyData(min, Color.BLUE)); }

    static long ts(LocalDate d, int h) { return d.atTime(h, 0).atZone(ZONA).toInstant().toEpochMilli(); }

    static StudySession s(String a, int min, long ts) { return new StudySession(a, min, ts, "manual"); }

    static List<String> ordem(List<StudySession> l) {
        List<String> out = new ArrayList<>();
        for (StudySession x : l) out.add(x.getSubject() + ":" + x.getMinutes());
        return out;
    }

    public static void main(String[] args) throws Exception {
        ordemCronologica();
        avisoDeMeta();
        gravarHoje();
        gravarOutroDia();
        avisoNaHora();
        comoFoi();
        System.out.println("SessionRecorder: " + (total - falhas) + "/" + total + " testes passaram");
        System.exit(falhas > 0 ? 1 : 0);
    }

    static void ordemCronologica() {
        List<StudySession> l = new ArrayList<>();
        SessionRecorder.insertChronological(l, s("A", 1, 100));
        SessionRecorder.insertChronological(l, s("B", 2, 300));
        SessionRecorder.insertChronological(l, s("C", 3, 200));
        SessionRecorder.insertChronological(l, s("D", 4, 50));
        SessionRecorder.insertChronological(l, s("E", 5, 200));   // empate: depois da igual
        eq("ordem cronológica", List.of("D:4", "A:1", "C:3", "E:5", "B:2"), ordem(l));
    }

    static void avisoDeMeta() {
        eq("sem meta", null, SessionRecorder.goalNudgeText("A", 0, 0, 0, 100, false));
        eq("bateu agora", "Você bateu a meta de A hoje!  (1h05)", SessionRecorder.goalNudgeText("A", 90, 60, 30, 65, false));
        eq("bateu exato", "Você bateu a meta de A hoje!  (1h00)", SessionRecorder.goalNudgeText("A", 60, 60, 50, 60, false));
        eq("já tinha batido", null, SessionRecorder.goalNudgeText("A", 60, 60, 60, 90, false));
        eq("faltam 15", "Faltam 15 min para a meta de A hoje.", SessionRecorder.goalNudgeText("A", 60, 60, 20, 45, false));
        eq("faltam 1", "Faltam 1 min para a meta de A hoje.", SessionRecorder.goalNudgeText("A", 60, 60, 20, 59, false));
        eq("faltam 16: nada", null, SessionRecorder.goalNudgeText("A", 60, 60, 20, 44, false));
        eq("dia difícil: sem cobrança", null, SessionRecorder.goalNudgeText("A", 60, 60, 20, 45, true));
        eq("dia difícil: comemora igual", "Você bateu a meta de A hoje!  (1h00)",
                SessionRecorder.goalNudgeText("A", 60, 60, 20, 60, true));
        eq("bate o mínimo, não o ideal", "Você bateu a meta de A hoje!  (40m)",
                SessionRecorder.goalNudgeText("A", 90, 40, 10, 40, false));
    }

    static void gravarHoje() {
        SessionRecorderTest t = new SessionRecorderTest();
        t.area("A", 100);
        t.sessions.add(s("A", 10, ts(QUA, 9)));
        t.sessions.add(s("A", 10, ts(QUA, 20)));   // sessão "do futuro" (ex.: relógio mudou)
        t.rec.add("A", 25, "cronometro", new SessionDetailsDialog.Details("Teoria", "cap 2", 3));
        eq("soma no total", 125, t.data.get("A").getMinutes());
        eq("entra antes da sessão mais tarde", List.of("A:10", "A:25", "A:10"), ordem(t.sessions));
        StudySession n = t.sessions.get(1);
        eq("hora = agora", AGORA.toEpochMilli(), n.getTimestamp());
        eq("tipo", "cronometro", n.getType());
        eq("sub-foco", "Teoria", n.getKind());
        eq("nota", "cap 2", n.getNote());
        eq("energia", 3, n.getEnergy());
        eq("avisa o StudyTracker depois de inserir", List.of("recorded 3"), t.chamadas);

        SessionRecorderTest u = new SessionRecorderTest();
        u.area("B", 0);
        u.rec.add("B", 5, "pomodoro", SessionDetailsDialog.Details.of(null));
        eq("sem detalhes: vazios", "|0", u.sessions.get(0).getKind() + u.sessions.get(0).getNote() + "|" + u.sessions.get(0).getEnergy());

        // dia futuro conta como hoje (igual a antes)
        SessionRecorderTest v = new SessionRecorderTest();
        v.area("C", 0);
        v.rec.add("C", 5, "manual", SessionDetailsDialog.Details.of(""), QUA.plusDays(3));
        eq("dia futuro: hora = agora", AGORA.toEpochMilli(), v.sessions.get(0).getTimestamp());
    }

    static void gravarOutroDia() {
        SessionRecorderTest t = new SessionRecorderTest();
        t.area("A", 0);
        t.sessions.add(s("A", 1, ts(QUA.minusDays(3), 8)));
        t.sessions.add(s("A", 2, ts(QUA.minusDays(2), 13)));
        t.sessions.add(s("A", 3, ts(QUA, 9)));
        t.rec.add("A", 40, "manual", new SessionDetailsDialog.Details(null, "esqueci", 0), QUA.minusDays(2));
        StudySession n = t.sessions.get(1);
        eq("ontem-de-ontem: ao meio-dia", ts(QUA.minusDays(2), 12), n.getTimestamp());
        eq("entra antes das 13h do mesmo dia", List.of("A:1", "A:40", "A:2", "A:3"), ordem(t.sessions));
        eq("conta no dia certo", 42, t.stats.minutesOnDay("A", QUA.minusDays(2)));
        eq("não conta hoje", 3, t.stats.minutesOnDay("A", QUA));
        eq("total soma igual", 40, t.data.get("A").getMinutes());
    }

    static void avisoNaHora() throws Exception {
        // hoje: bate a meta ⇒ toast depois do atraso
        SessionRecorderTest t = new SessionRecorderTest();
        t.area("A", 0);
        t.goals.put("A", 60); t.goalsMin.put("A", 30);
        t.sessions.add(s("A", 20, ts(QUA, 9)));
        t.rec.add("A", 15, "manual", SessionDetailsDialog.Details.of(""));

        // outro dia: nunca avisa
        SessionRecorderTest u = new SessionRecorderTest();
        u.area("A", 0);
        u.goals.put("A", 60);
        u.rec.add("A", 59, "manual", SessionDetailsDialog.Details.of(""), QUA.minusDays(1));

        // hoje, dia difícil, faltando pouco: nada
        SessionRecorderTest v = new SessionRecorderTest();
        v.area("A", 0);
        v.goals.put("A", 60);
        v.hardDays.add(QUA.toString());
        v.rec.add("A", 50, "manual", SessionDetailsDialog.Details.of(""));

        eq("antes do atraso: ainda sem toast", List.of("recorded 2"), new ArrayList<>(t.chamadas));
        Thread.sleep(SessionRecorder.NUDGE_DELAY_MS + 600);
        eq("bate o mínimo hoje: toast", List.of("recorded 2", "toast Você bateu a meta de A hoje!  (35m)"), new ArrayList<>(t.chamadas));
        eq("outro dia: sem toast", List.of("recorded 1"), new ArrayList<>(u.chamadas));
        eq("dia difícil: sem toast", List.of("recorded 1"), new ArrayList<>(v.chamadas));
    }

    // ── "Como foi?" ─────────────────────────────────────────────────────────

    static List<String> textos(Container c) {
        List<String> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l) out.add(l.getText());
            else if (k instanceof AbstractButton b) out.add("[" + b.getText() + (b.isSelected() ? "*" : "") + "]");
            if (k instanceof Container cc && !(k instanceof AbstractButton)) out.addAll(textos(cc));
        }
        return out;
    }

    static void comoFoi() {
        AppSettings cfg = new AppSettings();
        Map<String, List<String>> subFocos = new LinkedHashMap<>();
        subFocos.put("Treino", List.of("Perna", "Costas", "Core"));
        SessionDetailsDialog dlg = new SessionDetailsDialog(cfg, subFocos);

        cfg.askDetails = false;
        SessionDetailsDialog.Details d0 = dlg.ask(null, "Treino", 30, "x", "Costas");
        eq("sem perguntar: sub-foco pendente", "Costas", d0.kind);
        eq("sem perguntar: resto vazio", "|0", d0.note + "|" + d0.energy);
        eq("sem perguntar, sem pendente", "", dlg.ask(null, "Treino", 30, "x", null).kind);

        SessionDetailsDialog.Form f = dlg.new Form("Treino", 75, "Costas");
        eq("tela", List.of("1h15 de Treino. Como foi?", "No que você focou?", "[Perna]", "[Costas*]", "[Core]",
                "Energia", "[Baixa]", "[Ok]", "[Alta]", "Nota (vai para o Histórico)"), textos(f.panel));
        SessionDetailsDialog.Details r0 = f.result();
        eq("sem mexer: sub-foco pendente marcado", "Costas", r0.kind);
        eq("sem mexer: energia 0", 0, r0.energy);

        f.sfRow.btns.get(2).doClick();
        eq("marcar outro desmarca o anterior", "Core", f.result().kind);
        eq("só um marcado", false, f.sfRow.btns.get(1).isSelected());
        f.sfRow.btns.get(2).doClick();
        eq("clicar no marcado desmarca", "", f.result().kind);

        f.enRow.btns.get(2).doClick();
        eq("energia Alta = 3", 3, f.result().energy);
        f.enRow.btns.get(0).doClick();
        eq("energia Baixa = 1", 1, f.result().energy);

        f.note.setText("  lista 3 | ex. 4 <html>  ");
        eq("nota limpa", "lista 3 / ex. 4 ‹html>", f.result().note);

        SessionDetailsDialog.Form g = dlg.new Form("Cálculo", 20, "Costas");
        eq("área sem sub-focos: sem a linha", null, g.sfRow);
        eq("área sem sub-focos: tela", List.of("20m de Cálculo. Como foi?", "Energia", "[Baixa]", "[Ok]", "[Alta]",
                "Nota (vai para o Histórico)"), textos(g.panel));
        eq("área sem sub-focos: kind vazio", "", g.result().kind);

        SessionDetailsDialog.Form h = dlg.new Form("Treino", 5, "Bíceps");
        eq("pendente que não é da lista: nada marcado", "", h.result().kind);

        eq("cleanNote", "a/b", SessionDetailsDialog.cleanNote(" a|b "));
        eq("Details nulos viram vazios", "|", new SessionDetailsDialog.Details(null, null, 0).kind + "|"
                + new SessionDetailsDialog.Details(null, null, 0).note);
    }
}
