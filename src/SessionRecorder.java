import javax.swing.Timer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Registra o tempo estudado: soma no total da área, cria a sessão (na ordem cronológica) e,
 * se for hoje, dá o aviso de meta ("bateu a meta" / "faltam N min").
 *
 * Mexe nas MESMAS coleções do StudyTracker (não cópias). O relógio vem de fora, para os
 * testes fixarem o dia e a hora.
 */
final class SessionRecorder {

    /** O que o StudyTracker faz por nós. */
    interface Host {
        void recorded();            // pendingSubFoco = null; updateUI(); saveData(); saveSessions();
        void toast(String msg);
    }

    /** Atraso do aviso de meta, para não colidir com o toast de "tempo salvo". */
    static final int NUDGE_DELAY_MS = 2500;

    private final Map<String, StudyData> data;
    private final List<StudySession> sessions;
    private final Map<String, Integer> goals;
    private final StudyStats stats;
    private final Clock clock;
    private final Host host;

    SessionRecorder(Map<String, StudyData> data, List<StudySession> sessions, Map<String, Integer> goals,
                    StudyStats stats, Clock clock, Host host) {
        this.data = data;
        this.sessions = sessions;
        this.goals = goals;
        this.stats = stats;
        this.clock = clock;
        this.host = host;
    }

    void add(String subject, int minutes, String type, SessionDetailsDialog.Details d) {
        add(subject, minutes, type, d, LocalDate.now(clock));
    }

    /**
     * Registra tempo num dia. Dia passado (registro esquecido) vira uma sessão às 12:00 daquele dia;
     * metas, sequência e calendário são derivados das sessões, então já contam no dia certo.
     */
    void add(String subject, int minutes, String type, SessionDetailsDialog.Details d, LocalDate day) {
        LocalDate today = LocalDate.now(clock);
        boolean isToday = !day.isBefore(today);
        int beforeToday = stats.minutesOnDay(subject, today);
        long ts = isToday ? clock.millis()
                : day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        data.get(subject).addMinutes(minutes);
        insertChronological(sessions, new StudySession(subject, minutes, ts, type, d.note, d.kind, d.energy));
        host.recorded();

        if (isToday) {
            String msg = goalNudgeText(subject, goals.getOrDefault(subject, 0), stats.dailyMinFor(subject),
                    beforeToday, beforeToday + minutes, stats.isHardDay(today));
            if (msg != null) {
                Timer t = new Timer(NUDGE_DELAY_MS, e -> host.toast(msg));
                t.setRepeats(false);
                t.start();
            }
        }
    }

    /** Mantém a lista em ordem cronológica, como ela é carregada do disco (empate: depois das iguais). */
    static void insertChronological(List<StudySession> sessions, StudySession s) {
        int pos = sessions.size();
        while (pos > 0 && sessions.get(pos - 1).getTimestamp() > s.getTimestamp()) pos--;
        sessions.add(pos, s);
    }

    /**
     * Toast de incentivo: meta do dia batida agora, ou reta final. Celebra ao bater o MÍNIMO
     * ({@code alvo}). null = nada a dizer (sem meta, longe dela, já batida antes, ou dia difícil).
     */
    static String goalNudgeText(String subject, int goal, int alvo, int before, int after, boolean hardDay) {
        if (goal <= 0) return null;
        if (before < alvo && after >= alvo)
            return "Você bateu a meta de " + subject + " hoje!  (" + ScreenKit.fmtHM(after) + ")";
        if (!hardDay && after < alvo && alvo - after <= 15)
            return "Faltam " + (alvo - after) + " min para a meta de " + subject + " hoje.";
        return null;
    }
}
