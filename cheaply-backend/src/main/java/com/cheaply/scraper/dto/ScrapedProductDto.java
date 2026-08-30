package com.cheaply.scraper.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScrapedProductDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;
    private String price;
    private String link;

    @JsonProperty("image_url")
    @JsonAlias({"imageUrl", "image_url", "image"})
    private String imageUrl;

    private String weight;
    private String source;
}
