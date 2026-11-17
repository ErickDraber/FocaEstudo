import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

/**
 * Os avisos que o app mostra sozinho: versão nova (1×/dia, consulta o GitHub), lembrete
 * diário (ícone da bandeja) e resumo da semana (ao abrir, no fim de semana).
 *
 * Lê as MESMAS coleções e ajustes do StudyTracker (não cópias). As regras de "mostra ou não"
 * e os textos são estáticos e recebem a data/hora, para poderem ser testados.
 */
final class Notices {

    /** O que o StudyTracker faz por nós. */
    interface Host {
        void save();                 // saveData()
        void toast(String msg);      // aviso flutuante (sem bandeja)
        void trayClicked();          // clique no ícone da bandeja: volta do modo foco ou mostra a janela
    }

    static final String RELEASES_LATEST = "https://github.com/ErickDraber/FocaEstudo/releases/latest";
    static final String REMINDER_MSG = "Que tal uns minutos de estudo hoje? Sua sequência agradece. 🔥";

    private final Component parent;
    private final AppSettings cfg;
    private final StudyStats stats;
    private final Collection<String> areaNames;
    private final List<StudySession> sessions;
    private final Host host;

    private Timer reminderTimer;
    private java.awt.TrayIcon trayIcon;

    Notices(Component parent, AppSettings cfg, StudyStats stats, Collection<String> areaNames,
            List<StudySession> sessions, Host host) {
        this.parent = parent;
        this.cfg = cfg;
        this.stats = stats;
        this.areaNames = areaNames;
        this.sessions = sessions;
        this.host = host;
    }

    // ── AVISO DE VERSÃO NOVA ───────────────────────────────────────────────

    /** No máximo 1× por dia, pergunta ao GitHub qual é a última versão. Não envia dado nenhum. */
    void maybeCheckForUpdate() {
        String atual = System.getProperty("jpackage.app-version");   // só existe no .exe
        if (!cfg.updateCheck || atual == null) return;
        String hoje = LocalDate.now().toString();
        if (hoje.equals(cfg.updateCheckedDay)) return;
        Thread t = new Thread(() -> {
            String nova = fetchLatestVersion();
            if (nova == null) return;   // sem internet: tenta de novo na próxima abertura
            SwingUtilities.invokeLater(() -> {
                cfg.updateCheckedDay = hoje;
                host.save();
                if (compareVersions(nova, atual) > 0) showUpdateDialog(atual, nova);
            });
        }, "FocaEstudo-atualizacao");
        t.setDaemon(true);
        t.start();
    }

    /** O GitHub redireciona /releases/latest para /releases/tag/vX.Y.Z: a versão vem do endereço. */
    private static String fetchLatestVersion() {
        java.net.HttpURLConnection con = null;
        try {
            con = (java.net.HttpURLConnection) java.net.URI.create(RELEASES_LATEST).toURL().openConnection();
            con.setInstanceFollowRedirects(false);
            con.setRequestMethod("HEAD");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("User-Agent", "FocaEstudo");
            return versionFromLocation(con.getHeaderField("Location"));
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** ".../releases/tag/v1.4.0" → "1.4.0"; qualquer outra coisa → null. */
    static String versionFromLocation(String loc) {
        if (loc == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/releases/tag/v?(\\d{1,4}\\.\\d{1,4}\\.\\d{1,4})$").matcher(loc);
        return m.find() ? m.group(1) : null;
    }

    /** Compara "1.10.0" com "1.9.2" número a número. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int dx = 0, dy = 0;
            try { if (i < x.length) dx = Integer.parseInt(x[i].trim()); } catch (NumberFormatException ignored) {}
            try { if (i < y.length) dy = Integer.parseInt(y[i].trim()); } catch (NumberFormatException ignored) {}
            if (dx != dy) return Integer.compare(dx, dy);
        }
        return 0;
    }

    static String updateText(String atual, String nova) {
        return "Saiu a versão " + nova + " do FocaEstudo (você está usando a " + atual + ").\n\n"
             + "Para atualizar: baixe o zip, feche o FocaEstudo e extraia por cima da pasta atual.\n"
             + "Seus dados ficam guardados fora da pasta do programa, então nada se perde.";
    }

    private void showUpdateDialog(String atual, String nova) {
        Object[] opts = {"Abrir página de download", "Agora não"};
        int r = JOptionPane.showOptionDialog(parent, updateText(atual, nova),
                "Versão nova", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, opts, opts[0]);
        if (r != 0) return;
        try { Desktop.getDesktop().browse(java.net.URI.create(RELEASES_LATEST)); }
        catch (Exception e) {
            JOptionPane.showMessageDialog(parent, "Baixe em:\n" + RELEASES_LATEST,
                    "Versão nova", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ── LEMBRETE DIÁRIO (item 8) ───────────────────────────────────────────

    void setupReminder() {
        if (java.awt.SystemTray.isSupported()) {
            try {
                trayIcon = new java.awt.TrayIcon(AppTheme.appIcon(16), "FocaEstudo");
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> host.trayClicked());
                java.awt.SystemTray.getSystemTray().add(trayIcon);
            } catch (Exception e) { trayIcon = null; }
        }
        reminderTimer = new Timer(5 * 60 * 1000, e -> checkReminder());
        reminderTimer.setInitialDelay(30 * 1000);
        reminderTimer.start();
    }

    /** Resultado da regra do lembrete. */
    enum Reminder {
        WAIT,      // nada a fazer agora (desligado, já mostrado hoje ou antes da hora)
        SKIP_DAY,  // hoje não precisa (dia difícil ou já estudou): marca o dia sem gravar
        SHOW       // marca o dia, grava e mostra
    }

    /** A regra do lembrete, na mesma ordem de antes. */
    static Reminder reminderRule(int reminderHour, String shownDay, LocalDate today, int hourNow,
                                 boolean hardDay, int minutesToday) {
        if (reminderHour < 0) return Reminder.WAIT;
        if (today.toString().equals(shownDay)) return Reminder.WAIT;
        if (hardDay) return Reminder.SKIP_DAY;                // dia difícil: sem lembrete
        if (hourNow < reminderHour) return Reminder.WAIT;
        if (minutesToday > 0) return Reminder.SKIP_DAY;       // já estudou hoje
        return Reminder.SHOW;
    }

    void checkReminder() {
        if (cfg.reminderHour < 0) return;
        LocalDate today = LocalDate.now();
        String hoje = today.toString();
        if (hoje.equals(cfg.reminderShownDay)) return;
        // os minutos só são calculados se a regra chegar lá (igual a antes)
        boolean hard = stats.isHardDay(today);
        int hour = LocalTime.now().getHour();
        int min = hard || hour < cfg.reminderHour ? 0 : stats.minutesOnDay(null, today);
        Reminder r = reminderRule(cfg.reminderHour, cfg.reminderShownDay, today, hour, hard, min);
        if (r == Reminder.WAIT) return;
        cfg.reminderShownDay = hoje;
        if (r == Reminder.SKIP_DAY) return;

        host.save();
        if (trayIcon != null) {
            trayIcon.displayMessage("FocaEstudo", REMINDER_MSG, java.awt.TrayIcon.MessageType.INFO);
        } else {
            host.toast(REMINDER_MSG);
        }
    }

    // ── RESUMO SEMANAL (item 7) ─────────────────────────────────────────────

    /** "2026-W47" (semana ISO). */
    static String weekKey(LocalDate d) {
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        return d.get(wf.weekBasedYear()) + "-W" + d.get(wf.weekOfWeekBasedYear());
    }

    /** Mostra 1× por semana, a partir de sábado; ou na primeira vez de todas. */
    static boolean weeklyDue(String shownWeek, LocalDate today) {
        if (weekKey(today).equals(shownWeek)) return false;
        return shownWeek == null || today.getDayOfWeek().getValue() >= 6;
    }

    /** Texto do resumo dos últimos 7 dias; null se não estudou nada neles. */
    static String weeklySummaryText(StudyStats stats, Collection<String> areaNames, LocalDate today) {
        LocalDate ini = today.minusDays(6);
        int totalSemana = stats.minutesInRange(null, ini, today);
        if (totalSemana == 0) return null;

        int totalAnterior = stats.minutesInRange(null, ini.minusDays(7), today.minusDays(7));
        int diasMeta = 0, diasComEstudo = 0;
        String topSubj = null; int topMin = 0;
        for (String s : areaNames) {
            int m = stats.minutesInRange(s, ini, today);
            if (m > topMin) { topMin = m; topSubj = s; }
        }
        int metaDia = stats.dailyGoalFor(null);
        for (LocalDate d = ini; !d.isAfter(today); d = d.plusDays(1)) {
            int m = stats.minutesOnDay(null, d);
            if (m > 0) diasComEstudo++;
            if (metaDia > 0 && m >= metaDia) diasMeta++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Últimos 7 dias\n\n");
        sb.append("• Total estudado: ").append(ScreenKit.fmtHM(totalSemana)).append('\n');
        sb.append("• Dias com estudo: ").append(diasComEstudo).append(" de 7\n");
        if (metaDia > 0) sb.append("• Meta diária batida em ").append(diasMeta).append(" de 7 dias\n");
        if (topSubj != null) sb.append("• Matéria destaque: ").append(topSubj)
                .append(" (").append(ScreenKit.fmtHM(topMin)).append(")\n");
        if (totalAnterior > 0) {
            int d = totalSemana - totalAnterior;
            sb.append("• vs. semana anterior: ")
              .append(d >= 0 ? "▲ +" + ScreenKit.fmtHM(d) : "▼ " + ScreenKit.fmtHM(-d)).append('\n');
        }
        sb.append("\nBora manter o ritmo!");
        return sb.toString();
    }

    void maybeShowWeeklySummary() {
        if (sessions.isEmpty()) return;
        LocalDate today = LocalDate.now();
        if (!weeklyDue(cfg.weeklySummaryShownWeek, today)) return;

        cfg.weeklySummaryShownWeek = weekKey(today);
        host.save();

        String text = weeklySummaryText(stats, areaNames, today);
        if (text == null) return;
        JOptionPane.showMessageDialog(parent, text,
                "Resumo da semana", JOptionPane.INFORMATION_MESSAGE);
    }
}
