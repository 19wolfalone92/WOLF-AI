package ai.wolf.autopilot;

import java.util.Locale;

/**
 * SHA256-pinned PUBLIC GGUF downloads from verified Qwen/ggml-org repos.
 * Model weights run entirely on the Android CPU, no subscription or LLM API.
 */
public final class OfflineModelCatalog {
    public enum Entry {
        QWEN35_08(
                "Qwen3.5 0.8B Q4_0",
                "https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_0.gguf",
                "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf",
                520_000_000L,605_000_000L,
                "wolf-qwen3.5-0.8b-q4.gguf"),
        QWEN35_2(
                "Qwen3.5 2B Q4_K_M",
                "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
                "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
                1_220_000_000L,1_350_000_000L,
                "wolf-qwen3.5-2b-q4km.gguf"),
        QWEN35_4(
                "Qwen3.5 4B Q4_K_M",
                "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
                "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
                2_650_000_000L,2_850_000_000L,
                "wolf-qwen3.5-4b-q4km.gguf"),
        QWEN3_17(
                "Qwen3 1.7B Q4_K_M",
                "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
                "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5",
                1_100_000_000L,1_400_000_000L,
                "wolf-qwen3-1.7b-q4km.gguf"),
        QWEN3_4(
                "Qwen3 4B Q4_K_M",
                "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf",
                "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",
                2_350_000_000L,2_650_000_000L,
                "wolf-qwen3-4b-q4km.gguf");
        public final String title, url, sha256, downloadName;
        public final long minBytes,maxBytes;
        Entry(String title,String url,String sha,long min,long max,String file) {
            this.title=title;
            this.url=url;
            this.sha256=sha;
            this.minBytes=min;
            this.maxBytes=max;
            this.downloadName=file;
        }
    }
    private OfflineModelCatalog() {}

    public static Entry get(String raw) {
        if(raw==null)throw new IllegalArgumentException("Неизвестная модель.");
        for(Entry entry:Entry.values()) {
            if(entry.name().equals(raw))return entry;
        }
        throw new IllegalArgumentException("Неизвестная модель.");
    }

    /** No arbitrary server URL can enter DownloadManager from the catalog. */
    public static boolean trusted(Entry entry) {
        return entry != null &&
                entry.url.startsWith("https://huggingface.co/") &&
                entry.url.endsWith(".gguf") &&
                entry.sha256.matches("[0-9a-f]{64}") &&
                entry.minBytes >= 400_000_000L &&
                entry.maxBytes <= 3_600_000_000L &&
                entry.minBytes < entry.maxBytes &&
                entry.downloadName.matches("wolf-qwen3-[a-z0-9.-]+\\.gguf");
    }

    public static String describe(Entry entry) {
        if(!trusted(entry))throw new IllegalArgumentException("Нет доверенного файла.");
        return entry.title+" · "+String.format(Locale.ROOT,"%.1f–%.1f ГБ",
                entry.minBytes/1e9,entry.maxBytes/1e9) +
                " · полностью бесплатно после загрузки";
    }
}
