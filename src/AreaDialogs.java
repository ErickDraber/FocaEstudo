import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Os diálogos da tela Áreas: nova área, editar (nome + tipo), cor, sub-focos, zerar o tempo,
 * arquivar, reativar e deletar.
 *
 * Mexe nas MESMAS coleções do StudyTracker (não cópias); as regras que tocam todas as
 * coleções por área (renomear, deletar…) continuam no {@link Areas}. As janelas passam pelo
 * {@link Prompt}, que os testes trocam para responder sem abrir nada.
 */
final class AreaDialogs {

    /** O que o StudyTracker faz por nós. */
    interface Host {
        void changed();                 // updateUI() (que também grava os dados)
        void toast(String msg);
        void refreshCombo();
        Object selectedArea();          // item do combo "Área" (a área do cronômetro)
        void selectArea(Object area);
        boolean sessionInProgress();
        void saveData();
        void saveSessions();
        void saveChecklist();
    }

    /** As janelas (JOptionPane / JColorChooser). */
    interface Prompt {
        int confirm(Object message, String title, int optionType, int messageType);
        Color chooseColor(String title, Color initial);
    }

    static Prompt swingPrompt(Component parent) {
        return new Prompt() {
            public int confirm(Object message, String title, int optionType, int messageType) {
                return JOptionPane.showConfirmDialog(parent, message, title, optionType, messageType);
            }
            public Color chooseColor(String title, Color initial) {
                return JColorChooser.showDialog(parent, title, initial);
            }
        };
    }

    private final Map<String, StudyData> data;
    private final Set<String> archived;
    private final Map<String, String> areaType;
    private final Map<String, List<String>> subFocos;
    private final Areas areas;
    private final Host host;
    private final Prompt prompt;

    AreaDialogs(Map<String, StudyData> data, Set<String> archived, Map<String, String> areaType,
                Map<String, List<String>> subFocos, Areas areas, Host host, Prompt prompt) {
        this.data = data;
        this.archived = archived;
        this.areaType = areaType;
        this.subFocos = subFocos;
        this.areas = areas;
        this.host = host;
        this.prompt = prompt;
    }

    private String typeOf(String area) { return areaType.getOrDefault(area, "estudo"); }

    private boolean okCancel(JPanel panel, String title) {
        return prompt.confirm(panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION;
    }

    /** Seleciona a área no combo só se não houver sessão contando: senão o tempo iria para a área errada. */
    private void selectIfIdle(String area) {
        if (!host.sessionInProgress()) host.selectArea(area);
    }

    /** Arquivar/deletar a área da sessão em andamento faria o tempo ir para outra área. */
    private boolean blockIfRunning(String area) {
        if (host.sessionInProgress() && area.equals(host.selectedArea())) {
            host.toast("Termine ou zere a sessão de \"" + area + "\" antes.");
            return true;
        }
        return false;
    }

    // ── NOVA / EDITAR ───────────────────────────────────────────────────────

    /** Formulário "Nome" + "Tipo" (nova área e editar área). */
    static final class NameTypeForm {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JTextField fName;
        final JComboBox<String> cbType;

        NameTypeForm(String name, String typeKey) {
            fName = name == null ? new JTextField(18) : new JTextField(name, 18);
            cbType = tipoCombo(typeKey);
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4); c.anchor = GridBagConstraints.WEST;
            ScreenKit.addFormRow(panel, c, 0, "Nome:", fName);
            ScreenKit.addFormRow(panel, c, 1, "Tipo:", cbType);
        }

        String typeKey() { return AreaTypes.KEYS[Math.max(0, cbType.getSelectedIndex())]; }
    }

    static JComboBox<String> tipoCombo(String selectedKey) {
        String[] itens = new String[AreaTypes.KEYS.length];
        for (int i = 0; i < AreaTypes.KEYS.length; i++) itens[i] = AreaTypes.LABELS[i];
        JComboBox<String> cb = new JComboBox<>(itens);
        cb.setSelectedIndex(AreaTypes.index(selectedKey));
        cb.setFont(AppTheme.FONT_LABEL);
        return cb;
    }

    void add() {
        NameTypeForm f = new NameTypeForm(null, "estudo");
        if (!okCancel(f.panel, "Nova área")) return;
        String name = Areas.cleanName(f.fName.getText());
        if (name.isEmpty()) return;
        String type = f.typeKey();

        if (archived.contains(name)) { unarchive(name); return; }   // era arquivada → desarquiva
        if (data.containsKey(name)) { host.toast("Essa área já existe!"); return; }
        areas.add(name, type);
        host.refreshCombo(); selectIfIdle(name); host.changed();
    }

    void unarchive(String name) {
        if (!archived.remove(name)) return;
        host.refreshCombo(); selectIfIdle(name); host.changed();
        host.toast("\"" + name + "\" desarquivada.");
    }

    void edit(String old) {
        if (old == null || !data.containsKey(old)) return;
        Object cur = host.selectedArea();   // a área do cronômetro não muda

        NameTypeForm f = new NameTypeForm(old, typeOf(old));
        if (!okCancel(f.panel, "Editar área")) return;

        areas.setType(old, f.typeKey());

        switch (areas.rename(old, Areas.cleanName(f.fName.getText()))) {
            case EXISTS:
                host.toast("Esse nome já existe!");
                return;
            case UNCHANGED:   // só o tipo pode ter mudado
                host.refreshCombo(); host.selectArea(cur); host.changed();
                return;
            case OK:
                String nw = Areas.cleanName(f.fName.getText());
                host.refreshCombo(); host.selectArea(old.equals(cur) ? nw : cur);
                host.saveChecklist(); host.saveSessions(); host.changed();   // sessões agora vão junto no rename
        }
    }

    // ── COR / ZERAR / ARQUIVAR / DELETAR ───────────────────────────────────

    void color(String s) {
        if (s == null || !data.containsKey(s)) return;
        Color c = prompt.chooseColor("Cor de " + s, data.get(s).getColor());
        if (c != null) { data.get(s).setColor(c); host.changed(); }
    }

    static String zeroText(String area, int minutes) {
        return "Isto zera TODO o tempo acumulado de \"" + area + "\" (" + ScreenKit.fmtHM(minutes) + ").\n"
             + "O histórico de sessões é mantido. Continuar?\n\n"
             + "Para apenas reiniciar o cronômetro, use o botão \"Zerar\" na aba Cronômetro.";
    }

    void zero(String s) {
        if (s == null || !data.containsKey(s)) return;
        int r = prompt.confirm(zeroText(s, data.get(s).getMinutes()),
                "Zerar matéria", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) { data.get(s).setMinutes(0); host.changed(); host.toast("Tempo de \"" + s + "\" zerado."); }
    }

    static String archiveText(String area) {
        return "Arquivar \"" + area + "\"?\nEla sai da lista, mas o tempo e o histórico são mantidos.\n"
             + "Para reativar, use \"Reativar\" na tela Áreas.";
    }

    void archive(String s) {
        if (s == null || !data.containsKey(s) || blockIfRunning(s)) return;
        if (!areas.canArchive(s)) { host.toast("Deixe ao menos uma matéria ativa."); return; }
        int r = prompt.confirm(archiveText(s),
                "Arquivar matéria", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        archived.add(s);
        host.refreshCombo(); host.changed();
        host.toast("\"" + s + "\" arquivada.");
    }

    void delete(String s) {
        if (s == null || !data.containsKey(s) || blockIfRunning(s)) return;
        if (!areas.canDelete(s)) { host.toast("Deixe ao menos uma área ativa."); return; }
        int r = prompt.confirm("Deletar \"" + s + "\"? Todo o tempo será perdido.", "Deletar Matéria",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) { areas.delete(s); host.saveChecklist(); host.refreshCombo(); host.changed(); }
    }

    // ── SUB-FOCOS ───────────────────────────────────────────────────────────

    static String[] subFocoPreset(String type) {
        switch (type) {
            case "fisico": return new String[]{"Peito", "Costas", "Perna", "Ombro", "Braço", "Core", "Cardio"};
            case "estudo": return new String[]{"Teoria", "Exercícios", "Revisão"};
            default:       return new String[0];
        }
    }

    /** "Usar sugestões": o texto atual (sem linhas vazias nem repetidas) + as sugestões do tipo. */
    static String withPreset(String text, String type) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : text.split("\n")) if (!s.trim().isEmpty()) set.add(s.trim());
        Collections.addAll(set, subFocoPreset(type));
        return String.join("\n", set);
    }

    /** O que é gravado: uma por linha, sem "|", sem HTML, sem vazias e sem repetir (ignorando maiúsculas). */
    static List<String> parseSubFocos(String text) {
        List<String> nova = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (String line : text.split("\n")) {
            String v = AppTheme.semHtml(line.replace('|', '/').trim());
            if (!v.isEmpty() && vistos.add(v.toLowerCase())) nova.add(v);
        }
        return nova;
    }

    /** O formulário "Sub-focos". */
    final class SubFocosForm {
        final JPanel panel = new JPanel(new BorderLayout(0, 8));
        final JTextArea ta;
        final JButton preset;

        SubFocosForm(String area) {
            String tipo = typeOf(area);
            List<String> atuais = subFocos.getOrDefault(area, new ArrayList<>());
            ta = new JTextArea(String.join("\n", atuais), 8, 22);
            ta.setFont(AppTheme.FONT_LABEL);

            preset = new JButton("Usar sugestões de " + AreaTypes.label(tipo));
            preset.setFont(AppTheme.FONT_SMALL);
            preset.setFocusPainted(false);
            preset.setEnabled(subFocoPreset(tipo).length > 0);
            preset.addActionListener(e -> ta.setText(withPreset(ta.getText(), tipo)));

            JLabel h = ScreenKit.dlgLabel("Sub-focos de \"" + area + "\" — um por linha", true);
            panel.add(h, BorderLayout.NORTH);
            panel.add(new JScrollPane(ta), BorderLayout.CENTER);
            JPanel south = new JPanel(new BorderLayout());
            south.add(ScreenKit.dlgHint("Ex.: tópicos da prova, grupos musculares, hobbies."), BorderLayout.NORTH);
            JPanel pr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4)); pr.add(preset);
            south.add(pr, BorderLayout.SOUTH);
            panel.add(south, BorderLayout.SOUTH);
        }
    }

    /** Diálogo para editar os sub-focos de uma área (um por linha). */
    void subFocos(String area) {
        SubFocosForm f = new SubFocosForm(area);
        if (!okCancel(f.panel, "Sub-focos")) return;

        List<String> nova = parseSubFocos(f.ta.getText());
        if (nova.isEmpty()) subFocos.remove(area); else subFocos.put(area, nova);
        host.saveData();
        host.toast("Sub-focos de \"" + area + "\" atualizados.");
    }
}
