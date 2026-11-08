package ai.wolf.autopilot;

import java.time.Instant;
import java.util.List;

/** Actual JDK smoke tests for bulk scheduling input before any network write. */
public final class MetricoolBatchParserTest {
    private static int tests;
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final String FIRST =
        "2026-12-01T23:00:00+03:00 | https://media.example.org/a.mp4 | Серия 1";
    private static final String SECOND =
        "2026-12-02T01:00:00+03:00 | https://media.example.org/b.mp4 | Серия 2";
    private static void expectFail(String source, String timezone) {
        tests++;
        try { MetricoolBatchParser.parse(source, timezone, NOW); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid batch accepted: " + source);
    }
    public static void main(String[] args) {
        List<MetricoolBatchParser.Item> accepted = MetricoolBatchParser.parse(
                FIRST + "\n" + SECOND, "Europe/Minsk", NOW);
        tests++;
        if (accepted.size()!=2 || !accepted.get(0).caption.equals("Серия 1"))
            throw new AssertionError("Valid schedule must parse.");
        tests++;
        if (!MetricoolBatchParser.preview(accepted, "tiktok", "Europe/Minsk", true)
                .contains("АВТОПУБЛИКАЦИЯ"))
            throw new AssertionError("Full consent preview required.");
        expectFail("", "Europe/Minsk");
        expectFail("hello", "Europe/Minsk");
        expectFail(FIRST + "\n" + FIRST, "Europe/Minsk");
        expectFail(FIRST + "\n" + FIRST.replace("/a.mp4","/b.mp4"), "Europe/Minsk");
        expectFail(FIRST.replace("https:","http:"), "Europe/Minsk");
        expectFail(FIRST.replace("media.example.org","localhost"), "Europe/Minsk");
        expectFail(FIRST.replace("media.example.org","192.168.1.2"), "Europe/Minsk");
        expectFail(FIRST.replace("+03:00","+02:00"), "Europe/Minsk");
        expectFail(FIRST.replace("2026-12-01","2026-10-01"), "Europe/Minsk");
        expectFail(FIRST, "Mars/Olympus");
        expectFail(FIRST.replace("Серия 1",""), "Europe/Minsk");
        StringBuilder tooMany = new StringBuilder();
        for (int i=0;i<13;i++) tooMany.append("2026-12-")
                .append(String.format("%02d",i+1)).append("T23:00:00+03:00 | https://media.example.org/")
                .append(i).append(".mp4 | Серия\n");
        expectFail(tooMany.toString(), "Europe/Minsk");
        System.out.println("PASS: " + tests + " Metricool batch contracts.");
    }
}
