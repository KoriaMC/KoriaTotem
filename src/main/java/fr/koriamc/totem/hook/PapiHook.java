package fr.koriamc.totem.hook;

import fr.koriamc.totem.KoriaTotem;

/**
 * Pont vers PlaceholderAPI, chargé uniquement si le plugin est installé.
 */
public final class PapiHook {

    private final TotemExpansion expansion;

    public PapiHook(KoriaTotem plugin) {
        this.expansion = new TotemExpansion(plugin);
        this.expansion.register();
    }

    public void unregister() {
        try {
            expansion.unregister();
        } catch (Throwable ignored) {
            // PlaceholderAPI déjà arrêté
        }
    }
}
