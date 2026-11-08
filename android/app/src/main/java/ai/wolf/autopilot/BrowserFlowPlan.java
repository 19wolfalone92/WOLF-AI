package ai.wolf.autopilot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded, pre-reviewed navigation sequence. No forms or generated code. */
public final class BrowserFlowPlan {
    private BrowserFlowPlan() {}
    public static List<String> parse(String userPlan) {
        if (userPlan == null || userPlan.length() > 320)
            throw new IllegalArgumentException("План слишком длинный.");
        String[] fields = userPlan.split("[>\\n]", -1);
        if (fields.length < 1 || fields.length > 4)
            throw new IllegalArgumentException("Разрешено от 1 до 4 шагов.");
        ArrayList<String> plan = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for(String field : fields) {
            String label=field.trim().replaceAll("^[«\"]|[»\"]$", "");
            if (!BrowserActionPolicy.allowedLabel(label))
                throw new IllegalArgumentException("Недопустимый шаг: " + label);
            if (!seen.add(AssistivePolicy.normalize(label)))
                throw new IllegalArgumentException("Один пункт повторяется. Проверь маршрут.");
            plan.add(label);
        }
        return plan;
    }
}
