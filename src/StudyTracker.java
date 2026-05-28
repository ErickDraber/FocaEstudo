import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class StudyTracker extends JFrame {

    private static final String SAVE_FILE = "study_data.properties";
    private final Map<String, StudyData> studyDataMap = new LinkedHashMap<>();

    // --- Componentes da UI ---
    private PieChartPanel chartPanel;
    private JEditorPane summaryArea;
    private JComboBox<String> subjectComboBox;
    
    // --- Variáveis do Cronômetro (Stopwatch) ---
    private Timer stopwatch;
    private int elapsedSeconds = 0;
    private JLabel stopwatchLabel;
    private JButton startButton, stopButton, submitButton;

    // --- Variáveis do Timer (Countdown) ---
    private Timer countdownTimer;
    private int countdownSecondsRemaining = 0;
    private JLabel countdownLabel;
    private JSpinner focusSpinner, breakSpinner;
    private JButton startFocusButton, startBreakButton, stopCountdownButton;
    private JLabel statusLabel;

    // --- Componentes da Adição Manual ---
    private JTextField hoursField, minutesField;

    public StudyTracker() {
        loadData();
        
        setTitle("Rastreador de Estudos com Foco");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 750);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        chartPanel = new PieChartPanel(studyDataMap);
        chartPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        summaryArea = new JEditorPane();
        summaryArea.setEditable(false);
        summaryArea.setContentType("text/html");
        JScrollPane summaryScrollPane = new JScrollPane(summaryArea);
        summaryScrollPane.setPreferredSize(new Dimension(320, 0));

        JPanel controlPanel = createControlPanel();

        add(chartPanel, BorderLayout.CENTER);
        add(summaryScrollPane, BorderLayout.EAST);
        add(controlPanel, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveData();
                System.exit(0);
            }
        });

        // Inicializa os Timers
        setupTimers();
    }
    
    private void setupTimers() {
        // Timer do Cronômetro (contagem progressiva)
        stopwatch = new Timer(1000, e -> {
            elapsedSeconds++;
            updateStopwatchLabel();
        });

        // Timer do Pomodoro (contagem regressiva)
        countdownTimer = new Timer(1000, e -> {
            countdownSecondsRemaining--;
            updateCountdownLabel();
            if (countdownSecondsRemaining <= 0) {
                stopCountdown(true); // Para o timer e aciona o alerta
            }
        });
    }
    
    private JPanel createControlPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createTitledBorder("Painel de Controle"));

        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        subjectComboBox = new JComboBox<>();
        subjectComboBox.setPreferredSize(new Dimension(200, 25));
        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("Matéria Ativa:"), gbc);
        gbc.gridx = 1;
        topPanel.add(subjectComboBox, gbc);

        JPanel managementButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton editButton = new JButton("Editar Nome");
        JButton colorButton = new JButton("Mudar Cor");
        JButton deleteButton = new JButton("Deletar Matéria");
        JButton newSubjectButton = new JButton("Adicionar Matéria");
        JButton zeroTimeButton = new JButton("Zerar Tempo");
        managementButtons.add(editButton);
        managementButtons.add(colorButton);
        managementButtons.add(deleteButton);
        managementButtons.add(newSubjectButton);
        managementButtons.add(zeroTimeButton);

        gbc.gridx = 2;
        topPanel.add(managementButtons, gbc);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Cronômetro", createStopwatchPanel());
        tabbedPane.addTab("Adicionar Manualmente", createManualEntryPanel());
        tabbedPane.addTab("Timer Pomodoro", createTimerPanel()); // <-- NOVA ABA

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        editButton.addActionListener(e -> editSubjectName());
        colorButton.addActionListener(e -> changeSubjectColor());
        deleteButton.addActionListener(e -> deleteSubject());
        newSubjectButton.addActionListener(e -> addNewSubject());
        zeroTimeButton.addActionListener(e -> zeroOutSubjectTime());

        return mainPanel;
    }
    
    // NOVO: Painel para o Timer Pomodoro
    private JPanel createTimerPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Configurações de tempo
        focusSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 120, 1));
        breakSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));
        
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        settingsPanel.add(new JLabel("Foco (min):"));
        settingsPanel.add(focusSpinner);
        settingsPanel.add(Box.createHorizontalStrut(20));
        settingsPanel.add(new JLabel("Pausa (min):"));
        settingsPanel.add(breakSpinner);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        panel.add(settingsPanel, gbc);

        // Display do tempo
        countdownLabel = new JLabel("25:00");
        countdownLabel.setFont(new Font("Monospaced", Font.BOLD, 36));
        gbc.gridy = 1;
        panel.add(countdownLabel, gbc);

        // Status
        statusLabel = new JLabel("Pronto para iniciar");
        statusLabel.setForeground(Color.GRAY);
        gbc.gridy = 2;
        panel.add(statusLabel, gbc);

        // Botões de controle
        startFocusButton = new JButton("Iniciar Foco");
        startBreakButton = new JButton("Iniciar Pausa");
        stopCountdownButton = new JButton("Parar e Resetar");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(startFocusButton);
        buttonPanel.add(startBreakButton);
        buttonPanel.add(stopCountdownButton);
        gbc.gridy = 3;
        panel.add(buttonPanel, gbc);

        // Ações dos botões
        startFocusButton.addActionListener(e -> startCountdown(true));
        startBreakButton.addActionListener(e -> startCountdown(false));
        stopCountdownButton.addActionListener(e -> stopCountdown(false));

        return panel;
    }

    private JPanel createStopwatchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        stopwatchLabel = new JLabel("00:00:00");
        stopwatchLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
        startButton = new JButton("Iniciar");
        stopButton = new JButton("Parar");
        submitButton = new JButton("Enviar Tempo");
        panel.add(stopwatchLabel); panel.add(startButton); panel.add(stopButton); panel.add(submitButton);
        startButton.addActionListener(e -> startStopwatch());
        stopButton.addActionListener(e -> stopStopwatch());
        submitButton.addActionListener(e -> submitStopwatchTime());
        stopButton.setEnabled(false); submitButton.setEnabled(false);
        return panel;
    }

    private JPanel createManualEntryPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        hoursField = new JTextField(5);
        minutesField = new JTextField(5);
        JButton addButton = new JButton("Adicionar Tempo Manual");
        panel.add(new JLabel("Horas:")); panel.add(hoursField);
        panel.add(new JLabel("Minutos:")); panel.add(minutesField);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(addButton);
        addButton.addActionListener(e -> addManualTime());
        return panel;
    }
    
    // --- LÓGICA DO TIMER POMODORO ---
    
    private void startCountdown(boolean isFocus) {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (isFocus && subject == null) {
            JOptionPane.showMessageDialog(this, "Selecione uma matéria para iniciar o foco!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int minutes = isFocus ? (int) focusSpinner.getValue() : (int) breakSpinner.getValue();
        countdownSecondsRemaining = minutes * 60;
        
        if (isFocus) {
            statusLabel.setText("Estudando: " + subject);
            statusLabel.setForeground(new Color(0, 128, 0)); // Verde
        } else {
            statusLabel.setText("Em pausa...");
            statusLabel.setForeground(Color.BLUE);
        }
        
        updateCountdownLabel();
        countdownTimer.start();
        
        startFocusButton.setEnabled(false);
        startBreakButton.setEnabled(false);
        subjectComboBox.setEnabled(false);
    }
    
    private void stopCountdown(boolean finished) {
        countdownTimer.stop();
        
        if (finished) {
            Toolkit.getDefaultToolkit().beep(); // Alerta sonoro
            
            String subject = (String) subjectComboBox.getSelectedItem();
            boolean wasFocusSession = statusLabel.getText().startsWith("Estudando");
            
            if (wasFocusSession) {
                int minutesStudied = (int) focusSpinner.getValue();
                studyDataMap.get(subject).addMinutes(minutesStudied);
                updateUI();
                JOptionPane.showMessageDialog(this, "Sessão de foco concluída! " + minutesStudied + " min adicionados a " + subject, "Parabéns!", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Pausa concluída! Pronto para mais uma sessão de foco?", "Fim da Pausa", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        
        statusLabel.setText("Pronto para iniciar");
        statusLabel.setForeground(Color.GRAY);
        int focusMinutes = (int) focusSpinner.getValue();
        countdownSecondsRemaining = focusMinutes * 60; // Reseta para o tempo de foco
        updateCountdownLabel();

        startFocusButton.setEnabled(true);
        startBreakButton.setEnabled(true);
        subjectComboBox.setEnabled(true);
    }
    
    private void updateCountdownLabel() {
        int minutes = countdownSecondsRemaining / 60;
        int seconds = countdownSecondsRemaining % 60;
        countdownLabel.setText(String.format("%02d:%02d", minutes, seconds));
    }

    private void addManualTime() {
        String subject = (String) subjectComboBox.getSelectedItem();
        if (subject == null) { JOptionPane.showMessageDialog(this, "Por favor, selecione uma matéria!", "Aviso", JOptionPane.WARNING_MESSAGE); return; }
        try {
            int hours = hoursField.getText().trim().isEmpty() ? 0 : Integer.parseInt(hoursField.getText().trim());
            int minutes = minutesField.getText().trim().isEmpty() ? 0 : Integer.parseInt(minutesField.getText().trim());
            int totalMinutesToAdd = (hours * 60) + minutes;
            if (totalMinutesToAdd > 0) {
                studyDataMap.get(subject).addMinutes(totalMinutesToAdd); updateUI();
                hoursField.setText(""); minutesField.setText("");
                Toast.makeText(this, formatMinutes(totalMinutesToAdd) + " adicionado a " + subject, 2000);
            }
        } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Valores de tempo inválidos.", "Erro", JOptionPane.ERROR_MESSAGE); }
    }

    private void startStopwatch() {
        if (subjectComboBox.getSelectedItem() == null) { JOptionPane.showMessageDialog(this, "Por favor, selecione uma matéria primeiro!", "Aviso", JOptionPane.WARNING_MESSAGE); return; }
        stopwatch.start(); startButton.setEnabled(false); stopButton.setEnabled(true);
        submitButton.setEnabled(false); subjectComboBox.setEnabled(false); 
    }

    private void stopStopwatch() {
        stopwatch.stop(); startButton.setEnabled(true); stopButton.setEnabled(false);
        submitButton.setEnabled(elapsedSeconds > 0); subjectComboBox.setEnabled(true);
    }

    private void submitStopwatchTime() {
        if (elapsedSeconds == 0) return; String subject = (String) subjectComboBox.getSelectedItem();
        int minutesToAdd = (int) Math.round(elapsedSeconds / 60.0);
        if (minutesToAdd > 0) {
            studyDataMap.get(subject).addMinutes(minutesToAdd); updateUI();
            Toast.makeText(this, formatMinutes(minutesToAdd * 60) + " adicionado a " + subject, 3500);
        }
        elapsedSeconds = 0; updateStopwatchLabel(); submitButton.setEnabled(false);
    }

    private void updateStopwatchLabel() {
        int hours = elapsedSeconds / 3600; int minutes = (elapsedSeconds % 3600) / 60;
        int seconds = elapsedSeconds % 60;
        stopwatchLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void initializeUI() {
        if (studyDataMap.isEmpty()) { promptForSubjects(); }
        refreshSubjectComboBox(); updateUI(); setVisible(true);
    }

    private void refreshSubjectComboBox() {
        subjectComboBox.removeAllItems();
        for (String subject : studyDataMap.keySet()) { subjectComboBox.addItem(subject); }
    }

    private void promptForSubjects() {
        String subjectsString = JOptionPane.showInputDialog(this, "Bem-vindo! Digite as matérias, separadas por vírgula:", "Configuração Inicial", JOptionPane.PLAIN_MESSAGE);
        if (subjectsString != null && !subjectsString.trim().isEmpty()) {
            String[] subjects = subjectsString.split(","); Random rand = new Random();
            for (String subject : subjects) {
                String trimmedSubject = subject.trim();
                if (!trimmedSubject.isEmpty()) {
                    Color randomColor = new Color(rand.nextInt(200) + 25, rand.nextInt(200) + 25, rand.nextInt(200) + 25);
                    studyDataMap.put(trimmedSubject, new StudyData(0, randomColor));
                }
            }
        } else { JOptionPane.showMessageDialog(this, "Nenhuma matéria inserida. O programa será encerrado.", "Aviso", JOptionPane.WARNING_MESSAGE); System.exit(0); }
    }

    private void editSubjectName() {
        String oldName = (String) subjectComboBox.getSelectedItem(); if (oldName == null) return;
        String newName = JOptionPane.showInputDialog(this, "Digite o novo nome para a matéria:", "Editar Nome", JOptionPane.PLAIN_MESSAGE);
        if (newName != null && !newName.trim().isEmpty()) {
            newName = newName.trim();
            if (studyDataMap.containsKey(newName)) { JOptionPane.showMessageDialog(this, "Este nome de matéria já existe!", "Erro", JOptionPane.ERROR_MESSAGE); return; }
            StudyData data = studyDataMap.remove(oldName); studyDataMap.put(newName, data);
            refreshSubjectComboBox(); subjectComboBox.setSelectedItem(newName); updateUI();
        }
    }

    private void changeSubjectColor() {
        String subject = (String) subjectComboBox.getSelectedItem(); if (subject == null) return;
        Color currentColor = studyDataMap.get(subject).getColor();
        Color newColor = JColorChooser.showDialog(this, "Escolha uma cor para " + subject, currentColor);
        if (newColor != null) { studyDataMap.get(subject).setColor(newColor); updateUI(); }
    }

    private void deleteSubject() {
        String subject = (String) subjectComboBox.getSelectedItem(); if (subject == null) return;
        int confirmation = JOptionPane.showConfirmDialog(this, "Tem certeza que deseja deletar a matéria '" + subject + "'?", "Deletar Matéria", JOptionPane.YES_NO_OPTION);
        if (confirmation == JOptionPane.YES_OPTION) {
            studyDataMap.remove(subject); refreshSubjectComboBox(); updateUI();
        }
    }

    private void zeroOutSubjectTime() {
    String subject = (String) subjectComboBox.getSelectedItem();
    if (subject == null) {
        JOptionPane.showMessageDialog(this, "Por favor, selecione uma matéria!", "Aviso", JOptionPane.WARNING_MESSAGE);
        return;
    }

    int confirmation = JOptionPane.showConfirmDialog(
        this, 
        "Você tem certeza que deseja zerar todo o tempo de estudo para '" + subject + "'?\nEsta ação não pode ser desfeita.",
        "Confirmar Ação",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE);

    if (confirmation == JOptionPane.YES_OPTION) {
        // Acessa os dados da matéria e define os minutos como 0
        studyDataMap.get(subject).setMinutes(0);
        
        // Atualiza a interface gráfica para refletir a mudança
        updateUI();
        
        // Exibe uma mensagem de confirmação
        Toast.makeText(this, "Tempo de '" + subject + "' foi zerado.", 2000);
    }
    }

    private void addNewSubject() {
        String newName = JOptionPane.showInputDialog(this, "Digite o nome da nova matéria:", "Adicionar Matéria", JOptionPane.PLAIN_MESSAGE);
        if (newName != null && !newName.trim().isEmpty()) {
            String trimmedName = newName.trim();
            if (studyDataMap.containsKey(trimmedName)) { JOptionPane.showMessageDialog(this, "Essa matéria já existe!", "Erro", JOptionPane.ERROR_MESSAGE); return; }
            Random rand = new Random();
            Color randomColor = new Color(rand.nextInt(200) + 25, rand.nextInt(200) + 25, rand.nextInt(200) + 25);
            studyDataMap.put(trimmedName, new StudyData(0, randomColor));
            refreshSubjectComboBox(); subjectComboBox.setSelectedItem(trimmedName); updateUI();
        }
    }

    private void updateUI() {
        int totalMinutesAllSubjects = studyDataMap.values().stream().mapToInt(StudyData::getMinutes).sum();
        StringBuilder htmlText = new StringBuilder("<html><body style='font-family: Monospaced; font-size: 11pt; margin: 5px;'>");
        htmlText.append("<b>Resumo do Estudo:</b><br><br>"); htmlText.append("<table width='100%'>");
        for (Map.Entry<String, StudyData> entry : studyDataMap.entrySet()) {
            StudyData data = entry.getValue(); String hexColor = toHex(data.getColor()); String timeText = formatMinutes(data.getMinutes());
            htmlText.append("<tr>"); htmlText.append(String.format("<td width='80%%' style='color: %s;'>● %s</td>", hexColor, entry.getKey()));
            htmlText.append(String.format("<td width='20%%' align='right'><b>%s</b></td>", timeText)); htmlText.append("</tr>");
        }
        htmlText.append("<tr><td colspan='2'><hr></td></tr>");
        htmlText.append("<tr><td>● TOTAL DE ESTUDO</td>");
        htmlText.append(String.format("<td align='right'><b>%s</b></td>", formatMinutes(totalMinutesAllSubjects)));
        htmlText.append("</tr></table></body></html>");
        summaryArea.setText(htmlText.toString()); summaryArea.setCaretPosition(0); chartPanel.repaint();
    }

    private String formatMinutes(int totalMinutes) {
        if (totalMinutes < 0) return "0 h 0 min";
        int hours = totalMinutes / 60; int minutes = totalMinutes % 60;
        return String.format("%d h %d min", hours, minutes);
    }

    private String toHex(Color color) { return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    private void saveData() {
        Properties properties = new Properties();
        for (Map.Entry<String, StudyData> entry : studyDataMap.entrySet()) {
            StudyData data = entry.getValue(); String value = data.getMinutes() + "," + data.getColor().getRGB();
            properties.setProperty(entry.getKey(), value);
        }
        try (FileOutputStream out = new FileOutputStream(SAVE_FILE)) { properties.store(out, "Study Tracker Data");
        } catch (IOException e) { e.printStackTrace(); }
    }
    private void loadData() {
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(SAVE_FILE)) {
            properties.load(in);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key); String[] parts = value.split(",");
                int minutes = Integer.parseInt(parts[0]); Color color = new Color(Integer.parseInt(parts[1]));
                studyDataMap.put(key, new StudyData(minutes, color));
            }
        } catch (IOException | NumberFormatException e) { System.err.println("Arquivo de save não encontrado ou corrompido. Começando do zero."); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StudyTracker tracker = new StudyTracker();
            tracker.initializeUI();
        });
    }
    }

    class Toast extends JDialog {
        public static void makeText(JFrame owner, String text, long duration) { Toast toast = new Toast(owner, text); toast.setDuration(duration); toast.setVisible(true); }
        private long duration = 2000;
        public Toast(JFrame owner, String text) {
            super(owner, "", ModalityType.MODELESS); setLayout(new GridBagLayout());
            JPanel panel = new JPanel(); panel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
            panel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1)); panel.setBackground(new Color(240, 240, 240));
            JLabel label = new JLabel(text); label.setForeground(Color.BLACK); panel.add(label);
            setUndecorated(true); setBackground(new Color(0, 0, 0, 0)); setContentPane(panel);
            pack(); setLocation(owner.getX() + (owner.getWidth() - getWidth()) / 2, owner.getY() + owner.getHeight() - getHeight() - 50);
        }
        public void setDuration(long duration) { this.duration = duration; }
        @Override
        public void setVisible(boolean b) { if (b) { new Timer((int) duration, e -> setVisible(false)).start(); } super.setVisible(b); }
    }

    class PieChartPanel extends JPanel {
        private final Map<String, StudyData> data;
        public PieChartPanel(Map<String, StudyData> data) { this.data = data; setBackground(Color.WHITE); }
        private String getAbbreviation(String name) {
            StringBuilder abbreviation = new StringBuilder();
            for (char c : name.toCharArray()) { if (Character.isUpperCase(c) || Character.isDigit(c)) { abbreviation.append(c); } }
            if (abbreviation.length() == 0 && name.length() > 0) { abbreviation.append(Character.toUpperCase(name.charAt(0))); }
            return abbreviation.toString();
        }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); int totalMinutes = data.values().stream().mapToInt(StudyData::getMinutes).sum();
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (totalMinutes == 0) { g2d.drawString("Adicione tempo de estudo para começar.", (getWidth() / 2) - 100, getHeight() / 2); return; }
        int diameter = Math.min(getWidth(), getHeight()) - 40; int x = (getWidth() - diameter) / 2; int y = (getHeight() - diameter) / 2;
        double startAngle = 90.0;
        for (Map.Entry<String, StudyData> entry : data.entrySet()) {
            StudyData studyData = entry.getValue(); if (studyData.getMinutes() <= 0) continue;
            double arcAngle = (double) studyData.getMinutes() / totalMinutes * 360.0;
            g2d.setColor(studyData.getColor()); g2d.fillArc(x, y, diameter, diameter, (int) startAngle, (int) -Math.ceil(arcAngle));
            double labelAngle = startAngle - (arcAngle / 2.0); double labelRadius = diameter * 0.35;
            int labelX = (int) (x + diameter / 2 + labelRadius * Math.cos(Math.toRadians(labelAngle)));
            int labelY = (int) (y + diameter / 2 - labelRadius * Math.sin(Math.toRadians(labelAngle)));
            Color sliceColor = studyData.getColor();
            double luminance = (0.299 * sliceColor.getRed() + 0.587 * sliceColor.getGreen() + 0.114 * sliceColor.getBlue()) / 255;
            g2d.setColor(luminance > 0.5 ? Color.BLACK : Color.WHITE); g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            double percentage = (double) studyData.getMinutes() / totalMinutes * 100.0;
            String labelText = String.format("%s %.0f%%", getAbbreviation(entry.getKey()), percentage);
            FontMetrics fm = g2d.getFontMetrics(); int stringWidth = fm.stringWidth(labelText);
            g2d.drawString(labelText, labelX - stringWidth / 2, labelY + fm.getAscent() / 2);
            startAngle -= arcAngle;
        }
    }
    }