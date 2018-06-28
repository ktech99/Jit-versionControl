import java.io.*;
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
  private static final String DELETE_PROJECT =
      "Update Project " + "Set creator = null, name = null, CreatedOn = null" + " Where ID = ?";
  private PreparedStatement DeleteProjectStatement;
  private static final String DELETE_CODE = "Delete From Code, version " + " Where ID = ?";
  private PreparedStatement DeleteCodestatement;
  private static final String CHECK_OWNER = "SELECT creator From PROJECT where ID = ?";
  private PreparedStatement CheckOwnerStatement;
  private static final String GET_PROJECTS = "SELECT ID, name FROM PROJECT WHERE Creator = ?";
  private PreparedStatement GetProjectStatement;
  private static final String GET_LAST_PROJECT = "SELECT MAX(ID) as ID FROM PROJECT";
  private PreparedStatement GetLastProjectStatement;
  private static final String CREATE_PROJECT = "INSERT INTO PROJECT VALUES(?, ?, ?, null)";
  private PreparedStatement CreateProjectStatement;
  private static final String CREATE_CODE = "INSERT INTO CODE VALUES(?, ?, ?, ?, ?)";
  private PreparedStatement CreateCodeStatement;
  private static final String CREATE_VERSION = "INSERT INTO VERSION VALUES(?, ?, ?, ?, ?)";
  private PreparedStatement CreateVersionStatement;
  private static final String DELETE_FROM_CODE = "Delete From Code " + " Where ID = ?";
  private PreparedStatement DeleteFromCodeStatement;
  private static final String GET_PROJECT_NAMES = "Select name From PROJECT WHERE creator = ?";
  private PreparedStatement GetProjectNamesStatement;
  private static final String GET_PROJECT_VERSIONS =
      "Select version, message From Version where ID = ?";
  private PreparedStatement GetProjectVersions;

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
    DeleteProjectStatement = conn.prepareStatement(DELETE_PROJECT);
    DeleteCodestatement = conn.prepareStatement(DELETE_CODE);
    CheckOwnerStatement = conn.prepareStatement(CHECK_OWNER);
    GetProjectStatement = conn.prepareStatement(GET_PROJECTS);
    GetLastProjectStatement = conn.prepareStatement(GET_LAST_PROJECT);
    CreateProjectStatement = conn.prepareStatement(CREATE_PROJECT);
    CreateCodeStatement = conn.prepareStatement(CREATE_CODE);
    CreateVersionStatement = conn.prepareStatement(CREATE_VERSION);
    DeleteFromCodeStatement = conn.prepareStatement(DELETE_FROM_CODE);
    GetProjectNamesStatement = conn.prepareStatement(GET_PROJECT_NAMES);
    GetProjectVersions = conn.prepareStatement(GET_PROJECT_VERSIONS);
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
      CheckLoginStatement.setString(1, username);
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
      e.printStackTrace();
      return "Login failed\n";
    }
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
      commitTransaction();
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

  public String delete(int projectID) {
    if (this.username == null) {
      return "Please log in\n";
    }
    Scanner sc = new Scanner(System.in);
    System.out.println("Enter password:");
    String password = sc.nextLine();
    try {
      ResultSet passwordSet = CheckLoginStatement.executeQuery();
      passwordSet.next();
      String pass = passwordSet.getString("password");
      password = applyHash(password);
      if (!pass.equals(password)) {
        return "incorrect password\n";
      }
      CheckOwnerStatement.clearParameters();
      CheckOwnerStatement.setInt(1, projectID);
      ResultSet owner = CheckOwnerStatement.executeQuery();
      owner.next();
      if (!this.username.equals(owner.getString("creator"))) {
        return "Cannot Delete this project. Not the owner!\n";
      }
      DeleteProjectStatement.clearParameters();
      DeleteProjectStatement.setInt(1, projectID);
      DeleteProjectStatement.executeUpdate();

      File file = new File("projectDetails.det");
      file.delete();
      return "Deleted project " + projectID + "\n";
    } catch (Exception e) {
      return "Failed to delete project\n";
    }
  }

  public String add() {
    File folder = new File("").getAbsoluteFile();
    List<File> listOfFiles = new LinkedList<File>(Arrays.asList(folder.listFiles()));
    Iterator<File> F = listOfFiles.iterator();
    try {
      while (F.hasNext()) {
        File file = F.next();
        System.out.println(file.getName());
        if (file.isFile()
            && !(file.getName().endsWith(".class")
                || file.getName().endsWith(".BufferedReader")
                || file.getName().endsWith(".jit"))
            && file.getName().contains(".")) {
          FileInputStream fstream = new FileInputStream(file.getName());
          String outputFileName = file.getName() + ".jit";
          PrintStream output = new PrintStream(new File(outputFileName));
          BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
          String line;
          while ((line = br.readLine()) != null) {
            output.println(line);
          }
        }
      }
    } catch (Exception e) {
      System.out.println(e);
    }
    return "Files added\n";
  }

  public String commit(String message) {
    try {
      PrintStream output = new PrintStream(new File("message.commit"));
      output.println(message);
    } catch (FileNotFoundException e) {

    }
    return "commit message recorded\n";
  }

  public String view() {
    if (this.username == null) {
      return "Please log in\n";
    }
    String projectsString = "ID\tName\n";
    try {
      GetProjectStatement.clearParameters();
      GetProjectStatement.setString(1, this.username);
      ResultSet projects = GetProjectStatement.executeQuery();
      while (projects.next()) {
        projectsString += projects.getString("ID") + "\t" + projects.getString("name") + "\n";
      }
      projects.close();
    } catch (SQLException e) {
      return "Unable to locate projects";
    }
    return projectsString;
  }

  public String push() {
    if (this.username == null) {
      return "Please log in\n";
    }
    int lastID = 0;
    int projectID = 0;
    Scanner file = null;
    String projectName = "";
    String projectCreator = "";
    int projectVersion = 0;
    try {
      file = new Scanner(new File("projectDetails.det"));
    } catch (FileNotFoundException e) { // If the project doesn't exist
      Scanner sc = new Scanner(System.in);
      System.out.println("Enter project name:");
      String name = sc.nextLine();
      try {
        GetProjectNamesStatement.clearParameters();
        GetProjectNamesStatement.setString(1, username);
        ResultSet projectNames = GetProjectNamesStatement.executeQuery();
        while (projectNames.next()) {
          if (projectNames.getString("name").equals(name)) {
            return "Can't have 2 projects with same name";
          }
        }
        ResultSet last = GetLastProjectStatement.executeQuery();
        last.next();
        lastID = last.getInt("ID") + 1;
      } catch (SQLException f) {
        System.out.println(f);
      }
      try {
        PrintStream output = new PrintStream(new File("projectDetails.det"));
        output.println(lastID);
        output.println(1);
        output.println(this.username);
        output.println(name);
        CreateProjectStatement.clearParameters();
        CreateProjectStatement.setInt(1, lastID);
        CreateProjectStatement.setString(2, this.username);
        CreateProjectStatement.setString(3, name);
        CreateProjectStatement.executeUpdate();
        // Creating project
        File folder = new File("").getAbsoluteFile();
        List<File> listOfFiles = new LinkedList<File>(Arrays.asList(folder.listFiles()));
        Iterator<File> F = listOfFiles.iterator();
        while (F.hasNext()) {
          File input = F.next();
          if (input.isDirectory() || !input.getName().endsWith(".jit")) {
            F.remove();
          }
        }
        F = listOfFiles.iterator();
        // taking file input
        try {
          int fileCount = 0;
          while (F.hasNext()) {
            fileCount = 1;
            File input = F.next();
            System.out.println(input.getName());
            FileInputStream fstream = new FileInputStream(input.getName());
            BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
            String line;
            String code = "";
            String m;
            String message = "";
            FileInputStream messageStream = new FileInputStream("message.commit");
            BufferedReader messageReader = new BufferedReader(new InputStreamReader(messageStream));
            while ((line = br.readLine()) != null) {
              code += line + "\n";
            }
            while ((m = messageReader.readLine()) != null) {
              message += m + "\n";
            }
            CreateCodeStatement.clearParameters();
            CreateCodeStatement.setInt(1, lastID);
            CreateCodeStatement.setString(
                2, input.getName().substring(0, input.getName().lastIndexOf(".")));
            CreateCodeStatement.setString(3, message);
            CreateCodeStatement.setString(4, code);
            CreateCodeStatement.setInt(5, 1);
            CreateVersionStatement.clearParameters();
            CreateVersionStatement.setInt(1, lastID);
            CreateVersionStatement.setString(
                2, input.getName().substring(0, input.getName().lastIndexOf(".")));
            CreateVersionStatement.setString(3, message);
            CreateVersionStatement.setString(4, code);
            CreateVersionStatement.setInt(5, 1);
            CreateCodeStatement.executeUpdate();
            CreateVersionStatement.executeUpdate();
          }
          folder = new File("").getAbsoluteFile();
          listOfFiles = new LinkedList<File>(Arrays.asList(folder.listFiles()));
          Iterator<File> Files = listOfFiles.iterator();
          while (Files.hasNext()) {
            File Allfiles = Files.next();
            if (Allfiles.getName().endsWith(".jit")
                || Allfiles.getName().equals("message.commit")) {
              Allfiles.delete();
            }
          }
          if (fileCount == 0) {
            return "Please add and commit before pushing\n";
          }
        } catch (Exception i) {
          return "Please add and commit before pushing\n";
        }
      } catch (FileNotFoundException g) {
        return "Please add and commit before pushing\n";
        // file will always be found
      } catch (SQLException h) {
        System.out.println(h);
      }
      // insert into table
      // unlock here
      push();
    }
    // if project exists
    try {
      file = new Scanner(new File("projectDetails.det"));
      projectID = Integer.parseInt(file.next());
      projectVersion = Integer.parseInt(file.next()) + 1;
      projectCreator = file.next();
      projectName = file.nextLine();
      System.out.println(projectName);
      if (!projectCreator.equals(this.username)) {
        return "you can't push to this project as you aren't the owner";
      }
      PrintStream output = new PrintStream(new File("projectDetails.det"));
      output.println(projectID);
      output.println(projectVersion);
      output.println(projectCreator);
      output.println(projectName);
      File folder = new File("").getAbsoluteFile();
      List<File> listOfFiles = new LinkedList<File>(Arrays.asList(folder.listFiles()));
      Iterator<File> F = listOfFiles.iterator();
      while (F.hasNext()) {
        File input = F.next();
        if (input.isDirectory() || !input.getName().endsWith(".jit")) {
          F.remove();
        }
      }
      F = listOfFiles.iterator();
      // taking file input
      try {
        int fileCount = 0;
        while (F.hasNext()) {
          fileCount = 1;
          File input = F.next();
          System.out.println(input.getName());
          FileInputStream fstream = new FileInputStream(input.getName());
          BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
          String line;
          String code = "";
          String m;
          String message = "";
          FileInputStream messageStream = new FileInputStream("message.commit");
          BufferedReader messageReader = new BufferedReader(new InputStreamReader(messageStream));
          while ((line = br.readLine()) != null) {
            code += line + "\n";
          }
          while ((m = messageReader.readLine()) != null) {
            message += m + "\n";
          }
          DeleteFromCodeStatement.clearParameters();
          DeleteFromCodeStatement.setInt(1, projectID);
          DeleteFromCodeStatement.executeUpdate();
          CreateCodeStatement.clearParameters();
          CreateCodeStatement.setInt(1, projectID);
          CreateCodeStatement.setString(
              2, input.getName().substring(0, input.getName().lastIndexOf(".")));
          CreateCodeStatement.setString(3, message);
          CreateCodeStatement.setString(4, code);
          CreateCodeStatement.setInt(5, projectVersion);
          CreateVersionStatement.clearParameters();
          CreateVersionStatement.setInt(1, projectID);
          CreateVersionStatement.setString(
              2, input.getName().substring(0, input.getName().lastIndexOf(".")));
          CreateVersionStatement.setString(3, message);
          CreateVersionStatement.setString(4, code);
          CreateVersionStatement.setInt(5, projectVersion);
          CreateCodeStatement.executeUpdate();
          CreateVersionStatement.executeUpdate();
          // file.delete
        }
        folder = new File("").getAbsoluteFile();
        listOfFiles = new LinkedList<File>(Arrays.asList(folder.listFiles()));
        Iterator<File> Files = listOfFiles.iterator();
        while (Files.hasNext()) {
          File Allfiles = Files.next();
          if (Allfiles.getName().endsWith(".jit") || Allfiles.getName().equals("message.commit")) {
            Allfiles.delete();
          }
        }
        if (fileCount == 0) {
          return "Please add and commit before pushing\n";
        }
      } catch (Exception i) {
        return "Please add and commit before pushing\n";
      }
    } catch (FileNotFoundException g) {
      return "Please add and commit before pushing\n";
      // file will always be found
    }
    return "Project version " + projectVersion + " has been pushed\n";
  }

  // TODO: finish
  public String versions(int projectID) {
    if (this.username == null) {
      return "Please log in\n";
    }
    try {
      CheckOwnerStatement.clearParameters();
      CheckOwnerStatement.setInt(1, projectID);
      ResultSet owner = CheckOwnerStatement.executeQuery();
      owner.next();
      if (!this.username.equals(owner.getString("creator"))) {
        return "Cannot view version as you are not the owner\n";
      }
      GetProjectVersions.clearParameters();
      GetProjectVersions.setInt(1, projectID);
      ResultSet versions = GetProjectVersions.executeQuery();
      // print out project versions
      System.out.println("Project-ID: " + projectID);
      System.out.println("Version\tMessage");
    } catch (SQLException e) {
      System.out.println(e);
    }
    return "";
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

  // To create a hash of the password
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
