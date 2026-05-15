
import os

import httpx
from fastapi import APIRouter
from fastapi.responses import JSONResponse
from kafka import KafkaAdminClient
from kafka.errors import KafkaError
from qdrant_client import QdrantClient

router = APIRouter()


@router.get("/health")
async def health():
    checks = {}

    checks["qdrant"] = "up" if await _check_qdrant() else "down"
    checks["ollama"] = "up" if await _check_ollama() else "down"
    checks["kafka"] = "up" if _check_kafka() else "down"

    all_up = all(v == "up" for v in checks.values())
    overall = "up" if all_up else "degraded"

    return JSONResponse(
        content={"service": "embedding-service", "status": overall, "checks": checks},
        status_code=200 if all_up else 503,
    )


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


def _check_kafka() -> bool:
    bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")
    try:
        admin = KafkaAdminClient(bootstrap_servers=bootstrap, request_timeout_ms=3000)
        admin.list_topics()
        admin.close()
        return True
    except KafkaError:
        return False
    except Exception:
        return False
