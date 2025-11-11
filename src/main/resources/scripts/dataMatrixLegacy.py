import pandas as pd
import numpy as np
import networkx as nx
from sympy import Matrix, Mul, Add

# callgraph_content = """CUSTTRN2: program.
# IDENTIFICATION DIVISION: division.
# ENVIRONMENT DIVISION: division.
# DATA DIVISION: division.
# PROCEDURE DIVISION: division.
# 000-MAIN: paragraph.
# 100-VALIDATE-TRAN: perform.
# WS-FIELDS: variable.
# TRAN-PARMS: linkage-variable.
# TRANSACTION-RECORD: linkage-variable.
# 200-PROCESS-TRAN: paragraph.
# WS-FIELDS: variable.
# TRAN-PARMS: linkage-variable.
# CUST-REC: linkage-variable.
# CUSTTRN1: program.
# IDENTIFICATION DIVISION: division.
# ENVIRONMENT DIVISION: division.
# DATA DIVISION: division.
# report lines: comment.
# PROCEDURE DIVISION: division.
# 000-MAIN: paragraph.
# ACCEPT FROM  ACCEPT FROM: comment.
# 700-OPEN-FILES: perform.
# 100-PROCESS-TRANSACTIONS: paragraph.
# ERR-MSG-BAD-TRAN: variable.
# CUSTTRN2: call.
# CUST-REC: call-variable.
# TRANSACTION-RECORD: call-variable.
# REPORT-RECORD: file-out.
# CSTOUT-REC: file-out."""

DATA_CATEGORY_LIST = [
    "file-out",
    "file-in",
    "variable",
    "table-select",
    "call-variable",
    "cursor-fetch",
    "cursor-close",
    "cursor-open",
    "table-update",
    "table-insert",
    "linkage-variable",
    "call",
    "copybook-variable",
]

CATEGORY_LIST = [
    "program",
    "division",
    "paragraph",
    "call",
    "perform",
    "file-in",
    "file-out",
    "table",
    "variable",
]

def callgraph_read(callgraph_content):

    lines = callgraph_content.split('\n')
    list_of_components = []
    source_code = ''
    division_code = ''
    function_code = ''
    for line in lines:
        row = line.split(':')
        if len(row) <2:
            continue
        component = row[0].strip()
        category = row[1].replace('.', '').strip()
        if category == 'program' and source_code == '':
            source_code = component
            list_of_components.append([f"{component}", f"", f"", f"", category])
        elif category == 'program' and source_code != '':
            source_code = component
            list_of_components.append([f"{component}", f"", f"", f"", category])
        elif category != 'program' and source_code != '':
            if category == 'division' and (component == 'PROCEDURE DIVISION'
                                           or component == 'IDENTIFICATION DIVISION'
                                           or component == 'ENVIRONMENT DIVISION'
                                           or component == 'DATA DIVISION'):
                function_code = ''
                division_code = component
                list_of_components.append([f"{component}",
                                           f"{source_code}",
                                           f"{function_code}",
                                           f"{source_code}.{division_code}",
                                           category])
            elif category == 'paragraph':
                function_code = component
                list_of_components.append([f"{component}",
                                           f"{source_code}",
                                           f"{function_code}",
                                           f"{source_code}.{division_code}.{component}",
                                           category])
            elif category == 'comment':
                continue
            else:
                list_of_components.append([f"{component}",
                                           f"{source_code}",
                                           f"{function_code}",
                                           f"{source_code}.{division_code}.{function_code}.{component}",
                                           category])


        else:
            list_of_components.append([component,
                                       f"",
                                       f"",
                                       component,
                                       category])

    component_category = pd.DataFrame(list_of_components, columns=['Component',
                                                                   'Program',
                                                                   'Used_By',
                                                                   'Path',
                                                                   'Category'], dtype=str)

    return component_category


def get_components_and_missing_paragraphs(df, category_list):

    # ETL - filter only valid category for binary matrix and get missing paragraphs
    pd.options.mode.copy_on_write = True
    df.loc[:, "Program.Used_By"] = df.loc[:, "Program"] + "." + df.loc[:, "Used_By"]
    df.loc[:, "Category:Component"] = (
            df.loc[:, "Category"] + ":" + df.loc[:, "Component"]
    )
    all_mapped_paragraphs = df[
        (df["Program"] != "")
        & (df["Used_By"] != "")
        & (df["Program"].notnull())
        & (df["Used_By"].notnull())
        ]["Program.Used_By"]
    df = df[df["Category"].isin(category_list)]
    missing_paragraphs = set(all_mapped_paragraphs) - set(df["Program.Used_By"])
    return df, missing_paragraphs


def get_binary_matrix_ohe(df, category_list):

    components, missing_paragraphs = get_components_and_missing_paragraphs(
        df, category_list
    )
    if len(components) > 0:

        df_new = components.copy()

        df_new["Category:Component"] = df_new["Category"] + ":" + df_new["Component"]
        df_new["Program.Used_By"] = df_new["Program"] + "." + df_new["Used_By"]
        df_new.drop(["Path","Category", "Component", "Program", "Used_By"], axis=1, inplace=True)

        one_hot = pd.get_dummies(df_new['Category:Component'])

        result = df_new[['Program.Used_By']].join(one_hot)
        result.set_index('Program.Used_By', inplace=True)

        missing_data = pd.DataFrame(
            np.zeros((len(missing_paragraphs), result.shape[1])),
            index=list(missing_paragraphs),
            columns=result.columns,
        )

        df_new = pd.concat([result, missing_data])
        group_new = df_new.groupby(df_new.index).max()

        return group_new
    else:
        return None

def matrix_validation(matrix: pd.DataFrame) -> bool:

    if matrix is not None:
        if matrix.empty:
            print("Empty binary_matrix! Review input data.")
            return False

        column_sums_positive = (matrix.sum() > 0).all()
        only_0_and_1 = (matrix.isin([0, 1])).all().all()

        if column_sums_positive and only_0_and_1:
            return True
    else:
        print("Check your binary_matrix and review input data.")
        return False


def build_perform_attributes(components):
    list_of_paragraphs = components[components["Category"] == "paragraph"][
        ["Program", "Component"]
    ]
    list_of_performs = []
    list_of_paragraphs_size = len(list_of_paragraphs)
    for row in range(0, list_of_paragraphs_size):
        program = list_of_paragraphs["Program"].iloc[row]
        paragraph = list_of_paragraphs["Component"].iloc[row]
        list_of_performs.append(f"perform:{program}.{paragraph}")
    return list_of_performs


def filtering_categories(components, filtering_category):

    if filtering_category in CATEGORY_LIST:
        if filtering_category == 'paragraph':
            filtered_component = set(components[(components['Category'] == filtering_category)]['Component'])
        else:
            filtered_component = set(components[(components['Category'] == filtering_category)]['Component'])
    else:
        return None

    return filtered_component


def get_paragraph_content(components, program, paragraph):
    component_parts = components[
        (components["Used_By"] == paragraph) & (components["Program"] == program)
        ]
    if len(component_parts) > 0:
        return component_parts
    else:
        return None


def get_perform_usage(content, attrib):
    matching_result = pd.Series([0] * len(attrib), index=list(attrib))

    for perf in attrib:
        found_perform = False
        category = perf.split(":")[0]
        component = perf.split(".")[1]
        if (
                len(
                    content[
                        (content["Component"] == component)
                        & (content["Category"] == category)
                    ]
                )
                > 0
        ):
            found_perform = True
        if found_perform:
            matching_result[perf] = 1
    return matching_result


def get_perform_usage_list(components, perform_attributes):
    cobol_programs = filtering_categories(components, "program")
    cobol_functions = filtering_categories(components, "paragraph")
    list_of_performs = []
    for program in cobol_programs:
        for cobol_function in cobol_functions:
            paragraph_content = get_paragraph_content(
                components, program, cobol_function
            )
            if paragraph_content is not None and "perform" in set(
                    paragraph_content["Category"]
            ):
                list_of_performs.append(
                    [
                        f"{program}.{cobol_function}",
                        get_perform_usage(paragraph_content, perform_attributes),
                    ]
                )
            else:
                if paragraph_content is not None:
                    list_of_performs.append(
                        [
                            f"{program}.{cobol_function}",
                            pd.Series(
                                [0] * len(perform_attributes),
                                index=list(perform_attributes),
                                ),
                        ]
                    )

    return list_of_performs


def format_column_attributes(list_of_attributes_names):
    perform_attributes_size = len(list_of_attributes_names)
    new_column_names = []
    for i in range(0, perform_attributes_size):
        new_column_names.append(
            str(list_of_attributes_names[i]).replace(":", "-").replace(".", "/")
        )
    return new_column_names


def build_adjacency_matrix(components):
    performs = build_perform_attributes(components)
    perform_list = get_perform_usage_list(components, performs)

    rows_size = len(perform_list)
    list_of_names = []
    list_of_perform_attributes = []

    for i in range(0, rows_size):
        list_of_names.append(perform_list[i][0])
        list_of_perform_attributes.append(perform_list[i][1].to_list())
    formatted_columns = format_column_attributes(perform_list[0][1].keys())
    adjacency_matrix_performs = pd.DataFrame(
        list_of_perform_attributes, index=list_of_names, columns=formatted_columns
    )
    return adjacency_matrix_performs.sort_index(axis=0)


def set_columns_nodes(adjacency_matrix):
    adj = adjacency_matrix.sort_index()
    if len(adj.columns) == len(adj.index):
        adj.columns = adj.index
    adj_index_length = len(adj.index)
    adj_index_names = adj.index
    new_columns = []
    list_edges = []
    list_programs = []
    for j in range(adj_index_length):
        program_names = adj_index_names[j].split(".")[0]
        paragraphs_names = adj_index_names[j].split(".")[1]
        list_programs.append(program_names)
        list_edges.append((program_names, f"{program_names}.{paragraphs_names}"))
        new_columns.append(f"{program_names}.{paragraphs_names}")

    adj.columns = new_columns
    adj.index = new_columns

    return adj, list_edges, set(list_programs)


def filter_program_edges(program_paragraph_edges):
    program_paragraph_size = len(program_paragraph_edges)
    program_main_list = []
    for i in range(program_paragraph_size):
        # Consider only the edge from program to MAIN
        if "MAIN" in program_paragraph_edges[i][1]:
            program_main_list.append(program_paragraph_edges[i])
    return program_main_list


def get_call_edges(call_matrix):
    list_of_calls = []
    for column in call_matrix.columns:
        valid_calls = call_matrix[call_matrix[column] == 1].index.to_list()
        target = column.split(":")[1]
        for call in valid_calls:
            list_of_calls.append((call, target))
    return list_of_calls


def girvan_newman_communities(G):
    return nx.community.girvan_newman(G)


def louvain_communities(G):
    return nx.community.louvain_partitions(G)


def label_propagation_communities(G):
    return nx.algorithms.community.label_propagation_communities(G)


def compute_modularity(G, communities):
    return nx.algorithms.community.modularity(G, communities)


def compute_coverage_and_performance(G, communities):
    return nx.algorithms.community.partition_quality(G, communities)


def compute_conductance(G, communities):
    nx.algorithms.community.partition_quality(G, communities)
    conductance_values = []
    for community in communities:
        if len(community) == 0:
            continue
        boundary_edges = list(nx.edge_boundary(G, community))
        cut_size = len(boundary_edges)
        volume = sum([G.degree(node) for node in community])
        denominator = min(volume, 2 * G.number_of_edges() - volume)
        if denominator == 0:
            continue
        conductance = cut_size / denominator
        conductance_values.append(conductance)
    return (
        sum(conductance_values) / len(conductance_values) if conductance_values else 0
    )


def get_community_method_selected(G, method_name, method_function):
    communities = list(method_function(G))
    if method_name == "Label Propagation":
        df = pd.DataFrame(
            {
                "k": [1],
                "communities": [communities],
            }
        )
    else:
        df = pd.DataFrame(
            [[k + 1, communities[k]] for k in range(len(communities))],
            columns=["k", "communities"],
        )
    df["algorithm"] = method_name
    return df


def get_metric_selected(df, G, metric_name, metric_function):
    for index, row in df.iterrows():
        value = metric_function(G, row["communities"])
        if metric_name == "Coverage and Performance":
            df.at[index, metric_name.split(" and ")[0]] = value[0]
            df.at[index, metric_name.split(" and ")[1]] = value[1]
        else:
            df.at[index, metric_name] = value
    return df


def analyze_communities(G):
    community_detection_methods = {
        "Girvan-Newman": girvan_newman_communities,
        "Louvain": louvain_communities,
        # "Label Propagation": label_propagation_communities
    }
    metrics = {
        "Modularity": compute_modularity,  # Higher values indicate stronger community structure.
        "Conductance": compute_conductance,  # Lower values indicate better community separation.
        "Coverage and Performance": compute_coverage_and_performance,
    }
    results = pd.DataFrame()
    for method_name, method_function in community_detection_methods.items():
        df = get_community_method_selected(G, method_name, method_function)
        for metric_name, metric_function in metrics.items():
            df = get_metric_selected(df, G, metric_name, metric_function)
        results = pd.concat([results, df])
    return results


def get_best_criteria(df):

    df["q"] = df["Modularity"] / df["Conductance"]
    df["e"] = np.sqrt(df["q"] ** 2 + df["Performance"] ** 2)
    df = df.sort_values(by=["e", "algorithm"], ascending=[False, False])
    df = df.reset_index()

    df_best_3_communities = df[df["e"].isin(df["e"].nlargest(3))]

    return df_best_3_communities


def get_best_community_detection_methods(G):
    results = analyze_communities(G)
    communities = get_best_criteria(results)
    return communities


def detect_community(G):
    df_communities = get_best_community_detection_methods(G)
    return df_communities["communities"][0]


def generate_graph(adj_matrix, call_matrix):

    adjacency_matrix, program_paragraph_edges, program_nodes = set_columns_nodes(
        adj_matrix
    )

    # DONE Create the graph based on adj matrix
    adj_matrix_graph = nx.from_pandas_adjacency(adjacency_matrix)

    # DONE Adding program nodes
    for j in range(len(program_nodes)):
        adj_matrix_graph.add_node(program_nodes.pop())

    # TODO - Considering to add new nodes and edges. Review the solution
    list_program_paragraph_edges = []
    list_program_paragraph_edges = filter_program_edges(program_paragraph_edges)
    if len(list_program_paragraph_edges) > 0:
        adj_matrix_graph.add_edges_from(list_program_paragraph_edges)

    if call_matrix is not None:
        edges_from_call_matrix = get_call_edges(call_matrix.sort_index())
        adj_matrix_graph.add_edges_from(edges_from_call_matrix)

    # TODO Community detection First Approach: Girvan-Newman
    best_community_detected = detect_community(adj_matrix_graph)

    return best_community_detected


def create_symmetric_matrix(adj):
    input_matrix = Matrix(adj)
    sym_matrix = input_matrix + input_matrix.T
    return pd.DataFrame(sym_matrix.tolist(), columns=adj.columns, index=adj.index)


def indirect_data_relation(raw_data, paragraph_relationship):
    data_m = Matrix(raw_data)
    para_r = Matrix(paragraph_relationship)
    new_data_relationship = Add(data_m, Mul(para_r, data_m))
    return pd.DataFrame(
        new_data_relationship.tolist(), columns=raw_data.columns, index=raw_data.index
    )


def get_call_rows_columns(data_relationship):
    list_of_calls = []
    for j in data_relationship.columns.to_list():
        if "call:" in j:
            called_program = j.split("call:")[1]
            called_paragraphs = []
            for k in data_relationship.index.to_list():
                if called_program in k:
                    called_paragraphs.append(k)
            list_of_calls.append((called_program, called_paragraphs))
    return list_of_calls


def open_call(data_relationship):
    list_of_calls = get_call_rows_columns(data_relationship)
    df = data_relationship
    for j in range(len(list_of_calls)):
        source = data_relationship[
            data_relationship[f"call:{list_of_calls[j][0]}"] == 1
            ]
        for k in list_of_calls[j][1]:
            for l in source.index.to_list():
                df.loc[k] = df.loc[k] + df.loc[l]
        df = df.drop(f"call:{list_of_calls[j][0]}", axis=1)
    return df


def assign_communities(data, communities):
    data_index = data.index.to_list()
    mapping_communities = pd.Series(
        [-1] * len(data_index), index=data_index, name="Community"
    )
    for i in range(len(communities)):
        comm_list = list(communities[i])
        for j in range(len(comm_list)):
            for k in data_index:
                if k in comm_list[j] and mapping_communities[k] == -1:
                    mapping_communities[k] = i

    final_data_matrix_communities = pd.concat(
        [data, mapping_communities], axis=1, ignore_index=False, sort=True
    )
    return final_data_matrix_communities


def construct_community_graph_from_data(
            binary_matrix: pd.DataFrame,
            components: pd.DataFrame,
            binary_matrix_calls: pd.DataFrame,
    ) -> pd.DataFrame:

    if matrix_validation(binary_matrix):
        uni_adj_matrix = build_adjacency_matrix(components)
        G = generate_graph(uni_adj_matrix, binary_matrix_calls)
        bi_adj_matrix = create_symmetric_matrix(uni_adj_matrix)
        data_rel = indirect_data_relation(binary_matrix, bi_adj_matrix)
        data_matrix = open_call(data_rel)
        return assign_communities(data_matrix, G)
    else:
        return pd.DataFrame()

# print('CALLGRAPH', callgraph_content)

components = callgraph_read(callgraph_content)
binary_matrix = get_binary_matrix_ohe(components, DATA_CATEGORY_LIST)
binary_matrix_calls = get_binary_matrix_ohe(components, ["call"])
binary_matrix = construct_community_graph_from_data(
    binary_matrix, components, binary_matrix_calls
)
# print('BINARY', binary_matrix)
binary_matrix.to_csv()