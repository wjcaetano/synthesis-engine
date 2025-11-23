"""
Script para dividir empresas relacionadas em chunks para processamento LLM
Permite processar centenas/milhares de empresas em batches controlados
"""
import json
import sys

def execute(applicationContext, projectContext, empresas_relacionadas_json, chunk_size=50):
    """
    Divide lista de empresas em chunks para processamento em batches

    Args:
        applicationContext: Contexto da aplicação
        projectContext: Contexto do projeto
        empresas_relacionadas_json: JSON string com lista de empresas
        chunk_size: Tamanho de cada chunk (default: 50)

    Returns:
        JSON com chunks de empresas
    """
    try:
        # Parse JSON input
        if isinstance(empresas_relacionadas_json, str):
            data = json.loads(empresas_relacionadas_json)
        else:
            data = empresas_relacionadas_json

        # Extrair lista de empresas
        if isinstance(data, dict):
            empresas_relacionadas = data.get('empresas_relacionadas', [])
            metadata = {
                'status': data.get('status'),
                'search_mode': data.get('search_mode'),
                'search_value': data.get('search_value'),
                'cnpj_consultado': data.get('cnpj_consultado'),
                'empresa_alvo': data.get('empresa_alvo'),
                'total_relacionadas': data.get('total_relacionadas', 0)
            }
        elif isinstance(data, list):
            empresas_relacionadas = data
            metadata = {'total_relacionadas': len(data)}
        else:
            raise ValueError(f"Formato de dados inválido: {type(data)}")

        if not empresas_relacionadas:
            print("⚠️  Nenhuma empresa para processar em chunks")
            return json.dumps({
                "metadata": metadata,
                "total_chunks": 0,
                "chunk_size": int(chunk_size),
                "chunks": []
            }, ensure_ascii=False)

        # Dividir em chunks
        chunk_size = int(chunk_size)
        chunks = []

        for i in range(0, len(empresas_relacionadas), chunk_size):
            chunk_empresas = empresas_relacionadas[i:i + chunk_size]

            chunk = {
                "chunk_index": len(chunks),
                "chunk_id": f"chunk_{len(chunks):04d}",
                "chunk_size": len(chunk_empresas),
                "start_index": i,
                "end_index": min(i + chunk_size, len(empresas_relacionadas)) - 1,
                "empresas": chunk_empresas
            }

            chunks.append(chunk)

        result = {
            "metadata": metadata,
            "total_empresas": len(empresas_relacionadas),
            "total_chunks": len(chunks),
            "chunk_size": chunk_size,
            "chunks": chunks
        }

        print(f"✅ Criados {len(chunks)} chunks de até {chunk_size} empresas")
        print(f"   Total de empresas: {len(empresas_relacionadas)}")
        for i, chunk in enumerate(chunks[:5]):  # Mostrar primeiros 5
            print(f"   Chunk {i}: {chunk['chunk_size']} empresas (índices {chunk['start_index']}-{chunk['end_index']})")
        if len(chunks) > 5:
            print(f"   ... e mais {len(chunks) - 5} chunks")

        return json.dumps(result, ensure_ascii=False, indent=2)

    except Exception as e:
        error_result = {
            "status": "error",
            "error": str(e),
            "total_chunks": 0,
            "chunks": []
        }
        print(f"❌ Erro ao criar chunks: {str(e)}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return json.dumps(error_result, ensure_ascii=False)
