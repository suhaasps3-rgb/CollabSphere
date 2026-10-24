package com.collabsphere.util;

import java.sql.SQLException;

/**
 * WHAT THIS FILE DOES
 *   Defines ALL the error types of the application in one file.
 *
 * SYLLABUS (Unit III - Exception handling)
 *   - creating our own exception subclasses
 *   - CHECKED exception : AppException extends Exception. The compiler forces
 *                         the caller to either catch it or declare "throws".
 *   - UNCHECKED exception: InvalidInput extends RuntimeException. The compiler
 *                         does not force a try/catch.
 *   - exception hierarchy: the small classes below extend AppException, so a
 *     single  catch (AppException e)  handles every one of them.
 *   - exception chaining: Database keeps the original SQLException as "cause".
 *
 * Usage example:   throw new AppException.WorkspaceFull("Library App", 4);
 */
public class AppException extends Exception {

    private static final long serialVersionUID = 1L;

    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }

    // ------------------------------------------------------------------
    // Specific errors (static nested classes)
    // ------------------------------------------------------------------

    /** Wrong email, password or role at login. */
    public static class LoginFailed extends AppException {
        private static final long serialVersionUID = 1L;
        public LoginFailed() {
            super("Invalid email or password.");
        }
    }

    /** FR-2: two accounts cannot have the same email. */
    public static class DuplicateEmail extends AppException {
        private static final long serialVersionUID = 1L;
        public DuplicateEmail(String email) {
            super("An account with " + email + " already exists.");
        }
    }

    /** FR-9: a full workspace cannot accept new members. */
    public static class WorkspaceFull extends AppException {
        private static final long serialVersionUID = 1L;
        public WorkspaceFull(String workspace, int max) {
            super("Workspace '" + workspace + "' is full (maximum " + max + " members).");
        }
    }

    /** FR-17/18: somebody else already claimed the task. */
    public static class TaskAlreadyClaimed extends AppException {
        private static final long serialVersionUID = 1L;
        public TaskAlreadyClaimed(String task, String byWhom) {
            super("Task '" + task + "' is already claimed by " + byWhom + ".");
        }
    }

    /** FR-22: the same teammate cannot be reviewed twice. */
    public static class DuplicateReview extends AppException {
        private static final long serialVersionUID = 1L;
        public DuplicateReview(String name) {
            super("You have already reviewed " + name + " in this workspace.");
        }
    }

    /** Any database problem. Wraps the built-in SQLException. */
    public static class DatabaseError extends AppException {
        private static final long serialVersionUID = 1L;
        public DatabaseError(String whatFailed, SQLException cause) {
            super(whatFailed + " (" + cause.getMessage() + ")", cause);
        }
    }

    /** UNCHECKED: wrong input typed in a form (empty name, bad email...). */
    public static class InvalidInput extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public InvalidInput(String message) {
            super(message);
        }
    }
}
