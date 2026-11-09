package ai.wolf.autopilot;

/** Only GET (read-only) calls may be retried. Never repeat POST/dispatch. */
public final class SafeNetworkRetry {
    private SafeNetworkRetry() {}
    public static boolean retryGetStatus(int httpStatus,int attempt) {
        return attempt>=0 && attempt<2 &&
                (httpStatus==429 || httpStatus==502 || httpStatus==503 ||
                        httpStatus==504);
    }
    public static int delayMs(int attempt) {
        if(attempt<0 || attempt>2)throw new IllegalArgumentException("Invalid backoff");
        return 250*(1<<attempt); // 250, 500, 1000ms; max three attempts.
    }
}
