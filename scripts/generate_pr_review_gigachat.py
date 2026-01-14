#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import json
import requests

# ==== Настройки ====
# GitHub Actions передаст их через Secrets
GIGACHAT_CLIENT_ID = os.environ.get("GIGACHAT_CLIENT_ID")
GIGACHAT_CLIENT_SECRET = os.environ.get("GIGACHAT_CLIENT_SECRET")

# Путь к локальному RAG контексту
RAG_CONTEXT_FILE = "pr_data/rag_context.json"

# ==== Функции ====

def get_gigachat_token(client_id, client_secret):
    """Получение access token для GigaChat"""
    import base64
    auth = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
    r = requests.post(
        "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
        headers={
            "Authorization": f"Basic {auth}",
            "Content-Type": "application/x-www-form-urlencoded",
            "RqUID": "ci-pr-review"
        },
        data={"scope": "GIGACHAT_API_PERS"},
        verify=False  # Отключаем проверку SSL (только для CI)
    )
    r.raise_for_status()
    return r.json()["access_token"]


def call_gigachat(token, messages):
    """Вызов GigaChat Chat API"""
    url = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions"
    payload = {
        "model": "GigaChat",
        "messages": messages,
        "temperature": 0.2
    }
    r = requests.post(url, headers={
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
        },
        json=payload,
        verify=False  # Отключаем проверку SSL (только для CI)
    )
    r.raise_for_status()
    return r.json()["choices"][0]["message"]["content"]


def load_rag_context(path):
    """Загрузка локального RAG контекста"""
    if not os.path.exists(path):
        print(f"[WARN] RAG context file not found: {path}")
        return ""
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def main():
    print("=== Генерация PR-ревью через GigaChat ===")

    # Загружаем контекст
    rag_context = load_rag_context(RAG_CONTEXT_FILE)

    # Подготовка системного промпта
    SYSTEM_PROMPT = """
Ты — ассистент для ревью PR.
Используй только предоставленный контекст (код, документация, diff).
Если ответа нет — говори "Не удалось найти ответ".
Составь ревью коротко, с конкретными замечаниями.
"""

    # Ввод user prompt из GitHub Actions
    user_prompt = f"Сделай ревью PR, используя контекст:\n{rag_context}"

    # Получаем токен
    token = get_gigachat_token(GIGACHAT_CLIENT_ID, GIGACHAT_CLIENT_SECRET)

    # Вызываем GigaChat
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_prompt}
    ]

    review = call_gigachat(token, messages)
    print("\n=== PR Review ===\n")
    print(review)
    print("\n=== Конец ревью ===\n")

    # Сохраняем результат
    os.makedirs("pr_data", exist_ok=True)
    with open("pr_data/pr_review.txt", "w", encoding="utf-8") as f:
        f.write(review)
    print("[INFO] Ревью сохранено в pr_data/pr_review.txt")


if __name__ == "__main__":
    main()
