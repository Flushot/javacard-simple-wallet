package fitpay.javacard.simplewallet.applet;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import javacard.framework.AID;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import com.licel.jcardsim.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Test;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import com.licel.jcardsim.utils.ByteUtil;

import fitpay.javacard.simplewallet.applet.SimpleWalletApplet;

public class SimpleWalletAppletTest {
    private CardSimulator simulator;
    private AID aid = AIDUtil.create("FITPAYRULEZ!");

    @Before
    public void setup() {
        simulator = new CardSimulator();
        simulator.installApplet(aid, SimpleWalletApplet.class);
        simulator.selectApplet(aid);

        System.out.println("AID: " + ByteUtil.hexString(Hex.decode("FITPAYRULEZ!")));
        System.out.println("SELECT APDU: " + ByteUtil.hexString(AIDUtil.select(aid)));
        toggleDebug();
    }

    @Test
    public void canIssueCredit() {
        short amount = 5;

        for (int i = 1; i <= 3; i++) {
            issueCredit(amount);
            assertEquals(amount * i, getBalance());
        }
    }

    @Test
    public void canIssueCreditLarge() {
        short amount = 1000;

        for (int i = 1; i <= 3; i++) {
            issueCredit(amount);
            assertEquals(amount * i, getBalance());
        }
    }

    @Test
    public void canGetInitialZeroBalance() {
        assertEquals(0, getBalance());
    }

    @Test
    public void canIssueDebit() {
        issueCredit((short)10);
        issueDebit((short)5);

        assertEquals(5, getBalance());
    }

    @Test
    public void canIssueDebitLarge() {
        issueCredit((short)127);
        issueCredit((short)127);
        issueDebit((short)250);

        assertEquals(4, getBalance());
    }

    @Test
    public void negativeBalanceNotAllowed() {
        issueCredit((short)5);
        ResponseAPDU response = issueDebit((short)10, false);

        assertNotEquals(0x9000, response.getSW());
    }

    private ResponseAPDU issueDebit(short amount) {
        return issueDebit(amount, true);
    }

    private ResponseAPDU issueDebit(short amount, boolean forceSuccessful) {
        byte[] request;
        if (amount > 127) {
            // Large amount
            request = new byte[] {(byte)0xb0, (byte)0x30, (byte)0x00, (byte)0x00, (byte)0x02, (byte)(amount >> 8 & 0xFF), (byte)(amount & 0xFF)};
        } else {
            // Small amount
            request = new byte[] {(byte)0xb0, (byte)0x30, (byte)0x00, (byte)0x00, (byte)0x01, (byte)amount};
        }

        ResponseAPDU response = simulator.transmitCommand(new CommandAPDU(request));

        if (forceSuccessful) {
            assertEquals("issue debit failed", 0x9000, response.getSW());
        }

        return response;
    }

    private void issueCredit(short amount) {
        byte[] request;
        if (amount > 127) {
            // Large amount
            request = new byte[] {(byte)0xb0, (byte)0x40, (byte)0x00, (byte)0x00, (byte)0x02, (byte)(amount >> 8 & 0xFF), (byte)(amount & 0xFF)};
        } else {
            // Small amount
            request = new byte[] {(byte)0xb0, (byte)0x40, (byte)0x00, (byte)0x00, (byte)0x01, (byte)amount};
        }

        ResponseAPDU response = simulator.transmitCommand(new CommandAPDU(request));

        assertEquals("issue credit failed", 0x9000, response.getSW());
    }

    private short getBalance() {
        byte[] brequest = new byte[] {(byte)0xb0, (byte)0x50, (byte)0x00, (byte)0x00, (byte)0x02};
        ResponseAPDU response = simulator.transmitCommand(new CommandAPDU(brequest));

        assertEquals("get balance failed", 0x9000, response.getSW());

        ByteBuffer buf = ByteBuffer.wrap(response.getData());
        return buf.asShortBuffer().get();
    }

    private void toggleDebug() {
        byte[] request = new byte[] {(byte)0xb0, (byte)0x60, (byte)0x00, (byte)0x00};
        ResponseAPDU response = simulator.transmitCommand(new CommandAPDU(request));

        assertEquals("toggle debug failed", 0x9000, response.getSW());
    }
}
