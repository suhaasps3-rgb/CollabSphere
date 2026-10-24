package com.collabsphere.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.collabsphere.model.Model.Message;
import com.collabsphere.model.Model.Task;

/**
 * WHAT THIS FILE DOES
 *   Small helper methods used by many screens:
 *     0. Constants (all fixed values)
 *     1. input validation       2. password hashing
 *     3. rating / star helpers  4. generic helpers (Pair, firstOrNull, max)
 *     5. file reading / writing (java.io)
 *
 * SYLLABUS
 *   Unit II - variables in an INTERFACE (Constants), NESTED interface.
 *   Unit IV - java.io: File, FileWriter, BufferedWriter, PrintWriter,
 *             FileReader, BufferedReader (section 5).
 *   Unit I  - String class methods, type wrappers (Integer, Character),
 *             operators, control statements (if, for, while, do-while,
 *             break, continue), type conversion & casting, RECURSION.
 *   Unit II - final class (cannot be inherited), GENERIC class Pair<K,V>,
 *             GENERIC methods firstOrNull() and max().
 */
public final class Util {

    // =================================================================
    // 0. CONSTANTS
    // =================================================================

    /**
     * WHAT THIS CLASS DOES
     *   Keeps every fixed value of the application (names, limits, status words)
     *   in one place, so nothing is "hard-coded" in many files.
     *
     * Other files use it like this:
     *     import com.collabsphere.util.Util.Constants;   ...   Constants.TASK_OPEN
     *
     * SYLLABUS
     *   Unit II  - variables in an interface: every field here is automatically
     *              public, static and final (a constant).
     *   Unit I   - arrays (WORKSPACE_TYPES, SKILL_OPTIONS).
     */
    public interface Constants {

    String APP_NAME = "CollabSphere";

    // Who is logging in
    String ROLE_STUDENT = "STUDENT";
    String ROLE_FACULTY = "FACULTY";

    // Role of a member inside a workspace
    String ROLE_LEADER = "LEADER";
    String ROLE_MEMBER = "MEMBER";

    // Workspace status
    String STATUS_ACTIVE    = "ACTIVE";
    String STATUS_COMPLETED = "COMPLETED";

    // Task status:  OPEN -> CLAIMED -> DONE
    String TASK_OPEN    = "OPEN";
    String TASK_CLAIMED = "CLAIMED";
    String TASK_DONE    = "DONE";

    // Peer review stars
    int MIN_RATING = 1;
    int MAX_RATING = 5;

    // Thread timings
    int CHAT_REFRESH_MS    = 3000;    // chat checks for new messages every 3 seconds
    int ARCHIVE_EVERY_MS   = 60000;   // archive thread wakes up every minute
    int ARCHIVE_AFTER_DAYS = 7;       // completed workspaces older than this are archived

    int MAX_MESSAGE_LENGTH = 500;

    // Arrays shown in combo boxes / lists
    String[] WORKSPACE_TYPES = { "Course Project", "Hackathon", "Study Group" };
    String[] SKILL_OPTIONS   = { "Java", "Python", "C", "C++", "SQL", "HTML/CSS", "JavaScript",
                                 "UI Design", "Testing", "Documentation", "Machine Learning" };

    // Files and folders (relative to the project folder)
    String EXPORT_FOLDER   = "exports";
    String REMEMBER_FILE   = "remember.txt";
    }


    private Util() {
        // private constructor: nobody creates Util objects, all methods are static
    }

    // =================================================================
    // 1. VALIDATION  (String class + wrapper classes)
    // =================================================================

    /** Throws InvalidInput if the text is null or only spaces. */
    public static String requireText(String text, String fieldName) {
        if (text == null || text.trim().isEmpty()) {
            throw new AppException.InvalidInput(fieldName + " cannot be empty.");
        }
        return text.trim();
    }

    /** Checks an email using only String methods. Returns it in lower case. */
    public static String checkEmail(String email) {
        String e = requireText(email, "Email").toLowerCase();
        int at = e.indexOf('@');
        int dot = e.lastIndexOf('.');
        // relational and logical operators combined in one expression
        boolean valid = at > 0 && dot > at + 1 && dot < e.length() - 1 && !e.contains(" ");
        if (!valid) {
            throw new AppException.InvalidInput("Please enter a valid email address.");
        }
        return e;
    }

    /** Password: at least 6 characters with at least one letter and one digit. */
    public static void checkPassword(String password) {
        if (password == null || password.length() < 6) {
            throw new AppException.InvalidInput("Password must be at least 6 characters.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) {          // wrapper class Character
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                break;                            // no need to check further
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new AppException.InvalidInput("Password needs at least one letter and one digit.");
        }
    }

    /** Name: letters, spaces and dots only. Returned in Title Case. */
    public static String checkName(String name) {
        String n = requireText(name, "Name");
        int i = 0;
        do {
            char c = n.charAt(i);
            if (!Character.isLetter(c) && c != ' ' && c != '.') {
                throw new AppException.InvalidInput("Name can contain only letters, spaces and dots.");
            }
            i++;
        } while (i < n.length());
        return toTitleCase(n);
    }

    /** "ravi   kumar" -> "Ravi Kumar"  (split, substring, StringBuilder). */
    public static String toTitleCase(String text) {
        String[] words = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(w.charAt(0)))
              .append(w.substring(1).toLowerCase())
              .append(' ');
        }
        return sb.toString().trim();
    }

    // =================================================================
    // 2. PASSWORD HASHING  (passwords are never stored as plain text)
    // =================================================================

    /** SHA-256 hash of (email + password) as a 64-character hex string. */
    public static String hashPassword(String password, String email) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((email.toLowerCase() + ":" + password).getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b & 0xff));   // bitwise AND makes the byte unsigned
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Hashing failed", e);
        }
    }

    // =================================================================
    // 3. RATING HELPERS  (recursion, casting)
    // =================================================================

    /** RECURSION: stars(3) = "★★★". Base case n <= 0. */
    public static String stars(int n) {
        if (n <= 0) {
            return "";
        }
        return "★" + stars(n - 1);
    }

    /** RECURSION: bar('#', 4) = "####". Used for text charts. */
    public static String bar(char c, int n) {
        return (n <= 0) ? "" : c + bar(c, n - 1);       // ternary operator
    }

    /** 3.4 -> "★★★☆☆ 3.40". Casting double -> int with (int). */
    public static String ratingText(double rating) {
        int full = (int) Math.round(rating);
        return stars(full) + bar('☆', Constants.MAX_RATING - full)
               + String.format(" %.2f", rating);
    }

    /** Percentage using integer arithmetic. part*100L makes it long first (widening). */
    public static int percent(int part, int total) {
        if (total == 0) {
            return 0;
        }
        return (int) (part * 100L / total);
    }

    // =================================================================
    // 4. GENERICS
    // =================================================================

    /** GENERIC CLASS: holds two values of any types, e.g. Pair<String, Integer>. */
    public static class Pair<K, V> {
        private final K first;
        private final V second;

        public Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }

        public K getFirst()  { return first; }
        public V getSecond() { return second; }
    }

    /** GENERIC METHOD: first element of any list, or null if empty. */
    public static <T> T firstOrNull(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    /** GENERIC METHOD with a BOUNDED type: works for Integer, String, Double... */
    public static <T extends Comparable<T>> T max(List<T> list) {
        T best = null;
        for (T item : list) {
            if (best == null || item.compareTo(best) > 0) {
                best = item;
            }
        }
        return best;
    }

    // =================================================================
    // 5. FILES (java.io)
    // =================================================================


    /** Creates exports/<prefix>_<name>_<date-time>.<ext> */
    private static File newExportFile(String prefix, String name, String extension) {
        File folder = new File(Constants.EXPORT_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        String safeName = name.replaceAll("[^A-Za-z0-9]+", "_");
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(folder, prefix + "_" + safeName + "_" + time + "." + extension);
    }

    /** FR-14: writes the whole chat to a text file and returns that file. */
    public static File exportChat(String workspaceName, List<Message> messages) throws IOException {
        File file = newExportFile("chat", workspaceName, "txt");
        PrintWriter out = null;
        try {
            out = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            out.println("Chat history of '" + workspaceName + "'  (exported " + new Date() + ")");
            out.println("------------------------------------------------------------");
            for (Message m : messages) {
                out.println(m.toChatLine());
            }
        } finally {
            if (out != null) {
                out.close();          // always close, even if an error happened
            }
        }
        return file;
    }

    /** Writes tasks as CSV:  Title,Description,Status,Assigned To */
    public static File exportTasks(String workspaceName, List<Task> tasks) throws IOException {
        File file = newExportFile("tasks", workspaceName, "csv");
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(file));
            out.write("Title,Description,Status,Assigned To");
            out.newLine();
            for (Task t : tasks) {
                // commas inside text would break the CSV, so replace them
                out.write(t.getTitle().replace(',', ';') + "," + t.getDescription().replace(',', ';')
                        + "," + t.getStatus() + "," + t.getAssigneeName());
                out.newLine();
            }
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return file;
    }

    /**
     * Reads a CSV file. Each line: Title,Description
     * Skips empty lines, lines starting with # and the header line.
     */
    public static List<String[]> importTasks(File file) throws IOException {
        List<String[]> rows = new ArrayList<String[]>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.toLowerCase().startsWith("title,")) {
                    continue;
                }
                rows.add(line.split(",", 3));     // [0] = title, [1] = description
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return rows;
    }

    /** Saves any text (faculty report). */
    public static File saveReport(String text) throws IOException {
        File file = newExportFile("report", "faculty", "txt");
        FileWriter out = null;
        try {
            out = new FileWriter(file);
            out.write(text);
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return file;
    }

    /** "Remember my email": writes email and role on two lines. */
    public static void rememberLogin(String email, String role) {
        try {
            PrintWriter out = new PrintWriter(new FileWriter(Constants.REMEMBER_FILE));
            out.println(email);
            out.println(role);
            out.close();
        } catch (IOException e) {
            System.out.println("Could not save remembered login: " + e.getMessage());
        }
    }

    /** Returns {email, role} or null when nothing is remembered. */
    public static String[] readRememberedLogin() {
        File f = new File(Constants.REMEMBER_FILE);
        if (!f.exists()) {
            return null;
        }
        try {
            BufferedReader in = new BufferedReader(new FileReader(f));
            String email = in.readLine();
            String role = in.readLine();
            in.close();
            return (email == null) ? null : new String[] { email, role };
        } catch (IOException e) {
            return null;
        }
    }

    public static void forgetLogin() {
        new File(Constants.REMEMBER_FILE).delete();
    }
}
