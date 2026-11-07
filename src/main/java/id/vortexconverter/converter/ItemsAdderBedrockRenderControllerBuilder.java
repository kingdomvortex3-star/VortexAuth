
package id.vortexconverter.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ItemsAdderBedrockRenderControllerBuilder {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    public static final String CONTROLLER_IDENTIFIER =
            "controller.render.default";

    public static final String GEOMETRY_REFERENCE =
            "Geometry.default";

    public static final String MATERIAL_REFERENCE =
            "Material.default";

    public static final String TEXTURE_REFERENCE =
            "Texture.default";

    public JsonObject build() {
        JsonObject root = new JsonObject();

        root.addProperty("format_version", "1.8.0");

        JsonObject controllers = new JsonObject();
        JsonObject controller = new JsonObject();

        controller.addProperty(
                "geometry",
                GEOMETRY_REFERENCE
        );

        JsonArray materials = new JsonArray();
        JsonObject materialEntry = new JsonObject();

        materialEntry.addProperty(
                "*",
                MATERIAL_REFERENCE
        );

        materials.add(materialEntry);

        controller.add("materials", materials);

        JsonArray textures = new JsonArray();
        textures.add(TEXTURE_REFERENCE);

        controller.add("textures", textures);

        controllers.add(
                CONTROLLER_IDENTIFIER,
                controller
        );

        root.add("render_controllers", controllers);

        return root;
    }

    public File write(File resourcePackDirectory)
            throws IOException {

        if (resourcePackDirectory == null) {
            throw new IOException(
                    "Folder resource pack tidak tersedia."
            );
        }

        Files.createDirectories(
                resourcePackDirectory.toPath()
        );

        File directory = new File(
                resourcePackDirectory,
                "render_controllers"
        );

        Files.createDirectories(directory.toPath());

        File destination = new File(
                directory,
                "vortexconverter.render_controllers.json"
        );

        Files.writeString(
                destination.toPath(),
                GSON.toJson(build()),
                StandardCharsets.UTF_8
        );

        return destination;
    }
}
