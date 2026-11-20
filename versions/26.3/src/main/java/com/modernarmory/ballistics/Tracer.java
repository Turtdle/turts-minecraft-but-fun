package com.modernarmory.ballistics;

/**
 * A cartridge's tracer variant: same ballistics, plus a pyrotechnic charge in the bullet's base that
 * burns brightly for part of the flight, so the shooter can see where rounds go. Declared on the ball
 * cartridge in {@link Cartridges} (e.g. 5.56 M855 -> M856 tracer) and carried as its own ammo item.
 *
 * @param designation military designation, e.g. "M856"
 * @param name        display name of the ammo item
 * @param ammoItemId  registry path of the tracer ammo item
 * @param rgb         glow colour (0xRRGGBB), roughly what the compound burns as
 * @param burnoutM    distance (m) after which the tracer has burnt out and the round goes dark
 */
public record Tracer(String designation, String name, String ammoItemId, int rgb, double burnoutM) {}
