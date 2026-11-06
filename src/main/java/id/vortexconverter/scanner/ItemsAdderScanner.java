
package id.vortexconverter.scanner;

import id.vortexconverter.VortexConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public final class ItemsAdderScanner {

    private final VortexConverter plugin;
    private final File itemsAdderFolder;
    private final File contentsFolder;

    public ItemsAdderScanner(VortexConverter plugin) {
        this.plugin = plugin;

        File pluginsFolder = plugin.getDataFolder().getParentFile();

        this.itemsAdderFolder = new File(pluginsFolder, "ItemsAdder");
        this.contentsFolder = new File(itemsAdderFolder, "contents");
    }

    public ScanResult scan() {
        ScanResult result = new ScanResult();

        if (!itemsAdderFolder.exists()) {
            result.error = "ItemsAdder folder not found.";
            return result;
        }

        if (!contentsFolder.exists() || !contentsFolder.isDirectory()) {
            result.error = "ItemsAdder contents folder not found.";
            return result;
        }

        result.pluginInstalled = true;
        result.contentsFound = true;

        try (var paths = Files.walk(contentsFolder.toPath())) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                processFile(path, result);
            });

        } catch (IOException exception) {
            result.error = "Failed to scan ItemsAdder: "
                    + exception.getMessage();
        }

        result.namespaceCount = result.namespaces.size();
        result.scanSuccessful = result.error == null;

        plugin.getLogger().info(
                "ItemsAdder scan completed: "
                        + result.totalFiles + " files, "
                        + result.namespaceCount + " namespaces."
        );

        return result;
    }

    private void processFile(Path path, ScanResult result) {
        result.totalFiles++;

        File file = path.toFile();
        result.totalSize += file.length();

        Path relative = contentsFolder.toPath().relativize(path);

        if (relative.getNameCount() > 1) {
            result.namespaces.add(
                    relative.getName(0).toString()
            );
        }

        String filename = file.getName().toLowerCase();
        String normalizedPath = path.toString()
                .replace('\\', '/')
                .toLowerCase();

        if (filename.endsWith(".png")) {
            result.textures++;
        }

        if (filename.endsWith(".bbmodel")) {
            result.bbmodels++;
        }

        if (filename.endsWith(".json")) {
            result.jsonFiles++;

            if (normalizedPath.contains("/models/")) {
                result.models++;
            }
        }

        if (filename.endsWith(".yml")
                || filename.endsWith(".yaml")) {
            result.configurations++;
        }

        if (filename.endsWith(".png")
                || filename.endsWith(".bbmodel")
                || filename.endsWith(".json")
                || filename.endsWith(".yml")
                || filename.endsWith(".yaml")) {
            return;
        }

        result.otherFiles++;
    }

    public static final class ScanResult {

        private boolean pluginInstalled;
        private boolean contentsFound;
        private boolean scanSuccessful;

        private String error;

        private int totalFiles;
        private int namespaceCount;

        private int configurations;
        private int textures;
        private int models;
        private int bbmodels;
        private int jsonFiles;
        private int otherFiles;

        private long totalSize;

        private final Set<String> namespaces = new TreeSet<>();

        public boolean isPluginInstalled() {
            return pluginInstalled;
        }

        public boolean isContentsFound() {
            return contentsFound;
        }

        public boolean isScanSuccessful() {
            return scanSuccessful;
        }

        public String getError() {
            return error;
        }

        public int getTotalFiles() {
            return totalFiles;
        }

        public int getNamespaceCount() {
            return namespaceCount;
        }

        public int getConfigurations() {
            return configurations;
        }

        public int getTextures() {
            return textures;
        }

        public int getModels() {
            return models;
        }

        public int getBbmodels() {
            return bbmodels;
        }

        public int getJsonFiles() {
            return jsonFiles;
        }

        public int getOtherFiles() {
            return otherFiles;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public Set<String> getNamespaces() {
            return Collections.unmodifiableSet(namespaces);
        }

        public String getFormattedSize() {
            double mb = totalSize / (1024.0 * 1024.0);
            return String.format("%.2f MB", mb);
        }
    }
}
