"""
Cheaply - Flask application factory for the scraper service.
"""

from flask import Flask
from flask_cors import CORS

from app.config import CORS_ORIGINS
from app.routes.scraper_api import scraper_bp


def create_app() -> Flask:
    """Create, configure, and return the Flask application."""
    application = Flask(__name__)

    # No secret key is configured on purpose: this is a stateless JSON API
    # consumed by the Spring Boot backend, with no sessions or cookies for
    # Flask to sign. The previous hard-coded default served no function and
    # published a would-be secret in the repository.

    # CORS is empty by default. Server-to-server calls are not subject to CORS
    # at all, so the backend needs no entry here; an origin list only matters
    # if a browser front-end ever calls this service directly.
    if CORS_ORIGINS:
        CORS(application, origins=CORS_ORIGINS)

    application.register_blueprint(scraper_bp)
    return application
