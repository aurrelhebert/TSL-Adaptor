package io.ovh.tsl;

import io.warp10.WarpConfig;
import io.warp10.script.MemoryWarpScriptStack;
import io.warp10.script.StackUtils;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptLib;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Path("/api/v0/tsl")
public class ServeTSL {
    private static final String TSL_RESULT_TIME_UNIT = "tsl.result.timeunits";

    @ConfigProperty(name = "tsl.libso.path", defaultValue="tsl.so")
    String libSoPath;

    @ConfigProperty(name = "tsl.libso.error.prefix", defaultValue="error -")
    String errorPrefix;

    public ServeTSL() {

        // Load WarpScript necessary time unit result
        String resultTimeUnit;
        try {
           resultTimeUnit = ConfigProvider.getConfig().getValue(TSL_RESULT_TIME_UNIT, String.class);
        } catch (java.util.NoSuchElementException e) {
           resultTimeUnit = "us";
        }
        String timeunit = "warp.timeunits = " + resultTimeUnit;

        // Generate a native Warp10 properties
        Reader inputString = new StringReader(timeunit);
        BufferedReader reader = new BufferedReader(inputString);
        try {
            WarpConfig.setProperties(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Load TSL-Adaptor native extension JARs
        WarpConfig.setProperty("warpscript.extensions", "io.ovh.tsl.TSLAdaptorWarpScriptExtension");

        // Set all custom sources properties
        this.setExtensionParameters();
    }

    // Set all custom sources properties 
    private void setExtensionParameters() {
       Iterable<String> allPropertyNames = ConfigProvider.getConfig().getPropertyNames();
       for(String property: allPropertyNames) {
           if (property.startsWith("source."))  {
                WarpConfig.setProperty(property, ConfigProvider.getConfig().getValue(property, String.class));
           }
       }
    }

    // Get Get generic Def functions
    private String GetWarpScriptDef(String functionName) {
        return "<% <% FETCH" + functionName +" %> " +
                        "<% ERROR  -1 GET 'message' GET STOP %> " +
                        "<% %> TRY " +
                        "%> 'FETCH' DEF " +
               "<% <% FIND" + functionName +" %> " +
               "<% ERROR  -1 GET 'message' GET STOP %> " +
               "<% %> TRY " +
               "%> 'FIND' DEF ";
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String query(String tslScript, @Context HttpHeaders headers) {
        String result = "";

        // Get Basic authentication from headers
        final String authorization = headers.getHeaderString("Authorization");
        String credentials = "";
        if (authorization != null && authorization.toLowerCase().startsWith("basic")) {

            // Authorization: Basic base64credentials
            String base64Credentials = authorization.substring("Basic".length()).trim();
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            credentials = new String(credDecoded, StandardCharsets.UTF_8);
        }

        // Extract TSL parameters from config
        TSLConfig tslConfig = new TSLConfig(libSoPath, errorPrefix);

        // Load Warp 10 specific properties
        Properties properties = WarpConfig.getProperties();
        System.setProperty("java.awt.headless", "true");

        // Register Warp 10 extensions
        WarpScriptLib.registerExtensions();

        // Create a Warp10 stack with max limits
        MemoryWarpScriptStack stack = new MemoryWarpScriptStack(null, null, properties);
        stack.maxLimits();

        // Create a TSL parser
        TSL tsl = new TSL("TSL", tslConfig);
        try {
            // Replace actual Fetch by an InfluxFetch
            String toSend = "";

            toSend += this.GetWarpScriptDef(INFLUXRAW.GetFunctionName());

            stack.execMulti(toSend);

            // Push authentication
            stack.push(credentials);

            // Execute TSL
            stack.push(tslScript);
            tsl.apply(stack);

            // Parse result
            StringWriter out = new StringWriter();
            PrintWriter writer = new PrintWriter(out);
            StackUtils.toJSON(writer, stack);
            writer.flush();
            result = out.toString();
        } catch (WarpScriptException e) {
            e.printStackTrace();
            StringWriter out = new StringWriter();
            PrintWriter writer = new PrintWriter(out);
            try {
                StackUtils.toJSON(writer, stack);
            } catch (WarpScriptException ex) {
                ex.printStackTrace();
            }
            writer.flush();
            result = out.toString();
        }

        return result;
    }
}
