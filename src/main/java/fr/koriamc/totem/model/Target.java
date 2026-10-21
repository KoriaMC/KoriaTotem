package fr.koriamc.totem.model;

import java.util.Locale;

/**
 * Joueurs qui reçoivent les récompenses personnelles d'une victoire.
 * Les points de faction et les annonces sont toujours donnés une seule fois.
 */
public enum Target {

    /** Seul le joueur qui a cassé le dernier bloc. */
    DESTRUCTEUR,
    /** Les membres de la faction gagnante qui ont cassé au moins un bloc. */
    PARTICIPANTS,
    /** Les membres connectés au moment de la victoire. */
    MEMBRES_EN_LIGNE,
    /** Tous les membres ; les absents reçoivent leur récompense à leur connexion. */
    MEMBRES,
    /** Personne : uniquement les points de faction et les annonces. */
    AUCUNE;

    /** @return null si la valeur est inconnue */
    public static Target parse(String value, Target def) {
        if (value == null) {
            return def;
        }
        String v = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (Target target : values()) {
            if (target.name().equals(v)) {
                return target;
            }
        }
        return null;
    }
}
