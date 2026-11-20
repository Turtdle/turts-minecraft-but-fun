package com.modernarmory.drone;

/**
 * The pilot's input in acro (rate) mode, for one tick. Nothing here holds an attitude: input only says how
 * fast to rotate, and the drone stays at whatever angle it was left at. Two kinds of input add up: sticks
 * (-1..1 of the type's rate, through the expo curve) and direct rotation in degrees this tick (the mouse).
 *
 * @param pitch    pitch stick -1..1 (+ = nose down)
 * @param roll     roll stick -1..1 (+ = right side down)
 * @param yaw      yaw stick -1..1 (+ = turn right)
 * @param pitchDeg pitch this tick, degrees (+ = nose down; mouse up / down)
 * @param rollDeg  roll this tick, degrees (+ = right side down; mouse right / left)
 * @param yawDeg   yaw this tick, degrees (+ = right)
 * @param throttle collective thrust 0..1 of the motors' maximum (the stick stays where it is left)
 * @param boost    high rates (flips)
 * @param gimbal   camera pitch for gimballed cameras, degrees (+ = down)
 */
public record AcroSticks(float pitch, float roll, float yaw, float pitchDeg, float rollDeg, float yawDeg, float throttle,
                         boolean boost, float gimbal) {}
