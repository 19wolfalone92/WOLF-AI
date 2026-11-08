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
        if (any(q, "покажи батарею", "заряд батареи", "информация о телефоне",
                "сколько памяти", "свободное место на телефоне")) {
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
        // Native alarms and timers: Android's CLOCK UI shows the complete
        // request before committing; WOLF never asks for SET_ALARM permission.
        if (any(q, "будильник на", "поставь будильник", "разбуди в")) {
            Matcher clock = Pattern.compile("(?<![0-9])([01]?[0-9]|2[0-3]):([0-5][0-9])(?![0-9])")
                    .matcher(q);
            if (!clock.find())
                return new Plan(null, "Установить будильник",
                        "Напиши время в формате: «Поставь будильник на 07:30».", false);
            int hour = Integer.parseInt(clock.group(1));
            int minute = Integer.parseInt(clock.group(2));
            Intent alarm = new Intent(AlarmClock.ACTION_SET_ALARM);
            alarm.putExtra(AlarmClock.EXTRA_HOUR, hour);
            alarm.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            alarm.putExtra(AlarmClock.EXTRA_MESSAGE, "WOLF AI");
            alarm.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            return new Plan(alarm, "Открыть будильник на " +
                    String.format(Locale.ROOT, "%02d:%02d", hour, minute) +
                    " в приложении часов; подтвердить установку в Android.", null, false);
        }
        if (any(q, "поставь таймер", "запусти таймер", "таймер на")) {
            Matcher timer = Pattern.compile("(?<![0-9])([0-9]{1,3})\\s*(?:минут|мин\\b)")
                    .matcher(q);
            if (!timer.find())
                return new Plan(null, "Установить таймер",
                        "Напиши: «Поставь таймер на 10 минут».", false);
            int minutes = Integer.parseInt(timer.group(1));
            if (minutes < 1 || minutes > 600)
                return new Plan(null, "Установить таймер",
                        "Таймер должен быть от 1 до 600 минут.", false);
            Intent timerIntent = new Intent(AlarmClock.ACTION_SET_TIMER);
            timerIntent.putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60);
            timerIntent.putExtra(AlarmClock.EXTRA_MESSAGE, "WOLF AI");
            timerIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            return new Plan(timerIntent, "Открыть таймер " + minutes +
                    " мин; подтвердить запуск в Android.", null, false);
        }
        if (any(q, "настройки яркости", "настройки экрана"))
            return new Plan(new Intent(Settings.ACTION_DISPLAY_SETTINGS),
                    "Открыть настройки экрана. Изменение выполняешь сам.", null, false);
        if (any(q, "настройки звука", "громкость телефона"))
            return new Plan(new Intent(Settings.ACTION_SOUND_SETTINGS),
                    "Открыть настройки громкости и звука.", null, false);
        if (any(q, "настройки геолокации", "настройки местоположения"))
            return new Plan(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                    "Открыть настройки местоположения (без доступа WOLF к геоданным).", null, false);
        if (any(q, "настройки режима полёта", "режим полета"))
            return new Plan(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                    "Открыть настройки режима полёта.", null, false);
        if (any(q, "настройки приложения wolf", "настройки wolf ai")) {
            Intent app = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.getPackageName()));
            return new Plan(app, "Открыть системную страницу разрешений WOLF AI.", null, false);
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
