from qdrant_client import QdrantClient
from qdrant_client.http.models import PointStruct

class QdrantVectorStore:
    def __init__(self, host="localhost", port=6333, collection_name="rag_chunks"):
        self.client = QdrantClient(host=host, port=port)
        self.collection_name = collection_name
        # создаём коллекцию при старте, если нет
        self.client.recreate_collection(
            collection_name=collection_name,
            vectors={"size": 768, "distance": "Cosine"}
        )

    def upsert(self, points: list[PointStruct]):
        self.client.upsert(collection_name=self.collection_name, points=points)

    def search(self, vector, top_k=10, filter=None):
        return self.client.search(
            collection_name=self.collection_name,
            query_vector=vector,
            limit=top_k,
            query_filter=filter
        )

