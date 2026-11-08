package ai.wolf.autopilot;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scoped phone actions delegated to Android's own UIs.
 * No AccessibilityService, root, shell, screen reading, background spying,
 * direct calls, SMS sending or unrestricted file-system permissions.
 */
public final class DeviceActions {
    public static final class Plan {
        public final Intent intent;
        public final String description;
        public final String localAnswer;
        public final boolean chooseFile;
        Plan(Intent intent, String description, String localAnswer, boolean chooseFile) {
            this.intent = intent; this.description = description;
            this.localAnswer = localAnswer; this.chooseFile = chooseFile;
        }
    }
    private DeviceActions() {}

    private static boolean any(String text, String... phrases) {
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return false;
    }

    public static Plan plan(Context context, String command) {
        String q = command == null ? "" : command.toLowerCase(Locale.ROOT);
        if (any(q, "покажи батарею", "заряд батареи", "информация о телефоне")) {
            Intent state = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = state == null ? -1 :
                    state.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = state == null ? -1 :
                    state.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            StatFs filesystem = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long free = filesystem.getAvailableBytes() / (1024 * 1024 * 1024);
            long total = filesystem.getTotalBytes() / (1024 * 1024 * 1024);
            String battery = level >= 0 && scale > 0 ?
                    (level * 100 / scale) + "%" : "данные недоступны";
            String answer = "📱 Состояние телефона\n" +
                    "Android: " + Build.VERSION.RELEASE +
                    "\nЗаряд: " + battery +
                    "\nВнутреннее хранилище: свободно примерно " + free +
                    " ГБ из " + total + " ГБ.\n" +
                    "WOLF не читает сообщения, экран, контакты и другие приложения.";
            return new Plan(null, "Показать локальную информацию", answer, false);
        }
        if (any(q, "настройки wi-fi", "настройки вайфай",
                "открой wifi", "включи вайфай"))
            return new Plan(new Intent(Settings.ACTION_WIFI_SETTINGS),
                    "Открыть системные настройки Wi‑Fi. Подключение выбираешь сам.", null, false);
        if (any(q, "настройки bluetooth", "настройки блютуз"))
            return new Plan(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                    "Открыть системные настройки Bluetooth.", null, false);
        if (any(q, "настройки телефона", "открой настройки"))
            return new Plan(new Intent(Settings.ACTION_SETTINGS),
                    "Открыть системные настройки Android.", null, false);
        if (any(q, "включи будильник", "покажи будильник", "открой часы"))
            return new Plan(new Intent(AlarmClock.ACTION_SHOW_ALARMS),
                    "Открыть будильники. Время и включение выбираешь в приложении часов.", null, false);
        if (any(q, "позвони", "набери номер")) {
            Matcher match = Pattern.compile("\\+?[0-9][0-9()\\-\\s]{4,22}")
                    .matcher(command);
            if (!match.find())
                return new Plan(null, "Набрать номер", "Укажи номер: «Позвони +375...».", false);
            String digits = match.group().replaceAll("[^0-9+]", "");
            Intent dialer = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits));
            return new Plan(dialer, "Открыть номер " + digits +
                    " в звонилке. WOLF сам НЕ совершает вызов.", null, false);
        }
        if (any(q, "напиши смс", "открой смс"))
            return new Plan(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                    "Открыть редактор SMS. Отправлять сообщение будешь сам.", null, false);
        if (any(q, "открой камеру"))
            return new Plan(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                    "Открыть системную камеру без доступа WOLF к изображению.", null, false);
        if (any(q, "открой галерею", "открой фотографии")) {
            Intent pictures = new Intent(Intent.ACTION_GET_CONTENT);
            pictures.setType("image/*");
            pictures.addCategory(Intent.CATEGORY_OPENABLE);
            return new Plan(pictures, "Выбрать фото по желанию. WOLF не читает всю галерею.",
                    null, false);
        }
        if (any(q, "открой файлы")) {
            Intent files = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            files.setType("*/*");
            files.addCategory(Intent.CATEGORY_OPENABLE);
            files.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return new Plan(files, "Выбрать один файл. Остальные файлы остаются закрыты.",
                    null, true);
        }
        if (any(q, "открой карты")) {
            Intent maps = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="));
            return new Plan(maps, "Открыть карты Android.", null, false);
        }
        if (any(q, "открой календарь")) {
            Intent calendar = new Intent(Intent.ACTION_MAIN);
            calendar.addCategory(Intent.CATEGORY_APP_CALENDAR);
            return new Plan(calendar, "Открыть календарь Android.", null, false);
        }
        return new Plan(null, "Недоступное системное действие",
                "Эту системную команду пока не поддерживаю. " +
                "Могу открыть настройки, браузер, камеру, файлы, часы, " +
                "календарь, карты и набрать номер без автоматического звонка.",
                false);
    }
}
