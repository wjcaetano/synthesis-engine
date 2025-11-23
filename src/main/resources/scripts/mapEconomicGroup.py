#!/usr/bin/env python3
"""
Mapeamento de Grupos Econômicos
Identifica holdings, subsidiárias e empresas coligadas
Usa BFS (Breadth-First Search) para explorar rede societária
"""

import json
import networkx as nx
from collections import defaultdict, Counter


def execute(applicationContext, projectContext):
    """
    Mapeia grupo econômico completo a partir da empresa alvo

    Identifica:
    - Holdings (empresas controladoras)
    - Subsidiárias (empresas controladas)
    - Coligadas (empresas com sócios em comum)
    - Estrutura hierárquica do grupo
    """

    try:
        # Obter dados do projectContext
        duckdb_result = projectContext.get('duckdbResult', {})
        empresa_alvo = duckdb_result.get('empresa_alvo', {})
        empresas_relacionadas = duckdb_result.get('empresas_relacionadas', [])
        socios_relacionados = duckdb_result.get('socios_relacionados', [])

        if not empresa_alvo or not empresas_relacionadas:
            return json.dumps({
                "status": "no_data",
                "message": "Dados insuficientes para mapeamento de grupo econômico",
                "economic_group": {}
            })

        cnpj_alvo = duckdb_result.get('cnpj_consultado', '')

        # Criar grafo direcionado para relacionamentos societários
        G = nx.DiGraph()

        # Mapear CNPJ para dados completos da empresa
        cnpj_to_empresa = {}
        for empresa in empresas_relacionadas:
            cnpj = empresa.get('cnpj_basico', '')
            if cnpj:
                cnpj_to_empresa[cnpj] = empresa
                G.add_node(cnpj, **empresa)

        # Adicionar empresa alvo se não estiver na lista
        if cnpj_alvo not in cnpj_to_empresa:
            cnpj_to_empresa[cnpj_alvo] = empresa_alvo
            G.add_node(cnpj_alvo, **empresa_alvo)

        # Construir grafo de relacionamentos
        # Aresta: SOCIO → EMPRESA (sócio participa da empresa)
        for socio in socios_relacionados:
            cpf_cnpj_socio = socio.get('cpf_cnpj', '')
            cnpj_empresa = socio.get('cnpj_basico', '')

            if not cpf_cnpj_socio or not cnpj_empresa:
                continue

            # Adicionar sócio como nó (pode ser PF ou PJ)
            if cpf_cnpj_socio not in G:
                is_pj = len(cpf_cnpj_socio) == 14
                tipo = 'empresa' if is_pj else 'pessoa_fisica'

                # Se for PJ, tentar pegar dados da empresa
                if is_pj and cpf_cnpj_socio[:8] in cnpj_to_empresa:
                    empresa_socia = cnpj_to_empresa[cpf_cnpj_socio[:8]]
                    G.add_node(cpf_cnpj_socio[:8], tipo=tipo, **empresa_socia)
                else:
                    G.add_node(cpf_cnpj_socio[:8] if is_pj else cpf_cnpj_socio,
                              tipo=tipo,
                              nome=socio.get('nome_socio', 'N/A'))

            # Aresta: SÓCIO → EMPRESA
            socio_id = cpf_cnpj_socio[:8] if len(cpf_cnpj_socio) == 14 else cpf_cnpj_socio
            G.add_edge(socio_id, cnpj_empresa,
                      qualificacao=socio.get('qualificacao_socio', 'N/A'),
                      tipo='participacao')

        # Identificar níveis hierárquicos usando BFS a partir da empresa alvo
        # Nível 0: Empresa alvo
        # Nível -1, -2, ...: Holdings (quem controla a empresa alvo)
        # Nível +1, +2, ...: Subsidiárias (empresas controladas pela alvo)

        niveis = {cnpj_alvo: 0}
        visited = {cnpj_alvo}

        # BFS para cima (predecessores = holdings)
        queue_up = [(cnpj_alvo, 0)]
        while queue_up:
            current, level = queue_up.pop(0)
            # Predecessores = sócios que controlam esta empresa
            for predecessor in G.predecessors(current):
                if predecessor not in visited:
                    visited.add(predecessor)
                    niveis[predecessor] = level - 1
                    # Só continuar BFS se for PJ (empresa)
                    if G.nodes[predecessor].get('tipo') == 'empresa':
                        queue_up.append((predecessor, level - 1))

        # BFS para baixo (sucessores = subsidiárias)
        visited_down = {cnpj_alvo}
        queue_down = [(cnpj_alvo, 0)]
        while queue_down:
            current, level = queue_down.pop(0)
            # Sucessores = empresas onde este nó é sócio
            for successor in G.successors(current):
                if successor not in visited_down and G.nodes[successor].get('tipo') == 'empresa':
                    visited_down.add(successor)
                    niveis[successor] = level + 1
                    queue_down.append((successor, level + 1))

        # Organizar empresas por nível
        empresas_por_nivel = defaultdict(list)
        for cnpj, nivel in niveis.items():
            node_data = G.nodes[cnpj]
            if node_data.get('tipo') == 'empresa' or cnpj == cnpj_alvo:
                empresas_por_nivel[nivel].append({
                    'cnpj': cnpj,
                    'razao_social': node_data.get('razao_social', node_data.get('nome', 'N/A')),
                    'uf': node_data.get('uf', 'N/A'),
                    'situacao': node_data.get('situacao_cadastral', 'N/A'),
                    'capital_social': node_data.get('capital_social', 0),
                    'nivel': nivel
                })

        # Contar empresas por categoria
        holdings = empresas_por_nivel.get(-1, []) + empresas_por_nivel.get(-2, []) + empresas_por_nivel.get(-3, [])
        subsidiarias = empresas_por_nivel.get(1, []) + empresas_por_nivel.get(2, []) + empresas_por_nivel.get(3, [])

        # Calcular métricas do grupo
        todas_empresas_grupo = [e for nivel_empresas in empresas_por_nivel.values() for e in nivel_empresas]

        capital_total = sum(e['capital_social'] for e in todas_empresas_grupo)
        empresas_ativas = sum(1 for e in todas_empresas_grupo if e['situacao'] == '02')

        # Distribuição geográfica do grupo
        ufs_grupo = [e['uf'] for e in todas_empresas_grupo if e['uf'] != 'N/A']
        distribuicao_uf = Counter(ufs_grupo)

        # Identificar principais sócios PF do grupo (pessoas chave)
        principais_socios_pf = []
        socios_pf_counter = Counter()

        for socio in socios_relacionados:
            cpf_cnpj = socio.get('cpf_cnpj', '')
            if cpf_cnpj and len(cpf_cnpj) == 11:  # PF
                socios_pf_counter[cpf_cnpj] += 1

        for cpf, count in socios_pf_counter.most_common(10):
            # Encontrar nome do sócio
            socio_info = next((s for s in socios_relacionados if s.get('cpf_cnpj') == cpf), None)
            if socio_info:
                principais_socios_pf.append({
                    'cpf': cpf,
                    'nome': socio_info.get('nome_socio', 'N/A'),
                    'participacoes': count
                })

        # Preparar resultado
        result = {
            'status': 'success',
            'empresa_alvo': {
                'cnpj': cnpj_alvo,
                'razao_social': empresa_alvo.get('razao_social', 'N/A')
            },
            'estrutura_grupo': {
                'total_empresas': len(todas_empresas_grupo),
                'holdings': len(holdings),
                'subsidiarias': len(subsidiarias),
                'niveis_hierarquicos': len(empresas_por_nivel),
                'profundidade_maxima': max(abs(n) for n in empresas_por_nivel.keys()) if empresas_por_nivel else 0
            },
            'metricas_consolidadas': {
                'capital_total': capital_total,
                'empresas_ativas': empresas_ativas,
                'empresas_inativas': len(todas_empresas_grupo) - empresas_ativas,
                'taxa_atividade_pct': round((empresas_ativas / len(todas_empresas_grupo) * 100), 2) if todas_empresas_grupo else 0
            },
            'distribuicao_geografica': [
                {'uf': uf, 'total_empresas': count}
                for uf, count in distribuicao_uf.most_common()
            ],
            'holdings': holdings[:10],  # Top 10 holdings
            'subsidiarias': subsidiarias[:20],  # Top 20 subsidiárias
            'principais_socios_pf': principais_socios_pf,
            'hierarquia_completa': dict(sorted(empresas_por_nivel.items()))
        }

        return json.dumps(result, ensure_ascii=False)

    except Exception as e:
        import traceback
        return json.dumps({
            'status': 'error',
            'error': str(e),
            'traceback': traceback.format_exc()
        })
