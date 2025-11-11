package com.capco.brsp.synthesisengine.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

//JsonPath.read((Object) JsonPath.read(jsonTree.toString(), "$..[?(@.name == 'classBodyDeclaration' && @..children[?(@.name == 'modifier' && @.text == '@Id')].length() > 0)]..[?(@.name == 'typeType')].text"), "$[0]")

public class ParseTreeJsonConverter {

    public static ObjectNode toJson(ParseTree tree, Parser parser, TokenStream tokens) {
        ObjectMapper mapper = new ObjectMapper();
        return walk(tree, parser, mapper, tokens);
    }

    private static ObjectNode walk(ParseTree tree, Parser parser, ObjectMapper mapper, TokenStream tokens) {
        ObjectNode node = mapper.createObjectNode();

        if (tree instanceof ParserRuleContext ctx) {
            String ruleName = parser.getRuleNames()[ctx.getRuleIndex()];
            node.put("name", ruleName);
            String text = tokens.getText(ctx);
            node.put("text", text);
        } else if (tree instanceof TerminalNode terminal) {
            String text = terminal.getText();
            node.put("name", "terminal");
            node.put("text", text);
            return node;
        }

        // Recurse into children
        ArrayNode children = mapper.createArrayNode();
        for (int i = 0; i < tree.getChildCount(); i++) {
            children.add(walk(tree.getChild(i), parser, mapper, tokens));
        }

        node.set("children", children);

        return node;
    }
}
