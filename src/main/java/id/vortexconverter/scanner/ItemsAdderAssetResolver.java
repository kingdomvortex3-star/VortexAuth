
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

                String property = entry.getKey()
                        .toLowerCase(Locale.ROOT);

                if (!isAssetReference(property)) {
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
                            property
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

        String cleaned = value.trim()
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .replace("'", "");

        for (String part : cleaned.split("[,\\r\\n]+")) {

            String reference = part.trim()
                    .replace('\\', '/');

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
                extension,
                property
        );

        // Prioritas pertama: namespace yang dinyatakan di referensi.
        if (referenceNamespace != null) {
            for (String candidate : candidates) {
                List<File> matches = index.findExact(
                        candidate,
                        referenceNamespace,
                        referenceNamespace
                );

                if (!matches.isEmpty()) {
                    return matches;
                }
            }

            for (String candidate : candidates) {
                List<File> matches = index.findSuffix(
                        candidate,
                        referenceNamespace
                );

                if (matches.size() == 1) {
                    return matches;
                }
            }

            return List.of();
        }

        // Prioritas kedua: namespace pemilik item.
        for (String candidate : candidates) {
            List<File> matches = index.findExact(
                    candidate,
                    namespace,
                    namespace
            );

            if (!matches.isEmpty()) {
                return matches;
            }
        }

        for (String candidate : candidates) {
            List<File> matches = index.findSuffix(
                    candidate,
                    namespace
            );

            if (matches.size() == 1) {
                return matches;
            }
        }

        // Pencarian global hanya menerima satu hasil yang tidak ambigu.
        for (String candidate : candidates) {
            List<File> matches = index.findGlobalSuffix(candidate);

            if (matches.size() == 1) {
                return matches;
            }
        }

        return List.of();
    }

    private List<String> buildCandidates(
            String reference,
            String extension,
            String property
    ) {

        Set<String> candidates = new LinkedHashSet<>();

        String path = normalizeReference(reference);

        addWithExtension(candidates, path, extension);

        // Hilangkan awalan yang mungkin sudah diberikan konfigurasi.
        String relative = path;

        if (relative.startsWith("resourcepack/assets/")) {
            relative = relative.substring(
                    "resourcepack/assets/".length()
            );
        } else if (relative.startsWith("assets/")) {
            relative = relative.substring("assets/".length());
        } else if (relative.startsWith("textures/")) {
            relative = relative.substring("textures/".length());
        }

        addTextureAliases(
                candidates,
                relative,
                extension,
                property
        );

        return new ArrayList<>(candidates);
    }

    private void addTextureAliases(
            Set<String> candidates,
            String reference,
            String extension,
            String property
    ) {

        String path = normalizeReference(reference);

        if (path.isBlank()) {
            return;
        }

        if (property.contains("texture")) {

            addWithExtension(candidates, path, extension);

            if (path.startsWith("item/")) {
                String remainder = path.substring(5);

                addWithExtension(
                        candidates,
                        "textures/item/" + remainder,
                        extension
                );

                addWithExtension(
                        candidates,
                        "textures/items/" + remainder,
                        extension
                );

            } else if (path.startsWith("items/")) {
                String remainder = path.substring(6);

                addWithExtension(
                        candidates,
                        "textures/items/" + remainder,
                        extension
                );

                addWithExtension(
                        candidates,
                        "textures/item/" + remainder,
                        extension
                );

            } else if (path.startsWith("textures/")) {
                String remainder = path.substring(9);

                addWithExtension(
                        candidates,
                        "textures/" + remainder,
                        extension
                );

            } else {
                addWithExtension(
                        candidates,
                        "textures/item/" + path,
                        extension
                );

                addWithExtension(
                        candidates,
                        "textures/items/" + path,
                        extension
                );

                addWithExtension(
                        candidates,
                        "textures/" + path,
                        extension
                );
            }

            return;
        }

        addWithExtension(candidates, path, extension);

        if (property.contains("model")
                || property.contains("geometry")) {

            addWithExtension(
                    candidates,
                    "models/item/" + path,
                    extension
            );

            addWithExtension(
                    candidates,
                    "models/" + path,
                    extension
            );
        }
    }

    private void addWithExtension(
            Set<String> candidates,
            String path,
            String extension
    ) {

        String normalized = normalizeReference(path);

        if (normalized.isBlank()) {
            return;
        }

        if (!hasExtension(normalized)) {
            normalized += extension;
        }

        candidates.add(normalized);
    }

    private String expectedExtension(String property) {

        if (property.contains("texture")
                || property.equals("graphics.icon")
                || property.equals("icon")) {
            return ".png";
        }

        if (property.contains("model")
                || property.contains("geometry")
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

            Set<String> namespaces = extractNamespaces(path);

            for (String namespace : namespaces) {

                String key = namespace + ":" + basename;

                byNamespaceBasename.computeIfAbsent(
                        key,
                        ignored -> new ArrayList<>()
                ).add(file);
            }
        }

        private Set<String> extractNamespaces(String path) {

            Set<String> namespaces = new LinkedHashSet<>();

            String[] parts = path.split("/");

            if (parts.length > 0) {
                namespaces.add(parts[0]);
            }

            // Mendukung:
            // namespace/resourcepack/assets/namespace/...
            // namespace/assets/namespace/...
            for (int i = 0; i < parts.length - 1; i++) {

                if (parts[i].equals("assets")
                        && i + 1 < parts.length - 1) {

                    namespaces.add(parts[i + 1]);
                }
            }

            return namespaces;
        }

        List<File> findExact(
                String candidate,
                String assetNamespace,
                String referenceNamespace
        ) {

            String normalized = normalize(candidate);

            String preferred = referenceNamespace != null
                    ? referenceNamespace.toLowerCase(Locale.ROOT)
                    : assetNamespace.toLowerCase(Locale.ROOT);

            List<File> namespaced = byPath.get(
                    preferred + "/" + normalized
            );

            if (namespaced != null && !namespaced.isEmpty()) {
                return unique(namespaced);
            }

            List<File> exact = byPath.get(normalized);

            return exact == null ? List.of() : unique(exact);
        }

        List<File> findSuffix(
                String suffix,
                String namespace
        ) {

            String normalized = normalize(suffix);
            String preferred = namespace.toLowerCase(Locale.ROOT);

            List<File> matches = new ArrayList<>();

            for (Map.Entry<String, List<File>> entry
                    : byPath.entrySet()) {

                String path = entry.getKey();

                if (!belongsToNamespace(path, preferred)) {
                    continue;
                }

                if (path.equals(preferred + "/" + normalized)
                        || path.endsWith("/" + normalized)) {

                    matches.addAll(entry.getValue());
                }
            }

            return unique(matches);
        }

        List<File> findGlobalSuffix(String suffix) {

            String normalized = normalize(suffix);
            List<File> matches = new ArrayList<>();

            for (Map.Entry<String, List<File>> entry
                    : byPath.entrySet()) {

                String path = entry.getKey();

                if (path.equals(normalized)
                        || path.endsWith("/" + normalized)) {

                    matches.addAll(entry.getValue());
                }
            }

            return unique(matches);
        }

        private boolean belongsToNamespace(
                String path,
                String namespace
        ) {

            return path.startsWith(namespace + "/")
                    || path.contains("/assets/" + namespace + "/");
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
