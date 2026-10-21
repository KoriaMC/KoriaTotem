package fr.koriamc.totem.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Données générales sauvegardées : dernier vainqueur de chaque totem et blocs des
 * totems en cours (retirés au démarrage suivant si le serveur s'est arrêté brutalement).
 */
public final class MetaState {

    /** Vainqueur d'un totem. */
    public static final class Winner {

        private String factionId;
        private String tag;
        private String player;
        private long time;

        public Winner(String factionId, String tag, String player) {
            this.factionId = factionId;
            this.tag = tag;
            this.player = player;
            this.time = System.currentTimeMillis();
        }

        /** Identifiant de la faction gagnante, ou null pour un joueur seul. */
        public String getFactionId() {
            return factionId;
        }

        /** Tag de la faction, ou le pseudo du joueur s'il n'avait pas de faction. */
        public String getTag() {
            return tag == null ? "?" : tag;
        }

        public String getPlayer() {
            return player == null ? "?" : player;
        }

        public long getTime() {
            return time;
        }
    }

    private Map<String, Winner> last;
    /** totem -> blocs posés, au format "monde;x;y;z;id;data" (état d'origine de chaque emplacement). */
    private Map<String, List<String>> structures;

    public void ensureInit() {
        if (last == null) {
            last = new LinkedHashMap<String, Winner>();
        }
        if (structures == null) {
            structures = new LinkedHashMap<String, List<String>>();
        }
    }

    /** Blocs posés des totems en cours. */
    public Map<String, List<String>> getStructures() {
        return structures;
    }

    /** @return le dernier vainqueur de ce totem, ou null */
    public Winner getLast(String totemId) {
        return last.get(totemId);
    }

    public void setLast(String totemId, Winner winner) {
        last.put(totemId, winner);
    }

    public Map<String, Winner> getLast() {
        return last;
    }
}
