# import json
# import faiss
# import numpy as np
#
# with open("embeddings.json", "r") as f:
#     data = json.load(f)
#
# vectors = np.array([x["embedding"] for x in data]).astype("float32")
#
# dim = vectors.shape[1]
# index = faiss.IndexFlatL2(dim)
# index.add(vectors)
#
# faiss.write_index(index, "index.faiss")
#
# print(f"Saved FAISS index with {index.ntotal} vectors")

import os
import json
import faiss
import numpy as np
import requests
import re

DOCS_DIR = "docs"
MODEL = "nomic-embed-text"
CHUNK_SIZE = 300
OVERLAP = 80
OLLAMA_URL = "http://localhost:11434/api/embeddings"

def tokenize(text):
    return re.findall(r"\w+", text.lower())

def chunk_tokens(tokens, chunk_size, overlap):
    chunks = []
    start = 0
    while start < len(tokens):
        end = min(start + chunk_size, len(tokens))
        chunks.append(" ".join(tokens[start:end]))
        if end == len(tokens):
            break
        start += chunk_size - overlap
    return chunks

def get_embedding(text):
    r = requests.post(
        OLLAMA_URL,
        json={"model": MODEL, "prompt": text},
        timeout=120
    )
    r.raise_for_status()
    return r.json()["embedding"]

def main():
    embeddings = []
    all_vectors = []

    idx = 0

    for filename in os.listdir(DOCS_DIR):
        if not filename.endswith(".txt"):
            continue

        path = os.path.join(DOCS_DIR, filename)

        with open(path, "r", encoding="utf-8") as f:
            text = f.read()

        tokens = tokenize(text)
        chunks = chunk_tokens(tokens, CHUNK_SIZE, OVERLAP)

        for i, chunk in enumerate(chunks):
            emb = get_embedding(chunk)

            embeddings.append({
                "id": idx,
                "source": f"{filename}#chunk{i}",
                "text": chunk,
                "embedding": emb
            })

            all_vectors.append(emb)
            idx += 1

    vectors = np.array(all_vectors, dtype="float32")
    dim = vectors.shape[1]

    index = faiss.IndexFlatL2(dim)
    index.add(vectors)

    faiss.write_index(index, "index.faiss")

    with open("embeddings.json", "w", encoding="utf-8") as f:
        json.dump(embeddings, f, ensure_ascii=False, indent=2)

    print(f"Готово. Чанков: {len(embeddings)}")

if __name__ == "__main__":
    main()
