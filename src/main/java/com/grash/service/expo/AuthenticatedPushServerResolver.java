package com.grash.service.expo;

import io.github.jav.exposerversdk.helpers.PushServerResolver;
import okhttp3.*;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthenticatedPushServerResolver implements PushServerResolver {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final OkHttpClient client = new OkHttpClient();
    private final String accessToken;

    public AuthenticatedPushServerResolver(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public CompletableFuture<String> postAsync(URL url, String payload) throws CompletionException {
        return CompletableFuture.supplyAsync(() -> doPost(url, payload), executor);
    }

    private String doPost(URL url, String payload) {
        RequestBody body = RequestBody.create(payload, JSON);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Encoding", "gzip, deflate");

        if (accessToken != null && !accessToken.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + accessToken);
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "";
                throw new CompletionException(new IOException("Expo push failed with status " + response.code() + ": " + error));
            }
            ResponseBody responseBody = response.body();
            return responseBody != null ? responseBody.string() : "";
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }
}
