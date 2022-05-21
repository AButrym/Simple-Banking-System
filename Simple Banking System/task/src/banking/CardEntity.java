package banking;

public class CardEntity {
    private int id;
    private String number;
    private String pin;
    private int balance;

    public CardEntity(String number, String pin, int balance) {
        this.number = number;
        this.pin = pin;
        this.balance = balance;
    }

    public CardEntity(String number, String pin) {
        this(number, pin, 0);
    }

    public String getNumber() {
        return number;
    }

    public String getPin() {
        return pin;
    }

    public int getBalance() {
        return balance;
    }
}
