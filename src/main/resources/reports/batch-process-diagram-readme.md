# Batch Process Diagram

## Purpose
This document provides a comprehensive visualization of the batch processing workflow in the COBOL application, focusing on JCL job flow, dependencies between jobs, scheduling information, and file dependencies.

## Cypher Query for Neo4j

The following Cypher query extracts the batch processing workflow from the Neo4j database:

```cypher
// Query to extract JCL job and its relationships
MATCH (jcl:COBOLJcl {name: "TESTJCL"})
RETURN jcl

UNION

// Query to extract COBOL programs executed by the JCL job
MATCH (jcl:COBOLJcl {name: "TESTJCL"})-[r]->(prog:COBOLProgram)
RETURN jcl, r, prog

UNION

// Query to extract file dependencies for the COBOL programs
MATCH (prog:COBOLProgram {name: "CUSTTRN1"})-[:CONTAINS]->(para:COBOLParagraph)-[:CONTAINS]->(fileOp:COBOLFileOperation)
WHERE fileOp.operation IN ["input", "output"]
RETURN prog, para, fileOp

UNION

// Query to extract program calls between COBOL programs
MATCH (prog1:COBOLProgram {name: "CUSTTRN1"})-[:CONTAINS]->(para:COBOLParagraph)-[:CONTAINS]->(call)
WHERE call.rawCode CONTAINS "CALL 'CUSTTRN2'"
RETURN prog1, para, call

UNION

// Query to extract the called program
MATCH (prog2:COBOLProgram {name: "CUSTTRN2"})
RETURN prog2
```

## PlantUML Batch Process Diagram

```plantuml
@startuml Batch Process Diagram

' Define styles for better visualization
skinparam backgroundColor white
skinparam titleFontSize 20
skinparam titleFontColor #3498db
skinparam titleFontStyle bold

skinparam legend {
  BackgroundColor #f5f5f5
  BorderColor #cccccc
  FontSize 12
}

skinparam node {
  BackgroundColor #e8f4f8
  BorderColor #3498db
  FontColor #333333
  FontSize 14
}

skinparam database {
  BackgroundColor #f8e8e8
  BorderColor #db3434
  FontColor #333333
  FontSize 14
}

skinparam arrow {
  Color #666666
  FontColor #666666
  FontSize 12
}

skinparam rectangle {
  BackgroundColor #f0f0f0
  BorderColor #999999
  FontColor #333333
  FontSize 14
  RoundCorner 10
}

title Batch Process Workflow Diagram

' JCL Job definition
rectangle "TESTJCL\nJCL Job" as TESTJCL #d4f4fb {
  rectangle "RUNJCL\nStep" as RUNJCL #c2e0ff
}

' COBOL Programs
rectangle "CUSTTRN1\nMain Program" as CUSTTRN1 #e8f4f8
rectangle "CUSTTRN2\nSubroutine" as CUSTTRN2 #e8f4f8

' Files
database "CUSTFILE\nCustomer File\n(Input)" as CUSTFILE #f8e8e8
database "TRANFILE\nTransaction File\n(Input)" as TRANFILE #f8e8e8
database "CUSTRPT.OUT\nReport File\n(Output)" as CUSTRPT #f8e8e8
database "TRANFL.OUT\nUpdated Customer File\n(Output)" as CUSTOUT #f8e8e8

' Execution flow
TESTJCL --> RUNJCL : "Executes"
RUNJCL --> CUSTTRN1 : "Executes\nPGM=CUSTTRN1"

' File dependencies
CUSTTRN1 --> CUSTFILE : "Reads\n(DD: CUSTFILE)"
CUSTTRN1 --> TRANFILE : "Reads\n(DD: TRANFILE)"
CUSTTRN1 --> CUSTRPT : "Writes\n(DD: CUSTRPT)"
CUSTTRN1 --> CUSTOUT : "Writes\n(DD: CUSTOUT)"

' Program calls
CUSTTRN1 --> CUSTTRN2 : "Calls during\nupdate processing"

' Process flow within CUSTTRN1
note right of CUSTTRN1
  Process Flow:
  1. Open files
  2. Initialize report
  3. Read customer file
  4. Process transactions:
     - Update (calls CUSTTRN2)
     - Add
     - Delete
  5. Report transaction stats
  6. Close files
end note

' Scheduling information
note top of TESTJCL
  Scheduling Information:
  - Class: A
  - Region: 4M
  - Time: 1 minute
  - MSGCLASS: H
end note

legend
  <b>Batch Process Diagram</b>
  --
  This diagram illustrates the batch processing workflow
  including JCL job flow, program dependencies,
  file interactions, and process sequence.
  
  <b>Color Legend:</b>
  <back:#d4f4fb>■</back> JCL Job
  <back:#e8f4f8>■</back> COBOL Programs
  <back:#f8e8e8>■</back> Files
endlegend

@enduml
```

## Benefits
This batch process diagram provides a clear understanding of the operational aspects of the system, including:

1. **JCL Job Flow**: Visualizes how the TESTJCL job executes the CUSTTRN1 program.
2. **Program Dependencies**: Shows the relationship between CUSTTRN1 (main program) and CUSTTRN2 (subroutine).
3. **File Dependencies**: Illustrates the input files (CUSTFILE, TRANFILE) and output files (CUSTRPT.OUT, TRANFL.OUT) used in the process.
4. **Process Sequence**: Details the sequence of operations within the CUSTTRN1 program.
5. **Scheduling Information**: Provides information about job scheduling parameters.

This visualization helps operations teams understand the system's batch processing workflow, assists developers in maintaining and enhancing the system, and provides stakeholders with a clear picture of the operational aspects of the application.



## Prompt 1: Neo4j Cypher Query Generation

```
You are a Neo4j and COBOL expert tasked with creating a Cypher query to extract batch processing workflow data from a Neo4j database containing information about a legacy COBOL application.

## Context
The database contains information about a COBOL application with the following components:
- JCL jobs (labeled as COBOLJcl)
- COBOL programs (labeled as COBOLProgram)
- COBOL paragraphs (labeled as COBOLParagraph)
- File operations (labeled as COBOLFileOperation)
- Relationships between these components

## Requirements
Create a comprehensive Cypher query that extracts:
1. The JCL job named "TESTJCL"
2. The COBOL programs executed by this JCL job
3. The file dependencies (input and output) for these COBOL programs
4. The program calls between COBOL programs (specifically CUSTTRN1 calling CUSTTRN2)
5. Any relevant paragraphs and operations that illustrate the batch processing workflow

## Important Considerations
- The query should use UNION clauses to combine different aspects of the workflow
- The query should be optimized for visualization in a batch process diagram
- Include comments explaining each part of the query
- The main COBOL programs are CUSTTRN1 (main program) and CUSTTRN2 (subroutine)
- File operations have an "operation" property that can be "input" or "output"
- Program calls can be identified by searching for "CALL" in the rawCode property

## Expected Output Format
Provide a complete Cypher query with comments that can be executed against a Neo4j database to extract all the necessary information for creating a batch process diagram.
```

## Prompt 2: PlantUML Batch Process Diagram Generation

```
You are a UML and COBOL expert tasked with creating a PlantUML script to visualize a batch process diagram for a legacy COBOL application.

## Context
I have extracted data from a Neo4j database about a COBOL application's batch processing workflow using the following Cypher query:

[INSERT CYPHER QUERY FROM PROMPT 1 HERE]

The query results show:
- A JCL job named "TESTJCL" with a step "RUNJCL" that executes the COBOL program "CUSTTRN1"
- CUSTTRN1 reads from input files (CUSTFILE, TRANFILE) and writes to output files (CUSTRPT.OUT, TRANFL.OUT)
- CUSTTRN1 calls CUSTTRN2 as a subroutine during update transaction processing
- The main process flow in CUSTTRN1 includes: opening files, initializing reports, reading customer files, processing transactions (update, add, delete), reporting transaction statistics, and closing files
- Scheduling information for the JCL job includes Class A, Region 4M, Time 1 minute, and MSGCLASS H

## Requirements
Create a comprehensive PlantUML script that visualizes:
1. The JCL job flow
2. Dependencies between programs
3. File dependencies
4. Process sequence
5. Scheduling information

## Important Considerations
- Use appropriate UML notation for different components (rectangles for programs, databases for files, etc.)
- Include styling for better visualization (colors, fonts, etc.)
- Add notes to explain process flow and scheduling information
- Include a legend to explain the diagram elements
- The diagram should be visually appealing and easy to understand for both technical and non-technical stakeholders
- Optimize the layout for clarity and readability

## Expected Output Format
Provide a complete PlantUML script that can be used to generate a comprehensive batch process diagram. The script should include styling, notes, and a legend to enhance understanding.
```

## Usage Instructions

1. **First Pipeline Step**:
    - Input the first prompt into GPT-4o
    - The model will generate a Neo4j Cypher query
    - Review and validate the query against your Neo4j database

2. **Second Pipeline Step**:
    - Copy the Cypher query output from the first step
    - Replace `[INSERT CYPHER QUERY FROM PROMPT 1 HERE]` in the second prompt with the copied query
    - Input the updated second prompt into GPT-4o
    - The model will generate a PlantUML script for the batch process diagram

3. **Diagram Generation**:
    - Use the PlantUML script with a PlantUML renderer to generate the final diagram
    - Review the diagram for accuracy and completeness
    - Make any necessary adjustments to the script for better visualization

## Important Notes

- The prompts are designed to work specifically with the structure of the Neo4j database containing the COBOL application data
- The Cypher query assumes specific node labels and relationship types as described in the prompt
- The PlantUML script assumes specific components and relationships as described in the prompt
- Adjustments may be needed based on the actual structure of your Neo4j database and the specific requirements of your batch process diagram