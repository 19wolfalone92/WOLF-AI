package ai.wolf.autopilot;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

/** Reads only immediate Android OS resource telemetry; no cloud or storage. */
public final class LocalResourceGuard {
    private LocalResourceGuard() {}

    public static LocalResourcePolicy read(Context context) {
        Context app=context.getApplicationContext();
        long available=-1;
        boolean low=false;
        try {
            ActivityManager manager=(ActivityManager)
                    app.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager!=null) {
                ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memory);
                available=memory.availMem;
                low=memory.lowMemory;
            }
        } catch(RuntimeException ignored) {
            // OS readings are best effort; don't invent low-RAM status.
        }
        int thermal=-1;
        try {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) {
                PowerManager power=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
                if(power!=null)thermal=power.getCurrentThermalStatus();
            }
        } catch(RuntimeException ignored) { }
        int battery=-1;
        boolean charging=false;
        try {
            Intent status=app.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(status!=null) {
                int level=status.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
                int scale=status.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
                if(level>=0 && scale>0)battery=(int)Math.round(level*100.0/scale);
                int state=status.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
                charging=state==BatteryManager.BATTERY_STATUS_CHARGING ||
                        state==BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch(RuntimeException ignored) { }
        return LocalResourcePolicy.evaluate(available,low,thermal,battery,charging);
    }
}
