#!/usr/bin/env python3
"""
Detecção de Empresas Fantasmas
Identifica empresas de fachada criadas para fraude

Red Flags:
- Capital social muito baixo (R$ 1.000 - R$ 5.000)
- Abertura recente (<6 meses)
- Endereço em coworking/escritório virtual
- Sócio em múltiplas empresas
- Situação cadastral suspeita
"""

import json
from datetime import datetime, timedelta


def execute(applicationContext, projectContext):
    """
    Detecta empresas fantasmas (shell companies) criadas para fraude

    Heurísticas:
    - Capital baixíssimo incompatível com atividade
    - Abertura muito recente
    - Endereço virtual/compartilhado
    - Padrão de "empresa de gaveta"
    """

    try:
        # Obter dados do projectContext
        duckdb_result = projectContext.get('duckdbResult', {})
        empresa_alvo = duckdb_result.get('empresa_alvo', {})
        empresas_relacionadas = duckdb_result.get('empresas_relacionadas', [])
        socios_relacionados = duckdb_result.get('socios_relacionados', [])

        if not empresa_alvo:
            return json.dumps({
                "status": "no_data",
                "message": "Empresa alvo não encontrada",
                "is_ghost": False
            })

        # Analisar empresa alvo
        ghost_score = 0
        red_flags = []
        indicators = {}

        # ════════════════════════════════════════════════════════════
        # 1. CAPITAL SOCIAL SUSPEITO (0-30 pontos)
        # ════════════════════════════════════════════════════════════
        capital = empresa_alvo.get('capital_social', 0)

        if capital <= 1000:  # R$ 1.000
            capital_score = 30
            capital_tier = "CRÍTICO"
            red_flags.append("Capital social de R$ 1.000 (valor mínimo obrigatório - extremamente suspeito)")
        elif capital <= 5000:  # R$ 1.000 - 5.000
            capital_score = 20
            capital_tier = "MUITO BAIXO"
            red_flags.append(f"Capital social de R$ {capital:,.2f} (incompatível com operação real)")
        elif capital <= 10000:  # R$ 5.000 - 10.000
            capital_score = 10
            capital_tier = "BAIXO"
            red_flags.append(f"Capital social de R$ {capital:,.2f} (suspeito para empresa ativa)")
        else:
            capital_score = 0
            capital_tier = "NORMAL"

        ghost_score += capital_score
        indicators['capital'] = {
            'valor': capital,
            'pontos': capital_score,
            'tier': capital_tier
        }

        # ════════════════════════════════════════════════════════════
        # 2. DATA DE ABERTURA (0-25 pontos)
        # ════════════════════════════════════════════════════════════
        data_inicio = empresa_alvo.get('data_inicio_ativ', '')
        abertura_score = 0
        idade_meses = None

        if data_inicio:
            try:
                if isinstance(data_inicio, str) and len(data_inicio) >= 10:
                    data_abertura = datetime.strptime(data_inicio[:10], '%Y-%m-%d')
                    hoje = datetime.now()
                    idade_meses = (hoje - data_abertura).days // 30

                    if idade_meses <= 1:  # Menos de 1 mês
                        abertura_score = 25
                        red_flags.append(f"Empresa aberta há {idade_meses} mês - EXTREMAMENTE recente")
                    elif idade_meses <= 3:  # 1-3 meses
                        abertura_score = 20
                        red_flags.append(f"Empresa aberta há {idade_meses} meses - muito recente")
                    elif idade_meses <= 6:  # 3-6 meses
                        abertura_score = 10
                        red_flags.append(f"Empresa aberta há {idade_meses} meses - recente")
                    else:
                        abertura_score = 0
            except:
                abertura_score = 5

        ghost_score += abertura_score
        indicators['abertura'] = {
            'data': data_inicio,
            'idade_meses': idade_meses,
            'pontos': abertura_score
        }

        # ════════════════════════════════════════════════════════════
        # 3. ENDEREÇO SUSPEITO (0-20 pontos)
        # ════════════════════════════════════════════════════════════
        # Padrões comuns de endereços virtuais/coworking
        ENDERECO_SUSPEITO_KEYWORDS = [
            'COWORKING',
            'ESCRITORIO VIRTUAL',
            'SALA COMERCIAL',
            'BUSINESS CENTER',
            'REGUS',
            'WEWORK',
            'SPACES',
            'SALA COMPARTILHADA',
            'ENDEREÇO FISCAL'
        ]

        endereco_score = 0
        municipio = empresa_alvo.get('municipio', '').upper()
        # Nota: Não temos campo de endereço completo nos dados RFB básicos
        # Em produção, cruzaríamos com base de endereços virtuais conhecidos

        # Por ora, verificar se múltiplas empresas no mesmo município
        empresas_mesmo_municipio = sum(
            1 for e in empresas_relacionadas
            if e.get('municipio') == empresa_alvo.get('municipio')
        )

        if empresas_mesmo_municipio > 10:
            endereco_score = 15
            red_flags.append(
                f"{empresas_mesmo_municipio} empresas relacionadas no mesmo município "
                f"(possível endereço compartilhado)"
            )
        elif empresas_mesmo_municipio > 5:
            endereco_score = 10

        ghost_score += endereco_score
        indicators['endereco'] = {
            'municipio': municipio,
            'empresas_mesmo_municipio': empresas_mesmo_municipio,
            'pontos': endereco_score
        }

        # ════════════════════════════════════════════════════════════
        # 4. SÓCIO LARANJA (0-25 pontos)
        # ════════════════════════════════════════════════════════════
        socio_score = 0

        # Verificar se sócio aparece em múltiplas empresas
        if socios_relacionados:
            # Contar participações do primeiro sócio
            primeiro_socio = socios_relacionados[0]
            cpf_cnpj = primeiro_socio.get('cpf_cnpj', '')

            if cpf_cnpj and len(cpf_cnpj) == 11:  # CPF
                # Contar quantas vezes este CPF aparece como sócio
                participacoes = sum(
                    1 for s in socios_relacionados
                    if s.get('cpf_cnpj') == cpf_cnpj
                )

                if participacoes >= 10:
                    socio_score = 25
                    red_flags.append(
                        f"Sócio principal aparece em {participacoes} empresas (provável laranja)"
                    )
                elif participacoes >= 5:
                    socio_score = 15
                    red_flags.append(
                        f"Sócio principal aparece em {participacoes} empresas"
                    )

        ghost_score += socio_score
        indicators['socio'] = {
            'pontos': socio_score,
            'participacoes': participacoes if 'participacoes' in locals() else 0
        }

        # ════════════════════════════════════════════════════════════
        # CLASSIFICAÇÃO FINAL
        # ════════════════════════════════════════════════════════════

        if ghost_score >= 70:
            is_ghost = True
            confidence = "ALTÍSSIMA"
            recommendation = "🚨 BLOQUEAR - Empresa fantasma com alta probabilidade"
        elif ghost_score >= 50:
            is_ghost = True
            confidence = "ALTA"
            recommendation = "⚠️  INVESTIGAR - Padrão fortemente suspeito de empresa fantasma"
        elif ghost_score >= 30:
            is_ghost = True
            confidence = "MODERADA"
            recommendation = "⚠️  CAUTELA - Alguns indicadores de empresa fantasma presentes"
        else:
            is_ghost = False
            confidence = "BAIXA"
            recommendation = "✅ Provável empresa legítima (ou precisa mais investigação)"

        # ════════════════════════════════════════════════════════════
        # PADRÕES CONHECIDOS
        # ════════════════════════════════════════════════════════════

        patterns = []

        # Padrão 1: Empresa "Pré-Paga"
        if capital <= 1000 and idade_meses and idade_meses <= 3:
            patterns.append({
                'tipo': 'PRÉ-PAGA',
                'descricao': 'Empresa recém-criada com capital mínimo - possível "empresa de gaveta" vendida pronta'
            })

        # Padrão 2: Laranjal
        if socio_score > 0 and capital <= 5000:
            patterns.append({
                'tipo': 'LARANJAL',
                'descricao': 'Sócio em múltiplas empresas + capital baixo - possível laranja profissional'
            })

        # Padrão 3: Shell Company
        if ghost_score >= 60:
            patterns.append({
                'tipo': 'SHELL COMPANY',
                'descricao': 'Múltiplos indicadores de empresa de fachada para lavagem de dinheiro ou fraude'
            })

        # ════════════════════════════════════════════════════════════
        # PREPARAR RESULTADO
        # ════════════════════════════════════════════════════════════

        result = {
            'status': 'success',
            'is_ghost': is_ghost,
            'ghost_score': ghost_score,
            'confidence': confidence,
            'recommendation': recommendation,
            'empresa': {
                'cnpj': empresa_alvo.get('cnpj_basico', 'N/A'),
                'razao_social': empresa_alvo.get('razao_social', 'N/A'),
                'capital_social': capital,
                'data_inicio': data_inicio,
                'idade_meses': idade_meses
            },
            'indicators': indicators,
            'red_flags': red_flags,
            'patterns': patterns
        }

        return json.dumps(result, ensure_ascii=False)

    except Exception as e:
        import traceback
        return json.dumps({
            'status': 'error',
            'error': str(e),
            'traceback': traceback.format_exc()
        })
