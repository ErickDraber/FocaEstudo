import java.util.Properties;

/**
 * Ajustes do usuário (tela Ajustes) e marcadores de "já mostrei hoje/nesta semana".
 * Gravados no study_data.properties com chaves "__nome__".
 *
 * Os valores padrão ficam SÓ em {@link #reset()} — antes eles estavam repetidos na
 * declaração dos campos e no reloadFromDisk(), e era fácil um esquecer o outro.
 */
final class AppSettings {

    // --- Ajustes ---
    int     minFocusMin;     // não salva sessão de cronômetro/pomodoro abaixo disso
    int     idleMinutes;     // 0 = desativado (padrão)
    boolean pomoAutoCycle;   // encadeia foco → pausa → foco automaticamente
    int     reminderHour;    // hora do lembrete diário (0-23); -1 = desligado
    int     transitionMin;   // pausa sugerida entre blocos (min); 0 = desligado
    int     hyperfocusH;     // aviso de hiperfoco após N h seguidas; 0 = desligado
    boolean updateCheck;     // consulta o GitHub (1×/dia) se saiu versão nova
    boolean focusMode;       // ao começar uma sessão, a janela vira a faixa do modo foco
    boolean askDetails;      // ao terminar, pergunta sub-foco, energia e nota

    // --- Marcadores (null = nunca) ---
    String updateCheckedDay;        // ISO date da última consulta de versão que deu certo
    String weeklySummaryShownWeek;  // ISO "2026-W37" da última vez que mostrou o resumo
    String reminderShownDay;        // ISO date do último lembrete mostrado
    String balanceNudgeWeek;        // ISO "2026-W37" da última vez que avisou desequilíbrio

    AppSettings() { reset(); }

    /** Volta tudo ao padrão (um backup sem alguma chave não pode herdar o valor antigo). */
    void reset() {
        minFocusMin = 1; idleMinutes = 0; pomoAutoCycle = false; reminderHour = -1;
        transitionMin = 5; hyperfocusH = 3; updateCheck = true; focusMode = true; askDetails = true;
        updateCheckedDay = null; weeklySummaryShownWeek = null; reminderShownDay = null; balanceNudgeWeek = null;
    }

    /** Escreve os ajustes (sempre) e os marcadores (só os que existem). */
    void writeTo(Properties p) {
        p.setProperty("__min_focus__",      String.valueOf(minFocusMin));
        p.setProperty("__idle_min__",       String.valueOf(idleMinutes));
        p.setProperty("__pomo_autocycle__", String.valueOf(pomoAutoCycle));
        p.setProperty("__reminder_hour__",  String.valueOf(reminderHour));
        p.setProperty("__transition_min__", String.valueOf(transitionMin));
        p.setProperty("__hyperfocus_h__",   String.valueOf(hyperfocusH));
        p.setProperty("__update_check__",   String.valueOf(updateCheck));
        p.setProperty("__focus_mode__",     String.valueOf(focusMode));
        p.setProperty("__ask_details__",    String.valueOf(askDetails));
        if (updateCheckedDay != null)       p.setProperty("__update_checked_day__", updateCheckedDay);
        if (weeklySummaryShownWeek != null) p.setProperty("__week_summary_shown__", weeklySummaryShownWeek);
        if (reminderShownDay != null)       p.setProperty("__reminder_shown_day__", reminderShownDay);
        if (balanceNudgeWeek != null)       p.setProperty("__balance_nudge_week__", balanceNudgeWeek);
    }

    /**
     * Lê uma chave do study_data se ela for um ajuste/marcador.
     * @return true se a chave era daqui (o chamador pula para a próxima); false se não.
     * Valor inválido mantém o atual.
     */
    boolean readKey(String k, String v) {
        switch (k) {
            case "__min_focus__":      minFocusMin   = nonNeg(v, minFocusMin);   return true;
            case "__idle_min__":       idleMinutes   = nonNeg(v, idleMinutes);   return true;
            case "__transition_min__": transitionMin = nonNeg(v, transitionMin); return true;
            case "__hyperfocus_h__":   hyperfocusH   = nonNeg(v, hyperfocusH);   return true;
            case "__reminder_hour__":
                try { reminderHour = Integer.parseInt(v); } catch (Exception ignored) {}
                return true;
            case "__pomo_autocycle__": pomoAutoCycle = "true".equalsIgnoreCase(v);   return true;   // padrão desligado
            case "__ask_details__":    askDetails    = !"false".equalsIgnoreCase(v); return true;   // padrão ligado
            case "__focus_mode__":     focusMode     = !"false".equalsIgnoreCase(v); return true;
            case "__update_check__":   updateCheck   = !"false".equalsIgnoreCase(v); return true;
            case "__update_checked_day__": updateCheckedDay       = v; return true;
            case "__week_summary_shown__": weeklySummaryShownWeek = v; return true;
            case "__reminder_shown_day__": reminderShownDay       = v; return true;
            case "__balance_nudge_week__": balanceNudgeWeek       = v; return true;
            default: return false;
        }
    }

    private static int nonNeg(String v, int atual) {
        try { return Math.max(0, Integer.parseInt(v)); } catch (Exception e) { return atual; }
    }
}
