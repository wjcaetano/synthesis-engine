# 🎯 PIVOT STRATEGY: THE SHIELD (ANTIFRAUDE PIX)

**Data do Pivot**: 2025-11-23
**Decisão**: FOCAR EM UM VERTICAL - Risco & Compliance para Fintechs

---

## 📊 ANÁLISE DO FEEDBACK VC

### ✅ **O Que Está Certo**

| Aspecto | Rating | Comentário |
|---------|--------|------------|
| **Stack Técnica** | 9/10 | DuckDB + Parquet = custo marginal ~zero |
| **Diferencial** | 9/10 | Graph + LLM = contexto que Serasa não tem |
| **Viabilidade Comercial** | 8/10 | SE nichar corretamente |

### ❌ **O Problema Crítico: FALTA DE FOCO**

**Rating de Produto**: 5/10
**Motivo**: "Plataforma de Tudo" (Compliance + Vendas + Mercado + Crédito)

> **"Quem tenta vender tudo, não vende nada."**

**Análise Brutal**:
- Lead Scoring ≠ Detecção de Fraude (clientes diferentes, dores diferentes)
- Concentração de Mercado (CADE) = ciclo de vendas de 12 meses → **MORTE**
- ML de Inadimplência = requer dados históricos que não temos → **NÃO AGORA**

---

## 🚨 RED FLAGS (O Que MATAR AGORA)

### ❌ **MATAR IMEDIATAMENTE**

| Análise | Por Que Matar | Impacto |
|---------|---------------|---------|
| **2.2: Concentração de Mercado (CADE)** | Ciclo de vendas 12 meses. Só CADE e M&A compram. Morreremos antes da 1ª venda. | 🔴 CRÍTICO |
| **2.3: Lead Scoring Comercial** | Não é dor latente. Vendas B2B já tem ferramentas (LinkedIn Sales Navigator, etc) | 🟡 MÉDIO |
| **3.2: ML de Inadimplência** | Requer histórico. Calibração complexa. Regras heurísticas são mais rápidas de vender AGORA. | 🟡 MÉDIO |

**Ação**: Remover do roadmap, simplificar receita, focar em FRAUDE.

---

## 🟢 GREEN FLAGS (O Que FOCAR 100%)

### ✅ **DOR LATENTE = DINHEIRO RÁPIDO**

| Análise | Por Que Focar | Valor Imediato |
|---------|---------------|----------------|
| **1.1: Detecção de Laranjas** | Fintechs/Subadquirentes **sangram dinheiro** com fraude HOJE. Pagam rápido para estancar. | R$ 0,50-2,00/call × alto volume |
| **1.3: Redes Circulares** | Ocultação de beneficiário final. Compliance obrigatório (PLD-FT). | R$ 500/análise forense |
| **2.1: Grupos Econômicos** | Contexto para entender risco. "Laranja controla 40 empresas" = fraude. | Complementar |
| **1.2: Risco Geográfico** | Secundário mas útil para bancos. Pode ser upsell futuro. | Futuro |

---

## 🎯 PRODUTO ÚNICO: "THE SHIELD"

### **A Promessa**
> **"Não valide apenas a chave Pix. Valide a idoneidade do recebedor."**

### **O Problema que Resolvemos**

**ValidaPix HOJE**:
- ✅ Valida técnica: "A chave Pix existe?"
- ❌ NÃO valida: "É um golpista?"

**GAP Crítico**:
- Golpista cria CNPJ ontem
- Abre conta em banco digital
- Começa a receber golpes
- **Chave é válida, mas negócio é fraude**

**Nossa Solução**:
- Ao consultar Pix, rodamos grafo em **200ms**
- Retornamos:
  ```json
  {
    "risk_score": 95,
    "risk_level": "ALTO",
    "reason": "Sócio é laranja de 5 empresas baixadas + Capital R$ 1.000 incompatível",
    "recommendation": "BLOQUEAR"
  }
  ```

---

## 🏗️ ARQUITETURA DO SHIELD

### **Stack (JÁ IMPLEMENTADO)**
- ✅ DuckDB + Parquet (consultas <200ms)
- ✅ NetworkX (grafo de relacionamentos)
- ✅ LLM (contexto e explicação de risco)
- ✅ Chunking (escala para milhares de empresas)

### **Features CORE (Foco 100%)**

#### **1. Detecção de Laranjas** (detectLaranjas.py)
**Input**: CPF ou CNPJ
**Output**: Risk Score 0-100 + Explicação

**Red Flags**:
- ✅ CPF em 10+ empresas
- ✅ Taxa >70% empresas inativas
- ✅ Capital social <R$ 10k (média)
- ✅ Mesma qualificação em todas
- ✅ Concentração geográfica suspeita

#### **2. Empresas Fantasmas** (NOVO - implementar)
**Input**: CNPJ
**Output**: Probabilidade de empresa fantasma

**Red Flags**:
- Capital social R$ 1.000
- Endereço em coworking/virtual office
- Abertura recente (<6 meses)
- Sócio em múltiplas empresas
- Zero movimentação financeira (se tivermos acesso)

#### **3. Redes Circulares** (detectCircularOwnership.py)
**Input**: CNPJ
**Output**: Ciclos detectados

**Red Flags**:
- Empresa A controla B, B controla C, C controla A
- Ocultação de beneficiário final
- Estruturas offshore

#### **4. Vínculo com Empresas Baixadas** (NOVO - implementar)
**Input**: CPF/CNPJ
**Output**: Lista de empresas baixadas/inidôneas vinculadas

**Red Flags**:
- Sócio de empresas baixadas por fraude
- Histórico de falências
- Vínculo com lista suja (PEP, sanções)

---

## 💰 MODELO DE NEGÓCIO FOCADO

### **PRODUTO ÚNICO: The Shield API**

**Modelo**: Pay-per-call (API Real-time)

**Pricing**:
- **Tier 1**: R$ 2,00/consulta (até 1.000/mês)
- **Tier 2**: R$ 1,00/consulta (1.001-10.000/mês)
- **Tier 3**: R$ 0,50/consulta (>10.000/mês)

**Target Único**: Fintechs, Subadquirentes, Bancos Digitais

**Exemplo de Revenue (ValidaPix)**:
- 50.000 consultas/mês × R$ 0,50 = **R$ 25k MRR**
- ARR = **R$ 300k/ano** (1 cliente!)
- Com 10 clientes = **R$ 3M ARR**

### **Modelo de Parceria com ValidaPix**

**Opção A: Revenue Share**
- ValidaPix vende "ValidaPix Secure" (tier premium)
- Preço: R$ 0,10 a mais por consulta
- Split: 50/50 → R$ 0,05 para nós

**Opção B: White Label**
- ValidaPix integra nossa API
- Cobram o que quiserem
- Nós cobramos R$ 0,50 fixo por call

---

## 🚀 ROADMAP REVISADO (CASH FLOW FIRST)

### **FASE 1: MVP de Ataque (Semanas 1-2)** ← **ESTAMOS AQUI**

**Objetivo**: Ter algo que detecta fraude HOJE

**Status Atual**:
- ✅ TODO-001: Chunking com LLM (FEITO!)
- ✅ Detecção de Laranjas (FEITO!)
- ✅ Redes Circulares (FEITO!)
- ✅ Grupos Econômicos (FEITO!)

**O Que Falta**:
- 🔨 **API REST** simples (Flask/FastAPI)
  - `POST /api/v1/fraud-check`
  - Input: `{"cnpj": "12345678", "cpf": "12345678901"}`
  - Output: `{"risk_score": 85, "risk_level": "ALTO", "reasons": [...]}`

- 🔨 **Empresas Fantasmas** (script Python)
  - Heurísticas: capital baixo, endereço virtual, abertura recente

- 🔨 **Fraud Case Study** (Marketing)
  - Pegar caso famoso (ex: pirâmide recente)
  - Rodar na ferramenta
  - Provar: "Detectaríamos isso 6 meses antes"
  - Publicar no LinkedIn + marcar Yuri

**Deliverable**: API funcionando + Case study provando eficácia

---

### **FASE 2: Infraestrutura de Escala (Semanas 3-4)**

**Objetivo**: Não cair quando ValidaPix conectar

**Tarefas**:
- 🔨 TODO-002: Parquet Partitioning (performance)
  - Particionar por UF ou CNPJ prefix
  - Target: <100ms para 99% das queries

- 🔨 TODO-003: File Validation (confiabilidade)
  - MD5 checksum dos arquivos RFB
  - Alerta se dados corrompidos

- 🔨 Deploy Cloud
  - **Opção A**: AWS Lambda + API Gateway (serverless)
  - **Opção B**: GCP Cloud Run (containerizado)
  - **Opção C**: VPS robusta (Hetzner/DigitalOcean) + DuckDB local

- 🔨 Monitoramento
  - Logs de latência (p50, p95, p99)
  - Alertas se >500ms

**Deliverable**: API em produção, 99.9% uptime

---

### **FASE 3: Produto de Monitoramento (Semana 5+)**

**Objetivo**: Aumentar LTV, reduzir churn

**Tarefas**:
- 🔨 TODO-004: Time-Travel (histórico mensal)
  - Guardar snapshot de cada mês
  - Detectar mudanças bruscas:
    - Troca >50% sócios
    - Redução capital >50%
    - Mudança de estado
    - Empresa ficou baixada

- 🔨 Alertas Proativos
  - Webhook quando cliente monitorado muda status
  - Email/Slack para analista de risco

- 🔨 Dashboard (opcional futuro)
  - Frontend simples para visualizar histórico
  - Comparar "antes vs depois"

**Deliverable**: Produto de monitoramento (upsell)

---

## 🎖️ O SECRET SAUCE (Diferencial vs Serasa)

### **Competidores Tradicionais**

**Serasa, Boa Vista, etc**:
- ✅ Têm os dados
- ❌ Lentos (consulta leva segundos)
- ❌ Caros (R$ 10-50/consulta)
- ❌ Entregam DADOS, não CONTEXTO

**Exemplo de Output deles**:
```
Empresa: XPTO LTDA
Sócio: João da Silva
Capital Social: R$ 1.000
Situação: Ativa
```

### **Nossa Diferença**

**Nós**:
- ✅ Rápidos (<200ms)
- ✅ Baratos (R$ 0,50-2,00)
- ✅ **CONTEXTO + EXPLICAÇÃO** (LLM)

**Exemplo de Output nosso**:
```
Empresa: XPTO LTDA
Risk Score: 98/100 (CRÍTICO)

Motivo:
O sócio João da Silva é PROVÁVEL LARANJA.
Ele aparece como sócio-administrador em 40 empresas distribuídas
em 5 estados diferentes, com capital social total de apenas R$ 500.

Padrão anômalo detectado:
- 35 dessas empresas estão baixadas
- 30 foram abertas e fechadas em <1 ano
- Todas no mesmo setor (comércio varejista)
- Concentração em endereços de coworking

Recomendação: BLOQUEAR TRANSAÇÃO
```

**O LLM não lê dados. Ele EXPLICA o risco.**

Isso é o que o analista de crédito quer ler. Não o grafo. A conclusão do grafo.

---

## 📈 PROJEÇÃO DE REVENUE (FOCADA)

### **Cenário Conservador (Ano 1)**

| Cliente | Consultas/mês | Preço/call | MRR | ARR |
|---------|---------------|------------|-----|-----|
| ValidaPix | 50.000 | R$ 0,50 | R$ 25k | R$ 300k |
| Subadquirente 1 | 30.000 | R$ 0,75 | R$ 22,5k | R$ 270k |
| Subadquirente 2 | 20.000 | R$ 1,00 | R$ 20k | R$ 240k |
| Banco Digital 1 | 10.000 | R$ 1,50 | R$ 15k | R$ 180k |
| **TOTAL** | **110.000** | - | **R$ 82,5k** | **R$ 990k** |

**Custo Marginal**: ~R$ 5k/mês (Cloud + LLM)
**Margem Bruta**: 94%

### **Cenário Otimista (Ano 2)**

- 10 clientes fintechs
- 500k consultas/mês total
- Preço médio R$ 0,60
- **MRR**: R$ 300k
- **ARR**: R$ 3,6M

---

## 🎯 PROPOSTA DE VALOR PARA VALIDAPIX

### **O Pitch (1 frase)**
> "ValidaPix garante que o Pix existe. Nós garantimos que o Pix é seguro."

### **Produto Conjunto: "Pix Garantido"**

**ValidaPix Atual**:
- Valida chave Pix (técnica)
- Retorna: "Chave válida ✅"

**ValidaPix + The Shield**:
- Valida chave Pix (técnica)
- Valida idoneidade (risco)
- Retorna:
  ```json
  {
    "chave_valida": true,
    "risk_score": 15,
    "risk_level": "BAIXO",
    "safe_to_proceed": true
  }
  ```

**Benefício para ValidaPix**:
- Diferenciação competitiva
- Upsell para tier premium
- Redução de chargebacks/fraudes dos clientes

**Benefício para Clientes do ValidaPix**:
- Menos golpes recebidos
- Menos bloqueios de conta
- Mais confiança no Pix

---

## 🔥 PRÓXIMOS PASSOS TÁTICOS (ESTA SEMANA)

### **Prioridade 1: API REST** (2 dias)
- Criar endpoint `/fraud-check`
- Integrar com scripts existentes
- Testar com 100 CNPJs conhecidos

### **Prioridade 2: Fraud Case Study** (1 dia)
- Escolher caso famoso (ex: Bitcoin Banco, Empiricus, etc)
- Rodar análise
- Criar apresentação
- Publicar no LinkedIn

### **Prioridade 3: Simplificar Receita** (1 dia)
- Remover análises mortas (CADE, Lead Scoring)
- Focar template em detecção de fraude
- Relatório HTML simplificado

### **Prioridade 4: TODO-002 e TODO-003** (2 dias)
- Parquet partitioning
- File validation
- Garantir <200ms

---

## 📊 MÉTRICAS DE SUCESSO

### **Fase 1 (MVP de Ataque)**
- ✅ API funcionando
- ✅ Latência <200ms (p95)
- ✅ Fraud Case Study publicado
- ✅ 1 reunião marcada com ValidaPix

### **Fase 2 (Escala)**
- ✅ API em produção (Cloud)
- ✅ 99.9% uptime
- ✅ 1º contrato assinado

### **Fase 3 (Crescimento)**
- ✅ 5 clientes pagantes
- ✅ R$ 50k MRR
- ✅ Produto de monitoramento lançado

---

## 🚨 O QUE NÃO FAZER (ANTI-ROADMAP)

| ❌ NÃO FAZER | Por Quê |
|--------------|---------|
| Implementar dashboard web bonito | Cliente não liga. API é suficiente. |
| ML complexo de inadimplência | Dados insuficientes. Heurísticas vencem agora. |
| Análise de mercado (CADE) | Ciclo longo. Fuja disso. |
| Lead Scoring | Não é nossa praia. |
| Features que não detectam fraude | Foco absoluto em antifraude. |

---

## 🎖️ CONCLUSÃO: A FERRARI NA PISTA CERTA

**Antes**: Ferrari fazendo mudança (generalista)
**Depois**: Ferrari na pista de corrida (antifraude)

**Foco Único**: The Shield (Antifraude Pix para Fintechs)
**Cliente Único**: ValidaPix (depois, subadquirentes e bancos digitais)
**Problema Único**: Detectar fraude em tempo real (<200ms)
**Diferencial Único**: Graph + LLM = Contexto que Serasa não tem

**Próximo Marco**: Reunião com ValidaPix mostrando Fraud Case Study

---

**Data**: 2025-11-23
**Status**: PIVOT APROVADO
**Próximo Review**: Após 1º contrato assinado
