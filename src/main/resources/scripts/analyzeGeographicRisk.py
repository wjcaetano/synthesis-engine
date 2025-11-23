#!/usr/bin/env python3
"""
Análise de Concentração de Risco Geográfico
Avalia se empresas relacionadas estão concentradas em regiões de alto risco
Usa Herfindahl-Hirschman Index (HHI) para medir concentração
"""

import json
from collections import Counter


def execute(applicationContext, projectContext):
    """
    Analisa concentração geográfica de empresas relacionadas

    Métricas:
    - HHI (Herfindahl Index): 0-1 onde 1 = máxima concentração
    - Distribuição por UF e município
    - Identificação de concentração em áreas de risco
    """

    try:
        # Obter dados do projectContext
        duckdb_result = projectContext.get('duckdbResult', {})
        empresas_relacionadas = duckdb_result.get('empresas_relacionadas', [])

        if not empresas_relacionadas:
            return json.dumps({
                "status": "no_data",
                "message": "Nenhuma empresa encontrada para análise geográfica",
                "geographic_risk": {}
            })

        # Extrair distribuição por UF
        ufs = [e.get('uf', 'N/A') for e in empresas_relacionadas if e.get('uf')]
        uf_counts = Counter(ufs)
        total_empresas = len(ufs)

        # Extrair distribuição por município
        municipios = [
            (e.get('uf', 'N/A'), e.get('municipio', 'N/A'))
            for e in empresas_relacionadas
            if e.get('municipio')
        ]
        municipio_counts = Counter(municipios)

        # Calcular HHI por UF
        # HHI = Σ(market_share_i)^2
        # Varia de 0 (competição perfeita) a 1 (monopólio)
        hhi_uf = 0
        uf_distribution = []

        for uf, count in uf_counts.most_common():
            market_share = count / total_empresas
            hhi_uf += market_share ** 2

            uf_distribution.append({
                'uf': uf,
                'total_empresas': count,
                'percentual': round(market_share * 100, 2),
                'market_share': round(market_share, 4)
            })

        # Calcular HHI por município
        hhi_municipio = 0
        municipio_distribution = []

        for (uf, municipio), count in municipio_counts.most_common(20):  # Top 20 municípios
            market_share = count / total_empresas
            hhi_municipio += market_share ** 2

            municipio_distribution.append({
                'uf': uf,
                'municipio': municipio,
                'total_empresas': count,
                'percentual': round(market_share * 100, 2)
            })

        # Classificar risco baseado no HHI
        # HHI > 0.25 = Alta concentração (risco)
        # HHI 0.15-0.25 = Moderada concentração
        # HHI < 0.15 = Baixa concentração (diversificado)

        if hhi_uf > 0.25:
            risco_nivel = "ALTO"
            risco_descricao = "Alta concentração geográfica - risco de exposição regional"
        elif hhi_uf > 0.15:
            risco_nivel = "MÉDIO"
            risco_descricao = "Moderada concentração geográfica"
        else:
            risco_nivel = "BAIXO"
            risco_descricao = "Boa diversificação geográfica"

        # Identificar estado dominante
        estado_dominante = uf_counts.most_common(1)[0] if uf_counts else (None, 0)
        estado_dominante_uf = estado_dominante[0]
        estado_dominante_count = estado_dominante[1]
        estado_dominante_pct = (estado_dominante_count / total_empresas * 100) if total_empresas > 0 else 0

        # Identificar município dominante
        municipio_dominante = municipio_counts.most_common(1)[0] if municipio_counts else ((None, None), 0)
        municipio_dominante_info = municipio_dominante[0]
        municipio_dominante_count = municipio_dominante[1]
        municipio_dominante_pct = (municipio_dominante_count / total_empresas * 100) if total_empresas > 0 else 0

        # Red Flags
        red_flags = []

        if estado_dominante_pct > 60:
            red_flags.append(f"🚩 {estado_dominante_pct:.1f}% das empresas concentradas em {estado_dominante_uf}")

        if municipio_dominante_pct > 40:
            red_flags.append(
                f"🚩 {municipio_dominante_pct:.1f}% das empresas em um único município: "
                f"{municipio_dominante_info[1]}/{municipio_dominante_info[0]}"
            )

        if hhi_uf > 0.5:
            red_flags.append("🔴 Concentração CRÍTICA - alta vulnerabilidade a choques regionais")

        # Diversificação (total de UFs diferentes)
        total_ufs = len(uf_counts)
        if total_ufs == 1:
            red_flags.append("⚠️  Todas as empresas no mesmo estado - zero diversificação")
        elif total_ufs <= 3:
            red_flags.append(f"⚠️  Presença em apenas {total_ufs} estados")

        # Calcular índice de diversificação (inverso do HHI)
        # 0 = concentração total, 1 = diversificação perfeita
        diversification_index = 1 - hhi_uf

        # Recomendações
        recomendacoes = []
        if hhi_uf > 0.25:
            recomendacoes.append("Considerar expansão para outras regiões para mitigar risco geográfico")
        if municipio_dominante_pct > 50:
            recomendacoes.append("Avaliar exposição a riscos locais (economia, política, infraestrutura)")
        if total_ufs < 5:
            recomendacoes.append("Aumentar presença geográfica em diferentes estados")

        # Preparar resultado
        result = {
            'status': 'success',
            'risco_nivel': risco_nivel,
            'risco_descricao': risco_descricao,
            'hhi': {
                'uf': round(hhi_uf, 4),
                'municipio': round(hhi_municipio, 4),
                'interpretacao': (
                    "ALTA CONCENTRAÇÃO (>0.25)" if hhi_uf > 0.25 else
                    "MODERADA (0.15-0.25)" if hhi_uf > 0.15 else
                    "BAIXA CONCENTRAÇÃO (<0.15)"
                )
            },
            'diversification_index': round(diversification_index, 4),
            'metricas': {
                'total_empresas': total_empresas,
                'total_ufs': total_ufs,
                'total_municipios': len(municipio_counts),
                'estado_dominante': estado_dominante_uf,
                'percentual_estado_dominante': round(estado_dominante_pct, 2),
                'municipio_dominante': f"{municipio_dominante_info[1]}/{municipio_dominante_info[0]}",
                'percentual_municipio_dominante': round(municipio_dominante_pct, 2)
            },
            'distribuicao_uf': uf_distribution,
            'distribuicao_municipio': municipio_distribution,
            'red_flags': red_flags,
            'recomendacoes': recomendacoes
        }

        return json.dumps(result, ensure_ascii=False)

    except Exception as e:
        import traceback
        return json.dumps({
            'status': 'error',
            'error': str(e),
            'traceback': traceback.format_exc()
        })
