package fr.koriamc.totem.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scoreboard latéral d'un évènement, sans scintillement : chaque ligne est une
 * entrée invisible dont le texte est porté par le préfixe et le suffixe d'une
 * équipe (16 + 16 caractères en 1.8).
 * <p>
 * Le scoreboard actuel de chaque joueur est réutilisé (celui du serveur ou celui
 * d'un autre plugin) : les équipes et objectifs existants sont conservés, et
 * l'objectif affiché avant l'évènement est remis en place à la fin.
 */
public final class Sidebar {

    private static final int MAX_LINES = 15;
    private static final int PART = 16;
    private static final int TITLE = 32;

    private final String name;
    private final Set<Scoreboard> boards = new HashSet<Scoreboard>();
    private final Map<Scoreboard, Objective> previous = new HashMap<Scoreboard, Objective>();

    /** @param name nom interne de l'objectif (16 caractères maximum, ex : koriatotem) */
    public Sidebar(String name) {
        this.name = name.length() > 12 ? name.substring(0, 12) : name;
    }

    public boolean isShown() {
        return !boards.isEmpty();
    }

    /** Affiche (ou met à jour) le scoreboard pour ces joueurs. Les lignes au-delà de 15 sont ignorées. */
    public void show(Collection<? extends Player> players, String title, List<String> lines) {
        for (Player player : players) {
            Scoreboard board = player.getScoreboard();
            if (board != null) {
                boards.add(board);
            }
        }
        String shownTitle = Text.truncate(title, TITLE);
        for (Scoreboard board : boards) {
            try {
                render(board, shownTitle, lines);
            } catch (IllegalStateException ignored) {
                // scoreboard abandonné par un autre plugin entre deux mises à jour
            }
        }
    }

    private void render(Scoreboard board, String title, List<String> lines) {
        Objective objective = board.getObjective(name);
        if (objective == null) {
            Objective shown = board.getObjective(DisplaySlot.SIDEBAR);
            if (shown != null) {
                previous.put(board, shown);
            }
            objective = board.registerNewObjective(name, "dummy");
        }
        if (!title.equals(objective.getDisplayName())) {
            objective.setDisplayName(title);
        }
        if (objective.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        int count = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = entry(i);
            if (i >= count) {
                board.resetScores(entry);
                continue;
            }
            Team team = board.getTeam(name + i);
            if (team == null) {
                team = board.registerNewTeam(name + i);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            String[] parts = split(lines.get(i) == null ? "" : lines.get(i));
            if (!parts[0].equals(team.getPrefix())) {
                team.setPrefix(parts[0]);
            }
            if (!parts[1].equals(team.getSuffix())) {
                team.setSuffix(parts[1]);
            }
            Score score = objective.getScore(entry);
            if (score.getScore() != count - i) {
                score.setScore(count - i);
            }
        }
    }

    /** Retire le scoreboard de l'évènement et remet l'objectif affiché avant lui. */
    public void hide() {
        for (Scoreboard board : boards) {
            try {
                Objective objective = board.getObjective(name);
                if (objective != null) {
                    objective.unregister();
                }
                for (int i = 0; i < MAX_LINES; i++) {
                    Team team = board.getTeam(name + i);
                    if (team != null) {
                        team.unregister();
                    }
                }
                Objective old = previous.get(board);
                if (old != null && board.getObjective(DisplaySlot.SIDEBAR) == null) {
                    old.setDisplaySlot(DisplaySlot.SIDEBAR);
                }
            } catch (IllegalStateException ignored) {
                // objectif précédent supprimé entre-temps par son plugin
            }
        }
        boards.clear();
        previous.clear();
    }

    /** Entrée invisible et unique d'une ligne ("§0§r", "§1§r"...). */
    private static String entry(int index) {
        return ChatColor.values()[index].toString() + ChatColor.RESET;
    }

    /** Découpe une ligne en préfixe et suffixe de 16 caractères en conservant la couleur. */
    static String[] split(String line) {
        if (line.length() <= PART) {
            return new String[]{line, ""};
        }
        int cut = line.charAt(PART - 1) == ChatColor.COLOR_CHAR ? PART - 1 : PART;
        String prefix = line.substring(0, cut);
        String suffix = ChatColor.getLastColors(prefix) + line.substring(cut);
        if (suffix.length() > PART) {
            suffix = suffix.substring(0, PART);
            if (suffix.charAt(PART - 1) == ChatColor.COLOR_CHAR) {
                suffix = suffix.substring(0, PART - 1);
            }
        }
        return new String[]{prefix, suffix};
    }
}
