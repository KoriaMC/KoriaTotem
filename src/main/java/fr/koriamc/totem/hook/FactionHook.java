package fr.koriamc.totem.hook;

import fr.koriamc.factions.KoriaFactions;
import fr.koriamc.factions.api.KoriaFactionsAPI;
import fr.koriamc.factions.model.FPlayer;
import fr.koriamc.factions.model.Faction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Seule classe qui interroge KoriaFactions : faction d'un joueur, membres et
 * points de faction. Les factions sont désignées par leur identifiant (stable,
 * contrairement au tag).
 */
public final class FactionHook {

    /** @return l'identifiant de la faction du joueur, ou null s'il n'en a pas */
    public String getFactionId(Player player) {
        Faction f = KoriaFactionsAPI.getFaction(player);
        return f == null || !f.isNormal() ? null : f.getId();
    }

    /** Faction d'un joueur, connecté ou non. */
    public String getFactionId(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return getFactionId(online);
        }
        KoriaFactions kf = KoriaFactions.get();
        FPlayer fp = kf == null ? null : kf.players().get(uuid);
        Faction f = fp == null ? null : kf.factions().getFaction(fp);
        return f == null || !f.isNormal() ? null : f.getId();
    }

    /** @return la faction, ou null si elle n'existe plus */
    public Faction getFaction(String id) {
        Faction f = id == null ? null : KoriaFactionsAPI.getFactionById(id);
        return f == null || !f.isNormal() ? null : f;
    }

    public Faction getByTag(String tag) {
        Faction f = tag == null ? null : KoriaFactionsAPI.getFactionByTag(tag);
        return f == null || !f.isNormal() ? null : f;
    }

    /** Tag de la faction, ou "?" si elle a été dissoute. */
    public String getTag(String id) {
        Faction f = getFaction(id);
        return f == null ? "?" : f.getTag();
    }

    public List<String> getTags() {
        List<String> tags = new ArrayList<String>();
        for (Faction f : KoriaFactionsAPI.getFactions()) {
            tags.add(f.getTag());
        }
        return tags;
    }

    public int getSize(String id) {
        Faction f = getFaction(id);
        return f == null ? 0 : f.getSize();
    }

    public Set<UUID> getMembers(String id) {
        Faction f = getFaction(id);
        return f == null ? Collections.<UUID>emptySet() : f.getMemberIds();
    }

    public List<Player> getOnlineMembers(String id) {
        List<Player> list = new ArrayList<Player>();
        if (id == null) {
            return list;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (id.equals(getFactionId(p))) {
                list.add(p);
            }
        }
        return list;
    }

    public long getPoints(String id) {
        return KoriaFactionsAPI.getPoints(getFaction(id));
    }

    /**
     * Crédite des points de faction.
     *
     * @return les points réellement crédités (0 si la faction n'existe plus)
     */
    public long addPoints(String id, long amount) {
        Faction f = getFaction(id);
        return f == null || amount == 0 ? 0 : KoriaFactionsAPI.addPoints(f, amount, "koriatotem");
    }
}
