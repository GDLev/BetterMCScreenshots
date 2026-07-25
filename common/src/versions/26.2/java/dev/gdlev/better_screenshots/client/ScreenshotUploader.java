package dev.gdlev.better_screenshots.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.gdlev.better_screenshots.common.ScreenshotConfigData;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class ScreenshotUploader {

    public interface Listener {
        void onProgress(double progress);
        void onSuccess(String uploadedUrl);
        void onError(String error);
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().build();
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AMZ_DAY =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private ScreenshotUploader() {}

    public record ImmichAlbum(String id, String name) {}

    public static List<ImmichAlbum> fetchImmichAlbums(ScreenshotConfig cfg) throws Exception {
        String baseUrl = blankToNull(cfg.immichBaseUrl);
        String apiKey = blankToNull(cfg.immichApiKey);
        if (baseUrl == null || apiKey == null) {
            throw new IllegalArgumentException("Immich requires Server URL and API Key.");
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(normalizeImmichApiBase(baseUrl) + "/albums"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "application/json")
                .header("x-api-key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        ensureOk(response, "Immich albums");

        List<ImmichAlbum> albums = new ArrayList<>();
        JsonElement root = JsonParser.parseString(response.body());
        if (root != null && root.isJsonArray()) {
            for (JsonElement item : root.getAsJsonArray()) {
                if (!item.isJsonObject()) continue;
                var obj = item.getAsJsonObject();
                String id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsString() : "";
                String name = obj.has("albumName") && !obj.get("albumName").isJsonNull()
                        ? obj.get("albumName").getAsString()
                        : id;
                if (!id.isBlank()) albums.add(new ImmichAlbum(id, name));
            }
        }
        return albums;
    }

    public static void uploadAsync(File screenshotFile, Listener listener) {
        Thread.ofVirtual().start(() -> {
            try {
                if (listener == null || screenshotFile == null) return;

                ScreenshotConfig cfg = ScreenshotConfig.get();
                if (cfg.uploadProvider == ScreenshotConfig.UploadProvider.DISABLED) return;

                waitForFileReady(screenshotFile);
                byte[] bytes = Files.readAllBytes(screenshotFile.toPath());

                listener.onProgress(0.08);
                String uploadedUrl = switch (cfg.uploadProvider) {
                    case IMGUR -> uploadImgur(cfg, screenshotFile, bytes, listener);
                    case S3 -> uploadS3(cfg, screenshotFile, bytes, listener);
                    case CUSTOM_HTTP -> uploadCustom(cfg, screenshotFile, bytes, listener);
                    case CATBOX -> uploadCatbox(screenshotFile, bytes, listener);
                    case IMMICH -> uploadImmich(cfg, screenshotFile, bytes, listener);
                    case EXTERNAL_CUSTOM -> uploadExternal(cfg, screenshotFile, bytes, listener);
                    case DISABLED -> null;
                };

                if (uploadedUrl == null || uploadedUrl.isBlank()) {
                    throw new IOException(Component.translatable("better_screenshots.upload.error.empty_url").getString());
                }

                listener.onProgress(1.0);
                listener.onSuccess(uploadedUrl.trim());
            } catch (Exception e) {
                if (listener != null) listener.onError(safeMessage(e));
            }
        });
    }

    public static boolean isUploaderEnabled() {
        return ScreenshotConfig.get().uploadProvider != ScreenshotConfig.UploadProvider.DISABLED;
    }

    public static void copyUrlToClipboard(String url) {
        if (url == null || url.isBlank()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.keyboardHandler != null) {
            client.keyboardHandler.setClipboard(url);
        }
    }

    public static void showUploadSuccessToast() {
        showToast(
                Component.translatable("better_screenshots.upload.toast.title"),
                Component.translatable("better_screenshots.upload.toast.desc"));
    }

    public static void showUploadErrorToast(String error) {
        showToast(
                Component.translatable("better_screenshots.upload.toast.error_title"),
                Component.literal(error == null || error.isBlank()
                        ? Component.translatable("better_screenshots.upload.toast.error_desc").getString()
                        : error));
    }

    private static void showToast(Component title, Component description) {
        Minecraft client = Minecraft.getInstance();

        MinecraftCompat.toastManager(client).addToast(
                new SystemToast(SystemToast.SystemToastId.NARRATOR_TOGGLE, title, description)
        );
    }


    public static void uploadWithClientFeedback(File screenshotFile, String screenshotId, boolean updatePreviewIndicator) {
        Minecraft client = Minecraft.getInstance();
        ScreenshotConfig cfg = ScreenshotConfig.get();
        if (cfg.uploadProvider == ScreenshotConfig.UploadProvider.DISABLED) return;

        String id = (screenshotId == null || screenshotId.isBlank())
                ? String.valueOf(System.nanoTime())
                : screenshotId;

        if (updatePreviewIndicator) {
            ScreenshotPreviewRenderer.prepareUploadIndicator(true);
            ScreenshotPreviewRenderer.beginUploadIndicator();
        }

        if (screenshotFile == null || !screenshotFile.exists() || !screenshotFile.isFile()) {
            if (updatePreviewIndicator) {
                ScreenshotPreviewRenderer.markUploadError(Component.translatable("better_screenshots.upload.error.file_not_found").getString());
            }
            if (cfg.uploadChatNotification && client.player != null) {
                client.player.sendSystemMessage(Component.translatable(
                        "better_screenshots.upload.error",
                        Component.translatable("better_screenshots.upload.error.screenshot_file_not_found").getString()));
            }
            return;
        }

        uploadAsync(screenshotFile, new Listener() {
            @Override
            public void onProgress(double progress) {
                if (!updatePreviewIndicator) return;
                client.execute(() -> ScreenshotPreviewRenderer.updateUploadProgress(progress));
            }

            @Override
            public void onSuccess(String uploadedUrl) {
                client.execute(() -> {
                    if (updatePreviewIndicator) {
                        ScreenshotPreviewRenderer.markUploadSuccess(uploadedUrl);
                    }
                    ScreenshotPreviewRenderer.registerUploadedUrl(id, uploadedUrl);

                    if (cfg.uploadCopyToClipboard && client.keyboardHandler != null) {
                        client.keyboardHandler.setClipboard(uploadedUrl);
                    }

                    if (cfg.uploadChatNotification && client.player != null) {
                        MutableComponent link = Component.literal(uploadedUrl)
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.AQUA)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(uploadedUrl))));
                        MutableComponent message = Component.translatable("better_screenshots.upload.success")
                                .withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(" "))
                                .append(link);

                        if (cfg.uploadCopyToClipboard) {
                            message = message.append(Component.translatable("better_screenshots.upload.copied")
                                    .withStyle(ChatFormatting.DARK_GRAY));
                        } else {
                            MutableComponent sep = Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY);
                            MutableComponent copyBtn = Component.literal("[")
                                    .withStyle(ChatFormatting.DARK_GRAY)
                                    .append(Component.translatable("better_screenshots.chat.copy")
                                            .withStyle(s -> s
                                                    .withColor(ChatFormatting.GREEN)
                                                    .withBold(false)
                                                    .withHoverEvent(new HoverEvent.ShowText(
                                                            Component.translatable("better_screenshots.chat.copy_hint")
                                                                    .withStyle(ChatFormatting.DARK_GRAY)))
                                                    .withClickEvent(new ClickEvent.RunCommand(
                                                            "/better_screenshots copy_upload " + id))))
                                    .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
                            message = message.append(sep).append(copyBtn);
                        }

                        client.player.sendSystemMessage(message);
                    }
                });
            }

            @Override
            public void onError(String error) {
                client.execute(() -> {
                    if (updatePreviewIndicator) {
                        ScreenshotPreviewRenderer.markUploadError(error);
                    }
                    if (cfg.uploadChatNotification && client.player != null) {
                        client.player.sendSystemMessage(Component.translatable(
                                "better_screenshots.upload.error", error).withStyle(ChatFormatting.RED));
                    }
                });
            }
        });
    }

    private static String uploadImgur(
            ScreenshotConfig cfg, File file, byte[] bytes, Listener listener) throws Exception {
        String clientId = blankToNull(cfg.imgurClientId);
        String accessToken = blankToNull(cfg.imgurAccessToken);
        if (clientId == null && accessToken == null) {
            throw new IllegalArgumentException("Imgur requires Client ID or Access Token.");
        }

        listener.onProgress(0.20);
        MultipartBody body = multipart("image", file.getName(), bytes)
                .field("type", "file");

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("https://api.imgur.com/3/image"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "multipart/form-data; boundary=" + body.boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toBytes()));
        if (accessToken != null) {
            req.header("Authorization", "Bearer " + accessToken);
        } else {
            req.header("Authorization", "Client-ID " + clientId);
        }

        HttpResponse<String> response = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
        listener.onProgress(0.85);
        ensureOk(response, "Imgur");

        String url = findUrl(response.body());
        if (url == null) {
            throw new IOException(Component.translatable("better_screenshots.upload.error.imgur_missing_url").getString());
        }
        return url;
    }

    private static String uploadCatbox(
            File file, byte[] bytes, Listener listener) throws Exception {
        listener.onProgress(0.20);
        MultipartBody body = multipart("fileToUpload", file.getName(), bytes)
                .field("reqtype", "fileupload");

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://catbox.moe/user/api.php"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "multipart/form-data; boundary=" + body.boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toBytes()))
                .build();

        HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        listener.onProgress(0.85);
        ensureOk(response, "Catbox");

        String bodyText = response.body() == null ? "" : response.body().trim();
        if (bodyText.startsWith("http://") || bodyText.startsWith("https://")) {
            return bodyText;
        }
        String extracted = findUrl(bodyText);
        if (extracted != null) return extracted;
        throw new IOException(bodyText.isBlank() ? "Catbox returned empty response." : bodyText);
    }

    private static String uploadCustom(
            ScreenshotConfig cfg, File file, byte[] bytes, Listener listener) throws Exception {
        String url = blankToNull(cfg.customUploadUrl);
        if (url == null) throw new IllegalArgumentException("Custom uploader URL is required.");

        listener.onProgress(0.20);

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(normalizeEndpoint(url)))
                .version(HttpClient.Version.HTTP_1_1);

        String cookieKey = blankToNull(cfg.customCookieKey);
        String cookieValue = blankToNull(cfg.customCookieValue);
        if (cookieKey != null) {
            req.header("Cookie", cookieKey + "=" + (cookieValue == null ? "" : cookieValue));
        }

        for (ScreenshotConfigData.KeyValueEntry header : cfg.customHeaders) {
            String key = blankToNull(header == null ? null : header.key);
            if (key != null) {
                req.header(key, expand(header.value, file, bytes));
            }
        }

        if (cfg.customUploadBodyType == ScreenshotConfig.UploadBodyType.RAW_PNG) {
            req.header("Content-Type", "image/png");
            applyBody(req, cfg.customUploadMethod, HttpRequest.BodyPublishers.ofByteArray(bytes));
        } else {
            MultipartBody body = multipart(blankToDefault(cfg.customFileField, "file"), file.getName(), bytes);
            for (ScreenshotConfigData.KeyValueEntry field : cfg.customFormFields) {
                String key = blankToNull(field == null ? null : field.key);
                if (key != null) {
                    body.field(key, expand(field.value, file, bytes));
                }
            }
            req.header("Content-Type", "multipart/form-data; boundary=" + body.boundary);
            applyBody(req, cfg.customUploadMethod, HttpRequest.BodyPublishers.ofByteArray(body.toBytes()));
        }

        HttpResponse<String> response = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
        listener.onProgress(0.85);
        ensureOk(response, "Custom uploader");

        String responseBody = response.body();
        String responsePath = blankToNull(cfg.customResponseUrlJsonPath);
        if (responsePath != null) {
            String responsePathValue = findJsonPath(responseBody, responsePath);
            if (responsePathValue != null && !responsePathValue.isBlank()) return responsePathValue.trim();
        }

        String resolved = findUrl(responseBody);
        if (resolved != null) return resolved;

        String fallback = blankToNull(cfg.customFallbackUrl);
        return fallback == null ? url : expand(fallback, file, bytes, responseBody);
    }

    private static String uploadImmich(
            ScreenshotConfig cfg, File file, byte[] bytes, Listener listener) throws Exception {
        String baseUrl = blankToNull(cfg.immichBaseUrl);
        String apiKey = blankToNull(cfg.immichApiKey);
        if (baseUrl == null || apiKey == null) {
            throw new IllegalArgumentException("Immich requires Server URL and API Key.");
        }

        listener.onProgress(0.20);

        String endpoint = normalizeImmichApiBase(baseUrl) + "/assets";
        String modified = Instant.ofEpochMilli(file.lastModified()).toString();
        String deviceId = blankToDefault(cfg.immichDeviceId, "better-mc-screenshots");
        String deviceAssetId = sha1Hex((file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length())
                .getBytes(StandardCharsets.UTF_8));

        MultipartBody body = multipart("assetData", file.getName(), bytes)
                .field("deviceAssetId", deviceAssetId)
                .field("deviceId", deviceId)
                .field("fileCreatedAt", modified)
                .field("fileModifiedAt", modified)
                .field("filename", file.getName())
                .field("isFavorite", "false");

        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "application/json")
                .header("x-api-key", apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + body.boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toBytes()))
                .build();

        HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        listener.onProgress(0.85);
        ensureOk(response, "Immich");

        String id = findJsonPath(response.body(), "id");
        String albumId = blankToNull(cfg.immichAlbumId);
        if (id != null && !id.isBlank() && albumId != null) {
            addImmichAssetToAlbum(cfg, id, albumId);
        }
        return id == null || id.isBlank() ? endpoint : normalizeImmichApiBase(baseUrl).replaceAll("/api$", "") + "/photos/" + id;
    }

    private static void addImmichAssetToAlbum(ScreenshotConfig cfg, String assetId, String albumId) throws Exception {
        String baseUrl = blankToNull(cfg.immichBaseUrl);
        String apiKey = blankToNull(cfg.immichApiKey);
        if (baseUrl == null || apiKey == null || blankToNull(assetId) == null || blankToNull(albumId) == null) {
            return;
        }

        String json = "{\"albumIds\":[\"" + escapeJson(albumId) + "\"],\"assetIds\":[\"" + escapeJson(assetId) + "\"]}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(normalizeImmichApiBase(baseUrl) + "/albums/assets"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        ensureOk(response, "Immich album");
    }

    private static String uploadExternal(
            ScreenshotConfig cfg, File file, byte[] bytes, Listener listener) throws Exception {
        UploaderProfileRegistry.Profile profile = UploaderProfileRegistry.selected(cfg.externalUploaderName);
        if (profile == null) {
            throw new IllegalArgumentException("No external uploader profile found.");
        }
        if (cfg.externalUploaderName == null || cfg.externalUploaderName.isBlank()) {
            cfg.externalUploaderName = profile.name;
            ScreenshotConfig.save();
        }
        return uploadProfile(profile, file, bytes, listener);
    }

    private static String uploadProfile(
            UploaderProfileRegistry.Profile profile, File file, byte[] bytes, Listener listener) throws Exception {
        String url = blankToNull(expand(profile.url, file, bytes));
        if (url == null) throw new IllegalArgumentException("External uploader URL is required.");

        listener.onProgress(0.20);

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(normalizeEndpoint(url)))
                .version(HttpClient.Version.HTTP_1_1);
        for (UploaderProfileRegistry.Entry header : profile.headers) {
            String key = blankToNull(header == null ? null : header.key);
            if (key != null) {
                req.header(key, expand(header.value, file, bytes));
            }
        }

        ScreenshotConfig.UploadMethod method = parseMethod(profile.method);
        if ("RAW_PNG".equalsIgnoreCase(profile.bodyType)) {
            req.header("Content-Type", "image/png");
            applyBody(req, method, HttpRequest.BodyPublishers.ofByteArray(bytes));
        } else {
            MultipartBody body = multipart(blankToDefault(profile.fileField, "file"), file.getName(), bytes);
            for (UploaderProfileRegistry.Entry field : profile.formFields) {
                String key = blankToNull(field == null ? null : field.key);
                if (key != null) {
                    body.field(key, expand(field.value, file, bytes));
                }
            }
            req.header("Content-Type", "multipart/form-data; boundary=" + body.boundary);
            applyBody(req, method, HttpRequest.BodyPublishers.ofByteArray(body.toBytes()));
        }

        HttpResponse<String> response = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
        listener.onProgress(0.85);
        ensureOk(response, profile.name);

        String responseBody = response.body();
        String responseHeader = blankToNull(profile.responseUrlHeader);
        if (responseHeader != null) {
            String value = response.headers().firstValue(responseHeader).orElse("");
            if (!value.isBlank()) return value.trim();
        }

        String responsePath = blankToNull(profile.responseUrlJsonPath);
        String responsePathValue = null;
        if (responsePath != null) {
            responsePathValue = findJsonPath(responseBody, responsePath);
        }

        for (UploaderProfileRegistry.FollowUpRequest request : profile.afterUploadRequests) {
            executeFollowUpRequest(request, file, bytes, responseBody);
        }

        if (responsePathValue != null && !responsePathValue.isBlank()) return responsePathValue.trim();

        String resolved = findUrl(responseBody);
        if (resolved != null) return resolved;
        String fallback = blankToNull(profile.fallbackUrl);
        return fallback == null ? url : expand(fallback, file, bytes, responseBody);
    }

    private static void executeFollowUpRequest(
            UploaderProfileRegistry.FollowUpRequest request,
            File file,
            byte[] bytes,
            String uploadResponseBody) throws Exception {
        if (request == null) return;
        String url = blankToNull(expand(request.url, file, bytes, uploadResponseBody));
        if (url == null) return;

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(normalizeEndpoint(url)))
                .version(HttpClient.Version.HTTP_1_1);
        for (UploaderProfileRegistry.Entry header : request.headers) {
            String key = blankToNull(header == null ? null : header.key);
            if (key != null) {
                req.header(key, expand(header.value, file, bytes, uploadResponseBody));
            }
        }

        ScreenshotConfig.UploadMethod method = parseMethod(request.method);
        if ("FORM".equalsIgnoreCase(request.bodyType)) {
            StringBuilder form = new StringBuilder();
            for (UploaderProfileRegistry.Entry field : request.formFields) {
                String key = blankToNull(field == null ? null : field.key);
                if (key == null) continue;
                if (!form.isEmpty()) form.append('&');
                form.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
                form.append('=');
                form.append(URLEncoder.encode(expand(field.value, file, bytes, uploadResponseBody), StandardCharsets.UTF_8));
            }
            req.header("Content-Type", "application/x-www-form-urlencoded");
            applyBody(req, method, HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8));
        } else if ("EMPTY".equalsIgnoreCase(request.bodyType)) {
            applyBody(req, method, HttpRequest.BodyPublishers.noBody());
        } else {
            req.header("Content-Type", "application/json");
            applyBody(req, method, HttpRequest.BodyPublishers.ofString(
                    expand(request.body, file, bytes, uploadResponseBody), StandardCharsets.UTF_8));
        }

        HttpResponse<String> response = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
        ensureOk(response, blankToDefault(request.name, "Follow-up request"));
    }

    private static String uploadS3(
            ScreenshotConfig cfg, File file, byte[] bytes, Listener listener) throws Exception {
        String bucket = blankToNull(cfg.s3Bucket);
        String accessKey = blankToNull(cfg.s3AccessKey);
        String secretKey = blankToNull(cfg.s3SecretKey);
        if (bucket == null || accessKey == null || secretKey == null) {
            throw new IllegalArgumentException("S3 requires Bucket, Access Key and Secret Key.");
        }

        String region = blankToNull(cfg.s3Region);
        if (region == null) region = "us-east-1";

        String endpoint = blankToNull(cfg.s3Endpoint);
        if (endpoint == null) endpoint = "https://s3." + region + ".amazonaws.com";
        endpoint = normalizeEndpoint(endpoint);
        URI endpointUri = URI.create(endpoint);

        String objectKey = normalizeObjectKey(cfg.s3PathPrefix, file.getName());
        String encodedObjectKey = encodePath(objectKey);

        String basePath = endpointUri.getRawPath() == null ? "" : endpointUri.getRawPath();
        if (!basePath.isBlank() && basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        String canonicalUri = normalizeUriPath(basePath + "/" + bucket + "/" + encodedObjectKey);

        String query = endpointUri.getRawQuery() == null ? "" : endpointUri.getRawQuery();
        String requestUrl = endpointUri.getScheme() + "://" + endpointUri.getAuthority() + canonicalUri
                + (query.isBlank() ? "" : "?" + query);

        String payloadHash = sha256Hex(bytes);
        String contentType = "image/png";
        String hostHeader = endpointUri.getHost()
                + (endpointUri.getPort() > 0 ? ":" + endpointUri.getPort() : "");

        Instant now = Instant.now();
        String amzDate = AMZ_DATE.format(now);
        String date = AMZ_DAY.format(now);

        String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";
        String canonicalHeaders =
                "content-type:" + contentType + "\n" +
                "host:" + hostHeader + "\n" +
                "x-amz-content-sha256:" + payloadHash + "\n" +
                "x-amz-date:" + amzDate + "\n";

        String canonicalRequest =
                "PUT\n" +
                canonicalUri + "\n" +
                query + "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                payloadHash;

        String scope = date + "/" + region + "/s3/aws4_request";
        String stringToSign =
                "AWS4-HMAC-SHA256\n" +
                amzDate + "\n" +
                scope + "\n" +
                sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        signingKey = hmac(signingKey, region);
        signingKey = hmac(signingKey, "s3");
        signingKey = hmac(signingKey, "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(signingKey, stringToSign));

        String authHeader =
                "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope +
                ", SignedHeaders=" + signedHeaders +
                ", Signature=" + signature;

        listener.onProgress(0.25);

        HttpRequest req = HttpRequest.newBuilder(URI.create(requestUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", contentType)
                .header("Host", hostHeader)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

        HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        listener.onProgress(0.85);
        ensureOk(response, "S3");

        return requestUrl;
    }

    private static void waitForFileReady(File file) throws InterruptedException {
        long lastLen = -1;
        for (int i = 0; i < 30; i++) {
            if (file.exists() && file.isFile() && file.canRead()) {
                long len = file.length();
                if (len > 0 && len == lastLen) return;
                lastLen = len;
            }
            Thread.sleep(100);
        }
    }

    private static String normalizeObjectKey(String prefix, String fileName) {
        String cleanPrefix = prefix == null ? "" : prefix.trim();
        cleanPrefix = cleanPrefix.replace("\\", "/");
        while (cleanPrefix.startsWith("/")) cleanPrefix = cleanPrefix.substring(1);
        if (!cleanPrefix.isBlank() && !cleanPrefix.endsWith("/")) cleanPrefix += "/";
        return cleanPrefix + fileName;
    }

    private static String normalizeEndpoint(String endpoint) {
        String e = endpoint.trim();
        if (!e.startsWith("http://") && !e.startsWith("https://")) {
            e = "https://" + e;
        }
        return e;
    }

    private static String normalizeImmichApiBase(String endpoint) {
        String e = normalizeEndpoint(endpoint);
        while (e.endsWith("/")) e = e.substring(0, e.length() - 1);
        return e.endsWith("/api") ? e : e + "/api";
    }

    private static String normalizeUriPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return "/";
        String p = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        return p.replaceAll("/{2,}", "/");
    }

    private static String encodePath(String s) {
        String[] parts = s.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append('/');
            out.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8)
                    .replace("+", "%20"));
        }
        return out.toString();
    }

    private static void ensureOk(HttpResponse<String> response, String provider) throws IOException {
        int code = response.statusCode();
        if (code >= 200 && code < 300) return;
        String body = response.body() == null ? "" : response.body();
        throw new IOException(Component.translatable("better_screenshots.upload.error.http_failed", provider, code, body).getString());
    }

    private static String safeMessage(Throwable throwable) {
        String msg = throwable.getMessage();
        if (msg == null || msg.isBlank()) return throwable.getClass().getSimpleName();
        return msg;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private static String blankToDefault(String value, String fallback) {
        String v = blankToNull(value);
        return v == null ? fallback : v;
    }

    private static ScreenshotConfig.UploadMethod parseMethod(String value) {
        try {
            return ScreenshotConfig.UploadMethod.valueOf(blankToDefault(value, "POST").toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return ScreenshotConfig.UploadMethod.POST;
        }
    }

    private static void applyBody(
            HttpRequest.Builder req,
            ScreenshotConfig.UploadMethod method,
            HttpRequest.BodyPublisher body) {
        switch (method == null ? ScreenshotConfig.UploadMethod.POST : method) {
            case PUT -> req.PUT(body);
            case PATCH -> req.method("PATCH", body);
            case POST -> req.POST(body);
        }
    }

    private static String expand(String value, File file, byte[] bytes) {
        return expand(value, file, bytes, null);
    }

    private static String expand(String value, File file, byte[] bytes, String responseBody) {
        if (value == null) return "";
        String isoModified = Instant.ofEpochMilli(file.lastModified()).toString();
        GameplayPlaceholders gameplay = gameplayPlaceholders();
        String expanded = value
                .replace("{filename}", file.getName())
                .replace("{fileName}", file.getName())
                .replace("{timestamp}", String.valueOf(System.currentTimeMillis()))
                .replace("{isoNow}", Instant.now().toString())
                .replace("{isoModified}", isoModified)
                .replace("{uuid}", UUID.randomUUID().toString())
                .replace("{sha1}", safeDigest("SHA-1", bytes))
                .replace("{sha256}", safeDigest("SHA-256", bytes))
                .replace("{worldName}", gameplay.worldName())
                .replace("{world}", gameplay.worldName())
                .replace("{serverName}", gameplay.serverName())
                .replace("{server}", gameplay.serverName())
                .replace("{serverAddress}", gameplay.serverAddress())
                .replace("{serverIp}", gameplay.serverAddress())
                .replace("{dimension}", gameplay.dimension())
                .replace("{seed}", gameplay.seed())
                .replace("{x}", gameplay.blockX())
                .replace("{y}", gameplay.blockY())
                .replace("{z}", gameplay.blockZ())
                .replace("{blockX}", gameplay.blockX())
                .replace("{blockY}", gameplay.blockY())
                .replace("{blockZ}", gameplay.blockZ())
                .replace("{playerX}", gameplay.playerX())
                .replace("{playerY}", gameplay.playerY())
                .replace("{playerZ}", gameplay.playerZ())
                .replace("{coords}", gameplay.coords())
                .replace("{worldTime}", gameplay.worldTime())
                .replace("{dayTime}", gameplay.dayTime())
                .replace("{timeOfDay}", gameplay.timeOfDay());
        if (responseBody != null) {
            expanded = expandResponsePlaceholders(expanded, responseBody);
        }
        return expanded;
    }

    private record GameplayPlaceholders(
            String worldName,
            String serverName,
            String serverAddress,
            String dimension,
            String seed,
            String blockX,
            String blockY,
            String blockZ,
            String playerX,
            String playerY,
            String playerZ,
            String coords,
            String worldTime,
            String dayTime,
            String timeOfDay) {
        private static GameplayPlaceholders empty() {
            return new GameplayPlaceholders("", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
        }
    }

    private static GameplayPlaceholders gameplayPlaceholders() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null || client.level == null || hasOpenScreen(client)) {
                return GameplayPlaceholders.empty();
            }

            net.minecraft.core.BlockPos blockPos = client.player.blockPosition();
            String blockX = String.valueOf(blockPos.getX());
            String blockY = String.valueOf(blockPos.getY());
            String blockZ = String.valueOf(blockPos.getZ());
            String playerX = String.format(java.util.Locale.ROOT, "%.2f", client.player.getX());
            String playerY = String.format(java.util.Locale.ROOT, "%.2f", client.player.getY());
            String playerZ = String.format(java.util.Locale.ROOT, "%.2f", client.player.getZ());
            long worldTicks = levelTime(client.level, "getGameTime", "gameTime");
            long worldDays = worldTicks / 24000L;
            long dayTime = levelTime(client.level, "getDayTime", "dayTime");
            return new GameplayPlaceholders(
                    singleplayerWorldName(client),
                    serverDataString(client, "name"),
                    serverDataString(client, "ip"),
                    dimensionString(client.level.dimension()),
                    singleplayerSeed(client),
                    blockX,
                    blockY,
                    blockZ,
                    playerX,
                    playerY,
                    playerZ,
                    blockX + "," + blockY + "," + blockZ,
                    String.valueOf(worldDays),
                    String.valueOf(dayTime),
                    formatMinecraftTime(dayTime));
        } catch (Exception ignored) {
            return GameplayPlaceholders.empty();
        }
    }

    private static String singleplayerWorldName(Minecraft client) {
        Object server = callNoArg(client, "getSingleplayerServer");
        Object worldData = callNoArg(server, "getWorldData");
        Object levelName = callNoArg(worldData, "getLevelName");
        return levelName == null ? "" : String.valueOf(levelName);
    }

    private static String singleplayerSeed(Minecraft client) {
        Object server = callNoArg(client, "getSingleplayerServer");
        Object overworld = callNoArg(server, "overworld");
        String levelSeed = seedString(callNoArg(overworld, "getSeed"));
        if (!levelSeed.isBlank()) return levelSeed;

        Object worldData = callNoArg(server, "getWorldData");
        Object options = callNoArg(worldData, "worldGenOptions");
        return seedString(callNoArg(options, "seed"));
    }

    private static String serverDataString(Minecraft client, String fieldName) {
        Object serverData = callNoArg(client, "getCurrentServer");
        if (serverData == null) return "";
        try {
            Object value = serverData.getClass().getField(fieldName).get(serverData);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ignored) {
            try {
                var field = serverData.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(serverData);
                return value == null ? "" : String.valueOf(value);
            } catch (Exception ignoredAgain) {
                return "";
            }
        }
    }

    private static boolean hasOpenScreen(Minecraft client) {
        Object screen = callNoArg(client, "screen");
        if (screen == null) screen = callNoArg(client, "getScreen");
        if (screen != null) return true;
        try {
            return client.getClass().getField("screen").get(client) != null;
        } catch (Exception ignored) {
            try {
                var field = client.getClass().getDeclaredField("screen");
                field.setAccessible(true);
                return field.get(client) != null;
            } catch (Exception ignoredAgain) {
                return false;
            }
        }
    }

    private static String dimensionString(Object dimensionKey) {
        Object location = callNoArg(dimensionKey, "location");
        if (location == null) location = callNoArg(dimensionKey, "value");
        if (location != null) return String.valueOf(location);
        if (dimensionKey == null) return "";
        String raw = String.valueOf(dimensionKey);
        int slash = raw.lastIndexOf(" / ");
        if (slash >= 0) {
            int end = raw.indexOf(']', slash);
            return raw.substring(slash + 3, end >= 0 ? end : raw.length());
        }
        return raw;
    }

    private static long levelTime(Object level, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = callNoArg(level, methodName);
            if (value instanceof Number number) return number.longValue();
        }
        return 0L;
    }

    private static Object callNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            var method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String seedString(Object value) {
        if (value instanceof java.util.OptionalLong optional) {
            return optional.isPresent() ? String.valueOf(optional.getAsLong()) : "";
        }
        if (value instanceof Number number) return String.valueOf(number.longValue());
        return value == null ? "" : String.valueOf(value);
    }

    private static String formatMinecraftTime(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        int hours = (int) ((time / 1000L + 6L) % 24L);
        int minutes = (int) ((time % 1000L) * 60L / 1000L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static String expandResponsePlaceholders(String value, String responseBody) {
        String out = value;
        int start = out.indexOf("{response.");
        while (start >= 0) {
            int end = out.indexOf('}', start);
            if (end < 0) break;
            String path = out.substring(start + "{response.".length(), end);
            String replacement = findJsonPath(responseBody, path);
            if (replacement == null) replacement = "";
            out = out.substring(0, start) + replacement + out.substring(end + 1);
            start = out.indexOf("{response.", start + replacement.length());
        }
        return out;
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safeDigest(String algorithm, byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        return hmac(key, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private static String sha1Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private static String findJsonPath(String payload, String path) {
        if (payload == null || payload.isBlank() || path == null || path.isBlank()) return null;
        try {
            JsonElement current = JsonParser.parseString(payload);
            for (String rawPart : path.split("\\.")) {
                String part = rawPart.trim();
                if (part.isEmpty()) continue;
                if (current == null || current.isJsonNull() || !current.isJsonObject()) return null;
                var obj = current.getAsJsonObject();
                if (!obj.has(part)) return null;
                current = obj.get(part);
            }
            if (current != null && current.isJsonPrimitive()) {
                return current.getAsString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String findUrl(String payload) {
        if (payload == null || payload.isBlank()) return null;

        String trimmed = payload.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            int lineBreak = trimmed.indexOf('\n');
            return lineBreak > 0 ? trimmed.substring(0, lineBreak).trim() : trimmed;
        }

        try {
            JsonElement root = JsonParser.parseString(payload);
            String found = findUrlRecursive(root);
            if (found != null) return found;
        } catch (Exception ignored) {}

        int httpIdx = Math.max(trimmed.indexOf("https://"), trimmed.indexOf("http://"));
        if (httpIdx >= 0) {
            int end = trimmed.indexOf('"', httpIdx);
            if (end < 0) end = trimmed.length();
            return trimmed.substring(httpIdx, end).trim();
        }
        return null;
    }

    private static String findUrlRecursive(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return value;
            }
            return null;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                String found = findUrlRecursive(item);
                if (found != null) return found;
            }
            return null;
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            String[] preferredKeys = {"url", "link", "download_url", "permalink"};
            for (String key : preferredKeys) {
                if (obj.has(key)) {
                    String found = findUrlRecursive(obj.get(key));
                    if (found != null) return found;
                }
            }
            for (String key : obj.keySet()) {
                String found = findUrlRecursive(obj.get(key));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static MultipartBody multipart(String fileField, String fileName, byte[] fileBytes) {
        return new MultipartBody(fileField, fileName, fileBytes);
    }

    private static final class MultipartBody {
        private final String boundary = "----better-screenshots-" + System.nanoTime();
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private MultipartBody(String fileField, String fileName, byte[] fileBytes) {
            try {
                writeTextPart("Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + fileName + "\"\r\n"
                        + "Content-Type: image/png\r\n\r\n", fileBytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private MultipartBody field(String key, String value) {
            try {
                out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Disposition: form-data; name=\"" + key + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.write(value.getBytes(StandardCharsets.UTF_8));
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                return this;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void writeTextPart(String headers, byte[] bytes) throws IOException {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private byte[] toBytes() {
            try {
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return out.toByteArray();
        }
    }
}
