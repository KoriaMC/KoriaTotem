package fr.koriamc.totem.game;

import fr.koriamc.totem.KoriaTotem;
import fr.koriamc.totem.config.Settings;
import fr.koriamc.totem.model.Schedule;
import fr.koriamc.totem.model.TotemDefinition;
import fr.koriamc.totem.model.TotemSession;
import fr.koriamc.totem.util.Items;
import fr.koriamc.totem.util.Sidebar;
import fr.koriamc.totem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Affichage de l'évènement en cours : barre de progression, blocs restants,
 * faction qui casse et scoreboard.
 */
public final class Display {

    private final KoriaTotem plugin;
    private final Sidebar sidebar = new Sidebar("koriatotem");

    public Display(KoriaTotem plugin) {
        this.plugin = plugin;
    }

    /** Barre de progression de la destruction ("|||||-----"). */
    public String bar(double progress) {
        Settings s = plugin.settings();
        int length = s.barLength;
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * length);
        StringBuilder sb = new StringBuilder(s.barFull);
        for (int i = 0; i < length; i++) {
            if (i == filled) {
                sb.append(s.barEmpty);
            }
            sb.append(s.barSymbol);
        }
        return Text.color(sb.toString());
    }

    /** Temps au format m:ss. */
    public String time(long seconds) {
        long value = Math.max(0, seconds);
        return (value / 60) + ":" + (value % 60 < 10 ? "0" : "") + (value % 60);
    }

    public String percent(double progress) {
        return Text.number(Math.max(0, Math.min(1, progress)) * 100, plugin.settings().percentDecimals);
    }

    /** Planning lisible : "Samedi, Dimanche à 18:00, 21:00". */
    public String schedule(Schedule schedule) {
        if (!schedule.isEnabled()) {
            return plugin.msg().text("values.none");
        }
        List<String> days = new ArrayList<String>();
        for (DayOfWeek day : schedule.getDays()) {
            days.add(Items.capitalize(day.getDisplayName(TextStyle.FULL, Locale.FRENCH)));
        }
        List<String> hours = new ArrayList<String>();
        for (LocalTime time : schedule.getTimes()) {
            hours.add((time.getHour() < 10 ? "0" : "") + time.getHour()
                    + ":" + (time.getMinute() < 10 ? "0" : "") + time.getMinute());
        }
        return Text.join(days, ", ") + " " + plugin.msg().text("planning.at") + " " + Text.join(hours, ", ");
    }

    /** Variables communes d'un évènement, utilisables dans tous les messages et le scoreboard. */
    public Object[] vars(TotemSession session) {
        TotemDefinition def = session.getDefinition();
        String nobody = plugin.msg().text("values.nobody");
        UUID last = session.getLastBreaker();
        UUID best = session.getBestPlayer();
        int maxMinutes = def.getMaxDurationMinutes();
        long left = maxMinutes <= 0 ? -1 : maxMinutes * 60L - session.elapsedMillis() / 1000L;
        return new Object[]{
                "totem", Text.color(def.getName()),
                "id", def.getId(),
                "monde", def.getWorldName(),
                "x", def.getX(),
                "y", def.getY(),
                "z", def.getZ(),
                "restants", session.getRemaining(),
                "total", session.getTotal(),
                "casses", session.getTotal() - session.getRemaining(),
                "barre", bar(session.getProgress()),
                "pourcentage", percent(session.getProgress()),
                "faction", session.getCurrentName() == null ? nobody : session.getCurrentName(),
                "joueur", last == null ? nobody : session.getName(last),
                "meilleur", best == null ? nobody : session.getName(best),
                "meilleur_blocs", best == null ? 0 : session.getBlocks(best),
                "points", def.getVictoryPoints(),
                "reconstructions", session.getRebuilds(),
                "duree", plugin.msg().duration(session.elapsedMillis()),
                "temps", left < 0 ? plugin.msg().text("values.unlimited") : time(left),
                "etat", state(session)
        };
    }

    /** Texte d'état : intact ou en train d'être cassé par une faction. */
    public String state(TotemSession session) {
        if (session.getCurrentName() == null) {
            return plugin.msg().text("display.state-intact");
        }
        return plugin.msg().text("display.state-attacked", "faction", session.getCurrentName());
    }

    /** Envoie la barre de progression aux destinataires configurés. */
    public void update(TotemSession session) {
        if (plugin.settings().audience == Settings.Audience.AUCUN) {
            return;
        }
        Object[] vars = vars(session);
        for (Player player : audience(session)) {
            plugin.msg().send(player, "display.actionbar", vars);
        }
    }

    /**
     * Scoreboard latéral de l'évènement en cours (messages.json : scoreboard), affiché à tous les
     * joueurs connectés ; retiré quand aucun totem n'est en cours.
     */
    public void updateSidebar() {
        TotemSession session = plugin.totems().getActive();
        if (session == null || !plugin.settings().scoreboard) {
            if (sidebar.isShown()) {
                sidebar.hide();
            }
            return;
        }
        Object[] vars = vars(session);
        sidebar.show(Bukkit.getOnlinePlayers(), plugin.msg().text("scoreboard.title", vars),
                plugin.msg().lines("scoreboard.lines", vars));
    }

    /** Retire le scoreboard (arrêt du plugin). */
    public void hideSidebar() {
        sidebar.hide();
    }

    /** Joueurs qui reçoivent l'affichage, selon affichage.destinataires. */
    public List<Player> audience(TotemSession session) {
        List<Player> list = new ArrayList<Player>();
        Settings s = plugin.settings();
        if (s.audience == Settings.Audience.TOUS) {
            list.addAll(Bukkit.getOnlinePlayers());
            return list;
        }
        Location base = session.getDefinition().getBase();
        World world = base == null ? null : base.getWorld();
        if (world == null) {
            return list;
        }
        double radius = s.nearRadius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(base) <= radius * radius) {
                list.add(player);
            }
        }
        return list;
    }
}
