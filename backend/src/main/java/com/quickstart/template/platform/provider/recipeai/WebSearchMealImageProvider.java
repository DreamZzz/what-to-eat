package com.quickstart.template.platform.provider.recipeai;

import com.quickstart.template.contexts.meal.api.dto.MealRecommendationRequestDTO;
import com.quickstart.template.contexts.meal.api.dto.RecipeDTO;
import com.quickstart.template.contexts.meal.application.MealImageAssetService;
import com.quickstart.template.contexts.meal.application.MealImageResult;
import com.quickstart.template.contexts.meal.domain.MealImageAsset;
import com.quickstart.template.contexts.media.application.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "app.meal.image.provider", havingValue = "web-search")
public class WebSearchMealImageProvider implements MealImageProvider {
    private static final Logger log = LoggerFactory.getLogger(WebSearchMealImageProvider.class);
    private static final Pattern DIRECT_IMAGE_URL_PATTERN = Pattern.compile(
            "https?://[^\\s\"'<>]+\\.(?:jpg|jpeg|png|webp|gif|bmp)(?:\\?[^\\s\"'<>]*)?",
            Pattern.CASE_INSENSITIVE
    );

    private final MealImageAssetService mealImageAssetService;
    private final FileStorageService fileStorageService;
    private final RestClient htmlClient;
    private final RestClient binaryClient;
    private final String searchPath;
    private final String searchProviderName;
    private final int maxCandidates;

    public WebSearchMealImageProvider(
            MealImageAssetService mealImageAssetService,
            FileStorageService fileStorageService,
            @Value("${app.meal.image.web-search.base-url:https://cn.bing.com}") String baseUrl,
            @Value("${app.meal.image.web-search.search-path:/images/search}") String searchPath,
            @Value("${app.meal.image.web-search.user-agent:Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36}") String userAgent,
            @Value("${app.meal.image.web-search.provider-name:bing-image-search}") String searchProviderName,
            @Value("${app.meal.image.web-search.max-candidates:2}") int maxCandidates,
            @Value("${app.meal.image.web-search.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.meal.image.web-search.read-timeout-ms:4000}") long readTimeoutMs) {
        this.mealImageAssetService = mealImageAssetService;
        this.fileStorageService = fileStorageService;
        this.searchPath = searchPath;
        this.searchProviderName = searchProviderName;
        this.maxCandidates = Math.max(1, maxCandidates);
        SimpleClientHttpRequestFactory requestFactory = buildRequestFactory(connectTimeoutMs, readTimeoutMs);
        this.htmlClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.7")
                .build();
        this.binaryClient = RestClient.builder()
                .requestFactory(buildRequestFactory(connectTimeoutMs, readTimeoutMs))
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    @Override
    public String providerName() {
        return "web-search";
    }

    @Override
    public MealImageResult generate(MealRecommendationRequestDTO request, RecipeDTO recipe) {
        String dishName = resolveDishName(request, recipe);
        if (dishName.isBlank()) {
            return new MealImageResult(providerName(), null, "FAILED");
        }

        Optional<MealImageAsset> cachedAsset = mealImageAssetService.findLatestByDishName(dishName);
        if (cachedAsset.isPresent()) {
            return new MealImageResult(providerName(), cachedAsset.get().getPublicImageUrl(), "GENERATED");
        }

        try {
            List<ImageCandidate> candidates = searchImageCandidates(dishName);
            if (candidates.isEmpty()) {
                log.warn("Meal image search returned no candidates for dish {}", dishName);
            }

            for (ImageCandidate candidate : candidates) {
                try {
                    DownloadedImage downloadedImage = downloadImage(candidate.imageUrl());
                    FileStorageService.StoredFileDescriptor storedFile = fileStorageService.storeManagedFile(
                            downloadedImage.bytes(),
                            downloadedImage.contentType(),
                            "meal-images",
                            dishName
                    );
                    MealImageAsset asset = mealImageAssetService.saveOrGetExisting(
                            dishName,
                            candidate.imageUrl(),
                            candidate.pageUrl(),
                            storedFile.getStorageKey(),
                            storedFile.getPublicUrl(),
                            searchProviderName
                    );
                    return new MealImageResult(providerName(), asset.getPublicImageUrl(), "GENERATED");
                } catch (Exception candidateException) {
                    log.warn("Meal image candidate failed for {} -> {}", dishName, candidate.imageUrl(), candidateException);
                }
            }
        } catch (Exception exception) {
            log.warn("Meal image web search failed for {}", dishName, exception);
        }

        return new MealImageResult(providerName(), null, "FAILED");
    }

    private List<ImageCandidate> searchImageCandidates(String dishName) {
        String html = fetchSearchHtml(dishName);

        if (html == null || html.isBlank()) {
            return List.of();
        }

        String normalizedHtml = HtmlUtils.htmlUnescape(html)
                .replace("\\u0022", "\"")
                .replace("&#34;", "\"");

        List<String> imageUrls = extractFieldValues(normalizedHtml, "murl");
        if (imageUrls.isEmpty()) {
            imageUrls = extractDirectImageUrls(normalizedHtml);
        }

        List<String> pageUrls = extractFieldValues(normalizedHtml, "purl");
        Set<String> deduplicated = new LinkedHashSet<>();
        List<ImageCandidate> candidates = new ArrayList<>();

        for (int index = 0; index < imageUrls.size() && candidates.size() < maxCandidates; index++) {
            String imageUrl = normalizeSearchValue(imageUrls.get(index));
            if (imageUrl.isBlank() || !imageUrl.startsWith("http")) {
                continue;
            }
            if (!deduplicated.add(imageUrl)) {
                continue;
            }

            String pageUrl = index < pageUrls.size() ? normalizeSearchValue(pageUrls.get(index)) : null;
            candidates.add(new ImageCandidate(imageUrl, pageUrl));
        }

        if (candidates.isEmpty()) {
            log.warn(
                    "Meal image search produced no usable candidates for dish {} (htmlLength={}, containsMurl={}, containsQuotedMurl={}, htmlSnippet={})",
                    dishName,
                    html.length(),
                    normalizedHtml.contains("\"murl\":\""),
                    html.contains("&quot;murl&quot;:&quot;"),
                    abbreviate(normalizedHtml)
            );
        }
        return candidates;
    }

    private String fetchSearchHtml(String dishName) {
        String html = htmlClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(searchPath)
                        .queryParam("q", dishName + " 菜品图")
                        .build())
                .retrieve()
                .body(String.class);

        if (!isMovedPage(html)) {
            return html;
        }

        String redirectUrl = extractRedirectUrl(html);
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return html;
        }

        return htmlClient.get()
                .uri(URI.create(HtmlUtils.htmlUnescape(redirectUrl)))
                .retrieve()
                .body(String.class);
    }

    private List<String> extractFieldValues(String content, String fieldName) {
        List<String> values = new ArrayList<>();
        collectFieldValues(content, "\"" + fieldName + "\":\"", "\"", values);
        collectFieldValues(content, "\\\"" + fieldName + "\\\":\\\"", "\\\"", values);
        collectFieldValues(content, "&quot;" + fieldName + "&quot;:&quot;", "&quot;", values);
        return values;
    }

    private void collectFieldValues(String content, String startMarker, String endMarker, List<String> sink) {
        int cursor = 0;
        while (cursor >= 0 && cursor < content.length()) {
            int start = content.indexOf(startMarker, cursor);
            if (start < 0) {
                break;
            }
            start += startMarker.length();
            int end = content.indexOf(endMarker, start);
            if (end > start) {
                sink.add(content.substring(start, end));
            }
            cursor = start;
        }
    }

    private String normalizeSearchValue(String value) {
        if (value == null) {
            return "";
        }

        String unescaped = HtmlUtils.htmlUnescape(value);
        return unescaped
                .replace("\\u002f", "/")
                .replace("\\u003a", ":")
                .replace("\\u003f", "?")
                .replace("\\u003d", "=")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .trim();
    }

    private List<String> extractDirectImageUrls(String content) {
        List<String> values = new ArrayList<>();
        Matcher matcher = DIRECT_IMAGE_URL_PATTERN.matcher(content);
        while (matcher.find() && values.size() < maxCandidates * 3) {
            values.add(matcher.group());
        }
        return values;
    }

    private String abbreviate(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 220) {
            return normalized;
        }
        return normalized.substring(0, 220) + "...";
    }

    private boolean isMovedPage(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        return html.contains("<title>Object moved</title>") && html.contains("Object moved to <a href=\"");
    }

    private String extractRedirectUrl(String html) {
        int hrefStart = html.indexOf("Object moved to <a href=\"");
        if (hrefStart < 0) {
            return null;
        }
        hrefStart = html.indexOf('"', hrefStart);
        if (hrefStart < 0) {
            return null;
        }
        int urlStart = hrefStart + 1;
        int urlEnd = html.indexOf('"', urlStart);
        if (urlEnd <= urlStart) {
            return null;
        }
        return html.substring(urlStart, urlEnd);
    }

    private DownloadedImage downloadImage(String imageUrl) {
        ResponseEntity<byte[]> response = binaryClient.get()
                .uri(URI.create(imageUrl))
                .retrieve()
                .toEntity(byte[].class);
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("Empty image payload");
        }

        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        String normalizedContentType = normalizeImageContentType(contentType, imageUrl);
        if (!normalizedContentType.startsWith("image/")) {
            throw new IllegalStateException("Downloaded payload is not an image: " + normalizedContentType);
        }

        return new DownloadedImage(body, normalizedContentType);
    }

    private String normalizeImageContentType(String contentType, String imageUrl) {
        if (contentType != null && !contentType.isBlank()) {
            return MediaType.parseMediaType(contentType).toString().toLowerCase(Locale.ROOT);
        }

        String lowerUrl = imageUrl.toLowerCase(Locale.ROOT);
        if (lowerUrl.endsWith(".png")) {
            return "image/png";
        }
        if (lowerUrl.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerUrl.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }

    private String resolveDishName(MealRecommendationRequestDTO request, RecipeDTO recipe) {
        if (recipe != null && recipe.getTitle() != null && !recipe.getTitle().isBlank()) {
            return recipe.getTitle().trim();
        }
        if (request != null && request.getSourceText() != null) {
            return request.getSourceText().trim();
        }
        return "";
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(normalizeTimeout(connectTimeoutMs, 2_000));
        requestFactory.setReadTimeout(normalizeTimeout(readTimeoutMs, 4_000));
        return requestFactory;
    }

    private int normalizeTimeout(long timeoutMs, int fallbackMs) {
        if (timeoutMs <= 0) {
            return fallbackMs;
        }
        return (int) Math.min(timeoutMs, Integer.MAX_VALUE);
    }

    record ImageCandidate(String imageUrl, String pageUrl) { }

    record DownloadedImage(byte[] bytes, String contentType) { }
}
