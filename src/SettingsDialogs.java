import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Ajustes (o formulário), exportar/importar backup e a escolha do tema.
 *
 * Grava no MESMO {@link AppSettings} do StudyTracker. Recarregar tudo depois de importar e
 * reconstruir a tela depois de trocar o tema continuam no StudyTracker (mexem em todos os
 * campos dele), chamados pelo {@link Host}. As janelas passam pelo {@link Prompt}, que os
 * testes trocam para responder sem abrir nada.
 */
final class SettingsDialogs {

    /** O que o StudyTracker faz por nós. */
    interface Host {
        void saveData();
        void saveAll();                       // saveData + saveSessions + saveChecklist
        void reloadFromDisk();                // depois de importar
        void rebuildUI();                     // depois de trocar o tema
        void setAutoCycle(boolean on);        // checkbox do Pomodoro na tela Hoje
        void toast(String msg);
    }

    /** As janelas (JOptionPane, JFileChooser, Desktop). */
    interface Prompt {
        int confirm(Object message, String title, int optionType, int messageType);
        int option(String message, String title, Object[] options, Object initial);
        void message(String msg);                 // showMessageDialog(parent, msg)
        void info(String title, String msg);      // showMessageDialog(parent, msg, title, INFORMATION)
        File saveFile(File suggested);            // null = cancelou
        File openFile();                          // null = cancelou
        void openFolder(File dir) throws Exception;
    }

    static Prompt swingPrompt(Component parent) {
        return new Prompt() {
            public int confirm(Object message, String title, int optionType, int messageType) {
                return JOptionPane.showConfirmDialog(parent, message, title, optionType, messageType);
            }
            public int option(String message, String title, Object[] options, Object initial) {
                return JOptionPane.showOptionDialog(parent, message, title, JOptionPane.DEFAULT_OPTION,
                        JOptionPane.PLAIN_MESSAGE, null, options, initial);
            }
            public void message(String msg) { JOptionPane.showMessageDialog(parent, msg); }
            public void info(String title, String msg) {
                JOptionPane.showMessageDialog(parent, msg, title, JOptionPane.INFORMATION_MESSAGE);
            }
            public File saveFile(File suggested) {
                JFileChooser fc = new JFileChooser();          // abre em Documentos
                fc.setSelectedFile(suggested);
                return fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
            }
            public File openFile() {
                JFileChooser fc = new JFileChooser();
                return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
            }
            public void openFolder(File dir) throws Exception { Desktop.getDesktop().open(dir); }
        };
    }

    private final AppSettings cfg;
    private final StudyStore store;
    private final Host host;
    private final Prompt prompt;

    SettingsDialogs(AppSettings cfg, StudyStore store, Host host, Prompt prompt) {
        this.cfg = cfg;
        this.store = store;
        this.host = host;
        this.prompt = prompt;
    }

    // ── AJUSTES ─────────────────────────────────────────────────────────────

    void showSettings() {
        SettingsForm f = new SettingsForm();
        int r = prompt.confirm(f.panel, "Ajustes", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        f.apply();
    }

    /** "Desligado" ⇒ -1; "07:00" ⇒ 7. */
    static int reminderHourOf(String item) {
        return (item == null || item.startsWith("Des")) ? -1 : Integer.parseInt(item.substring(0, 2));
    }

    static String reminderItem(int hour) {
        return hour < 0 ? "Desligado" : String.format("%02d:00", hour);
    }

    /** O formulário "Ajustes". */
    final class SettingsForm {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JSpinner spFocus, spIdle, spTrans, spHyper;
        final JCheckBox cbCycle, cbAsk, cbFocus, cbUpdate;
        final JComboBox<String> cbReminder;
        final JButton btnExport, btnImport, btnFolder;

        SettingsForm() {
            spFocus = new JSpinner(new SpinnerNumberModel(cfg.minFocusMin, 0, 60, 1));
            spIdle  = new JSpinner(new SpinnerNumberModel(cfg.idleMinutes, 0, 120, 1));
            spTrans = new JSpinner(new SpinnerNumberModel(cfg.transitionMin, 0, 30, 1));
            spHyper = new JSpinner(new SpinnerNumberModel(cfg.hyperfocusH, 0, 12, 1));
            cbCycle = new JCheckBox("Encadear foco → pausa → foco automaticamente", cfg.pomoAutoCycle);
            cbCycle.setOpaque(false);

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.WEST;

            int row = 0;
            ScreenKit.addFormRow(panel, c, row++, "Foco mínimo (min):", spFocus);
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(ScreenKit.dlgHint("Sessões de cronômetro/pomodoro abaixo disso não são salvas."), c);
            c.gridwidth = 1;

            ScreenKit.addFormRow(panel, c, row++, "Inatividade (min):", spIdle);
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(ScreenKit.dlgHint("Sem mexer no PC por esse tempo, o cronômetro pausa e pergunta.  0 = desligado."), c);
            c.gridwidth = 1;

            ScreenKit.addFormRow(panel, c, row++, "Pausa entre blocos (min):", spTrans);
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(ScreenKit.dlgHint("Sugestão de pausa depois de salvar um bloco do cronômetro.  0 = desligado."), c);
            c.gridwidth = 1;

            ScreenKit.addFormRow(panel, c, row++, "Aviso de hiperfoco (h):", spHyper);
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(ScreenKit.dlgHint("Aviso pra respirar depois de N horas seguidas de cronômetro.  0 = desligado."), c);
            c.gridwidth = 1;

            cbReminder = new JComboBox<>();
            cbReminder.addItem("Desligado");
            for (int hRem = 5; hRem <= 23; hRem++) cbReminder.addItem(reminderItem(hRem));
            cbReminder.setSelectedItem(reminderItem(cfg.reminderHour));
            ScreenKit.addFormRow(panel, c, row++, "Lembrete diário:", cbReminder);
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
            panel.add(ScreenKit.dlgHint("Se não tiver estudado até essa hora, o app avisa (notificação do Windows)."), c);

            cbAsk = new JCheckBox("Ao terminar, perguntar sub-foco, energia e nota", cfg.askDetails);
            cbAsk.setOpaque(false);
            cbFocus = new JCheckBox("Ao começar uma sessão, recolher a janela numa faixa pequena", cfg.focusMode);
            cbFocus.setOpaque(false);
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(12, 4, 2, 4);
            panel.add(ScreenKit.dlgLabel("Modo foco", true), c);
            c.gridy = row++; c.insets = new Insets(2, 4, 4, 4);
            panel.add(cbFocus, c);
            c.gridy = row++; c.insets = new Insets(0, 4, 4, 4);
            panel.add(ScreenKit.dlgHint("A faixa fica por cima das outras janelas, com o tempo e a meta de hoje. \"Abrir app\" volta para a tela cheia."), c);
            c.gridy = row++; c.insets = new Insets(8, 4, 4, 4);
            panel.add(cbAsk, c);
            c.gridy = row++; c.insets = new Insets(0, 4, 4, 4);
            panel.add(ScreenKit.dlgHint("Tudo opcional: Enter salva. Desligado, salva direto (com o sub-foco sugerido, se houver)."), c);

            c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(12, 4, 2, 4);
            panel.add(ScreenKit.dlgLabel("Pomodoro", true), c);
            c.gridy = row++; c.insets = new Insets(2, 4, 4, 4);
            panel.add(cbCycle, c);

            c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.insets = new Insets(12, 4, 2, 4);
            panel.add(ScreenKit.dlgLabel("Backup", true), c);
            JPanel backupRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            btnExport = new JButton("Exportar dados…");
            btnImport = new JButton("Importar dados…");
            btnFolder = new JButton("Abrir pasta dos dados");
            btnExport.setFocusPainted(false); btnImport.setFocusPainted(false); btnFolder.setFocusPainted(false);
            btnExport.addActionListener(e -> exportData());
            btnImport.addActionListener(e -> importData());
            btnFolder.addActionListener(e -> openDataFolder());
            backupRow.add(btnExport); backupRow.add(btnImport); backupRow.add(btnFolder);
            c.gridy = row++; c.insets = new Insets(2, 4, 4, 4);
            panel.add(backupRow, c);
            c.gridy = row++; c.insets = new Insets(0, 4, 4, 4);
            panel.add(ScreenKit.dlgHint("Um arquivo único com matérias + histórico, para backup ou levar a outro PC."), c);

            cbUpdate = new JCheckBox("Avisar quando sair uma versão nova", cfg.updateCheck);
            cbUpdate.setOpaque(false);
            c.gridy = row++; c.insets = new Insets(12, 4, 2, 4);
            panel.add(ScreenKit.dlgLabel("Atualizações", true), c);
            c.gridy = row++; c.insets = new Insets(2, 4, 4, 4);
            panel.add(cbUpdate, c);
            c.gridy = row++; c.insets = new Insets(0, 4, 4, 4);
            panel.add(ScreenKit.dlgHint("Consulta o GitHub no máximo 1× por dia. Nenhum dado seu é enviado."), c);
        }

        /** O "OK": grava no cfg, atualiza o Pomodoro da tela Hoje, salva e avisa. */
        void apply() {
            cfg.minFocusMin   = (int) spFocus.getValue();
            cfg.idleMinutes   = (int) spIdle.getValue();
            cfg.transitionMin = (int) spTrans.getValue();
            cfg.hyperfocusH   = (int) spHyper.getValue();
            cfg.pomoAutoCycle = cbCycle.isSelected();
            cfg.focusMode     = cbFocus.isSelected();
            cfg.askDetails    = cbAsk.isSelected();
            cfg.updateCheck   = cbUpdate.isSelected();
            cfg.reminderHour  = reminderHourOf((String) cbReminder.getSelectedItem());
            host.setAutoCycle(cfg.pomoAutoCycle);
            host.saveData();
            host.toast("Ajustes salvos.");
        }
    }

    void openDataFolder() {
        try { prompt.openFolder(store.dir); }
        catch (Exception ex) { prompt.info("Pasta dos dados", store.dir.getAbsolutePath()); }
    }

    // ── EXPORTAR / IMPORTAR ─────────────────────────────────────────────────

    static String backupFileName(LocalDate day) {
        return "focaestudo-backup-" + day.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
    }

    void exportData() {
        host.saveAll();   // garante que o disco está atualizado
        File f = prompt.saveFile(new File(backupFileName(LocalDate.now())));
        if (f == null) return;
        try {
            Files.write(f.toPath(), store.exportText().getBytes(StandardCharsets.UTF_8));
            host.toast("Backup exportado.");
        } catch (IOException e) {
            prompt.message("Falha ao exportar: " + e.getMessage());
        }
    }

    static final String IMPORT_TEXT =
            "Isto substitui TODas as matérias, metas e histórico atuais pelos do arquivo.\n"
          + "Seus dados atuais vão para .bak. Continuar?";

    void importData() {
        File f = prompt.openFile();
        if (f == null) return;
        if (f.length() > StudyStore.MAX_IMPORT_BYTES) {
            prompt.message("Arquivo grande demais para ser um backup do FocaEstudo.");
            return;
        }
        try {
            String all = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            StudyStore.Backup b = StudyStore.parseBackup(all);
            if (b == null) {
                prompt.message("Arquivo não parece um backup do FocaEstudo.");
                return;
            }
            if (StudyStore.countSubjects(b.dados) == 0) {
                prompt.message("O backup não tem matérias válidas — importação cancelada.");
                return;
            }

            int r = prompt.confirm(IMPORT_TEXT, "Importar dados", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;

            store.importBackup(b);
            host.reloadFromDisk();
            host.toast("Dados importados.");
        } catch (Exception e) {
            prompt.message("Falha ao importar: " + e.getMessage());
        }
    }

    // ── TEMA ────────────────────────────────────────────────────────────────

    void chooseTheme() {
        String[] options = {"Claro", "Escuro"};
        int current = AppTheme.dark ? 1 : 0;
        int choice = prompt.option("Escolha o tema da interface:", "Tema", options, options[current]);
        if (choice < 0) return;
        if (choice == 0) AppTheme.applyLight();
        else             AppTheme.applyDark();
        host.rebuildUI();
    }
}
