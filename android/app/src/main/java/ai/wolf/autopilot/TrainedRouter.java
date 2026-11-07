package ai.wolf.autopilot;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inference for a tiny CPU-trained routing model that is embedded in this APK
 * at GitHub Actions build time. This is NOT an LLM nor a guarantee of accuracy.
 */
public final class TrainedRouter {
    private static final Pattern WORD = Pattern.compile("[a-zа-яё0-9_]{2,}",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final JSONObject model;
    private final List<String> labels = new ArrayList<>();
    private final int vocabularySize;
    private final int documentCount;

    private TrainedRouter(JSONObject model) throws Exception {
        this.model = model;
        if (model.getInt("schema") != 1 ||
                !"multinomial-naive-bayes".equals(model.getString("type")))
            throw new IllegalStateException("Неизвестная модель классификатора.");
        JSONArray arr = model.getJSONArray("labels");
        for (int i = 0; i < arr.length(); i++) {
            String label = arr.getString(i);
            if (!CommandParser.validProject(label))
                throw new IllegalStateException("Неизвестный проект в обученной модели.");
            labels.add(label);
        }
        if (labels.size() != 6) throw new IllegalStateException("Неполная модель маршрутизации.");
        vocabularySize = model.getInt("vocab_size");
        JSONObject docs = model.getJSONObject("documents");
        int count = 0;
        for (String label : labels) count += docs.getInt(label);
        documentCount = count;
        if (vocabularySize < 10 || documentCount < 20)
            throw new IllegalStateException("Недостаточно примеров для модели.");
    }

    public static TrainedRouter load(Context context) throws Exception {
        try (InputStream input = context.getAssets().open("task_router_model.json");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) {
                if (out.size() + n > 400000)
                    throw new IllegalStateException("Модель маршрутизации слишком большая.");
                out.write(buffer, 0, n);
            }
            JSONObject parsed = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            return new TrainedRouter(parsed);
        }
    }

    private static List<String> tokenize(String text) {
        Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        List<String> tokens = new ArrayList<>();
        while (matcher.find() && tokens.size() < 100) tokens.add(matcher.group());
        return tokens;
    }

    public String predict(String text) throws Exception {
        if (text == null || text.trim().length() < 5) return null;
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) return null;
        JSONObject docs = model.getJSONObject("documents");
        JSONObject totals = model.getJSONObject("totals");
        JSONObject counts = model.getJSONObject("counts");

        double highest = Double.NEGATIVE_INFINITY;
        double second = Double.NEGATIVE_INFINITY;
        String best = null;
        for (String label : labels) {
            JSONObject labelCounts = counts.getJSONObject(label);
            int denom = totals.getInt(label) + vocabularySize;
            double value = Math.log((docs.getInt(label) + 1.0) /
                    (documentCount + labels.size()));
            for (String token : tokens) {
                value += Math.log((labelCounts.optInt(token, 0) + 1.0) / denom);
            }
            if (value > highest) {
                second = highest;
                highest = value;
                best = label;
            } else if (value > second) {
                second = value;
            }
        }
        // Abstain on ambiguous or out-of-domain prompts. Explicit user labels
        // and hard-coded project references remain authoritative.
        if (best == null || highest - second < 1.25) return null;
        return best;
    }
}
