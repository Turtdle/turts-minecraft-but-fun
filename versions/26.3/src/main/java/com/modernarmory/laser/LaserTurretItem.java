package com.modernarmory.laser;

import java.util.function.Consumer;

import com.modernarmory.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The HELWS laser turret, carried as one item. Used on the ground it places the whole emplacement with its
 * operator's seat on the block aimed at and the turret beyond it, facing away from the player, if it fits (TurretLayout.check); while held, a
 * hologram shows where it would go and what is in the way (client.LaserHologram).
 */
public class LaserTurretItem extends Item {
    public LaserTurretItem(Properties properties) {
        super(properties);
    }

    /** The block the seat row goes on, for a click on {@code clicked}'s {@code face}. */
    public static BlockPos target(Level level, BlockPos clicked, Direction face) {
        return level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        Direction facing = ctx.getHorizontalDirection();
        TurretLayout.Placement p = TurretLayout.check(level, target(level, ctx.getClickedPos(), ctx.getClickedFace()), facing, player);
        if (!p.ok()) {
            if (!level.isClientSide() && player != null) player.sendOverlayMessage(Component.literal(p.message()).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            LaserTurretBlock.build(level, p.core(), facing, ModBlocks.LASER_TURRET.get());
            level.playSound(null, p.core(), SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f);
            if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS; // 26.x: no sidedSuccess
    }

    @SuppressWarnings("deprecation") // deprecated in 26.3 but still called by ItemStack when building tooltips
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("10 kW laser air-defence turret: burns drones out of the sky.").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Place on flat ground (3 wide, 4 deep, 3 tall; a hologram shows the fit). Right-click it to take the seat.")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("Left click: lock the drone nearest the crosshair and fire (it tracks by itself). Right click: cease. Sneak: leave.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
