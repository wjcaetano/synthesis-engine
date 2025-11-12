# LMT Jira Report - Enhanced Recipe Guide

## Visão Geral

A receita `lmt-jira-report-enhanced.yaml` é uma versão melhorada da receita original que integra **LLM Intelligence** em pontos estratégicos do pipeline, mantendo a eficiência do JOLT para transformações estruturais.

---

## 🆚 Comparação: Original vs Enhanced

| Aspecto | Original | Enhanced |
|---------|----------|----------|
| **Coleta de dados** | ✅ Jira API + JOLT | ✅ Jira API + JOLT (mantido) |
| **Normalização** | ✅ JOLT estrutural | ✅ JOLT estrutural (mantido) |
| **Limpeza semântica** | ❌ Não tem | ✨ LLM remove ruído de descrições |
| **Classificação** | ❌ Manual | ✨ LLM classifica automaticamente |
| **Extração de entidades** | ❌ Não tem | ✨ LLM identifica serviços, DBs, APIs |
| **Persistência Neo4j** | ✅ Modelo básico | ✨ Modelo expandido com entidades |
| **Analytics** | ✅ Queries Cypher | ✅ Queries Cypher + análise de entidades |
| **Análise de dependências** | ❌ Não tem | ✨ LLM gera queries → explora grafo |
| **Detecção de padrões** | ❌ Não tem | ✨ LLM detecta bottlenecks, zombies, etc. |
| **Action plan** | ❌ Não tem | ✨ LLM gera tarefas acionáveis |
| **Relatório** | ✅ HTML básico | ✨ HTML com insights AI |
| **Configurabilidade** | ❌ Fixo | ✨ Flags para habilitar/desabilitar LLM |

---

## 🏗️ Arquitetura do Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│  PHASE 1: Data Collection & Structural Normalization       │
├─────────────────────────────────────────────────────────────┤
│  Jira API → JOLT (joltJiraToNormalized) → Normalized JSON  │
│  Changelog extraction → JOLT (joltEnrichChangelog)         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  PHASE 2: LLM Enrichment (Conditional)                     │
├─────────────────────────────────────────────────────────────┤
│  ✨ LLM cleans descriptions (DATA_CLEANER)                 │
│  ✨ LLM classifies issues (ISSUE_CLASSIFIER)               │
│  ✨ LLM extracts entities (ENTITY_EXTRACTOR)               │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  PHASE 3: Graph Building                                   │
├─────────────────────────────────────────────────────────────┤
│  Neo4j Models: User, Epic, Issue, StatusChange             │
│  ✨ NEW: TechnicalEntity (Service, Database, etc.)         │
│  Relationships: ASSIGNED_TO, BELONGS_TO_EPIC, etc.         │
│  ✨ NEW: MENTIONS (Issue → TechnicalEntity)                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  PHASE 4: Analytics Queries                                │
├─────────────────────────────────────────────────────────────┤
│  Daily Timeline, Blocker Analysis, User Performance        │
│  Epic Progress, Velocity Trends                            │
│  ✨ NEW: Technical Entity Analysis                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  PHASE 5: Advanced LLM Analysis (Conditional)              │
├─────────────────────────────────────────────────────────────┤
│  ✨ Dependency Analysis:                                   │
│     - LLM generates Cypher queries                         │
│     - Template executes with @@@neo4j                      │
│     - LLM analyzes results and calculates impact           │
│  ✨ Pattern Detection:                                     │
│     - LLM generates queries for 5 patterns                 │
│     - Detects: bottlenecks, zombies, ping-pong, etc.      │
│  ✨ Action Plan Generation:                                │
│     - Converts insights into prioritized tasks             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  PHASE 6: Insights Generation                              │
├─────────────────────────────────────────────────────────────┤
│  STRATEGY_AGENT: Strategic analysis                        │
│  Executive Summary generation                              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  PHASE 7: Report Generation                                │
├─────────────────────────────────────────────────────────────┤
│  HTML Report with:                                         │
│  - AI-generated executive summary                          │
│  - Focus areas and recommendations                         │
│  - Critical issues with reasoning                          │
│  - Enhanced visualizations                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎛️ Configuração

### Opções Adicionais

```yaml
config:
  options:
    # ... opções existentes ...

    - name: enableLLMEnrichment
      type: BOOLEAN
      label: "Enable LLM Enrichment (classification, entities, etc.)?"
      defaultValue: true

    - name: enableAdvancedAnalysis
      type: BOOLEAN
      label: "Enable Advanced LLM Analysis (patterns, dependencies)?"
      defaultValue: true
```

**Quando desabilitar:**
- **enableLLMEnrichment: false** → Economiza custos, pipeline mais rápido, pula classificação e extração
- **enableAdvancedAnalysis: false** → Pula análise de padrões e dependências (ainda gera insights básicos)

---

## 🤖 Agents Configurados

### Data Processing Agents

| Agent | Model | Temp | Purpose |
|-------|-------|------|---------|
| `DATA_CLEANER` | gpt-4o-mini | 0.1 | Remove ruído de descrições |
| `ISSUE_CLASSIFIER` | gpt-4o-mini | 0.15 | Classifica issues (área, complexidade, impacto) |
| `ENTITY_EXTRACTOR` | gpt-4o | 0.2 | Extrai serviços, DBs, APIs mencionados |

### Analysis Agents

| Agent | Model | Temp | Purpose |
|-------|-------|------|---------|
| `DEPENDENCY_QUERY_GENERATOR` | gpt-4o | 0.2 | Gera queries Cypher para dependências |
| `DEPENDENCY_ANALYST` | gpt-4o | 0.25 | Analisa resultados de dependências |
| `PATTERN_QUERY_GENERATOR` | gpt-4o | 0.25 | Gera queries para detecção de padrões |
| `PATTERN_ANALYST` | gpt-4o | 0.3 | Analisa padrões detectados |

### Strategic & Reporting Agents

| Agent | Model | Temp | Purpose |
|-------|-------|------|---------|
| `STRATEGY_AGENT` | gpt-4o | 0.2 | Análise estratégica de alto nível |
| `ACTION_PLANNER` | gpt-4o | 0.3 | Gera action items acionáveis |
| `NARRATIVE_GENERATOR` | gpt-4o | 0.6 | Narrativas para gráficos |

---

## 📊 Modelos Neo4j Expandidos

### Modelo TechnicalEntity (NOVO)

```yaml
TechnicalEntity:
  labels: ["TechnicalEntity", "JiraReport"]
  key: "${type}:${name}"  # Ex: "Service:UserService"
  properties:
    type: "Service|Database|ExternalSystem|Infrastructure"
    name: "Nome da entidade"
    context: "Contexto onde foi mencionada"
    firstSeenDate: "Data da primeira menção"
    mentionCount: "Número de vezes mencionada"
  relationships:
    - MENTIONED_IN → Issue
```

### Modelo JiraIssue Expandido

**Propriedades LLM adicionadas:**

```yaml
JiraIssue:
  # ... propriedades existentes ...
  descriptionClean: "Descrição limpa pelo LLM"
  llmTechnicalArea: "backend|frontend|database|..."
  llmRootCause: "code_defect|configuration_error|..."
  llmComplexity: "trivial|low|medium|high|very_high"
  llmBusinessImpact: "minor|moderate|significant|critical"
  llmTechnicalDebt: "none|low|medium|high"
  llmClassificationConfidence: 0.85
```

---

## 🔍 Análises Avançadas

### 1. Dependency Analysis

**Fluxo:**
1. LLM gera queries Cypher customizadas para cada blocker
2. Template executa queries com `@@@neo4j`
3. LLM analisa resultados e calcula impact scores

**Queries geradas:**
- Direct children (issues que dependem do blocker)
- Epic impact (outros issues no mesmo epic)
- Technical entity connections (issues mencionando mesmas entidades)

**Output:**
```json
{
  "blockerAnalysis": [{
    "issueKey": "LMT-123",
    "impactScore": {
      "direct": 80,
      "epic": 90,
      "entity": 25,
      "total": 195
    },
    "priority": "P0",
    "recommendedAction": "Assign senior dev immediately",
    "estimatedUnblockImpact": "18 issues, 73 story points"
  }],
  "resolutionOrder": ["LMT-123", "LMT-130"],
  "quickWins": [...]
}
```

### 2. Pattern Detection

**Padrões detectados:**

| Padrão | Descrição | Query Cypher Gerada |
|--------|-----------|---------------------|
| **Bottleneck Users** | Usuários com muitos issues mas baixa completion rate | `MATCH (u:User)<-[:ASSIGNED_TO]-(i) WHERE...` |
| **Zombie Issues** | Issues "In Progress" sem atividade há 14+ dias | `MATCH (i:Issue)-[:CHANGED]-(sc) WHERE duration...` |
| **Ping-Pong Issues** | Issues com > 5 mudanças de status em 30 dias | `MATCH (sc:StatusChange) WITH count(sc) WHERE...` |
| **Epic Risks** | Epics com muitos blockers ou velocity baixa | `MATCH (e:Epic)<-[:BELONGS_TO_EPIC]-(i) WHERE...` |
| **Tech Debt Hotspots** | Entidades em muitos issues de alto technical debt | `MATCH (i)-[:MENTIONS]->(e) WHERE i.llmTechnicalDebt...` |

**Output:**
```json
{
  "patterns": [{
    "patternType": "bottleneck_users",
    "severity": "high",
    "affectedEntities": {
      "users": ["john@company.com"],
      "count": 1,
      "avgWorkload": 12,
      "avgCompletionRate": 28
    },
    "recommendations": [{
      "priority": "P1",
      "action": "Redistribute 4-5 issues from John",
      "expectedImpact": "Reduce workload to 7-8 issues"
    }]
  }],
  "summary": {
    "totalPatternsDetected": 5,
    "criticalPatterns": 1
  }
}
```

### 3. Action Plan Generation

**Converte análises em tarefas acionáveis:**

```json
{
  "actions": [{
    "actionId": "ACT-001",
    "priority": "P0",
    "title": "Resolve critical blocker LMT-123",
    "description": "Issue blocking 8 others, high impact",
    "owner": "senior-backend-dev@company.com",
    "estimatedHours": 8,
    "successCriteria": "LMT-123 Done, 8 issues unblocked",
    "dueBy": "+1d"
  }],
  "summary": {
    "totalActions": 15,
    "byPriority": {"P0": 2, "P1": 5, "P2": 6, "P3": 2}
  }
}
```

---

## 💰 Otimização de Custos

### Estratégias Implementadas

1. **Modelos por tarefa:**
   - `gpt-4o-mini` para limpeza e classificação (~60% mais barato)
   - `gpt-4o` apenas para análise complexa

2. **Caching habilitado:**
   ```yaml
   caches:
     transforms:
       - prompt  # Cache prompts LLM
       - neo4j   # Cache queries Neo4j
       - jolt    # Cache transformações JOLT
   ```

3. **Execução condicional:**
   - Flags para desabilitar LLM enrichment ou advanced analysis
   - Permite usar receita sem custos LLM se necessário

4. **JOLT primeiro:**
   - JOLT remove ~60% dos tokens desnecessários antes de enviar para LLM
   - Issues normalizadas = prompts menores

### Estimativa de Custos

**Cenário: 1000 issues, 14 dias de análise**

| Componente | Modelo | Custo Estimado |
|------------|--------|----------------|
| Limpeza de descrições | gpt-4o-mini | ~$2 |
| Classificação | gpt-4o-mini | ~$3 |
| Extração de entidades | gpt-4o | ~$5 |
| Dependency analysis | gpt-4o | ~$1 |
| Pattern detection | gpt-4o | ~$1 |
| Insights & summary | gpt-4o | ~$1 |
| **TOTAL** | - | **~$13** |

**Comparado com só LLM (sem JOLT):** ~$35
**Economia:** 62%

---

## 📖 Como Usar

### 1. Executar com Todas as Features

```bash
# Via CLI ou UI
synthesis-engine execute lmt-jira-report-enhanced.yaml \
  --jiraProjectKey=LMT \
  --daysBack=14 \
  --enableLLMEnrichment=true \
  --enableAdvancedAnalysis=true
```

### 2. Executar Sem LLM (Apenas JOLT + Neo4j)

```bash
synthesis-engine execute lmt-jira-report-enhanced.yaml \
  --jiraProjectKey=LMT \
  --daysBack=14 \
  --enableLLMEnrichment=false \
  --enableAdvancedAnalysis=false
```

**Use este modo para:**
- Testes rápidos
- Economizar custos
- Quando LLM não está disponível

### 3. Executar Com Enrichment Mas Sem Advanced Analysis

```bash
synthesis-engine execute lmt-jira-report-enhanced.yaml \
  --jiraProjectKey=LMT \
  --daysBack=14 \
  --enableLLMEnrichment=true \
  --enableAdvancedAnalysis=false
```

**Bom balanço:** Classifica issues e extrai entidades, mas pula análise profunda.

---

## 🔧 Customização

### Adicionar Novo Padrão de Detecção

1. **Editar `generatePatternQueries`:**

```yaml
templates:
  generatePatternQueries: |-
    @@@agent("PATTERN_QUERY_GENERATOR")

    Generate queries for these patterns:
    1. Bottleneck users
    2. Zombie issues
    3. Ping-pong issues
    4. Epic risks
    5. Tech debt hotspots
    6. YOUR_NEW_PATTERN  # ← Adicione aqui
```

2. **LLM irá gerar query automaticamente** para o novo padrão!

### Adicionar Nova Classificação

Edite o prompt em `classifySingleIssue`:

```yaml
[CLASSIFICATION TASK]
Classify along these dimensions:
1. Technical Area: ...
2. Root Cause: ...
3. Complexity: ...
4. Business Impact: ...
5. Technical Debt: ...
6. YOUR_NEW_DIMENSION: ...  # ← Adicione aqui
```

### Adicionar Novo Tipo de Entidade

Edite o prompt em `extractEntitiesFromIssue`:

```yaml
[ENTITY TYPES TO EXTRACT]
1. Services/APIs
2. Databases
3. Infrastructure
4. External Systems
5. Technologies
6. Error Codes
7. Environments
8. YOUR_NEW_ENTITY_TYPE  # ← Adicione aqui
```

---

## 🐛 Troubleshooting

### Issue: LLM retorna JSON inválido

**Solução:** Template já usa `@@@extractMarkdownCode` que extrai JSON de blocos markdown.

Se ainda falhar, aumente `maxTurns` do agent ou ajuste temperature.

### Issue: Queries Neo4j muito lentas

**Solução:**
1. Adicione índices no Neo4j:
   ```cypher
   CREATE INDEX issue_status IF NOT EXISTS FOR (i:Issue) ON (i.status)
   CREATE INDEX issue_llm_impact IF NOT EXISTS FOR (i:Issue) ON (i.llmBusinessImpact)
   ```

2. Use `LIMIT` nas queries geradas

3. Reduza `daysBack` para menos dados

### Issue: Custos muito altos

**Soluções:**
1. Use `enableLLMEnrichment=false` para pular enrichment
2. Use `gpt-4o-mini` em mais agents
3. Reduza `daysBack` (menos issues = menos custos)
4. Aumente cache TTL

### Issue: Padrões não detectados

**Possíveis causas:**
1. Dados insuficientes (aumente `daysBack`)
2. Queries geradas pelo LLM incorretas (verifique logs)
3. Padrão não existe no projeto

**Debug:**
```yaml
# Adicione logs para ver queries geradas:
executeSinglePatternQuery: |-
  @@@log("${'Generated query: ' + #query['cypher']}")
  @@@neo4j
  @@@log("${'Query result: ' + @JsonUtils.writeAsJsonString(#queryResult, true)}")
```

---

## 🔄 Migrando da Receita Original

### Passos:

1. **Backup da receita original:**
   ```bash
   cp lmt-jira-report.yaml lmt-jira-report.yaml.bak
   ```

2. **Copiar nova receita:**
   ```bash
   cp lmt-jira-report-enhanced.yaml lmt-jira-report.yaml
   ```

3. **Primeira execução com flags desabilitadas:**
   ```bash
   # Teste sem LLM primeiro
   --enableLLMEnrichment=false
   --enableAdvancedAnalysis=false
   ```

4. **Habilite gradualmente:**
   ```bash
   # Teste com enrichment
   --enableLLMEnrichment=true
   --enableAdvancedAnalysis=false
   ```

5. **Full featured:**
   ```bash
   # Tudo habilitado
   --enableLLMEnrichment=true
   --enableAdvancedAnalysis=true
   ```

### Compatibilidade

✅ **100% compatível com dados existentes**
- Modelos Neo4j expandidos, mas compatíveis com anteriores
- Queries antigas continuam funcionando
- HTML report mantém estrutura básica

❌ **Requer:**
- Azure OpenAI configurado
- Models: gpt-4o, gpt-4o-mini disponíveis
- Variável de ambiente `JIRA_AUTH_HEADER`

---

## 📚 Recursos Relacionados

- **Documentação LLM patterns:** `docs/lmt-jira-report-llm-neo4j-pattern.md`
- **Exemplos práticos:** `docs/lmt-jira-report-llm-examples-CORRECTED.yaml`
- **JOLT vs LLM guide:** `docs/jolt-vs-llm-when-to-use.md`
- **Análise completa:** `docs/lmt-jira-report-llm-analysis.md`

---

## 🎯 Próximos Passos

### Para Melhorias Futuras

1. **Implementar tool `neo4jQuery`:**
   - Permitiria agents explorarem grafo interativamente
   - Análise multi-turn mais profunda

2. **Adicionar mais padrões:**
   - Code review bottlenecks
   - Test coverage gaps
   - Deployment frequency patterns

3. **Machine Learning:**
   - Treinar modelo para prever blocker resolution time
   - Anomaly detection automático

4. **Dashboards interativos:**
   - Substituir HTML estático por dashboard React
   - Filtros em tempo real
   - Drill-down em issues

5. **Notificações proativas:**
   - Slack/Teams quando padrão crítico detectado
   - Email digest diário automático

---

## ✅ Checklist de Qualidade

Antes de usar em produção:

- [ ] Azure OpenAI configurado e testado
- [ ] Neo4j acessível e com índices criados
- [ ] JIRA_AUTH_HEADER configurada corretamente
- [ ] Testado com `enableLLMEnrichment=false` (baseline)
- [ ] Testado com `enableLLMEnrichment=true`
- [ ] Testado com `enableAdvancedAnalysis=true`
- [ ] Custos monitorados e aceitáveis
- [ ] Logs revisados sem erros críticos
- [ ] HTML report gerado com sucesso
- [ ] Stakeholders validaram insights

---

## 📞 Suporte

Para dúvidas ou problemas:

1. **Revise logs:** Procure por `#FF0000` (erros) nos logs
2. **Consulte documentação:** `docs/lmt-jira-report-*.md`
3. **Teste com flags desabilitadas:** Isole problema (JOLT vs LLM)
4. **Verifique custos:** Azure OpenAI usage dashboard

**Happy analyzing! 🚀**
