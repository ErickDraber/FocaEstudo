import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Camada de arquivos do FocaEstudo: onde os dados ficam, leitura/gravação dos três
 * .properties (dados, sessões, tarefas), backup (.bak + gravação atômica), exportar/importar
 * e a migração da pasta do programa (versões ≤ 1.0.2).
 *
 * NÃO sabe o que cada chave significa: isso é do {@link StudyDataFile} (study_data) e as
 * travas ready/loadOk continuam no StudyTracker.
 *
 * Recebe a pasta dos dados no construtor: os testes usam uma pasta temporária.
 */
final class StudyStore {

    static final String DATA_NAME       = "study_data.properties";
    static final String SESSIONS_NAME   = "study_sessions.properties";
    static final String CHECKLIST_NAME  = "study_checklist.properties";
    static final String MIGRATED_MARKER = "DADOS_MOVIDOS.txt";
    static final String LOCK_NAME       = "focaestudo.lock";

    static final String SEP_DATA     = "===== FOCAESTUDO DADOS =====";
    static final String SEP_SESSIONS = "===== FOCAESTUDO SESSOES =====";
    static final String SEP_TAREFAS  = "===== FOCAESTUDO TAREFAS =====";
    static final long   MAX_IMPORT_BYTES = 20L * 1024 * 1024;   // backups reais têm poucos KB

    final File dir;
    final File dataFile, sessionFile, checklistFile;

    StudyStore(File dir) {
        this.dir = dir;
        this.dataFile      = new File(dir, DATA_NAME);
        this.sessionFile   = new File(dir, SESSIONS_NAME);
        this.checklistFile = new File(dir, CHECKLIST_NAME);
    }

    // ── ONDE FICAM OS DADOS ─────────────────────────────────────────────────

    /** Descobre a raiz do programa a partir da localização das classes (…/bin ⇒ raiz). */
    static File resolveBaseDir() {
        // Empacotado pelo jpackage (FocaEstudo.exe): dados ao lado do .exe, não em …/app
        String exe = System.getProperty("jpackage.app-path");
        if (exe != null) {
            File dir = new File(exe).getParentFile();
            if (dir != null && dir.isDirectory()) return dir;
        }
        try {
            File loc = new File(StudyStore.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = loc.isFile() ? loc.getParentFile() : loc;   // …/bin  ou  …/app.jar
            if (dir != null && "bin".equalsIgnoreCase(dir.getName()) && dir.getParentFile() != null)
                dir = dir.getParentFile();
            if (dir != null && dir.isDirectory()) return dir;
        } catch (Exception ignored) {}
        return new File(System.getProperty("user.dir"));
    }

    /** %APPDATA%\FocaEstudo; -Dfocaestudo.dados=<pasta> sobrepõe; fora do Windows, a pasta do programa. */
    static File resolveDataDir(File baseDir) {
        String custom = System.getProperty("focaestudo.dados");
        if (custom != null && !custom.trim().isEmpty()) return new File(custom.trim());
        String appData = System.getenv("APPDATA");
        if (appData != null && new File(appData).isDirectory()) return new File(appData, "FocaEstudo");
        return baseDir;
    }

    /**
     * Copia (não move) os dados de 'baseDir' para esta pasta, uma vez só.
     * Nunca sobrescreve: se aqui já há dados, não mexe em nada.
     * @return baseDir se migrou agora (o app avisa o usuário); null se não.
     */
    File migrateLegacyFrom(File baseDir) {
        if (dir.getAbsoluteFile().equals(baseDir.getAbsoluteFile())) return null;
        if (dataFile.exists()) return null;
        if (new File(baseDir, MIGRATED_MARKER).exists()) return null;
        if (!new File(baseDir, DATA_NAME).isFile()) return null;   // nada a migrar
        try {
            // study_data por último: é a existência dele que marca a migração como feita.
            for (File f : new File[]{sessionFile, checklistFile, dataFile})
                for (String suf : new String[]{".bak", ""}) {
                    File src = new File(baseDir, f.getName() + suf);
                    if (src.isFile())
                        Files.copy(src.toPath(), new File(dir, src.getName()).toPath(),
                                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
        } catch (IOException e) {
            e.printStackTrace();   // sem study_data aqui, a próxima abertura tenta de novo
            return null;
        }
        try {
            Files.write(new File(baseDir, MIGRATED_MARKER).toPath(), (
                    "A partir da versão 1.1.0, o FocaEstudo guarda seus dados em:\r\n"
                  + "    " + dir.getAbsolutePath() + "\r\n\r\n"
                  + "Os arquivos study_*.properties desta pasta são a cópia antiga e não são mais usados.\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}   // sem permissão de escrita: só não deixa o aviso
        return baseDir;
    }

    // Mantido vivo pela duração do processo: o SO libera o lock quando o processo termina.
    @SuppressWarnings("unused")
    private static java.nio.channels.FileLock instanceLock;

    /**
     * Garante uma única instância: duas cópias gravando o mesmo save corrompem os dados.
     * Usa lock de arquivo na pasta dos dados.
     * @return false se outra instância segura o lock.
     */
    boolean acquireSingleInstanceLock() {
        try {
            java.nio.channels.FileChannel ch = new RandomAccessFile(new File(dir, LOCK_NAME), "rw").getChannel();
            instanceLock = ch.tryLock();
            return instanceLock != null;
        } catch (java.nio.channels.OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            return true;    // pasta sem permissão de escrita: não dá para checar, segue
        }
    }

    // ── DADOS (matérias, metas, ajustes) ────────────────────────────────────

    /** Lê study_data já neutralizando HTML. null ⇒ arquivo não existe (primeiro uso). */
    Properties readData() throws IOException {
        if (!dataFile.exists()) return null;
        try (FileInputStream in = new FileInputStream(dataFile)) {
            return loadSemHtml(in);
        }
    }

    void writeData(Properties p) {
        backup(dataFile);
        storeAtomic(p, dataFile, "FocaEstudo Data");
    }

    // ── SESSÕES ─────────────────────────────────────────────────────────────

    /** Sessões ordenadas por horário. Arquivo ausente ⇒ lista vazia. */
    List<StudySession> readSessions() throws IOException {
        List<StudySession> out = new ArrayList<>();
        if (!sessionFile.exists()) return out;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(sessionFile)) { p.load(in); }
        for (String k : p.stringPropertyNames()) {
            StudySession s = StudySession.deserialize(p.getProperty(k));
            if (s != null) out.add(s);
        }
        out.sort(Comparator.comparingLong(StudySession::getTimestamp));
        return out;
    }

    void writeSessions(List<StudySession> sessions) {
        Properties p = new Properties();
        for (int i = 0; i < sessions.size(); i++) p.setProperty("s" + i, sessions.get(i).serialize());
        backup(sessionFile);
        storeAtomic(p, sessionFile, "FocaEstudo Sessions");
    }

    // ── TAREFAS ─────────────────────────────────────────────────────────────

    /** Arquivo ausente ⇒ lista vazia. */
    List<ChecklistItem> readChecklist() throws IOException {
        List<ChecklistItem> out = new ArrayList<>();
        if (!checklistFile.exists()) return out;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(checklistFile)) { p.load(in); }
        for (String k : p.stringPropertyNames()) {
            ChecklistItem it = ChecklistItem.deserialize(p.getProperty(k));
            if (it != null) out.add(it);
        }
        return out;
    }

    void writeChecklist(List<ChecklistItem> items) {
        Properties p = new Properties();
        for (int i = 0; i < items.size(); i++) p.setProperty("c" + i, items.get(i).serialize());
        backup(checklistFile);
        storeAtomic(p, checklistFile, "FocaEstudo Checklist");
    }

    // ── BACKUP: EXPORTAR / IMPORTAR ─────────────────────────────────────────

    /** As três seções de um arquivo de backup. */
    static final class Backup {
        final String dados, sessoes, tarefas;
        Backup(String dados, String sessoes, String tarefas) {
            this.dados = dados; this.sessoes = sessoes; this.tarefas = tarefas;
        }
    }

    /** Texto do backup com os três arquivos atuais (grave o estado antes de chamar). */
    String exportText() throws IOException {
        String dados   = dataFile.exists()      ? readText(dataFile)      : "";
        String sessoes = sessionFile.exists()   ? readText(sessionFile)   : "";
        String tarefas = checklistFile.exists() ? readText(checklistFile) : "";
        return SEP_DATA + "\n" + dados + "\n"
             + SEP_SESSIONS + "\n" + sessoes + "\n"
             + SEP_TAREFAS + "\n" + tarefas + "\n";
    }

    /** Separa as seções. null ⇒ não parece um backup do FocaEstudo. Aceita backup antigo sem tarefas. */
    static Backup parseBackup(String all) {
        int iData = all.indexOf(SEP_DATA);
        int iSess = all.indexOf(SEP_SESSIONS);
        int iTar  = all.indexOf(SEP_TAREFAS);
        if (iData < 0 || iSess < 0 || iSess < iData) return null;
        String dados   = all.substring(iData + SEP_DATA.length(), iSess).trim();
        String sessoes = iTar > iSess
                ? all.substring(iSess + SEP_SESSIONS.length(), iTar).trim()
                : all.substring(iSess + SEP_SESSIONS.length()).trim();
        String tarefas = iTar > iSess ? all.substring(iTar + SEP_TAREFAS.length()).trim() : "";
        return new Backup(dados, sessoes, tarefas);
    }

    /** Quantas linhas "matéria=minutos,cor" válidas a seção de dados tem. */
    static long countSubjects(String dados) throws IOException {
        Properties test = new Properties();
        test.load(new StringReader(dados));
        return test.stringPropertyNames().stream().filter(k -> {
            String v = test.getProperty(k);
            if (v == null || !v.contains(",")) return false;
            try {
                String[] pp = v.split(",");
                Integer.parseInt(pp[0].trim());
                Integer.parseInt(pp[1].trim());
                return true;
            } catch (Exception ex) { return false; }
        }).count();
    }

    /** Substitui os três arquivos pelo backup; os atuais vão para .bak. */
    void importBackup(Backup b) throws IOException {
        backup(dataFile); backup(sessionFile); backup(checklistFile);
        Files.write(dataFile.toPath(),      b.dados.getBytes(StandardCharsets.UTF_8));
        Files.write(sessionFile.toPath(),   b.sessoes.getBytes(StandardCharsets.UTF_8));
        Files.write(checklistFile.toPath(), b.tarefas.getBytes(StandardCharsets.UTF_8));
    }

    // ── UTILITÁRIOS DE ARQUIVO ──────────────────────────────────────────────

    /** .properties são ISO-8859-1 (com \\u para o resto). */
    static String readText(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1);
    }

    /**
     * Grava as Properties de forma atômica: escreve num arquivo temporário, força
     * para disco e só então renomeia por cima do original. Se faltar luz no meio,
     * o arquivo bom continua intacto.
     */
    static void storeAtomic(Properties p, File target, String header) {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                p.store(out, header);
                out.flush();
                out.getFD().sync();
            }
            try {
                Files.move(tmp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
            tmp.delete();
        }
    }

    /** Lê Properties já neutralizando HTML em chaves e valores (ver AppTheme.semHtml). */
    static Properties loadSemHtml(InputStream in) throws IOException {
        Properties raw = new Properties();
        raw.load(in);
        Properties p = new Properties();
        for (String k : raw.stringPropertyNames())
            p.setProperty(AppTheme.semHtml(k), AppTheme.semHtml(raw.getProperty(k)));
        return p;
    }

    /** Copia arquivo para <nome>.bak antes de sobrescrever. */
    static void backup(File f) {
        if (!f.isFile()) return;
        try (FileInputStream in = new FileInputStream(f);
             FileOutputStream out = new FileOutputStream(new File(f.getParentFile(), f.getName() + ".bak"))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException ignored) {}
    }
}
