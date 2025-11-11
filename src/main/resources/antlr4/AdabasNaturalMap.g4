grammar AdabasNaturalMap;

LPAREN: '(';
RPAREN: ')';
COLON: ':';
EQ: '=';
ON_OFF: 'ON' | 'OFF';
LIKE: 'LIKE';
OF: 'OF';
VIEW: 'VIEW';
INIT: 'INIT';

DATE_MASK: [A-Z0-9][A-Z0-9] '/' [A-Z0-9][A-Z0-9] '/' [A-Z0-9]+;
ID: [a-zA-Z#*+][a-zA-Z0-9.#_/:-]*;
WORD: [a-zA-Z]+;
NUMBER: ('-')? [0-9]+ ('.' [0-9]+)?;
DECIMAL_NUMBER: [0-9]+ (',' [0-9]+);
STRING: '\'' ( ~[\r\n\\'] | '\\' . | '\'\'' )* '\'';
PRINT_STRING     : '"' .*? '"';
BOOLEAN: 'TRUE' | 'FALSE';
WS: [ \t\r\n]+ -> skip;
BLOCK_COMMENT   : '/*' ~[\r\n]* -> skip;
COMMENT: [/*] ' ' ~[\r\n]* (~[a-zA-Z])? -> skip;
ASTERISK_COMMENT:  '*' [\r\n]* -> skip;
LINE_COMMENT: ('**')+  ~[\r\n]* -> skip;
BLOCK_LINE_COMMENT: '*' '-'+ '*' -> skip;
IMPORTANT_COMMENT: '******' ~[\r\n]* '******' -> skip;
SPACE_DOT:'  . ' ~[\r\n]* (~[a-zA-Z])? -> skip;
//INDENT  : (' ') ;

map: defineDataStatement formatStatement inputHeader EOF?
       | EOF;

formatStatement
    : 'FORMAT' formatOption+ // allows multiple FORMAT options
    ;

formatOption
    : ID EQ NUMBER
    | ID EQ STRING
    | ID EQ ON_OFF
    ;

inputHeader
    : 'INPUT' inputOptions* (inputElements+)? // main INPUT keyword
    ;

inputOptions
    : 'NO' 'ERASE'
    | 'USING' 'MAP' ID
    | 'MARK'
    | LPAREN attributeOptions RPAREN
    ;

attributeOptions
    : attributeAssignment (','? attributeAssignment)* ','?
    ;

attributeAssignment
    : EQ
    | ID EQ attributeValue
    | ID
    ;

attributeValue
    : ID
    | ID STRING
    | STRING
    | ON_OFF
    | DATE_MASK
    | NUMBER
    ;

inputElements
    : inputLayoutLine+ 'END'? // handles lines like "001T +LABEL (AD=ODL) /*.01U012 A012 ."
    ;

inputLayoutLine
    : layoutLineNumber layoutLineType layoutContent+ (inputOptions)?
    ;

layoutLineNumber
    : NUMBER; // usually 3-digit like 001, 002

layoutLineType
    : 'T' // Text
    | 'V' // Variable (input)
    | 'B' // Box or border
    | 'M' // Message line
    | 'H' // Header
    ;

layoutContent
    : ID
    | '+' ID               // literal with + prefix
    | '-' ID               // literal with - prefix
    | STRING
    | PRINT_STRING
    | COLON
    ;

defineDataStatement: 'DEFINE' 'DATA'
    defineDataOptions+
    'END-DEFINE'
    ;

defineDataOptions: dataArea+
                 | fieldDefinition+
                 ;

dataArea: 'LOCAL' ('USING' ID)?
        | 'PARAMETER' ('USING' ID)?
        | 'GLOBAL USING' ID ('WITH' ID)?
        ;

fieldDefinition: fieldNumber? fieldIdentifier+
                dataType? (initstmt)?
                | 'LOCAL'
                | 'DYNAMIC'
                | 'BY VALUE' (ID)?
                ;

initstmt:INIT '<' literal (',' literal)*? '>'
        | INIT '<(' (ID '=' ID) (ID '=' ('NE' | ID)) ')>'
        | 'CONST' '<' NUMBER (',' NUMBER)*'>'
        | 'CONST' '<' literal '>'
        | 'CONST' '<' 'H'STRING '>'
        | INIT '<' 'TRUE' '>'
        | INIT '<' 'FALSE' '>'
        | INIT 'TRUE'
        | INIT ID
        | INIT NUMBER
        | INIT STRING;

fieldNumber: NUMBER;

fieldIdentifier: 'USER'
            | LIKE ID
            | ('NAME'|'DATE'|'TIME'|'USER')
            | 'DATA'
            | 'REDEFINE' ID
            | ID;

dataType: '(' dataTypeSpecifier ')'
        | VIEW OF ID
        | ID;

dataTypeSpecifier: ID '/' NUMBER (':' (NUMBER | ID))?
        |(NUMBER':'NUMBER)(','dataTypeSpecifier+)?
        | DECIMAL_NUMBER(':'NUMBER)
        | ID (','NUMBER)
        | ID
        | NUMBER
        ;

literal: NUMBER
       | STRING
       | PRINT_STRING
       | BOOLEAN;