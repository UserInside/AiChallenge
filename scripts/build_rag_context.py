#!/usr/bin/env python3

import json
import os
from pathlib import Path

PR_FILES_JSON = Path("pr_data/pr_files_raw.json")
FILES_DIR = Path("pr_data/files")
OUTPUT_FILE = Path("pr_data/rag_context.json")

MAX_LINES_CODE = 400
MAX_LINES_DIFF = 500
MAX_LINES_DOC = 800


def chunk_lines(text: str, max_lines: int):
    lines = text.splitlines()
    for i in range(0, len(lines), max_lines):
        yield "\n".join(lines[i:i + max_lines])


def load_pr_files():
    with PR_FILES_JSON.open("r", encoding="utf-8") as f:
        return json.load(f)


def is_doc_file(filename: str) -> bool:
    name = filename.lower()
    return (
        name.endswith("readme.md")
        or name.startswith("docs/")
        or name.endswith(".md")
    )


def main():
    rag_chunks = []

    pr_files = load_pr_files()

    # 1. DIFFs
    for f in pr_files:
        patch = f.get("patch")
        if not patch:
            continue

        for chunk in chunk_lines(patch, MAX_LINES_DIFF):
            rag_chunks.append({
                "type": "diff",
                "file": f["filename"],
                "content": chunk,
                "source": "pr"
            })

    # 2. FULL FILE CONTENT
    for file_path in FILES_DIR.glob("*"):
        try:
            text = file_path.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue

        original_name = file_path.name

        if is_doc_file(original_name):
            max_lines = MAX_LINES_DOC
            chunk_type = "doc"
        else:
            max_lines = MAX_LINES_CODE
            chunk_type = "code"

        for chunk in chunk_lines(text, max_lines):
            rag_chunks.append({
                "type": chunk_type,
                "file": original_name,
                "content": chunk,
                "source": "pr"
            })

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_FILE.write_text(
        json.dumps(rag_chunks, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    print(f"RAG context built: {OUTPUT_FILE}")
    print(f"Chunks count: {len(rag_chunks)}")


if __name__ == "__main__":
    main()
