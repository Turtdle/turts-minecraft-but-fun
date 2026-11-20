package com.modernarmory.grenade;

import java.util.List;
import java.util.function.Consumer;

import com.modernarmory.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A hand grenade, thrown with CS-style controls (GrenadeHandler): hold left or right click and the pin comes out
 * (the spoon is still held, so nothing is burning yet); let go and it is thrown, the spoon flies and the fuze
 * starts. Left = overhand, right = underhand, both together = medium. Let go before the pin is out, or switch
 * items, and nothing happens.
 */
public class GrenadeItem extends Item implements GeoItem {
    /** How long the pin pull takes (the "pull_pin" animation). */
    public static final int PIN_TICKS = 8;
    private static final String CONTROLLER = "main", ACTIONS = "actions";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final GrenadeType type;

    public GrenadeItem(GrenadeType type, Properties properties) {
        super(properties);
        this.type = type;
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public GrenadeType type() {
        return type;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatableWithFallback(getDescriptionId(stack), type.name());
    }

    /** The pin comes out (the spoon is still held, so nothing burns yet). Called by GrenadeHandler. */
    void pullPin(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player, ModSounds.GRENADE_PIN.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        triggerAnim(player, GeoItem.getOrAssignId(stack, level), ACTIONS, "pull_pin");
    }

    /** Let go before the pin was out, or switched away: the pin goes back in. */
    void cancel(ServerPlayer player, ItemStack stack) {
        stopTriggeredAnim(player, GeoItem.getOrAssignId(stack, player.serverLevel()), ACTIONS, "pull_pin");
    }

    /** Throws it: the grenade leaves the hand where the throw animation lets go of it; the spoon flies, the fuze starts. */
    void throwFrom(ServerPlayer player, ItemStack stack, ThrowKind kind) {
        ServerLevel level = player.serverLevel();
        triggerAnim(player, GeoItem.getOrAssignId(stack, level), ACTIONS, kind.animation);
        player.getCooldowns().addCooldown(this, 10);
        GrenadeThrows.schedule(level, type.releaseTicks(kind.under), () -> {
            if (!player.isAlive()) return;
            GrenadeEntity.throwFrom(player, type, kind);
            level.playSound(null, player, ModSounds.GRENADE_SPOON.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(type.description()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(String.format("Fuze %.1f s", type.fuzeSeconds())).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Hold left / right click: pin out. Release left: overhand, right: underhand, both: medium")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- GeckoLib (animations from tools/modelgen/guns/<id>.py: idle, pull_pin, hold, throw, throw_under)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, 2, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, ACTIONS, 1, state -> PlayState.STOP)
                .triggerableAnim("pull_pin", RawAnimation.begin().thenPlay("pull_pin").thenLoop("hold"))
                .triggerableAnim("throw", RawAnimation.begin().thenPlay("throw"))
                .triggerableAnim("throw_under", RawAnimation.begin().thenPlay("throw_under")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new com.modernarmory.client.renderer.GrenadeRenderer(GrenadeItem.this);
                return renderer;
            }
        });
    }
}
