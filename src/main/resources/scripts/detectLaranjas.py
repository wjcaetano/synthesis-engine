#!/usr/bin/env python3
"""
Detecção de "Laranjas" - Pessoas que emprestam CPF para empresas
Identifica padrões suspeitos de uso de CPF em múltiplas empresas
"""

import json
import duckdb


def execute(applicationContext, projectContext):
    """
    Analisa sócios PF (CPF) que aparecem em múltiplas empresas
    buscando padrões de "laranja" (CPF emprestado)

    Red Flags:
    - Múltiplas empresas com CPF
    - Alta taxa de empresas inativas/baixadas
    - Capital social muito baixo
    - Mesma qualificação em todas empresas
    - Empresas no mesmo endereço
    """

    try:
        # Obter dados do projectContext (vem do duckdbQueryOptimized)
        duckdb_result = projectContext.get('duckdbResult', {})
        socios_relacionados = duckdb_result.get('socios_relacionados', [])
        empresas_relacionadas = duckdb_result.get('empresas_relacionadas', [])

        if not socios_relacionados:
            return json.dumps({
                "status": "no_data",
                "message": "Nenhum sócio encontrado para análise",
                "laranjas_detectados": []
            })

        # Conectar ao DuckDB para análises
        parquet_path = projectContext.get('$api', {}).get('configs', {}).get('vars', {}).get('parquetPath', 'data/rfb')

        con = duckdb.connect(database=':memory:')

        # Análise de "Laranjas"
        laranjas_detectados = []

        # Agrupar sócios por CPF (pessoas físicas = 11 caracteres)
        socios_pf = {}
        for socio in socios_relacionados:
            cpf_cnpj = socio.get('cpf_cnpj', '')
            # CPF tem 11 dígitos, CNPJ tem 14
            if cpf_cnpj and len(cpf_cnpj) == 11:
                if cpf_cnpj not in socios_pf:
                    socios_pf[cpf_cnpj] = {
                        'cpf': cpf_cnpj,
                        'nome': socio.get('nome_socio', 'N/A'),
                        'empresas': [],
                        'qualificacoes': set()
                    }

                # Buscar dados da empresa relacionada
                empresa_cnpj = socio.get('cnpj_basico', '')
                empresa_info = next(
                    (e for e in empresas_relacionadas if e.get('cnpj_basico') == empresa_cnpj),
                    None
                )

                if empresa_info:
                    socios_pf[cpf_cnpj]['empresas'].append({
                        'cnpj': empresa_cnpj,
                        'razao_social': empresa_info.get('razao_social', 'N/A'),
                        'situacao': empresa_info.get('situacao_cadastral', 'N/A'),
                        'capital_social': empresa_info.get('capital_social', 0),
                        'uf': empresa_info.get('uf', 'N/A'),
                        'municipio': empresa_info.get('municipio', 'N/A')
                    })
                    socios_pf[cpf_cnpj]['qualificacoes'].add(socio.get('qualificacao_socio', 'N/A'))

        # Analisar cada sócio PF buscando padrões de "laranja"
        for cpf, dados in socios_pf.items():
            total_empresas = len(dados['empresas'])

            # Filtro 1: Precisa ter pelo menos 3 empresas para ser suspeito
            if total_empresas < 3:
                continue

            # Calcular métricas
            empresas_inativas = sum(1 for e in dados['empresas'] if e['situacao'] != '02')
            taxa_inativas = (empresas_inativas / total_empresas) * 100

            capital_medio = sum(e['capital_social'] for e in dados['empresas']) / total_empresas

            # Verificar se todas empresas têm mesma qualificação (suspeito)
            mesma_qualificacao = len(dados['qualificacoes']) == 1

            # Verificar concentração geográfica (mesmo município)
            municipios = [e['municipio'] for e in dados['empresas']]
            municipio_dominante = max(set(municipios), key=municipios.count) if municipios else None
            taxa_mesmo_municipio = (municipios.count(municipio_dominante) / total_empresas) * 100 if municipio_dominante else 0

            # SCORING DE RISCO (0-100)
            risco_score = 0
            red_flags = []

            # Red Flag 1: Muitas empresas (peso: 20)
            if total_empresas >= 10:
                risco_score += 20
                red_flags.append(f"Sócio em {total_empresas} empresas (alto volume)")
            elif total_empresas >= 5:
                risco_score += 10
                red_flags.append(f"Sócio em {total_empresas} empresas")

            # Red Flag 2: Alta taxa de inativas (peso: 30)
            if taxa_inativas > 70:
                risco_score += 30
                red_flags.append(f"{taxa_inativas:.0f}% das empresas estão inativas/baixadas")
            elif taxa_inativas > 40:
                risco_score += 15
                red_flags.append(f"{taxa_inativas:.0f}% das empresas estão inativas")

            # Red Flag 3: Capital social muito baixo (peso: 20)
            if capital_medio < 10000:  # Menos de R$ 10k
                risco_score += 20
                red_flags.append(f"Capital social médio muito baixo: R$ {capital_medio:,.2f}")
            elif capital_medio < 50000:
                risco_score += 10
                red_flags.append(f"Capital social médio baixo: R$ {capital_medio:,.2f}")

            # Red Flag 4: Mesma qualificação em todas (peso: 15)
            if mesma_qualificacao:
                risco_score += 15
                qualif = list(dados['qualificacoes'])[0]
                red_flags.append(f"Mesma qualificação em todas: {qualif}")

            # Red Flag 5: Concentração no mesmo município (peso: 15)
            if taxa_mesmo_municipio > 80:
                risco_score += 15
                red_flags.append(f"{taxa_mesmo_municipio:.0f}% das empresas em {municipio_dominante}")
            elif taxa_mesmo_municipio > 60:
                risco_score += 7

            # Classificação de risco
            if risco_score >= 70:
                nivel_risco = "CRÍTICO"
                conclusao = "ALTA probabilidade de ser 'laranja' (CPF emprestado para empresas de fachada)"
            elif risco_score >= 50:
                nivel_risco = "ALTO"
                conclusao = "MODERADA probabilidade de ser 'laranja'. Requer investigação adicional"
            elif risco_score >= 30:
                nivel_risco = "MÉDIO"
                conclusao = "Padrão levemente suspeito. Monitoramento recomendado"
            else:
                # Não incluir na lista se risco baixo
                continue

            # Adicionar à lista de laranjas detectados
            laranjas_detectados.append({
                'cpf': cpf,
                'nome': dados['nome'],
                'risco_score': risco_score,
                'nivel_risco': nivel_risco,
                'conclusao': conclusao,
                'metricas': {
                    'total_empresas': total_empresas,
                    'empresas_ativas': total_empresas - empresas_inativas,
                    'empresas_inativas': empresas_inativas,
                    'taxa_inativas_pct': round(taxa_inativas, 1),
                    'capital_social_medio': round(capital_medio, 2),
                    'qualificacoes_unicas': list(dados['qualificacoes']),
                    'municipio_dominante': municipio_dominante,
                    'concentracao_geografica_pct': round(taxa_mesmo_municipio, 1)
                },
                'red_flags': red_flags,
                'empresas_sample': dados['empresas'][:5]  # Top 5 para não sobrecarregar
            })

        # Ordenar por risco (maior primeiro)
        laranjas_detectados.sort(key=lambda x: x['risco_score'], reverse=True)

        # Preparar resultado
        result = {
            'status': 'success',
            'total_socios_analisados': len(socios_pf),
            'total_laranjas_detectados': len(laranjas_detectados),
            'laranjas_detectados': laranjas_detectados[:20],  # Top 20 mais suspeitos
            'resumo': {
                'criticos': sum(1 for l in laranjas_detectados if l['nivel_risco'] == 'CRÍTICO'),
                'altos': sum(1 for l in laranjas_detectados if l['nivel_risco'] == 'ALTO'),
                'medios': sum(1 for l in laranjas_detectados if l['nivel_risco'] == 'MÉDIO')
            }
        }

        return json.dumps(result, ensure_ascii=False)

    except Exception as e:
        import traceback
        return json.dumps({
            'status': 'error',
            'error': str(e),
            'traceback': traceback.format_exc()
        })
