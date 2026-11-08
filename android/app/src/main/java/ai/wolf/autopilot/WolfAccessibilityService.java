package ai.wolf.autopilot;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Locale;

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

    static final class Request {
        final String type, target;
        final long expires;
        Request(String type, String target, long expires) {
            this.type = type; this.target = target; this.expires = expires;
        }
    }

    public static boolean available() { return connected != null; }

    /** All commands originate in WOLF's explicit confirmation dialog. */
    public static String arm(String type, String target) {
        WolfAccessibilityService svc = connected;
        if (svc == null) return "Служба Android недоступна: включи WOLF в настройках специальных возможностей.";
        if (!(type.equals("click") || type.equals("scroll")))
            throw new IllegalArgumentException("Недопустимое действие.");
        if (type.equals("click") && !AssistivePolicy.allowedLabel(target))
            throw new IllegalArgumentException("Опасная или слишком общая кнопка заблокирована.");
        if (svc.pending != null && SystemClock.elapsedRealtime() < svc.pending.expires)
            return "Уже есть ожидающая команда. Отмени её перед новой.";
        svc.lastResult = "Ожидаю другое приложение";
        svc.pending = new Request(type, target,
                SystemClock.elapsedRealtime() + LIFETIME_MS);
        return "Команда активирована на 45 секунд. Перейди вручную в нужное приложение. " +
                "WOLF выполнит ровно одно действие, если найдёт однозначный элемент. " +
                "Никаких дальнейших действий без нового подтверждения.";
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
        Request request = pending;
        if (request == null || event == null) return;
        if (SystemClock.elapsedRealtime() > request.expires) {
            pending = null;
            lastResult = "Время ожидания истекло.";
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String app = root.getPackageName().toString();
        if (!AssistivePolicy.allowedPackage(app, getPackageName())) return;
        // Clear before attempting the write. Repeated accessibility events
        // must NEVER cause double taps / multiple scrolls.
        pending = null;
        if ("scroll".equals(request.type)) {
            AccessibilityNodeInfo scroll = findScrollable(root);
            lastResult = scroll != null &&
                    scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    ? "✅ Одно пролистывание выполнено в " + app
                    : "Прокрутка не выполнена: подходящий элемент не найден.";
            return;
        }
        AccessibilityNodeInfo hit = findUniqueClickable(root, request.target);
        if (hit == null) {
            lastResult = "Не выполнено: кнопка не найдена или неоднозначна.";
            return;
        }
        boolean clicked = hit.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        lastResult = clicked ? "✅ Один клик выполнен в " + app +
                ". Проверь результат в приложении." :
                "Android не разрешил выполнить нажатие.";
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
