# Reports Recipe System Documentation

## Table of Contents
1. [Overview](#overview)
2. [Recipe Configuration Structure](#recipe-configuration-structure)
3. [Available Directives](#available-directives)
4. [Creating and Customizing Reports](#creating-and-customizing-reports)
5. [Advanced Features](#advanced-features)
   - [Using Prompts to Generate Cypher Queries and PlantUML Scripts](#using-prompts-to-generate-cypher-queries-and-plantuml-scripts)
6. [Examples](#examples)
7. [Best Practices](#best-practices)

## Overview

The Reports Recipe System is an enhanced framework for generating various types of reports from data stored in a Neo4j database. It evolves from the previous JSON-based configuration to a more structured and modular YAML recipe approach, allowing users to define a chain of operations that extract, transform, and visualize data in different formats.

The system is designed to be extensible, allowing new tools to be added as needed. It uses a YAML recipe configuration file to define the report generation process, which can include database queries, data transformations, and visualization generation.

### Key Features

- **Structured Recipe Configuration**: Define complex report generation processes using a clear YAML recipe structure
- **Template-Based Approach**: Use templates to define report types and their properties
- **Chainable Operations**: Execute a sequence of operations, with each operation using the results of previous operations
- **Multiple Output Formats**: Generate reports in various formats, including tables, PlantUML diagrams, and more
- **Enhanced Visualizations**: Create visually appealing diagrams with modern color schemes, clear labels, and proper spacing
- **Business Context**: Add business-friendly explanations alongside technical details to make diagrams accessible to all stakeholders
- **Extensible Architecture**: Add new tools to the system to support additional data sources or output formats

## Recipe Configuration Structure

A report recipe configuration is a YAML file with the following structure:

```yaml
config:
  fresh: true
  progressCustomData: "${#projects[0]['reports']}"
executor: project-model-executor.groovy
executorEvents:
  beforeAll: |-
    # Initialization code
vars:
  # Global variables
  neo4jURL: "http://localhost:7474/db/neo4j/tx/commit"
  neo4jEncodedAuth: "Basic ..."
templates:
  cards:
    # Report templates
    - id: "0"
      name: "Report Name"
      description: "Report Description"
      type: "table"
  reports:
    # Report definitions
    - filesReport: |-
        @@@neo4j(${#recipe['vars']['neo4jURL']}, ${#recipe['vars']['neo4jEncodedAuth']})
        @@@jolt(${#recipe['templates']['joltNeo4jTableToHeadersAndRows']})
        @@@_spel(${#project['reports'][#fileIndex].put('content', @JsonUtils.readAsMap(#content))})
        # Neo4j Cypher query
```

### Fields

- **config**: General configuration settings
  - **fresh**: Whether to create a fresh project context
  - **progressCustomData**: Expression to select data for progress tracking
- **executor**: The executor script to use
- **executorEvents**: Event handlers for the executor
  - **beforeAll**: Code to execute before all reports
- **vars**: Global variables for use in reports
- **templates**: Templates for report types
  - **cards**: List of report templates with ID, name, description, and type
- **reports**: The actual report definitions
  - Each report is defined as a list item with a name and a multiline string containing:
    - **@@@neo4j**: Directive to execute a Neo4j query with URL and authentication parameters
    - **@@@jolt**: Directive to apply a Jolt transformation to the query results
    - **@@@_spel**: Directive to execute a SpEL expression, typically to store results
    - **Cypher Query**: The actual Neo4j Cypher query to execute

## Available Directives

The recipe system provides the following built-in directives:

### @@@neo4j

Executes a Neo4j Cypher query.

**Parameters**:
- **url** (required): The URL of the Neo4j database
- **auth** (required): The authentication credentials for the Neo4j database

**Example**:
```yaml
@@@neo4j(${#recipe['vars']['neo4jURL']}, ${#recipe['vars']['neo4jEncodedAuth']})
MATCH (n) RETURN n LIMIT 10
```

### @@@jolt

Applies Jolt transformations to JSON data.

**Parameters**:
- **spec** (required): The Jolt transformation specification or a reference to a template containing the specification

**Example**:
```yaml
@@@jolt(${#recipe['templates']['joltNeo4jTableToHeadersAndRows']})
```

### @@@_spel

Executes a Spring Expression Language (SpEL) expression.

**Parameters**:
- **expression** (required): The SpEL expression to execute

**Example**:
```yaml
@@@_spel(${#project['reports'][#fileIndex].put('content', @JsonUtils.readAsMap(#content))})
```

### @@@llm

Sends a prompt to a language model and processes the response.

**Parameters**:
- **prompt** (required): The prompt to send to the language model
- **options** (optional): Additional options for the language model

**Example**:

    @@@llm(Create a plantuml diagram based on the following data: ${#data})

## Creating and Customizing Reports

To create a new report using the recipe system, follow these steps:

1. **Define the Report Requirements**: Determine what data you need to extract, how it should be transformed, and what the final output should look like.

2. **Create a Template Entry**: Add a new entry to the `templates.cards` section with a unique ID, name, description, and type.

3. **Create a Report Definition**: Add a new entry to the `reports` section with a name that describes the report, and define the directives and queries needed to generate the report.

4. **Test the Recipe**: Use the system to test the report recipe and verify that it produces the expected results.

5. **Refine the Recipe**: Adjust the recipe as needed to improve the report's accuracy, performance, or presentation.

### Using Recipe Variables and Templates

The recipe system provides access to variables and templates through SpEL expressions. You can reference:

- **Recipe variables**: Access global variables defined in the `vars` section using `${#recipe['vars']['variableName']}`
- **Recipe templates**: Access templates defined in the `templates` section using `${#recipe['templates']['templateName']}`
- **Project data**: Access project-specific data using `${#project['key']}`
- **File index**: Access the current file index using `${#fileIndex}`

For example, to use a Neo4j URL and authentication from the recipe variables:

    @@@neo4j(${#recipe['vars']['neo4jURL']}, ${#recipe['vars']['neo4jEncodedAuth']})

### Directive Types

The recipe system supports several types of directives:

- **@@@neo4j**: Executes Neo4j Cypher queries
- **@@@jolt**: Applies Jolt transformations to JSON data
- **@@@_spel**: Executes SpEL expressions for data manipulation
- **@@@llm**: Sends prompts to language models for generating content

## Advanced Features

### Using Prompts to Generate Cypher Queries and PlantUML Scripts

One of the most powerful features of the recipe system is the ability to use language models to generate Cypher queries and PlantUML scripts. This two-step process allows for dynamic and intelligent generation of complex diagrams:

1. **First Prompt**: Generate a Cypher query to extract relevant data from Neo4j
2. **Second Prompt**: Use the results of the Cypher query to generate a PlantUML script

This approach is particularly useful for creating complex diagrams like component diagrams, where the relationships between components need to be extracted from the database and then visualized in a clear and appealing way.

#### Example: Two-Step Prompt for Component Diagram

**Step 1: Generate Cypher Query with Prompt**

```yaml
reports:
  - componentsDiagram: |-
      @@@prompt
      @@@extractMarkdownCode
      @@@_spel(${#project['reports'][#fileIndex].put('content', {'data': #content})})
      You are tasked with creating a Cypher query for Neo4j to extract data for a component diagram of a COBOL application. The diagram should visualize the high-level components of the system and their relationships.

      The application consists of:
      1. COBOL Programs: CUSTTRN1 (main program) and CUSTTRN2 (subroutine)
      2. JCL Job: TESTJCL
      3. External Files: CUSTOMER-FILE, TRANSACTION-FILE, CUSTOMER-FILE-OUT, REPORT-FILE

      Key relationships:
        - CUSTTRN1 calls CUSTTRN2 for update transactions
        - TESTJCL executes CUSTTRN1
        - CUSTTRN1 reads from CUSTOMER-FILE and TRANSACTION-FILE
        - CUSTTRN1 writes to CUSTOMER-FILE-OUT and REPORT-FILE

      Create a comprehensive Cypher query that extracts all these components and their relationships to be used for generating a component diagram. Include comments in the query to explain each section.
```

**Step 2: Generate PlantUML Script with Prompt Using Cypher Results**

```yaml
templates:
  componentsDiagram: |-
    @@@freemarker
    @@@openllmthread
    @@@prompt
    @@@extractMarkdownCode@set:project.currentPage
    @@@_exec(${#recipe['templates']['componentsDiagram']})
    @@@closellmthread
    @@@spel(${#content.replace('!!componentsDiagram!!', #recipe['templates']['wrapBase64Image'].replace('!!fullBase64Value!!', @FileUtils.getFullBase64(#fullFilePath.toString().replace('documents', 'componentsDiagram').replace('.html', '.png')) ?: #recipe['templates']['emptyDiagram']))})
    Using the results from the following Cypher query, create a PlantUML script to generate a component diagram for a COBOL application at the end

    The component diagram should:
      1. Visualize the high-level components of the system and their relationships
      2. Include COBOL Programs (CUSTTRN1, CUSTTRN2), JCL Job (TESTJCL), and External Files
      3. Show the relationships between components (calls, executes, reads, writes)
      4. Use appropriate styling to distinguish between different types of components
      5. Include a title, header, and legend
      6. Add detailed descriptive notes for key components explaining their purpose and functionality

    Provide the complete PlantUML script that can be directly used to generate the component diagram: ' + T(String).join(', ', #results[2]['results'][0]['data'].![#this['row'][0]])}
```

This two-step approach allows for:
- Dynamic generation of complex queries based on application requirements
- Intelligent transformation of query results into visually appealing diagrams
- Customization of both the data extraction and visualization steps
- Addition of business context and explanations to technical diagrams

## Examples

### Example 1: Basic Neo4j Query

This example shows a simple report that queries a Neo4j database and returns the results.

```yaml
reports:
  - filesReport: |-
      @@@neo4j(${#recipe['vars']['neo4jURL']}, ${#recipe['vars']['neo4jEncodedAuth']})
      @@@jolt(${#recipe['templates']['joltNeo4jTableToHeadersAndRows']})
      @@@_spel(${#project['reports'][#fileIndex].put('content', @JsonUtils.readAsMap(#content))})
      MATCH (n)
      WHERE n:COBOLJcl OR n:COBOLProgram OR n:Db2Table OR n:Db2Procedure OR n:Screen OR n:COBOLVsamFile or n:COBOLFileControl
      WITH 
        sum(CASE WHEN n:COBOLJcl THEN 1 ELSE 0 END) as Jcl_ProcCount,
        sum(CASE WHEN n:COBOLProgram THEN 1 ELSE 0 END) as ProgramCount,
        sum(CASE WHEN n:Db2Table THEN 1 ELSE 0 END) as TableCount,
        sum(CASE WHEN n:Db2Procedure THEN 1 ELSE 0 END) as StoredProcCount,
        sum(CASE WHEN n:COBOLVsamFile THEN 1 ELSE 0 END) as VSMCount,
        sum(CASE WHEN n:COBOLFileControl THEN 1 ELSE 0 END) as FileCtrCount,
        sum(CASE WHEN n:Screen THEN 1 ELSE 0 END) as ScrCount
      RETURN 
        Jcl_ProcCount as NumberOfJCLsOrProcs,
        ProgramCount as NumberOfCobolPrograms,
        TableCount as NumberOfDB2Tables,
        StoredProcCount as StoredProcedureCount,
        VSMCount as VSAMCount,
        FileCtrCount as FileCount,
        ScrCount as ScreenCount
```

### Example 2: Program Analysis Report

This example shows a report that analyzes COBOL programs for complexity and maintenance risk.

```yaml
reports:
  - fileContentReport: |-
      @@@neo4j(${#recipe['vars']['neo4jURL']}, ${#recipe['vars']['neo4jEncodedAuth']})
      @@@jolt(${#recipe['templates']['joltNeo4jTableToHeadersAndRows']})
      @@@_spel(${#project['reports'][#fileIndex].put('content', @JsonUtils.readAsMap(#content))})
      MATCH (prog:COBOLProgram)
      OPTIONAL MATCH (prog)-[:CONTAINS]->(proc:COBOLProcedureDivision)-[:CONTAINS]->(para:COBOLParagraph)
      WITH prog, collect(para.rawCode) as logicCodes, count(para) as paragraphCount
      OPTIONAL MATCH (prog)-[:CONTAINS]->(data:COBOLDataDivision)
      WITH prog, logicCodes, paragraphCount, data.rawCode as dataCode
      WITH prog,
          logicCodes,
          paragraphCount,
          CASE WHEN dataCode IS NOT NULL THEN size(split(dataCode, '\n')) ELSE 0 END as dataLines
      UNWIND logicCodes as logic
      WITH 
         prog.name AS programName,
         sum(size(split(coalesce(logic, ''), '\n'))) as totalLinesLogic,
         dataLines as totalLinesData,
         sum(size(split(coalesce(logic, ''), '\n'))) + dataLines as grandTotal,
         paragraphCount,
         round(1.0 * sum(size(split(coalesce(logic, ''), '\n'))) / paragraphCount, 1) as avgLinesPerParagraph,
         round(1.0 * dataLines / sum(size(split(coalesce(logic, ''), '\n'))), 2) as dataToLogicRatio
      RETURN 
         programName,
         totalLinesLogic,
         totalLinesData,
         grandTotal,
         paragraphCount,
         avgLinesPerParagraph,
         dataToLogicRatio,
         CASE 
             WHEN dataToLogicRatio > 1.5 THEN 'Data-Heavy'
             WHEN dataToLogicRatio < 0.5 THEN 'Logic-Heavy'
             ELSE 'Balanced'
         END as dataLogicProfile,
         CASE 
             WHEN avgLinesPerParagraph > 50 THEN 'Complex'
             WHEN avgLinesPerParagraph > 30 THEN 'Moderate'
             ELSE 'Simple'
         END as complexityProfile,
         CASE
             WHEN paragraphCount > 50 THEN 'Large'
             WHEN paragraphCount > 25 THEN 'Medium'
             ELSE 'Small'
         END as sizeProfile,
         CASE 
             WHEN avgLinesPerParagraph > 50 AND paragraphCount > 50 THEN 'High Risk'
             WHEN avgLinesPerParagraph > 30 OR paragraphCount > 50 THEN 'Medium Risk'
             ELSE 'Low Risk'
         END as maintenanceRisk
      ORDER BY grandTotal DESC
```

### Example 3: Component Diagram Generation with Direct Cypher Query

This example shows how to create a component diagram using a direct Cypher query.

```yaml
reports:
  - componentsDiagram: |-
      @@@neo4j(${#recipe['vars']['neo4jURL']}, ${#recipe['vars']['neo4jEncodedAuth']})
      @@@jolt(${#recipe['templates']['joltNeo4jTableToHeadersAndRows']})
      @@@_spel(${#project['reports'][#fileIndex].put('content', @JsonUtils.readAsMap(#content))})
      // Component Diagram Cypher Query
      // This query extracts the relationships between COBOL programs, JCL jobs, and files

      // Start with JCL jobs
      MATCH (jcl:COBOLJcl)

      // Get COBOL programs called by JCL
      OPTIONAL MATCH (jcl)-[:EXECUTES]->(program:COBOLProgram)

      // If no direct EXECUTES relationship, try to infer from raw code
      WITH jcl, collect(program) as programs
      WHERE size(programs) = 0
      OPTIONAL MATCH (p:COBOLProgram)
      WHERE jcl.rawCode CONTAINS p.name
      WITH jcl, collect(p) as inferredPrograms

      // Combine direct and inferred programs
      MATCH (allProgram:COBOLProgram)
      WHERE allProgram.name IN [p in inferredPrograms | p.name] 
         OR exists((jcl)-[:EXECUTES]->(allProgram))

      // Get files used by programs
      OPTIONAL MATCH (allProgram)-[:CONTAINS]->(:COBOLDataDivision)-[:CONTAINS]->(:COBOLFileSection)-[:CONTAINS]->(file:COBOLFileControl)

      // Get program calls
      OPTIONAL MATCH (allProgram)-[:CONTAINS]->(:COBOLProcedureDivision)-[:CONTAINS]->(:COBOLParagraph)-[:CONTAINS]->(call:COBOLCall)

      // Get called programs
      OPTIONAL MATCH (calledProgram:COBOLProgram)
      WHERE calledProgram.name = call.name

      // Return the results
      RETURN 
          jcl.name as JCLName,
          allProgram.name as ProgramName,
          file.name as FileName,
          call.name as CalledProgramName,
          calledProgram.name as VerifiedCalledProgram
      ORDER BY JCLName, ProgramName, FileName
```

## Best Practices

### Security

- **Avoid Hardcoding Credentials**: Don't hardcode credentials in the report recipe. Use environment variables or a secure credential store instead.
- **Validate Input**: Always validate input data before processing it, especially when using the `shell_run` tool.
- **Limit Access**: Restrict access to the report generation API to authorized users only.

### Performance

- **Optimize Database Queries**: Write efficient Neo4j queries to minimize database load and response time.
- **Limit Result Sets**: Use LIMIT clauses in Neo4j queries to avoid returning large result sets.
- **Cache Results**: Consider caching report results for frequently accessed reports.

### Maintainability

- **Document Report Recipes**: Include comments in your report recipes to explain their purpose and how they work.
- **Use Meaningful Names**: Use descriptive names for tools, parameters, and result variables.
- **Break Down Complex Reports**: Split complex reports into smaller, more manageable parts.
- **Version Control**: Store report recipes in a version control system to track changes over time.

### Error Handling

- **Validate Configurations**: Validate report recipes before executing them to catch errors early.
- **Handle Errors Gracefully**: Implement error handling to provide meaningful error messages when report generation fails.
- **Log Errors**: Log errors and exceptions to help diagnose and fix issues.

### Recipe System Specific

- **Template Organization**: Group related templates together for better organization.
- **Reuse Common Patterns**: Extract common patterns into separate files and include them in multiple recipes.
- **Test Incrementally**: Test each step in the chain individually before combining them.
- **Document Dependencies**: Clearly document dependencies between different parts of the recipe.
