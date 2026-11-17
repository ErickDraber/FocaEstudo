import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * "Como foi?" ao terminar uma sessão: sub-foco e energia em botões de 1 clique, nota opcional.
 * Enter salva; fechar a janela também salva (o tempo nunca se perde por causa do diálogo).
 *
 * Lê o MESMO {@link AppSettings} e o mesmo mapa de sub-focos do StudyTracker. O formulário
 * ({@link Form}) é separado da janela, para os testes marcarem botões e lerem o resultado.
 */
final class SessionDetailsDialog {

    static final String[] ENERGY_TEXT = {"", "Baixa", "Ok", "Alta"};

    /** Sub-foco, energia (0 = não informada, 1..3) e nota de uma sessão. Tudo opcional. */
    static final class Details {
        final String kind, note;
        final int energy;
        Details(String kind, String note, int energy) {
            this.kind = kind == null ? "" : kind;
            this.note = note == null ? "" : note;
            this.energy = energy;
        }
        static Details of(String kind) { return new Details(kind, "", 0); }
    }

    private final AppSettings cfg;
    private final Map<String, List<String>> subFocos;

    SessionDetailsDialog(AppSettings cfg, Map<String, List<String>> subFocos) {
        this.cfg = cfg;
        this.subFocos = subFocos;
    }

    /**
     * Pergunta (se o ajuste estiver ligado) e devolve os detalhes. {@code pendingSubFoco} é o
     * sub-foco já escolhido antes (ex.: rodízio do "Agora"): vem marcado, ou vai direto se não perguntar.
     */
    Details ask(Component owner, String subject, int minutes, String titulo, String pendingSubFoco) {
        if (!cfg.askDetails) return Details.of(pendingSubFoco);

        Form f = new Form(subject, minutes, pendingSubFoco);
        JOptionPane op = new JOptionPane(f.panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"Salvar"}, "Salvar");
        JDialog dlg = op.createDialog(owner, titulo);
        dlg.addWindowFocusListener(new WindowAdapter() {   // já dá para digitar a nota; Enter salva
            @Override public void windowGainedFocus(WindowEvent e) { f.note.requestFocusInWindow(); }
        });
        f.note.addActionListener(e -> op.setValue("Salvar"));
        dlg.setVisible(true);
        dlg.dispose();
        return f.result();
    }

    /** O conteúdo do "Como foi?". */
    final class Form {
        final JPanel panel = new JPanel(new GridBagLayout());
        final List<String> sfs;
        final ChoiceRow sfRow;     // null se a área não tem sub-focos
        final ChoiceRow enRow;
        final JTextField note;

        Form(String subject, int minutes, String pendingSubFoco) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1; c.insets = new Insets(2, 2, 2, 2);
            int row = 0;

            c.gridy = row++;
            panel.add(ScreenKit.dlgLabel(ScreenKit.fmtHM(minutes) + " de " + subject + ". Como foi?", true), c);

            sfs = subFocos.getOrDefault(subject, Collections.emptyList());
            if (!sfs.isEmpty()) {
                c.gridy = row++; c.insets = new Insets(10, 2, 2, 2);
                panel.add(ScreenKit.dlgHint("No que você focou?"), c);
                sfRow = new ChoiceRow(sfs.toArray(new String[0]), sfs.indexOf(pendingSubFoco));
                c.gridy = row++; c.insets = new Insets(2, 2, 2, 2);
                panel.add(sfRow, c);
            } else {
                sfRow = null;
            }

            c.gridy = row++; c.insets = new Insets(10, 2, 2, 2);
            panel.add(ScreenKit.dlgHint("Energia"), c);
            enRow = new ChoiceRow(new String[]{ENERGY_TEXT[1], ENERGY_TEXT[2], ENERGY_TEXT[3]}, -1);
            c.gridy = row++; c.insets = new Insets(2, 2, 2, 2);
            panel.add(enRow, c);

            c.gridy = row++; c.insets = new Insets(10, 2, 2, 2);
            panel.add(ScreenKit.dlgHint("Nota (vai para o Histórico)"), c);
            note = new JTextField(28);
            note.setFont(AppTheme.FONT_LABEL);
            c.gridy = row++; c.insets = new Insets(2, 2, 2, 2);
            panel.add(note, c);
        }

        Details result() {
            String kind = sfRow != null && sfRow.selected() >= 0 ? sfs.get(sfRow.selected()) : null;
            return new Details(kind, cleanNote(note.getText()), enRow.selected() + 1);
        }
    }

    /** A nota gravada: sem "|" (separa campos da sessão), sem HTML, sem espaços nas pontas. */
    static String cleanNote(String text) {
        return AppTheme.semHtml(text.replace('|', '/').trim());
    }

    /** Linha de botões em que no máximo um fica marcado; clicar no marcado desmarca. */
    static final class ChoiceRow extends JPanel {
        final List<JToggleButton> btns = new ArrayList<>();
        ChoiceRow(String[] labels, int initial) {
            super(new FlowLayout(FlowLayout.LEFT, 6, 0));
            for (int i = 0; i < labels.length; i++) {
                // Pintado à mão: no Nimbus o botão marcado quase não se distingue do desmarcado.
                JToggleButton b = new JToggleButton(labels[i], i == initial) {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        int w = getWidth(), h = getHeight();
                        if (isSelected()) {
                            g2.setColor(AppTheme.ACCENT);
                            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
                        } else {
                            g2.setColor(getModel().isRollover() ? AppTheme.SURFACE2 : AppTheme.SURFACE);
                            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
                            g2.setColor(AppTheme.DIVIDER);
                            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
                        }
                        g2.setFont(isSelected() ? AppTheme.FONT_BOLD : AppTheme.FONT_LABEL);
                        g2.setColor(isSelected() ? Color.WHITE : AppTheme.TEXT_PRI);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
                                (h - fm.getHeight()) / 2 + fm.getAscent());
                        g2.dispose();
                    }
                };
                b.setFont(AppTheme.FONT_BOLD);   // mede pelo negrito: o texto não muda de tamanho ao marcar
                b.setContentAreaFilled(false);
                b.setBorderPainted(false);
                b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
                b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                b.setFocusPainted(false);
                b.addActionListener(e -> { if (b.isSelected()) for (JToggleButton o : btns) if (o != b) o.setSelected(false); });
                btns.add(b);
                add(b);
            }
        }
        int selected() {
            for (int i = 0; i < btns.size(); i++) if (btns.get(i).isSelected()) return i;
            return -1;
        }
    }
}
