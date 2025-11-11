grammar AdabasNatural;

@lexer::members {
  private boolean atFirstLineChar() {
    return getCharPositionInLine() == 0;
  }

  private void commentLineLog() {
      System.out.println("Comment at line " + getLine());
  }
}

fragment WS_NO_NL
  : [ \t\f\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]
  ;
LINE_COMMENT
  : {atFirstLineChar()}? '*' {System.out.println("Comment at line " + getLine());} ('*' | WS_NO_NL)? (~[\r\n]* | EOF) -> skip
  ;

REDEFINE: 'REDEFINE';
USING: 'USING';
SORT: 'SORT';
SAME: 'SAME';
RECORD: 'RECORD';
END_REPEAT: 'END-REPEAT';
RETURN: 'RETURN';
EM: 'EM';
INPUT: 'INPUT';
OFF: 'OFF';
DO: 'DO';
DOEND: 'DOEND';
MASK: 'MASK';
SCAN: 'SCAN';
DIVIDE: 'DIVIDE';
REMAINDER: 'REMAINDER';

// Lexer rules
//Adabas reference urls you may find it below
// For Reserved. KW
// https://documentation.softwareag.com/adabas/cxx146/userguide/CONNXCDD32C/Reserved_Keywords_and_Symbols.htm
// https://documentation.softwareag.com/adabas/cxx146/userguide/CONNXCDD32C/Reserved_Keywords_and_Symbols.htm
// Official Adabas Examples for testing and remaining purposes
// https://documentation.softwareag.com/natural/nat914unx/pg/pg_exas.htm
// http://lab.antlr.org/ - for testing and compiling the code online
//Lastly Modified & Enhanced by Mirza Iqbal 06.06.2024 13:15

// Part "A" Reserved Keywords
ABS: 'ABS';
ADABAS_REUSE: 'ADABAS_REUSE';
ANY: 'ANY';
AUTHORIZATION: 'AUTHORIZATION';
ACOS: 'ACOS';
ADABAS_UISIZE: 'ADABAS_UISIZE';
ARRAYSEARCH: 'ARRAYSEARCH';
AUTOCOMMIT: 'AUTOCOMMIT';
ADABAS: 'ADABAS';
ADABAS_UIUNIT: 'ADABAS_UIUNIT';
AS: 'AS';
AUTOCOUNTER: 'AUTOCOUNTER';
ADABAS_DSSIZE: 'ADABAS_DSSIZE';
ADD: 'ADD';
ASC: 'ASC';
AVEDEVMEDIAN: 'AVEDEVMEDIAN';
ADABAS_DSUNIT: 'ADABAS_DSUNIT';
ALL: 'ALL';
ASCII: 'ASCII';
AVEDAVEMEAN: 'AVEDEVMEAN';
ADABAS_MAXISN: 'ADABAS_MAXISN';
ALL_COLS: 'ALL~COLS';
ASIN: 'ASIN';
AVG: 'AVG';
ADABAS_NISIZE: 'ADABAS_NISIZE';
ALTER: 'ALTER';
ATAN: 'ATAN';
AVG_DISTINCT: 'AVG_DISTINCT';
ADABAS_NIUNIT: 'ADABAS_NIUNIT';
AND: 'AND';
ATAN2: 'ATAN2';

// PART "B" RESERVED KEYWORDS
BEGIN: 'BEGIN';
BIBDATA: 'BIBDATA';
BIN: 'BIN';
BIT_LENGTH: 'BIT_LENGTH';
BEGINTRANS: 'BEGINTRANS';
BIBXREF: 'BIBXREF';
BINARY: 'BINARY';
BLOCK: 'BLOCK';
BETWEEN: 'BETWEEN';
BIGINT: 'BIGINT';
BIT: 'BIT';
BY: 'BY';
BOTTOM:'BOTTOM';

// PART "C" RESERVED KEYWORDS
CALL: 'CALL';
CALLNAT: 'CALLNAT';
CNXFORCECHAR: 'CNXFORCECHAR';
CONNXEXT: 'CONNXEXT';
COT: 'COT';
CASCADE: 'CASCADE';
CNXMEMORY: 'CNXMEMORY';
CONNXLENGTH: 'CONNXLENGTH';
COUNT: 'COUNT';
CASE: 'CASE';
//CNXNAME: 'CNXNAME';
CONNXNULLABLEINDEX: 'CONNXNULLABLEINDEX';
COUNT_DISTINCT: 'COUNT_DISTINCT';
CAST: 'CAST';
CNXPREFERENCE: 'CNXPREFERENCE';
CONNXNULLSPACE: 'CONNXNULLSPACE';
CREATE: 'CREATE';
CATALOG: 'CATALOG';
CNXRPC: 'CNXRPC';
CONNXOFFSET: 'CONNXOFFSET';
CURDATE: 'CURDATE';
CEILING: 'CEILING';
CNXSLEEP: 'CNXSLEEP';
CONNXOPTION: 'CONNXOPTION';
CURRENT: 'CURRENT';
CHAR: 'CHAR';
COEFVARPCT: 'COEFVARPCT';
CONNXPRECISION: 'CONNXPRECISION';
CURRENT_CATALOG: 'CURRENT_CATALOG';
CHAR_LENGTH: 'CHAR_LENGTH';
COEFVARPCTP: 'COEFVARPCTP';
CONNXPREFIX: 'CONNXPREFIX';
CURRENT_DATE: 'CURRENT_DATE';
COLUMN: 'COLUMN';
CONNXSCALE: 'CONNXSCALE';
CURRENT_SCHEMA: 'CURRENT_SCHEMA';
CHARACTER_LENGTH: 'CHARACTER_LENGTH';
COMMIT: 'COMMIT';
CONNXTYPE: 'CONNXTYPE';
CURRENT_TIME: 'CURRENT_TIME';
CHR: 'CHR';
CONCAT: 'CONCAT';
CONSTRAINT: 'CONSTRAINT';
CURRENT_TIMESTAMP: 'CURRENT_TIMESTAMP';
CLUSTER: 'CLUSTER';
CONNX_HASH: 'CONNX_HASH';
CONVERT: 'CONVERT';
CURTIME: 'CURTIME';
CNXFORCEBINARY: 'CNXFORCEBINARY';
CONNX_VERSION: 'CONNX_VERSION';
COS: 'COS';
COMPRESS:'COMPRESS';
CHARACTER:'CHARACTER';

// PART "D" RESERVED KEYWORDS
DATABASE: 'DATABASE';
DATEPART: 'DATEPART';
DAYOFWEEK: 'DAYOFWEEK';
DECODE: 'DECODE';
DESCRIPTION: 'DESCRIPTION';
DATE: 'DATE';
DATETIME: 'DATETIME';
DAYOFYEAR: 'DAYOFYEAR';
DEFAULT: 'DEFAULT';
DIFFERENCE: 'DIFFERENCE';
DATEADD: 'DATEADD';
DAY: 'DAY';
DBID: 'DBID';
DEGREES: 'DEGREES';
DISTINCT: 'DISTINCT';
DATEDIFF: 'DATEDIFF';
//DAYNAME: 'DAYNAME';
DELETE: 'DELETE';
DOUBLE: 'DOUBLE';
//DATENAME: 'DATENAME';
DAYOFMONTH: 'DAYOFMONTH';
DECIMAL: 'DECIMAL';
DESC: 'DESC';
DROP: 'DROP';
DISPLAY:'DISPLAY';
DECIDEON:'DECIDE ON';

// "E" THROUGH "J" RESERVED KEYWORDS
ELSE: 'ELSE';
EDITED: 'EDITED';
ESCAPE: 'ESCAPE';
FLOAT: 'FLOAT';
GROUP: 'GROUP';
INDEX: 'INDEX';
ENCRYPT: 'ENCRYPT';
FLOOR: 'FLOOR';
HAVING: 'HAVING';
INNER: 'INNER';
END: 'END';
FN: 'FN';
HEX: 'HEX';
INSERT: 'INSERT';
EXECUTE: 'EXECUTE';
FOR: 'FOR';
HOST: 'HOST';
INT: 'INT';
EXISTS: 'EXISTS';
FOREIGN: 'FOREIGN';
HOUR: 'HOUR';
INTEGER: 'INTEGER';
EXP: 'EXP';
FROM: 'FROM';
IF: 'IF';
INTO: 'INTO';
EXTRACT: 'EXTRACT';
//GETCURSORNAME: 'GETCURSORNAME';
IFEMPTY: 'IFEMPTY';
IS: 'IS';
FILE: 'FILE';
GETDATE: 'GETDATE';
IFNULL: 'IFNULL';
IS_NOT: 'IS NOT';
FIRST: 'FIRST';
GETVMSDATE: 'GETVMSDATE';
ILIKE: 'ILIKE';
JOIN: 'JOIN';
FIXED: 'FIXED';
FIND: 'FIND';
GRANT: 'GRANT';
IN: 'IN';
INCLUDE: 'INCLUDE';
IMMEDIATE:'IMMEDIATE';
INIT: 'INIT';

// "K" THROUGH "L" RESERVED KEYWORDS
KEY: 'KEY';
LAST: 'LAST';
LOCATE: 'LOCATE';
LONGVARBINARY: 'LONGVARBINARY';
KTHLARGEST: 'KTHLARGEST';
LCASE: 'LCASE';
LOG: 'LOG';
LOWER: 'LOWER';
KTHSMALLEST: 'KTHSMALLEST';
LEFT: 'LEFT';
LOG10: 'LOG10';
LTRIM: 'LTRIM';
KURTOSIS: 'KURTOSIS';
LENGTH: '*LENGTH';
LONG: 'LONG';
KURTOSISP: 'KURTOSISP';
LIKE: 'LIKE';
LONGNVARCHAR: 'LONGNVARCHAR';
LEAVING:'LEAVING';

// "M" THROUGH "N" RESERVED KEYWORDS
MAX: 'MAX';
MINUTE: 'MINUTE';
MULTIMODALCOUNT: 'MULTIMODALCOUNT';
NCLOB: 'NCLOB';
//NTUSERNAME: 'NTUSERNAME';
MEDIAN: 'MEDIAN';
NETRICS_MATCH: 'NETRICS_MATCH';
NULL: 'NULL';
MICROSOFT: 'MICROSOFT';
MODE: 'MODE';
NATIONAL: 'NATIONAL';
NOT: 'NOT';
MIDDLE: 'MIDDLE';
MONTH: 'MONTH';
MODULE:'MODULE';
NATURAL: 'NATURAL';
NOW: 'NOW';
NUMERIC: 'NUMERIC';
MIN: 'MIN';
//MONTHNAME: 'MONTHNAME';
NCHAR: 'NCHAR';
NOWAIT: 'NOWAIT';
NVARCHAR: 'NVARCHAR';

// "O" THROUGH "P" RESERVED KEYWORDS
OCTET_LENGTH: 'OCTET_LENGTH';
ORDER: 'ORDER';
POW: 'POW';
ODBC: 'ODBC';
OUTER: 'OUTER';
POWER: 'POWER';
OF: 'OF';
OUTPUT: 'OUTPUT';
PRECISION: 'PRECISION';
OJ: 'OJ';
PASSWORD: 'PASSWORD';
PRIMARY: 'PRIMARY';
ON: 'ON';
PI: 'PI';
PRIVILEGES: 'PRIVILEGES';
OPTION: 'OPTION';
PORT: 'PORT';
PRODUCT: 'PRODUCT';
OR: 'OR';
OCC: '*OCC';
POSITION: 'POSITION';
PUBLIC: 'PUBLIC';
PERFORM: 'PERFORM';
POS:'POS';

// "Q" THROUGH "R" RESERVED KEYWORDS
QUANTILE: 'QUANTILE';
RANGE: 'RANGE';
REPLACE: 'REPLACE';
ROUNDED: 'ROUNDED';
RMSERRORP: 'RMSERRORP';
QUARTER: 'QUARTER';
REAL: 'REAL';
RESTRICT: 'RESTRICT';
ROLLBACK: 'ROLLBACK';
QUARTILE1: 'QUARTILE1';
REFERENCES: 'REFERENCES';
REVERSE: 'REVERSE';
ROTATE: 'ROTATE';
QUARTILE3: 'QUARTILE3';
REMOTEPORT: 'REMOTEPORT';
REVOKE: 'REVOKE';
ROUND: 'ROUND';
RADIANS: 'RADIANS';
REMOTESERVER: 'REMOTESERVER';
RIGHT: 'RIGHT';
ROWCOUNT: 'ROWCOUNT';
RAND: 'RAND';
REPEAT: 'REPEAT';
RMSERROR: 'RMSERROR';
RTRIM: 'RTRIM';
ROUTINE: 'ROUTINE';
REPOSITION:'REPOSITION';

// "SA" THROUGH "SP" RESERVED KEYWORDS
SCHEMA: 'SCHEMA';
SETREALTIMEDATABUFFER: 'SETREALTIMEDATABUFFER';
SKEWNESS: 'SKEWNESS';
SORTFIRST: 'SORTFIRST';
SECOND: 'SECOND';
//SHORTNAME: 'SHORTNAME';
SKEWNESSP: 'SKEWNESSP';
SORTLAST: 'SORTLAST';
SELECT: 'SELECT';
SIGN: 'SIGN';
SLIKE: 'SLIKE';
SORTMIDDLE: 'SORTMIDDLE';
SEQNO: 'SEQNO';
SIN: 'SIN';
SMALLINT: 'SMALLINT';
SOUNDEX: 'SOUNDEX';
SET: 'SET';
SIZE: 'SIZE';
SOME: 'SOME';


// "SQL_" RESERVED KEYWORDS
SQL_BINARY: 'SQL_BINARY';
SQL_INTEGER: 'SQL_INTEGER';
SQL_TIMESTAMP: 'SQL_TIMESTAMP';
SQL_TSI_QUARTER: 'SQL_TSI_QUARTER';
SQL_BIT: 'SQL_BIT';
SQL_LONGVARBINARY: 'SQL_LONGVARBINARY';
SQL_TINYINT: 'SQL_TINYINT';
SQL_TSI_SECOND: 'SQL_TSI_SECOND';
SQL_CHAR: 'SQL_CHAR';
SQL_LONGVARCHAR: 'SQL_LONGVARCHAR';
SQL_TSI_DAY: 'SQL_TSI_DAY';
SQL_TSI_WEEK: 'SQL_TSI_WEEK';
SQL_DATE: 'SQL_DATE';
SQL_NUMERIC: 'SQL_NUMERIC';
SQL_TSI_FRAC_SECOND: 'SQL_TSI_FRAC_SECOND';
SQL_TSI_YEAR: 'SQL_TSI_YEAR';
SQL_DECIMAL: 'SQL_DECIMAL';
SQL_REAL: 'SQL_REAL';
SQL_TSI_HOUR: 'SQL_TSI_HOUR';
//SQL_USER_NAME: 'SQL_USER_NAME';
SQL_DOUBLE: 'SQL_DOUBLE';
SQL_SMALLINT: 'SQL_SMALLINT';
SQL_TSI_MINUTE: 'SQL_TSI_MINUTE';
SQL_VARBINARY: 'SQL_VARBINARY';
SQL_FLOAT: 'SQL_FLOAT';
SQL_TIME: 'SQL_TIME';
SQL_TSI_MONTH: 'SQL_TSI_MONTH';
SQL_VARCHAR: 'SQL_VARCHAR';
STOP:'STOP';

// "SQ" THROUGH "SZ" RESERVED KEYWORDS
SQRT: 'SQRT';
STRCMP: 'STRCMP';
SUBSTRING: 'SUBSTRING';
SUPPRESSION: 'SUPPRESSION';
STDDEV: 'STDDEV';
STUFF: 'STUFF';
SUM: 'SUM';
SWITCH: 'SWITCH';
STDDEVP: 'STDDEVP';
SUBSTR: 'SUBSTR';
SUM_DISTINCT: 'SUM_DISTINCT';

// "T" THROUGH "U" RESERVED KEYWORDS
TABLE: 'TABLE';
TIMESTAMPADD: 'TIMESTAMPADD';
TRIMEAN: 'TRIMEAN';
UPDATE: 'UPDATE';
TABLEPHYSICALLYEXISTS: 'TABLEPHYSICALLYEXISTS';
TIMESTAMPDIFF: 'TIMESTAMPDIFF';
TRUNCATE: 'TRUNCATE';
UPPER: 'UPPER';
TAN: 'TAN';
TINYINT: 'TINYINT';
UCASE: 'UCASE';
UQINDEX: 'UQINDEX';
THEN: 'THEN';
TO: 'TO';
UNION: 'UNION';
USE: 'USE';
TIME: 'TIME';
TPADATEOFBIRTH: 'TPADATEOFBIRTH';
UNIQUE: 'UNIQUE';
//USER: 'USER';
TIMESTAMP: 'TIMESTAMP';
TRIM: '*TRIM';
UNSIGNED: 'UNSIGNED';
UBOUND:'*UBOUND';
TOP:'TOP';

// "V" THROUGH "Z" RESERVED KEYWORDS
VALUES: 'VALUES';
VARIANCEP: 'VARIANCEP';
VIRTUAL: 'VIRTUAL';
WITH: 'WITH';
VARBINARY: 'VARBINARY';
VARYING: 'VARYING';
WEEK: 'WEEK';
WORK: 'WORK';
VARCHAR: 'VARCHAR';
VENDOR: 'VENDOR';
WHEN: 'WHEN';
//XPUSERNAME: 'XPUSERNAME';
VARIANCE: 'VARIANCE';
VIEW: 'VIEW';
VAL: 'VAL';
WHERE: 'WHERE';
YEAR: 'YEAR';


// Additional common tokens
LPAREN: '('; // Left parenthesis
RPAREN: ')'; // Right parenthesis
LBRACE: '{'; // Left curly brace
RBRACE: '}'; // Right curly brace
LBRACK: '['; // Left square bracket
RBRACK: ']'; // Right square bracket
DOT: '.'; // Dot for access
COMMA: ','; // Comma
PLUS: '+'; // Plus
MINUS: '-'; // Minus
STAR: '*'; // Multiplication
SLASH: '/'; // Division
GT: '>'; // Greater than
LT: '<'; // Less than
EQ: '='; // Equal
NEQ: '!='; // Not equal
LOGICAL_AND: '&&'; // Logical AND
LOGICAL_OR: '||'; // Logical OR
LOGICAL_NOT: '!'; // Logical NOT
INC: '++'; // Increment
DEC: '--'; // Decrement
COLON: ':'; // Colon
QMARK: '?'; // Question mark
BSLASH: '\\'; // Backslash
MOD: '%'; // Modulo
PIPE: '|'; // Pipe
AMP: '&'; // Ampersand
CARET: '^'; // Caret for XOR
TILDE: '~'; // Tilde for bitwise NOT
SEMICOLON: ';';
READ: 'READ';
END_READ: 'END-READ';
MOVE: 'MOVE';
WHILE: 'WHILE';
END_IF: 'END-IF';

// Definitions of IDs, constants, and literals
// ID: [a-zA-Z#][a-zA-Z0-9.#_-]*;
//NEWLINE: [\r\n]+;
//ID: [a-zA-Z#*+][a-zA-Z0-9.#_/:-]*;
DATE_MASK: [A-Z0-9][A-Z0-9] '/' [A-Z0-9]+ ('/' [A-Z0-9]+)*;
ID
  : [a-zA-Z#] [a-zA-Z0-9.#_/:-]*          // normal names, #SOMA, #T, #I, etc.
  | ('*+' | '*' | '+')     [a-zA-Z0-9.#_/:-]+          // *OPSYS, +FOO — but NOT bare * or +
  ;
NUM_X: [0-9]+ 'X';
ALPHANUMERIC: [0-9][a-zA-Z]+;
WORD: [a-zA-Z]+;
NUMBER: ('-')? [0-9]+ ([.,]? [0-9]+)?;
DECIMAL_NUMBER: [0-9]+ (',' [0-9]+);
NUMBER_WITH_DASH:[0-9]+ ('-' [0-9]+);
//STRING: '\'' ( ~[\r\n\\'] | '\\' . )* '\'';
STRING: '\'' ( ~[\r\n\\'] | '\\' . | '\'\'' )* '\'';
PRINT_STRING     : '"' .*? '"';
BOOLEAN: 'TRUE' | 'FALSE';
SIMPLE_ID: [A-Za-z0-9_-]+;
VARNAME : (Chr | Let) (Chr | Let | [0-9])*;
fragment Chr : [-_$#@]+;
fragment Let : [a-zA-Z]+;
fragment Ws : (' ' | '\t' | '\n' | '\r');
//fragment Asterik: [*];
VARIABLE:[a-zA-Z]+;
NUM:'0'..'9' ;
BLOCK_COMMENT   : '/*' ~[\r\n]* -> skip;
WS: [ \t\r\n]+ -> skip;
BLOCK_LINE_COMMENT: STAR MINUS+ STAR -> skip;
//ASTERISK_COMMENT:  '*' [\r\n]* -> skip;
//LINE_COMMENT
//  : ( {getCharPositionInLine() == 0}? [ \t]* '*'   // first line or line start with optional spaces
//    | '\r'? '\n' [ \t]* '*'                        // after newline with optional spaces
//    )
//    ~[\r\n]* -> skip
//  ;
//LINE_COMMENT: [\n] [ \t]* '*' ~[\n]* -> skip;
//NEW_LINE: [\r\n]+ -> skip;
//COMMENT: '* ' ~[\r\n]* (~[a-zA-Z])? -> skip;
//LINE_COMMENT: {getCharPositionInLine() == 0}? '*' ~[\n]* -> skip;
//DEFINE_BLOCK: 'DEFINE DATA' .*? 'END-DEFINE' -> skip ;// Skip everything between 'DEFINE' and 'END-DEFINE'
//LINE_COMMENT: ('**')+  ~[\r\n]* -> skip;
//SKIP_INPUT_LINE: 'INPUT' ~[\r\n]* -> skip;
//BLOCK_LINE_COMMENT: '*' '-'+ '*' -> skip;
IMPORTANT_COMMENT: '******' ~[\r\n]* '******' -> skip;
SPACE_DOT:'  . ' ~[\r\n]* (~[a-zA-Z])? -> skip;
INDENT  : (' ') ;
// Updated Parser Rules

program: (naturalStatement)+ EOF?
       | EOF;

naturalStatement: stmtScope
         | stmt
         ;

stmtScope: notMainCode
         | repeatStatement
         | loopStmt
         | forLoopStatement
         | atStatements
         | decideOnStatement
         | decideForStatement
         | loopStatement
         | findSqlStatement
         | findStatement
         | sortStatement
         ;

notMainCode: defineDataStatement
          | defineFunctionStatement
          | defineWindowStatement
          | subroutineStatement;


// https://documentation.softwareag.com/one/9.3.2/en/webhelp/one-webhelp/natmf/sm/sm-over.htm
stmt:  readSqlStatement
         | readStatement
         | ifReportModeStmt
         | ifStatement
         | addStatement
         | definePrinterStatement
         | assignStatement
         | moveStatement
         | updateStatement
         | displayStatement
         | endStatement
         | includeStatement
         | callnatStatement
         | callStatement
         | writeStatement
         | inputStatement
         | escapeStatement
         | subtractStatement
         | divideStatement
         | resizeStatement
         | compressStatement
         | acceptRejectStatement
         | limitStatement
         | examineStatement
         | setStatements
         | resetStatement
         | redefineStatement
         | computeStatement
         | obtainStatement
         | ignoreStatement
         | performStatement
         | stopStatemnet
         | formatStatement
         | onErrorStatement
         | printStatement
         | writeWorkFileStatement
         | insertStatement
         | deleteSqlStatement
         | newPageStatement
         | fetchStatement
         | getStatement
         | backoutStatement
         | endTransactionStatement
         | storeStatement
         | deleteStatement
         | reinputStatement
         | functionCall
         | transactionStatement
         | dataOperationStatement
         | typeCastingStatement
         | dateFunctionStatement
         | databaseOperationStatement
         | encryptionOperationStatement
         | aggregationStatement
         | dataManipulationStatement
         | statisticalFunctionStatement
         | schemaOperationStatement
         | tableOperationStatement
         | textManipulationStatement;

readSqlStatement:  (ID)? READ readOptions+ (byStatement)? (offSet)* ;
readStatement: (ID)? READ readOptions+ (byStatement)? (offSet)* (naturalStatement)* END_READ
           ;
readOptions: ID
           | 'RECORDS' 'IN'? 'FILE'? (NUMBER)?
           | 'IN' ID
           | multiFetchClause
           | noOfLines
           | 'NUMBER'
           | sequenceOptions
           ;
multiFetchClause: 'MULTI FETCH' ('ON'|'OFF')? NUMBER?
           ;
byStatement: 'DESCENDING' (BY|'WITH') ID
           | 'BY NAME'
           | 'BY KEY' ID?
           | 'BY' ID
           ;

noOfLines: 'ALL'
         | '('NUMBER')'
         ;

offSet: 'STARTING FROM' operand ('ENDING AT' operand)?
      | 'FROM' operand 'TO'? operand?
      | 'WITH' WORD '=' ID
      | 'WITH' expr
      | 'STARTING WITH ISN =' ID
      ;

sequenceOptions: 'VARIABLE' ID;

findSqlStatement: (ID)? FIND findSqlOptions? readOptions+ (byStatement)?
(offSet)?;

findStatement:(ID)? FIND  readOptions+ (byStatement)?
(offSet)? (stmt)* ('END-FIND'|('LOOP''('ID')'))?;

sortStatement: (ID)? SORT BY ID USING ID+;

findSqlOptions: 'FIRST' | 'NUMBER' | 'UNIQUE';

actionStatement: naturalStatement+ ;

addStatement: ADD addOptions+ TO addOptions+;

addOptions: NUMBER
          | ID
          | '('ID')'
          ;
moveStatement: MOVE EDITED
               operand LPAREN editMask RPAREN?
               (TO operand)? LPAREN editMask RPAREN?                             # moveA
               | MOVE moveOptionStmt? operand+ TO safeOperands                   # moveB
               | MOVE ('LEFT' | 'RIGHT') 'JUSTIFIED'? operand TO safeOperands    # moveC
               ;

//safeOperands: operand ({ System.out.println("More operands: " + getVocabulary().getSymbolicName(_input.LA(1))); } operand)*;
safeOperands: operand ({ _input.LT(1).getText().endsWith(".") == false }? operand)*;

operand: ID'('DECIMAL_NUMBER')'
       | ID('('NUMBER ( ',' NUMBER)*?')')
       | ID('/' ID)*?
       | ID('('ID '+' NUMBER ')')
       | ID('(' ID(',' ID)*? ')')?
       | NUMBER
       | WORD
       | '(' ID ')'
       | functionCall
       | PRINT_STRING
       | 'H'? STRING
       | '-'
       | 'TRUE'
       | 'FALSE'
       | '(*)'
       | '(' NUMBER(':'NUMBER)? ')'
       | '(' NUMBER(','NUMBER)*? ')'
       | '(' (ID '=' ID) (ID '=' ('NE' | ID))')'
       ;

editMask:  EM EQ ID
         | EM EQ DATE_MASK
         | EM EQ 'MM/DD/YY'
         | EM EQ 'MM/DD/YYYY'
         | EM EQ 'MMDDYY'
         | EM EQ 'MMDDYYYY'
         | EM EQ NUMBER
         | EM EQ 'HH:II:SS'
         | EM EQ '+'? 'Z.ZZZ.ZZZ.ZZZ.ZZ9,99'
         ;

moveOptionStmt: EDITED | ROUNDED | 'BY NAME'
           | BY POSITION | ALL ;
updateStatement: UPDATE (LPAREN ID RPAREN)? WITH? updateSetStatements? SAME? RECORD?
               whereCondition? ;

updateSetStatements: WORD SET STAR
                   | ID SET STAR
                   |ID SET assignStatement;
displayStatement: DISPLAY (displaystmts)+;
displaystmts: PRINT_STRING
             | STRING
             | SIMPLE_ID
             | ALPHANUMERIC
             | ID '(*)'
             | ID
             ;
endStatement: END
            | END ID;
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

fieldDefinition: fieldNumber? REDEFINE? fieldIdentifier+
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
            | ID;

dataType: '(' dataTypeSpecifier ')'
        | VIEW OF ID
        | ID;

dataTypeSpecifier
        : NUM_X
        | ID '(' dataTypeSpecifier ')' (NUM_X)?
        | ID '/' NUMBER (':' (NUMBER | ID))?
        |(NUMBER':'NUMBER)(','dataTypeSpecifier+)?
        | DECIMAL_NUMBER(':'NUMBER)
        | ID (','NUMBER ('/' NUMBER (':' (NUMBER | ID))?)?)
        | ID
        | NUMBER
        ;

definePrinterStatement: 'DEFINE PRINTER' definePrinterOptions+;

definePrinterOptions: '(' NUMBER')'
                    | 'OUTPUT'
                    | STRING
                    | ID
                    ;

defineFunctionStatement: 'DEFINE'
'FUNCTION' defineFunctionOptions+ naturalStatement+ 'END-FUNCTION';

defineFunctionOptions: ID
          | 'RETURNS' ID
          | '('ID')'
          ;
defineWindowStatement:'DEFINE WINDOW' ID ('SIZE' sizeOptions)?
('BASE' baseOptions)? ('CONTROL' controlOptions)?
('FRAMED' framedOptions)?
;
sizeOptions: NUMBER(STAR NUMBER)?
           | 'AUTO'
           | 'QUARTER'
           | 'CURSOR'
           ;

baseOptions: NUMBER '/' NUMBER;

ifReportModeStmt: IF expr+ (THEN)? (ifReportOptions)
   elseOptions?
    ;

elseOptions: ELSE (ifReportOptions)?
        ;
ifReportOptions: naturalStatement
               | doStatement
               ;

ifStatement: IF expr+ (THEN)? (ifOptions)+
   (ELSE (ifOptions)+ )? END_IF
    ;

ifOptions:  naturalStatement+
         ;

controlOptions: 'WINDOW'
              | 'SCREEN'
              ;

framedOptions: 'ON'
             | 'OFF'
             ;

expr
  : LPAREN expr+ RPAREN
  | operand (('=' | 'EQ' | 'IS') operand ('THRU' operand)?)
  | 'NOT' expr
  | 'NOT UNIQUE' expr
  | expr (POWER | STAR | SLASH | AND | PLUS | MINUS | OR | CONCAT | EQ | 'IS' | '<>' | '<' | '>'| '<='| '>=' | LT | 'LE' | 'GE'| 'GT' | 'NE' | ':=' | '=') expr
  | 'NOT' expr
  | functionCall
  | varnameref
  | exprconst
  ;

exprconst  : NUMBER
           | DECIMAL_NUMBER
           | STRING
           | ('TRUE' | 'FALSE')
           | PRINT_STRING
           | ID '(*)'
  ;

maskFunctionCall:
        MASK '(' STRING '.'* ')'
       | MASK '(' NUMBER ')';

varnameref:ID '('NUMBER')'
         | ID'(' NUMBER':'NUMBER ')'
         | ID
         | ID('(' ID ')')
         | ID(',' ID)*?
         | VARNAME
         | DECIMAL_NUMBER
  ;


includeStatement: INCLUDE ID;
callStatement: 'CALL' STRING (variable+)?
          ;

callnatStatement: CALLNAT
           STRING (variable+)?
          ;

variable: ID'('NUMBER')'
        | ID'('ID')'
        | ID '(*)'?
        | STRING
        ;

writeStatement: 'WRITE' (LPAREN NUMBER RPAREN)? writeLine+;

writeLine: writeLineStatements+;

writeLineStatements: spacing
        | title
        | STRING
        | '('expr')'
        | ID
        | LPAREN editMask RPAREN
        | lineSeparator
        | '('ID')'
        | 'LEFT' 'JUSTIFIED'?
        | SIMPLE_ID
        | '(ES=ON)'
        |'-'
        ;
spacing : ALPHANUMERIC
		;
title: 'TITLE'
     | 'NOTITLE'
     | 'NOHDR'
     ;

lineSeparator: '//' | '/' ;

/*
LINK: https://documentation.softwareag.com/natural/nat912mf/sm/input.htm?hi=input+inputs

Screen Mode
In screen mode, execution of the INPUT statement results in the display of a screen according to the fields and positioning notation specified.
The message line of the screen is used by Natural for error messages.

...

Non-Screen Modes
The INPUT statement may be used for an operation on line-oriented devices or for the processing of batch input from sequential files.
The same map layouts as defined for screen mode operation can also be processed in non-screen mode.

...

One or more parameters, enclosed within parentheses, may be specified immediately after the INPUT statement or an element being displayed.

INPUT           'Text'           VARI          /*    Text 1234
INPUT (PM=I)    'Text'           VARI          /*    Text 4321
INPUT           'Text' (PM=I)    VARI (PM=I)   /*    txeT 4321
INPUT           'Text' (PM=I)    VARI          /*    txeT 1234
...

If more than one parameter is specified, one or more blanks must be present between each entry. An entry may not be split between two statement lines.

PARAMETERS:
AD	Attribute Definition
AL	Alphanumeric Length for Output
BX	Box Definition
CD	Color Definition
CV	Control Variable
DF	Date Format
DL	Display Length for Output
DY	Dynamic Attributes
EM	Edit Mask
EMU	Unicode Edit Mask
FL	Floating Point Mantissa Length
HE	Helproutine
IP	Input Prompting Text
LS	Line Size
MC	Multiple-Value Field Count
MS	Manual Skip
NL	Numeric Length for Output
PC	Periodic Group Count
PM	Print Mode
PS	Page Size
SB	Selection Box
SG	Sign Position
ZP	Zero Printing
*/
inputStatement: INPUT windowOptions? inputOptions? inputLineStatements? withTextOption?
               markOption? mapOption?;

//inputHeader
//    : 'INPUT' inputOptions* (inputElements+)?
//    ;

inputOptions
    : 'NO' 'ERASE'
    | 'USING' 'MAP' ID
    | 'MARK'
    | LPAREN attributeOptions RPAREN
    ;
//
attributeOptions
    : attributeAssignment (COMMA? attributeAssignment)* COMMA?
    ;

attributeAssignment
    : EQ
    | editMask
    | ID EQ attributeValue
    | ID
    ;
//
attributeValue
    : ON
    | OFF
    | ID
    | ID STRING
    | STRING
    | DATE_MASK
    | NUMBER
    ;
//
//inputElements
//    : inputLayoutLine+ 'END'? // handles lines like "001T +LABEL (AD=ODL) /*.01U012 A012 ."
//    ;
//
//inputLayoutLine
//    : layoutLineNumber layoutLineType layoutContent+ (inputOptions)?
//    ;
//
//layoutLineNumber
//    : NUMBER; // usually 3-digit like 001, 002
//
//layoutLineType
//    : 'T' // Text
//    | 'V' // Variable (input)
//    | 'B' // Box or border
//    | 'M' // Message line
//    | 'H' // Header
//    ;
//
//layoutContent
//    : ID
//    | '+' ID               // literal with + prefix
//    | '-' ID               // literal with - prefix
//    | STRING
//    | PRINT_STRING
//    | COLON
//    ;

inputLineStatements: (lineSeparator | inputLayoutLine)+;// 'T' STRING LPAREN attributeOptions RPAREN;//inputLayoutLine+;

inputLayoutLine
    : layoutLineNumber? layoutContent+ (inputOptions)?
    ;

layoutLineNumber
    : SIMPLE_ID
    | NUM_X;

layoutContent
    : ID (LPAREN NUMBER RPAREN)?
    | '+' ID               // literal with + prefix
    | '-' ID               // literal with - prefix
    | STRING
    | PRINT_STRING
    | COLON
    ;

//inputStmts:spacing
//        | STRING
//        | LPAREN expr+ RPAREN
//        | ID
//        | LPAREN editMask RPAREN
//        | lineSeparator
//        | '('ID')'
//        | 'LEFT' 'JUSTIFIED'?
//        | SIMPLE_ID
//        ;

withTextOption: 'WITH'? 'TEXT' ID;
mapOption: 'USING'? 'MAP' (ID|STRING);
windowOptions: 'WINDOW' '=' STRING;
subtractStatement: 'SUBTRACT' operand FROM operand;
divideStatement
    : DIVIDE operand INTO operand (REMAINDER operand)?
    ;escapeStatement:'ESCAPE' escapeOptions?;

escapeOptions: TOP REPOSITION?
             | BOTTOM ('(' ID ')')? IMMEDIATE?
             | ROUTINE IMMEDIATE?
             | MODULE IMMEDIATE?
             ;

repeatStatement: (ID)? REPEAT  (naturalStatement)+ (repeatOptions expr)? (END_REPEAT | END)
               |(ID)? REPEAT (repeatOptions expr)?  (naturalStatement)+ (END_REPEAT | END | loopStmt) ;

repeatOptions: 'UNTIL'
             | 'WHILE'
             ;

labelStatement: ID '.';

forLoopStatement: (ID)? FOR expr 'FROM'? expr 'TO'? expr
              ('STEP' '-1')? (naturalStatement)+
              ('END-FOR' | loopStmt)
              ;

loopStmt: 'LOOP' ('(' ID ')')?;

resizeStatement: 'RESIZE' resizeOptions? ID('(' ID ')')? TO ID('(' ID ')')?;
resizeOptions: 'DYNAMIC'
             |  'ARRAY'
             ;

compressStatement: COMPRESS 'NUMERIC'? 'FULL'? compressOptions+ ('TO' | INTO) compressOptions+;

compressOptions: ID ('/' ID)*?
                | ID ('(' ID (','ID)*? ')')?
                | ID ('(' ID (',' STAR)*? ')')?
                | ID ('(' NUMBER ')')?
                | PRINT_STRING
                | STRING
                | 'LEAVING NO' 'SPACE'?
                | LEAVING 'SPACE'?
                | functionCall
                | '('NUMBER')'
                | '('ID')'
                | NUMBER
                | ID('(*)')
                ;
acceptRejectStatement: stmtOptions IF? expr?;
stmtOptions: 'ACCEPT'
           | 'REJECT'
           ;
limitStatement: 'LIMIT' NUMBER;

atStatements: blockStart (LPAREN NUMBER RPAREN)? DO? (naturalStatement)+ (blockend | DOEND);

blockStart: 'AT END OF DATA'
          | 'AT END OF PAGE'
          | 'AT START OF DATA'
          | 'AT TOP OF PAGE'
          | 'AT BREAK'
          ;
blockend: 'END-ENDDATA'
       | 'END-ENDPAGE'
       | 'END-START'
       | 'END-TOPPAGE'
       | 'END-BREAK'
       ;
examineStatement:'EXAMINE' examineOptions+;

examineOptions: 'FULL' 'VALUE'? 'OF'?
              | FOR 'FULL'? 'VALUE'? 'OF'?
              | ID('('NUMBER')')
              | ID('(*)')?
              | 'GIVING'? 'POSITION' 'IN'?
              | 'GIVING'? 'NUMBER' 'IN'?
              | 'GIVING'? 'LENGTH' 'IN'?
              | 'GIVING'? 'INDEX' 'IN'?
              | INTO (UPPER | LOWER) CASE?
              | STRING
              | PRINT_STRING
              |'(' ID ')'
              | deleteReplaceClause
              ;
deleteReplaceClause: 'AND'? translateOptions
              | 'AND'? 'DELETE' 'FIRST'?
              | 'AND'? 'REPLACE' 'FIRST'? 'WITH'? ('FULL VALUE OF')? STRING?
              | 'WITH' 'DELIMITER'
              ;
translateOptions: 'TRANSLATE INTO UPPER CASE'
                | 'TRANSLATE INTO LOWER CASE';

setStatements: SET assignmentStatement
             | (ID'.')? SET TIME
             | (ID'.')?'SETTIME'
             | SET 'CONTROL' setOptions?
             | SET 'GLOBALS' setOptions?
             | SET KEY (ID'='(ID|STRING))? (ID '=')? setKeyOptions?
             | SET 'WINDOW' setWindowOptions?
             ;

setOptions: STRING
           | ID '=' NUMBER
           ;
setKeyOptions: 'ALL' | 'ON' | 'OFF' | 'COMMAND ON'
          | 'COMMAND OFF' | 'NAMED' STRING| 'NAMED OFF';

setWindowOptions: ID | 'OFF';

resetStatement: 'RESET' resetOptions+;

resetOptions: WORD
           | ID '(' dataTypeSpecifier+ ')' (NUM_X)?
           | ID '(' ID '(' ID (',' NUMBER)*? ')' ')'
           | ID '(' (ID '(' ID ')')* ')'
           | '(' ID (',' NUMBER)*? ')'
           | '(' NUMBER_WITH_DASH ID ')'
           | ID ('/' ID)?
           | ID '/' 'N'
           | STRING
           | ID ('('NUMBER')')
           | ID('/' ID)? ('(' NUMBER ':' NUMBER')')?
           | '(' ID ')'
           | '(' (ID '(' ID ')')+ ')'
           | ID('(' ID ')')
           | ID
           | expr
           | '(*)'
           |'('NUMBER ':' NUMBER')'
           ;

//redefineStatement: 'REDEFINE' resetOptions+
//                 ;

redefineOption
    : WORD
    | ID '(' dataTypeSpecifier+ ')' (NUM_X)?
    | ID                                 // Simple ID
    | ID '(' NUMBER ')'                  // ID with a single number
    | ID '(' NUMBER ':' NUMBER ')'       // ID with a number range
    | ID '/' ID                          // ID with a slash
    | ID '/' 'N'
    | STRING
    | '(' ID ')'                         // Parenthesized ID
    | '(' NUMBER ':' NUMBER ')'          // Parenthesized number range
    | '(' NUMBER_WITH_DASH ID ')'        // Parenthesized with dash
    | expr                               // General expression
    | '(*)'                              // Asterisk placeholder
    | redefineArrayStructure             // New rule for array structures
    ;

redefineArrayStructure
    : ID '(' redefineArrayStructureOptions+ ')'
    ;

redefineArrayStructureOptions
    : ID                                 // Simple ID within array
    | '(' NUMBER ':' NUMBER ')'          // Number range within array
    | NUMBER                             // Just a number within array
    | ID '/' NUMBER                      // ID with a slash and number
    | ID '/' 'N'     ATE
    ;


// ... update redefineStatement to use the new rule
redefineStatement: 'REDEFINE' redefineOption+;

computeStatement: computeOptions 'ROUNDED'?
             computeExpr;

computeOptions: 'COMPUTE'
              | 'ASSIGN'
              ;
computeExpr: ID '(' ID ')' '=' expr
        | ID ':=' expr
        | ID '=' expr
        | assignStatement
        | functionCall;

ignoreStatement:'IGNORE' ID?;

performStatement: PERFORM performId;

performId: ID | SIMPLE_ID;

subroutineStatement: 'DEFINE' 'SUBROUTINE'? subroutineOptions+ (naturalStatement)+
('END-SUBROUTINE'| returnStatement);

subroutineOptions: ID
                 | SIMPLE_ID
                 | '(' ID (',' ID)*? ')'
                 | '[''-'']'
                 ;

decideOnStatement: DECIDEON
        decisionbasis operand2*?  decideOptions? 'END-DECIDE';
decisionbasis: 'FIRST' 'VALUE'? 'OF'? (ID | functionCall | ID'(' NUMBER ')')
             | 'EVERY' 'VALUE'? 'OF'? (ID | functionCall)
             ;
operand2: 'VALUE' decideValueOptions
 (naturalStatement)+;

decideValueOptions: ID
                  | functionCall
                  | STRING(','STRING)*?
                  | NUMBER(','NUMBER)*?
                  ;
decideOptions: 'ANY' 'VALUE' (naturalStatement)+
             | 'ALL' 'VALUE' (naturalStatement)+
             | 'NONE' 'VALUE'? ((naturalStatement)+)?
             ;

decideForStatement: 'DECIDE FOR' ('FIRST'|'EVERY') 'CONDITION' (whenCond+)? 'END-DECIDE';

whenCond: 'WHEN' expr (naturalStatement)+
        | 'WHEN ANY' (naturalStatement)+
        | 'WHEN ALL' (naturalStatement)+
        | 'WHEN NONE' (naturalStatement)+
        ;
stopStatemnet: STOP;

formatStatement: 'FORMAT' (LPAREN NUMBER RPAREN)? formatOptions+;

//formatOptions: (NUMBER)
//             | '(' NUMBER ')'
//             | WORD '=' 'OFF'
//             | WORD '=' NUMBER
//             | ID '=' 'ON'
//             | ID '=' NUMBER
//             | ID '=' ID;

formatOptions
    : ID EQ NUMBER
    | ID EQ STRING
    | ID EQ (ON | OFF)
    ;


onErrorStatement: 'ON ERROR' (naturalStatement)+ 'END-ERROR';

printStatement:'PRINT' printLine+;
printLine: printLineStatements;
printLineStatements:spacing
        | title
        | STRING
        | '('expr')'
        | ID
        | LPAREN editMask RPAREN
        | lineSeparator
        | '('ID')'
        ;
writeWorkFileStatement: 'WRITE WORK' 'FILE'? NUMBER 'VARIABLE'? ID;

fetchStatement: 'FETCH' fetchOptions? fetchOperands+;

fetchOptions
    : REPEAT
    | RETURN
    ;

fetchOperands: ID
            | STRING
            ;

getStatement:(ID)? 'GET' 'IN'? 'FILE'? getOptions+;

getOptions: '('NUMBER')'
           | ID('('ID')')?
          ;
backoutStatement:'BACKOUT' 'TRANSACTION'?;

endTransactionStatement:'END' 'OF'? 'TRANSACTION' (STRING|ID)?;

storeStatement: (ID)? 'STORE' 'RECORD'? 'IN'? 'FILE'? ID
                storeOptions+?;

storeOptions: 'WITH' assignStatement
| 'USING'? 'SAME'
'RECORD'? 'AS'? 'STATEMENT'?
('(' ID ')')?;

insertStatement: 'INSERT INTO' ID insertOptions+?;

insertOptions: '(*)' valuesClause
             | '(' ('NAME' | WORD) (',' WORD)* ')'
             ;

valuesClause: 'VALUES' '(' 'VIEW' ID ')'
            | 'VALUES' '(' ID (',' ID)')'
            ;

deleteSqlStatement: 'DELETE FROM' ID whereCondition?;

whereCondition: 'WHERE' (('NAME' | ID )'=' STRING)+
              | 'WHERE CURRENT OF CURSOR'
              | 'WHERE' ID;

deleteStatement: 'DELETE' 'RECORD'? 'IN'? 'STATEMENT'? '('ID')'?;

reinputStatement: 'REINPUT' 'FULL'? (ID | STRING) (markOption+)? alarmOption?;

markOption: '-' STRING
          | 'MARK' ID('(' ID ')')?
          ;

alarmOption: 'ALARM';

doStatement: DO naturalStatement+ DOEND;

returnStatement: RETURN (returnOptions)?;

returnOptions: ID;

newPageStatement:'NEWPAGE' '('NUMBER')';

obtainStatement:'OBTAIN' obtainOptions+;

obtainOptions: WORD
           | ID '(' dataTypeSpecifier+ ')';

scanFunctionCall: SCAN STRING;

// Function calls including new statistical, time, and text manipulation functions
functionCall: (maskFunctionCall | scanFunctionCall | functionName functionCallstmts);



functionCallstmts: '(' (expr
(',' expr)*)? ')'
| '(' (expr functionCallstmts*?) ')'
| (',' NUMBER*)
;
functionName: ABS | ACOS | ASIN | ATAN | ATAN2
             | ARRAYSEARCH | AVEDEVMEDIAN | AVG | AVG_DISTINCT
             | BIN | BIT_LENGTH | BIBDATA
             | COT | CEILING | COS
             | DECODE | DEGREES | DISTINCT | DATEPART | DAYOFWEEK | DAYOFYEAR
             | ENCRYPT | FLOAT | FLOOR | FN | HEX
             | LOG | LOG10 | LCASE | LOWER | LENGTH | LIKE | LAST
             | MAX | MIN | MEDIAN | MODE | MONTH
             | OCTET_LENGTH | OCC | POW | POWER | POS
             | QUANTILE | QUARTER | QUARTILE1 | QUARTILE3 | RADIANS | RAND
             | ROUND | REPLACE | REVERSE
             | SQRT | STRCMP | SUBSTR | SUBSTRING | SUM | STDDEV
             | TIMESTAMPADD | TIMESTAMPDIFF | TRUNCATE | TRIMEAN |
               TRIM|  UBOUND | VAL | MASK;

conditionalStatement: (AND | ANY | AS | ALL | IF | IS | IS_NOT
| IFEMPTY | IFNULL) expression;

assignmentStatement: ID ':=' expr
                   | ID '=' ID
                   | (ID'/' ID) ':=' (STRING| ID)
                   | ID '=' expr
                   | ID '(' (NUMBER | ID) ')' '=' expr
                   | ID '(' (NUMBER | ID) ')' ':=' expr
                   | ID ('(' NUMBER ':' NUMBER ')')? ':=' ID ('(' NUMBER ':' NUMBER ')')?
                   | ID ('(' NUMBER ':' NUMBER')')? ':=' ID ('(' NUMBER ':' NUMBER')')?
                   | ID('(' ID ')') ':=' expr
                   ;

assignStatement
  : ID assignStmtOptions? equalOperand expr labelRef?
  ;

labelRef: LPAREN ID RPAREN;

assignStmtOptions
    : expr
    | '(' NUMBER_WITH_DASH ID ')'
    | '(' NUMBER STAR ID ')' (PLUS ID)*
    | '(' ID STAR NUMBER ')' (PLUS ID)*
    | ID STAR NUMBER  (PLUS ID)*
    | ID (SLASH ID)?
    | ID (SLASH NUMBER)?
    | ID SLASH 'N'
    | STRING
    | WORD
    | ID ('('NUMBER')')
    | ID(SLASH ID)? ('(' NUMBER ':' NUMBER')')?
    | ID('(' ID ')')
    | ID
    | '(' NUMBER STAR ID ')'
    ;


equalOperand: ':='
            | '='
            | 'EQ'
            ;
statisticalFunctionStatement: SKEWNESS '(' expression ')';
schemaOperationStatement: SCHEMA '(' expression ')';
alternative: expression ':' naturalStatement;

transactionStatement: BEGIN | BEGINTRANS | END | ROLLBACK | REVOKE;
dataOperationStatement: BINARY ID | BIBXREF ID | INSERT INTO ID VALUES '(' expression ')';
controlFlowStatement: CASE ID OF alternative+ END | ELSE naturalStatement;
typeCastingStatement: CAST '(' expression AS type ')';
dateFunctionStatement: ID '=' (CURDATE | CURRENT_TIME | CURRENT_DATE | CURRENT_TIMESTAMP
                          | DATE | DATETIME | DATEADD | DATEDIFF);
databaseOperationStatement: DATABASE ID | DROP ID | DELETE FROM ID;
aggregationStatement: (SUM | COUNT | AVG | MAX | MIN) '(' DISTINCT? ID ')';
dataManipulationStatement: (LTRIM | RTRIM | UPPER | LOWER) '(' expression ')';

// Table and text manipulation operations
tableOperationStatement: (TABLE | TABLEPHYSICALLYEXISTS | VALUES | UPDATE) expression;
textManipulationStatement: (TRIM | UCASE | UPPER) '(' expression ')';

loopStatement: FOR '(' assignmentStatement ';' conditionalStatement ';' updateStatement ')' '{' program '}'
              | WHILE '(' expression ')' '{' program '}';

encryptionOperationStatement: ENCRYPT '(' expression ')';

// Expression definitions with additional SQL functions
expression:expression (PLUS | MINUS | STAR | SLASH) expression
          | ID ('=' | '<' | '>' | '<='| '>='| LT | GT | AND | OR | 'NE') expression
          | ('=' | '<' | '>' | '<='| '>='| LT | GT| AND | OR | 'NE') expression
          | '(' expression ')'
          | literal
          | ID (expression)?
          | functionCall;

literal: NUMBER
       | STRING
       | PRINT_STRING
       | BOOLEAN;

type: CHAR | VARCHAR | INTEGER | BOOLEAN | DATETIME
     | SQL_BINARY | SQL_INTEGER | SQL_TIMESTAMP | SQL_DATE | SQL_NUMERIC
     | SQL_DECIMAL | SQL_REAL | SQL_DOUBLE | SQL_SMALLINT | SQL_FLOAT
     | SQL_CHAR | SQL_LONGVARCHAR | SQL_VARCHAR;
