"""
Script para consolidar análises LLM de múltiplos chunks em um resultado único
"""
import json
import sys

def execute(applicationContext, projectContext):
    """
    Consolida análises LLM de todos os chunks em uma análise final

    Args:
        applicationContext: Contexto da aplicação
        projectContext: Contexto do projeto (deve conter chunkAnalyses)

    Returns:
        JSON consolidado com análise completa
    """
    try:
        # Obter análises de chunks do contexto
        chunk_analyses = projectContext.get('chunkAnalyses', [])
        metadata = projectContext.get('chunkingMetadata', {})

        if not chunk_analyses:
            print("⚠️  Nenhuma análise de chunk encontrada para consolidar")
            return json.dumps({
                "status": "error",
                "error": "Nenhuma análise para consolidar",
                "risco_score": 0,
                "nivel_risco": "DESCONHECIDO"
            }, ensure_ascii=False)

        print(f"🔄 Consolidando {len(chunk_analyses)} análises de chunks...")

        # Agregar dados de todos os chunks
        all_principais_socios = []
        all_empresas_chave = []
        all_red_flags = []
        all_oportunidades = []
        all_recomendacoes = []
        risk_scores = []

        for idx, analysis in enumerate(chunk_analyses):
            if not isinstance(analysis, dict):
                print(f"⚠️  Chunk {idx} não é um dict válido, pulando...")
                continue

            # Coletar principais sócios (evitar duplicatas por CPF/CNPJ)
            socios = analysis.get('principais_socios', [])
            for socio in socios:
                cpf_cnpj = socio.get('cpf_cnpj', '')
                if cpf_cnpj and not any(s.get('cpf_cnpj') == cpf_cnpj for s in all_principais_socios):
                    all_principais_socios.append(socio)

            # Coletar empresas chave (evitar duplicatas por CNPJ)
            empresas = analysis.get('empresas_chave', [])
            for empresa in empresas:
                cnpj = empresa.get('cnpj', '')
                if cnpj and not any(e.get('cnpj') == cnpj for e in all_empresas_chave):
                    all_empresas_chave.append(empresa)

            # Coletar red flags (evitar duplicatas exatas)
            red_flags = analysis.get('red_flags', [])
            for flag in red_flags:
                if flag and flag not in all_red_flags:
                    all_red_flags.append(flag)

            # Coletar oportunidades (evitar duplicatas)
            oportunidades = analysis.get('oportunidades', [])
            for opp in oportunidades:
                if opp and opp not in all_oportunidades:
                    all_oportunidades.append(opp)

            # Coletar recomendações (evitar duplicatas)
            recomendacoes = analysis.get('recomendacoes', [])
            for rec in recomendacoes:
                if rec and rec not in all_recomendacoes:
                    all_recomendacoes.append(rec)

            # Coletar scores de risco
            score = analysis.get('risco_score', 0)
            if score and score > 0:
                risk_scores.append(score)

        # Calcular risco consolidado (média ponderada ou máximo)
        if risk_scores:
            # Usar o score máximo (abordagem conservadora)
            consolidated_risk_score = max(risk_scores)
            # Ou usar média: sum(risk_scores) / len(risk_scores)
        else:
            consolidated_risk_score = 50  # Neutro

        # Determinar nível de risco
        if consolidated_risk_score >= 80:
            nivel_risco = "CRÍTICO"
        elif consolidated_risk_score >= 60:
            nivel_risco = "ALTO"
        elif consolidated_risk_score >= 40:
            nivel_risco = "MÉDIO"
        else:
            nivel_risco = "BAIXO"

        # Ordenar por relevância
        all_principais_socios = sorted(
            all_principais_socios,
            key=lambda s: (1 if s.get('relevancia') == 'ALTA' else (2 if s.get('relevancia') == 'MÉDIA' else 3)),
            reverse=False
        )[:20]  # Top 20 sócios

        all_empresas_chave = sorted(
            all_empresas_chave,
            key=lambda e: e.get('relevancia_score', 0),
            reverse=True
        )[:50]  # Top 50 empresas

        # Gerar resumo executivo consolidado
        total_empresas = metadata.get('total_empresas', 0)
        total_chunks = metadata.get('total_chunks', 0)

        resumo_executivo = (
            f"Análise consolidada de {total_empresas} empresas processadas em {total_chunks} batches. "
            f"Identificados {len(all_principais_socios)} sócios principais e {len(all_empresas_chave)} empresas-chave. "
            f"Nível de risco avaliado como {nivel_risco} (score: {consolidated_risk_score}/100). "
        )

        if all_red_flags:
            resumo_executivo += f"{len(all_red_flags)} red flags identificados requerem atenção imediata."

        # Resultado consolidado
        consolidated_analysis = {
            "status": "success",
            "consolidation_metadata": {
                "total_chunks_processed": len(chunk_analyses),
                "total_empresas_analisadas": total_empresas,
                "chunks_com_sucesso": len([a for a in chunk_analyses if a.get('risco_score', 0) > 0])
            },
            "risco_score": consolidated_risk_score,
            "nivel_risco": nivel_risco,
            "resumo_executivo": resumo_executivo,
            "principais_socios": all_principais_socios,
            "empresas_chave": all_empresas_chave,
            "red_flags": all_red_flags[:30],  # Top 30 red flags mais críticos
            "oportunidades": all_oportunidades[:20],  # Top 20 oportunidades
            "recomendacoes": all_recomendacoes[:15]  # Top 15 recomendações
        }

        print(f"✅ Consolidação concluída:")
        print(f"   Risco Score: {consolidated_risk_score}/100 ({nivel_risco})")
        print(f"   Sócios: {len(all_principais_socios)}")
        print(f"   Empresas: {len(all_empresas_chave)}")
        print(f"   Red Flags: {len(all_red_flags)}")

        return json.dumps(consolidated_analysis, ensure_ascii=False, indent=2)

    except Exception as e:
        error_result = {
            "status": "error",
            "error": str(e),
            "risco_score": 0,
            "nivel_risco": "ERRO"
        }
        print(f"❌ Erro na consolidação: {str(e)}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return json.dumps(error_result, ensure_ascii=False)
