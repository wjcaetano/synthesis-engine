import json
import pandas as pd
import re

# Scripts adapted from brsp-gen-ai-fine-tuner/procedural/tech/sql_statement_story_telling.py

# allSqlStatements = [
#     {
#       "sql_block" : "*>EXECSQL DECLARE C1 CURSOR FOR\n      *>EXECSQL SELECT DEPT, MIN(PERF), MAX(PERF),\n      *>EXECSQL AVG(CAST(PERF AS DEC(5,2))),\n      *>EXECSQL MIN(HOURS), MAX(HOURS), AVG(HOURS)\n      *>EXECSQL FROM RBAROSA.EMPL E, RBAROSA.PAY P\n      *>EXECSQL WHERE E.NBR = P.NBR\n      *>EXECSQL GROUP BY DEPT\n      *>EXECSQL",
#       "sql_clause" : "SELECT",
#       "program" : "CUSTTRN1"
#     },
#       {
#         "sql_block" : "EXEC SQL SELECT SVC_PGM_ID INTO :SERVICE-PGM-ID FROM TTT_SERVICE WHERE SVC_ID = :MSG-SERVICE-ID END-EXEC",
#         "sql_clause" : "SELECT_INTO",
#         "program" : "SERVGETTER"
#       }
# ]

# allSqlStatementsFromWS = [
      # {
      #   "sql_block" : "EXECSQL CALL RBAROSA.COMPUTE_STATS_PROC() END-EXEC",
      #   "sql_clause" : "CALL_PROCEDURE",
      #   "program" : "CUSTTRN1"
      # },
      #  {
      #   "sql_block" : "*>EXECSQL INSERT INTO RBAROSA.EMPL (NBR, DEPT, NAME) VALUES (:NBR-VAR, :DEPT-VAR, :NAME-VAR)",
      #   "sql_clause" : "INSERT",
      #   "program" : "SERVGETTER"
      # },
#        {
#         "sql_block" : "UPDATE RBAROSA.EMPL SET AMOUNT = :AMOUNT-VAR, DATE = :DATE-VAR WHERE NBR = :NBR-VAR",
#         "sql_clause" : "UPDATE",
#         "program" : "CUSTTRN2"
#       },
#        {
#         "sql_block" : "UPDATE DBPROD.MATRL_MDICO_ESPCL SET VLIBRD_MATRL_MDICO = NULL WHERE CURRENT OF CRS-MT FOR ROW 1 OF 10",
#         "sql_clause" : "UPDATE_CURSOR",
#         "program" : "CUSTTRN2"
#       },
#        {
#         "sql_block" : "DELETE  FROM PAY WHERE NBR = :NBR-VAR ",
#         "sql_clause" : "DELETE",
#         "program" : "SERVGETTER"
#       }
#   ]

def get_tables(sql_statements):

    data = []
    for sql_statement in sql_statements:
        statement = sql_statement['sql_clause']
        raw_query = sql_statement['sql_block']
        try:
            sql_story_factory_function = _get_sql_story_factory(statement, raw_query)
            cleaned_raw_query = CleanRawQuery(raw_query)
            print(cleaned_raw_query.raw_query)
            tables = sql_story_factory_function(cleaned_raw_query.raw_query)
            if len(tables) == 0:
                raise Exception(f"None table was found in the query")
            tables = [tables] if type(tables) is str else tables
            data.append({'program': sql_statement['program'], 'tables': [f'table:{t}' for t in tables]})
        except Exception as e:
            print(
                f"An error occurred when handling the query: '{raw_query}' "
                f"as a {statement} statement. "
                f"It has an unexpected syntax or incorrect statement.\n"
                f"Error: {e}"
            )

    return data


class CleanRawQuery:

    def __init__(self, raw_query):
        cleaned = re.sub(r"\*>EXECSQL", "", raw_query)
        cleaned = re.sub(r"\s+", " ", cleaned)
        cleaned = re.sub("EXEC SQL", "", cleaned)
        cleaned = re.sub("EXECSQL", "", cleaned)
        cleaned = re.sub("END-EXEC", "", cleaned)
        self.raw_query = cleaned.strip()

    @property
    def raw_query(self):
        return self._raw_query

    @raw_query.setter
    def raw_query(self, value):
        self._raw_query = value


def _get_sql_story_factory(statement, raw_query):
    if statement == "CALL_PROCEDURE":
        raise Exception(
            f"CALL_PROCEDURE statement doesn't has TABLES."
        )
    elif statement == "DELETE":
        return sql_delete
    elif statement == "FETCH":
        raise Exception(
            f"CALL_PROCEDURE statement doesn't has TABLES."
        )
    elif statement == "INSERT":
        return sql_insert
    elif statement == "OPEN_CURSOR":
        raise Exception(
            f"OPEN_CURSOR statement doesn't has TABLES."
        )
    elif statement == "SELECT_INTO":
        return sql_select_into
    elif statement == "SELECT" and "DECLARE" in raw_query:
        return sql_declare_cursor
    elif statement == "SELECT" and "DECLARE" not in raw_query:
        return sql_select_into
    elif statement == "UPDATE":
        return sql_update
    elif statement == "UPDATE_CURSOR":
        return sql_update
    else:
        raise Exception(
            f"There is no function to treat the statement {statement}."
        )


def _get_tables_from_select(raw_query: str):
    # https://www.ibm.com/docs/en/db2-for-zos/12?topic=statements-select-into
    # https://www.ibm.com/docs/en/db2-for-zos/12?topic=statement-joining-data-from-more-than-one-table
    # https://www.ibm.com/docs/en/db2-for-zos/12?topic=statement-subqueries
    # https://www.ibm.com/docs/en/db2-for-zos/12?topic=statement-combining-result-tables-from-multiple-select-statements

    def get_snippet_by_from_statement(query):
        snippet = query.split(" FROM ")[1]
        snippet = re.sub(r"WHERE.{0,}", "", snippet)
        return snippet

    def list_likely_tables(snippet):
        if "," in snippet:
            # There is an IMPLICIT join. Ex: SELECT * FROM TABLE-A, TABLE-B
            tables = snippet.split(",")
        elif " JOIN " in snippet:
            tables = snippet.split(" JOIN ")
        else:
            tables = [snippet]
        return tables

    def get_table(table):
        table = table.strip()
        # remove anything remained after the table name, such as:
        # TABLE-X AS X
        # TABLE-X X
        table = re.sub(r"\s.{1,}", "", table)
        table = table.strip()
        return table

    raw_query = re.sub(r"[()]", "", raw_query)
    queries = raw_query.split("SELECT")

    tables = []
    for query in queries:
        if query == "":
            continue
        snippet = get_snippet_by_from_statement(query)
        likely_tables = list_likely_tables(snippet)
        for table in likely_tables:
            table = get_table(table)
            if table == "":
                continue
            tables.append(table)

    return tables


def sql_declare_cursor(raw_query: str):
    # https://www.ibm.com/docs/en/db2-for-zos/12?topic=statements-declare-cursor
    # Ex: DECLARE CURSOR_C1 CURSOR FOR SELECT DEPT FROM EMP

    new_raw_query = raw_query.split(" FOR ")[1]
    return _get_tables_from_select(new_raw_query)


def sql_delete(raw_query: str):
    # https://www.ibm.com/docs/en/db2-for-zos/12?topic=statements-delete
    # Ex: DELETE FROM EMP WHERE ID = 1

    if "DELETE" not in raw_query:
        raise Exception()

    table = raw_query.split("FROM")[1].split("WHERE")[0]
    table = table.strip()
    # remove the table alias. Ex: DELETE FROM EMP X WHERE
    return re.sub(r"\s.?", "", table)


def sql_insert(raw_query: str):
    # https://www.ibm.com/docs/en/db2-for-zos/12?topic=statements-insert

    if "SELECT" in raw_query:
        raise Exception(
            f"If this SQL creates a temporary table. "
            f"It should be treated as a SELECT instead of an INSERT."
        )

    if re.search(r"\)\s?VALUES", raw_query) is not None:
        # columns are declared. Ex: INSERT INTO TABLE (COLUMN1) VALUES (VALUE1)
        table = raw_query.split("INTO")[1].split("(")[0]
    else:
        # columns arent declared. Ex: INSERT INTO TABLE VALUES (VALUE1)
        table = raw_query.split("INTO")[1].split("VALUES")[0]

    return table.strip()


def sql_select_into(raw_query: str):

    return _get_tables_from_select(raw_query)


def sql_update(raw_query: str):
    # https://www.ibm.com/docs/en/db2-for-zos/12?topic=statements-update

    table = raw_query.split("UPDATE")[1].split("SET")[0]
    # revome FOR statement. Ex: UPDATE POLICY FOR PORTION OF TIME FROM '2014-01-01' TO '9999-12-31' SET TYPE='HMO'",
    table = re.sub(r"FOR.{1,}", "", table)
    table = table.strip()
    # remove the table alias. Ex: UPDATE EMP X SET SALARY = 1
    table = re.sub(r"\s.?", "", table)
    table = table.strip()

    return table


def identify_tables(allSqlStatements, allSqlStatementsFromWS):
    all_tables = get_tables(allSqlStatements)
    all_tables_b = get_tables(allSqlStatementsFromWS)
    all_tables.extend(all_tables_b)

    programs = []
    for _tables in all_tables:
        programs.append(_tables['program'])

    components = []
    for program in list(set(programs)):
        tables = []
        for _tables in all_tables:
            if _tables['program'] == program:
                tables.extend(_tables['tables'])

        components.append({'program': program, 'tables': list(set(tables))})

    print('ALL-TABLES', components)
    return components

# if type(allSqlStatements) != list:
#     print('Error: allSqlStatements variable is not a LIST\nallSqlStatements:', allSqlStatements)
#     allSqlStatements = []

print('ALL', allSqlStatements)
print('ALL-WS', allSqlStatementsFromWS)
# allSqlStatements = [allSqlStatements]
# allSqlStatementsFromWS = [allSqlStatementsFromWS]
# allSqlStatementsFromWS = []

identify_tables(allSqlStatements, allSqlStatementsFromWS)