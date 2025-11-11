# Sequence Diagram for Transaction Processing

## Purpose
This document provides a detailed sequence diagram illustrating the transaction processing flow in the COBOL application, specifically focusing on the interaction between CUSTTRN1 and CUSTTRN2 programs, file operations, and error handling sequences.

## Key Elements
- Program calls (CUSTTRN1 calling CUSTTRN2)
- File operations
- Error handling sequences

## Benefits
- Illustrates the dynamic behavior and interaction between components
- Provides a comprehensive understanding of the transaction processing flow
- Visualizes error handling paths
- Highlights integration points between programs and files
- Supports maintenance and knowledge transfer

## Cypher Query for Neo4j
The following Cypher query can be used to extract the transaction processing flow from the Neo4j database:

```cypher
// Cypher query to extract transaction processing flow
MATCH path = (p1:COBOLProgram {name: "CUSTTRN1"})-[:CONTAINS]->(para1:COBOLParagraph)-[:PERFORMS|CALLS*1..3]->(para2)
WHERE para1.name IN ["000-MAIN", "100-PROCESS-TRANSACTIONS", "200-PROCESS-UPDATE-TRAN", "210-PROCESS-ADD-TRAN", "220-PROCESS-DELETE-TRAN"]
RETURN path

UNION

MATCH path = (p1:COBOLProgram {name: "CUSTTRN1"})-[:CONTAINS]->(para:COBOLParagraph)-[:READS|WRITES]->(file:COBOLFile)
RETURN path

UNION

MATCH path = (p1:COBOLProgram {name: "CUSTTRN1"})-[:CALLS]->(p2:COBOLProgram {name: "CUSTTRN2"})
RETURN path

UNION

MATCH path = (p1:COBOLProgram {name: "CUSTTRN2"})-[:CONTAINS]->(para:COBOLParagraph)
RETURN path

UNION

MATCH path = (para:COBOLParagraph)-[:HANDLES_ERROR]->(errorPara:COBOLParagraph)
WHERE para.name IN ["200-PROCESS-UPDATE-TRAN", "710-READ-TRAN-FILE", "730-READ-CUSTOMER-FILE", "740-WRITE-CUSTOUT-FILE"]
RETURN path
```

## PlantUML Sequence Diagram

```plantuml
@startuml Transaction Processing Sequence Diagram

' Define participants with custom styling
skinparam participant {
    BackgroundColor #F8F8F8
    BorderColor #2C3E50
    FontColor #2C3E50
}

skinparam sequence {
    ArrowColor #2C3E50
    LifeLineBorderColor #2C3E50
    LifeLineBackgroundColor #F8F8F8
    GroupBackgroundColor #ECECEC
    GroupBorderColor #2C3E50
}

skinparam note {
    BackgroundColor #FFF9C4
    BorderColor #FFD54F
}

' Define actors and systems
actor "User/Batch Job" as User
participant "JCL Job\nTESTJCL" as JCL #E3F2FD
box "COBOL Programs" #E8F5E9
    participant "CUSTTRN1" as CUSTTRN1
    participant "CUSTTRN2" as CUSTTRN2
end box
collections "Files" as Files #FFEBEE {
    database "CUSTOMER-FILE\n(Input)" as CUSTFILE
    database "TRANSACTION-FILE\n(Input)" as TRANFILE
    database "CUSTOMER-FILE-OUT\n(Output)" as CUSTOUT
    database "REPORT-FILE\n(Output)" as REPORT
}

' Start the sequence
User -> JCL: Submit job
activate JCL

JCL -> CUSTTRN1: Execute program
activate CUSTTRN1

' Main process flow
CUSTTRN1 -> CUSTTRN1: 000-MAIN
note right: Initialize date and time

CUSTTRN1 -> CUSTTRN1: 700-OPEN-FILES
CUSTTRN1 -> Files: Open input/output files
alt File Open Error
    CUSTTRN1 -> REPORT: Write error message
    CUSTTRN1 -> JCL: Return with error code
end

CUSTTRN1 -> CUSTTRN1: 800-INIT-REPORT
CUSTTRN1 -> REPORT: Write report header

CUSTTRN1 -> CUSTTRN1: 730-READ-CUSTOMER-FILE
CUSTTRN1 -> CUSTFILE: Read first customer record
alt File Read Error
    CUSTTRN1 -> CUSTTRN1: 299-REPORT-BAD-TRAN
    CUSTTRN1 -> REPORT: Write error message
end

' Transaction processing loop
loop Until WS-TRAN-EOF = 'Y'
    CUSTTRN1 -> CUSTTRN1: 100-PROCESS-TRANSACTIONS
    CUSTTRN1 -> CUSTTRN1: 710-READ-TRAN-FILE
    CUSTTRN1 -> TRANFILE: Read transaction record
    
    alt Transaction Out of Sequence
        CUSTTRN1 -> CUSTTRN1: 299-REPORT-BAD-TRAN
        CUSTTRN1 -> REPORT: Write error message
    else Transaction In Sequence
        alt Transaction Code = 'UPDATE'
            CUSTTRN1 -> CUSTTRN1: 200-PROCESS-UPDATE-TRAN
            CUSTTRN1 -> CUSTTRN1: 720-POSITION-CUST-FILE
            
            alt No Matching Customer Key
                CUSTTRN1 -> CUSTTRN1: 299-REPORT-BAD-TRAN
                CUSTTRN1 -> REPORT: Write error message
            else Matching Customer Key Found
                CUSTTRN1 -> CUSTTRN2: CALL with CUST-REC, TRANSACTION-RECORD
                activate CUSTTRN2
                
                CUSTTRN2 -> CUSTTRN2: 000-MAIN
                CUSTTRN2 -> CUSTTRN2: 100-VALIDATE-TRAN
                
                alt Validation Error
                    CUSTTRN2 -> CUSTTRN1: Return with WS-TRAN-OK = 'N'
                    CUSTTRN1 -> CUSTTRN1: 299-REPORT-BAD-TRAN
                    CUSTTRN1 -> REPORT: Write error message
                else Validation Success
                    CUSTTRN2 -> CUSTTRN2: 200-PROCESS-TRAN
                    
                    alt Field Name = 'BALANCE'
                        CUSTTRN2 -> CUSTTRN2: Update customer balance
                    else Field Name = 'ORDERS'
                        CUSTTRN2 -> CUSTTRN2: Update customer orders
                    end
                    
                    CUSTTRN2 -> CUSTTRN1: Return with WS-TRAN-OK = 'Y'
                    CUSTTRN1 -> CUSTTRN1: 740-WRITE-CUSTOUT-FILE
                    CUSTTRN1 -> CUSTOUT: Write updated customer record
                end
                
                deactivate CUSTTRN2
            end
            
        else Transaction Code = 'ADD'
            CUSTTRN1 -> CUSTTRN1: 210-PROCESS-ADD-TRAN
            CUSTTRN1 -> CUSTTRN1: 720-POSITION-CUST-FILE
            
            alt Duplicate Customer Key
                CUSTTRN1 -> CUSTTRN1: 299-REPORT-BAD-TRAN
                CUSTTRN1 -> REPORT: Write error message
            else No Duplicate
                CUSTTRN1 -> CUSTTRN1: Create new customer record
                CUSTTRN1 -> CUSTTRN1: 740-WRITE-CUSTOUT-FILE
                CUSTTRN1 -> CUSTOUT: Write new customer record
            end
            
        else Transaction Code = 'DELETE'
            CUSTTRN1 -> CUSTTRN1: 220-PROCESS-DELETE-TRAN
            CUSTTRN1 -> CUSTTRN1: 720-POSITION-CUST-FILE
            
            alt No Matching Customer Key
                CUSTTRN1 -> CUSTTRN1: 299-REPORT-BAD-TRAN
                CUSTTRN1 -> REPORT: Write error message
            else Matching Customer Key Found
                CUSTTRN1 -> CUSTTRN1: Skip writing to output file
                CUSTTRN1 -> CUSTTRN1: 730-READ-CUSTOMER-FILE
            end
            
        else Invalid Transaction Code
            CUSTTRN1 -> CUSTTRN1: 299-REPORT-BAD-TRAN
            CUSTTRN1 -> REPORT: Write error message
        end
        
        CUSTTRN1 -> CUSTTRN1: 830-REPORT-TRAN-PROCESSED
        CUSTTRN1 -> REPORT: Write transaction details
    end
end

' Finalize processing
CUSTTRN1 -> CUSTTRN1: 850-REPORT-TRAN-STATS
CUSTTRN1 -> REPORT: Write transaction statistics

CUSTTRN1 -> CUSTTRN1: 790-CLOSE-FILES
CUSTTRN1 -> Files: Close all files

CUSTTRN1 -> JCL: Return control
deactivate CUSTTRN1

JCL -> User: Job completed
deactivate JCL

@enduml
```

## Diagram Explanation

The sequence diagram illustrates the transaction processing flow in the COBOL application:

### 1. Initialization
- The process begins when a user or batch job submits the JCL job TESTJCL
- CUSTTRN1 is executed and initializes by opening files and setting up the report

### 2. Transaction Processing Loop
- CUSTTRN1 reads transaction records one by one from TRANSACTION-FILE
- For each transaction, it validates the sequence and processes based on transaction code:
  - **UPDATE**: Calls CUSTTRN2 to validate and apply updates to customer records
  - **ADD**: Creates new customer records
  - **DELETE**: Removes customer records by not copying them to the output file

### 3. Error Handling
- Various error conditions are handled throughout the process:
  - File I/O errors
  - Transaction sequence errors
  - Invalid transaction codes
  - Missing or duplicate customer keys
  - Validation errors in CUSTTRN2

### 4. Finalization
- After processing all transactions, CUSTTRN1 generates statistics
- All files are closed, and control returns to the JCL job

## Key Components

### 1. Programs
- **CUSTTRN1**: Main program that orchestrates the transaction processing
- **CUSTTRN2**: Subroutine called by CUSTTRN1 to validate and process update transactions

### 2. Files
- **CUSTOMER-FILE**: Input file containing customer records
- **TRANSACTION-FILE**: Input file containing transaction records
- **CUSTOMER-FILE-OUT**: Output file for updated customer records
- **REPORT-FILE**: Output file for processing reports and error messages

### 3. Key Paragraphs
- **000-MAIN**: Entry point for both programs
- **100-PROCESS-TRANSACTIONS**: Main transaction processing loop in CUSTTRN1
- **200-PROCESS-UPDATE-TRAN**: Handles update transactions
- **210-PROCESS-ADD-TRAN**: Handles add transactions
- **220-PROCESS-DELETE-TRAN**: Handles delete transactions
- **299-REPORT-BAD-TRAN**: Error handling routine
- **100-VALIDATE-TRAN**: Validates transactions in CUSTTRN2
- **200-PROCESS-TRAN**: Processes valid transactions in CUSTTRN2

## Business Value

This sequence diagram provides several benefits:

1. **Comprehensive Understanding**: Illustrates the complete transaction processing flow, helping stakeholders understand how the system works
2. **Error Path Visualization**: Clearly shows how various error conditions are handled
3. **Integration Points**: Highlights the interaction between programs and files
4. **Maintenance Support**: Helps developers understand the system behavior when making changes
5. **Knowledge Transfer**: Facilitates knowledge transfer to new team members

By visualizing the transaction processing flow, this diagram supports both technical understanding and business decision-making regarding the legacy COBOL application.

## Prompt 1: Generate Neo4j Cypher Query for Transaction Processing Flow

```
I need to create a Cypher query for Neo4j to extract the transaction processing flow from a COBOL application, specifically focusing on the interaction between CUSTTRN1 and CUSTTRN2 programs. This query will be used to generate data for a sequence diagram.

The COBOL application has the following structure:
1. CUSTTRN1 is the main program that processes transactions from a transaction file
2. For UPDATE transactions, CUSTTRN1 calls CUSTTRN2 to validate and process the updates
3. The application handles different transaction types (UPDATE, ADD, DELETE)
4. There are various error handling paths throughout the process

The Neo4j database contains the following node types:
- COBOLProgram: Represents COBOL programs (e.g., CUSTTRN1, CUSTTRN2)
- COBOLParagraph: Represents paragraphs within programs (e.g., 000-MAIN, 100-PROCESS-TRANSACTIONS)
- COBOLFile: Represents files used by the programs (e.g., CUSTOMER-FILE, TRANSACTION-FILE)

The relationships between nodes include:
- CONTAINS: Links programs to their paragraphs
- PERFORMS: Links paragraphs to other paragraphs they perform
- CALLS: Links programs or paragraphs to other programs they call
- READS/WRITES: Links paragraphs to files they read from or write to
- HANDLES_ERROR: Links paragraphs to error handling paragraphs

Please create a comprehensive Cypher query that:
1. Extracts the main transaction processing flow from CUSTTRN1, including key paragraphs like 000-MAIN, 100-PROCESS-TRANSACTIONS, 200-PROCESS-UPDATE-TRAN, 210-PROCESS-ADD-TRAN, and 220-PROCESS-DELETE-TRAN
2. Captures file operations (reading from and writing to files)
3. Identifies the call from CUSTTRN1 to CUSTTRN2
4. Includes the paragraphs within CUSTTRN2
5. Captures error handling paths

The query should use UNION to combine multiple MATCH clauses for different aspects of the flow. Make sure the query is optimized for Neo4j and includes appropriate comments.
```

## Prompt 2: Generate PlantUML Sequence Diagram from Neo4j Data

```
I need to create a PlantUML sequence diagram that visualizes the transaction processing flow in a COBOL application, specifically focusing on the interaction between CUSTTRN1 and CUSTTRN2 programs, file operations, and error handling sequences.

The diagram should be based on data extracted from Neo4j using the following Cypher query:

[INSERT CYPHER QUERY FROM PROMPT 1 RESULT HERE]

The COBOL application processes transactions as follows:
1. CUSTTRN1 is the main program that:
   - Opens files (700-OPEN-FILES)
   - Initializes reports (800-INIT-REPORT)
   - Reads customer file (730-READ-CUSTOMER-FILE)
   - Processes transactions (100-PROCESS-TRANSACTIONS) until end of file
   - Reports transaction stats (850-REPORT-TRAN-STATS)
   - Closes files (790-CLOSE-FILES)

2. In the transaction processing, CUSTTRN1:
   - Reads transaction file (710-READ-TRAN-FILE)
   - Evaluates transaction code (UPDATE, ADD, DELETE)
   - For UPDATE transactions, it calls CUSTTRN2 program

3. CUSTTRN2 program:
   - Validates transaction (100-VALIDATE-TRAN)
   - Processes transaction (200-PROCESS-TRAN) if valid

4. Error handling occurs throughout with 299-REPORT-BAD-TRAN in CUSTTRN1

Please create a comprehensive PlantUML sequence diagram that:
1. Shows the complete transaction processing flow
2. Includes all participants: User/Batch Job, JCL Job (TESTJCL), COBOL Programs (CUSTTRN1, CUSTTRN2), and Files (CUSTOMER-FILE, TRANSACTION-FILE, CUSTOMER-FILE-OUT, REPORT-FILE)
3. Visualizes the main process flow, including initialization, transaction processing loop, and finalization
4. Illustrates different transaction types (UPDATE, ADD, DELETE) and their processing paths
5. Shows error handling sequences for various scenarios
6. Uses proper styling for better readability (colors, notes, etc.)

The diagram should be detailed enough for technical users (developers, tech leads) but also clear enough for non-technical stakeholders (executives, business analysts). Include appropriate notes and comments to explain key aspects of the flow.

Make sure to use PlantUML's advanced features like:
- Custom styling for participants and arrows
- Boxes to group related participants
- Activation/deactivation to show when participants are active
- Alt/else blocks to show conditional processing
- Loop blocks to show repetitive processing
- Notes to provide additional context

The final diagram should be comprehensive, visually appealing, and provide valuable insights into the transaction processing flow of the COBOL application.
```
