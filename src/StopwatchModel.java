import java.util.function.LongSupplier;

/**
 * Estado do cronômetro, sem Swing: tempo LÍQUIDO (só conta rodando), pausas, tempo
 * BRUTO (relógio de parede desde o 1º "Iniciar") e o aviso de hiperfoco.
 *
 * Quem faz o "tique" de 1 s é o javax.swing.Timer do StudyTracker, que chama {@link #tick()}.
 * O relógio é injetado para os testes controlarem o tempo bruto.
 */
final class StopwatchModel {

    private final LongSupplier clockMs;
    private boolean running;
    private int     elapsedSeconds;     // líquido
    private long    grossStartMs;       // 0 = ainda não iniciado
    private int     pauseCount;
    private boolean hyperfocusWarned;

    StopwatchModel(LongSupplier clockMs) { this.clockMs = clockMs; }

    StopwatchModel() { this(System::currentTimeMillis); }

    /** Começa ou retoma. @return true se é uma sessão NOVA (retomar não reabre o modo foco). */
    boolean start() {
        boolean nova = grossStartMs == 0;
        if (nova) grossStartMs = clockMs.getAsLong();
        running = true;
        return nova;
    }

    /** Pausa; só conta pausa se estava rodando. */
    void pause() {
        if (running) pauseCount++;
        running = false;
    }

    /** Zera a sessão do cronômetro (o tempo já salvo nas matérias não é tocado). */
    void reset() {
        running = false;
        elapsedSeconds = 0;
        grossStartMs = 0;
        pauseCount = 0;
        hyperfocusWarned = false;
    }

    void tick() { if (running) elapsedSeconds++; }

    boolean isRunning()     { return running; }
    boolean hasTime()       { return elapsedSeconds > 0; }
    boolean started()       { return grossStartMs != 0; }
    int     elapsedSeconds(){ return elapsedSeconds; }
    int     pauseCount()    { return pauseCount; }

    long grossSeconds() {
        return grossStartMs == 0 ? 0 : (clockMs.getAsLong() - grossStartMs) / 1000;
    }

    /** Minutos que vão para o histórico (arredonda: 90 s ⇒ 2 min). */
    int roundedMinutes() { return (int) Math.round(elapsedSeconds / 60.0); }

    /** Abaixo do foco mínimo não salva (mínimo nunca menor que 1 min). */
    boolean belowMinimum(int minFocusMin) { return roundedMinutes() < Math.max(1, minFocusMin); }

    /** true UMA vez por sessão, quando o tempo bruto chega a {@code hours} horas (0 = desligado). */
    boolean shouldWarnHyperfocus(int hours) {
        if (hours <= 0 || hyperfocusWarned || !started()) return false;
        if (grossSeconds() < hours * 3600L) return false;
        hyperfocusWarned = true;
        return true;
    }

    String elapsedText() { return hms(elapsedSeconds); }

    /** "líquido · N pausas · bruto hh:mm:ss"; " " antes do 1º iniciar (mantém a altura da linha). */
    String infoText() {
        if (!started()) return " ";
        String pausas = pauseCount == 1 ? "1 pausa" : pauseCount + " pausas";
        return "líquido  ·  " + pausas + "  ·  bruto " + hms(grossSeconds());
    }

    static String hms(long s) {
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    /** Sem mexer no teclado/mouse há {@code idleMinutes} min? (0 = verificação desligada). */
    static boolean idleTooLong(long nowMs, long lastActivityMs, int idleMinutes) {
        return idleMinutes > 0 && nowMs - lastActivityMs >= idleMinutes * 60_000L;
    }
}
