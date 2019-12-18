package io.ovh.tsl;

import io.warp10.script.*;
import java.util.ArrayList;

// TSL WarpScript extension
public class TSL extends NamedWarpScriptFunction implements WarpScriptStackFunction {

    private String errorPrefix;
    private TSLGenerator tslGenerator;

    public TSL(String name, TSLConfig tslConfig) {
        super(name);
        this.errorPrefix = tslConfig.getError();
        this.tslGenerator = new TSLGenerator(tslConfig.getPath());
    }

    @Override
    public Object apply(WarpScriptStack stack) throws WarpScriptException {

        Object o = stack.pop();

        if (!(o instanceof String)) {
            throw new WarpScriptException(getName() + " expects a String on top of the stack.");
        }

        String tslScript = (String) o;

        o = stack.pop();

        if (!(o instanceof String)) {
            throw new WarpScriptException(getName() + " expects a String on top of the stack.");
        }

        String token = (String) o;

        ArrayList<String> symbols = new ArrayList<String>();

        symbols.addAll(stack.getSymbolTable().keySet());

        String warpScript = this.tslGenerator.GenerateWarpScript(token,
                tslScript, false, symbols);

        if (warpScript.startsWith(this.errorPrefix)) {
            throw new WarpScriptException(getName() + " " + warpScript);
        }

        warpScript = "SAVE 'context' STORE\n" + warpScript;
        warpScript += "\n$context RESTORE\n";

        stack.execMulti(warpScript);

        return stack;
    }
}
