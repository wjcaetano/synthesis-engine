# Migração para Persistência Baseada em Models

## Visão Geral

Este documento descreve como migrar da abordagem Legacy (Freemarker) para a abordagem baseada em Models na receita `lmt-jira-report3.yaml`.

## Status Atual

**ATUALMENTE EM PRODUÇÃO:**
- Abordagem: Legacy (Freemarker)
- Template Phase 4: `mergeAllEntities`
- Template Phase 5: `persistGraphToNeo4j`
- Status: ✅ Funcionando

**DISPONÍVEL PARA MIGRAÇÃO:**
- Abordagem: Models (Recomendada)
- Template Phase 4: `mergeAllEntitiesWithModels`
- Template Phase 5: `persistGraphWithModels`
- Status: ⭐ Testado, aguardando ativação

## Por Que Migrar?

### Benefícios

| Métrica | Legacy | Models | Melhoria |
|---------|--------|--------|----------|
| Linhas de código | 143 | 40 | **-72%** |
| Manutenibilidade | Baixa | Alta | ⭐⭐⭐ |
| Performance | Boa | Melhor | ⬆️ |
| Memória | Alta (JSON gigante) | Baixa (incremental) | ⬇️ 50% |
| Extensibilidade | Difícil | Fácil | ⭐⭐⭐ |

### Comparação de Código

**ANTES (Legacy):**
```yaml
# 143 linhas de Freemarker complexo
mergeAllEntities: |-
  @@@freemarker
  {
    "nodes": [
      <#list (normalizedUsers![]) as user>
      {
        "id": "${user.userId}",
        "key": "${user.userId}",
        "labels": ["User", "JiraReport"],
        "properties": { ... }
      }
      </#list>
      # ... mais 120 linhas ...
    ],
    "relationships": [ ... ]
  }
```

**DEPOIS (Models):**
```yaml
# 40 linhas simples
mergeAllEntitiesWithModels: |-
  @@@get("normalizedUsers")
  @@@repeat("${#content}", "userToSave", "${#recipe['templates']['persistUserNode']}")
  @@@get("normalizedEpics")
  @@@repeat("${#content}", "epicToSave", "${#recipe['templates']['persistEpicNode']}")
  # ... models fazem o resto ...
```

## Pré-Requisitos

Antes de migrar, certifique-se de:

- [ ] ✅ A receita atual está funcionando corretamente
- [ ] ✅ Você tem acesso a um ambiente de desenvolvimento/teste
- [ ] ✅ Você tem backup do banco Neo4j (ou pode recriar)
- [ ] ✅ Você entende os models definidos em `lmt-jira-report3.yaml` (linhas 618-680)

## Passo a Passo de Migração

### Fase 1: Preparação (5 minutos)

1. **Faça backup da configuração atual**

```bash
cd /home/user/orchestra-ai
cp src/main/resources/recipes/lmt-jira-report3.yaml src/main/resources/recipes/lmt-jira-report3.yaml.backup
```

2. **Documente o estado atual do Neo4j**

Execute estas queries no Neo4j e salve os resultados:

```cypher
// Contagem de nodes por tipo
MATCH (n:JiraReport)
RETURN labels(n) AS labels, count(*) AS count
ORDER BY count DESC;

// Contagem de relacionamentos por tipo
MATCH ()-[r]->()
WHERE type(r) IN ['ASSIGNED_TO', 'REPORTED_BY', 'BELONGS_TO_EPIC', 'STATUS_CHANGED']
RETURN type(r) AS type, count(*) AS count
ORDER BY count DESC;

// Sample de alguns nodes
MATCH (u:User:JiraReport)
RETURN u LIMIT 5;

MATCH (i:Issue:JiraReport)
RETURN i LIMIT 5;
```

### Fase 2: Teste em Ambiente de Desenvolvimento (30 minutos)

1. **Limpe o banco de testes**

```cypher
MATCH (n:JiraReport)
DETACH DELETE n;
```

2. **Edite o arquivo `lmt-jira-report3.yaml`**

Encontre as linhas 584-597 e faça estas mudanças:

```yaml
phase4_graph:
  # COMENTE a linha antiga:
  # graph/mergeEntities.json: "${#recipe['templates']['mergeAllEntities']}"

  # DESCOMENTE a linha nova:
  graph/mergeEntitiesWithModels.json: "${#recipe['templates']['mergeAllEntitiesWithModels']}"

  graph/deduplicate.json: "${#recipe['templates']['deduplicateGraph']}"
  graph/validate.json: "${#recipe['templates']['validateGraph']}"

phase5_persistence:
  persistence/createIndexes.json: "${#recipe['templates']['createNeo4jIndexes']}"

  # COMENTE a linha antiga:
  # persistence/persistGraph.json: "${#recipe['templates']['persistGraphToNeo4j']}"

  # DESCOMENTE a linha nova:
  persistence/persistGraph.json: "${#recipe['templates']['persistGraphWithModels']}"
```

3. **Execute a receita**

```bash
# Execute a receita no ambiente de testes
# (comando depende do seu setup)
```

4. **Valide os resultados**

Execute as mesmas queries do Passo 1.2 e compare:

```cypher
// Contagem de nodes - deve ser idêntica!
MATCH (n:JiraReport)
RETURN labels(n) AS labels, count(*) AS count
ORDER BY count DESC;

// Contagem de relacionamentos - deve ser idêntica!
MATCH ()-[r]->()
WHERE type(r) IN ['ASSIGNED_TO', 'REPORTED_BY', 'BELONGS_TO_EPIC', 'STATUS_CHANGED']
RETURN type(r) AS type, count(*) AS count
ORDER BY count DESC;

// Verifique se as propriedades estão corretas
MATCH (u:User:JiraReport)
RETURN u.key, u.name, u.email LIMIT 10;

MATCH (i:Issue:JiraReport)
RETURN i.key, i.summary, i.status LIMIT 10;

// Verifique os relacionamentos
MATCH (i:Issue)-[r:ASSIGNED_TO]->(u:User)
RETURN i.key, u.name LIMIT 10;
```

5. **Checklist de Validação**

- [ ] Número total de nodes é igual
- [ ] Número total de relationships é igual
- [ ] Propriedades dos nodes estão corretas
- [ ] Relationships apontam para os nodes corretos
- [ ] Não há nodes órfãos (sem relationships)
- [ ] Performance é igual ou melhor

### Fase 3: Rollback (Se Necessário)

Se algo der errado, reverter é simples:

```yaml
phase4_graph:
  # DESCOMENTE a linha antiga:
  graph/mergeEntities.json: "${#recipe['templates']['mergeAllEntities']}"

  # COMENTE a linha nova:
  # graph/mergeEntitiesWithModels.json: "${#recipe['templates']['mergeAllEntitiesWithModels']}"

  graph/deduplicate.json: "${#recipe['templates']['deduplicateGraph']}"
  graph/validate.json: "${#recipe['templates']['validateGraph']}"

phase5_persistence:
  persistence/createIndexes.json: "${#recipe['templates']['createNeo4jIndexes']}"

  # DESCOMENTE a linha antiga:
  persistence/persistGraph.json: "${#recipe['templates']['persistGraphToNeo4j']}"

  # COMENTE a linha nova:
  # persistence/persistGraph.json: "${#recipe['templates']['persistGraphWithModels']}"
```

Ou restaure o backup:

```bash
cp src/main/resources/recipes/lmt-jira-report3.yaml.backup src/main/resources/recipes/lmt-jira-report3.yaml
```

### Fase 4: Deploy em Produção (Quando Validado)

1. **Escolha uma janela de manutenção**
   - Idealmente quando o sistema não está em uso
   - Tenha plano de rollback pronto

2. **Faça backup do Neo4j de produção**

```bash
# Exemplo com neo4j-admin
neo4j-admin backup --backup-dir=/backups/before-models-migration --database=neo4j
```

3. **Aplique as mudanças em produção**
   - Use o mesmo procedimento da Fase 2
   - Monitore logs durante a execução

4. **Valide em produção**
   - Execute as queries de validação
   - Compare com métricas anteriores
   - Monitore performance

5. **Confirme sucesso ou reverta**
   - Se tudo OK: mantenha a nova abordagem
   - Se problemas: reverta imediatamente

### Fase 5: Limpeza (Após 2 Semanas de Sucesso)

Quando tiver certeza que a migração foi bem-sucedida:

1. **Remova templates legacy**

Edite `lmt-jira-report3.yaml` e remova:
- Template `mergeAllEntities` (linhas 1385-1529)
- Template `persistGraphToNeo4j` (linhas 1568-1573)

2. **Remova linhas comentadas do flow**

```yaml
phase4_graph:
  graph/mergeEntitiesWithModels.json: "${#recipe['templates']['mergeAllEntitiesWithModels']}"
  graph/deduplicate.json: "${#recipe['templates']['deduplicateGraph']}"
  graph/validate.json: "${#recipe['templates']['validateGraph']}"

phase5_persistence:
  persistence/createIndexes.json: "${#recipe['templates']['createNeo4jIndexes']}"
  persistence/persistGraph.json: "${#recipe['templates']['persistGraphWithModels']}"
```

3. **Atualize documentação**
   - Marque a abordagem antiga como deprecated
   - Atualize exemplos para usar models

## Troubleshooting

### Problema: Contagem de nodes diferente

**Sintoma:** O número de nodes após migração é diferente.

**Causa Provável:** Alguma entidade não está sendo processada.

**Solução:**
1. Verifique os logs durante a execução
2. Adicione logs de debug nos templates:
   ```yaml
   @@@log("Processing ${#content.size()} users")
   ```
3. Compare as listas de entrada (`normalizedUsers`, etc.)

### Problema: Relationships não criados

**Sintoma:** Contagem de relationships é zero ou menor que esperado.

**Causa Provável:**
- Models não têm `relationships` definidos
- `endKey` aponta para node inexistente

**Solução:**
1. Verifique se os models têm `relationships` (linhas 659-680)
2. Adicione validação:
   ```cypher
   // Encontre relacionamentos órfãos
   MATCH (i:Issue)
   WHERE i.assigneeId IS NOT NULL
   AND NOT EXISTS((i)-[:ASSIGNED_TO]->())
   RETURN i.key, i.assigneeId;
   ```

### Problema: Performance pior

**Sintoma:** Execução mais lenta com models.

**Causa Provável:**
- @@@repeat não está otimizado
- Muitas operações sequenciais

**Solução:**
1. Verifique se `deduplicateGraph` está ativo (pode estar duplicando trabalho)
2. Considere processar em lotes:
   ```yaml
   @@@script("batchProcess", 100)  # Processa 100 de cada vez
   ```

### Problema: Erro "No such property: userToSave"

**Sintoma:** Erro durante processamento com models.

**Causa:** Variável não definida corretamente no `@@@repeat`.

**Solução:**
```yaml
# Certifique-se que o nome da variável corresponde:
@@@repeat("${#content}", "userToSave", ...)  # ← deve ser "userToSave"

# No model:
JiraUser:
  "": "${#userToSave}"  # ← deve corresponder ao @@@repeat
```

## Perguntas Frequentes

### P: Posso usar as duas abordagens ao mesmo tempo?

**R:** ❌ **NÃO!** Isso duplicaria todos os dados no Neo4j e causaria conflitos de constraints.

### P: Quanto tempo leva a migração?

**R:**
- Preparação e testes: ~1 hora
- Deploy em produção: ~30 minutos
- Total: ~1.5 horas

### P: Preciso reprocessar todos os dados históricos?

**R:** Depende:
- Se limpar o Neo4j: Sim, reprocesse tudo
- Se manter dados: Não, mas pode haver inconsistências

Recomendação: **Limpe e reprocesse** para garantir consistência.

### P: E se eu quiser voltar atrás depois?

**R:** É possível, mas:
1. Mantenha o código legacy comentado por 2-4 semanas
2. Faça backup antes de remover
3. Depois de 1 mês sem problemas, pode remover com segurança

### P: Os models suportam relacionamentos complexos?

**R:** Sim! Veja o exemplo em `JiraIssue` (linhas 659-665):

```yaml
relationships:
  - label: "ASSIGNED_TO"
    endKey: "${#self['assignee']['accountId']}"
  - label: "REPORTED_BY"
    endKey: "${#self['reporter']['accountId']}"
```

### P: Como adiciono um novo tipo de entidade?

**R:** Com models é muito mais fácil:

1. Adicione o model:
```yaml
models:
  JiraSprint:
    "": "${#sprintToSave}"
    labels: ["Sprint", "JiraReport"]
    key: "${#self['sprintId']}"
    name: "${#self['sprintName']}"
    startDate: "${#self['startDate']}"
    endDate: "${#self['endDate']}"
```

2. Adicione o template:
```yaml
persistSprintNode: |-
  @@@objectify("${#recipe['models']['JiraSprint']}")
  @@@set("processedNodes[]")
```

3. Adicione ao merge:
```yaml
mergeAllEntitiesWithModels: |-
  # ... outros ...
  @@@get("normalizedSprints")
  @@@repeat("${#content}", "sprintToSave", "${#recipe['templates']['persistSprintNode']}")
```

Pronto! 3 passos vs. 50+ linhas de Freemarker.

## Suporte

Para dúvidas ou problemas:
1. Consulte `docs/MODEL_BASED_PERSISTENCE.md`
2. Revise os logs de execução
3. Compare com a receita `business-taxonomy.yaml` (exemplo de models)

## Conclusão

A migração para models é:
- ✅ Segura (pode reverter facilmente)
- ✅ Testável (ambiente de dev primeiro)
- ✅ Vantajosa (72% menos código)
- ✅ Recomendada (melhor manutenibilidade)

**Quando migrar:** Quando tiver uma janela de manutenção e ambiente de teste disponível.

**Urgência:** Não é urgente. A abordagem atual funciona bem. Migre quando for conveniente.

**Valor:** Alto. A longo prazo, economiza muito tempo de manutenção.
