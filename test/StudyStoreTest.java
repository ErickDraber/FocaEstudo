import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Testes da camada de arquivos (StudyStore). Usa só pastas temporárias —
 * nunca toca nos dados reais em %APPDATA%\FocaEstudo.
 */
public class StudyStoreTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    static File novaPasta() throws IOException { return Files.createTempDirectory("focaestudo-teste").toFile(); }
    static String ler(File f) throws IOException { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
    static void escrever(File f, String s) throws IOException { Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8)); }

    public static void main(String[] args) throws Exception {
        // ── dados principais ────────────────────────────────────────────────
        { StudyStore st = new StudyStore(novaPasta());
          eq("readData sem arquivo ⇒ null", null, st.readData());
          Properties p = new Properties();
          p.setProperty("Cálculo", "120,-16776961");
          p.setProperty("goal_Cálculo", "30");
          st.writeData(p);
          Properties lido = st.readData();
          eq("roundtrip matéria (acento)", "120,-16776961", lido.getProperty("Cálculo"));
          eq("roundtrip meta", "30", lido.getProperty("goal_Cálculo"));
          eq("sem .bak na 1ª gravação", false, new File(st.dir, StudyStore.DATA_NAME + ".bak").exists());
          p.setProperty("goal_Cálculo", "45");
          st.writeData(p);
          eq("2ª gravação ⇒ .bak existe", true, new File(st.dir, StudyStore.DATA_NAME + ".bak").exists());
          Properties bak = new Properties();
          try (InputStream in = new FileInputStream(new File(st.dir, StudyStore.DATA_NAME + ".bak"))) { bak.load(in); }
          eq(".bak guarda a versão anterior", "30", bak.getProperty("goal_Cálculo"));
          eq("arquivo novo tem o valor novo", "45", st.readData().getProperty("goal_Cálculo"));
          eq("não sobra .tmp", false, new File(st.dir, StudyStore.DATA_NAME + ".tmp").exists());
        }
        { StudyStore st = new StudyStore(novaPasta());
          Properties p = new Properties();
          p.setProperty("<html>X", "<HTML><b>oi");
          st.writeData(p);
          Properties lido = st.readData();
          eq("readData neutraliza <html> na chave", true, lido.stringPropertyNames().contains("‹html>X"));
          eq("readData neutraliza <html> no valor", "‹HTML><b>oi", lido.getProperty("‹html>X"));
        }

        // ── sessões ─────────────────────────────────────────────────────────
        { StudyStore st = new StudyStore(novaPasta());
          eq("readSessions sem arquivo ⇒ vazio", 0, st.readSessions().size());
          List<StudySession> ss = new ArrayList<>();
          ss.add(new StudySession("B", 20, 3000L, "manual", "nota", "cap 2", 3));
          ss.add(new StudySession("A", 10, 1000L, "pomodoro"));
          ss.add(new StudySession("A", 15, 2000L, "cronometro"));
          st.writeSessions(ss);
          List<StudySession> lidas = st.readSessions();
          eq("sessões: quantidade", 3, lidas.size());
          eq("sessões: ordenadas por horário", Arrays.asList(1000L, 2000L, 3000L),
                  Arrays.asList(lidas.get(0).getTimestamp(), lidas.get(1).getTimestamp(), lidas.get(2).getTimestamp()));
          eq("sessões: campos preservados", ss.get(0).serialize(), lidas.get(2).serialize());
          st.writeSessions(ss.subList(0, 1));
          eq("sessões: regravar substitui", 1, st.readSessions().size());
        }

        // ── tarefas ─────────────────────────────────────────────────────────
        { StudyStore st = new StudyStore(novaPasta());
          eq("readChecklist sem arquivo ⇒ vazio", 0, st.readChecklist().size());
          List<ChecklistItem> cl = new ArrayList<>();
          cl.add(new ChecklistItem("A", "Lista 4", 30, false));
          cl.add(new ChecklistItem("B", "Rever cap. 3", 0, true));
          st.writeChecklist(cl);
          List<ChecklistItem> lidas = st.readChecklist();
          eq("tarefas: quantidade", 2, lidas.size());
          Set<String> ser = new HashSet<>();
          for (ChecklistItem it : lidas) ser.add(it.serialize());
          eq("tarefas: conteúdo", new HashSet<>(Arrays.asList(cl.get(0).serialize(), cl.get(1).serialize())), ser);
        }

        // ── backup: exportar / importar ─────────────────────────────────────
        { StudyStore origem = new StudyStore(novaPasta());
          Properties p = new Properties(); p.setProperty("Física", "60,-65536"); p.setProperty("goal_Física", "20");
          origem.writeData(p);
          origem.writeSessions(Collections.singletonList(new StudySession("Física", 25, 5000L, "manual")));
          origem.writeChecklist(Collections.singletonList(new ChecklistItem("Física", "Lista 1", 0, false)));
          String texto = origem.exportText();
          eq("export tem as 3 seções", true, texto.contains(StudyStore.SEP_DATA)
                  && texto.contains(StudyStore.SEP_SESSIONS) && texto.contains(StudyStore.SEP_TAREFAS));

          StudyStore.Backup b = StudyStore.parseBackup(texto);
          eq("parseBackup reconhece o export", true, b != null);
          eq("countSubjects", 1L, StudyStore.countSubjects(b.dados));

          StudyStore destino = new StudyStore(novaPasta());
          Properties antigo = new Properties(); antigo.setProperty("Velha", "5,-1");
          destino.writeData(antigo);
          destino.importBackup(b);
          eq("import: matéria nova", "60,-65536", destino.readData().getProperty("Física"));
          eq("import: matéria antiga sumiu", null, destino.readData().getProperty("Velha"));
          eq("import: dados antigos em .bak", true, ler(new File(destino.dir, StudyStore.DATA_NAME + ".bak")).contains("Velha"));
          eq("import: sessões", 1, destino.readSessions().size());
          eq("import: tarefas", 1, destino.readChecklist().size());
        }
        { // backup antigo, sem a seção de tarefas
          String velho = StudyStore.SEP_DATA + "\nA=10,-1\n" + StudyStore.SEP_SESSIONS + "\ns0=A|10|1000|manual\n";
          StudyStore.Backup b = StudyStore.parseBackup(velho);
          eq("backup sem tarefas: aceito", true, b != null);
          eq("backup sem tarefas: sessões", "s0=A|10|1000|manual", b.sessoes);
          eq("backup sem tarefas: tarefas vazias", "", b.tarefas);
        }
        eq("parseBackup texto qualquer ⇒ null", null, StudyStore.parseBackup("oi"));
        eq("parseBackup seções invertidas ⇒ null", null,
                StudyStore.parseBackup(StudyStore.SEP_SESSIONS + "\n" + StudyStore.SEP_DATA + "\n"));
        eq("countSubjects sem matérias", 0L, StudyStore.countSubjects("goal_A=30\n__theme__=dark\n"));

        // ── migração da pasta do programa (≤ 1.0.2) ─────────────────────────
        { File base = novaPasta(), dados = new File(novaPasta(), "FocaEstudo");
          escrever(new File(base, StudyStore.DATA_NAME), "A=10,-1\n");
          escrever(new File(base, StudyStore.SESSIONS_NAME), "s0=A|10|1000|manual\n");
          escrever(new File(base, StudyStore.DATA_NAME + ".bak"), "velho\n");
          StudyStore st = new StudyStore(dados);
          dados.mkdirs();
          eq("migra: devolve a pasta de origem", base, st.migrateLegacyFrom(base));
          eq("migra: copia study_data", "A=10,-1\n", ler(new File(dados, StudyStore.DATA_NAME)));
          eq("migra: copia sessões", true, new File(dados, StudyStore.SESSIONS_NAME).isFile());
          eq("migra: copia .bak", true, new File(dados, StudyStore.DATA_NAME + ".bak").isFile());
          eq("migra: original continua lá (cópia, não move)", true, new File(base, StudyStore.DATA_NAME).isFile());
          eq("migra: deixa o aviso", true, new File(base, StudyStore.MIGRATED_MARKER).isFile());
          escrever(new File(dados, StudyStore.DATA_NAME), "B=20,-1\n");
          eq("migra só uma vez", null, st.migrateLegacyFrom(base));
          eq("migra: não sobrescreve dados novos", "B=20,-1\n", ler(new File(dados, StudyStore.DATA_NAME)));
        }
        { File base = novaPasta();
          escrever(new File(base, StudyStore.DATA_NAME), "A=10,-1\n");
          eq("migra: mesma pasta ⇒ nada", null, new StudyStore(base).migrateLegacyFrom(base));
        }
        { File base = novaPasta(), dados = novaPasta();
          eq("migra: nada a migrar ⇒ null", null, new StudyStore(dados).migrateLegacyFrom(base));
          eq("migra: sem dados, sem aviso", false, new File(base, StudyStore.MIGRATED_MARKER).exists());
        }

        System.out.println("StudyStore: " + (total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }
}
