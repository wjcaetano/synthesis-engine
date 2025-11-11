import pandas as pd
import json

# allComponents = [
#          {
#            "program" : "CUSTTRN2",
#            "programCalls" : [ ],
#            "jclCalls" : [ ],
#            "files" : [ ]
#          },
#          {
#            "program" : "SERVGETTER",
#            "programCalls" : [ ],
#            "jclCalls" : [ ],
#            "files" : [ "REPORT-FILE" ]
#          },
#          {
#            "program" : "CUSTTRN3",
#            "programCalls" : [ ],
#            "jclCalls" : [ ],
#            "files" : [ ]
#          },
#          {
#            "program" : "CUSTTRN1",
#            "programCalls" : [
#              "SERVGETTER",
#              "CUSTTRN2"
#            ],
#            "jclCalls" : [
#              "TESTJCL"
#            ],
#            "files" : [
#              "CUSTOMER-FILE-OUT",
#              "TRANSACTION-FILE",
#              "CUSTOMER-FILE",
#              "REPORT-FILE" ]
#          }
#        ]
#
#
# allTables = [
#         {
#             "program": "SERVGETTER",
#             "tables": [
#                 "TTT_SERVICE",
#                 "PAY",
#                 "RBAROSA.EMPL"
#             ]
#         },
#         {
#             "program": "CUSTTRN2",
#             "tables": [
#                 "DBPROD.MATRL_MDICO_ESPCL",
#                 "RBAROSA.EMPL"
#             ]
#         },
#         {
#             "program": "CUSTTRN1",
#             "tables": [
#                 "RBAROSA.PAY",
#                 "RBAROSA.EMPL"
#             ]
#         }
#     ]

def merge(allComponents, allTables):
    for components in allComponents:
        components['tables'] = []
        for tables in allTables:
            if components['program'] == tables['program']:
                components['tables'] = tables['tables']
    return allComponents


# allComponents = [allComponents]

if type(allComponents) != list or len(allComponents) == 0:
    print('COMPONENTS:', allComponents)
    raise Exception("allComponents variable is empty or it is not a LIST")

if type(allTables) != list:
    print('Error: allTables variable is not a LIST\nallTables:', allTables)
    allTables = []

allComponents = merge(allComponents, allTables)

# Collect all possible columns (from programCalls + files)
all_cols = sorted({x for d in allComponents for x in d["programCalls"] + d['jclCalls'] + d["files"] + d['tables']})

# Build a DataFrame
df = pd.DataFrame([
    {
        "program": d["program"],
        **{col: (col in d["programCalls"] + d['jclCalls'] + d["files"] + d['tables']) for col in all_cols}
    }
    for d in allComponents
])


# print(df.to_csv(index=False))
df.to_csv(index=False)