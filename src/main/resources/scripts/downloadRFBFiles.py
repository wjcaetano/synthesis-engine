"""
Script para download dos arquivos da Receita Federal (Dados Abertos CNPJ)
URL: https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/
"""
import requests
import zipfile
import os
from pathlib import Path
import json
import sys
from datetime import datetime

def execute(applicationContext, projectContext):
    """
    Executa o download e extração dos arquivos da Receita Federal

    Args:
        applicationContext: Contexto da aplicação Orchestra-AI
        projectContext: Contexto do projeto (contém rootFolder e configs)

    Returns:
        JSON com status do download
    """
    # Obter configurações
    force_download = projectContext.get('$api', {}).get('configs', {}).get('options', {}).get('force_download', False)
    download_month = projectContext.get('$api', {}).get('configs', {}).get('options', {}).get('download_month', '2025-11')

    # URL base da Receita Federal
    base_url = f"https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/{download_month}/"

    # Arquivos essenciais para o relatório de Asset Intelligence
    # Nota: Download seletivo para economizar banda e espaço
    files_config = {
        'empresas': {
            'files': ['Empresas0.zip'],  # Pode expandir para Empresas1-9 se necessário
            'extract_to': 'empresas'
        },
        'estabelecimentos': {
            'files': ['Estabelecimentos0.zip', 'Estabelecimentos1.zip'],  # Top 2 para começar
            'extract_to': 'estabelecimentos'
        },
        'socios': {
            'files': ['Socios0.zip', 'Socios1.zip'],  # Top 2 para começar
            'extract_to': 'socios'
        },
        'referencias': {
            'files': ['Cnaes.zip', 'Municipios.zip', 'Naturezas.zip', 'Paises.zip', 'Qualificacoes.zip'],
            'extract_to': 'referencias'
        }
    }

    # Diretórios de trabalho
    root_folder = Path(projectContext.get('rootFolder', '.'))
    base_download_dir = root_folder / 'data' / 'rfb_raw'
    base_download_dir.mkdir(parents=True, exist_ok=True)

    log_messages = []
    downloaded_files = []
    extracted_files = []
    errors = []

    try:
        for category, config in files_config.items():
            category_dir = base_download_dir / config['extract_to']
            category_dir.mkdir(parents=True, exist_ok=True)

            for filename in config['files']:
                zip_path = category_dir / filename

                # Skip se já existe e force_download=False
                if zip_path.exists() and not force_download:
                    msg = f"⏭️  {filename} já existe em {category} - pulando download"
                    print(msg)
                    log_messages.append(msg)
                    continue

                # Download do arquivo
                url = base_url + filename
                msg = f"📥 Baixando {filename} de {category}..."
                print(msg)
                log_messages.append(msg)

                try:
                    response = requests.get(url, stream=True, timeout=300)
                    response.raise_for_status()

                    # Salvar arquivo ZIP
                    total_size = int(response.headers.get('content-length', 0))
                    downloaded_size = 0

                    with open(zip_path, 'wb') as f:
                        for chunk in response.iter_content(chunk_size=8192):
                            if chunk:
                                f.write(chunk)
                                downloaded_size += len(chunk)

                    msg = f"✅ {filename} baixado ({downloaded_size / (1024*1024):.1f} MB)"
                    print(msg)
                    log_messages.append(msg)
                    downloaded_files.append(str(zip_path))

                    # Extrair ZIP
                    msg = f"📦 Extraindo {filename}..."
                    print(msg)
                    log_messages.append(msg)

                    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                        zip_ref.extractall(category_dir)
                        extracted_count = len(zip_ref.namelist())
                        extracted_files.extend([str(category_dir / name) for name in zip_ref.namelist()])

                    msg = f"✅ {extracted_count} arquivo(s) CSV extraído(s) de {filename}"
                    print(msg)
                    log_messages.append(msg)

                except requests.exceptions.RequestException as e:
                    error_msg = f"❌ Erro ao baixar {filename}: {str(e)}"
                    print(error_msg, file=sys.stderr)
                    log_messages.append(error_msg)
                    errors.append({"file": filename, "error": str(e)})
                    continue

                except zipfile.BadZipFile as e:
                    error_msg = f"❌ Erro ao extrair {filename}: arquivo ZIP corrompido"
                    print(error_msg, file=sys.stderr)
                    log_messages.append(error_msg)
                    errors.append({"file": filename, "error": "BadZipFile"})
                    continue

        # Resultado final
        result = {
            "status": "success" if not errors else "partial_success",
            "timestamp": datetime.now().isoformat(),
            "download_month": download_month,
            "downloaded_files": len(downloaded_files),
            "extracted_csv_files": len(extracted_files),
            "files": downloaded_files,
            "csv_files": extracted_files[:20],  # Primeiros 20 para não sobrecarregar JSON
            "total_csv_count": len(extracted_files),
            "errors": errors,
            "logs": log_messages[-50:]  # Últimos 50 logs
        }

        # Log final
        if errors:
            print(f"\n⚠️  Download concluído com {len(errors)} erro(s)")
        else:
            print(f"\n🎉 Download concluído com sucesso!")
            print(f"   • {len(downloaded_files)} arquivo(s) ZIP baixado(s)")
            print(f"   • {len(extracted_files)} arquivo(s) CSV extraído(s)")

        return json.dumps(result, ensure_ascii=False, indent=2)

    except Exception as e:
        error_result = {
            "status": "error",
            "error": str(e),
            "logs": log_messages
        }
        print(f"❌ Erro crítico: {str(e)}", file=sys.stderr)
        return json.dumps(error_result, ensure_ascii=False)
