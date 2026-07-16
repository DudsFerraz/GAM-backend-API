package br.org.gam.api.shared.web;

import java.net.URI;

public final class PublicApiUri {

    private static final String API_BASE_PATH = "/api";

    private PublicApiUri() {
    }

    public static URI forResource(String apiRelativePath) {
        if (apiRelativePath == null || !apiRelativePath.startsWith("/")) {
            throw new IllegalArgumentException("API-relative resource paths must begin with '/'.");
        }
        return URI.create(API_BASE_PATH + apiRelativePath);
    }
}
