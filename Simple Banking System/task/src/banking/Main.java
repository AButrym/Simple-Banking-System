package banking;

import java.util.*;
import java.util.function.Supplier;

import static java.util.function.Predicate.not;

public class Main {
    public static void main(String[] args) {
        try (var cardRepository = new CardRepository(
                getParameter("-fileName", args).orElse("db.s3db"))) {

            new Bank(cardRepository).run();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Optional<String> getParameter(String name, String[] args) {
        return Arrays.stream(args)
                .dropWhile(not(name::equals))
                .skip(1)
                .findFirst();
    }
}


class Bank {
    private static final Scanner SCANNER = new Scanner(System.in);
    private CardRepository cardRepository;

    public Bank(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public void run() {
        State state = this.start;
        while (state != State.TERMINAL) {
            state = state.operate();
        }
    }

    private interface State {
        static State showMenu(State self, Option... options) {
            for (var option : options) {
                System.out.printf("%d. %s%n", option.id, option.name);
            }
            String response = SCANNER.nextLine();
            try {
                int chosenOption = Integer.parseInt(response);
                for (var option : options) {
                    if (option.id == chosenOption) {
                        return option.targetState.get();
                    }
                }
            } catch (NumberFormatException ex) { /**/ }
            System.out.println("Can't process your input: " + response);
            return self;
        }

        State operate();

        State TERMINAL = () -> State.TERMINAL;
    }

    private static class Option {
        int id;
        String name;
        Supplier<State> targetState;

        public Option(int id, String name, Supplier<State> targetState) {
            this.id = id;
            this.name = name;
            this.targetState = targetState;
        }
    }

    private static class Card {
        private static final Random RANDOM = new Random();

        private final String number;
        private final String pin;

        private Card(String number, String pin) {
            this.number = number;
            this.pin = pin;
        }

        private Card(String number) {
            this(number, generatePin());
        }

        private Card() {
            this(generateNewNumber());
        }

        private static String generateNewNumber() {
            int newId = RANDOM.nextInt(1_000_000_000);
            var result = String.format("400000%09d", newId);
            return result + calcLuhnCheckDigit(result);
        }

        private static String calcLuhnCheckDigit(String number) {
            int sum = 0;
            for (int i = 0; i < number.length(); i++) {
                int d = number.charAt(i) - '0';
                if (i % 2 == 0) {
                    d = d > 4 ? 2 * d - 9 : 2 * d;
                }
                sum += d;
            }
            return String.valueOf((200 - sum) % 10);
        }

        private static String generatePin() {
            return String.format("%04d", RANDOM.nextInt(10_000));
        }

        public static Card issueNew() {
            return new Card();
        }

        public static boolean checkLuhn(String number) {
            return number.length() == 16 &&
                    calcLuhnCheckDigit(number.substring(0, 15))
                            .equals(number.substring(15));
        }

        @Override
        public String toString() {
            return "Your card number:\n" +
                    number + "\n" +
                    "Your card PIN:\n" +
                    pin + "\n";
        }
    }

    private final State start = () -> State.showMenu(this.start,
            new Option(1, "Create an account", () -> this.createAccount),
            new Option(2, "Log into account", () -> this.login),
            new Option(0, "Exit", () -> this.exit)
    );

    private final State createAccount = () -> {
        Card card;
        do {
            card = Card.issueNew();
        } while (cardRepository.hasCardWithNumber(card.number));
        cardRepository.save(new CardEntity(card.number, card.pin, 0));
        System.out.println("Your card has been created");
        System.out.println(card);
        return this.start;
    };

    private final State login = () -> {
        System.out.println("Enter your card number:");
        var number = SCANNER.nextLine();
        System.out.println("Enter your PIN:");
        var pin = SCANNER.nextLine();
        if (cardRepository.hasCardWithNumberAndPin(number, pin)) {
            return new LoggedAs(number);
        } else {
            System.out.println("Wrong card number or PIN!");
            return this.start;
        }
    };

    private final State exit = () -> {
        System.out.println("Bye!");
        return State.TERMINAL;
    };

    private class LoggedAs implements State {
        private final String number;

        public LoggedAs(String number) {
            System.out.println("You have successfully logged in!");
            this.number = number;
        }

        public String getNumber() {
            return number;
        }

        @Override
        public State operate() {
            return State.showMenu(this,
                    new Option(1, "Balance", () -> this.balance),
                    new Option(2, "Add income", () -> this.addIncome),
                    new Option(3, "Do transfer", () -> this.doTransfer),
                    new Option(4, "Close account", () -> this.closeAccount),
                    new Option(5, "Log out", () -> this.logout),
                    new Option(0, "Exit", () -> Bank.this.exit)
            );
        }

        private final State balance = () -> {
            System.out.println("Balance: " +
                    cardRepository.getBalanceByNumber(getNumber()));
            return this;
        };

        private final State logout = () -> {
            System.out.println("You have successfully logged out!");
            return Bank.this.start;
        };

        private final State addIncome = () -> {
            System.out.println("Enter income:");
            String response = SCANNER.nextLine();
            try {
                int deposit = Integer.parseInt(response);
                if (deposit > 0) {
                    cardRepository.addIncomeToAccount(deposit, getNumber());
                }
            } catch (NumberFormatException e) {
                // nothing
            }
            System.out.println("There was a problem with your response: " +
                    response);
            return this;
        };

        private final State doTransfer = () -> {
            System.out.println("Transfer\n" +
                    "Enter card number:");
            String recipientNumber = SCANNER.nextLine();
            if (!Card.checkLuhn(recipientNumber)) {
                System.out.println("Probably you made a mistake in the card number." +
                        " Please try again!");
            } else if (!cardRepository.hasCardWithNumber(recipientNumber)) {
                System.out.println("Such a card does not exist.");
            } else {
                System.out.println("Enter how much money you want to transfer:");
                int money = Integer.parseInt(SCANNER.nextLine());
                if (money > 0) {
                    try {
                        cardRepository.transfer(getNumber(), recipientNumber, money);
                        System.out.println("Success!");
                    } catch (NotEnoughMoneyException e) {
                        System.out.println("Not enough money!");
                    }
                }
            }
            return this;
        };

        private final State closeAccount = () -> {
            cardRepository.deleteCardWithNumber(getNumber());
            return Bank.this.start;
        };
    }
}