import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;

/** Testes dos diálogos da tela Áreas (as janelas respondem pelo Prompt, sem abrir nada). */
public class AreaDialogsTest {

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
    Map<String, Integer> goals = new LinkedHashMap<>(), goalsMin = new LinkedHashMap<>(),
                         goalsWeek = new LinkedHashMap<>(), goalsMonth = new LinkedHashMap<>(),
                         streakBest = new LinkedHashMap<>();
    Map<String, Set<DayOfWeek>> goalDays = new LinkedHashMap<>();
    Map<String, LocalDate> examDate = new LinkedHashMap<>();
    Set<String> archived = new LinkedHashSet<>();
    Map<String, String> areaType = new LinkedHashMap<>();
    Map<String, List<String>> subFocos = new LinkedHashMap<>();
    Map<String, List<String>> weekPlan = new LinkedHashMap<>();
    Areas areas = new Areas(data, sessions, checklist, goals, goalsMin, goalsWeek, goalsMonth,
            streakBest, goalDays, examDate, archived, areaType, subFocos, weekPlan);

    // combo / sessão
    Object combo;
    boolean contando;
    List<String> chamadas = new ArrayList<>();

    // respostas das janelas: cada confirm consome uma; null = não esperava janela
    Deque<Function<Object, Integer>> respostas = new ArrayDeque<>();
    List<String> janelas = new ArrayList<>();
    Color corEscolhida;

    AreaDialogs dlg = new AreaDialogs(data, archived, areaType, subFocos, areas, new AreaDialogs.Host() {
        public void changed()               { chamadas.add("changed"); }
        public void toast(String msg)       { chamadas.add("toast " + msg); }
        public void refreshCombo()          { chamadas.add("refreshCombo"); }
        public Object selectedArea()        { return combo; }
        public void selectArea(Object a)    { chamadas.add("select " + a); combo = a; }
        public boolean sessionInProgress()  { return contando; }
        public void saveData()              { chamadas.add("saveData"); }
        public void saveSessions()          { chamadas.add("saveSessions"); }
        public void saveChecklist()         { chamadas.add("saveChecklist"); }
    }, new AreaDialogs.Prompt() {
        public int confirm(Object msg, String title, int opt, int type) {
            janelas.add(title + " | " + opt + "/" + type);
            Function<Object, Integer> r = respostas.poll();
            if (r == null) throw new IllegalStateException("janela inesperada: " + title);
            return r.apply(msg);
        }
        public Color chooseColor(String title, Color initial) {
            janelas.add(title + " | " + initial.getRGB());
            return corEscolhida;
        }
    });

    void area(String nome) { data.put(nome, new StudyData(90, Color.BLUE)); }

    /** Responde OK preenchendo o formulário Nome/Tipo. */
    void nomeTipo(String nome, int tipo) {
        respostas.add(msg -> {
            JPanel p = (JPanel) msg;
            for (Component k : p.getComponents()) {
                if (k instanceof JTextField f) f.setText(nome);
                if (k instanceof JComboBox<?> cb && tipo >= 0) cb.setSelectedIndex(tipo);
            }
            return JOptionPane.OK_OPTION;
        });
    }

    void responde(int r) { respostas.add(m -> r); }

    public static void main(String[] args) {
        regras();
        nova();
        editar();
        corZerar();
        arquivarDeletar();
        subFocos();
        System.out.println("AreaDialogs: " + (total - falhas) + "/" + total + " testes passaram");
        System.exit(falhas > 0 ? 1 : 0);
    }

    static void regras() {
        eq("sugestões físico", "[Peito, Costas, Perna, Ombro, Braço, Core, Cardio]",
                Arrays.toString(AreaDialogs.subFocoPreset("fisico")));
        eq("sugestões estudo", "[Teoria, Exercícios, Revisão]", Arrays.toString(AreaDialogs.subFocoPreset("estudo")));
        eq("lazer sem sugestões", 0, AreaDialogs.subFocoPreset("lazer").length);
        eq("usar sugestões junta sem repetir", "Revisão\nCap 3\nTeoria\nExercícios",
                AreaDialogs.withPreset("Revisão\n\n  Cap 3  \nRevisão", "estudo"));
        eq("lê sub-focos", List.of("Cap 1", "a/b", "‹html>x"),
                AreaDialogs.parseSubFocos("  Cap 1 \n\ncap 1\na|b\n<html>x\nCAP 1"));
        eq("texto de zerar", "Isto zera TODO o tempo acumulado de \"Cálculo\" (1h30).\n"
                + "O histórico de sessões é mantido. Continuar?\n\n"
                + "Para apenas reiniciar o cronômetro, use o botão \"Zerar\" na aba Cronômetro.",
                AreaDialogs.zeroText("Cálculo", 90));
        eq("texto de arquivar", "Arquivar \"X\"?\nEla sai da lista, mas o tempo e o histórico são mantidos.\n"
                + "Para reativar, use \"Reativar\" na tela Áreas.", AreaDialogs.archiveText("X"));
        JComboBox<String> cb = AreaDialogs.tipoCombo("lazer");
        eq("combo de tipo: itens", 5, cb.getItemCount());
        eq("combo de tipo: selecionado", "Lazer", cb.getSelectedItem());
        eq("combo de tipo: desconhecido = Estudo", "Estudo", AreaDialogs.tipoCombo("??").getSelectedItem());
        AreaDialogs.NameTypeForm f = new AreaDialogs.NameTypeForm("Violão", "lazer");
        eq("form editar: nome", "Violão", f.fName.getText());
        eq("form editar: tipo", "lazer", f.typeKey());
        eq("form nova: nome vazio", "", new AreaDialogs.NameTypeForm(null, "estudo").fName.getText());
    }

    static void nova() {
        AreaDialogsTest t = new AreaDialogsTest();
        t.area("A");
        t.nomeTipo("  Treino  ", 1);
        t.dlg.add();
        eq("cria com tipo", "fisico", t.areaType.get("Treino"));
        eq("cria no fim", "[A, Treino]", t.data.keySet().toString());
        eq("sequência", List.of("refreshCombo", "select Treino", "changed"), t.chamadas);
        eq("janela", List.of("Nova área | 2/-1"), t.janelas);

        t.chamadas.clear(); t.contando = true; t.combo = "A";
        t.nomeTipo("Lazer", 2);
        t.dlg.add();
        eq("com sessão: não troca o combo", List.of("refreshCombo", "changed"), t.chamadas);
        eq("com sessão: combo fica", "A", t.combo);
        t.contando = false;

        t.chamadas.clear();
        t.nomeTipo("A", 0);
        t.dlg.add();
        eq("já existe", List.of("toast Essa área já existe!"), t.chamadas);

        t.chamadas.clear();
        t.nomeTipo("   ", 0);
        t.dlg.add();
        eq("nome vazio: nada", List.of(), t.chamadas);

        t.chamadas.clear();
        t.responde(JOptionPane.CANCEL_OPTION);
        t.dlg.add();
        eq("cancelar: nada", List.of(), t.chamadas);
        eq("cancelar: nada criado", 3, t.data.size());

        t.chamadas.clear();
        t.archived.add("Treino");
        t.nomeTipo("Treino", 3);
        t.dlg.add();
        eq("nome de arquivada: desarquiva", false, t.archived.contains("Treino"));
        eq("desarquivar mantém o tipo antigo", "fisico", t.areaType.get("Treino"));
        eq("desarquivar: sequência", List.of("refreshCombo", "select Treino", "changed", "toast \"Treino\" desarquivada."), t.chamadas);

        t.chamadas.clear();
        t.dlg.unarchive("Treino");
        eq("desarquivar o que não está arquivado: nada", List.of(), t.chamadas);
    }

    static void editar() {
        AreaDialogsTest t = new AreaDialogsTest();
        t.area("A"); t.area("B");
        t.combo = "B";
        t.dlg.edit(null); t.dlg.edit("Nenhuma");
        eq("inexistente: sem janela", List.of(), t.janelas);

        t.nomeTipo("A", 2);
        t.dlg.edit("A");
        eq("só o tipo", "lazer", t.areaType.get("A"));
        eq("só o tipo: sequência (combo volta ao de antes)", List.of("refreshCombo", "select B", "changed"), t.chamadas);
        eq("janela", "Editar área | 2/-1", t.janelas.get(0));

        t.chamadas.clear();
        t.nomeTipo("B", 0);
        t.dlg.edit("A");
        eq("nome repetido", List.of("toast Esse nome já existe!"), t.chamadas);
        eq("nome repetido: o tipo já mudou (igual a antes)", null, t.areaType.get("A"));

        t.chamadas.clear();
        t.sessions.add(new StudySession("A", 30, 1L, "manual"));
        t.nomeTipo("  Álgebra ", -1);
        t.dlg.edit("A");
        eq("renomeia na mesma posição", "[Álgebra, B]", t.data.keySet().toString());
        eq("sessões vão junto", "Álgebra", t.sessions.get(0).getSubject());
        eq("renomear outra: combo fica", List.of("refreshCombo", "select B", "saveChecklist", "saveSessions", "changed"), t.chamadas);

        t.chamadas.clear();
        t.nomeTipo("Bê", -1);
        t.dlg.edit("B");
        eq("renomear a do combo: combo segue", List.of("refreshCombo", "select Bê", "saveChecklist", "saveSessions", "changed"), t.chamadas);

        t.chamadas.clear();
        t.responde(JOptionPane.CANCEL_OPTION);
        t.dlg.edit("Bê");
        eq("cancelar: nada", List.of(), t.chamadas);
    }

    static void corZerar() {
        AreaDialogsTest t = new AreaDialogsTest();
        t.area("A");
        t.corEscolhida = null;
        t.dlg.color("A");
        eq("cor cancelada", List.of(), t.chamadas);
        eq("janela de cor", List.of("Cor de A | " + Color.BLUE.getRGB()), t.janelas);
        t.corEscolhida = Color.RED;
        t.dlg.color("A");
        eq("cor escolhida", Color.RED, t.data.get("A").getColor());
        eq("cor: atualiza", List.of("changed"), t.chamadas);
        t.dlg.color("X");
        eq("cor de inexistente: sem janela", 2, t.janelas.size());

        t.chamadas.clear(); t.janelas.clear();
        String[] msg = {null};
        t.respostas.add(m -> { msg[0] = (String) m; return JOptionPane.NO_OPTION; });
        t.dlg.zero("A");
        eq("zerar: pergunta com o total", AreaDialogs.zeroText("A", 90), msg[0]);
        eq("zerar: janela", List.of("Zerar matéria | 0/2"), t.janelas);
        eq("zerar: não", 90, t.data.get("A").getMinutes());
        t.responde(JOptionPane.YES_OPTION);
        t.dlg.zero("A");
        eq("zerar: sim", 0, t.data.get("A").getMinutes());
        eq("zerar: avisos", List.of("changed", "toast Tempo de \"A\" zerado."), t.chamadas);
    }

    static void arquivarDeletar() {
        AreaDialogsTest t = new AreaDialogsTest();
        t.area("A"); t.area("B");
        t.combo = "A"; t.contando = true;
        t.dlg.archive("A");
        t.dlg.delete("A");
        eq("área da sessão: bloqueia", List.of("toast Termine ou zere a sessão de \"A\" antes.",
                "toast Termine ou zere a sessão de \"A\" antes."), t.chamadas);
        eq("área da sessão: sem janela", List.of(), t.janelas);

        t.chamadas.clear();
        t.responde(JOptionPane.YES_OPTION);
        t.dlg.archive("B");   // outra área: pode, mesmo contando
        eq("arquiva", true, t.archived.contains("B"));
        eq("arquivar: sequência", List.of("refreshCombo", "changed", "toast \"B\" arquivada."), t.chamadas);
        eq("arquivar: janela", List.of("Arquivar matéria | 0/3"), t.janelas);
        t.contando = false;

        t.chamadas.clear();
        t.dlg.archive("A");
        eq("última ativa: não arquiva", List.of("toast Deixe ao menos uma matéria ativa."), t.chamadas);
        t.chamadas.clear();
        t.dlg.delete("A");
        eq("última ativa: não deleta", List.of("toast Deixe ao menos uma área ativa."), t.chamadas);

        t.chamadas.clear();
        t.responde(JOptionPane.NO_OPTION);
        t.dlg.delete("B");
        eq("deletar: não", true, t.data.containsKey("B"));
        eq("deletar: não, nada", List.of(), t.chamadas);
        t.responde(JOptionPane.YES_OPTION);
        t.dlg.delete("B");   // arquivada pode
        eq("deletar arquivada", "[A]", t.data.keySet().toString());
        eq("deletar: sai das arquivadas", false, t.archived.contains("B"));
        eq("deletar: sequência", List.of("saveChecklist", "refreshCombo", "changed"), t.chamadas);
        eq("deletar: janela", "Deletar Matéria | 0/2", t.janelas.get(t.janelas.size() - 1));

        t.chamadas.clear();
        t.responde(JOptionPane.NO_OPTION);
        t.dlg.archive("Nenhuma");
        t.dlg.delete(null);
        eq("inexistente: nada", List.of(), t.chamadas);
        eq("inexistente: resposta não usada", 1, t.respostas.size());
    }

    static void subFocos() {
        AreaDialogsTest t = new AreaDialogsTest();
        t.area("Treino"); t.areaType.put("Treino", "fisico");
        t.area("Violão"); t.areaType.put("Violão", "lazer");
        t.subFocos.put("Treino", new ArrayList<>(List.of("Perna", "Mobilidade")));

        AreaDialogs.SubFocosForm f = t.dlg.new SubFocosForm("Treino");
        eq("texto atual", "Perna\nMobilidade", f.ta.getText());
        eq("botão de sugestões", "Usar sugestões de Físico", f.preset.getText());
        f.preset.doClick();
        eq("sugestões juntam", "Perna\nMobilidade\nPeito\nCostas\nOmbro\nBraço\nCore\nCardio", f.ta.getText());
        eq("lazer: botão desligado", false, t.dlg.new SubFocosForm("Violão").preset.isEnabled());

        t.respostas.add(m -> {
            JTextArea ta = acharTa((Container) m);
            ta.setText("Costas\n\ncostas\nPerna|Glúteo\n");
            return JOptionPane.OK_OPTION;
        });
        t.dlg.subFocos("Treino");
        eq("grava", List.of("Costas", "Perna/Glúteo"), t.subFocos.get("Treino"));
        eq("grava e avisa", List.of("saveData", "toast Sub-focos de \"Treino\" atualizados."), t.chamadas);
        eq("janela", List.of("Sub-focos | 2/-1"), t.janelas);

        t.chamadas.clear();
        t.respostas.add(m -> { acharTa((Container) m).setText("  \n"); return JOptionPane.OK_OPTION; });
        t.dlg.subFocos("Treino");
        eq("vazio remove", false, t.subFocos.containsKey("Treino"));

        t.chamadas.clear();
        t.responde(JOptionPane.CANCEL_OPTION);
        t.dlg.subFocos("Violão");
        eq("cancelar: nada", List.of(), t.chamadas);
    }

    static JTextArea acharTa(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JTextArea ta) return ta;
            if (k instanceof Container cc) { JTextArea ta = acharTa(cc); if (ta != null) return ta; }
        }
        return null;
    }
}
