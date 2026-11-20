package com.modernarmory.gun;

import net.minecraft.sounds.SoundEvent;

/**
 * What a {@link Feed} may do while reloading one gun in one shooter's hands. Implemented by the
 * server's GunHandler, so feed strategies stay free of networking and inventory details.
 */
public interface ReloadContext {
    /** Rounds loaded right now (magazine / belt / tube + chamber). */
    int loaded();

    void setLoaded(int rounds);

    /** Magazine / belt / tube capacity with the fitted attachments. */
    int magazineSize();

    /** Rounds the shooter can load (Integer.MAX_VALUE with infinite ammo). */
    int available();

    /** Removes up to {@code rounds} from the shooter's ammo; returns how many were taken. */
    int take(int rounds);

    /** Length of the tactical ({@code empty = false}) or empty reload / reload step, ticks (attachments + mastery applied). */
    int reloadTicks(boolean empty);

    /** The gun's sounds. */
    SoundSet sounds();

    /** Plays a reload sound on the shooter and starts a gun animation (restart = replay it even if it is still running). */
    void cue(SoundEvent sound, String animation, boolean restart);

    /** Tell the shooter there is nothing to load. */
    void noAmmo();
}
