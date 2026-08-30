"""
Scraper API routes - consumed by the Spring Boot backend.
"""

import logging
import time

from flask import Blueprint, jsonify, request

from app import config
from app.services.scraper import scrape_data

logger = logging.getLogger("cheaply.scraper_api")

scraper_bp = Blueprint("scraper_api", __name__)


@scraper_bp.route("/scrape", methods=["POST"])
def handle_scrape():
    """
    Accepts JSON: {"query": "..."}
    Returns JSON: {"query": ..., "products": [...], "stores": [...], "duration_ms": N}

    Each entry in "stores" is {name, status, count, error} with status one of
    ok | empty | failed. This exists so the caller can tell a store with no
    matching products apart from a store that broke - previously both cases
    produced the same bare empty list and outages went unnoticed.
    """
    # The key is read from config at request time (not captured at import) so
    # tests and config reloads see the current value. When no key is set the
    # check is skipped, which keeps local development friction-free - but any
    # networked deployment should set one, because this endpoint starts real
    # browsers on demand. /health stays open for liveness probes.
    if config.API_KEY and request.headers.get("X-API-Key") != config.API_KEY:
        logger.warning("Rejected /scrape request: bad or missing API key")
        return jsonify({"error": "Invalid or missing API key"}), 401

    data = request.get_json(silent=True) or {}
    raw_query = data.get("query")
    query = raw_query.strip() if isinstance(raw_query, str) else ""

    if not query:
        # Form-data fallback kept for manual curl testing.
        query = (request.form.get("query") or "").strip()

    if not query:
        return jsonify({"error": "Query parameter is required"}), 400

    if len(query) > config.MAX_QUERY_LENGTH:
        query = query[: config.MAX_QUERY_LENGTH].strip()

    started = time.monotonic()
    try:
        logger.info("Executing scrape for query: '%s'", query)
        products, stores = scrape_data(query)
        duration_ms = int((time.monotonic() - started) * 1000)
        logger.info(
            "Returning %d products for '%s' in %dms (%s)",
            len(products), query, duration_ms,
            ", ".join(f"{s['name']}={s['status']}" for s in stores),
        )
        return jsonify({
            "query": query,
            "products": products,
            "stores": stores,
            "duration_ms": duration_ms,
        }), 200
    except Exception:
        # Deliberately no exception detail in the body: str(e) from Selenium
        # can carry local paths and driver internals that do not belong in an
        # HTTP response. The full traceback is in the logs.
        logger.error("Scraping failed for query '%s'", query, exc_info=True)
        return jsonify({"error": "Failed to scrape product data"}), 500


@scraper_bp.route("/health", methods=["GET"])
def health_check():
    return jsonify({"status": "UP", "service": "cheaply-scraper"}), 200
