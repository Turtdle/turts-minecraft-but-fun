package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;

import java.util.Map;

import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.item.GunItem;

import net.minecraft.client.Minecraft;

/**
 * Iron sights under optics ("ironsights" section): guns whose front sight used to stay up behind a red dot (AK-47,
 * M16A4, MP5, Vector, M249) get one fitted and are photographed aimed and from the hip: both iron sights must be
 * folded away (PartVisibility.IRON_SIGHTS), the M16A4 keeping its front sight base.
 */
final class AutoTestIronSights {
    private static final String[] GUNS = {"ak47", "m16a4", "mp5", "vector", "m249", "aa12", "saiga12"};
    private static final int EACH = 40;
    static final int LENGTH = 10 + GUNS.length * EACH;

    static void script(int t0) {
        int t = t0 + 10;
        for (String gun : GUNS) {
            at(t, () -> {
                cmd("clear @s"); cmd("gamemode creative"); cmd("give @s modernarmory:" + gun);
                cmd("fill -3 -60 -6 3 -55 3 minecraft:air"); cmd("tp @s 0.5 -60 0.5 180 0");
            });
            at(t + 4, () -> {
                Minecraft.getInstance().player.getInventory().selected = 0;
                var server = Minecraft.getInstance().getSingleplayerServer();
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayers().get(0);
                    var stack = sp.getMainHandItem();
                    if (stack.getItem() instanceof GunItem g)
                        g.setAttachments(stack, new GunAttachments(Map.of(AttachmentType.OPTIC, "red_dot")));
                });
            });
            at(t + 10, () -> { look(180, 0); Minecraft.getInstance().options.keyUse.setDown(true); });
            at(t + 30, () -> shot("63_ironsights_" + gun + "_aim"));
            at(t + 31, () -> Minecraft.getInstance().options.keyUse.setDown(false));
            at(t + 37, () -> shot("63_ironsights_" + gun + "_hip"));
            t += EACH;
        }
    }

    private AutoTestIronSights() {}
}
