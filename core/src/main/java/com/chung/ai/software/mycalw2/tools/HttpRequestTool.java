package com.chung.ai.software.mycalw2.tools;
 
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@Slf4j
public class HttpRequestTool {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool("Performs an HTTP GET request to the specified URL and returns the response body as a string")
    public String get(String url) {
        log.info("HTTP GET request to: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (Exception e) {
            log.error("Error during GET request to {}: {}", url, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Performs an HTTP POST request to the specified URL with the given body and returns the response body as a string")
    public String post(String url, String body) {
        log.info("HTTP POST request to: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (Exception e) {
            log.error("Error during POST request to {}: {}", url, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Performs an HTTP PUT request to the specified URL with the given body and returns the response body as a string")
    public String put(String url, String body) {
        log.info("HTTP PUT request to: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (Exception e) {
            log.error("Error during PUT request to {}: {}", url, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Performs an HTTP DELETE request to the specified URL and returns the response body as a string")
    public String delete(String url) {
        log.info("HTTP DELETE request to: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (Exception e) {
            log.error("Error during DELETE request to {}: {}", url, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private String handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();
        if (statusCode >= 200 && statusCode < 300) {
            return body;
        } else {
            return "Error: Received HTTP " + statusCode + ". Response: " + body;
        }
    }
}
