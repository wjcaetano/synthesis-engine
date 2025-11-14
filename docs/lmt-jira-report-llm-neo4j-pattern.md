# Padrão Correto: LLM + Neo4j (Sem Tool)

## ⚠️ Correção Importante

**Nos documentos anteriores, mencionei um tool `neo4jQuery` que não existe ainda.**

O padrão correto no Synthesis Engine é:

```
┌─────────────────────────────────────────────────────────────┐
│  LLM Agent                                                  │
│  ↓                                                          │
│  Gera Cypher Query (como string JSON)                      │
│  ↓                                                          │
│  Template executa com @@@neo4j                              │
│  ↓                                                          │
│  Resultado processado e retornado ao LLM (se multi-turn)   │
└─────────────────────────────────────────────────────────────┘
```

---

## Padrão 1: LLM Gera Query → Template Executa (Análise Única)

### Caso de Uso: Análise de Bloqueadores

**Fluxo:**
1. LLM recebe contexto dos bloqueadores
2. LLM gera queries Cypher específicas para analisar cada bloqueador
3. Template executa todas as queries
4. LLM recebe resultados e faz análise final

**Implementação:**

```yaml
agents:
  - name: DEPENDENCY_ANALYST
    provider: azure
    model: gpt-4o
    temperature: 0.2
    maxTurns: 1  # Single-turn: gera queries de uma vez

templates:
  analyzeDependencies: |-
    @@@log("#00FF00Step 1: LLM generates Cypher queries...")
    @@@freemarker
    @@@agent("DEPENDENCY_ANALYST")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("generatedQueries")

    You are a Neo4j expert. Generate Cypher queries to analyze these blockers:

    [BLOCKERS]
    ${@JsonUtils.writeAsJsonString(#blockerAnalysis, true)}

    [GRAPH SCHEMA]
    Nodes: User, Epic, Issue, StatusChange
    Relationships: ASSIGNED_TO, REPORTED_BY, BELONGS_TO_EPIC, CHILD_OF, CHANGED

    [TASK]
    For each blocked issue, generate Cypher queries to find:
    1. All child issues (CHILD_OF relationship)
    2. Issues in the same epic
    3. Total story points blocked

    [OUTPUT FORMAT]
    Return JSON array of queries:
    ```json
    {
      "queries": [
        {
          "id": "blocker_LMT-123_children",
          "description": "Find all issues blocked by LMT-123",
          "cypher": "MATCH (blocked:Issue {key: 'LMT-123'})<-[:CHILD_OF]-(child:Issue) WHERE child.status <> 'Done' RETURN child.key AS childKey, child.summary AS summary, child.storyPoints AS points"
        },
        {
          "id": "blocker_LMT-123_epic_impact",
          "description": "Find epic impact of LMT-123 blocker",
          "cypher": "MATCH (blocked:Issue {key: 'LMT-123'})-[:BELONGS_TO_EPIC]->(epic:Epic)<-[:BELONGS_TO_EPIC]-(related:Issue) WHERE related.status <> 'Done' RETURN epic.name, count(related) AS affectedCount, sum(toInteger(related.storyPoints)) AS blockedPoints"
        }
      ]
    }
    ```

  executeGeneratedQueries: |-
    @@@log("#00FF00Step 2: Executing generated queries...")
    @@@spel("${#generatedQueries['queries']}")
    @@@repeat("${#content}", "query", "${#recipe['templates']['executeSingleQuery']}")
    @@@set("queryResults")
    @@@jsonify

  executeSingleQuery: |-
    @@@log("${'Executing: ' + #query['id']}")
    @@@freemarker
    @@@set("cypherToExecute")
    @@@neo4j
    @@@jolt("${#recipe['jolts']['joltNeo4jTableToJson']}")
    @@@set("result")
    @@@_spel("${#query.put('result', #result)}")
    ${query.cypher}

  analyzeQueryResults: |-
    @@@log("#00FF00Step 3: LLM analyzes query results...")
    @@@agent("DEPENDENCY_ANALYST")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("dependencyInsights")
    @@@jsonify

    You generated these queries and here are the results:

    [QUERIES AND RESULTS]
    ${@JsonUtils.writeAsJsonString(#generatedQueries, true)}

    [ANALYSIS TASK]
    Based on the query results:
    1. Calculate cascade impact for each blocker
    2. Identify critical dependencies
    3. Recommend resolution order
    4. Suggest mitigation strategies

    Return JSON:
    ```json
    {
      "blockerAnalysis": [
        {
          "issueKey": "LMT-123",
          "directlyBlocks": ["LMT-124", "LMT-125"],
          "blockedStoryPoints": 34,
          "affectedEpics": ["EPIC-1"],
          "resolutionPriority": 1,
          "reasoning": "Blocks 2 critical issues totaling 34 points",
          "recommendedAction": "Assign to senior developer immediately"
        }
      ],
      "resolutionOrder": ["LMT-123", "LMT-130"],
      "criticalPath": ["LMT-123"]
    }
    ```

# Complete pipeline
projectModel:
  dependencyAnalysis.json: |-
    @@@exec("${#recipe['templates']['analyzeDependencies']}")
    @@@exec("${#recipe['templates']['executeGeneratedQueries']}")
    @@@exec("${#recipe['templates']['analyzeQueryResults']}")
```

**Resultado:** LLM gera queries específicas → Template executa → LLM analisa resultados

---

## Padrão 2: Iterativo Multi-Turn (Para Análise Exploratória)

### Caso de Uso: Detecção de Padrões Desconhecidos

**Fluxo:**
1. LLM gera primeira query exploratória
2. Template executa e retorna resultado
3. LLM analisa resultado e decide:
   - Se encontrou padrão → gera próxima query para confirmar
   - Se não encontrou → gera query diferente
4. Repete até maxTurns ou até encontrar padrões suficientes

**Implementação:**

```yaml
agents:
  - name: PATTERN_DETECTIVE
    provider: azure
    model: gpt-4o
    temperature: 0.3
    maxTurns: 5  # Permite exploração iterativa

templates:
  detectPatterns: |-
    @@@log("#00FF00Starting iterative pattern detection...")
    @@@repeat("${T(java.util.Arrays).asList('bottlenecks', 'zombies', 'pingpong', 'hotspots', 'thrashing')}", "patternType", "${#recipe['templates']['detectSinglePattern']}")
    @@@set("allPatterns")
    @@@jsonify

  detectSinglePattern: |-
    @@@log("${'Detecting pattern: ' + #patternType}")

    # Turn 1: LLM gera query inicial
    @@@freemarker
    @@@agent("PATTERN_DETECTIVE")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("initialQuery")

    Detect "${patternType}" pattern in the Jira graph.

    [CONTEXT]
    Daily Timeline: ${@JsonUtils.writeAsJsonString(#dailyTimeline, true)}
    User Performance: ${@JsonUtils.writeAsJsonString(#userPerformance, true)}

    [GRAPH SCHEMA]
    Nodes: User, Issue, StatusChange, Epic
    Relationships: ASSIGNED_TO, CHANGED, BELONGS_TO_EPIC

    [PATTERN DEFINITIONS]
    - bottlenecks: Users with many assigned issues but low completion rate
    - zombies: Issues in "In Progress" with no status change in 14+ days
    - pingpong: Issues with frequent status changes (To Do ↔ In Progress)
    - hotspots: Epics with many blocked/high-complexity issues
    - thrashing: Issues reassigned multiple times

    [TASK]
    Generate ONE Cypher query to detect "${patternType}" pattern.

    Return JSON:
    ```json
    {
      "patternType": "${patternType}",
      "hypothesis": "Brief description of what you're looking for",
      "cypher": "MATCH ... RETURN ..."
    }
    ```

    # Execute query
    @@@freemarker
    @@@set("cypherQuery")
    @@@neo4j
    @@@jolt
    @@@set("queryResult")
    ${initialQuery.cypher}

    # Turn 2: LLM analisa resultado e decide se precisa mais queries
    @@@agent("PATTERN_DETECTIVE")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("analysis")

    You generated this query:
    ${@JsonUtils.writeAsJsonString(#initialQuery, true)}

    Results:
    ${@JsonUtils.writeAsJsonString(#queryResult, true)}

    [ANALYSIS]
    1. Did you find the "${patternType}" pattern?
    2. If yes: How severe is it? What's the impact?
    3. If no: Why not? (Maybe data doesn't show this pattern)

    Return JSON:
    ```json
    {
      "patternFound": true/false,
      "severity": "none|low|medium|high|critical",
      "affectedEntities": {
        "users": ["user@example.com"],
        "issues": ["LMT-123"],
        "count": 5
      },
      "description": "Detailed description of the pattern",
      "recommendation": "What should be done about it",
      "confidence": 0.85
    }
    ```

    @@@_spel("${#analysis.put('patternType', #patternType)}")
    @@@_spel("${#analysis.put('query', #initialQuery)}")
```

**Vantagem:** Cada padrão é detectado de forma independente e focada.

---

## Padrão 3: Query Parametrizada (Para Queries Conhecidas)

### Caso de Uso: Análise de Entity Específico

**Quando usar:** Quando você sabe exatamente qual query precisa, mas quer LLM para interpretar os resultados.

```yaml
templates:
  analyzeServiceImpact: |-
    @@@log("#00FF00Analyzing impact of technical entities...")

    # Executar query fixa (sem LLM)
    @@@neo4j
    @@@jolt
    @@@set("serviceImpacts")

    MATCH (i:Issue)-[m:MENTIONS]->(e:TechnicalEntity)
    WHERE i.status <> 'Done'
      AND i.llmBusinessImpact IN ['significant', 'critical']
    WITH e,
         count(DISTINCT i) AS issueCount,
         collect(i.key) AS issues,
         sum(CASE WHEN i.llmBusinessImpact = 'critical' THEN 1 ELSE 0 END) AS criticalCount
    WHERE issueCount >= 2
    RETURN e.type AS entityType,
           e.name AS entityName,
           issueCount,
           issues,
           criticalCount
    ORDER BY criticalCount DESC, issueCount DESC
    LIMIT 10

    # LLM interpreta resultados
    @@@agent("ENTITY_ANALYST")
    @@@set("serviceAnalysis")

    These technical entities are mentioned in multiple high-impact issues:

    ${@JsonUtils.writeAsJsonString(#serviceImpacts, true)}

    [ANALYSIS]
    For each entity:
    1. Why is it appearing in so many issues?
    2. Is this a systemic problem or coincidence?
    3. What action should be taken?
    4. Priority level?

    Provide analysis in narrative form, focusing on actionable insights.

agents:
  - name: ENTITY_ANALYST
    provider: azure
    model: gpt-4o
    temperature: 0.4
    maxTurns: 1
```

**Vantagem:** Query otimizada manualmente, LLM só para interpretação.

---

## Padrão 4: Query Builder (LLM Ajuda a Construir Query Complexa)

### Caso de Uso: Usuário Faz Pergunta Ad-Hoc

```yaml
templates:
  answerUserQuestion: |-
    @@@log("#00FF00Answering user question with graph query...")

    # Turn 1: LLM converte pergunta em Cypher
    @@@freemarker
    @@@agent("CYPHER_EXPERT")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("generatedQuery")

    Convert this user question into a Cypher query:

    [USER QUESTION]
    "${userQuestion}"

    [GRAPH SCHEMA]
    Nodes: User, Epic, Issue, StatusChange, TechnicalEntity
    Relationships: ASSIGNED_TO, REPORTED_BY, BELONGS_TO_EPIC, CHILD_OF, CHANGED, MENTIONS

    [TASK]
    Generate a Cypher query to answer the question.
    Also suggest how to present the results (table, graph, timeline, chart).

    Return JSON:
    ```json
    {
      "cypher": "MATCH ... RETURN ...",
      "explanation": "This query finds...",
      "visualizationType": "table|graph|chart|timeline"
    }
    ```

    # Execute query
    @@@freemarker
    @@@set("cypherToExecute")
    @@@neo4j
    @@@jolt
    @@@set("queryResult")
    ${generatedQuery.cypher}

    # Turn 2: LLM explica resultados
    @@@agent("CYPHER_EXPERT")
    @@@set("answer")

    You generated this query:
    ${generatedQuery.cypher}

    Results:
    ${@JsonUtils.writeAsJsonString(#queryResult, true)}

    [TASK]
    Answer the original user question in plain language.
    - Summarize key findings
    - Highlight important numbers
    - Provide context

    Original question: "${userQuestion}"

agents:
  - name: CYPHER_EXPERT
    provider: azure
    model: gpt-4o
    temperature: 0.2
    maxTurns: 2

# Uso:
projectModel:
  adhocQuery.json: |-
    @@@spel("${T(java.util.Map).of('userQuestion', 'Show me all issues blocked for more than 5 days')}")
    @@@set("vars")
    @@@exec("${#recipe['templates']['answerUserQuestion']}")
```

---

## Comparação dos Padrões

| Padrão | Quando Usar | LLM Turns | Flexibilidade | Performance |
|--------|-------------|-----------|---------------|-------------|
| **1. Gera → Executa → Analisa** | Análise estruturada conhecida | 2 | Média | Boa |
| **2. Iterativo Multi-Turn** | Exploração de padrões desconhecidos | 3-5 | Alta | Média |
| **3. Query Fixa + Interpretação** | Query conhecida, interpretação variável | 1 | Baixa | Excelente |
| **4. Query Builder Ad-Hoc** | Perguntas do usuário em linguagem natural | 2 | Muito Alta | Média |

---

## Exemplo Prático Completo: Análise de Bloqueadores

### Estrutura Completa

```yaml
# ============================================
# Agent Configuration
# ============================================
agents:
  - name: BLOCKER_QUERY_GENERATOR
    provider: azure
    model: gpt-4o
    temperature: 0.15
    maxTurns: 1

  - name: BLOCKER_ANALYST
    provider: azure
    model: gpt-4o
    temperature: 0.25
    maxTurns: 1

# ============================================
# Templates
# ============================================
templates:
  # Step 1: LLM gera queries customizadas por bloqueador
  generateBlockerQueries: |-
    @@@log("#00FF00Generating custom queries for each blocker...")
    @@@freemarker
    @@@agent("BLOCKER_QUERY_GENERATOR")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("blockerQueries")

    Generate Cypher queries to analyze these blockers:

    [BLOCKED ISSUES]
    <#list blockerAnalysis as blocker>
    - ${blocker.blockedKey}: ${blocker.blockedSummary} (blocked for ${blocker.daysBlocked} days)
    </#list>

    [GRAPH SCHEMA]
    Nodes: User, Epic, Issue, StatusChange
    Relationships:
    - Issue -[ASSIGNED_TO]-> User
    - Issue -[BELONGS_TO_EPIC]-> Epic
    - Issue -[CHILD_OF]-> Issue (parent-child)
    - StatusChange -[CHANGED]-> Issue

    [QUERIES TO GENERATE]
    For each blocked issue, generate 3 queries:

    1. **Children Query**: Find all child issues (directly blocked)
    2. **Epic Impact Query**: Find other issues in same epic
    3. **Assignment History Query**: Find if issue was reassigned (indicates complexity)

    Return JSON:
    ```json
    {
      "queries": [
        {
          "blockedIssueKey": "LMT-123",
          "queryType": "children",
          "cypher": "MATCH (blocked:Issue {key: 'LMT-123'})<-[:CHILD_OF]-(child:Issue) WHERE child.status <> 'Done' RETURN child.key, child.summary, child.storyPoints"
        },
        {
          "blockedIssueKey": "LMT-123",
          "queryType": "epic_impact",
          "cypher": "MATCH (blocked:Issue {key: 'LMT-123'})-[:BELONGS_TO_EPIC]->(epic:Epic)<-[:BELONGS_TO_EPIC]-(related:Issue) WHERE related.status <> 'Done' AND related.key <> 'LMT-123' RETURN epic.name, count(related) AS count, sum(toInteger(related.storyPoints)) AS points"
        },
        {
          "blockedIssueKey": "LMT-123",
          "queryType": "assignment_history",
          "cypher": "MATCH (issue:Issue {key: 'LMT-123'})-[:ASSIGNED_TO]->(users:User) RETURN count(DISTINCT users) AS assigneeCount"
        }
      ]
    }
    ```

  # Step 2: Executar todas as queries
  executeBlockerQueries: |-
    @@@log("#00FF00Executing ${#blockerQueries['queries'].size()} queries...")
    @@@spel("${#blockerQueries['queries']}")
    @@@repeat("${#content}", "query", "${#recipe['templates']['executeOneBlockerQuery']}")
    @@@set("executedQueries")
    @@@jsonify

  executeOneBlockerQuery: |-
    @@@log("${'Executing ' + #query['queryType'] + ' for ' + #query['blockedIssueKey']}")
    @@@freemarker
    @@@set("cypherCommand")
    @@@neo4j
    @@@jolt
    @@@set("queryResult")
    @@@_spel("${#query.put('result', #queryResult)}")

    ${query.cypher}

  # Step 3: LLM analisa todos os resultados
  analyzeBlockerResults: |-
    @@@log("#00FF00Analyzing query results...")
    @@@agent("BLOCKER_ANALYST")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("blockerInsights")
    @@@jsonify

    You generated queries to analyze blockers. Here are the results:

    [ORIGINAL BLOCKERS]
    ${@JsonUtils.writeAsJsonString(#blockerAnalysis, true)}

    [QUERY RESULTS]
    ${@JsonUtils.writeAsJsonString(#executedQueries, true)}

    [ANALYSIS TASK]
    For each blocked issue:

    1. **Calculate Impact Score:**
       - childrenCount × 10 (each child blocked is serious)
       - epicImpactPoints × 2 (story points blocked)
       - assigneeCount × 5 (reassignments indicate difficulty)

    2. **Determine Priority:**
       - P0: Impact score > 100 OR critical epic blocked
       - P1: Impact score > 50
       - P2: Impact score > 20
       - P3: Impact score ≤ 20

    3. **Recommend Action:**
       - Specific next steps
       - Suggested owner
       - Alternative approaches

    4. **Resolution Order:**
       - Order blockers by priority and impact
       - Consider dependencies between blockers

    Return JSON:
    ```json
    {
      "blockerDetails": [
        {
          "issueKey": "LMT-123",
          "impactScore": 145,
          "priority": "P0",
          "metrics": {
            "childrenBlocked": 8,
            "epicStoryPointsBlocked": 45,
            "assignmentChanges": 3,
            "daysBlocked": 12
          },
          "affectedEpics": ["EPIC-1", "EPIC-2"],
          "reasoning": "Blocks 8 issues directly, 45 story points in critical epic. High complexity indicated by 3 reassignments.",
          "recommendedAction": "Assign to senior backend developer immediately. Consider breaking into smaller tasks.",
          "alternatives": [
            "Can temporarily disable feature flag to unblock 3 of the 8 children",
            "2 children can proceed with mock implementation while waiting for fix"
          ],
          "estimatedResolutionTime": "2-3 days"
        }
      ],
      "resolutionOrder": ["LMT-123", "LMT-130", "LMT-145"],
      "criticalPathBlockers": ["LMT-123"],
      "quickWins": [
        {
          "issueKey": "LMT-130",
          "reason": "Low impact score but blocking 3 issues. Configuration change only, 2h estimate."
        }
      ],
      "summary": "3 critical blockers (P0) require immediate attention. Resolving LMT-123 will unblock 45 story points."
    }
    ```

# ============================================
# Pipeline Integration
# ============================================
projectModel:
  # ... existing steps ...
  analytics.json: "${#recipe['templates']['runAnalyticsQueries']}"

  # NEW: Deep blocker analysis
  blockerDeepAnalysis.json: |-
    @@@exec("${#recipe['templates']['generateBlockerQueries']}")
    @@@exec("${#recipe['templates']['executeBlockerQueries']}")
    @@@exec("${#recipe['templates']['analyzeBlockerResults']}")

  # ... rest of pipeline ...
  llmInsights.json: "${#recipe['templates']['generateLLMInsights']}"
```

### Saída Esperada

```json
{
  "blockerDetails": [
    {
      "issueKey": "LMT-123",
      "impactScore": 145,
      "priority": "P0",
      "metrics": {
        "childrenBlocked": 8,
        "epicStoryPointsBlocked": 45,
        "assignmentChanges": 3,
        "daysBlocked": 12
      },
      "reasoning": "Blocks 8 critical issues...",
      "recommendedAction": "Assign to senior dev immediately",
      "alternatives": ["Use feature flag workaround", "Mock implementation"]
    }
  ],
  "resolutionOrder": ["LMT-123", "LMT-130", "LMT-145"]
}
```

---

## Debugging LLM + Neo4j

### 1. Ver queries geradas

```yaml
templates:
  generateQueries: |-
    @@@agent("QUERY_GEN")
    @@@set("queries")
    @@@log("${'Generated queries: ' + @JsonUtils.writeAsJsonString(#queries, true)}")  # DEBUG
    @@@jsonify
```

### 2. Ver resultados de query

```yaml
templates:
  executeQuery: |-
    @@@neo4j
    @@@jolt
    @@@set("result")
    @@@log("${'Query result: ' + @JsonUtils.writeAsJsonString(#result, true)}")  # DEBUG
```

### 3. Validar query antes de executar

```yaml
templates:
  safeExecute: |-
    @@@freemarker
    @@@set("cypherQuery")

    <#-- Validar que query não é destrutiva -->
    <#if query.cypher?contains("DELETE") || query.cypher?contains("REMOVE") || query.cypher?contains("DETACH")>
      <#-- Log error e skip -->
      @@@log("#FF0000DANGER: Query contains destructive operation, skipping!")
      {}
    <#else>
      @@@neo4j
      @@@jolt
      @@@set("result")
      ${query.cypher}
    </#if>
```

---

## Conclusão

**Padrão Correto:**
```
LLM → Gera Cypher (JSON) → Template @@@neo4j → Executa → Resultado → (Opcionalmente) LLM analisa
```

**Não existe tool `neo4jQuery` ainda**, mas o padrão acima permite:
- ✅ Queries dinâmicas geradas por LLM
- ✅ Análise exploratória multi-turn
- ✅ Interpretação de resultados por LLM
- ✅ Flexibilidade total

**Para implementar um tool `neo4jQuery` no futuro**, seria necessário:
1. Criar classe Java que implementa interface de Tool
2. Registrar no `ToolsService`
3. Configurar para agents poderem chamar
4. Isso permitiria agents fazerem queries sem templates intermediários

**Mas por enquanto**, o padrão "LLM gera → Template executa" funciona perfeitamente! 🚀
