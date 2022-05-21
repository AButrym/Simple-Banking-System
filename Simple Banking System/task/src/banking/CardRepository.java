package banking;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CardRepository implements AutoCloseable {
    private final Connection conn;

    public CardRepository(String fileName) throws SQLException {
        String url = "jdbc:sqlite:" + fileName;
        conn = DriverManager.getConnection(url);
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS card (\n"
                + "	id integer PRIMARY KEY,\n"
                + "	number TEXT NOT NULL,\n"
                + "	pin TEXT NOT NULL,\n"
                + "	balance INTEGER DEFAULT 0\n"
                + ");";
        try (var preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void save(CardEntity cardEntity) {
        String sql = "INSERT INTO card(number, pin, balance) VALUES(?,?,?);";
        try (var preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, cardEntity.getNumber());
            preparedStatement.setString(2, cardEntity.getPin());
            preparedStatement.setInt(3, cardEntity.getBalance());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Iterable<CardEntity> getAll() {
        String sql = "SELECT number, pin, balance FROM card;";
        List<CardEntity> res = new ArrayList<>();
        try (var ps = conn.prepareStatement(sql);
             var rs = ps.executeQuery();
        ) {
            while (rs.next()) {
                String number = rs.getString("number");
                String pin = rs.getString("pin");
                int balance = rs.getInt("balance");
                res.add(new CardEntity(number, pin, balance));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return res;
    }

    @Override
    public void close() throws Exception {
        try (conn) {
            //
        }
    }

    public int getBalanceByNumber(String number) {
        String sql = "SELECT balance FROM card WHERE number = ?;";
        try (var preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, number);
            try (var rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new IllegalArgumentException("Account not found: " + number);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasCardWithNumber(String number) {
        String sql = "SELECT 1 FROM card WHERE number = ?;";
        try (var preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, number);
            try (var rs = preparedStatement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasCardWithNumberAndPin(String number, String pin) {
        String sql = "SELECT 1 FROM card WHERE number = ? AND pin = ?;";
        try (var preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, number);
            preparedStatement.setString(2, pin);
            try (var rs = preparedStatement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addIncomeToAccount(int deposit, String number) {
        String sql = "UPDATE card SET balance = balance + ? WHERE number = ?;";
        try (var preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, deposit);
            preparedStatement.setString(2, number);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteCardWithNumber(String number) {
        String sql = "DELETE FROM card WHERE number = ?;";
        try (var preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, number);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void transfer(String sender, String recipient, int money) {
        try {
            conn.setAutoCommit(false);
            String sql1 = "UPDATE card\n" +
                    "SET balance = balance - ?\n" +
                    "WHERE number = ? AND balance >= ?;";
            try (var preparedStatement = conn.prepareStatement(sql1)) {
                preparedStatement.setInt(1, money);
                preparedStatement.setString(2, sender);
                preparedStatement.setInt(3, money);
                int res = preparedStatement.executeUpdate();
                if (res != 1) {
                    conn.rollback();
                    throw new NotEnoughMoneyException();
                }
            }
            String sql2 = "UPDATE card\n" +
                    "SET balance = balance + ?\n" +
                    "WHERE number = ?;";
            try (var preparedStatement = conn.prepareStatement(sql2)) {
                preparedStatement.setInt(1, money);
                preparedStatement.setString(2, recipient);
                int res = preparedStatement.executeUpdate();
                if (res == 1) {
                    conn.commit();
                } else {
                    conn.rollback();
                    throw new IllegalArgumentException(
                            "Problem during sending money to " + recipient);
                }
            }
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                e.addSuppressed(ex);
            }
            throw new RuntimeException(e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                //
            }
        }
    }
}
