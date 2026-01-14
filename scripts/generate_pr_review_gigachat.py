#!/usr/bin/env python3
import os
import json
import urllib3
import requests
import uuid
import base64
import warnings
#!/usr/bin/env python3
import os
import json
import uuid
import requests
import warnings

# Отключаем предупреждения SSL (временное решение для CI с самоподписанными сертификатами)
from requests.packages.urllib3.exceptions import InsecureRequestWarning
warnings.simplefilter('ignore', InsecureRequestWarning)

# Переменные окружения (устанавливаем через GitHub Secrets)
GIGACHAT_CLIENT_ID = os.environ.get("GIGACHAT_CLIENT_ID")
GIGACHAT_CLIENT_SECRET = os.environ.get("GIGACHAT_CLIENT_SECRET")

# Файл с контекстом PR, который CI положил на шаге RAG
PR_DATA_FILE = "pr_data/rag_context.json"

# URL для получения токена
TOKEN_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
# URL для GigaChat
GIGACHAT_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions"

MODEL = "GigaChat-2"

def get_gigachat_token(client_id: str, client_secret: str) -> str:
    """
    Получение access_token для GigaChat API.
    """
    data = {
        "scope": "GIGACHAT_API_PERS"
    }
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Accept": "application/json",
        "RqUID": str(uuid.uuid4()),
        "Authorization": f"Basic {client_secret}"  # в старом коде использовался client_secret
    }
    r = requests.post(TOKEN_URL, data=data, headers=headers, verify=False)
    print("Status code:", r.status_code)
    print("Response body:", r.text)

    if r.status_code != 200:
        raise ValueError(f"Не удалось получить токен. HTTP {r.status_code}: {r.text}")

    token_response = r.json()
    # В GigaChat токен приходит как access_token
    if "access_token" not in token_response:
        raise ValueError(f"Не удалось получить access_token. Ответ сервера: {token_response}")

    return token_response["access_token"]
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
    if not os.path.exists(PR_DATA_FILE):
        print(f"Файл с контекстом PR не найден: {PR_DATA_FILE}")
        exit(1)

    with open(PR_DATA_FILE, "r", encoding="utf-8") as f:
        pr_context = json.load(f)

    # Преобразуем в текст для отправки в GigaChat
    messages = [{"role": "system", "content": "Ты ассистент для кода."},
                {"role": "user", "content": json.dumps(pr_context, ensure_ascii=False)}]

    token = get_gigachat_token(GIGACHAT_CLIENT_ID, GIGACHAT_CLIENT_SECRET)

    payload = {
        "model": MODEL,
        "messages": messages,
        "max_tokens": 512,
        "repetition_penalty": 1.0
    }

    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "X-Request-Id": str(uuid.uuid4()),
        "Authorization": f"Bearer {token}"
    }

    response = requests.post(GIGACHAT_URL, headers=headers, json=payload, verify=False)
    if response.status_code != 200:
        print(f"Ошибка при вызове GigaChat API: {response.status_code} {response.text}")
        exit(1)

    resp_json = response.json()
    answer = resp_json.get("choices", [{}])[0].get("message", {}).get("content")
    usage = resp_json.get("usage", {})
    total_tokens = usage.get("total_tokens")
    prompt_tokens = usage.get("prompt_tokens")
    completion_tokens = usage.get("completion_tokens")

    if total_tokens is not None:
        print(f"📊 Использовано токенов: {total_tokens} (prompt: {prompt_tokens}, completion: {completion_tokens})")

    print("\n=== PR REVIEW ===\n")
    print(answer or "Не удалось получить ответ от GigaChat.")

if __name__ == "__main__":
    main()