package fr.koriamc.totem.api;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.game.TotemManager;
import fr.koriamc.totem.model.FactionStats;
import fr.koriamc.totem.model.MetaState;
import fr.koriamc.totem.model.TotemDefinition;
import fr.koriamc.totem.model.TotemSession;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;

/**
 * Accès pour les autres plugins KoriaMC (quêtes, boutique, panneaux...).
 * <pre>
 * if (KoriaTotemAPI.isRunning()) {
 *     TotemSession session = KoriaTotemAPI.getActiveSession();
 *     int restants = session.getRemaining();
 * }
 * </pre>
 * Évènements disponibles dans fr.koriamc.totem.api.event.
 */
public final class KoriaTotemAPI {

    private KoriaTotemAPI() {
    }

    private static KoriaTotem plugin() {
        KoriaTotem plugin = KoriaTotem.get();
        if (plugin == null) {
            throw new IllegalStateException("KoriaTotem n'est pas activé");
        }
        return plugin;
    }

    public static boolean isAvailable() {
        return KoriaTotem.get() != null;
    }

    /** Identifiants des totems configurés. */
    public static List<String> getTotemIds() {
        return plugin().catalog().ids();
    }

    /** @return la configuration d'un totem, ou null */
    public static TotemDefinition getTotem(String id) {
        return plugin().catalog().get(id);
    }

    public static boolean isRunning() {
        return plugin().totems().isRunning();
    }

    /** @return le premier totem en cours, ou null */
    public static TotemSession getActiveSession() {
        return plugin().totems().getActive();
    }

    /** @return le totem en cours portant cet identifiant, ou null */
    public static TotemSession getSession(String id) {
        return plugin().totems().get(id);
    }

    public static Collection<TotemSession> getSessions() {
        return plugin().totems().sessions();
    }

    /** Ce bloc fait-il partie d'un totem en cours ? */
    public static boolean isTotemBlock(Location location) {
        return plugin().totems().sessionAt(location) != null;
    }

    /**
     * Fait apparaître un totem.
     *
     * @param force ignorer la limite d'un seul évènement à la fois
     */
    public static TotemManager.Result start(String id, boolean force) {
        return plugin().totems().start(id, force);
    }

    /** Arrête un totem sans vainqueur. @return false s'il n'était pas en cours */
    public static boolean stop(String id) {
        return plugin().totems().stopTotem(id);
    }

    /** @return les millisecondes avant le prochain totem planifié, ou -1 */
    public static long getNextMillis() {
        return plugin().totems().nextScheduledMillis();
    }

    /** @return le prochain totem planifié, ou null */
    public static TotemDefinition getNextTotem() {
        return plugin().totems().nextScheduled();
    }

    /** Victoires d'une faction (identifiant KoriaFactions). */
    public static int getWins(String factionId) {
        FactionStats stats = plugin().data().findFaction(factionId);
        return stats == null ? 0 : stats.getWins();
    }

    /**
     * Classement des factions.
     *
     * @param totemId totem concerné, ou null pour le total
     */
    public static List<FactionStats> getTop(String totemId, int limit) {
        return plugin().data().top(totemId, limit);
    }

    /** @return le dernier vainqueur d'un totem, ou null */
    public static MetaState.Winner getLastWinner(String totemId) {
        return plugin().data().meta().getLast(totemId);
    }
}
