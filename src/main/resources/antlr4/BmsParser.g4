parser grammar BmsParser;

options { tokenVocab=BmsLexer; }

startRule
    : macro+ EOF
    ;

macro
    : macroDef operands
    ;

macroDef
    : identifier? MACRO_NAME
    | MACRO_NAME
    ;

identifier
    : ALPHA
    ;

operands
    : operand (VALUE_SEPARATOR operand)*
    ;

operand
    : identifier ASSIGN value
    ;

value
    : ALPHA
    | STRING_START ANY STRING_END
    | values
    ;

values
    : LPAREN value (VALUE_SEPARATOR value)* RPAREN
    ;
