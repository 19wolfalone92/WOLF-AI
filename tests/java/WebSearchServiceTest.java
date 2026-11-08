package ai.wolf.autopilot;

import java.util.List;

/** No network needed; runs against production pure-Java HTML result parser. */
public final class WebSearchServiceTest {
    private static int count;
    private WebSearchServiceTest() {}

    private static void check(boolean value, String label) {
        count++;
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        String html = "<html><body>" +
                "<div class=\"result\"><a rel=\"nofollow\" class=\"result__a\"" +
                " href=\"//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Ftest%3Fa%3D1&amp;rut=777\">" +
                "Инструкция &amp; пример</a>" +
                "<a class=\"result__snippet\">Официальный &lt;текст&gt; и описание</a></div>" +
                "<div class=\"result\"><a href=\"https://docs.example.org/page\"" +
                " class=\"result__a\">Документы &quot;WOLF&quot;</a>" +
                "<div class=\"result__snippet\">Описание номер два</div></div>" +
                "<div><a href=\"javascript:alert(1)\" class=\"result__a\">danger</a></div>" +
                "<a href=\"http://insecure.example/\" class=\"result__a\">http</a>" +
                "<a href=\"https://docs.example.org/page\" class=\"result__a\">duplicate</a>" +
                "</body></html>";
        List<WebSearchService.Result> found = WebSearchService.parseHtml(html);
        check(found.size() == 2, "must extract two distinct public HTTPS results");
        check(found.get(0).url.equals("https://example.com/test?a=1"),
                "must unwrap DuckDuckGo tracking URL");
        check(found.get(0).title.equals("Инструкция & пример"),
                "must decode HTML entities in titles");
        check(found.get(1).url.equals("https://docs.example.org/page"),
                "must accept direct HTTPS URL even if attributes reordered");
        check(found.get(0).snippet.contains("Официальный <текст>"),
                "must extract independent search snippets");
        check(found.get(1).title.contains("\"WOLF\""),
                "must decode quotes without adding HTML");
        check(found.stream().noneMatch(x -> x.url.startsWith("http://") ||
                        x.url.startsWith("javascript:")),
                "no insecure or executable search links");
        check(found.get(0).url.startsWith("https://"),
                "user-visible sources must be real HTTPS links");
        check(WebSearchService.parseHtml("<html>CAPTCHA: bot challenge</html>").isEmpty(),
                "bot challenge must not be misrepresented as real search");
        boolean rejected = false;
        try { WebSearchService.parseHtml("X".repeat(245000)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "oversized search result must be rejected");
        check(found.get(0).title.length() < 110, "bounded title size");
        System.out.println("PASS WOLF search parser: " + count +
                " executable source/HTTPS/challenge tests");
    }
}
