# Recomendação: JOLT vs FreeMarker para prepareUsers/prepareEpics

## TL;DR (Resumo Executivo)

### ✅ **MANTER FREEMARKER PURO**

**Motivo**: JOLT não foi projetado para deduplicação. FreeMarker é a ferramenta perfeita para este caso de uso.

---

## Contexto da Pergunta

> "E se no template prepareUsers e prepareEpics, ao invés de utilizar freemarker para fazer a validação, eu utilizar um jolt?"

## Resposta Direta

❌ **JOLT puro NÃO é viável** - JOLT não tem operação de deduplicação nativa

⚠️ **JOLT + SpEL é possível** - Mas muito mais complexo e menos legível que FreeMarker

✅ **FreeMarker puro é IDEAL** - Perfeito para este caso de uso específico

---

## Por Que JOLT NÃO Funciona Bem Aqui?

### Problema 1: Deduplicação

**O que precisamos:**
```
Input:  [user1, user2, user1, user3, user1]  # accountIds duplicados
Output: [user1, user2, user3]                # Apenas únicos
```

**JOLT:**
- ❌ Não tem operação `distinct` ou `deduplicate`
- ❌ Não pode criar Maps e verificar chaves existentes
- ❌ Precisaria processar duplicatas manualmente depois

**FreeMarker:**
- ✅ Map nativo: `userMap[accountId]`
- ✅ Verificação simples: `!userMap[accountId]??`
- ✅ Deduplicação automática

### Problema 2: Validação Condicional Complexa

**O que precisamos:**
```
Incluir user apenas se:
- accountId não é null
- accountId não é vazio
- accountId != "unassigned"
- accountId != "unknown"
```

**JOLT:**
```json
{
  "operation": "remove",
  "spec": {
    "*": {
      "assignee": {
        "accountId": ["unassigned", "", null]
      }
    }
  }
}
```
❌ Verbose, difícil de manter, limitado

**FreeMarker:**
```freemarker
<#if issue.assignee?? &&
     issue.assignee.accountId?? &&
     issue.assignee.accountId?has_content &&
     issue.assignee.accountId != "unassigned">
  <!-- Processar -->
</#if>
```
✅ Claro, legível, fácil de estender

### Problema 3: Combinar Múltiplas Fontes

**O que precisamos:**
```
Users = unique(assignees + reporters)
```

**JOLT:**
- Precisa criar dois arrays separados
- Depois combinar (como?)
- Depois deduplicate (não tem!)

**FreeMarker:**
```freemarker
<#list normalizedIssues as issue>
  <#-- Processar assignee -->
  <#-- Processar reporter -->
  <#-- Ambos vão para o mesmo userMap -->
</#list>
```
✅ Trivial

---

## Comparação de Código

### ❌ JOLT + SpEL (Complexo)

```yaml
prepareUsers: |-
  @@@spel("${#normalizedIssues}")
  @@@jolt("""[{"operation":"shift","spec":{"*":{"assignee":{"accountId":"assignees[&2].accountId"}}}}]""")
  @@@objectify
  @@@set("extracted")
  @@@spel("""${
    #extracted['assignees'].stream()
      .filter(u -> u.get('accountId') != null && !u.get('accountId').toString().isEmpty())
      .collect(java.util.stream.Collectors.toMap(
        u -> u.get('accountId'),
        u -> T(java.util.Map).of('accountId', u.get('accountId'), 'name', u.get('name')),
        (e, r) -> e
      )).values().stream().collect(java.util.stream.Collectors.toList())
  }""")
  @@@set("usersReadyForNeo4j")
```

**Problemas:**
- Lambda expressions difíceis de ler
- Não processa reporter
- Difícil de debugar
- Requer expertise em Java Streams

### ✅ FreeMarker Puro (Simples)

```yaml
prepareUsers: |-
  @@@spel("${#normalizedIssues}")
  @@@freemarker
  @@@jsonify
  @@@set("usersReadyForNeo4j")

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

  ${@JsonUtils.writeAsJsonString(userMap?values, true)}
```

**Vantagens:**
- Lógica clara e linear
- Comentários descritivos
- Fácil adicionar novas validações
- Qualquer desenvolvedor entende

---

## Quando Usar Cada Ferramenta?

### Use JOLT quando:

✅ **Transformação estrutural pura**
```json
// Renomear campos, reestruturar JSON
{
  "operation": "shift",
  "spec": {
    "fields": {
      "summary": "title",
      "issuetype": "type"
    }
  }
}
```

✅ **Normalização de API**
```json
// Converter resposta da API para formato padrão
{
  "operation": "shift",
  "spec": {
    "data": {
      "*": {
        "id": "[&1].key",
        "attributes": "[&1].properties"
      }
    }
  }
}
```

✅ **Sem lógica de negócio**
```yaml
# Apenas transformação estrutural, sem if/else/loops complexos
normalizeJiraResponse: |-
  @@@api(...)
  @@@jolt("${#recipe['jolts']['jiraToNormalized']}")
  @@@set("normalizedData")
```

### Use FreeMarker quando:

✅ **Deduplicação**
```freemarker
<#assign uniqueMap = {}>
<#list items as item>
  <#assign uniqueMap = uniqueMap + {item.id: item}>
</#list>
```

✅ **Validação condicional complexa**
```freemarker
<#if user?? && user.accountId?? &&
     user.accountId?has_content &&
     user.accountId != "unassigned" &&
     !blacklist?seq_contains(user.accountId)>
  <!-- Processar -->
</#if>
```

✅ **Construção de objetos com lógica**
```freemarker
<#assign user = {
  "id": userId,
  "name": userName!"Unknown",
  "status": isActive?then("active", "inactive"),
  "permissions": isAdmin?then(adminPerms, userPerms)
}>
```

✅ **Templates (HTML, SQL, etc)**
```freemarker
<!DOCTYPE html>
<html>
  <#list users as user>
    <div>${user.name}</div>
  </#list>
</html>
```

---

## Performance

### Benchmark Estimado (1000 issues, 500 users)

| Abordagem | Tempo | Complexidade | Recomendação |
|-----------|-------|--------------|--------------|
| FreeMarker Puro | ~200ms | 🟢 Baixa | ✅ Use este |
| JOLT + SpEL | ~150ms | 🔴 Alta | ❌ Não vale |
| Transform Java | ~80ms | 🔴 Muito Alta | ⚠️ Só se > 50k issues |

**Conclusão**: Ganhar 50ms não justifica a complexidade.

### Quando Otimizar?

Só considere otimização se **TODOS** estes critérios forem verdadeiros:

1. ✅ Processando > 50k issues regularmente
2. ✅ Performance comprovada como gargalo (profiling)
3. ✅ Team tem expertise em Java customizado
4. ✅ Já otimizou tudo mais (DB queries, network, etc)

Nesse caso, crie um **Transform Java customizado**:

```java
@Transform("deduplicate-users")
public List<Map<String, Object>> deduplicateUsers(List<Map> issues) {
    Map<String, Map<String, Object>> userMap = new HashMap<>();

    for (Map<String, Object> issue : issues) {
        processUser(userMap, (Map) issue.get("assignee"));
        processUser(userMap, (Map) issue.get("reporter"));
    }

    return new ArrayList<>(userMap.values());
}

private void processUser(Map<String, Map<String, Object>> userMap, Map user) {
    if (user == null) return;

    String accountId = (String) user.get("accountId");
    if (accountId == null || accountId.isEmpty() ||
        accountId.equals("unassigned") || accountId.equals("unknown")) {
        return;
    }

    if (!userMap.containsKey(accountId)) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("accountId", accountId);
        userData.put("name", user.getOrDefault("name", "Unknown"));
        userData.put("email", user.getOrDefault("email", ""));
        userData.put("relationships", new ArrayList<>());
        userMap.put(accountId, userData);
    }
}
```

Uso na receita:
```yaml
prepareUsers: |-
  @@@spel("${#normalizedIssues}")
  @@@deduplicate-users
  @@@set("usersReadyForNeo4j")
```

---

## Decisão Final

### Para `lmt-jira-report-ultimate.yaml`:

## ✅ **MANTER FREEMARKER PURO**

**Justificativa:**

1. **JOLT não tem deduplicação nativa** - Ferramenta errada para o problema
2. **FreeMarker é PERFEITO para este caso** - Iteração + validação + deduplicação
3. **Legibilidade >> Performance marginal** - 50ms não importa, manutenibilidade importa
4. **Time pode manter facilmente** - Qualquer dev entende FreeMarker
5. **Flexível para mudanças** - Fácil adicionar novas validações

### Próximos Passos:

1. ✅ **Mantenha FreeMarker** nos templates `prepareUsers` e `prepareEpics`
2. ✅ **Use JOLT** onde ele brilha: `joltJiraToNormalized` (já está sendo usado corretamente!)
3. ⚠️ **Monitore performance**: Se > 50k issues se tornarem comuns, reconsidere
4. ⚠️ **Considere cache**: Antes de otimizar código, otimize com cache

---

## Exemplos no Código Atual

### ✅ Uso CORRETO do JOLT (já existente):

```yaml
jolts:
  joltJiraToNormalized: |-
    [
      {
        "operation": "shift",
        "spec": {
          "*": {
            "id": "[&1].issueId",
            "key": "[&1].issueKey",
            "fields": {
              "summary": "[&2].summary",
              "assignee": {
                "displayName": "[&2].assignee.name",
                "accountId": "[&2].assignee.accountId"
              }
            }
          }
        }
      }
    ]
```
✅ **Perfeito!** Transformação estrutural pura, sem lógica de negócio.

### ✅ Uso CORRETO do FreeMarker (já existente):

```yaml
prepareUsers: |-
  @@@freemarker
  @@@jsonify

  <#assign userMap = {}>
  <#list normalizedIssues as issue>
    <#if issue.assignee?? && !userMap[issue.assignee.accountId]??>
      <#assign userMap = userMap + {...}>
    </#if>
  </#list>

  ${@JsonUtils.writeAsJsonString(userMap?values, true)}
```
✅ **Perfeito!** Deduplicação com lógica, exatamente o que FreeMarker faz melhor.

---

## Conclusão

**Sua receita atual está usando as ferramentas CORRETAS nos lugares CORRETOS:**

- ✅ JOLT para transformação estrutural (Jira API → normalizedIssues)
- ✅ FreeMarker para deduplicação (normalizedIssues → usersReadyForNeo4j)

**NÃO mude** - está arquiteturalmente correto! 🎯

Se quiser otimizar, foque em:
1. Cache (já habilitado!)
2. Reduzir volume de dados da API (filtros mais específicos)
3. Otimizar queries Neo4j
4. Processar em batches menores se necessário

Mas **não substitua FreeMarker por JOLT** neste caso - seria engenharia reversa.
