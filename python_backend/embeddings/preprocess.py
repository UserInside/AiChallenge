def format_code_chunk(path: str, module: str, entity_type: str, entity_name: str, content: str) -> str:
    return f"""// FILE: {path}
// MODULE: {module}
// ENTITY: {entity_type} {entity_name}

{content.strip()}
"""

