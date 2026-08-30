"""
Centralised configuration for the Cheaply scraper service.

Everything the service can be tuned with is read here, once, at import time,
so that no other module reaches into os.environ directly.
"""

import logging
import os
from pathlib import Path

from dotenv import load_dotenv

_ENV_PATH = Path(__file__).resolve().parent.parent / ".env"
load_dotenv(dotenv_path=_ENV_PATH)


def _int_env(name: str, default: int, minimum: int = 1) -> int:
    """Read an integer setting, falling back to the default if it is unusable."""
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        value = int(raw)
    except ValueError:
        logging.getLogger("cheaply.scraper").warning(
            "%s=%r is not an integer; using %d", name, raw, default
        )
        return default
    if value < minimum:
        logging.getLogger("cheaply.scraper").warning(
            "%s=%d is below the minimum of %d; using %d", name, value, minimum, minimum
        )
        return minimum
    return value


def _bool_env(name: str, default: bool = False) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in ("true", "1", "yes", "on")


def _list_env(name: str) -> list[str]:
    raw = os.getenv(name, "")
    return [item.strip() for item in raw.split(",") if item.strip()]


# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()

logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

logger = logging.getLogger("cheaply.scraper")

# ---------------------------------------------------------------------------
# Server
# ---------------------------------------------------------------------------
DEBUG = _bool_env("FLASK_DEBUG", False)
HOST = os.getenv("HOST", "0.0.0.0")
PORT = _int_env("PORT", 5000)

# ---------------------------------------------------------------------------
# Access control
# ---------------------------------------------------------------------------
# This service drives real browsers on demand, which makes an open endpoint an
# expensive thing to leave lying around. When an API key is configured every
# request must present it; the key is the only thing standing between this
# process and being used as someone else's scraping proxy.
API_KEY = os.getenv("SCRAPER_API_KEY", "").strip()

# Empty by default. Nothing but the backend should be talking to this service,
# and the backend is not a browser, so it never sends an Origin header.
CORS_ORIGINS = _list_env("CORS_ORIGINS")

MAX_QUERY_LENGTH = _int_env("MAX_QUERY_LENGTH", 120)

# ---------------------------------------------------------------------------
# Chrome / Selenium
# ---------------------------------------------------------------------------
CHROME_BIN = os.getenv("CHROME_BIN")

# ---------------------------------------------------------------------------
# Scraper tuning
# ---------------------------------------------------------------------------
MAX_PRODUCTS_PER_STORE = _int_env("MAX_PRODUCTS_PER_STORE", 10)

# How long a single page load may take.
SCRAPER_TIMEOUT_SECONDS = _int_env("SCRAPER_TIMEOUT_SECONDS", 15)


# Number of *additional* attempts after the first one fails. MAX_RETRIES=1
# means two attempts in total.
MAX_RETRIES = _int_env("MAX_RETRIES", 1, minimum=0)

# The ceiling on one /scrape call, enforced per store. The backend's read
# timeout is set above this value so the two services agree on how long a
# request may take; raising one without the other reintroduces the bug where
# the caller gave up while the work carried on.
SCRAPER_TOTAL_BUDGET_SECONDS = _int_env("SCRAPER_TOTAL_BUDGET_SECONDS", 45)

# Caps how many headless browsers this process will run at once, across all
# concurrent requests. Each Chrome instance costs real memory, and without a
# ceiling a burst of traffic will exhaust the container rather than queue.
MAX_CONCURRENT_BROWSERS = _int_env("MAX_CONCURRENT_BROWSERS", 4)

