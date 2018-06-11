import java.io.FileInputStream;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.Properties;

/** Runs queries against a back-end database */
public class Query {
  private String configFilename;
  private Properties configProps = new Properties();

  private String jSQLDriver;
  private String jSQLUrl;
  private String jSQLUser;
  private String jSQLPassword;

  // DB Connection
  private Connection conn;

  // Logged In User
  private String username; // customer username is unique

  // global variables

  // Canned queries

  private static final String CHECK_CREATE = "SELECT username, Email FROM userEmail";
  private PreparedStatement checkCreateStatement;
  private static final String CREATE_USER = "INSERT INTO USERS VALUES(?, ?)";
  private PreparedStatement createUserStatement;
  private static final String CREATE_USER_EMAIL = "INSERT INTO UserEmail VALUES(?, ?)";
  private PreparedStatement createUserEmailStatement;
  private static final String CHECK_LOGIN = "SELECT ?, password FROM USERS";
  private PreparedStatement CheckLoginStatement;

  // transactions
  private static final String BEGIN_TRANSACTION_SQL =
      "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; BEGIN TRANSACTION;";
  private PreparedStatement beginTransactionStatement;

  private static final String COMMIT_SQL = "COMMIT TRANSACTION";
  private PreparedStatement commitTransactionStatement;

  private static final String ROLLBACK_SQL = "ROLLBACK TRANSACTION";
  private PreparedStatement rollbackTransactionStatement;

  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
      return "ID: "
          + fid
          + " Day: "
          + dayOfMonth
          + " Carrier: "
          + carrierId
          + " Number: "
          + flightNum
          + " Origin: "
          + originCity
          + " Dest: "
          + destCity
          + " Duration: "
          + time
          + " Capacity: "
          + capacity
          + " Price: "
          + price;
    }
  }

  public Query(String configFilename) {
    this.configFilename = configFilename;
  }

  /* Connection code to SQL Azure.  */
  public void openConnection() throws Exception {
    configProps.load(new FileInputStream(configFilename));

    jSQLDriver = configProps.getProperty("flightservice.jdbc_driver");
    jSQLUrl = configProps.getProperty("flightservice.url");
    jSQLUser = configProps.getProperty("flightservice.sqlazure_username");
    jSQLPassword = configProps.getProperty("flightservice.sqlazure_password");

    /* load jdbc drivers */
    Class.forName(jSQLDriver).newInstance();

    /* open connections to the flights database */
    conn =
        DriverManager.getConnection(
            jSQLUrl, // database
            jSQLUser, // user
            jSQLPassword); // password

    conn.setAutoCommit(true); // by default automatically commit after each statement

    /* You will also want to appropriately set the transaction's isolation level through:
       conn.setTransactionIsolation(...)
       See Connection class' JavaDoc for details.
    */
  }

  public void closeConnection() throws Exception {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created. Do not drop any tables and do not clear the
   * flights table. You should clear any tables you use to store reservations and reset the next
   * reservation ID to be 1.
   */

  // public void clearTables() {
  //   try {
  //
  //   } catch (SQLException e) {
  //     clearTables();
  //   }
  // }

  /**
   * prepare all the SQL statements in this method. "preparing" a statement is almost like compiling
   * it. Note that the parameters (with ?) are still not filled in
   */
  public void prepareStatements() throws Exception {
    beginTransactionStatement = conn.prepareStatement(BEGIN_TRANSACTION_SQL);
    commitTransactionStatement = conn.prepareStatement(COMMIT_SQL);
    rollbackTransactionStatement = conn.prepareStatement(ROLLBACK_SQL);

    /* add here more prepare statements for all the other queries you need */
    /* . . . . . . */
    checkCreateStatement = conn.prepareStatement(CHECK_CREATE);
    createUserStatement = conn.prepareStatement(CREATE_USER);
    createUserEmailStatement = conn.prepareStatement(CREATE_USER_EMAIL);
    CheckLoginStatement = conn.prepareStatement(CHECK_LOGIN);
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username
   * @param password
   * @return If someone has already logged in, then return "User already logged in\n" For all other
   *     errors, return "Login failed\n".
   *     <p>Otherwise, return "Logged in as [username]\n".
   */
  public String loginUser(String username, String password) {
    if (this.username != null) {
      System.out.println(this.username);
      return "already logged in\n";
    }
    try {
      CheckLoginStatement.clearParameters();
      CheckLoginStatement.setString(1, this.username);
      ResultSet passwordSet = CheckLoginStatement.executeQuery();
      passwordSet.next();
      String pass = passwordSet.getString("password");
      password = applyHash(password);
      if (!pass.equals(password)) {
        return "incorrect password\n";
      } else {
        this.username = username;
        return "Logged in as " + username + "\n";
      }
    } catch (SQLException e) {
      // e.printStackTrace();
      return "Login failed\n";
    }
    //  return "Login failed\n";
  }

  public String logOut() {
    if (this.username == null) {
      return "No one logged in\n";
    }
    this.username = null;
    return "Log out successful\n";
  }

  /**
   * Implement the create user function.
   *
   * @param username new user's username. User names are unique the system.
   * @param password new user's password.
   * @param Email new user's Email, has to has @. Email is unique to a user
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String createUser(String username, String password, String Email) {
    try {
      if (!Email.contains("@")) {
        return "invalid email\n";
      }
      beginTransaction();
      ResultSet Check = checkCreateStatement.executeQuery();
      while (Check.next()) {
        String allEmails = Check.getString("Email");
        String allUsers = Check.getString("username");
        if (allEmails.equals(Email)) {
          return "Email already in use\n";
        }
        if (allUsers.equals(username)) {
          return "Username already in use\n";
        }
      }
      Check.close();
      createUserStatement.clearParameters();
      createUserStatement.setString(1, username);
      password = applyHash(password);
      createUserStatement.setString(2, password);
      createUserEmailStatement.clearParameters();
      createUserEmailStatement.setString(1, username);
      createUserEmailStatement.setString(2, Email);
      createUserStatement.executeUpdate();
      createUserEmailStatement.executeUpdate();
      return "Created user " + username + "\n";
    } catch (SQLException e) {
      try {
        rollbackTransaction();
      } catch (SQLException f) {
        f.printStackTrace();
      }
      e.printStackTrace();
      return "Failed to Create User\n";
    }
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
   *     the current session.
   * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
   *     If try to book an itinerary with invalid ID, then return "No such itinerary {@code
   *     itineraryId}\n". If the user already has a reservation on the same day as the one that they
   *     are trying to book now, then return "You cannot book two flights in the same day\n". For
   *     all other errors, return "Booking failed\n".
   *     <p>And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
   *     where reservationId is a unique number in the reservation system that starts from 1 and
   *     increments by 1 each time a successful reservation is made by any user in the system.
   */

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *     the user has no reservations, then return "No reservations found\n" For all other errors,
   *     return "Failed to retrieve reservations\n"
   *     <p>Otherwise return the reservations in the following format:
   *     <p>Reservation [reservation ID] paid: [true or false]:\n" [flight 1 under the reservation]
   *     [flight 2 under the reservation] Reservation [reservation ID] paid: [true or false]:\n"
   *     [flight 1 under the reservation] [flight 2 under the reservation] ...
   *     <p>Each flight should be printed using the same format as in the {@code Flight} class.
   * @see Flight#toString()
   */

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
   *     all other errors, return "Failed to cancel reservation [reservationId]"
   *     <p>If successful, return "Canceled reservation [reservationId]"
   *     <p>Even though a reservation has been canceled, its ID should not be reused by the system.
   */

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
   *     is not found / not under the logged in user's name, then return "Cannot find unpaid
   *     reservation [reservationId] under user: [username]\n" If the user does not have enough
   *     money in their account, then return "User has only [balance] in account but itinerary costs
   *     [cost]\n" For all other errors, return "Failed to pay for reservation [reservationId]\n"
   *     <p>If successful, return "Paid reservation: [reservationId] remaining balance: [balance]\n"
   *     where [balance] is the remaining balance in the user's account.
   */

  /* some utility functions below */

  public void beginTransaction() throws SQLException {
    conn.setAutoCommit(false);
    beginTransactionStatement.executeUpdate();
  }

  public void commitTransaction() throws SQLException {
    commitTransactionStatement.executeUpdate();
    conn.setAutoCommit(true);
  }

  public void rollbackTransaction() throws SQLException {
    rollbackTransactionStatement.executeUpdate();
    conn.setAutoCommit(true);
  }

  private static String applyHash(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes("UTF-8"));
      return bytesToHex(hash);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String bytesToHex(byte[] hash) {
    StringBuffer hexString = new StringBuffer();
    for (int i = 0; i < hash.length; i++) {
      String hex = Integer.toHexString(0xff & hash[i]);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }
}
