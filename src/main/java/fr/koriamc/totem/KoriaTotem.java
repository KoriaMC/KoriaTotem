package fr.koriamc.totem;

import fr.koriamc.totem.command.CommandManager;
import fr.koriamc.totem.config.ConfigDocs;
import fr.koriamc.totem.config.JsonFile;
import fr.koriamc.totem.config.TotemCatalog;
import fr.koriamc.totem.config.Messages;
import fr.koriamc.totem.config.Settings;
import fr.koriamc.totem.game.Display;
import fr.koriamc.totem.game.TotemManager;
import fr.koriamc.totem.gui.Menu;
import fr.koriamc.totem.gui.MenuManager;
import fr.koriamc.totem.hook.FactionHook;
import fr.koriamc.totem.hook.GuiHook;
import fr.koriamc.totem.hook.PapiHook;
import fr.koriamc.totem.hook.QuestHook;
import fr.koriamc.totem.hook.VaultHook;
import fr.koriamc.totem.listener.TotemListener;
import fr.koriamc.totem.reward.RewardService;
import fr.koriamc.totem.storage.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * KoriaTotem : évènement Totem pour KoriaMC (Spigot 1.8.8).
 * Un totem apparaît : la faction qui le détruit remporte des points de faction et des récompenses.
 */
public final class KoriaTotem extends JavaPlugin {

    private static KoriaTotem instance;

    // Fichiers
    private JsonFile configFile;
    private JsonFile totemsFile;
    private JsonFile messagesFile;
    private JsonFile menusFile;
    private Configuration config = new MemoryConfiguration();
    private Configuration messagesConfig = new MemoryConfiguration();
    private Configuration menusConfig = new MemoryConfiguration();

    // Réglages
    private final Settings settings = new Settings();
    private final TotemCatalog catalog = new TotemCatalog();
    private final Messages messages = new Messages();
    private final ConfigDocs configDocs = new ConfigDocs();

    // Services
    private DataManager data;
    private FactionHook factionHook;
    private TotemManager totems;
    private Display display;
    private RewardService rewards;
    private MenuManager menus;
    private CommandManager commands;
    private QuestHook questHook;
    private GuiHook guiHook;
    private PapiHook papi;
    private VaultHook vault;

    public static KoriaTotem get() {
        return instance;
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;

        configFile = new JsonFile(this, "config.json");
        totemsFile = new JsonFile(this, "totems.json", "*");
        messagesFile = new JsonFile(this, "messages.json");
        menusFile = new JsonFile(this, "menus.json", "liste.objets", "top.objets", "editeur.fichiers");

        data = new DataManager(this);
        factionHook = new FactionHook();
        totems = new TotemManager(this);
        display = new Display(this);
        rewards = new RewardService(this);
        menus = new MenuManager(this);
        commands = new CommandManager(this);
        questHook = new QuestHook(this);
        guiHook = new GuiHook(this);

        for (String problem : loadConfigs()) {
            getLogger().warning(problem);
        }
        configDocs.load(this);
        configDocs.writeReadme(this, configFiles());

        try {
            data.init();
            data.load();
            totems.cleanupLeftovers();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible de charger les données : le plugin est désactivé pour éviter"
                    + " toute perte.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                papi = new PapiHook(this);
                getLogger().info("PlaceholderAPI détecté : variables %koriatotem_...% enregistrées.");
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Impossible de s'accrocher à PlaceholderAPI", t);
                papi = null;
            }
        }
        questHook.register();

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new TotemListener(this), this);
        pm.registerEvents(menus, this);
        pm.registerEvents(menus.prompt(), this);

        commands.load(config, messagesConfig);
        commands.registerBukkitCommand();

        totems.start();
        data.startAutoSave();
        getLogger().info("KoriaTotem activé en " + (System.currentTimeMillis() - start) + " ms (stockage : "
                + data.getStorageName() + ", " + catalog.count() + " totems).");
    }

    @Override
    public void onDisable() {
        if (data == null) {
            return;
        }
        if (commands != null) {
            commands.unregisterBukkitCommand();
        }
        if (totems != null) {
            totems.stop();
            totems.stopAll(false);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        if (papi != null) {
            papi.unregister();
            papi = null;
        }
        data.shutdown();
        instance = null;
    }

    /** @return les problèmes à signaler (erreurs JSON, totems invalides) */
    private List<String> loadConfigs() {
        List<String> problems = new ArrayList<String>();
        for (JsonFile file : configFiles()) {
            String error = file.load();
            if (error != null) {
                problems.add(file.getName() + " : " + error);
            }
        }
        config = configFile.toConfiguration();
        messagesConfig = messagesFile.toConfiguration();
        menusConfig = menusFile.toConfiguration();

        settings.load(config, getLogger());
        messages.load(messagesConfig, settings);
        catalog.load(totemsFile.toConfiguration(), settings);
        problems.addAll(catalog.getProblems());
        return problems;
    }

    /**
     * Recharge toute la configuration (/totem reload, éditeur).
     *
     * @return les problèmes détectés, à afficher à l'auteur de la commande
     */
    public List<String> reload() {
        List<String> problems = loadConfigs();
        commands.load(config, messagesConfig);
        data.startAutoSave();
        questHook.register();
        return problems;
    }

    /** Dépose de l'argent via Vault. @return false si aucune économie n'est disponible */
    public boolean deposit(Player player, double amount) {
        if (vault == null && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            vault = VaultHook.create();
        }
        if (vault == null) {
            return false;
        }
        vault.deposit(player, amount);
        return true;
    }

    // ============================================================ Accesseurs

    public Settings settings() {
        return settings;
    }

    public TotemCatalog catalog() {
        return catalog;
    }

    public Messages msg() {
        return messages;
    }

    /** Explication des options (éditeur /totem config et LISEZMOI.txt). */
    public ConfigDocs configDocs() {
        return configDocs;
    }

    public Configuration config() {
        return config;
    }

    public Configuration menusConfig() {
        return menusConfig;
    }

    /** Fichiers de configuration, dans l'ordre de l'éditeur. */
    public List<JsonFile> configFiles() {
        return Arrays.asList(configFile, totemsFile, messagesFile, menusFile);
    }

    /** Fichier des totems, modifié par /totem create, position et supprimer. */
    public JsonFile totemsFile() {
        return totemsFile;
    }

    public DataManager data() {
        return data;
    }

    public FactionHook factionHook() {
        return factionHook;
    }

    public TotemManager totems() {
        return totems;
    }

    public Display display() {
        return display;
    }

    public RewardService rewards() {
        return rewards;
    }

    public MenuManager menus() {
        return menus;
    }

    public CommandManager commands() {
        return commands;
    }

    public QuestHook questHook() {
        return questHook;
    }

    public GuiHook guiHook() {
        return guiHook;
    }
}
