from fastapi import FastAPI
from pydantic import BaseModel

from embeddings.bge_service import BGEEmbeddingService
from embeddings.config import EMBEDDING_CONFIG

app = FastAPI(title="RAG Backend")

embedder = BGEEmbeddingService(EMBEDDING_CONFIG)

class EmbedRequest(BaseModel):
    text: str

@app.post("/embed_query")
def embed_query(req: EmbedRequest):
    vec = embedder.embed_query(req.text)
    return {"vector": vec}

