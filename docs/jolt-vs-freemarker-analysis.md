# JOLT vs FreeMarker - Análise de Viabilidade

## Contexto

Avaliar se é viável substituir FreeMarker por JOLT nos templates `prepareUsers` e `prepareEpics` da receita `lmt-jira-report-ultimate.yaml`.

## Requisitos Funcionais

Os templates precisam:

1. **Extração**: Coletar users de assignee + reporter, epics de epicKey
2. **Deduplicação**: Garantir que cada user/epic apareça apenas uma vez
3. **Validação**: Filtrar valores null, vazios, "unassigned", "unknown"
4. **Construção**: Criar objetos completos com fallbacks
5. **Transformação**: Gerar array final

## Opção 1: JOLT Puro ❌

### Exemplo para Users:

```yaml
prepareUsersJoltOnly: |-
  @@@spel("${#normalizedIssues}")
  @@@jolt("""
  [
    {
      "operation": "shift",
      "spec": {
        "*": {
          "assignee": {
            "accountId": "[&2].assignee.@(1,accountId)",
            "name": "[&2].assignee.@(1,name)",
            "email": "[&2].assignee.@(1,email)"
          },
          "reporter": {
            "accountId": "[&2].reporter.@(1,accountId)",
            "name": "[&2].reporter.@(1,name)",
            "email": "[&2].reporter.@(1,email)"
          }
        }
      }
    },
    {
      "operation": "modify-default-beta",
      "spec": {
        "*": {
          "assignee": {
            "relationships": []
          },
          "reporter": {
            "relationships": []
          }
        }
      }
    }
  ]
  """)
  @@@set("usersWithDuplicates")
```

### Problemas:

❌ **JOLT não tem deduplicação nativa**
- Não há operação JOLT para remover duplicatas por chave
- Precisaríamos processar manualmente depois

❌ **Filtrar valores inválidos é verboso**
- Remover "unassigned", "unknown", null requer lógica complexa
- JOLT não tem operação condicional tipo "if accountId != 'unassigned' then include"

❌ **Combinar assignee + reporter no mesmo array**
- JOLT cria arrays separados por padrão
- Combinar e deduplicate requer múltiplos estágios

### Conclusão: ❌ INVIÁVEL

JOLT puro não consegue fazer deduplicação de forma eficiente.

## Opção 2: Híbrido JOLT + SpEL ⚠️

### Estratégia:

1. **JOLT**: Extrair e normalizar estrutura
2. **SpEL**: Deduplicate usando Java streams
3. **JOLT**: Transformação final (se necessário)

### Exemplo:

```yaml
prepareUsersHybrid: |-
  @@@log("#00FF00Preparing users with JOLT + SpEL...")
  @@@spel("${#normalizedIssues}")

  # Passo 1: JOLT extrai todos users (com duplicatas)
  @@@jolt("""
  [
    {
      "operation": "shift",
      "spec": {
        "*": {
          "assignee": {
            "accountId": "users[].accountId",
            "name": "users[].name",
            "email": "users[].email"
          }
        }
      }
    }
  ]
  """)
  @@@objectify
  @@@set("extractedData")

  # Passo 2: SpEL deduplicate
  @@@spel("""
  ${
    #extractedData['users']
      .stream()
      .filter(u -> u.get('accountId') != null &&
                   !u.get('accountId').toString().isEmpty() &&
                   !u.get('accountId').equals('unassigned') &&
                   !u.get('accountId').equals('unknown'))
      .collect(T(java.util.stream.Collectors).toMap(
        u -> u.get('accountId'),
        u -> T(java.util.Map).of(
          'accountId', u.get('accountId'),
          'name', u.get('name') ?: 'Unknown',
          'email', u.get('email') ?: '',
          'relationships', T(java.util.List).of()
        ),
        (existing, replacement) -> existing
      ))
      .values()
      .stream()
      .collect(T(java.util.stream.Collectors).toList())
  }
  """)
  @@@set("usersReadyForNeo4j")
  @@@log("${'Prepared ' + #usersReadyForNeo4j.size() + ' users'}")
```

### Prós:

✅ **Performance melhor que FreeMarker puro**
- JOLT é muito rápido para transformações estruturais
- SpEL é compilado e otimizado

✅ **Separação de concerns**
- JOLT faz transformação estrutural
- SpEL faz lógica de negócio (deduplicação)

### Contras:

⚠️ **SpEL complexo e difícil de ler**
- Lambda expressions, streams, collectors
- Difícil de debugar

⚠️ **Não suporta reporter facilmente**
- Precisaria de múltiplas passagens de JOLT
- Ou SpEL ainda mais complexo para combinar assignee + reporter

⚠️ **Manutenibilidade**
- Mistura de paradigmas (declarativo + imperativo)
- Requer conhecimento profundo de SpEL

### Conclusão: ⚠️ POSSÍVEL MAS NÃO RECOMENDADO

Tecnicamente viável, mas mais complexo e menos legível que FreeMarker.

## Opção 3: FreeMarker (Atual) ✅

### Código Atual:

```yaml
prepareUsers: |-
  @@@log("#00FF00Preparing users with validated keys...")
  @@@spel("${#normalizedIssues}")
  @@@freemarker
  @@@jsonify
  @@@set("usersReadyForNeo4j")
  @@@log("${'Prepared ' + #usersReadyForNeo4j.size() + ' users'}")

  <#assign users = []>
  <#assign userMap = {}>

  <#list normalizedIssues as issue>
    <#-- Assignee -->
    <#if issue.assignee?? && issue.assignee.accountId?? &&
         issue.assignee.accountId?has_content &&
         issue.assignee.accountId != "unassigned">
      <#if !userMap[issue.assignee.accountId]??>
        <#assign userMap = userMap + {
          issue.assignee.accountId: {
            "accountId": issue.assignee.accountId,
            "name": issue.assignee.name!"Unknown",
            "email": issue.assignee.email!"",
            "relationships": []
          }
        }>
      </#if>
    </#if>

    <#-- Reporter -->
    <#if issue.reporter?? && issue.reporter.accountId?? &&
         issue.reporter.accountId?has_content &&
         issue.reporter.accountId != "unknown">
      <#if !userMap[issue.reporter.accountId]??>
        <#assign userMap = userMap + {
          issue.reporter.accountId: {
            "accountId": issue.reporter.accountId,
            "name": issue.reporter.name!"Unknown",
            "email": issue.reporter.email!"",
            "relationships": []
          }
        }>
      </#if>
    </#if>
  </#list>

  <#list userMap?keys as userId>
    <#assign users = users + [userMap[userId]]>
  </#list>

  ${@JsonUtils.writeAsJsonString(users, true)}
```

### Prós:

✅ **Legibilidade máxima**
- Fácil entender o que está acontecendo
- Comentários claros (Assignee, Reporter)
- Estrutura linear

✅ **Deduplicação trivial**
- Map nativo do FreeMarker
- Verificação simples: `!userMap[key]??`

✅ **Validação clara**
- Condicionais legíveis
- Fácil adicionar novas validações

✅ **Manutenibilidade**
- Qualquer desenvolvedor FreeMarker entende
- Fácil debugar com logs intermediários

✅ **Flexibilidade**
- Fácil adicionar novos campos
- Fácil mudar lógica de validação
- Fácil adicionar transformações

### Contras:

⚠️ **Performance ligeiramente inferior**
- FreeMarker é interpretado (não compilado)
- Mas para volumes < 10k issues, diferença negligenciável

### Conclusão: ✅ RECOMENDADO

FreeMarker é a melhor escolha para este caso de uso.

## Opção 4: JOLT para Extração + FreeMarker para Lógica 🔶

### Estratégia:

Use JOLT apenas para transformação estrutural inicial (extração), depois FreeMarker para lógica de negócio.

### Exemplo:

```yaml
prepareUsers: |-
  @@@log("#00FF00Preparing users...")

  # Passo 1: JOLT extrai estrutura simplificada
  @@@spel("${#normalizedIssues}")
  @@@jolt("""
  [
    {
      "operation": "shift",
      "spec": {
        "*": {
          "assignee": {
            "accountId": "allUsers[].id",
            "name": "allUsers[].name",
            "email": "allUsers[].email",
            "$": "allUsers[].type",
            "@(1,assignee)": "allUsers[].data"
          },
          "reporter": {
            "accountId": "allUsers[].id",
            "name": "allUsers[].name",
            "email": "allUsers[].email",
            "$": "allUsers[].type",
            "@(1,reporter)": "allUsers[].data"
          }
        }
      }
    }
  ]
  """)
  @@@objectify
  @@@set("extractedUsers")

  # Passo 2: FreeMarker deduplica e valida
  @@@freemarker
  @@@jsonify
  @@@set("usersReadyForNeo4j")

  <#assign users = []>
  <#assign userMap = {}>

  <#list extractedUsers.allUsers as user>
    <#if user.id?? && user.id?has_content &&
         user.id != "unassigned" && user.id != "unknown">
      <#if !userMap[user.id]??>
        <#assign userMap = userMap + {
          user.id: {
            "accountId": user.id,
            "name": user.name!"Unknown",
            "email": user.email!"",
            "relationships": []
          }
        }>
      </#if>
    </#if>
  </#list>

  <#list userMap?keys as userId>
    <#assign users = users + [userMap[userId]]>
  </#list>

  ${@JsonUtils.writeAsJsonString(users, true)}
```

### Prós:

✅ **JOLT faz o que faz melhor**: Transformação estrutural
✅ **FreeMarker faz o que faz melhor**: Lógica condicional e deduplicação
✅ **Performance razoável**: JOLT rápido na extração

### Contras:

⚠️ **Mais complexo**: Dois paradigmas
⚠️ **Marginal gain**: FreeMarker puro já é rápido o suficiente
⚠️ **JOLT spec verboso**: Não simplifica muito

### Conclusão: 🔶 OVERKILL

Para este caso, a complexidade adicional não justifica o ganho mínimo de performance.

## Comparação Final

| Aspecto | JOLT Puro | JOLT + SpEL | FreeMarker Puro | JOLT + FreeMarker |
|---------|-----------|-------------|-----------------|-------------------|
| **Deduplicação** | ❌ Impossível | ✅ Possível | ✅ Trivial | ✅ Trivial |
| **Validação** | ❌ Difícil | ⚠️ Verbosa | ✅ Clara | ✅ Clara |
| **Legibilidade** | ⚠️ OK | ❌ Ruim | ✅ Excelente | ⚠️ OK |
| **Performance** | ✅ Rápido | ✅ Rápido | ⚠️ OK | ✅ Rápido |
| **Manutenibilidade** | ⚠️ OK | ❌ Difícil | ✅ Fácil | ⚠️ OK |
| **Flexibilidade** | ❌ Limitada | ⚠️ Média | ✅ Alta | ✅ Alta |
| **Curva de aprendizado** | ⚠️ Média | ❌ Alta | ✅ Baixa | ⚠️ Média |

## Recomendação Final

### ✅ **Manter FreeMarker Puro**

**Razões:**

1. **JOLT não foi projetado para deduplicação**: Este é um caso de uso fora do escopo do JOLT
2. **FreeMarker é PERFEITO para este caso**: Iteração + validação + deduplicação + construção
3. **Legibilidade importa mais que performance marginal**: Para volumes < 10k issues, diferença é < 100ms
4. **Manutenibilidade**: Qualquer desenvolvedor entende FreeMarker, poucos dominam JOLT avançado
5. **Flexibilidade futura**: Fácil adicionar novas validações ou campos

### Quando Usar JOLT?

JOLT é excelente para:

✅ **Transformação estrutural pura**: Renomear campos, reestruturar JSON
✅ **Normalização de API**: Converter respostas de APIs para formato padrão
✅ **Sem lógica de negócio**: Quando não há validação ou deduplicação
✅ **Performance crítica**: Quando processando milhões de registros

### Quando Usar FreeMarker?

FreeMarker é excelente para:

✅ **Lógica de negócio**: Validação, filtros, condicionais complexas
✅ **Deduplicação**: Usando Maps
✅ **Construção de objetos**: Com fallbacks e defaults
✅ **Templates legíveis**: HTML, SQL, JSON com lógica

## Otimização Possível

Se performance for realmente crítica (> 50k issues), considere:

### Usar Transform Java Customizado

```java
@Transform("deduplicate-users")
public Object deduplicateUsers(List<Map<String, Object>> issues) {
    Map<String, Map<String, Object>> userMap = new HashMap<>();

    for (Map<String, Object> issue : issues) {
        Map<String, Object> assignee = (Map<String, Object>) issue.get("assignee");
        if (assignee != null) {
            String accountId = (String) assignee.get("accountId");
            if (isValid(accountId)) {
                userMap.putIfAbsent(accountId, createUser(assignee));
            }
        }

        Map<String, Object> reporter = (Map<String, Object>) issue.get("reporter");
        if (reporter != null) {
            String accountId = (String) reporter.get("accountId");
            if (isValid(accountId)) {
                userMap.putIfAbsent(accountId, createUser(reporter));
            }
        }
    }

    return new ArrayList<>(userMap.values());
}
```

Na receita:
```yaml
prepareUsers: |-
  @@@spel("${#normalizedIssues}")
  @@@deduplicate-users
  @@@set("usersReadyForNeo4j")
```

Mas isso só vale a pena se:
- Processando > 50k issues
- Performance é gargalo comprovado
- Team tem expertise em Java

## Benchmark Estimado

Para 1000 issues com ~500 users únicos:

| Abordagem | Tempo Estimado | Complexidade |
|-----------|---------------|--------------|
| FreeMarker Puro | ~200ms | Baixa |
| JOLT + SpEL | ~150ms | Alta |
| Transform Java | ~80ms | Muito Alta |

**Ganho**: 120ms para complexidade muito maior = ❌ **NÃO VALE A PENA**

Para 50k issues com ~5k users únicos:

| Abordagem | Tempo Estimado | Complexidade |
|-----------|---------------|--------------|
| FreeMarker Puro | ~5s | Baixa |
| JOLT + SpEL | ~3s | Alta |
| Transform Java | ~1s | Muito Alta |

**Ganho**: 4s para complexidade muito maior = ⚠️ **CONSIDERAR SE PERFORMANCE FOR CRÍTICA**

## Conclusão

**Para lmt-jira-report-ultimate.yaml:**

✅ **Manter FreeMarker** nos templates `prepareUsers` e `prepareEpics`

**Motivos:**
1. Volumes típicos (< 5k issues) não justificam otimização
2. Legibilidade e manutenibilidade são mais importantes
3. JOLT não tem deduplicação nativa
4. FreeMarker é mais flexível para futuras mudanças

Se no futuro você tiver volumes massivos (> 50k issues), considere criar um transform Java customizado em vez de JOLT.
