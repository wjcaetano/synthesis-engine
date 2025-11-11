# Package Diagram for COBOL Application

## Overview
This document provides the necessary scripts to generate a Package Diagram for the COBOL application. The diagram organizes COBOL programs and copybooks into logical groups, showing program groupings, copybook dependencies, and logical subsystems.

## Benefits
- Helps understand the modular structure of the application
- Visualizes program groupings and dependencies
- Identifies logical subsystems within the application

## Neo4j Cypher Query
The following Cypher query extracts the package structure from the COBOL application data:

```cypher
// Match all COBOL programs
MATCH (program:COBOLProgram)

// Match copybooks used by programs
OPTIONAL MATCH (program)-[:USES]->(copybook:COBOLCopybook)

// Match program calls between programs
OPTIONAL MATCH (program)-[:CALLS]->(calledProgram:COBOLProgram)

// Group programs by their functional area
WITH program, 
     CASE 
       WHEN program.name CONTAINS 'CUST' THEN 'CustomerManagement'
       WHEN program.name CONTAINS 'TRAN' THEN 'TransactionProcessing'
       ELSE 'Utilities'
     END AS packageName,
     collect(DISTINCT copybook) AS usedCopybooks,
     collect(DISTINCT calledProgram) AS calledPrograms

// Return the results organized by package
RETURN packageName,
       collect({
         name: program.name,
         copybooks: [cb IN usedCopybooks WHERE cb IS NOT NULL | cb.name],
         calls: [cp IN calledPrograms WHERE cp IS NOT NULL | cp.name]
       }) AS programs
ORDER BY packageName
```

## PlantUML Script
The following PlantUML script generates the Package Diagram:

```plantuml
@startuml Package Diagram

' Set theme and styling for better visualization
!theme cerulean
skinparam backgroundColor white
skinparam packageStyle rectangle
skinparam packageBackgroundColor lightyellow
skinparam packageBorderColor black
skinparam packageFontColor black
skinparam packageFontSize 14
skinparam packageFontStyle bold
skinparam arrowColor #33658A
skinparam linetype ortho

' Title
title <size:20><b>COBOL Application Package Diagram</b></size>

' Legend
legend right
  <b>Package Diagram</b>
  |= Symbol |= Description |
  | <back:lightyellow>Package</back> | Logical grouping of programs |
  | <back:lightblue>Program</back> | COBOL program |
  | <back:lightgreen>Copybook</back> | COBOL copybook |
  | -----> | Dependency |
endlegend

' Define packages
package "Transaction Processing" as TransactionProcessing {
  [CUSTTRN1] as CUSTTRN1 #lightblue
  [CUSTTRN2] as CUSTTRN2 #lightblue
  note bottom of CUSTTRN1
    Main transaction processing program
    Handles ADD, UPDATE, DELETE operations
  end note
  note bottom of CUSTTRN2
    Subroutine for processing updates
    Called by CUSTTRN1
  end note
}

package "Data Structures" as DataStructures {
  [TRANREC.cpy] as TRANREC #lightgreen
  [CUSTCOPY.cpy] as CUSTCOPY #lightgreen
  note bottom of TRANREC
    Transaction record layout
    Used for processing transactions
  end note
  note bottom of CUSTCOPY
    Customer record layout
    Defines customer data structure
  end note
}

package "File Operations" as FileOperations {
  [File I/O] as FileIO #lightblue
  note bottom of FileIO
    File operations for:
    - CUSTOMER-FILE
    - TRANSACTION-FILE
    - CUSTOMER-FILE-OUT
    - REPORT-FILE
  end note
}

package "Reporting" as Reporting {
  [Report Generation] as ReportGen #lightblue
  note bottom of ReportGen
    Generates transaction reports
    and error messages
  end note
}

' Define relationships
CUSTTRN1 --> CUSTTRN2 : calls
CUSTTRN1 --> TRANREC : uses
CUSTTRN1 --> CUSTCOPY : uses
CUSTTRN2 --> TRANREC : uses
CUSTTRN2 --> CUSTCOPY : uses
CUSTTRN1 --> FileIO : performs
CUSTTRN1 --> ReportGen : performs

' Add overall system boundary
rectangle "COBOL Transaction Processing System" {
  TransactionProcessing
  DataStructures
  FileOperations
  Reporting
}

@enduml
```

## Diagram Explanation

The Package Diagram organizes the COBOL application into the following logical groups:

1. **Transaction Processing**
   - Contains the main transaction processing programs (CUSTTRN1, CUSTTRN2)
   - CUSTTRN1 is the main program that handles ADD, UPDATE, and DELETE operations
   - CUSTTRN2 is a subroutine called by CUSTTRN1 to process updates

2. **Data Structures**
   - Contains the copybooks that define the data structures used by the programs
   - TRANREC.cpy defines the transaction record layout
   - CUSTCOPY.cpy defines the customer record layout

3. **File Operations**
   - Represents the file I/O operations performed by the programs
   - Includes operations on CUSTOMER-FILE, TRANSACTION-FILE, CUSTOMER-FILE-OUT, and REPORT-FILE

4. **Reporting**
   - Represents the reporting functionality of the application
   - Includes generation of transaction reports and error messages

The diagram shows the dependencies between these components, illustrating how the programs use copybooks and call other programs.

## How to Use

1. **Neo4j Query**:
   - Load the COBOL application data into a Neo4j database
   - Run the provided Cypher query to extract the package structure
   - The query results can be used to generate a custom visualization

2. **PlantUML Diagram**:
   - Copy the PlantUML script to a PlantUML editor (e.g., [PlantUML Online Server](https://www.plantuml.com/plantuml/))
   - Generate the diagram
   - Export the diagram as an image for inclusion in documentation

## Conclusion

This Package Diagram provides a clear visualization of the modular structure of the COBOL application, helping stakeholders understand the organization of programs and copybooks into logical groups. It serves as a valuable tool for developers, architects, and other stakeholders involved in maintaining or modernizing the application.