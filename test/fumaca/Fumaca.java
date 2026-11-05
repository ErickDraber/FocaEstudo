import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/**
 * Base dos testes de fumaça: abrem o StudyTracker DE VERDADE (precisa de tela, não roda
 * headless) numa pasta de dados temporária e acionam os métodos internos por reflexão.
 * Nunca tocam nos dados reais: a pasta é definida em -Dfocaestudo.dados ANTES de a classe
 * StudyTracker ser carregada.
 */
class Fumaca {
    static Object st;
    static int falhas = 0, total = 0;

    /** Ajustes que evitam diálogos modais durante o teste. */
    static final String SEM_DIALOGOS = "__ask_details__=false\n__focus_mode__=false\n__update_check__=false\n"
            + "__transition_min__=0\n__week_summary_shown__=" + semanaIso() + "\n";

    static String semanaIso() {
        java.time.LocalDate d = java.time.LocalDate.now();
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        return d.get(wf.weekBasedYear()) + "-W" + d.get(wf.weekOfWeekBasedYear());
    }

    /** Cria a pasta temporária com os arquivos dados e abre o app nela. */
    static Path abrirApp(String dados, String sessoes, String tarefas) throws Exception {
        Path dir = Files.createTempDirectory("focaestudo-fumaca");
        escrever(dir.resolve("study_data.properties"), dados + "\n" + SEM_DIALOGOS);
        if (sessoes != null) escrever(dir.resolve("study_sessions.properties"), sessoes);
        if (tarefas != null) escrever(dir.resolve("study_checklist.properties"), tarefas);
        System.setProperty("focaestudo.dados", dir.toString());
        System.out.println("pasta de teste: " + dir);
        SwingUtilities.invokeAndWait(() -> {
            try {
                st = Class.forName("StudyTracker").getDeclaredConstructor().newInstance();
                call(st, "initializeUI");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        return dir;
    }

    static void escrever(Path p, String s) throws Exception {
        Files.write(p, s.getBytes(StandardCharsets.ISO_8859_1));
    }

    static Object get(String n) {
        try { Field f = st.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(st); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    static Object call(Object alvo, String n, Object... args) {
        try {
            for (Method m : alvo.getClass().getDeclaredMethods())
                if (m.getName().equals(n) && m.getParameterCount() == args.length) {
                    m.setAccessible(true); return m.invoke(alvo, args);
                }
            throw new NoSuchMethodException(n);
        } catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
          catch (Exception e) { throw new RuntimeException(e); }
    }

    static Object call(String n, Object... args) { return call(st, n, args); }

    static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    static void eq(String nome, Object esp, Object obt) {
        total++;
        if (!Objects.equals(esp, obt)) { falhas++; System.out.println("FALHOU  " + nome + ": esperado " + esp + ", obtido " + obt); }
        else System.out.println("ok      " + nome);
    }

    static void fim(String nome) {
        System.out.println(nome + ": " + (total - falhas) + "/" + total);
        System.exit(falhas > 0 ? 1 : 0);
    }
}
