package fr.koriamc.totem.hook;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.model.FactionStats;
import fr.koriamc.totem.model.MetaState;
import fr.koriamc.totem.model.PlayerStats;
import fr.koriamc.totem.model.TotemDefinition;
import fr.koriamc.totem.model.TotemSession;
import fr.koriamc.totem.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Variables PlaceholderAPI :
 * <ul>
 *   <li>%koriatotem_actif%, %koriatotem_nom%, %koriatotem_restants%, %koriatotem_total%, %koriatotem_casses%,
 *       %koriatotem_barre%, %koriatotem_pourcentage%, %koriatotem_faction%, %koriatotem_joueur%,
 *       %koriatotem_meilleur%, %koriatotem_meilleur_blocs%, %koriatotem_x% / _y% / _z%, %koriatotem_etat%</li>
 *   <li>%koriatotem_prochain%, %koriatotem_prochain_temps%</li>
 *   <li>%koriatotem_&lt;id&gt;_actif%, %koriatotem_&lt;id&gt;_prochain%, %koriatotem_&lt;id&gt;_dernier%</li>
 *   <li>%koriatotem_top_&lt;n&gt;_faction%, %koriatotem_top_&lt;n&gt;_victoires%</li>
 *   <li>%koriatotem_victoires%, %koriatotem_rang%, %koriatotem_blocs%, %koriatotem_derniers_blocs%,
 *       %koriatotem_participations%</li>
 * </ul>
 */
final class TotemExpansion extends PlaceholderExpansion {

    /** Variables de l'évènement en cours reprises de Display.vars. */
    private static final String[] SESSION_KEYS = {"id", "restants", "total", "casses", "barre", "pourcentage",
            "faction", "joueur", "meilleur", "meilleur_blocs", "x", "y", "z", "etat", "points", "temps"};

    private final KoriaTotem plugin;

    TotemExpansion(KoriaTotem plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "koriatotem";
    }

    @Override
    public String getAuthor() {
        return "KoriaMC";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        String global = global(key);
        if (global != null) {
            return global;
        }
        if (key.startsWith("top_")) {
            return top(key.substring("top_".length()));
        }
        String perTotem = perTotem(key);
        if (perTotem != null) {
            return perTotem;
        }
        return player == null ? "" : forPlayer(player, key);
    }

    /** Variables du totem en cours et du prochain. */
    private String global(String key) {
        TotemSession active = plugin.totems().getActive();
        if (key.equals("actif")) {
            return String.valueOf(active != null);
        }
        if (key.equals("prochain") || key.equals("prochain_temps")) {
            TotemDefinition next = plugin.totems().nextScheduled();
            if (next == null) {
                return plugin.msg().text("values.none");
            }
            return key.equals("prochain") ? Text.color(next.getName())
                    : plugin.msg().duration(plugin.totems().nextScheduledMillis());
        }
        if (key.equals("nom")) {
            return active == null ? plugin.msg().text("values.none") : Text.color(active.getDefinition().getName());
        }
        for (String sessionKey : SESSION_KEYS) {
            if (!sessionKey.equals(key)) {
                continue;
            }
            if (active == null) {
                return key.equals("faction") || key.equals("joueur") || key.equals("meilleur")
                        ? plugin.msg().text("values.nobody") : key.equals("barre") ? plugin.display().bar(0) : "0";
            }
            Object[] vars = plugin.display().vars(active);
            for (int i = 0; i + 1 < vars.length; i += 2) {
                if (key.equals(vars[i])) {
                    return String.valueOf(vars[i + 1]);
                }
            }
        }
        return null;
    }

    /** "&lt;n&gt;_faction" ou "&lt;n&gt;_victoires" */
    private String top(String rest) {
        int underscore = rest.indexOf('_');
        Integer index = underscore < 0 ? null : Text.parseInt(rest.substring(0, underscore));
        if (index == null || index < 1) {
            return "";
        }
        List<FactionStats> top = plugin.data().top(null, index);
        if (top.size() < index) {
            return rest.endsWith("victoires") ? "0" : plugin.msg().text("values.none");
        }
        FactionStats stats = top.get(index - 1);
        return rest.substring(underscore + 1).equals("victoires") ? String.valueOf(stats.getWins()) : stats.getTag();
    }

    /** "&lt;id&gt;_actif", "&lt;id&gt;_prochain" ou "&lt;id&gt;_dernier" */
    private String perTotem(String key) {
        int underscore = key.lastIndexOf('_');
        if (underscore <= 0) {
            return null;
        }
        TotemDefinition def = plugin.catalog().get(key.substring(0, underscore));
        if (def == null) {
            return null;
        }
        String field = key.substring(underscore + 1);
        if (field.equals("actif")) {
            return String.valueOf(plugin.totems().get(def.getId()) != null);
        }
        if (field.equals("prochain")) {
            long millis = def.getSchedule().millisUntilNext(ZonedDateTime.now(plugin.settings().zone));
            return millis < 0 ? plugin.msg().text("values.none") : plugin.msg().duration(millis);
        }
        if (field.equals("dernier")) {
            MetaState.Winner winner = plugin.data().meta().getLast(def.getId());
            return winner == null ? plugin.msg().text("values.none") : winner.getTag();
        }
        return null;
    }

    /** Variables propres au joueur. */
    private String forPlayer(Player player, String key) {
        String factionId = plugin.factionHook().getFactionId(player);
        FactionStats faction = plugin.data().findFaction(factionId);
        PlayerStats stats = plugin.data().findPlayer(player.getUniqueId());
        if (key.equals("victoires")) {
            return faction == null ? "0" : String.valueOf(faction.getWins());
        }
        if (key.equals("rang")) {
            return factionId == null ? "0" : String.valueOf(plugin.data().rank(factionId));
        }
        if (key.equals("blocs")) {
            return stats == null ? "0" : String.valueOf(stats.getBlocks());
        }
        if (key.equals("derniers_blocs")) {
            return stats == null ? "0" : String.valueOf(stats.getFinalBlows());
        }
        if (key.equals("participations")) {
            return stats == null ? "0" : String.valueOf(stats.getParticipations());
        }
        return null;
    }
}
