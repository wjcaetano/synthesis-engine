# 🐛 Bugs e Melhorias - Relatórios Jira

## 📋 Sumário Executivo

Análise completa dos bugs identificados nos relatórios HTML gerados pela receita `lmt-jira-report2.yaml` e propostas de correção e melhorias.

---

## 🔴 BUGS CRÍTICOS IDENTIFICADOS

### **Bug #1: Métricas Zeradas no Dashboard (index.html)**

**Problema:**
```html
<div class="stat-card">
  <h3>Completed</h3>
  <div class="number">0</div>  <!-- ❌ ERRADO - Deveria ser ~10 -->
</div>
<div class="stat-card">
  <h3>In Progress</h3>
  <div class="number">0</div>  <!-- ❌ ERRADO -->
</div>
<div class="stat-card">
  <h3>Overall Progress</h3>
  <div class="number">0%</div> <!-- ❌ ERRADO - Deveria ser ~18% -->
</div>
```

**Causa Raiz:**
- **Arquivo**: `lmt-jira-report2.yaml`, linha 1028
- **Query Neo4j**: `queryProjectStats`

```cypher
count(CASE WHEN i.status IN ['Done', 'Closed'] THEN 1 END) AS completedIssues
```

**Problema**: A query busca status `'Done'` ou `'Closed'`, mas as issues do Jira têm status **`'Resolved'`**.

**Evidência:**
No `issues.html` vemos issues com status "Resolved":
- LMT-497: Status = "Resolved"
- LMT-16: Status = "Resolved"
- LMT-17: Status = "Resolved"
- LMT-696: Status = "Resolved"
- etc.

**Impacto:**
- Dashboard mostra 0% de progresso quando na verdade ~18% está completo (10/56)
- Métricas de completed e in progress incorretas
- Executive summary mostra dados errados

---

### **Bug #2: Relatórios de Usuário Analisam Pessoa Errada**

**Problema:**

**Relatório de Willian Caetano:**
```html
<h1>👤 Willian Caetano</h1>
<!-- Mas a análise fala de Heloisa Rocha: -->
<p>Heloisa Rocha currently has <span class="highlight">6 tasks</span>...</p>
```

**Relatório de Fabio Faria:**
```html
<h1>👤 Fabio Faria</h1>
<!-- Mas a análise TAMBÉM fala de Heloisa Rocha: -->
<p>Heloisa Rocha currently has <span class="highlight">6 tasks</span>...</p>
```

**Causa Raiz:**
- **Arquivo**: `lmt-jira-report2.yaml`, linha 1580-1629
- **Prompt da LLM**: `USER_REPORT_AGENT`

**Possíveis Causas:**
1. **Cache/Thread da LLM não está sendo limpo** - `@@@closellmthread` não está funcionando
2. **Temperatura muito baixa (0.2)** - LLM está "colada" na primeira resposta
3. **Prompt não é assertivo o suficiente** - Precisa de mais ênfase
4. **userDataJson não está correto** - Dados não estão sendo injetados corretamente

**Impacto:**
- **CRÍTICO**: Todos os relatórios individuais mostram análise errada
- Usuários recebem análise de outra pessoa
- Perda total de confiança nos relatórios

---

### **Bug #3: Lista de Épicos Vazia**

**Problema:**
```html
<div class="card">
  <h2>🎯 By Epic</h2>
  <div class="link-grid">
    <!-- VAZIO - Nenhum épico listado -->
  </div>
</div>
```

**Causa Raiz:**
- **Arquivo**: `lmt-jira-report2.yaml`, linha 953-974
- **Query Neo4j**: `queryAllEpics`

```cypher
OPTIONAL MATCH (e:Epic:JiraReport)
OPTIONAL MATCH (e)<-[:BELONGS_TO_EPIC]-(i:Issue:JiraReport)
```

**Possíveis Causas:**
1. **Nenhum épico foi extraído do Jira** - Issues não têm campo `parent` com tipo "Epic"
2. **Relacionamento BELONGS_TO_EPIC não foi criado** - LLM não extraiu
3. **Campo parent está null** - Jira não retorna épicos para essas issues

**Impacto:**
- Impossível ver progresso por épico
- Navegação por épico não funciona
- Perda de contexto de agrupamento

---

### **Bug #4: Status "In Progress" Não É Capturado no Neo4j**

**Problema:**
- Dashboard mostra "In Progress: 0" mesmo com muitas issues em progresso
- Ao consultar Neo4j, TODAS as issues têm status "Open" ou "Resolved"
- NENHUMA issue tem status "In Progress"

**Evidência Neo4j:**
```json
{
  "status": "Open",     // ✅ Existe
  "status": "Resolved", // ✅ Existe
  "status": "In Progress" // ❌ NÃO EXISTE (mas deveria!)
}
```

**Causa Raiz:**
- **Arquivo**: `lmt-jira-report2.yaml`, linha 589
- **Prompt da LLM**: `KNOWLEDGE_GRAPH_EXTRACTOR` (processChunk)

**Problema**: O prompt instruía a LLM para extrair `status: string` mas não enfatizava que o status deve ser extraído EXATAMENTE como vem do Jira. A LLM estava normalizando/interpretando os status values, convertendo "In Progress" para "Open".

**Fluxo de Dados:**
1. Jira API retorna: `status: { name: "In Progress" }`
2. JOLT transformation extrai: `status: "In Progress"` ✅
3. LLM processa e normaliza para: `status: "Open"` ❌
4. Neo4j persiste: `status: "Open"` ❌

**Impacto:**
- **CRÍTICO**: Métricas de "In Progress" sempre zeradas
- Impossível rastrear issues atualmente em andamento
- Dashboard não reflete a realidade do projeto
- Analytics e queries sobre work-in-progress falham

---

## 🔧 CORREÇÕES PROPOSTAS

### **Correção #1: Incluir 'Resolved' nos Status de Completude**

**Arquivo**: `lmt-jira-report2.yaml`

**Localização**: Múltiplas queries precisam ser corrigidas:

#### 1.1. queryProjectStats (linha 1028)
```cypher
# ANTES:
count(CASE WHEN i.status IN ['Done', 'Closed'] THEN 1 END) AS completedIssues

# DEPOIS:
count(CASE WHEN i.status IN ['Done', 'Closed', 'Resolved'] THEN 1 END) AS completedIssues
```

#### 1.2. queryAllUsers (linha 943)
```cypher
# ANTES:
count(DISTINCT CASE WHEN i.status IN ['Done', 'Closed', 'Resolved'] THEN i END) AS completed

# DEPOIS: ✅ JÁ ESTÁ CORRETO (já inclui 'Resolved')
```

#### 1.3. queryAllEpics (linha 964)
```cypher
# ANTES:
count(CASE WHEN i.status IN ['Done', 'Closed', 'Resolved'] THEN 1 END) AS completed

# DEPOIS: ✅ JÁ ESTÁ CORRETO
```

**Implementação:**
```yaml
queryProjectStats: |-
  @@@log("#FF00FFQuerying project statistics...")
  @@@neo4j
  @@@jolt("${#recipe['jolts']['joltNeo4jTableToJson']}")
  @@@set("projectStats")

  OPTIONAL MATCH (i:Issue:JiraReport)
  WITH count(i) AS totalIssues,
       count(CASE WHEN i.status IN ['Done', 'Closed', 'Resolved'] THEN 1 END) AS completedIssues,
       count(CASE WHEN i.status = 'In Progress' THEN 1 END) AS inProgress,
       count(CASE WHEN i.status = 'Open' THEN 1 END) AS open,
       count(CASE WHEN i.status CONTAINS 'Block' THEN 1 END) AS blocked,
       COALESCE(sum(i.storyPoints), 0) AS totalPoints,
       COALESCE(sum(CASE WHEN i.status IN ['Done', 'Closed', 'Resolved'] THEN i.storyPoints ELSE 0 END), 0) AS completedPoints
  OPTIONAL MATCH (u:User:JiraReport)
  WHERE u.accountId <> 'unassigned'
  WITH totalIssues, completedIssues, inProgress, open, blocked, totalPoints, completedPoints, count(u) AS totalUsers
  OPTIONAL MATCH (e:Epic:JiraReport)
  RETURN totalIssues,
         completedIssues,
         inProgress,
         open,
         blocked,
         totalPoints,
         completedPoints,
         totalUsers,
         count(e) AS totalEpics,
         CASE WHEN totalIssues > 0
              THEN round(100.0 * completedIssues / totalIssues, 1)
              ELSE 0.0 END AS overallProgress
```

---

### **Correção #2: Forçar LLM a Analisar Usuário Correto**

**Problema**: LLM ignora o usuário no JSON e analisa sempre "Heloisa Rocha"

**Solução Multifacetada:**

#### 2.1. Aumentar Temperatura do Agente
```yaml
# ANTES (linha 82-86):
- name: USER_REPORT_AGENT
  provider: azure
  model: gpt-4o-mini
  deploymentName: Chatbot
  temperature: 0.2  # ❌ Muito baixo - LLM "cola" na primeira resposta
  maxTurns: 1

# DEPOIS:
- name: USER_REPORT_AGENT
  provider: azure
  model: gpt-4o-mini
  deploymentName: Chatbot
  temperature: 0.5  # ✅ Mais variação entre respostas
  maxTurns: 1
```

#### 2.2. Melhorar Prompt para Ser Mais Assertivo

**Arquivo**: `lmt-jira-report2.yaml`, linha 1587-1629

```yaml
generateUserReportAnalysis: |-
  @@@_exec("${#recipe['templates']['prepareUserReportData']}")
  @@@freemarker
  @@@closellmthread
  @@@agent("USER_REPORT_AGENT")
  @@@set("userAnalysis")

  You are a team performance analyst. You MUST analyze ONLY the specific user provided in the data below.

  # USER TO ANALYZE

  **User Name**: ${currentUser.userName}
  **User Email**: ${currentUser.userEmail}
  **User ID**: ${currentUser.userId}

  # FULL USER DATA (JSON)

  ${userDataJson}

  # CRITICAL INSTRUCTIONS

  ⚠️ **MANDATORY**: Analyze ONLY **${currentUser.userName}** (ID: ${currentUser.userId})

  ❌ DO NOT analyze any other user
  ❌ DO NOT use generic team-wide analysis
  ❌ DO NOT mention any user other than **${currentUser.userName}**
  ✅ ONLY reference issues from the issueDetails array above
  ✅ ALWAYS use the user's name **${currentUser.userName}** when referring to them

  # ANALYSIS REQUIREMENTS

  Write a detailed performance analysis (300-400 words) for **${currentUser.userName}** covering:

  ## 1. Performance Overview (2 paragraphs)

  Start with: "**${currentUser.userName}** currently has..."

  - **${currentUser.userName}**'s current workload: ${currentUser.totalIssues} issues
  - **${currentUser.userName}**'s completion rate: ${currentUser.completionRate}%
  - Analysis of **${currentUser.userName}**'s completed vs in-progress tasks

  ## 2. Detailed Insights (2-3 paragraphs)

  - Distribution of **${currentUser.userName}**'s issues (by type, priority, status)
  - **${currentUser.userName}**'s specific strengths based on completed tasks
  - Potential bottlenecks in **${currentUser.userName}**'s workload
  - **${currentUser.userName}**'s work patterns

  ## 3. Actionable Recommendations (1-2 paragraphs)

  - Specific suggestions for **${currentUser.userName}** to improve
  - Workload optimization for **${currentUser.userName}**
  - Skills development needs for **${currentUser.userName}**

  # OUTPUT FORMAT

  Return 5-7 HTML <p> tags (NO wrapper elements).

  # FORMATTING RULES

  - Use <strong> for section headers
  - Use <span class="highlight"> for metrics
  - Use <span class="success"> for strengths
  - Use <span class="risk"> for concerns
  - Reference specific issue keys (e.g., LMT-123)

  # SPECIAL CASE

  If **${currentUser.userName}** has 0 issues (totalIssues = 0), write:

  "<p><strong>${currentUser.userName}</strong> has no assigned issues in the current period (last ${$api.configs.options.daysBack} days).</p>"

  # GENERATE ANALYSIS NOW

  Analyze **${currentUser.userName}** (${currentUser.userEmail}):
```

#### 2.3. Adicionar Validação Pós-LLM

Adicionar um template que valida se a LLM analisou o usuário correto:

```yaml
validateUserAnalysis: |-
  @@@get("userAnalysis")
  @@@set("rawUserAnalysis")
  @@@freemarker
  @@@set("userAnalysis")
  <#if !rawUserAnalysis?contains(currentUser.userName)>
  <p><strong>⚠️ Analysis Error</strong></p>
  <p><strong>${currentUser.userName}</strong> has ${currentUser.totalIssues} assigned issues with a completion rate of ${currentUser.completionRate}%. Detailed analysis could not be generated. Please review their tasks manually.</p>
  <#else>
  ${rawUserAnalysis}
  </#if>
```

---

### **Correção #3: Investigar e Corrigir Épicos Vazios**

**Diagnóstico:**

1. **Verificar se Jira retorna campo parent**
   - Adicionar log para ver se `parent` está presente nos dados

2. **Verificar se LLM extrai épicos**
   - Verificar se o Knowledge Graph tem nós do tipo "Epic"

3. **Adicionar fallback para épicos**
   - Se não houver épicos, mostrar mensagem explicativa

**Implementação:**

#### 3.1. Adicionar Log de Diagnóstico
```yaml
collectJiraIssues: |-
  # ... código existente ...
  @@@set("enrichedIssues")
  @@@log("${'Normalized and enriched ' + #enrichedIssues.size() + ' issues'}")

  # NOVO: Log para verificar épicos
  @@@spel("${#enrichedIssues.stream().filter(i -> i['parent'] != null && i['parent']['issueType'] == 'Epic').count()}")
  @@@set("epicCount")
  @@@log("${'Found ' + #epicCount + ' issues with Epic parent'}")

  @@@jsonify
```

#### 3.2. Melhorar Template de Index para Mostrar Aviso
```yaml
generateIndexReport: |-
  # ... no HTML ...
  <div class="card">
    <h2>🎯 By Epic</h2>
    <#if allEpics?? && allEpics?has_content>
    <div class="link-grid">
      <#list allEpics as epic>
      <a href="epics/epic_${epic?index?string('0000')}.html" class="link-card">
        <h3>${epic.epicKey}</h3>
        <p>${epic.totalIssues!0} issues • ${epic.progress!0}% complete</p>
      </a>
      </#list>
    </div>
    <#else>
    <div style="padding: 2rem; text-align: center; color: #64748b;">
      <p>📭 No epics found in this period</p>
      <p style="font-size: 0.875rem; margin-top: 0.5rem;">
        Issues may not be linked to epics, or epics may not have been updated in the last ${$api.configs.options.daysBack} days.
      </p>
    </div>
    </#if>
  </div>
```

---

### **Correção #4: Preservar Status Exato do Jira (Não Normalizar)**

**Problema**: LLM estava normalizando valores de status, convertendo "In Progress" para "Open"

**Solução**: Adicionar instruções explícitas no prompt da LLM para extrair status EXATAMENTE como aparecem no input

**Arquivo**: `lmt-jira-report2.yaml`

#### 4.1. Atualizar Definição de Issue Nodes (linha 589)
```yaml
# ANTES:
- status: string

# DEPOIS:
- status: string ⚠️ **CRITICAL**: Extract the EXACT status value from input - DO NOT normalize, standardize, or change it. If input has "In Progress", use "In Progress". If "Open", use "Open". Preserve the exact string including spaces and capitalization.
```

#### 4.2. Adicionar Regra de Normalização Crítica (linha 727)
```yaml
# NORMALIZATION & RESOLUTION RULES

⚠️ **CRITICAL - Status Values**: NEVER normalize, standardize, or change status values. Extract them EXACTLY as they appear in the input data. "In Progress" must stay "In Progress", NOT become "Open" or "InProgress".
```

#### 4.3. Adicionar Validação no Quality Check (linha 806)
```yaml
# QUALITY CHECKS

Before returning, verify:
- ✅ All User nodes have accountId, name, evidence
- ✅ All Issue nodes have key, summary, evidence
- ✅ **CRITICAL**: All Issue nodes have EXACT status from input (DO NOT change "In Progress" to "Open" or any other value)
```

#### 4.4. Aplicar Mesma Correção para Epic e StatusChange Nodes
```yaml
### 3. Epic Nodes
- status: parent.status ⚠️ **Extract EXACT status value - DO NOT normalize**

### 4. StatusChange Nodes
- from: status string ⚠️ **Extract EXACT status value - DO NOT normalize**
- to: status string ⚠️ **Extract EXACT status value - DO NOT normalize**
```

**Validação da Correção:**
Após aplicar a correção, consultar Neo4j:
```cypher
MATCH (i:Issue:JiraReport)
RETURN i.status AS status, count(i) AS count
ORDER BY count DESC
```

Resultado esperado deve incluir:
```
status           | count
-----------------|------
"In Progress"    | X     ✅ (deve aparecer!)
"Open"           | Y
"Resolved"       | Z
"Done"           | W
```

---

## 🚀 MELHORIAS PROPOSTAS

### **Melhoria #1: Dashboard com Mais Métricas**

Adicionar estatísticas adicionais ao index.html:

```html
<!-- Adicionar ao grid de stats -->
<div class="stat-card">
  <h3>Open</h3>
  <div class="number">${stats.open!0}</div>
</div>
<div class="stat-card">
  <h3>Blocked</h3>
  <div class="number">${stats.blocked!0}</div>
</div>
<div class="stat-card">
  <h3>Team Size</h3>
  <div class="number">${stats.totalUsers!0}</div>
</div>
<div class="stat-card">
  <h3>Velocity (pts/day)</h3>
  <div class="number">${((stats.completedPoints!0) / ($api.configs.options.daysBack!14))?string("0.0")}</div>
</div>
```

---

### **Melhoria #2: Gráficos Visuais**

Adicionar Chart.js para visualizações:

```html
<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>

<!-- Status Distribution Pie Chart -->
<div class="card">
  <h2>📊 Status Distribution</h2>
  <canvas id="statusChart" style="max-height: 300px;"></canvas>
</div>

<script>
const statusCtx = document.getElementById('statusChart').getContext('2d');
new Chart(statusCtx, {
  type: 'doughnut',
  data: {
    labels: ['Completed', 'In Progress', 'Open', 'Blocked'],
    datasets: [{
      data: [${stats.completedIssues}, ${stats.inProgress}, ${stats.open}, ${stats.blocked}],
      backgroundColor: ['#10b981', '#3b82f6', '#f59e0b', '#ef4444']
    }]
  }
});
</script>

<!-- User Performance Bar Chart -->
<div class="card">
  <h2>👥 Team Performance</h2>
  <canvas id="userChart" style="max-height: 400px;"></canvas>
</div>

<script>
const userCtx = document.getElementById('userChart').getContext('2d');
new Chart(userCtx, {
  type: 'bar',
  data: {
    labels: [
      <#list allUsers as user>
      '${user.userName}'<#if user?has_next>,</#if>
      </#list>
    ],
    datasets: [
      {
        label: 'Total Issues',
        data: [<#list allUsers as user>${user.totalIssues}<#if user?has_next>,</#if></#list>],
        backgroundColor: '#667eea'
      },
      {
        label: 'Completed',
        data: [<#list allUsers as user>${user.completed}<#if user?has_next>,</#if></#list>],
        backgroundColor: '#10b981'
      }
    ]
  },
  options: {
    indexAxis: 'y',
    scales: {
      x: { beginAtZero: true }
    }
  }
});
</script>
```

---

### **Melhoria #3: Filtros e Busca no Dashboard**

Adicionar JavaScript para filtrar issues:

```html
<!-- Search Bar -->
<div class="card">
  <h2>🔍 Search Issues</h2>
  <input
    type="text"
    id="issueSearch"
    placeholder="Search by key, summary, assignee..."
    style="width: 100%; padding: 1rem; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 1rem;"
  >
</div>

<!-- Filters -->
<div class="card">
  <h2>🎛️ Filters</h2>
  <div style="display: flex; gap: 1rem; flex-wrap: wrap;">
    <select id="statusFilter" style="padding: 0.5rem; border-radius: 6px;">
      <option value="">All Statuses</option>
      <option value="Open">Open</option>
      <option value="In Progress">In Progress</option>
      <option value="Resolved">Resolved</option>
      <option value="Closed">Closed</option>
    </select>

    <select id="priorityFilter" style="padding: 0.5rem; border-radius: 6px;">
      <option value="">All Priorities</option>
      <option value="High">High</option>
      <option value="Medium">Medium</option>
      <option value="Low">Low</option>
    </select>

    <select id="assigneeFilter" style="padding: 0.5rem; border-radius: 6px;">
      <option value="">All Assignees</option>
      <#list allUsers as user>
      <option value="${user.userName}">${user.userName}</option>
      </#list>
    </select>
  </div>
</div>

<script>
// Filter logic
const allIssues = ${allIssues?json_string};
const searchInput = document.getElementById('issueSearch');
const statusFilter = document.getElementById('statusFilter');
const priorityFilter = document.getElementById('priorityFilter');
const assigneeFilter = document.getElementById('assigneeFilter');

function applyFilters() {
  const searchTerm = searchInput.value.toLowerCase();
  const status = statusFilter.value;
  const priority = priorityFilter.value;
  const assignee = assigneeFilter.value;

  // Filter and re-render table
  // ... implementation ...
}

searchInput.addEventListener('input', applyFilters);
statusFilter.addEventListener('change', applyFilters);
priorityFilter.addEventListener('change', applyFilters);
assigneeFilter.addEventListener('change', applyFilters);
</script>
```

---

### **Melhoria #4: Timeline de Atividades**

Adicionar seção mostrando atividades recentes:

```html
<div class="card">
  <h2>📅 Recent Activity</h2>
  <div style="border-left: 3px solid #667eea; padding-left: 1.5rem;">
    <#if dailyChanges?? && dailyChanges?has_content>
    <#list dailyChanges as day>
    <div style="margin-bottom: 2rem;">
      <h3 style="color: #667eea; font-size: 1.1rem; margin-bottom: 0.5rem;">
        ${day.date}
      </h3>
      <div style="color: #64748b; font-size: 0.875rem; margin-bottom: 0.75rem;">
        <span class="success">${day.completed} completed</span> •
        <span style="color: #3b82f6;">${day.started} started</span> •
        <#if day.blocked gt 0><span class="risk">${day.blocked} blocked</span></#if>
      </div>
      <#if day.changes?? && day.changes?has_content>
      <#list day.changes as change>
      <div style="padding: 0.5rem 1rem; background: #f8fafc; border-radius: 6px; margin-bottom: 0.5rem;">
        <code style="color: #667eea; font-weight: 600;">${change.issueKey}</code>
        <span style="color: #475569;">: ${change.summary?truncate(60)}</span>
        <div style="font-size: 0.75rem; color: #64748b; margin-top: 0.25rem;">
          ${change.from} → ${change.to} by ${change.author}
        </div>
      </div>
      </#list>
      </#if>
    </div>
    </#list>
    </#if>
  </div>
</div>
```

---

### **Melhoria #5: Alertas de Riscos**

Adicionar seção de alertas automáticos:

```html
<div class="card">
  <h2>⚠️ Risks & Alerts</h2>

  <#assign risks = []>

  <!-- Check: Blocked issues -->
  <#if projectStats[0].blocked gt 0>
    <#assign risks = risks + [{
      "type": "risk",
      "icon": "🚫",
      "title": "Blocked Issues",
      "message": projectStats[0].blocked + " issues are currently blocked"
    }]>
  </#if>

  <!-- Check: Overloaded users -->
  <#list allUsers as user>
    <#if user.totalIssues gt 20>
      <#assign risks = risks + [{
        "type": "warning",
        "icon": "⚡",
        "title": "High Workload",
        "message": user.userName + " has " + user.totalIssues + " issues assigned"
      }]>
    </#if>
  </#list>

  <!-- Check: Low completion rate -->
  <#if projectStats[0].overallProgress lt 30>
    <#assign risks = risks + [{
      "type": "risk",
      "icon": "📉",
      "title": "Low Progress",
      "message": "Only " + projectStats[0].overallProgress + "% of issues are completed"
    }]>
  </#if>

  <#if risks?has_content>
    <#list risks as risk>
    <div style="padding: 1rem 1.5rem; background: <#if risk.type == 'risk'>#fee2e2<#else>#fef3c7</#if>; border-left: 4px solid <#if risk.type == 'risk'>#dc2626<#else>#f59e0b</#if>; border-radius: 6px; margin-bottom: 1rem;">
      <div style="font-weight: 600; color: #1e293b; margin-bottom: 0.25rem;">
        ${risk.icon} ${risk.title}
      </div>
      <div style="color: #475569; font-size: 0.95rem;">
        ${risk.message}
      </div>
    </div>
    </#list>
  <#else>
    <div style="text-align: center; padding: 2rem; color: #10b981;">
      <div style="font-size: 3rem; margin-bottom: 0.5rem;">✅</div>
      <div style="font-weight: 600;">No critical risks detected</div>
    </div>
  </#if>
</div>
```

---

### **Melhoria #6: Exportar Dados**

Adicionar botão para exportar para CSV/JSON:

```html
<div class="card">
  <h2>💾 Export Data</h2>
  <div style="display: flex; gap: 1rem;">
    <button onclick="exportToCSV()" style="padding: 0.75rem 1.5rem; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600;">
      📄 Export to CSV
    </button>
    <button onclick="exportToJSON()" style="padding: 0.75rem 1.5rem; background: #10b981; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600;">
      📦 Export to JSON
    </button>
  </div>
</div>

<script>
function exportToCSV() {
  const allIssues = ${allIssues?json_string};
  let csv = 'Key,Summary,Type,Priority,Status,Assignee,Story Points\n';

  allIssues.forEach(issue => {
    csv += `"${issue.issueKey}","${issue.summary}","${issue.issueType}","${issue.priority}","${issue.status}","${issue.assigneeName}",${issue.storyPoints}\n`;
  });

  const blob = new Blob([csv], { type: 'text/csv' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'jira-report-${.now?string("yyyy-MM-dd")}.csv';
  a.click();
}

function exportToJSON() {
  const data = {
    projectStats: ${projectStats?json_string},
    allUsers: ${allUsers?json_string},
    allIssues: ${allIssues?json_string},
    generatedAt: '${.now?string("yyyy-MM-dd HH:mm:ss")}'
  };

  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'jira-report-${.now?string("yyyy-MM-dd")}.json';
  a.click();
}
</script>
```

---

## 📊 RESUMO DAS CORREÇÕES

| Bug | Severidade | Causa | Correção | Impacto |
|-----|-----------|-------|----------|---------|
| **Métricas Zeradas** | 🔴 Crítico | Query busca 'Done'/'Closed' mas Jira usa 'Resolved' | Adicionar 'Resolved' nas queries | Métricas corretas |
| **LLM analisa usuário errado** | 🔴 Crítico | Temperatura baixa + prompt fraco + cache | Aumentar temp + prompt mais assertivo + validação | Análises corretas |
| **Épicos vazios** | 🟡 Médio | Issues sem épicos OU LLM não extrai | Adicionar logs + fallback UI | Melhor UX |

---

## 🎯 PRIORIZAÇÃO DE IMPLEMENTAÇÃO

### **Fase 1 - Correções Críticas (Imediato)**
1. ✅ Corrigir query `queryProjectStats` - adicionar 'Resolved'
2. ✅ Aumentar temperatura do `USER_REPORT_AGENT` para 0.5
3. ✅ Melhorar prompt de análise de usuário
4. ✅ Adicionar validação pós-LLM

### **Fase 2 - Melhorias Essenciais (Curto Prazo)**
5. ✅ Adicionar métricas extras no dashboard (Open, Blocked, Team Size)
6. ✅ Adicionar mensagem explicativa para épicos vazios
7. ✅ Adicionar seção de Recent Activity
8. ✅ Adicionar seção de Risks & Alerts

### **Fase 3 - Melhorias Avançadas (Médio Prazo)**
9. ⏳ Implementar Chart.js para gráficos visuais
10. ⏳ Adicionar filtros e busca no dashboard
11. ⏳ Adicionar exportação CSV/JSON
12. ⏳ Implementar dashboard responsivo (mobile-friendly)

---

## 📝 PRÓXIMOS PASSOS

1. **Implementar Correção #1**: Corrigir queries Neo4j
2. **Implementar Correção #2**: Melhorar prompt de usuário e temperatura
3. **Testar com dados reais**: Executar receita e validar correções
4. **Implementar Melhorias Fase 2**: Adicionar métricas e alertas
5. **Iterar baseado em feedback**: Ajustar baseado em uso real

---

## 📚 Referências

- **Arquivo Principal**: `src/main/resources/recipes/lmt-jira-report2.yaml`
- **Linhas Críticas**:
  - 1028: queryProjectStats (Bug #1)
  - 1580-1629: generateUserReportAnalysis (Bug #2)
  - 953-974: queryAllEpics (Bug #3)
- **Status Jira**: https://support.atlassian.com/jira-cloud-administration/docs/what-are-issue-statuses-priorities-and-resolutions/

---

## 👥 Autor

- **Análise**: Claude (Anthropic)
- **Data**: 2025-11-17
- **Versão**: 1.0
