from app.rag.generation.context_builder import BuiltContext
from app.rag.generation.schemas import GroundedAnswer


class CitationValidationError(RuntimeError):
    pass


class CitationValidator:

    def validate(
            self,
            answer: GroundedAnswer,
            context: BuiltContext,
    ) -> GroundedAnswer:

        valid_ids = {
            source.source_id for source in context.sources
        }

        if not answer.answerable:

            return GroundedAnswer(
                answerable=False,
                answer="",
                citations=[],
                reason=answer.reason,
            )

        if not answer.citations:
            raise CitationValidationError(
                "Answerable response "
                "contains no citations."
            )

        invalid = (
            set(answer.citations) - valid_ids
        )

        if invalid:
            raise CitationValidationError(
                f"Invalid citations: {invalid}"
            )

        return answer