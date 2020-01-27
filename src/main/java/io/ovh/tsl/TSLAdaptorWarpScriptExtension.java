package io.ovh.tsl;

import java.util.HashMap;
import java.util.Map;
import io.warp10.warp.sdk.WarpScriptExtension;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class TSLAdaptorWarpScriptExtension extends WarpScriptExtension {

    private static final Map<String,Object> functions;

    @ConfigProperty(name = "influx.query.url", defaultValue="http://127.0.0.1:8080")
    public static String influxQueryURL;

    static {
        functions = new HashMap<String, Object>();

        functions.put("FETCH" + INFLUXRAW.GetFunctionName(),
                new INFLUXRAW("FETCH" + INFLUXRAW.GetFunctionName(), false));
        functions.put("FIND" + INFLUXRAW.GetFunctionName(),
                new INFLUXRAW("FIND" + INFLUXRAW.GetFunctionName(), true));
    }

    @Override
    public Map<String, Object> getFunctions() {
        return functions;
    }
}
