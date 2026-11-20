package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;
import static com.modernarmory.client.AutoTest.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.Attachment;
import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.Attachments;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.gun.GunModels;
import com.modernarmory.gun.GunDefinition;
import com.modernarmory.gun.Guns;
import com.modernarmory.item.GunItem;
import com.modernarmory.registry.ModItems;


/**
 * Attachment fit ("attfit" section): for every gun, which attachments fit (slot + calibre + the model has it,
 * the Gunsmith's rule); fails if a gun's model carries an attachment the gun has no slot for, or a slot nothing
 * fits. Then fits the newly opened ones (AK red dot, M14 bipod, Vector grip) and photographs them.
 */
final class AutoTestAttachFit {
    static final int LENGTH = 130;
    private static final String[][] SHOTS = {{"ak47", "OPTIC", "red_dot"}, {"m14", "UNDERBARREL", "bipod"}, {"vector", "UNDERBARREL", "vertical_grip"}};

    static void script(int t0) {
        at(t0 + 2, () -> {
            List<String> problems = new ArrayList<>();
            for (GunDefinition g : Guns.all()) {
                StringBuilder fits = new StringBuilder();
                for (Attachment a : Attachments.ALL.values()) {
                    boolean model = GunModels.hasAttachment(g.modelId(), a.id());
                    boolean slot = g.spec().slots().contains(a.type());
                    boolean calibre = a.caliber() == null || a.caliber() == g.spec().cartridge().caliberClass();
                    if (model && slot && calibre) fits.append(' ').append(a.id());
                    if (model && !slot) problems.add(g.id() + ": model has " + a.id() + " but no " + a.type() + " slot");
                }
                for (AttachmentType t : g.spec().slots()) {
                    boolean any = false;
                    for (Attachment a : Attachments.ALL.values())
                        any |= a.type() == t && GunModels.hasAttachment(g.modelId(), a.id())
                                && (a.caliber() == null || a.caliber() == g.spec().cartridge().caliberClass());
                    if (!any) problems.add(g.id() + ": " + t + " slot but nothing fits");
                }
                ModernArmory.LOGGER.info("[autotest] attfit {}:{}", g.id(), fits);
            }
            ModernArmory.LOGGER.info("[autotest] attfit: {} problem(s) {} -> {}", problems.size(), problems, problems.isEmpty() ? "PASS" : "FAIL");
        });
        int t = t0 + 10;
        for (String[] s : SHOTS) {
            String gun = s[0], slot = s[1], att = s[2];
            at(t, () -> {
                cmd("clear @s"); cmd("gamemode creative"); cmd("give @s modernarmory:" + gun);
                cmd("fill -3 -60 -6 3 -55 3 minecraft:air"); cmd("tp @s 0.5 -60 0.5 180 0");
            });
            at(t + 4, () -> {
                mc().player.getInventory().setSelectedSlot(0);
                var server = mc().getSingleplayerServer();
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayers().get(0);
                    var stack = sp.getMainHandItem();
                    if (stack.getItem() instanceof GunItem g)
                        g.setAttachments(stack, new GunAttachments(Map.of(AttachmentType.valueOf(slot), att)));
                });
            });
            at(t + 10, () -> { look(180, 0); mc().options.keyUse.setDown(true); });
            at(t + 30, () -> shot("58_" + gun + "_" + att + "_aim"));
            at(t + 31, () -> mc().options.keyUse.setDown(false));
            at(t + 36, () -> shot("58_" + gun + "_" + att + "_hip"));
            t += 40;
        }
    }

    private AutoTestAttachFit() {}
}
