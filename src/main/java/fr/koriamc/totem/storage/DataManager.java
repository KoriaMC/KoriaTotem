package fr.koriamc.totem.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.config.Settings;
import fr.koriamc.totem.model.FactionStats;
import fr.koriamc.totem.model.MetaState;
import fr.koriamc.totem.model.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Statistiques en mémoire (joueurs, factions, derniers vainqueurs), chargées au
 * démarrage puis sérialisées en JSON sur le thread principal et écrites par un
 * thread dédié.
 */
public final class DataManager {

    private static final String META_KEY = "totem";

    private final KoriaTotem plugin;
    private final Map<UUID, PlayerStats> players = new HashMap<UUID, PlayerStats>();
    private final Map<String, FactionStats> factions = new HashMap<String, FactionStats>();
    private MetaState meta = new MetaState();
    private Storage storage;
    private Gson gson;
    private ExecutorService executor;
    private BukkitTask autoSave;
    /** Tant que le chargement n'a pas réussi, rien n'est écrit (évite d'écraser les données). */
    private boolean loaded;

    public DataManager(KoriaTotem plugin) {
        this.plugin = plugin;
    }

    public void init() throws Exception {
        Settings s = plugin.settings();
        GsonBuilder builder = new GsonBuilder().disableHtmlEscaping();
        File folder = plugin.getDataFolder();
        if ("MYSQL".equals(s.storageType) || "MARIADB".equals(s.storageType)) {
            storage = SqlStorage.mysql(s.mysqlHost, s.mysqlPort, s.mysqlDatabase, s.mysqlUser, s.mysqlPassword,
                    s.mysqlPrefix, s.mysqlParameters);
        } else if ("SQLITE".equals(s.storageType)) {
            storage = SqlStorage.sqlite(new File(folder, s.sqliteFile));
        } else {
            if (!"JSON".equals(s.storageType)) {
                plugin.getLogger().warning("Type de stockage inconnu '" + s.storageType + "', JSON utilisé.");
            }
            if (s.jsonPretty) {
                builder.setPrettyPrinting();
            }
            storage = new JsonStorage(new File(folder, "data"), s.jsonBackups, plugin.getLogger());
        }
        gson = builder.create();
        storage.init();
        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "KoriaTotem-Save");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public String getStorageName() {
        return storage == null ? "?" : storage.getName();
    }

    public void load() throws Exception {
        DataSnapshot data = storage.load();
        players.clear();
        factions.clear();
        for (Map.Entry<String, String> e : data.players.entrySet()) {
            try {
                PlayerStats ps = gson.fromJson(e.getValue(), PlayerStats.class);
                if (ps != null) {
                    ps.ensureInit();
                    players.put(ps.getUniqueId(), ps);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Joueur illisible ignoré (" + e.getKey() + ") : " + ex.getMessage());
            }
        }
        for (Map.Entry<String, String> e : data.factions.entrySet()) {
            try {
                FactionStats fs = gson.fromJson(e.getValue(), FactionStats.class);
                if (fs != null && fs.getFactionId() != null) {
                    fs.ensureInit();
                    factions.put(fs.getFactionId(), fs);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Faction illisible ignorée (" + e.getKey() + ") : " + ex.getMessage());
            }
        }
        String rawMeta = data.meta.get(META_KEY);
        MetaState state = rawMeta == null ? null : gson.fromJson(rawMeta, MetaState.class);
        meta = state == null ? new MetaState() : state;
        meta.ensureInit();
        loaded = true;
        plugin.getLogger().info("Statistiques chargées (" + getStorageName() + ") : " + players.size()
                + " joueur(s), " + factions.size() + " faction(s).");
    }

    // ================================================================= Accès

    public MetaState meta() {
        return meta;
    }

    /** Statistiques d'un joueur (créées si besoin). */
    public PlayerStats player(UUID uuid, String name) {
        PlayerStats ps = players.get(uuid);
        if (ps == null) {
            ps = new PlayerStats(uuid, name);
            players.put(uuid, ps);
        } else {
            ps.setName(name);
        }
        return ps;
    }

    /** @return les statistiques du joueur, ou null s'il n'en a pas */
    public PlayerStats findPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public Collection<PlayerStats> players() {
        return players.values();
    }

    /** Statistiques d'une faction (créées si besoin). */
    public FactionStats faction(String factionId, String tag) {
        FactionStats fs = factions.get(factionId);
        if (fs == null) {
            fs = new FactionStats(factionId, tag);
            factions.put(factionId, fs);
        } else {
            fs.setTag(tag);
        }
        return fs;
    }

    public FactionStats findFaction(String factionId) {
        return factionId == null ? null : factions.get(factionId);
    }

    public Collection<FactionStats> factions() {
        return factions.values();
    }

    /**
     * Classement des factions.
     *
     * @param totemId totem concerné, ou null pour le total
     * @param limit  nombre maximum de lignes
     */
    public List<FactionStats> top(final String totemId, int limit) {
        List<FactionStats> list = new ArrayList<FactionStats>();
        for (FactionStats fs : factions.values()) {
            if (wins(fs, totemId) > 0) {
                list.add(fs);
            }
        }
        Collections.sort(list, new Comparator<FactionStats>() {
            @Override
            public int compare(FactionStats a, FactionStats b) {
                int diff = wins(b, totemId) - wins(a, totemId);
                if (diff != 0) {
                    return diff;
                }
                long blocks = b.getBlocks() - a.getBlocks();
                return blocks == 0 ? a.getTag().compareToIgnoreCase(b.getTag()) : (blocks > 0 ? 1 : -1);
            }
        });
        return list.size() > limit ? new ArrayList<FactionStats>(list.subList(0, limit)) : list;
    }

    /** Victoires d'une faction, au total ou sur un totem précis. */
    public static int wins(FactionStats stats, String totemId) {
        return totemId == null ? stats.getWins() : stats.getWins(totemId);
    }

    /** Rang d'une faction au classement général (1 = première), 0 si elle n'y figure pas. */
    public int rank(String factionId) {
        List<FactionStats> all = top(null, Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getFactionId().equals(factionId)) {
                return i + 1;
            }
        }
        return 0;
    }

    // ============================================================ Sauvegarde

    /** Copie sérialisée des données utiles (thread principal). */
    public DataSnapshot snapshot() {
        DataSnapshot snapshot = new DataSnapshot();
        for (PlayerStats ps : players.values()) {
            if (!ps.isEmpty()) {
                snapshot.players.put(ps.getId(), gson.toJson(ps));
            }
        }
        for (FactionStats fs : factions.values()) {
            if (!fs.isEmpty()) {
                snapshot.factions.put(fs.getFactionId(), gson.toJson(fs));
            }
        }
        snapshot.meta.put(META_KEY, gson.toJson(meta));
        return snapshot;
    }

    public void saveAsync() {
        if (!loaded || executor == null || executor.isShutdown()) {
            return;
        }
        final DataSnapshot snapshot = snapshot();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                write(snapshot);
            }
        });
    }

    private void write(DataSnapshot snapshot) {
        try {
            storage.save(snapshot);
            if (plugin.settings().debug) {
                plugin.getLogger().info("Sauvegarde effectuée.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde des statistiques", e);
        }
    }

    public void startAutoSave() {
        stopAutoSave();
        int minutes = plugin.settings().autoSaveMinutes;
        if (minutes <= 0) {
            return;
        }
        long period = minutes * 60L * 20L;
        autoSave = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                saveAsync();
            }
        }, period, period);
    }

    public void stopAutoSave() {
        if (autoSave != null) {
            autoSave.cancel();
            autoSave = null;
        }
    }

    /** Sauvegarde synchrone finale et fermeture du stockage. */
    public void shutdown() {
        stopAutoSave();
        if (storage == null) {
            return;
        }
        if (!loaded) {
            storage.close();
            return;
        }
        DataSnapshot snapshot = snapshot();
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Des sauvegardes asynchrones n'ont pas pu se terminer à temps.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        write(snapshot);
        storage.close();
    }
}
