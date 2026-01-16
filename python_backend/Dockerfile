# python_backend/Dockerfile
FROM python:3.11-slim

# рабочая директория внутри контейнера
WORKDIR /app

# копируем зависимости
COPY pyproject.toml .
COPY requirements.txt .

# устанавливаем зависимости
RUN pip install --upgrade pip
RUN if [ -f requirements.txt ]; then pip install -r requirements.txt; fi

# копируем весь backend
COPY . .

# expose порт для FastAPI
EXPOSE 8000

# запуск FastAPI
CMD ["python", "-m", "uvicorn", "api.server:app", "--host", "0.0.0.0", "--port", "8000"]

