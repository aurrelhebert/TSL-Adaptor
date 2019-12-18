package io.ovh.tsl;

import io.warp10.WarpConfig;
import io.warp10.continuum.Configuration;

public class TimeConstants {
    public static final long TIME_UNITS_PER_MS;

    static {
        // Manage WarpScript time series UNIT per ms constant
        // This constant is the first property set when starting
        String tu = WarpConfig.getProperty(Configuration.WARP_TIME_UNITS);

        if (null == tu) {
            throw new RuntimeException("Missing time units.");
        } else if ("ms".equals(tu)) {
            TIME_UNITS_PER_MS = 1L;
        } else if ("us".equals(tu)) {
            TIME_UNITS_PER_MS = 1000L;
        } else if ("ns".equals(tu)) {
            TIME_UNITS_PER_MS = 1000000L;
        } else {
            throw new RuntimeException("Invalid time unit.");
        }
    }
}
