package fitpay.javacard.simplewallet.client;

import com.licel.jcardsim.utils.AIDUtil;
import fitpay.javacard.simplewallet.NotEnoughMoneyException;
import jnasmartcardio.Smartcardio;

import javax.smartcardio.*;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class GuiWalletClient {
    private JTextField creditAmountText;
    private JButton creditButton;
    private JTextField debitAmountText;
    private JButton debitButton;
    private JPanel creditPanel;
    private JPanel debitPanel;
    private JPanel balancePanel;
    private JLabel balanceLabel;
    private JPanel rootPanel;

    private volatile CardChannel cardChannel;

    public static void main(String[] args) throws Exception {
        JFrame frame = new JFrame("GUI Wallet");
        frame.setContentPane(new GuiWalletClient().rootPanel);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public GuiWalletClient() throws CardException, NoSuchAlgorithmException {
        creditButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    int amount = Integer.parseInt(creditAmountText.getText());
                    if (amount > 0) {
                        issueCredit(amount);
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
                        issueDebit(amount);
                    }
                } catch (NotEnoughMoneyException ex) {
                    showError("You can't afford that!");
                } catch (CardException ex) {
                    showError(String.format("Error issuing debit: %s", ex));
                }
            }
        });

        refreshUI();

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    connectCard();
                } catch (Exception ex) {
                    showError("Unable to connect to smart card");
                }
            }
        });
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(rootPanel, message);
    }

    private void connectCard() throws CardException, NoSuchAlgorithmException {
        TerminalFactory terminalFactory = TerminalFactory.getInstance("PC/SC", null, new Smartcardio());
        List<CardTerminal> terminals = terminalFactory.terminals().list();

        cardChannel = null;
        refreshUI();

        if (terminals.size() > 0) {
            CardTerminal cardTerminal = terminals.get(0);
            while (!cardTerminal.isCardPresent()) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ex) {
                    return;
                }
            }

            Card card = cardTerminal.connect("T=1");
            cardChannel = card.getBasicChannel();

            // Select app
            ResponseAPDU response = cardChannel.transmit(new CommandAPDU(AIDUtil.select("FITPAYRULEZ!")));
            if (response.getSW() != 0x9000) {
                showError("Smart card does not contain wallet app");
                cardChannel = null;
            }
        }

        refreshUI();
    }

    private void refreshUI() throws CardException {
        boolean cardConnected = (cardChannel != null);

        debitButton.setEnabled(cardConnected);
        debitAmountText.setEnabled(cardConnected);
        creditButton.setEnabled(cardConnected);
        creditAmountText.setEnabled(cardConnected);

        if (cardConnected) {
            short balance = getBalance();
            balanceLabel.setText(String.format("%d", balance));
        } else {
            balanceLabel.setText("Insert card...");
        }
    }

    private short getBalance() throws CardException {
        byte[] brequest = new byte[] {(byte)0xb0, (byte)0x50, (byte)0x00, (byte)0x00, (byte)0x02};
        ResponseAPDU response = cardChannel.transmit(new CommandAPDU(brequest));
        if (response.getSW() != 0x9000) {
            throw new RuntimeException("getBalance failed: " + response);
        }

        ByteBuffer buffer = ByteBuffer.wrap(response.getData());
        return buffer.asShortBuffer().get();
    }

    private void issueCredit(int amount) throws CardException {
        byte[] request = new byte[] {(byte)0xb0, (byte)0x40, (byte)0x00, (byte)0x00, (byte)0x01, (byte)amount};
        ResponseAPDU response = cardChannel.transmit(new CommandAPDU(request));
        if (response.getSW() != 0x9000) {
            throw new RuntimeException("issueCredit failed: " + response);
        }

        refreshUI();
    }

    private void issueDebit(int amount) throws CardException {
        byte[] request = new byte[] {(byte)0xb0, (byte)0x30, (byte)0x00, (byte)0x00, (byte)0x01, (byte)amount};
        ResponseAPDU response = cardChannel.transmit(new CommandAPDU(request));
        if (response.getSW() != 0x9000) {
            if (response.getSW() == 0xff85) {
                throw new NotEnoughMoneyException();
            }

            throw new RuntimeException("issueDebit failed: " + response);
        }

        refreshUI();
    }
}
