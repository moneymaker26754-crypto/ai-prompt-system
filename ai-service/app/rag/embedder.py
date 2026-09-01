class EmbeddingService:

    def __init__(
            self,
            ollama_client,
            model: str,
            batch_size: int = 32
    ):
        self.ollama_client = ollama_client
        self.model = model
        self.batch_size = batch_size

    async def embed_documents(self, texts:list[str],) -> list[list[float]]:

        result: list[list[float]] = []

        for start in range(0, len(texts), self.batch_size):

            batch = texts[start:start + self.batch_size]

            embeddings = await self.ollama_client.embed(
                batch,
                model=self.model,
            )

            result.extend(embeddings)

        return result


    async def embed_query(self, query: str) -> list[float]:

        embeddings = await self.ollama_client.embed(
            [query],
            model=self.model,
        )

        return embeddings[0]