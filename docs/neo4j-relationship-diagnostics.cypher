// ============================================
// Neo4j Relationship Diagnostics
// Para lmt-jira-report3.yaml
// ============================================

// ===========================================
// 1. DIAGNÓSTICO GERAL
// ===========================================

// 1.1 Contar nodes por tipo
CALL {
  MATCH (n:User:JiraReport) RETURN 'User' AS type, count(n) AS count
  UNION
  MATCH (n:Issue:JiraReport) RETURN 'Issue' AS type, count(n) AS count
  UNION
  MATCH (n:Epic:JiraReport) RETURN 'Epic' AS type, count(n) AS count
  UNION
  MATCH (n:StatusChange:JiraReport) RETURN 'StatusChange' AS type, count(n) AS count
}
RETURN type, count
ORDER BY count DESC;

// 1.2 Contar relacionamentos por tipo
MATCH ()-[r]->()
WHERE any(label IN labels(startNode(r)) WHERE label = 'JiraReport')
RETURN type(r) AS relationshipType, count(*) AS count
ORDER BY count DESC;

// 1.3 Verificar se há relacionamentos (resposta rápida)
MATCH ()-[r]->()
WHERE any(label IN labels(startNode(r)) WHERE label = 'JiraReport')
RETURN count(r) AS totalRelationships;

// ===========================================
// 2. DIAGNÓSTICO DE ISSUES
// ===========================================

// 2.1 Sample de Issues com suas propriedades
MATCH (i:Issue:JiraReport)
RETURN i.key, i.summary, i.assigneeId, i.reporterId, i.status
LIMIT 5;

// 2.2 Issues COM assigneeId mas SEM relacionamento ASSIGNED_TO
MATCH (i:Issue:JiraReport)
WHERE i.assigneeId IS NOT NULL
AND i.assigneeId <> ''
AND NOT EXISTS((i)-[:ASSIGNED_TO]->())
RETURN i.key, i.summary, i.assigneeId
LIMIT 10;

// 2.3 Issues COM reporterId mas SEM relacionamento REPORTED_BY
MATCH (i:Issue:JiraReport)
WHERE i.reporterId IS NOT NULL
AND i.reporterId <> ''
AND NOT EXISTS((i)-[:REPORTED_BY]->())
RETURN i.key, i.summary, i.reporterId
LIMIT 10;

// 2.4 Verificar se users com esses IDs existem
MATCH (i:Issue:JiraReport)
WHERE i.assigneeId IS NOT NULL AND i.assigneeId <> ''
WITH i.assigneeId AS userId
MATCH (u:User:JiraReport {key: userId})
RETURN userId, u.name, u.email
LIMIT 10;

// 2.5 Issues órfãos (sem nenhum relacionamento)
MATCH (i:Issue:JiraReport)
WHERE NOT EXISTS((i)-[]-())
RETURN i.key, i.summary, i.status, i.assigneeId, i.reporterId
LIMIT 10;

// ===========================================
// 3. VERIFICAÇÃO DE RELACIONAMENTOS EXISTENTES
// ===========================================

// 3.1 Verificar ASSIGNED_TO
MATCH (i:Issue:JiraReport)-[r:ASSIGNED_TO]->(u:User:JiraReport)
RETURN i.key, i.summary, u.key AS userKey, u.name, u.email
LIMIT 10;

// 3.2 Verificar REPORTED_BY
MATCH (i:Issue:JiraReport)-[r:REPORTED_BY]->(u:User:JiraReport)
RETURN i.key, i.summary, u.key AS userKey, u.name, u.email
LIMIT 10;

// 3.3 Verificar BELONGS_TO_EPIC
MATCH (i:Issue:JiraReport)-[r:BELONGS_TO_EPIC]->(e:Epic:JiraReport)
RETURN i.key, i.summary, e.key AS epicKey, e.name AS epicName
LIMIT 10;

// 3.4 Verificar STATUS_CHANGED
MATCH (sc:StatusChange:JiraReport)-[r:STATUS_CHANGED]->(i:Issue:JiraReport)
RETURN sc.key, sc.from, sc.to, sc.date, i.key AS issueKey
LIMIT 10;

// ===========================================
// 4. ANÁLISE DE PROBLEMAS COMUNS
// ===========================================

// 4.1 Keys incompatíveis (assigneeId não corresponde a nenhum User.key)
MATCH (i:Issue:JiraReport)
WHERE i.assigneeId IS NOT NULL AND i.assigneeId <> ''
WITH i.assigneeId AS userId, collect(i.key) AS issues
OPTIONAL MATCH (u:User:JiraReport {key: userId})
WHERE u IS NULL
RETURN userId, size(issues) AS affectedIssues, issues[0..3] AS sampleIssues
LIMIT 10;

// 4.2 Verificar formato dos keys
CALL {
  MATCH (u:User:JiraReport) RETURN 'User' AS type, u.key AS key LIMIT 5
  UNION
  MATCH (i:Issue:JiraReport) RETURN 'Issue' AS type, i.key AS key LIMIT 5
  UNION
  MATCH (e:Epic:JiraReport) RETURN 'Epic' AS type, e.key AS key LIMIT 5
}
RETURN type, key
ORDER BY type;

// ===========================================
// 5. CRIAR RELACIONAMENTOS MANUALMENTE (SE FALTAREM)
// ===========================================

// 5.1 Criar ASSIGNED_TO se faltar
MATCH (i:Issue:JiraReport)
WHERE i.assigneeId IS NOT NULL
AND i.assigneeId <> ''
AND NOT EXISTS((i)-[:ASSIGNED_TO]->())
MATCH (u:User:JiraReport {key: i.assigneeId})
MERGE (i)-[r:ASSIGNED_TO]->(u)
RETURN count(r) AS relationshipsCreated;

// 5.2 Criar REPORTED_BY se faltar
MATCH (i:Issue:JiraReport)
WHERE i.reporterId IS NOT NULL
AND i.reporterId <> ''
AND NOT EXISTS((i)-[:REPORTED_BY]->())
MATCH (u:User:JiraReport {key: i.reporterId})
MERGE (i)-[r:REPORTED_BY]->(u)
RETURN count(r) AS relationshipsCreated;

// 5.3 Criar BELONGS_TO_EPIC se faltar (assumindo que parent está na Issue)
// NOTA: Esta query só funciona se você tiver o campo parentKey na Issue
MATCH (i:Issue:JiraReport)
WHERE i.parentKey IS NOT NULL
AND i.parentKey <> ''
AND NOT EXISTS((i)-[:BELONGS_TO_EPIC]->())
MATCH (e:Epic:JiraReport {key: i.parentKey})
MERGE (i)-[r:BELONGS_TO_EPIC]->(e)
RETURN count(r) AS relationshipsCreated;

// ===========================================
// 6. NOVOS RELACIONAMENTOS DERIVADOS
// ===========================================

// 6.1 WORKS_ON_SAME_EPIC (colaboração entre users)
MATCH (u1:User:JiraReport)<-[:ASSIGNED_TO]-(i1:Issue:JiraReport)-[:BELONGS_TO_EPIC]->(e:Epic:JiraReport)
      <-[:BELONGS_TO_EPIC]-(i2:Issue:JiraReport)-[:ASSIGNED_TO]->(u2:User:JiraReport)
WHERE id(u1) < id(u2)
WITH u1, u2, e, count(DISTINCT i1) + count(DISTINCT i2) AS sharedIssues
WHERE sharedIssues > 1
MERGE (u1)-[r:WORKS_ON_SAME_EPIC]->(u2)
SET r.epic = e.key,
    r.epicName = e.name,
    r.sharedIssues = sharedIssues,
    r.strength = toFloat(sharedIssues) / 10.0,
    r.lastUpdate = datetime()
RETURN count(r) AS collaborationsCreated;

// 6.2 CONTRIBUTES_TO (user contribui para epic)
MATCH (u:User:JiraReport)<-[:ASSIGNED_TO]-(i:Issue:JiraReport)-[:BELONGS_TO_EPIC]->(e:Epic:JiraReport)
WITH u, e,
     count(i) AS issuesCount,
     sum(i.storyPoints) AS totalStoryPoints,
     collect(DISTINCT i.status) AS statuses
MERGE (u)-[r:CONTRIBUTES_TO]->(e)
SET r.issuesCount = issuesCount,
    r.totalStoryPoints = totalStoryPoints,
    r.activeIssues = size([s IN statuses WHERE s IN ['In Progress', 'To Do']]),
    r.lastUpdate = datetime()
RETURN count(r) AS contributionsCreated;

// 6.3 SIMILAR_WORK (issues com área técnica similar)
MATCH (i1:Issue:JiraReport), (i2:Issue:JiraReport)
WHERE i1.llmTechnicalArea = i2.llmTechnicalArea
AND i1.llmTechnicalArea IS NOT NULL
AND i1.llmTechnicalArea <> ''
AND id(i1) < id(i2)
MERGE (i1)-[r:SIMILAR_WORK]->(i2)
SET r.area = i1.llmTechnicalArea,
    r.lastUpdate = datetime()
RETURN count(r) AS similarWorkCreated;

// 6.4 SAME_COMPLEXITY (issues com complexidade similar)
MATCH (i1:Issue:JiraReport), (i2:Issue:JiraReport)
WHERE i1.llmComplexity = i2.llmComplexity
AND i1.llmComplexity IS NOT NULL
AND i1.llmComplexity <> ''
AND id(i1) < id(i2)
MERGE (i1)-[r:SAME_COMPLEXITY]->(i2)
SET r.complexity = i1.llmComplexity,
    r.lastUpdate = datetime()
RETURN count(r) AS sameComplexityCreated;

// 6.5 HIGH_PRIORITY_FOR (issues de alta prioridade atribuídas a user)
MATCH (i:Issue:JiraReport)-[:ASSIGNED_TO]->(u:User:JiraReport)
WHERE i.priority IN ['Highest', 'High']
MERGE (u)-[r:WORKS_ON_HIGH_PRIORITY]->(i)
SET r.priority = i.priority,
    r.lastUpdate = datetime()
RETURN count(r) AS highPriorityCreated;

// ===========================================
// 7. VISUALIZAÇÕES ÚTEIS
// ===========================================

// 7.1 Rede completa de uma Issue específica
MATCH path = (i:Issue:JiraReport {key: 'LMT-767'})-[*1..2]-()
RETURN path
LIMIT 50;

// 7.2 Todas as Issues atribuídas a um User
MATCH (u:User:JiraReport {name: 'Maikon Lenz'})
OPTIONAL MATCH (u)<-[:ASSIGNED_TO]-(i:Issue:JiraReport)
OPTIONAL MATCH (i)-[:BELONGS_TO_EPIC]->(e:Epic:JiraReport)
RETURN u, i, e;

// 7.3 Estrutura completa de um Epic
MATCH (e:Epic:JiraReport {key: 'LMT-722'})
OPTIONAL MATCH (e)<-[:BELONGS_TO_EPIC]-(i:Issue:JiraReport)
OPTIONAL MATCH (i)-[:ASSIGNED_TO]->(u:User:JiraReport)
RETURN e, i, u;

// 7.4 Colaboração entre dois users
MATCH (u1:User:JiraReport {name: 'Maikon Lenz'})<-[:ASSIGNED_TO]-(i1:Issue:JiraReport)
      -[:BELONGS_TO_EPIC]->(e:Epic:JiraReport)<-[:BELONGS_TO_EPIC]-(i2:Issue:JiraReport)
      -[:ASSIGNED_TO]->(u2:User:JiraReport {name: 'Heloisa Rocha'})
RETURN u1, i1, e, i2, u2;

// 7.5 Heatmap de atividade por Epic
MATCH (e:Epic:JiraReport)<-[:BELONGS_TO_EPIC]-(i:Issue:JiraReport)
WITH e,
     count(i) AS totalIssues,
     size([i IN collect(i) WHERE i.status = 'Done']) AS doneIssues,
     size([i IN collect(i) WHERE i.status IN ['In Progress', 'Testing']]) AS activeIssues
RETURN e.key, e.name, totalIssues, doneIssues, activeIssues,
       toFloat(doneIssues) / totalIssues AS completionRate
ORDER BY totalIssues DESC;

// ===========================================
// 8. ANÁLISES AVANÇADAS
// ===========================================

// 8.1 Top contributors por Epic
MATCH (u:User:JiraReport)-[r:CONTRIBUTES_TO]->(e:Epic:JiraReport)
RETURN e.name AS epic,
       collect({name: u.name, issues: r.issuesCount, points: r.totalStoryPoints}) AS contributors
ORDER BY e.name;

// 8.2 Users mais colaborativos
MATCH (u1:User:JiraReport)-[r:WORKS_ON_SAME_EPIC]->(u2:User:JiraReport)
WITH u1, sum(r.sharedIssues) AS totalCollaborations
RETURN u1.name, u1.email, totalCollaborations
ORDER BY totalCollaborations DESC
LIMIT 10;

// 8.3 Issues sem conexão (potenciais problemas)
MATCH (i:Issue:JiraReport)
WHERE NOT EXISTS((i)-[:ASSIGNED_TO]->())
AND NOT EXISTS((i)-[:REPORTED_BY]->())
AND NOT EXISTS((i)-[:BELONGS_TO_EPIC]->())
RETURN i.key, i.summary, i.status, i.priority
ORDER BY i.priority DESC
LIMIT 20;

// 8.4 Distribuição de trabalho por User
MATCH (u:User:JiraReport)<-[:ASSIGNED_TO]-(i:Issue:JiraReport)
WITH u,
     count(i) AS totalIssues,
     sum(i.storyPoints) AS totalPoints,
     size([i IN collect(i) WHERE i.status IN ['To Do', 'In Progress']]) AS activeIssues,
     size([i IN collect(i) WHERE i.status = 'Done']) AS completedIssues
RETURN u.name, u.email, totalIssues, totalPoints, activeIssues, completedIssues,
       toFloat(completedIssues) / totalIssues AS completionRate
ORDER BY totalIssues DESC;

// 8.5 Epics mais ativos
MATCH (e:Epic:JiraReport)<-[:BELONGS_TO_EPIC]-(i:Issue:JiraReport)
WITH e,
     count(DISTINCT i) AS totalIssues,
     count(DISTINCT CASE WHEN i.status IN ['In Progress', 'Testing'] THEN i END) AS activeIssues,
     collect(DISTINCT i.llmTechnicalArea) AS areas
WHERE totalIssues > 0
RETURN e.key, e.name, e.status, totalIssues, activeIssues, areas
ORDER BY activeIssues DESC, totalIssues DESC
LIMIT 10;

// ===========================================
// 9. LIMPEZA (USE COM CUIDADO!)
// ===========================================

// 9.1 Remover TODOS os relacionamentos derivados (não afeta dados originais)
// DESCOMENTE PARA EXECUTAR:
// MATCH ()-[r:WORKS_ON_SAME_EPIC|CONTRIBUTES_TO|SIMILAR_WORK|SAME_COMPLEXITY|WORKS_ON_HIGH_PRIORITY]->()
// DELETE r
// RETURN count(r) AS deletedRelationships;

// 9.2 Remover TODOS os nodes e relacionamentos JiraReport (CUIDADO!)
// DESCOMENTE PARA EXECUTAR:
// MATCH (n:JiraReport)
// DETACH DELETE n
// RETURN count(n) AS deletedNodes;

// ===========================================
// FIM
// ===========================================
