
package id.vortexconverter.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class BedrockGeometryGenerator {

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public JsonObject generate(
            JavaModelReader.JavaModel model,
            String identifier,
            int textureWidth,
            int textureHeight
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.12.0");

        JsonArray geometries = new JsonArray();
        JsonObject geometry = new JsonObject();

        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.addProperty("texture_width", textureWidth);
        description.addProperty("texture_height", textureHeight);
        description.addProperty("visible_bounds_width", 4.0);
        description.addProperty("visible_bounds_height", 4.0);

        JsonArray offset = vector(0, 1, 0);
        description.add("visible_bounds_offset", offset);

        geometry.add("description", description);

        JsonArray bones = new JsonArray();
        JsonObject bone = new JsonObject();

        bone.addProperty("name", "root");
        bone.add("pivot", vector(0, 0, 0));

        JsonArray cubes = new JsonArray();

        for (JavaModelReader.Element element : model.elements()) {
            cubes.add(createCube(element));
        }

        bone.add("cubes", cubes);
        bones.add(bone);

        geometry.add("bones", bones);
        geometries.add(geometry);

        root.add("minecraft:geometry", geometries);

        return root;
    }

    private JsonObject createCube(
            JavaModelReader.Element element
    ) {
        JsonObject cube = new JsonObject();

        JavaModelReader.Vector3 from = element.from();
        JavaModelReader.Vector3 to = element.to();

        double x = from.x() - 8.0;
        double y = 16.0 - to.y();
        double z = from.z() - 8.0;

        cube.add("origin", vector(x, y, z));

        cube.add("size", vector(
                to.x() - from.x(),
                to.y() - from.y(),
                to.z() - from.z()
        ));

        JavaModelReader.Vector3 rotation = element.rotation();

        if (rotation != null
                && (rotation.x() != 0
                || rotation.y() != 0
                || rotation.z() != 0)) {

            cube.add("rotation", vector(
                    rotation.x(),
                    rotation.y(),
                    rotation.z()
            ));

            JavaModelReader.Vector3 pivot = element.origin();

            cube.add("pivot", vector(
                    pivot.x() - 8.0,
                    16.0 - pivot.y(),
                    pivot.z() - 8.0
            ));
        }

        JsonObject uv = new JsonObject();

        for (JavaModelReader.Face face : element.faces()) {
            if (face.uv() == null) {
                continue;
            }

            JsonObject faceUv = new JsonObject();

            double[] coordinates = face.uv();

            faceUv.add("uv", vector(
                    coordinates[0],
                    coordinates[1]
            ));

            faceUv.add("uv_size", vector(
                    coordinates[2] - coordinates[0],
                    coordinates[3] - coordinates[1]
            ));

            uv.add(mapDirection(face.direction()), faceUv);
        }

        if (!uv.isEmpty()) {
            cube.add("uv", uv);
        }

        return cube;
    }

    private String mapDirection(String direction) {
        return switch (direction.toLowerCase()) {
            case "up" -> "up";
            case "down" -> "down";
            case "north" -> "north";
            case "south" -> "south";
            case "east" -> "east";
            case "west" -> "west";
            default -> direction.toLowerCase();
        };
    }

    private JsonArray vector(double x, double y, double z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }

    private JsonArray vector(double x, double y) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        return array;
    }

    public void save(
            JavaModelReader.JavaModel model,
            String identifier,
            int textureWidth,
            int textureHeight,
            File output
    ) throws IOException {

        JsonObject geometry = generate(
                model,
                identifier,
                textureWidth,
                textureHeight
        );

        File parent = output.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException(
                    "Cannot create geometry output directory."
            );
        }

        try (FileWriter writer = new FileWriter(output)) {
            gson.toJson(geometry, writer);
        }
    }
}
