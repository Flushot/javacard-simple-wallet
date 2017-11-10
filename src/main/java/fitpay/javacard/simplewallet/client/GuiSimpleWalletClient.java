package fitpay.javacard.simplewallet.client;

import fitpay.javacard.simplewallet.NotEnoughMoneyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GuiSimpleWalletClient {
    private static final Logger log = LoggerFactory.getLogger(GuiSimpleWalletClient.class);

    private JTextField creditAmountText;
    private JButton creditButton;
    private JTextField debitAmountText;
    private JButton debitButton;
    private JPanel creditPanel;
    private JPanel debitPanel;
    private JPanel balancePanel;
    private JLabel balanceLabel;
    private JPanel rootPanel;

    private volatile WalletService walletService;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Simple Wallet");
        frame.setContentPane(new GuiSimpleWalletClient().rootPanel);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private GuiSimpleWalletClient() {
        creditButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    int amount = Integer.parseInt(creditAmountText.getText());
                    if (amount > 0) {
                        walletService.issueCredit(amount);
                        refreshUI();
                    }
                } catch (CardException ex) {
                    showError(String.format("Error issuing credit: %s", ex));
                }
            }
        });

        debitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    int amount = Integer.parseInt(debitAmountText.getText());
                    if (amount > 0) {
                        walletService.issueDebit(amount);
                        refreshUI();
                    }
                } catch (NotEnoughMoneyException ex) {
                    showError("You can't afford that!");
                } catch (CardException ex) {
                    showError(String.format("Error issuing debit: %s", ex));
                }
            }
        });

        refreshUI();
        connectCard();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(rootPanel, message);
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
                        log.error("Card failure: %s", ex);
                        //exitApp();
                    }

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            refreshUI();
                        }
                    });

                    if (walletService != null && walletService.isCardPresent()) {
                        walletService.waitForRemoval();
                        walletService = null;
                    }

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            refreshUI();
                        }
                    });
                }
            }
        };

        cardConnectorWorker.execute();
    }

    private void refreshUI() {
        boolean isCardPresent = (walletService != null && walletService.isCardPresent());

        debitButton.setEnabled(isCardPresent);
        debitAmountText.setEnabled(isCardPresent);
        creditButton.setEnabled(isCardPresent);
        creditAmountText.setEnabled(isCardPresent);

        String balanceText;
        if (isCardPresent) {
            try {
                short balance = walletService.getBalance();
                balanceText = String.format("%d", balance);
            } catch (CardException ex) {
                balanceText = String.format("Error: %s", ex);
            }
        } else {
            balanceText = "Waiting for card...";
        }

        balanceLabel.setText(balanceText);
    }
}
