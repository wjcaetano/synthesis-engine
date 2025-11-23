#!/usr/bin/env python3
"""
Lead Scoring Comercial
Ranqueia empresas por potencial comercial para prospecção B2B
Score baseado em capital, porte, situação cadastral e crescimento
"""

import json
from datetime import datetime


def execute(applicationContext, projectContext):
    """
    Calcula Lead Score (0-100) para empresas relacionadas

    Critérios:
    - Capital social (0-30 pontos)
    - Situação cadastral ativa (0-25 pontos)
    - Porte da empresa (0-20 pontos)
    - Indicadores de crescimento (0-25 pontos)

    Output: Lista ranqueada de leads qualificados
    """

    try:
        # Obter dados do projectContext
        duckdb_result = projectContext.get('duckdbResult', {})
        empresas_relacionadas = duckdb_result.get('empresas_relacionadas', [])

        if not empresas_relacionadas:
            return json.dumps({
                "status": "no_data",
                "message": "Nenhuma empresa encontrada para scoring",
                "leads": []
            })

        scored_leads = []

        for empresa in empresas_relacionadas:
            score = 0
            score_breakdown = {}

            # 1. CAPITAL SOCIAL (0-30 pontos)
            capital = empresa.get('capital_social', 0)
            if capital >= 10_000_000:  # >= R$ 10M
                capital_score = 30
                capital_tier = "PREMIUM"
            elif capital >= 1_000_000:  # R$ 1M - 10M
                capital_score = 20
                capital_tier = "ALTO"
            elif capital >= 100_000:  # R$ 100k - 1M
                capital_score = 10
                capital_tier = "MÉDIO"
            else:  # < R$ 100k
                capital_score = 5
                capital_tier = "BAIXO"

            score += capital_score
            score_breakdown['capital'] = {
                'pontos': capital_score,
                'tier': capital_tier,
                'valor': capital
            }

            # 2. SITUAÇÃO CADASTRAL (0-25 pontos)
            situacao = empresa.get('situacao_cadastral', '')
            if situacao == '02':  # Ativa
                situacao_score = 25
                situacao_desc = "ATIVA"
            elif situacao == '08':  # Suspensa
                situacao_score = 10
                situacao_desc = "SUSPENSA (baixa prioridade)"
            else:  # Baixada, Inapta, etc.
                situacao_score = 0
                situacao_desc = "INATIVA (desconsiderar)"

            score += situacao_score
            score_breakdown['situacao'] = {
                'pontos': situacao_score,
                'status': situacao_desc
            }

            # 3. PORTE DA EMPRESA (0-20 pontos)
            porte = empresa.get('porte_empresa', '')
            if porte == '05':  # Grande
                porte_score = 20
                porte_desc = "GRANDE"
            elif porte == '03':  # Média
                porte_score = 15
                porte_desc = "MÉDIA"
            elif porte == '01':  # Pequena
                porte_score = 10
                porte_desc = "PEQUENA"
            else:  # Micro ou não informado
                porte_score = 5
                porte_desc = "MICRO/NÃO INFORMADO"

            score += porte_score
            score_breakdown['porte'] = {
                'pontos': porte_score,
                'descricao': porte_desc
            }

            # 4. INDICADORES DE CRESCIMENTO (0-25 pontos)
            # Baseado em data de início de atividade
            data_inicio = empresa.get('data_inicio_ativ', '')
            growth_score = 0
            growth_profile = ""

            if data_inicio:
                try:
                    # Converter data para datetime
                    if isinstance(data_inicio, str) and len(data_inicio) >= 10:
                        ano_inicio = int(data_inicio[:4])
                        ano_atual = datetime.now().year
                        idade_empresa = ano_atual - ano_inicio

                        if idade_empresa <= 2:  # Startup/Disruptor
                            growth_score = 25
                            growth_profile = "STARTUP (<2 anos) - alto potencial disruptivo"
                        elif idade_empresa <= 5:  # Crescimento
                            growth_score = 20
                            growth_profile = "CRESCIMENTO (2-5 anos)"
                        elif idade_empresa <= 10:  # Consolidada
                            growth_score = 15
                            growth_profile = "CONSOLIDADA (5-10 anos)"
                        else:  # Madura
                            growth_score = 10
                            growth_profile = "MADURA (>10 anos) - estável"
                except:
                    growth_score = 5
                    growth_profile = "Idade não determinada"
            else:
                growth_score = 5
                growth_profile = "Data início não disponível"

            score += growth_score
            score_breakdown['growth'] = {
                'pontos': growth_score,
                'perfil': growth_profile
            }

            # Classificar lead por segmento (A, B, C, D)
            if score >= 70:
                segment = "A"
                priority = "ALTA"
                recommendation = "🎯 Lead premium - contato imediato"
            elif score >= 50:
                segment = "B"
                priority = "MÉDIA-ALTA"
                recommendation = "👍 Lead qualificado - incluir em pipeline"
            elif score >= 30:
                segment = "C"
                priority = "MÉDIA"
                recommendation = "⚠️  Lead moderado - nurturing recomendado"
            else:
                segment = "D"
                priority = "BAIXA"
                recommendation = "❌ Lead baixa prioridade - desconsiderar"

            # Adicionar à lista
            scored_leads.append({
                'cnpj': empresa.get('cnpj_basico', 'N/A'),
                'razao_social': empresa.get('razao_social', 'N/A'),
                'nome_fantasia': empresa.get('nome_fantasia', 'N/A'),
                'uf': empresa.get('uf', 'N/A'),
                'municipio': empresa.get('municipio', 'N/A'),
                'lead_score': score,
                'segment': segment,
                'priority': priority,
                'recommendation': recommendation,
                'score_breakdown': score_breakdown,
                'contato': {
                    'telefone': empresa.get('ddd_telefone_1', 'N/A'),
                    'email': empresa.get('email', 'N/A')
                } if 'ddd_telefone_1' in empresa or 'email' in empresa else None
            })

        # Ordenar por score (maior primeiro)
        scored_leads.sort(key=lambda x: x['lead_score'], reverse=True)

        # Estatísticas
        total_leads = len(scored_leads)
        segment_a = sum(1 for l in scored_leads if l['segment'] == 'A')
        segment_b = sum(1 for l in scored_leads if l['segment'] == 'B')
        segment_c = sum(1 for l in scored_leads if l['segment'] == 'C')
        segment_d = sum(1 for l in scored_leads if l['segment'] == 'D')

        # Preparar resultado
        result = {
            'status': 'success',
            'total_empresas_analisadas': total_leads,
            'estatisticas': {
                'segment_a_count': segment_a,
                'segment_b_count': segment_b,
                'segment_c_count': segment_c,
                'segment_d_count': segment_d,
                'leads_qualificados': segment_a + segment_b,  # A + B
                'taxa_qualificacao_pct': round(((segment_a + segment_b) / total_leads * 100), 2) if total_leads > 0 else 0
            },
            'top_leads': scored_leads[:100],  # Top 100 leads
            'leads_premium': [l for l in scored_leads if l['segment'] == 'A'][:20],  # Top 20 premium
            'recomendacao': (
                f"✅ {segment_a + segment_b} leads qualificados (A+B) identificados. "
                f"Priorizar {segment_a} leads premium (segmento A) para contato imediato."
                if segment_a + segment_b > 0 else
                "⚠️  Nenhum lead qualificado identificado. Revisar critérios de busca."
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
