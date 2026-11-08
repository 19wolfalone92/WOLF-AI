package ai.wolf.autopilot;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline-only chat transcript for the locally hosted model. No cloud sync. */
public final class ChatStore extends SQLiteOpenHelper {
    public static final class Message {
        public final String role, text;
        Message(String role, String text) {
            this.role = role; this.text = text;
        }
    }
    public ChatStore(Context context) { super(context, "wolf_chat.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "role TEXT NOT NULL, body TEXT NOT NULL, created INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int from, int to) {}

    public void add(String role, String body) {
        if (!("user".equals(role) || "assistant".equals(role))) throw new IllegalArgumentException("Invalid chat role");
        if (body == null || body.isEmpty()) return;
        ContentValues row = new ContentValues();
        row.put("role", role);
        row.put("body", body.substring(0, Math.min(body.length(), 6000)));
        row.put("created", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.insertOrThrow("messages", null, row);
        db.execSQL("DELETE FROM messages WHERE _id NOT IN (SELECT _id FROM messages ORDER BY _id DESC LIMIT 40)");
    }

    public List<Message> recent(int max) {
        if (max < 1 || max > 20) throw new IllegalArgumentException("Invalid history limit");
        List<Message> messages = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT role,body FROM messages ORDER BY _id DESC LIMIT " + max, null)) {
            while (c.moveToNext()) messages.add(new Message(c.getString(0), c.getString(1)));
        }
        Collections.reverse(messages);
        return messages;
    }

    /**
     * Only local chat text is exported, never a token or model binary.
     * The caller MUST encrypt this JSON before sharing it off-device.
     */
    public String exportJson() throws Exception {
        org.json.JSONObject obj = new org.json.JSONObject();
        obj.put("schema", 1);
        org.json.JSONArray messages = new org.json.JSONArray();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT role,body,created FROM messages ORDER BY _id ASC LIMIT 40", null)) {
            while (cursor.moveToNext()) {
                messages.put(new org.json.JSONObject()
                        .put("role", cursor.getString(0))
                        .put("body", cursor.getString(1))
                        .put("created", cursor.getLong(2)));
            }
        }
        obj.put("messages", messages);
        return obj.toString();
    }

    public static void validatePortable(String serialized) throws Exception {
        if (serialized == null || serialized.length() > 280_000)
            throw new IllegalArgumentException("Повреждённый либо слишком большой архив чата.");
        org.json.JSONObject root = new org.json.JSONObject(serialized);
        if (root.getInt("schema") != 1 || root.length() != 2)
            throw new IllegalArgumentException("Неизвестный формат архива чата.");
        org.json.JSONArray values = root.getJSONArray("messages");
        if (values.length() > 40)
            throw new IllegalArgumentException("В архиве более 40 сообщений.");
        for (int i = 0; i < values.length(); i++) {
            org.json.JSONObject item = values.getJSONObject(i);
            String role = item.getString("role");
            String body = item.getString("body");
            long created = item.getLong("created");
            if (item.length() != 3 || !("user".equals(role) || "assistant".equals(role)) ||
                    body.isEmpty() || body.length() > 6000 ||
                    created < 0 || created > 4_102_444_800_000L)
                throw new IllegalArgumentException("Недопустимое сообщение в архиве.");
        }
    }

    /** Replace chat transactionally. No messages are sent to the web. */
    public void importJson(String serialized) throws Exception {
        validatePortable(serialized);
        org.json.JSONArray rows = new org.json.JSONObject(serialized).getJSONArray("messages");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("messages", null, null);
            for (int i = 0; i < rows.length(); i++) {
                org.json.JSONObject row = rows.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put("role", row.getString("role"));
                values.put("body", row.getString("body"));
                values.put("created", row.getLong("created"));
                db.insertOrThrow("messages", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void clearHistory() { getWritableDatabase().delete("messages", null, null); }
}
