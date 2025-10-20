package qnec.dev.printersystem.Models;

import java.time.ZonedDateTime;

public class TimeState {
    private final ZonedDateTime now;
    public TimeState(ZonedDateTime now) { this.now = now; }
    public ZonedDateTime now() { return now; }
}
