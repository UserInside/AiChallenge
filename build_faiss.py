import json
import faiss
import numpy as np

with open("embeddings.json", "r") as f:
    data = json.load(f)

vectors = np.array([x["embedding"] for x in data]).astype("float32")

dim = vectors.shape[1]
index = faiss.IndexFlatL2(dim)
index.add(vectors)

faiss.write_index(index, "index.faiss")

print(f"Saved FAISS index with {index.ntotal} vectors")