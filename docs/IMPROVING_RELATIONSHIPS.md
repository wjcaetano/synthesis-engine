# Melhorando Relacionamentos no Neo4j - Jira Knowledge Graph

## Status Atual dos Relacionamentos

A receita `lmt-jira-report3.yaml` atualmente cria estes relacionamentos:

```cypher
Issue -[ASSIGNED_TO]-> User
Issue -[REPORTED_BY]-> User
Issue -[BELONGS_TO_EPIC]-> Epic
StatusChange -[STATUS_CHANGED]-> Issue
```

## Verificação dos Relacionamentos Existentes

### Queries para Diagnóstico

Execute estas queries no Neo4j para verificar o estado atual:

```cypher
// 1. Contar todos os relacionamentos
MATCH ()-[r]->()
RETURN type(r) AS relationshipType, count(*) AS count
ORDER BY count DESC;

// 2. Verificar Issue -> User (ASSIGNED_TO)
MATCH (i:Issue)-[r:ASSIGNED_TO]->(u:User)
RETURN i.key, i.summary, u.name, u.email
LIMIT 10;

// 3. Verificar Issue -> User (REPORTED_BY)
MATCH (i:Issue)-[r:REPORTED_BY]->(u:User)
RETURN i.key, i.summary, u.name, u.email
LIMIT 10;

// 4. Verificar Issue -> Epic (BELONGS_TO_EPIC)
MATCH (i:Issue)-[r:BELONGS_TO_EPIC]->(e:Epic)
RETURN i.key, i.summary, e.key, e.name
LIMIT 10;

// 5. Verificar StatusChange -> Issue
MATCH (sc:StatusChange)-[r:STATUS_CHANGED]->(i:Issue)
RETURN sc.key, sc.from, sc.to, sc.date, i.key
LIMIT 10;

// 6. Issues órfãos (sem assignee)
MATCH (i:Issue)
WHERE i.assigneeId IS NOT NULL
AND NOT EXISTS((i)-[:ASSIGNED_TO]->())
RETURN i.key, i.summary, i.assigneeId
LIMIT 10;

// 7. Issues órfãos (sem epic)
MATCH (i:Issue)
WHERE NOT EXISTS((i)-[:BELONGS_TO_EPIC]->())
RETURN i.key, i.summary, i.status
LIMIT 10;
```

## Problema Comum: Relacionamentos Não Criados

Se os relacionamentos não existem, pode ser devido a:

### 1. Keys Incompatíveis

**Problema:** O `endKey` não corresponde ao `key` do node de destino.

**Exemplo:**
```yaml
Issue.assigneeId = "712020:b81f16d3-c6c9-42ea-9c99-91b78ccd4cbe"
User.key = "712020:b81f16d3-c6c9-42ea-9c99-91b78ccd4cbe"  # ✅ Match!
```

**Verificação:**
```cypher
// Encontrar issues com assigneeId mas sem relacionamento
MATCH (i:Issue)
WHERE i.assigneeId IS NOT NULL
AND i.assigneeId <> ''
WITH i
OPTIONAL MATCH (i)-[r:ASSIGNED_TO]->(u:User)
WHERE r IS NULL
RETURN i.key, i.assigneeId,
       EXISTS((u:User {key: i.assigneeId})) AS userExists
LIMIT 10;
```

### 2. Valores Vazios ou Nulos

**Problema:** O template verifica `?? &&` mas o valor pode ser string vazia.

**Solução:** Adicionar verificação extra.

## Melhorias Propostas

### 1. Novos Relacionamentos Básicos

Adicione estes relacionamentos ao template `mergeAllEntities`:

```yaml
relationships: [
  # ... relacionamentos existentes ...

  # Epic -> User (OWNED_BY) - Quem é responsável pelo Epic
  <#list (normalizedEpics![]) as epic>
    <#if epic.owner?? && epic.owner.accountId??>
  {
    "startKey": "${epic.epicKey}",
    "endKey": "${epic.owner.accountId}",
    "label": "OWNED_BY"
  },
    </#if>
  </#list>

  # Issue -> Issue (BLOCKS) - Issue bloqueia outra
  <#list (classifiedIssues![]) as issue>
    <#if issue.linkedIssues??>
      <#list issue.linkedIssues as link>
        <#if link.type == "Blocks">
  {
    "startKey": "${issue.issueKey}",
    "endKey": "${link.issueKey}",
    "label": "BLOCKS"
  },
        </#if>
      </#list>
    </#if>
  </#list>

  # Issue -> Issue (DEPENDS_ON) - Issue depende de outra
  <#list (classifiedIssues![]) as issue>
    <#if issue.linkedIssues??>
      <#list issue.linkedIssues as link>
        <#if link.type == "Depends">
  {
    "startKey": "${issue.issueKey}",
    "endKey": "${link.issueKey}",
    "label": "DEPENDS_ON"
  },
        </#if>
      </#list>
    </#if>
  </#list>

  # Issue -> Issue (RELATES_TO) - Issues relacionadas
  <#list (classifiedIssues![]) as issue>
    <#if issue.linkedIssues??>
      <#list issue.linkedIssues as link>
        <#if link.type == "Relates">
  {
    "startKey": "${issue.issueKey}",
    "endKey": "${link.issueKey}",
    "label": "RELATES_TO"
  },
        </#if>
      </#list>
    </#if>
  </#list>

  # User -> User (COLLABORATES_WITH) - Baseado em trabalho conjunto
  # Este seria gerado por análise de colaboração
  <#list (collaborationData![]) as collab>
  {
    "startKey": "${collab.user1}",
    "endKey": "${collab.user2}",
    "label": "COLLABORATES_WITH",
    "properties": {
      "strength": ${collab.strength?c},
      "sharedIssues": ${collab.sharedIssues?c}
    }
  },
  </#list>
]
```

### 2. Relacionamentos com Propriedades

Adicione propriedades aos relacionamentos para torná-los mais informativos:

```yaml
# Issue -> User (ASSIGNED_TO) com timestamp
{
  "startKey": "${issue.issueKey}",
  "endKey": "${issue.assignee.accountId}",
  "label": "ASSIGNED_TO",
  "properties": {
    "assignedDate": "${issue.assignedDate!''}",
    "workload": ${issue.storyPoints?c}
  }
}

# Issue -> Epic (BELONGS_TO_EPIC) com % de conclusão
{
  "startKey": "${issue.issueKey}",
  "endKey": "${issue.parent.key}",
  "label": "BELONGS_TO_EPIC",
  "properties": {
    "progress": "${issue.progress!0}",
    "priority": "${issue.priority}"
  }
}
```

### 3. Relacionamentos Derivados (Cypher)

Crie relacionamentos adicionais usando queries Cypher após a carga inicial:

```cypher
// 1. WORKS_ON_SAME_EPIC - Users que trabalham no mesmo Epic
MATCH (u1:User)<-[:ASSIGNED_TO]-(i1:Issue)-[:BELONGS_TO_EPIC]->(e:Epic)
      <-[:BELONGS_TO_EPIC]-(i2:Issue)-[:ASSIGNED_TO]->(u2:User)
WHERE id(u1) < id(u2)  // Evita duplicatas
WITH u1, u2, e, count(DISTINCT i1) + count(DISTINCT i2) AS sharedIssues
WHERE sharedIssues > 1
MERGE (u1)-[r:WORKS_ON_SAME_EPIC]->(u2)
SET r.epic = e.key,
    r.sharedIssues = sharedIssues,
    r.strength = sharedIssues * 1.0 / 10  // Normalizado
RETURN count(*) AS collaborationsCreated;

// 2. CONTRIBUTES_TO - Users contribuem para Epics
MATCH (u:User)<-[:ASSIGNED_TO]-(i:Issue)-[:BELONGS_TO_EPIC]->(e:Epic)
WITH u, e,
     count(i) AS issuesCount,
     sum(i.storyPoints) AS totalStoryPoints,
     collect(DISTINCT i.status) AS statuses
MERGE (u)-[r:CONTRIBUTES_TO]->(e)
SET r.issuesCount = issuesCount,
    r.totalStoryPoints = totalStoryPoints,
    r.statuses = statuses,
    r.lastUpdate = datetime()
RETURN count(*) AS contributionsCreated;

// 3. FREQUENTLY_ASSIGNS_TO - Reporter frequentemente atribui para User
MATCH (reporter:User)<-[:REPORTED_BY]-(i:Issue)-[:ASSIGNED_TO]->(assignee:User)
WITH reporter, assignee, count(i) AS assignmentCount
WHERE assignmentCount > 3
MERGE (reporter)-[r:FREQUENTLY_ASSIGNS_TO]->(assignee)
SET r.count = assignmentCount
RETURN count(*) AS frequentAssignmentsCreated;

// 4. SIMILAR_WORK - Issues com área técnica similar
MATCH (i1:Issue), (i2:Issue)
WHERE i1.llmTechnicalArea = i2.llmTechnicalArea
AND i1.llmTechnicalArea IS NOT NULL
AND id(i1) < id(i2)
MERGE (i1)-[r:SIMILAR_WORK]->(i2)
SET r.area = i1.llmTechnicalArea
RETURN count(*) AS similarWorkCreated;

// 5. HIGH_RISK_DEPENDENCY - Issues de alto risco que bloqueiam outras
MATCH (blocking:Issue)-[:BLOCKS]->(blocked:Issue)
WHERE blocking.llmRiskLevel IN ['High', 'Critical']
MERGE (blocking)-[r:HIGH_RISK_DEPENDENCY]->(blocked)
SET r.riskLevel = blocking.llmRiskLevel,
    r.impact = blocking.llmBusinessImpact
RETURN count(*) AS highRiskDepsCreated;
```

### 4. Relacionamentos Temporais

Crie relacionamentos baseados em sequência temporal:

```cypher
// FOLLOWS - Issue que veio depois (mesmo assignee, mesmo epic)
MATCH (e:Epic)<-[:BELONGS_TO_EPIC]-(i1:Issue)-[:ASSIGNED_TO]->(u:User)
      <-[:ASSIGNED_TO]-(i2:Issue)-[:BELONGS_TO_EPIC]->(e)
WHERE i1.createdDate < i2.createdDate
WITH i1, i2, e, u
ORDER BY i2.createdDate
WITH i1, collect(i2)[0] AS nextIssue
WHERE nextIssue IS NOT NULL
MERGE (i1)-[r:FOLLOWED_BY]->(nextIssue)
SET r.timeGap = duration.between(
  datetime(i1.createdDate),
  datetime(nextIssue.createdDate)
).days
RETURN count(*) AS followedByCreated;
```

## Implementação: Adicionar Template de Pós-Processamento

Adicione um novo template na receita para criar relacionamentos derivados:

```yaml
templates:
  # ... templates existentes ...

  createDerivedRelationships: |-
    @@@log("#00FF00[PHASE 5.3] Creating derived relationships...")
    @@@neo4j
    @@@jsonify
    // 1. WORKS_ON_SAME_EPIC
    MATCH (u1:User)<-[:ASSIGNED_TO]-(i1:Issue)-[:BELONGS_TO_EPIC]->(e:Epic)<-[:BELONGS_TO_EPIC]-(i2:Issue)-[:ASSIGNED_TO]->(u2:User)
    WHERE id(u1) < id(u2)
    WITH u1, u2, e, count(DISTINCT i1) + count(DISTINCT i2) AS sharedIssues
    WHERE sharedIssues > 1
    MERGE (u1)-[r:WORKS_ON_SAME_EPIC]->(u2)
    SET r.epic = e.key, r.sharedIssues = sharedIssues;

    // 2. CONTRIBUTES_TO
    MATCH (u:User)<-[:ASSIGNED_TO]-(i:Issue)-[:BELONGS_TO_EPIC]->(e:Epic)
    WITH u, e, count(i) AS issuesCount, sum(i.storyPoints) AS totalStoryPoints
    MERGE (u)-[r:CONTRIBUTES_TO]->(e)
    SET r.issuesCount = issuesCount, r.totalStoryPoints = totalStoryPoints;

    // 3. SIMILAR_WORK
    MATCH (i1:Issue), (i2:Issue)
    WHERE i1.llmTechnicalArea = i2.llmTechnicalArea
    AND i1.llmTechnicalArea IS NOT NULL
    AND id(i1) < id(i2)
    MERGE (i1)-[r:SIMILAR_WORK]->(i2)
    SET r.area = i1.llmTechnicalArea;
```

E adicione ao flow:

```yaml
flow:
  # ... fases existentes ...

  phase5_persistence:
    persistence/createIndexes.json: "${#recipe['templates']['createNeo4jIndexes']}"
    persistence/persistGraph.json: "${#recipe['templates']['persistGraphToNeo4j']}"
    persistence/derivedRelationships.json: "${#recipe['templates']['createDerivedRelationships']}"  # NOVO
```

## Visualização dos Relacionamentos

### Queries para Visualização

```cypher
// 1. Visão geral de uma Issue com todos os relacionamentos
MATCH path = (i:Issue {key: 'LMT-767'})-[*1..2]-()
RETURN path
LIMIT 50;

// 2. Rede de colaboração de um User
MATCH path = (u:User {key: '712020:b81f16d3-c6c9-42ea-9c99-91b78ccd4cbe'})-[:ASSIGNED_TO|REPORTED_BY|WORKS_ON_SAME_EPIC*1..2]-()
RETURN path
LIMIT 50;

// 3. Estrutura de um Epic com Issues e Users
MATCH (e:Epic {key: 'LMT-722'})
OPTIONAL MATCH (e)<-[:BELONGS_TO_EPIC]-(i:Issue)
OPTIONAL MATCH (i)-[:ASSIGNED_TO]->(u:User)
RETURN e, i, u;

// 4. Caminho crítico (Issues que bloqueiam outras)
MATCH path = (i1:Issue)-[:BLOCKS*1..3]->(i2:Issue)
WHERE i1.priority = 'Highest'
RETURN path
LIMIT 20;

// 5. Heatmap de colaboração
MATCH (u1:User)-[r:WORKS_ON_SAME_EPIC]->(u2:User)
RETURN u1.name, u2.name, r.sharedIssues
ORDER BY r.sharedIssues DESC
LIMIT 20;
```

## Modelo de Dados Completo

```
┌─────────┐
│  User   │
└────┬────┘
     │
     │ ASSIGNED_TO (n)
     │ REPORTED_BY (n)
     │ WORKS_ON_SAME_EPIC (n)
     │ FREQUENTLY_ASSIGNS_TO (n)
     │ CONTRIBUTES_TO (n)
     │
     ▼
┌─────────┐         ┌──────────────┐
│  Issue  ├────────►│    Epic      │
└────┬────┘         └──────────────┘
     │    BELONGS_TO_EPIC (1)
     │
     │ BLOCKS (n)
     │ DEPENDS_ON (n)
     │ RELATES_TO (n)
     │ SIMILAR_WORK (n)
     │ FOLLOWED_BY (1)
     │
     ▼
┌───────────────┐
│ StatusChange  │
└───────────────┘
     STATUS_CHANGED (n)
```

## Queries Analíticas Avançadas

```cypher
// 1. Usuários mais produtivos por Epic
MATCH (u:User)-[r:CONTRIBUTES_TO]->(e:Epic)
RETURN e.name AS epic,
       collect({user: u.name, issues: r.issuesCount}) AS contributors
ORDER BY e.name;

// 2. Gargalos (Issues bloqueando muitas outras)
MATCH (blocker:Issue)-[:BLOCKS]->(blocked:Issue)
WITH blocker, count(blocked) AS blockedCount
WHERE blockedCount > 2
RETURN blocker.key, blocker.summary, blockedCount, blocker.status
ORDER BY blockedCount DESC;

// 3. Rede de dependências
MATCH path = (i1:Issue)-[:DEPENDS_ON*1..3]->(i2:Issue)
WHERE i1.status <> 'Done'
AND i2.status = 'To Do'
RETURN path;

// 4. Colaboração entre áreas técnicas
MATCH (i1:Issue)-[:ASSIGNED_TO]->(u1:User)
MATCH (i2:Issue)-[:ASSIGNED_TO]->(u2:User)
WHERE i1.llmTechnicalArea <> i2.llmTechnicalArea
AND EXISTS((i1)-[:RELATES_TO]-(i2))
RETURN i1.llmTechnicalArea AS area1,
       i2.llmTechnicalArea AS area2,
       count(*) AS collaborations
ORDER BY collaborations DESC;

// 5. Risco propagado (Issues de alto risco que bloqueiam outras)
MATCH path = (risky:Issue)-[:BLOCKS*1..3]->(affected:Issue)
WHERE risky.llmRiskLevel IN ['High', 'Critical']
RETURN risky.key, risky.llmRiskLevel,
       count(DISTINCT affected) AS affectedIssues,
       collect(DISTINCT affected.key) AS affectedKeys
ORDER BY affectedIssues DESC;
```

## Próximos Passos

1. **Verificar relacionamentos existentes** com as queries de diagnóstico
2. **Adicionar relacionamentos básicos** que faltam
3. **Implementar relacionamentos derivados** com template de pós-processamento
4. **Criar dashboards** de visualização
5. **Adicionar queries analíticas** ao template de reports

## Ferramentas de Visualização

### Neo4j Browser
```
// Configuração de estilo visual
:style {
  node {
    diameter: 50px;
    color: #A5ABB6;
    border-color: #9AA1AC;
    border-width: 2px;
    text-color-internal: #FFFFFF;
    font-size: 10px;
  }
  relationship {
    color: #A5ABB6;
    shaft-width: 1px;
    font-size: 8px;
    padding: 3px;
    text-color-external: #000000;
    text-color-internal: #FFFFFF;
    caption: "<type>";
  }
  node.User {
    color: #4CAF50;
    border-color: #388E3C;
    text-color-internal: #FFFFFF;
    caption: "{name}";
  }
  node.Issue {
    color: #2196F3;
    border-color: #1976D2;
    caption: "{key}";
  }
  node.Epic {
    color: #FF9800;
    border-color: #F57C00;
    caption: "{name}";
  }
}
```

### Bloom (Neo4j Bloom)
- Crie "Perspectives" para diferentes visões
- Configure "Search Phrases" para buscas naturais
- Adicione "Business Rules" para destacar padrões

## Conclusão

Com estas melhorias, seu knowledge graph ficará:

✅ **Mais conectado** - Relacionamentos em múltiplas dimensões
✅ **Mais informativo** - Propriedades nos relacionamentos
✅ **Mais analítico** - Relacionamentos derivados revelam padrões
✅ **Mais visual** - Fácil de explorar e entender
✅ **Mais útil** - Queries para insights reais

O próximo passo é implementar gradualmente cada tipo de relacionamento e validar os resultados.
