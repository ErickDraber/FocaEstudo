import java.util.*;

/** Testes dos ajustes (AppSettings): padrões, gravação/leitura no study_data e reset. */
public class AppSettingsTest {

    static int falhas = 0, total = 0;

    static void eq(String nome, Object esperado, Object obtido) {
        total++;
        if (!Objects.equals(esperado, obtido)) {
            falhas++;
            System.out.println("FALHOU  " + nome + ": esperado " + esperado + ", obtido " + obtido);
        }
    }

    /** Lê todas as chaves como o loadData faz; devolve as que NÃO eram ajustes. */
    static Set<String> lerTudo(AppSettings s, Properties p) {
        Set<String> sobras = new TreeSet<>();
        for (String k : p.stringPropertyNames()) if (!s.readKey(k, p.getProperty(k))) sobras.add(k);
        return sobras;
    }

    public static void main(String[] args) {
        // Padrões (os mesmos de antes, quando eram campos do StudyTracker)
        { AppSettings s = new AppSettings();
          eq("padrão minFocusMin", 1, s.minFocusMin);
          eq("padrão idleMinutes (desligado)", 0, s.idleMinutes);
          eq("padrão pomoAutoCycle", false, s.pomoAutoCycle);
          eq("padrão reminderHour (desligado)", -1, s.reminderHour);
          eq("padrão transitionMin", 5, s.transitionMin);
          eq("padrão hyperfocusH", 3, s.hyperfocusH);
          eq("padrão updateCheck", true, s.updateCheck);
          eq("padrão focusMode", true, s.focusMode);
          eq("padrão askDetails", true, s.askDetails);
          eq("padrão updateCheckedDay", null, s.updateCheckedDay);
          eq("padrão weeklySummaryShownWeek", null, s.weeklySummaryShownWeek);
          eq("padrão reminderShownDay", null, s.reminderShownDay);
          eq("padrão balanceNudgeWeek", null, s.balanceNudgeWeek);
        }

        // Ida e volta com tudo diferente do padrão
        { AppSettings a = new AppSettings();
          a.minFocusMin = 10; a.idleMinutes = 15; a.pomoAutoCycle = true; a.reminderHour = 20;
          a.transitionMin = 0; a.hyperfocusH = 6; a.updateCheck = false; a.focusMode = false; a.askDetails = false;
          a.updateCheckedDay = "2026-11-05"; a.weeklySummaryShownWeek = "2026-W45";
          a.reminderShownDay = "2026-11-04"; a.balanceNudgeWeek = "2026-W44";
          Properties p = new Properties();
          a.writeTo(p);
          AppSettings b = new AppSettings();
          eq("ida e volta: nada sobra", Collections.emptySet(), lerTudo(b, p));
          eq("ida e volta: minFocusMin", 10, b.minFocusMin);
          eq("ida e volta: idleMinutes", 15, b.idleMinutes);
          eq("ida e volta: pomoAutoCycle", true, b.pomoAutoCycle);
          eq("ida e volta: reminderHour", 20, b.reminderHour);
          eq("ida e volta: transitionMin", 0, b.transitionMin);
          eq("ida e volta: hyperfocusH", 6, b.hyperfocusH);
          eq("ida e volta: updateCheck", false, b.updateCheck);
          eq("ida e volta: focusMode", false, b.focusMode);
          eq("ida e volta: askDetails", false, b.askDetails);
          eq("ida e volta: updateCheckedDay", "2026-11-05", b.updateCheckedDay);
          eq("ida e volta: weeklySummaryShownWeek", "2026-W45", b.weeklySummaryShownWeek);
          eq("ida e volta: reminderShownDay", "2026-11-04", b.reminderShownDay);
          eq("ida e volta: balanceNudgeWeek", "2026-W44", b.balanceNudgeWeek);

          // reset volta tudo ao padrão (usado ao importar backup)
          b.reset();
          AppSettings padrao = new AppSettings();
          Properties pb = new Properties(), pp = new Properties();
          b.writeTo(pb); padrao.writeTo(pp);
          eq("reset ⇒ igual ao padrão", pp, pb);
          eq("reset limpa marcadores", null, b.balanceNudgeWeek);
        }

        // Chaves gravadas: nomes do formato do arquivo (não podem mudar — compatibilidade)
        { Properties p = new Properties();
          new AppSettings().writeTo(p);
          eq("chaves gravadas com o padrão", new TreeSet<>(Arrays.asList(
                  "__min_focus__", "__idle_min__", "__pomo_autocycle__", "__reminder_hour__",
                  "__transition_min__", "__hyperfocus_h__", "__update_check__", "__focus_mode__",
                  "__ask_details__")), new TreeSet<>(p.stringPropertyNames()));
        }

        // Leitura tolerante (arquivos editados à mão ou de versões antigas)
        { AppSettings s = new AppSettings();
          s.readKey("__min_focus__", "-5");
          eq("negativo vira 0 (minFocus)", 0, s.minFocusMin);
          s.readKey("__idle_min__", "abc");
          eq("número inválido mantém o valor", 0, s.idleMinutes);
          s.readKey("__reminder_hour__", "-1");
          eq("lembrete -1 aceito (desligado)", -1, s.reminderHour);
          s.readKey("__pomo_autocycle__", "TRUE");
          eq("autocycle 'TRUE'", true, s.pomoAutoCycle);
          s.readKey("__pomo_autocycle__", "sim");
          eq("autocycle só liga com 'true'", false, s.pomoAutoCycle);
          s.readKey("__focus_mode__", "qualquer");
          eq("focusMode só desliga com 'false'", true, s.focusMode);
          s.readKey("__ask_details__", "FALSE");
          eq("askDetails 'FALSE'", false, s.askDetails);
          eq("chave desconhecida não é consumida", false, s.readKey("goal_A", "30"));
          eq("matéria não é consumida", false, s.readKey("Cálculo", "10,-1"));
        }

        System.out.println("AppSettings: " + (total - falhas) + "/" + total + " testes passaram");
        if (falhas > 0) System.exit(1);
    }
}
