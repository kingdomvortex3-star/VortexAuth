
package id.vortexconverter.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class BlockbenchGeometryGenerator {

    public JsonObject generate(
            BlockbenchModelReader.BlockbenchModel model,
            String identifier,
            int textureWidth,
            int textureHeight
    ) {
        if (model == null) {
            throw new IllegalArgumentException("Blockbench model cannot be null");
        }

        if (identifier == null || identifier.isBlank()) {
            identifier = "geometry.vortex.model";
        }

        if (textureWidth <= 0) textureWidth = 64;
        if (textureHeight <= 0) textureHeight = 64;

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.12.0");

        JsonObject geometry = new JsonObject();
        JsonObject description = new JsonObject();

        description.addProperty("identifier", identifier);
        description.addProperty("texture_width", textureWidth);
        description.addProperty("texture_height", textureHeight);
        description.addProperty("visible_bounds_width", 4.0);
        description.addProperty("visible_bounds_height", 4.0);
        description.addProperty("visible_bounds_offset", 1.0);

        geometry.add("description", description);

        JsonArray bones = new JsonArray();
        Map<String, String> boneNames = new HashMap<>();

        if (model.bones != null) {
            for (BlockbenchModelReader.Bone bone : model.bones) {
                if (bone != null && bone.uuid != null) {
                    boneNames.put(bone.uuid, sanitizeBoneName(bone.name));
                }
            }

            for (BlockbenchModelReader.Bone bone : model.bones) {
                if (bone != null) {
                    bones.add(createBone(bone, boneNames));
                }
            }
        }

        geometry.add("bones", bones);

        JsonArray geometries = new JsonArray();
        geometries.add(geometry);
        root.add("minecraft:geometry", geometries);

        return root;
    }

    private JsonObject createBone(
            BlockbenchModelReader.Bone bone,
            Map<String, String> boneNames
    ) {
        JsonObject result = new JsonObject();

        String name = sanitizeBoneName(bone.name);
        result.addProperty("name", name);

        if (bone.parent != null && !bone.parent.isBlank()) {
            String parentName = boneNames.get(bone.parent);

            if (parentName != null) {
                result.addProperty("parent", parentName);
            }
        }

        result.add("pivot", vector(bone.pivot));

        if (bone.rotation != null) {
            result.add("rotation", vector(bone.rotation));
        }

        JsonArray cubes = new JsonArray();

        if (bone.cubes != null) {
            for (BlockbenchModelReader.Cube cube : bone.cubes) {
                if (cube != null) {
                    JsonObject cubeJson = createCube(cube);
                    if (cubeJson != null) {
                        cubes.add(cubeJson);
                    }
                }
            }
        }

        if (!cubes.isEmpty()) {
            result.add("cubes", cubes);
        }

        return result;
    }

    private JsonObject createCube(BlockbenchModelReader.Cube cube) {
        if (cube.from == null || cube.to == null) {
            return null;
        }

        double x = cube.from.x;
        double y = cube.from.y;
        double z = cube.from.z;

        double sizeX = cube.to.x - cube.from.x;
        double sizeY = cube.to.y - cube.from.y;
        double sizeZ = cube.to.z - cube.from.z;

        if (sizeX < 0 || sizeY < 0 || sizeZ < 0) {
            return null;
        }

        JsonObject result = new JsonObject();

        JsonArray origin = new JsonArray();
        origin.add(x);
        origin.add(y);
        origin.add(z);

        JsonArray size = new JsonArray();
        size.add(sizeX);
        size.add(sizeY);
        size.add(sizeZ);

        result.add("origin", origin);
        result.add("size", size);

        if (cube.origin != null) {
            result.add("pivot", vector(cube.origin));
        }

        if (cube.rotation != null) {
            result.add("rotation", vector(cube.rotation));
        }

        return result;
    }

    private JsonArray vector(BlockbenchModelReader.Vector3 vector) {
        JsonArray array = new JsonArray();

        if (vector == null) {
            array.add(0);
            array.add(0);
            array.add(0);
            return array;
        }

        array.add(vector.x);
        array.add(vector.y);
        array.add(vector.z);

        return array;
    }

    private String sanitizeBoneName(String name) {
        if (name == null || name.isBlank()) {
            return "bone";
        }

        String sanitized = name
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "_");

        if (sanitized.isBlank()) {
            return "bone";
        }

        if (Character.isDigit(sanitized.charAt(0))) {
            sanitized = "bone_" + sanitized;
        }

        return sanitized;
    }
}
