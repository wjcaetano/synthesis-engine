lexer grammar CobolCicsLexer;

// Punctuation
LPAREN    : '(' ;
RPAREN    : ')' ;
DOT       : '.'  ;

// Keywords
EXEC      : 'EXEC' ;
CICS      : 'CICS' ;
END_EXEC  : 'END-EXEC' ;

SEND      : 'SEND' ;
RECEIVE   : 'RECEIVE' ;
RETURN    : 'RETURN' ;
INTO      : 'INTO' ;
RESP      : 'RESP' [0-9]* ;
TEXT      : 'TEXT' ;
MAP       : 'MAP' ;
MAPSET    : 'MAPSET' ;
DATAONLY  : 'DATAONLY' ;
FROM      : 'FROM' ;
TO        : 'TO' ;
ERASE     : 'ERASE' ;
READ      : 'READ' ;
RIDFLD    : 'RIDFLD' ;
FILE      : 'FILE' ;
CURSOR    : 'CURSOR' ;
FREEKB    : 'FREEKB' ;
ABEND     : 'ABEND' ;
LABEL     : 'LABEL' ;
PROGRAM   : 'PROGRAM' ;
LINK      : 'LINK' ;
UPDATE    : 'UPDATE' ;
WRITE     : 'WRITE' ;
REWRITE   : 'REWRITE' ;
WRITEQ    : 'WRITEQ' ;
TD        : 'TD' ;
IN        : 'IN' ;
OF        : 'OF' ;
QUEUE     : 'QUEUE' ;
LENGTH    : 'LENGTH' ;
COMMAREA  : 'COMMAREA' ;
TRANSID   : 'TRANSID' ;
IMMEDIATE : 'IMMEDIATE' ;

CONDITION : 'CONDITION' ;
ERROR     : 'ERROR' ;
HANDLE    : 'HANDLE' ;

// Atoms
STRING     : '\'' (~['\r\n])* '\'' ;
IDENTIFIER : [a-zA-Z0-9-]+ ;

// Skip
WS : [ \t\r\n]+ -> skip ;
