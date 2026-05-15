
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.health import router as health_router

logging.basicConfig(
    level=logging.getLevelName(os.getenv("LOG_LEVEL", "INFO")),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(application: FastAPI):
    logger.info("Starting ContextEngine embedding-service")
    yield
    logger.info("Shutting down ContextEngine embedding-service")


app = FastAPI(
    title="ContextEngine Embedding Service",
    description="Kafka consumer that generates semantic embeddings via Ollama and stores them in Qdrant",
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(health_router)


@app.get("/")
async def root():
    return JSONResponse({"service": "embedding-service", "status": "running"})
