package io.ovh.tsl;

import io.warp10.WarpConfig;
import io.warp10.continuum.gts.GTSHelper;
import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.continuum.store.Constants;
import io.warp10.continuum.store.thrift.data.Metadata;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStackFunction;
import org.apache.commons.lang.StringUtils;
import org.boon.core.Sys;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class INFLUXRAW extends LOADSOURCERAW implements WarpScriptStackFunction {

    protected static final String INFLUX_QUERY_URL = "source.influx.query.url";
    private static final String DEFAULT_INFLUX_QUERY_URL = "http://127.0.0.1:8086";
    private String influxQueryURL;

    private static final String INFLUX_QUERY_DATABASE = "source.influx.query.database";
    private static final String DEFAULT_INFLUX_QUERY_DATABASE = "telegraf";
    private String influxQueryDatabase;

    private static final String INFLUX_RESULT_SEPARATOR = "source.influx.result.separator";
    private static final String DEFAULT_INFLUX_RESULT_SEPARATOR = ".";
    private String influxResultSeparator;

    public static String GetFunctionName() {
        return "INFLUXRAW";
    }

    public INFLUXRAW(String name, Boolean isFind) {
        super(name, isFind);
        this.influxQueryURL = WarpConfig.getProperty(INFLUX_QUERY_URL, DEFAULT_INFLUX_QUERY_URL);
        this.influxQueryDatabase = WarpConfig.getProperty(INFLUX_QUERY_DATABASE, DEFAULT_INFLUX_QUERY_DATABASE);
        this.influxResultSeparator = WarpConfig.getProperty(INFLUX_RESULT_SEPARATOR, DEFAULT_INFLUX_RESULT_SEPARATOR);
    }

    // FindGTS is the Query done on Influx to get all selectors matching the given query
    // (used for TSL FIND statements)
    public List<GeoTimeSerie> FindGTS(String token, String selector, Map<String, String> labels)
            throws WarpScriptException {

        // Parse Influx authentication from Warp token
        if (!token.contains(":")) {
            throw new WarpScriptException(getName() + " expects influx auth (user and password) split by :");
        }

        String[] tokenParts = token.split(":", 2);
        String influxUser = tokenParts[0];
        String influxPassword = tokenParts[1];

        // Parse from measurement and field selection
        String influxMeasurement = selector;
        String influxFields = "*";
        if (selector.contains(this.influxResultSeparator)) {
            String[] selectorParts = token.split(this.influxResultSeparator, 2);
            influxMeasurement  = selectorParts[0];
            influxFields = selectorParts[1];
        }

        // Parse Warp where clauses and convert it in Influx format
        String whereClause = "";
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            whereClause += " AND " + entry.getKey().toString() + " = '" + entry.getValue() +"'";
        }

        // Start influx
        InfluxDB influxdb = InfluxDBFactory.connect(this.influxQueryURL, influxUser, influxPassword);

        // First iterate on all valid fields
        String getFields = " SHOW FIELD KEYS FROM " + influxMeasurement;
        Query queryFields= new Query(getFields, this.influxQueryDatabase, true);
        QueryResult fieldsResult = influxdb.query(queryFields, TimeUnit.NANOSECONDS);

        // Associate all fields to a specific measurement
        Map<String,List<Object>> validFields = new HashMap<>();
        for (QueryResult.Result fieldResult: fieldsResult.getResults()) {
            if (fieldResult.getSeries() == null) {
                continue;
            }

            // Parse field result Influx query
            List<QueryResult.Series> fieldSeries = fieldResult.getSeries();
            for (QueryResult.Series field: fieldSeries) {
                if (influxFields != "*") {
                   if (!field.getName().matches(influxFields)){
                       continue;
                   }
                }

                List<List<Object>> allvalues = field.getValues();

                List<Object> fieldValues = new ArrayList<>();

                for (List<Object> values: allvalues) {

                    // Get only the field name, (removing its type)
                    if (values.size()>=1) {
                        fieldValues.add(values.get(0));
                    }
                }
                validFields.put(field.getName(), fieldValues);
            }
        }

        // Get complete series with tags and Influx measurement
        String getSeries = "SHOW SERIES FROM " + influxMeasurement
                           + " WHERE " + StringUtils.removeStart(whereClause, " AND");
        Query querySeries = new Query(getSeries, this.influxQueryDatabase, true);
        QueryResult queryResult = influxdb.query(querySeries, TimeUnit.NANOSECONDS);

        influxdb.close();

        // Parse series query result
        List<GeoTimeSerie> parsedSeries = new ArrayList<GeoTimeSerie>();
        for (QueryResult.Result seriesResult: queryResult.getResults()) {
            if (seriesResult.getSeries() == null) {
                continue;
            }

            List<QueryResult.Series> series = seriesResult.getSeries();

            for (QueryResult.Series completeTag: series) {

                List<List<Object>> allvalues = completeTag.getValues();

                for (List<Object> values: allvalues) {
                    for(Object tag: values) {

                        Metadata meta = new Metadata();
                        String[] tagParts = tag.toString().split(",");
                        String seriesName = tagParts[0];

                        // Get current series tags
                        for (String item: tagParts) {
                            String[] splitItem = item.split("=", 2);
                            if (splitItem.length < 2) {
                                continue;
                            } else {
                                meta.putToLabels(splitItem[0], splitItem[1]);
                            }
                        }

                        // Create a single series per tags and per field
                        if (validFields.containsKey(seriesName)) {
                            for (Object field: validFields.get(seriesName)) {
                                GeoTimeSerie gts = new GeoTimeSerie(0);
                                gts.setMetadata(meta);
                                gts.setName(seriesName + this.influxResultSeparator + field.toString());
                                parsedSeries.add(gts);
                            }
                        }

                    }
                }
            }
        }
        return parsedSeries;
    }


    // FetchGTS is the Query done on Influx to get raw data between two dates for a given selector
    // (used for TSL FETCH statements)
    public List<GeoTimeSerie> FetchGTS(String token, String selector, Map<String, String> labels, Long start, Long end)
            throws WarpScriptException {

        // Convert provided date to Influx valid query format in nano seconds
        start = start * 1000L;
        end = end * 1000L;

        // Parse Influx authentication from Warp token
        if (!token.contains(":")) {
            throw new WarpScriptException(getName() + " expects influx auth (user and password) split by :");
        }

        String[] tokenParts = token.split(":", 2);
        String influxUser = tokenParts[0];
        String influxPassword = tokenParts[1];

        // Parse from measurement and field selection
        String influxMeasurement = selector;
        String influxFields = "*";
        if (selector.contains(this.influxResultSeparator)) {
            String[] selectorParts = token.split(this.influxResultSeparator, 2);
            influxMeasurement  = selectorParts[0];
            influxFields = selectorParts[1];
        }

        // Parse Warp where clauses and convert it in Influx format
        String whereClause = "";
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            whereClause += " AND " + entry.getKey().toString() + " = '" + entry.getValue() +"'";
        }

        // Open an Influx connection
        InfluxDB influxdb = InfluxDBFactory.connect(this.influxQueryURL, influxUser, influxPassword);

        // Instantiate a first tag query to be aware of the query tags
        String getTags = " SHOW TAG KEYS FROM " + influxMeasurement
                         + " WHERE " + StringUtils.removeStart(whereClause, " AND");
        Query queryTags = new Query(getTags, this.influxQueryDatabase, true);
        QueryResult tagsResult = influxdb.query(queryTags, TimeUnit.NANOSECONDS);

        // Keep tags results in a tag List
        List<Object> validTags = new ArrayList<>();
        for (QueryResult.Result tagResult: tagsResult.getResults()) {
            if (tagResult.getSeries() == null) {
                continue;
            }

            List<QueryResult.Series> tagSeries = tagResult.getSeries();

            for (QueryResult.Series tag: tagSeries) {

                List<List<Object>> allvalues = tag.getValues();

                // Add all returned tags in tag list
                for (List<Object> values: allvalues) {
                    validTags.addAll(values);
                }
            }
        }

        // Execute Influx raw query
        String nativeInflux = " SELECT " + influxFields + " FROM " + influxMeasurement
                              + " WHERE " + " time >= " + start.toString()
                              + " AND time <= " + end + whereClause;
        Query query = new Query(nativeInflux, this.influxQueryDatabase, true);
        QueryResult results = influxdb.query(query, TimeUnit.NANOSECONDS);

        // Close Influx connection
        influxdb.close();

        // Parsed result to provide as Warp10 FETCH an series list
        List<GeoTimeSerie> parsedSeries = new ArrayList<GeoTimeSerie>();
        if (results.getResults().size() == 0) {
            return parsedSeries;
        }

        for (QueryResult.Result result: results.getResults()) {
            if (result.getSeries() == null) {
                continue;
            }

            List<QueryResult.Series> series = result.getSeries();

            Metadata meta = new Metadata();

            // Parse each return series
            for (QueryResult.Series returnedSeries: series) {

                // Empty labels
                if (meta.getLabelsSize() > 0) {
                    meta.getLabels().clear();
                }

                // Set Series labels based on query tags
                if (null != returnedSeries.getTags()) {
                    meta.setLabels(returnedSeries.getTags());
                } else {
                    meta.setLabels(new HashMap<String,String>());
                }

                // Set series name
                String measurement = returnedSeries.getName();
                List<List<Object>> allvalues = returnedSeries.getValues();
                Map<String,GeoTimeSerie> mapGTS = new HashMap<>();

                int firstInt = 1;

                // Loop over the columns, column 0 is the timestamp
                for (int i = 1; i < returnedSeries.getColumns().size(); i++) {

                    String prefix = "";
                    if (validTags.contains(returnedSeries.getColumns().get(i))) {
                        firstInt++;
                        continue;
                    }

                    // The name is composed of a measurement and the Influx field stored in column
                    meta.setName(prefix + measurement + this.influxResultSeparator + returnedSeries.getColumns().get(i));

                    int hint = returnedSeries.getValues().size();

                    // Parse all values
                    for (List<Object> values: allvalues) {

                        // Get series id based on its name and its tag too
                        String uniqueId = measurement + this.influxResultSeparator
                                          + returnedSeries.getColumns().get(i) + "{";
                        for (Object currentTag: validTags) {
                            int tagsIndex = returnedSeries.getColumns().indexOf(currentTag.toString());
                            Object tagValue = values.get(tagsIndex);

                            if (null == tagValue) {
                                continue;
                            }

                            uniqueId += currentTag.toString() + "=" + tagValue.toString();
                            meta.getLabels().put(currentTag.toString(),tagValue.toString());
                        }

                        uniqueId += "}" ;
                        GeoTimeSerie gts = new GeoTimeSerie(hint);

                        // When the series exists, load it from actual map, otherwise instantiate it
                        if (mapGTS.containsKey(uniqueId)) {
                            gts = mapGTS.get(uniqueId);
                        } else {
                            gts.setMetadata(meta);
                            mapGTS.put(uniqueId, gts);
                        }

                        // Parse and put current value in current time series
                        long timestamp = 0L;
                        if (firstInt == i) {
                            long dts = (long) ((double) values.get(0));

                            timestamp += dts / (1000000000L / Constants.TIME_UNITS_PER_S);

                            // Set timestamp
                            values.set(0, timestamp);
                        } else {
                        timestamp = (long) values.get(0);
                        }

                        Object value = values.get(i);

                        if (null == value) {
                            continue;
                        }

                        GTSHelper.setValue(gts, timestamp, value);
                    }
                }
                parsedSeries.addAll(mapGTS.values());
            }
        }
        return parsedSeries;
    }
}