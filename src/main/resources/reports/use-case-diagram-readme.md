# Use Case Diagram - Customer Transaction Processing System

## Purpose
This document provides the Cypher query and PlantUML script for generating a Use Case Diagram of the COBOL Customer Transaction Processing System.

- **Purpose**: Identify the main functions of the system from a user perspective
- **Target Audience**: All stakeholders (developers, executives, tech leads, stakeholders)
- **Key Elements**:
  - System boundary
  - Actors (users, external systems)
  - Use cases (Add Customer, Update Customer, Delete Customer, etc.)
- **Benefits**: Provides a functional view of the system capabilities

## Cypher Query for Neo4j

The following Cypher query can be used to extract the use case information from the COBOL application:

```cypher
// Define the system boundary by identifying the main COBOL programs
MATCH (p:COBOLProgram)
WHERE p.name IN ['CUSTTRN1', 'CUSTTRN2']

// Find main paragraphs that represent use cases
OPTIONAL MATCH (p)-[:CONTAINS]->(para:COBOLParagraph)
WHERE para.name IN [
  '100-PROCESS-TRANSACTIONS',
  '200-PROCESS-UPDATE-TRAN',
  '210-PROCESS-ADD-TRAN',
  '220-PROCESS-DELETE-TRAN',
  '299-REPORT-BAD-TRAN',
  '830-REPORT-TRAN-PROCESSED',
  '850-REPORT-TRAN-STATS'
]

// Find file interactions to identify external systems
OPTIONAL MATCH (p)-[:READS|WRITES]->(f:COBOLFile)

// Find program calls to identify interactions
OPTIONAL MATCH (p1:COBOLProgram)-[:CALLS]->(p2:COBOLProgram)
WHERE p1.name = 'CUSTTRN1' AND p2.name = 'CUSTTRN2'

// Return the results
RETURN 
  p.name as Program,
  collect(distinct para.name) as UseCases,
  collect(distinct f.name) as ExternalSystems,
  collect(distinct p2.name) as CalledPrograms
```

## PlantUML Script

The following PlantUML script creates a comprehensive Use Case Diagram for the Customer Transaction Processing System:

```plantuml
@startuml Customer Transaction Processing System

' Use professional skinparam settings for better visuals
skinparam handwritten false
skinparam shadowing false
skinparam roundcorner 12
skinparam linetype ortho
skinparam packageStyle rectangle
skinparam monochrome false
skinparam dpi 300

' Use an elegant color scheme
skinparam backgroundColor white
skinparam usecaseBorderColor #2C3E50
skinparam usecaseBackgroundColor #ECF0F1
skinparam actorBorderColor #2C3E50
skinparam actorBackgroundColor #ECF0F1
skinparam arrowColor #2C3E50
skinparam packageBorderColor #2C3E50
skinparam packageBackgroundColor #FAFAFA
skinparam noteBackgroundColor #FFFFCC
skinparam noteBorderColor #2C3E50

' Title with business-friendly formatting
title <size:20><b>Customer Transaction Processing System</b></size>\n<size:14>Use Case Diagram</size>

' Define actors with improved styling
actor "Batch Scheduler" as scheduler #E74C3C
actor "Operations Team" as operations #3498DB
actor "External Systems" as external #27AE60

' Define system boundary with a more elegant style
rectangle "Customer Transaction Processing System" as system {
  ' Main use cases
  usecase "Process Customer\nTransactions" as UC1 #D5F5E3
  
  ' Transaction types
  usecase "Add Customer" as UC2 #D5F5E3
  usecase "Update Customer" as UC3 #D5F5E3
  usecase "Delete Customer" as UC4 #D5F5E3
  
  ' Supporting use cases
  usecase "Validate Transactions" as UC5 #D5F5E3
  usecase "Generate Transaction\nReports" as UC6 #D5F5E3
  usecase "Handle Transaction\nErrors" as UC7 #D5F5E3
  
  ' Relationships between use cases
  UC1 <.. UC2 : <<include>>
  UC1 <.. UC3 : <<include>>
  UC1 <.. UC4 : <<include>>
  UC2 ..> UC5 : <<include>>
  UC3 ..> UC5 : <<include>>
  UC4 ..> UC5 : <<include>>
  UC1 ..> UC6 : <<include>>
  UC5 ..> UC7 : <<extend>>
}

' Actor relationships with improved styling
scheduler --> UC1 : initiates
operations --> UC6 : reviews
operations --> UC7 : resolves
external --> UC1 : provides data for

' Add detailed notes for clarity
note bottom of UC1
  <b>Main Process:</b>
  Handles all transaction types (ADD, UPDATE, DELETE)
  from the transaction file. Serves as the central
  processing hub for the entire system.
end note

note bottom of UC3
  <b>Update Process:</b>
  Updates customer balance or orders
  using CUSTTRN2 subroutine. Validates
  data before applying changes.
end note

note bottom of UC6
  <b>Reporting:</b>
  Generates detailed reports on transaction
  processing statistics and errors for
  business analysis and auditing.
end note

note right of external
  <b>External Data Sources:</b>
  Provide customer and transaction
  data files for processing.
end note

@enduml
```

## Diagram Explanation

The Use Case Diagram illustrates:

1. **System Boundary**: The "Customer Transaction Processing System" rectangle represents the system boundary, containing all use cases.

2. **Actors**:
   - **Batch Scheduler** (Red): Initiates the batch processing job
   - **Operations Team** (Blue): Reviews reports and resolves errors
   - **External Systems** (Green): Provides customer and transaction data files

3. **Primary Use Cases**:
   - **Process Customer Transactions**: The main function that processes all transaction types
   - **Add Customer**: Creates new customer records
   - **Update Customer**: Modifies existing customer records (balance, orders)
   - **Delete Customer**: Removes customer records
   - **Validate Transactions**: Ensures transaction data is valid
   - **Generate Transaction Reports**: Creates reports on processing results
   - **Handle Transaction Errors**: Manages and reports transaction errors

4. **Relationships**:
   - **Include**: Indicates that one use case includes the functionality of another
   - **Extend**: Indicates optional behavior that may be triggered under certain conditions

## Benefits

This Use Case Diagram provides:

1. A clear functional view of the system capabilities
2. Visibility into how external actors interact with the system
3. Understanding of the main business processes supported by the application
4. A foundation for modernization planning and knowledge transfer

The diagram serves as an essential tool for all stakeholders to understand the system's functionality without needing to delve into the technical implementation details. The color coding and detailed notes enhance comprehension for both technical and non-technical audiences.

## Prompt 1: Neo4j Cypher Query Generation

```
You are a COBOL and Neo4j expert tasked with creating a Cypher query to extract data for a Use Case Diagram from a Neo4j database containing COBOL application information.

## Context
The database contains information about a legacy COBOL application called "Customer Transaction Processing System" with two main programs:
1. CUSTTRN1 - Main program that processes customer transactions
2. CUSTTRN2 - Subroutine called by CUSTTRN1 to validate and process update transactions

## Requirements
Create a Cypher query that will:
1. Identify the main COBOL programs (CUSTTRN1, CUSTTRN2)
2. Extract paragraphs that represent use cases (especially those handling transaction processing)
3. Identify file interactions to determine external systems
4. Capture program calls to understand interactions between components

## Important Neo4j Structure Information
- COBOL programs are represented as nodes with the label `:COBOLProgram`
- Paragraphs are represented as nodes with the label `:COBOLParagraph`
- Files are represented as nodes with the label `:COBOLFile`
- Relationships include:
  - `[:CONTAINS]` - Program contains a paragraph
  - `[:CALLS]` - Program calls another program
  - `[:READS]` - Program reads from a file
  - `[:WRITES]` - Program writes to a file

## Expected Output
Provide a well-commented Cypher query that returns:
1. Program names
2. Use case paragraphs associated with each program
3. External systems (files) that interact with the programs
4. Program call relationships

The query should be optimized for Neo4j and include clear comments explaining each section.
```

## Prompt 2: PlantUML Use Case Diagram Generation

```
You are a UML and software architecture expert tasked with creating a PlantUML script for a Use Case Diagram based on data extracted from a Neo4j database.

## Context
The data represents a legacy COBOL application called "Customer Transaction Processing System" with two main programs:
1. CUSTTRN1 - Main program that processes customer transactions
2. CUSTTRN2 - Subroutine called by CUSTTRN1 to validate and process update transactions

## Neo4j Query Results
The following data was extracted from Neo4j (replace this with the actual results from Prompt 1):

```json
{
  "Program": "CUSTTRN1",
  "UseCases": [
    "100-PROCESS-TRANSACTIONS",
    "200-PROCESS-UPDATE-TRAN",
    "210-PROCESS-ADD-TRAN",
    "220-PROCESS-DELETE-TRAN",
    "299-REPORT-BAD-TRAN",
    "830-REPORT-TRAN-PROCESSED",
    "850-REPORT-TRAN-STATS"
  ],
  "ExternalSystems": [
    "CUSTOMER-FILE",
    "TRANSACTION-FILE",
    "CUSTOMER-FILE-OUT",
    "REPORT-FILE"
  ],
  "CalledPrograms": [
    "CUSTTRN2"
  ]
}
```