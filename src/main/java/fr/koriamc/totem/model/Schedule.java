package fr.koriamc.totem.model;

import org.bukkit.configuration.ConfigurationSection;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Planning d'un evenement : jours de la semaine et heures de demarrage.
 * "TOUS" dans les jours signifie tous les jours.
 */
public final class Schedule {

    private static final String[] FRENCH_DAYS = {"LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE"};

    private final boolean enabled;
    private final Set<DayOfWeek> days;
    private final List<LocalTime> times;

    private Schedule(boolean enabled, Set<DayOfWeek> days, List<LocalTime> times) {
        this.enabled = enabled;
        this.days = days;
        this.times = times;
    }

    public static Schedule empty() {
        return new Schedule(false, EnumSet.noneOf(DayOfWeek.class), Collections.<LocalTime>emptyList());
    }

    /**
     * Lit la section "planning".
     *
     * @param where    chemin affiche dans les problemes signales
     * @param problems liste alimentee avec les erreurs rencontrees
     */
    public static Schedule read(ConfigurationSection s, String where, List<String> problems) {
        if (s == null) {
            return empty();
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String raw : s.getStringList("jours")) {
            String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (value.equals("TOUS") || value.equals("TOUT")) {
                days.addAll(EnumSet.allOf(DayOfWeek.class));
                continue;
            }
            DayOfWeek day = parseDay(value);
            if (day == null) {
                problems.add(where + ".jours : jour inconnu " + raw);
            } else {
                days.add(day);
            }
        }
        List<LocalTime> times = new ArrayList<LocalTime>();
        for (String raw : s.getStringList("heures")) {
            LocalTime time = parseTime(raw);
            if (time == null) {
                problems.add(where + ".heures : heure invalide " + raw + " (format HH:mm)");
            } else {
                times.add(time);
            }
        }
        Collections.sort(times);
        boolean enabled = !s.contains("actif") || s.getBoolean("actif");
        return new Schedule(enabled && !days.isEmpty() && !times.isEmpty(), days, times);
    }

    private static DayOfWeek parseDay(String value) {
        for (int i = 0; i < FRENCH_DAYS.length; i++) {
            if (FRENCH_DAYS[i].equals(value)) {
                return DayOfWeek.of(i + 1);
            }
        }
        try {
            return DayOfWeek.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.length() == 4) {
            v = "0" + v;
        }
        try {
            return LocalTime.parse(v);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Un planning sans jour ni heure ne demarre jamais l'evenement. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Ce moment precis (a la minute) est-il une heure de demarrage ? */
    public boolean matches(ZonedDateTime now) {
        if (!enabled || !days.contains(now.getDayOfWeek())) {
            return false;
        }
        for (LocalTime time : times) {
            if (time.getHour() == now.getHour() && time.getMinute() == now.getMinute()) {
                return true;
            }
        }
        return false;
    }

    /** @return le prochain demarrage prevu, ou null si le planning est vide */
    public ZonedDateTime next(ZonedDateTime now) {
        if (!enabled) {
            return null;
        }
        for (int day = 0; day <= 7; day++) {
            ZonedDateTime base = now.plusDays(day);
            if (!days.contains(base.getDayOfWeek())) {
                continue;
            }
            for (LocalTime time : times) {
                ZonedDateTime candidate = base.withHour(time.getHour()).withMinute(time.getMinute())
                        .withSecond(0).withNano(0);
                if (candidate.isAfter(now)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** @return les millisecondes avant le prochain demarrage, ou -1 s'il n'y en a pas */
    public long millisUntilNext(ZonedDateTime now) {
        ZonedDateTime next = next(now);
        return next == null ? -1L : next.toInstant().toEpochMilli() - now.toInstant().toEpochMilli();
    }

    public Set<DayOfWeek> getDays() {
        return days;
    }

    public List<LocalTime> getTimes() {
        return times;
    }
}
