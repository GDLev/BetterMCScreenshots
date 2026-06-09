package dev.gdlev.better_screenshots.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
import java.util.HexFormat;
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
                    case DISABLED -> null;
                };

                if (uploadedUrl == null || uploadedUrl.isBlank()) {
                    throw new IOException("Uploader returned empty URL.");
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

        client.getToastManager().addToast(
                SystemToast.multiline(client, SystemToast.SystemToastId.NARRATOR_TOGGLE, title, description)
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
                ScreenshotPreviewRenderer.markUploadError("File not found");
            }
            if (cfg.uploadChatNotification && client.player != null) {
                client.player.displayClientMessage(Component.translatable(
                        "better_screenshots.upload.error", "Screenshot file not found"), false);
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
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uploadedUrl)));
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
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                                            Component.translatable("better_screenshots.chat.copy_hint")
                                                                    .withStyle(ChatFormatting.DARK_GRAY)))
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                                                            "/better_screenshots copy_upload " + id))))
                                    .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
                            message = message.append(sep).append(copyBtn);
                        }

                        client.player.displayClientMessage(message, false);
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
                        client.player.displayClientMessage(Component.translatable(
                                "better_screenshots.upload.error", error).withStyle(ChatFormatting.RED), false);
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
            throw new IOException("Imgur response does not contain uploaded URL.");
        }
        return url;
    }

    private static String uploadCatbox(
            File file, byte[] bytes, Listener listener) throws Exception {
        listener.onProgress(0.20);
        MultipartBody body = multipart("fileToUpload", file.getName(), bytes)
                .field("reqtype", "fileupload");

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://catbox.moe/user/api.php"))
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

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(normalizeEndpoint(url)));

        String cookieKey = blankToNull(cfg.customCookieKey);
        String cookieValue = blankToNull(cfg.customCookieValue);
        if (cookieKey != null) {
            req.header("Cookie", cookieKey + "=" + (cookieValue == null ? "" : cookieValue));
        }

        String headerKey = blankToNull(cfg.customHeaderKey);
        String headerValue = blankToNull(cfg.customHeaderValue);
        if (headerKey != null) {
            req.header(headerKey, headerValue == null ? "" : headerValue);
        }

        if (cfg.customUploadMethod == ScreenshotConfig.UploadMethod.PUT) {
            req.header("Content-Type", "image/png");
            req.PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        } else {
            MultipartBody body = multipart("file", file.getName(), bytes);
            String postKey = blankToNull(cfg.customPostKey);
            if (postKey != null) {
                body.field(postKey, cfg.customPostValue == null ? "" : cfg.customPostValue);
            }
            req.header("Content-Type", "multipart/form-data; boundary=" + body.boundary);
            req.POST(HttpRequest.BodyPublishers.ofByteArray(body.toBytes()));
        }

        HttpResponse<String> response = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
        listener.onProgress(0.85);
        ensureOk(response, "Custom uploader");

        String resolved = findUrl(response.body());
        if (resolved != null) return resolved;

        return url;
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
        throw new IOException(provider + " upload failed (" + code + "): " + body);
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
