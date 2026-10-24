package com.collabsphere.model;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.collabsphere.util.AppException;
import com.collabsphere.util.Util.Constants;
import com.collabsphere.util.Util;

/**
 * WHAT THIS FILE DOES
 *   Holds all the "model" classes - the plain objects the program works with:
 *       Person (abstract) -> Student, Faculty
 *       Workspace (+ Member, Leader, Stats, ArchivedWorkspace)
 *       Task, Message
 *   They are STATIC NESTED classes of Model, so other files import them like
 *       import com.collabsphere.model.Model.Student;
 *   and then simply write "Student".
 *
 * SYLLABUS: see the comment above each class below.
 */
public final class Model {

    private Model() { }   // Model is only a container - no objects of it

    /**
     * WHAT THIS CLASS DOES
     *   The common PARENT of Student and Faculty. Both have an id, name, email
     *   and password, so these are written only once here.
     *
     * SYLLABUS
     *   Unit I  - class, constructors, CONSTRUCTOR OVERLOADING, "this" keyword,
     *             ACCESS CONTROL, static variable (lives for the whole program).
     *   Unit II - ABSTRACT class (cannot do "new Person()"), hierarchical
     *             abstraction (Person -> Student, Faculty), FINAL method,
     *             overriding methods of the OBJECT class: toString, equals, hashCode.
     *
     *   Access control used below:
     *     private   -> only this class           (email, passwordHash)
     *     protected -> this class + subclasses   (id, name)
     *     public    -> every class               (getters)
     *   (Note: classes nested inside the same outer class - here Model - may
     *    see each other's private members; for all OTHER files the rules
     *    above apply exactly.)
     */
    public static abstract class Person {

        private static int objectsCreated = 0;   // one shared copy for ALL Person objects

        protected int id;
        protected String name;
        private String email;
        private String passwordHash;

        // Constructor 1: used when reading an existing user from the database
        protected Person(int id, String name, String email) {
            this.id = id;              // "this.id" = the field, "id" = the parameter
            this.name = name;
            this.email = email;
            objectsCreated++;
        }

        // Constructor 2 (overloaded): a new user who has no id yet
        protected Person(String name, String email) {
            this(0, name, email);      // this(...) calls constructor 1
        }

        // ABSTRACT methods: every subclass MUST give its own version
        public abstract String getRole();

        public abstract String getWelcomeTitle();

        // FINAL method: subclasses are not allowed to override it
        public final String getEmail() {
            return email;
        }

        public int getId()                 { return id; }
        public void setId(int id)          { this.id = id; }
        public String getName()            { return name; }
        public void setName(String name)   { this.name = name; }
        public String getPasswordHash()    { return passwordHash; }
        public void setPasswordHash(String h) { this.passwordHash = h; }

        public static int getObjectsCreated() {
            return objectsCreated;
        }

        // ----- methods inherited from java.lang.Object, overridden -----

        @Override
        public String toString() {
            return name + " <" + email + ">";
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Person p = (Person) other;               // down-casting Object -> Person
            return id == p.id;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    /**
     * WHAT THIS CLASS DOES
     *   A student: profile (skills, bio) and Global Rating (FR-4, FR-5).
     *
     * SYLLABUS (Unit II - forms of inheritance)
     *   SPECIALIZATION : Student IS-A Person and overrides getRole() etc.
     *   COMBINATION    : it EXTENDS a class (Person) and IMPLEMENTS an
     *                    interface (Reviewable) at the same time.
     *   super(...)     : calls the Person constructor.
     *   Interfaces     : Rateable and Reviewable are defined at the bottom of this
     *                    file. Reviewable EXTENDS Rateable (extending interfaces).
     *   Unit IV        : Set (TreeSet keeps skills sorted and without duplicates).
     */
    public static class Student extends Person implements Reviewable {

        private Set<String> skills = new TreeSet<String>();
        private String bio = "";
        private double rating;
        private int reviewCount;

        public Student(String name, String email) {
            super(name, email);
        }

        public Student(int id, String name, String email, double rating) {
            super(id, name, email);
            this.rating = rating;
        }

        @Override
        public String getRole() {
            return Constants.ROLE_STUDENT;
        }

        @Override
        public String getWelcomeTitle() {
            return "Student Dashboard - " + name;      // "name" is protected in Person
        }

        // ----- from interface Rateable -----
        @Override
        public double getRating() {
            return rating;
        }

        // ----- from interface Reviewable -----
        @Override
        public int getReviewCount() {
            return reviewCount;
        }

        @Override
        public String getRatingText() {
            if (reviewCount == NO_REVIEWS) {           // constant inherited from the interface
                return "Not rated yet";
            }
            return Util.ratingText(rating) + "  (" + reviewCount + " reviews)";
        }

        public void setRating(double rating)        { this.rating = rating; }
        public void setReviewCount(int count)       { this.reviewCount = count; }
        public String getBio()                      { return bio; }
        public void setBio(String bio)              { this.bio = (bio == null) ? "" : bio; }
        public Set<String> getSkills()              { return skills; }

        /** "Java, SQL, Java" -> {Java, SQL}  (stored in the DB as one text column) */
        public void setSkillsFromText(String text) {
            skills.clear();
            if (text == null) {
                return;
            }
            for (String s : text.split(",")) {
                if (s.trim().length() > 0) {
                    skills.add(s.trim());
                }
            }
        }

        /** {Java, SQL} -> "Java, SQL" */
        public String getSkillsText() {
            StringBuilder sb = new StringBuilder();
            for (String s : skills) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(s);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return super.toString() + " [Student]";    // super.method() calls the parent version
        }
    }

    /** INTERFACE: anything that has a rating. */
    interface Rateable {
        double getRating();
    }

    /** EXTENDING an interface: Reviewable gets getRating() from Rateable and adds more. */
    interface Reviewable extends Rateable {

        int NO_REVIEWS = 0;          // variable in an interface = public static final constant

        int getReviewCount();

        String getRatingText();
    }

    /**
     * WHAT THIS CLASS DOES
     *   A faculty member (pre-registered, logs in to monitor workspaces).
     *
     * SYLLABUS (Unit II)
     *   EXTENSION form of inheritance: Faculty keeps everything from Person and
     *   ADDS something new (department) that Person does not have.
     */
    public static class Faculty extends Person {

        private String department;

        public Faculty(int id, String name, String email, String department) {
            super(id, name, email);
            this.department = department;
        }

        @Override
        public String getRole() {
            return Constants.ROLE_FACULTY;
        }

        @Override
        public String getWelcomeTitle() {
            return "Faculty Dashboard - " + name + " (" + department + ")";
        }

        public String getDepartment() {
            return department;
        }

        @Override
        public String toString() {          // what a JComboBox shows
            return name + " - " + department;
        }
    }

    /**
     * WHAT THIS CLASS DOES
     *   A workspace (course project / hackathon / study group) and its members.
     *
     * SYLLABUS
     *   Unit I  - STATIC NESTED classes (Member, Leader, Stats), METHOD OVERLOADING
     *             (two addMember methods).
     *   Unit II - EXTENSION      : Leader extends Member and adds powers.
     *             LIMITATION     : ArchivedWorkspace (bottom of file) extends
     *                              Workspace but takes away the right to edit.
     *             POLYMORPHISM   : create() returns a Workspace reference that may
     *                              really be an ArchivedWorkspace object.
     *   Unit IV - List (ArrayList of members).
     */
    public static class Workspace {

        private int id;
        private String name;
        private String type;
        private String description;
        private int maxMembers;
        private String status;
        private int facultyId;                       // 0 = no supervisor
        private List<Member> members = new ArrayList<Member>();
        private Stats stats;                         // only filled for the faculty screen

        public Workspace(int id, String name, String type, String description,
                         int maxMembers, String status, int facultyId) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.description = description;
            this.maxMembers = maxMembers;
            this.status = status;
            this.facultyId = facultyId;
        }

        /** Factory method used by the DAO. Archived rows become ArchivedWorkspace objects. */
        public static Workspace create(int id, String name, String type, String description,
                                       int maxMembers, String status, int facultyId, boolean archived) {
            if (archived) {
                return new ArchivedWorkspace(id, name, type, description, maxMembers, status, facultyId);
            }
            return new Workspace(id, name, type, description, maxMembers, status, facultyId);
        }

        public int getId()              { return id; }
        public void setId(int id)       { this.id = id; }
        public String getName()         { return name; }
        public String getType()         { return type; }
        public String getDescription()  { return description; }
        public int getMaxMembers()      { return maxMembers; }
        public String getStatus()       { return status; }
        public int getFacultyId()       { return facultyId; }
        public List<Member> getMembers(){ return members; }
        public Stats getStats()         { return stats; }
        public void setStats(Stats s)   { this.stats = s; }

        public boolean isCompleted() {
            return Constants.STATUS_COMPLETED.equals(status);
        }

        public boolean isArchived() {
            return false;
        }

        /** Called before adding tasks / members / messages. Normal workspaces allow it. */
        public void checkEditable() throws AppException {
            // nothing to check for a normal workspace
        }

        // ----- METHOD OVERLOADING: same name, different parameters -----
        public void addMember(Member m) {
            members.add(m);
        }

        public void addMember(int studentId, String studentName, String role) {
            if (Constants.ROLE_LEADER.equals(role)) {
                addMember(new Leader(studentId, studentName));
            } else {
                addMember(new Member(studentId, studentName, role));
            }
        }

        /** Returns the member with this student id, or null. */
        public Member findMember(int studentId) {
            for (Member m : members) {
                if (m.getStudentId() == studentId) {
                    return m;
                }
            }
            return null;
        }

        @Override
        public String toString() {                   // text shown in the JTree
            return name + " [" + type + "]" + (isCompleted() ? " ✔" : "");
        }

        // =================================================================
        // STATIC NESTED CLASSES
        // =================================================================

        /** One member of a workspace (FR-11: every member has a role). */
        public static class Member {
            private int studentId;
            private String studentName;
            private String role;

            public Member(int studentId, String studentName, String role) {
                this.studentId = studentId;
                this.studentName = studentName;
                this.role = role;
            }

            public int getStudentId()      { return studentId; }
            public String getStudentName() { return studentName; }
            public String getRole()        { return role; }

            /** Only leaders may mark the workspace completed. Overridden in Leader. */
            public boolean canMarkComplete() {
                return false;
            }

            @Override
            public String toString() {
                return studentName + " (" + role.toLowerCase() + ")";
            }
        }

        /** EXTENSION: a Leader is a Member with extra power. */
        public static class Leader extends Member {
            public Leader(int studentId, String studentName) {
                super(studentId, studentName, Constants.ROLE_LEADER);
            }

            @Override
            public boolean canMarkComplete() {        // METHOD OVERRIDING
                return true;
            }
        }

        /** Numbers shown on the faculty screen. */
        public static class Stats {
            private int members, pendingTasks, doneTasks;

            public Stats(int members, int pendingTasks, int doneTasks) {
                this.members = members;
                this.pendingTasks = pendingTasks;
                this.doneTasks = doneTasks;
            }

            public int getMembers()      { return members; }
            public int getPendingTasks() { return pendingTasks; }
            public int getDoneTasks()    { return doneTasks; }
        }
    }

    /**
     * LIMITATION form of inheritance (FR-24 archive):
     * an archived workspace is a Workspace that can no longer be edited.
     * It is not public - other packages only ever see it as a "Workspace".
     */
    static class ArchivedWorkspace extends Workspace {

        ArchivedWorkspace(int id, String name, String type, String description,
                          int maxMembers, String status, int facultyId) {
            super(id, name, type, description, maxMembers, status, facultyId);
        }

        @Override
        public boolean isArchived() {
            return true;
        }

        @Override
        public void checkEditable() throws AppException {
            throw new AppException("'" + getName() + "' is archived and read-only.");
        }

        @Override
        public String toString() {
            return "[ARCHIVED] " + getName();
        }
    }

    /**
     * WHAT THIS CLASS DOES
     *   One task in a workspace's shared task list (FR-15 .. FR-19).
     *   Life of a task:  OPEN  --claim-->  CLAIMED  --mark done-->  DONE
     *
     * SYLLABUS (Unit I) - constructor overloading, this(...) chaining.
     */
    public static class Task {

        private int id;
        private int workspaceId;
        private String title;
        private String description;
        private String status;
        private int assigneeId;          // 0 = nobody has claimed it yet
        private String assigneeName;

        // Constructor 1: a brand-new task typed by the user
        public Task(int workspaceId, String title, String description) {
            this(0, workspaceId, title, description, Constants.TASK_OPEN, 0, null);
        }

        // Constructor 2: a task read from the database
        public Task(int id, int workspaceId, String title, String description,
                    String status, int assigneeId, String assigneeName) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.title = title;
            this.description = (description == null) ? "" : description;
            this.status = status;
            this.assigneeId = assigneeId;
            this.assigneeName = assigneeName;
        }

        public int getId()              { return id; }
        public int getWorkspaceId()     { return workspaceId; }
        public String getTitle()        { return title; }
        public String getDescription()  { return description; }
        public String getStatus()       { return status; }
        public int getAssigneeId()      { return assigneeId; }

        public String getAssigneeName() {
            return (assigneeName == null) ? "-" : assigneeName;
        }

        @Override
        public String toString() {
            return "#" + id + " " + title + " (" + status + ")";
        }
    }

    /**
     * WHAT THIS CLASS DOES
     *   One chat message (FR-12), plus "History" - the list of messages that the
     *   chat screen is currently showing.
     *
     * SYLLABUS (Unit II)
     *   CONSTRUCTION form of inheritance: History extends ArrayList only to REUSE
     *   its code (storing a list). A chat history is not really "a kind of
     *   ArrayList" - just like java.util.Stack is built on top of Vector.
     */
    public static class Message {

        private int id;
        private int workspaceId;
        private int studentId;
        private String senderName;
        private String text;
        private Timestamp sentAt;

        // new message typed by the user
        public Message(int workspaceId, int studentId, String text) {
            this(0, workspaceId, studentId, null, text, null);
        }

        // message read from the database
        public Message(int id, int workspaceId, int studentId, String senderName,
                       String text, Timestamp sentAt) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.studentId = studentId;
            this.senderName = senderName;
            this.text = text;
            this.sentAt = sentAt;
        }

        public int getId()            { return id; }
        public int getWorkspaceId()   { return workspaceId; }
        public int getStudentId()     { return studentId; }
        public String getText()       { return text; }

        /** "[21-10-2026 11:30] Ravi: hello" */
        public String toChatLine() {
            String time = (sentAt == null) ? "" : new SimpleDateFormat("dd-MM-yyyy HH:mm").format(sentAt);
            return "[" + time + "] " + senderName + ": " + text;
        }

        /** CONSTRUCTION: reuses ArrayList and adds one helper method. */
        public static class History extends ArrayList<Message> {
            private static final long serialVersionUID = 1L;

            /** id of the newest message (0 when empty) - the chat asks the DB for ids above this. */
            public int lastId() {
                return isEmpty() ? 0 : get(size() - 1).getId();
            }
        }
    }
}
