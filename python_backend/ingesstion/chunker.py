import os
import re
from typing import List, Dict

from embeddings.preprocess import format_code_chunk

KOTLIN_FILE_EXTENSIONS = [".kt", ".kts"]

def get_kotlin_files(root_dir: str) -> List[str]:
    kotlin_files = []
    for dirpath, _, filenames in os.walk(root_dir):
        for f in filenames:
            if any(f.endswith(ext) for ext in KOTLIN_FILE_EXTENSIONS):
                kotlin_files.append(os.path.join(dirpath, f))
    return kotlin_files

def parse_kotlin_file(file_path: str) -> List[Dict]:
    """
    Возвращает список сущностей в файле:
    - class
    - interface
    - object
    - function
    """
    entities = []
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Простые regex для классов, функций и объектов
    class_pattern = re.compile(r"(class|interface|object)\s+(\w+)", re.MULTILINE)
    func_pattern = re.compile(r"fun\s+(\w+)\s*\(", re.MULTILINE)

    for m in class_pattern.finditer(content):
        entities.append({
            "entity_type": m.group(1),
            "entity_name": m.group(2),
            "content": content[m.start():m.end()]
        })

    for m in func_pattern.finditer(content):
        entities.append({
            "entity_type": "function",
            "entity_name": m.group(1),
            "content": content[m.start():m.end()]
        })

    return entities

def create_chunks_from_file(file_path: str, module_name: str) -> List[Dict]:
    chunks = []
    for entity in parse_kotlin_file(file_path):
        chunk_text = format_code_chunk(
            path=file_path,
            module=module_name,
            entity_type=entity["entity_type"],
            entity_name=entity["entity_name"],
            content=entity["content"]
        )
        chunks.append({
            "text": chunk_text,
            "metadata": {
                "path": file_path,
                "module": module_name,
                "entity_type": entity["entity_type"],
                "entity_name": entity["entity_name"]
            }
        })
    return chunks

