import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/**
 * Cartão "Agora": a protagonista da tela Hoje. Uma sugestão só, grande, com 1 clique para começar.
 *
 * Estados, nesta ordem: sessão contando (em andamento / pausa do Pomodoro) → dia difícil →
 * sugestão do {@link NowSuggester} → "Tudo em dia". O que sugerir é do NowSuggester; o que os
 * botões fazem é do StudyTracker (via {@link Actions}).
 */
final class NowCard extends RoundedPanel {

    /** Estado da sessão e do dia. Quem implementa é o StudyTracker. */
    interface Source {
        boolean sessionInProgress();
        /** Pomodoro ativo, mas na pausa (não no foco). */
        boolean pomodoroBreak();
        /** Área escolhida no combo (a da sessão em andamento). */
        String selectedArea();
        Map<String, StudyData> areas();
        boolean isHardDay(LocalDate day);
    }

    interface Actions {
        void focusMode();
        /** "Começar": seleciona a área (e o sub-foco) e liga o cronômetro. */
        void start(String area, String subFoco);
        /** "hoje tá difícil?" para {@code day} (o dia em que o cartão foi desenhado). */
        void hardDay(LocalDate day);
        /** "desfazer" o dia difícil de {@code day}. */
        void undoHardDay(LocalDate day);
    }

    private final Source src;
    private final NowSuggester suggester;
    private final Set<String> skipped;   // "Outra coisa" neste turno (o StudyTracker zera ao mudar de turno)
    private final Actions actions;
    private final JLabel reason;
    private final JPanel body;

    NowCard(Source src, NowSuggester suggester, Set<String> skipped, Actions actions) {
        super(18, AppTheme.SURFACE);
        this.src = src; this.suggester = suggester; this.skipped = skipped; this.actions = actions;
        setBorderColor(AppTheme.ACCENT, 2f);
        setLayout(new BorderLayout(0, 6));
        setBorder(new EmptyBorder(14, 18, 12, 18));
        reason = new JLabel("Agora");
        reason.setFont(AppTheme.FONT_SMALL);
        reason.setForeground(AppTheme.ACCENT);
        add(reason, BorderLayout.NORTH);
        body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        add(body, BorderLayout.CENTER);
    }

    void refresh() {
        body.removeAll();
        LocalDate hoje = LocalDate.now();

        // Sessão contando: o "agora" é ela, não uma sugestão nova.
        if (src.sessionInProgress()) {
            boolean pausaPomo = src.pomodoroBreak();
            String area = src.selectedArea();
            StudyData sd = area == null ? null : src.areas().get(area);
            reason.setText(pausaPomo ? "Agora · pausa do Pomodoro" : "Agora · em andamento");
            JLabel nm = new JLabel(pausaPomo ? "Pausa" : area,
                    bigDot(sd != null && !pausaPomo ? sd.getColor() : AppTheme.ACCENT), SwingConstants.LEFT);
            nm.setIconTextGap(10);
            nm.setFont(AppTheme.FONT_TITLE);
            nm.setForeground(AppTheme.TEXT_PRI);
            StyledButton foco = new StyledButton("Modo foco", StyledButton.Variant.FILLED);
            foco.setFont(AppTheme.FONT_BOLD);
            foco.setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 22));
            foco.setToolTipText("Recolhe a janela numa faixa pequena com o tempo");
            foco.addActionListener(e -> actions.focusMode());
            body.add(nm,               BorderLayout.CENTER);
            body.add(centered(foco),   BorderLayout.EAST);
            body.revalidate(); body.repaint();
            return;
        }

        if (src.isHardDay(hoje)) {
            reason.setText("Agora · dia difícil");
            JLabel l = new JLabel("Sem cobrança hoje. Se der vontade, qualquer minuto conta.");
            l.setFont(AppTheme.FONT_LABEL); l.setForeground(AppTheme.TEXT_SEC);
            body.add(l, BorderLayout.CENTER);
            body.add(links(textBtn("desfazer", () -> actions.undoHardDay(hoje))), BorderLayout.SOUTH);
            body.revalidate(); body.repaint();
            return;
        }

        int turno = PlannerPanel.slotAt(LocalTime.now());
        NowSuggester.Suggestion sug = suggester.next(hoje, turno, skipped);
        if (sug == null && !skipped.isEmpty()) { skipped.clear(); sug = suggester.next(hoje, turno, skipped); }   // deu a volta
        StyledButton hard = textBtn("hoje tá difícil?", () -> actions.hardDay(hoje));

        if (sug == null) {
            reason.setText("Agora");
            JLabel l = new JLabel("Tudo em dia por aqui. Aproveite o descanso.");
            l.setFont(AppTheme.FONT_LABEL); l.setForeground(AppTheme.TEXT_SEC);
            body.add(l, BorderLayout.CENTER);
            body.add(links(hard), BorderLayout.SOUTH);
        } else {
            final String area = sug.area();
            final String sf = sug.subFoco();
            reason.setText("Agora · " + sug.reason());

            StudyData sd = src.areas().get(area);
            JLabel nm = new JLabel(sf != null ? area + " · " + sf : area,
                    bigDot(sd != null ? sd.getColor() : AppTheme.ACCENT), SwingConstants.LEFT);
            nm.setIconTextGap(10);
            nm.setFont(AppTheme.FONT_TITLE);
            nm.setForeground(AppTheme.TEXT_PRI);
            nm.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel txt = new JPanel();
            txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
            txt.setOpaque(false);
            txt.add(nm);
            // O motivo já fala da meta? Então não repete o progresso embaixo.
            String prog = sug.reason().contains("meta") ? null : suggester.progressText(area, hoje);
            if (prog != null) {
                JLabel p = new JLabel(prog);
                p.setFont(AppTheme.FONT_SMALL); p.setForeground(AppTheme.TEXT_SEC);
                p.setBorder(new EmptyBorder(2, 24, 0, 0));
                p.setAlignmentX(Component.LEFT_ALIGNMENT);
                txt.add(p);
            }

            StyledButton go = new StyledButton("Começar", StyledButton.Variant.FILLED);
            go.setFont(AppTheme.FONT_BOLD);
            go.setBorder(BorderFactory.createEmptyBorder(8, 26, 8, 26));
            go.addActionListener(e -> actions.start(area, sf));

            StyledButton outra = textBtn("Outra coisa", () -> { skipped.add(area); refresh(); });
            outra.setToolTipText("Mostrar outra sugestão");
            body.add(txt,          BorderLayout.CENTER);
            body.add(centered(go), BorderLayout.EAST);
            body.add(links(outra, hard), BorderLayout.SOUTH);
        }
        body.revalidate();
        body.repaint();
    }

    /** Centralizado na altura, sem esticar. */
    private static JPanel centered(JComponent c) {
        JPanel w = new JPanel(new GridBagLayout());
        w.setOpaque(false);
        w.add(c);
        return w;
    }

    private static JPanel links(JComponent... links) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(4, 14, 0, 0));
        for (JComponent l : links) p.add(l);
        return p;
    }

    private static StyledButton textBtn(String text, Runnable r) {
        StyledButton b = new StyledButton(text, StyledButton.Variant.TEXT);
        b.setFont(AppTheme.FONT_SMALL);
        b.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        b.addActionListener(e -> r.run());
        return b;
    }

    private static Icon bigDot(Color c) {
        return new Icon() {
            public int getIconWidth()  { return 14; }
            public int getIconHeight() { return 14; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c); g2.fillOval(x, y, 14, 14); g2.dispose();
            }
        };
    }
}
