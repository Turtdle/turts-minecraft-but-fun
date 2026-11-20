package com.modernarmory.drone;

import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.modernarmory.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;

/**
 * Handheld ground control station. Paired with the last drone deployed while it was in your inventory;
 * right-click to look through that drone's camera (if it is in range). With no paired drone around, it
 * pairs with the nearest drone of yours. In the camera view: WASD fly, jump / sneak up and down, sprint
 * for speed, mouse to look, left click for the drone's action, scroll to zoom, the exit key (B) to come
 * back to your body.
 */
public class DroneControllerItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    /** How far the controller looks for an unpaired drone of yours (m). */
    private static final double FIND_RADIUS = 96;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DroneControllerItem(Properties properties) {
        super(properties);
        GeoItem.registerSyncedAnimatable(this);
    }

    /** The controller the player carries (off hand first, then main hand, then inventory), or EMPTY. */
    public static ItemStack find(Player player) {
        if (player.getOffhandItem().getItem() instanceof DroneControllerItem) return player.getOffhandItem();
        if (player.getMainHandItem().getItem() instanceof DroneControllerItem) return player.getMainHandItem();
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) if (s.getItem() instanceof DroneControllerItem) return s;
        return ItemStack.EMPTY;
    }

    public static void pair(ItemStack controller, DroneEntity drone) {
        controller.set(ModDataComponents.DRONE_LINK.get(), drone.getUUID());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) { // 26.x: InteractionResult
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;
        DroneEntity drone = paired(sl, stack);
        if (drone == null) {
            drone = nearestOwned(sl, sp);
            if (drone != null) pair(stack, drone);
        }
        if (drone == null) {
            sp.sendOverlayMessage(Component.literal("No drone paired: deploy one with this controller in your inventory"));
            return InteractionResult.FAIL;
        }
        DroneControl.startPiloting(sp, drone);
        return InteractionResult.CONSUME;
    }

    private static @Nullable DroneEntity paired(ServerLevel level, ItemStack stack) {
        UUID id = stack.get(ModDataComponents.DRONE_LINK.get());
        return id != null && level.getEntity(id) instanceof DroneEntity d && !d.isRemoved() ? d : null;
    }

    private static @Nullable DroneEntity nearestOwned(ServerLevel level, ServerPlayer p) {
        DroneEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (DroneEntity d : level.getEntitiesOfClass(DroneEntity.class, p.getBoundingBox().inflate(FIND_RADIUS))) {
            if (!p.getUUID().equals(d.owner())) continue;
            double dist = d.distanceToSqr(p);
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        return best;
    }

    @SuppressWarnings("deprecation") // deprecated in 26.3 but still called by ItemStack when building tooltips
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.accept(Component.literal(stack.has(ModDataComponents.DRONE_LINK.get()) ? "Paired with a drone" : "Not paired")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Right-click: fly the paired drone. In the camera: WASD, jump / sneak, sprint = fast,")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("left click = drone action, scroll = zoom, B = back to your body.").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- GeckoLib (tools/modelgen/guns/drone_controller.py: "idle")

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<DroneControllerItem>("main", 2, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private com.modernarmory.client.renderer.@Nullable DeviceItemRenderer<DroneControllerItem> renderer;

            @Override
            public @Nullable GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) renderer = new com.modernarmory.client.renderer.DeviceItemRenderer<>(DroneControllerItem.this, "drone_controller");
                return renderer;
            }
        });
    }
}
