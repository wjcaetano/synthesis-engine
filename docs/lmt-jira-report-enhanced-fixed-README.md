# LMT Jira Report - Enhanced Fixed Version

## ✅ Problemas Corrigidos

### 1. **Keys Null no Neo4j**
**Problema**: Nós estavam sendo persistidos com `key: "null"` porque o `@@@objectify` não conseguia avaliar expressões SpEL corretamente.

**Solução**:
- Criamos uma **fase de pré-processamento** (Phase 3) que constrói objetos completos com todos os campos necessários ANTES do `@@@objectify`
- Os modelos agora usam variáveis específicas (`#userToSave`, `#issueToSave`, etc.) em vez de tentar acessar contextos complexos
- Todos os campos têm valores default usando o operador `?:` do SpEL

**Exemplo**:
```yaml
# ANTES (problemático)
JiraUser:
  "": "${#user}"  # #user pode estar incompleto
  key: "${#self['accountId']}"  # accountId pode ser null

# DEPOIS (corrigido)
JiraUser:
  "": "${#userToSave}"  # userToSave é pré-construído com todos os campos
  key: "${#self['accountId'] ?: 'unknown-user'}"  # Com fallback
```

### 2. **Relacionamentos Não Criados**
**Problema**: A lógica de relacionamentos usando FreeMarker inline dentro dos modelos não era executada corretamente durante o `@@@objectify`.

**Solução**:
- **Movemos a construção de relacionamentos para ANTES do objectify**
- Criamos templates de pré-processamento que constroem arrays de relacionamentos usando FreeMarker
- Os relacionamentos são adicionados como arrays simples nos objetos antes da persistência

**Exemplo de pré-processamento**:
```yaml
buildIssuesWithRelationships: |-
  @@@spel("${#enrichedIssues}")
  @@@freemarker
  @@@jsonify
  @@@set("issuesReadyForNeo4j")

  <#assign issuesReady = []>
  <#list enrichedIssues as issue>
    <#assign rels = []>

    <#-- Construir relacionamentos -->
    <#if issue.assignee?? && issue.assignee.accountId??>
      <#assign rels = rels + [{"label": "ASSIGNED_TO", "endKey": issue.assignee.accountId}]>
    </#if>

    <#-- Adicionar ao objeto final -->
    <#assign issueReady = {
      "issueKey": issue.issueKey,
      ...outros campos...,
      "relationships": rels
    }>

    <#assign issuesReady = issuesReady + [issueReady]>
  </#list>

  ${@JsonUtils.writeAsJsonString(issuesReady, true)}
```

### 3. **Relatório HTML Superficial**
**Problema**: O relatório gerado era uma única página HTML muito simples, sem detalhes por usuário, épico ou issue.

**Solução**:
- Criamos **múltiplos relatórios HTML** inspirados no `agentic-smart-recipe.yaml`
- Estrutura de arquivos:
  ```
  reports/
  ├── document.html           # Índice principal com dashboard
  ├── executive-summary.html  # Resumo executivo
  ├── users-report.html       # Relatório de usuários
  ├── epics-report.html       # Relatório de épicos
  ├── issues-report.html      # Relatório de issues
  └── entities-report.html    # Relatório de entidades técnicas
  ```

## 🏗️ Nova Arquitetura

### Pipeline Completo

```
Phase 1: Data Collection
├── Jira API → JOLT Normalization → enrichedIssues
└── Output: normalizedIssues, enrichedIssues

Phase 2: LLM Enrichment (opcional)
├── Cleaning, Classification, Entity Extraction
└── Output: enrichedIssues (com campos llm*)

Phase 3: Pre-processing for Neo4j ✨ (NOVO!)
├── buildUsersWithRelationships → usersReadyForNeo4j
├── buildEpicsWithRelationships → epicsReadyForNeo4j
├── buildStatusChangesWithRelationships → statusChangesReadyForNeo4j
├── buildIssuesWithRelationships → issuesReadyForNeo4j
└── buildEntitiesWithRelationships → entitiesReadyForNeo4j

Phase 4: Build Neo4j Graph
├── persistUsersFromPreprocessed
├── persistEpicsFromPreprocessed
├── persistStatusChangesFromPreprocessed
├── persistIssuesFromPreprocessed
└── persistEntitiesFromPreprocessed

Phase 5: Analytics
└── Query data from Neo4j → allData

Phase 6: Multiple HTML Reports ✨ (NOVO!)
├── document.html (índice)
├── executive-summary.html
├── users-report.html
├── epics-report.html
├── issues-report.html
└── entities-report.html
```

## 🔧 Como Usar

### 1. Configurar Credenciais

```bash
export JIRA_AUTH_HEADER="Basic $(echo -n 'email@example.com:api_token' | base64)"
```

### 2. Executar a Receita

```bash
# Opção 1: Via API
POST /api/recipes/lmt-jira-report-enhanced-fixed/execute
{
  "options": {
    "jiraProjectKey": "LMT",
    "daysBack": 14,
    "clearDatabase": false,
    "enableLLMEnrichment": false,  # Desligado por padrão para testes
    "enableAdvancedAnalysis": false
  }
}

# Opção 2: Via CLI (se disponível)
synthesis-engine run lmt-jira-report-enhanced-fixed --project=LMT --days=14
```

### 3. Ver os Relatórios

Os relatórios são gerados na pasta configurada no executor:

```
outputs/
└── reports/
    ├── document.html           ← Abrir este primeiro!
    ├── executive-summary.html
    ├── users-report.html
    ├── epics-report.html
    ├── issues-report.html
    └── entities-report.html
```

## 🔍 Validar Dados no Neo4j

### Queries de Verificação

```cypher
// 1. Contar nós por tipo
MATCH (n:JiraReport)
RETURN labels(n) as nodeType, count(n) as count
ORDER BY count DESC

// 2. Verificar que keys não são null
MATCH (n:JiraReport)
WHERE n.key IS NULL OR n.key = 'null'
RETURN labels(n) as nodeType, count(n) as nullKeys

// 3. Verificar relacionamentos
MATCH (i:Issue)-[r]->(target)
RETURN type(r) as relType, labels(target) as targetType, count(r) as count
ORDER BY count DESC

// 4. Ver exemplo de issue completo
MATCH (i:Issue)
RETURN i
LIMIT 1

// 5. Ver relacionamentos de uma issue
MATCH (i:Issue {key: 'LMT-123'})-[r]->(target)
RETURN i.key, type(r), labels(target), target.key

// 6. Usuários com suas issues
MATCH (u:User)<-[:ASSIGNED_TO]-(i:Issue)
RETURN u.name, count(i) as issueCount
ORDER BY issueCount DESC
```

### Resultados Esperados

Se tudo estiver funcionando corretamente, você deve ver:

```json
// Nó de usuário
{
  "labels": ["User", "JiraReport"],
  "properties": {
    "key": "5f8a1b2c3d4e5f6g7h8i9j0k",  // ✅ accountId real
    "name": "João Silva",
    "email": "joao.silva@example.com",
    "parentRelationship": "CONTAINS"
  }
}

// Nó de issue
{
  "labels": ["Issue", "JiraReport"],
  "properties": {
    "key": "LMT-123",  // ✅ issueKey real
    "summary": "Fix authentication bug",
    "status": "In Progress",
    "priority": "High",
    ...outros campos...
  }
}

// Relacionamentos
(Issue)-[:ASSIGNED_TO]->(User)
(Issue)-[:BELONGS_TO_EPIC]->(Epic)
(Issue)-[:CHILD_OF]->(Issue)
(StatusChange)-[:CHANGED]->(Issue)
```

## 🎯 Próximos Passos

### 1. Habilitar LLM Enrichment

Edite a configuração:
```yaml
options:
  - name: enableLLMEnrichment
    defaultValue: true  # Ativar
```

Isso adicionará:
- Limpeza semântica de descrições
- Classificação automática (área técnica, complexidade, impacto)
- Extração de entidades técnicas (services, databases, APIs)

### 2. Adicionar LLM para Relacionamentos

Podemos criar um novo agente que:
1. Analisa os dados coletados
2. Sugere relacionamentos adicionais baseados em similaridade semântica
3. Enriquece metadados dos relacionamentos

Exemplo:
```yaml
- name: RELATIONSHIP_ENRICHER
  provider: azure
  model: gpt-4o

enrichRelationshipsWithLLM: |-
  @@@agent("RELATIONSHIP_ENRICHER")
  @@@extractMarkdownCode
  @@@objectify

  Analyze these issues and suggest additional relationships:

  [ISSUES]
  ${@JsonUtils.writeAsJsonString(#issuesReadyForNeo4j, true)}

  [TASK]
  Find semantic connections:
  1. Similar technical areas
  2. Shared error patterns
  3. Dependent functionality
  4. Common technical entities

  Return JSON with suggested relationships and confidence scores.
```

### 3. Melhorar Relatórios

- Adicionar gráficos interativos (Chart.js)
- Incluir análise de tendências
- Mostrar network graphs de relacionamentos
- Adicionar filtros e busca

### 4. Adicionar Cache Inteligente

```yaml
caches:
  transforms:
    - prompt      # ✅ Já habilitado
    - neo4j       # ✅ Já habilitado
    - jolt        # ✅ Já habilitado
    - api         # Adicionar para Jira API
```

## 📊 Comparação: Antes vs Depois

| Aspecto | ANTES (Enhanced) | DEPOIS (Enhanced Fixed) |
|---------|------------------|-------------------------|
| Keys no Neo4j | ❌ `key: "null"` | ✅ `key: "LMT-123"` |
| Relacionamentos | ❌ Não criados | ✅ Todos criados |
| Relatórios | ❌ 1 página simples | ✅ 6 páginas detalhadas |
| Pré-processamento | ❌ Inexistente | ✅ Fase dedicada |
| Modelos | ❌ FreeMarker inline complexo | ✅ Simples com variáveis pré-construídas |
| Debug | ❌ Difícil rastrear erros | ✅ Logs em cada etapa |
| Manutenibilidade | ❌ Lógica espalhada | ✅ Separação clara de concerns |

## 🐛 Troubleshooting

### Problema: Keys ainda estão null

**Diagnóstico**:
```yaml
# Adicionar log no saveUser
saveUser: |-
  @@@log("${'DEBUG userToSave: ' + @JsonUtils.writeAsJsonString(#userToSave, true)}")
  @@@objectify("${#recipe['models']['JiraUser']}")
  ...
```

**Possíveis causas**:
1. `usersReadyForNeo4j` está vazio ou mal formado
2. O repeat não está definindo `userToSave` corretamente
3. O modelo tem erro de sintaxe SpEL

### Problema: Relacionamentos não aparecem

**Diagnóstico**:
```cypher
// Ver se os nós de destino existem
MATCH (i:Issue {key: 'LMT-123'})
MATCH (u:User {key: 'accountId123'})
RETURN i, u
```

Se os nós existem mas sem relacionamento:
- Verificar se o array `relationships` tem o formato correto
- Verificar se `endKey` corresponde ao `key` do nó de destino

### Problema: Relatórios não são gerados

**Verificar**:
1. Permissões de escrita na pasta de output
2. Sintaxe FreeMarker nos templates de HTML
3. Variável `allData` está disponível no contexto

## 📚 Referências

- [Receita Original](../src/main/resources/recipes/lmt-jira-report.yaml)
- [Receita Enhanced (com problema)](../src/main/resources/recipes/lmt-jira-report-enhanced.yaml)
- [Receita Fixed (esta)](../src/main/resources/recipes/lmt-jira-report-enhanced-fixed.yaml)
- [Agentic Smart Recipe](../src/main/resources/recipes/agentic-smart-recipe.yaml) - Inspiração para múltiplos relatórios
- [Documentação Neo4j](https://neo4j.com/docs/)
- [Documentação FreeMarker](https://freemarker.apache.org/docs/)
- [Documentação SpEL](https://docs.spring.io/spring-framework/reference/core/expressions.html)
