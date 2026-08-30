package com.cheaply.scraper.client;

import com.cheaply.exception.ScraperUnavailableException;
import com.cheaply.scraper.dto.ScraperRequest;
import com.cheaply.scraper.dto.ScraperResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * HTTP client for the Python scraper service.
 *
 * <p>The read timeout deserves a note. It was previously 10 seconds, while the
 * scraper drives three headless browsers in parallel with retries and can
 * legitimately take the better part of a minute on a cold query. The backend
 * therefore gave up on almost every cache miss and returned 503 while the
 * scrape carried on running with nobody listening. The two services now have a
 * documented shared budget: the scraper caps itself at
 * SCRAPER_TOTAL_BUDGET_SECONDS and this timeout sits comfortably above it.
 */
@Slf4j
@Component
public class ScraperClient {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final RestClient restClient;
    private final String scraperUrl;
    private final String apiKey;

    public ScraperClient(
            @Value("${cheaply.scraper.url:http://localhost:5000}") String scraperUrl,
            @Value("${cheaply.scraper.read-timeout-ms:60000}") int readTimeoutMs,
            @Value("${cheaply.scraper.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${cheaply.scraper.api-key:}") String apiKey
    ) {
        this.scraperUrl = scraperUrl;
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(scraperUrl)
                .requestFactory(requestFactory)
                .build();

        if (!StringUtils.hasText(apiKey)) {
            log.warn("cheaply.scraper.api-key is not set - calls to the scraper service will be unauthenticated.");
        }
        log.info("Scraper client configured for {} (connect {}ms, read {}ms)",
                scraperUrl, connectTimeoutMs, readTimeoutMs);
    }

    public ScraperResponse scrape(String query) {
        log.info("Calling scraper service at {}/scrape for query '{}'", scraperUrl, query);

        try {
            ScraperResponse response = restClient.post()
                    .uri("/scrape")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (StringUtils.hasText(apiKey)) {
                            headers.set(API_KEY_HEADER, apiKey);
                        }
                    })
                    .body(ScraperRequest.builder().query(query).build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        // The body may carry a correlation id from the scraper's
                        // logs, but never anything we should show a caller.
                        log.error("Scraper service returned {} for query '{}'", res.getStatusCode(), query);
                        throw new ScraperUnavailableException(
                                "The product search service returned an error. Please try again shortly.");
                    })
                    .body(ScraperResponse.class);

            if (response == null) {
                log.warn("Scraper service returned an empty body for query '{}'", query);
                throw new ScraperUnavailableException(
                        "The product search service returned no data. Please try again shortly.");
            }

            log.info("Scraper returned {} products for '{}' in {}ms; store outcomes: {}",
                    response.productsOrEmpty().size(), query, response.getDurationMs(), response.storesOrEmpty());
            return response;

        } catch (ScraperUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            // Connection refused, DNS failure, read timeout, unparseable body.
            log.error("Scraper service call failed at {}: {}", scraperUrl, e.getMessage());
            throw new ScraperUnavailableException(
                    "The product search service is unavailable or timed out. Please try again later.");
        }
    }
}
