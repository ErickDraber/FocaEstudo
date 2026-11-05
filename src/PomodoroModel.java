/**
 * Estado do Pomodoro, sem Swing: bloco atual (foco ou pausa), tempo restante, pausa
 * do bloco e a contagem do ciclo (a cada 4 focos, pausa longa).
 *
 * Quem faz o "tique" de 1 s é o javax.swing.Timer do StudyTracker, que chama {@link #tick()}.
 */
final class PomodoroModel {

    private boolean running;        // contando agora
    private boolean paused;         // bloco pausado (continua "ativo")
    private boolean focus;          // bloco atual é foco (false = pausa)
    private int     plannedMinutes;
    private int     remainingSeconds;
    private int     cycleFocos;     // focos concluídos no ciclo atual

    /** Começa um bloco (substitui o que houver). */
    void start(boolean isFocus, int minutes) {
        focus = isFocus;
        plannedMinutes = minutes;
        remainingSeconds = minutes * 60;
        paused = false;
        running = true;
    }

    /** Um segundo. @return true quando o bloco acabou neste tique. */
    boolean tick() {
        if (!running) return false;
        remainingSeconds--;
        return remainingSeconds <= 0;
    }

    /** Pausa ou retoma. @return o novo estado: true = pausado. */
    boolean togglePause() {
        paused = !paused;
        running = !paused;
        return paused;
    }

    /** Para de contar (fim do bloco ou antes de decidir o próximo). */
    void halt() { running = false; }

    /**
     * Volta ao estado ocioso, mostrando um foco cheio.
     * @param finished false = interrompido pelo usuário: o ciclo recomeça do 1º foco.
     */
    void idle(boolean finished, int focusMinutes) {
        if (!finished) cycleFocos = 0;
        running = false;
        paused = false;
        remainingSeconds = focusMinutes * 60;
    }

    void completeFocus()        { cycleFocos++; }
    boolean longBreakDue()      { return cycleFocos % 4 == 0; }
    static int longBreakMinutes(int breakMinutes) { return Math.max(15, breakMinutes * 3); }

    boolean isActive()          { return running || paused; }
    boolean isRunning()         { return running; }
    boolean isPaused()          { return paused; }
    boolean isFocus()           { return focus; }
    int     plannedMinutes()    { return plannedMinutes; }
    int     remainingSeconds()  { return remainingSeconds; }
    int     currentFocusNumber(){ return cycleFocos + 1; }

    /** Minutos já feitos do foco atual (ainda não salvos). Em pausa, 0. */
    int focusMinutesDone() {
        if (!focus) return 0;
        return Math.max(0, plannedMinutes * 60 - Math.max(0, remainingSeconds)) / 60;
    }

    String remainingText() {
        int r = Math.max(0, remainingSeconds);
        return String.format("%02d:%02d", r / 60, r % 60);
    }
}
