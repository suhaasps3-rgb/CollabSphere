=====================================================================
  CollabSphere - Unified Campus Collaborative Network
  Java 17 Swing + JDBC + MySQL 8   (Eclipse project, 8 Java files)
=====================================================================

1. SETUP IN ECLIPSE
   a) Install MySQL 8 and make sure the MySQL service is running.
   b) Download MySQL Connector/J (the JDBC driver):
        https://dev.mysql.com/downloads/connector/j/  -> "Platform Independent" ZIP
      Unzip it; you need  mysql-connector-j-x.x.x.jar
   c) Eclipse: File > Import > General > Existing Projects into Workspace
      > choose the CollabSphere folder > Finish.
   d) Right-click the project > Build Path > Add External Archives...
      > choose mysql-connector-j-x.x.x.jar
      (This puts the driver on the CLASSPATH - the list of places where Java
       looks for classes. Without it you get "MySQL driver not found".)
   e) Open db.properties and type YOUR MySQL user and password.
   f) Right-click src/com/collabsphere/Main.java > Run As > Java Application.
      The database "collabsphere" and its 7 tables are created automatically.

2. PROJECT STRUCTURE  - 8 files, read them in this order
   util/AppException.java  all our error types (nested exception classes)
   util/Util.java          Constants interface, validation, password hash,
                           star helpers (recursion), generics, file export/import
   model/Model.java        the objects: Person, Student, Faculty, Workspace
                           (+Member, Leader, ArchivedWorkspace), Task, Message
   dao/Database.java       ALL JDBC code: connect, create DB + 7 tables,
                           BaseDAO<T>, StudentDAO, WorkspaceDAO, TaskDAO, MessageDAO
   ui/LoginFrame.java      login/register window + FacultyFrame
   ui/MainFrame.java       student window + TaskTableModel, ProfilePanel,
                           PeerReviewDialog
   ui/ChatPanel.java       chat tab (its threads) + ThreadDemoDialog
   Main.java               starts everything + background archive thread

   Why fewer files? A .java file may contain several classes but only ONE
   "public" class (named like the file). So:
     - model and dao classes are STATIC NESTED classes, used from other files
       with imports such as  import com.collabsphere.model.Model.Student;
     - helper windows (FacultyFrame, ProfilePanel, ...) are package-private
       classes sitting in the same file as the window that uses them.

3. HOW TO EXPLAIN ONE FEATURE END-TO-END (example: claiming a task)
   1. Student selects a task and clicks "Claim"           (ui/MainFrame.java)
   2. The button sends an ActionEvent to MenuHandler      (delegation event model)
   3. MenuHandler calls claimTask() -> taskDAO.claim()    (-> TaskDAO in dao/Database.java)
   4. TaskDAO.claim() is synchronized and runs
        UPDATE Task SET claimed_by=? ... WHERE task_id=? AND claimed_by IS NULL
      If 0 rows changed, someone else was faster ->
        throw new AppException.TaskAlreadyClaimed(...)
   5. MenuHandler catches AppException and shows the message (FR-18);
      the finally block reloads the task table either way.

4. DEMO SCRIPT
   1. Register three students (e.g. ravi@college.edu / abc123, sita@..., arjun@...).
   2. Log in as Ravi > New Workspace > Invite sita@college.edu.
      (Choose max members 2 and invite a third student -> "workspace is full".)
   3. Tasks tab: Add Task, or File > Import Tasks > resources/sample_tasks.csv.
      Select a task > Claim > Mark Done. Right-click a row for the pop-up menu.
   4. Chat tab: send messages. Run the program a second time and log in as
      Sita - messages appear within 3 seconds. "Export" saves a .txt file.
   5. Tools > Thread Tools: list threads, life-cycle demo, two students claim.
   6. Workspace > Mark Completed, then Workspace > Peer Review (1-5 stars).
      The reviewed student's Global Rating changes (My Profile tab).
   7. Tools > Archive Now: completed workspaces become read-only "[ARCHIVED]".
   8. Logout, choose Faculty: faculty@college.edu / faculty123.

5. TROUBLESHOOTING
   "MySQL driver not found"  -> step 1d.
   "Cannot set up MySQL ..." -> MySQL not running, or wrong password in db.properties.
   Start again from empty    -> in MySQL run  DROP DATABASE collabsphere;

NOTE: the SRS also mentions an R Shiny dashboard. R is not part of the Java
syllabus, so the same faculty figures are shown in FacultyFrame instead.
