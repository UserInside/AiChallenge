import json
import faiss
import numpy as np
from pathlib import Path
import subprocess
import requests

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src" / "main" / "kotlin"
OUT = ROOT / "assistant"

EMBED_URL = "http://localhost:11434/api/embeddings"
MODEL = "nomic-embed-text"


def embed(text: str):
    r = requests.post(
        EMBED_URL,
        json={"model": MODEL, "prompt": text},
        timeout=60
    )
    return r.json()["embedding"]


def extract_chunks():
    chunks = []
    idx = 0

    for path in SRC.glob("*.kt"):
        content = path.read_text(encoding="utf-8")

        for block in content.split("\n\n"):
            block = block.strip()
            if len(block) < 40:
                continue

            chunks.append({
                "id": idx,
                "text": block,
                "source": f"{path.name}",
                "type": "code"
            })
            idx += 1

    return chunks


def main():
    print("→ Extracting code chunks")
    chunks = extract_chunks()

    print(f"→ Chunks: {len(chunks)}")

    vectors = []
    for c in chunks:
        vec = embed(c["text"])
        vectors.append(vec)

    dim = len(vectors[0])
    index = faiss.IndexFlatL2(dim)
    index.add(np.array(vectors, dtype="float32"))

    OUT.mkdir(exist_ok=True)

    faiss.write_index(index, str(OUT / "assistant.faiss"))

    with open(OUT / "assistant_embeddings.json", "w") as f:
        json.dump(chunks, f, ensure_ascii=False, indent=2)

    print("✓ Assistant index built")


if __name__ == "__main__":
    main()

