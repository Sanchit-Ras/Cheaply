"""
Cheaply — Web scraping module.

Scrapes product data from Amazon India, Flipkart Grocery, BigBasket and
Blinkit using Selenium. Each store is scraped in parallel via ThreadPoolExecutor.
"""

import logging
import re
import threading
import time
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
from functools import wraps

from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

from app.config import (
    CHROME_BIN,
    MAX_CONCURRENT_BROWSERS,
    MAX_PRODUCTS_PER_STORE,
    MAX_RETRIES,
    SCRAPER_TIMEOUT_SECONDS,
    SCRAPER_TOTAL_BUDGET_SECONDS,
)

logger = logging.getLogger("cheaply.scraper")

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
_WEIGHT_RE = re.compile(
    r"(\d+(?:\.\d+)?\s?(?:kg|g|litre|liter|ml|l))\b", re.IGNORECASE
)

_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0"
)

_RETRY_BACKOFF = 2     # seconds multiplier between retries


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def _build_chrome_options() -> Options:
    """Create a configured headless Chrome Options instance."""
    opts = Options()
    opts.add_argument("--headless=new")
    opts.add_argument("--disable-gpu")
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")
    opts.add_argument("--disable-extensions")
    opts.add_argument("--disable-blink-features=AutomationControlled")
    opts.add_argument(f"user-agent={_USER_AGENT}")
    if CHROME_BIN:
        opts.binary_location = CHROME_BIN
    return opts


def _create_driver() -> webdriver.Chrome:
    """Initialise and return a Chrome WebDriver instance."""
    try:
        driver = webdriver.Chrome(service=Service(), options=_build_chrome_options())
        driver.set_page_load_timeout(SCRAPER_TIMEOUT_SECONDS)
        return driver
    except Exception:
        logger.error(
            "Failed to initialise Chrome WebDriver. "
            "Ensure Chrome and ChromeDriver are installed.",
            exc_info=True,
        )
        raise


def _extract_weight(title: str) -> str:
    """Extract weight/volume from a product title string."""
    match = _WEIGHT_RE.search(title)
    return match.group(0) if match else "N/A"


_BROWSER_SLOTS = threading.BoundedSemaphore(MAX_CONCURRENT_BROWSERS)


def _with_retry(func):
    """Retry a store scraper, then re-raise so the caller can report failure.

    The previous version returned [] after the final attempt, which made a
    store that was down indistinguishable from a store with no matches. It now
    raises the last error and lets scrape_data turn it into a per-store status.

    MAX_RETRIES counts additional attempts, so 1 means two attempts in total.

    The semaphore caps how many headless browsers this process runs at once
    across all concurrent requests; excess store scrapes wait for a slot
    instead of exhausting the container. The slot is released during the
    backoff sleep so a retrying store does not starve the others.
    """

    @wraps(func)
    def wrapper(*args, **kwargs):
        attempts = MAX_RETRIES + 1
        last_error = None
        for attempt in range(1, attempts + 1):
            try:
                with _BROWSER_SLOTS:
                    return func(*args, **kwargs)
            except Exception as exc:
                last_error = exc
                if attempt < attempts:
                    wait = _RETRY_BACKOFF * attempt
                    logger.warning(
                        "%s attempt %d/%d failed, retrying in %ds...",
                        func.__name__, attempt, attempts, wait,
                        exc_info=True,
                    )
                    time.sleep(wait)

        logger.error("%s failed after %d attempts", func.__name__, attempts)
        raise last_error

    return wrapper


# ---------------------------------------------------------------------------
# Amazon scraper
# ---------------------------------------------------------------------------
@_with_retry
def scrape_amazon_data(search_query: str) -> list[dict]:
    """Scrape product listings from Amazon India using direct search URL."""
    driver = _create_driver()
    try:
        encoded = urllib.parse.quote_plus(search_query)
        driver.get(f"https://www.amazon.in/s?k={encoded}")
        logger.info("Amazon search page loaded for '%s'", search_query)

        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "div.s-main-slot"))
        )

        products = driver.find_elements(
            By.CSS_SELECTOR,
            "div.s-main-slot div[data-component-type='s-search-result']",
        )

        results: list[dict] = []
        for product in products[:MAX_PRODUCTS_PER_STORE]:
            try:
                # Cards carry two h2 elements: a brand line (a-size-mini)
                # first and the actual product title (a-size-base-plus)
                # second. A bare "h2 span" therefore sometimes returned just
                # the brand ("Conscious Food"), which also lost the pack size
                # that price-per-unit ranking depends on.
                title = ""
                try:
                    title = product.find_element(
                        By.CSS_SELECTOR, "h2.a-size-base-plus span"
                    ).text.strip()
                except Exception:
                    pass
                if not title:
                    title = product.find_element(By.CSS_SELECTOR, "h2 span").text.strip()
                if not title:
                    continue

                try:
                    price = (
                        product.find_element(By.CSS_SELECTOR, "span.a-price-whole")
                        .text.replace("₹", "")
                        .replace(",", "")
                        .strip()
                    )
                except Exception:
                    price = "NA"

                try:
                    link = product.find_element(
                        By.CSS_SELECTOR, "a.a-link-normal.s-no-outline"
                    ).get_attribute("href")
                except Exception:
                    link = ""

                try:
                    image_url = product.find_element(
                        By.CSS_SELECTOR, "img.s-image"
                    ).get_attribute("src")
                except Exception:
                    image_url = ""

                weight = _extract_weight(title)

                results.append(
                    {
                        "title": title,
                        "price": price,
                        "link": link,
                        "image_url": image_url,
                        "weight": weight,
                        "source": "Amazon",
                    }
                )
            except Exception:
                logger.debug("Skipped an Amazon product (missing fields)", exc_info=True)

        logger.info("Scraped %d Amazon products", len(results))
        return results

    finally:
        driver.quit()


# ---------------------------------------------------------------------------
# Flipkart Grocery scraper
# ---------------------------------------------------------------------------
@_with_retry
def scrape_flipkart_data(search_query: str) -> list[dict]:
    """Scrape product listings from Flipkart Grocery."""
    driver = _create_driver()
    try:
        encoded = urllib.parse.quote_plus(search_query)
        url = (
            f"https://www.flipkart.com/search?q={encoded}"
            "&otracker=search&marketplace=GROCERY"
        )
        driver.get(url)
        logger.info("Flipkart Grocery page loaded for '%s'", search_query)

        # --- Handle pincode verification popup ---
        try:
            pincode_input = WebDriverWait(driver, 4).until(
                EC.presence_of_element_located(
                    (By.CSS_SELECTOR, "input[placeholder*='pincode'], input[type='tel']")
                )
            )
            pincode_input.clear()
            pincode_input.send_keys("110001")  # Default Delhi pincode
            pincode_input.send_keys(Keys.RETURN)
            logger.info("Entered pincode to dismiss Flipkart popup")
            time.sleep(1.5)
        except Exception:
            logger.debug("No pincode popup detected, continuing")

        # --- Dismiss any login popup ---
        try:
            close_btn = driver.find_element(
                By.CSS_SELECTOR, "button._2KpZ6l._2doB4z, button[class*='close']"
            )
            close_btn.click()
            logger.debug("Dismissed Flipkart login popup")
        except Exception:
            pass

        # Wait for product cards to appear
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located(
                (By.CSS_SELECTOR, "div[data-id]")
            )
        )

        products = driver.find_elements(By.CSS_SELECTOR, "div[data-id]")

        results: list[dict] = []
        for product in products[:MAX_PRODUCTS_PER_STORE]:
            try:
                # Title extraction
                title = ""
                try:
                    title_el = product.find_element(By.CSS_SELECTOR, ".w_S99S")
                    title = title_el.text.strip()
                except Exception:
                    try:
                        title_el = product.find_element(By.CSS_SELECTOR, "a[title]")
                        title = title_el.get_attribute("title")
                    except Exception:
                        title = product.find_element(
                            By.CSS_SELECTOR, "a.wjcEIp, div._4rR01T, a.IRpwTa"
                        ).text.strip()

                if not title:
                    continue

                # Price extraction
                price = "NA"
                price_selectors = [
                    ".Nx9bqj",
                    "._30jeq3",
                    "div[class*='Nx9']",
                    "div[class*='price']",
                    "span[class*='price']",
                ]
                for sel in price_selectors:
                    try:
                        el = product.find_element(By.CSS_SELECTOR, sel)
                        raw = el.text.strip()
                        if raw and "₹" in raw:
                            price = raw.replace("₹", "").replace(",", "")
                            break
                    except Exception:
                        continue

                # Fallback: search for ₹ symbol anywhere in the card text
                if price == "NA":
                    try:
                        card_text = product.text
                        price_match = re.search(r"₹\s?([\d,]+(?:\.\d+)?)", card_text)
                        if price_match:
                            price = price_match.group(1).replace(",", "")
                    except Exception:
                        pass

                # Link extraction
                try:
                    link_el = product.find_element(By.CSS_SELECTOR, "a[href]")
                    href = link_el.get_attribute("href")
                    link = href if href.startswith("http") else f"https://www.flipkart.com{href}"
                except Exception:
                    link = ""

                # Image extraction
                try:
                    image_url = product.find_element(
                        By.CSS_SELECTOR, "img.DByo9Z, img.DByuf4, img._396cs4"
                    ).get_attribute("src")
                except Exception:
                    try:
                        image_url = product.find_element(
                            By.TAG_NAME, "img"
                        ).get_attribute("src")
                    except Exception:
                        image_url = ""

                weight = _extract_weight(title)
                if weight == "N/A":
                    try:
                        weight = _extract_weight(product.text)
                    except Exception:
                        pass

                results.append(
                    {
                        "title": title,
                        "price": price,
                        "link": link,
                        "image_url": image_url,
                        "weight": weight,
                        "source": "Flipkart",
                    }
                )
            except Exception:
                logger.debug(
                    "Skipped a Flipkart product (missing fields)", exc_info=True
                )

        logger.info("Scraped %d Flipkart products", len(results))
        return results

    finally:
        driver.quit()


# ---------------------------------------------------------------------------
# BigBasket scraper
# ---------------------------------------------------------------------------
@_with_retry
def scrape_bigbasket_data(search_query: str) -> list[dict]:
    """Scrape product listings from BigBasket's search page.

    BigBasket uses styled-components, so class names carry a stable component
    prefix (SKUDeck, BrandName, DeckImage) followed by a build hash - the
    selectors match on the prefix only.
    """
    driver = _create_driver()
    try:
        encoded = urllib.parse.quote_plus(search_query)
        driver.get(f"https://www.bigbasket.com/ps/?q={encoded}")
        logger.info("BigBasket search page loaded for '%s'", search_query)

        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located(
                (By.CSS_SELECTOR, "div[class*='SKUDeck']")
            )
        )

        # Card images load lazily; walk the viewport once so they hydrate.
        for offset in (400, 900, 1400):
            driver.execute_script(f"window.scrollTo(0, {offset})")
            time.sleep(0.7)
        driver.execute_script("window.scrollTo(0, 0)")
        time.sleep(0.5)

        products = driver.find_elements(By.CSS_SELECTOR, "div[class*='SKUDeck']")

        results: list[dict] = []
        for product in products[:MAX_PRODUCTS_PER_STORE]:
            try:
                # Brand and description are separate elements; the full
                # product name is their concatenation.
                brand = ""
                try:
                    brand = product.find_element(
                        By.CSS_SELECTOR, "span[class*='BrandName']"
                    ).text.strip()
                except Exception:
                    pass
                try:
                    description = product.find_element(
                        By.CSS_SELECTOR, "h3.line-clamp-2"
                    ).text.strip()
                except Exception:
                    description = ""
                title = f"{brand} {description}".strip()
                if not title:
                    continue

                # Cards open with a discount badge ("₹19 OFF") that sits
                # before the price block in DOM order, so "first rupee amount"
                # is wrong for discounted items. Take the first amount NOT
                # followed by OFF - that is the selling price; the
                # struck-through MRP comes after it.
                price = "NA"
                price_match = re.search(r"₹\s?([\d,]+(?:\.\d+)?)(?![\d.,])(?!\s*OFF)", product.text)
                if price_match:
                    price = price_match.group(1).replace(",", "")

                try:
                    href = product.find_element(
                        By.CSS_SELECTOR, "a[href*='/pd/']"
                    ).get_attribute("href")
                    link = href if href.startswith("http") else f"https://www.bigbasket.com{href}"
                except Exception:
                    link = ""

                try:
                    img = product.find_element(By.CSS_SELECTOR, "img[class*='DeckImage']")
                    image_url = img.get_attribute("src") or img.get_attribute("data-src") or ""
                except Exception:
                    image_url = ""

                # Pack size renders as its own line, e.g. "1 kg - Pouch".
                weight = _extract_weight(title)
                if weight == "N/A":
                    weight = _extract_weight(product.text)

                results.append(
                    {
                        "title": title,
                        "price": price,
                        "link": link,
                        "image_url": image_url,
                        "weight": weight,
                        "source": "BigBasket",
                    }
                )
            except Exception:
                logger.debug("Skipped a BigBasket product (missing fields)", exc_info=True)

        logger.info("Scraped %d BigBasket products", len(results))
        return results

    finally:
        driver.quit()


# ---------------------------------------------------------------------------
# Blinkit scraper
# ---------------------------------------------------------------------------
@_with_retry
def scrape_blinkit_data(search_query: str) -> list[dict]:
    """Scrape product listings from Blinkit's search page.

    Blinkit renders Tailwind utility classes; cards are identified by a
    stable utility combination and carry the numeric product id in the
    element's id attribute, which is also how the product URL is built
    (the cards contain no anchor tags at all).
    """
    driver = _create_driver()
    try:
        encoded = urllib.parse.quote_plus(search_query)
        driver.get(f"https://blinkit.com/s/?q={encoded}")
        logger.info("Blinkit search page loaded for '%s'", search_query)

        card_selector = "div.tw-relative.tw-flex.tw-h-full.tw-flex-col"
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, card_selector))
        )

        # Card images load lazily; walk the viewport once so they hydrate.
        for offset in (400, 900, 1400):
            driver.execute_script(f"window.scrollTo(0, {offset})")
            time.sleep(0.7)
        driver.execute_script("window.scrollTo(0, 0)")
        time.sleep(0.5)

        products = driver.find_elements(By.CSS_SELECTOR, card_selector)

        results: list[dict] = []
        for product in products[:MAX_PRODUCTS_PER_STORE]:
            try:
                title = product.find_element(
                    By.CSS_SELECTOR, "div.tw-text-300.tw-font-semibold.tw-line-clamp-2"
                ).text.strip()
                if not title:
                    continue

                try:
                    price = (
                        product.find_element(
                            By.CSS_SELECTOR, "div.tw-text-200.tw-font-semibold"
                        )
                        .text.replace("₹", "")
                        .replace(",", "")
                        .strip()
                    )
                except Exception:
                    price = "NA"

                product_id = product.get_attribute("id") or ""
                link = (
                    f"https://blinkit.com/prn/p/prid/{product_id}"
                    if product_id.isdigit()
                    else ""
                )

                try:
                    img = product.find_element(By.TAG_NAME, "img")
                    image_url = img.get_attribute("src") or img.get_attribute("data-src") or ""
                except Exception:
                    image_url = ""

                try:
                    pack = product.find_element(
                        By.CSS_SELECTOR, "div.tw-text-200.tw-font-medium.tw-line-clamp-1"
                    ).text.strip()
                    weight = _extract_weight(pack)
                except Exception:
                    weight = "N/A"
                if weight == "N/A":
                    weight = _extract_weight(title)

                results.append(
                    {
                        "title": title,
                        "price": price,
                        "link": link,
                        "image_url": image_url,
                        "weight": weight,
                        "source": "Blinkit",
                    }
                )
            except Exception:
                logger.debug("Skipped a Blinkit product (missing fields)", exc_info=True)

        logger.info("Scraped %d Blinkit products", len(results))
        return results

    finally:
        driver.quit()


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------
_SCRAPERS = [
    ("Amazon", scrape_amazon_data),
    ("Flipkart", scrape_flipkart_data),
    ("BigBasket", scrape_bigbasket_data),
    ("Blinkit", scrape_blinkit_data),
]

_STORE_ORDER = {name: index for index, (name, _) in enumerate(_SCRAPERS)}


def _record_result(store: str, future, products: list, stores: list) -> None:
    """Turn one finished future into a per-store status entry."""
    try:
        results = future.result()
        products.extend(results)
        stores.append({
            "name": store,
            "status": "ok" if results else "empty",
            "count": len(results),
            "error": None,
        })
        logger.info("%s returned %d products", store, len(results))
    except Exception as exc:
        stores.append({
            "name": store,
            "status": "failed",
            "count": 0,
            # Class name plus a bounded slice of the message: enough to tell
            # a timeout from a selector change, without dumping Selenium's
            # multi-line diagnostics (local paths included) into the response.
            "error": f"{type(exc).__name__}: {str(exc)[:200]}",
        })
        logger.error("%s scraping failed", store, exc_info=True)


def scrape_data(search_query: str) -> tuple[list[dict], list[dict]]:
    """Scrape all stores in parallel for the given query.

    Returns (products, stores): the combined product list and one status entry
    per store, so the caller can tell "no matches" apart from "store failed".

    The whole call is bounded by SCRAPER_TOTAL_BUDGET_SECONDS. Stores that
    have not finished by then are reported as failed rather than holding the
    request open past what the backend's read timeout allows - the two
    services share that budget by agreement, and the previous mismatch (a 10s
    caller timeout against a ~60s worst-case scrape) broke every cold search.
    """
    products: list[dict] = []
    stores: list[dict] = []
    processed: set[str] = set()

    pool = ThreadPoolExecutor(max_workers=len(_SCRAPERS))
    futures = {pool.submit(fn, search_query): name for name, fn in _SCRAPERS}

    try:
        for future in as_completed(futures, timeout=SCRAPER_TOTAL_BUDGET_SECONDS):
            store = futures[future]
            processed.add(store)
            _record_result(store, future, products, stores)
    except TimeoutError:
        for future, store in futures.items():
            if store in processed:
                continue
            if future.done():
                # Finished in the instant between the timeout firing and this
                # sweep - its results are real, so keep them.
                _record_result(store, future, products, stores)
            else:
                future.cancel()
                stores.append({
                    "name": store,
                    "status": "failed",
                    "count": 0,
                    "error": f"exceeded the {SCRAPER_TOTAL_BUDGET_SECONDS}s scrape budget",
                })
                logger.error("%s did not finish within the scrape budget", store)
    finally:
        # Do not wait for stragglers: the response goes out now, and a thread
        # still driving a browser will clean itself up through its own page
        # load timeout and the finally: driver.quit() in its store function.
        pool.shutdown(wait=False, cancel_futures=True)

    stores.sort(key=lambda status: _STORE_ORDER[status["name"]])

    logger.info(
        "Scraping complete - %d products for '%s' (%s)",
        len(products),
        search_query,
        ", ".join(f"{s['name']}={s['status']}" for s in stores),
    )
    return products, stores
