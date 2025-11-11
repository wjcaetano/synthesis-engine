# Deployment Diagram for COBOL Application

## Purpose
This deployment diagram illustrates the physical deployment of the COBOL application components, showing how the system is physically distributed across the mainframe environment.

## Key Elements
- Mainframe environment
- File systems
- Batch processing components
- Physical data stores

## Benefits
- Helps understand the operational environment and infrastructure dependencies
- Provides a clear view of how components are deployed physically
- Assists in planning for system maintenance and upgrades
- Facilitates communication between technical and non-technical stakeholders

## Cypher Query for Neo4j
The following Cypher query extracts the necessary information from the Neo4j database to create the deployment diagram:

```cypher
// Cypher query to extract deployment information
MATCH (program:COBOLProgram)
MATCH (file:COBOLFile)
MATCH (job:JCLJob)-[:EXECUTES]->(program)
MATCH (program)-[:READS|WRITES]->(file)
RETURN program.name as ProgramName, 
       collect(DISTINCT file.name) as Files, 
       collect(DISTINCT job.name) as Jobs
```

## PlantUML Deployment Diagram

```plantuml
@startuml Deployment Diagram - COBOL Application

!define MAINFRAME #E8F8F5
!define FILESYSTEM #D5F5E3
!define BATCH #FCF3CF
!define DATASTORE #FADBD8

skinparam backgroundColor white
skinparam handwritten false
skinparam defaultTextAlignment center
skinparam wrapWidth 200
skinparam maxMessageSize 150

skinparam node {
  BackgroundColor MAINFRAME
  BorderColor #2E86C1
  FontColor #17202A
  FontSize 14
}

skinparam database {
  BackgroundColor DATASTORE
  BorderColor #E74C3C
  FontColor #17202A
  FontSize 14
}

skinparam folder {
  BackgroundColor FILESYSTEM
  BorderColor #27AE60
  FontColor #17202A
  FontSize 14
}

skinparam rectangle {
  BackgroundColor BATCH
  BorderColor #F39C12
  FontColor #17202A
  FontSize 14
}

skinparam arrow {
  Color #566573
  FontColor #17202A
  FontSize 12
}

skinparam title {
  FontColor #17202A
  FontSize 22
  BorderColor #ABB2B9
}

title Deployment Diagram - COBOL Application

node "Mainframe Environment" as mainframe {
  rectangle "JCL Job\nTESTJCL" as jcl #BATCH
  
  node "Batch Processing Components" as batch {
    rectangle "CUSTTRN1\nMain Program" as custtrn1 #BATCH
    rectangle "CUSTTRN2\nSubroutine" as custtrn2 #BATCH
  }
  
  folder "File Systems" as files {
    database "CUSTOMER-FILE\n(Input)" as custfile #DATASTORE
    database "TRANSACTION-FILE\n(Input)" as tranfile #DATASTORE
    database "CUSTOMER-FILE-OUT\n(Output)" as custout #DATASTORE
    database "REPORT-FILE\n(Output)" as reportfile #DATASTORE
  }
}

jcl --> custtrn1 : executes
custtrn1 --> custtrn2 : calls
custtrn1 --> custfile : reads
custtrn1 --> tranfile : reads
custtrn1 --> custout : writes
custtrn1 --> reportfile : writes

note right of mainframe
  <b>Mainframe System</b>
  Running batch processing for
  customer transaction management
end note

note bottom of files
  <b>Physical Data Stores</b>
  Customer and transaction data
  stored in mainframe datasets
end note

note right of batch
  <b>Batch Processing</b>
  Processes customer transactions
  (ADD, UPDATE, DELETE)
end note

@enduml
```

## Prompt 1: Generate Cypher Query for Neo4j Data Extraction

```
You are tasked with creating a Cypher query to extract deployment information from a Neo4j database containing COBOL application data. The extracted data will be used to generate a deployment diagram.

The Neo4j database contains the following node types:
- COBOLProgram: Represents COBOL programs (e.g., CUSTTRN1, CUSTTRN2)
- COBOLFile: Represents files used by the programs (e.g., CUSTOMER-FILE, TRANSACTION-FILE)
- JCLJob: Represents JCL jobs that execute the programs (e.g., TESTJCL)

These nodes are connected by relationships such as:
- EXECUTES: JCLJob to COBOLProgram
- CALLS: COBOLProgram to COBOLProgram
- READS: COBOLProgram to COBOLFile
- WRITES: COBOLProgram to COBOLFile

Create a Cypher query that extracts:
1. All COBOL programs and their names
2. All files read or written by each program
3. All JCL jobs that execute the programs
4. The relationships between these components

The query should be optimized to provide all necessary information for creating a comprehensive deployment diagram that shows the physical deployment of the application components, including the mainframe environment, file systems, batch processing components, and physical data stores.

Ensure the query is efficient and returns results in a format that can be easily processed by the next step in the pipeline.
```

## Prompt 2: Generate PlantUML Deployment Diagram

```
Using the data extracted from the Neo4j database with the Cypher query, create a PlantUML script for a deployment diagram that visualizes the physical deployment of a COBOL application.

The Neo4j data contains information about:
- COBOL programs (CUSTTRN1, CUSTTRN2)
- Files used by the programs (CUSTOMER-FILE, TRANSACTION-FILE, CUSTOMER-FILE-OUT, REPORT-FILE)
- JCL jobs (TESTJCL)
- Relationships between these components (executes, calls, reads, writes)

Your PlantUML script should:

1. Create a visually appealing deployment diagram with clear organization and professional styling
2. Show the mainframe environment as the main deployment node
3. Include the following components:
   - Batch processing components (COBOL programs)
   - File systems (input and output files)
   - JCL jobs that execute the programs
4. Illustrate the relationships between components with appropriate arrows and labels
5. Use color coding to distinguish between different types of components
6. Include informative notes to explain key aspects of the deployment
7. Optimize the layout for readability and comprehension

The diagram should be detailed enough for technical stakeholders while remaining accessible to executives and other non-technical stakeholders. Focus on showing the physical deployment aspects rather than logical relationships.

Use PlantUML's deployment diagram notation with appropriate stereotypes, and enhance the visual appeal with custom styling. The final diagram should help stakeholders understand the operational environment and infrastructure dependencies of the COBOL application.
```