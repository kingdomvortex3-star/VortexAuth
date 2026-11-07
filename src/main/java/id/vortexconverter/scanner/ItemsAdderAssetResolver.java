
package id.vortexconverter.scanner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    private static final int MAX_PARENT_DEPTH = 32;

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

            resolvedFiles.forEach((key, value) ->
                    copy.put(
                            key,
                            Collections.unmodifiableList(
                                    new ArrayList<>(value)
                            )
                    )
            );

            return Collections.unmodifiableMap(copy);
        }

        public List<String> getMissingReferences() {
            return Collections.unmodifiableList(missingReferences);
        }

        public int getResolvedFileCount() {
            return resolvedFiles.values()
                    .stream()
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

                for (String reference : splitReferences(entry.getValue())) {

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
                        continue;
                    }

                    putResolvedFiles(
                            resolved,
                            result,
                            resultKey,
                            matches
                    );

                    if (isModelProperty(property)) {
                        for (File modelFile : matches) {
                            resolveModelTextures(
                                    modelFile,
                                    reference,
                                    asset.getNamespace(),
                                    index,
                                    resolved,
                                    result
                            );
                        }
                    }
                }
            }

            result.missingReferences +=
                    resolved.missingReferences.size();

            result.assets.add(resolved);
        }

        return result;
    }

    private void resolveModelTextures(
            File modelFile,
            String modelReference,
            String assetNamespace,
            FileIndex index,
            ResolvedAsset resolved,
            ResolutionResult result
    ) {
        Map<String, String> inheritedTextures =
                new LinkedHashMap<>();

        Set<String> visitedModels = new LinkedHashSet<>();

        collectModelTextures(
                modelFile,
                modelReference,
                modelReference,
                assetNamespace,
                index,
                inheritedTextures,
                visitedModels,
                resolved,
                result,
                0
        );

        for (Map.Entry<String, String> entry
                : inheritedTextures.entrySet()) {

            String textureKey = entry.getKey();

            String textureReference = resolveTextureAlias(
                    entry.getValue(),
                    inheritedTextures,
                    new LinkedHashSet<>()
            );

            if (textureReference == null
                    || textureReference.isBlank()) {

                resolved.missingReferences.add(
                        "model_texture_alias:"
                                + modelReference
                                + ":"
                                + textureKey
                                + ":"
                                + entry.getValue()
                );

                continue;
            }

            if (textureReference.startsWith("#")) {
                resolved.missingReferences.add(
                        "model_texture_alias:"
                                + modelReference
                                + ":"
                                + textureKey
                                + ":"
                                + textureReference
                );

                continue;
            }

            List<File> textureFiles = findFiles(
                    textureReference,
                    assetNamespace,
                    index,
                    "texture"
            );

            String resultKey =
                    "textures:model_texture:"
                            + modelReference
                            + ":"
                            + textureKey
                            + ":"
                            + textureReference;

            if (textureFiles.isEmpty()) {
                if (!containsResolvedKey(resolved, resultKey)) {
                    resolved.missingReferences.add(resultKey);
                }

                continue;
            }

            putResolvedFiles(
                    resolved,
                    result,
                    resultKey,
                    textureFiles
            );
        }
    }

    private void collectModelTextures(
            File modelFile,
            String modelReference,
            String rootModelReference,
            String assetNamespace,
            FileIndex index,
            Map<String, String> inheritedTextures,
            Set<String> visitedModels,
            ResolvedAsset resolved,
            ResolutionResult result,
            int depth
    ) {
        if (modelFile == null || !modelFile.isFile()) {
            return;
        }

        if (depth >= MAX_PARENT_DEPTH) {
            resolved.missingReferences.add(
                    "model_parent_depth:"
                            + rootModelReference
                            + ":batas kedalaman parent tercapai."
            );
            return;
        }

        String modelIdentity;

        try {
            modelIdentity = modelFile.toPath()
                    .toRealPath()
                    .toString()
                    .toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            modelIdentity = modelFile.getAbsolutePath()
                    .toLowerCase(Locale.ROOT);
        }

        if (!visitedModels.add(modelIdentity)) {
            resolved.missingReferences.add(
                    "model_parent_cycle:"
                            + rootModelReference
                            + ":"
                            + modelReference
            );
            return;
        }

        try {
            String json = Files.readString(modelFile.toPath());

            JsonElement rootElement =
                    JsonParser.parseString(json);

            if (!rootElement.isJsonObject()) {
                resolved.missingReferences.add(
                        "model_invalid_json:"
                                + modelReference
                );
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();

            // Parent harus diproses sebelum texture child.
            if (root.has("parent")
                    && root.get("parent").isJsonPrimitive()
                    && root.getAsJsonPrimitive("parent").isString()) {

                String parentReference =
                        root.get("parent").getAsString().trim();

                if (!parentReference.isBlank()
                        && !parentReference.equalsIgnoreCase("null")
                        && !parentReference.startsWith("minecraft:")) {

                    List<File> parentFiles = findFiles(
                            parentReference,
                            assetNamespace,
                            index,
                            "model"
                    );

                    if (parentFiles.isEmpty()) {
                        resolved.missingReferences.add(
                                "model_parent:"
                                        + rootModelReference
                                        + ":"
                                        + parentReference
                        );
                    } else {
                        for (File parentFile : parentFiles) {

                            String parentKey =
                                    "models:parent:"
                                            + rootModelReference
                                            + ":"
                                            + parentReference;

                            // Simpan file parent agar tahap Builder
                            // dapat mengambil geometry-nya.
                            putResolvedFiles(
                                    resolved,
                                    result,
                                    parentKey,
                                    List.of(parentFile)
                            );

                            collectModelTextures(
                                    parentFile,
                                    parentReference,
                                    rootModelReference,
                                    assetNamespace,
                                    index,
                                    inheritedTextures,
                                    visitedModels,
                                    resolved,
                                    result,
                                    depth + 1
                            );
                        }
                    }
                }
            }

            // Texture child menimpa key parent yang sama.
            if (root.has("textures")
                    && root.get("textures").isJsonObject()) {

                JsonObject textures =
                        root.getAsJsonObject("textures");

                for (Map.Entry<String, JsonElement> entry
                        : textures.entrySet()) {

                    JsonElement value = entry.getValue();

                    if (!value.isJsonPrimitive()
                            || !value.getAsJsonPrimitive().isString()) {
                        continue;
                    }

                    String textureReference =
                            value.getAsString().trim();

                    if (textureReference.isBlank()
                            || textureReference.equalsIgnoreCase("null")) {
                        continue;
                    }

                    inheritedTextures.put(
                            entry.getKey(),
                            textureReference
                    );
                }
            }

        } catch (Exception exception) {
            resolved.missingReferences.add(
                    "model_read:"
                            + modelReference
                            + ":"
                            + modelFile.getName()
                            + ":"
                            + exception.getMessage()
            );
        } finally {
            visitedModels.remove(modelIdentity);
        }
    }

    private void putResolvedFiles(
            ResolvedAsset resolved,
            ResolutionResult result,
            String key,
            List<File> files
    ) {
        if (files == null || files.isEmpty()) {
            return;
        }

        List<File> existing = resolved.resolvedFiles.get(key);

        if (existing == null) {
            existing = new ArrayList<>();
            resolved.resolvedFiles.put(key, existing);
        }

        Set<String> knownPaths = new LinkedHashSet<>();

        for (File file : existing) {
            if (file != null) {
                knownPaths.add(normalizeFileIdentity(file));
            }
        }

        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }

            String identity = normalizeFileIdentity(file);

            if (knownPaths.add(identity)) {
                existing.add(file);
                result.resolvedFiles++;
            }
        }
    }

    private String normalizeFileIdentity(File file) {
        try {
            return file.toPath()
                    .toRealPath()
                    .toString()
                    .toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            return file.getAbsolutePath()
                    .toLowerCase(Locale.ROOT);
        }
    }

    private boolean containsResolvedKey(
            ResolvedAsset resolved,
            String key
    ) {
        return resolved.resolvedFiles.containsKey(key);
    }

    private String resolveTextureAlias(
            String reference,
            Map<String, String> textures,
            Set<String> visited
    ) {
        if (reference == null) {
            return null;
        }

        String current = reference.trim();

        if (!current.startsWith("#")) {
            return current;
        }

        String key = current.substring(1).trim();

        if (key.isBlank()
                || !visited.add(key)
                || !textures.containsKey(key)) {
            return null;
        }

        return resolveTextureAlias(
                textures.get(key),
                textures,
                visited
        );
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

    private boolean isModelProperty(String property) {
        return property.equals("model")
                || property.endsWith(".model")
                || property.contains("model_path")
                || property.contains("model_file")
                || property.contains("item_model");
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
            String reference = part.trim().replace('\\', '/');

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
            referenceNamespace = normalized.substring(0, colon);
            normalized = normalized.substring(colon + 1);
        }

        String extension = expectedExtension(property);

        List<String> candidates = buildCandidates(
                normalized,
                extension,
                property
        );

        String preferredNamespace = referenceNamespace != null
                ? referenceNamespace
                : namespace;

        for (String candidate : candidates) {
            List<File> matches = index.findExact(
                    candidate,
                    preferredNamespace
            );

            if (!matches.isEmpty()) {
                return matches;
            }
        }

        for (String candidate : candidates) {
            List<File> matches = index.findSuffix(
                    candidate,
                    preferredNamespace
            );

            if (!matches.isEmpty()) {
                return matches;
            }
        }

        for (String candidate : candidates) {
            String basename = getBasename(candidate);

            if (basename.isBlank()) {
                continue;
            }

            List<File> matches = index.findNamespaceBasename(
                    basename,
                    preferredNamespace
            );

            if (matches.size() == 1) {
                return matches;
            }
        }

        for (String candidate : candidates) {
            List<File> matches =
                    index.findGlobalSuffix(candidate);

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

        String relative = path;

        if (relative.startsWith("resourcepack/assets/")) {
            relative = relative.substring(
                    "resourcepack/assets/".length()
            );
        } else if (relative.startsWith("assets/")) {
            relative = relative.substring("assets/".length());
        }

        if (property.contains("texture") || property.equals("icon")) {
            addTextureCandidates(candidates, relative, extension);
        } else {
            addWithExtension(candidates, relative, extension);

            if (property.contains("model")
                    || property.contains("geometry")
                    || property.contains("animation")) {

                addWithExtension(
                        candidates,
                        "models/" + relative,
                        extension
                );

                addWithExtension(
                        candidates,
                        "models/item/" + relative,
                        extension
                );
            }
        }

        return new ArrayList<>(candidates);
    }

    private void addTextureCandidates(
            Set<String> candidates,
            String path,
            String extension
    ) {
        String normalized = normalizeReference(path);

        addWithExtension(candidates, normalized, extension);

        if (normalized.startsWith("textures/")) {
            String remainder =
                    normalized.substring("textures/".length());

            addWithExtension(candidates, remainder, extension);
            addWithExtension(
                    candidates,
                    "textures/" + remainder,
                    extension
            );
            return;
        }

        if (normalized.startsWith("item/")) {
            String remainder =
                    normalized.substring("item/".length());

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
            return;
        }

        if (normalized.startsWith("items/")) {
            String remainder =
                    normalized.substring("items/".length());

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
            return;
        }

        addWithExtension(
                candidates,
                "textures/" + normalized,
                extension
        );

        addWithExtension(
                candidates,
                "textures/item/" + normalized,
                extension
        );

        addWithExtension(
                candidates,
                "textures/items/" + normalized,
                extension
        );
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
        if (property.contains("texture") || property.equals("icon")) {
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

    private String getBasename(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0
                ? path.substring(slash + 1)
                : path;
    }

    private boolean hasExtension(String value) {
        int slash = value.lastIndexOf('/');
        int dot = value.lastIndexOf('.');
        return dot > slash;
    }

    private static final class FileIndex {

        private final Map<String, List<File>> byPath =
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

            for (String namespace : extractNamespaces(path)) {
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
                String namespace
        ) {
            String normalized = normalize(candidate);
            String preferred = namespace.toLowerCase(Locale.ROOT);

            List<String> paths = List.of(
                    preferred + "/" + normalized,
                    "assets/" + preferred + "/" + normalized,
                    normalized
            );

            for (String path : paths) {
                List<File> matches = byPath.get(path);

                if (matches != null && !matches.isEmpty()) {
                    return unique(matches);
                }
            }

            return List.of();
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
                        || path.equals(
                                "assets/" + preferred + "/" + normalized
                        )
                        || path.endsWith("/" + normalized)) {

                    matches.addAll(entry.getValue());
                }
            }

            return unique(matches);
        }

        List<File> findNamespaceBasename(
                String basename,
                String namespace
        ) {
            String key = namespace.toLowerCase(Locale.ROOT)
                    + ":"
                    + basename.toLowerCase(Locale.ROOT);

            List<File> matches = byNamespaceBasename.get(key);

            return matches == null
                    ? List.of()
                    : unique(matches);
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
                    || path.startsWith("assets/" + namespace + "/")
                    || path.contains("/assets/" + namespace + "/");
        }

        private List<File> unique(List<File> files) {
            return new ArrayList<>(new LinkedHashSet<>(files));
        }

        private String normalize(String value) {
            return value.replace('\\', '/')
                    .toLowerCase(Locale.ROOT);
        }
    }
}
