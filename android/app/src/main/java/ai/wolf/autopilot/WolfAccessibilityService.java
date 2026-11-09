package ai.wolf.autopilot;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Collections;

/**
 * Explicit, one-shot Android Accessibility actions on a foreground
 * third-party app. Permission must be manually granted in Android settings.
 * No background recording, screen dumps, text entry or arbitrary model tools.
 *
 * Never collects password fields, WebView content, screenshots or contacts.
 * This is an ASSISTED navigation bridge, not unrestricted device control.
 */
public final class WolfAccessibilityService extends AccessibilityService {
    private static volatile WolfAccessibilityService connected;
    private static final long LIFETIME_MS = 45000L;
    private static final int MAX_NODES = 450;
    private volatile Request pending;
    private volatile String lastResult = "Ожидание команды.";

    private final Handler main = new Handler(Looper.getMainLooper());

    static final class Request {
        final String type, target;
        final long expires;
        final List<String> labels;
        int step;
        long lastClickMillis;
        String lockedPackage;
        Request(String type, String target, long expires) {
            this.type = type; this.target = target; this.expires = expires;
            this.labels = "sequence".equals(type) ?
                    BrowserFlowPlan.parse(target) : Collections.singletonList(target);
        }
    }

    public static boolean available() { return connected != null; }

    /** All commands originate in WOLF's explicit confirmation dialog. */
    public static String arm(String type, String target) {
        WolfAccessibilityService svc = connected;
        if (svc == null) return "Служба Android недоступна: включи WOLF в настройках специальных возможностей.";
        if (!(type.equals("click") || type.equals("scroll") || type.equals("sequence") || type.equals("inspect")))
            throw new IllegalArgumentException("Недопустимое действие.");
        if (type.equals("click") && !AssistivePolicy.allowedLabel(target))
            throw new IllegalArgumentException("Опасная или слишком общая кнопка заблокирована.");
        if (svc.pending != null && SystemClock.elapsedRealtime() < svc.pending.expires)
            return "Уже есть ожидающая команда. Отмени её перед новой.";
        svc.lastResult = "Ожидаю другое приложение";
        svc.pending = new Request(type, target,
                SystemClock.elapsedRealtime() + LIFETIME_MS);
        return "Команда активирована на 45 секунд. Перейди вручную в нужное приложение. " +
                ("sequence".equals(type) ?
                        "WOLF выполнит максимум четыре заранее указанных безопасных шага " +
                                "только в одном приложении, при однозначных совпадениях." :
                        "WOLF выполнит не более одного подтверждённого действия.") +
                " Никаких других операций без нового согласия.";
    }

    public static String status() {
        WolfAccessibilityService svc = connected;
        if (svc == null) return "Служба WOLF отключена в Android.";
        Request request = svc.pending;
        if (request != null && SystemClock.elapsedRealtime() > request.expires) {
            svc.pending = null;
            svc.lastResult = "Время ожидания закончилось — действие не выполнялось.";
        }
        return svc.lastResult;
    }

    public static void cancel() {
        WolfAccessibilityService svc = connected;
        if (svc != null) {
            svc.pending = null;
            svc.lastResult = "Команда отменена.";
        }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = this;
        pending = null; // never recover old commands after service restart
        lastResult = "Служба активна. Ожидаю подтверждённую команду.";
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null) attemptPending();
    }

    private void attemptPending() {
        Request request = pending;
        if (request == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now > request.expires) {
            pending = null;
            lastResult = "Время ожидания истекло. Маршрут остановлен.";
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String app = root.getPackageName().toString();
        if (request.lockedPackage != null &&
                !request.lockedPackage.equals(app)) {
            pending = null;
            lastResult = "Маршрут прерван: приложение сменилось.";
            return;
        }
        if (!AssistivePolicy.allowedPackage(app, getPackageName())) return;
        if ("inspect".equals(request.type)) {
            pending = null;
            lastResult = "🔎 Доступные безопасные элементы в " + app + ":\\n" +
                    listNavigationLabels(root);
            return;
        }
        if ("scroll".equals(request.type)) {
            pending = null; // Never repeat a scroll event.
            AccessibilityNodeInfo scroll = findScrollable(root);
            lastResult = scroll != null &&
                    scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    ? "✅ Одно пролистывание выполнено в " + app
                    : "Прокрутка не выполнена: подходящий элемент не найден.";
            return;
        }
        if (request.step > 0 && now - request.lastClickMillis < 800L) return;
        String label = request.labels.get(request.step);
        AccessibilityNodeInfo hit = findUniqueClickable(root, label);
        if (hit == null) {
            if ("click".equals(request.type)) {
                pending = null;
                lastResult = "Не выполнено: кнопка не найдена или неоднозначна.";
            }
            // In a preapproved route the page may still be loading; stay
            // pending only until 45-second TTL, never widen to another app.
            return;
        }
        if (request.lockedPackage == null) request.lockedPackage = app;
        // Advance before triggering events to guarantee at-most-once taps.
        request.step++;
        request.lastClickMillis = now;
        if (!hit.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            pending = null;
            lastResult = "Android не разрешил нажатие. Маршрут остановлен.";
            return;
        }
        if (request.step >= request.labels.size()) {
            pending = null;
            lastResult = "✅ Выполнено " + request.step + " подтверждённых нажатий в " +
                    app + ". Проверь результат вручную.";
        } else {
            lastResult = "✅ Шаг " + request.step + "/" + request.labels.size() +
                    " в " + app + ". Ищу следующий безопасный элемент...";
            // A single delayed attempt covers pages that do not emit a
            // content-change event. Other events may also drive progression.
            main.postDelayed(() -> {
                if (pending == request) attemptPending();
            }, 900L);
        }
    }

    /**
     * Only accessible visible clickable labels, no EditText contents,
     * screenshots, passwords or full-page transcription. Volatile RAM only.
     */
    private static String listNavigationLabels(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        queue.add(root);
        int nodes = 0;
        while(!queue.isEmpty() && nodes++<MAX_NODES && seen.size()<16) {
            AccessibilityNodeInfo node=queue.removeFirst();
            if(node.isClickable() && node.isEnabled() &&
                    node.isVisibleToUser() && !node.isPassword() &&
                    (node.getClassName()==null ||
                        !node.getClassName().toString().contains("EditText"))) {
                CharSequence label=node.getContentDescription();
                if(label==null || label.length()==0)label=node.getText();
                String display=label==null?"":label.toString().trim();
                if(display.length()<=70 && AssistivePolicy.allowedLabel(display))
                    seen.add(display.replace('\\n',' '));
            }
            for(int i=0;i<node.getChildCount();i++) {
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)queue.addLast(child);
            }
        }
        if(seen.isEmpty())return "Нет доступных безопасных кнопок. " +
                "Приложение может скрывать элементы от Accessibility.";
        StringBuilder out=new StringBuilder();
        for(String value:seen)out.append("• ").append(value).append("\\n");
        return out.toString();
    }

    private static AccessibilityNodeInfo findUniqueClickable(
            AccessibilityNodeInfo root, String text) {
        String expected = AssistivePolicy.normalize(text);
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        AccessibilityNodeInfo candidate = null;
        int seen = 0;
        while (!queue.isEmpty() && seen++ < MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isVisibleToUser() && node.isEnabled() && !node.isPassword()) {
                CharSequence label = node.getText();
                CharSequence description = node.getContentDescription();
                if (expected.equals(AssistivePolicy.normalize(label)) ||
                        expected.equals(AssistivePolicy.normalize(description))) {
                    AccessibilityNodeInfo target = nearestClickable(node);
                    if (target != null) {
                        if (candidate != null && candidate != target) return null;
                        candidate = target;
                    }
                }
            }
            for (int j=0; j<node.getChildCount(); j++) {
                AccessibilityNodeInfo child = node.getChild(j);
                if (child != null) queue.add(child);
            }
        }
        return seen <= MAX_NODES ? candidate : null;
    }

    private static AccessibilityNodeInfo nearestClickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int depth=0; depth<4 && current!=null; depth++) {
            if (current.isClickable() && current.isEnabled() &&
                    current.isVisibleToUser() && !current.isPassword())
                return current;
            current = current.getParent();
        }
        return null;
    }

    private static AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int seen=0;
        while (!queue.isEmpty() && seen++ < MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isScrollable() && node.isVisibleToUser() && node.isEnabled())
                return node;
            for(int j=0; j<node.getChildCount();j++) {
                AccessibilityNodeInfo child=node.getChild(j);
                if(child!=null) queue.add(child);
            }
        }
        return null;
    }

    @Override public void onInterrupt() {
        pending = null;
    }
    @Override public void onDestroy() {
        pending = null;
        if (connected == this) connected = null;
        super.onDestroy();
    }
}
