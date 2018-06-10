import java.io.FileInputStream;
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
  private int itenaryCount = 0;
  private int balance = 0;
  private Map<Integer, List<Integer>> itenary;
  private Map<Integer, Integer> itenaryDay;
  private Map<Integer, Integer> itenaryPrice;

  // Canned queries

  private static final String CHECK_FLIGHT_CAPACITY =
      "SELECT capacity FROM FlightCapacity WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;
  private static final String addUser = "INSERT INTO Users VALUES( ?, ?, ?);";
  private PreparedStatement createUserStatement;
  private static final String allUsers = "SELECT *" + " FROM Users;";
  private PreparedStatement getUsers;
  private static final String singleHop =
      "Select Top(?) fid, origin_city, dest_city, flight_num, carrier_id, actual_time, day_of_month, capacity, price "
          + "From Flights "
          + "Where origin_city = ? AND dest_city = ? AND day_of_month = ? And Not actual_time = 0"
          + " ORDER BY actual_time, fid ASC"
          + ";";
  private PreparedStatement getSingleHop;
  private static final String indirect =
      "SELECT DISTINCT Top(?) F.fid AS fid, F.origin_city AS origin_city, F.dest_city as dest_city, F.flight_num AS num, F.carrier_id as carrier_id, F.actual_time as actual_time, F.day_of_month as day_of_month, F.capacity as capacity, F.price as price,"
          + " F2.fid AS fid2, F2.dest_city as dest_city2, F2.flight_num AS num2, F2.carrier_id as carrier_id2, F2.actual_time as actual_time2, F2.capacity as capacity2, F2.price as price2,  (F.actual_time + F2.actual_time) AS totalTime"
          + " FROM FLIGHTS AS F, FLIGHTS AS F2 "
          + " WHERE F.origin_city = ? "
          + " AND F.dest_city = F2.origin_city "
          + " AND F.day_of_month = ? "
          + " AND F2.day_of_month = F.day_of_month "
          + " AND F2.dest_city = ? And Not (F.actual_time = 0 Or F2.actual_time = 0)"
          + "Order by (F.actual_time + F2.actual_time), F.fid ASC";
  private PreparedStatement getIndirect;
  private static final String reservationsDay =
      "Select day" + " From Reservations" + " Where username = ?";
  private PreparedStatement getReservations;
  private static final String reservationsNumber = "Select max(rid) AS last" + " From Reservations";
  private PreparedStatement getReservationsNumber;
  private static final String addReservation =
      "Insert Into Reservations " + "values" + "( ?, ?, ?, ?, ?, ?)";
  private PreparedStatement addRes;
  private static final String unpaidReservations =
      "Select price, fid" + " From Reservations" + " Where username = ? AND paid = 0 AND rid = ?";
  private PreparedStatement getUnpaidReservations;
  private static final String allReservations =
      "Select price, fid, paid" + " From Reservations" + " Where username = ? AND rid = ?";
  private PreparedStatement getAllReservations;
  private static final String reservationValues =
      "Select R.rid, R.paid, F.fid, F.day_of_month, F.carrier_id, F.flight_num, F.price,"
          + " F.origin_city, F.dest_city, F.actual_time, F.capacity"
          + " From Reservations AS R, Flights as F"
          + " Where R.username = ? AND R.fid = F.fid ";
  private PreparedStatement getReservationsValues;
  private static final String updateBalance =
      "update Users" + " Set balance = ?" + " Where username = ?";
  private PreparedStatement doUpdateBalance;
  private static final String setReservation =
      "update Reservations" + " Set paid = 1" + " Where rid = ?;";
  private PreparedStatement setReservationsValues;
  private static final String updateCapacity =
      "Update FlightCapacity " + "Set capacity = ? " + "where fid = ?";
  private PreparedStatement doUpdateCapacity;
  private static final String deleteReservation =
      "Update Reservations "
          + "Set username = null, paid = null, price = null, day = null"
          + " Where rid = ?";
  private PreparedStatement doDeleteReservation;
  private static final String DeleteSearchSQL = "Delete From Users";
  private PreparedStatement searchStatement;
  private static final String deleteReservationTable = "Delete From Reservations";
  private PreparedStatement doDeleteReservationTable;
  private static final String delFC = "Delete From FlightCapacity";
  private PreparedStatement doDelFC;
  private static final String insertFC =
      "INSERT into FlightCapacity SELECT fid, capacity FROM Flights";
  private PreparedStatement doInsertFC;

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
  public void clearTables() {
    try {
      searchStatement.executeUpdate();
      doDeleteReservationTable.executeUpdate();
      doDelFC.executeUpdate();
      doInsertFC.executeUpdate();
    } catch (SQLException e) {
      clearTables();
    }
  }

  /**
   * prepare all the SQL statements in this method. "preparing" a statement is almost like compiling
   * it. Note that the parameters (with ?) are still not filled in
   */
  public void prepareStatements() throws Exception {
    beginTransactionStatement = conn.prepareStatement(BEGIN_TRANSACTION_SQL);
    commitTransactionStatement = conn.prepareStatement(COMMIT_SQL);
    rollbackTransactionStatement = conn.prepareStatement(ROLLBACK_SQL);
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);

    /* add here more prepare statements for all the other queries you need */
    /* . . . . . . */
    createUserStatement = conn.prepareStatement(addUser);
    getUsers = conn.prepareStatement(allUsers);
    getSingleHop = conn.prepareStatement(singleHop);
    addRes = conn.prepareStatement(addReservation);
    getIndirect = conn.prepareStatement(indirect);
    getReservations = conn.prepareStatement(reservationsDay);
    getReservationsNumber = conn.prepareStatement(reservationsNumber);
    getUnpaidReservations = conn.prepareStatement(unpaidReservations);
    getAllReservations = conn.prepareStatement(allReservations);
    doUpdateBalance = conn.prepareStatement(updateBalance);
    getReservationsValues = conn.prepareStatement(reservationValues);
    setReservationsValues = conn.prepareStatement(setReservation);
    doUpdateCapacity = conn.prepareStatement(updateCapacity);
    doDeleteReservation = conn.prepareStatement(deleteReservation);
    searchStatement = conn.prepareStatement(DeleteSearchSQL);
    doDeleteReservationTable = conn.prepareStatement(deleteReservationTable);
    doDelFC = conn.prepareStatement(delFC);
    doInsertFC = conn.prepareStatement(insertFC);
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
  public String transaction_login(String username, String password) {
    if (this.username != null) {
      return "User already logged in\n";
    }
    try {
      getUsers.clearParameters();
      ResultSet nameList = getUsers.executeQuery();
      while (nameList.next()) {
        String user = nameList.getString("username");
        String pass = nameList.getString("password");
        if (user.equals(username)) {
          if (!pass.equals(password)) {
            return "Login failed\n";
          }
          this.username = username;
          this.balance = nameList.getInt("balance");
          return "Logged in as " + username + "\n";
        }
      }
      nameList.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return "Login failed\n";
  }

  /**
   * Implement the create user function.
   *
   * @param username new user's username. User names are unique the system.
   * @param password new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *     otherwise).
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    if (initAmount < 0) {
      return "Failed to create user\n";
    }
    try {
      conn.setAutoCommit(false);
      getUsers.clearParameters();
      ResultSet nameList = getUsers.executeQuery();
      while (nameList.next()) {
        String user = nameList.getString("username");
        if (user.equals(username)) {
          return "Failed to create user\n";
        }
      }
      nameList.close();
      createUserStatement.clearParameters();
      createUserStatement.setString(1, username);
      createUserStatement.setString(2, password);
      createUserStatement.setInt(3, initAmount);
      createUserStatement.executeUpdate();
      commitTransactionStatement.executeUpdate();

    } catch (SQLException e) {
      return "Failed to create user\n";
    }
    return "Created user " + username + "\n";
  }

  /**
   * Implement the search function.
   *
   * <p>Searches for flights from the given origin city to the given destination city, on the given
   * day of the month. If {@code directFlight} is true, it only searches for direct flights,
   * otherwise is searches for direct flights and flights with two "hops." Only searches for up to
   * the number of itineraries given by {@code numberOfItineraries}.
   *
   * <p>The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight if true, then only search for direct flights, otherwise include indirect
   *     flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *     occurs, then return "Failed to search\n".
   *     <p>Otherwise, the sorted itineraries printed in the following format:
   *     <p>Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *     minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *     <p>Each flight should be printed using the same format as in the {@code Flight} class.
   *     Itinerary numbers in each search should always start from 0 and increase by 1.
   * @see Flight#toString()
   */
  public String transaction_search(
      String originCity,
      String destinationCity,
      boolean directFlight,
      int dayOfMonth,
      int numberOfItineraries) {
    // return transaction_search_unsafe(
    //     originCity, destinationCity, directFlight, dayOfMonth, numberOfItineraries);
    return search(originCity, destinationCity, directFlight, dayOfMonth, numberOfItineraries);
  }

  private String search(
      String originCity,
      String destinationCity,
      boolean directFlight,
      int dayOfMonth,
      int numberOfItineraries) {
    String flightData = "";
    itenary = new TreeMap<Integer, List<Integer>>();
    itenaryDay = new TreeMap<Integer, Integer>();
    itenaryPrice = new TreeMap<Integer, Integer>();
    ArrayList<Integer> time = new ArrayList<Integer>();
    ArrayList<String> flightStringData = new ArrayList<String>();
    ArrayList<Integer> dayOfMonthList = new ArrayList<Integer>();
    ArrayList<List<Integer>> fidList = new ArrayList<List<Integer>>();
    ArrayList<Integer> priceOfFlightList = new ArrayList<Integer>();
    int itenaryCount = 0;
    try {
      getSingleHop.clearParameters();
      getSingleHop.setInt(1, numberOfItineraries);
      getSingleHop.setString(2, originCity);
      getSingleHop.setString(3, destinationCity);
      getSingleHop.setInt(4, dayOfMonth);
      ResultSet oneHopResults = getSingleHop.executeQuery();
      while (oneHopResults.next()) {
        int result_flightId = oneHopResults.getInt("fid");
        int result_dayOfMonth = oneHopResults.getInt("day_of_month");
        String result_carrierId = oneHopResults.getString("carrier_id");
        int result_flightNum = oneHopResults.getInt("flight_num");
        String result_originCity = oneHopResults.getString("origin_city");
        String result_destCity = oneHopResults.getString("dest_city");
        int result_time = oneHopResults.getInt("actual_time");
        int result_capacity = oneHopResults.getInt("capacity");
        int result_price = oneHopResults.getInt("price");
        List<Integer> flightIds = new ArrayList<Integer>();
        flightIds.add(result_flightId);
        time.add(result_time);
        dayOfMonthList.add(result_dayOfMonth);
        priceOfFlightList.add(result_price);
        fidList.add(flightIds);
        flightStringData.add(
            ": 1 flight(s), "
                + (result_time)
                + " minutes\n"
                + "ID: "
                + result_flightId
                + " Day: "
                + result_dayOfMonth
                + " Carrier: "
                + result_carrierId
                + " Number: "
                + result_flightNum
                + " Origin: "
                + result_originCity
                + " Dest: "
                + result_destCity
                + " Duration: "
                + result_time
                + " Capacity: "
                + result_capacity
                + " Price: "
                + result_price
                + "\n");
        itenaryCount += 1;
      }
      oneHopResults.close();
      if (!directFlight) {
        getIndirect.clearParameters();
        getIndirect.setInt(1, numberOfItineraries - itenaryCount);
        getIndirect.setString(2, originCity);
        getIndirect.setInt(3, dayOfMonth);
        getIndirect.setString(4, destinationCity);
        ResultSet indirectResults = getIndirect.executeQuery();
        while (indirectResults.next()) {
          int result_flightId = indirectResults.getInt("fid");
          int result_dayOfMonth = indirectResults.getInt("day_of_month");
          String result_carrierId = indirectResults.getString("carrier_id");
          int result_flightNum = indirectResults.getInt("num");
          String result_originCity = indirectResults.getString("origin_city");
          String result_destCity = indirectResults.getString("dest_city");
          int result_time = indirectResults.getInt("actual_time");
          int result_capacity = indirectResults.getInt("capacity");
          int result_price = indirectResults.getInt("price");
          int result_flightId2 = indirectResults.getInt("fid2");
          String result_carrierId2 = indirectResults.getString("carrier_id2");
          int result_flightNum2 = indirectResults.getInt("num2");
          String result_destCity2 = indirectResults.getString("dest_city2");
          int result_time2 = indirectResults.getInt("actual_time2");
          int result_capacity2 = indirectResults.getInt("capacity2");
          int result_price2 = indirectResults.getInt("price2");
          List<Integer> flightIds = new ArrayList<Integer>();
          flightIds.add(result_flightId);
          flightIds.add(result_flightId2);
          fidList.add(flightIds);
          time.add(result_time + result_time2);
          dayOfMonthList.add(result_dayOfMonth);
          priceOfFlightList.add(result_price + result_price2);
          flightStringData.add(
              ": 2 flight(s), "
                  + (result_time + result_time2)
                  + " minutes\n"
                  + "ID: "
                  + result_flightId
                  + " Day: "
                  + result_dayOfMonth
                  + " Carrier: "
                  + result_carrierId
                  + " Number: "
                  + result_flightNum
                  + " Origin: "
                  + result_originCity
                  + " Dest: "
                  + result_destCity
                  + " Duration: "
                  + result_time
                  + " Capacity: "
                  + result_capacity
                  + " Price: "
                  + result_price
                  + "\n"
                  + "ID: "
                  + result_flightId2
                  + " Day: "
                  + result_dayOfMonth
                  + " Carrier: "
                  + result_carrierId2
                  + " Number: "
                  + result_flightNum2
                  + " Origin: "
                  + result_destCity
                  + " Dest: "
                  + result_destCity2
                  + " Duration: "
                  + result_time2
                  + " Capacity: "
                  + result_capacity2
                  + " Price: "
                  + result_price2
                  + "\n");
        }
      }
      if (flightStringData.size() == 0) {
        return "No flights match your selection\n";
      }
      for (int i = 0; i < time.size() - 1; i++) {
        for (int j = 0; j < time.size() - i - 1; j++) {
          if (time.get(j) > time.get(j + 1)) {
            int temp = time.get(j);
            time.set(j, time.get(j + 1));
            time.set(j + 1, temp);
            String temp2 = flightStringData.get(j);
            flightStringData.set(j, flightStringData.get(j + 1));
            flightStringData.set(j + 1, temp2);
            int temp3 = dayOfMonthList.get(j);
            dayOfMonthList.set(j, dayOfMonthList.get(j + 1));
            dayOfMonthList.set(j + 1, temp3);
            int temp4 = priceOfFlightList.get(j);
            priceOfFlightList.set(j, priceOfFlightList.get(j + 1));
            priceOfFlightList.set(j + 1, temp4);
            List<Integer> temp5 = fidList.get(j);
            fidList.set(j, fidList.get(j + 1));
            fidList.set(j + 1, temp5);
          }
        }
      }
      for (int i = 0; i < flightStringData.size(); i++) {
        flightData += "Itinerary " + i + flightStringData.get(i);
        itenary.put(i, fidList.get(i));
        itenaryDay.put(i, dayOfMonthList.get(i));
        itenaryPrice.put(i, priceOfFlightList.get(i));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return flightData;
  }

  /**
   * Same as {@code transaction_search} except that it only performs single hop search and do it in
   * an unsafe manner.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight
   * @param dayOfMonth
   * @param numberOfItineraries
   * @return The search results. Note that this implementation *does not conform* to the format
   *     required by {@code transaction_search}.
   */
  private String transaction_search_unsafe(
      String originCity,
      String destinationCity,
      boolean directFlight,
      int dayOfMonth,
      int numberOfItineraries) {
    StringBuffer sb = new StringBuffer();

    try {
      // one hop itineraries
      String unsafeSearchSQL =
          "SELECT TOP ("
              + numberOfItineraries
              + ") day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price "
              + "FROM Flights "
              + "WHERE origin_city = \'"
              + originCity
              + "\' AND dest_city = \'"
              + destinationCity
              + "\' AND day_of_month =  "
              + dayOfMonth
              + " "
              + "ORDER BY actual_time ASC";

      Statement searchStatement = conn.createStatement();
      ResultSet oneHopResults = searchStatement.executeQuery(unsafeSearchSQL);

      while (oneHopResults.next()) {
        int result_dayOfMonth = oneHopResults.getInt("day_of_month");
        String result_carrierId = oneHopResults.getString("carrier_id");
        String result_flightNum = oneHopResults.getString("flight_num");
        String result_originCity = oneHopResults.getString("origin_city");
        String result_destCity = oneHopResults.getString("dest_city");
        int result_time = oneHopResults.getInt("actual_time");
        int result_capacity = oneHopResults.getInt("capacity");
        int result_price = oneHopResults.getInt("price");

        sb.append(
            "Day: "
                + result_dayOfMonth
                + " Carrier: "
                + result_carrierId
                + " Number: "
                + result_flightNum
                + " Origin: "
                + result_originCity
                + " Destination: "
                + result_destCity
                + " Duration: "
                + result_time
                + " Capacity: "
                + result_capacity
                + " Price: "
                + result_price
                + "\n");
      }
      oneHopResults.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return sb.toString();
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
  public String transaction_book(int itineraryId) {
    try {
      beginTransaction();
      if (this.username == null) {
        return "Cannot book reservations, not logged in\n";
      }
      if (!itenary.containsKey(itineraryId)) {
        return "No such itinerary " + itineraryId + "\n";
      }
      getReservations.clearParameters();
      getReservations.setString(1, username);
      ResultSet res = getReservations.executeQuery();
      while (res.next()) {
        if (res.getInt("day") == itenaryDay.get(itineraryId)) {
          return "You cannot book two flights in the same day\n";
        }
      }
      res.close();
      // lock here
      int lastNum = 0;
      ResultSet lastVal = getReservationsNumber.executeQuery();
      lastVal.next();
      lastNum = lastVal.getInt("last");
      lastVal.close();
      lastNum += 1;
      List<Integer> flightsToBook = itenary.get(itineraryId);
      for (int i = 0; i < flightsToBook.size(); i++) {
        addRes.clearParameters();
        addRes.setInt(1, lastNum);
        addRes.setString(2, username);
        int day = itenaryDay.get(itineraryId);
        addRes.setInt(3, day);
        int flight = flightsToBook.get(i);
        int capacity = checkFlightCapacity(flight);
        if (capacity <= 0) {
          // rollback
          return "Booking failed\n";
        }
        addRes.setInt(4, flight);
        addRes.setInt(5, 0);
        addRes.setInt(6, itenaryPrice.get(itineraryId));
        addRes.executeUpdate();

        doUpdateCapacity.setInt(1, (capacity - 1));
        doUpdateCapacity.setInt(2, flight);
        doUpdateCapacity.executeUpdate();
        // String updateCapacity =
        //     "Update FlightCapacity "
        //         + "Set capacity = "
        //         + (capacity - 1)
        //         + " where fid = "
        //         + flight;
        // Statement capacityUpdateStatement = conn.createStatement();
        // capacityUpdateStatement.executeUpdate(updateCapacity);
      }
      commitTransaction();
      return "Booked flight(s), reservation ID: " + lastNum + "\n";
    } catch (SQLException e) {
      try {
        transaction_book(itineraryId);
        rollbackTransaction();
        return "Booking failed\n";
      } catch (SQLException f) {
        return "Booking failed\n";
      }
    }
  }

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
  public String transaction_reservations() {
    if (this.username == null) {
      return "Cannot view reservations, not logged in\n";
    }
    try {
      getReservationsValues.clearParameters();
      getReservationsValues.setString(1, username);
      ResultSet res = getReservationsValues.executeQuery();
      String output = "";

      String lastID = "";
      boolean paid;
      while (res.next()) {
        String id = res.getString("rid");
        if (res.getInt("paid") == 0) {
          paid = false;
        } else {
          paid = true;
        }
        Flight F = new Flight();
        F.fid = res.getInt("fid");
        F.dayOfMonth = res.getInt("day_of_month");
        F.carrierId = res.getString("carrier_id");
        F.flightNum = res.getString("flight_num");
        F.originCity = res.getString("origin_city");
        F.destCity = res.getString("dest_city");
        F.time = res.getInt("actual_time");
        F.capacity = res.getInt("capacity");
        F.price = res.getInt("price");
        if (id != lastID) {
          output += "Reservation " + id + " paid: " + paid + ":\n";
        }
        output += F.toString() + "\n";
        lastID = id;
      }
      if (output.length() == 0) {
        return "No reservations found\n";
      }
      return output;
    } catch (SQLException e) {
      return "Failed to retrieve reservations\n";
    }
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
   *     all other errors, return "Failed to cancel reservation [reservationId]"
   *     <p>If successful, return "Canceled reservation [reservationId]"
   *     <p>Even though a reservation has been canceled, its ID should not be reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    // only implement this if you are interested in earning extra credit for the HW!
    if (this.username == null) {
      return "Cannot cancel reservations, not logged in\n";
    }
    // int priceTemp = 0;
    boolean canceled = false;
    try {
      getAllReservations.clearParameters();
      getAllReservations.setString(1, username);
      getAllReservations.setInt(2, reservationId);
      ResultSet findAllReservations = getAllReservations.executeQuery();

      while (findAllReservations.next()) {
        int paid = findAllReservations.getInt("paid");
        int fid = findAllReservations.getInt("fid");
        int price = findAllReservations.getInt("price");
        // priceTemp = price;
        if (paid == 1) {
          balance += price;
        }
        doUpdateBalance.clearParameters();
        doUpdateBalance.setInt(1, balance);
        doUpdateBalance.setString(2, username);
        doUpdateBalance.executeUpdate();
        doDeleteReservation.setInt(1, reservationId);
        doDeleteReservation.executeUpdate();
        int capacity = checkFlightCapacity(fid);
        doUpdateCapacity.clearParameters();
        doUpdateCapacity.setInt(1, (capacity + 1));
        doUpdateCapacity.setInt(2, fid);
        doUpdateCapacity.executeUpdate();

        canceled = true;
      }
      if (canceled) {
        return "Canceled reservation " + reservationId + "\n";
      }
      findAllReservations.close();

    } catch (SQLException e) {
      return "Failed to cancel reservation " + reservationId + "\n";
    }
    return "Failed to cancel reservation " + reservationId + "\n";
  }

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
  public String transaction_pay(int reservationId) {
    // add transactions
    int price = 0;
    if (this.username == null) {
      return "Cannot pay, not logged in\n";
    }
    try {
      getUnpaidReservations.clearParameters();
      getUnpaidReservations.setString(1, username);
      getUnpaidReservations.setInt(2, reservationId);
      ResultSet findUnpaid = getUnpaidReservations.executeQuery();

      boolean found = false;
      while (findUnpaid.next()) {
        found = true;
        price = findUnpaid.getInt("price");
        if (price > balance) {
          return "User has only " + balance + " in account but itinerary costs " + price + "\n";
        }
      }
      if (!found) {
        return "Cannot find unpaid reservation "
            + reservationId
            + " under user: "
            + username
            + "\n";
      }
      findUnpaid.close();
    } catch (SQLException e) {
      return "Failed to pay for reservation " + reservationId + "\n";
    }
    try {
      balance -= price;
      doUpdateBalance.clearParameters();
      doUpdateBalance.setInt(1, balance);
      doUpdateBalance.setString(2, username);
      doUpdateBalance.executeUpdate();
      setReservationsValues.clearParameters();
      setReservationsValues.setInt(1, reservationId);
      setReservationsValues.executeUpdate();
      return "Paid reservation: " + reservationId + " remaining balance: " + balance + "\n";
    } catch (SQLException e) {
      balance += price;
      return "Failed to pay for reservation " + reservationId + "\n";
      // rollback;
    }
  }

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

  /**
   * Shows an example of using PreparedStatements after setting arguments. You don't need to use
   * this method if you don't want to.
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }
}
