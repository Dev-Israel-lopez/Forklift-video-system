package qnec.dev.printersystem.Services;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import qnec.dev.printersystem.Models.TimeState;

/** Servicio de 1 Hz para entregar la hora actual (LOCAL por defecto). */
public class ClockService implements AutoCloseable {

    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "clock-ticker");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> task;
    private volatile ZoneId zone = ZoneId.systemDefault();

    public void setZone(ZoneId zone) { this.zone = zone; }

    /** Inicia el ticker a 1 Hz y envía TimeState en cada tick. */
    public void start(java.util.function.Consumer<TimeState> onTick) {
        stop();
        task = exec.scheduleAtFixedRate(() -> {
            try {
                onTick.accept(new TimeState(ZonedDateTime.now(zone)));
            } catch (Exception ignored) { /* seguimos al siguiente tick */ }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    @Override public void close() {
        stop();
        exec.shutdownNow();
    }
}
