
"""
PII detection and anonymization using Microsoft Presidio.

When enabled (PII_DETECTION_ENABLED=true), each knowledge event's text is
scanned for personally identifiable information before chunking. Detected
entities — names, emails, phone numbers, IP addresses, credit card numbers —
are replaced with their type placeholder (e.g. <PERSON>, <EMAIL_ADDRESS>).

This protects PII from being embedded into vectors and surfaced in query
answers. The original text is never persisted after this stage.

Presidio requires a spaCy language model. The Dockerfile pre-downloads
en_core_web_sm at build time to avoid startup latency.
"""

import logging
from functools import lru_cache

logger = logging.getLogger(__name__)

# Entities to detect and anonymize. Add or remove based on your compliance requirements.
ENTITIES_TO_DETECT = [
    "PERSON",
    "EMAIL_ADDRESS",
    "PHONE_NUMBER",
    "CREDIT_CARD",
    "IP_ADDRESS",
    "US_SSN",
    "IBAN_CODE",
    "MEDICAL_LICENSE",
]


@lru_cache(maxsize=1)
def _get_engines():
    """
    Lazily initialize Presidio engines on first use.
    Cached so initialization happens once per process, not per message.
    """
    from presidio_analyzer import AnalyzerEngine
    from presidio_anonymizer import AnonymizerEngine

    analyzer = AnalyzerEngine()
    anonymizer = AnonymizerEngine()
    logger.info("Presidio PII detection engines initialized")
    return analyzer, anonymizer


def anonymize_pii(text: str) -> tuple[str, list[str]]:
    """
    Scan text for PII and replace detected entities with type placeholders.

    Returns:
        (anonymized_text, list_of_detected_entity_types)
        If no PII is found, the original text and an empty list are returned.
        If Presidio is unavailable, the original text is returned unmodified
        and a warning is logged — the pipeline never fails due to PII detection.
    """
    if not text or not text.strip():
        return text, []

    try:
        analyzer, anonymizer = _get_engines()

        results = analyzer.analyze(
            text=text,
            language="en",
            entities=ENTITIES_TO_DETECT,
            score_threshold=0.7,
        )

        if not results:
            return text, []

        anonymized = anonymizer.anonymize(text=text, analyzer_results=results)
        entity_types = list({r.entity_type for r in results})

        logger.debug(
            "PII detected and anonymized: entities=%s, chars_before=%d, chars_after=%d",
            entity_types, len(text), len(anonymized.text),
        )
        return anonymized.text, entity_types

    except ImportError:
        logger.warning(
            "presidio-analyzer is not installed — PII detection skipped. "
            "Install with: pip install presidio-analyzer presidio-anonymizer"
        )
        return text, []
    except Exception as exc:
        logger.warning("PII detection failed (text passed through unmodified): %s", exc)
        return text, []
