package com.capco.brsp.synthesisengine.utils;

import com.capco.brsp.synthesisengine.tools.Transforms;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

class TransformsTest {

    @Test
    void testEscapeJsString() throws JsonProcessingException {
        String json = """
                {
                  "key": "order1",
                  "labels": ["Order"],
                  "date": "2024-01-01",
                  "customer": {
                    "key": "cust42",
                    "labels": ["Customer"],
                    "name": "Alice"
                  },
                  "items": [
                    {
                      "key": "itemA",
                      "labels": ["Item"],
                      "sku": "ABC"
                    },
                    {
                      "key": "itemB",
                      "labels": ["Item"],
                      "sku": "DEF"
                    }
                  ],
                  "notes": ["Urgent", "Gift"]
                }
            """;

        var a = Transforms.nodify(json);
        a = a;
    }
}
