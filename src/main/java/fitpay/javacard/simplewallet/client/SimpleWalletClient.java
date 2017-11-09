package fitpay.javacard.simplewallet.client;

import java.nio.ByteBuffer;
import java.util.List;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardChannel;
import javax.smartcardio.TerminalFactory;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import com.licel.jcardsim.utils.AIDUtil;

import fitpay.javacard.simplewallet.NotEnoughMoneyException;
import jnasmartcardio.Smartcardio;

@SuppressWarnings("restriction")
public class SimpleWalletClient {
    @SuppressWarnings("restriction")
    public static void main(String[] args) throws Exception {
        TerminalFactory terminalFactory = TerminalFactory.getInstance("PC/SC", null, new Smartcardio());
        System.out.println("default type: " + TerminalFactory.getDefaultType());
        System.out.println("terminal count: " + terminalFactory.terminals().list().size());

        List<CardTerminal> terminals = terminalFactory.terminals().list();
        if (terminals.size() == 0) {
            System.err.println("no card reader terminal is present");
            return;
        }

        CardTerminal cardTerminal = terminals.get(0);
        System.out.println("terminal: " + cardTerminal);
        System.out.println("is card present: " + cardTerminal.isCardPresent());

        if (cardTerminal.isCardPresent()) {
            Card card = cardTerminal.connect("T=1");
            System.out.println("connected to card: " + card);

            CardChannel cardChannel = card.getBasicChannel();
            ResponseAPDU response = cardChannel.transmit(new CommandAPDU(AIDUtil.select("FITPAYRULEZ!")));
            System.out.println("select response: " + response);
            System.out.println("current balance: " + getBalance(cardChannel));

            System.out.println("issuing credit");
            issueCredit(cardChannel, 5);
            System.out.println("updated balance: " + getBalance(cardChannel));
        } else {
            System.out.println("waiting for card");

            while (true) {
                try {
                    if (cardTerminal.isCardPresent()) {
                        System.out.println("card found... connecting....");
                        if (cardTerminal.isCardPresent()) {
                            Card card = cardTerminal.connect("T=1");
                            System.out.println("connected to card: " + card);

                            CardChannel cardChannel = card.getBasicChannel();
                            ResponseAPDU response = cardChannel.transmit(new CommandAPDU(AIDUtil.select("FITPAYRULEZ!")));
                            if (response.getSW() != 0x9000) {
                                System.out.println("no fitpay wallet found, is it installed?");
                            } else {
                                System.out.println("current balance: " + getBalance(cardChannel));

                                System.out.println("spending some mula");
                                try {
                                    issueDebit(cardChannel, 5);
                                    System.out.println("spending completed, updated balance: " + getBalance(cardChannel));
                                } catch (NotEnoughMoneyException e) {
                                    System.out.println("sorry pal, you don't have enough money!");
                                }
                            }

                            while (cardTerminal.isCardPresent()) {
                                Thread.sleep(250);
                            }

                            System.out.println("session completed");
                        }
                    } else {
                        Thread.sleep(250);
                    }
                } catch (Exception e) {
                    System.err.println("uh oh scoobie: " + e.getMessage());
                }
            }
        }
    }

    @SuppressWarnings("restriction")
    private static void issueDebit(CardChannel ch, int amount) throws CardException {
        byte[] request = new byte[] {(byte)0xb0, (byte)0x30, (byte)0x00, (byte)0x00, (byte)0x01, (byte)amount};
        ResponseAPDU response = ch.transmit(new CommandAPDU(request));

        if (response.getSW() != 0x9000) {
            if (response.getSW() == 0xff85) {
                throw new NotEnoughMoneyException();
            }

            throw new RuntimeException("issue debit failed: " + response);
        }
    }

    @SuppressWarnings("restriction")
    private static void issueCredit(CardChannel ch, int amount) throws CardException {
        byte[] request = new byte[] {(byte)0xb0, (byte)0x40, (byte)0x00, (byte)0x00, (byte)0x01, (byte)amount};
        ResponseAPDU response = ch.transmit(new CommandAPDU(request));
        if (response.getSW() != 0x9000) {
            throw new RuntimeException("issue credit failed: " + response);
        }
    }

    @SuppressWarnings("restriction")
    private static short getBalance(CardChannel ch) throws CardException {
        byte[] brequest = new byte[] {(byte)0xb0, (byte)0x50, (byte)0x00, (byte)0x00, (byte)0x02};
        ResponseAPDU response = ch.transmit(new CommandAPDU(brequest));
        //assertEquals("get balance failed", 0x9000, response.getSW());

        ByteBuffer buffer = ByteBuffer.wrap(response.getData());
        return buffer.asShortBuffer().get();
    }
}
