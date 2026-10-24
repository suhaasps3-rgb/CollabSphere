package com.collabsphere;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.collabsphere.dao.Database.WorkspaceDAO;
import com.collabsphere.dao.Database;
import com.collabsphere.model.Model.Person;
import com.collabsphere.ui.LoginFrame;
import com.collabsphere.util.AppException;
import com.collabsphere.util.Util.Constants;

/**
 * WHAT THIS FILE DOES
 *   The program starts here.
 *     1. Create the MySQL database and tables (Database.initialize)
 *     2. Start the background archive thread
 *     3. Show the login window
 *
 * HOW THE PROJECT IS ORGANISED (OOP thinking - coping with complexity)
 *   model  - plain objects: Person, Student, Faculty, Workspace, Task, Message
 *   dao    - talks to MySQL with JDBC (one DAO class per main table)
 *   ui     - Swing windows; they call the DAO classes
 *   util   - constants, errors, small helpers, file reading/writing
 *   Each package has one job, so a big problem becomes small, easy pieces.
 *   ABSTRACTION (Person, BaseDAO), ENCAPSULATION (private fields + getters),
 *   INHERITANCE (Student extends Person) and POLYMORPHISM (a Person variable
 *   holding a Student or Faculty) are all used.
 *
 * SYLLABUS
 *   Unit I   - a simple Java program: public static void main(String[] args).
 *   Unit III - THREAD GROUP, DAEMON thread, thread PRIORITY, extending Thread.
 *   Unit I   - garbage collection: logout() drops the only reference to the user.
 */
public class Main {

    /** Every background thread of the app is created inside this group. */
    public static final ThreadGroup WORKERS = new ThreadGroup("CollabSphere-Workers");

    private static Person currentUser;          // Student or Faculty (polymorphism)
    private static ArchiveThread archiveThread;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // keep Java's default look if the system look is not available
        }

        try {
            Database.initialize();
        } catch (AppException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Database problem", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        archiveThread = new ArchiveThread();
        archiveThread.start();

        // Swing windows must be created on the Swing thread (Event Dispatch Thread)
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LoginFrame().setVisible(true);
            }
        });
    }

    public static Person getCurrentUser()            { return currentUser; }
    public static void setCurrentUser(Person p)      { currentUser = p; }
    public static ArchiveThread getArchiveThread()   { return archiveThread; }

    /**
     * After this, no variable refers to the old Student/Faculty object, so it
     * becomes eligible for GARBAGE COLLECTION - Java frees the memory itself.
     */
    public static void logout() {
        currentUser = null;
    }

    // =====================================================================
    /**
     * FR-24: automatic archiving, done by a background thread.
     *
     *  - created by EXTENDING the Thread class and overriding run()
     *  - DAEMON thread: a "helper" thread. Java does not wait for it when the
     *    user closes the program.
     *  - MIN_PRIORITY: archiving is not urgent, so it gets the lowest priority.
     */
    public static class ArchiveThread extends Thread {

        private final WorkspaceDAO workspaceDAO = new WorkspaceDAO();

        public ArchiveThread() {
            super(WORKERS, "Archive-Thread");
            setDaemon(true);                       // must be set BEFORE start()
            setPriority(Thread.MIN_PRIORITY);
        }

        @Override
        public void run() {
            while (true) {
                archiveNow(Constants.ARCHIVE_AFTER_DAYS);
                try {
                    Thread.sleep(Constants.ARCHIVE_EVERY_MS);   // thread state: TIMED_WAITING
                } catch (InterruptedException e) {
                    return;                                       // thread ends: TERMINATED
                }
            }
        }

        /** synchronized: a manual run and the automatic run can never overlap. */
        public synchronized int archiveNow(int olderThanDays) {
            try {
                int count = workspaceDAO.archiveOldWorkspaces(olderThanDays);
                if (count > 0) {
                    System.out.println("Archived " + count + " workspace(s).");
                }
                return count;
            } catch (AppException e) {
                System.out.println("Archive failed: " + e.getMessage());
                return 0;
            }
        }
    }
}
