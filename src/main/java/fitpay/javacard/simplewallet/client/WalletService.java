package fitpay.javacard.simplewallet.client;

import com.licel.jcardsim.utils.AIDUtil;
import fitpay.javacard.simplewallet.NotEnoughMoneyException;
import jnasmartcardio.Smartcardio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private CardTerminal terminal;
    private CardChannel channel;

    private WalletService(CardTerminal terminal, CardChannel channel) {
        if (terminal == null) {
            throw new IllegalArgumentException("terminal is required");
        }

        this.terminal = terminal;

        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }

        this.channel = channel;
    }

    public boolean isCardPresent() {
        return (this.channel != null);
    }

    public void waitForRemoval() {
        log.debug("Waiting for card removal...");
        try {
            do {
                terminal.waitForCardAbsent(1000);
            } while (terminal.isCardPresent());
        } catch (CardException ex) {
            log.error("Error: %s", ex);
        }
    }

    public static WalletService acceptCard(int terminalIndex, boolean waitForCard) throws CardException, NoSuchAlgorithmException {
        log.debug("default type: " + TerminalFactory.getDefaultType());

        TerminalFactory terminalFactory = TerminalFactory.getInstance("PC/SC", null, new Smartcardio());
        List<CardTerminal> terminals = terminalFactory.terminals().list();
        log.debug("terminal count: " + terminals.size());

        if (terminals.size() == 0) {
            log.error("no terminal found");
            throw new RuntimeException("No terminal found");
        }

        CardTerminal cardTerminal = terminals.get(terminalIndex);
        log.debug("terminal: " + cardTerminal);

        if (waitForCard) {
            log.debug("waiting for card");
            while (true) {
                try {
                    cardTerminal.waitForCardPresent(1000);
                    if (cardTerminal.isCardPresent()) {
                        log.debug("card found... connecting....");
                        break;
                    }
                } catch (CardException ex) {
                    log.error("Card error: %s", ex);
                }
            }
        } else if (!cardTerminal.isCardPresent()) {
            return null;
        }

        Card card = cardTerminal.connect("T=1");
        log.debug("connected to card: " + card);

        CardChannel channel = card.getBasicChannel();

        // Select app
        ResponseAPDU response = channel.transmit(new CommandAPDU(AIDUtil.select("FITPAYRULEZ!")));
        log.debug("select response: " + response);
        if (response.getSW() != 0x9000) {
            log.error("no fitpay wallet found, is it installed?");
            throw new RuntimeException("Card does not contain wallet app");
        }

        return new WalletService(cardTerminal, channel);
    }

    public short getBalance() throws CardException {
        byte[] brequest = new byte[] {(byte)0xb0, (byte)0x50, (byte)0x00, (byte)0x00, (byte)0x02};
        ResponseAPDU response = channel.transmit(new CommandAPDU(brequest));
        if (response.getSW() != 0x9000) {
            throw new RuntimeException("getBalance failed: " + response);
        }

        ByteBuffer buffer = ByteBuffer.wrap(response.getData());
        return buffer.asShortBuffer().get();
    }

    public void issueCredit(int amount) throws CardException {
        byte[] request = new byte[] {(byte)0xb0, (byte)0x40, (byte)0x00, (byte)0x00, (byte)0x01, (byte)amount};
        ResponseAPDU response = channel.transmit(new CommandAPDU(request));
        if (response.getSW() != 0x9000) {
            throw new RuntimeException("issueCredit failed: " + response);
        }
    }

    public void issueDebit(int amount) throws CardException {
        byte[] request = new byte[] {(byte)0xb0, (byte)0x30, (byte)0x00, (byte)0x00, (byte)0x01, (byte)amount};
        ResponseAPDU response = channel.transmit(new CommandAPDU(request));
        if (response.getSW() != 0x9000) {
            if (response.getSW() == 0xff85) {
                throw new NotEnoughMoneyException();
            }

            throw new RuntimeException("issueDebit failed: " + response);
        }
    }
}
