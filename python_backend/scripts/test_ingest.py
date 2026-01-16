from vectorstore.upsert_chunks import ingest_project

if __name__ == "__main__":
    # Путь к Kotlin проекту
    ingest_project("../kotlin_project/src/main/kotlin", "demo-module")

