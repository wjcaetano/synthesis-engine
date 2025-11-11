lexer grammar BmsLexer;

LINE_COMMENT
	: '*' ~[\r\n]* -> channel(HIDDEN)
	;

STRING_START
    : '\''
    ->pushMode(STRING_MODE)
    ;

ASSIGN
    : '='
    ;

STAR
    : '*'
    ;

MACRO_NAME
    : 'DFHMSD'
    | 'DFHMDI'
    | 'DFHMDF'
    ;

ALPHA
    : [\-_.&A-Za-z0-9]+
    ;

ECOM
    : '&'
    ;

LPAREN
    : '('
    ;

RPAREN
    : ')'
    ;

VALUE_SEPARATOR
    : ','
    ;

NEWLINE
    : [\r\n]+
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

mode STRING_MODE;
STRING_END: '\''->popMode;
ANY: ~[']*;