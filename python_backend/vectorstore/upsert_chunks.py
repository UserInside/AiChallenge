from qdrant_client.http.models import PointStruct
from embeddings.bge_service import BGEEmbeddingService
from embeddings.config import EMBEDDING_CONFIG
from vectorstore.qdrant_store import QdrantVectorStore
from ingestion.chunker import get_kotlin_files, create_chunks_from_file

embedder = BGEEmbeddingService(EMBEDDING_CONFIG)
store = QdrantVectorStore(collection_name="rag_chunks")

def ingest_project(root_dir: str, module_name: str):
    files = get_kotlin_files(root_dir)
    for file_path in files:
        chunks = create_chunks_from_file(file_path, module_name)
        points = []
        for idx, chunk in enumerate(chunks):
            vector = embedder.embed_chunks([chunk["text"]])[0]
            points.append(PointStruct(
                id=f"{file_path}_{idx}",
                vector=vector,
                payload=chunk["metadata"]
            ))
        store.upsert(points)
    print(f"Ingested {len(files)} Kotlin files into Qdrant")

