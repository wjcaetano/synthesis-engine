package com.capco.brsp.synthesisengine.utils;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

public class JsonCustomPrettyPrinter extends DefaultPrettyPrinter {
    @Override
    public JsonCustomPrettyPrinter createInstance() {
        JsonCustomPrettyPrinter customPrettyPrinter = new JsonCustomPrettyPrinter();
        customPrettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        return customPrettyPrinter;
    }
}
