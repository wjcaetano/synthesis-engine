# System Context Diagram for COBOL Application

## Purpose
This document provides a System Context Diagram for the COBOL application, showing it in the context of the broader IT landscape. The diagram helps identify integration points and dependencies for modernization planning.

## Key Elements
- External systems
- User groups
- Data flows across system boundaries
- Integration points

## Benefits
- Provides a strategic overview of the system in its broader context
- Identifies all integration points and dependencies for modernization planning
- Shows how data flows across system boundaries
- Clarifies how different user groups interact with the system
- Helps identify potential areas for modernization and integration with newer systems
- Provides valuable documentation for knowledge transfer and system understanding

## Cypher Query for Neo4j
The following Cypher query extracts the system context relationships from the Neo4j database. This query identifies the COBOL application, external systems, user groups, and data flows across system boundaries:

```cypher
// System Context Diagram Cypher Query
// This query extracts the COBOL application in the context of the broader IT landscape

// Start with the main JCL job that represents the entry point to the system
MATCH (jcl:COBOLJcl {name: "TESTJCL"})

// Get COBOL programs executed by the JCL
MATCH (jcl)-[:EXECUTES]->(program:COBOLProgram)

// Get all programs in the application (including those called by the main programs)
OPTIONAL MATCH (program)-[:CONTAINS]->(:COBOLProcedureDivision)-[:CONTAINS]->(:COBOLParagraph)-[:CALLS]->(calledProgram:COBOLProgram)

// Get external files used by the application
OPTIONAL MATCH (program)-[:CONTAINS]->(:COBOLDataDivision)-[:CONTAINS]->(:COBOLFileSection)-[:CONTAINS]->(file:COBOLFile)

// Get file operations (READ/WRITE)
OPTIONAL MATCH (program)-[:CONTAINS]->(:COBOLProcedureDivision)-[:CONTAINS]->(paragraph:COBOLParagraph)-[fileOp:READS|WRITES]->(file:COBOLFile)

// Return the results for system context
RETURN 
    jcl.name as EntryPoint,
    collect(DISTINCT program.name) as MainPrograms,
    collect(DISTINCT calledProgram.name) as SubPrograms,
    collect(DISTINCT file.name) as ExternalFiles,
    collect(DISTINCT {
        program: program.name,
        paragraph: paragraph.name,
        operation: type(fileOp),
        file: file.name
    }) as FileOperations
```

## PlantUML Script for System Context Visualization
The following PlantUML script creates a visually enhanced System Context Diagram based on the COBOL application structure:

```plantuml
@startuml System Context Diagram

' Styling
skinparam backgroundColor white
skinparam defaultTextAlignment center
skinparam titleFontSize 24
skinparam headerFontSize 16
skinparam footerFontSize 12
skinparam legendFontSize 14
skinparam ArrowFontSize 12
skinparam shadowing true
skinparam roundCorner 15
skinparam handwritten false
skinparam monochrome false
skinparam linetype ortho

' Color scheme - using C4 model colors with enhanced business elegance
!define SYSTEM_BG #1168BD
!define SYSTEM_BORDER #3C7FC0
!define EXTERNAL_SYSTEM_BG #999999
!define EXTERNAL_SYSTEM_BORDER #8A8A8A
!define PERSON_BG #08427B
!define PERSON_BORDER #073B6F
!define ENTERPRISE_BOUNDARY_BG #FFFFFF
!define ENTERPRISE_BOUNDARY_BORDER #444444
!define DATA_STORE_BG #438DD5
!define DATA_STORE_BORDER #3C7FC0

' Title
title <b><font size=24>System Context Diagram: COBOL Customer Transaction Processing System</font></b>

' Legend
legend right
  <b>Legend</b>
  |= Component |= Description |
  |<back:PERSON_BG><color:white>   Person   </color></back>| User or User Group |
  |<back:SYSTEM_BG><color:white>   System   </color></back>| COBOL Application |
  |<back:EXTERNAL_SYSTEM_BG><color:white>   External System   </color></back>| External System |
  |<back:DATA_STORE_BG><color:white>   Data Store   </color></back>| Data Repository |
  |<color:ENTERPRISE_BOUNDARY_BORDER>----</color>| Enterprise Boundary |
endlegend

' Enterprise boundary
rectangle "Enterprise Boundary" as EnterpriseBoundary {
  ' Main COBOL Application System
  rectangle "<b>Customer Transaction Processing System</b>\n\n<i>A COBOL-based system that processes customer transactions\nincluding updates, additions, and deletions</i>" as COBOLSystem <<SYSTEM_BG>> #SYSTEM_BG

  ' External Systems within the enterprise
  rectangle "<b>Batch Processing System</b>\n\n<i>Schedules and executes the customer\ntransaction processing jobs</i>" as BatchSystem <<EXTERNAL_SYSTEM_BG>> #EXTERNAL_SYSTEM_BG
  
  database "<b>Customer Data Store</b>\n\n<i>Stores customer records and transaction history</i>" as CustomerDataStore <<DATA_STORE_BG>> #DATA_STORE_BG
  
  database "<b>Transaction Data Store</b>\n\n<i>Stores transaction records for processing</i>" as TransactionDataStore <<DATA_STORE_BG>> #DATA_STORE_BG
  
  rectangle "<b>Reporting System</b>\n\n<i>Generates and distributes reports\non transaction processing</i>" as ReportingSystem <<EXTERNAL_SYSTEM_BG>> #EXTERNAL_SYSTEM_BG
}

' External actors/systems outside the enterprise
actor "<b>Operations Team</b>\n\n<i>Manages and monitors\nthe batch processing</i>" as OperationsTeam <<PERSON_BG>> #PERSON_BG

actor "<b>Business Users</b>\n\n<i>Submit transaction requests\nand review reports</i>" as BusinessUsers <<PERSON_BG>> #PERSON_BG

rectangle "<b>External Data Systems</b>\n\n<i>Provide data for transaction processing</i>" as ExternalDataSystems <<EXTERNAL_SYSTEM_BG>> #EXTERNAL_SYSTEM_BG

' Relationships with detailed labels
OperationsTeam --> BatchSystem : "<b>Submits and monitors</b>\nSchedules batch jobs and\nmonitors execution"

BusinessUsers --> TransactionDataStore : "<b>Submits transactions</b>\nCreates ADD, UPDATE, and\nDELETE transaction requests"

BusinessUsers <-- ReportingSystem : "<b>Provides reports</b>\nDelivers transaction\nprocessing results and statistics"

BatchSystem --> COBOLSystem : "<b>Executes programs</b>\nRuns TESTJCL to execute\nCUSTTRN1 and CUSTTRN2"

COBOLSystem --> CustomerDataStore : "<b>Reads and updates</b>\nRetrieves customer records and\nwrites updated information"

COBOLSystem --> TransactionDataStore : "<b>Processes</b>\nReads transaction data\nfor processing"

COBOLSystem --> ReportingSystem : "<b>Generates</b>\nCreates transaction\nprocessing reports"

ExternalDataSystems --> TransactionDataStore : "<b>Provides</b>\nSupplies external\ntransaction data"

' Notes with detailed information
note right of COBOLSystem
  <b>Core Components:</b>
  - CUSTTRN1 (Main program)
  - CUSTTRN2 (Update subroutine)
  - TESTJCL (JCL job)
  
  <b>Key Functions:</b>
  - Process customer transactions
  - Update customer records
  - Generate processing reports
  - Handle error conditions
end note

note bottom of CustomerDataStore
  <b>Files:</b>
  - CUSTFILE (Input)
  - TRANFL.OUT (Output)
  
  <b>Data:</b>
  - Customer identification
  - Account balances
  - Order history
  - Contact information
end note

note bottom of TransactionDataStore
  <b>Files:</b>
  - TRANFILE (Input)
  
  <b>Transaction Types:</b>
  - ADD (Create new customers)
  - UPDATE (Modify existing records)
  - DELETE (Remove customer records)
end note

note bottom of ReportingSystem
  <b>Files:</b>
  - CUSTRPT.OUT (Output)
  
  <b>Report Contents:</b>
  - Transaction statistics
  - Processing results
  - Error reports
end note

@enduml
```

## Visual Enhancements
The System Context Diagram includes several visual enhancements to improve understanding and business elegance:

1. **C4 Model Styling**: Uses the C4 model color scheme and styling for clear system context representation
2. **Clear Boundaries**: Enterprise boundary clearly delineates internal vs. external systems
3. **Descriptive Labels**: All components include both names and descriptions of their purpose
4. **Relationship Descriptions**: Data flows are labeled with descriptive text explaining the nature of the interaction
5. **User Groups**: Clearly identifies the different user groups that interact with the system
6. **Explanatory Notes**: Additional notes provide details about core components and file structures
7. **Professional Layout**: Clean, organized layout with consistent spacing and styling
8. **Comprehensive Legend**: Clear legend explaining all component types for easy reference
9. **Database Symbols**: Uses database symbols for data stores to enhance visual clarity
10. **Bold Headings**: Uses bold text for headings to improve readability

## Key Elements Explained

1. **Customer Transaction Processing System**: The core COBOL application consisting of CUSTTRN1, CUSTTRN2, and executed by TESTJCL. It processes customer transactions, updates customer records, and generates reports.

2. **Batch Processing System**: Represents the mainframe batch processing environment that schedules and executes the JCL jobs. It serves as the execution environment for the COBOL programs.

3. **Customer Data Store**: The storage system for customer records (CUSTFILE input and TRANFL.OUT output). It contains customer identification, account balances, order history, and contact information.

4. **Transaction Data Store**: The storage system for transaction records (TRANFILE input). It stores ADD, UPDATE, and DELETE transaction requests submitted by business users.

5. **Reporting System**: The system that handles the reports generated by the COBOL application (CUSTRPT.OUT). It provides transaction statistics, processing results, and error reports to business users.

6. **Operations Team**: The IT staff responsible for managing and monitoring the batch processing. They schedule jobs, monitor execution, and handle operational issues.

7. **Business Users**: The end users who submit transaction requests and review reports. They interact with the system by creating transaction requests and consuming the generated reports.

8. **External Data Systems**: External systems that provide data for transaction processing. These systems supply transaction data that is processed by the COBOL application.

This System Context Diagram provides a comprehensive view of the COBOL application's place within the broader IT landscape, helping stakeholders understand its integration points and dependencies for modernization planning.

## Prompt 1: Generate Cypher Query for Neo4j

```
You are a Neo4j and COBOL expert tasked with creating a Cypher query to extract system context information for a legacy COBOL application. This query will be used to generate a System Context Diagram showing the application in the context of the broader IT landscape.

## Input Data
The Neo4j database contains data extracted from a COBOL application with the following structure:
- Nodes labeled 'COBOLProgram' represent COBOL programs (e.g., CUSTTRN1, CUSTTRN2)
- Nodes labeled 'COBOLJcl' represent JCL jobs (e.g., TESTJCL)
- Nodes labeled 'COBOLFile' represent files used by the programs
- Nodes labeled 'COBOLParagraph' represent paragraphs within programs
- Relationships like [:EXECUTES], [:CALLS], [:CONTAINS], [:READS], [:WRITES] connect these nodes

The application consists of:
1. A main program (CUSTTRN1) that processes customer transactions (ADD, UPDATE, DELETE)
2. A subroutine (CUSTTRN2) called by CUSTTRN1 to process updates
3. A JCL job (TESTJCL) that executes CUSTTRN1
4. Input files (CUSTOMER-FILE, TRANSACTION-FILE)
5. Output files (CUSTOMER-FILE-OUT, REPORT-FILE)

## Task
Create a comprehensive Cypher query that:
1. Starts with the main JCL job (TESTJCL) as the entry point
2. Identifies all COBOL programs executed by this JCL
3. Finds all programs called by these main programs
4. Identifies all external files used by the application
5. Captures file operations (READ/WRITE) performed by program paragraphs
6. Returns a structured result that includes:
   - The entry point (JCL job)
   - Main programs executed directly by the JCL
   - Subprograms called by the main programs
   - External files used by the application
   - Detailed file operations with program, paragraph, operation type, and file name

## Output Format
The query should return results in a format that can be easily used to generate a System Context Diagram, showing the COBOL application in the context of external systems, user groups, and data flows.

## Important Considerations
- Ensure the query handles potential missing relationships with OPTIONAL MATCH
- Use DISTINCT to avoid duplicate results
- Include detailed comments explaining each part of the query
- Structure the query to be efficient and readable
- Consider the Neo4j database structure as represented in the legacy_code_final.json file
```

## Prompt 2: Generate PlantUML Script for System Context Diagram

```
You are a UML and software architecture expert tasked with creating a comprehensive System Context Diagram for a legacy COBOL application using PlantUML. This diagram will be used by developers, executives, tech leads, and stakeholders to understand the application from a reverse engineering perspective.

## Input Data
The following information has been extracted from a Neo4j database using a Cypher query:

- Entry Point: "TESTJCL" (JCL job)
- Main Programs: ["CUSTTRN1"]
- Sub Programs: ["CUSTTRN2"]
- External Files: ["CUSTOMER-FILE", "TRANSACTION-FILE", "CUSTOMER-FILE-OUT", "REPORT-FILE"]
- File Operations: [
    {program: "CUSTTRN1", paragraph: "730-READ-CUSTOMER-FILE", operation: "READS", file: "CUSTOMER-FILE"},
    {program: "CUSTTRN1", paragraph: "710-READ-TRAN-FILE", operation: "READS", file: "TRANSACTION-FILE"},
    {program: "CUSTTRN1", paragraph: "740-WRITE-CUSTOUT-FILE", operation: "WRITES", file: "CUSTOMER-FILE-OUT"},
    {program: "CUSTTRN1", paragraph: "299-REPORT-BAD-TRAN", operation: "WRITES", file: "REPORT-FILE"}
  ]

The COBOL application is a customer transaction processing system that:
1. Processes customer transactions (ADD, UPDATE, DELETE)
2. Reads from and writes to customer and transaction files
3. Generates reports on transaction processing
4. Uses a batch processing environment to execute jobs

## Task
Create a comprehensive PlantUML script that:
1. Shows the COBOL application in the context of the broader IT landscape
2. Includes external systems, user groups, and data flows across system boundaries
3. Uses visual enhancements for better understanding and business elegance
4. Provides detailed information about components and their interactions
5. Is suitable for a diverse audience including developers, executives, tech leads, and stakeholders

## Output Format
The PlantUML script should:
1. Use the C4 model styling for clear system context representation
2. Include an enterprise boundary to delineate internal vs. external systems
3. Use appropriate symbols for different component types (systems, people, data stores)
4. Include descriptive labels for all components and relationships
5. Add detailed notes explaining key components
6. Include a comprehensive legend
7. Use visual enhancements like colors, styling, and formatting for business elegance

## Important Considerations
- The diagram should be detailed enough for developers but also understandable by executives
- Use database symbols for data stores to enhance visual clarity
- Include detailed notes about core components and file structures
- Use bold text and formatting to improve readability
- Consider the relationships between components based on the file operations
- Ensure the diagram shows the complete system context, including implied components like:
  - Batch Processing System
  - Operations Team
  - Business Users
  - Reporting System
  - External Data Systems
```