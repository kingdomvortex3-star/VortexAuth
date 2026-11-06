
package id.vortexconverter.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ItemsAdderAssetResolver {

    public static final class ResolvedAsset {

        private final ItemsAdderConfigParser.AssetInfo asset;

        private final Map<String, List<File>> resolvedFiles =
                new LinkedHashMap<>();

        private final List<String> missingReferences =
                new ArrayList<>();

        public ResolvedAsset(
                ItemsAdderConfigParser.AssetInfo asset
        ) {
            this.asset = asset;
        }

        public ItemsAdderConfigParser.AssetInfo getAsset() {
            return asset;
        }

        public Map<String, List<File>> getResolvedFiles() {
            Map<String, List<File>> copy = new LinkedHashMap<>();

            resolvedFiles.forEach(
                    (key, value) -> copy.put(
                            key,
                            Collections.unmodifiableList(
                                    new ArrayList<>(value)
                            )
                    )
            );

            return Collections.unmodifiableMap(copy);
        }

        public List<String> getMissingReferences() {
            return Collections.unmodifiableList(
                    missingReferences
            );
        }

        public int getResolvedFileCount() {
            return resolvedFiles.values().stream()
                    .mapToInt(List::size)
                    .sum();
        }

        public boolean hasMissingReferences() {
            return !missingReferences.isEmpty();
        }
    }

    public static final class ResolutionResult {

        private final List<ResolvedAsset> assets =
                new ArrayList<>();

        private int scannedFiles;
        private int resolvedFiles;
        private int missingReferences;

        public List<ResolvedAsset> getAssets() {
            return Collections.unmodifiableList(assets);
        }

        public int getScannedFiles() {
            return scannedFiles;
        }

        public int getResolvedFiles() {
            return resolvedFiles;
        }

        public int getMissingReferences() {
            return missingReferences;
        }

        public int getTotalAssets() {
            return assets.size();
        }
    }

    public ResolutionResult resolve(
            ItemsAdderConfigParser.ParseResult parsed,
            File contentsFolder
    ) {
        ResolutionResult result = new ResolutionResult();

        if (parsed == null
                || contentsFolder == null
                || !contentsFolder.isDirectory()) {
            return result;
        }

        FileIndex index;

        try {
            index = buildFileIndex(contentsFolder, result);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to index ItemsAdder assets: "
                            + exception.getMessage(),
                    exception
            );
        }

        for (ItemsAdderConfigParser.AssetInfo asset
                : parsed.getAssets()) {

            ResolvedAsset resolved = new ResolvedAsset(asset);

            for (Map.Entry<String, String> entry
                    : asset.getProperties().entrySet()) {

                String key = entry.getKey()
                        .toLowerCase(Locale.ROOT);

                if (!isAssetReference(key)) {
                    continue;
                }

                List<String> references = splitReferences(
                        entry.getValue()
                );

                for (String reference : references) {

                    List<File> matches = findFiles(
                            reference,
                            asset.getNamespace(),
                            index,
                            key
                    );

                    String resultKey =
                            entry.getKey() + ":" + reference;

                    if (matches.isEmpty()) {
                        resolved.missingReferences.add(resultKey);
                    } else {
                        resolved.resolvedFiles.put(
                                resultKey,
                                matches
                        );

                        result.resolvedFiles += matches.size();
                    }
                }
            }

            result.missingReferences +=
                    resolved.missingReferences.size();

            result.assets.add(resolved);
        }

        return result;
    }

    private FileIndex buildFileIndex(
            File root,
            ResolutionResult result
    ) throws IOException {

        Path rootPath = root.toPath().toRealPath();
        FileIndex index = new FileIndex();

        try (Stream<Path> paths = Files.walk(rootPath)) {

            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .toList()) {

                Path realPath = path.toRealPath();

                if (!realPath.startsWith(rootPath)) {
                    continue;
                }

                File file = realPath.toFile();

                String relative = rootPath.relativize(realPath)
                        .toString()
                        .replace('\\', '/')
                        .toLowerCase(Locale.ROOT);

                index.add(relative, file);
                result.scannedFiles++;
            }
        }

        return index;
    }

    private boolean isAssetReference(String key) {

        if (key.contains("material")
                || key.contains("display_name")
                || key.contains("permission")
                || key.contains("custom_model_data")
                || key.contains("model_id")) {
            return false;
        }

        return key.equals("texture")
                || key.equals("textures")
                || key.endsWith(".texture")
                || key.endsWith(".textures")
                || key.contains("texture_path")
                || key.contains("texture_file")
                || key.equals("model")
                || key.endsWith(".model")
                || key.contains("model_path")
                || key.contains("model_file")
                || key.contains("item_model")
                || key.equals("geometry")
                || key.contains("geometry_path")
                || key.contains("sound")
                || key.contains("animation")
                || key.endsWith("_file")
                || key.endsWith("_path");
    }

    private List<String> splitReferences(String value) {

        if (value == null || value.isBlank()) {
            return List.of();
        }

        Set<String> references = new LinkedHashSet<>();

        String cleaned = value.trim();

        cleaned = cleaned
                .replace("[", "")
                .replace("]", "")
                .replace("(", "")
                .replace(")", "")
                .replace("\"", "")
                .replace("'", "");

        String[] parts = cleaned.split("[,\\r\\n]+");

        for (String part : parts) {

            String reference = part.trim()
                    .replace("\\", "/");

            if (reference.startsWith("-")) {
                reference = reference.substring(1).trim();
            }

            if (reference.isBlank()
                    || reference.equalsIgnoreCase("none")
                    || reference.equalsIgnoreCase("null")
                    || reference.equalsIgnoreCase("false")
                    || reference.equalsIgnoreCase("true")
                    || reference.contains("{")
                    || reference.contains("}")) {
                continue;
            }

            references.add(reference);
        }

        return new ArrayList<>(references);
    }

    private List<File> findFiles(
            String reference,
            String namespace,
            FileIndex index,
            String property
    ) {

        String normalized = normalizeReference(reference);

        if (normalized.isBlank()) {
            return List.of();
        }

        String referenceNamespace = null;

        int colon = normalized.indexOf(':');

        if (colon > 0) {
            referenceNamespace =
                    normalized.substring(0, colon);

            normalized = normalized.substring(colon + 1);
        }

        String extension = expectedExtension(property);

        List<String> candidates = buildCandidates(
                normalized,
                extension
        );

        // 1. Cari kecocokan path lengkap terlebih dahulu.
        for (String candidate : candidates) {

            List<File> matches = index.findExact(
                    candidate,
                    namespace,
                    referenceNamespace
            );

            if (!matches.isEmpty()) {
                return matches;
            }
        }

        // 2. Cari berdasarkan suffix path.
        for (String candidate : candidates) {

            List<File> matches = index.findSuffix(
                    candidate,
                    namespace,
                    referenceNamespace
            );

            if (!matches.isEmpty()) {
                return matches;
            }
        }

        // 3. Pencarian basename hanya jika hasilnya tidak ambigu.
        String basename = normalized.substring(
                normalized.lastIndexOf('/') + 1
        );

        if (!hasExtension(basename)) {
            basename += extension;
        }

        List<File> basenameMatches = index.findBasename(
                basename,
                namespace,
                referenceNamespace
        );

        if (basenameMatches.size() == 1) {
            return basenameMatches;
        }

        return List.of();
    }

    private List<String> buildCandidates(
            String reference,
            String extension
    ) {

        List<String> candidates = new ArrayList<>();

        addCandidate(candidates, reference, extension);

        String path = normalizeReference(reference);

        if (path.startsWith("textures/")) {
            addCandidate(
                    candidates,
                    path.substring("textures/".length()),
                    extension
            );
        }

        if (path.startsWith("resourcepack/assets/")) {
            addCandidate(
                    candidates,
                    path.substring("resourcepack/assets/".length()),
                    extension
            );
        }

        if (path.startsWith("assets/")) {
            addCandidate(
                    candidates,
                    path.substring("assets/".length()),
                    extension
            );
        }

        if (!path.startsWith("textures/")) {
            addCandidate(
                    candidates,
                    "textures/" + path,
                    extension
            );
        }

        if (!path.startsWith("resourcepack/assets/")) {
            addCandidate(
                    candidates,
                    "resourcepack/assets/" + path,
                    extension
            );
        }

        if (!path.startsWith("assets/")) {
            addCandidate(
                    candidates,
                    "assets/" + path,
                    extension
            );
        }

        return candidates;
    }

    private void addCandidate(
            List<String> candidates,
            String value,
            String extension
    ) {

        String normalized = normalizeReference(value);

        if (normalized.isBlank()) {
            return;
        }

        if (!hasExtension(normalized)) {
            normalized += extension;
        }

        if (!candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private String expectedExtension(String property) {

        if (property.contains("texture")) {
            return ".png";
        }

        if (property.contains("model")
                || property.contains("geometry")
                || property.contains("item_model")
                || property.contains("animation")) {
            return ".json";
        }

        if (property.contains("sound")) {
            return ".ogg";
        }

        return ".png";
    }

    private String normalizeReference(String value) {

        String normalized = value.trim()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return normalized;
    }

    private boolean hasExtension(String value) {

        int slash = value.lastIndexOf('/');
        int dot = value.lastIndexOf('.');

        return dot > slash;
    }

    private static final class FileIndex {

        private final Map<String, List<File>> byPath =
                new LinkedHashMap<>();

        private final Map<String, List<File>> byBasename =
                new LinkedHashMap<>();

        private final Map<String, List<File>> byNamespaceBasename =
                new LinkedHashMap<>();

        void add(
                String relativePath,
                File file
        ) {

            String path = normalize(relativePath);

            byPath.computeIfAbsent(
                    path,
                    ignored -> new ArrayList<>()
            ).add(file);

            String basename = file.getName()
                    .toLowerCase(Locale.ROOT);

            byBasename.computeIfAbsent(
                    basename,
                    ignored -> new ArrayList<>()
            ).add(file);

            String[] parts = path.split("/");

            Set<String> namespaces = new LinkedHashSet<>();

            if (parts.length > 0) {
                namespaces.add(parts[0]);
            }

            // Dukungan struktur:
            // namespace/resourcepack/assets/namespace/textures/...
            // namespace/assets/namespace/textures/...
            for (int i = 0; i < parts.length - 1; i++) {

                if (parts[i].equals("assets")
                        && i + 1 < parts.length - 1) {

                    namespaces.add(parts[i + 1]);
                }
            }

            for (String namespace : namespaces) {

                String key = namespace + ":" + basename;

                byNamespaceBasename.computeIfAbsent(
                        key,
                        ignored -> new ArrayList<>()
                ).add(file);
            }
        }

        List<File> findExact(
                String candidate,
                String assetNamespace,
                String referenceNamespace
        ) {

            String normalized = normalize(candidate);

            String preferredNamespace =
                    referenceNamespace != null
                            ? referenceNamespace.toLowerCase(Locale.ROOT)
                            : assetNamespace.toLowerCase(Locale.ROOT);

            List<File> preferred = byPath.get(
                    preferredNamespace + "/" + normalized
            );

            if (preferred != null && !preferred.isEmpty()) {
                return unique(preferred);
            }

            List<File> exact = byPath.get(normalized);

            if (exact != null && !exact.isEmpty()) {
                return unique(exact);
            }

            return List.of();
        }

        List<File> findSuffix(
                String suffix,
                String assetNamespace,
                String referenceNamespace
        ) {

            String normalized = normalize(suffix);

            String preferredNamespace =
                    referenceNamespace != null
                            ? referenceNamespace.toLowerCase(Locale.ROOT)
                            : assetNamespace.toLowerCase(Locale.ROOT);

            List<File> preferredMatches = new ArrayList<>();

            for (Map.Entry<String, List<File>> entry
                    : byPath.entrySet()) {

                String path = entry.getKey();

                if (!belongsToNamespace(
                        path,
                        preferredNamespace
                )) {
                    continue;
                }

                if (path.equals(preferredNamespace + "/" + normalized)
                        || path.endsWith("/" + normalized)) {

                    preferredMatches.addAll(entry.getValue());
                }
            }

            List<File> preferredUnique = unique(preferredMatches);

            if (!preferredUnique.isEmpty()) {
                return preferredUnique;
            }

            if (referenceNamespace != null) {
                return List.of();
            }

            List<File> globalMatches = new ArrayList<>();

            for (Map.Entry<String, List<File>> entry
                    : byPath.entrySet()) {

                String path = entry.getKey();

                if (path.equals(normalized)
                        || path.endsWith("/" + normalized)) {

                    globalMatches.addAll(entry.getValue());
                }
            }

            return unique(globalMatches);
        }

        List<File> findBasename(
                String basename,
                String assetNamespace,
                String referenceNamespace
        ) {

            String namespace = referenceNamespace != null
                    ? referenceNamespace.toLowerCase(Locale.ROOT)
                    : assetNamespace.toLowerCase(Locale.ROOT);

            String key = namespace + ":"
                    + basename.toLowerCase(Locale.ROOT);

            List<File> matches = byNamespaceBasename.get(key);

            if (matches != null && !matches.isEmpty()) {
                return unique(matches);
            }

            if (referenceNamespace != null) {
                return List.of();
            }

            List<File> global = byBasename.get(
                    basename.toLowerCase(Locale.ROOT)
            );

            return global == null ? List.of() : unique(global);
        }

        private boolean belongsToNamespace(
                String path,
                String namespace
        ) {

            if (path.startsWith(namespace + "/")) {
                return true;
            }

            return path.contains(
                    "/assets/" + namespace + "/"
            );
        }

        private List<File> unique(List<File> files) {
            return new ArrayList<>(
                    new LinkedHashSet<>(files)
            );
        }

        private String normalize(String value) {
            return value.replace('\\', '/')
                    .toLowerCase(Locale.ROOT);
        }
    }
}
