import json
from gigachat import Gigachat  # библиотека для Gigachat API
from pathlib import Path

# Загружаем ключ из GitHub secrets
import os
GIGACHAT_API_KEY = os.environ.get("GIGACHAT_API_KEY")

client = Gigachat(api_key=GIGACHAT_API_KEY)

# Загружаем документацию
def load_docs(path="docs/"):
    docs = []
    for file in Path(path).glob("*.md"):
        docs.append(file.read_text())
    return docs

# Загружаем JSON MCP
with open("mcp.json") as f:
    mcp_data = json.load(f)

# Мини-функция поиска по документации (простая RAG)
def search_docs(query, docs):
    results = [doc for doc in docs if query.lower() in doc.lower()]
    return "\n".join(results[:3])  # берем максимум 3 совпадения

# Основная функция
def answer_question(query):
    docs = load_docs()
    doc_context = search_docs(query, docs)

    # Добавляем MCP данные
    mcp_context = json.dumps(mcp_data, indent=2)

    prompt = f"""
    Пользователь спросил: "{query}"
    Используй документацию и FAQ для ответа:
    {doc_context}

    Данные пользователей и тикетов:
    {mcp_context}
    """

    response = client.chat(prompt)
    return response
