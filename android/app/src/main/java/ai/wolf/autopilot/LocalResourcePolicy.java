package ai.wolf.autopilot;

/**
 * Side-effect-free policy. JVM-tested; never initiates networking, spends
 * provider credits or assumes mobile thermal readings are always available.
 */
public final class LocalResourcePolicy {
    public static final long GB=1_000_000_000L;
    public enum Level { NORMAL, CONSERVE, CRITICAL }
    public final Level level;
    public final String reason;
    public final int maxSteps;
    public final int maxTokens;

    private LocalResourcePolicy(Level level,String reason,int stages,int tokens) {
        this.level=level;
        this.reason=reason;
        this.maxSteps=stages;
        this.maxTokens=tokens;
    }

    /**
     * thermal 0..6 (Android PowerManager), -1 when unknown.
     * Battery -1 unknown, otherwise percentage; charging devices are exempt
     * from low-battery throttling, but NEVER from RAM/thermal protection.
     */
    public static LocalResourcePolicy evaluate(long availableRam, boolean androidLowMemory,
                                                int thermal,int battery,boolean charging) {
        if(androidLowMemory || (availableRam>=0 && availableRam<1_200_000_000L))
            return new LocalResourcePolicy(Level.CRITICAL,"Android сообщает о нехватке RAM",1,192);
        if(thermal>=3)
            return new LocalResourcePolicy(Level.CRITICAL,"Устройство перегревается",1,192);
        if(!charging && battery>=0 && battery<=10)
            return new LocalResourcePolicy(Level.CRITICAL,"Низкий заряд аккумулятора",1,192);
        if((availableRam>=0 && availableRam<2_500_000_000L) ||
                thermal>=2 || (!charging && battery>=0 && battery<=20))
            return new LocalResourcePolicy(Level.CONSERVE,"Экономия RAM, заряда или снижение нагрева",1,256);
        return new LocalResourcePolicy(Level.NORMAL,"Ресурсов достаточно",4,768);
    }

    public int steps(int requested) {
        return Math.min(Math.max(1,requested),maxSteps);
    }

    public int tokens(int requested) {
        return Math.min(Math.max(128,requested),maxTokens);
    }

    /** Never hard-fail the whole chat simply because a large GGUF was chosen. */
    public boolean useImported(long bytes) {
        if(bytes<=0)return false;
        if(level==Level.CRITICAL)return false;
        if(level==Level.CONSERVE && bytes>1_000_000_000L)return false;
        return true;
    }

    public String warning() {
        return level==Level.NORMAL?"":"⚠️ WOLF автоматически уменьшил нагрузку: "+
                reason+". Используется только локальная модель, без платного облака.";
    }
}
