#!/usr/bin/env python3
import os
import uuid
import requests
import json
import urllib3
import base64

# Отключаем предупреждения SSL (только временно)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

GIGACHAT_CLIENT_ID = os.environ.get("GIGACHAT_CLIENT_ID")
GIGACHAT_CLIENT_SECRET = os.environ.get("GIGACHAT_CLIENT_SECRET")
API_KEY = f"{GIGACHAT_CLIENT_ID}:{GIGACHAT_CLIENT_SECRET}"

MODEL = "gigachat-test"  # замените на нужную модель
PR_DATA_FILE = "pr_data/rag_context.json"

def get_gigachat_token(client_id, client_secret):
    # Формируем корректный Basic Auth
    credentials = f"{client_id}:{client_secret}"
    encoded_credentials = base64.b64encode(credentials.encode()).decode()

    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Accept": "application/json",
        "RqUID": str(uuid.uuid4()),
        "Authorization": f"Basic {encoded_credentials}"
    }
    data = "scope=GIGACHAT_API_PERS"

    r = requests.post(
        "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
        headers=headers,
        data=data,
        verify=False  # временно отключаем SSL
    )
    r.raise_for_status()
    token_response = r.json()
    return token_response["accessToken"]

def generate_pr_review(token, messages):
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "X-Request-Id": str(uuid.uuid4()),
        "Authorization": f"Bearer {token}"
    }
    payload = {
        "model": MODEL,
        "messages": [{"role": role, "content": content} for role, content in messages],
        "max_tokens": 512,
        "repetition_penalty": 1.0
    }

    r = requests.post(
        "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
        headers=headers,
        json=payload,
        verify=False  # временно отключаем SSL
    )
    r.raise_for_status()
    response = r.json()

    if response.get("status") and response["status"] != 200:
        return f"Ошибка API ({response['status']}): {response.get('message', 'Неизвестная ошибка')}"

    choices = response.get("choices")
    if choices and len(choices) > 0:
        return choices[0]["message"]["content"]
    return "Не удалось получить ответ"

def main():
    print("=== Генерация PR-ревью через GigaChat ===")
    token = get_gigachat_token(GIGACHAT_CLIENT_ID, GIGACHAT_CLIENT_SECRET)

    # Загружаем локальные данные RAG
    with open(PR_DATA_FILE, "r", encoding="utf-8") as f:
        context = json.load(f)

    messages = [
        ("system", "Ты — ассистент. Отвечай строго на основе контекста."),
        ("user", f"Проанализируй PR и дай текст ревью с замечаниями:\n{json.dumps(context, ensure_ascii=False)}")
    ]

    answer = generate_pr_review(token, messages)
    print("\n=== Ответ ассистента ===")
    print(answer)

if __name__ == "__main__":
    main()
