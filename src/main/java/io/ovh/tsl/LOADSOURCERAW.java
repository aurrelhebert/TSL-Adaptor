package io.ovh.tsl;

import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.warp10.script.unary.TOTIMESTAMP.parseTimestamp;

public abstract class LOADSOURCERAW extends NamedWarpScriptFunction implements WarpScriptStackFunction {

    private Boolean isFind;

    public LOADSOURCERAW(String name, Boolean isFind) {
        super(name);
        this.isFind = isFind;
    }

    @Override
    public Object apply(WarpScriptStack stack) throws WarpScriptException {
        Object top = stack.pop();

        // Pop and parse native FETCH list parameter
        if (!(top instanceof List)) {
            throw new WarpScriptException(getName() + " expects a LIST on top of the stack.");
        }

        List<Object> params = (List) top;

        if (!(params.get(0) instanceof String)) {
            throw new WarpScriptException(getName() + " expects Influx basic auth");
        }

        String token = (String) params.get(0);

        if (!(params.get(1) instanceof String)) {
            throw new WarpScriptException(getName() + " expects Influx basic auth");
        }

        String selector = (String) params.get(1);

        if (!(params.get(2) instanceof Map)) {
            throw new WarpScriptException(getName() + " expects a labels map");
        }
        Map labels = (Map) params.get(2);
        Map<String, String> stringLabels = new HashMap<String, String>();

        for (Object key: labels.keySet()) {
            stringLabels.put(key.toString(), labels.get(key).toString());
        }

        // Case of a FIND statement
        if (this.isFind) {
            List<GeoTimeSerie> allSeries = this.FindGTS(token, selector, stringLabels);
            stack.push(allSeries);
            return stack;
        }

        Object startObject = params.get(3);
        Long start = -1L;
        if (!(startObject instanceof String) && !(startObject instanceof java.util.Date) && !(startObject instanceof Long)) {
            throw new WarpScriptException(getName() + " expects an ISO8601 timestamp, a Date instance or a long tick as 3rd parameter.");
        } else if (startObject instanceof java.util.Date) {
            start = ((java.util.Date) startObject).getTime() * TimeConstants.TIME_UNITS_PER_MS;
        } else if (startObject instanceof String) {
            start = parseTimestamp(startObject.toString());
        } else if (startObject instanceof Long) {
            start = (Long) startObject;
        }

        Object endObject = params.get(4);
        Long end = -1L;
        if (!(endObject instanceof String) && !(endObject instanceof java.util.Date) && !(endObject instanceof Long)) {
            throw new WarpScriptException(getName() + " expects an ISO8601 timestamp, a Date instance or a long tick as 3rd parameter.");
        } else if (endObject instanceof java.util.Date) {
            end = ((java.util.Date) endObject).getTime() * TimeConstants.TIME_UNITS_PER_MS;
        } else if (endObject instanceof String) {
            end = parseTimestamp(endObject.toString());
        } else if (endObject instanceof Long) {
            end = (Long) endObject;
        }

        if (start > end ) {
            Long tmp = start;
            start = end;
            end = tmp;
        }

        if (startObject instanceof Long && endObject instanceof Long) {
            start = end - start;
        }

        // Call custom data source native FETCH
        List<GeoTimeSerie> allSeries = this.FetchGTS(token, selector, stringLabels, start, end);
        stack.push(allSeries);
        return stack;
    }

    abstract public List<GeoTimeSerie> FetchGTS(String token, String selector, Map<String, String> labels, Long start, Long end)
            throws WarpScriptException;
    abstract public List<GeoTimeSerie> FindGTS(String token, String selector, Map<String, String> labels)
            throws WarpScriptException;
}
