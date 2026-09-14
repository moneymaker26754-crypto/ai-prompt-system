from app.rag.generation.prompts import GROUNDED_RAG_PROMPT
from app.rag.generation.schemas import GroundedAnswer


class LangChainAnswerGenerator:

    def __init__(
            self,
            llm,
    ):
        self.llm = llm

        structured_llm = (
            llm.with_structured_output(
                GroundedAnswer
            )
        )

        self.chain = (
            GROUNDED_RAG_PROMPT | structured_llm
        )

    async def generate(
            self,
            question: str,
            context: str,
    ) -> GroundedAnswer:

        result = await self.chain.ainvoke(
            {
                "question": question,
                "context": context,
            }
        )

        return result

    async def aclose(self) -> None:
        try:
            await self.llm._async_client.close()
        finally:
            self.llm._client.close()
