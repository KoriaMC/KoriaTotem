package fr.koriamc.totem.model;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Un totem en cours : blocs encore debout, faction qui le casse en ce moment et
 * nombre de blocs cassés par chaque joueur et chaque faction.
 */
public final class TotemSession {

    /** Préfixe de la clé d'un joueur sans faction, qui joue alors pour lui seul. */
    public static final String SOLO = "@";

    private final TotemDefinition definition;
    private final long startedAt = System.currentTimeMillis();
    private final List<Location> blocks;
    /** Type et donnée de chaque emplacement avant la pose du totem, remis à la fin. */
    private final List<int[]> originals = new ArrayList<int[]>();
    private final boolean[] standing;
    private final Map<UUID, Integer> playerBlocks = new LinkedHashMap<UUID, Integer>();
    private final Map<UUID, String> names = new HashMap<UUID, String>();
    private final Map<UUID, String> playerGroup = new HashMap<UUID, String>();
    private final Map<String, Integer> groupBlocks = new LinkedHashMap<String, Integer>();
    private final Map<String, String> groupNames = new HashMap<String, String>();
    private final Set<Integer> announced = new HashSet<Integer>();

    private int remaining;
    private String current;
    private String currentName;
    private UUID lastBreaker;
    private int rebuilds;

    public TotemSession(TotemDefinition definition, List<Location> blocks) {
        this.definition = definition;
        this.blocks = blocks;
        this.standing = new boolean[blocks.size()];
        for (int i = 0; i < standing.length; i++) {
            standing[i] = true;
        }
        this.remaining = blocks.size();
    }

    public TotemDefinition getDefinition() {
        return definition;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long elapsedMillis() {
        return System.currentTimeMillis() - startedAt;
    }

    /** L'évènement a-t-il dépassé sa durée maximale ? */
    public boolean isExpired() {
        int max = definition.getMaxDurationMinutes();
        return max > 0 && elapsedMillis() >= max * 60000L;
    }

    // ================================================================ Blocs

    public List<Location> getBlocks() {
        return blocks;
    }

    public List<int[]> getOriginals() {
        return originals;
    }

    /** @return l'index du bloc du totem à cette position, ou -1 */
    public int indexOf(Location location) {
        if (location == null || location.getWorld() == null) {
            return -1;
        }
        for (int i = 0; i < blocks.size(); i++) {
            Location block = blocks.get(i);
            if (block.getWorld().equals(location.getWorld()) && block.getBlockX() == location.getBlockX()
                    && block.getBlockY() == location.getBlockY() && block.getBlockZ() == location.getBlockZ()) {
                return i;
            }
        }
        return -1;
    }

    public boolean isStanding(int index) {
        return index >= 0 && index < standing.length && standing[index];
    }

    /** Marque un bloc comme cassé. */
    public void breakBlock(int index) {
        if (isStanding(index)) {
            standing[index] = false;
            remaining--;
        }
    }

    /** Tous les blocs sont de nouveau debout. */
    public void rebuild() {
        for (int i = 0; i < standing.length; i++) {
            standing[i] = true;
        }
        remaining = standing.length;
        announced.clear();
        rebuilds++;
    }

    public int getRemaining() {
        return remaining;
    }

    public int getTotal() {
        return standing.length;
    }

    /** Progression de la destruction, de 0 à 1. */
    public double getProgress() {
        return standing.length == 0 ? 1 : (standing.length - remaining) / (double) standing.length;
    }

    /** Nombre de reconstructions depuis le début. */
    public int getRebuilds() {
        return rebuilds;
    }

    /** Palier d'annonce déjà passé ? (true la première fois seulement depuis la dernière reconstruction) */
    public boolean announceOnce(int remainingBlocks) {
        return announced.add(remainingBlocks);
    }

    // ============================================================ Casseurs

    /** Clé du groupe qui casse en ce moment : identifiant de faction, ou {@link #SOLO} + UUID. */
    public String getCurrent() {
        return current;
    }

    /** Tag de la faction (ou pseudo du joueur seul) qui casse en ce moment. */
    public String getCurrentName() {
        return currentName;
    }

    public void setCurrent(String key, String name) {
        this.current = key;
        this.currentName = name;
    }

    /** Dernier joueur à avoir cassé un bloc. */
    public UUID getLastBreaker() {
        return lastBreaker;
    }

    /** Enregistre un bloc cassé par ce joueur pour ce groupe. */
    public void addBlock(UUID uuid, String name, String group, String groupName) {
        Integer count = playerBlocks.get(uuid);
        playerBlocks.put(uuid, count == null ? 1 : count + 1);
        names.put(uuid, name);
        playerGroup.put(uuid, group);
        Integer groupCount = groupBlocks.get(group);
        groupBlocks.put(group, groupCount == null ? 1 : groupCount + 1);
        groupNames.put(group, groupName);
        lastBreaker = uuid;
    }

    public int getBlocks(UUID uuid) {
        Integer value = playerBlocks.get(uuid);
        return value == null ? 0 : value;
    }

    public Map<UUID, Integer> getPlayerBlocks() {
        return playerBlocks;
    }

    public String getName(UUID uuid) {
        return names.get(uuid);
    }

    /** Groupe pour lequel ce joueur a cassé ses blocs. */
    public String getGroup(UUID uuid) {
        return playerGroup.get(uuid);
    }

    public int getGroupBlocks(String group) {
        Integer value = groupBlocks.get(group);
        return value == null ? 0 : value;
    }

    public String getGroupName(String group) {
        return groupNames.get(group);
    }

    /** @return le joueur qui a cassé le plus de blocs, ou null */
    public UUID getBestPlayer() {
        UUID best = null;
        int count = 0;
        for (Map.Entry<UUID, Integer> entry : playerBlocks.entrySet()) {
            if (entry.getValue() > count) {
                best = entry.getKey();
                count = entry.getValue();
            }
        }
        return best;
    }

    /** @return le groupe qui a cassé le plus de blocs, ou null (en cas d'égalité : le premier arrivé) */
    public String getBestGroup() {
        String best = null;
        int count = 0;
        for (Map.Entry<String, Integer> entry : groupBlocks.entrySet()) {
            if (entry.getValue() > count) {
                best = entry.getKey();
                count = entry.getValue();
            }
        }
        return best;
    }

    /** Joueurs qui ont cassé au moins {@code min} blocs. */
    public List<UUID> participants(int min) {
        List<UUID> list = new ArrayList<UUID>();
        for (Map.Entry<UUID, Integer> entry : playerBlocks.entrySet()) {
            if (entry.getValue() >= min) {
                list.add(entry.getKey());
            }
        }
        return list;
    }
}
