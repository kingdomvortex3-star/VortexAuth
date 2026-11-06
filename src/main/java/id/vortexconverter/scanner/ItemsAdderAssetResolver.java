
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

        public ResolvedAsset(ItemsAdderConfigParser.AssetInfo asset) {
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
                            Collections.unmodifiableList(value)
                    )
            );

            return Collections.unmodifiableMap(copy);
        }

        public List<String> getMissingReferences() {
            return Collections.unmodifiableList(missingReferences);
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

        private final List<ResolvedAsset> assets = new ArrayList<>();
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

        if (parsed == null || contentsFolder == null
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

        for (ItemsAdderConfigParser.AssetInfo asset : parsed.getAssets()) {
            ResolvedAsset resolved = new ResolvedAsset(asset);

            for (Map.Entry<String, String> entry
                    : asset.getProperties().entrySet()) {

                String key = entry.getKey().toLowerCase(Locale.ROOT);

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

                    String resultKey = entry.getKey() + ":" + reference;

                    if (matches.isEmpty()) {
                        resolved.missingReferences.add(
                                resultKey
                        );
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
                || key.contains("custom_model_data")) {
            return false;
        }

        return key.equals("texture")
                || key.equals("textures")
                || key.endsWith(".texture")
                || key.endsWith(".textures")
                || key.contains("texture_path")
                || key.equals("model")
                || key.endsWith(".model")
                || key.contains("model_path")
                || key.contains("item_model")
                || key.equals("geometry")
                || key.contains("sound")
                || key.endsWith("_file")
                || key.endsWith("_path");
    }

    private List<String> splitReferences(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        Set<String> references = new LinkedHashSet<>();

        String cleaned = value.trim();

        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        for (String part : cleaned.split(",")) {
            String reference = part.trim()
                    .replace("\\", "/")
                    .replace("\"", "")
                    .replace("'", "");

            if (reference.isBlank()
                    || reference.equalsIgnoreCase("none")
                    || reference.equalsIgnoreCase("null")
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
            referenceNamespace = normalized.substring(0, colon);
            normalized = normalized.substring(colon + 1);
        }

        String extension = expectedExtension(property);

        List<String> candidates = new ArrayList<>();

        addCandidate(candidates, normalized, extension);

        if (!normalized.startsWith("textures/")) {
            addCandidate(
                    candidates,
                    "textures/" + normalized,
                    extension
            );
        }

        if (!normalized.startsWith("resource/")) {
            addCandidate(
                    candidates,
                    "resource/" + normalized,
                    extension
            );
        }

        if (extension.equals(".png")) {
            addCandidate(
                    candidates,
                    "textures/items/" + normalized,
                    extension
            );
            addCandidate(
                    candidates,
                    "textures/blocks/" + normalized,
                    extension
            );
        }

        for (String candidate : candidates) {
            List<File> exactMatches = index.findExact(
                    candidate,
                    referenceNamespace,
                    namespace
            );

            if (!exactMatches.isEmpty()) {
                return exactMatches;
            }
        }

        for (String candidate : candidates) {
            List<File> suffixMatches = index.findSuffix(
                    candidate,
                    referenceNamespace,
                    namespace
            );

            if (!suffixMatches.isEmpty()) {
                return suffixMatches;
            }
        }

        String basename = normalized.substring(
                normalized.lastIndexOf('/') + 1
        );

        if (!hasExtension(basename)) {
            basename += extension;
        }

        List<File> namespaceMatches = index.findBasename(
                basename,
                namespace
        );

        if (!namespaceMatches.isEmpty()) {
            return namespaceMatches;
        }

        if (referenceNamespace != null) {
            return index.findBasename(
                    basename,
                    referenceNamespace
            );
        }

        return List.of();
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
                || property.contains("item_model")) {
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

        void add(String relativePath, File file) {
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

            if (parts.length >= 2) {
                String namespace = parts[0];

                String key = namespace + ":" + basename;

                byNamespaceBasename.computeIfAbsent(
                        key,
                        ignored -> new ArrayList<>()
                ).add(file);
            }
        }

        List<File> findExact(
                String path,
                String referenceNamespace,
                String assetNamespace
        ) {
            String normalized = normalize(path);

            if (referenceNamespace != null) {
                List<File> namespaced = byPath.get(
                        referenceNamespace.toLowerCase(Locale.ROOT)
                                + "/" + normalized
                );

                if (namespaced != null && !namespaced.isEmpty()) {
                    return unique(namespaced);
                }
            }

            List<File> local = byPath.get(
                    assetNamespace.toLowerCase(Locale.ROOT)
                            + "/" + normalized
            );

            if (local != null && !local.isEmpty()) {
                return unique(local);
            }

            List<File> exact = byPath.get(normalized);

            return exact == null ? List.of() : unique(exact);
        }

        List<File> findSuffix(
                String suffix,
                String referenceNamespace,
                String assetNamespace
        ) {
            String normalized = normalize(suffix);

            List<File> matches = new ArrayList<>();

            String requiredNamespace = referenceNamespace != null
                    ? referenceNamespace.toLowerCase(Locale.ROOT)
                    : assetNamespace.toLowerCase(Locale.ROOT);

            for (Map.Entry<String, List<File>> entry : byPath.entrySet()) {
                String path = entry.getKey();

                if (!path.startsWith(requiredNamespace + "/")) {
                    continue;
                }

                if (path.endsWith("/" + normalized)
                        || path.equals(requiredNamespace + "/" + normalized)) {
                    matches.addAll(entry.getValue());
                }
            }

            if (matches.isEmpty() && referenceNamespace == null) {
                for (Map.Entry<String, List<File>> entry : byPath.entrySet()) {
                    if (entry.getKey().endsWith("/" + normalized)) {
                        matches.addAll(entry.getValue());
                    }
                }
            }

            return unique(matches);
        }

        List<File> findBasename(
                String basename,
                String namespace
        ) {
            String key = namespace.toLowerCase(Locale.ROOT)
                    + ":" + basename.toLowerCase(Locale.ROOT);

            List<File> matches = byNamespaceBasename.get(key);

            return matches == null ? List.of() : unique(matches);
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
