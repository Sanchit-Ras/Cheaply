"""Contract tests for the /scrape endpoint, with the Selenium layer mocked.

These pin the response envelope the Spring Boot backend depends on:
{"query", "products", "stores", "duration_ms"}. No browser is started.
"""

import pytest

import app.routes.scraper_api as scraper_api
from app import config, create_app


SAMPLE_PRODUCT = {
    "title": "Tata Salt 1kg",
    "price": "25",
    "link": "https://example.test/p",
    "image_url": "",
    "weight": "1kg",
    "source": "Amazon",
}

SAMPLE_STORES = [
    {"name": "Amazon", "status": "ok", "count": 1, "error": None},
    {"name": "JioMart", "status": "empty", "count": 0, "error": None},
    {"name": "Flipkart", "status": "failed", "count": 0, "error": "TimeoutException: x"},
]


@pytest.fixture()
def client(monkeypatch):
    monkeypatch.setattr(config, "API_KEY", "")
    application = create_app()
    application.config["TESTING"] = True
    return application.test_client()


def stub_scrape(products, stores):
    def _scrape(query):
        return products, stores
    return _scrape


def test_scrape_returns_the_envelope(client, monkeypatch):
    monkeypatch.setattr(scraper_api, "scrape_data", stub_scrape([SAMPLE_PRODUCT], SAMPLE_STORES))

    response = client.post("/scrape", json={"query": "salt"})

    assert response.status_code == 200
    body = response.get_json()
    assert body["query"] == "salt"
    assert body["products"] == [SAMPLE_PRODUCT]
    assert body["stores"] == SAMPLE_STORES
    assert isinstance(body["duration_ms"], int)


def test_scrape_requires_a_query(client):
    assert client.post("/scrape", json={}).status_code == 400
    assert client.post("/scrape", json={"query": "   "}).status_code == 400


def test_scrape_rejects_a_non_string_query(client):
    # A number here used to blow up on .strip() and surface as a 500.
    assert client.post("/scrape", json={"query": 123}).status_code == 400


def test_scrape_truncates_an_oversized_query(client, monkeypatch):
    seen = {}

    def _scrape(query):
        seen["query"] = query
        return [], []

    monkeypatch.setattr(scraper_api, "scrape_data", _scrape)
    client.post("/scrape", json={"query": "x" * 500})

    assert len(seen["query"]) == config.MAX_QUERY_LENGTH


def test_scrape_failure_is_a_500_without_internal_details(client, monkeypatch):
    def _scrape(query):
        raise RuntimeError("chromedriver exploded at C:/internal/path")

    monkeypatch.setattr(scraper_api, "scrape_data", _scrape)
    response = client.post("/scrape", json={"query": "salt"})

    assert response.status_code == 500
    assert "internal" not in response.get_data(as_text=True)


def test_api_key_is_enforced_when_configured(client, monkeypatch):
    monkeypatch.setattr(config, "API_KEY", "sekret")
    monkeypatch.setattr(scraper_api, "scrape_data", stub_scrape([SAMPLE_PRODUCT], []))

    assert client.post("/scrape", json={"query": "salt"}).status_code == 401
    assert client.post(
        "/scrape", json={"query": "salt"}, headers={"X-API-Key": "wrong"}
    ).status_code == 401
    assert client.post(
        "/scrape", json={"query": "salt"}, headers={"X-API-Key": "sekret"}
    ).status_code == 200


def test_health_stays_open_even_with_an_api_key(client, monkeypatch):
    monkeypatch.setattr(config, "API_KEY", "sekret")

    response = client.get("/health")

    assert response.status_code == 200
    assert response.get_json()["status"] == "UP"
