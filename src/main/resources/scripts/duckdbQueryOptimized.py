"""
Script para consulta otimizada de dados CNPJ usando DuckDB
Implementa relevance scoring para filtrar top empresas relacionadas
"""
import duckdb
import json
import sys
from pathlib import Path

def execute(applicationContext, projectContext, cnpj_input, max_results=50):
    """
    Consulta empresas relacionadas a um CNPJ com scoring de relevância

    Args:
        applicationContext: Contexto da aplicação
        projectContext: Contexto do projeto
        cnpj_input: CNPJ base para busca (formato: 12.345.678/0001-90 ou 12345678)
        max_results: Máximo de empresas a retornar (default: 50)

    Returns:
        JSON com empresas relacionadas ordenadas por relevância
    """
    try:
        # Normalizar CNPJ (remover pontuação, pegar apenas 8 dígitos base)
        cnpj_basico = ''.join(filter(str.isdigit, cnpj_input))[:8]

        if len(cnpj_basico) != 8:
            return json.dumps({
                "status": "error",
                "error": f"CNPJ inválido. Deve conter 8 dígitos base. Fornecido: {cnpj_input}",
                "empresas_relacionadas": []
            }, ensure_ascii=False)

        # Diretórios
        root_folder = Path(projectContext.get('rootFolder', '.'))
        parquet_dir = root_folder / 'data' / 'parquet'

        # Verificar se arquivos Parquet existem
        required_files = {
            'empresas': parquet_dir / 'empresas.parquet',
            'estabelecimentos': parquet_dir / 'estabelecimentos.parquet',
            'socios': parquet_dir / 'socios.parquet'
        }

        missing_files = [name for name, path in required_files.items() if not path.exists()]
        if missing_files:
            return json.dumps({
                "status": "error",
                "error": f"Arquivos Parquet não encontrados: {', '.join(missing_files)}. Execute a receita rfb-data-ingestion primeiro.",
                "empresas_relacionadas": []
            }, ensure_ascii=False)

        # Conectar DuckDB
        con = duckdb.connect(database=':memory:')

        print(f"🔍 Consultando empresas relacionadas ao CNPJ {cnpj_basico}...")

        # Query otimizada com relevance scoring
        # Estratégia:
        # 1. Encontrar todos os sócios da empresa alvo
        # 2. Encontrar todas as empresas onde esses sócios participam
        # 3. Calcular score de relevância baseado em:
        #    - Capital social da empresa
        #    - Percentual de participação do sócio (se disponível)
        #    - Situação cadastral (ativa = maior score)
        # 4. Retornar top N empresas ordenadas por relevância

        query = f"""
        WITH socios_empresa_alvo AS (
            -- Passo 1: Buscar todos os sócios da empresa alvo
            SELECT DISTINCT
                cnpj_cpf_socio,
                nome_socio,
                qualificacao_socio
            FROM '{required_files['socios']}'
            WHERE cnpj_basico = '{cnpj_basico}'
        ),
        empresas_relacionadas_raw AS (
            -- Passo 2: Buscar empresas onde esses sócios participam
            SELECT DISTINCT
                s.cnpj_basico,
                s.cnpj_cpf_socio,
                s.nome_socio,
                s.qualificacao_socio,
                e.razao_social,
                e.capital_social,
                e.porte_empresa,
                e.natureza_juridica,
                est.nome_fantasia,
                est.situacao_cadastral,
                est.uf,
                est.municipio,
                est.cnae_fiscal_principal
            FROM '{required_files['socios']}' s
            INNER JOIN socios_empresa_alvo sea
                ON s.cnpj_cpf_socio = sea.cnpj_cpf_socio
            LEFT JOIN '{required_files['empresas']}' e
                ON s.cnpj_basico = e.cnpj_basico
            LEFT JOIN '{required_files['estabelecimentos']}' est
                ON s.cnpj_basico = est.cnpj_basico
                AND est.identificador_matriz_filial = '1'  -- Apenas matriz
            WHERE s.cnpj_basico != '{cnpj_basico}'  -- Excluir a própria empresa
        ),
        empresas_scored AS (
            -- Passo 3: Calcular relevance score
            SELECT
                cnpj_basico,
                razao_social,
                nome_fantasia,
                capital_social,
                porte_empresa,
                natureza_juridica,
                situacao_cadastral,
                uf,
                municipio,
                cnae_fiscal_principal,
                nome_socio,
                qualificacao_socio,
                -- Relevance Score (0-100)
                CAST(
                    COALESCE(
                        CASE
                            WHEN TRY_CAST(capital_social AS DOUBLE) > 10000000 THEN 40
                            WHEN TRY_CAST(capital_social AS DOUBLE) > 1000000 THEN 25
                            WHEN TRY_CAST(capital_social AS DOUBLE) > 100000 THEN 15
                            ELSE 5
                        END, 5
                    )
                    +
                    CASE
                        WHEN situacao_cadastral = '02' THEN 30  -- Ativa
                        WHEN situacao_cadastral = '01' THEN 10  -- Nula
                        ELSE 0
                    END
                    +
                    CASE
                        WHEN porte_empresa = '05' THEN 20  -- Grande
                        WHEN porte_empresa = '03' THEN 15  -- Média
                        WHEN porte_empresa = '01' THEN 10  -- Pequena
                        ELSE 5
                    END
                AS INT) as relevance_score,
                -- Metadados adicionais
                COUNT(*) OVER (PARTITION BY cnpj_basico) as shared_partners_count
            FROM empresas_relacionadas_raw
        )
        -- Passo 4: Retornar top N empresas únicas ordenadas por score
        SELECT DISTINCT ON (cnpj_basico)
            cnpj_basico,
            razao_social,
            nome_fantasia,
            capital_social,
            porte_empresa,
            natureza_juridica,
            situacao_cadastral,
            uf,
            municipio,
            cnae_fiscal_principal,
            nome_socio as exemplo_socio_comum,
            qualificacao_socio,
            relevance_score,
            shared_partners_count
        FROM empresas_scored
        ORDER BY cnpj_basico, relevance_score DESC
        LIMIT {int(max_results)}
        """

        # Executar query
        result_df = con.execute(query).fetchdf()

        # Converter para lista de dicionários
        empresas_relacionadas = result_df.to_dict(orient='records')

        # Converter valores numpy/pandas para tipos nativos Python
        for empresa in empresas_relacionadas:
            for key, value in empresa.items():
                if hasattr(value, 'item'):  # numpy/pandas types
                    empresa[key] = value.item()
                elif value is None or (isinstance(value, float) and str(value) == 'nan'):
                    empresa[key] = None

        # Buscar informações da empresa alvo
        empresa_alvo_query = f"""
        SELECT
            e.cnpj_basico,
            e.razao_social,
            e.capital_social,
            est.nome_fantasia,
            est.situacao_cadastral,
            est.uf,
            est.municipio
        FROM '{required_files['empresas']}' e
        LEFT JOIN '{required_files['estabelecimentos']}' est
            ON e.cnpj_basico = est.cnpj_basico
            AND est.identificador_matriz_filial = '1'
        WHERE e.cnpj_basico = '{cnpj_basico}'
        LIMIT 1
        """

        empresa_alvo_df = con.execute(empresa_alvo_query).fetchdf()
        empresa_alvo = empresa_alvo_df.to_dict(orient='records')[0] if len(empresa_alvo_df) > 0 else None

        # Limpar empresa_alvo
        if empresa_alvo:
            for key, value in empresa_alvo.items():
                if hasattr(value, 'item'):
                    empresa_alvo[key] = value.item()

        # Resultado final
        result = {
            "status": "success",
            "cnpj_consultado": cnpj_basico,
            "empresa_alvo": empresa_alvo,
            "total_relacionadas": len(empresas_relacionadas),
            "max_results": int(max_results),
            "empresas_relacionadas": empresas_relacionadas
        }

        print(f"✅ Encontradas {len(empresas_relacionadas)} empresas relacionadas")
        print(f"   Top 5 por relevância:")
        for i, emp in enumerate(empresas_relacionadas[:5], 1):
            print(f"   {i}. {emp.get('razao_social', 'N/A')} (Score: {emp.get('relevance_score', 0)})")

        con.close()
        return json.dumps(result, ensure_ascii=False, indent=2)

    except Exception as e:
        error_result = {
            "status": "error",
            "error": str(e),
            "empresas_relacionadas": []
        }
        print(f"❌ Erro na consulta: {str(e)}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return json.dumps(error_result, ensure_ascii=False)
