# BPMN Diagram for COBOL Application

This document provides a Business Process Model and Notation (BPMN) diagram for the COBOL application, focusing on the customer transaction processing system.

## 1. Purpose

The BPMN diagram models the business processes from an end-to-end perspective, showing how customer transactions are processed through the system. It is designed to help business stakeholders, analysts, developers, executives, and tech leads understand the flow of operations and decision points in the application.

## 2. Key Elements

- **Business Activities**: The main operations performed by the system
- **Events**: Triggers and results in the process flow
- **Gateways**: Decision points that determine the flow of execution
- **Swim Lanes**: Different actors or components in the system

## 3. Cypher Query for Neo4j

The following Cypher query extracts the business process flow from the Neo4j database:

```cypher
// Query to extract the business process flow for BPMN diagram
MATCH (p:COBOLProgram {name: "CUSTTRN1"})-[:CONTAINS]->(para:COBOLParagraph)
WHERE para.name IN ["000-MAIN", "100-PROCESS-TRANSACTIONS", "200-PROCESS-UPDATE-TRAN", 
                    "210-PROCESS-ADD-TRAN", "220-PROCESS-DELETE-TRAN", 
                    "299-REPORT-BAD-TRAN", "700-OPEN-FILES", "790-CLOSE-FILES"]
RETURN p, para

UNION

MATCH (p1:COBOLProgram {name: "CUSTTRN1"})-[:CONTAINS]->(para1:COBOLParagraph)-[r]->(para2:COBOLParagraph)
WHERE para1.name IN ["000-MAIN", "100-PROCESS-TRANSACTIONS", "200-PROCESS-UPDATE-TRAN", 
                     "210-PROCESS-ADD-TRAN", "220-PROCESS-DELETE-TRAN"]
RETURN p1, para1, r, para2

UNION

MATCH (p1:COBOLProgram {name: "CUSTTRN1"})-[:CONTAINS]->(para:COBOLParagraph)-[:CALLS]->(p2:COBOLProgram {name: "CUSTTRN2"})
RETURN p1, para, p2

UNION

MATCH (p:COBOLProgram {name: "CUSTTRN2"})-[:CONTAINS]->(para:COBOLParagraph)
WHERE para.name IN ["000-MAIN", "100-VALIDATE-TRAN", "200-PROCESS-TRAN"]
RETURN p, para

UNION

MATCH (p:COBOLProgram)-[:CONTAINS]->(para:COBOLParagraph)-[:READS|WRITES]->(file:COBOLFile)
WHERE p.name IN ["CUSTTRN1", "CUSTTRN2"]
RETURN p, para, file
```

## 4. PlantUML BPMN Diagram

```plantuml
@startuml BPMN Diagram for Customer Transaction Processing

' BPMN specific settings
!define BPMN https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/bpmn
!include BPMN/bpmn.puml

' Custom styling
skinparam backgroundColor white
skinparam ArrowColor #2C3E50
skinparam ActivityBorderColor #2C3E50
skinparam ActivityBackgroundColor #F8F8F8
skinparam ActivityFontColor #2C3E50
skinparam ActivityFontSize 14
skinparam ActivityFontStyle bold
skinparam NoteBackgroundColor #FFFFCC
skinparam NoteBorderColor #2C3E50
skinparam NoteFontColor #2C3E50
skinparam ParticipantBackgroundColor #F8F8F8
skinparam ParticipantBorderColor #2C3E50
skinparam ParticipantFontColor #2C3E50
skinparam ParticipantFontSize 16
skinparam ParticipantFontStyle bold
skinparam SequenceGroupBackgroundColor #ECECEC
skinparam SequenceGroupBorderColor #2C3E50
skinparam SequenceGroupFontColor #2C3E50

' Define participants (swim lanes)
participant "Transaction Processing System" as TPS
participant "Customer Database" as CDB
participant "Reporting System" as RS

' Start event
start

' Main process flow
:Initialize System;
note right: Get current date/time and display startup message

' Open files
:Open Files;
note right
  - Transaction File
  - Customer File
  - Customer Output File
  - Report File
end note

' Check file status
if (Files opened successfully?) then (yes)
  :Initialize Report;
  :Read Customer File;
  
  ' Transaction processing loop
  while (More transactions?) is (yes)
    :Read Transaction Record;
    
    if (Transaction in sequence?) then (yes)
      ' Process based on transaction type
      switch (Transaction Type)
      case (UPDATE)
        :Process Update Transaction;
        note right
          1. Position customer file
          2. Check if customer exists
          3. Call CUSTTRN2 to update
          4. Report results
        end note
        
        ' Call to CUSTTRN2
        partition "CUSTTRN2 Processing" {
          :Validate Transaction;
          note right
            Check:
            - Transaction code
            - Field name
            - Action code
          end note
          
          if (Transaction valid?) then (yes)
            :Process Transaction;
            note right
              Update customer record
              based on field and action
            end note
          else (no)
            :Set Error Message;
          endif
        }
        
        if (Update successful?) then (yes)
          :Increment Update Counter;
        else (no)
          :Report Bad Transaction;
        endif
        
      case (ADD)
        :Process Add Transaction;
        note right
          1. Position customer file
          2. Check for duplicate key
          3. Create new customer record
          4. Write to output file
        end note
        
        if (Add successful?) then (yes)
          :Increment Add Counter;
        else (no)
          :Report Bad Transaction;
        endif
        
      case (DELETE)
        :Process Delete Transaction;
        note right
          1. Position customer file
          2. Check if customer exists
          3. Skip customer record
        end note
        
        if (Delete successful?) then (yes)
          :Increment Delete Counter;
        else (no)
          :Report Bad Transaction;
        endif
        
      case (OTHER)
        :Report Invalid Transaction Code;
      endswitch
      
      if (Transaction processed successfully?) then (yes)
        :Report Transaction Processed;
      endif
      
    else (no)
      :Report Out of Sequence Transaction;
    endif
  endwhile (no)
  
  ' Copy remaining records
  :Copy Remaining Records;
  note right: Copy all remaining customer records to output file
  
  ' Generate report
  :Generate Transaction Statistics Report;
  note right
    Report counts for:
    - ADD transactions
    - UPDATE transactions
    - DELETE transactions
  end note
  
  ' Close files
  :Close Files;
  
else (no)
  :Display File Error Message;
  :Set Error Return Code;
endif

' End event
end

@enduml
```

## 5. Diagram Explanation

The BPMN diagram illustrates the end-to-end business process of the customer transaction processing system:

1. **System Initialization**: The process begins with system initialization, opening necessary files, and preparing for transaction processing.

2. **Transaction Processing Loop**: The main loop reads transaction records and processes them based on their type:
   - **UPDATE**: Updates existing customer records by calling the CUSTTRN2 subroutine
   - **ADD**: Creates new customer records
   - **DELETE**: Removes customer records

3. **Decision Points**: Key decision points include:
   - File status checks
   - Transaction sequence validation
   - Transaction type determination
   - Customer record existence checks
   - Transaction validation in CUSTTRN2

4. **Error Handling**: The process includes error handling for:
   - File opening errors
   - Out-of-sequence transactions
   - Invalid transaction codes
   - Non-existent customer records
   - Duplicate customer keys
   - Data validation errors

5. **Reporting**: The process generates reports for:
   - Processed transactions
   - Error transactions
   - Transaction statistics

6. **System Shutdown**: The process concludes with copying remaining records and closing files.

This diagram provides a comprehensive view of the business process, connecting technical implementation to business operations in a way that is understandable to both technical and non-technical stakeholders.


## Prompt 1: Cypher Query Generation

```
You are a Neo4j and COBOL expert tasked with creating a Cypher query to extract business process flow data for a BPMN diagram. The data will be used to visualize the business processes in a legacy COBOL application.

## Input Files
You have access to the following files:
1. legacy_code_final.json - Contains the COBOL program definitions with paragraphs, variables, and file operations
2. callgraph.txt - Shows the relationships between programs, paragraphs, and variables
3. neo4j-database-data-final.json - Contains the Neo4j database structure

## Task
Create a comprehensive Cypher query that extracts the following information from the Neo4j database:

1. The main COBOL programs (CUSTTRN1 and CUSTTRN2) and their key paragraphs
2. The relationships between paragraphs (especially the flow of execution)
3. The program calls (CUSTTRN1 calling CUSTTRN2)
4. File operations (reading and writing to files)
5. Any error handling or decision points in the process flow

## Requirements
- The query should focus on the business process flow, not the technical details
- Use MATCH and UNION clauses to combine different aspects of the process
- Include WHERE clauses to filter for the most relevant paragraphs
- The query should return nodes and relationships that can be used to construct a BPMN diagram
- Ensure the query captures the end-to-end process flow from initialization to completion

## Neo4j Database Structure
The Neo4j database contains the following node types:
- COBOLProgram: Represents a COBOL program
- COBOLParagraph: Represents a paragraph in a COBOL program
- COBOLFile: Represents a file used by a COBOL program
- COBOLFileOperation: Represents a file operation (read/write)

And the following relationship types:
- CONTAINS: Links a program to its paragraphs or a paragraph to its operations
- BELONGS: Links elements to their parent structures
- DEPENDS_ON: Shows dependencies between components

## Expected Output
Provide a complete, executable Cypher query that can be run against the Neo4j database to extract the business process flow data. Include comments to explain the purpose of each part of the query.
```

## Prompt 2: PlantUML BPMN Script Generation

```
You are a business process modeling expert tasked with creating a PlantUML script for a BPMN diagram. The diagram will visualize the business processes in a legacy COBOL application based on data extracted from Neo4j.

## Input
You will receive the output of a Cypher query that extracted business process flow data from Neo4j. The data includes:
1. COBOL programs (CUSTTRN1 and CUSTTRN2) and their key paragraphs
2. Relationships between paragraphs
3. Program calls
4. File operations
5. Error handling and decision points

## Task
Create a comprehensive PlantUML script that generates a BPMN diagram showing the end-to-end business process flow of the customer transaction processing system.

## Requirements
1. Use proper BPMN notation with:
   - Activities (tasks and sub-processes)
   - Events (start, end, intermediate)
   - Gateways (exclusive, parallel, inclusive)
   - Swim lanes for different actors or components
   - Connections between elements

2. Include the following business processes:
   - System initialization (opening files, preparing for processing)
   - Transaction processing loop
   - Different transaction types (UPDATE, ADD, DELETE)
   - Error handling paths
   - Reporting and statistics generation
   - System shutdown

3. Add detailed notes and explanations to make the diagram understandable to:
   - Business stakeholders
   - Analysts
   - Developers
   - Executives
   - Tech leads

4. Apply professional styling with:
   - Consistent color scheme
   - Clear fonts
   - Proper spacing
   - Logical layout
   - Visual hierarchy

## COBOL Application Overview
The application processes customer transactions with these main components:
1. CUSTTRN1: Main program that reads transactions and processes them based on type
2. CUSTTRN2: Subroutine called by CUSTTRN1 to validate and process UPDATE transactions
3. Transaction types: UPDATE, ADD, DELETE
4. Files: Transaction file, Customer file, Customer output file, Report file
5. Error handling for invalid transactions, file errors, and data validation

## Expected Output
Provide a complete, executable PlantUML script that generates a professional BPMN diagram. Include comments to explain the purpose of each section of the script. The diagram should be visually appealing and provide a clear understanding of the business process flow.
```

## Usage Instructions

1. Use these prompts in a pipeline with GPT-4o:
   - Feed Prompt 1 to GPT-4o to generate the Cypher query
   - Take the output from Prompt 1 and combine it with Prompt 2 to generate the PlantUML script

2. Important considerations:
   - Ensure the Neo4j database is properly loaded with the data from neo4j-database-data-final.json
   - The Cypher query may need adjustments based on the specific structure of your Neo4j database
   - The PlantUML script requires the BPMN extension for PlantUML

3. The resulting BPMN diagram will provide a comprehensive view of the business processes in the COBOL application, suitable for presentation to various stakeholders including business analysts, developers, executives, and tech leads.

