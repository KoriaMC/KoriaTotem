package fr.koriamc.totem.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Statistiques d'une faction (sérialisées en JSON) : victoires, blocs de totem
 * cassés et détail par totem.
 */
public final class FactionStats {

    private String factionId;
    private String tag;
    private int wins;
    private long blocks;
    private long lastWin;
    private Map<String, Integer> winsPerTotem;

    public FactionStats(String factionId, String tag) {
        this.factionId = factionId;
        this.tag = tag;
        this.winsPerTotem = new LinkedHashMap<String, Integer>();
    }

    /** Recrée les collections absentes après une lecture JSON. */
    public void ensureInit() {
        if (winsPerTotem == null) {
            winsPerTotem = new LinkedHashMap<String, Integer>();
        }
    }

    public String getFactionId() {
        return factionId;
    }

    /** Tag connu lors de la dernière victoire (la faction peut avoir été dissoute). */
    public String getTag() {
        return tag == null ? "?" : tag;
    }

    public void setTag(String tag) {
        if (tag != null && !tag.isEmpty()) {
            this.tag = tag;
        }
    }

    public int getWins() {
        return wins;
    }

    /** Blocs de totem cassés par les membres de la faction. */
    public long getBlocks() {
        return blocks;
    }

    public void addBlocks(long count) {
        blocks += Math.max(0, count);
    }

    public long getLastWin() {
        return lastWin;
    }

    public int getWins(String totemId) {
        Integer value = winsPerTotem.get(totemId);
        return value == null ? 0 : value;
    }

    /** Enregistre une victoire sur un totem. */
    public void addWin(String totemId) {
        wins++;
        lastWin = System.currentTimeMillis();
        Integer current = winsPerTotem.get(totemId);
        winsPerTotem.put(totemId, current == null ? 1 : current + 1);
    }

    public Map<String, Integer> getWinsPerTotem() {
        return winsPerTotem;
    }

    /** Une faction sans victoire ni bloc cassé n'est pas sauvegardée. */
    public boolean isEmpty() {
        return wins == 0 && blocks == 0;
    }
}
