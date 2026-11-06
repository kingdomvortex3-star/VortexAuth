
package id.vortexconverter.converter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class BlockbenchPackPipeline {

    private final BlockbenchConversionService conversionService;
    private final BedrockResourcePackBuilder resourcePackBuilder;
    private final BedrockPackValidator packValidator;
    private final BedrockPackArchiver packArchiver;

    public BlockbenchPackPipeline() {
        this.conversionService = new BlockbenchConversionService();
        this.resourcePackBuilder = new BedrockResourcePackBuilder();
        this.packValidator = new BedrockPackValidator();
        this.packArchiver = new BedrockPackArchiver();
    }

    public PipelineResult convert(
            File inputFile,
            File outputDirectory,
            String packName,
            String geometryIdentifier
    ) throws IOException {

        Objects.requireNonNull(inputFile, "Input file cannot be null");
        Objects.requireNonNull(outputDirectory, "Output directory cannot be null");

        if (!inputFile.exists() || !inputFile.isFile()) {
            throw new IOException("BBModel file does not exist: " + inputFile);
        }

        if (!inputFile.getName().toLowerCase().endsWith(".bbmodel")) {
            throw new IOException("Input file must be a .bbmodel file");
        }

        if (packName == null || packName.isBlank()) {
            throw new IOException("Pack name cannot be empty");
        }

        if (geometryIdentifier == null || geometryIdentifier.isBlank()) {
            throw new IOException("Geometry identifier cannot be empty");
        }

        String safePackName = sanitize(packName);

        Path outputPath = outputDirectory.toPath()
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(outputPath);

        Path workDirectory = outputPath
                .resolve(".vortex-work")
                .resolve(safePackName);

        Path packOutputDirectory = outputPath.resolve("packs");

        Files.createDirectories(workDirectory);
        Files.createDirectories(packOutputDirectory);

        BlockbenchConversionService.ConversionResult conversion =
                conversionService.convert(
                        inputFile,
                        workDirectory.toFile(),
                        geometryIdentifier
                );

        BedrockResourcePackBuilder.BuildResult build =
                resourcePackBuilder.build(
                        conversion,
                        packOutputDirectory.toFile(),
                        safePackName
                );

        BedrockPackValidator.ValidationResult validation =
                packValidator.validate(build.packDirectory());

        if (!validation.valid()) {
            throw new ValidationException(
                    "Resource pack validation failed.",
                    validation.errors(),
                    validation.warnings()
            );
        }

        Path mcpackFile = packArchiver.createMcpack(
                build.packDirectory().toPath(),
                outputPath,
                safePackName
        );

        return new PipelineResult(
                inputFile,
                build.packDirectory(),
                mcpackFile.toFile(),
                conversion.boneCount(),
                conversion.animationCount(),
                conversion.uvCubeCount(),
                conversion.copiedTextureCount(),
                conversion.missingTextureCount(),
                validation.warnings()
        );
    }

    private String sanitize(String name) {
        String sanitized = name
                .trim()
                .replaceAll("[^a-zA-Z0-9._-]", "_");

        if (sanitized.isBlank()) {
            return "VortexResourcePack";
        }

        return sanitized;
    }

    public record PipelineResult(
            File inputFile,
            File packDirectory,
            File mcpackFile,
            int boneCount,
            int animationCount,
            int uvCubeCount,
            int copiedTextureCount,
            int missingTextureCount,
            List<String> warnings
    ) {
        public PipelineResult {
            warnings = List.copyOf(warnings);
        }
    }

    public static class ValidationException extends IOException {

        private final List<String> errors;
        private final List<String> warnings;

        public ValidationException(
                String message,
                List<String> errors,
                List<String> warnings
        ) {
            super(message);
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
