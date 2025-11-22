"""
Script para conversão dos arquivos CSV da Receita Federal para formato Parquet
Usa DuckDB para processamento eficiente de dados volumosos
"""
import duckdb
import json
import sys
from pathlib import Path
from datetime import datetime

def execute(applicationContext, projectContext):
    """
    Converte arquivos CSV da RFB para formato Parquet usando DuckDB

    Args:
        applicationContext: Contexto da aplicação Orchestra-AI
        projectContext: Contexto do projeto

    Returns:
        JSON com status da conversão
    """
    root_folder = Path(projectContext.get('rootFolder', '.'))
    csv_base_dir = root_folder / 'data' / 'rfb_raw'
    parquet_dir = root_folder / 'data' / 'parquet'
    parquet_dir.mkdir(parents=True, exist_ok=True)

    # Conectar DuckDB em modo in-memory (rápido)
    con = duckdb.connect(database=':memory:')

    # Configurações de conversão
    # Formato dos CSVs da RFB: delimitador ';', sem header, encoding latin1
    conversions = {
        "empresas": {
            "csv_pattern": "empresas/*.EMPRECSV",
            "parquet_file": "empresas.parquet",
            "columns": [
                "cnpj_basico", "razao_social", "natureza_juridica", "qualificacao_responsavel",
                "capital_social", "porte_empresa", "ente_federativo_responsavel"
            ]
        },
        "estabelecimentos": {
            "csv_pattern": "estabelecimentos/*.ESTABELE",
            "parquet_file": "estabelecimentos.parquet",
            "columns": [
                "cnpj_basico", "cnpj_ordem", "cnpj_dv", "identificador_matriz_filial",
                "nome_fantasia", "situacao_cadastral", "data_situacao_cadastral",
                "motivo_situacao_cadastral", "nome_cidade_exterior", "pais",
                "data_inicio_atividade", "cnae_fiscal_principal", "cnae_fiscal_secundaria",
                "tipo_logradouro", "logradouro", "numero", "complemento", "bairro",
                "cep", "uf", "municipio", "ddd_1", "telefone_1", "ddd_2", "telefone_2",
                "ddd_fax", "fax", "correio_eletronico", "situacao_especial", "data_situacao_especial"
            ]
        },
        "socios": {
            "csv_pattern": "socios/*.SOCIOCSV",
            "parquet_file": "socios.parquet",
            "columns": [
                "cnpj_basico", "identificador_socio", "nome_socio", "cnpj_cpf_socio",
                "qualificacao_socio", "data_entrada_sociedade", "pais", "cpf_representante_legal",
                "nome_representante_legal", "qualificacao_representante_legal",
                "faixa_etaria"
            ]
        }
    }

    log_messages = []
    parquet_files = []
    stats = {}
    errors = []

    try:
        for table_name, config in conversions.items():
            csv_dir = csv_base_dir / config['csv_pattern'].split('/')[0]
            csv_pattern = config['csv_pattern'].split('/')[-1]

            # Buscar arquivos CSV
            csv_files = list(csv_dir.glob(csv_pattern))

            if not csv_files:
                msg = f"⚠️  Nenhum arquivo CSV encontrado para {table_name} em {csv_dir}"
                print(msg, file=sys.stderr)
                log_messages.append(msg)
                continue

            parquet_path = parquet_dir / config['parquet_file']

            msg = f"🔄 Convertendo {len(csv_files)} arquivo(s) CSV → {config['parquet_file']}..."
            print(msg)
            log_messages.append(msg)

            try:
                # Criar query SQL para ler todos os CSVs e converter para Parquet
                # DuckDB lê automaticamente múltiplos arquivos e detecta schema
                csv_paths_str = ', '.join([f"'{csv_file}'" for csv_file in csv_files])

                # Opções de leitura otimizadas para CSVs da RFB
                read_csv_options = {
                    'delim': ';',
                    'header': False,
                    'null_padding': True,
                    'ignore_errors': True,  # Tolera linhas malformadas
                    'quote': '"',
                    'escape': '"'
                }

                # Gerar colunas automáticas (column0, column1, ...)
                # A RFB não fornece header nos CSVs
                columns_clause = ', '.join([
                    f"column{i} AS {col}"
                    for i, col in enumerate(config['columns'])
                ])

                # Query de conversão
                query = f"""
                COPY (
                    SELECT {columns_clause}
                    FROM read_csv_auto([{csv_paths_str}],
                        delim=';',
                        header=false,
                        null_padding=true,
                        ignore_errors=true,
                        max_line_size=1048576
                    )
                )
                TO '{parquet_path}'
                (FORMAT PARQUET, COMPRESSION 'ZSTD', ROW_GROUP_SIZE 100000)
                """

                # Executar conversão
                con.execute(query)

                # Obter estatísticas do arquivo Parquet gerado
                size_mb = parquet_path.stat().st_size / (1024 * 1024)
                row_count_query = f"SELECT COUNT(*) as count FROM '{parquet_path}'"
                row_count = con.execute(row_count_query).fetchone()[0]

                msg = f"✅ {config['parquet_file']} criado:"
                print(msg)
                log_messages.append(msg)
                msg = f"   • Tamanho: {size_mb:.1f} MB"
                print(msg)
                log_messages.append(msg)
                msg = f"   • Linhas: {row_count:,}"
                print(msg)
                log_messages.append(msg)

                parquet_files.append(str(parquet_path))
                stats[table_name] = {
                    "file": str(parquet_path),
                    "size_mb": round(size_mb, 2),
                    "row_count": row_count,
                    "csv_sources": len(csv_files)
                }

            except Exception as e:
                error_msg = f"❌ Erro ao converter {table_name}: {str(e)}"
                print(error_msg, file=sys.stderr)
                log_messages.append(error_msg)
                errors.append({"table": table_name, "error": str(e)})

        # Processar arquivos de referência (pequenos, direto)
        referencias_dir = csv_base_dir / 'referencias'
        if referencias_dir.exists():
            msg = "📚 Processando arquivos de referência..."
            print(msg)
            log_messages.append(msg)

            for zip_file in referencias_dir.glob('*.zip'):
                try:
                    import zipfile
                    with zipfile.ZipFile(zip_file, 'r') as z:
                        z.extractall(referencias_dir)
                except Exception as e:
                    log_messages.append(f"⚠️  Erro ao extrair {zip_file.name}: {e}")

        # Resultado final
        result = {
            "status": "success" if not errors else "partial_success",
            "timestamp": datetime.now().isoformat(),
            "parquet_files": parquet_files,
            "stats": stats,
            "total_rows": sum(s['row_count'] for s in stats.values()),
            "total_size_mb": round(sum(s['size_mb'] for s in stats.values()), 2),
            "errors": errors,
            "logs": log_messages
        }

        # Log final
        if errors:
            print(f"\n⚠️  Conversão concluída com {len(errors)} erro(s)")
        else:
            print(f"\n🎉 Conversão concluída com sucesso!")
            print(f"   • {len(parquet_files)} arquivo(s) Parquet criado(s)")
            print(f"   • {result['total_rows']:,} linhas processadas")
            print(f"   • {result['total_size_mb']} MB em disco")

        con.close()
        return json.dumps(result, ensure_ascii=False, indent=2)

    except Exception as e:
        error_result = {
            "status": "error",
            "error": str(e),
            "logs": log_messages
        }
        print(f"❌ Erro crítico: {str(e)}", file=sys.stderr)
        con.close()
        return json.dumps(error_result, ensure_ascii=False)
