
package id.vortexconverter.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BlockbenchConversionService {

    private final BlockbenchModelReader modelReader;
    private final BlockbenchTextureMapper textureMapper;
    private final BlockbenchGeometryGenerator geometryGenerator;
    private final BedrockAnimationGenerator animationGenerator;
    private final BlockbenchTextureCopyService textureCopyService;

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public BlockbenchConversionService() {
        this.modelReader = new BlockbenchModelReader();
        this.textureMapper = new BlockbenchTextureMapper();
        this.geometryGenerator = new BlockbenchGeometryGenerator();
        this.animationGenerator = new BedrockAnimationGenerator();
        this.textureCopyService = new BlockbenchTextureCopyService();
    }

    public ConversionResult convert(
            File inputFile,
            File outputDirectory,
            String geometryIdentifier
    ) throws IOException {

        if (inputFile == null || !inputFile.isFile()) {
            throw new IOException(
                    "Input BBModel file does not exist."
            );
        }

        if (!inputFile.getName().toLowerCase(Locale.ROOT)
                .endsWith(".bbmodel")) {
            throw new IOException(
                    "Input file must be a .bbmodel file."
            );
        }

        if (outputDirectory == null) {
            throw new IOException(
                    "Output directory cannot be null."
            );
        }

        Path outputPath = outputDirectory.toPath();

        Files.createDirectories(outputPath);

        BlockbenchModelReader.BlockbenchModel model =
                modelReader.read(inputFile);

        if (model.bones() == null || model.bones().isEmpty()) {
            throw new IOException(
                    "No bones found in Blockbench model."
            );
        }

        var uvMappings = textureMapper.read(inputFile);

        if (geometryIdentifier == null
                || geometryIdentifier.isBlank()) {
            geometryIdentifier = createIdentifier(model.name());
        }

        JsonObject geometry = geometryGenerator.generate(
                model,
                geometryIdentifier,
                model.textureWidth(),
                model.textureHeight(),
                uvMappings
        );

        Path geometryFile = outputPath.resolve(
                "geometry.geo.json"
        );

        writeJson(geometryFile, geometry);

        Path animationDirectory = outputPath.resolve("animations");

        Files.createDirectories(animationDirectory);

        List<Path> animationFiles = new ArrayList<>();

        if (model.animations() != null) {
            for (BlockbenchModelReader.Animation animation
                    : model.animations()) {

                if (animation == null) {
                    continue;
                }

                JsonObject animationJson =
                        animationGenerator.generate(
                                animation,
                                model.bones()
                        );

                String animationFileName =
                        sanitizeFileName(animation.name())
                                + ".animation.json";

                Path animationFile = animationDirectory.resolve(
                        animationFileName
                );

                writeJson(animationFile, animationJson);

                animationFiles.add(animationFile);
            }
        }

        File sourceDirectory = inputFile.getAbsoluteFile()
                .getParentFile();

        BlockbenchTextureCopyService.CopyResult textureResult =
                textureCopyService.copyTextures(
                        inputFile,
                        sourceDirectory,
                        outputDirectory
                );

        List<File> copiedTextureFiles = new ArrayList<>();

        Path textureDirectory = outputPath
                .resolve("textures")
                .resolve("entity");

        for (String fileName : textureResult.copiedFiles()) {
            copiedTextureFiles.add(
                    textureDirectory.resolve(fileName).toFile()
            );
        }

        return new ConversionResult(
                inputFile,
                geometryFile.toFile(),
                animationFiles.stream()
                        .map(Path::toFile)
                        .toList(),
                model.bones().size(),
                uvMappings.size(),
                animationFiles.size(),
                copiedTextureFiles,
                textureResult.copiedCount(),
                textureResult.missingCount()
        );
    }

    private void writeJson(
            Path output,
            JsonObject json
    ) throws IOException {

        Path parent = output.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (var writer = Files.newBufferedWriter(
                output,
                StandardCharsets.UTF_8
        )) {
            gson.toJson(json, writer);
        }
    }

    private String createIdentifier(String modelName) {
        return "geometry.vortex." + sanitizeFileName(modelName);
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }

        String sanitized = name
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");

        return sanitized.isBlank() ? "unnamed" : sanitized;
    }

    public record ConversionResult(
            File inputFile,
            File geometryFile,
            List<File> animationFiles,
            int boneCount,
            int uvCubeCount,
            int animationCount,
            List<File> textureFiles,
            int copiedTextureCount,
            int missingTextureCount
    ) {}
}
