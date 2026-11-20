package com.modernarmory.block;

import com.modernarmory.menu.GunsmithMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A workbench for fitting attachments, used like a crafting table. */
public class GunsmithTableBlock extends Block {
    private static final Component TITLE = Component.translatable("container.modernarmory.gunsmith");

    public GunsmithTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) player.openMenu(state.getMenuProvider(level, pos));
        return InteractionResult.SUCCESS; // 26.x: sidedSuccess() is gone, SUCCESS swings on both sides
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inv, player) -> new GunsmithMenu(id, inv, ContainerLevelAccess.create(level, pos)), TITLE);
    }
}
