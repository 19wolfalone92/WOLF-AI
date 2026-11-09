package ai.wolf.autopilot;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent local task inbox plus real remote CI state.
 * Migration keeps all v1 tasks; a saved draft is never represented as completed.
 */
public final class TaskStore extends SQLiteOpenHelper {
    public static final String DRAFT = "draft";
    public static final String SUBMITTED = "submitted";
    public static final String RUNNING = "running";
    public static final String SUCCESS = "success";
    public static final String FAILURE = "failure";
    public static final String AGENT_NONE = "none";
    public static final String AGENT_RESERVED = "reserved";
    public static final String AGENT_UNKNOWN = "dispatch_unknown";
    public static final class Task {
        public final long id;
        public final String text, project, mode, uid, status;
        public final long created, runId, issueNumber, agentRunId, agentSubmittedAt;
        public final String agentStatus;
        public Task(long id, String text, String project, String mode,
                    long created, String uid, String status, long runId, long issueNumber,
                    String agentStatus, long agentRunId, long agentSubmittedAt) {
            this.id = id; this.text = text; this.project = project; this.mode = mode;
            this.created = created; this.uid = uid; this.status = status; this.runId = runId;
            this.issueNumber = issueNumber;
            this.agentStatus = agentStatus;
            this.agentRunId = agentRunId;
            this.agentSubmittedAt = agentSubmittedAt;
        }
    }

    public TaskStore(Context context) {
        super(context, "wolf_tasks.db", null, 4);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "body TEXT NOT NULL, project TEXT NOT NULL, " +
                "mode TEXT NOT NULL, created INTEGER NOT NULL, " +
                "task_uid TEXT NOT NULL UNIQUE, status TEXT NOT NULL DEFAULT 'draft'," +
                "run_id INTEGER NOT NULL DEFAULT 0," +
                "issue_number INTEGER NOT NULL DEFAULT 0," +
                "agent_status TEXT NOT NULL DEFAULT 'none'," +
                "agent_run_id INTEGER NOT NULL DEFAULT 0," +
                "agent_submitted_at INTEGER NOT NULL DEFAULT 0)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN task_uid TEXT");
            db.execSQL("ALTER TABLE tasks ADD COLUMN status TEXT NOT NULL DEFAULT 'draft'");
            db.execSQL("ALTER TABLE tasks ADD COLUMN run_id INTEGER NOT NULL DEFAULT 0");
            // Existing v1 tasks are local drafts; their old text was never dispatched.
            try (Cursor c = db.rawQuery("SELECT _id FROM tasks", null)) {
                while (c.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("task_uid", newUid());
                    db.update("tasks", values, "_id=?", new String[]{String.valueOf(c.getLong(0))});
                }
            }
            db.execSQL("CREATE UNIQUE INDEX tasks_uid_idx ON tasks(task_uid)");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN issue_number INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN agent_status TEXT NOT NULL DEFAULT 'none'");
            db.execSQL("ALTER TABLE tasks ADD COLUMN agent_run_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN agent_submitted_at INTEGER NOT NULL DEFAULT 0");
        }
    }

    public static String newUid() {
        return "w" + UUID.randomUUID().toString().replace("-", "");
    }

    public long add(String body, String project, String mode) {
        if (body == null || body.trim().length() < 3 || body.length() > 600)
            throw new IllegalArgumentException("Задание должно содержать 3–600 символов.");
        if (SensitiveText.containsCredential(body) || GithubBacklog.suspectedSecret(body))
            throw new IllegalArgumentException("Задание содержит возможные токены или пароли. " +
                    "Нельзя сохранять/публиковать секреты в заданиях GitHub.");
        if (!CommandParser.validProject(project) || !(mode.equals("audit") || mode.equals("test")))
            throw new IllegalArgumentException("Недопустимый проект или режим.");
        ContentValues values = new ContentValues();
        values.put("body", body.trim());
        values.put("project", project);
        values.put("mode", mode);
        values.put("created", System.currentTimeMillis());
        values.put("task_uid", newUid());
        values.put("status", DRAFT);
        long id = getWritableDatabase().insertOrThrow("tasks", null, values);
        getWritableDatabase().execSQL("DELETE FROM tasks WHERE _id NOT IN " +
                "(SELECT _id FROM tasks ORDER BY _id DESC LIMIT 100)");
        return id;
    }

    public List<Task> recent() {
        ArrayList<Task> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT _id,body,project,mode,created,task_uid,status,run_id,issue_number," +
                "agent_status,agent_run_id,agent_submitted_at " +
                        "FROM tasks ORDER BY _id DESC LIMIT 30", null)) {
            while (cursor.moveToNext()) {
                result.add(new Task(cursor.getLong(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3), cursor.getLong(4),
                        cursor.getString(5), cursor.getString(6), cursor.getLong(7), cursor.getLong(8),
                        cursor.getString(9), cursor.getLong(10), cursor.getLong(11)));
            }
        }
        return result;
    }

    public void submitted(long id, String uid) {
        ContentValues values = new ContentValues();
        values.put("status", SUBMITTED);
        getWritableDatabase().update("tasks", values, "_id=? AND task_uid=?",
                new String[]{String.valueOf(id), uid});
    }

    public int reconcile(List<GithubApi.RunInfo> runs) {
        int updated = 0;
        SQLiteDatabase db = getWritableDatabase();
        for (Task task : recent()) {
            if (DRAFT.equals(task.status)) continue;
            for (GithubApi.RunInfo run : runs) {
                if (run.agent || !run.fromDispatch || !run.name.contains(task.uid)) continue;
                if (!run.name.contains(task.project) || !run.name.contains(task.mode)) continue;
                // Never associate a job that predates task creation by >120 seconds.
                if (run.createdEpochMillis > 0 && run.createdEpochMillis + 120000L < task.created) continue;
                String state = run.state();
                if (task.runId == run.id && state.equals(task.status)) break;
                ContentValues values = new ContentValues();
                values.put("status", state);
                values.put("run_id", run.id);
                updated += db.update("tasks", values, "_id=? AND task_uid=?",
                        new String[]{String.valueOf(task.id), task.uid});
                break;
            }
        }
        return updated;
    }

    /**
     * Persist a reservation BEFORE any irreversible workflow_dispatch request.
     * Only one unknown/in-progress attempt can exist per local task.
     */
    public boolean reserveAgent(long id, String uid) {
        ContentValues values = new ContentValues();
        values.put("agent_status", AGENT_RESERVED);
        values.put("agent_run_id", 0);
        values.put("agent_submitted_at", System.currentTimeMillis());
        int updated = getWritableDatabase().update("tasks", values,
                "_id=? AND task_uid=? AND agent_status IN ('none','success','failure')",
                new String[]{String.valueOf(id), uid});
        return updated == 1;
    }

    public void acceptedAgent(long id, String uid) {
        ContentValues values = new ContentValues();
        values.put("agent_status", SUBMITTED);
        getWritableDatabase().update("tasks", values,
                "_id=? AND task_uid=? AND agent_status='reserved'",
                new String[]{String.valueOf(id), uid});
    }

    public void unknownAgent(long id, String uid) {
        ContentValues values = new ContentValues();
        values.put("agent_status", AGENT_UNKNOWN);
        getWritableDatabase().update("tasks", values,
                "_id=? AND task_uid=? AND agent_status='reserved'",
                new String[]{String.valueOf(id), uid});
    }

    public int reconcileAgent(List<GithubApi.RunInfo> runs) {
        int updated = 0;
        SQLiteDatabase db = getWritableDatabase();
        for (Task task : recent()) {
            if (AGENT_NONE.equals(task.agentStatus)) continue;
            for (GithubApi.RunInfo run : runs) {
                if (!run.agent || !run.fromDispatch || !run.name.contains(task.uid) ||
                        !run.name.contains(task.project) || !run.name.contains(task.mode))
                    continue;
                // Reject preexisting jobs. Timestamps are authoritative when supplied.
                if (run.createdEpochMillis <= 0 ||
                        run.createdEpochMillis + 5000L < task.agentSubmittedAt) continue;
                String next = run.state();
                if (run.id == task.agentRunId && next.equals(task.agentStatus)) break;
                ContentValues values = new ContentValues();
                values.put("agent_status", next);
                values.put("agent_run_id", run.id);
                updated += db.update("tasks", values, "_id=? AND task_uid=?",
                        new String[]{String.valueOf(task.id), task.uid});
                break;
            }
        }
        return updated;
    }

    public void markIssue(long id, String uid, long number) {
        if (number < 1) throw new IllegalArgumentException("Invalid GitHub issue number");
        ContentValues values = new ContentValues();
        values.put("issue_number", number);
        getWritableDatabase().update("tasks", values, "_id=? AND task_uid=? AND issue_number=0",
                new String[]{String.valueOf(id), uid});
    }

    /**
     * Portable task journal; records are data only. Import NEVER dispatches
     * or approves a workflow, Issue, TikTok posting or remote write.
     */
    public String exportJson() throws Exception {
        org.json.JSONObject root = new org.json.JSONObject().put("schema", 1);
        org.json.JSONArray values = new org.json.JSONArray();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT body,project,mode,created,task_uid,status,run_id,issue_number," +
                "agent_status,agent_run_id,agent_submitted_at " +
                "FROM tasks ORDER BY _id ASC LIMIT 100", null)) {
            while (cursor.moveToNext()) {
                org.json.JSONObject row = new org.json.JSONObject();
                row.put("body", cursor.getString(0));
                row.put("project", cursor.getString(1));
                row.put("mode", cursor.getString(2));
                row.put("created", cursor.getLong(3));
                row.put("uid", cursor.getString(4));
                row.put("status", cursor.getString(5));
                row.put("run_id", cursor.getLong(6));
                row.put("issue_number", cursor.getLong(7));
                row.put("agent_status", cursor.getString(8));
                row.put("agent_run_id", cursor.getLong(9));
                row.put("agent_submitted_at", cursor.getLong(10));
                values.put(row);
            }
        }
        root.put("tasks", values);
        return root.toString();
    }

    public static void validatePortable(String serialized) throws Exception {
        if (serialized == null || serialized.length() > 200_000)
            throw new IllegalArgumentException("Слишком большой архив заданий.");
        org.json.JSONObject root = new org.json.JSONObject(serialized);
        if (root.getInt("schema") != 1 || root.length() != 2)
            throw new IllegalArgumentException("Неизвестная версия архива заданий.");
        org.json.JSONArray tasks = root.getJSONArray("tasks");
        if (tasks.length() > 100)
            throw new IllegalArgumentException("Архив превышает 100 задач.");
        java.util.HashSet<String> unique = new java.util.HashSet<>();
        for (int i = 0; i < tasks.length(); i++) {
            org.json.JSONObject row = tasks.getJSONObject(i);
            String body = row.getString("body");
            String project = row.getString("project");
            String mode = row.getString("mode");
            String uid = row.getString("uid");
            String status = row.getString("status");
            String agent = row.getString("agent_status");
            if (row.length() != 11 ||
                    body.trim().length() < 3 || body.length() > 600 ||
                    GithubBacklog.suspectedSecret(body) ||
                    !CommandParser.validProject(project) ||
                    !("audit".equals(mode) || "test".equals(mode)) ||
                    !uid.matches("w[a-f0-9]{32}") || !unique.add(uid) ||
                    !status.matches("[a-z_]{3,30}") ||
                    !agent.matches("[a-z_]{3,30}"))
                throw new IllegalArgumentException("Некорректное задание в резервной копии.");
            long created = row.getLong("created");
            long run = row.getLong("run_id");
            long issue = row.getLong("issue_number");
            long agentRun = row.getLong("agent_run_id");
            long submittedAt = row.getLong("agent_submitted_at");
            if (created < 0 || created > 4_102_444_800_000L ||
                    run < 0 || issue < 0 || agentRun < 0 ||
                    submittedAt < 0 || submittedAt > 4_102_444_800_000L)
                throw new IllegalArgumentException("Неверный ID или дата задания.");
        }
    }

    public void importJson(String serialized) throws Exception {
        validatePortable(serialized);
        org.json.JSONArray tasks = new org.json.JSONObject(serialized).getJSONArray("tasks");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("tasks", null, null);
            for (int i = 0; i < tasks.length(); i++) {
                org.json.JSONObject row = tasks.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put("body", row.getString("body"));
                values.put("project", row.getString("project"));
                values.put("mode", row.getString("mode"));
                values.put("created", row.getLong("created"));
                values.put("task_uid", row.getString("uid"));
                values.put("status", row.getString("status"));
                values.put("run_id", row.getLong("run_id"));
                values.put("issue_number", row.getLong("issue_number"));
                values.put("agent_status", row.getString("agent_status"));
                values.put("agent_run_id", row.getLong("agent_run_id"));
                values.put("agent_submitted_at", row.getLong("agent_submitted_at"));
                db.insertOrThrow("tasks", null, values);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void remove(long id) {
        getWritableDatabase().delete("tasks", "_id=?", new String[]{String.valueOf(id)});
    }
}
