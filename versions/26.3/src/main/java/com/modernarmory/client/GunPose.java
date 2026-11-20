package com.modernarmory.client;

import com.modernarmory.item.GunItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Third person: guns are held with both hands, shouldered at eye level, and the body faces where the head looks
 * (the two-handed pose points the arms along the look, so a body turned away from it would swing the gun through
 * the arms).
 *
 * <p>26.x builds a render state before anything is drawn, and GeckoLib poses worn armour (kevlar) from it right
 * then; changing the state later (RenderPlayerEvent) would move the body but leave armour arms hanging and twist
 * helmets. So both are set before the state is built: the arm pose through the gun items' client extension (asked
 * while the state is extracted), the body turn on the player entity at the start of each frame.
 */
public final class GunPose {
    /** The crossbow hold for hip and aim alike: both arms at eye level following the look, the hands on the gun. */
    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return hand == InteractionHand.MAIN_HAND ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
        }
    };

    /** Before the frame's render states are built: everyone holding a gun turns their body to their head. */
    static void onFramePre(RenderFrameEvent.Pre event) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Player p : level.players()) {
            if (!(p.getMainHandItem().getItem() instanceof GunItem)) continue;
            p.yBodyRot = p.yHeadRot;
            p.yBodyRotO = p.yHeadRotO;
        }
    }

    private GunPose() {}
}
