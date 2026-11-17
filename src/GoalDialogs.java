import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * Os diálogos de metas: metas de uma área (diária/mínimo/semana/mês, dias, prova e sugestões),
 * "Gerenciar metas" (limpar várias de uma vez), metas de equilíbrio por tipo, e o aviso
 * semanal de equilíbrio.
 *
 * Mexe nas MESMAS coleções do StudyTracker (não cópias). Cada diálogo é um formulário
 * ({@link GoalEditor}, {@link ManageForm}, {@link TypeForm}) com {@code apply()} separado da
 * janela, para os testes preencherem os campos e salvarem sem abrir nada.
 */
final class GoalDialogs {

    /** O que o StudyTracker faz por nós. */
    interface Host {
        void changed();                    // updateUI() (que também grava)
        void toast(String msg);
        String typeOf(String area);
        int minutesByType(String type, LocalDate start, LocalDate end);
    }

    private final Component parent;
    private final Map<String, StudyData> areas;
    private final Set<String> archived;
    private final Map<String, Integer> goals, goalsMin, goalsWeek, goalsMonth, typeGoalWeek;
    private final Map<String, Set<DayOfWeek>> goalDays;
    private final Map<String, LocalDate> examDate;
    private final AppSettings cfg;
    private final StudyStats stats;
    private final Host host;

    GoalDialogs(Component parent, Map<String, StudyData> areas, Set<String> archived,
                Map<String, Integer> goals, Map<String, Integer> goalsMin,
                Map<String, Integer> goalsWeek, Map<String, Integer> goalsMonth,
                Map<String, Set<DayOfWeek>> goalDays, Map<String, LocalDate> examDate,
                Map<String, Integer> typeGoalWeek, AppSettings cfg, StudyStats stats, Host host) {
        this.parent = parent;
        this.areas = areas;
        this.archived = archived;
        this.goals = goals;
        this.goalsMin = goalsMin;
        this.goalsWeek = goalsWeek;
        this.goalsMonth = goalsMonth;
        this.goalDays = goalDays;
        this.examDate = examDate;
        this.typeGoalWeek = typeGoalWeek;
        this.cfg = cfg;
        this.stats = stats;
        this.host = host;
    }

    private static String fmtHM(int m)                     { return ScreenKit.fmtHM(m); }
    private static JLabel dlgLabel(String s, boolean bold) { return ScreenKit.dlgLabel(s, bold); }
    private static JLabel dlgHint(String s)                { return ScreenKit.dlgHint(s); }
    private static void addFormRow(JPanel p, GridBagConstraints c, int row, String label, JComponent f) {
        ScreenKit.addFormRow(p, c, row, label, f);
    }

    private boolean confirm(JPanel panel, String title) {
        return JOptionPane.showConfirmDialog(parent, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    // ── METAS DE UMA ÁREA ───────────────────────────────────────────────────

    void editGoals(String subject) {
        if (subject == null) { host.toast("Selecione uma matéria!"); return; }
        GoalEditor ed = new GoalEditor(subject);
        if (!confirm(ed.panel, "Metas de \"" + subject + "\"")) return;
        ed.apply();
    }

    /** O formulário "Metas de …". */
    final class GoalEditor {
        final String subject;
        final JPanel panel = new JPanel(new GridBagLayout());
        final JTextField fDay, fMin, fWeek, fMonth, fExam;
        final JToggleButton[] tgDias = new JToggleButton[7];
        final JLabel diasHint;

        GoalEditor(String subject) {
            this.subject = subject;
            fDay   = goalField(goals.getOrDefault(subject, 0));
            fMin   = goalField(goalsMin.getOrDefault(subject, 0));
            fWeek  = goalField(goalsWeek.getOrDefault(subject, 0));
            fMonth = goalField(goalsMonth.getOrDefault(subject, 0));

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(3, 3, 3, 3);
            c.anchor = GridBagConstraints.WEST;

            int row = 0;
            addFormRow(panel, c, row++, "Meta diária (ideal):", fDay);
            addFormRow(panel, c, row++, "Mínimo aceitável:",    fMin);
            addFormRow(panel, c, row++, "Meta semanal:", fWeek);
            addFormRow(panel, c, row++, "Meta mensal:",  fMonth);
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(0, 3, 8, 3);
            panel.add(dlgHint("Bater o mínimo já mantém a sequência. Vazio = mínimo igual ao ideal."), c);
            c.gridwidth = 1; c.insets = new Insets(3, 3, 3, 3);

            c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(2, 3, 4, 3);
            panel.add(dlgHint("Minutos (\"90\") ou horas (\"1h30\").  Semana/mês em branco = calculados pela meta diária × dias."), c);

            JButton btnLimpar = new JButton("Limpar metas");
            btnLimpar.setFont(AppTheme.FONT_SMALL);
            btnLimpar.setMargin(new Insets(2, 8, 2, 8));
            btnLimpar.setFocusPainted(false);
            btnLimpar.setToolTipText("Zera meta diária, semanal e mensal desta matéria");
            btnLimpar.addActionListener(e -> { fDay.setText(""); fMin.setText(""); fWeek.setText(""); fMonth.setText(""); });
            JPanel limparRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            limparRow.add(btnLimpar);
            c.gridy = row++; c.insets = new Insets(0, 3, 10, 3);
            panel.add(limparRow, c);
            c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.insets = new Insets(3, 3, 3, 3);

            // Dias da semana em que a meta vale (para sequência não quebrar no fim de semana).
            Set<DayOfWeek> diasAtuais = goalDays.get(subject);
            String[] nomes = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
            JPanel diasRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
            for (int i = 0; i < 7; i++) {
                DayOfWeek dw = DayOfWeek.of(i + 1);
                boolean on = diasAtuais == null || diasAtuais.isEmpty() || diasAtuais.contains(dw);
                tgDias[i] = new JToggleButton(nomes[i], on);
                tgDias[i].setMargin(new Insets(2, 6, 2, 6));
                tgDias[i].setFocusPainted(false);
                diasRow.add(tgDias[i]);
            }

            diasHint = dlgHint(nDiasSel() + " dia(s)/semana com meta");
            // Ao MEXER nos dias, semana e mês passam a ser diária × nº de dias.
            for (JToggleButton t : tgDias) t.addItemListener(e -> {
                int nd = nDiasSel();
                int d;
                try { d = GoalForm.parseDuration(fDay.getText()); } catch (Exception ex) { d = 0; }
                if (d > 0) {
                    int wk = GoalForm.weekFromDaily(d, nd), mo = GoalForm.monthFromWeek(wk);
                    fWeek.setText(String.valueOf(wk));
                    fMonth.setText(String.valueOf(mo));
                    diasHint.setText(nd + " dia(s)/semana  →  semana " + fmtHM(wk) + "  ·  mês " + fmtHM(mo));
                } else {
                    diasHint.setText(nd + " dia(s)/semana. Preencha a meta diária p/ calcular semana e mês.");
                }
            });

            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(dlgLabel("Dias da meta diária", true), c);
            c.gridy = row++;
            panel.add(diasRow, c);
            c.gridy = row++; c.insets = new Insets(0, 3, 4, 3);
            panel.add(diasHint, c);
            c.gridy = row++; c.insets = new Insets(0, 3, 10, 3);
            panel.add(dlgHint("Dias desmarcados não exigem a meta e não quebram a sequência."), c);
            c.insets = new Insets(3, 3, 3, 3);

            // Data de prova (opcional).
            fExam = new JTextField(GoalForm.examText(examDate.get(subject)), 10);
            fExam.setFont(AppTheme.FONT_LABEL);
            addFormRow(panel, c, row++, "Data da prova:", fExam);
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(0, 3, 12, 3);
            panel.add(dlgHint("Formato dd/mm/aaaa. Deixe vazio para não usar."), c);
            c.gridwidth = 1; c.insets = new Insets(3, 3, 3, 3);

            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(dlgLabel("Sugestões de meta diária", true), c);

            JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            for (int m : new int[]{15, 30, 45, 60, 90, 120}) {
                final int mm = m;
                JButton b = new JButton(fmtHM(mm));
                b.setMargin(new Insets(2, 8, 2, 8));
                b.setFocusPainted(false);
                b.addActionListener(e -> fillGoalFields(mm));
                presets.add(b);
            }
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(presets, c);

            int avg30  = stats.avgDailyMinutes(subject, 30);
            int avgAct = stats.avgActiveDayMinutes(subject, 30);
            if (avg30 > 0) {
                c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
                panel.add(suggestionRow("Sua média nos últimos 30 dias: " + fmtHM(avg30) + "/dia",
                        Math.max(5, avg30)), c);
            }
            if (avgAct > 0 && avgAct != avg30) {
                c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
                panel.add(suggestionRow("Média nos dias em que você estudou: " + fmtHM(avgAct), avgAct), c);
            }
            if (avg30 == 0 && avgAct == 0) {
                c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
                panel.add(dlgHint("Estude alguns dias e o app passa a sugerir metas pela sua média."), c);
            }

            int melhorSem = stats.bestWeekMinutes(subject);
            if (melhorSem > 0) {
                int perDay = GoalForm.bestWeekPerDay(melhorSem, nDiasSel());
                c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
                panel.add(suggestionRow("Repetir sua melhor semana (" + fmtHM(melhorSem) + ")  →  " + fmtHM(perDay) + "/dia",
                        perDay), c);
            }

            // Meta adaptativa (item 1): sobe se vem batendo folgado, baixa se vem falhando muito.
            int adapt = stats.adaptiveGoalSuggestion(subject);
            if (adapt > 0) {
                int atual = goals.getOrDefault(subject, 0);
                c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
                panel.add(suggestionRow(
                        (adapt > atual ? "Você vem batendo com folga — que tal subir para " + fmtHM(adapt) + "?"
                                       : "Semanas puxadas — sugestão: baixar para " + fmtHM(adapt) + " e retomar o ritmo"),
                        adapt), c);
            }
        }

        private boolean[] diasSel() {
            boolean[] b = new boolean[7];
            for (int i = 0; i < 7; i++) b[i] = tgDias[i].isSelected();
            return b;
        }

        int nDiasSel() { return GoalForm.daysCount(diasSel()); }

        /**
         * Preenche os 3 campos a partir de uma meta diária, usando o nº de dias/semana
         * selecionados: semana = diária × nDias; mês = semana × ~4,345.
         */
        void fillGoalFields(int daily) {
            int week = GoalForm.weekFromDaily(daily, nDiasSel());
            fDay.setText(String.valueOf(daily));
            fWeek.setText(String.valueOf(week));
            fMonth.setText(String.valueOf(GoalForm.monthFromWeek(week)));
        }

        private JPanel suggestionRow(String text, int minutes) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            row.add(dlgHint(text));
            JButton use = new JButton("Usar");
            use.setMargin(new Insets(1, 8, 1, 8));
            use.setFont(AppTheme.FONT_SMALL);
            use.setFocusPainted(false);
            use.addActionListener(e -> fillGoalFields(minutes));
            row.add(use);
            return row;
        }

        /** O "OK" do diálogo: grava nas coleções, chama o updateUI e avisa. */
        void apply() {
            int gDay, gWeek, gMonth;
            try {
                gDay   = GoalForm.parseDuration(fDay.getText());
                gWeek  = GoalForm.parseDuration(fWeek.getText());
                gMonth = GoalForm.parseDuration(fMonth.getText());
            } catch (IllegalArgumentException ex) {
                host.toast("Meta inválida — use algo como \"90\", \"1h30\" ou \"1:30\".");
                return;
            }

            // Dias da meta
            Set<DayOfWeek> dias = GoalForm.daysFrom(diasSel());   // null = todos os dias
            if (dias == null) goalDays.remove(subject);
            else              goalDays.put(subject, dias);

            // Data da prova
            try {
                LocalDate prova = GoalForm.parseExamDate(fExam.getText());
                if (prova == null) examDate.remove(subject); else examDate.put(subject, prova);
            } catch (IllegalArgumentException ex) {
                host.toast("Data da prova inválida (use dd/mm/aaaa) — ignorada.");
                examDate.remove(subject);
            }

            int gMin;
            try { gMin = GoalForm.parseDuration(fMin.getText()); } catch (IllegalArgumentException ex) { gMin = 0; }
            goals.put(subject, gDay);
            gMin = GoalForm.effectiveMin(gMin, gDay);
            if (gMin > 0) goalsMin.put(subject, gMin); else goalsMin.remove(subject);
            goalsWeek.put(subject, gWeek);
            goalsMonth.put(subject, gMonth);
            host.changed();
            host.toast((gDay > 0 || gWeek > 0 || gMonth > 0)
                    ? "Metas de \"" + subject + "\" atualizadas."
                    : "Metas de \"" + subject + "\" removidas.");
        }
    }

    /** Campo de texto para meta, pré-preenchido (vazio se 0). */
    private static JTextField goalField(int value) {
        JTextField f = new JTextField(value > 0 ? String.valueOf(value) : "", 6);
        f.setFont(AppTheme.FONT_LABEL);
        return f;
    }

    // ── GERENCIAR METAS (limpar várias de uma vez) ─────────────────────────

    void manageGoals() {
        ManageForm f = new ManageForm();
        if (!confirm(f.panel, "Gerenciar metas")) return;
        f.apply();
    }

    /** Texto de cada caixa: "   Cálculo   —   Dia 30m · Sem 3h30 · Mês 15h00" ou "sem meta". */
    static String manageRowText(String area, int day, int week, int month) {
        boolean temMeta = day > 0 || week > 0 || month > 0;
        return "   " + area + "   —   " + (temMeta
                ? "Dia " + fmtHM(Math.max(0, day)) + " · Sem " + fmtHM(week) + " · Mês " + fmtHM(month)
                : "sem meta");
    }

    final class ManageForm {
        final JPanel panel = new JPanel(new GridBagLayout());
        final Map<String, JCheckBox> cbs = new LinkedHashMap<>();
        final JButton todas;

        ManageForm() {
            List<String> ativas = new ArrayList<>();
            for (String s : areas.keySet()) if (!archived.contains(s)) ativas.add(s);

            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1; c.insets = new Insets(2, 2, 2, 2);
            int row = 0;

            c.gridy = row++; panel.add(dlgLabel("Marque as matérias para limpar as metas:", true), c);

            // Agrupa por tipo de área, na ordem Estudo → Físico → Lazer → Trabalho → Outro.
            Map<String, List<String>> porTipo = new LinkedHashMap<>();
            for (String t : AreaTypes.KEYS) porTipo.put(t, new ArrayList<>());
            for (String s : ativas) porTipo.computeIfAbsent(host.typeOf(s), k -> new ArrayList<>()).add(s);

            for (Map.Entry<String, List<String>> grupo : porTipo.entrySet()) {
                if (grupo.getValue().isEmpty()) continue;
                JLabel cab = new JLabel(AreaTypes.label(grupo.getKey()).toUpperCase());
                cab.setFont(AppTheme.FONT_CAPTION);
                cab.setForeground(AppTheme.TEXT_MUT);
                c.gridy = row++; c.insets = new Insets(8, 2, 2, 2); panel.add(cab, c);
                c.insets = new Insets(2, 2, 2, 2);
                for (String s : grupo.getValue()) {
                    int d = goals.getOrDefault(s, 0), w = stats.weeklyGoalFor(s), mo = stats.monthlyGoalFor(s);
                    JCheckBox cb = new JCheckBox(manageRowText(s, d, w, mo));
                    cb.setEnabled(d > 0 || w > 0 || mo > 0);
                    cbs.put(s, cb);
                    c.gridy = row++; panel.add(cb, c);
                }
            }

            todas = new JButton("Marcar todas com meta");
            todas.setFont(AppTheme.FONT_SMALL);
            todas.setFocusPainted(false);
            todas.addActionListener(e -> cbs.values().forEach(cb -> { if (cb.isEnabled()) cb.setSelected(true); }));
            c.gridy = row++; c.fill = GridBagConstraints.NONE; c.insets = new Insets(8, 2, 2, 2);
            panel.add(todas, c);
        }

        void apply() {
            int n = 0;
            for (Map.Entry<String, JCheckBox> e : cbs.entrySet()) {
                if (!e.getValue().isSelected()) continue;
                String s = e.getKey();
                goals.remove(s); goalsMin.remove(s); goalsWeek.remove(s); goalsMonth.remove(s);
                n++;
            }
            if (n == 0) { host.toast("Nenhuma matéria marcada."); return; }
            host.changed();
            host.toast(n == 1 ? "Metas de 1 matéria limpas." : "Metas de " + n + " matérias limpas.");
        }
    }

    // ── METAS DE EQUILÍBRIO (por tipo, na semana) ──────────────────────────

    void typeGoals() {
        TypeForm f = new TypeForm();
        if (!confirm(f.panel, "Metas de equilíbrio (semana)")) return;
        f.apply();
    }

    final class TypeForm {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JTextField[] fs = new JTextField[AreaTypes.KEYS.length];

        TypeForm() {
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4); c.anchor = GridBagConstraints.WEST;
            c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
            panel.add(dlgHint("Mínimo por semana para cada tipo (vazio = sem meta de equilíbrio)."), c);
            c.gridwidth = 1;

            for (int i = 0; i < AreaTypes.KEYS.length; i++) {
                int v = typeGoalWeek.getOrDefault(AreaTypes.KEYS[i], 0);
                fs[i] = new JTextField(v > 0 ? String.valueOf(v) : "", 6);
                fs[i].setFont(AppTheme.FONT_LABEL);
                addFormRow(panel, c, i + 1, AreaTypes.LABELS[i] + ":", fs[i]);
            }
        }

        void apply() {
            try {
                for (int i = 0; i < AreaTypes.KEYS.length; i++) {
                    int v = GoalForm.parseDuration(fs[i].getText());
                    if (v > 0) typeGoalWeek.put(AreaTypes.KEYS[i], v); else typeGoalWeek.remove(AreaTypes.KEYS[i]);
                }
            } catch (IllegalArgumentException ex) {
                host.toast("Valor inválido — use \"90\" ou \"1h30\"."); return;
            }
            host.changed();
            host.toast("Metas de equilíbrio atualizadas.");
        }
    }

    // ── AVISO DE EQUILÍBRIO ─────────────────────────────────────────────────

    /**
     * Os tipos com meta de equilíbrio ainda zerados na semana (rótulos em minúsculas, na ordem
     * dos tipos), ou null se hoje não é dia de avisar: dia difícil, antes de quinta ou já avisado
     * nesta semana.
     */
    static List<String> nudgeMissing(LocalDate today, boolean hardDay, String nudgedWeek,
                                     Map<String, Integer> typeGoalWeek,
                                     java.util.function.ToIntBiFunction<String, LocalDate> minutesByTypeSince) {
        if (hardDay) return null;
        if (today.getDayOfWeek().getValue() < 4) return null;   // só de quinta em diante
        if (Notices.weekKey(today).equals(nudgedWeek)) return null;

        List<String> faltando = new ArrayList<>();
        LocalDate ws = StudyStats.weekStart(today);
        for (String t : AreaTypes.KEYS) {
            if (typeGoalWeek.getOrDefault(t, 0) <= 0) continue;
            if (minutesByTypeSince.applyAsInt(t, ws) == 0) faltando.add(AreaTypes.label(t).toLowerCase());
        }
        return faltando;
    }

    static String nudgeText(List<String> faltando) {
        return "Sua semana está sem " + String.join(" e ", faltando) + ". Que tal um pouco hoje?";
    }

    /** Aviso gentil (1x por semana) se algum tipo com meta está zerado a partir de quinta. */
    void balanceNudge() {
        LocalDate today = LocalDate.now();
        List<String> faltando = nudgeMissing(today, stats.isHardDay(today), cfg.balanceNudgeWeek, typeGoalWeek,
                (t, ws) -> host.minutesByType(t, ws, today));
        if (faltando == null || faltando.isEmpty()) return;
        cfg.balanceNudgeWeek = Notices.weekKey(today);   // persistido no saveData() do updateUI
        Timer tt = new Timer(600, e -> host.toast(nudgeText(faltando)));
        tt.setRepeats(false); tt.start();
    }
}
