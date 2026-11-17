import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.time.*;
import java.util.*;
import java.util.List;

/** Testes do plano da semana (WeekPlan) e das partes do dia na tela Hoje (TodayPanels). */
public class TodayPanelsTest {

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
                         goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>();
    Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    Set<String> hardDays = new HashSet<>();
    Map<String, String> tipo = new LinkedHashMap<>();
    Map<String, List<String>> plan = new LinkedHashMap<>();
    List<String> chamadas = new ArrayList<>();
    StudyStats stats = new StudyStats(sessions, data.keySet(), archived, goals, goalsMin, goalsWeek,
            goalsMonth, goalDays, hardDays, Clock.systemDefaultZone());
    WeekPlan wp = new WeekPlan(plan, data, archived, goalDays, stats, this::typeOf);

    String typeOf(String a) { return tipo.getOrDefault(a, "estudo"); }

    void area(String nome) { data.put(nome, new StudyData(0, Color.BLUE)); }

    void sessao(String area, int min, LocalDate dia) {
        sessions.add(new StudySession(area, min, dia.atTime(9, 0).atZone(ZONA).toInstant().toEpochMilli(), "manual"));
    }

    void planeja(int dow, int turno, String... areas) {
        plan.computeIfAbsent(dow + "_" + PlannerPanel.SLOT[turno], k -> new ArrayList<>()).addAll(List.of(areas));
    }

    /** O plano como texto: "1m=[A] 1t=[B] …" na ordem dos dias e turnos. */
    String planoTexto() {
        StringBuilder sb = new StringBuilder();
        for (int d = 1; d <= 7; d++)
            for (int s = 0; s < 3; s++) {
                List<String> l = plan.get(d + "_" + PlannerPanel.SLOT[s]);
                if (l != null && !l.isEmpty()) sb.append(d).append(PlannerPanel.SLOT[s]).append('=').append(l).append(' ');
            }
        return sb.toString().trim();
    }

    TodayPanels panels() {
        return new TodayPanels(new TodayPanels.Source() {
            public Map<String, StudyData> areas()  { return data; }
            public Set<String> archived()          { return archived; }
            public String typeOf(String a)         { return TodayPanelsTest.this.typeOf(a); }
            public StudyStats stats()              { return stats; }
            public int idealDaily(String a)        { return goals.getOrDefault(a, 0); }
            public WeekPlan plan()                 { return wp; }
        }, new TodayPanels.Actions() {
            public void manageGoals()      { chamadas.add("manageGoals"); }
            public void openPlanner()      { chamadas.add("openPlanner"); }
            public void fillPlan()         { chamadas.add("fillPlan"); }
            public void start(String a)    { chamadas.add("start " + a); }
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
            if (k instanceof JLabel l && texto.equals(l.getText()) && l.getMouseListeners().length > 0) {
                for (MouseListener ml : l.getMouseListeners())
                    ml.mouseClicked(new MouseEvent(l, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
                return true;
            }
            if (k instanceof Container cc && clicar(cc, texto)) return true;
        }
        return false;
    }

    public static void main(String[] args) {
        semana();
        preencher();
        telas();
        System.out.println("TodayPanels: " + (total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }

    // ── WeekPlan: leitura do plano e escolha do turno ───────────────────────
    static void semana() {
        TodayPanelsTest t = new TodayPanelsTest();
        t.area("Cálculo"); t.area("Física"); t.area("Velha");
        t.archived.add("Velha");
        t.planeja(1, 0, "Cálculo", "Apagada", "Velha", "Física");
        eq("planned: sem apagadas nem arquivadas, na ordem", List.of("Cálculo", "Física"), t.wp.planned(1, 0));
        eq("planned: turno vazio", List.of(), t.wp.planned(1, 1));
        eq("dayIsEmpty: dia com plano", false, t.wp.dayIsEmpty(1));
        t.planeja(2, 2, "Velha");
        eq("dayIsEmpty: só arquivada conta como vazio", true, t.wp.dayIsEmpty(2));
        eq("dayIsEmpty: dia sem nada", true, t.wp.dayIsEmpty(3));
        eq("dayHasArea: em qualquer turno", true, t.wp.dayHasArea(1, "Física"));
        eq("dayHasArea: outro dia", false, t.wp.dayHasArea(3, "Física"));
        eq("dayHasArea: conta o gravado, mesmo arquivada", true, t.wp.dayHasArea(2, "Velha"));

        // pickSlot: hábito manda; sem hábito, o turno mais vazio do dia (empate: o mais cedo)
        TodayPanelsTest p = new TodayPanelsTest();
        p.area("A"); p.area("B");
        eq("pickSlot: dia vazio ⇒ manhã", 0, p.wp.pickSlot("A", 3));
        p.planeja(3, 0, "B"); p.planeja(3, 2, "B");
        eq("pickSlot: sem hábito ⇒ o turno mais vazio", 1, p.wp.pickSlot("A", 3));
        p.planeja(1, 2, "A"); p.planeja(2, 2, "A"); p.planeja(4, 1, "A");
        eq("pickSlot: hábito (noite 2× > tarde 1×)", 2, p.wp.pickSlot("A", 3));
        p.planeja(5, 1, "A");
        eq("pickSlot: hábito empatado ⇒ o mais cedo", 1, p.wp.pickSlot("A", 3));
    }

    // ── WeekPlan.autofill: "Preencher pelas metas" ──────────────────────────
    static void preencher() {
        TodayPanelsTest t = new TodayPanelsTest();
        eq("autofill: sem áreas ⇒ 0", 0, t.wp.autofill());

        t.area("física"); t.area("Cálculo"); t.area("Corrida"); t.area("Sem meta"); t.area("Velha");
        t.tipo.put("Corrida", "fisico");
        t.goals.put("física", 30); t.goals.put("Cálculo", 60); t.goals.put("Velha", 30);
        t.goalsMin.put("Corrida", 20);              // só mínimo também conta como meta
        t.archived.add("Velha");
        t.goalDays.put("Cálculo", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        t.goalDays.put("Corrida", EnumSet.of(DayOfWeek.TUESDAY));

        int n = t.wp.autofill();
        eq("autofill: blocos colocados (Cálculo 2 + física 7 + Corrida 1)", 10, n);
        // ordem: tipo (Estudo, depois Físico), dentro do tipo por nome sem maiúsculas (Cálculo < física)
        // Cálculo entra na manhã (dia vazio); física cai na tarde da segunda (turno mais vazio) e o
        // hábito a mantém na tarde nos outros dias; Corrida, na terça, pega a manhã (mais vazia).
        eq("autofill: plano montado",
                "1m=[Cálculo] 1t=[física] 2m=[Corrida] 2t=[física] 3m=[Cálculo] 3t=[física] "
              + "4t=[física] 5t=[física] 6t=[física] 7t=[física]", t.planoTexto());
        eq("autofill: de novo não duplica", 0, t.wp.autofill());

        // completar: mantém o que existe e respeita o hábito
        TodayPanelsTest u = new TodayPanelsTest();
        u.area("Cálculo"); u.goals.put("Cálculo", 30);
        u.planeja(1, 2, "Cálculo"); u.planeja(2, 0, "Outra coisa");
        eq("autofill: completa 6 dias", 6, u.wp.autofill());
        eq("autofill: segue o hábito (noite) e mantém o que havia",
                "1n=[Cálculo] 2m=[Outra coisa] 2n=[Cálculo] 3n=[Cálculo] 4n=[Cálculo] 5n=[Cálculo] 6n=[Cálculo] 7n=[Cálculo]",
                u.planoTexto());
    }

    // ── TodayPanels ─────────────────────────────────────────────────────────
    static void telas() {
        eq("data por extenso", "Quarta-feira, 18 de novembro", TodayPanels.dateText(LocalDate.of(2026, 11, 18)));
        eq("meta: feito", "feito", TodayPanels.goalText(90, 30, 90));
        eq("meta: mínimo feito", "40m · mínimo feito", TodayPanels.goalText(40, 30, 90));
        eq("meta: abaixo do mínimo", "20m / 1h30", TodayPanels.goalText(20, 30, 90));

        LocalDate hoje = LocalDate.now();
        int dow = hoje.getDayOfWeek().getValue();
        int agora = PlannerPanel.slotAt(LocalTime.now());

        // vazio
        TodayPanelsTest v = new TodayPanelsTest();
        TodayPanels pv = v.panels();
        pv.refresh();
        eq("vazio: cabeçalho", List.of(TodayPanels.dateText(hoje), "Um bloco hoje já conta"), textos(pv.header()));
        eq("vazio: turnos", List.of("Turnos de hoje", "Nada planejado para hoje.", "[Preencher pelas metas]"),
                textos(pv.shiftsCard()));
        eq("vazio: metas", List.of("Metas de hoje", "[editar metas]", "Nenhuma meta para hoje."), textos(pv.goalsCard()));
        List<String> sem = textos(pv.weekStrip());
        eq("vazio: semana com 7 dias livres", 7, Collections.frequency(sem, "livre"));
        eq("vazio: semana começa na segunda", PlannerPanel.DOW[0], sem.get(2));
        clicar(pv.shiftsCard(), "Preencher pelas metas");
        clicar(pv.goalsCard(), "editar metas");
        clicar(pv.weekStrip(), "editar semana");
        eq("botões ⇒ ações", List.of("fillPlan", "manageGoals", "openPlanner"), v.chamadas);

        // com dados
        TodayPanelsTest t = new TodayPanelsTest();
        t.area("Cálculo"); t.area("Corrida"); t.area("Inglês"); t.area("Velha");
        t.tipo.put("Corrida", "fisico");
        t.archived.add("Velha");
        t.goals.put("Corrida", 60); t.goals.put("Cálculo", 90); t.goalsMin.put("Cálculo", 30);
        t.goals.put("Inglês", 30); t.goalDays.put("Inglês", EnumSet.complementOf(EnumSet.of(hoje.getDayOfWeek())));
        t.goals.put("Velha", 30);
        t.sessao("Cálculo", 40, hoje);
        t.sessao("Inglês", 10, hoje.minusDays(3));
        t.hardDays.add(hoje.minusDays(5).toString());
        t.planeja(dow, agora, "Cálculo", "Inglês");
        t.planeja(dow, (agora + 1) % 3, "Velha");
        TodayPanels p = t.panels();
        p.refresh();

        eq("cabeçalho: dias ativos (hoje, há 3 dias, dia difícil há 5)", "3 dos últimos 7 dias", textos(p.header()).get(1));
        eq("activeDaysLast7", 3, p.activeDaysLast7(hoje));

        List<String> metas = textos(p.goalsCard());
        eq("metas de hoje: por tipo (Estudo antes de Físico); sem meta hoje e arquivada fora",
                List.of("Metas de hoje", "[editar metas]", "Cálculo", "40m · mínimo feito", "Corrida", "0m / 1h00"), metas);

        List<String> turnos = textos(p.shiftsCard());
        String rotuloAgora = PlannerPanel.SLOT_LABEL[agora] + " · agora";
        eq("turnos: o atual marcado", true, turnos.contains(rotuloAgora));
        eq("turnos: área feita hoje", true, turnos.contains("Cálculo · feito"));
        eq("turnos: área por fazer", true, turnos.contains("Inglês"));
        eq("turnos: arquivada não aparece", false, turnos.contains("Velha"));
        eq("turnos: os outros dois livres", 2, Collections.frequency(turnos, "livre"));
        clicar(p.shiftsCard(), "Inglês");
        clicar(p.shiftsCard(), "Cálculo · feito");   // feito não é clicável
        eq("turnos: clicar na área começa", List.of("start Inglês"), t.chamadas);

        List<String> semana = textos(p.weekStrip());
        eq("semana: hoje tem as áreas planejadas (sem 'livre')", 6, Collections.frequency(semana, "livre"));

        // dia difícil: metas não cobram
        t.hardDays.add(hoje.toString());
        p.refresh();
        eq("dia difícil: metas", List.of("Metas de hoje", "[editar metas]", "Dia difícil: nada é cobrado hoje."),
                textos(p.goalsCard()));
        p.refresh();
        eq("refresh não duplica", 1, Collections.frequency(textos(p.shiftsCard()), rotuloAgora));
    }
}
