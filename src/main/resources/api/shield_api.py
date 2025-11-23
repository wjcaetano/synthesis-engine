#!/usr/bin/env python3
"""
THE SHIELD API - Fraud Detection for Pix Transactions
Real-time fraud detection API for fintechs, subacquirers, and digital banks

Endpoints:
  POST /api/v1/fraud-check - Check if CPF/CNPJ is fraudulent
  POST /api/v1/batch-check - Batch fraud checking (up to 100)
  GET /health - Health check
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import Optional, List
import json
import sys
import os
from datetime import datetime

# Add scripts directory to path
sys.path.append(os.path.join(os.path.dirname(__file__), '../../scripts'))

# Import our fraud detection modules
try:
    import detectLaranjas
    import detectCircularOwnership
    import analyzeGeographicRisk
    import mapEconomicGroup
    import duckdbQueryOptimized
except ImportError as e:
    print(f"Warning: Could not import all modules: {e}")

app = FastAPI(
    title="The Shield API",
    description="Fraud Detection for Pix Transactions - Powered by Orchestra-AI",
    version="1.0.0"
)

# CORS middleware (adjust for production)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure this for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ═══════════════════════════════════════════════════════════════
# REQUEST/RESPONSE MODELS
# ═══════════════════════════════════════════════════════════════

class FraudCheckRequest(BaseModel):
    """Request model for fraud check"""
    cnpj: Optional[str] = Field(None, description="CNPJ to check (8 or 14 digits)")
    cpf: Optional[str] = Field(None, description="CPF to check (11 digits)")
    include_details: bool = Field(False, description="Include detailed analysis")

    class Config:
        schema_extra = {
            "example": {
                "cnpj": "12345678",
                "include_details": True
            }
        }


class FraudCheckResponse(BaseModel):
    """Response model for fraud check"""
    risk_score: int = Field(..., description="Risk score 0-100 (100=max risk)")
    risk_level: str = Field(..., description="BAIXO|MÉDIO|ALTO|CRÍTICO")
    safe_to_proceed: bool = Field(..., description="Recommendation: safe to proceed?")
    reasons: List[str] = Field(..., description="List of risk factors detected")
    timestamp: str = Field(..., description="Analysis timestamp")

    # Optional detailed analysis
    laranja_analysis: Optional[dict] = None
    circular_ownership: Optional[dict] = None
    economic_group: Optional[dict] = None

    class Config:
        schema_extra = {
            "example": {
                "risk_score": 85,
                "risk_level": "ALTO",
                "safe_to_proceed": False,
                "reasons": [
                    "Sócio detectado como provável 'laranja' (CPF em 15 empresas)",
                    "80% das empresas relacionadas estão inativas",
                    "Capital social médio de R$ 1.200 é incompatível com porte"
                ],
                "timestamp": "2025-11-23T10:30:00Z"
            }
        }


class BatchFraudCheckRequest(BaseModel):
    """Batch fraud check request"""
    checks: List[dict] = Field(..., description="List of CNPJs/CPFs to check (max 100)")
    include_details: bool = Field(False, description="Include detailed analysis")

    class Config:
        schema_extra = {
            "example": {
                "checks": [
                    {"cnpj": "12345678"},
                    {"cpf": "12345678901"},
                    {"cnpj": "87654321"}
                ],
                "include_details": False
            }
        }


class HealthResponse(BaseModel):
    """Health check response"""
    status: str
    version: str
    timestamp: str
    components: dict


# ═══════════════════════════════════════════════════════════════
# HELPER FUNCTIONS
# ═══════════════════════════════════════════════════════════════

def create_mock_context(cnpj: Optional[str] = None, cpf: Optional[str] = None):
    """
    Create mock applicationContext and projectContext for standalone API
    In production, this would connect to real DuckDB/Neo4j
    """
    # For now, return mock context
    # TODO: Integrate with real duckdbQueryOptimized.py
    applicationContext = {}

    projectContext = {
        'duckdbResult': {
            'cnpj_consultado': cnpj or '',
            'empresa_alvo': {
                'cnpj_basico': cnpj or '',
                'razao_social': 'Empresa de Teste LTDA',
                'capital_social': 50000,
                'situacao_cadastral': '02',
                'uf': 'SP'
            },
            'empresas_relacionadas': [],
            'socios_relacionados': []
        }
    }

    return applicationContext, projectContext


def calculate_overall_risk(laranja_result, circular_result, group_result):
    """
    Calculate overall risk score combining all analyses

    Returns:
        tuple: (risk_score, risk_level, reasons, safe_to_proceed)
    """
    risk_score = 0
    reasons = []

    # 1. Laranja Detection (weight: 50%)
    if laranja_result and laranja_result.get('status') == 'success':
        if laranja_result['total_laranjas_detectados'] > 0:
            top_laranja = laranja_result['laranjas_detectados'][0]
            risk_score += top_laranja['risco_score'] * 0.5
            reasons.append(
                f"Sócio detectado como provável 'laranja' ({top_laranja['metricas']['total_empresas']} empresas)"
            )
            if top_laranja['metricas']['taxa_inativas_pct'] > 70:
                reasons.append(
                    f"{top_laranja['metricas']['taxa_inativas_pct']}% das empresas relacionadas estão inativas"
                )

    # 2. Circular Ownership (weight: 30%)
    if circular_result and circular_result.get('status') == 'success':
        if circular_result['total_cycles_detected'] > 0:
            risk_score += 30
            reasons.append(
                f"{circular_result['total_cycles_detected']} estruturas circulares detectadas (ocultação de beneficiário)"
            )

    # 3. Economic Group Red Flags (weight: 20%)
    if group_result and group_result.get('status') == 'success':
        if group_result['metricas_consolidadas']['taxa_atividade_pct'] < 50:
            risk_score += 20
            reasons.append(
                f"Grupo econômico com {group_result['metricas_consolidadas']['taxa_atividade_pct']}% de empresas ativas"
            )

    # Determine risk level
    if risk_score >= 70:
        risk_level = "CRÍTICO"
        safe_to_proceed = False
    elif risk_score >= 50:
        risk_level = "ALTO"
        safe_to_proceed = False
    elif risk_score >= 30:
        risk_level = "MÉDIO"
        safe_to_proceed = True  # But with caution
    else:
        risk_level = "BAIXO"
        safe_to_proceed = True

    if not reasons:
        reasons.append("Nenhum padrão de fraude detectado")

    return int(risk_score), risk_level, reasons, safe_to_proceed


# ═══════════════════════════════════════════════════════════════
# API ENDPOINTS
# ═══════════════════════════════════════════════════════════════

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """
    Health check endpoint
    """
    return HealthResponse(
        status="healthy",
        version="1.0.0",
        timestamp=datetime.utcnow().isoformat() + "Z",
        components={
            "duckdb": "available",
            "fraud_detection": "ready",
            "llm": "ready"
        }
    )


@app.post("/api/v1/fraud-check", response_model=FraudCheckResponse)
async def fraud_check(request: FraudCheckRequest):
    """
    Check if a CNPJ or CPF is associated with fraud patterns

    This endpoint analyzes:
    - Laranja detection (CPF lending)
    - Circular ownership structures
    - Economic group analysis
    - Geographic risk concentration

    Returns a risk score (0-100) and recommendation.
    """
    try:
        # Validate input
        if not request.cnpj and not request.cpf:
            raise HTTPException(
                status_code=400,
                detail="Either 'cnpj' or 'cpf' must be provided"
            )

        # Create context (mock for now)
        app_ctx, proj_ctx = create_mock_context(
            cnpj=request.cnpj,
            cpf=request.cpf
        )

        # Run fraud detection analyses
        laranja_result = None
        circular_result = None
        group_result = None

        try:
            # 1. Laranja Detection
            laranja_json = detectLaranjas.execute(app_ctx, proj_ctx)
            laranja_result = json.loads(laranja_json)

            # 2. Circular Ownership
            circular_json = detectCircularOwnership.execute(app_ctx, proj_ctx)
            circular_result = json.loads(circular_json)

            # 3. Economic Group
            group_json = mapEconomicGroup.execute(app_ctx, proj_ctx)
            group_result = json.loads(group_json)

        except Exception as e:
            # If analysis fails, return conservative high-risk response
            return FraudCheckResponse(
                risk_score=50,
                risk_level="MÉDIO",
                safe_to_proceed=False,
                reasons=[f"Erro na análise: {str(e)}. Por segurança, recomendamos cautela."],
                timestamp=datetime.utcnow().isoformat() + "Z"
            )

        # Calculate overall risk
        risk_score, risk_level, reasons, safe_to_proceed = calculate_overall_risk(
            laranja_result,
            circular_result,
            group_result
        )

        # Build response
        response = FraudCheckResponse(
            risk_score=risk_score,
            risk_level=risk_level,
            safe_to_proceed=safe_to_proceed,
            reasons=reasons,
            timestamp=datetime.utcnow().isoformat() + "Z"
        )

        # Include detailed analysis if requested
        if request.include_details:
            response.laranja_analysis = laranja_result
            response.circular_ownership = circular_result
            response.economic_group = group_result

        return response

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Internal error: {str(e)}"
        )


@app.post("/api/v1/batch-check")
async def batch_fraud_check(request: BatchFraudCheckRequest):
    """
    Batch fraud checking (up to 100 CNPJs/CPFs)

    Useful for bulk validation of a list of companies/individuals.
    """
    if len(request.checks) > 100:
        raise HTTPException(
            status_code=400,
            detail="Maximum 100 checks per batch request"
        )

    results = []
    for check_item in request.checks:
        try:
            fraud_req = FraudCheckRequest(
                cnpj=check_item.get('cnpj'),
                cpf=check_item.get('cpf'),
                include_details=request.include_details
            )
            result = await fraud_check(fraud_req)
            results.append({
                "input": check_item,
                "result": result.dict()
            })
        except Exception as e:
            results.append({
                "input": check_item,
                "error": str(e)
            })

    return {
        "total_checked": len(request.checks),
        "results": results,
        "timestamp": datetime.utcnow().isoformat() + "Z"
    }


# ═══════════════════════════════════════════════════════════════
# MAIN (for standalone testing)
# ═══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    import uvicorn

    print("🛡️  Starting The Shield API...")
    print("📍 Swagger UI: http://localhost:8000/docs")
    print("📍 ReDoc: http://localhost:8000/redoc")

    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        log_level="info"
    )
