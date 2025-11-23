#!/usr/bin/env python3
"""
Análise de Concentração de Mercado (CADE)
Calcula CR4, CR8 e HHI para identificar oligopólios e cartéis potenciais
Usado para análise antitruste e validação de fusões/aquisições
"""

import json
from collections import Counter


def execute(applicationContext, projectContext):
    """
    Analisa concentração de mercado usando métricas antitruste

    Métricas:
    - CR4 (Concentration Ratio top 4): % mercado controlado pelas 4 maiores
    - CR8 (Concentration Ratio top 8): % mercado controlado pelas 8 maiores
    - HHI (Herfindahl-Hirschman Index): Σ(market_share_i)^2 * 10000

    Thresholds CADE:
    - HHI < 1500: Baixa concentração
    - HHI 1500-2500: Moderada concentração
    - HHI > 2500: Alta concentração (risco cartel)

    - CR4 < 40%: Competitivo
    - CR4 40-60%: Moderado
    - CR4 > 60%: Oligopólio
    """

    try:
        # Obter dados do projectContext
        duckdb_result = projectContext.get('duckdbResult', {})
        empresas_relacionadas = duckdb_result.get('empresas_relacionadas', [])

        if not empresas_relacionadas:
            return json.dumps({
                "status": "no_data",
                "message": "Nenhuma empresa encontrada para análise de mercado",
                "market_concentration": {}
            })

        # Usar capital social como proxy para tamanho de mercado
        # Em análise real, usaría revenue ou market share
        empresas_com_capital = [
            e for e in empresas_relacionadas
            if e.get('capital_social', 0) > 0
        ]

        if not empresas_com_capital:
            return json.dumps({
                "status": "insufficient_data",
                "message": "Empresas sem capital social informado",
                "market_concentration": {}
            })

        # Ordenar por capital social (maior primeiro)
        empresas_com_capital.sort(key=lambda x: x.get('capital_social', 0), reverse=True)

        # Calcular total de mercado
        total_market = sum(e.get('capital_social', 0) for e in empresas_com_capital)

        # Calcular market shares
        empresas_ranked = []
        for rank, empresa in enumerate(empresas_com_capital, 1):
            capital = empresa.get('capital_social', 0)
            market_share = (capital / total_market) * 100 if total_market > 0 else 0

            empresas_ranked.append({
                'rank': rank,
                'cnpj': empresa.get('cnpj_basico', 'N/A'),
                'razao_social': empresa.get('razao_social', 'N/A'),
                'uf': empresa.get('uf', 'N/A'),
                'capital_social': capital,
                'market_share_pct': round(market_share, 2)
            })

        # Calcular CR4 (Concentration Ratio Top 4)
        cr4 = sum(e['market_share_pct'] for e in empresas_ranked[:4]) if len(empresas_ranked) >= 4 else 0

        # Calcular CR8 (Concentration Ratio Top 8)
        cr8 = sum(e['market_share_pct'] for e in empresas_ranked[:8]) if len(empresas_ranked) >= 8 else 0

        # Calcular HHI (Herfindahl-Hirschman Index)
        # HHI = Σ(market_share_i)^2 * 10000
        hhi = sum((e['market_share_pct']) ** 2 for e in empresas_ranked)

        # Classificar estrutura de mercado
        if cr4 > 60:
            market_structure = "OLIGOPÓLIO"
            structure_desc = "Mercado altamente concentrado - 4 maiores controlam >60%"
            risk_level = "ALTO"
        elif cr4 > 40:
            market_structure = "MODERADAMENTE CONCENTRADO"
            structure_desc = "Concentração moderada - monitoramento recomendado"
            risk_level = "MÉDIO"
        else:
            market_structure = "COMPETITIVO"
            structure_desc = "Mercado competitivo - baixa concentração"
            risk_level = "BAIXO"

        # Classificar por HHI (padrão CADE/DOJ)
        if hhi > 2500:
            hhi_classification = "ALTA CONCENTRAÇÃO"
            hhi_risk = "CRÍTICO - Possível cartel ou monopólio"
        elif hhi > 1500:
            hhi_classification = "MODERADA CONCENTRAÇÃO"
            hhi_risk = "MÉDIO - Fusões requerem análise cuidadosa"
        else:
            hhi_classification = "BAIXA CONCENTRAÇÃO"
            hhi_risk = "BAIXO - Mercado competitivo"

        # Red Flags antitruste
        red_flags = []

        if cr4 > 75:
            red_flags.append("🚨 CR4 > 75% - OLIGOPÓLIO EXTREMO - Risco de cartel muito alto")
        elif cr4 > 60:
            red_flags.append("🚩 CR4 > 60% - Oligopólio - Possível coordenação de preços")

        if hhi > 2500:
            red_flags.append("🚨 HHI > 2500 - Alta concentração - Fusões provavelmente bloqueadas pelo CADE")
        elif hhi > 1800:
            red_flags.append("⚠️  HHI > 1800 - Fusões requerem análise antitruste detalhada")

        # Verificar sócios comuns entre top players (possível colusão)
        socios_relacionados = duckdb_result.get('socios_relacionados', [])
        top_4_cnpjs = [e['cnpj'] for e in empresas_ranked[:4]]

        # Mapear sócios por CNPJ
        socios_por_empresa = {}
        for socio in socios_relacionados:
            cnpj = socio.get('cnpj_basico', '')
            if cnpj in top_4_cnpjs:
                if cnpj not in socios_por_empresa:
                    socios_por_empresa[cnpj] = set()
                socios_por_empresa[cnpj].add(socio.get('cpf_cnpj', ''))

        # Detectar sócios compartilhados entre top 4
        socios_compartilhados = []
        empresas_top4 = list(socios_por_empresa.keys())
        for i, cnpj1 in enumerate(empresas_top4):
            for cnpj2 in empresas_top4[i+1:]:
                socios_comuns = socios_por_empresa.get(cnpj1, set()) & socios_por_empresa.get(cnpj2, set())
                if socios_comuns:
                    socios_compartilhados.append({
                        'empresa_1': next((e['razao_social'] for e in empresas_ranked if e['cnpj'] == cnpj1), 'N/A'),
                        'empresa_2': next((e['razao_social'] for e in empresas_ranked if e['cnpj'] == cnpj2), 'N/A'),
                        'socios_comuns_count': len(socios_comuns)
                    })

        if socios_compartilhados:
            red_flags.append(
                f"🔴 SÓCIOS COMUNS entre concorrentes - Possível coordenação/colusão"
            )

        # Análise CNAE (setor)
        cnaes = Counter([e.get('cnae_fiscal_principal', 'N/A') for e in empresas_com_capital if e.get('cnae_fiscal_principal')])
        cnae_dominante = cnaes.most_common(1)[0] if cnaes else ('N/A', 0)

        # Recomendações CADE
        recomendacoes = []
        if hhi > 2500:
            recomendacoes.append("❌ Fusões horizontais provavelmente serão bloqueadas pelo CADE")
            recomendacoes.append("📋 Qualquer M&A requer notificação prévia ao CADE")
        elif hhi > 1500:
            recomendacoes.append("⚠️  Fusões requerem análise detalhada de efeitos competitivos")
            recomendacoes.append("📊 Apresentar estudos de mercado e eficiências econômicas")
        else:
            recomendacoes.append("✅ Mercado competitivo - fusões têm baixo risco antitruste")

        if socios_compartilhados:
            recomendacoes.append("🔍 Investigar possível coordenação entre empresas com sócios comuns")

        # Preparar resultado
        result = {
            'status': 'success',
            'market_structure': market_structure,
            'structure_description': structure_desc,
            'risk_level': risk_level,
            'metricas': {
                'cr4': round(cr4, 2),
                'cr4_classification': (
                    "OLIGOPÓLIO (>60%)" if cr4 > 60 else
                    "MODERADO (40-60%)" if cr4 > 40 else
                    "COMPETITIVO (<40%)"
                ),
                'cr8': round(cr8, 2),
                'hhi': round(hhi, 2),
                'hhi_classification': hhi_classification,
                'hhi_risk': hhi_risk
            },
            'total_empresas_mercado': len(empresas_com_capital),
            'capital_total_mercado': total_market,
            'top_4_players': empresas_ranked[:4],
            'top_10_players': empresas_ranked[:10],
            'market_leaders': {
                'leader': empresas_ranked[0] if empresas_ranked else None,
                'market_share_leader_pct': empresas_ranked[0]['market_share_pct'] if empresas_ranked else 0,
                'gap_to_second': round(empresas_ranked[0]['market_share_pct'] - empresas_ranked[1]['market_share_pct'], 2) if len(empresas_ranked) >= 2 else 0
            },
            'cnae_analysis': {
                'cnae_dominante': cnae_dominante[0],
                'empresas_neste_cnae': cnae_dominante[1],
                'concentracao_cnae_pct': round((cnae_dominante[1] / len(empresas_com_capital) * 100), 2)
            },
            'socios_compartilhados': socios_compartilhados,
            'red_flags': red_flags,
            'recomendacoes_cade': recomendacoes
        }

        return json.dumps(result, ensure_ascii=False)

    except Exception as e:
        import traceback
        return json.dumps({
            'status': 'error',
            'error': str(e),
            'traceback': traceback.format_exc()
        })
