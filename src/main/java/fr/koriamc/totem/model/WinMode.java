package fr.koriamc.totem.model;

import java.util.Locale;

/**
 * Désignation du vainqueur quand le dernier bloc du totem tombe.
 */
public enum WinMode {

    /** La faction du joueur qui casse le dernier bloc. */
    DERNIER_BLOC,
    /** La faction qui a cassé le plus de blocs pendant tout l'évènement. */
    PLUS_DE_BLOCS;

    /** Lit "DERNIER-BLOC" ou "PLUS-DE-BLOCS". @return null si la valeur est inconnue */
    public static WinMode parse(String value, WinMode def) {
        if (value == null) {
            return def;
        }
        String v = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (WinMode mode : values()) {
            if (mode.name().equals(v)) {
                return mode;
            }
        }
        return null;
    }
}
