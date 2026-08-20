import io
from typing import List, Tuple
from pypdf import PdfReader


class PdfExtractionError(Exception):
    """Raised when PDF extraction fails or yields no usable text."""
    pass


def extract_text_from_pdf(pdf_bytes: bytes) -> Tuple[str, List[Tuple[int, str]]]:
    """
    Extracts text from PDF bytes page by page.
    Returns (full_text, pages) where pages is a list of (page_number_1_indexed, page_text).
    Raises PdfExtractionError if unparseable or if the document contains no extractable text (FR-004).
    """
    if not pdf_bytes or len(pdf_bytes) == 0:
        raise PdfExtractionError("PDF file is empty")

    try:
        reader = PdfReader(io.BytesIO(pdf_bytes))
        if len(reader.pages) == 0:
            raise PdfExtractionError("PDF contains zero pages")

        pages: List[Tuple[int, str]] = []
        full_text_parts: List[str] = []

        for idx, page in enumerate(reader.pages):
            page_num = idx + 1
            page_text = page.extract_text() or ""
            page_text = page_text.strip()
            if page_text:
                pages.append((page_num, page_text))
                full_text_parts.append(page_text)

        full_text = "\n\n".join(full_text_parts).strip()
        if not full_text:
            raise PdfExtractionError("PDF contains no extractable text layer (scanned or image-only)")

        return full_text, pages

    except PdfExtractionError:
        raise
    except Exception as e:
        raise PdfExtractionError(f"Failed to parse PDF: {str(e)}") from e
