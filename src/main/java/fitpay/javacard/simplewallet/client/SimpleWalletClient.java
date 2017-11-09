package fitpay.javacard.simplewallet.client;

import fitpay.javacard.simplewallet.NotEnoughMoneyException;
import org.apache.log4j.PropertyConfigurator;

public class SimpleWalletClient {
    public static void main(String[] args) throws Exception {
        PropertyConfigurator.configure(
                SimpleWalletClient.class.getClassLoader()
                        .getResourceAsStream("log4j.properties"));

        int terminalIndex = 0;
        WalletService walletService = WalletService.acceptCard(terminalIndex, false);
        if (walletService != null) {
            // Card already inserted
            System.out.println("current balance: " + walletService.getBalance());

            System.out.println("issuing credit");
            walletService.issueCredit(5);

            System.out.println("updated balance: " + walletService.getBalance());
        } else {
            // Card not inserted yet
            walletService = WalletService.acceptCard(terminalIndex, true);
            if (walletService != null) {
                // Card now inserted
                try {
                    System.out.println("current balance: " + walletService.getBalance());

                    System.out.println("spending some mula");
                    walletService.issueDebit(5);

                    System.out.println("spending completed, updated balance: " + walletService.getBalance());
                } catch (NotEnoughMoneyException e) {
                    System.out.println("sorry pal, you don't have enough money!");
                } catch (Exception e) {
                    System.err.println("uh oh scoobie: " + e.getMessage());
                }
            }
        }
    }
}
