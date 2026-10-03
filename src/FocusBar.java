import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Modo foco: durante uma sessão, a janela principal some e fica só esta faixa pequena,
 * sempre por cima, com a área, o tempo, a barra da meta de hoje e os botões essenciais.
 * O tempo fica à vista sem a tela cheia de coisas competindo pela atenção.
 */
public class FocusBar extends JFrame {

    public interface Actions {
        void togglePause();
        void finish();
        void expand();
    }

    private static Point lastLocation;   // a faixa volta para onde a pessoa deixou (nesta execução)

    private final JLabel area   = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JLabel time   = new JLabel("00:00:00");
    private final GaugeBar bar  = new GaugeBar();
    private final StyledButton pause, finish;
    private Point dragFrom;

    public FocusBar(Actions actions) {
        super("FocaEstudo · foco");
        setIconImages(AppTheme.appIcons());
        setUndecorated(true);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {   // Alt+F4 na faixa: volta para o app, sem perder a sessão
            @Override public void windowClosing(WindowEvent e) { actions.expand(); }
        });

        RoundedPanel root = new RoundedPanel(16, AppTheme.SURFACE, false);
        root.setBorderColor(AppTheme.ACCENT, 2f);
        root.setLayout(new BorderLayout(14, 0));
        root.setBorder(new EmptyBorder(10, 16, 12, 12));
        try { setBackground(new Color(0, 0, 0, 0)); }   // cantos arredondados
        catch (Exception ignored) { root.setOpaque(true); }

        area.setFont(AppTheme.FONT_BOLD);
        area.setForeground(AppTheme.TEXT_PRI);
        area.setIconTextGap(7);
        status.setFont(AppTheme.FONT_SMALL);
        status.setForeground(AppTheme.TEXT_SEC);
        bar.setPreferredSize(new Dimension(170, 6));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{area, status}) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel barWrap = new JPanel(new BorderLayout());
        barWrap.setOpaque(false);
        barWrap.setBorder(new EmptyBorder(5, 0, 3, 0));
        barWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        barWrap.setMaximumSize(new Dimension(220, 14));
        barWrap.add(bar);
        left.add(area);
        left.add(barWrap);
        left.add(status);

        time.setFont(new Font("Consolas", Font.BOLD, 24));
        time.setForeground(AppTheme.TEXT_PRI);

        pause  = new StyledButton("Pausar",   StyledButton.Variant.OUTLINED);
        finish = new StyledButton("Terminar", StyledButton.Variant.FILLED);
        StyledButton open = new StyledButton("Abrir app", StyledButton.Variant.TEXT);
        for (StyledButton b : new StyledButton[]{pause, finish, open}) {
            b.setFont(AppTheme.FONT_SMALL);
            b.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        }
        pause.addActionListener(e -> actions.togglePause());
        finish.addActionListener(e -> actions.finish());
        open.addActionListener(e -> actions.expand());
        open.setToolTipText("Volta para a janela completa (a sessão continua)");

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(time);
        right.add(Box.createHorizontalStrut(6));
        right.add(pause);
        right.add(finish);
        right.add(open);
        JPanel rightWrap = new JPanel(new GridBagLayout());   // centraliza na altura
        rightWrap.setOpaque(false);
        rightWrap.add(right);

        root.add(left,      BorderLayout.CENTER);
        root.add(rightWrap, BorderLayout.EAST);
        setContentPane(root);

        // Sem barra de título: arrasta-se pela própria faixa.
        MouseAdapter drag = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragFrom = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragFrom == null) return;
                Point p = getLocation();
                setLocation(p.x + e.getX() - dragFrom.x, p.y + e.getY() - dragFrom.y);
            }
            @Override public void mouseReleased(MouseEvent e) { lastLocation = getLocation(); }
        };
        for (Component c : new Component[]{root, left, area, status, time}) {
            c.addMouseListener(drag);
            c.addMouseMotionListener(drag);
        }
        root.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        pack();
        setSize(Math.max(getWidth(), 560), getHeight());
        if (lastLocation != null) setLocation(lastLocation);
        else {   // canto superior direito da área útil da tela
            Rectangle wa = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            setLocation(wa.x + wa.width - getWidth() - 24, wa.y + 24);
        }
    }

    /**
     * @param goalFrac fração da meta de hoje (contando o tempo que está correndo); &lt; 0 esconde a barra
     */
    public void update(String areaName, Color color, String timeText, String statusText,
                       boolean paused, double goalFrac, String finishLabel) {
        area.setText(areaName);
        area.setIcon(PlannerPanel.dot(color));
        time.setText(timeText);
        time.setForeground(paused ? AppTheme.TEXT_SEC : AppTheme.TEXT_PRI);
        status.setText(statusText);
        status.setForeground(paused ? AppTheme.WARNING : AppTheme.TEXT_SEC);
        pause.setText(paused ? "Retomar" : "Pausar");
        finish.setText(finishLabel);
        bar.setVisible(goalFrac >= 0);
        if (goalFrac >= 0) bar.set(goalFrac, color, goalFrac >= 1);
    }

    @Override public void dispose() {
        if (isShowing()) lastLocation = getLocation();
        super.dispose();
    }
}
