# Model-Based Persistence in Orchestra AI

## Overview

This document explains the new **model-based persistence** approach implemented in `lmt-jira-report3.yaml` that uses the `@@@objectify` command with declarative models for improved performance and maintainability.

## Motivation

The previous approach used large Freemarker templates to manually construct JSON structures with nodes and relationships. This had several drawbacks:

- **143+ lines** of complex Freemarker code
- Difficult to maintain and error-prone
- Creates large intermediate JSON structures in memory
- Repeats similar logic for each entity type

## Solution

The new approach uses **declarative models** that are processed by `@@@objectify` + `@@@nodify` + `@@@neo4j`:

### How It Works

1. **Models** (lines 618-680): Define the structure of each entity type
2. **Templates** (lines 1312-1343): Process entities using models
3. **@@@objectify**: Transforms data using model definitions
4. **@@@nodify**: Converts objectified data to graph nodes/relationships
5. **@@@neo4j**: Persists to Neo4j database

## Architecture

### Commands Used

- `@@@objectify("${#recipe['models']['ModelName']}")`: Transforms data using a model
- `@@@repeat("${#list}", "variable", "template")`: Iterates over a list
- `@@@nodify`: Converts to graph structure (nodes + relationships)
- `@@@neo4j`: Persists to Neo4j

### Data Flow

```
normalizedUsers (raw data)
    ↓
@@@repeat → iterate each user
    ↓
@@@objectify(JiraUser model) → transform to node structure
    ↓
processedNodes[] → accumulate all nodes
    ↓
@@@nodify → convert to graph structure
    ↓
@@@neo4j → persist to database
```

## Models Definition

Each model defines:

```yaml
ModelName:
  "": "${#inputVariable}"              # Input data variable
  labels: ["Label1", "Label2"]          # Neo4j node labels
  key: "${#self['uniqueId']}"           # Unique identifier
  propertyName: "${#self['value']}"     # Node properties
  relationships:                        # Relationships to other nodes
    - label: "RELATIONSHIP_TYPE"
      endKey: "${#self['targetId']}"
```

### Example: JiraIssue Model

```yaml
JiraIssue:
  "": "${#issueToSave}"
  labels: ["Issue", "JiraReport"]
  key: "${#self['issueKey'] ?: 'unknown-issue'}"
  summary: "${#self['summary'] ?: 'No summary'}"
  status: "${#self['status'] ?: ''}"
  relationships:
    - label: "ASSIGNED_TO"
      endKey: "${#self['assignee']['accountId']}"
    - label: "REPORTED_BY"
      endKey: "${#self['reporter']['accountId']}"
    - label: "BELONGS_TO_EPIC"
      endKey: "${#self['parent']['key']}"
```

## Templates

### Individual Node Processing

```yaml
persistIssueNode: |-
  @@@objectify("${#recipe['models']['JiraIssue']}")
  @@@set("processedNodes[]")
```

This template:
1. Takes the current `issueToSave` variable
2. Applies the JiraIssue model transformation
3. Appends result to `processedNodes` array

### Batch Processing

```yaml
mergeAllEntitiesWithModels: |-
  @@@log("#FFFF00[PHASE 4.1] Merging all entities using models...")
  @@@spel("${#projectContext.put('processedNodes', @Utils.createConcurrentList())}")
  @@@get("normalizedUsers")
  @@@repeat("${#content}", "userToSave", "${#recipe['templates']['persistUserNode']}")
  @@@get("normalizedEpics")
  @@@repeat("${#content}", "epicToSave", "${#recipe['templates']['persistEpicNode']}")
  @@@get("classifiedIssues")
  @@@repeat("${#content}", "issueToSave", "${#recipe['templates']['persistIssueNode']}")
  @@@script("extractStatusChanges")
  @@@repeat("${#statusChanges}", "statusChange", "${#recipe['templates']['persistStatusChangeNode']}")
  @@@get("processedNodes")
  @@@set("unifiedNodes")
  @@@jsonify
```

### Persistence

```yaml
persistGraphWithModels: |-
  @@@log("#00FF00[PHASE 5.2] Persisting knowledge graph to Neo4j using models...")
  @@@get("unifiedNodes")
  @@@nodify
  @@@neo4j
  @@@jsonify
```

## How to Enable

To use the new model-based approach, update the `flow` section:

```yaml
phase4_graph:
  # NEW APPROACH (Recommended)
  graph/mergeEntitiesWithModels.json: "${#recipe['templates']['mergeAllEntitiesWithModels']}"
  # OLD APPROACH (Comment out or remove)
  # graph/mergeEntities.json: "${#recipe['templates']['mergeAllEntities']}"
  graph/deduplicate.json: "${#recipe['templates']['deduplicateGraph']}"
  graph/validate.json: "${#recipe['templates']['validateGraph']}"

phase5_persistence:
  persistence/createIndexes.json: "${#recipe['templates']['createNeo4jIndexes']}"
  # NEW APPROACH (Recommended)
  persistence/persistGraph.json: "${#recipe['templates']['persistGraphWithModels']}"
  # OLD APPROACH (Comment out or remove)
  # persistence/persistGraph.json: "${#recipe['templates']['persistGraphToNeo4j']}"
```

## Benefits

### Code Reduction
- **Before**: 143 lines of Freemarker
- **After**: ~40 lines using models

### Maintainability
- Centralized structure in models
- Changes to models automatically propagate
- Clearer separation of concerns

### Performance
- Incremental processing instead of building giant JSON
- Lower memory footprint
- Faster execution

### Extensibility
- Easy to add new entity types
- Simple to add/remove properties
- Straightforward relationship management

## Implementation Details

### Scripts

The `extractStatusChanges` Groovy script extracts status changes from issues:

```groovy
class ExtractStatusChanges implements IExecutor {
  @Override
  Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
    def issues = projectContext.classifiedIssues ?: []
    def statusChanges = []

    issues.each { issue ->
      def issueKey = issue.issueKey
      def dailyChanges = issue.dailyStatusChanges ?: []

      dailyChanges.each { change ->
        statusChanges.add([
          issueKey: issueKey,
          from: change.from ?: '',
          to: change.to ?: '',
          date: change.date ?: '',
          author: change.author ?: '',
          authorId: change.authorId ?: 'unknown'
        ])
      }
    }

    projectContext.put("statusChanges", statusChanges)
    return statusChanges
  }
}
```

### Model Resolution

When `@@@objectify` is called with a model:

1. **ScriptService2.java** (line 1271-1295) handles the `objectify` command
2. Evaluates the model expression: `${#recipe['models']['JiraUser']}`
3. Calls `recursivelyEvaluate` (line 1738-1871) to process the model
4. Recursively evaluates all SpEL expressions (`${...}`)
5. Returns the transformed object structure

### Nodify Processing

The `@@@nodify` command (Transforms.java):

1. Validates each entity has `key` and `labels` fields
2. Extracts properties (skips reserved fields)
3. Processes `relationships` array
4. Creates Neo4j-compatible nodes and relationships
5. Returns `{ nodes: [...], relationships: [...] }`

## Comparison: Old vs New

### Old Approach (Freemarker)

```yaml
mergeAllEntities: |-
  @@@freemarker
  @@@objectify
  {
    "nodes": [
      <#list (normalizedUsers![]) as user>
      {
        "id": "${user.userId}",
        "key": "${user.userId}",
        "labels": ["User", "JiraReport"],
        "properties": {
          "key": "${user.userId}",
          "name": "${user.userName}",
          "email": "${user.userEmail!''}",
          ...
        }
      }<#sep>,</#sep>
      </#list>
      ...
    ],
    "relationships": [
      <#list (classifiedIssues![]) as issue>
        <#if issue.assignee?? && issue.assignee.accountId??>
      {
        "source": "${issue.issueKey}",
        "target": "${issue.assignee.accountId}",
        "type": "ASSIGNED_TO"
      },
        </#if>
      </#list>
      ...
    ]
  }
```

### New Approach (Models)

```yaml
# Model definition (reusable)
JiraUser:
  "": "${#userToSave}"
  labels: ["User", "JiraReport"]
  key: "${#self['userId']}"
  name: "${#self['userName']}"
  email: "${#self['userEmail']}"

# Template (simple iteration)
mergeAllEntitiesWithModels: |-
  @@@get("normalizedUsers")
  @@@repeat("${#content}", "userToSave", "${#recipe['templates']['persistUserNode']}")
  ...
```

## Troubleshooting

### Error: "Invalid entity structure: missing 'key' or 'labels'"

**Cause**: The model doesn't define both `key` and `labels` at the root level.

**Solution**: Ensure your model has:
```yaml
ModelName:
  key: "${#self['uniqueId']}"
  labels: ["Label1", "Label2"]
```

### Error: "No such property: userToSave"

**Cause**: The variable name in `@@@repeat` doesn't match the model's input variable.

**Solution**: Match the variable names:
```yaml
@@@repeat("${#users}", "userToSave", ...)  # userToSave must match model's ""
```

### Relationships Not Created

**Cause**: The `endKey` in relationships points to non-existent node.

**Solution**: Ensure target nodes are created first and keys match:
```yaml
relationships:
  - label: "ASSIGNED_TO"
    endKey: "${#self['assignee']['accountId']}"  # Must match User.key
```

## Future Enhancements

- **Validation**: Add JSON Schema validation for models
- **Batch Optimization**: Process nodes in configurable batch sizes
- **Partial Updates**: Only update changed nodes
- **Relationship Inference**: Auto-detect relationships from data
- **Model Inheritance**: Support base models with extensions

## References

- ScriptService2.java (line 1271-1295): `@@@objectify` implementation
- ScriptService2.java (line 1738-1871): `recursivelyEvaluate` logic
- Transforms.java (line 14-42): `@@@nodify` implementation
- business-taxonomy.yaml: Example of model usage in another recipe
