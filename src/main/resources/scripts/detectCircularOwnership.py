#!/usr/bin/env python3
"""
Detecção de Propriedade Circular - Estruturas societárias circulares
Detecta quando: Empresa A controla B, B controla C, C controla A (ciclos)
"""

import json
import networkx as nx


def execute(applicationContext, projectContext):
    """
    Detecta estruturas de propriedade circular usando teoria dos grafos

    Ciclos indicam:
    - Fraude societária
    - Evasão fiscal
    - Ocultação de beneficiário final
    - Estruturas de governança problemáticas
    """

    try:
        # Obter dados do projectContext
        duckdb_result = projectContext.get('duckdbResult', {})
        socios_relacionados = duckdb_result.get('socios_relacionados', [])
        empresas_relacionadas = duckdb_result.get('empresas_relacionadas', [])

        if not socios_relacionados or not empresas_relacionadas:
            return json.dumps({
                "status": "no_data",
                "message": "Dados insuficientes para análise de circularidade",
                "cycles_detected": []
            })

        # Criar grafo direcionado (A → B significa "A é sócio de B")
        G = nx.DiGraph()

        # Mapear CNPJ para nome (para visualização)
        cnpj_to_name = {}
        for empresa in empresas_relacionadas:
            cnpj = empresa.get('cnpj_basico', '')
            nome = empresa.get('razao_social', 'N/A')
            if cnpj:
                cnpj_to_name[cnpj] = nome
                G.add_node(cnpj, nome=nome, tipo='empresa')

        # Adicionar arestas (relacionamentos societários)
        # Apenas sócios PJ (CNPJ com 14 caracteres) podem criar ciclos
        edges_added = 0
        for socio in socios_relacionados:
            cpf_cnpj_socio = socio.get('cpf_cnpj', '')
            cnpj_empresa = socio.get('cnpj_basico', '')

            # Apenas PJ (14 dígitos = CNPJ)
            if cpf_cnpj_socio and len(cpf_cnpj_socio) == 14 and cnpj_empresa:
                # Adicionar nó do sócio se não existir
                if cpf_cnpj_socio not in G:
                    # Tentar encontrar nome da empresa sócia
                    socio_empresa = next(
                        (e for e in empresas_relacionadas if e.get('cnpj_basico') == cpf_cnpj_socio[:8]),
                        None
                    )
                    nome_socio = socio_empresa.get('razao_social', 'N/A') if socio_empresa else 'N/A'
                    cnpj_to_name[cpf_cnpj_socio[:8]] = nome_socio
                    G.add_node(cpf_cnpj_socio[:8], nome=nome_socio, tipo='empresa')

                # Aresta: SOCIO_CNPJ → EMPRESA_CNPJ (sócio controla empresa)
                G.add_edge(cpf_cnpj_socio[:8], cnpj_empresa, qualificacao=socio.get('qualificacao_socio', 'N/A'))
                edges_added += 1

        if edges_added == 0:
            return json.dumps({
                "status": "no_pj_relationships",
                "message": "Nenhuma relação PJ-PJ encontrada (necessário para ciclos)",
                "cycles_detected": []
            })

        # Detectar ciclos simples (simple_cycles retorna TODOS os ciclos)
        try:
            all_cycles = list(nx.simple_cycles(G))
        except:
            all_cycles = []

        # Analisar ciclos detectados
        cycles_analyzed = []
        for cycle in all_cycles[:50]:  # Limitar a 50 ciclos para não sobrecarregar
            cycle_length = len(cycle)

            # Obter nomes das empresas no ciclo
            cycle_empresas = []
            for cnpj in cycle:
                nome = cnpj_to_name.get(cnpj, 'Desconhecida')
                cycle_empresas.append({
                    'cnpj': cnpj,
                    'nome': nome
                })

            # Criar representação do ciclo (A → B → C → A)
            cycle_path = " → ".join([f"{e['nome'][:30]}" for e in cycle_empresas])
            cycle_path += f" → {cycle_empresas[0]['nome'][:30]}"  # Fechar o ciclo

            # Classificar risco baseado no tamanho do ciclo
            if cycle_length == 2:
                risco_nivel = "MÉDIO"
                risco_descricao = "Propriedade cruzada simples (A controla B, B controla A)"
            elif cycle_length == 3:
                risco_nivel = "ALTO"
                risco_descricao = "Triângulo societário (A→B→C→A) - possível ocultação"
            elif cycle_length >= 4:
                risco_nivel = "CRÍTICO"
                risco_descricao = f"Estrutura circular complexa ({cycle_length} níveis) - alta suspeita de fraude"
            else:
                risco_nivel = "BAIXO"
                risco_descricao = "Ciclo unitário (empresa controla a si mesma - erro de dados?)"

            cycles_analyzed.append({
                'cycle_id': len(cycles_analyzed),
                'cycle_length': cycle_length,
                'empresas': cycle_empresas,
                'cycle_path': cycle_path,
                'risco_nivel': risco_nivel,
                'risco_descricao': risco_descricao
            })

        # Ordenar por risco (CRÍTICO > ALTO > MÉDIO)
        risk_order = {"CRÍTICO": 0, "ALTO": 1, "MÉDIO": 2, "BAIXO": 3}
        cycles_analyzed.sort(key=lambda x: (risk_order.get(x['risco_nivel'], 99), -x['cycle_length']))

        # Calcular métricas do grafo
        total_nodes = G.number_of_nodes()
        total_edges = G.number_of_edges()

        # Detectar componentes fortemente conectados (SCCs)
        # SCCs são subgrafos onde todos os nós alcançam todos os outros
        sccs = list(nx.strongly_connected_components(G))
        sccs_com_ciclos = [scc for scc in sccs if len(scc) > 1]

        # Preparar resultado
        result = {
            'status': 'success',
            'total_cycles_detected': len(all_cycles),
            'cycles_analyzed': cycles_analyzed[:20],  # Top 20 mais críticos
            'graph_metrics': {
                'total_empresas': total_nodes,
                'total_relationships': total_edges,
                'strongly_connected_components': len(sccs_com_ciclos),
                'average_cycle_length': round(sum(len(c) for c in all_cycles) / len(all_cycles), 2) if all_cycles else 0
            },
            'risk_summary': {
                'criticos': sum(1 for c in cycles_analyzed if c['risco_nivel'] == 'CRÍTICO'),
                'altos': sum(1 for c in cycles_analyzed if c['risco_nivel'] == 'ALTO'),
                'medios': sum(1 for c in cycles_analyzed if c['risco_nivel'] == 'MÉDIO')
            },
            'recommendation': (
                "🔴 ATENÇÃO: Estruturas circulares detectadas! "
                "Requer análise forense para identificar beneficiário final real."
                if len(all_cycles) > 0 else
                "✅ Nenhuma estrutura circular detectada. Governança corporativa adequada."
            )
        }

        return json.dumps(result, ensure_ascii=False)

    except Exception as e:
        import traceback
        return json.dumps({
            'status': 'error',
            'error': str(e),
            'traceback': traceback.format_exc()
        })
