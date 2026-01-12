import sys
from transformers import AutoModelForCausalLM, AutoTokenizer

# Загружаем локальную модель
MODEL_NAME = "databricks/dolly-v2-3b"
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = AutoModelForCausalLM.from_pretrained(MODEL_NAME)

def main():
    prompt = sys.stdin.read()

    inputs = tokenizer(prompt, return_tensors="pt")
    outputs = model.generate(**inputs, max_new_tokens=300)
    answer = tokenizer.decode(outputs[0], skip_special_tokens=True)

    print(answer)

if __name__ == "__main__":
    main()
