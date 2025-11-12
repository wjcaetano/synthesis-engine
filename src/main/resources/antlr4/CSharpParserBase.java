package antlr4;

import antlr4.CSharpParser;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

public abstract class CSharpParserBase extends Parser {
    protected CSharpParserBase(TokenStream input) {
        super(input);
    }

    protected boolean IsLocalVariableDeclaration() {
        if (!(this._ctx instanceof CSharpParser.Local_variable_declarationContext local_var_decl)) {
            return false;
        }
        if (local_var_decl == null) return true;
        CSharpParser.Local_variable_typeContext local_variable_type = local_var_decl.local_variable_type();
        if (local_variable_type == null) return true;
        return !local_variable_type.getText().equals("var");
    }
}