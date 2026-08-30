"""Tests for the scrape_data aggregator, with fake store scrapers.

The three real store scrapers drive browsers and are exercised manually; what
matters here is the aggregation contract: per-store statuses, ordering, the
budget, and that one broken store never hides the others' results.
"""

import time

import pytest

import app.services.scraper as scraper


SAMPLE_PRODUCT = {"title": "Tata Salt 1kg", "price": "25", "source": "Amazon"}


def use_scrapers(monkeypatch, entries):
    monkeypatch.setattr(scraper, "_SCRAPERS", entries)
    monkeypatch.setattr(
        scraper, "_STORE_ORDER", {name: i for i, (name, _) in enumerate(entries)}
    )


def test_reports_ok_empty_and_failed_per_store(monkeypatch):
    def good(query):
        return [SAMPLE_PRODUCT]

    def empty(query):
        return []

    def broken(query):
        raise RuntimeError("markup changed")

    use_scrapers(monkeypatch, [("Amazon", good), ("JioMart", empty), ("Flipkart", broken)])

    products, stores = scraper.scrape_data("salt")

    assert products == [SAMPLE_PRODUCT]
    assert [s["name"] for s in stores] == ["Amazon", "JioMart", "Flipkart"]
    assert [s["status"] for s in stores] == ["ok", "empty", "failed"]
    assert stores[2]["error"].startswith("RuntimeError")
    assert stores[0]["error"] is None


def test_one_broken_store_does_not_hide_the_others(monkeypatch):
    def good(query):
        return [SAMPLE_PRODUCT]

    def broken(query):
        raise RuntimeError("down")

    use_scrapers(monkeypatch, [("Amazon", broken), ("JioMart", good)])

    products, stores = scraper.scrape_data("salt")

    assert len(products) == 1
    assert {s["name"]: s["status"] for s in stores} == {"Amazon": "failed", "JioMart": "ok"}


def test_a_stuck_store_is_cut_off_at_the_budget(monkeypatch):
    def fast(query):
        return [SAMPLE_PRODUCT]

    def stuck(query):
        time.sleep(5)
        return [SAMPLE_PRODUCT]

    use_scrapers(monkeypatch, [("Amazon", fast), ("Flipkart", stuck)])
    monkeypatch.setattr(scraper, "SCRAPER_TOTAL_BUDGET_SECONDS", 1)

    started = time.monotonic()
    products, stores = scraper.scrape_data("salt")
    elapsed = time.monotonic() - started

    assert elapsed < 4, "the call must return at the budget, not wait for the straggler"
    assert products == [SAMPLE_PRODUCT]
    by_name = {s["name"]: s for s in stores}
    assert by_name["Amazon"]["status"] == "ok"
    assert by_name["Flipkart"]["status"] == "failed"
    assert "budget" in by_name["Flipkart"]["error"]


def test_retry_decorator_retries_then_raises(monkeypatch):
    monkeypatch.setattr(scraper, "MAX_RETRIES", 1)
    monkeypatch.setattr(scraper, "_RETRY_BACKOFF", 0)
    calls = {"count": 0}

    @scraper._with_retry
    def flaky(query):
        calls["count"] += 1
        raise RuntimeError("always fails")

    with pytest.raises(RuntimeError):
        flaky("salt")

    assert calls["count"] == 2, "MAX_RETRIES=1 means two attempts in total"


def test_retry_decorator_succeeds_on_a_later_attempt(monkeypatch):
    monkeypatch.setattr(scraper, "MAX_RETRIES", 1)
    monkeypatch.setattr(scraper, "_RETRY_BACKOFF", 0)
    calls = {"count": 0}

    @scraper._with_retry
    def flaky(query):
        calls["count"] += 1
        if calls["count"] == 1:
            raise RuntimeError("transient")
        return [SAMPLE_PRODUCT]

    assert flaky("salt") == [SAMPLE_PRODUCT]
    assert calls["count"] == 2
