import java.awt.Color;
import java.time.*;
import java.util.*;
import java.util.List;

/** Testes dos avisos automáticos: versão nova, lembrete diário e resumo da semana. */
public class NoticesTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    static final ZoneId ZONA = ZoneId.systemDefault();
    static final LocalDate QUA = LocalDate.of(2026, 11, 18);   // quarta-feira, semana ISO 47

    // ── estado "do app" ─────────────────────────────────────────────────────
    Map<String, StudyData> data = new LinkedHashMap<>();
    List<StudySession> sessions = new ArrayList<>();
    Set<String> archived = new LinkedHashSet<>();
    Map<String, Integer> goals = new LinkedHashMap<>(), goalsMin = new LinkedHashMap<>(),
                         goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>();
    Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    Set<String> hardDays = new HashSet<>();
    StudyStats stats = new StudyStats(sessions, data.keySet(), archived, goals, goalsMin, goalsWeek,
            goalsMonth, goalDays, hardDays, Clock.systemDefaultZone());
    AppSettings cfg = new AppSettings();
    List<String> chamadas = new ArrayList<>();

    Notices notices = new Notices(null, cfg, stats, data.keySet(), sessions, new Notices.Host() {
        public void save()            { chamadas.add("save"); }
        public void toast(String msg) { chamadas.add("toast " + msg); }
        public void trayClicked()     { chamadas.add("tray"); }
    });

    void area(String nome) { data.put(nome, new StudyData(0, Color.BLUE)); }

    void sessao(String area, int min, LocalDate dia) {
        sessions.add(new StudySession(area, min, dia.atTime(9, 0).atZone(ZONA).toInstant().toEpochMilli(), "manual"));
    }

    public static void main(String[] args) {
        versao();
        lembrete();
        semana();
        resumo();
        lembreteNaClasse();
        resumoNaClasse();
        System.out.println("Notices: " + (total - falhas) + "/" + total + " testes passaram");
        System.exit(falhas > 0 ? 1 : 0);
    }

    // ── versão nova ─────────────────────────────────────────────────────────

    static void versao() {
        String base = "https://github.com/ErickDraber/FocaEstudo/releases/tag/";
        eq("versão com v", "1.4.0", Notices.versionFromLocation(base + "v1.4.0"));
        eq("versão sem v", "2.10.3", Notices.versionFromLocation(base + "2.10.3"));
        eq("sem Location", null, Notices.versionFromLocation(null));
        eq("página de releases (sem tag)", null,
                Notices.versionFromLocation("https://github.com/ErickDraber/FocaEstudo/releases"));
        eq("tag com sufixo", null, Notices.versionFromLocation(base + "v1.4.0-beta"));
        eq("só dois números", null, Notices.versionFromLocation(base + "v1.4"));

        eq("1.10.0 > 1.9.2 (número, não texto)", 1, Notices.compareVersions("1.10.0", "1.9.2"));
        eq("1.4.0 < 1.4.1", -1, Notices.compareVersions("1.4.0", "1.4.1"));
        eq("iguais", 0, Notices.compareVersions("1.4.0", "1.4.0"));
        eq("1.4 == 1.4.0", 0, Notices.compareVersions("1.4", "1.4.0"));
        eq("2.0.0 > 1.99.99", 1, Notices.compareVersions("2.0.0", "1.99.99"));
        eq("pedaço inválido vale 0", 0, Notices.compareVersions("1.x.0", "1.0.0"));

        eq("texto da versão nova",
                "Saiu a versão 1.5.0 do FocaEstudo (você está usando a 1.4.0).\n\n"
              + "Para atualizar: baixe o zip, feche o FocaEstudo e extraia por cima da pasta atual.\n"
              + "Seus dados ficam guardados fora da pasta do programa, então nada se perde.",
                Notices.updateText("1.4.0", "1.5.0"));
    }

    // ── lembrete diário ─────────────────────────────────────────────────────

    static void lembrete() {
        Notices.Reminder W = Notices.Reminder.WAIT, P = Notices.Reminder.SKIP_DAY, S = Notices.Reminder.SHOW;
        String ontem = QUA.minusDays(1).toString();
        eq("desligado", W, Notices.reminderRule(-1, null, QUA, 23, false, 0));
        eq("já mostrado hoje", W, Notices.reminderRule(20, QUA.toString(), QUA, 23, false, 0));
        eq("já mostrado hoje vence o dia difícil", W, Notices.reminderRule(20, QUA.toString(), QUA, 23, true, 0));
        eq("dia difícil marca mesmo antes da hora", P, Notices.reminderRule(20, ontem, QUA, 8, true, 0));
        eq("antes da hora", W, Notices.reminderRule(20, ontem, QUA, 19, false, 0));
        eq("antes da hora não olha os minutos", W, Notices.reminderRule(20, ontem, QUA, 19, false, 30));
        eq("na hora, já estudou", P, Notices.reminderRule(20, ontem, QUA, 20, false, 1));
        eq("na hora, sem estudo", S, Notices.reminderRule(20, ontem, QUA, 20, false, 0));
        eq("nunca mostrado, depois da hora", S, Notices.reminderRule(0, null, QUA, 0, false, 0));
    }

    // ── resumo da semana: quando ────────────────────────────────────────────

    static void semana() {
        eq("chave da semana", "2026-W47", Notices.weekKey(QUA));
        eq("chave: domingo ainda é a mesma semana", "2026-W47", Notices.weekKey(QUA.plusDays(4)));
        eq("chave: segunda vira", "2026-W48", Notices.weekKey(QUA.plusDays(5)));
        eq("chave: ano ISO (1º/jan/2027 é W53 de 2026)", "2026-W53", Notices.weekKey(LocalDate.of(2027, 1, 1)));
        eq("chave sem zero à esquerda", "2026-W2", Notices.weekKey(LocalDate.of(2026, 1, 7)));

        String passada = "2026-W46";
        eq("nunca mostrou: mostra até na quarta", true, Notices.weeklyDue(null, QUA));
        eq("quarta, já mostrou outra semana: não", false, Notices.weeklyDue(passada, QUA));
        eq("sexta: não", false, Notices.weeklyDue(passada, QUA.plusDays(2)));
        eq("sábado: mostra", true, Notices.weeklyDue(passada, QUA.plusDays(3)));
        eq("domingo: mostra", true, Notices.weeklyDue(passada, QUA.plusDays(4)));
        eq("sábado, já mostrou esta semana: não", false, Notices.weeklyDue("2026-W47", QUA.plusDays(3)));
    }

    // ── resumo da semana: texto ─────────────────────────────────────────────

    static void resumo() {
        NoticesTest t = new NoticesTest();
        t.area("Cálculo"); t.area("Física"); t.area("Inglês");
        eq("sem estudo: sem resumo", null, Notices.weeklySummaryText(t.stats, t.data.keySet(), QUA));
        t.sessao("Cálculo", 90, QUA.minusDays(7));   // só na semana anterior
        eq("só semana anterior: sem resumo", null, Notices.weeklySummaryText(t.stats, t.data.keySet(), QUA));

        // sem meta, semana anterior maior (▼); empate de minutos fica com a primeira área
        t.sessao("Física", 40, QUA);
        t.sessao("Inglês", 40, QUA.minusDays(6));
        eq("sem meta, queda",
                "Últimos 7 dias\n\n"
              + "• Total estudado: 1h20\n"
              + "• Dias com estudo: 2 de 7\n"
              + "• Matéria destaque: Física (40m)\n"
              + "• vs. semana anterior: ▼ 10m\n"
              + "\nBora manter o ritmo!",
                Notices.weeklySummaryText(t.stats, t.data.keySet(), QUA));

        // com meta diária (soma das metas = 60), subida (▲) e um dia fora da janela
        t.goals.put("Cálculo", 30); t.goals.put("Física", 30);
        t.sessao("Cálculo", 60, QUA.minusDays(1));
        t.sessao("Cálculo", 15, QUA.minusDays(2));
        t.sessao("Física", 500, QUA.plusDays(1));    // amanhã: fora
        eq("com meta, subida",
                "Últimos 7 dias\n\n"
              + "• Total estudado: 2h35\n"
              + "• Dias com estudo: 4 de 7\n"
              + "• Meta diária batida em 1 de 7 dias\n"
              + "• Matéria destaque: Cálculo (1h15)\n"
              + "• vs. semana anterior: ▲ +1h05\n"
              + "\nBora manter o ritmo!",
                Notices.weeklySummaryText(t.stats, t.data.keySet(), QUA));

        // semana anterior zerada: sem a linha de comparação; empate exato = "▲ +0m"
        NoticesTest u = new NoticesTest();
        u.area("A");
        u.sessao("A", 30, QUA);
        eq("sem semana anterior", false,
                Notices.weeklySummaryText(u.stats, u.data.keySet(), QUA).contains("vs. semana anterior"));
        u.sessao("A", 30, QUA.minusDays(10));
        eq("empate com a anterior", true,
                Notices.weeklySummaryText(u.stats, u.data.keySet(), QUA).contains("• vs. semana anterior: ▲ +0m\n"));
    }

    // ── a classe, com a data de hoje de verdade ─────────────────────────────

    static void lembreteNaClasse() {
        String hoje = LocalDate.now().toString();

        NoticesTest t = new NoticesTest();
        t.notices.checkReminder();
        eq("desligado: nada", List.of(), t.chamadas);
        eq("desligado: não marca", null, t.cfg.reminderShownDay);

        t.cfg.reminderHour = 0;   // qualquer hora já passou
        t.notices.checkReminder();
        eq("sem estudo: grava e avisa (sem bandeja vira toast)",
                List.of("save", "toast " + Notices.REMINDER_MSG), t.chamadas);
        eq("marca o dia", hoje, t.cfg.reminderShownDay);
        t.chamadas.clear();
        t.notices.checkReminder();
        eq("de novo no mesmo dia: nada", List.of(), t.chamadas);

        NoticesTest d = new NoticesTest();
        d.cfg.reminderHour = 0;
        d.hardDays.add(hoje);
        d.notices.checkReminder();
        eq("dia difícil: sem aviso e sem gravar", List.of(), d.chamadas);
        eq("dia difícil: marca o dia", hoje, d.cfg.reminderShownDay);

        NoticesTest e = new NoticesTest();
        e.cfg.reminderHour = 0;
        e.area("A");
        e.sessao("A", 5, LocalDate.now());
        e.notices.checkReminder();
        eq("já estudou: sem aviso e sem gravar", List.of(), e.chamadas);
        eq("já estudou: marca o dia", hoje, e.cfg.reminderShownDay);

        NoticesTest h = new NoticesTest();
        h.cfg.reminderHour = 24;  // nunca chega a hora
        h.notices.checkReminder();
        eq("antes da hora: nada", List.of(), h.chamadas);
        eq("antes da hora: não marca", null, h.cfg.reminderShownDay);
    }

    static void resumoNaClasse() {
        LocalDate hoje = LocalDate.now();

        NoticesTest t = new NoticesTest();
        t.notices.maybeShowWeeklySummary();
        eq("sem sessões: nem marca", null, t.cfg.weeklySummaryShownWeek);
        eq("sem sessões: não grava", List.of(), t.chamadas);

        // nunca mostrou + só estudo antigo: marca e grava, mas não abre diálogo (texto null)
        t.area("A");
        t.sessao("A", 30, hoje.minusDays(30));
        t.notices.maybeShowWeeklySummary();
        eq("marca a semana", Notices.weekKey(hoje), t.cfg.weeklySummaryShownWeek);
        eq("grava antes de montar o texto", List.of("save"), t.chamadas);
        t.chamadas.clear();
        t.notices.maybeShowWeeklySummary();
        eq("mesma semana: nada", List.of(), t.chamadas);
    }
}
