package ai.wolf.autopilot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Narrow GitHub Actions dispatcher for public, no-auth CPU Chromium tasks.
 * No free-form commands or secrets passed as workflow inputs.
 */
public final class GithubCloudBrowser {
    private static final String WORKFLOW =
            "https://api.github.com/repos/19wolfalone92/WOLF-AI/actions/workflows/" +
            "wolf-ai-cloud-browser.yml";
    public static final String RUNS =
            "https://github.com/19wolfalone92/WOLF-AI/actions/workflows/" +
            "wolf-ai-cloud-browser.yml";
    private GithubCloudBrowser() {}

    private static HttpURLConnection connection(String endpoint, String method,
                                                String token) throws Exception {
        if (token == null || token.trim().isEmpty())
            throw new IllegalStateException(
                    "Нужен GitHub PAT: Actions (Read and write) для WOLF-AI.");
        HttpURLConnection c = (HttpURLConnection)new URL(endpoint).openConnection();
        c.setRequestMethod(method);
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(12000);
        c.setReadTimeout(16000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "WOLF-AI-Cloud-13");
        return c;
    }

    private static String boundedRead(HttpURLConnection c) throws Exception {
        InputStream response = c.getResponseCode() >= 400 ?
                c.getErrorStream() : c.getInputStream();
        if (response == null) return "";
        try(InputStream in=response; ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
            byte[] block=new byte[4096]; int n;
            while ((n=in.read(block))!=-1) {
                if (buffer.size()+n>120000)
                    throw new IllegalStateException("Ответ GitHub слишком большой.");
                buffer.write(block,0,n);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static String dispatch(CloudBrowserCommand.Plan plan,
                                  String token) throws Exception {
        if (plan == null) throw new IllegalArgumentException("Нет плана.");
        // Re-parse, even if the UI already validated the input.
        CloudBrowserCommand.Plan approved = CloudBrowserCommand.parse(
                "Облачный браузер " + plan.url +
                (plan.steps.isEmpty() ? "" : " шаги: " + plan.steps));
        HttpURLConnection c=connection(WORKFLOW+"/dispatches","POST",token);
        try {
            JSONObject payload = new JSONObject().put("ref","main")
                    .put("inputs",new JSONObject()
                            .put("url",approved.url)
                            .put("steps",approved.steps));
            byte[] request=payload.toString().getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            c.setFixedLengthStreamingMode(request.length);
            try(OutputStream out=c.getOutputStream()){out.write(request);}
            int status=c.getResponseCode();
            boundedRead(c);
            if (status!=204)
                throw new IllegalStateException("GitHub не подтвердил приём браузерного " +
                        "задания: HTTP " + status +
                        ". Перед повтором проверь запуски Actions.");
            return "✅ GitHub принял задание облачного браузера. " +
                    "Это ещё не готовый отчёт. Проверь реальный статус и " +
                    "скачай browser_report.json и PNG из Artifacts:\n" + RUNS;
        } finally { c.disconnect(); }
    }

    public static String recent(String token) throws Exception {
        HttpURLConnection c=connection(WORKFLOW+"/runs?per_page=5","GET",token);
        try {
            int code=c.getResponseCode();
            String body=boundedRead(c);
            if (code!=200) throw new IllegalStateException("GitHub HTTP " + code);
            JSONArray runs=new JSONObject(body).getJSONArray("workflow_runs");
            StringBuilder out=new StringBuilder("☁️ Последние облачные запуски:\n");
            int emitted=0;
            for(int i=0;i<runs.length() && emitted<5;i++) {
                JSONObject item=runs.getJSONObject(i);
                if (!"workflow_dispatch".equals(item.optString("event"))) continue;
                out.append("#").append(item.getLong("id")).append(" — ")
                        .append(item.optString("status","unknown")).append(" / ")
                        .append(item.isNull("conclusion")?"выполняется":
                                item.optString("conclusion","unknown"))
                        .append("\n")
                        .append("https://github.com/19wolfalone92/WOLF-AI/actions/runs/")
                        .append(item.getLong("id")).append("\n");
                emitted++;
            }
            if (emitted==0) out.append("Запущенных из приложения заданий нет.\n");
            out.append("Проверь Artifacts каждого запуска, а не только текст ИИ.");
            return out.toString();
        } finally { c.disconnect(); }
    }
}
