
package id.vortexconverter.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            return Collections.unmodifiableMap(resolvedFiles);
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

        Map<String, List<File>> fileIndex = buildFileIndex(
                contentsFolder,
                result
        );

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
                            fileIndex,
                            key
                    );

                    if (matches.isEmpty()) {
                        resolved.missingReferences.add(
                                entry.getKey() + " = " + reference
                        );
                    } else {
                        resolved.resolvedFiles.put(
                                entry.getKey() + ":" + reference,
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

    private Map<String, List<File>> buildFileIndex(
            File root,
            ResolutionResult result
    ) {
        Map<String, List<File>> index = new LinkedHashMap<>();

        try (Stream<Path> paths = Files.walk(root.toPath())) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                File file = path.toFile();

                String relative = root.toPath()
                        .relativize(path)
                        .toString()
                        .replace('\\', '/')
                        .toLowerCase(Locale.ROOT);

                String filename = file.getName()
                        .toLowerCase(Locale.ROOT);

                index.computeIfAbsent(relative, ignored ->
                        new ArrayList<>()).add(file);

                index.computeIfAbsent(filename, ignored ->
                        new ArrayList<>()).add(file);

                result.scannedFiles++;
            });
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to index ItemsAdder assets: "
                            + exception.getMessage(),
                    exception
            );
        }

        return index;
    }

    private boolean isAssetReference(String key) {
        return key.contains("texture")
                || key.contains("model")
                || key.endsWith("path")
                || key.contains("item_model");
    }

    private List<String> splitReferences(String value) {
        List<String> references = new ArrayList<>();

        if (value == null || value.isBlank()) {
            return references;
        }

        for (String part : value.split(",")) {
            String reference = part.trim()
                    .replace("\\", "/")
                    .replace("\"", "")
                    .replace("'", "");

            if (!reference.isBlank()) {
                references.add(reference);
            }
        }

        return references;
    }

    private List<File> findFiles(
            String reference,
            Map<String, List<File>> index,
            String property
    ) {
        String normalized = reference
                .replace("\\", "/")
                .replaceFirst("^/+", "")
                .toLowerCase(Locale.ROOT);

        List<String> candidates = new ArrayList<>();
        candidates.add(normalized);

        boolean hasExtension = normalized.matches(".*\\.[a-z0-9]+$");

        if (!hasExtension) {
            if (property.contains("texture")) {
                candidates.add(normalized + ".png");
            } else if (property.contains("model")) {
                candidates.add(normalized + ".json");
                candidates.add(normalized + ".bbmodel");
            } else {
                candidates.add(normalized + ".png");
                candidates.add(normalized + ".json");
                candidates.add(normalized + ".bbmodel");
            }
        }

        for (String candidate : candidates) {
            List<File> exact = index.get(candidate);

            if (exact != null && !exact.isEmpty()) {
                return deduplicate(exact);
            }
        }

        for (String candidate : candidates) {
            List<File> matches = new ArrayList<>();

            for (Map.Entry<String, List<File>> entry : index.entrySet()) {
                String indexedPath = entry.getKey();

                if (indexedPath.endsWith("/" + candidate)) {
                    matches.addAll(entry.getValue());
                }
            }

            if (!matches.isEmpty()) {
                return deduplicate(matches);
            }
        }

        return List.of();
    }

    private List<File> deduplicate(List<File> files) {
        return new ArrayList<>(
                files.stream().distinct().toList()
        );
    }
}
