
package id.vortexconverter.commands;

import id.vortexconverter.VortexConverter;
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

    public CommandManager(VortexConverter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("vortexconverter.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);

            case "scan" -> scanPlugins(sender);

            case "report" -> sendReport(sender);

            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§aVortexConverter configuration reloaded.");
            }

            case "convert" -> sender.sendMessage(
                    "§eConversion engine is not implemented yet."
            );

            default -> {
                sender.sendMessage("§cUnknown command. Use /vortexconvert help.");
            }
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter §7- §fJava to Bedrock");
        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§b/vortexconvert help §7- Show commands");
        sender.sendMessage("§b/vortexconvert scan §7- Scan installed plugins");
        sender.sendMessage("§b/vortexconvert report §7- Server environment report");
        sender.sendMessage("§b/vortexconvert reload §7- Reload configuration");
        sender.sendMessage("§b/vortexconvert convert §7- Start conversion");
        sender.sendMessage("§8--------------------------------");
    }

    private void scanPlugins(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter Plugin Scanner");
        sender.sendMessage("§7Checking installed integrations...");

        checkPlugin(sender, "ItemsAdder");
        checkPlugin(sender, "Oraxen");
        checkPlugin(sender, "Nexo");
        checkPlugin(sender, "ModelEngine");
        checkPlugin(sender, "Geyser-Spigot");
        checkPlugin(sender, "Geyser");

        sender.sendMessage("§aPlugin scan completed.");
    }

    private void checkPlugin(CommandSender sender, String name) {
        Plugin detected = Bukkit.getPluginManager().getPlugin(name);

        if (detected != null && detected.isEnabled()) {
            sender.sendMessage(
                    "§a[FOUND] §f" + name +
                    " §7(v" + detected.getPluginMeta().getVersion() + ")"
            );
        } else {
            sender.sendMessage("§c[NOT FOUND] §f" + name);
        }
    }

    private void sendReport(CommandSender sender) {
        sender.sendMessage("§b§lVortexConverter Server Report");
        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§7Server: §f" + Bukkit.getName());
        sender.sendMessage("§7Minecraft: §f" + Bukkit.getMinecraftVersion());
        sender.sendMessage("§7Bukkit API: §f" + Bukkit.getBukkitVersion());
        sender.sendMessage("§7Java: §f" + System.getProperty("java.version"));
        sender.sendMessage("§7Online players: §f" +
                Bukkit.getOnlinePlayers().size());

        sender.sendMessage("§8--------------------------------");
        sender.sendMessage("§7Workspace: §f" +
                plugin.getWorkspaceFolder().getPath());

        sender.sendMessage("§7Output: §f" +
                plugin.getOutputFolder().getPath());

        sender.sendMessage("§8--------------------------------");
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
                    "help",
                    "scan",
                    "report",
                    "reload",
                    "convert"
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
