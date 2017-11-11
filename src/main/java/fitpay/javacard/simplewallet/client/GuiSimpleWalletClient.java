package fitpay.javacard.simplewallet.client;

import fitpay.javacard.simplewallet.NotEnoughMoneyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;

public class GuiSimpleWalletClient {
    private static final Logger log = LoggerFactory.getLogger(GuiSimpleWalletClient.class);

    private JButton creditButton;
    private JFormattedTextField amountText;
    private JButton debitButton;
    private JLabel balanceLabel;
    private JPanel rootPanel;

    private volatile WalletService walletService;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Simple Wallet");
        frame.setContentPane(new GuiSimpleWalletClient().rootPanel);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        //frame.pack();
        frame.setSize(new Dimension(350, 100));
        frame.setResizable(false);
        frame.setVisible(true);
    }

    private GuiSimpleWalletClient() {
        Border padding = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        rootPanel.setBorder(padding);

        NumberFormat format = NumberFormat.getInstance();
        NumberFormatter formatter = new NumberFormatter(format);
        formatter.setValueClass(Integer.class);
        formatter.setMinimum(0);
        //formatter.setMaximum(Short.MAX_VALUE);  // Screws up input
        formatter.setAllowsInvalid(false);
        DefaultFormatterFactory formatterFactory = new DefaultFormatterFactory(formatter);
        amountText.setFormatterFactory(formatterFactory);

        creditButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                onCreditClicked();
            }
        });

        debitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                onDebitClicked();
            }
        });

        refreshUI();
        connectCard();
    }

    private void onCreditClicked() {
        String amountStr = amountText.getText();
        if (amountStr.length() == 0) {
            return;
        }

        try {
            short amount = Short.parseShort(amountStr);
            if (amount > 0) {
                walletService.issueCredit(amount);
                refreshUI();
            }
        } catch (Exception ex) {
            log.error("Credit error", ex);
            showError(String.format("Error issuing credit: %s", ex.getMessage()));
        }
    }

    private void onDebitClicked() {
        String amountStr = amountText.getText();
        if (amountStr.length() == 0) {
            return;
        }

        try {
            short amount = Short.parseShort(amountStr);
            if (amount > 0) {
                walletService.issueDebit(amount);
                refreshUI();
            }
        } catch (NotEnoughMoneyException ex) {
            showError("Insufficient funds");
        } catch (Exception ex) {
            log.error("Debit error", ex);
            showError(String.format("Error issuing debit: %s", ex.getMessage()));
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(rootPanel, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void exitApp() {
        Container frame = rootPanel.getParent();
        do {
            frame = frame.getParent();
        } while (!(frame instanceof JFrame));
        ((JFrame)frame).dispose();
    }

    private void connectCard() {
        SwingWorker cardConnectorWorker = new SwingWorker() {
            protected Object doInBackground() throws Exception {
                int terminalIndex = 0;
                while (true) {
                    try {
                        walletService = WalletService.acceptCard(terminalIndex, true);
                    } catch (Exception ex) {
                        log.error("acceptCard error:", ex);
                        showError(String.format("Error reading card: %s", ex.getMessage()));
                        exitApp();
                        break;
                    }

                    refreshUI();

                    if (walletService != null && walletService.isCardPresent()) {
                        walletService.waitForRemoval();
                        walletService = null;
                    }

                    refreshUI();
                }

                return null;
            }
        };

        cardConnectorWorker.execute();
    }

    private void refreshUI() {
        boolean isCardPresent = (walletService != null && walletService.isCardPresent());

        amountText.setEnabled(isCardPresent);
        debitButton.setEnabled(isCardPresent);
        creditButton.setEnabled(isCardPresent);

        String balanceText;
        if (isCardPresent) {
            try {
                short balance = walletService.getBalance();

                amountText.setEnabled(balance > 0);
                debitButton.setEnabled(balance > 0);

                balanceText = String.format("%d", balance);
            } catch (CardException ex) {
                log.error("Error getting balance", ex);
                balanceText = "Error";
                showError(String.format("Error getting balance: %s", ex.getMessage()));
            }
        } else {
            balanceText = "Waiting for card...";
        }

        balanceLabel.setText(balanceText);
    }
}
