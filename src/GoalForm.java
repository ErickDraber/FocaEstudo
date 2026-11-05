import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

/**
 * Regras dos formulários de meta, sem Swing: ler durações digitadas, derivar
 * semana/mês da meta diária, dias da meta, data da prova e o mínimo aceitável.
 */
final class GoalForm {

    private GoalForm() {}

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Lê uma duração em minutos. Aceita: "90", "90m", "90 min", "1h", "1h30", "1h30m",
     * "1:30", "1,5h", "2 h". Vazio ⇒ 0. Negativo ⇒ 0.
     * @throws IllegalArgumentException se o texto não for reconhecido.
     */
    static int parseDuration(String text) {
        if (text == null) return 0;
        String t = text.trim().toLowerCase().replace(" ", "").replace(',', '.');
        if (t.isEmpty()) return 0;
        try {
            if (t.contains(":")) {                       // h:mm
                String[] p = t.split(":", 2);
                int h = p[0].isEmpty() ? 0 : Integer.parseInt(p[0]);
                int m = p[1].isEmpty() ? 0 : Integer.parseInt(p[1]);
                return Math.max(0, h * 60 + m);
            }
            t = t.replace("min", "m");
            if (t.contains("h") || t.endsWith("m")) {    // 1h / 1h30 / 45m / 1.5h
                int hIdx = t.indexOf('h');
                double h = 0; int m = 0;
                if (hIdx >= 0) {
                    String hs = t.substring(0, hIdx);
                    if (!hs.isEmpty()) h = Double.parseDouble(hs);
                    String rest = t.substring(hIdx + 1).replace("m", "");
                    if (!rest.isEmpty()) m = Integer.parseInt(rest);
                } else {
                    String ms = t.replace("m", "");
                    if (!ms.isEmpty()) m = Integer.parseInt(ms);
                }
                return Math.max(0, (int) Math.round(h * 60) + m);
            }
            if (t.contains(".")) return Math.max(0, (int) Math.round(Double.parseDouble(t)));
            return Math.max(0, Integer.parseInt(t));     // número puro = minutos
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("duração inválida: " + text);
        }
    }

    /** Meta semanal derivada: diária × dias da semana com meta. */
    static int weekFromDaily(int daily, int nDias) { return daily * nDias; }

    /** Meta mensal derivada: semana × 30/7 (~4,3 semanas). */
    static int monthFromWeek(int week) { return (int) Math.round(week * 30.0 / 7.0); }

    /** Quantos dias marcados (seg..dom); nenhum marcado vale como 7. */
    static int daysCount(boolean[] selecionados) {
        int n = 0;
        for (boolean b : selecionados) if (b) n++;
        return n == 0 ? 7 : n;
    }

    /** Dias marcados (índice 0 = segunda). null ⇒ todos os dias (nenhum ou os 7 marcados). */
    static Set<DayOfWeek> daysFrom(boolean[] selecionados) {
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        for (int i = 0; i < 7; i++) if (selecionados[i]) dias.add(DayOfWeek.of(i + 1));
        return dias.isEmpty() || dias.size() == 7 ? null : dias;
    }

    /** Mínimo aceitável só vale se for menor que o ideal; senão 0 (= igual ao ideal). */
    static int effectiveMin(int min, int ideal) { return min > 0 && min < ideal ? min : 0; }

    /** Meta diária para repetir a melhor semana (no mínimo 5 min). */
    static int bestWeekPerDay(int bestWeek, int nDias) { return Math.max(5, bestWeek / Math.max(1, nDias)); }

    /** "dd/mm/aaaa" ⇒ data; vazio ⇒ null. @throws IllegalArgumentException se inválida. */
    static LocalDate parseExamDate(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return null;
        try { return LocalDate.parse(t, DMY); }
        catch (Exception e) { throw new IllegalArgumentException("data inválida: " + text); }
    }

    static String examText(LocalDate d) { return d == null ? "" : d.format(DMY); }
}
