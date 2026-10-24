package com.collabsphere.dao;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.collabsphere.model.Model.Faculty;
import com.collabsphere.model.Model.Message;
import com.collabsphere.model.Model.Person;
import com.collabsphere.model.Model.Student;
import com.collabsphere.model.Model.Task;
import com.collabsphere.model.Model.Workspace;
import com.collabsphere.util.AppException;
import com.collabsphere.util.Util.Constants;
import com.collabsphere.util.Util;

/**
 * WHAT THIS FILE DOES
 *   ALL the JDBC / MySQL code of the project, in four parts:
 *     PART 1  connection settings, open / close connections   (JDBC steps 1, 2, 6)
 *     PART 2  create the database and the 7 tables, JDBC info
 *     PART 3  BaseDAO<T> - shared SELECT / INSERT code          (JDBC steps 3, 4, 5)
 *     PART 4  one DAO class per area: StudentDAO, WorkspaceDAO, TaskDAO, MessageDAO
 *   ("DAO" = Data Access Object = the class that talks to the tables.)
 *   The screens import the DAOs like:  import com.collabsphere.dao.Database.TaskDAO;
 */
public final class Database {

    private Database() { }

    // #################################################################
    // PART 1 - CONNECTIONS
    // #################################################################
    /**
     * PART 1: Opens and closes connections to MySQL. Every DAO uses it.
     *   The login details are read from the file db.properties.
     *
     * SYLLABUS (Unit V - JDBC)
     *   The 6 BASIC STEPS of a JDBC program:
     *     1. Load the driver           Class.forName("com.mysql.cj.jdbc.Driver")
     *     2. Open a connection         DriverManager.getConnection(url, user, password)
     *     3. Create a statement        con.prepareStatement(sql)
     *     4. Execute it                executeQuery()  or  executeUpdate()
     *     5. Read the ResultSet        while (rs.next()) { ... }
     *     6. Close everything          rs.close(), ps.close(), con.close()
     *   Steps 1, 2 and 6 are in this file; steps 3-5 are in BaseDAO.
     *
     *   MySQL Connector/J is a TYPE 4 driver ("thin" driver, 100% Java): it
     *   speaks MySQL's network protocol directly, no extra software needed.
     *
     * ALSO: static block (Unit I), java.io FileInputStream + Properties (Unit IV),
     *       METHOD OVERLOADING: three close(...) methods.
     */


    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    // default settings, replaced by values from db.properties
    private static String host = "localhost";
    private static String port = "3306";
    private static String dbName = "collabsphere";
    private static String user = "root";
    private static String password = "root";

    // STATIC BLOCK: runs once, the first time this class is used
    static {
        readSettings();
    }


    /** Reads db.properties (key=value lines) from the project folder. */
    private static void readSettings() {
        File file = new File("db.properties");
        if (!file.exists()) {
            System.out.println("db.properties not found - using default settings");
            return;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            Properties p = new Properties();
            p.load(in);
            host = p.getProperty("db.host", host).trim();
            port = p.getProperty("db.port", port).trim();
            dbName = p.getProperty("db.name", dbName).trim();
            user = p.getProperty("db.user", user).trim();
            password = p.getProperty("db.password", password);
        } catch (IOException e) {
            System.out.println("Could not read db.properties: " + e.getMessage());
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    // ---------------- STEP 1: load the driver ----------------
    public static void loadDriver() throws AppException {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new AppException("MySQL driver not found. Add mysql-connector-j-x.x.x.jar to the "
                    + "project's Build Path (CLASSPATH). See README.txt step 3.");
        }
    }

    // ---------------- STEP 2: open a connection ----------------
    private static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    /** Connection to the collabsphere database. */
    public static Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + dbName + OPTIONS;
        return DriverManager.getConnection(url, user, password);
    }

    /** Connection to the MySQL server only (no database yet) - needed for CREATE DATABASE. */
    public static Connection getServerConnection() throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + OPTIONS;
        return DriverManager.getConnection(url, user, password);
    }

    public static String getDbName()   { return dbName; }
    public static String getAddress()  { return host + ":" + port; }

    // ---------------- STEP 6: close (METHOD OVERLOADING) ----------------
    public static void close(ResultSet rs) {
        try { if (rs != null) rs.close(); } catch (SQLException ignored) { }
    }

    public static void close(Statement st) {
        try { if (st != null) st.close(); } catch (SQLException ignored) { }
    }

    public static void close(Connection con) {
        try { if (con != null) con.close(); } catch (SQLException ignored) { }
    }

    /** Undo an unfinished transaction (used when something fails half-way). */
    public static void rollback(Connection con) {
        try { if (con != null) con.rollback(); } catch (SQLException ignored) { }
    }

    // #################################################################
    // PART 2 - CREATE DATABASE AND TABLES
    // #################################################################
    /**
     * PART 2: Runs once at start-up: creates the database and the 7 tables from the
     *   SRS (Student, Faculty, Workspace, WorkspaceMember, Task, Message,
     *   PeerEvaluation) and adds two demo faculty accounts.
     *   "IF NOT EXISTS" makes it safe to run every time.
     *
     * SYLLABUS (Unit V - JDBC)
     *   - Creating a new DATABASE and TABLES with JDBC (Statement.executeUpdate)
     *   - Statement batch (addBatch / executeBatch)
     *   - DatabaseMetaData (information about the database and driver)
     *   - Types of JDBC drivers and JDBC architecture (jdbcInfo())
     */



    private static final String[] CREATE_TABLES = {
        "CREATE TABLE IF NOT EXISTS Student ("
        + " student_id INT AUTO_INCREMENT PRIMARY KEY,"
        + " name VARCHAR(100) NOT NULL,"
        + " email VARCHAR(150) NOT NULL UNIQUE,"
        + " password_hash CHAR(64) NOT NULL,"
        + " skills VARCHAR(500) DEFAULT '',"
        + " bio VARCHAR(1000) DEFAULT '',"
        + " global_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00)",

        "CREATE TABLE IF NOT EXISTS Faculty ("
        + " faculty_id INT AUTO_INCREMENT PRIMARY KEY,"
        + " name VARCHAR(100) NOT NULL,"
        + " email VARCHAR(150) NOT NULL UNIQUE,"
        + " password_hash CHAR(64) NOT NULL,"
        + " department VARCHAR(100) DEFAULT 'CSE')",

        "CREATE TABLE IF NOT EXISTS Workspace ("
        + " workspace_id INT AUTO_INCREMENT PRIMARY KEY,"
        + " name VARCHAR(120) NOT NULL,"
        + " type VARCHAR(30) NOT NULL,"
        + " description VARCHAR(1000) DEFAULT '',"
        + " max_members INT NOT NULL DEFAULT 4,"
        + " status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',"
        + " archived TINYINT(1) NOT NULL DEFAULT 0,"
        + " created_by INT NOT NULL,"
        + " faculty_id INT NULL,"
        + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
        + " completed_at TIMESTAMP NULL,"
        + " FOREIGN KEY (created_by) REFERENCES Student(student_id),"
        + " FOREIGN KEY (faculty_id) REFERENCES Faculty(faculty_id))",

        "CREATE TABLE IF NOT EXISTS WorkspaceMember ("
        + " workspace_id INT NOT NULL,"
        + " student_id INT NOT NULL,"
        + " role VARCHAR(10) NOT NULL DEFAULT 'MEMBER',"
        + " PRIMARY KEY (workspace_id, student_id),"
        + " FOREIGN KEY (workspace_id) REFERENCES Workspace(workspace_id),"
        + " FOREIGN KEY (student_id) REFERENCES Student(student_id))",

        "CREATE TABLE IF NOT EXISTS Task ("
        + " task_id INT AUTO_INCREMENT PRIMARY KEY,"
        + " workspace_id INT NOT NULL,"
        + " title VARCHAR(150) NOT NULL,"
        + " description VARCHAR(1000) DEFAULT '',"
        + " status VARCHAR(10) NOT NULL DEFAULT 'OPEN',"
        + " claimed_by INT NULL,"
        + " FOREIGN KEY (workspace_id) REFERENCES Workspace(workspace_id),"
        + " FOREIGN KEY (claimed_by) REFERENCES Student(student_id))",

        "CREATE TABLE IF NOT EXISTS Message ("
        + " message_id INT AUTO_INCREMENT PRIMARY KEY,"
        + " workspace_id INT NOT NULL,"
        + " student_id INT NOT NULL,"
        + " content VARCHAR(500) NOT NULL,"
        + " sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
        + " INDEX idx_chat (workspace_id, message_id),"
        + " FOREIGN KEY (workspace_id) REFERENCES Workspace(workspace_id),"
        + " FOREIGN KEY (student_id) REFERENCES Student(student_id))",

        "CREATE TABLE IF NOT EXISTS PeerEvaluation ("
        + " eval_id INT AUTO_INCREMENT PRIMARY KEY,"
        + " workspace_id INT NOT NULL,"
        + " reviewer_id INT NOT NULL,"
        + " reviewee_id INT NOT NULL,"
        + " rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),"
        + " feedback VARCHAR(1000) DEFAULT '',"
        + " UNIQUE (workspace_id, reviewer_id, reviewee_id),"
        + " FOREIGN KEY (workspace_id) REFERENCES Workspace(workspace_id),"
        + " FOREIGN KEY (reviewer_id) REFERENCES Student(student_id),"
        + " FOREIGN KEY (reviewee_id) REFERENCES Student(student_id))"
    };

    /** Called once from Main. */
    public static void initialize() throws AppException {
        Database.loadDriver();
        Connection con = null;
        Statement st = null;
        try {
            // 1) CREATE DATABASE - needs a connection to the server, not to a database
            con = Database.getServerConnection();
            st = con.createStatement();
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + Database.getDbName());
            Database.close(st);
            Database.close(con);

            // 2) CREATE TABLEs - sent together as one batch
            con = Database.getConnection();
            st = con.createStatement();
            for (String sql : CREATE_TABLES) {
                st.addBatch(sql);
            }
            st.executeBatch();
            System.out.println("Database '" + Database.getDbName() + "' and 7 tables are ready.");

            // 3) demo faculty accounts (faculty are pre-registered - SRS 2.6)
            addFacultyIfEmpty(con);
        } catch (SQLException e) {
            throw new AppException.DatabaseError("Cannot set up MySQL at " + Database.getAddress()
                    + ". Is MySQL running and is the password in db.properties correct?", e);
        } finally {
            Database.close(st);
            Database.close(con);
        }
    }

    private static void addFacultyIfEmpty(Connection con) throws SQLException {
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Faculty");
        rs.next();
        int count = rs.getInt(1);
        rs.close();
        st.close();
        if (count > 0) {
            return;
        }
        String[][] faculty = {                       // 2-D array: one row per faculty
            { "Dr. Ananya Rao",    "faculty@college.edu", "CSE" },
            { "Prof. Kiran Kumar", "kiran@college.edu",   "IT"  }
        };
        PreparedStatement ps = con.prepareStatement(
                "INSERT INTO Faculty(name, email, password_hash, department) VALUES (?, ?, ?, ?)");
        for (String[] f : faculty) {
            ps.setString(1, f[0]);
            ps.setString(2, f[1]);
            ps.setString(3, Util.hashPassword("faculty123", f[1]));
            ps.setString(4, f[2]);
            ps.executeUpdate();
        }
        ps.close();
        System.out.println("Added demo faculty logins (password: faculty123).");
    }

    /** Help > JDBC Information: theory + live details from DatabaseMetaData. */
    public static String jdbcInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("JDBC ARCHITECTURE\n")
          .append("  Java program (Swing screens + DAO classes)\n")
          .append("     |  JDBC API: DriverManager, Connection, Statement, PreparedStatement,\n")
          .append("     |            ResultSet, ResultSetMetaData, DatabaseMetaData, SQLException\n")
          .append("  DriverManager  ->  MySQL Connector/J driver  ->  TCP/IP  ->  MySQL server\n\n")
          .append("TYPES OF JDBC DRIVERS\n")
          .append("  Type 1  JDBC-ODBC bridge   - converts JDBC calls to ODBC calls\n")
          .append("  Type 2  Native-API         - uses the database's native C library\n")
          .append("  Type 3  Network protocol   - talks to a middleware server\n")
          .append("  Type 4  Thin / pure Java   - talks to the database directly  <== used here\n\n");
        Connection con = null;
        try {
            con = Database.getConnection();
            DatabaseMetaData md = con.getMetaData();
            sb.append("THIS CONNECTION\n")
              .append("  URL      : ").append(md.getURL()).append('\n')
              .append("  Database : ").append(md.getDatabaseProductName()).append(' ')
              .append(md.getDatabaseProductVersion()).append('\n')
              .append("  Driver   : ").append(md.getDriverName()).append(' ')
              .append(md.getDriverVersion()).append('\n');
            ResultSet tables = md.getTables(Database.getDbName(), null, "%", new String[] { "TABLE" });
            sb.append("  Tables   :");
            while (tables.next()) {
                sb.append(' ').append(tables.getString("TABLE_NAME"));
            }
            tables.close();
        } catch (SQLException e) {
            sb.append("Could not read database details: ").append(e.getMessage());
        } finally {
            Database.close(con);
        }
        return sb.toString();
    }

    // #################################################################
    // PART 3 and 4 - DAO CLASSES
    // #################################################################

    /**
     * WHAT THIS CLASS DOES
     *   The JDBC code that every DAO needs (run a SELECT, run an INSERT/UPDATE)
     *   is written ONCE here. StudentDAO, WorkspaceDAO, TaskDAO and MessageDAO
     *   extend this class. ("DAO" = Data Access Object = the class that talks to
     *   one table.)
     *
     * SYLLABUS
     *   Unit II - GENERIC CLASS: BaseDAO<T>. T is the type a row becomes
     *             (Student, Task, ...).
     *           - ABSTRACT class + SPECIFICATION form of inheritance: each
     *             subclass must SPECIFY how one row becomes an object (mapRow).
     *   Unit V  - JDBC steps 3-5: PreparedStatement, executeQuery/executeUpdate,
     *             ResultSet, ResultSetMetaData, getGeneratedKeys.
     *
     *   SECURITY (NFR-15): values are always passed with "?" placeholders, never
     *   joined into the SQL text, so SQL injection is impossible.
     */
    public static abstract class BaseDAO<T> {

        /** Each subclass says how to turn the current ResultSet row into an object. */
        protected abstract T mapRow(ResultSet rs) throws SQLException;

        /** Runs a SELECT and returns every row as an object of type T. */
        protected List<T> queryList(String sql, Object[] params) throws AppException {
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            List<T> list = new ArrayList<T>();
            try {
                con = Database.getConnection();         // step 2
                ps = con.prepareStatement(sql);             // step 3
                setParams(ps, params);
                rs = ps.executeQuery();                     // step 4
                while (rs.next()) {                         // step 5
                    list.add(mapRow(rs));
                }
                return list;
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Reading data failed", e);
            } finally {                                     // step 6 - always runs
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }

        /** Runs INSERT / UPDATE / DELETE. Returns how many rows were changed. */
        protected int update(String sql, Object[] params) throws AppException {
            Connection con = null;
            PreparedStatement ps = null;
            try {
                con = Database.getConnection();
                ps = con.prepareStatement(sql);
                setParams(ps, params);
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Saving data failed", e);
            } finally {
                Database.close(ps);
                Database.close(con);
            }
        }

        /** Runs an INSERT and returns the new AUTO_INCREMENT id. */
        protected int insert(String sql, Object[] params) throws AppException {
            Connection con = null;
            try {
                con = Database.getConnection();
                return insert(con, sql, params);
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Saving data failed", e);
            } finally {
                Database.close(con);
            }
        }

        /** Overloaded version that uses an open connection (needed inside a transaction). */
        protected static int insert(Connection con, String sql, Object[] params) throws SQLException {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            setParams(ps, params);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int newId = keys.next() ? keys.getInt(1) : 0;
            keys.close();
            ps.close();
            return newId;
        }

        /** Runs a SELECT that returns one number, e.g. SELECT COUNT(*) ... */
        protected int queryInt(String sql, Object[] params) throws AppException {
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                con = Database.getConnection();
                ps = con.prepareStatement(sql);
                setParams(ps, params);
                rs = ps.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Reading data failed", e);
            } finally {
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }

        /** Puts params[0] into the 1st "?", params[1] into the 2nd "?", ... */
        protected static void setParams(PreparedStatement ps, Object[] params) throws SQLException {
            if (params == null) {
                return;
            }
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);           // JDBC counts from 1
            }
        }

        /** ResultSetMetaData: does the current query result contain this column? */
        protected static boolean hasColumn(ResultSet rs, String column) throws SQLException {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (md.getColumnLabel(i).equalsIgnoreCase(column)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * WHAT THIS CLASS DOES
     *   Everything about people: register, login (student or faculty), profile,
     *   and peer reviews that change a student's Global Rating.
     *   Tables used: Student, Faculty, PeerEvaluation.
     *
     * SYLLABUS
     *   Unit II  - extends the generic class BaseDAO<Student>; POLYMORPHISM:
     *              login() returns a Person that is really a Student or a Faculty.
     *   Unit III - throw / throws with our own exceptions.
     *   Unit IV  - List, Set (HashSet).
     *   Unit V   - JDBC TRANSACTION (setAutoCommit / commit / rollback) in
     *              submitReview(); findFaculty() shows all 6 JDBC steps in one place.
     */
    public static class StudentDAO extends BaseDAO<Student> {

        // a student row + how many reviews the student has received
        private static final String SELECT_STUDENT =
            "SELECT s.*, (SELECT COUNT(*) FROM PeerEvaluation p WHERE p.reviewee_id = s.student_id)"
            + " AS review_count FROM Student s";

        @Override
        protected Student mapRow(ResultSet rs) throws SQLException {
            Student s = new Student(rs.getInt("student_id"), rs.getString("name"),
                                    rs.getString("email"), rs.getDouble("global_rating"));
            s.setPasswordHash(rs.getString("password_hash"));
            s.setSkillsFromText(rs.getString("skills"));
            s.setBio(rs.getString("bio"));
            s.setReviewCount(rs.getInt("review_count"));
            return s;
        }

        public Student findById(int id) throws AppException {
            return Util.firstOrNull(queryList(SELECT_STUDENT + " WHERE s.student_id = ?",
                                              new Object[] { id }));     // int auto-boxed to Integer
        }

        public Student findByEmail(String email) throws AppException {
            return Util.firstOrNull(queryList(SELECT_STUDENT + " WHERE s.email = ?",
                                              new Object[] { email }));
        }

        public int countStudents() throws AppException {
            return queryInt("SELECT COUNT(*) FROM Student", null);
        }

        // =================================================================
        // REGISTER and LOGIN  (FR-1, FR-2, FR-3)
        // =================================================================

        public Student register(String name, String email, String password, String confirm)
                throws AppException {
            String cleanName = Util.checkName(name);
            String cleanEmail = Util.checkEmail(email);
            Util.checkPassword(password);
            if (!password.equals(confirm)) {
                throw new AppException.InvalidInput("The two passwords do not match.");
            }
            if (findByEmail(cleanEmail) != null) {
                throw new AppException.DuplicateEmail(cleanEmail);
            }
            Student s = new Student(cleanName, cleanEmail);
            s.setPasswordHash(Util.hashPassword(password, cleanEmail));   // store only the hash
            int newId = insert("INSERT INTO Student(name, email, password_hash) VALUES (?, ?, ?)",
                               new Object[] { s.getName(), s.getEmail(), s.getPasswordHash() });
            s.setId(newId);
            return s;
        }

        /**
         * POLYMORPHISM: the return type is Person, the real object is either a
         * Student or a Faculty, depending on the role chosen on the login screen.
         */
        public Person login(String email, String password, String role) throws AppException {
            String cleanEmail = Util.checkEmail(email);
            Util.requireText(password, "Password");

            Person person;
            if (Constants.ROLE_FACULTY.equals(role)) {
                person = findFaculty(cleanEmail);
            } else {
                person = findByEmail(cleanEmail);
            }
            if (person == null || !person.getPasswordHash().equals(Util.hashPassword(password, cleanEmail))) {
                throw new AppException.LoginFailed();
            }
            return person;
        }

        /** All 6 JDBC steps written out in one method (for explanation). */
        public Faculty findFaculty(String email) throws AppException {
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                // (Step 1 - loading the driver - was done once at start-up)
                con = Database.getConnection();                                   // Step 2
                ps = con.prepareStatement("SELECT * FROM Faculty WHERE email = ?");    // Step 3
                ps.setString(1, email);
                rs = ps.executeQuery();                                               // Step 4
                if (rs.next()) {                                                      // Step 5
                    Faculty f = new Faculty(rs.getInt("faculty_id"), rs.getString("name"),
                                            rs.getString("email"), rs.getString("department"));
                    f.setPasswordHash(rs.getString("password_hash"));
                    return f;
                }
                return null;
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Faculty lookup failed", e);
            } finally {                                                               // Step 6
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }

        /** All faculty, for the "Supervisor" combo box. */
        public List<Faculty> findAllFaculty() throws AppException {
            List<Faculty> list = new ArrayList<Faculty>();
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                con = Database.getConnection();
                ps = con.prepareStatement("SELECT * FROM Faculty ORDER BY name");
                rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new Faculty(rs.getInt("faculty_id"), rs.getString("name"),
                                         rs.getString("email"), rs.getString("department")));
                }
                return list;
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Loading faculty failed", e);
            } finally {
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }

        // =================================================================
        // PROFILE  (FR-4)
        // =================================================================

        public void updateProfile(Student s) throws AppException {
            update("UPDATE Student SET name = ?, skills = ?, bio = ? WHERE student_id = ?",
                   new Object[] { s.getName(), s.getSkillsText(), s.getBio(), s.getId() });
        }

        // =================================================================
        // PEER REVIEW  (FR-21, FR-22, FR-23)
        // =================================================================

        /**
         * Saves one review AND recalculates the reviewee's Global Rating.
         * Both steps are one TRANSACTION: if the second fails, the first is undone.
         */
        public void submitReview(int workspaceId, int reviewerId, int revieweeId, String revieweeName,
                                 int stars, String feedback) throws AppException {
            if (stars < Constants.MIN_RATING || stars > Constants.MAX_RATING) {
                throw new AppException.InvalidInput("Please choose 1 to 5 stars.");
            }
            if (reviewedIds(workspaceId, reviewerId).contains(revieweeId)) {
                throw new AppException.DuplicateReview(revieweeName);
            }
            Connection con = null;
            PreparedStatement ps = null;
            try {
                con = Database.getConnection();
                con.setAutoCommit(false);                       // start the transaction

                ps = con.prepareStatement("INSERT INTO PeerEvaluation"
                        + "(workspace_id, reviewer_id, reviewee_id, rating, feedback) VALUES (?, ?, ?, ?, ?)");
                ps.setInt(1, workspaceId);
                ps.setInt(2, reviewerId);
                ps.setInt(3, revieweeId);
                ps.setInt(4, stars);
                ps.setString(5, feedback.trim());
                ps.executeUpdate();
                ps.close();

                // FR-23: new Global Rating = average of every star the student received
                ps = con.prepareStatement("UPDATE Student SET global_rating ="
                        + " (SELECT ROUND(AVG(rating), 2) FROM PeerEvaluation WHERE reviewee_id = ?)"
                        + " WHERE student_id = ?");
                ps.setInt(1, revieweeId);
                ps.setInt(2, revieweeId);
                ps.executeUpdate();

                con.commit();                                    // both changes saved together
            } catch (SQLException e) {
                Database.rollback(con);                      // undo everything
                throw new AppException.DatabaseError("Review could not be saved", e);
            } finally {
                Database.close(ps);
                Database.close(con);
            }
        }

        /** Ids of teammates this reviewer already reviewed in a workspace. */
        public Set<Integer> reviewedIds(int workspaceId, int reviewerId) throws AppException {
            Set<Integer> ids = new HashSet<Integer>();
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                con = Database.getConnection();
                ps = con.prepareStatement(
                    "SELECT reviewee_id FROM PeerEvaluation WHERE workspace_id = ? AND reviewer_id = ?");
                ps.setInt(1, workspaceId);
                ps.setInt(2, reviewerId);
                rs = ps.executeQuery();
                while (rs.next()) {
                    ids.add(rs.getInt(1));                   // auto-boxing int -> Integer
                }
                return ids;
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Loading reviews failed", e);
            } finally {
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }

        /** Feedback lines a student received, e.g. "4★  Great work - Library App". */
        public List<String> feedbackFor(int studentId) throws AppException {
            List<String> lines = new ArrayList<String>();
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                con = Database.getConnection();
                ps = con.prepareStatement("SELECT p.rating, p.feedback, w.name FROM PeerEvaluation p"
                        + " JOIN Workspace w ON w.workspace_id = p.workspace_id WHERE p.reviewee_id = ?");
                ps.setInt(1, studentId);
                rs = ps.executeQuery();
                while (rs.next()) {
                    String text = rs.getString("feedback");
                    if (text == null || text.isEmpty()) {
                        text = "(no comment)";
                    }
                    lines.add(rs.getInt("rating") + "★  " + text + "  - " + rs.getString("name"));
                }
                return lines;
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Loading feedback failed", e);
            } finally {
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }

        /**
         * For the faculty screen: counts[i] = number of items with i stars.
         * forGlobalRatings = true  -> students' Global Ratings (rounded)  (FR-28)
         * forGlobalRatings = false -> individual peer ratings given       (FR-29)
         */
        public int[] starCounts(boolean forGlobalRatings) throws AppException {
            String sql = forGlobalRatings
                ? "SELECT ROUND(global_rating) FROM Student s"
                  + " WHERE EXISTS (SELECT 1 FROM PeerEvaluation p WHERE p.reviewee_id = s.student_id)"
                : "SELECT rating FROM PeerEvaluation";
            int[] counts = new int[Constants.MAX_RATING + 1];    // index 0..5
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                con = Database.getConnection();
                ps = con.prepareStatement(sql);
                rs = ps.executeQuery();
                while (rs.next()) {
                    int stars = rs.getInt(1);
                    if (stars >= 0 && stars <= Constants.MAX_RATING) {
                        counts[stars]++;
                    }
                }
                return counts;
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Loading ratings failed", e);
            } finally {
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }
    }

    /**
     * WHAT THIS CLASS DOES
     *   Create workspaces, invite members, complete and archive workspaces.
     *   Tables used: Workspace, WorkspaceMember.
     *
     * SYLLABUS
     *   Unit V  - JDBC TRANSACTIONS: create() and invite() change two things that
     *             must succeed or fail together (FR-7, FR-9).
     *           - ResultSetMetaData (hasColumn) in mapRow.
     *   Unit III - throwing our own exceptions (WorkspaceFull ...).
     */
    public static class WorkspaceDAO extends BaseDAO<Workspace> {

        @Override
        protected Workspace mapRow(ResultSet rs) throws SQLException {
            Workspace w = Workspace.create(rs.getInt("workspace_id"), rs.getString("name"),
                    rs.getString("type"), rs.getString("description"), rs.getInt("max_members"),
                    rs.getString("status"), rs.getInt("faculty_id"), rs.getBoolean("archived"));
            // the faculty query adds three extra count columns
            if (hasColumn(rs, "member_count")) {
                w.setStats(new Workspace.Stats(rs.getInt("member_count"),
                                               rs.getInt("pending_tasks"), rs.getInt("done_tasks")));
            }
            return w;
        }

        /**
         * FR-6 / FR-7: insert the workspace AND make the creator its leader in ONE
         * transaction, so a half-created workspace can never be left behind.
         */
        public Workspace create(Student leader, String name, String type, String description,
                                int maxMembers, int facultyId) throws AppException {
            String cleanName = Util.requireText(name, "Workspace name");
            Connection con = null;
            PreparedStatement ps = null;
            try {
                con = Database.getConnection();
                con.setAutoCommit(false);                                 // BEGIN

                Integer supervisor = (facultyId > 0) ? Integer.valueOf(facultyId) : null;  // null -> SQL NULL
                int newId = insert(con, "INSERT INTO Workspace(name, type, description, max_members,"
                        + " created_by, faculty_id) VALUES (?, ?, ?, ?, ?, ?)",
                        new Object[] { cleanName, type, description, maxMembers, leader.getId(), supervisor });

                ps = con.prepareStatement(
                        "INSERT INTO WorkspaceMember(workspace_id, student_id, role) VALUES (?, ?, ?)");
                ps.setInt(1, newId);
                ps.setInt(2, leader.getId());
                ps.setString(3, Constants.ROLE_LEADER);
                ps.executeUpdate();

                con.commit();                                             // COMMIT
                Workspace w = new Workspace(newId, cleanName, type, description, maxMembers,
                                            Constants.STATUS_ACTIVE, facultyId);
                w.addMember(leader.getId(), leader.getName(), Constants.ROLE_LEADER);
                return w;
            } catch (SQLException e) {
                Database.rollback(con);                               // ROLLBACK
                throw new AppException.DatabaseError("Workspace could not be created", e);
            } finally {
                Database.close(ps);
                Database.close(con);
            }
        }

        /**
         * FR-8 / FR-9: a member invites another student by email.
         * "SELECT ... FOR UPDATE" locks the workspace row while we count members,
         * so two people cannot both take the last free place.
         */
        public Student invite(Workspace w, Student inviter, String email) throws AppException {
            w.checkEditable();                                // archived -> exception
            if (w.isCompleted()) {
                throw new AppException("A completed workspace cannot take new members.");
            }
            if (w.findMember(inviter.getId()) == null) {
                throw new AppException("Only members of this workspace can invite.");
            }
            Student invitee = new StudentDAO().findByEmail(Util.checkEmail(email));
            if (invitee == null) {
                throw new AppException("No student is registered with " + email.trim() + ".");
            }
            if (w.findMember(invitee.getId()) != null) {
                throw new AppException(invitee.getName() + " is already a member.");
            }

            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                con = Database.getConnection();
                con.setAutoCommit(false);

                ps = con.prepareStatement("SELECT max_members FROM Workspace WHERE workspace_id = ? FOR UPDATE");
                ps.setInt(1, w.getId());
                rs = ps.executeQuery();
                rs.next();
                int max = rs.getInt(1);
                rs.close();
                ps.close();

                ps = con.prepareStatement("SELECT COUNT(*) FROM WorkspaceMember WHERE workspace_id = ?");
                ps.setInt(1, w.getId());
                rs = ps.executeQuery();
                rs.next();
                int current = rs.getInt(1);
                rs.close();
                ps.close();

                if (current >= max) {
                    con.rollback();
                    throw new AppException.WorkspaceFull(w.getName(), max);   // FR-9
                }

                ps = con.prepareStatement(
                        "INSERT INTO WorkspaceMember(workspace_id, student_id, role) VALUES (?, ?, ?)");
                ps.setInt(1, w.getId());
                ps.setInt(2, invitee.getId());
                ps.setString(3, Constants.ROLE_MEMBER);
                ps.executeUpdate();
                con.commit();
                return invitee;
            } catch (SQLException e) {
                Database.rollback(con);
                throw new AppException.DatabaseError("Could not add the member", e);
            } finally {
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }

        /** All workspaces a student belongs to, each with its members (NFR-14). */
        public List<Workspace> findForStudent(int studentId) throws AppException {
            List<Workspace> list = queryList("SELECT w.* FROM Workspace w JOIN WorkspaceMember m"
                    + " ON m.workspace_id = w.workspace_id WHERE m.student_id = ?"
                    + " ORDER BY w.archived, w.workspace_id DESC", new Object[] { studentId });
            for (Workspace w : list) {
                loadMembers(w);
            }
            return list;
        }

        /** Fills w.getMembers() - leaders first. */
        private void loadMembers(Workspace w) throws AppException {
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                con = Database.getConnection();
                ps = con.prepareStatement("SELECT m.student_id, m.role, s.name FROM WorkspaceMember m"
                        + " JOIN Student s ON s.student_id = m.student_id WHERE m.workspace_id = ?"
                        + " ORDER BY m.role = 'LEADER' DESC, s.name");
                ps.setInt(1, w.getId());
                rs = ps.executeQuery();
                while (rs.next()) {
                    w.addMember(rs.getInt("student_id"), rs.getString("name"), rs.getString("role"));
                }
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Loading members failed", e);
            } finally {
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }

        /** Only the leader may complete a workspace. Uses polymorphism: canMarkComplete(). */
        public void markCompleted(Workspace w, Student s) throws AppException {
            w.checkEditable();
            Workspace.Member me = w.findMember(s.getId());
            if (me == null || !me.canMarkComplete()) {
                throw new AppException("Only the team leader can mark the workspace as completed.");
            }
            update("UPDATE Workspace SET status = ?, completed_at = NOW() WHERE workspace_id = ?",
                   new Object[] { Constants.STATUS_COMPLETED, w.getId() });
        }

        /** FR-24: archive workspaces completed more than 'days' days ago. Returns how many. */
        public int archiveOldWorkspaces(int days) throws AppException {
            return update("UPDATE Workspace SET archived = 1 WHERE status = ? AND archived = 0"
                    + " AND completed_at <= NOW() - INTERVAL ? DAY",
                    new Object[] { Constants.STATUS_COMPLETED, days });
        }

        /** Faculty screen: every workspace + member / pending / done counts (FR-26, FR-27). */
        public List<Workspace> findAllWithStats() throws AppException {
            return queryList("SELECT w.*,"
                    + " (SELECT COUNT(*) FROM WorkspaceMember m WHERE m.workspace_id = w.workspace_id) AS member_count,"
                    + " (SELECT COUNT(*) FROM Task t WHERE t.workspace_id = w.workspace_id AND t.status <> 'DONE') AS pending_tasks,"
                    + " (SELECT COUNT(*) FROM Task t WHERE t.workspace_id = w.workspace_id AND t.status = 'DONE') AS done_tasks"
                    + " FROM Workspace w ORDER BY w.workspace_id", null);
        }

        public int countActive() throws AppException {
            return queryInt("SELECT COUNT(*) FROM Workspace WHERE status = 'ACTIVE' AND archived = 0", null);
        }
    }

    /**
     * WHAT THIS CLASS DOES
     *   The shared task list: add, claim, complete, import, leaderboard
     *   (FR-15 .. FR-20). Table used: Task.
     *
     * SYLLABUS
     *   Unit III - SYNCHRONIZED method claim(): only one thread at a time.
     *   Unit II  - GENERIC class Util.Pair<Student, Integer> for leaderboard rows.
     */
    public static class TaskDAO extends BaseDAO<Task> {

        // every task query also fetches the name of the student who claimed it
        private static final String SELECT_TASK = "SELECT t.*, s.name AS assignee_name FROM Task t"
                + " LEFT JOIN Student s ON s.student_id = t.claimed_by";

        @Override
        protected Task mapRow(ResultSet rs) throws SQLException {
            return new Task(rs.getInt("task_id"), rs.getInt("workspace_id"), rs.getString("title"),
                            rs.getString("description"), rs.getString("status"),
                            rs.getInt("claimed_by"),             // SQL NULL is returned as 0
                            rs.getString("assignee_name"));
        }

        public List<Task> findByWorkspace(int workspaceId) throws AppException {
            return queryList(SELECT_TASK + " WHERE t.workspace_id = ? ORDER BY t.task_id",
                             new Object[] { workspaceId });
        }

        public Task findById(int taskId) throws AppException {
            return Util.firstOrNull(queryList(SELECT_TASK + " WHERE t.task_id = ?", new Object[] { taskId }));
        }

        /** FR-15: add a task (members only, not in an archived workspace). */
        public Task add(Workspace w, Student s, String title, String description) throws AppException {
            checkMember(w, s);
            Task t = new Task(w.getId(), Util.requireText(title, "Task title"), description.trim());
            int id = insert("INSERT INTO Task(workspace_id, title, description) VALUES (?, ?, ?)",
                            new Object[] { t.getWorkspaceId(), t.getTitle(), t.getDescription() });
            return findById(id);
        }

        /**
         * FR-16 / FR-17 / FR-18: claim a task.
         *
         * Two protections make sure only ONE student can get a task:
         *  1. "synchronized" - inside this program, threads enter one at a time.
         *  2. The SQL only updates the row if claimed_by IS NULL. If two computers
         *     try at the same moment, MySQL lets only one UPDATE change the row;
         *     the other one changes 0 rows.
         */
        public synchronized Task claim(Task task, Student s) throws AppException {
            int rowsChanged = update("UPDATE Task SET claimed_by = ?, status = ?"
                    + " WHERE task_id = ? AND claimed_by IS NULL",
                    new Object[] { s.getId(), Constants.TASK_CLAIMED, task.getId() });
            Task fresh = findById(task.getId());
            if (rowsChanged == 0) {
                throw new AppException.TaskAlreadyClaimed(task.getTitle(), fresh.getAssigneeName());
            }
            return fresh;
        }

        /** FR-19: only the student who claimed the task can complete it. */
        public void complete(Task task, Student s) throws AppException {
            if (task.getAssigneeId() != s.getId()) {
                throw new AppException("Only the student who claimed this task can mark it done.");
            }
            update("UPDATE Task SET status = ? WHERE task_id = ? AND claimed_by = ?",
                   new Object[] { Constants.TASK_DONE, task.getId(), s.getId() });
        }

        /** Adds many tasks read from a CSV file. Returns how many were added. */
        public int importTasks(Workspace w, Student s, List<String[]> rows) throws AppException {
            int added = 0;
            for (String[] row : rows) {
                String title = row[0].trim();
                String description = (row.length > 1) ? row[1] : "";
                if (!title.isEmpty()) {
                    add(w, s, title, description);
                    added++;
                }
            }
            return added;
        }

        /** FR-20: every student with the number of tasks they completed, best first. */
        public List<Util.Pair<Student, Integer>> leaderboard() throws AppException {
            List<Util.Pair<Student, Integer>> rows = new ArrayList<Util.Pair<Student, Integer>>();
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                con = Database.getConnection();
                ps = con.prepareStatement("SELECT s.student_id, s.name, s.email, s.global_rating,"
                        + " COUNT(t.task_id) AS done FROM Student s"
                        + " LEFT JOIN Task t ON t.claimed_by = s.student_id AND t.status = 'DONE'"
                        + " GROUP BY s.student_id, s.name, s.email, s.global_rating"
                        + " ORDER BY done DESC, s.global_rating DESC, s.name");
                rs = ps.executeQuery();
                while (rs.next()) {
                    Student st = new Student(rs.getInt("student_id"), rs.getString("name"),
                                             rs.getString("email"), rs.getDouble("global_rating"));
                    rows.add(new Util.Pair<Student, Integer>(st, rs.getInt("done")));
                }
                return rows;
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Loading leaderboard failed", e);
            } finally {
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }

        /** NFR-14: only members of an editable workspace may change its tasks. */
        private void checkMember(Workspace w, Student s) throws AppException {
            w.checkEditable();
            if (w.findMember(s.getId()) == null) {
                throw new AppException("You are not a member of this workspace.");
            }
        }
    }

    /**
     * WHAT THIS CLASS DOES
     *   Saves and loads chat messages (FR-12 .. FR-14) and counts messages per
     *   day for the faculty screen (FR-30). Table used: Message.
     *
     *   How "live" chat works without a server: every 3 seconds the chat thread
     *   asks "give me messages with id greater than the last one I have"
     *   (findAfter). The index on (workspace_id, message_id) makes this fast.
     *
     * SYLLABUS (Unit IV) - Map (LinkedHashMap keeps the dates in order).
     */
    public static class MessageDAO extends BaseDAO<Message> {

        @Override
        protected Message mapRow(ResultSet rs) throws SQLException {
            return new Message(rs.getInt("message_id"), rs.getInt("workspace_id"),
                    rs.getInt("student_id"), rs.getString("sender_name"),
                    rs.getString("content"), rs.getTimestamp("sent_at"));
        }

        public void send(Message m) throws AppException {
            insert("INSERT INTO Message(workspace_id, student_id, content) VALUES (?, ?, ?)",
                   new Object[] { m.getWorkspaceId(), m.getStudentId(), m.getText() });
        }

        /** Messages of one workspace that are newer than lastId. */
        public List<Message> findAfter(int workspaceId, int lastId) throws AppException {
            return queryList("SELECT m.*, s.name AS sender_name FROM Message m"
                    + " JOIN Student s ON s.student_id = m.student_id"
                    + " WHERE m.workspace_id = ? AND m.message_id > ? ORDER BY m.message_id",
                    new Object[] { workspaceId, lastId });
        }

        /** date -> number of messages. workspaceId 0 means "all workspaces". */
        public Map<String, Integer> messagesPerDay(int workspaceId) throws AppException {
            Map<String, Integer> perDay = new LinkedHashMap<String, Integer>();
            String sql = "SELECT DATE(sent_at) AS msg_date, COUNT(*) AS total FROM Message"
                    + (workspaceId > 0 ? " WHERE workspace_id = ?" : "")
                    + " GROUP BY DATE(sent_at) ORDER BY msg_date";
            Connection con = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                con = Database.getConnection();
                ps = con.prepareStatement(sql);
                if (workspaceId > 0) {
                    ps.setInt(1, workspaceId);
                }
                rs = ps.executeQuery();
                while (rs.next()) {
                    perDay.put(rs.getString("msg_date"), rs.getInt("total"));
                }
                return perDay;
            } catch (SQLException e) {
                throw new AppException.DatabaseError("Loading chat activity failed", e);
            } finally {
                Database.close(rs);
                Database.close(ps);
                Database.close(con);
            }
        }
    }
}
