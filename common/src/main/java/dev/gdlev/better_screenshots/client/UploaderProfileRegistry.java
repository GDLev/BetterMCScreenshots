package dev.gdlev.better_screenshots.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class UploaderProfileRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<Profile> cachedProfiles = new ArrayList<>();

    private UploaderProfileRegistry() {}

    public static List<Profile> reload() {
        cachedProfiles = loadProfiles();
        return cachedProfiles;
    }

    public static List<Profile> profiles() {
        if (cachedProfiles.isEmpty()) {
            cachedProfiles = loadProfiles();
        }
        return cachedProfiles;
    }

    public static Profile selected(String name) {
        String wanted = name == null ? "" : name.trim();
        for (Profile profile : profiles()) {
            if (profile.name != null && profile.name.equals(wanted)) {
                return profile;
            }
        }
        return profiles().isEmpty() ? null : profiles().getFirst();
    }

    public static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("better_screenshots_uploaders");
    }

    public static void ensureDirectory() {
        try {
            Files.createDirectories(directory());
        } catch (Exception ignored) {}
    }

    private static List<Profile> loadProfiles() {
        List<Profile> result = new ArrayList<>();
        try {
            ensureDirectory();
            try (var stream = Files.list(directory())) {
                stream.filter(path -> {
                            String fileName = path.getFileName().toString();
                            return fileName.endsWith(".json") && !fileName.startsWith("_");
                        })
                        .sorted()
                        .forEach(path -> {
                            try (Reader reader = Files.newBufferedReader(path)) {
                                Profile profile = GSON.fromJson(reader, Profile.class);
                                normalize(profile);
                                if (profile != null && profile.name != null && !profile.name.isBlank()) {
                                    result.add(profile);
                                }
                            } catch (Exception ignored) {}
                        });
            }
        } catch (Exception ignored) {}
        result.sort(Comparator.comparing(profile -> profile.name.toLowerCase(java.util.Locale.ROOT)));
        return result;
    }

    private static void normalize(Profile profile) {
        if (profile == null) return;
        if (profile.name == null) profile.name = "";
        if (profile.url == null) profile.url = "";
        if (profile.method == null || profile.method.isBlank()) profile.method = "POST";
        if (profile.bodyType == null || profile.bodyType.isBlank()) profile.bodyType = "MULTIPART";
        if (profile.fileField == null || profile.fileField.isBlank()) profile.fileField = "file";
        if (profile.headers == null) profile.headers = new ArrayList<>();
        if (profile.formFields == null) profile.formFields = new ArrayList<>();
        if (profile.afterUploadRequests == null) profile.afterUploadRequests = new ArrayList<>();
        if (profile.responseUrlJsonPath == null) profile.responseUrlJsonPath = "";
        if (profile.responseUrlHeader == null) profile.responseUrlHeader = "";
        if (profile.fallbackUrl == null) profile.fallbackUrl = "";
        for (FollowUpRequest request : profile.afterUploadRequests) {
            normalize(request);
        }
    }

    private static void normalize(FollowUpRequest request) {
        if (request == null) return;
        if (request.name == null) request.name = "";
        if (request.url == null) request.url = "";
        if (request.method == null || request.method.isBlank()) request.method = "POST";
        if (request.bodyType == null || request.bodyType.isBlank()) request.bodyType = "JSON";
        if (request.headers == null) request.headers = new ArrayList<>();
        if (request.formFields == null) request.formFields = new ArrayList<>();
        if (request.body == null) request.body = "";
    }

    public static class Profile {
        public String name = "";
        public String url = "";
        public String method = "POST";
        public String bodyType = "MULTIPART";
        public String fileField = "file";
        public List<Entry> headers = new ArrayList<>();
        public List<Entry> formFields = new ArrayList<>();
        public List<FollowUpRequest> afterUploadRequests = new ArrayList<>();
        public String responseUrlJsonPath = "";
        public String responseUrlHeader = "";
        public String fallbackUrl = "";
    }

    public static class FollowUpRequest {
        public String name = "";
        public String url = "";
        public String method = "POST";
        public String bodyType = "JSON";
        public List<Entry> headers = new ArrayList<>();
        public List<Entry> formFields = new ArrayList<>();
        public String body = "";
    }

    public static class Entry {
        public String key = "";
        public String value = "";

        public Entry() {}

        public Entry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
