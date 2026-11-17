import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;

/**
 * Tela Áreas: criar, editar, arquivar e deletar.
 * Esses botões ficavam sempre à vista embaixo do cronômetro (com o "Deletar" vermelho a
 * um clique). Aqui cada área tem os seus, e mexer numa área não troca a do cronômetro.
 *
 * A tela só MOSTRA as áreas e repassa os cliques: os diálogos e as regras ficam no
 * StudyTracker (via {@link Actions}) e no {@link Areas}. Recebe as MESMAS coleções do
 * StudyTracker (não cópias), então {@link #refresh()} sempre mostra o estado atual.
 */
final class AreasScreen extends JPanel {

    /** O que cada botão faz. Quem implementa é o StudyTracker. */
    interface Actions {
        void add();
        void subFocos(String area);
        void edit(String area);
        void color(String area);
        void zero(String area);
        void archive(String area);
        void unarchive(String area);
        void delete(String area);
    }

    /** Um bloco da lista: título ("Estudo", "Arquivadas"…) e as áreas embaixo dele. */
    record Section(String label, List<String> areas, boolean archived) {}

    private final Map<String, StudyData> studyDataMap;
    private final Set<String> archived;
    private final Function<String, String> typeOf;
    private final String[] typeKeys, typeLabels;
    private final Actions actions;
    private final ScreenKit.ScrollBody body;

    AreasScreen(Map<String, StudyData> studyDataMap, Set<String> archived, Function<String, String> typeOf,
                String[] typeKeys, String[] typeLabels, Actions actions) {
        super(new BorderLayout());
        this.studyDataMap = studyDataMap; this.archived = archived; this.typeOf = typeOf;
        this.typeKeys = typeKeys; this.typeLabels = typeLabels; this.actions = actions;

        StyledButton add = new StyledButton("+ Área", StyledButton.Variant.FILLED);
        add.addActionListener(e -> actions.add());
        body = new ScreenKit.ScrollBody(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(12, 16, 16, 16));

        setBackground(AppTheme.BG);
        add(ScreenKit.screenHeader("Áreas", "Suas áreas da vida, por tipo. O histórico de cada uma fica guardado.", add),
                BorderLayout.NORTH);
        add(ScreenKit.quietScroll(body), BorderLayout.CENTER);
    }

    /**
     * Como a lista se organiza, sem Swing: um bloco por tipo (na ordem dos tipos, só os que
     * têm área ativa; áreas na ordem do mapa) e, por último, "Arquivadas" (na ordem em que
     * foram arquivadas, só as que ainda existem).
     */
    static List<Section> sections(Collection<String> areas, Set<String> archived, Function<String, String> typeOf,
                                  String[] typeKeys, String[] typeLabels) {
        List<Section> out = new ArrayList<>();
        for (int t = 0; t < typeKeys.length; t++) {
            List<String> doTipo = new ArrayList<>();
            for (String s : areas)
                if (!archived.contains(s) && typeOf.apply(s).equals(typeKeys[t])) doTipo.add(s);
            if (!doTipo.isEmpty()) out.add(new Section(typeLabels[t], doTipo, false));
        }
        List<String> arq = new ArrayList<>();
        for (String s : archived) if (areas.contains(s)) arq.add(s);
        if (!arq.isEmpty()) out.add(new Section("Arquivadas", arq, true));
        return out;
    }

    /** Remonta a lista a partir das coleções (chamado pelo updateUI do StudyTracker). */
    void refresh() {
        body.removeAll();
        for (Section sec : sections(studyDataMap.keySet(), archived, typeOf, typeKeys, typeLabels)) {
            body.add(sectionLabel(sec.label()));
            for (String s : sec.areas()) body.add(areaRow(s, sec.archived()));
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent sectionLabel(String label) {
        JLabel l = new JLabel(label);
        l.setFont(AppTheme.FONT_BOLD);
        l.setForeground(AppTheme.TEXT_SEC);
        l.setBorder(new EmptyBorder(body.getComponentCount() == 0 ? 0 : 14, 4, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JComponent areaRow(String area, boolean arquivada) {
        StudyData sd = studyDataMap.get(area);
        RoundedPanel row = new RoundedPanel(12, AppTheme.SURFACE);
        row.setLayout(new GridBagLayout());   // nome e botões centralizados na mesma linha
        row.setBorder(new EmptyBorder(4, 12, 4, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JLabel nome = new JLabel(area, PlannerPanel.dot(sd.getColor()), SwingConstants.LEFT);
        nome.setIconTextGap(8);
        nome.setFont(AppTheme.FONT_LABEL);
        nome.setForeground(arquivada ? AppTheme.TEXT_SEC : AppTheme.TEXT_PRI);
        JLabel total = new JLabel(ScreenKit.fmtHM(sd.getMinutes()));
        total.setFont(AppTheme.FONT_SMALL);
        total.setForeground(AppTheme.TEXT_SEC);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(nome); left.add(total);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btns.setOpaque(false);
        StyledButton.Variant v = StyledButton.Variant.TEXT;
        if (arquivada) {
            btns.add(ScreenKit.smallBtn("Reativar", v, () -> actions.unarchive(area)));
        } else {
            btns.add(ScreenKit.smallBtn("Sub-focos", v, () -> actions.subFocos(area)));
            btns.add(ScreenKit.smallBtn("Editar",    v, () -> actions.edit(area)));
            btns.add(ScreenKit.smallBtn("Cor",       v, () -> actions.color(area)));
            btns.add(ScreenKit.smallBtn("Zerar",     v, () -> actions.zero(area)));
            btns.add(ScreenKit.smallBtn("Arquivar",  v, () -> actions.archive(area)));
        }
        btns.add(ScreenKit.smallBtn("Deletar", v, () -> actions.delete(area)));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.weightx = 1; row.add(left, c);
        c.gridx = 1; c.weightx = 0; row.add(btns, c);

        JPanel wrap = new JPanel(new BorderLayout());   // espaço entre as linhas
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(0, 0, 6, 0));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        wrap.add(row);
        return wrap;
    }
}
