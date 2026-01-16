from embeddings.service import EmbeddingConfig

# Публичная embedding-модель для кода
EMBEDDING_CONFIG = EmbeddingConfig(
    model_name="microsoft/codebert-base",  # embedding модель для кода
    dimension=768,                         # размерность модели
    version="v1",
    normalize=True,
    batch_size=16
)

