import sys
import requests

EMBED_URL = "http://localhost:11434/api/embeddings"  # локальный Ollama
MODEL = "nomic-embed-text"

if len(sys.argv) < 2:
    print("Ошибка: не указан вопрос", file=sys.stderr)
    sys.exit(1)

question = " ".join(sys.argv[1:])

try:
    resp = requests.post(
        EMBED_URL,
        json={"model": MODEL, "prompt": question},
        timeout=60
    )
    resp.raise_for_status()
    embedding = resp.json()["embedding"]
    # выводим embedding через запятую для Kotlin
    print(",".join(map(str, embedding)))
except Exception as e:
    print(f"Ошибка при получении embedding: {e}", file=sys.stderr)
    sys.exit(1)
