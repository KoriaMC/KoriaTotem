package fr.koriamc.totem.model;

import fr.koriamc.totem.config.Settings;
import fr.koriamc.totem.util.Items;
import fr.koriamc.totem.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un totem décrit dans totems.json : une colonne de blocs posée à une position au
 * début de l'évènement. Les options absentes prennent la valeur par défaut de
 * config.json (section evenement).
 */
public final class TotemDefinition {

    private final String id;
    private final String name;
    private final String icon;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final boolean positioned;
    private final String material;
    private final int materialId;
    private final int materialData;
    private final int height;
    private final WinMode winMode;
    private final boolean rebuild;
    private final int maxDurationMinutes;
    private final Schedule schedule;
    private final Target target;
    private final long victoryPoints;
    private final List<String> winnerRewards;
    private final List<String> participantRewards;

    private TotemDefinition(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.icon = b.icon;
        this.world = b.world;
        this.x = b.x;
        this.y = b.y;
        this.z = b.z;
        this.positioned = b.positioned;
        this.material = b.material;
        this.materialId = b.materialId;
        this.materialData = b.materialData;
        this.height = b.height;
        this.winMode = b.winMode;
        this.rebuild = b.rebuild;
        this.maxDurationMinutes = b.maxDurationMinutes;
        this.schedule = b.schedule;
        this.target = b.target;
        this.victoryPoints = b.victoryPoints;
        this.winnerRewards = Collections.unmodifiableList(b.winnerRewards);
        this.participantRewards = Collections.unmodifiableList(b.participantRewards);
    }

    /** Valeurs lues avant la construction (évite un constructeur à vingt paramètres). */
    private static final class Builder {
        String id;
        String name;
        String icon;
        String world;
        int x;
        int y;
        int z;
        boolean positioned;
        String material;
        int materialId;
        int materialData;
        int height;
        WinMode winMode;
        boolean rebuild;
        int maxDurationMinutes;
        Schedule schedule;
        Target target;
        long victoryPoints;
        List<String> winnerRewards = new ArrayList<String>();
        List<String> participantRewards = new ArrayList<String>();
    }

    /**
     * Lit un totem de totems.json.
     *
     * @param problems liste alimentée avec les erreurs de configuration
     */
    public static TotemDefinition read(String id, ConfigurationSection s, Settings settings, List<String> problems) {
        String where = "totems.json : " + id;
        Builder b = new Builder();
        b.id = id;
        b.name = s.getString("nom", id);
        b.icon = s.getString("icone", "OBSIDIAN");
        b.world = s.getString("monde", "");
        ConfigurationSection position = s.getConfigurationSection("position");
        if (position != null) {
            b.x = position.getInt("x");
            b.y = position.getInt("y");
            b.z = position.getInt("z");
        }
        b.positioned = !b.world.isEmpty() && position != null && (b.x != 0 || b.y != 0 || b.z != 0);
        if (!b.positioned) {
            problems.add(where + " : position incomplète (monde et position.x/y/z, ou /totem position " + id + ")");
        }
        b.material = s.getString("materiau", settings.material);
        int[] parsed = MaterialSet.parse(b.material);
        if (parsed == null || parsed[0] <= 0) {
            problems.add(where + ".materiau inconnu : " + b.material + " (OBSIDIAN utilisé)");
            parsed = MaterialSet.parse("OBSIDIAN");
        }
        b.materialId = parsed[0];
        b.materialData = Math.max(0, parsed[1]);
        b.height = s.getInt("hauteur", settings.height);
        if (b.height < 1 || b.height > 64) {
            problems.add(where + ".hauteur doit être comprise entre 1 et 64");
            b.height = Math.max(1, Math.min(64, b.height));
        }
        WinMode mode = WinMode.parse(s.getString("mode-victoire"), settings.winMode);
        if (mode == null) {
            problems.add(where + ".mode-victoire inconnu (DERNIER-BLOC ou PLUS-DE-BLOCS)");
            mode = settings.winMode;
        }
        b.winMode = mode;
        b.rebuild = s.contains("reconstruction") ? s.getBoolean("reconstruction") : settings.rebuild;
        b.maxDurationMinutes = Math.max(0, s.getInt("duree-max-minutes", settings.maxDurationMinutes));
        b.schedule = Schedule.read(s.getConfigurationSection("planning"), where + ".planning", problems);
        b.victoryPoints = Math.max(0, s.getLong("points-victoire", settings.victoryPoints));
        ConfigurationSection rewards = s.getConfigurationSection("recompenses");
        Target target = Target.parse(rewards == null ? null : rewards.getString("cible"), settings.target);
        if (target == null) {
            problems.add(where + ".recompenses.cible inconnue (DESTRUCTEUR, PARTICIPANTS, MEMBRES_EN_LIGNE, MEMBRES, AUCUNE)");
            target = settings.target;
        }
        b.target = target;
        b.winnerRewards = new ArrayList<String>(Items.stringList(rewards, "vainqueur"));
        b.participantRewards = new ArrayList<String>(Items.stringList(rewards, "participants"));
        return new TotemDefinition(b);
    }

    public String getId() {
        return id;
    }

    /** Nom affiché (codes couleur non traduits). */
    public String getName() {
        return name;
    }

    /** Matériau de l'icône dans les menus. */
    public String getIcon() {
        return icon;
    }

    public String getWorldName() {
        return world;
    }

    /** @return le monde, ou null s'il n'est pas chargé */
    public World getWorld() {
        return world.isEmpty() ? null : Bukkit.getWorld(world);
    }

    /** Position du bloc le plus bas du totem, ou null si le monde n'est pas chargé. */
    public Location getBase() {
        World w = getWorld();
        return w == null ? null : new Location(w, x, y, z);
    }

    /** Blocs du totem, du bas vers le haut. */
    public List<Location> getBlocks() {
        List<Location> blocks = new ArrayList<Location>();
        World w = getWorld();
        if (w == null) {
            return blocks;
        }
        for (int i = 0; i < height; i++) {
            blocks.add(new Location(w, x, y + i, z));
        }
        return blocks;
    }

    /** Position de téléportation : à côté du totem. */
    public Location getSpawn() {
        World w = getWorld();
        return w == null ? null : new Location(w, x + 2.5, y, z + 0.5);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getMaterial() {
        return material;
    }

    public int getMaterialId() {
        return materialId;
    }

    public int getMaterialData() {
        return materialData;
    }

    /** Nombre de blocs du totem. */
    public int getHeight() {
        return height;
    }

    public WinMode getWinMode() {
        return winMode;
    }

    /** Le totem se reconstruit-il quand une autre faction se met à le casser ? */
    public boolean isRebuild() {
        return rebuild;
    }

    /** Durée maximale de l'évènement en minutes (0 = illimitée). */
    public int getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public Target getTarget() {
        return target;
    }

    /** Points de faction crédités à la faction gagnante (en plus des actions POINTS des récompenses). */
    public long getVictoryPoints() {
        return victoryPoints;
    }

    public List<String> getWinnerRewards() {
        return winnerRewards;
    }

    public List<String> getParticipantRewards() {
        return participantRewards;
    }

    /** Un totem est jouable si sa position est définie et son monde chargé. */
    public boolean isPlayable() {
        return positioned && getWorld() != null;
    }

    public boolean isPositioned() {
        return positioned;
    }
}
