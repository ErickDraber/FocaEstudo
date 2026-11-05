import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Fumaça: renomear área no app aberto leva sessões, sequência, tarefas e gravação junto. */
public class FumacaAreas extends Fumaca {

    public static void main(String[] a) throws Exception {
        long agora = System.currentTimeMillis();
        Path dir = abrirApp(
                "Calculo=60,-16776961\nF\\u00EDsica=10,-65536\ngoal_Calculo=30",
                "s0=Calculo|30|" + agora + "|manual\ns1=Calculo|30|" + (agora - 86_400_000L) + "|manual\n"
              + "s2=F\\u00EDsica|10|" + agora + "|manual\n",
                "c0=Calculo|0|20|Lista 1\n");

        edt(() -> {
            Object stats = get("stats"), areas = get("areas");
            eq("antes: sequência de Calculo", 2, call(stats, "currentStreak", "Calculo"));
            eq("rename OK", "OK", String.valueOf(call(areas, "rename", "Calculo", "Cálculo")));
            call("refreshCombo"); call("saveChecklist"); call("saveSessions"); call("updateUI");
            eq("depois: sequência acompanha o nome novo", 2, call(stats, "currentStreak", "Cálculo"));
            eq("depois: feito hoje acompanha", 30, call(stats, "minutesOnDay", "Cálculo", LocalDate.now()));
            eq("depois: nome antigo sem sessões", 0, call(stats, "minutesOnDay", "Calculo", LocalDate.now()));
            eq("ordem mantida", "[Cálculo, Física]", ((Map<?, ?>) get("studyDataMap")).keySet().toString());
        });

        // .properties grava 'á' como á
        String sess    = new String(Files.readAllBytes(dir.resolve("study_sessions.properties")), StandardCharsets.ISO_8859_1);
        String dados   = new String(Files.readAllBytes(dir.resolve("study_data.properties")), StandardCharsets.ISO_8859_1);
        String tarefas = new String(Files.readAllBytes(dir.resolve("study_checklist.properties")), StandardCharsets.ISO_8859_1);
        eq("arquivo de sessões com o nome novo", true, sess.contains("C\\u00E1lculo|30|") && !sess.contains("=Calculo|"));
        eq("arquivo de dados com o nome novo", true, dados.contains("C\\u00E1lculo=") && dados.contains("goal_C\\u00E1lculo=30"));
        eq("arquivo de tarefas com o nome novo", true, tarefas.contains("C\\u00E1lculo|"));

        fim("Fumaça áreas");
    }
}
