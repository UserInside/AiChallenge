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

    k = 5
    distances, indices = index.search(vector, k)

    # Загружаем тексты
    with open("embeddings.json", "r") as f:
        data = json.load(f)

    results = []
    for idx, score in zip(indices[0], distances[0]):
        results.append({
            "text": data[idx]["text"],
            "score": float(score)
        })

    # ВАЖНО: stdout = ТОЛЬКО JSON
    sys.stdout.write(json.dumps(results))

if __name__ == "__main__":
    main()