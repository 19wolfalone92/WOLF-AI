package ai.wolf.autopilot;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Explicit opt-in learning: user-entered facts and labelled routing examples.
 * No shadow memory, third-party cloud sync, or secret transmission.
 * This does NOT alter llama.cpp/Qwen neural weights.
 */
public final class LearningStore extends SQLiteOpenHelper {
    public static final class Fact {
        public final String text;
        public final long created;
        Fact(String text, long created) { this.text = text; this.created = created; }
    }

    public LearningStore(Context context) {
        super(context, "wolf_learning.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE facts (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "body TEXT NOT NULL, created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE examples (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "body TEXT NOT NULL, project TEXT NOT NULL, created INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    private static void validateContent(String value, int max) {
        if (value == null || value.trim().length() < 5 || value.length() > max)
            throw new IllegalArgumentException("Пример должен содержать 5–" + max + " символов.");
        if (GithubBacklog.suspectedSecret(value))
            throw new IllegalArgumentException("Не сохраняйте пароли и API-ключи в памяти модели.");
    }

    public void teach(String fact) {
        validateContent(fact, 600);
        ContentValues values = new ContentValues();
        values.put("body", fact.trim());
        values.put("created", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.insertOrThrow("facts", null, values);
        db.execSQL("DELETE FROM facts WHERE _id NOT IN " +
                "(SELECT _id FROM facts ORDER BY _id DESC LIMIT 100)");
    }

    public void teachRoute(String example, String project) {
        validateContent(example, 450);
        if (!CommandParser.validProject(project))
            throw new IllegalArgumentException("Недопустимый проект.");
        ContentValues row = new ContentValues();
        row.put("body", example.trim());
        row.put("project", project);
        row.put("created", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.insertOrThrow("examples", null, row);
        db.execSQL("DELETE FROM examples WHERE _id NOT IN " +
                "(SELECT _id FROM examples ORDER BY _id DESC LIMIT 120)");
    }

    private static Set<String> tokens(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ");
        Set<String> words = new HashSet<>(Arrays.asList(lower.trim().split("\\s+")));
        words.remove("");
        // Common words are not strong evidence of a task's topic.
        words.removeAll(Arrays.asList("сделай", "проверь", "можно", "нужно", "привет",
                "пожалуйста", "помоги", "что", "когда", "чтобы", "make", "help", "check"));
        return words;
    }

    private static double score(String query, String candidate) {
        Set<String> a = tokens(query);
        Set<String> b = tokens(candidate);
        if (a.isEmpty() || b.isEmpty()) return 0;
        int overlap = 0;
        for (String word : a) if (b.contains(word)) overlap++;
        // Normalized overlap rewards short precise matches, not long generic notes.
        return (double) overlap / Math.sqrt(a.size() * b.size());
    }

    public List<Fact> related(String query, int count) {
        if (count < 1 || count > 5) throw new IllegalArgumentException("Invalid memory limit.");
        final class Scored {
            Fact fact;
            double score;
            Scored(Fact fact, double score) {this.fact=fact; this.score=score;}
        }
        List<Scored> matched = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT body, created FROM facts ORDER BY _id DESC LIMIT 100", null)) {
            while (c.moveToNext()) {
                Fact fact = new Fact(c.getString(0), c.getLong(1));
                double relevance = score(query, fact.text);
                if (relevance >= 0.18) matched.add(new Scored(fact, relevance));
            }
        }
        matched.sort((a, b) -> Double.compare(b.score, a.score));
        List<Fact> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, matched.size()); i++) out.add(matched.get(i).fact);
        return out;
    }

    /**
     * Few-shot on-device task routing. A route is used ONLY when a labelled
     * example has > 75% normalized word overlap. Otherwise use built-in rules.
     */
    public String learnedProject(String text) {
        String best = null;
        double maximum = 0.75;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT body, project FROM examples ORDER BY _id DESC LIMIT 120", null)) {
            while (c.moveToNext()) {
                String target = c.getString(1);
                if (!CommandParser.validProject(target)) continue;
                double similarity = score(text, c.getString(0));
                if (similarity > maximum) { best = target; maximum = similarity; }
            }
        }
        return best;
    }

    public int factCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM facts", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int exampleCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM examples", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("facts", null, null);
        db.delete("examples", null, null);
    }
}
