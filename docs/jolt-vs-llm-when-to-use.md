# JOLT vs LLM: Quando Usar Cada Um

## TL;DR - Resposta Rápida

**❓ Preciso usar JOLT se estou usando LLM?**

**Resposta:** Sim, na maioria dos casos! JOLT e LLM são **complementares**, não substitutos.

```
┌─────────────────────────────────────────────────────────┐
│  Jira API Response (JSON complexo)                     │
│         ↓                                               │
│  ✅ JOLT: Normaliza estrutura (rápido, determinístico) │
│         ↓                                               │
│  JSON limpo e consistente                               │
│         ↓                                               │
│  ✅ LLM: Enriquece semanticamente (lento, inteligente) │
│         ↓                                               │
│  JSON enriquecido com classificações e insights         │
│         ↓                                               │
│  Neo4j                                                  │
└─────────────────────────────────────────────────────────┘
```

---

## Comparação Detalhada

| Aspecto | JOLT | LLM |
|---------|------|-----|
| **Velocidade** | ⚡ Instantâneo (<10ms) | 🐢 1-3 segundos |
| **Custo** | 💰 Grátis | 💰💰 ~$0.01 por 100 items |
| **Consistência** | ✅ 100% determinístico | ⚠️ 95-98% consistente |
| **Uso ideal** | Transformações estruturais | Análise semântica |
| **Complexidade** | Alta (spec complexa) | Baixa (prompt em linguagem natural) |
| **Manutenção** | 🔧 Difícil (specs crípticas) | ✅ Fácil (ajustar prompt) |
| **Capacidade** | Só reestrutura | Entende contexto |

---

## Quando Usar JOLT

### ✅ Caso 1: Normalização de Estrutura (SEMPRE)

**Problema:** Jira API retorna JSON profundamente aninhado e inconsistente

**Exemplo - Resposta do Jira:**
```json
{
  "issues": [
    {
      "id": "10001",
      "key": "LMT-123",
      "fields": {
        "summary": "Bug in login",
        "issuetype": {
          "name": "Bug",
          "id": "1"
        },
        "assignee": {
          "displayName": "John Doe",
          "emailAddress": "john@company.com",
          "accountId": "abc123"
        },
        "customfield_10014": "EPIC-1",  // Epic link
        "customfield_10016": 8,         // Story points
        "description": {
          "content": [
            {
              "content": [
                {"text": "Login fails when..."}
              ]
            }
          ]
        }
      }
    }
  ]
}
```

**❌ SEM JOLT - Enviar direto para LLM:**
```yaml
# Custo: $0.02 por issue (tokens desperdiçados com estrutura)
# Consistência: 85% (LLM pode interpretar campos errado)
# Velocidade: 2 segundos por issue
```

**✅ COM JOLT - Normalizar primeiro:**
```yaml
templates:
  normalizeJiraData: |-
    @@@jolt("${#recipe['jolts']['joltJiraToNormalized']}")
    @@@set("normalizedIssues")
```

**Resultado normalizado:**
```json
{
  "issueKey": "LMT-123",
  "issueId": "10001",
  "summary": "Bug in login",
  "issueType": "Bug",
  "assignee": {
    "name": "John Doe",
    "email": "john@company.com",
    "accountId": "abc123"
  },
  "epicKey": "EPIC-1",
  "storyPoints": 8,
  "description": "Login fails when..."
}
```

**Depois enviar para LLM:**
```yaml
# Custo: $0.005 por issue (60% menos tokens!)
# Consistência: 98% (estrutura já normalizada)
# Velocidade: 1 segundo por issue
```

**Conclusão:** JOLT remove ruído estrutural antes de enviar para LLM.

---

### ✅ Caso 2: Resultados de Neo4j (SEMPRE)

**Problema:** Neo4j retorna formato tabular específico

**Resposta Neo4j crua:**
```json
{
  "results": [
    {
      "columns": ["issueKey", "summary", "storyPoints"],
      "data": [
        {
          "row": ["LMT-123", "Bug in login", 8]
        },
        {
          "row": ["LMT-124", "Add feature", 13]
        }
      ]
    }
  ]
}
```

**✅ JOLT transforma para JSON limpo:**
```yaml
jolts:
  joltNeo4jTableToJson: |-
    [
      {
        "operation": "shift",
        "spec": {
          "results": {
            "*": {
              "data": {
                "*": {
                  "row": {
                    "*": "[&2].@(4,columns[&0])"
                  }
                }
              }
            }
          }
        }
      }
    ]
```

**Resultado:**
```json
[
  {
    "issueKey": "LMT-123",
    "summary": "Bug in login",
    "storyPoints": 8
  },
  {
    "issueKey": "LMT-124",
    "summary": "Add feature",
    "storyPoints": 13
  }
]
```

**Este JOLT é usado em TODA a receita atual:**
```yaml
# Linha 5-6 da receita:
transformDefaultParams:
  jolt:
    - "${#recipe['jolts']['joltNeo4jTableToJson']}"

# Usado em todas as queries:
query.dailyTimeline: |-
  @@@neo4j
  @@@jolt("${#recipe['jolts']['joltNeo4jTableToJson']}")  # ← SEMPRE
  MATCH (sc:StatusChange)...
```

**Conclusão:** Não tem como evitar JOLT para resultados Neo4j no padrão atual.

---

### ✅ Caso 3: Transformações Determinísticas

**Quando:** Você sabe exatamente a transformação e ela nunca muda

**Exemplo:** Extrair changelogs de status
```yaml
jolts:
  joltEnrichChangelog: |-
    [
      {
        "operation": "shift",
        "spec": {
          "*": {
            "changeHistory": {
              "*": {
                "changes": {
                  "*": {
                    "field": {
                      "status": {  # Só extrai mudanças de status
                        "@(3,date)": "[&6].dailyStatusChanges[&3].date",
                        "@(2,oldValue)": "[&6].dailyStatusChanges[&3].from",
                        "@(2,newValue)": "[&6].dailyStatusChanges[&3].to"
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    ]
```

**Por que JOLT aqui?**
- ⚡ Performance: 1000 issues em 50ms
- 💰 Custo: Zero
- ✅ Consistência: 100%
- 🎯 Propósito: Extração mecânica, sem semântica

**❌ LLM seria overkill:** "Extract status changes" → 2 segundos, $0.01, possível erro

---

## Quando Usar LLM

### ✅ Caso 1: Limpeza Semântica

**Problema:** Descrições do Jira têm ruído que JOLT não consegue remover

**Exemplo:**
```
Original description:
"Hi team,

{color:red}URGENT{color}

The login page is broken. When I try to login with my credentials,
it shows error {code}ERR-500{code}.

Stack trace:
at com.app.UserService.login(UserService.java:123)
at com.app.LoginController.handleLogin(LoginController.java:45)

Thanks,
John
--
John Doe | Senior Developer | Acme Corp"
```

**❌ JOLT não consegue:**
- Remover saudações e assinaturas
- Distinguir conteúdo técnico de social
- Extrair info relevante mantendo contexto

**✅ LLM consegue:**
```yaml
templates:
  cleanDescription: |-
    @@@agent("DATA_CLEANER")
    @@@set("cleanedText")

    Clean this Jira description, keeping only technical content:
    ${issue.description}

    Remove: greetings, signatures, markup
    Keep: error messages, stack traces, technical details
```

**Resultado:**
```
Login page error ERR-500 when attempting login.
Stack trace: UserService.login(UserService.java:123)
```

**Pipeline ideal:**
```yaml
# 1. JOLT normaliza estrutura
@@@jolt("${#recipe['jolts']['joltJiraToNormalized']}")

# 2. LLM limpa semântica
@@@agent("DATA_CLEANER")
```

---

### ✅ Caso 2: Classificação e Categorização

**Problema:** Classificar issues requer entendimento do conteúdo

**Exemplo - Issue:**
```json
{
  "summary": "Timeout on user authentication endpoint",
  "description": "API endpoint /api/v1/auth/login returns 504 after 30 seconds...",
  "issueType": "Bug"
}
```

**❌ JOLT não consegue:**
- Determinar que é "backend" (poderia ser frontend timeout)
- Avaliar complexidade (precisa entender arquitetura)
- Estimar business impact (requer contexto)

**✅ LLM consegue:**
```yaml
templates:
  classifyIssue: |-
    @@@agent("ISSUE_CLASSIFIER")
    @@@objectify
    @@@set("classification")

    Classify this issue:
    ${@JsonUtils.writeAsJsonString(#issue, true)}

    Return: {
      "technicalArea": "backend|frontend|database|...",
      "complexity": "low|medium|high",
      "businessImpact": "minor|moderate|significant|critical"
    }
```

**Resultado:**
```json
{
  "technicalArea": "backend",
  "complexity": "medium",
  "businessImpact": "significant",
  "reasoning": "Authentication is critical, timeout suggests performance issue"
}
```

---

### ✅ Caso 3: Extração de Entidades

**Problema:** Identificar serviços/APIs mencionados no texto

**Descrição:**
```
"The UserService is failing to connect to the payment_db database.
Error occurs when calling StripeAPI for payment processing.
Affects production environment only."
```

**❌ JOLT não consegue:**
- Regex básico capturaria "payment_db" mas não "UserService"
- Não distingue entre "database" (tipo) e "payment_db" (nome)
- Não entende contexto de "production environment"

**✅ LLM consegue:**
```yaml
templates:
  extractEntities: |-
    @@@agent("ENTITY_EXTRACTOR")
    @@@objectify
    @@@set("entities")

    Extract technical entities:
    ${issue.description}

    Return: {
      "services": ["UserService"],
      "databases": ["payment_db"],
      "externalSystems": ["StripeAPI"],
      "environments": ["production"]
    }
```

---

### ✅ Caso 4: Geração de Schemas Dinâmicos

**Problema:** Campos customizados do Jira variam por projeto

**Jira Project A:**
```json
{
  "customfield_10014": "EPIC-1",      // Epic Link
  "customfield_10016": 8,             // Story Points
  "customfield_10025": "Q4-2024"      // Target Release
}
```

**Jira Project B:**
```json
{
  "customfield_10014": "Team-Backend", // Team
  "customfield_10016": "High",         // Business Priority
  "customfield_10025": "John Doe"      // Technical Lead
}
```

**❌ JOLT requer spec manual por projeto:**
```yaml
jolts:
  projectA: "customfield_10014 → epicKey"
  projectB: "customfield_10014 → team"
```

**✅ LLM pode descobrir automaticamente:**
```yaml
templates:
  discoverSchema: |-
    @@@agent("SCHEMA_ARCHITECT")
    @@@objectify
    @@@set("schemaMapping")

    Analyze these Jira custom fields:
    ${@JsonUtils.writeAsJsonString(#sampleIssues, true)}

    Determine what each customfield represents.
    Return mapping.
```

**Resultado:**
```json
{
  "customfield_10014": {
    "fieldName": "epicLink",
    "dataType": "string",
    "neo4jRelationship": "BELONGS_TO_EPIC"
  },
  "customfield_10016": {
    "fieldName": "storyPoints",
    "dataType": "integer",
    "neo4jProperty": "storyPoints"
  }
}
```

---

### ✅ Caso 5: Análise de Queries Neo4j

**Problema:** Resultados de queries precisam ser interpretados

**Query Result:**
```json
[
  {
    "entityName": "UserService",
    "issueCount": 15,
    "criticalCount": 8
  },
  {
    "entityName": "payment_db",
    "issueCount": 12,
    "criticalCount": 3
  }
]
```

**❌ JOLT só pode reestruturar:**
```yaml
# Pode ordenar, filtrar, mas não interpretar
```

**✅ LLM pode analisar:**
```yaml
templates:
  analyzeEntityImpact: |-
    @@@agent("ANALYST")

    These entities appear in multiple issues:
    ${@JsonUtils.writeAsJsonString(#queryResult, true)}

    What does this indicate?
    - Is this a systemic problem?
    - Which entity should be addressed first?
    - What actions are recommended?
```

**Resultado:**
```
UserService is a critical bottleneck with 8 critical issues (53%).
Recommendation: Immediate code review and refactoring.
payment_db has fewer critical issues (25%) but still requires attention.
Consider database optimization as second priority.
```

---

## Padrões de Uso Recomendados

### 🏆 Padrão 1: Pipeline Híbrido (Recomendado)

```yaml
templates:
  hybridPipeline: |-
    # Etapa 1: JOLT normaliza estrutura
    @@@log("#00FF00Step 1: Structural normalization with JOLT...")
    @@@jolt("${#recipe['jolts']['joltJiraToNormalized']}")
    @@@set("normalizedIssues")

    # Etapa 2: LLM limpa semanticamente
    @@@log("#00FF00Step 2: Semantic cleaning with LLM...")
    @@@repeat("${#normalizedIssues}", "issue", "${#recipe['templates']['cleanDescription']}")
    @@@set("cleanedIssues")

    # Etapa 3: LLM classifica e enriquece
    @@@log("#00FF00Step 3: Classification and enrichment with LLM...")
    @@@repeat("${#cleanedIssues}", "issue", "${#recipe['templates']['classifyIssue']}")
    @@@set("enrichedIssues")

    # Etapa 4: LLM extrai entidades
    @@@log("#00FF00Step 4: Entity extraction with LLM...")
    @@@repeat("${#enrichedIssues}", "issue", "${#recipe['templates']['extractEntities']}")
    @@@set("finalIssues")

    @@@jsonify

# Fluxo:
# Jira API → JOLT (estrutura) → LLM (semântica) → LLM (classificação) → LLM (entidades) → Neo4j
```

**Vantagens:**
- ⚡ JOLT é rápido para parte mecânica
- 🧠 LLM foca em análise semântica
- 💰 Custos otimizados (JOLT reduz tokens)
- ✅ Melhor de ambos

---

### 🏆 Padrão 2: JOLT para Queries, LLM para Análise

```yaml
templates:
  queryAndAnalyze: |-
    # Query Neo4j (retorna formato tabular)
    @@@neo4j
    @@@jolt("${#recipe['jolts']['joltNeo4jTableToJson']}")  # ← JOLT SEMPRE
    @@@set("queryResult")

    MATCH (i:Issue)-[:MENTIONS]->(e:TechnicalEntity)
    RETURN e.name, count(i) AS issueCount
    ORDER BY issueCount DESC

    # LLM analisa resultados
    @@@agent("ANALYST")
    @@@set("analysis")

    These technical entities appear in many issues:
    ${@JsonUtils.writeAsJsonString(#queryResult, true)}

    What does this indicate? What actions should be taken?
```

**Fluxo:**
```
Neo4j → JOLT (tabular → JSON) → LLM (análise) → Insights
```

---

### 🏆 Padrão 3: LLM Gera, JOLT Valida

```yaml
templates:
  llmGenerateJoltValidate: |-
    # LLM gera schema
    @@@agent("SCHEMA_GEN")
    @@@objectify
    @@@set("generatedSchema")

    Analyze custom fields and generate schema...

    # JOLT valida estrutura
    @@@jolt
    @@@set("validatedSchema")
    [
      {
        "operation": "default",
        "spec": {
          "*": {
            "dataType": "string",
            "required": false
          }
        }
      }
    ]
```

---

## Decisão Rápida: Flowchart

```
┌─────────────────────────────────────────┐
│ Preciso transformar dados?              │
└───────────────┬─────────────────────────┘
                │
                ▼
       ┌─────────────────┐
       │ É transformação │
       │   estrutural?   │
       └────┬────────┬───┘
            │YES     │NO
            ▼        ▼
    ┌───────────┐  ┌──────────────┐
    │ Usa JOLT  │  │ Requer        │
    │           │  │ entendimento  │
    │ • Rápido  │  │ semântico?    │
    │ • Grátis  │  └──┬────────┬──┘
    │ • 100%    │     │YES     │NO
    └───────────┘     ▼        ▼
                ┌─────────┐  ┌─────────┐
                │ Usa LLM │  │ JOLT +  │
                │         │  │ Regex   │
                └─────────┘  └─────────┘
```

---

## Análise de Custos: JOLT vs LLM

### Cenário: 1000 Issues do Jira

#### Opção A: Só JOLT
```yaml
# Normalização estrutural
@@@jolt("${#recipe['jolts']['joltJiraToNormalized']}")

Custo: $0
Tempo: ~50ms
Resultado: JSON estruturado, sem enriquecimento semântico
```

#### Opção B: Só LLM
```yaml
# Enviar JSON cru do Jira para LLM
@@@repeat("${#rawIssues}", "issue", "...")

Custo: $20 (tokens desperdiçados com estrutura)
Tempo: ~2000 segundos (33 minutos)
Resultado: Enriquecido, mas caro e lento
```

#### Opção C: JOLT + LLM (Híbrido) ⭐
```yaml
# 1. JOLT normaliza (remove ruído)
@@@jolt("${#recipe['jolts']['joltJiraToNormalized']}")

# 2. LLM enriquece (só o necessário)
@@@repeat("${#normalizedIssues}", "issue", "...")

Custo: $8 (60% economia vs só LLM)
Tempo: ~1200 segundos (20 minutos)
Resultado: Rápido, barato, enriquecido ✅
```

---

## Casos Práticos da Receita Atual

### 1. Normalização Jira → Neo4j

**Atual (CORRETO):**
```yaml
templates:
  collectJiraIssues: |-
    @@@api("${jiraURL}", ...)           # Coleta
    @@@jolt("${#recipe['jolts']['joltJiraToNormalized']}")  # ← JOLT
    @@@set("normalizedIssues")
```

**✅ Manter JOLT porque:**
- Remove estrutura complexa do Jira
- Consistente 100%
- Instantâneo

**✨ Adicionar LLM depois:**
```yaml
templates:
  collectAndEnrich: |-
    # 1. JOLT normaliza
    @@@jolt("${#recipe['jolts']['joltJiraToNormalized']}")
    @@@set("normalizedIssues")

    # 2. LLM enriquece
    @@@repeat("${#normalizedIssues}", "issue", "${#recipe['templates']['enrichIssue']}")
    @@@set("enrichedIssues")

  enrichIssue: |-
    @@@agent("ENRICHER")
    @@@objectify
    @@@set("enrichment")
    @@@_spel("${#issue.putAll(#enrichment)}")

    Enrich: ${@JsonUtils.writeAsJsonString(#issue, true)}
    Return: {
      "llmCategory": "...",
      "llmComplexity": "...",
      "llmCleanDescription": "..."
    }
```

---

### 2. Resultados Neo4j → Análise

**Atual (CORRETO):**
```yaml
templates:
  query.dailyTimeline: |-
    @@@neo4j
    @@@jolt("${#recipe['jolts']['joltNeo4jTableToJson']}")  # ← JOLT
    @@@set("dailyTimeline")

    MATCH (sc:StatusChange)-[:CHANGED]->(i:Issue)
    RETURN ...
```

**✅ Manter JOLT porque:**
- Neo4j sempre retorna formato tabular
- JOLT converte para JSON consumível
- Usado em TODAS as queries da receita

**✨ Adicionar LLM depois para análise:**
```yaml
templates:
  analyzeTimeline: |-
    # 1. Query + JOLT (como sempre)
    @@@neo4j
    @@@jolt("${#recipe['jolts']['joltNeo4jTableToJson']}")
    @@@set("timeline")
    MATCH ...

    # 2. LLM analisa
    @@@agent("TIMELINE_ANALYST")
    @@@set("analysis")

    Analyze this timeline:
    ${@JsonUtils.writeAsJsonString(#timeline, true)}

    Identify trends, anomalies, and insights.
```

---

### 3. Changelog Extraction

**Atual (CORRETO):**
```yaml
templates:
  enrichChangelogData: |-
    @@@jolt("${#recipe['jolts']['joltEnrichChangelog']}")  # ← JOLT
    @@@set("enrichedIssues")
```

**✅ Manter JOLT porque:**
- Extração puramente estrutural
- Não requer semântica
- Perfeito para JOLT

**🤔 LLM não adiciona valor aqui**
- Seria mais lento
- Seria mais caro
- Sem ganho de qualidade

---

## Recomendações Finais

### Para a Receita lmt-jira-report

**✅ MANTER JOLT para:**
1. ✅ Normalização inicial do Jira (`joltJiraToNormalized`)
2. ✅ Conversão de resultados Neo4j (`joltNeo4jTableToJson`)
3. ✅ Extração de changelogs (`joltEnrichChangelog`)

**✨ ADICIONAR LLM para:**
1. ✨ Limpeza de descrições (semântica)
2. ✨ Classificação de issues (interpretação)
3. ✨ Extração de entidades técnicas (NER)
4. ✨ Análise de resultados de queries (insights)
5. ✨ Geração de schemas dinâmicos (descoberta)

### Pipeline Ideal Completo

```yaml
projectModel:
  # ETAPA 1: Coleta + Normalização Estrutural (JOLT)
  collectJiraData: |-
    @@@api(...)
    @@@jolt("${#recipe['jolts']['joltJiraToNormalized']}")
    @@@set("normalizedIssues")

  # ETAPA 2: Enriquecimento Estrutural (JOLT)
  enrichChangelogs: |-
    @@@spel("${#normalizedIssues}")
    @@@jolt("${#recipe['jolts']['joltEnrichChangelog']}")
    @@@set("structurallyEnriched")

  # ETAPA 3: Enriquecimento Semântico (LLM)
  semanticEnrichment.json: |-
    @@@exec("${#recipe['templates']['cleanDescriptions']}")
    @@@exec("${#recipe['templates']['classifyIssues']}")
    @@@exec("${#recipe['templates']['extractEntities']}")

  # ETAPA 4: Persistência
  buildGraph: "${#recipe['templates']['buildGraph']}"

  # ETAPA 5: Analytics (Neo4j + JOLT)
  analytics.json: |-
    @@@exec("${#recipe['templates']['runAnalyticsQueries']}")

  # ETAPA 6: Insights (LLM analisa resultados)
  llmInsights.json: |-
    @@@exec("${#recipe['templates']['generateLLMInsights']}")
```

---

## Conclusão

**Não é "JOLT OU LLM", é "JOLT E LLM":**

```
JOLT = Braço Direito (estrutura, velocidade, consistência)
LLM  = Cérebro (semântica, contexto, inteligência)
```

**Princípio:**
- Use JOLT para o que é **mecânico e determinístico**
- Use LLM para o que requer **entendimento e contexto**
- Use **ambos juntos** para melhor resultado

**Regra de Ouro:**
> "Se um regex ou JOLT spec consegue fazer, use isso.
> Se precisa entender o significado, use LLM.
> Na dúvida, use ambos em sequência."

---

## Recursos Adicionais

- Documentação JOLT: https://github.com/bazaarvoice/jolt
- Spec atual na receita: linhas 155-321 do `lmt-jira-report.yaml`
- Exemplos LLM: `docs/lmt-jira-report-llm-examples-CORRECTED.yaml`
