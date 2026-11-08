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

    public void remove(long id) {
        getWritableDatabase().delete("tasks", "_id=?", new String[]{String.valueOf(id)});
    }
}
