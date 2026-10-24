# CollabSphere – where each syllabus topic is used

There are 8 Java files. Every file starts with a **WHAT THIS FILE DOES** block, and every class inside it has a **WHAT THIS CLASS DOES / SYLLABUS** block. The tables below give the same information sorted by unit. Files: `Main.java`, `util/AppException.java`, `util/Util.java`, `model/Model.java`, `dao/Database.java`, `ui/LoginFrame.java`, `ui/MainFrame.java`, `ui/ChatPanel.java` (under `src/com/collabsphere/`).

## UNIT-I

| Topic | File → where |
|---|---|
| Need for OOP, OOP concepts, coping with complexity, abstraction | `Main.java` header (packages = layers; abstraction, encapsulation, inheritance, polymorphism) |
| Data types, variables, scope & lifetime | `Model.java › Person` (static `objectsCreated` lives for the whole run; instance fields), `MainFrame.java › showMemory` (loop-local `temp`) |
| Arrays | `Util.java › Constants` (`WORKSPACE_TYPES`, `SKILL_OPTIONS`), `Database.java` (part 2) (`CREATE_TABLES`, 2-D `faculty` array), `Database.java › StudentDAO.starCounts` (`int[]`) |
| Operators, expressions | `Util.java` – logical/relational (`checkEmail`), bitwise `&` (`hashPassword`), ternary (`bar`) |
| Control statements | `if/else` everywhere; `switch` in `MainFrame.java › MenuHandler`; `for`, `while`, `do-while`, `break`, `continue` in `Util.java` |
| Type conversion & casting | `Util.ratingText` `(int) Math.round`, `Util.percent` (widening to long), `PeerReviewDialog.StarRating.makeStar`, `(Student) user` in `LoginFrame.java` |
| Simple Java program | `Main.java` |
| Classes, objects, constructors, methods | every class in `Model.java` |
| Access control | `Model.java › Person` (private / protected / public explained in its header) |
| `this` keyword | `Model.java › Person` constructors (`this.id = id`, `this(0, name, email)`) |
| Garbage collection | `Main.logout()`, Help ▸ Memory (`MainFrame.showMemory`, `System.gc()`) |
| Method overloading | `Database.java › close(...)` ×3, `Model.java › Workspace.addMember(...)` ×2, `Database.java › BaseDAO.insert(...)` ×2 |
| Constructor overloading | `Person`, `Student`, `Task`, `Message` |
| Recursion | `Util.stars()`, `Util.bar()`, `MainFrame.countNodes()` |
| Nested & inner classes | static nested: `Workspace.Member/Leader/Stats`, `Message.History`, `Util.Pair`, `AppException.*`, `MainFrame.TaskTableModel`; inner: `MainFrame.MenuHandler`, `ChatPanel.SenderThread/RefreshThread`, `ThreadDemoDialog.ClaimJob`; anonymous: all listeners |
| String class | `Util.java` (trim, indexOf, lastIndexOf, contains, split, substring, charAt, format), `Student.setSkillsFromText`, `Util.java` section 5 |
| Type wrappers | `Character.isLetter/isDigit`, `Integer` in `new Object[] { id }` (autoboxing), `(Integer) size.getSelectedItem()` (unboxing) |

## UNIT-II

| Topic | File → where |
|---|---|
| Hierarchical abstraction | `Person → Student, Faculty`; `BaseDAO → 4 DAOs`; `AppException → 6 subclasses` |
| Specialization | `Student extends Person` |
| Specification | `BaseDAO` abstract `mapRow()` – each DAO must specify it |
| Construction | `Message.History extends ArrayList<Message>` |
| Extension | `Faculty extends Person` (adds department), `Workspace.Leader extends Member` |
| Limitation | `ArchivedWorkspace` (in `Model.java`) – `checkEditable()` always throws |
| Combination | `Student extends Person implements Reviewable` |
| Member access rules | `Model.java › Person` |
| `super` | `super(...)` in `Student`, `Faculty`, `Leader`, `ArchiveThread`; `super.toString()` in `Student`; `super.paintComponent()` in `StarRating` |
| `final` with inheritance | `final class Util`, `final` method `Person.getEmail()` |
| Method overriding / polymorphism | `getRole()`, `getWelcomeTitle()`, `canMarkComplete()`, `checkEditable()`; `StudentDAO.login()` returns a `Person` |
| Abstract classes | `Person`, `BaseDAO` |
| Object class | `Person.toString/equals/hashCode`, `getClass()` |
| Packages, importing, CLASSPATH | 5 packages (`util`, `model`, `dao`, `ui`, root); nested-class imports such as `import com.collabsphere.model.Model.Student;`; `import` in every file; README step 1d (driver JAR on the build path) |
| Interfaces – define, implement, variables, extend | `Rateable`, `Reviewable extends Rateable` (in `Model.java`); `AppConstants` (interface variables); `MenuHandler implements ActionListener`; `ClaimJob implements Runnable` |
| Generic class | `BaseDAO<T>`, `Util.Pair<K,V>` |
| Generic methods | `Util.firstOrNull()`, `Util.max()` (bounded `<T extends Comparable<T>>`) |

## UNIT-III

| Topic | File → where |
|---|---|
| Exception hierarchy, checked/unchecked, own subclasses | `AppException.java` (whole file) |
| try / catch / multiple catch | `MainFrame.MenuHandler`, `LoginFrame.login()` |
| throw / throws | `TaskDAO.claim`, `WorkspaceDAO.invite`, `StudentDAO.register` |
| finally | `BaseDAO.queryList` (closing JDBC objects), `Util.java` section 5, `MainFrame.claimTask` |
| Built-in exceptions | `SQLException`, `ClassNotFoundException` (`Database.java` part 1), `IOException`, `InterruptedException` |
| Multithreading vs multitasking | `ChatPanel.java › ThreadDemoDialog` header and window text |
| Thread life cycle | `ThreadDemoDialog.lifeCycleDemo()` – NEW, RUNNABLE, TIMED_WAITING, WAITING, BLOCKED, TERMINATED |
| Creating threads | `extends Thread`: `Main.ArchiveThread`, `ChatPanel.SenderThread/RefreshThread`; `implements Runnable`: `ThreadDemoDialog.ClaimJob` |
| Priorities | `ArchiveThread` (MIN), `ClaimJob` threads (MAX) |
| Synchronization | `synchronized` method `TaskDAO.claim`, `ArchiveThread.archiveNow`; `synchronized` blocks in `ChatPanel` |
| Inter-thread communication | `ChatPanel`: Send → `notify()`, `SenderThread` → `wait()`; `lifeCycleDemo` |
| Thread groups | `Main.WORKERS` (listed by Thread Tools ▸ List threads) |
| Daemon threads | `ArchiveThread`, `SenderThread`, `RefreshThread` |

## UNIT-IV

| Topic | File → where |
|---|---|
| String & Object class | see Unit I/II rows |
| List / Set / Map | `ArrayList`, `LinkedList` (`ChatPanel.outbox`); `TreeSet` (`Student.skills`), `HashSet` (`StudentDAO.reviewedIds`); `LinkedHashMap` (`MessageDAO.messagesPerDay`), `Map.Entry` loop (`FacultyFrame.chatReport`) |
| java.io | `Util.java` (section 5) (File, FileWriter, BufferedWriter, PrintWriter, FileReader, BufferedReader), `Database.java` part 1 (FileInputStream + Properties) |
| Delegation event model | `MainFrame` header and `MenuHandler` |
| Events / listeners | ActionEvent (buttons, menus), ItemEvent (`onlyMineBox`, auto-refresh, faculty filter), MouseEvent (tree, table, stars), KeyEvent (chat Enter key, login Enter key), WindowEvent (closing) |
| Adapter classes | `MouseAdapter`, `KeyAdapter`, `WindowAdapter` |
| Border / Grid / Flow / Card / GridBag layout | `MainFrame` / `LoginFrame` forms & `FacultyFrame` cards / toolbars / `LoginFrame` cards / `ProfilePanel`, `PeerReviewDialog` |

## UNIT-V

| Topic | File → where |
|---|---|
| Swing, MVC | `MainFrame.TaskTableModel` (Model) + `JTable` (View) + listeners (Controller); reused in `FacultyFrame` |
| JFrame, JComponent | `LoginFrame`, `MainFrame`, `FacultyFrame`; `PeerReviewDialog.StarRating extends JComponent` |
| JLabel | `LoginFrame.java` (title, form labels), `MainFrame.java` (status bar) |
| JTextField, JButton, JCheckBox, JRadioButton | `LoginFrame` (role radios, remember-me), `MainFrame` (`onlyMineBox`) |
| JList, JComboBox | `ProfilePanel` (skills); `MainFrame.newWorkspace`, `PeerReviewDialog`, `FacultyFrame` filter |
| Trees, Tables | `MainFrame` JTree and JTables, `FacultyFrame` |
| JMenuBar, JMenu, JMenuItem, JCheckBoxMenuItem, JRadioButtonMenuItem, JSeparator | `MainFrame.createMenuBar()` |
| Popup menu | right-click on the tree / task table in `MainFrame` |
| JDBC drivers & architecture | `Database.jdbcInfo()` (Help ▸ JDBC Info), `Database.java` part 1 comment |
| JDBC basic steps | `Database.java` part 1 comment; all 6 steps in one method: `StudentDAO.findFaculty()` |
| JDBC classes & interfaces | DriverManager, Connection, Statement (+batch), PreparedStatement, ResultSet, ResultSetMetaData (`BaseDAO.hasColumn`), DatabaseMetaData (`jdbcInfo`); transactions in `WorkspaceDAO.create/invite`, `StudentDAO.submitReview` |
| Creating database and tables with JDBC | `Database.initialize()` |
