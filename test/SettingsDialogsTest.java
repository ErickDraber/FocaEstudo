import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.function.Function;

/** Testes de Ajustes, exportar/importar backup e tema (as janelas respondem pelo Prompt). */
public class SettingsDialogsTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    final Path dir;
    final StudyStore store;
    AppSettings cfg = new AppSettings();
    List<String> chamadas = new ArrayList<>();
    List<String> janelas = new ArrayList<>();
    Deque<Function<Object, Integer>> respostas = new ArrayDeque<>();
    File arquivoSalvar, arquivoAbrir;
    File sugerido;
    boolean pastaFalha;
    int tema = -1;

    SettingsDialogs dlg;

    SettingsDialogsTest() {
        try { dir = Files.createTempDirectory("focaestudo-ajustes"); } catch (Exception e) { throw new RuntimeException(e); }
        store = new StudyStore(dir.toFile());
        dlg = new SettingsDialogs(cfg, store, new SettingsDialogs.Host() {
            public void saveData()             { chamadas.add("saveData"); }
            public void saveAll()              { chamadas.add("saveAll"); }
            public void reloadFromDisk()       { chamadas.add("reload"); }
            public void rebuildUI()            { chamadas.add("rebuild " + (AppTheme.dark ? "escuro" : "claro")); }
            public void setAutoCycle(boolean on) { chamadas.add("autoCycle " + on); }
            public void toast(String msg)      { chamadas.add("toast " + msg); }
        }, new SettingsDialogs.Prompt() {
            public int confirm(Object msg, String title, int opt, int type) {
                janelas.add(title + " | " + opt + "/" + type);
                Function<Object, Integer> r = respostas.poll();
                if (r == null) throw new IllegalStateException("janela inesperada: " + title);
                return r.apply(msg);
            }
            public int option(String msg, String title, Object[] options, Object initial) {
                janelas.add(title + " | " + msg + " | " + Arrays.toString(options) + " | " + initial);
                return tema;
            }
            public void message(String msg)            { janelas.add("msg " + msg); }
            public void info(String title, String msg) { janelas.add("info " + title + " | " + msg); }
            public File saveFile(File s)               { sugerido = s; janelas.add("salvar"); return arquivoSalvar; }
            public File openFile()                     { janelas.add("abrir"); return arquivoAbrir; }
            public void openFolder(File d) throws Exception {
                janelas.add("pasta " + d.getName());
                if (pastaFalha) throw new Exception("sem Desktop");
            }
        });
    }

    void escrever(String nome, String txt) throws Exception {
        Files.write(dir.resolve(nome), txt.getBytes(StandardCharsets.ISO_8859_1));
    }

    String ler(String nome) throws Exception {
        return new String(Files.readAllBytes(dir.resolve(nome)), StandardCharsets.ISO_8859_1);
    }

    public static void main(String[] args) throws Exception {
        regras();
        ajustes();
        pasta();
        exportar();
        importar();
        tema();
        System.out.println("SettingsDialogs: " + (total - falhas) + "/" + total + " testes passaram");
        System.exit(falhas > 0 ? 1 : 0);
    }

    static void regras() {
        eq("lembrete desligado", -1, SettingsDialogs.reminderHourOf("Desligado"));
        eq("lembrete nulo", -1, SettingsDialogs.reminderHourOf(null));
        eq("lembrete 07:00", 7, SettingsDialogs.reminderHourOf("07:00"));
        eq("lembrete 23:00", 23, SettingsDialogs.reminderHourOf("23:00"));
        eq("item do lembrete", "08:00", SettingsDialogs.reminderItem(8));
        eq("item desligado", "Desligado", SettingsDialogs.reminderItem(-1));
        eq("nome do backup", "focaestudo-backup-20261117.txt", SettingsDialogs.backupFileName(LocalDate.of(2026, 11, 17)));
    }

    static void ajustes() {
        SettingsDialogsTest t = new SettingsDialogsTest();
        t.cfg.minFocusMin = 3; t.cfg.idleMinutes = 10; t.cfg.transitionMin = 7; t.cfg.hyperfocusH = 2;
        t.cfg.pomoAutoCycle = true; t.cfg.focusMode = false; t.cfg.askDetails = true; t.cfg.updateCheck = false;
        t.cfg.reminderHour = 20;
        SettingsDialogs.SettingsForm f = t.dlg.new SettingsForm();
        eq("foco mínimo", 3, f.spFocus.getValue());
        eq("inatividade", 10, f.spIdle.getValue());
        eq("transição", 7, f.spTrans.getValue());
        eq("hiperfoco", 2, f.spHyper.getValue());
        eq("ciclo", true, f.cbCycle.isSelected());
        eq("modo foco", false, f.cbFocus.isSelected());
        eq("perguntar", true, f.cbAsk.isSelected());
        eq("atualizações", false, f.cbUpdate.isSelected());
        eq("lembrete", "20:00", f.cbReminder.getSelectedItem());
        eq("itens do lembrete", 20, f.cbReminder.getItemCount());
        eq("primeiro horário", "05:00", f.cbReminder.getItemAt(1));
        eq("botões de backup", "Exportar dados…|Importar dados…|Abrir pasta dos dados",
                f.btnExport.getText() + "|" + f.btnImport.getText() + "|" + f.btnFolder.getText());

        // cancelar não grava
        t.respostas.add(m -> JOptionPane.CANCEL_OPTION);
        t.dlg.showSettings();
        eq("cancelar: nada", List.of(), t.chamadas);
        eq("janela", List.of("Ajustes | 2/-1"), t.janelas);

        // OK com tudo mudado
        t.respostas.add(m -> {
            SettingsDialogsTest.preencher((JPanel) m);
            return JOptionPane.OK_OPTION;
        });
        t.dlg.showSettings();
        eq("grava foco mínimo", 5, t.cfg.minFocusMin);
        eq("grava inatividade", 0, t.cfg.idleMinutes);
        eq("grava transição", 30, t.cfg.transitionMin);
        eq("grava hiperfoco", 12, t.cfg.hyperfocusH);
        eq("grava ciclo", false, t.cfg.pomoAutoCycle);
        eq("grava modo foco", true, t.cfg.focusMode);
        eq("grava perguntar", false, t.cfg.askDetails);
        eq("grava atualizações", true, t.cfg.updateCheck);
        eq("grava lembrete desligado", -1, t.cfg.reminderHour);
        eq("sequência", List.of("autoCycle false", "saveData", "toast Ajustes salvos."), t.chamadas);

        // lembrete ligado
        SettingsDialogs.SettingsForm g = t.dlg.new SettingsForm();
        eq("desligado aparece", "Desligado", g.cbReminder.getSelectedItem());
        g.cbReminder.setSelectedItem("06:00");
        g.apply();
        eq("grava 06:00", 6, t.cfg.reminderHour);
    }

    /** Muda todos os campos do formulário pela ordem em que aparecem. */
    static void preencher(Container p) {
        List<JSpinner> sp = new ArrayList<>();
        List<JCheckBox> cb = new ArrayList<>();
        List<JComboBox<?>> co = new ArrayList<>();
        juntar(p, sp, cb, co);
        int[] v = {5, 0, 30, 12};
        for (int i = 0; i < 4; i++) sp.get(i).setValue(v[i]);
        for (JCheckBox c : cb) c.setSelected(!c.isSelected());
        co.get(0).setSelectedIndex(0);
    }

    static void juntar(Container c, List<JSpinner> sp, List<JCheckBox> cb, List<JComboBox<?>> co) {
        for (Component k : c.getComponents()) {
            if (k instanceof JSpinner s) { sp.add(s); continue; }
            if (k instanceof JCheckBox b) cb.add(b);
            else if (k instanceof JComboBox<?> x) co.add(x);
            else if (k instanceof Container cc) juntar(cc, sp, cb, co);
        }
    }

    static void pasta() {
        SettingsDialogsTest t = new SettingsDialogsTest();
        SettingsDialogs.SettingsForm f = t.dlg.new SettingsForm();
        f.btnFolder.doClick();
        eq("abre a pasta dos dados", List.of("pasta " + t.dir.getFileName()), t.janelas);
        t.janelas.clear(); t.pastaFalha = true;
        f.btnFolder.doClick();
        eq("sem Desktop: mostra o caminho", List.of("pasta " + t.dir.getFileName(),
                "info Pasta dos dados | " + t.dir.toFile().getAbsolutePath()), t.janelas);
    }

    static void exportar() throws Exception {
        SettingsDialogsTest t = new SettingsDialogsTest();
        t.escrever(StudyStore.DATA_NAME, "Calculo=30,-1\n");
        t.escrever(StudyStore.SESSIONS_NAME, "s0=Calculo|30|1|manual\n");

        t.dlg.exportData();
        eq("cancelar: salva antes", List.of("saveAll"), t.chamadas);
        eq("sugere o nome do dia", SettingsDialogs.backupFileName(LocalDate.now()), t.sugerido.getName());

        t.chamadas.clear();
        File destino = t.dir.resolve("meu-backup.txt").toFile();
        t.arquivoSalvar = destino;
        SettingsDialogs.SettingsForm f = t.dlg.new SettingsForm();
        f.btnExport.doClick();
        eq("exporta", List.of("saveAll", "toast Backup exportado."), t.chamadas);
        String txt = new String(Files.readAllBytes(destino.toPath()), StandardCharsets.UTF_8);
        eq("conteúdo = exportText", t.store.exportText(), txt);
        eq("tem as matérias", true, txt.contains("Calculo=30,-1"));

        t.chamadas.clear(); t.janelas.clear();
        t.arquivoSalvar = t.dir.resolve("nao-existe").resolve("x.txt").toFile();
        t.dlg.exportData();
        eq("falha: avisa", true, t.janelas.get(1).startsWith("msg Falha ao exportar: "));
        eq("falha: sem toast", List.of("saveAll"), t.chamadas);
    }

    static void importar() throws Exception {
        SettingsDialogsTest t = new SettingsDialogsTest();
        t.escrever(StudyStore.DATA_NAME, "Antiga=10,-1\n");
        Path arq = t.dir.resolve("backup.txt");

        t.dlg.importData();
        eq("cancelar: nada", List.of("abrir"), t.janelas);

        t.arquivoAbrir = arq.toFile();
        t.janelas.clear();
        Files.write(arq, "qualquer coisa".getBytes(StandardCharsets.UTF_8));
        t.dlg.importData();
        eq("não é backup", List.of("abrir", "msg Arquivo não parece um backup do FocaEstudo."), t.janelas);

        t.janelas.clear();
        Files.write(arq, (StudyStore.SEP_DATA + "\nlixo\n" + StudyStore.SEP_SESSIONS + "\n").getBytes(StandardCharsets.UTF_8));
        t.dlg.importData();
        eq("sem matérias", List.of("abrir", "msg O backup não tem matérias válidas — importação cancelada."), t.janelas);

        String backup = StudyStore.SEP_DATA + "\nNova=60,-16776961\n" + StudyStore.SEP_SESSIONS
                + "\ns0=Nova|60|1|manual\n" + StudyStore.SEP_TAREFAS + "\nc0=Nova|0|20|Lista\n";
        Files.write(arq, backup.getBytes(StandardCharsets.UTF_8));
        t.janelas.clear();
        String[] msg = {null};
        t.respostas.add(m -> { msg[0] = (String) m; return JOptionPane.NO_OPTION; });
        t.dlg.importData();
        eq("pergunta", SettingsDialogs.IMPORT_TEXT, msg[0]);
        eq("janela de confirmação", List.of("abrir", "Importar dados | 0/2"), t.janelas);
        eq("não: dados intactos", "Antiga=10,-1\n", t.ler(StudyStore.DATA_NAME));
        eq("não: nada chamado", List.of(), t.chamadas);

        t.respostas.add(m -> JOptionPane.YES_OPTION);
        SettingsDialogs.SettingsForm f = t.dlg.new SettingsForm();
        f.btnImport.doClick();
        eq("sim: troca os dados", "Nova=60,-16776961", t.ler(StudyStore.DATA_NAME));
        eq("sim: sessões", "s0=Nova|60|1|manual", t.ler(StudyStore.SESSIONS_NAME));
        eq("sim: tarefas", "c0=Nova|0|20|Lista", t.ler(StudyStore.CHECKLIST_NAME));
        eq("sim: antigos em .bak", "Antiga=10,-1\n", t.ler(StudyStore.DATA_NAME + ".bak"));
        eq("sim: recarrega e avisa", List.of("reload", "toast Dados importados."), t.chamadas);

        // arquivo grande demais (não lê)
        SettingsDialogsTest u = new SettingsDialogsTest();
        File grande = u.dir.resolve("grande.txt").toFile();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(grande, "rw")) { raf.setLength(StudyStore.MAX_IMPORT_BYTES + 1); }
        u.arquivoAbrir = grande;
        u.dlg.importData();
        eq("grande demais", List.of("abrir", "msg Arquivo grande demais para ser um backup do FocaEstudo."), u.janelas);

        // erro ao recarregar: vira mensagem de falha
        SettingsDialogsTest v = new SettingsDialogsTest();
        SettingsDialogs quebra = new SettingsDialogs(v.cfg, v.store, new SettingsDialogs.Host() {
            public void saveData() {} public void saveAll() {} public void rebuildUI() {}
            public void setAutoCycle(boolean on) {} public void toast(String m) { v.chamadas.add("toast " + m); }
            public void reloadFromDisk() { throw new IllegalStateException("boom"); }
        }, new SettingsDialogs.Prompt() {
            public int confirm(Object m, String t2, int o, int ty) { return JOptionPane.YES_OPTION; }
            public int option(String m, String t2, Object[] o, Object i) { return -1; }
            public void message(String m) { v.janelas.add("msg " + m); }
            public void info(String t2, String m) {}
            public File saveFile(File s) { return null; }
            public File openFile() { return arq.toFile(); }
            public void openFolder(File d) {}
        });
        quebra.importData();
        eq("erro ao recarregar", List.of("msg Falha ao importar: boom"), v.janelas);
        eq("erro ao recarregar: sem toast", List.of(), v.chamadas);
    }

    static void tema() {
        boolean antes = AppTheme.dark;
        try {
            SettingsDialogsTest t = new SettingsDialogsTest();
            AppTheme.applyLight();
            t.tema = -1;
            t.dlg.chooseTheme();
            eq("fechar: nada", List.of(), t.chamadas);
            eq("janela", List.of("Tema | Escolha o tema da interface: | [Claro, Escuro] | Claro"), t.janelas);
            t.tema = 1;
            t.dlg.chooseTheme();
            eq("escuro", List.of("rebuild escuro"), t.chamadas);
            eq("inicial = atual", "Tema | Escolha o tema da interface: | [Claro, Escuro] | Claro", t.janelas.get(1));
            t.janelas.clear(); t.chamadas.clear();
            t.tema = 0;
            t.dlg.chooseTheme();
            eq("já escuro: inicial Escuro", true, t.janelas.get(0).endsWith("| Escuro"));
            eq("claro", List.of("rebuild claro"), t.chamadas);
        } finally {
            if (antes) AppTheme.applyDark(); else AppTheme.applyLight();
        }
    }
}
