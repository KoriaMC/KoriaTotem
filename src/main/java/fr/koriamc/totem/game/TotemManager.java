package fr.koriamc.totem.game;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.api.event.TotemBreakEvent;
import fr.koriamc.totem.api.event.TotemDestroyEvent;
import fr.koriamc.totem.api.event.TotemEndEvent;
import fr.koriamc.totem.api.event.TotemStartEvent;
import fr.koriamc.totem.model.MetaState;
import fr.koriamc.totem.model.PlayerStats;
import fr.koriamc.totem.model.Target;
import fr.koriamc.totem.model.TotemDefinition;
import fr.koriamc.totem.model.TotemSession;
import fr.koriamc.totem.model.WinMode;
import fr.koriamc.totem.util.Sounds;
import fr.koriamc.totem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Déroulement des totems : apparition manuelle ou planifiée, blocs cassés par les
 * factions, reconstruction, victoire, récompenses et retrait du totem.
 */
@SuppressWarnings("deprecation")
public final class TotemManager {

    /** Résultat d'une demande de démarrage. */
    public enum Result {
        OK,
        /** Aucun totem ne porte cet identifiant. */
        INCONNU,
        /** Ce totem est déjà en cours. */
        DEJA_EN_COURS,
        /** Un autre totem est en cours et evenement.un-seul-a-la-fois est actif. */
        AUTRE_EN_COURS,
        /** Position non définie ou monde non chargé. */
        POSITION_INVALIDE,
        /** Un autre plugin a annulé le démarrage. */
        ANNULE
    }

    /** Résultat d'un bloc cassé, pour le message au joueur. */
    public enum BreakResult {
        /** Le bloc est compté. */
        COMPTE,
        /** Le joueur ne peut pas casser le totem (créatif, sans faction...). */
        REFUSE,
        /** Un autre plugin a annulé le coup. */
        ANNULE
    }

    private final KoriaTotem plugin;
    private final Map<String, TotemSession> sessions = new LinkedHashMap<String, TotemSession>();
    private BukkitTask task;
    private String lastMinute = "";

    public TotemManager(KoriaTotem plugin) {
        this.plugin = plugin;
    }

    /** Démarre la boucle de jeu (une fois par seconde). */
    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Termine tous les totems en cours sans vainqueur et retire leurs blocs. */
    public void stopAll(boolean announce) {
        for (TotemSession session : new ArrayList<TotemSession>(sessions.values())) {
            end(session, announce ? "totem.stopped" : null, false);
        }
        plugin.display().updateSidebar();
    }

    /**
     * Retire les totems restés en place après un arrêt brutal du serveur (blocs
     * enregistrés au démarrage de chaque évènement).
     */
    public void cleanupLeftovers() {
        Map<String, List<String>> structures = plugin.data().meta().getStructures();
        if (structures.isEmpty()) {
            return;
        }
        int restored = 0;
        for (Map.Entry<String, List<String>> entry : new LinkedHashMap<String, List<String>>(structures).entrySet()) {
            for (String raw : entry.getValue()) {
                String[] parts = raw.split(";");
                if (parts.length < 6) {
                    continue;
                }
                World world = Bukkit.getWorld(parts[0]);
                Integer x = Text.parseInt(parts[1]);
                Integer y = Text.parseInt(parts[2]);
                Integer z = Text.parseInt(parts[3]);
                Integer id = Text.parseInt(parts[4]);
                Integer data = Text.parseInt(parts[5]);
                if (world == null || x == null || y == null || z == null || id == null || data == null) {
                    continue;
                }
                world.getBlockAt(x, y, z).setTypeIdAndData(id, data.byteValue(), false);
                restored++;
            }
        }
        structures.clear();
        plugin.data().saveAsync();
        plugin.getLogger().info("Totem resté en place après un arrêt du serveur : " + restored + " bloc(s) retiré(s).");
    }

    // =================================================================== Accès

    public Collection<TotemSession> sessions() {
        return sessions.values();
    }

    /** @return le totem en cours portant cet identifiant, ou null */
    public TotemSession get(String id) {
        return id == null ? null : sessions.get(id.toLowerCase(Locale.ROOT));
    }

    /** @return le premier totem en cours, ou null */
    public TotemSession getActive() {
        for (TotemSession session : sessions.values()) {
            return session;
        }
        return null;
    }

    public boolean isRunning() {
        return !sessions.isEmpty();
    }

    /** @return le totem en cours dont un bloc se trouve à cette position, ou null */
    public TotemSession sessionAt(Location location) {
        for (TotemSession session : sessions.values()) {
            if (session.indexOf(location) >= 0) {
                return session;
            }
        }
        return null;
    }

    /** @return le totem en cours dans le rayon de protection de cette position, ou null */
    public TotemSession protectedAt(Location location) {
        int radius = plugin.settings().protectRadius;
        if (radius <= 0 || location == null || location.getWorld() == null) {
            return null;
        }
        for (TotemSession session : sessions.values()) {
            TotemDefinition def = session.getDefinition();
            if (!location.getWorld().getName().equals(def.getWorldName())) {
                continue;
            }
            int dx = Math.abs(location.getBlockX() - def.getX());
            int dz = Math.abs(location.getBlockZ() - def.getZ());
            int dy = location.getBlockY() - def.getY();
            if (dx <= radius && dz <= radius && dy >= -radius && dy <= def.getHeight() + radius) {
                return session;
            }
        }
        return null;
    }

    /** @return le prochain totem prévu au planning, ou null */
    public TotemDefinition nextScheduled() {
        if (!plugin.settings().scheduleEnabled) {
            return null;
        }
        ZonedDateTime now = ZonedDateTime.now(plugin.settings().zone);
        TotemDefinition best = null;
        long bestMillis = Long.MAX_VALUE;
        for (TotemDefinition def : plugin.catalog().all()) {
            long millis = def.getSchedule().millisUntilNext(now);
            if (millis >= 0 && millis < bestMillis) {
                bestMillis = millis;
                best = def;
            }
        }
        return best;
    }

    /** @return les millisecondes avant le prochain totem prévu, ou -1 */
    public long nextScheduledMillis() {
        TotemDefinition next = nextScheduled();
        return next == null ? -1L : next.getSchedule().millisUntilNext(ZonedDateTime.now(plugin.settings().zone));
    }

    // ================================================================ Démarrage

    /**
     * Fait apparaître un totem.
     *
     * @param force ignorer la limite d'un seul évènement à la fois
     */
    public Result start(String id, boolean force) {
        TotemDefinition def = plugin.catalog().get(id);
        if (def == null) {
            return Result.INCONNU;
        }
        if (sessions.containsKey(def.getId())) {
            return Result.DEJA_EN_COURS;
        }
        if (!force && plugin.settings().onlyOneAtATime && !sessions.isEmpty()) {
            return Result.AUTRE_EN_COURS;
        }
        if (!def.isPlayable()) {
            return Result.POSITION_INVALIDE;
        }
        TotemStartEvent event = new TotemStartEvent(def);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Result.ANNULE;
        }
        TotemSession session = new TotemSession(def, def.getBlocks());
        List<String> saved = new ArrayList<String>();
        for (Location location : session.getBlocks()) {
            Block block = location.getBlock();
            int[] original = {block.getTypeId(), block.getData()};
            session.getOriginals().add(original);
            saved.add(location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";"
                    + location.getBlockZ() + ";" + original[0] + ";" + original[1]);
        }
        plugin.data().meta().getStructures().put(def.getId(), saved);
        build(session);
        sessions.put(def.getId(), session);
        plugin.data().saveAsync();

        announce(session, "totem.start", plugin.display().vars(session));
        for (Player player : Bukkit.getOnlinePlayers()) {
            Sounds.play(player, plugin.settings().sound("demarrage"));
        }
        plugin.guiHook().update(session);
        plugin.display().updateSidebar();
        return Result.OK;
    }

    /** Arrête un totem sans vainqueur. @return false s'il n'était pas en cours */
    public boolean stopTotem(String id) {
        TotemSession session = get(id);
        if (session == null) {
            return false;
        }
        end(session, "totem.stopped", false);
        return true;
    }

    /** Pose (ou repose) tous les blocs du totem. */
    private void build(TotemSession session) {
        TotemDefinition def = session.getDefinition();
        for (Location location : session.getBlocks()) {
            location.getBlock().setTypeIdAndData(def.getMaterialId(), (byte) def.getMaterialData(), false);
        }
    }

    /** Remet chaque emplacement dans son état d'avant l'évènement. */
    private void remove(TotemSession session) {
        List<Location> blocks = session.getBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            int[] original = i < session.getOriginals().size() ? session.getOriginals().get(i) : new int[]{0, 0};
            blocks.get(i).getBlock().setTypeIdAndData(original[0], (byte) original[1], false);
        }
        plugin.data().meta().getStructures().remove(session.getDefinition().getId());
    }

    // ================================================================== Boucle

    private void tick() {
        ZonedDateTime now = ZonedDateTime.now(plugin.settings().zone);
        String minute = now.getYear() + "-" + now.getDayOfYear() + "-" + now.getHour() + "-" + now.getMinute();
        if (!minute.equals(lastMinute)) {
            lastMinute = minute;
            onMinute(now);
        }
        for (TotemSession session : new ArrayList<TotemSession>(sessions.values())) {
            if (session.getDefinition().getWorld() == null) {
                end(session, "totem.stopped", false);
            } else if (session.isExpired()) {
                end(session, "totem.timeout", false);
            } else {
                plugin.display().update(session);
                plugin.guiHook().update(session);
            }
        }
        plugin.display().updateSidebar();
    }

    /** Démarrages planifiés et annonces avant l'apparition. */
    private void onMinute(ZonedDateTime now) {
        if (!plugin.settings().scheduleEnabled) {
            return;
        }
        for (TotemDefinition def : plugin.catalog().all()) {
            if (!def.getSchedule().isEnabled()) {
                continue;
            }
            if (def.getSchedule().matches(now)) {
                Result result = start(def.getId(), false);
                if (result != Result.OK) {
                    plugin.getLogger().info("Totem planifié " + def.getId() + " non démarré : " + result.name());
                }
                continue;
            }
            long millis = def.getSchedule().millisUntilNext(now);
            if (millis < 0) {
                continue;
            }
            int minutes = (int) Math.round(millis / 60000.0);
            if (plugin.settings().warnMinutes.contains(minutes)) {
                plugin.msg().broadcast("totem.warn", "totem", Text.color(def.getName()), "id", def.getId(),
                        "minutes", minutes, "temps", plugin.msg().duration(millis),
                        "x", def.getX(), "y", def.getY(), "z", def.getZ(), "monde", def.getWorldName());
            }
        }
    }

    // ============================================================ Blocs cassés

    /**
     * Un joueur casse un bloc du totem. Le bloc est retiré sans rien lâcher, et le
     * totem se reconstruit si une autre faction reprend la destruction (option
     * reconstruction).
     */
    public BreakResult breakBlock(TotemSession session, Player player, Block block) {
        int index = session.indexOf(block.getLocation());
        if (index < 0 || !session.isStanding(index)) {
            return BreakResult.REFUSE;
        }
        if (plugin.settings().isIgnored(player)) {
            return BreakResult.REFUSE;
        }
        String factionId = plugin.factionHook().getFactionId(player);
        if (factionId == null && !plugin.settings().soloAllowed) {
            return BreakResult.REFUSE;
        }
        TotemBreakEvent event = new TotemBreakEvent(session.getDefinition(), player, factionId, block);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return BreakResult.ANNULE;
        }
        TotemDefinition def = session.getDefinition();
        String key = factionId == null ? TotemSession.SOLO + player.getUniqueId() : factionId;
        String name = factionId == null ? player.getName() : plugin.factionHook().getTag(factionId);
        boolean newAttacker = !key.equals(session.getCurrent());

        if (newAttacker && def.isRebuild() && session.getCurrent() != null && session.getRemaining() < session.getTotal()) {
            session.rebuild();
            build(session);
            announce(session, "totem.rebuilt", append(plugin.display().vars(session), "ancienne", session.getCurrentName(),
                    "nouvelle", name));
            for (Player p : plugin.display().audience(session)) {
                Sounds.play(p, plugin.settings().sound("reconstruction"));
            }
        }
        session.setCurrent(key, name);
        session.breakBlock(index);
        session.addBlock(player.getUniqueId(), player.getName(), key, name);
        block.setType(org.bukkit.Material.AIR);
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, def.getMaterialId());
        if (factionId != null) {
            plugin.data().faction(factionId, name).addBlocks(1);
        }

        Object[] vars = plugin.display().vars(session);
        if (newAttacker) {
            announce(session, "totem.attack", vars);
            for (Player p : plugin.display().audience(session)) {
                Sounds.play(p, plugin.settings().sound("attaque"));
            }
        }
        plugin.msg().send(player, "totem.block-broken", vars);
        Sounds.play(player, plugin.settings().sound("bloc"));
        int remaining = session.getRemaining();
        if (remaining > 0 && plugin.settings().announceBlocks.contains(remaining) && session.announceOnce(remaining)) {
            announce(session, "totem.remaining", vars);
            for (Player p : Bukkit.getOnlinePlayers()) {
                Sounds.play(p, plugin.settings().sound("palier"));
            }
        }
        plugin.guiHook().update(session);
        if (remaining <= 0) {
            destroy(session, player);
        }
        return BreakResult.COMPTE;
    }

    // ================================================================ Victoire

    private void destroy(TotemSession session, Player lastBreaker) {
        TotemDefinition def = session.getDefinition();
        String winnerKey = def.getWinMode() == WinMode.PLUS_DE_BLOCS && session.getBestGroup() != null
                ? session.getBestGroup() : session.getCurrent();
        boolean solo = winnerKey.startsWith(TotemSession.SOLO);
        String factionId = solo ? null : winnerKey;
        String name = session.getGroupName(winnerKey);
        if (name == null) {
            name = solo ? lastBreaker.getName() : plugin.factionHook().getTag(factionId);
        }
        UUID best = session.getBestPlayer();
        List<UUID> winners = recipients(session, winnerKey, lastBreaker);

        if (factionId != null) {
            plugin.data().faction(factionId, name).addWin(def.getId());
        }
        plugin.data().player(lastBreaker.getUniqueId(), lastBreaker.getName()).addFinalBlow();
        plugin.data().meta().setLast(def.getId(), new MetaState.Winner(factionId, name, lastBreaker.getName()));

        Object[] vars = append(plugin.display().vars(session), "faction", name, "gagnant", name,
                "joueur", lastBreaker.getName(), "destructeur", lastBreaker.getName(),
                "meilleur", best == null ? plugin.msg().text("values.nobody") : session.getName(best),
                "meilleur_blocs", best == null ? 0 : session.getBlocks(best),
                "blocs_faction", session.getGroupBlocks(winnerKey));
        long points = factionId == null ? 0 : plugin.factionHook().addPoints(factionId, def.getVictoryPoints());
        points += plugin.rewards().grant(factionId, winners, def.getWinnerRewards(), vars);
        if (!def.getParticipantRewards().isEmpty()) {
            List<UUID> others = new ArrayList<UUID>();
            for (UUID uuid : session.participants(plugin.settings().participationBlocks)) {
                if (!winners.contains(uuid)) {
                    others.add(uuid);
                }
            }
            plugin.rewards().grant(null, others, def.getParticipantRewards(), vars);
        }
        announce(session, "totem.win", append(vars, "points", points));
        for (Player player : Bukkit.getOnlinePlayers()) {
            Sounds.play(player, plugin.settings().sound("victoire"));
        }
        fireworks(def);
        plugin.questHook().onWin(def.getId(), winners);
        Bukkit.getPluginManager().callEvent(new TotemDestroyEvent(def, factionId, name, lastBreaker.getUniqueId(), points));
        end(session, null, true);
    }

    /** Joueurs qui reçoivent les récompenses du vainqueur, selon la cible configurée. */
    private List<UUID> recipients(TotemSession session, String winnerKey, Player lastBreaker) {
        List<UUID> out = new ArrayList<UUID>();
        Target target = session.getDefinition().getTarget();
        if (target == Target.AUCUNE) {
            return out;
        }
        boolean solo = winnerKey.startsWith(TotemSession.SOLO);
        if (solo) {
            out.add(UUID.fromString(winnerKey.substring(TotemSession.SOLO.length())));
            return out;
        }
        if (target == Target.DESTRUCTEUR) {
            if (winnerKey.equals(session.getGroup(lastBreaker.getUniqueId()))) {
                out.add(lastBreaker.getUniqueId());
            }
        } else if (target == Target.PARTICIPANTS) {
            for (UUID uuid : session.getPlayerBlocks().keySet()) {
                if (winnerKey.equals(session.getGroup(uuid))) {
                    out.add(uuid);
                }
            }
        } else if (target == Target.MEMBRES_EN_LIGNE) {
            for (Player player : plugin.factionHook().getOnlineMembers(winnerKey)) {
                out.add(player.getUniqueId());
            }
        } else if (target == Target.MEMBRES) {
            out.addAll(plugin.factionHook().getMembers(winnerKey));
        }
        if (out.isEmpty()) {
            // repli : le meilleur casseur de la faction gagnante
            UUID best = null;
            int count = 0;
            for (Map.Entry<UUID, Integer> entry : session.getPlayerBlocks().entrySet()) {
                if (winnerKey.equals(session.getGroup(entry.getKey())) && entry.getValue() > count) {
                    best = entry.getKey();
                    count = entry.getValue();
                }
            }
            if (best != null) {
                out.add(best);
            }
        }
        return out;
    }

    /**
     * Termine un évènement et retire le totem.
     *
     * @param messageKey message diffusé (null après une victoire, déjà annoncée)
     * @param destroyed  true si le totem a été détruit
     */
    private void end(TotemSession session, String messageKey, boolean destroyed) {
        sessions.remove(session.getDefinition().getId());
        remove(session);
        int min = plugin.settings().participationBlocks;
        for (Map.Entry<UUID, Integer> entry : session.getPlayerBlocks().entrySet()) {
            PlayerStats stats = plugin.data().player(entry.getKey(), session.getName(entry.getKey()));
            stats.addBlocks(entry.getValue());
            if (entry.getValue() >= min) {
                stats.addParticipation();
            }
        }
        if (messageKey != null) {
            announce(session, messageKey, plugin.display().vars(session));
            for (Player player : Bukkit.getOnlinePlayers()) {
                Sounds.play(player, plugin.settings().sound("fin"));
            }
        }
        plugin.guiHook().clear(session.getDefinition());
        Bukkit.getPluginManager().callEvent(new TotemEndEvent(session.getDefinition(), destroyed));
        plugin.display().updateSidebar();
        plugin.data().saveAsync();
    }

    /**
     * Annonce un message : à tout le serveur si evenement.annonce-globale est actif,
     * sinon seulement aux joueurs proches du totem.
     */
    private void announce(TotemSession session, String key, Object... kv) {
        if (plugin.settings().broadcast) {
            plugin.msg().broadcast(key, kv);
            return;
        }
        for (Player player : plugin.display().audience(session)) {
            plugin.msg().send(player, key, kv);
        }
    }

    private void fireworks(TotemDefinition def) {
        if (!plugin.settings().fireworks) {
            return;
        }
        Location base = def.getBase();
        if (base == null) {
            return;
        }
        Location top = base.clone().add(0.5, def.getHeight(), 0.5);
        for (int i = 0; i < 3; i++) {
            Firework firework = top.getWorld().spawn(top, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().withColor(Color.RED, Color.ORANGE)
                    .with(FireworkEffect.Type.BALL_LARGE).flicker(true).trail(true).build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        }
    }

    /** Variables en plus (placées en tête : elles remplacent celles de même nom). */
    private static Object[] append(Object[] kv, Object... extra) {
        Object[] all = new Object[kv.length + extra.length];
        System.arraycopy(extra, 0, all, 0, extra.length);
        System.arraycopy(kv, 0, all, extra.length, kv.length);
        return all;
    }
}
