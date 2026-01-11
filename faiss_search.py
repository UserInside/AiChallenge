import sys
import json
import faiss
import numpy as np
import warnings

warnings.filterwarnings("ignore")

def main():
    # Читаем embedding из stdin
    query_embedding = json.loads(sys.stdin.read())
    vector = np.array([query_embedding], dtype="float32")

    # Загружаем FAISS index
    index = faiss.read_index("index.faiss")

    # Количество ближайших соседей
    k = 15
    distances, indices = index.search(vector, k)

    # Загружаем метаданные чанков
    # Ожидаемый формат embeddings.json:
    # [
    #   {
    #     "id": 0,
    #     "text": "...",
    #     "source": "docs/file1.md"
    #   }
    # ]
    with open("embeddings.json", "r", encoding="utf-8") as f:
        data = json.load(f)

    results = []

    for idx, score in zip(indices[0], distances[0]):
        if idx < 0:
            continue

        item = data[idx]

        results.append({
            "id": item.get("id", idx),
            "text": item["text"],
            "score": float(score),
            "source": item.get("source", "unknown")
        })

    # stdout — ТОЛЬКО JSON
    sys.stdout.write(json.dumps(results, ensure_ascii=False))

if __name__ == "__main__":
    main()
