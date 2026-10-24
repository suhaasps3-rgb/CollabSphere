package com.collabsphere.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.collabsphere.Main;
import com.collabsphere.dao.Database.MessageDAO;
import com.collabsphere.dao.Database.TaskDAO;
import com.collabsphere.model.Model.Message;
import com.collabsphere.model.Model.Student;
import com.collabsphere.model.Model.Task;
import com.collabsphere.model.Model.Workspace;
import com.collabsphere.util.AppException;
import com.collabsphere.util.Util.Constants;
import com.collabsphere.util.Util;

/* =====================================================================
 * FILE: ChatPanel.java  - everything about THREADS on the screen
 *   class ChatPanel        (public)  chat tab: sender + refresh threads
 *   class ThreadDemoDialog           Tools > Thread Tools: life cycle demo,
 *                                    thread list, two students claiming
 * ===================================================================== */

/**
 * WHAT THIS CLASS DOES
 *   The chat tab (EIR-3): message area, text box, Send and Export buttons.
 *
 * HOW IT WORKS - three threads cooperate:
 *   1. Swing thread    : reacts to the Send button and puts the message in
 *                        the "outbox" list. It never waits for MySQL, so the
 *                        window never freezes (FR-13, NFR-6).
 *   2. SenderThread    : WAITS until the outbox has a message, then saves it
 *                        to MySQL.  (wait() / notify() = inter-thread
 *                        communication, producer-consumer)
 *   3. RefreshThread   : every 3 seconds asks MySQL for new messages and
 *                        hands them to the Swing thread to display.
 *
 * SYLLABUS
 *   Unit I   - INNER classes (SenderThread, RefreshThread) - they can use the
 *              fields of ChatPanel directly.
 *   Unit III - creating threads (extends Thread), daemon threads, thread
 *              group, SYNCHRONIZED methods/blocks, wait() and notify().
 *   Unit IV  - KeyAdapter (Enter key sends; live character counter),
 *              java.io export (Util.exportChat), LinkedList.
 */
public class ChatPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Student me;
    private final MessageDAO messageDAO = new MessageDAO();

    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JButton exportButton = new JButton("Export");
    private final JLabel counterLabel = new JLabel("0/" + Constants.MAX_MESSAGE_LENGTH);
    private final JLabel statusLabel = new JLabel("Select a workspace on the left to chat.");

    // ----- data shared by several threads (always used inside synchronized) -----
    private Workspace workspace;                                   // which chat is open
    private final Message.History history = new Message.History(); // messages on screen
    private final LinkedList<Message> outbox = new LinkedList<Message>();  // waiting to be sent
    private boolean autoRefresh = true;

    private final SenderThread senderThread = new SenderThread();
    private final RefreshThread refreshThread = new RefreshThread();

    public ChatPanel(Student me) {
        this.me = me;
        setLayout(new BorderLayout(5, 5));

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 13));

        JPanel bottom = new JPanel(new BorderLayout(5, 5));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(counterLabel);
        buttons.add(sendButton);
        buttons.add(exportButton);
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        add(statusLabel, BorderLayout.NORTH);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // ---- events ----
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportChat();
            }
        });
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                counterLabel.setText(inputField.getText().length() + "/" + Constants.MAX_MESSAGE_LENGTH);
            }
        });

        setInputEnabled(false);
        senderThread.start();
        refreshThread.start();
    }

    // =================================================================
    // Called by MainFrame
    // =================================================================

    /** Shows the chat of another workspace. */
    public void showWorkspace(Workspace w) {
        synchronized (this) {
            workspace = w;
            history.clear();
        }
        chatArea.setText("");
        statusLabel.setText(w == null ? "Select a workspace on the left to chat." : "Chat: " + w.getName());
        setInputEnabled(w != null && !w.isArchived());
    }

    public synchronized void setAutoRefresh(boolean on) {
        autoRefresh = on;
    }

    public synchronized List<Message> getMessages() {
        return new LinkedList<Message>(history);
    }

    /** Stops both threads (on logout). interrupt() makes sleep()/wait() throw InterruptedException. */
    public void stopThreads() {
        senderThread.interrupt();
        refreshThread.interrupt();
    }

    // =================================================================
    // PRODUCER side: the Swing thread adds a message and wakes the sender
    // =================================================================
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > Constants.MAX_MESSAGE_LENGTH) {
            JOptionPane.showMessageDialog(this, "Message too long (max " + Constants.MAX_MESSAGE_LENGTH + ").");
            return;
        }
        synchronized (this) {
            if (workspace == null) {
                return;
            }
            outbox.add(new Message(workspace.getId(), me.getId(), text));
            notify();                      // wake up the SenderThread waiting in wait()
        }
        inputField.setText("");
        counterLabel.setText("0/" + Constants.MAX_MESSAGE_LENGTH);
    }

    // =================================================================
    // INNER CLASS 1 - CONSUMER thread: saves messages to MySQL
    // =================================================================
    private class SenderThread extends Thread {

        SenderThread() {
            super(Main.WORKERS, "Chat-Sender");
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Message next;
                    synchronized (ChatPanel.this) {
                        while (outbox.isEmpty()) {
                            ChatPanel.this.wait();          // sleep until notify() - state WAITING
                        }
                        next = outbox.removeFirst();
                    }
                    try {
                        messageDAO.send(next);              // database work OUTSIDE the lock
                    } catch (AppException e) {
                        showStatus("Message not sent: " + e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                // stopThreads() was called - leaving run() ends the thread
            }
        }
    }

    // =================================================================
    // INNER CLASS 2 - checks for new messages every 3 seconds
    // =================================================================
    private class RefreshThread extends Thread {

        RefreshThread() {
            super(Main.WORKERS, "Chat-Refresh");
            setDaemon(true);
            setPriority(Thread.NORM_PRIORITY);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    loadNewMessages();
                    Thread.sleep(Constants.CHAT_REFRESH_MS);   // state TIMED_WAITING
                }
            } catch (InterruptedException e) {
                // stopped
            }
        }

        private void loadNewMessages() {
            Workspace w;
            int lastId;
            synchronized (ChatPanel.this) {
                if (workspace == null || !autoRefresh) {
                    return;
                }
                w = workspace;
                lastId = history.lastId();
            }
            try {
                List<Message> fresh = messageDAO.findAfter(w.getId(), lastId);
                if (!fresh.isEmpty()) {
                    showMessages(w, fresh);
                }
            } catch (AppException e) {
                showStatus("Connection problem: " + e.getMessage());
            }
        }
    }

    /**
     * Only the Swing thread may change Swing components, so other threads use
     * SwingUtilities.invokeLater(...) to ask it to do the update.
     */
    private void showMessages(final Workspace w, final List<Message> fresh) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                synchronized (ChatPanel.this) {
                    if (workspace != w) {
                        return;                       // user switched to another chat meanwhile
                    }
                    for (Message m : fresh) {
                        if (m.getId() > history.lastId()) {
                            history.add(m);
                            chatArea.append(m.toChatLine() + "\n");
                        }
                    }
                }
                chatArea.setCaretPosition(chatArea.getDocument().getLength());   // scroll down
                statusLabel.setText("Chat: " + w.getName() + "  (" + history.size() + " messages)");
            }
        });
    }

    private void showStatus(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(text);
            }
        });
    }

    private void exportChat() {
        Workspace w;
        synchronized (this) {
            w = workspace;
        }
        if (w == null) {
            JOptionPane.showMessageDialog(this, "Select a workspace first.");
            return;
        }
        try {
            File file = Util.exportChat(w.getName(), getMessages());
            JOptionPane.showMessageDialog(this, "Chat saved to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save: " + e.getMessage());
        }
    }

    private void setInputEnabled(boolean on) {
        inputField.setEnabled(on);
        sendButton.setEnabled(on);
    }
}

/**
 * WHAT THIS CLASS DOES
 *   Tools > Thread Tools. Three buttons that SHOW multithreading at work:
 *     1. List threads        - every thread in our ThreadGroup with its
 *                              state, priority and daemon flag
 *     2. Life-cycle demo     - one thread passes through NEW, RUNNABLE,
 *                              TIMED_WAITING, WAITING, BLOCKED, TERMINATED
 *     3. Two students claim  - two threads claim the SAME task at the same
 *                              time; only one may win (SRS test case, FR-17)
 *
 * MULTITASKING vs MULTITHREADING
 *   Multitasking  : the operating system runs several PROGRAMS at once
 *                   (Eclipse, MySQL, a browser). Each has its own memory.
 *   Multithreading: ONE program runs several THREADS that share the same
 *                   memory (our Swing thread, chat threads, archive thread).
 *
 * SYLLABUS (Unit III) - thread life cycle, Runnable, join(), priorities,
 *   thread groups, daemon threads, synchronized, wait()/notify().
 */
class ThreadDemoDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextArea output = new JTextArea(22, 70);
    private final Workspace workspace;
    private final Student me;

    public ThreadDemoDialog(JFrame owner, Workspace workspace, Student me) {
        super(owner, "Thread Tools", false);
        this.workspace = workspace;
        this.me = me;
        output.setEditable(false);
        output.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JButton list = new JButton("List threads");
        JButton lifeCycle = new JButton("Life-cycle demo");
        JButton race = new JButton("Two students claim one task");
        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(list);
        buttons.add(lifeCycle);
        buttons.add(race);

        list.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listThreads();
            }
        });
        lifeCycle.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runInBackground(new Runnable() {
                    @Override
                    public void run() {
                        lifeCycleDemo();
                    }
                });
            }
        });
        race.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runInBackground(new Runnable() {
                    @Override
                    public void run() {
                        claimRaceDemo();
                    }
                });
            }
        });

        setLayout(new BorderLayout());
        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(owner);
        print("Multitasking  = many programs at once, each with its own memory.");
        print("Multithreading = many threads inside ONE program, sharing memory.\n");
        listThreads();
    }

    /** Demos sleep and join, so they run on their own thread - the window stays usable. */
    private void runInBackground(Runnable job) {
        output.setText("");
        new Thread(Main.WORKERS, job, "Demo-Runner").start();
    }

    /** Adds a line to the text area from ANY thread (via the Swing thread). */
    private void print(final String line) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                output.append(line + "\n");
            }
        });
    }

    // -----------------------------------------------------------------
    // 1. List all threads of our thread group
    // -----------------------------------------------------------------
    private void listThreads() {
        Thread[] threads = new Thread[Main.WORKERS.activeCount() + 5];
        int count = Main.WORKERS.enumerate(threads);         // fills the array
        print("Thread group '" + Main.WORKERS.getName() + "' has " + count + " live thread(s):");
        for (int i = 0; i < count; i++) {
            Thread t = threads[i];
            print(String.format("  %-16s state=%-13s priority=%d daemon=%b",
                    t.getName(), t.getState(), t.getPriority(), t.isDaemon()));
        }
        print("");
    }

    // -----------------------------------------------------------------
    // 2. Thread life cycle
    // -----------------------------------------------------------------
    private void lifeCycleDemo() {
        final Object lock = new Object();

        Thread sleeper = new Thread(Main.WORKERS, new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(600);                       // -> TIMED_WAITING
                } catch (InterruptedException ignored) { }
            }
        }, "Sleeper");

        Thread waiter = new Thread(Main.WORKERS, new Runnable() {
            @Override
            public void run() {
                synchronized (lock) {
                    try {
                        lock.wait();                         // -> WAITING until notify
                    } catch (InterruptedException ignored) { }
                }
            }
        }, "Waiter");

        Thread blocked = new Thread(Main.WORKERS, new Runnable() {
            @Override
            public void run() {
                synchronized (lock) {                        // -> BLOCKED while lock is taken
                    // got the lock, nothing else to do
                }
            }
        }, "Blocked");

        try {
            print("Sleeper created          : " + sleeper.getState());        // NEW
            sleeper.start();
            waiter.start();
            print("This demo thread runs    : " + Thread.currentThread().getState());  // RUNNABLE
            Thread.sleep(200);
            print("Sleeper inside sleep()   : " + sleeper.getState());        // TIMED_WAITING
            print("Waiter inside wait()     : " + waiter.getState());         // WAITING

            synchronized (lock) {                    // we hold the lock now...
                blocked.start();
                Thread.sleep(200);
                print("Blocked wants the lock   : " + blocked.getState());    // BLOCKED
                lock.notify();                       // wake the waiter (inter-thread communication)
            }                                        // ...lock released here

            sleeper.join();                          // join(): wait until the thread finishes
            waiter.join();
            blocked.join();
            print("All three after run()    : " + sleeper.getState() + ", "
                    + waiter.getState() + ", " + blocked.getState());       // TERMINATED
        } catch (InterruptedException e) {
            print("Demo interrupted");
        }
    }

    // -----------------------------------------------------------------
    // 3. Two threads claim the same task (FR-17 test)
    // -----------------------------------------------------------------
    private void claimRaceDemo() {
        if (workspace == null || workspace.isArchived()) {
            print("First select an active workspace in the main window.");
            return;
        }
        // the second "student" is a teammate if there is one, otherwise me again
        Student other = me;
        for (Workspace.Member m : workspace.getMembers()) {
            if (m.getStudentId() != me.getId()) {
                other = new Student(m.getStudentId(), m.getStudentName(), "", 0);
                break;
            }
        }
        final TaskDAO taskDAO = new TaskDAO();
        try {
            final Task task = taskDAO.add(workspace, me, "Race test task", "created by Thread Tools");
            print("Created task #" + task.getId() + ". Two threads now try to claim it...\n");

            Thread a = new Thread(Main.WORKERS, new ClaimJob(taskDAO, task, me), "Claimer-A");
            Thread b = new Thread(Main.WORKERS, new ClaimJob(taskDAO, task, other), "Claimer-B");
            a.setPriority(Thread.MAX_PRIORITY);
            b.setPriority(Thread.MAX_PRIORITY);
            a.start();
            b.start();
            a.join();
            b.join();
            print("\nOnly one thread could claim the task - the other got an error message.");
        } catch (AppException e) {
            print("Error: " + e.getMessage());
        } catch (InterruptedException e) {
            print("Interrupted");
        }
    }

    /** INNER class implementing Runnable: the job each claimer thread runs. */
    private class ClaimJob implements Runnable {
        private final TaskDAO taskDAO;
        private final Task task;
        private final Student student;

        ClaimJob(TaskDAO taskDAO, Task task, Student student) {
            this.taskDAO = taskDAO;
            this.task = task;
            this.student = student;
        }

        @Override
        public void run() {
            String who = Thread.currentThread().getName() + " (" + student.getName() + ")";
            try {
                taskDAO.claim(task, student);           // synchronized method
                print(who + ": SUCCESS - task claimed");
            } catch (AppException e) {
                print(who + ": REFUSED - " + e.getMessage());
            }
        }
    }
}
