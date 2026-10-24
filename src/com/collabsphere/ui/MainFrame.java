package com.collabsphere.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import com.collabsphere.Main;
import com.collabsphere.dao.Database.StudentDAO;
import com.collabsphere.dao.Database.TaskDAO;
import com.collabsphere.dao.Database.WorkspaceDAO;
import com.collabsphere.dao.Database;
import com.collabsphere.model.Model.Faculty;
import com.collabsphere.model.Model.Person;
import com.collabsphere.model.Model.Student;
import com.collabsphere.model.Model.Task;
import com.collabsphere.model.Model.Workspace;
import com.collabsphere.util.AppException;
import com.collabsphere.util.Util.Constants;
import com.collabsphere.util.Util;

/* =====================================================================
 * FILE: MainFrame.java  - the student's window and its parts
 *   class MainFrame        (public)  main window: menus, tree, tabs, actions
 *   class TaskTableModel             data model of the task JTable (MVC)
 *   class ProfilePanel               "My Profile" tab
 *   class PeerReviewDialog           star-rating review form (+ StarRating)
 * The helper classes have no "public", so they are visible only inside
 * package com.collabsphere.ui.
 * ===================================================================== */

/**
 * WHAT THIS CLASS DOES
 *   The student's main window (EIR-2):
 *     - menu bar + toolbar at the top
 *     - JTree of "my workspaces -> members" on the left (FR-10)
 *     - tabs: Tasks, Chat, Leaderboard, My Profile
 *
 * HOW A CLICK IS HANDLED (delegation event model)
 *   A menu item or button is the event SOURCE. When clicked it creates an
 *   ActionEvent and passes ("delegates") it to the registered LISTENER - here
 *   the inner class MenuHandler - which calls the right method, e.g. claimTask().
 *   That method calls a DAO, the DAO talks to MySQL, and the screen is refreshed.
 *
 * SYLLABUS
 *   Unit V  - JMenuBar, JMenu, JMenuItem, JCheckBoxMenuItem, JRadioButtonMenuItem,
 *             JSeparator, JPopupMenu (right click), JTree, JTable, JCheckBox,
 *             MVC (TaskTableModel at the bottom = the Model, JTable = the View).
 *   Unit IV - BorderLayout, FlowLayout, GridLayout; ActionEvent, ItemEvent,
 *             MouseEvent, WindowEvent; MouseAdapter and WindowAdapter.
 *   Unit I  - INNER class (MenuHandler), STATIC NESTED class (TaskTableModel),
 *             RECURSION (countNodes), garbage collection (showMemory).
 *   Unit III - multiple catch blocks (MenuHandler).
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final Student student;
    private final WorkspaceDAO workspaceDAO = new WorkspaceDAO();
    private final TaskDAO taskDAO = new TaskDAO();
    private final StudentDAO studentDAO = new StudentDAO();

    private Workspace current;                      // workspace selected in the tree

    // left: tree
    private final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("My Workspaces");
    private final JTree tree = new JTree(treeRoot);

    // tabs
    private final TaskTableModel taskModel = new TaskTableModel();
    private final JTable taskTable = new JTable(taskModel);
    private final JCheckBox onlyMineBox = new JCheckBox("Only my tasks");
    private final DefaultTableModel leaderModel =
            new DefaultTableModel(new String[] { "Rank", "Student", "Tasks done", "Global Rating" }, 0);
    private final ChatPanel chatPanel;
    private final ProfilePanel profilePanel;
    private final JTabbedPane tabs = new JTabbedPane();

    private final JLabel titleLabel = new JLabel("Select a workspace");
    private final JLabel statusBar = new JLabel(" ");
    private final MenuHandler handler = new MenuHandler();

    public MainFrame(Student student) {
        this.student = student;
        Person p = student;                         // up-casting: a Student IS-A Person
        setTitle(Constants.APP_NAME + " - " + p.getWelcomeTitle());   // runs Student's version

        chatPanel = new ChatPanel(student);
        profilePanel = new ProfilePanel(student);

        setJMenuBar(createMenuBar());
        setLayout(new BorderLayout());
        add(createToolbar(), BorderLayout.NORTH);
        add(createTree(), BorderLayout.WEST);
        add(createTabs(), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApp();
            }
        });

        setSize(1100, 700);
        setLocationRelativeTo(null);
        refreshAll();
    }

    // =================================================================
    // INNER CLASS: one listener for every menu item and button.
    // Each item has an "action command" string that tells us what to do.
    // =================================================================
    private class MenuHandler implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String command = e.getActionCommand();
            try {
                switch (command) {
                    case "New Workspace":   newWorkspace();       break;
                    case "Invite":          invite();             break;
                    case "Mark Completed":  markCompleted();      break;
                    case "Peer Review":     peerReview();         break;
                    case "Refresh":         refreshAll();         break;
                    case "Add Task":        addTask();            break;
                    case "Claim":           claimTask();          break;
                    case "Mark Done":       markDone();           break;
                    case "Import Tasks":    importTasks();        break;
                    case "Export Tasks":    exportTasks();        break;
                    case "Thread Tools":    new ThreadDemoDialog(MainFrame.this, current, student).setVisible(true); break;
                    case "Archive Now":     archiveNow();         break;
                    case "JDBC Info":       showText("JDBC Information", Database.jdbcInfo()); break;
                    case "Memory":          showMemory();         break;
                    case "Logout":          logout();             break;
                    case "Exit":            exitApp();            break;
                    default:                statusBar.setText("Unknown command: " + command);
                }
            } catch (AppException.InvalidInput ex) {       // wrong input
                JOptionPane.showMessageDialog(MainFrame.this, ex.getMessage(), "Check input",
                                              JOptionPane.WARNING_MESSAGE);
            } catch (AppException ex) {                    // any of our checked exceptions
                JOptionPane.showMessageDialog(MainFrame.this, ex.getMessage(), "Error",
                                              JOptionPane.ERROR_MESSAGE);
            } catch (IOException ex) {                     // file problems
                JOptionPane.showMessageDialog(MainFrame.this, "File error: " + ex.getMessage());
            }
        }
    }

    /** Creates a menu item that sends its text as the command to MenuHandler. */
    private JMenuItem menuItem(String text, KeyStroke shortcut) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(handler);
        if (shortcut != null) {
            item.setAccelerator(shortcut);
        }
        return item;
    }

    private JButton button(String text) {
        JButton b = new JButton(text);
        b.addActionListener(handler);
        return b;
    }

    // =================================================================
    // BUILDING THE WINDOW
    // =================================================================
    private JMenuBar createMenuBar() {
        JMenu file = new JMenu("File");
        file.add(menuItem("Import Tasks", KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK)));
        file.add(menuItem("Export Tasks", null));
        file.add(new JSeparator());                         // a line between groups
        file.add(menuItem("Logout", null));
        file.add(menuItem("Exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK)));

        JMenu workspace = new JMenu("Workspace");
        workspace.add(menuItem("New Workspace", KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)));
        workspace.add(menuItem("Invite", null));
        workspace.addSeparator();
        workspace.add(menuItem("Mark Completed", null));
        workspace.add(menuItem("Peer Review", null));

        // View menu: a check-box item and a group of radio-button items
        JMenu view = new JMenu("View");
        JCheckBoxMenuItem autoRefresh = new JCheckBoxMenuItem("Auto-refresh chat", true);
        autoRefresh.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                chatPanel.setAutoRefresh(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        view.add(autoRefresh);
        view.addSeparator();

        String[] filterNames = { "All tasks", "Open tasks", "Claimed tasks", "Done tasks" };
        String[] filterValues = { null, Constants.TASK_OPEN, Constants.TASK_CLAIMED, Constants.TASK_DONE };
        ButtonGroup group = new ButtonGroup();              // only one radio item selected at a time
        for (int i = 0; i < filterNames.length; i++) {
            final String status = filterValues[i];          // "final" so the inner class can use it
            JRadioButtonMenuItem radio = new JRadioButtonMenuItem(filterNames[i], i == 0);
            radio.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    taskModel.setStatusFilter(status);
                }
            });
            group.add(radio);
            view.add(radio);
        }
        view.addSeparator();
        view.add(menuItem("Refresh", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0)));

        JMenu tools = new JMenu("Tools");
        tools.add(menuItem("Thread Tools", null));
        tools.add(menuItem("Archive Now", null));

        JMenu help = new JMenu("Help");
        help.add(menuItem("JDBC Info", null));
        help.add(menuItem("Memory", null));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(workspace);
        bar.add(view);
        bar.add(tools);
        bar.add(help);
        return bar;
    }

    private JPanel createToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(button("New Workspace"));
        bar.add(button("Invite"));
        bar.add(button("Mark Completed"));
        bar.add(button("Peer Review"));
        bar.add(button("Refresh"));
        bar.add(new JLabel("     "));
        bar.add(titleLabel);
        return bar;
    }

    private JScrollPane createTree() {
        // right-click (pop-up) menu for the tree
        final JPopupMenu popup = new JPopupMenu();
        popup.add(menuItem("Invite", null));
        popup.add(menuItem("Mark Completed", null));
        popup.add(menuItem("Peer Review", null));

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                tree.setSelectionPath(path);
                selectNode((DefaultMutableTreeNode) path.getLastPathComponent());
                if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {   // right button
                    popup.show(tree, e.getX(), e.getY());
                } else if (e.getClickCount() == 2) {                              // double click
                    tabs.setSelectedIndex(1);                                     // open Chat tab
                }
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setPreferredSize(new Dimension(250, 0));
        return scroll;
    }

    private JTabbedPane createTabs() {
        // ---- Tasks tab ----
        JPanel taskButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        taskButtons.add(button("Add Task"));
        taskButtons.add(button("Claim"));
        taskButtons.add(button("Mark Done"));
        taskButtons.add(onlyMineBox);
        onlyMineBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                taskModel.setOnlyStudent(onlyMineBox.isSelected() ? student.getId() : 0);
            }
        });

        final JPopupMenu taskPopup = new JPopupMenu();
        taskPopup.add(menuItem("Claim", null));
        taskPopup.add(menuItem("Mark Done", null));
        taskTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                int row = taskTable.rowAtPoint(e.getPoint());
                if (row >= 0 && (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3)) {
                    taskTable.setRowSelectionInterval(row, row);
                    taskPopup.show(taskTable, e.getX(), e.getY());
                }
            }
        });

        JPanel tasksTab = new JPanel(new BorderLayout());
        tasksTab.add(taskButtons, BorderLayout.NORTH);
        tasksTab.add(new JScrollPane(taskTable), BorderLayout.CENTER);

        tabs.addTab("Tasks", tasksTab);
        tabs.addTab("Chat", chatPanel);
        tabs.addTab("Leaderboard", new JScrollPane(new JTable(leaderModel)));
        tabs.addTab("My Profile", new JScrollPane(profilePanel));
        return tabs;
    }

    // =================================================================
    // LOADING DATA
    // =================================================================
    private void refreshAll() {
        refreshTree();
        refreshLeaderboard();
        profilePanel.refresh();
    }

    /** Rebuilds the tree: root -> workspaces -> members. */
    private void refreshTree() {
        List<Workspace> list;
        try {
            list = workspaceDAO.findForStudent(student.getId());
        } catch (AppException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
            return;
        }
        treeRoot.removeAllChildren();
        Workspace again = null;
        for (Workspace w : list) {
            DefaultMutableTreeNode wsNode = new DefaultMutableTreeNode(w);
            for (Workspace.Member m : w.getMembers()) {
                wsNode.add(new DefaultMutableTreeNode(m));
            }
            treeRoot.add(wsNode);
            if (current != null && current.getId() == w.getId()) {
                again = w;                               // keep the same selection
            }
        }
        ((DefaultTreeModel) tree.getModel()).reload();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        statusBar.setText(" " + list.size() + " workspace(s), " + (countNodes(treeRoot) - 1)
                          + " items in the tree");
        selectWorkspace(again);
    }

    /** RECURSION: a node counts 1 plus all the nodes below each of its children. */
    private int countNodes(TreeNode node) {
        int total = 1;
        for (int i = 0; i < node.getChildCount(); i++) {
            total = total + countNodes(node.getChildAt(i));
        }
        return total;
    }

    /** A click on a member selects that member's workspace. */
    private void selectNode(DefaultMutableTreeNode node) {
        Object value = node.getUserObject();
        if (value instanceof Workspace.Member) {
            value = ((DefaultMutableTreeNode) node.getParent()).getUserObject();
        }
        if (value instanceof Workspace) {
            selectWorkspace((Workspace) value);
        }
    }

    private void selectWorkspace(Workspace w) {
        boolean changed = (w == null || current == null || w.getId() != current.getId());
        current = w;
        if (w == null) {
            titleLabel.setText("Select a workspace");
            taskModel.setTasks(new ArrayList<Task>());
        } else {
            titleLabel.setText(w.getName() + "  (" + w.getMembers().size() + "/" + w.getMaxMembers()
                    + " members, " + (w.isArchived() ? "archived" : w.getStatus().toLowerCase()) + ")");
            loadTasks();
        }
        if (changed) {
            chatPanel.showWorkspace(w);
        }
    }

    private void loadTasks() {
        try {
            taskModel.setTasks(taskDAO.findByWorkspace(current.getId()));
        } catch (AppException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    private void refreshLeaderboard() {
        try {
            leaderModel.setRowCount(0);
            int rank = 1;
            for (Util.Pair<Student, Integer> row : taskDAO.leaderboard()) {
                Student s = row.getFirst();
                leaderModel.addRow(new Object[] { rank, s.getName(), row.getSecond(),
                                                  Util.ratingText(s.getRating()) });
                rank++;
            }
        } catch (AppException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    // =================================================================
    // ACTIONS (they "throw" problems; MenuHandler catches them in one place)
    // =================================================================
    private Workspace needWorkspace() throws AppException {
        if (current == null) {
            throw new AppException("First select a workspace in the tree on the left.");
        }
        return current;
    }

    private Task needSelectedTask() throws AppException {
        int row = taskTable.getSelectedRow();
        if (row < 0) {
            throw new AppException("First select a task in the table.");
        }
        return taskModel.getTaskAt(row);
    }

    /** FR-6: a small form inside a JOptionPane (GridLayout with 2 columns). */
    private void newWorkspace() throws AppException {
        JTextField name = new JTextField(20);
        JComboBox<String> type = new JComboBox<String>(Constants.WORKSPACE_TYPES);
        JComboBox<Integer> size = new JComboBox<Integer>(new Integer[] { 2, 3, 4, 5, 6, 8, 10 });
        size.setSelectedItem(4);
        JComboBox<Object> supervisor = new JComboBox<Object>();
        supervisor.addItem("-- none --");
        for (Faculty f : studentDAO.findAllFaculty()) {
            supervisor.addItem(f);
        }
        JTextArea description = new JTextArea(3, 20);

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Name:"));          form.add(name);
        form.add(new JLabel("Type:"));          form.add(type);
        form.add(new JLabel("Max members:"));   form.add(size);
        form.add(new JLabel("Supervisor:"));    form.add(supervisor);
        form.add(new JLabel("Description:"));   form.add(new JScrollPane(description));

        int answer = JOptionPane.showConfirmDialog(this, form, "New Workspace", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        Object chosen = supervisor.getSelectedItem();
        int facultyId = (chosen instanceof Faculty) ? ((Faculty) chosen).getId() : 0;
        int max = (Integer) size.getSelectedItem();          // cast Object -> Integer, then unboxing

        current = workspaceDAO.create(student, name.getText(), (String) type.getSelectedItem(),
                                      description.getText().trim(), max, facultyId);
        refreshTree();
        statusBar.setText(" Workspace created. You are the leader.");
    }

    private void invite() throws AppException {
        Workspace w = needWorkspace();
        String email = JOptionPane.showInputDialog(this, "Email of the student to invite:");
        if (email == null) {
            return;                                     // Cancel pressed
        }
        Student added = workspaceDAO.invite(w, student, email);
        refreshTree();
        JOptionPane.showMessageDialog(this, added.getName() + " joined " + w.getName() + ".");
    }

    private void markCompleted() throws AppException {
        Workspace w = needWorkspace();
        if (JOptionPane.showConfirmDialog(this, "Mark '" + w.getName() + "' as completed?\n"
                + "After this, members can review each other.") != JOptionPane.YES_OPTION) {
            return;
        }
        workspaceDAO.markCompleted(w, student);
        refreshTree();
    }

    private void peerReview() throws AppException {
        Workspace w = needWorkspace();
        if (!w.isCompleted()) {
            throw new AppException("Peer review opens after the leader marks the workspace completed.");
        }
        List<Workspace.Member> teammates = new ArrayList<Workspace.Member>();
        for (Workspace.Member m : w.getMembers()) {
            if (m.getStudentId() != student.getId()) {
                teammates.add(m);
            }
        }
        PeerReviewDialog dialog = new PeerReviewDialog(this, w, student, teammates,
                studentDAO.reviewedIds(w.getId(), student.getId()));
        if (!dialog.hasSomeoneToReview()) {
            throw new AppException("You have already reviewed everyone in this workspace.");
        }
        dialog.setVisible(true);                         // modal: waits here until closed
        refreshAll();
    }

    private void addTask() throws AppException {
        Workspace w = needWorkspace();
        JTextField title = new JTextField(20);
        JTextField description = new JTextField(20);
        Object[] form = { "Title:", title, "Description:", description };
        if (JOptionPane.showConfirmDialog(this, form, "New Task", JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION) {
            taskDAO.add(w, student, title.getText(), description.getText());
            loadTasks();
        }
    }

    private void claimTask() throws AppException {
        needWorkspace().checkEditable();
        Task t = needSelectedTask();
        try {
            taskDAO.claim(t, student);
            statusBar.setText(" You claimed: " + t.getTitle());
        } finally {
            loadTasks();       // finally: refresh the table whether the claim worked or not
        }
    }

    private void markDone() throws AppException {
        Task t = needSelectedTask();
        taskDAO.complete(t, student);
        loadTasks();
        refreshLeaderboard();
    }

    private void importTasks() throws AppException, IOException {
        Workspace w = needWorkspace();
        JFileChooser chooser = new JFileChooser(new File("resources"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            int added = taskDAO.importTasks(w, student, Util.importTasks(chooser.getSelectedFile()));
            loadTasks();
            JOptionPane.showMessageDialog(this, added + " task(s) imported.");
        }
    }

    private void exportTasks() throws AppException, IOException {
        Workspace w = needWorkspace();
        File file = Util.exportTasks(w.getName(), taskModel.getAllTasks());
        JOptionPane.showMessageDialog(this, "Tasks saved to:\n" + file.getAbsolutePath());
    }

    private void archiveNow() {
        int count = Main.getArchiveThread().archiveNow(0);    // 0 days = archive every completed one
        refreshTree();
        JOptionPane.showMessageDialog(this, count + " completed workspace(s) archived.");
    }

    /** Garbage collection demo: memory used before and after System.gc(). */
    private void showMemory() {
        Runtime rt = Runtime.getRuntime();
        long before = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        for (int i = 0; i < 100000; i++) {
            String temp = "object " + i;     // 'temp' exists only inside one loop pass;
            temp.length();                   // afterwards nobody refers to it -> garbage
        }
        System.gc();                         // only a REQUEST - the JVM decides when to collect
        long after = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        showText("Memory", "Used before System.gc(): " + before + " MB\n"
                + "Used after  System.gc(): " + after + " MB\n"
                + "Person objects created  : " + Person.getObjectsCreated() + "\n\n"
                + "Java frees an object automatically when no variable refers to it any more.");
    }

    private void showText(String title, String text) {
        JTextArea area = new JTextArea(text, 18, 70);
        area.setEditable(false);
        area.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(area), title, JOptionPane.PLAIN_MESSAGE);
    }

    private void logout() {
        chatPanel.stopThreads();
        Main.logout();
        dispose();
        new LoginFrame().setVisible(true);
    }

    private void exitApp() {
        if (JOptionPane.showConfirmDialog(this, "Exit CollabSphere?") == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

}

// =================================================================
/**
 * MVC: this is the MODEL of the task JTable. It holds the data; the JTable
 * (the VIEW) asks it "how many rows?" and "what is in cell (row, col)?".
 * After the data changes, fireTableDataChanged() tells the view to redraw.
 * The faculty window (LoginFrame.java) reuses it.
 */
class TaskTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private final String[] columns = { "ID", "Title", "Description", "Status", "Assigned to" };
    private final List<Task> allTasks = new ArrayList<Task>();
    private final List<Task> shown = new ArrayList<Task>();
    private String statusFilter = null;        // null = show every status
    private int onlyStudentId = 0;             // 0 = everybody's tasks

    public void setTasks(List<Task> tasks) {
        allTasks.clear();
        allTasks.addAll(tasks);
        applyFilter();
    }

    public void setStatusFilter(String status) {
        statusFilter = status;
        applyFilter();
    }

    public void setOnlyStudent(int studentId) {
        onlyStudentId = studentId;
        applyFilter();
    }

    private void applyFilter() {
        shown.clear();
        for (Task t : allTasks) {
            boolean statusOk = (statusFilter == null || statusFilter.equals(t.getStatus()));
            boolean personOk = (onlyStudentId == 0 || onlyStudentId == t.getAssigneeId());
            if (statusOk && personOk) {
                shown.add(t);
            }
        }
        fireTableDataChanged();               // tell the JTable to redraw
    }

    public Task getTaskAt(int row)      { return shown.get(row); }
    public List<Task> getAllTasks()     { return allTasks; }

    @Override public int getRowCount()            { return shown.size(); }
    @Override public int getColumnCount()         { return columns.length; }
    @Override public String getColumnName(int c)  { return columns[c]; }

    @Override
    public Object getValueAt(int row, int col) {
        Task t = shown.get(row);
        switch (col) {
            case 0:  return t.getId();
            case 1:  return t.getTitle();
            case 2:  return t.getDescription();
            case 3:  return t.getStatus();
            default: return t.getAssigneeName();
        }
    }
}

/**
 * WHAT THIS CLASS DOES
 *   "My Profile" tab: name, skills, bio, Global Rating, feedback received
 *   (FR-4, FR-5).
 *
 * SYLLABUS
 *   Unit IV - GRID BAG LAYOUT: the most flexible layout. Every component is
 *             placed in a cell (gridx, gridy) described by GridBagConstraints.
 *   Unit V  - JList (multiple selection), JTextField, JTextArea, JLabel, JButton.
 */
class ProfilePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Student student;
    private final StudentDAO studentDAO = new StudentDAO();

    private final JTextField nameField = new JTextField(25);
    private final JLabel ratingLabel = new JLabel();
    private final JList<String> skillList = new JList<String>(Constants.SKILL_OPTIONS);
    private final JTextArea bioArea = new JTextArea(4, 25);
    private final DefaultListModel<String> feedbackModel = new DefaultListModel<String>();

    private int row = 0;     // next free row in the grid

    public ProfilePanel(Student student) {
        this.student = student;
        setLayout(new GridBagLayout());

        skillList.setVisibleRowCount(6);         // Ctrl+click selects several skills
        bioArea.setLineWrap(true);
        JButton saveButton = new JButton("Save profile");

        addRow("Name:", nameField);
        addRow("Email:", new JLabel(student.getEmail()));
        addRow("Global Rating:", ratingLabel);
        addRow("Skills (Ctrl+click):", new JScrollPane(skillList));
        addRow("Bio:", new JScrollPane(bioArea));
        addRow("", saveButton);
        addRow("Feedback received:", new JScrollPane(new JList<String>(feedbackModel)));

        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
            }
        });
        refresh();
    }

    /** Puts a label in column 0 and a component in column 1 of the next row. */
    private void addRow(String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridy = row;

        c.gridx = 0;
        add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1.0;                              // column 1 takes the extra width
        c.fill = GridBagConstraints.HORIZONTAL;
        add(field, c);
        row++;
    }

    /** Reloads the student from MySQL (rating changes after reviews) and fills the form. */
    public void refresh() {
        try {
            Student fresh = studentDAO.findById(student.getId());
            student.setRating(fresh.getRating());
            student.setReviewCount(fresh.getReviewCount());

            nameField.setText(student.getName());
            ratingLabel.setText(student.getRatingText());
            bioArea.setText(student.getBio());

            // select the student's skills in the JList
            List<Integer> selected = new ArrayList<Integer>();
            for (int i = 0; i < Constants.SKILL_OPTIONS.length; i++) {
                if (student.getSkills().contains(Constants.SKILL_OPTIONS[i])) {
                    selected.add(i);
                }
            }
            int[] indexes = new int[selected.size()];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = selected.get(i);          // auto-unboxing Integer -> int
            }
            skillList.setSelectedIndices(indexes);

            feedbackModel.clear();
            for (String line : studentDAO.feedbackFor(student.getId())) {
                feedbackModel.addElement(line);
            }
        } catch (AppException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    private void save() {
        try {
            student.setName(Util.checkName(nameField.getText()));
            List<String> skills = skillList.getSelectedValuesList();
            student.setSkillsFromText(String.join(",", skills));
            student.setBio(bioArea.getText().trim());
            studentDAO.updateProfile(student);
            JOptionPane.showMessageDialog(this, "Profile saved.");
        } catch (AppException.InvalidInput e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        } catch (AppException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

/**
 * WHAT THIS CLASS DOES
 *   Peer review form (EIR-4): choose a teammate, click 1-5 stars, write
 *   optional feedback (FR-21 .. FR-23).
 *
 * SYLLABUS
 *   Unit V  - JComponent: StarRating (bottom of this file) is our OWN Swing
 *             component - it extends JComponent and draws itself in
 *             paintComponent().
 *   Unit IV - MOUSE events with MouseAdapter (click and hover on the stars),
 *             GridBagLayout, JComboBox.
 */
class PeerReviewDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JComboBox<Workspace.Member> teammateBox = new JComboBox<Workspace.Member>();
    private final StarRating stars = new StarRating();
    private final JTextArea feedbackArea = new JTextArea(4, 25);

    public PeerReviewDialog(JFrame owner, final Workspace workspace, final Student me,
                            List<Workspace.Member> teammates, Set<Integer> alreadyReviewed) {
        super(owner, "Peer Review - " + workspace.getName(), true);   // true = modal

        for (Workspace.Member m : teammates) {
            if (!alreadyReviewed.contains(m.getStudentId())) {
                teammateBox.addItem(m);
            }
        }

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Teammate:"), c);
        c.gridx = 1;              form.add(teammateBox, c);
        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Stars:"), c);
        c.gridx = 1;              form.add(stars, c);
        c.gridx = 0; c.gridy = 2; form.add(new JLabel("Feedback:"), c);
        c.gridx = 1;              form.add(new JScrollPane(feedbackArea), c);

        JButton submit = new JButton("Submit");
        JButton close = new JButton("Close");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(submit);
        buttons.add(close);

        submit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Workspace.Member target = (Workspace.Member) teammateBox.getSelectedItem();
                if (target == null) {
                    return;
                }
                try {
                    new StudentDAO().submitReview(workspace.getId(), me.getId(), target.getStudentId(),
                            target.getStudentName(), stars.getRating(), feedbackArea.getText());
                    JOptionPane.showMessageDialog(PeerReviewDialog.this,
                            "Review saved. " + target.getStudentName() + "'s rating is updated.");
                    teammateBox.removeItem(target);
                    stars.setRating(0);
                    feedbackArea.setText("");
                    if (teammateBox.getItemCount() == 0) {
                        dispose();                       // everybody reviewed
                    }
                } catch (AppException.InvalidInput ex) {
                    JOptionPane.showMessageDialog(PeerReviewDialog.this, ex.getMessage());
                } catch (AppException ex) {
                    JOptionPane.showMessageDialog(PeerReviewDialog.this, ex.getMessage(), "Error",
                                                  JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    /** true if at least one teammate still needs a review. */
    public boolean hasSomeoneToReview() {
        return teammateBox.getItemCount() > 0;
    }

    // =================================================================
    /** A custom Swing component: five clickable stars. */
    static class StarRating extends JComponent {

        private static final long serialVersionUID = 1L;
        private static final int SIZE = 30;          // width of one star
        private int rating = 0;                      // 0 = nothing chosen yet
        private int hover = 0;                       // star under the mouse

        StarRating() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setRating(e.getX() / SIZE + 1);  // which star was clicked
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    hover = e.getX() / SIZE + 1;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = 0;
                    repaint();
                }
            };
            addMouseListener(mouse);          // clicks / exit
            addMouseMotionListener(mouse);    // movement
        }

        int getRating() {
            return rating;
        }

        void setRating(int r) {
            rating = Math.min(r, Constants.MAX_RATING);
            repaint();                        // asks Swing to call paintComponent again
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(SIZE * Constants.MAX_RATING, SIZE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int shown = (hover > 0) ? hover : rating;
            for (int i = 0; i < Constants.MAX_RATING; i++) {
                Polygon star = makeStar(i * SIZE + SIZE / 2, SIZE / 2);
                g.setColor(i < shown ? new Color(245, 180, 0) : Color.LIGHT_GRAY);
                g.fillPolygon(star);
                g.setColor(Color.DARK_GRAY);
                g.drawPolygon(star);
            }
        }

        /** 10 corner points around (cx, cy): outer and inner points alternate. */
        private Polygon makeStar(int cx, int cy) {
            Polygon p = new Polygon();
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI / 2 + i * Math.PI / 5;
                int radius = (i % 2 == 0) ? 13 : 6;
                p.addPoint((int) (cx + radius * Math.cos(angle)),      // casting double -> int
                           (int) (cy - radius * Math.sin(angle)));
            }
            return p;
        }
    }
}
