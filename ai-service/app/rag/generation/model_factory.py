from langchain_ollama import ChatOllama


def create_chat_model(
        model_name: str,
        base_url: str,
):

    return ChatOllama(
        model=model_name,
        base_url=base_url,
        temperature=0,
        num_predict=1200,
    )