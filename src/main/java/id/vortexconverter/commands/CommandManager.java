
package id.vortexconverter.commands;

import id.vortexconverter.VortexConverter;
import id.vortexconverter.scanner.ItemsAdderScanner;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CommandManager implements CommandExecutor, TabCompleter {

    private final VortexConverter plugin;
    private final ItemsAdderScanner itemsAdderScanner;

    public CommandManager(VortexConverter plugin) {
        this.plugin = plugin;
        this.itemsAdderScanner = new ItemsAdderScanner(plugin);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("vortexconverter.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "scan" -> scanPlugins(sender);
            case "itemsadder" -> scanItemsAdder(sender);
            case "report" -> sendReport(sender);

            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§aConfiguration reloaded.");
            }

            case "convert" -> sender.sendMessage(
                    "§eConversion engine is not implemented yet."
            );

            default -> sender.sendMessage(
                    "§cUnknown command. Use /vortexconvert help."
            );
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter");
        sender.sendMessage("§8----------------------------");
        sender.sendMessage("§b/vortexconvert help §7- Help");
        sender.sendMessage("§b/vortexconvert scan §7- Scan integrations");
        sender.sendMessage("§b/vortexconvert itemsadder §7- Scan ItemsAdder");
        sender.sendMessage("§b/vortexconvert report §7- Server report");
        sender.sendMessage("§b/vortexconvert reload §7- Reload config");
        sender.sendMessage("§b/vortexconvert convert §7- Convert assets");
        sender.sendMessage("§8----------------------------");
    }

    private void scanPlugins(CommandSender sender) {
        sender.sendMessage("§b§lIntegration Scanner");

        checkPlugin(sender, "ItemsAdder");
        checkPlugin(sender, "Oraxen");
        checkPlugin(sender, "Nexo");
        checkPlugin(sender, "ModelEngine");
        checkPlugin(sender, "Geyser-Spigot");
        checkPlugin(sender, "Geyser");

        sender.sendMessage("§aIntegration scan completed.");
    }

    private void checkPlugin(CommandSender sender, String name) {
        Plugin detected = Bukkit.getPluginManager().getPlugin(name);

        if (detected != null && detected.isEnabled()) {
            sender.sendMessage(
                    "§a[FOUND] §f" + name + " §7v"
                            + detected.getPluginMeta().getVersion()
            );
        } else {
            sender.sendMessage("§c[NOT FOUND] §f" + name);
        }
    }

    private void scanItemsAdder(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter - ItemsAdder Scanner");
        sender.sendMessage("§7Scanning ItemsAdder assets...");

        ItemsAdderScanner.ScanResult result = itemsAdderScanner.scan();

        if (!result.isPluginInstalled()) {
            sender.sendMessage("§cItemsAdder folder not found.");
            return;
        }

        if (!result.isContentsFound()) {
            sender.sendMessage("§cItemsAdder contents folder not found.");
            return;
        }

        if (!result.isScanSuccessful()) {
            sender.sendMessage("§cScan failed: " + result.getError());
            return;
        }

        sender.sendMessage("§8----------------------------");
        sender.sendMessage("§aScan completed successfully!");
        sender.sendMessage("§7Namespaces: §f" + result.getNamespaceCount());
        sender.sendMessage("§7Total files: §f" + result.getTotalFiles());
        sender.sendMessage("§7Configurations: §f" + result.getConfigurations());
        sender.sendMessage("§7Textures: §f" + result.getTextures());
        sender.sendMessage("§7JSON models: §f" + result.getModels());
        sender.sendMessage("§7BBModels: §f" + result.getBbmodels());
        sender.sendMessage("§7Other files: §f" + result.getOtherFiles());
        sender.sendMessage("§7Total size: §f" + result.getFormattedSize());

        sender.sendMessage("§8----------------------------");
        sender.sendMessage("§bDetected namespaces:");

        for (String namespace : result.getNamespaces()) {
            sender.sendMessage("§7- §f" + namespace);
        }
    }

    private void sendReport(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter Server Report");
        sender.sendMessage("§8----------------------------");
        sender.sendMessage("§7Server: §f" + Bukkit.getName());
        sender.sendMessage("§7Minecraft: §f" + Bukkit.getMinecraftVersion());
        sender.sendMessage("§7Bukkit API: §f" + Bukkit.getBukkitVersion());
        sender.sendMessage("§7Java: §f" + System.getProperty("java.version"));
        sender.sendMessage("§7Online players: §f" + Bukkit.getOnlinePlayers().size());
        sender.sendMessage("§7Workspace: §f" + plugin.getWorkspaceFolder().getPath());
        sender.sendMessage("§7Output: §f" + plugin.getOutputFolder().getPath());
        sender.sendMessage("§8----------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("vortexconverter.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            for (String option : List.of(
                    "help", "scan", "itemsadder",
                    "report", "reload", "convert"
            )) {
                if (option.startsWith(input)) {
                    suggestions.add(option);
                }
            }

            return suggestions;
        }

        return List.of();
    }
}
