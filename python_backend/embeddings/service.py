from dataclasses import dataclass
from typing import List, Sequence

@dataclass(frozen=True)
class EmbeddingConfig:
    model_name: str
    dimension: int
    version: str
    normalize: bool = True
    batch_size: int = 32

class EmbeddingService:
    def embed_chunks(self, texts: Sequence[str]) -> List[list[float]]:
        raise NotImplementedError

    def embed_query(self, text: str) -> list[float]:
        raise NotImplementedError

