package ai.wolf.autopilot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONTokener;

/**
 * A real HTTPS-only, user-operated mini-browser.
 * No JavaScript by default, no WebView -> Java bridge, no local file access,
 * explicitly confirmed same-origin, navigation-only actions may run inside
 * user-authenticated sessions. No credentials, cookies, password fields,
 * external-domain action, arbitrary scripts or automated form submission.
 */
public final class BrowserActivity extends Activity {
    private static final int BG = Color.rgb(10, 15, 28);
    private static final int PANEL = Color.rgb(23, 34, 55);
    private static final int TEXT = Color.rgb(238, 246, 255);
    private static final int CYAN = Color.rgb(100, 216, 248);
    public static final String EXTRA_TARGET = "wolf_browser_target";

    private EditText address;
    private TextView status;
    private WebView page;
    private boolean javascriptEnabled;

    public static String resolve(String typed) {
        String input = typed == null ? "" : typed.trim();
        if (input.isEmpty()) return "https://html.duckduckgo.com/html/";
        if (input.length() > 400) throw new IllegalArgumentException("Слишком длинный адрес/запрос.");
        if (input.matches("(?i)^https?://.*")) {
            Uri url = Uri.parse(input);
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null ||
                    url.getUserInfo() != null || url.getPort() > 0 ||
                    url.getHost().isEmpty())
                throw new IllegalArgumentException("Открываются только обычные HTTPS-адреса.");
            return url.toString();
        }
        if (input.contains("://") || input.startsWith("file:") || input.startsWith("javascript:"))
            throw new IllegalArgumentException("Запрещённый тип адреса.");
        if (input.matches("(?i)[a-z0-9.-]+\\.[a-z]{2,24}(/[^\\s]*)?")) {
            return "https://" + input;
        }
        return "https://html.duckduckgo.com/html/?q=" +
                URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private Button control(String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(13);
        b.setTextColor(CYAN);
        b.setBackground(bg(PANEL, 14));
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(10), dp(9), dp(10), dp(4));
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(54)));

        Button back = control("‹");
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        back.setOnClickListener(v -> {
            if (page.canGoBack()) page.goBack();
            else finish();
        });

        address = new EditText(this);
        address.setTextColor(TEXT);
        address.setHintTextColor(Color.rgb(154, 173, 200));
        address.setHint("Сайт или поисковый запрос");
        address.setTextSize(14);
        address.setSingleLine(true);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setPadding(dp(12), 0, dp(8), 0);
        address.setBackground(bg(PANEL, 14));
        LinearLayout.LayoutParams addressSize = new LinearLayout.LayoutParams(0, dp(46), 1f);
        addressSize.setMargins(dp(6), 0, dp(6), 0);
        toolbar.addView(address, addressSize);

        Button go = control("→");
        toolbar.addView(go, new LinearLayout.LayoutParams(dp(44), dp(48)));
        go.setOnClickListener(v -> navigate(address.getText().toString()));
        address.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(48)));
        Button forward = control("Вперёд ›");
        Button refresh = control("⟳");
        Button scripts = control("JS: выкл");
        Button external = control("↗ Открыть снаружи");
        for (Button b : new Button[]{forward, refresh, scripts, external}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1f);
            p.setMargins(dp(2), 0, dp(2), 0);
            controls.addView(b, p);
        }

        page = new WebView(this);
        WebSettings settings = page.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(page, false);
        page.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                status.setText("🌐 Загрузка " + progress + "% · HTTPS");
            }
        });
        page.setWebViewClient(new WebViewClient() {
            private boolean https(Uri uri) {
                return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                        && uri.getHost() != null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,
                                                                WebResourceRequest request) {
                if (!https(request.getUrl())) {
                    status.setText("Заблокирован переход на незащищённый адрес.");
                    return true;
                }
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                address.setText(url);
                status.setText("✓ Страница открыта · HTTPS" +
                        (javascriptEnabled ? " · JavaScript включён" :
                                " · JavaScript выключен"));
            }
            @Override public void onReceivedSslError(WebView view,
                                                       SslErrorHandler handler,
                                                       SslError error) {
                handler.cancel(); // No user override of invalid HTTPS certs.
                status.setText("⛔ Ошибка HTTPS-сертификата. Сайт заблокирован.");
            }
            @Override public void onReceivedError(WebView view,
                                                  WebResourceRequest request,
                                                  WebResourceError error) {
                if (request.isForMainFrame())
                    status.setText("Не удалось загрузить страницу: " +
                            String.valueOf(error.getDescription()));
            }
            @Override public void onSafeBrowsingHit(WebView view,
                                                     WebResourceRequest request,
                                                     int threatType,
                                                     android.webkit.SafeBrowsingResponse callback) {
                callback.backToSafety(true);
                status.setText("⛔ Небезопасный сайт заблокирован.");
            }
        });
        LinearLayout helper = new LinearLayout(this);
        helper.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(helper, new LinearLayout.LayoutParams(-1, dp(47)));
        Button browserAssist = control("🤖 Навигация по кнопке");
        helper.addView(browserAssist, new LinearLayout.LayoutParams(-1, dp(43)));
        browserAssist.setOnClickListener(v -> showAssistedNavigation());

        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1f));
        status = new TextView(this);
        status.setTextColor(CYAN);
        status.setTextSize(12);
        status.setPadding(dp(8), dp(6), dp(8), dp(8));
        status.setText("Веб-браузер WOLF · только HTTPS · без JS по умолчанию");
        root.addView(status);

        forward.setOnClickListener(v -> { if (page.canGoForward()) page.goForward(); });
        refresh.setOnClickListener(v -> page.reload());
        scripts.setOnClickListener(v -> {
            if (javascriptEnabled) {
                javascriptEnabled = false;
                settings.setJavaScriptEnabled(false);
                scripts.setText("JS: выкл");
                page.reload();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Разрешить JavaScript для этой вкладки?")
                    .setMessage("Некоторые сайты не работают без JavaScript. " +
                            "Это разрешит выполнение кода страницы внутри WebView. " +
                            "WOLF не получает права на другие приложения, файлы " +
                            "или управление сайтом. Включай только для доверенных сайтов.")
                    .setNegativeButton("Оставить выключенным", null)
                    .setPositiveButton("Включить для вкладки", (dialog, which) -> {
                        javascriptEnabled = true;
                        settings.setJavaScriptEnabled(true);
                        scripts.setText("JS: вкл");
                        page.reload();
                    }).show();
        });
        external.setOnClickListener(v -> {
            String url = page.getUrl();
            if (url != null && url.startsWith("https://")) {
                try {
                    Intent externalIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(externalIntent);
                } catch (Exception error) {
                    status.setText("Внешний браузер недоступен.");
                }
            }
        });
        String target = getIntent().getStringExtra(EXTRA_TARGET);
        navigate(target == null ? "" : target);
    }

    private String responseString(String json) {
        try {
            Object parsed = new JSONTokener(json == null ? "null" : json).nextValue();
            return parsed instanceof String ? (String) parsed : "no-result";
        } catch (Exception ignored) { return "no-result"; }
    }

    /**
     * This is a user-directed action in the current WebView session.
     * User logs in normally inside the browser; WOLF never sees passwords,
     * cookies or form content. Every navigation requires a preview + consent.
     */
    private void showAssistedNavigation() {
        if (page == null || !javascriptEnabled) {
            status.setText("Для навигации включи JS для этой вкладки и войди на сайт вручную.");
            return;
        }
        final String expectedOrigin;
        try { expectedOrigin = BrowserActionPolicy.safeOrigin(page.getUrl()); }
        catch (Exception error) {
            status.setText("Автоматизация запрещена на этой странице.");
            return;
        }
        EditText target = new EditText(this);
        target.setSingleLine(true);
        target.setTextColor(TEXT);
        target.setHintTextColor(CYAN);
        target.setHint("Точное название видимой кнопки или ссылки");
        new AlertDialog.Builder(this)
                .setTitle("🤖 Помощник сайта")
                .setMessage("Сначала войди на сайт вручную. " +
                        "WOLF может нажать только одну точно совпавшую " +
                        "видимую кнопку или ссылку на текущем HTTPS-домене. " +
                        "Он не читает пароли/куки, не заполняет формы, " +
                        "не нажимает «Оплатить», «Отправить» или «Опубликовать».")
                .setView(target)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Найти элемент", (dialog, which) -> {
                    String label = target.getText().toString().trim();
                    try {
                        String preview = BrowserActionPolicy.script(expectedOrigin, label, false);
                        page.evaluateJavascript(preview, json -> {
                            if (page == null || isFinishing()) return;
                            if (!"ready:1".equals(responseString(json))) {
                                status.setText("Не нашёл ровно одну безопасную кнопку с текстом «" +
                                        label + "». Попробуй другое название.");
                                return;
                            }
                            new AlertDialog.Builder(this)
                                    .setTitle("Подтверди один клик")
                                    .setMessage("Сайт: " + expectedOrigin +
                                            "\nКнопка: «" + label + "»\n\n" +
                                            "WOLF только вызовет один клик. " +
                                            "Результат на сервере не подтверждён. " +
                                            "На следующей странице новое действие " +
                                            "потребует отдельного согласия.")
                                    .setNegativeButton("Отмена", null)
                                    .setPositiveButton("Нажать один раз", (d,w) -> {
                                        if (page == null || !javascriptEnabled) return;
                                        try {
                                            page.evaluateJavascript(BrowserActionPolicy.script(
                                                    expectedOrigin, label, true), result -> {
                                                if (status == null) return;
                                                String answer = responseString(result);
                                                status.setText("click-invoked".equals(answer) ?
                                                        "✓ Нажатие вызвано, результат проверь на сайте." :
                                                        "Клик не выполнен: " + answer);
                                            });
                                        } catch (Exception error) {
                                            status.setText("Действие заблокировано.");
                                        }
                                    }).show();
                        });
                    } catch (Exception error) {
                        status.setText("Нельзя выполнить эту команду: " + error.getMessage());
                    }
                }).show();
    }

    private void navigate(String query) {
        try {
            String target = resolve(query);
            status.setText("Загружаю защищённую страницу…");
            page.loadUrl(target);
        } catch (Exception e) {
            status.setText("Адрес не открыт: " + e.getMessage());
        }
    }

    @Override public void onBackPressed() {
        if (page != null && page.canGoBack()) page.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (page != null) {
            page.stopLoading();
            page.removeAllViews();
            page.destroy();
            page = null;
        }
        super.onDestroy();
    }
}
