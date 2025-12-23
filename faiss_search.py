import sys
import json
import faiss
import numpy as np

query_embedding = json.loads(sys.stdin.read())

index = faiss.read_index("index.faiss")

vector = np.array([query_embedding], dtype="float32")

k = 5
distances, indices = index.search(vector, k)

with open("embeddings.json", "r") as f:
    data = json.load(f)

results = []
for idx in indices[0]:
    results.append(data[idx]["text"])

print(json.dumps(results))