import sys
import json
import numpy as np
import faiss
from pathlib import Path

EMBEDDINGS_FILE = "assistant_embeddings.json"
INDEX_FILE = "index.faiss"
TOP_K_DEFAULT = 5

def load_embeddings():
    with open(EMBEDDINGS_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)
    texts = [item["text"] for item in data]
    embeddings = np.array([item["embedding"] for item in data], dtype="float32")
    return texts, embeddings

def load_or_build_index(embeddings):
    dim = embeddings.shape[1]
    if Path(INDEX_FILE).exists():
        index = faiss.read_index(INDEX_FILE)
    else:
        index = faiss.IndexFlatL2(dim)
        index.add(embeddings)
        faiss.write_index(index, INDEX_FILE)
    return index

def search(question_embedding, index, texts, top_k=TOP_K_DEFAULT):
    distances, indices = index.search(np.array([question_embedding], dtype="float32"), top_k)
    results = []
    for idx, dist in zip(indices[0], distances[0]):
        results.append({
            "id": str(idx),
            "text": texts[idx],
            "score": float(dist)
        })
    return results

def get_embedding(text):
    """Тестовая функция: простой вектор на основе кодов символов."""
    dim = 20
    vec = [0.0]*dim
    for i, c in enumerate(text):
        vec[i % dim] += ord(c)/255.0
    return vec


def main():
    if len(sys.argv) < 2:
        print(json.dumps([]))
        return
    question = sys.argv[1]
    top_k = int(sys.argv[2]) if len(sys.argv) > 2 else TOP_K_DEFAULT
    texts, embeddings = load_embeddings()
    index = load_or_build_index(embeddings)
    question_emb = get_embedding(question)
    results = search(question_emb, index, texts, top_k)
    print(json.dumps(results, ensure_ascii=False))

if __name__ == "__main__":
    main()
