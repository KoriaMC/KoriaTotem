package fr.koriamc.totem.gui;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.command.SubCommand;
import fr.koriamc.totem.config.JsonFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Collections;
import java.util.Locale;

/**
 * Ouverture des menus et gestion des clics.
 */
public final class MenuManager implements Listener {

    private final KoriaTotem plugin;
    private final ChatPrompt prompt;

    public MenuManager(KoriaTotem plugin) {
        this.plugin = plugin;
        this.prompt = new ChatPrompt(plugin);
    }

    /** Saisie de valeurs au clavier (éditeur de configuration). */
    public ChatPrompt prompt() {
        return prompt;
    }

    /** Section de menus.json (jamais nulle). */
    public ConfigurationSection section(String path) {
        ConfigurationSection s = plugin.menusConfig().getConfigurationSection(path);
        return s != null ? s : new MemoryConfiguration().createSection(path);
    }

    /**
     * Ouvre un menu d'après son identifiant (action MENU:&lt;id&gt; de menus.json) :
     * liste, top, top:&lt;totem&gt;, config, config:&lt;fichier&gt;.
     */
    public void open(Player player, String id) {
        String raw = id == null ? "liste" : id.trim();
        int colon = raw.indexOf(':');
        String key = (colon < 0 ? raw : raw.substring(0, colon)).toLowerCase(Locale.ROOT);
        String arg = colon < 0 ? null : raw.substring(colon + 1).trim();
        if (key.equals("liste") || key.equals("principal") || key.equals("totems")) {
            openList(player);
        } else if (key.equals("top") || key.equals("classement")) {
            openTop(player, arg);
        } else if (key.equals("config")) {
            SubCommand command = plugin.commands().get("config");
            if (command == null || !command.hasPermission(player)) {
                plugin.msg().send(player, "general.no-permission");
            } else if (!openConfig(player, arg)) {
                plugin.msg().send(player, "config-editor.unknown-file", "file", arg);
            }
        } else {
            plugin.getLogger().warning("Menu inconnu : " + id);
        }
    }

    /** Liste des totems : état, planning et dernier vainqueur. */
    public void openList(Player player) {
        if (plugin.catalog().count() == 0) {
            plugin.msg().send(player, "totem.none-configured");
            return;
        }
        new TotemListMenu(plugin, player).open();
    }

    /**
     * Classement des factions.
     *
     * @param totemId totem concerné, ou null pour le total
     */
    public void openTop(Player player, String totemId) {
        String id = totemId == null || totemId.isEmpty() ? null : totemId.toLowerCase(Locale.ROOT);
        if (id != null && plugin.catalog().get(id) == null) {
            plugin.msg().send(player, "totem.unknown", "totem", totemId);
            return;
        }
        new TopMenu(plugin, player, id).open();
    }

    /**
     * Éditeur de configuration : liste des fichiers, ou directement un fichier.
     *
     * @return false si le fichier demandé n'existe pas
     */
    public boolean openConfig(Player player, String fileName) {
        if (fileName == null) {
            new ConfigFilesMenu(plugin, player).open();
            return true;
        }
        for (JsonFile file : plugin.configFiles()) {
            String name = file.getName();
            if (name.equalsIgnoreCase(fileName) || name.equalsIgnoreCase(fileName + ".json")) {
                new ConfigSectionMenu(plugin, player, file, Collections.<String>emptyList(), 0).open();
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof Menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize() && event.getWhoClicked() instanceof Player) {
            ((Menu) top.getHolder()).handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
