parser grammar CobolCicsParser;

options { tokenVocab = CobolCicsLexer; }

// Entry
cicsStatement
    : execCicsCommand+ EOF
    ;

// EXEC CICS ... END-EXEC block
execCicsCommand
    : EXEC CICS cicsAction* END_EXEC
    ;

// Actions inside EXEC CICS
cicsAction
    : handleConditionCommand
    | handleAbendCommand
    | sendCommand
    | respCommand
    | linkStatement
    | transidStatement
    | commareaStatement
    | readStatement
    | writeStatement
    | rewriteStatement
    | writeqStatement
    | ridfldStatement
    | receiveCommand
    | returnCommand
    | CURSOR
    | ERASE
    | FREEKB
    | IMMEDIATE
    | DATAONLY
    ;

// Parameters (note: as in your original, this allows empty; consider removing the ? if undesired)
cicsParameter
    : CURSOR
    | FREEKB
    ;

conditionStatement
    : errorCondition
    ;

errorCondition
    : ERROR LPAREN IDENTIFIER RPAREN
    ;

handleConditionCommand
    : HANDLE CONDITION (errorCondition+)
    ;

handleAbendCommand
    : HANDLE ABEND LABEL? lableValue?
    ;

lableValue
    : LPAREN IDENTIFIER RPAREN
    ;

sendCommand
    : SEND MAP paramStatement MAPSET mapsetValue (FROM fromValue)?
    ;

receiveCommand
    : RECEIVE mapStatement? intoStatement?
    ;

returnCommand
    : RETURN
    ;

mapStatement
    : MAP paramStatement MAPSET mapsetValue
    ;

linkStatement
    : LINK PROGRAM paramStatement
    ;

intoStatement
    : INTO mapsetValue
    ;

commareaStatement
    : COMMAREA paramStatement
    ;

transidStatement
    : TRANSID paramStatement
    ;

readStatement
    : READ UPDATE? FILE paramStatement (INTO paramStatement)?
    ;

writeStatement
    : WRITE UPDATE? FILE paramStatement (FROM paramStatement)?
    ;

rewriteStatement
    : REWRITE UPDATE? FILE paramStatement (INTO paramStatement)?
    ;

writeqStatement
    : WRITEQ TD? QUEUE paramStatement (FROM paramStatement)? (LENGTH paramStatement)?
    ;

ridfldStatement
    : RIDFLD paramStatement
    ;

// Params & values
paramStatement
    : complexParam? | simpleParam?
    ;

simpleParam
    : LPAREN (IDENTIFIER | STRING) RPAREN
    ;

complexParam
    : LPAREN (IDENTIFIER | STRING) (IN | OF IDENTIFIER)* RPAREN
    ;

fromValue
    : LPAREN IDENTIFIER RPAREN
    ;

mapsetValue
    : LPAREN IDENTIFIER RPAREN
    ;

respCommand
    : RESP respValue
    ;

respValue
    : LPAREN IDENTIFIER RPAREN
    ;

eraseOption
    : ERASE
    ;
