package fr.koriamc.totem.config;

import fr.koriamc.totem.model.Target;
import fr.koriamc.totem.model.WinMode;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Réglages typés de config.json, relus à chaque rechargement.
 */
public final class Settings {

    /** Destinataires de la barre de progression pendant l'évènement. */
    public enum Audience {
        /** Les joueurs à portée du totem (rayon-proches). */
        PROCHES,
        /** Tout le serveur. */
        TOUS,
        /** Personne. */
        AUCUN
    }

    public boolean debug;

    // Stockage
    public String storageType;
    public int autoSaveMinutes;
    public int jsonBackups;
    public boolean jsonPretty;
    public String sqliteFile;
    public String mysqlHost;
    public int mysqlPort;
    public String mysqlDatabase;
    public String mysqlUser;
    public String mysqlPassword;
    public String mysqlPrefix;
    public String mysqlParameters;

    // Planning
    public ZoneId zone;
    public boolean scheduleEnabled;
    /** Minutes avant le démarrage annoncées aux joueurs. */
    public final List<Integer> warnMinutes = new ArrayList<Integer>();

    // Évènement
    public boolean onlyOneAtATime;
    public String material;
    public int height;
    public WinMode winMode;
    public boolean rebuild;
    public int maxDurationMinutes;
    public Target target;
    /** Points de faction crédités à la faction gagnante. */
    public long victoryPoints;
    public boolean broadcast;
    /** Blocs restants qui déclenchent une annonce globale. */
    public final List<Integer> announceBlocks = new ArrayList<Integer>();
    public int participationBlocks;
    public boolean fireworks;
    public boolean soloAllowed;

    // Anti-abus
    public boolean ignoreCreative;
    public final Set<String> ignoredPermissions = new HashSet<String>();

    // Protection autour du totem
    public int protectRadius;
    public boolean protectBreak;
    public boolean protectPlace;
    public boolean protectExplosions;

    // Affichage
    public Audience audience;
    public int nearRadius;
    public boolean scoreboard;
    public int barLength;
    public String barSymbol;
    public String barFull;
    public String barEmpty;
    public int topShown;
    public int percentDecimals;

    // Titres
    public int titleFadeIn;
    public int titleStay;
    public int titleFadeOut;

    // Intégrations
    public boolean questIntegration;
    public String questObjective;
    public boolean guiIntegration;
    public String guiPrefix;

    private final Map<String, String> sounds = new HashMap<String, String>();

    public void load(ConfigurationSection c, Logger logger) {
        debug = c.getBoolean("debug");

        storageType = str(c, "stockage.type", "JSON").toUpperCase(Locale.ROOT);
        autoSaveMinutes = c.getInt("stockage.sauvegarde-auto-minutes");
        jsonBackups = c.getInt("stockage.copies-de-securite");
        jsonPretty = c.getBoolean("stockage.json-indente");
        sqliteFile = str(c, "stockage.sqlite-fichier", "donnees.db");
        mysqlHost = str(c, "stockage.mysql.hote", "localhost");
        mysqlPort = c.getInt("stockage.mysql.port", 3306);
        mysqlDatabase = str(c, "stockage.mysql.base", "koriatotem");
        mysqlUser = str(c, "stockage.mysql.utilisateur", "root");
        mysqlPassword = str(c, "stockage.mysql.mot-de-passe", "");
        mysqlPrefix = str(c, "stockage.mysql.prefixe-tables", "kt_");
        mysqlParameters = str(c, "stockage.mysql.parametres", "");

        try {
            zone = ZoneId.of(str(c, "planning.fuseau", "Europe/Paris"));
        } catch (DateTimeException e) {
            logger.warning("Fuseau horaire inconnu dans planning.fuseau : " + c.getString("planning.fuseau")
                    + " (Europe/Paris utilisé)");
            zone = ZoneId.of("Europe/Paris");
        }
        scheduleEnabled = !c.contains("planning.actif") || c.getBoolean("planning.actif");
        warnMinutes.clear();
        for (Integer minutes : c.getIntegerList("planning.annonce-avant-minutes")) {
            if (minutes != null && minutes > 0) {
                warnMinutes.add(minutes);
            }
        }

        onlyOneAtATime = c.getBoolean("evenement.un-seul-a-la-fois");
        material = str(c, "evenement.materiau", "OBSIDIAN");
        height = Math.max(1, Math.min(64, c.getInt("evenement.hauteur", 5)));
        WinMode mode = WinMode.parse(c.getString("evenement.mode-victoire"), WinMode.DERNIER_BLOC);
        if (mode == null) {
            logger.warning("Valeur inconnue dans evenement.mode-victoire : " + c.getString("evenement.mode-victoire")
                    + " (DERNIER-BLOC utilisé)");
            mode = WinMode.DERNIER_BLOC;
        }
        winMode = mode;
        rebuild = c.getBoolean("evenement.reconstruction");
        maxDurationMinutes = Math.max(0, c.getInt("evenement.duree-max-minutes", 30));
        Target parsedTarget = Target.parse(c.getString("evenement.cible-recompenses"), Target.PARTICIPANTS);
        if (parsedTarget == null) {
            logger.warning("Cible inconnue dans evenement.cible-recompenses : "
                    + c.getString("evenement.cible-recompenses") + " (PARTICIPANTS utilisée)");
            parsedTarget = Target.PARTICIPANTS;
        }
        target = parsedTarget;
        victoryPoints = Math.max(0, c.getLong("evenement.points-victoire", 500));
        broadcast = c.getBoolean("evenement.annonce-globale");
        announceBlocks.clear();
        for (Integer blocks : c.getIntegerList("evenement.annonce-blocs-restants")) {
            if (blocks != null && blocks > 0) {
                announceBlocks.add(blocks);
            }
        }
        participationBlocks = Math.max(1, c.getInt("evenement.participation-blocs-minimum", 1));
        fireworks = c.getBoolean("evenement.feux-artifice");
        soloAllowed = c.getBoolean("evenement.joueurs-sans-faction");

        ignoreCreative = c.getBoolean("anti-abus.ignorer-creatif");
        ignoredPermissions.clear();
        for (String node : c.getStringList("anti-abus.permissions-ignorees")) {
            if (node != null && !node.trim().isEmpty()) {
                ignoredPermissions.add(node.trim());
            }
        }

        protectRadius = Math.max(0, c.getInt("protection.rayon"));
        protectBreak = c.getBoolean("protection.casser-blocs");
        protectPlace = c.getBoolean("protection.poser-blocs");
        protectExplosions = c.getBoolean("protection.explosions");

        audience = audience(str(c, "affichage.destinataires", "PROCHES"), logger);
        nearRadius = Math.max(1, c.getInt("affichage.rayon-proches", 60));
        scoreboard = c.getBoolean("affichage.scoreboard");
        barLength = Math.max(1, c.getInt("affichage.barre.longueur", 20));
        barSymbol = str(c, "affichage.barre.symbole", "|");
        barFull = str(c, "affichage.barre.couleur-pleine", "&c");
        barEmpty = str(c, "affichage.barre.couleur-vide", "&8");
        topShown = Math.max(1, c.getInt("affichage.top-affiches", 10));
        percentDecimals = Math.max(0, Math.min(2, c.getInt("affichage.decimales-pourcentage")));

        titleFadeIn = c.getInt("titres.apparition", 5);
        titleStay = c.getInt("titres.duree", 40);
        titleFadeOut = c.getInt("titres.disparition", 10);

        questIntegration = c.getBoolean("integrations.koriaquest");
        questObjective = str(c, "integrations.objectif-koriaquest", "GAGNER_TOTEM").toUpperCase(Locale.ROOT);
        guiIntegration = c.getBoolean("integrations.koria-gui");
        guiPrefix = str(c, "integrations.koria-gui-prefixe", "totem_");

        sounds.clear();
        ConfigurationSection soundSection = c.getConfigurationSection("sons");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, soundSection.getString(key, ""));
            }
        }
    }

    /** Son de la section sons (chaîne vide si absent). */
    public String sound(String key) {
        String value = sounds.get(key);
        return value == null ? "" : value;
    }

    /** Un joueur ignoré ne peut pas casser le totem (créatif, staff en modération). */
    public boolean isIgnored(Player player) {
        if (player == null) {
            return true;
        }
        if (ignoreCreative && player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        for (String node : ignoredPermissions) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }

    private Audience audience(String raw, Logger logger) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (Audience candidate : Audience.values()) {
            if (candidate.name().equals(value)) {
                return candidate;
            }
        }
        logger.warning("Valeur inconnue dans affichage.destinataires : " + raw + " (PROCHES utilisé)");
        return Audience.PROCHES;
    }

    private static String str(ConfigurationSection c, String path, String def) {
        String value = c.getString(path);
        return value == null ? def : value;
    }
}
