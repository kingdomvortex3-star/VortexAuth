
package id.vortexconverter.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ItemsAdderBedrockGeometryConverter {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private static final double COORDINATE_OFFSET = 8.0;

    public ConversionResult convert(
            ItemsAdderModelParser.ModelData model,
            String geometryIdentifier,
            int textureWidth,
            int textureHeight
    ) {

        List<String> warnings = new ArrayList<>();

        if (model == null) {
            return ConversionResult.failure(
                    "Data model tidak tersedia."
            );
        }

        if (geometryIdentifier == null
                || geometryIdentifier.isBlank()) {
            return ConversionResult.failure(
                    "Geometry identifier tidak boleh kosong."
            );
        }

        if (textureWidth <= 0 || textureHeight <= 0) {
            return ConversionResult.failure(
                    "Ukuran texture harus lebih besar dari nol."
            );
        }

        if (!model.hasElements()) {
            return ConversionResult.failure(
                    "Model tidak memiliki elements."
            );
        }

        JsonObject geometry = new JsonObject();
        geometry.addProperty("format_version", "1.12.0");

        JsonObject geometryObject = new JsonObject();
        JsonObject description = new JsonObject();

        description.addProperty(
                "identifier",
                normalizeIdentifier(geometryIdentifier)
        );

        description.addProperty("texture_width", textureWidth);
        description.addProperty("texture_height", textureHeight);
        description.addProperty("visible_bounds_width", 4.0);
        description.addProperty("visible_bounds_height", 4.0);

        JsonArray visibleBoundsOffset = new JsonArray();
        visibleBoundsOffset.add(0.0);
        visibleBoundsOffset.add(1.0);
        visibleBoundsOffset.add(0.0);

        description.add(
                "visible_bounds_offset",
                visibleBoundsOffset
        );

        geometryObject.add("description", description);

        JsonArray bones = new JsonArray();

        int convertedElements = 0;
        int convertedFaces = 0;
        int skippedFaces = 0;

        for (ItemsAdderModelParser.ElementData element
                : model.elements()) {

            if (element.from().size() != 3
                    || element.to().size() != 3) {
                warnings.add(
                        "Element " + element.index()
                                + " dilewati karena koordinat tidak valid."
                );
                continue;
            }

            double fromX = element.from().get(0);
            double fromY = element.from().get(1);
            double fromZ = element.from().get(2);

            double toX = element.to().get(0);
            double toY = element.to().get(1);
            double toZ = element.to().get(2);

            double sizeX = toX - fromX;
            double sizeY = toY - fromY;
            double sizeZ = toZ - fromZ;

            if (!isFinite(fromX, fromY, fromZ,
                    toX, toY, toZ)
                    || sizeX <= 0
                    || sizeY <= 0
                    || sizeZ <= 0) {

                warnings.add(
                        "Element " + element.index()
                                + " dilewati karena ukuran cube tidak valid."
                );
                continue;
            }

            JsonObject cube = new JsonObject();

            JsonArray origin = vector(
                    fromX - COORDINATE_OFFSET,
                    COORDINATE_OFFSET - toY,
                    fromZ - COORDINATE_OFFSET
            );

            JsonArray size = vector(
                    sizeX,
                    sizeY,
                    sizeZ
            );

            cube.add("origin", origin);
            cube.add("size", size);

            JsonObject faceUv = new JsonObject();

            for (ItemsAdderModelParser.FaceData face
                    : element.faces()) {

                if (!face.hasExplicitUv()) {
                    skippedFaces++;
                    continue;
                }

                double u1 = face.getU1();
                double v1 = face.getV1();
                double u2 = face.getU2();
                double v2 = face.getV2();

                if (!isFinite(u1, v1, u2, v2)) {
                    skippedFaces++;
                    continue;
                }

                JsonObject uv = new JsonObject();

                uv.add(
                        "uv",
                        vector(u1, v1)
                );

                uv.add(
                        "uv_size",
                        vector(u2 - u1, v2 - v1)
                );

                faceUv.add(
                        normalizeDirection(face.direction()),
                        uv
                );

                convertedFaces++;
            }

            if (faceUv.isEmpty()) {
                warnings.add(
                        "Element " + element.index()
                                + " tidak memiliki face dengan UV eksplisit."
                );
                continue;
            }

            cube.add("uv", faceUv);

            JsonObject bone = new JsonObject();

            bone.addProperty(
                    "name",
                    "vortex_element_" + element.index()
            );

            bone.addProperty("neverRender", false);

            JsonArray pivot = vector(
                    fromX - COORDINATE_OFFSET,
                    COORDINATE_OFFSET - fromY,
                    fromZ - COORDINATE_OFFSET
            );

            bone.add("pivot", pivot);

            ItemsAdderModelParser.ElementRotation rotation =
                    element.rotation();

            if (rotation != null) {
                JsonArray rotationArray = convertRotation(rotation);

                if (rotationArray != null) {
                    bone.add("rotation", rotationArray);
                } else {
                    warnings.add(
                            "Rotasi element " + element.index()
                                    + " tidak dapat dikonversi."
                    );
                }
            }

            JsonArray cubes = new JsonArray();
            cubes.add(cube);

            bone.add("cubes", cubes);
            bones.add(bone);

            convertedElements++;
        }

        if (convertedElements == 0) {
            return new ConversionResult(
                    false,
                    null,
                    0,
                    0,
                    skippedFaces,
                    warnings,
                    "Tidak ada element yang berhasil dikonversi."
            );
        }

        geometryObject.add("bones", bones);

        JsonArray geometries = new JsonArray();
        geometries.add(geometryObject);

        geometry.add("minecraft:geometry", geometries);

        if (skippedFaces > 0) {
            warnings.add(
                    skippedFaces
                            + " face dilewati karena UV tidak tersedia "
                            + "atau tidak valid."
            );
        }

        warnings.add(
                "Rotasi face UV belum diterapkan secara terpisah."
        );

        warnings.add(
                "Konversi ini merupakan tahap awal geometry Bedrock. "
                        + "Hasil tetap perlu diuji di client Bedrock."
        );

        return new ConversionResult(
                true,
                geometry,
                convertedElements,
                convertedFaces,
                skippedFaces,
                warnings,
                null
        );
    }

    public void write(
            ConversionResult result,
            File destination
    ) throws IOException {

        if (result == null
                || !result.successful()
                || result.geometry() == null) {
            throw new IOException(
                    result == null
                            ? "Hasil konversi tidak tersedia."
                            : result.error() == null
                            ? "Geometry tidak berhasil dibuat."
                            : result.error()
            );
        }

        if (destination == null) {
            throw new IOException(
                    "Lokasi file geometry tidak tersedia."
            );
        }

        File parent = destination.getParentFile();

        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        Files.writeString(
                destination.toPath(),
                GSON.toJson(result.geometry()),
                StandardCharsets.UTF_8
        );
    }

    private JsonArray convertRotation(
            ItemsAdderModelParser.ElementRotation rotation
    ) {
        if (rotation.origin().size() != 3) {
            return null;
        }

        double angle = rotation.angle();

        if (!Double.isFinite(angle)) {
            return null;
        }

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        switch (rotation.axis().toLowerCase(Locale.ROOT)) {
            case "x" -> x = angle;
            case "y" -> y = -angle;
            case "z" -> z = -angle;
            default -> {
                return null;
            }
        }

        return vector(x, y, z);
    }

    private String normalizeDirection(String direction) {
        if (direction == null) {
            return "north";
        }

        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "north" -> "north";
            case "south" -> "south";
            case "east" -> "east";
            case "west" -> "west";
            case "up" -> "up";
            case "down" -> "down";
            default -> "north";
        };
    }

    private String normalizeIdentifier(String identifier) {
        String normalized = identifier.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.:-]", "_");

        if (!normalized.startsWith("geometry.")) {
            normalized = "geometry." + normalized;
        }

        return normalized;
    }

    private JsonArray vector(double... values) {
        JsonArray array = new JsonArray();

        for (double value : values) {
            array.add(value);
        }

        return array;
    }

    private boolean isFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }

        return true;
    }

    public record ConversionResult(
            boolean successful,
            JsonObject geometry,
            int convertedElements,
            int convertedFaces,
            int skippedFaces,
            List<String> warnings,
            String error
    ) {

        public ConversionResult {
            warnings = warnings == null
                    ? List.of()
                    : List.copyOf(warnings);
        }

        public static ConversionResult failure(String error) {
            return new ConversionResult(
                    false,
                    null,
                    0,
                    0,
                    0,
                    List.of(),
                    error
            );
        }
    }
}
