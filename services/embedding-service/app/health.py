
import os

import httpx
from fastapi import APIRouter
from fastapi.responses import JSONResponse
from qdrant_client import QdrantClient
from qdrant_client.http.exceptions import UnexpectedResponse

router = APIRouter()


@router.get("/health")
async def health():
    status = {"service": "embedding-service", "status": "up", "checks": {}}

    qdrant_ok = await _check_qdrant()
    status["checks"]["qdrant"] = "up" if qdrant_ok else "down"

    ollama_ok = await _check_ollama()
    status["checks"]["ollama"] = "up" if ollama_ok else "down"

    overall = "up" if qdrant_ok and ollama_ok else "degraded"
    status["status"] = overall

    http_status = 200 if overall == "up" else 503
    return JSONResponse(content=status, status_code=http_status)


async def _check_qdrant() -> bool:
    try:
        client = QdrantClient(
            host=os.getenv("QDRANT_HOST", "localhost"),
            port=int(os.getenv("QDRANT_PORT", "6333")),
            timeout=5,
        )
        client.get_collections()
        return True
    except Exception:
        return False


async def _check_ollama() -> bool:
    ollama_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.get(f"{ollama_url}/api/tags")
            return response.status_code == 200
    except Exception:
        return False
