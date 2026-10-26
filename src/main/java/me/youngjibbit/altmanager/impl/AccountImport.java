package me.youngjibbit.altmanager.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.account.Account;
import me.youngjibbit.account.AccountManager;
import me.youngjibbit.account.AccountTextImport;
import net.minecraft.client.Minecraft;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class AccountImport extends JPanel implements ActionListener {
    private static final long serialVersionUID = 1L;
    public final JButton openButton = new JButton("Open a File...");

    public AccountImport() { openButton.addActionListener(this); add(openButton); }

    @Override public void actionPerformed(ActionEvent event) {
        if (event.getSource() == openButton) {
            chooseAndImport(message -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message)));
        }
    }

    public static void chooseAndImport(Consumer<String> status) {
        Minecraft mc = Minecraft.getMinecraft();
        Thread worker = new Thread(() -> {
            try {
                final Path[] selected = new Path[1];
                SwingUtilities.invokeAndWait(() -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setFileFilter(new FileNameExtensionFilter("Account text files", "txt"));
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) selected[0] = chooser.getSelectedFile().toPath();
                });
                if (selected[0] == null) {
                    mc.addScheduledTask(() -> status.accept("Import cancelled."));
                    return;
                }
                List<Account> imported = AccountTextImport.read(selected[0]);
                mc.addScheduledTask(() -> {
                    AccountManager manager = Pulsar.INSTANCE.getAccountManager();
                    int added = 0;
                    for (Account account : imported) if (manager.addAccount(account) == account) added++;
                    int duplicates = imported.size() - added;
                    status.accept(manager.save() ? "Imported " + added + " accounts; " + duplicates
                            + " duplicates kept their existing credentials." : "Imported in memory; saving failed. Check the account-file status.");
                });
            } catch (Exception failure) {
                mc.addScheduledTask(() -> status.accept("Import failed. Check the UTF-8 file and its username[:password] lines."));
            }
        }, "Account import");
        worker.setDaemon(true);
        worker.start();
    }
}
