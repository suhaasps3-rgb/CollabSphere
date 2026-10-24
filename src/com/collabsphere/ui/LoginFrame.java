package com.collabsphere.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import com.collabsphere.Main;
import com.collabsphere.dao.Database.MessageDAO;
import com.collabsphere.dao.Database.StudentDAO;
import com.collabsphere.dao.Database.TaskDAO;
import com.collabsphere.dao.Database.WorkspaceDAO;
import com.collabsphere.model.Model.Faculty;
import com.collabsphere.model.Model.Person;
import com.collabsphere.model.Model.Student;
import com.collabsphere.model.Model.Task;
import com.collabsphere.model.Model.Workspace;
import com.collabsphere.util.AppException;
import com.collabsphere.util.Util.Constants;
import com.collabsphere.util.Util;

/* =====================================================================
 * FILE: LoginFrame.java  - the two windows that are NOT for students
 *   class LoginFrame    (public)  login + register screen, opened first
 *   class FacultyFrame            faculty monitoring window
 * Only LoginFrame is public; FacultyFrame is "package-private" (no access
 * modifier), so it can only be used inside package com.collabsphere.ui.
 * A .java file may hold many classes but only ONE public class, whose name
 * must match the file name.
 * ===================================================================== */

/**
 * WHAT THIS CLASS DOES
 *   The first window: Login and Register (EIR-1).
 *
 * SYLLABUS
 *   Unit IV - CARD LAYOUT  : two "cards" (login, register) in the same place;
 *                            only one is shown at a time.
 *           - GRID LAYOUT  : the forms (label | field rows).
 *           - FLOW / BORDER LAYOUT : header and whole window.
 *           - Events: ActionListener (buttons), KeyAdapter (Enter key),
 *             WindowAdapter (closing the window) - ADAPTER classes.
 *   Unit V  - JFrame, JLabel, JTextField, JPasswordField, JButton,
 *             JRadioButton (+ ButtonGroup), JCheckBox.
 */
public class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final StudentDAO studentDAO = new StudentDAO();

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    // login card
    private final JTextField emailField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JRadioButton studentRadio = new JRadioButton("Student", true);
    private final JRadioButton facultyRadio = new JRadioButton("Faculty");
    private final JCheckBox rememberBox = new JCheckBox("Remember my email");

    // register card
    private final JTextField regName = new JTextField(20);
    private final JTextField regEmail = new JTextField(20);
    private final JPasswordField regPassword = new JPasswordField(20);
    private final JPasswordField regConfirm = new JPasswordField(20);

    public LoginFrame() {
        super(Constants.APP_NAME + " - Login");
        setLayout(new BorderLayout());
        add(createHeader(), BorderLayout.NORTH);

        cardPanel.add(createLoginCard(), "login");
        cardPanel.add(createRegisterCard(), "register");
        cardPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        add(cardPanel, BorderLayout.CENTER);

        JLabel hint = new JLabel("Demo faculty: faculty@college.edu / faculty123", JLabel.CENTER);
        hint.setForeground(Color.GRAY);
        add(hint, BorderLayout.SOUTH);

        // WindowAdapter: we only need windowClosing, the adapter fills in the other methods
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        fillRememberedLogin();
        pack();
        setLocationRelativeTo(null);       // centre of the screen
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JLabel title = new JLabel(Constants.APP_NAME + " - Campus Collaboration");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        header.add(title);
        return header;
    }

    private JPanel createLoginCard() {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));   // 0 rows = as many as needed

        ButtonGroup roleGroup = new ButtonGroup();    // only one of the radios can be selected
        roleGroup.add(studentRadio);
        roleGroup.add(facultyRadio);
        JPanel roles = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        roles.add(studentRadio);
        roles.add(facultyRadio);

        JButton loginButton = new JButton("Login");
        JButton toRegister = new JButton("New student? Register");

        form.add(new JLabel("Email:"));      form.add(emailField);
        form.add(new JLabel("Password:"));   form.add(passwordField);
        form.add(new JLabel("Login as:"));   form.add(roles);
        form.add(new JLabel(""));            form.add(rememberBox);
        form.add(loginButton);               form.add(toRegister);

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                login();
            }
        });
        toRegister.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cards.show(cardPanel, "register");         // switch card
            }
        });
        // press Enter in the password box to log in
        passwordField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    login();
                }
            }
        });
        return form;
    }

    private JPanel createRegisterCard() {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        JButton registerButton = new JButton("Create account");
        JButton back = new JButton("Back to login");

        form.add(new JLabel("Full name:"));                      form.add(regName);
        form.add(new JLabel("College email:"));                  form.add(regEmail);
        form.add(new JLabel("Password (6+, letter & digit):"));  form.add(regPassword);
        form.add(new JLabel("Confirm password:"));               form.add(regConfirm);
        form.add(registerButton);                                form.add(back);

        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                register();
            }
        });
        back.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cards.show(cardPanel, "login");
            }
        });
        return form;
    }

    private void register() {
        try {
            Student s = studentDAO.register(regName.getText(), regEmail.getText(),
                    new String(regPassword.getPassword()), new String(regConfirm.getPassword()));
            JOptionPane.showMessageDialog(this, "Welcome " + s.getName() + "! Now log in.");
            emailField.setText(s.getEmail());
            studentRadio.setSelected(true);
            cards.show(cardPanel, "login");
        } catch (AppException.InvalidInput e) {          // unchecked - bad form input
            JOptionPane.showMessageDialog(this, e.getMessage(), "Check input", JOptionPane.WARNING_MESSAGE);
        } catch (AppException e) {                       // checked - duplicate email / database
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void login() {
        String role = studentRadio.isSelected() ? Constants.ROLE_STUDENT : Constants.ROLE_FACULTY;
        try {
            Person user = studentDAO.login(emailField.getText(), new String(passwordField.getPassword()), role);
            Main.setCurrentUser(user);

            if (rememberBox.isSelected()) {
                Util.rememberLogin(user.getEmail(), role);
            } else {
                Util.forgetLogin();
            }

            dispose();                                  // close the login window
            // the real type of the object decides which window opens
            if (user instanceof Student) {
                new MainFrame((Student) user).setVisible(true);
            } else {
                new FacultyFrame((Faculty) user).setVisible(true);
            }
        } catch (AppException.InvalidInput e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Check input", JOptionPane.WARNING_MESSAGE);
        } catch (AppException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Login failed", JOptionPane.ERROR_MESSAGE);
            passwordField.setText("");
        }
    }

    private void fillRememberedLogin() {
        String[] saved = Util.readRememberedLogin();     // {email, role} or null
        if (saved != null) {
            emailField.setText(saved[0]);
            rememberBox.setSelected(true);
            facultyRadio.setSelected(Constants.ROLE_FACULTY.equals(saved[1]));
            studentRadio.setSelected(!facultyRadio.isSelected());
        }
    }
}

/**
 * WHAT THIS CLASS DOES
 *   The faculty monitoring window (FR-25 .. FR-32). It shows:
 *     - 4 number cards: active workspaces, students, pending tasks, done tasks
 *     - a table of all workspaces with completion % and pass/at-risk prediction
 *     - the tasks of the selected workspace
 *     - star-rating distributions and chat messages per day (text bar charts)
 *   The combo box filters everything by workspace (FR-32).
 *   Only a Faculty login opens this window (NFR-13).
 *
 * SYLLABUS
 *   Unit IV - GridLayout (the 4 cards), ItemListener (combo box filter),
 *             Map (messages per day), WindowAdapter.
 *   Unit V  - JTable with DefaultTableModel, and MVC re-use of TaskTableModel.
 */
class FacultyFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final WorkspaceDAO workspaceDAO = new WorkspaceDAO();
    private final StudentDAO studentDAO = new StudentDAO();
    private final TaskDAO taskDAO = new TaskDAO();
    private final MessageDAO messageDAO = new MessageDAO();

    private List<Workspace> workspaces = new ArrayList<Workspace>();

    private final JLabel activeCard = bigNumber();
    private final JLabel studentCard = bigNumber();
    private final JLabel pendingCard = bigNumber();
    private final JLabel doneCard = bigNumber();

    private final JComboBox<Object> filterBox = new JComboBox<Object>();
    private final DefaultTableModel workspaceModel = new DefaultTableModel(new String[] {
            "ID", "Workspace", "Type", "Status", "Members", "Pending", "Done", "Completion", "Prediction" }, 0);
    private final TaskTableModel taskModel = new TaskTableModel();
    private final JTextArea ratingsText = textArea();
    private final JTextArea chatText = textArea();

    public FacultyFrame(Faculty faculty) {
        super(Constants.APP_NAME + " - " + faculty.getWelcomeTitle());

        // ---- 4 number cards in one row (GridLayout 1 x 4) ----
        JPanel cards = new JPanel(new GridLayout(1, 4, 10, 10));
        cards.add(card("Active workspaces", activeCard));
        cards.add(card("Students", studentCard));
        cards.add(card("Pending tasks", pendingCard));
        cards.add(card("Completed tasks", doneCard));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterRow.add(new JLabel("Show workspace:"));
        filterRow.add(filterBox);
        filterBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    showData();
                }
            }
        });

        JPanel top = new JPanel(new BorderLayout());
        top.add(cards, BorderLayout.CENTER);
        top.add(filterRow, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Workspaces", new JScrollPane(new JTable(workspaceModel)));
        tabs.addTab("Tasks", new JScrollPane(new JTable(taskModel)));
        tabs.addTab("Ratings", new JScrollPane(ratingsText));
        tabs.addTab("Chat activity", new JScrollPane(chatText));

        setJMenuBar(createMenuBar());
        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        setSize(1000, 650);
        setLocationRelativeTo(null);
        loadData();
    }

    private static JLabel bigNumber() {
        JLabel l = new JLabel("0", JLabel.CENTER);
        l.setFont(new Font("SansSerif", Font.BOLD, 28));
        return l;
    }

    private static JTextArea textArea() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setFont(new Font("Monospaced", Font.PLAIN, 13));
        return a;
    }

    private JPanel card(String caption, JLabel number) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEtchedBorder());
        p.add(number, BorderLayout.CENTER);
        p.add(new JLabel(caption, JLabel.CENTER), BorderLayout.SOUTH);
        return p;
    }

    private JMenuBar createMenuBar() {
        JMenu file = new JMenu("File");
        JMenuItem refresh = new JMenuItem("Refresh");
        JMenuItem report = new JMenuItem("Export report");
        JMenuItem logout = new JMenuItem("Logout");
        file.add(refresh);
        file.add(report);
        file.addSeparator();
        file.add(logout);

        refresh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadData();
            }
        });
        report.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportReport();
            }
        });
        logout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Main.logout();
                dispose();
                new LoginFrame().setVisible(true);
            }
        });

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        return bar;
    }

    /** Reads everything from MySQL and refills the filter combo box. */
    private void loadData() {
        try {
            workspaces = workspaceDAO.findAllWithStats();
            activeCard.setText(String.valueOf(workspaceDAO.countActive()));          // FR-26
            studentCard.setText(String.valueOf(studentDAO.countStudents()));
        } catch (AppException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
            return;
        }
        filterBox.removeAllItems();            // (this fires ItemEvents; showData() handles them)
        filterBox.addItem("All workspaces");
        for (Workspace w : workspaces) {
            filterBox.addItem(w);
        }
        showData();
    }

    /** Fills the tables/text for the workspace chosen in the combo box. */
    private void showData() {
        Object choice = filterBox.getSelectedItem();
        Workspace chosen = (choice instanceof Workspace) ? (Workspace) choice : null;

        int pending = 0;
        int done = 0;
        workspaceModel.setRowCount(0);
        for (Workspace w : workspaces) {
            if (chosen != null && w.getId() != chosen.getId()) {
                continue;
            }
            Workspace.Stats s = w.getStats();
            int total = s.getPendingTasks() + s.getDoneTasks();
            int percent = Util.percent(s.getDoneTasks(), total);
            pending += s.getPendingTasks();
            done += s.getDoneTasks();
            workspaceModel.addRow(new Object[] { w.getId(), w.getName(), w.getType(),
                    w.isArchived() ? "ARCHIVED" : w.getStatus(), s.getMembers(), s.getPendingTasks(),
                    s.getDoneTasks(), percent + "% of 100%", predict(total, percent) });
        }
        pendingCard.setText(String.valueOf(pending));       // FR-27
        doneCard.setText(String.valueOf(done));

        try {
            List<Task> tasks = (chosen == null) ? new ArrayList<Task>() : taskDAO.findByWorkspace(chosen.getId());
            taskModel.setTasks(tasks);
            ratingsText.setText(ratingsReport());
            chatText.setText(chatReport(chosen));
        } catch (AppException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    /** FR-31: simple rule - 50% or more of the tasks done means "likely to pass". */
    private String predict(int totalTasks, int percent) {
        if (totalTasks == 0) {
            return "No tasks yet";
        }
        return (percent >= 50) ? "Likely PASS" : "At risk";
    }

    /** FR-28 and FR-29 as text bar charts (Util.bar uses recursion). */
    private String ratingsReport() throws AppException {
        int[] global = studentDAO.starCounts(true);
        int[] peer = studentDAO.starCounts(false);
        StringBuilder sb = new StringBuilder("STUDENTS' GLOBAL RATING (rounded)\n");
        for (int stars = 5; stars >= 1; stars--) {
            sb.append(stars).append(" stars | ").append(Util.bar('#', Math.min(global[stars], 40)))
              .append(' ').append(global[stars]).append('\n');
        }
        sb.append("\nALL PEER RATINGS GIVEN (spot unfair 1-star or 5-star patterns)\n");
        for (int stars = 5; stars >= 1; stars--) {
            sb.append(stars).append(" stars | ").append(Util.bar('#', Math.min(peer[stars], 40)))
              .append(' ').append(peer[stars]).append('\n');
        }
        return sb.toString();
    }

    /** FR-30: messages per day (one workspace or all). */
    private String chatReport(Workspace chosen) throws AppException {
        Map<String, Integer> perDay = messageDAO.messagesPerDay(chosen == null ? 0 : chosen.getId());
        StringBuilder sb = new StringBuilder("CHAT MESSAGES PER DAY\n\n");
        for (Map.Entry<String, Integer> entry : perDay.entrySet()) {
            int count = entry.getValue();
            sb.append(entry.getKey()).append(" | ").append(Util.bar('#', Math.min(count, 50)))
              .append(' ').append(count).append('\n');
        }
        Integer busiest = Util.max(new ArrayList<Integer>(perDay.values()));   // generic method
        sb.append(busiest == null ? "No messages yet." : "\nBusiest day: " + busiest + " messages");
        return sb.toString();
    }

    private void exportReport() {
        StringBuilder sb = new StringBuilder("CollabSphere faculty report\n\n");
        for (int r = 0; r < workspaceModel.getRowCount(); r++) {
            for (int c = 0; c < workspaceModel.getColumnCount(); c++) {
                sb.append(workspaceModel.getColumnName(c)).append(": ")
                  .append(workspaceModel.getValueAt(r, c)).append("   ");
            }
            sb.append('\n');
        }
        sb.append('\n').append(ratingsText.getText()).append('\n').append(chatText.getText());
        try {
            File file = Util.saveReport(sb.toString());
            JOptionPane.showMessageDialog(this, "Report saved to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save: " + e.getMessage());
        }
    }
}
