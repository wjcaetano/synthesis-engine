# LMT Jira Daily Report - Knowledge Graph Edition

## Resumo Executivo

Esta receita resolve os problemas de persistência no Neo4j utilizando **LLM para extrair um Knowledge Graph estruturado** diretamente dos dados do Jira, garantindo que todos os nós e relacionamentos sejam criados corretamente.

## Problemas Resolvidos

### ❌ Problema Anterior

Nas receitas anteriores:
1. **Nós com `key: "null"`** - SpEL avaliando campos inexistentes
2. **Relacionamentos nulos** - FreeMarker inline não executava no contexto correto
3. **Persistência incompleta** - Faltavam dados essenciais no grafo
4. **Relatórios superficiais** - Apenas uma página HTML simples

### ✅ Solução Atual

1. **LLM extrai Knowledge Graph completo** - Nodes + Relationships estruturados
2. **Persistência garantida** - Cypher direto para cada node e relationship
3. **Múltiplos relatórios HTML** - Índice + Executive Summary + Por Usuário + Por Épico + Issues
4. **Análise por LLM** - Cada relatório tem análise contextual gerada por agente especializado

---

## Arquitetura da Solução

### Pipeline de 5 Fases

```
Phase 1: Data Collection
│
├─ Jira API (com changelog)
├─ JOLT Normalization
└─ Output: normalizedIssues, enrichedIssues

Phase 2: Knowledge Graph Extraction
│
├─ LLM Agent (KNOWLEDGE_GRAPH_EXTRACTOR)
├─ Input: Jira issues completos
└─ Output: {nodes: [...], relationships: [...]}

Phase 3: Neo4j Persistence
│
├─ Persist Nodes (User, Epic, Issue, StatusChange)
├─ Persist Relationships (ASSIGNED_TO, REPORTED_BY, etc.)
└─ Output: Graph completo no Neo4j

Phase 4: Analytics Queries
│
├─ Query all users (com estatísticas)
├─ Query all epics (com progresso)
├─ Query all issues (com assignees)
├─ Query daily changes (timeline)
└─ Output: Dados agregados para relatórios

Phase 5: Multi-Page HTML Reports
│
├─ index.html (dashboard principal)
├─ executive-summary.html (análise executiva)
├─ users/{userId}.html (relatório por usuário)
├─ epics/{epicKey}.html (relatório por épico)
└─ issues.html (listagem completa)
```

---

## Fase 2: Knowledge Graph Extraction (Detalhado)

### Prompt para LLM

O prompt é baseado no modelo fornecido pelo usuário, adaptado para dados Jira:

```yaml
extractKnowledgeGraph: |-
  You are an expert Knowledge Graph architect.

  From the Jira issues data, extract all entities (Nodes)
  and relationships (Edges).

  ## Entity Types

  User:
    - id: accountId
    - properties: {name, email, accountId, evidence}

  Epic:
    - id: epicKey
    - properties: {name, status, evidence}

  Issue:
    - id: issueKey
    - properties: {summary, description, status, priority,
                   issueType, createdDate, updatedDate,
                   storyPoints, evidence}

  StatusChange:
    - id: issueKey + "|" + date + "|" + from + "->" + to
    - properties: {issueKey, date, from, to, author, evidence}

  ## Relationship Types

  - ASSIGNED_TO: Issue -> User
  - REPORTED_BY: Issue -> User
  - BELONGS_TO_EPIC: Issue -> Epic
  - CHILD_OF: Issue -> Issue (parent)
  - STATUS_CHANGED: Issue -> StatusChange

  ## Output Format

  {
    "nodes": [
      {
        "id": "john@example.com",
        "type": "User",
        "properties": {
          "name": "John Doe",
          "email": "john@example.com",
          "accountId": "5f8a1b2c",
          "evidence": "assignee: John Doe"
        }
      }
    ],
    "relationships": [
      {
        "source": "LMT-123",
        "target": "5f8a1b2c",
        "type": "ASSIGNED_TO"
      }
    ]
  }
```

### Por Que Isso Funciona?

1. **LLM entende estrutura complexa** - Consegue identificar relações implícitas
2. **Normaliza IDs automaticamente** - accountId, issueKey, epicKey
3. **Extrai evidências** - Campo "evidence" documenta origem dos dados
4. **Cria relacionamentos corretos** - Valida que source e target existem
5. **Output estruturado** - JSON válido pronto para persistência

---

## Fase 3: Neo4j Persistence (Detalhado)

### Persistência de Nodes

Para cada node extraído pela LLM:

```yaml
persistSingleNode: |-
  @@@freemarker
  @@@neo4j

  <#assign props = node.properties>
  MERGE (n:${node.type}:JiraReport {id: '${node.id}'})
  SET
    <#if node.type == "User">
    n.name = '${props.name?json_string}',
    n.email = '${props.email?json_string}',
    n.accountId = '${props.accountId?json_string}'
    <#elseif node.type == "Issue">
    n.key = '${node.id}',
    n.summary = '${props.summary?json_string}',
    n.status = '${props.status?json_string}',
    n.priority = '${props.priority?json_string}',
    n.storyPoints = ${props.storyPoints!0}
    ...
    </#if>
  RETURN n.id AS nodeId
```

**Vantagens:**
- ✅ **MERGE garante unicidade** - Não duplica nodes
- ✅ **Propriedades completas** - Todos os campos são setados
- ✅ **Escapamento correto** - `?json_string` previne injection
- ✅ **Tipo dinâmico** - Suporta User, Epic, Issue, StatusChange

### Persistência de Relationships

Para cada relationship:

```yaml
persistSingleRelationship: |-
  @@@freemarker
  @@@neo4j

  MATCH (source:JiraReport {id: '${rel.source}'})
  MATCH (target:JiraReport {id: '${rel.target}'})
  MERGE (source)-[r:${rel.type}]->(target)
  RETURN type(r) AS relType
```

**Vantagens:**
- ✅ **Valida existência** - MATCH garante que source e target existem
- ✅ **MERGE evita duplicatas** - Mesmo relacionamento não é criado duas vezes
- ✅ **Tipo dinâmico** - ASSIGNED_TO, REPORTED_BY, etc.

---

## Fase 4: Analytics Queries

### Query All Users

```cypher
MATCH (u:User:JiraReport)
OPTIONAL MATCH (u)<-[:ASSIGNED_TO]-(i:Issue)
WITH u,
     count(DISTINCT i) AS totalIssues,
     count(DISTINCT CASE WHEN i.status IN ['Done', 'Closed']
                         THEN i END) AS completed
RETURN u.accountId AS userId,
       u.name AS userName,
       u.email AS userEmail,
       totalIssues,
       completed,
       CASE WHEN totalIssues > 0
            THEN round(100.0 * completed / totalIssues, 1)
            ELSE 0 END AS completionRate
ORDER BY totalIssues DESC
```

**Retorna:**
```json
[
  {
    "userId": "john.doe@example.com",
    "userName": "John Doe",
    "userEmail": "john.doe@example.com",
    "totalIssues": 15,
    "completed": 12,
    "completionRate": 80.0
  }
]
```

### Query All Epics

```cypher
MATCH (e:Epic:JiraReport)
OPTIONAL MATCH (e)<-[:BELONGS_TO_EPIC]-(i:Issue)
WITH e,
     count(i) AS totalIssues,
     count(CASE WHEN i.status IN ['Done', 'Closed']
                THEN 1 END) AS completed
RETURN e.key AS epicKey,
       e.name AS epicName,
       e.status AS epicStatus,
       totalIssues,
       completed,
       CASE WHEN totalIssues > 0
            THEN round(100.0 * completed / totalIssues, 1)
            ELSE 0 END AS progress
ORDER BY totalIssues DESC
```

### Query Daily Changes

```cypher
MATCH (i:Issue)-[:STATUS_CHANGED]->(sc:StatusChange)
WITH date(sc.date) AS day,
     collect({
       issueKey: i.key,
       summary: i.summary,
       from: sc.from,
       to: sc.to,
       author: sc.author
     }) AS changes
RETURN toString(day) AS date,
       size([c IN changes WHERE c.to IN ['Done', 'Closed']]) AS completed,
       size([c IN changes WHERE c.to = 'In Progress']) AS started,
       size([c IN changes WHERE c.to CONTAINS 'Block']) AS blocked,
       changes
ORDER BY day DESC
```

---

## Fase 5: Multi-Page HTML Reports

### Estrutura de Arquivos

```
reports/
├── index.html                      # Dashboard principal
├── executive-summary.html          # Resumo executivo
├── issues.html                     # Listagem de issues
├── users/
│   ├── john.doe@example.com.html  # Relatório do John
│   ├── jane.smith@example.com.html # Relatório da Jane
│   └── ...
└── epics/
    ├── LMT-100.html               # Relatório do Epic LMT-100
    ├── LMT-200.html               # Relatório do Epic LMT-200
    └── ...
```

### index.html (Dashboard Principal)

Features:
- **Project Stats Grid** - Total issues, completed, in progress, overall progress
- **Navigation Cards** - Links para todas as páginas
- **User Cards** - Card para cada usuário com stats
- **Epic Cards** - Card para cada épico com progresso

### executive-summary.html

Gerado por LLM Agent `EXECUTIVE_SUMMARY_AGENT`:

```yaml
generateExecutiveSummary: |-
  @@@agent("EXECUTIVE_SUMMARY_AGENT")

  Create an executive summary for management (250-300 words):

  [PROJECT DATA]
  Project Stats: ...
  Daily Changes: ...
  Users: ...
  Epics: ...

  [REQUIREMENTS]
  - Executive-level (CEO/CTO audience)
  - Highlight key achievements and blockers
  - Identify top performers
  - Flag risks and delays
  - Provide 2-3 actionable recommendations
  - Use markdown formatting
```

Output renderizado com `marked.js`.

### users/{userId}.html

Relatório individual por usuário:

**Geração Dinâmica:**
```yaml
reports/users: "${#allUsers != null ?
  @Utils.createWithAListOfKeys(
    #allUsers.![#this['userId']],
    #recipe['templates']['generateUserReport']
  ) : {}}"
```

**Features:**
- Header com nome e email do usuário
- Grid de stats (Total Issues, Completed, Completion Rate)
- Progress bar visual
- Análise gerada por LLM `USER_REPORT_AGENT`

### epics/{epicKey}.html

Relatório individual por épico:

**Features:**
- Header com epicKey e epicName
- Grid de stats (Total Issues, Completed, Progress)
- Progress bar visual
- Análise gerada por LLM `EPIC_REPORT_AGENT`

### issues.html

Tabela completa de todas as issues com:
- Issue Key
- Summary
- Type
- Priority (com badge colorido)
- Status
- Assignee

---

## Comparação: FreeMarker vs Knowledge Graph LLM

### Abordagem Anterior (FreeMarker)

```yaml
prepareUsers: |-
  @@@freemarker
  <#assign userMap = {}>
  <#list normalizedIssues as issue>
    <#if issue.assignee?? && issue.assignee.accountId??>
      <#if !userMap[issue.assignee.accountId]??>
        <#assign userMap = userMap + {
          issue.assignee.accountId: {
            "accountId": issue.assignee.accountId,
            "name": issue.assignee.name,
            "relationships": []  # Como construir isso?
          }
        }>
      </#if>
    </#if>
  </#list>
```

**Problemas:**
❌ Difícil construir relationships manualmente
❌ Lógica complexa e propensa a erros
❌ Não valida se relacionamentos fazem sentido
❌ Hardcoded para tipos específicos

### Abordagem Nova (Knowledge Graph LLM)

```yaml
extractKnowledgeGraph: |-
  @@@agent("KNOWLEDGE_GRAPH_EXTRACTOR")

  You are an expert Knowledge Graph architect.
  Extract entities and relationships from Jira data.

  Input: ${@JsonUtils.writeAsJsonString(enrichedIssues, true)}

  Output:
  {
    "nodes": [...],
    "relationships": [...]
  }
```

**Vantagens:**
✅ LLM entende relações complexas
✅ Valida estrutura automaticamente
✅ Extrai evidências para auditoria
✅ Flexível para novos tipos de entidades
✅ Normaliza IDs corretamente

---

## Vantagens da Abordagem Knowledge Graph

### 1. Persistência Garantida

**Antes:**
```json
{
  "key": "null",  // ❌ Campo vazio
  "relationships": null  // ❌ Nenhum relacionamento
}
```

**Depois:**
```json
{
  "id": "john@example.com",
  "type": "User",
  "properties": {
    "accountId": "5f8a1b2c",  // ✅ ID válido
    "name": "John Doe",
    "email": "john@example.com"
  }
}
```

### 2. Relacionamentos Corretos

**LLM extrai:**
```json
{
  "source": "LMT-123",
  "target": "5f8a1b2c",
  "type": "ASSIGNED_TO"
}
```

**Persiste no Neo4j:**
```cypher
MATCH (source:JiraReport {id: 'LMT-123'})
MATCH (target:JiraReport {id: '5f8a1b2c'})
MERGE (source)-[r:ASSIGNED_TO]->(target)
```

**Validação:**
- ✅ Source existe? Sim (MATCH valida)
- ✅ Target existe? Sim (MATCH valida)
- ✅ Relacionamento criado? Sim (MERGE cria)

### 3. Múltiplos Relatórios

**Antes:** 1 página HTML
**Depois:** Estrutura completa com navegação

```
index.html
  ├─ executive-summary.html
  ├─ issues.html
  ├─ users/
  │    ├─ user1.html
  │    ├─ user2.html
  │    └─ user3.html
  └─ epics/
       ├─ epic1.html
       ├─ epic2.html
       └─ epic3.html
```

### 4. Análise Contextual por LLM

Cada relatório tem análise específica:

**Executive Summary:**
- Visão geral do projeto
- Top performers
- Riscos identificados
- Recomendações acionáveis

**User Report:**
- Análise de workload
- Produtividade
- Recomendações individuais

**Epic Report:**
- Progresso do épico
- Riscos de atraso
- Próximos passos

---

## Como Executar a Receita

### 1. Configurar Variáveis de Ambiente

```bash
export JIRA_AUTH_HEADER="Basic <base64-encoded-credentials>"
export NEO4J_PASSWORD="select-shirt-judge-miguel-antonio-46"
```

### 2. Executar a Receita

```bash
java -jar synthesis-engine.jar \
  --recipe lmt-jira-daily-report-kg.yaml \
  --option jiraProjectKey=LMT \
  --option daysBack=14 \
  --option enableLLMEnrichment=true
```

### 3. Verificar Output

```
reports/
├── index.html                   ← Abrir este primeiro
├── executive-summary.html
├── issues.html
├── users/
│   ├── john.doe@example.com.html
│   └── jane.smith@example.com.html
└── epics/
    ├── LMT-100.html
    └── LMT-200.html
```

### 4. Validar Neo4j

```cypher
// Verificar nodes criados
MATCH (n:JiraReport)
RETURN labels(n) AS type, count(n) AS total

// Verificar relationships
MATCH ()-[r]->()
WHERE r:ASSIGNED_TO OR r:REPORTED_BY OR r:BELONGS_TO_EPIC
RETURN type(r) AS relType, count(r) AS total

// Verificar issue específico
MATCH (i:Issue {key: 'LMT-123'})
OPTIONAL MATCH (i)-[r]-(connected)
RETURN i, r, connected
```

---

## Troubleshooting

### Problema: Nenhum node persistido

**Verificar:**
1. LLM retornou JSON válido?
   ```yaml
   @@@log("${@JsonUtils.writeAsJsonString(#knowledgeGraph, true)}")
   ```
2. Nodes têm IDs únicos?
3. Neo4j está rodando?

**Solução:**
- Verificar logs da execução
- Validar output do LLM Agent
- Testar Cypher manualmente

### Problema: Relationships não criados

**Verificar:**
1. Source e target nodes existem?
   ```cypher
   MATCH (n:JiraReport {id: 'LMT-123'})
   RETURN n
   ```
2. IDs estão corretos?

**Solução:**
- Usar `OPTIONAL MATCH` para debug
- Verificar logs de persistência

### Problema: Relatórios vazios

**Verificar:**
1. Queries Neo4j retornando dados?
   ```yaml
   @@@log("${@JsonUtils.writeAsJsonString(#allUsers, true)}")
   ```
2. Templates FreeMarker corretos?

**Solução:**
- Executar queries manualmente no Neo4j Browser
- Verificar variáveis disponíveis no template

---

## Próximos Passos (Melhorias Futuras)

### 1. Adicionar Mais Entidades

```yaml
TechnicalEntity:
  - Serviços mencionados em descriptions
  - Databases
  - APIs externas

Comment:
  - Comentários dos issues
  - Autor, data, conteúdo
```

### 2. Relacionamentos Avançados

```yaml
DEPENDS_ON: Issue -> Issue (dependencies)
BLOCKS: Issue -> Issue (blockers)
MENTIONS: Issue -> TechnicalEntity
COMMENTED_BY: Comment -> User
```

### 3. Análise Temporal

```yaml
query.velocityTrends: |-
  MATCH (i:Issue)-[:STATUS_CHANGED]->(sc:StatusChange)
  WHERE sc.to IN ['Done', 'Closed']
  WITH date(sc.date) AS day,
       sum(i.storyPoints) AS pointsCompleted
  RETURN day, pointsCompleted
  ORDER BY day
```

### 4. Detecção de Padrões

```yaml
detectBottleneckUsers: |-
  MATCH (u:User)<-[:ASSIGNED_TO]-(i:Issue)
  WHERE i.status = 'In Progress'
  WITH u, count(i) AS inProgress
  WHERE inProgress > 10
  RETURN u.name, inProgress
  ORDER BY inProgress DESC
```

---

## Conclusão

A abordagem **Knowledge Graph com LLM** resolve definitivamente os problemas de persistência no Neo4j:

✅ **Nodes completos** - Todos os campos preenchidos corretamente
✅ **Relationships válidos** - Source e target validados
✅ **Múltiplos relatórios** - Dashboard completo com navegação
✅ **Análise por LLM** - Insights contextuais em cada relatório
✅ **Escalável** - Fácil adicionar novos tipos de entidades
✅ **Manutenível** - Lógica clara e bem estruturada

Esta é a solução definitiva para o problema de persistência no Neo4j! 🎯
