from typing import List, Sequence
from sentence_transformers import SentenceTransformer
from embeddings.service import EmbeddingService, EmbeddingConfig

class BGEEmbeddingService(EmbeddingService):
    def __init__(self, config: EmbeddingConfig):
        self.config = config
        self.model = SentenceTransformer(
            config.model_name,
            device="cuda" if self._has_cuda() else "cpu"
        )
        # проверка размерности
        test_vec = self.model.encode(
            "embedding-dimension-check",
            normalize_embeddings=config.normalize
        )
        if len(test_vec) != config.dimension:
            raise ValueError(
                f"Embedding dimension mismatch: expected {config.dimension}, got {len(test_vec)}"
            )

    def embed_chunks(self, texts: Sequence[str]) -> List[list[float]]:
        if not texts:
            return []
        vectors = self.model.encode(
            list(texts),
            batch_size=self.config.batch_size,
            normalize_embeddings=self.config.normalize,
            show_progress_bar=False,
        )
        return [vec.tolist() for vec in vectors]

    def embed_query(self, text: str) -> list[float]:
        return self.model.encode(text, normalize_embeddings=self.config.normalize).tolist()

    @staticmethod
    def _has_cuda() -> bool:
        try:
            import torch
            return torch.cuda.is_available()
        except ImportError:
            return False

