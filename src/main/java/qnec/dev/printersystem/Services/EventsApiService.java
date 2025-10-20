package qnec.dev.printersystem.Services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import qnec.dev.printersystem.dto.EventsApiResponse;

public class EventsApiService {
    private static final Logger log = LogManager.getLogger(EventsApiService.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Gson gson = new GsonBuilder().create();
    private final String baseUrl;

    public EventsApiService(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
    }

    public EventsApiResponse fetchEvents() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/events"))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();

        long t0 = System.currentTimeMillis();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        long took = System.currentTimeMillis() - t0;

        log.debug("HTTP {} {} ({} ms)", resp.statusCode(), req.uri(), took);

        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return gson.fromJson(resp.body(), EventsApiResponse.class);
        }
        throw new RuntimeException("HTTP " + resp.statusCode() + " en " + took + " ms: " + resp.body());
    }
}
