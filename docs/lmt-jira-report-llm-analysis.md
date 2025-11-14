# Análise da Receita lmt-jira-report: Oportunidades de Uso de LLM

## Visão Geral

A receita `lmt-jira-report` implementa um pipeline completo de análise de Issues do Jira:

1. **Coleta de Dados** → Issues do Jira via API REST
2. **Transformação** → Normalização e enriquecimento com JOLT
3. **Persistência** → Grafo de conhecimento no Neo4j
4. **Análise** → Queries Cypher para métricas e KPIs
5. **Insights** → Geração de insights com LLM
6. **Relatório** → HTML estilizado com gráficos

---

## Estrutura do Pipeline Atual

### 1. Coleta de Dados (`collectJiraIssues`)
```yaml
Jira API → JOLT Transform → Dados Normalizados
```

**Dados coletados:**
- Issues (key, summary, description, status, priority)
- Usuários (assignee, reporter)
- Épicos e relacionamentos
- Changelogs completos
- Comments, links, timetracking

### 2. Construção do Grafo (`buildGraph`)
```yaml
Neo4j Nodes:
├─ User (accountId, name, email)
├─ Epic (key, name, summary, status)
├─ Issue (key, summary, description, status, type, priority...)
└─ StatusChange (from, to, timestamp, author)

Relationships:
├─ Issue -[ASSIGNED_TO]→ User
├─ Issue -[REPORTED_BY]→ User
├─ Issue -[BELONGS_TO_EPIC]→ Epic
├─ Issue -[CHILD_OF]→ Issue
└─ StatusChange -[CHANGED]→ Issue
```

### 3. Análise e Insights (`llmInsights.json`)

**Agentes LLM já configurados:**

1. **STRATEGY_AGENT** (GPT-4o, temp=0.2)
   - Análise estratégica de dados
   - Identificação de focus areas
   - Recomendações de alto nível

2. **DAILY_SUMMARY_AGENT** (GPT-4o, temp=0.7)
   - Resumos diários em formato standup
   - 3-5 bullet points por dia
   - Destaque de anomalias

3. **BLOCKER_ANALYST** (GPT-4o, temp=0.3)
   - Análise de bloqueadores
   - **Tem acesso ao tool `neo4jQuery`**
   - Pode explorar o grafo para análises profundas

---

## Oportunidades de Uso de LLM por Etapa

### 🎯 Etapa 1: Construção e Validação de Schemas

#### **Problema:**
Os modelos de dados (JiraUser, JiraEpic, JiraIssue, JiraStatusChange) são definidos manualmente e podem não capturar todos os campos relevantes do Jira.

#### **Solução com LLM:**

**1.1 Geração Automática de Schemas**
```yaml
templates:
  generateDynamicSchema: |-
    @@@log("#00FF00Analyzing Jira API response to generate optimal schema...")
    @@@spel("${#rawIssues[0]}")  # Sample issue
    @@@jsonify
    @@@agent("SCHEMA_ARCHITECT")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("suggestedSchema")

    You are a data modeling expert. Analyze this Jira issue sample:

    ${@JsonUtils.writeAsJsonString(#content, true)}

    [TASK]
    Generate an optimal Neo4j schema with:
    1. Node labels and properties
    2. Relationship types
    3. Constraints and indexes
    4. Custom fields mapping (customfield_*)

    [OUTPUT FORMAT]
    Return JSON:
    {
      "nodes": [
        {"label": "Issue", "properties": {"key": "string", ...}, "constraints": ["UNIQUE key"]}
      ],
      "relationships": [
        {"type": "ASSIGNED_TO", "from": "Issue", "to": "User", "properties": {...}}
      ],
      "customFieldMappings": {
        "customfield_10014": {"name": "epicLink", "type": "relationship", "target": "Epic"},
        "customfield_10016": {"name": "storyPoints", "type": "integer"}
      }
    }
```

**Agent Config:**
```yaml
agents:
  - name: SCHEMA_ARCHITECT
    provider: azure
    model: gpt-4o
    temperature: 0.1  # Baixa temp para precisão
    maxTurns: 1
```

**1.2 Validação de Schema com Dados Reais**
```yaml
templates:
  validateSchemaWithLLM: |-
    @@@agent("SCHEMA_VALIDATOR")
    Validate if this Neo4j schema is complete for the given Jira data:

    [SCHEMA]
    ${@JsonUtils.writeAsJsonString(#currentSchema, true)}

    [SAMPLE DATA]
    ${@JsonUtils.writeAsJsonString(#normalizedIssues[0..5], true)}

    [VALIDATION CHECKS]
    1. Are all important fields mapped?
    2. Are relationships correctly defined?
    3. Are there any missing indexes?
    4. Suggest optimizations for query performance

    Return JSON with validation results and suggestions.
```

---

### 🔧 Etapa 2: Preparação e Enriquecimento de Dados

#### **2.1 Limpeza Semântica de Descrições**

**Problema:** Descrições do Jira podem conter:
- Markup mal formatado
- Informações duplicadas
- Ruído (assinaturas, disclaimers)

**Solução:**
```yaml
templates:
  cleanDescriptionsWithLLM: |-
    @@@spel("${#enrichedIssues}")
    @@@repeat("${#content}", "issue", "${#recipe['templates']['cleanIssueDescription']}")
    @@@set("cleanedIssues")

  cleanIssueDescription: |-
    @@@agent("DATA_CLEANER")
    @@@set("cleanDescription")
    @@@_spel("${#issue.put('description', #cleanDescription)}")

    Clean this Jira issue description:

    [ORIGINAL]
    ${issue.description}

    [REQUIREMENTS]
    - Remove markup artifacts
    - Extract only the core technical content
    - Preserve code snippets
    - Remove signatures and boilerplate
    - Return plain text, max 500 chars

    Return only the cleaned description.

agents:
  - name: DATA_CLEANER
    provider: azure
    model: gpt-4o-mini  # Modelo menor para tarefas simples
    temperature: 0.2
    maxTurns: 1
```

#### **2.2 Extração de Entidades Técnicas**

**Enriquecer issues com entidades extraídas:**

```yaml
templates:
  extractTechnicalEntities: |-
    @@@repeat("${#enrichedIssues}", "issue", "${#recipe['templates']['extractEntitiesFromIssue']}")

  extractEntitiesFromIssue: |-
    @@@agent("ENTITY_EXTRACTOR")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("entities")
    @@@_spel("${#issue.put('technicalEntities', #entities)}")

    Extract technical entities from this Jira issue:

    [ISSUE]
    Key: ${issue.issueKey}
    Summary: ${issue.summary}
    Description: ${issue.description}
    Comments: ${@JsonUtils.writeAsJsonString(#issue.comments, false)}

    [EXTRACT]
    - Services/APIs mentioned
    - Database tables/schemas
    - Error codes
    - Environment names (prod, dev, staging)
    - Technologies (Java, Spring, React, etc.)
    - Dependencies between systems

    Return JSON:
    {
      "services": ["UserService", "PaymentAPI"],
      "databases": ["user_db.accounts"],
      "errorCodes": ["ERR-500", "TIMEOUT"],
      "environments": ["production"],
      "technologies": ["Spring Boot", "PostgreSQL"],
      "dependencies": [{"from": "UserService", "to": "PaymentAPI"}]
    }

agents:
  - name: ENTITY_EXTRACTOR
    provider: azure
    model: gpt-4o
    temperature: 0.3
    maxTurns: 1
```

**Persistir entidades no Neo4j:**
```yaml
models:
  TechnicalEntity:
    "": "${#entity}"
    labels: ["TechnicalEntity", "JiraReport"]
    key: "${#self['type'] + ':' + #self['name']}"
    type: "${#self['type']}"  # service, database, error, etc.
    name: "${#self['name']}"
    firstMentionedIn: "${#self['issueKey']}"
    relationships: |-
      [{"label": "MENTIONED_IN", "endKey": "${#self['issueKey']}"}]
```

#### **2.3 Classificação Automática de Issues**

```yaml
templates:
  classifyIssuesWithLLM: |-
    @@@repeat("${#enrichedIssues}", "issue", "${#recipe['templates']['classifyIssue']}")

  classifyIssue: |-
    @@@agent("ISSUE_CLASSIFIER")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("classification")
    @@@_spel("${#issue.putAll(#classification)}")

    Classify this Jira issue:

    [ISSUE DATA]
    Summary: ${issue.summary}
    Description: ${issue.description}
    Type: ${issue.issueType}

    [CLASSIFICATION DIMENSIONS]
    1. Technical Category: [backend, frontend, database, infrastructure, security, performance]
    2. Root Cause: [code_bug, config_error, infrastructure, third_party, design_flaw, unknown]
    3. Complexity: [trivial, low, medium, high, critical]
    4. Business Impact: [low, medium, high, critical]
    5. Urgency: [can_wait, soon, urgent, immediate]

    Return JSON:
    {
      "technicalCategory": "backend",
      "rootCause": "code_bug",
      "complexity": "medium",
      "businessImpact": "high",
      "urgency": "urgent",
      "reasoning": "Brief explanation"
    }

agents:
  - name: ISSUE_CLASSIFIER
    provider: azure
    model: gpt-4o
    temperature: 0.2
    maxTurns: 1
```

---

### 📊 Etapa 3: Análise Avançada com Acesso ao Grafo

#### **3.1 Análise de Dependências com neo4jQuery Tool**

O agente `BLOCKER_ANALYST` já tem acesso ao tool `neo4jQuery`. Vamos expandir:

```yaml
templates:
  deepDependencyAnalysis: |-
    @@@agent("DEPENDENCY_ANALYST")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("dependencyInsights")

    You have access to a Neo4j graph database via the `neo4jQuery` tool.

    [TASK]
    Analyze dependencies for these blocked issues:
    ${@JsonUtils.writeAsJsonString(#blockerAnalysis, true)}

    [ANALYSIS STEPS]
    1. For each blocked issue, use neo4jQuery to find:
       - All issues that depend on it (DEPENDS_ON relationships)
       - The full dependency chain (recursive)
       - Users affected by the blocking
       - Epics impacted

    2. Calculate:
       - Cascade impact score (number of issues blocked)
       - Business priority (sum of story points blocked)
       - Time criticality (based on due dates)

    3. Recommend resolution order based on:
       - Maximum impact
       - Quickest wins
       - Critical path blocking

    [EXAMPLE QUERY]
    Use neo4jQuery like this:
    MATCH (blocked:Issue {key: 'LMT-123'})
    OPTIONAL MATCH (dependent:Issue)-[:DEPENDS_ON]->(blocked)
    RETURN blocked, collect(dependent) as dependencies

    Return JSON with your analysis and recommendations.

agents:
  - name: DEPENDENCY_ANALYST
    provider: azure
    model: gpt-4o
    temperature: 0.3
    maxTurns: 3  # Permite múltiplas queries
    tools: ["neo4jQuery"]
```

#### **3.2 Detecção de Padrões e Anomalias**

```yaml
templates:
  detectPatternsWithLLM: |-
    @@@agent("PATTERN_DETECTIVE")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("patterns")

    You have access to the Neo4j graph. Analyze patterns and anomalies:

    [CONTEXT]
    Daily Timeline: ${@JsonUtils.writeAsJsonString(#dailyTimeline, true)}
    User Performance: ${@JsonUtils.writeAsJsonString(#userPerformance, true)}
    Epic Progress: ${@JsonUtils.writeAsJsonString(#epicProgress, true)}

    [PATTERN DETECTION]
    Use neo4jQuery to investigate:

    1. **Bottleneck Users**
       - Users with many ASSIGNED_TO relationships but low completion rate
       - Users who are single points of failure

    2. **Zombie Issues**
       - Issues with no StatusChange in last N days
       - Issues with many comments but no progress

    3. **Ping-Pong Issues**
       - Issues that change status frequently (To Do → In Progress → To Do)
       - MATCH pattern: Issue with alternating status changes

    4. **Epic Risk Detection**
       - Epics with many blocked issues
       - Epics with completion rate significantly below average

    5. **Team Collaboration Issues**
       - Issues with many assignee changes
       - Issues transferred between teams frequently

    [OUTPUT]
    For each pattern found, provide:
    - Pattern name and description
    - Affected entities (issues, users, epics)
    - Severity (low/medium/high)
    - Root cause hypothesis
    - Recommended actions

    Return JSON array of patterns.

agents:
  - name: PATTERN_DETECTIVE
    provider: azure
    model: gpt-4o
    temperature: 0.4
    maxTurns: 5
    tools: ["neo4jQuery"]
```

#### **3.3 Previsão de Riscos**

```yaml
templates:
  predictRisksWithLLM: |-
    @@@agent("RISK_PREDICTOR")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("riskPredictions")

    Predict project risks based on historical data from the graph:

    [HISTORICAL DATA]
    ${@JsonUtils.writeAsJsonString(#dailyTimeline, true)}
    ${@JsonUtils.writeAsJsonString(#velocityTrends, true)}
    ${@JsonUtils.writeAsJsonString(#epicProgress, true)}

    [RISK PREDICTION]
    Use neo4jQuery to:
    1. Analyze historical blocker resolution times
    2. Identify epics with declining velocity
    3. Find issues approaching due date with status != "In Progress"
    4. Detect team members with increasing workload

    [PREDICTIONS]
    For each epic, predict:
    - Probability of missing deadline (0-100%)
    - Confidence level (low/medium/high)
    - Risk factors contributing to the prediction
    - Mitigation strategies

    Return JSON:
    {
      "epicKey": "LMT-EPIC-001",
      "predictions": [
        {
          "risk": "Missed Deadline",
          "probability": 75,
          "confidence": "high",
          "factors": ["3 blocked issues", "velocity declining 20%", "2 developers on leave"],
          "mitigation": ["Resolve blocker LMT-123 first", "Add 1 developer to team"]
        }
      ]
    }

agents:
  - name: RISK_PREDICTOR
    provider: azure
    model: gpt-4o
    temperature: 0.3
    maxTurns: 4
    tools: ["neo4jQuery"]
```

---

### 📝 Etapa 4: Geração de Relatórios Inteligentes

#### **4.1 Executive Summary Contextualizado**

Já existe `llm.executiveSummary`, mas pode ser melhorado:

```yaml
templates:
  llm.executiveSummaryEnhanced: |-
    @@@freemarker
    @@@agent("EXECUTIVE_REPORTER")
    @@@set("executiveSummary")

    Create an executive summary for C-level audience:

    [PROJECT CONTEXT]
    Project: ${$api.configs.options.jiraProjectKey}
    Period: Last ${$api.configs.options.daysBack} days

    [QUANTITATIVE DATA]
    - Overall completion: ${#epicProgress.![#this.completionPct].stream().average().orElse(0)}%
    - Active blockers: ${#blockerAnalysis.size()}
    - Velocity: ${#velocityTrends.avgDailyVelocity} pts/day
    - Team size: ${#userPerformance.size()} members

    [QUALITATIVE INSIGHTS]
    Strategy Analysis: ${@JsonUtils.writeAsJsonString(#strategyInsights, true)}
    Patterns Detected: ${@JsonUtils.writeAsJsonString(#patterns, true)}
    Risk Predictions: ${@JsonUtils.writeAsJsonString(#riskPredictions, true)}

    [REQUIREMENTS]
    1. Start with TLDR (2-3 sentences)
    2. Structure:
       - Overall Health: RAG status (Red/Amber/Green) with justification
       - Key Achievements: Top 3 wins this period
       - Critical Concerns: Top 3 risks requiring attention
       - Strategic Recommendations: 3 actionable items with expected impact
    3. Tone: Executive-level, data-driven, action-oriented
    4. Length: 250-300 words

    [OUTPUT FORMAT]
    Use markdown with clear sections.
```

#### **4.2 Relatórios Personalizados por Stakeholder**

```yaml
templates:
  generatePersonalizedReports: |-
    @@@log("#00FF00Generating personalized reports for stakeholders...")
    @@@repeat("${#stakeholders}", "stakeholder", "${#recipe['templates']['generateStakeholderReport']}")

  generateStakeholderReport: |-
    @@@agent("PERSONALIZED_REPORTER")
    @@@set("personalizedReport")

    Generate a personalized report for this stakeholder:

    [STAKEHOLDER]
    Role: ${stakeholder.role}  # PM, Tech Lead, Developer, QA
    Focus Areas: ${stakeholder.focusAreas}  # ["epic-X", "performance", "security"]

    [FULL CONTEXT]
    All Issues: ${@JsonUtils.writeAsJsonString(#enrichedIssues, true)}
    Analytics: ${@JsonUtils.writeAsJsonString(#analytics, true)}

    [FILTER AND SUMMARIZE]
    1. Filter data relevant to stakeholder's focus areas
    2. Highlight issues/blockers they should care about
    3. Provide actionable insights specific to their role

    [TONE BY ROLE]
    - PM: Business impact, timeline, risks
    - Tech Lead: Technical debt, architecture, team velocity
    - Developer: Assigned issues, blockers, code reviews
    - QA: Test coverage, bugs, quality metrics

    Return markdown report.

agents:
  - name: PERSONALIZED_REPORTER
    provider: azure
    model: gpt-4o
    temperature: 0.6
    maxTurns: 1
```

#### **4.3 Geração de Recomendações Acionáveis**

```yaml
templates:
  generateActionableRecommendations: |-
    @@@agent("ACTION_PLANNER")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("actionPlan")

    Based on all analysis, create an action plan:

    [ANALYSIS RESULTS]
    Blockers: ${@JsonUtils.writeAsJsonString(#blockerAnalysis, true)}
    Patterns: ${@JsonUtils.writeAsJsonString(#patterns, true)}
    Risks: ${@JsonUtils.writeAsJsonString(#riskPredictions, true)}
    Dependencies: ${@JsonUtils.writeAsJsonString(#dependencyInsights, true)}

    [CREATE ACTION PLAN]
    For each issue/risk identified, provide:
    1. Action ID
    2. Priority (P0/P1/P2/P3)
    3. Action title (verb + specific outcome)
    4. Owner (suggest based on expertise)
    5. Effort estimate (hours)
    6. Impact (expected improvement)
    7. Dependencies (what must happen first)
    8. Success criteria (how to measure)

    [PRIORITIZATION LOGIC]
    - P0: Blocking production, affects customers
    - P1: Blocking release, affects deadline
    - P2: Important but not blocking
    - P3: Nice to have, can be deferred

    Return JSON array of actions, sorted by priority.

agents:
  - name: ACTION_PLANNER
    provider: azure
    model: gpt-4o
    temperature: 0.3
    maxTurns: 1
```

---

## 🎨 Etapa 5: Visualizações Geradas por LLM

#### **5.1 Geração de Queries Cypher Customizadas**

```yaml
templates:
  generateCustomQueriesWithLLM: |-
    @@@agent("CYPHER_EXPERT")
    @@@extractMarkdownCode
    @@@set("customQueries")

    You are a Neo4j Cypher expert. Generate optimized queries:

    [GRAPH SCHEMA]
    Nodes: User, Epic, Issue, StatusChange, TechnicalEntity
    Relationships: ASSIGNED_TO, REPORTED_BY, BELONGS_TO_EPIC, CHILD_OF, CHANGED, MENTIONED_IN, DEPENDS_ON

    [USER REQUEST]
    "${userQuestion}"  # Ex: "Show me all issues blocked for more than 5 days"

    [GENERATE]
    1. Cypher query to answer the question
    2. Visualization type (table, graph, timeline, chart)
    3. Data transformation needed for viz

    Return JSON:
    {
      "cypherQuery": "MATCH ...",
      "vizType": "graph",
      "transformation": "Group by epic and count issues"
    }

agents:
  - name: CYPHER_EXPERT
    provider: azure
    model: gpt-4o
    temperature: 0.2
    maxTurns: 1
```

#### **5.2 Narrativas Automáticas para Gráficos**

```yaml
templates:
  generateChartNarratives: |-
    @@@repeat("${#charts}", "chart", "${#recipe['templates']['narrateChart']}")

  narrateChart: |-
    @@@agent("DATA_NARRATOR")
    @@@set("narrative")
    @@@_spel("${#chart.put('narrative', #narrative)}")

    Generate a narrative for this chart:

    [CHART DATA]
    Type: ${chart.type}
    Data: ${@JsonUtils.writeAsJsonString(#chart.data, true)}

    [NARRATIVE REQUIREMENTS]
    1. Describe the trend (2-3 sentences)
    2. Highlight key insights
    3. Call out anomalies
    4. Provide context (why this matters)
    5. Suggest actions if applicable

    Example:
    "Velocity has increased 15% over the last 2 weeks, driven primarily by the Platform team completing 23 story points. However, the Mobile team shows a declining trend, dropping from 18 to 12 points. This may indicate resource constraints or increased blocker frequency. Recommend investigating Mobile team blockers."

    Return plain text, max 150 words.

agents:
  - name: DATA_NARRATOR
    provider: azure
    model: gpt-4o
    temperature: 0.7
    maxTurns: 1
```

---

## 🔄 Pipeline Completo Sugerido com LLM

```yaml
projectModel:
  # Existing steps
  _clearDatabase: ${#recipe['templates']['clearDatabase']}
  collectJiraData: "${#recipe['templates']['collectJiraIssues']}"

  # NEW: Schema validation
  schemaValidation.json: "${#recipe['templates']['validateSchemaWithLLM']}"

  # Existing transformation
  enrichChangelogs: "${#recipe['templates']['enrichChangelogData']}"

  # NEW: Data enrichment with LLM
  cleanedData.json: "${#recipe['templates']['cleanDescriptionsWithLLM']}"
  technicalEntities.json: "${#recipe['templates']['extractTechnicalEntities']}"
  classifiedIssues.json: "${#recipe['templates']['classifyIssuesWithLLM']}"

  # Graph building
  buildGraph: "${#recipe['templates']['buildGraph']}"

  # Analytics
  analytics.json: "${#recipe['templates']['runAnalyticsQueries']}"

  # NEW: Advanced LLM analysis
  dependencyAnalysis.json: "${#recipe['templates']['deepDependencyAnalysis']}"
  patternDetection.json: "${#recipe['templates']['detectPatternsWithLLM']}"
  riskPredictions.json: "${#recipe['templates']['predictRisksWithLLM']}"

  # Insights
  llmInsights.json: "${#recipe['templates']['generateLLMInsights']}"
  actionPlan.json: "${#recipe['templates']['generateActionableRecommendations']}"

  # Reports
  daily-report.html: "${#recipe['templates']['generateHTMLReport']}"
  executive-summary.md: "${#recipe['templates']['llm.executiveSummaryEnhanced']}"
```

---

## 💰 Otimização de Custos

### Estratégias:

1. **Use modelos diferentes por tarefa:**
   - `gpt-4o-mini` para limpeza de texto, classificação simples
   - `gpt-4o` para análise estratégica, padrões complexos
   - Temperature baixa (0.1-0.3) para tarefas determinísticas

2. **Cache de prompts:**
   ```yaml
   caches:
     transforms:
       - prompt  # Já configurado!
       - neo4j
   ```

3. **Batch processing:**
   - Agrupe múltiplas issues antes de enviar para LLM
   - Use `@@@repeat` com chunks de 10-50 issues

4. **Filtragem pré-LLM:**
   - Use Cypher queries para pré-filtrar dados relevantes
   - Apenas envie dados que precisam de análise semântica

5. **Incremental updates:**
   - Apenas processe issues que mudaram desde último run
   - Armazene checksums no Neo4j

---

## 📈 Métricas de Sucesso

Adicione tracking de qualidade dos insights de LLM:

```yaml
templates:
  trackLLMQuality: |-
    @@@neo4j

    // Create quality tracking node
    CREATE (q:QualityMetrics:JiraReport {
      runId: "${@Utils.generateUUID()}",
      timestamp: datetime(),
      insightsGenerated: ${#strategyInsights.recommendations.size()},
      patternsDetected: ${#patterns.size()},
      risksIdentified: ${#riskPredictions.size()},
      avgConfidence: ${#riskPredictions.![#this.confidence].stream().average().orElse(0)}
    })
```

---

## 🚀 Próximos Passos Recomendados

### Fase 1: Quick Wins (1-2 semanas)
1. ✅ Implementar `cleanDescriptionsWithLLM`
2. ✅ Adicionar `classifyIssuesWithLLM`
3. ✅ Melhorar `llm.executiveSummary` com mais contexto

### Fase 2: Advanced Analytics (2-4 semanas)
1. ✅ Implementar `DEPENDENCY_ANALYST` com neo4jQuery
2. ✅ Criar `PATTERN_DETECTIVE` para anomalias
3. ✅ Adicionar `RISK_PREDICTOR`

### Fase 3: Full Intelligence (4-8 semanas)
1. ✅ Schema dinâmico com LLM
2. ✅ Extração de entidades técnicas
3. ✅ Geração de ações acionáveis
4. ✅ Relatórios personalizados por stakeholder

---

## 🛠️ Exemplo Prático: Adicionando Classificação de Issues

### 1. Adicionar Agent Config

```yaml
agents:
  - name: ISSUE_CLASSIFIER
    provider: azure
    model: gpt-4o-mini  # Modelo mais barato para classificação
    temperature: 0.2
    maxTurns: 1
```

### 2. Adicionar Template

```yaml
templates:
  classifyIssues: |-
    @@@log("#00FF00Classifying issues with LLM...")
    @@@spel("${#enrichedIssues}")
    @@@repeat("${#content}", "issue", "${#recipe['templates']['classifyIssue']}")
    @@@set("classifiedIssues")
    @@@jsonify

  classifyIssue: |-
    @@@freemarker
    @@@agent("ISSUE_CLASSIFIER")
    @@@extractMarkdownCode
    @@@objectify
    @@@set("classification")
    @@@_spel("${#issue.put('llmClassification', #classification)}")

    Classify this issue:
    Summary: ${issue.summary}
    Description: ${issue.description!'No description'}
    Type: ${issue.issueType}

    Return JSON:
    {
      "category": "backend|frontend|database|infrastructure",
      "complexity": "low|medium|high",
      "urgency": "low|medium|high|critical"
    }
```

### 3. Adicionar ao Pipeline

```yaml
projectModel:
  collectJiraData: "${#recipe['templates']['collectJiraIssues']}"
  enrichChangelogs: "${#recipe['templates']['enrichChangelogData']}"
  classifiedIssues: "${#recipe['templates']['classifyIssues']}"  # NOVO!
  buildGraph: "${#recipe['templates']['buildGraph']}"
  # ... resto do pipeline
```

### 4. Atualizar Modelo Neo4j

```yaml
models:
  JiraIssue:
    # ... propriedades existentes ...
    llmCategory: "${#self['llmClassification']['category']}"
    llmComplexity: "${#self['llmClassification']['complexity']}"
    llmUrgency: "${#self['llmClassification']['urgency']}"
```

### 5. Usar na Análise

```yaml
templates:
  query.highComplexityIssues: |-
    @@@neo4j
    @@@jolt
    @@@set("highComplexityIssues")

    MATCH (i:Issue)
    WHERE i.llmComplexity = 'high'
      AND i.status <> 'Done'
    RETURN i.key AS issueKey,
           i.summary AS summary,
           i.llmCategory AS category,
           i.llmUrgency AS urgency
    ORDER BY i.llmUrgency DESC
```

---

## 📚 Recursos Adicionais

### Documentação do Synthesis Engine
- `src/main/resources/recipes/README.md` - Guia de receitas
- `src/main/resources/executors/README.md` - Executors disponíveis

### Exemplos de Agents com Tools
- Veja `BLOCKER_ANALYST` config (linha 61-67) para exemplo de agent com `neo4jQuery` tool

### JOLT Transformations
- Use JOLT para normalizar respostas de LLM
- Veja `joltJiraToNormalized` (linha 176-281) como exemplo

---

## ❓ FAQ

**Q: Os LLMs vão deixar o pipeline muito lento?**
A: Use paralelização com `@@@repeat` e escolha modelos apropriados. Tasks simples com gpt-4o-mini são rápidas.

**Q: Como garantir consistência nas respostas do LLM?**
A: Use temperature baixa (0.1-0.3) e force JSON output com `@@@extractMarkdownCode` + `@@@objectify`.

**Q: Posso usar LLM local ao invés de Azure?**
A: Sim! Configure um provider local no `agents` config. O Synthesis Engine suporta múltiplos providers.

**Q: Como debugar templates com LLM?**
A: Use `@@@log` para ver prompts e respostas. Ex: `@@@log("${#strategyInsights}")`.

**Q: Os resultados de LLM são cacheados?**
A: Sim, se você configurar `caches.transforms: [prompt]`. Isso evita chamadas duplicadas.

---

## 🎯 Conclusão

Você pode usar LLM em **TODAS** as etapas do pipeline:

1. ✅ **Schema**: Geração e validação automática
2. ✅ **Preparação**: Limpeza, enriquecimento, classificação
3. ✅ **Análise**: Padrões, dependências, riscos (com acesso ao grafo!)
4. ✅ **Relatórios**: Summaries, narrativas, recomendações

**Vantagens:**
- Insights mais profundos e acionáveis
- Automação de tarefas que antes eram manuais
- Adaptação dinâmica aos dados do projeto
- Relatórios personalizados por audiência

**Próximo passo:** Escolha uma das implementações acima e comece a testar! Recomendo começar com **classificação de issues** (exemplo prático no final).
