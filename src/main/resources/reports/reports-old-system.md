# Reports System Documentation

## Table of Contents
1. [Overview](#overview)
2. [Report Configuration Structure](#report-configuration-structure)
3. [Available Tools](#available-tools)
4. [Creating and Customizing Reports](#creating-and-customizing-reports)
5. [Examples](#examples)
6. [Best Practices](#best-practices)

## Overview

The Reports System is framework for generating various types of reports from data stored in a Neo4j database. It allows users to define a chain of operations that extract, transform, and visualize data in different formats.

The system is designed to be extensible, allowing new tools to be added as needed. It uses a JSON configuration file to define the report generation process, which can include database queries, data transformations, and visualization generation.

### Key Features

- **Flexible Configuration**: Define complex report generation processes using a simple JSON configuration file
- **Chainable Operations**: Execute a sequence of operations, with each operation using the results of previous operations
- **Multiple Output Formats**: Generate reports in various formats, including JSON, PlantUML diagrams, and more
- **Enhanced Visualizations**: Create visually appealing diagrams with modern color schemes, clear labels, and proper spacing
- **Business Context**: Add business-friendly explanations alongside technical details to make diagrams accessible to all stakeholders
- **Extensible Architecture**: Add new tools to the system to support additional data sources or output formats

## Report Configuration Structure

A report configuration is a JSON file with the following structure:

```json
{
  "resultSelection": "<SpEL expression to select final results>",
  "chain": [
    {
      "tool": "<tool name>",
      "parameters": {
        "<parameter name>": "<parameter value>"
      },
      "outType": "<output type>"
    }
  ]
}
```

### Fields

- **resultSelection**: A Spring Expression Language (SpEL) expression that selects and processes the final results from the chain execution. This is optional; if not provided, all results from the chain will be returned.
- **chain**: An array of tool executions to be performed in sequence.
  - **tool**: The name of the tool to execute.
  - **parameters**: A map of parameters for the tool.
  - **outType**: The expected output type of the tool execution (string, decimal, integer, map, list).

## Available Tools

The system provides the following built-in tools:

### api_call

Makes HTTP requests to external APIs.

**Parameters**:
- **url** (required): The URL to call
- **method** (optional, default: "GET"): The HTTP method to use (GET, POST, PUT, DELETE, etc.)
- **body** (optional): The request body
- **headers** (optional): A map of HTTP headers

**Example**:
```json
{
  "tool": "api_call",
  "parameters": {
    "url": "http://localhost:7474/db/neo4j/tx/commit",
    "method": "POST",
    "headers": {
      "Authorization": "Basic {...put credentials}",
      "Content-Type": "application/json"
    },
    "body": {
      "statements": [
        {
          "statement": "MATCH (n) RETURN n LIMIT 10"
        }
      ]
    }
  },
  "outType": "string"
}
```

### jolt

Applies Jolt transformations to JSON data.

**Parameters**:
- **input** (required): The input JSON data to transform
- **spec** (required): The Jolt transformation specification

**Example**:
```json
{
  "tool": "jolt",
  "parameters": {
    "input": "${#results[0]}",
    "spec": "[{ \"operation\": \"shift\", \"spec\": { \"results\": { \"0\": { \"data\": \"data\" } } } }]"
  },
  "outType": "list"
}
```

### shell_run

Executes shell commands.

**Parameters**:
- **os** (required): The operating system (e.g., "windows;", "linux")
- **command** (required): The command to execute

**Example**:
```json
{
  "tool": "shell_run",
  "parameters": {
    "os": "windows",
    "command": "dir"
  },
  "outType": "string"
}
```

## Creating and Customizing Reports

To create a new report, follow these steps:

1. **Define the Report Requirements**: Determine what data you need to extract, how it should be transformed, and what the final output should look like.

2. **Create a Configuration File**: Create a JSON file with the structure described above, defining the chain of operations needed to generate the report.

3. **Test the Configuration**: Use the API endpoint to test the report configuration and verify that it produces the expected results.

4. **Refine the Configuration**: Adjust the configuration as needed to improve the report's accuracy, performance, or presentation.

### Using Previous Results

Each tool in the chain can access the results of previous tools using SpEL expressions. The results are available in the `#results` array, where `#results[0]` is the result of the first tool, `#results[1]` is the result of the second tool, and so on.

For example, to use the result of the first tool as input to the second tool:

```json
{
  "tool": "jolt",
  "parameters": {
    "input": "${#results[0]}",
    "spec": "..."
  },
  "outType": "list"
}
```

### Output Types

The `outType` field specifies the expected output type of the tool execution. The following output types are supported:

- **string**: A JSON string
- **decimal**: A decimal number
- **integer**: An integer number
- **map**: A JSON object
- **list**: A JSON array

## Examples

### Example 1: Basic Neo4j Query

This example shows a simple report that queries a Neo4j database and returns the results.

```json
{
  "chain": [
    {
      "tool": "api_call",
      "parameters": {
        "url": "http://localhost:7474/db/neo4j/tx/commit",
        "method": "POST",
        "headers": {
          "Authorization": "Basic {...put credentials here...}",
          "Content-Type": "application/json"
        },
        "body": {
          "statements": [
            {
              "statement": "MATCH (n) RETURN n LIMIT 10"
            }
          ]
        }
      },
      "outType": "string"
    }
  ]
}
```

### Example 2: Generating Enhanced PlantUML Diagrams

This example shows how to generate enhanced PlantUML diagrams from Neo4j data with improved visuals and business-friendly explanations.

```json
{
  "resultSelection": "${T(java.util.stream.Stream).concat(T(java.util.stream.Stream).concat(#results[1].stream(), {{'id': '9', 'type': 'plantuml', 'name': 'JCL Flow Diagram', 'description': 'Enhanced JCL flow diagram with business context and improved visuals', 'content': {'data': @Utils.extractMarkdownCode(#results[3]['llmResponse'])}}}.stream()), {{'id': '10', 'type': 'plantuml', 'name': 'Business Process Flow', 'description': 'Comprehensive process flow diagram showing the business and technical flow of logic across programs with enhanced visuals', 'content': {'data': @Utils.extractMarkdownCode(#results[5]['llmResponse'])}}}.stream()).toList()}",
  "chain": [
    {
      "tool": "api_call",
      "parameters": {
        "url": "http://localhost:7474/db/neo4j/tx/commit",
        "method": "POST",
        "headers": {
          "Authorization": "Basic {...put credentials here...}",
          "Content-Type": "application/json"
        },
        "body": {
          "statements": [
            {
              "statement": "MATCH (jcl:COBOLJcl) WHERE jcl.rawCode IS NOT NULL RETURN jcl.name AS JCLName, jcl.rawCode AS RawCode ORDER BY JCLName"
            }
          ]
        }
      },
      "outType": "map"
    },
    {
      "tool": "jolt",
      "parameters": {
        "input": "${#results[0]}",
        "spec": "[{ \"operation\": \"shift\", \"spec\": { \"results\": { \"0\": { \"data\": \"data\" } } } }]"
      },
      "outType": "list"
    },
    {
      "tool": "api_call",
      "parameters": {
        "url": "http://localhost:8089/api/v2/prompt?projectUUID=99664c5a-571f-4384-8c8b-956ce1507897",
        "method": "POST",
        "body": "${'Create a plantuml script for JCL flow diagram using the following JCL data (without anything else, just the script):\\n\\nJCL Names and Raw Code:\\n' + T(String).join('\\n\\n', #results[2]['results'][0]['data'].![#this['row'][0] + ':\\n' + #this['row'][1]]) + '\\n\\nIMPORTANT VISUAL REQUIREMENTS:\\n1. Use a modern, visually appealing color scheme with contrasting colors for different elements\\n2. Add clear labels and meaningful descriptions\\n3. Use different shapes or colors to distinguish between different types of components\\n4. Improve readability with proper spacing and alignment\\n5. Include a legend to explain the symbols and colors used\\n\\nBUSINESS EXPLANATION REQUIREMENTS:\\n1. For each technical element, add a note or label with a business-friendly explanation\\n2. Explain what each JCL job does in business terms (e.g., \"Processes customer transactions\" instead of just \"CUSTPROC\")\\n3. Use business terminology alongside technical terms\\n4. Add context about how each component fits into the overall business process\\n5. Include brief descriptions of the business purpose of each major component'}"
      },
      "outType": "map"
    },

    {
      "tool": "api_call",
      "parameters": {
        "url": "http://localhost:7474/db/neo4j/tx/commit",
        "method": "POST",
        "headers": {
          "Authorization": "Basic {...put credentials here...}",
          "Content-Type": "application/json"
        },
        "body": {
          "statements": [
            {
              "statement": "// Start with all COBOL programs\nMATCH (program:COBOLProgram)\n\n// Get the main paragraph flow within each program\nWITH program,\n     [(program)-[:CONTAINS]->(pd:COBOLProcedureDivision)-[:CONTAINS]->(para:COBOLParagraph) | para.name] AS paragraphs\n\n// Get CALL statements to other programs\nOPTIONAL MATCH (program)-[:CONTAINS]->(:COBOLProcedureDivision)-[:CONTAINS]->(para:COBOLParagraph)-[:CONTAINS]->(call:COBOLCall)\nWITH program, paragraphs, call.name AS calledProgram\n\n// Get PERFORM statements within the program\nOPTIONAL MATCH (program)-[:CONTAINS]->(:COBOLProcedureDivision)-[:CONTAINS]->(para:COBOLParagraph)-[:CONTAINS]->(perform:COBOLPerform)\nWITH program, paragraphs, calledProgram, perform.name AS performedParagraph, para.name AS sourceParagraph\n\n// Return the results\nRETURN \n    program.name AS ProgramName,\n    paragraphs AS ProgramParagraphs,\n    calledProgram AS CalledProgram,\n    sourceParagraph AS SourceParagraph,\n    performedParagraph AS PerformedParagraph\nORDER BY ProgramName"
            }
          ]
        }
      },
      "outType": "map"
    },

    {
      "tool": "api_call",
      "parameters": {
        "url": "http://localhost:8089/api/v2/prompt?projectUUID=99664c5a-571f-4384-8c8b-956ce1507897",
        "method": "POST",
        "body": "${'Create a plantuml script for a process flow diagram showing the flow of logic across programs using the following callgraph data (without anything else, just the script):\\n\\nProgram Call and Flow Data:\\n' + T(String).join('\\n\\n', #results[4]['results'][0]['data'].![#this['row'][0] + ' has paragraphs: ' + #this['row'][1] + (#this['row'][2] != null ? '\\nCalls program: ' + #this['row'][2] : '') + (#this['row'][3] != null && #this['row'][4] != null ? '\\nParagraph ' + #this['row'][3] + ' performs: ' + #this['row'][4] : '')]) + '\\n\\nIMPORTANT VISUAL REQUIREMENTS:\\n1. Use a modern, visually appealing color scheme with contrasting colors for different elements\\n2. Add clear labels and meaningful descriptions\\n3. Use different shapes or colors to distinguish between programs, paragraphs, and calls\\n4. Improve readability with proper spacing and alignment\\n5. Include a legend to explain the symbols and colors used\\n6. Use gradients or shadows to enhance visual appeal\\n7. Group related components together visually\\n\\nBUSINESS EXPLANATION REQUIREMENTS:\\n1. For each program and major paragraph, add a note with a business-friendly explanation\\n2. Explain what each program does in business terms (e.g., \"Customer Data Validation\" instead of just \"CUSTVAL\")\\n3. Use business terminology alongside technical terms\\n4. Add context about how each program fits into the overall business process\\n5. Include brief descriptions of the business purpose of each major component\\n6. Explain the business significance of program-to-program calls'}"
      },
      "outType": "map"
    }
  ]
}
```

## Best Practices

### Security

- **Avoid Hardcoding Credentials**: Don't hardcode credentials in the report configuration. Use environment variables or a secure credential store instead.
- **Validate Input**: Always validate input data before processing it, especially when using the `shell_run` tool.
- **Limit Access**: Restrict access to the report generation API to authorized users only.

### Performance

- **Optimize Database Queries**: Write efficient Neo4j queries to minimize database load and response time.
- **Limit Result Sets**: Use LIMIT clauses in Neo4j queries to avoid returning large result sets.
- **Cache Results**: Consider caching report results for frequently accessed reports.

### Maintainability

- **Document Report Configurations**: Include comments in your report configurations to explain their purpose and how they work.
- **Use Meaningful Names**: Use descriptive names for tools, parameters, and result variables.
- **Break Down Complex Reports**: Split complex reports into smaller, more manageable parts.
- **Version Control**: Store report configurations in a version control system to track changes over time.

### Error Handling

- **Validate Configurations**: Validate report configurations before executing them to catch errors early.
- **Handle Errors Gracefully**: Implement error handling to provide meaningful error messages when report generation fails.
- **Log Errors**: Log errors and exceptions to help diagnose and fix issues.
