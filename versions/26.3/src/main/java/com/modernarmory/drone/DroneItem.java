package com.modernarmory.drone;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.modernarmory.registry.ModDataComponents;
import com.modernarmory.registry.ModItems;
import com.modernarmory.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;

/**
 * A drone, packed. Right-click a block: set it down there; right-click the air: hand-launch it (it hovers
 * in front of you). A Drone Controller in your inventory is paired with it and puts you behind its camera
 * straight away. The battery (shown as the item bar) is kept when you pick the drone back up and recharges
 * while carried.
 */
public class DroneItem extends Item implements GeoItem {
    private static final RawAnimation ITEM = RawAnimation.begin().thenLoop("item");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final DroneType type;

    public DroneItem(DroneType type, Properties properties) {
        super(properties);
        this.type = type;
        GeoItem.registerSyncedAnimatable(this);
    }

    public DroneType type() {
        return type;
    }

    /** A packed drone of {@code type} with this much charge. */
    public static ItemStack stackFor(DroneType type, float battery) {
        ItemStack stack = new ItemStack(ModItems.drone(type));
        stack.set(ModDataComponents.DRONE_BATTERY.get(), Mth.clamp(battery, 0, 1));
        return stack;
    }

    public static float battery(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.DRONE_BATTERY.get(), 1f);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatableWithFallback(getDescriptionId() /* 26.x: no per-stack variant */, type.name());
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Vec3 at = ctx.getClickLocation();
        if (ctx.getClickedFace() != Direction.UP) at = at.add(ctx.getClickedFace().getUnitVec3().scale(type.width() * 0.6));
        return deploy(ctx.getLevel(), player, ctx.getItemInHand(), at, false) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) { // 26.x: InteractionResult
        ItemStack stack = player.getItemInHand(hand);
        Vec3 at = player.getEyePosition().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2)).add(0, -0.4, 0);
        return deploy(level, player, stack, at, true) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private boolean deploy(Level level, Player player, ItemStack stack, Vec3 at, boolean airborne) {
        float battery = battery(stack);
        if (battery < 0.03f) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.literal("Battery flat: let it charge in your inventory"));
            return false;
        }
        DroneEntity d = DroneEntity.create(level, type, player, at, player.getYRot(), battery, airborne);
        if (!level.noCollision(d, d.getBoundingBox())) return false;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return true;
        sl.addFreshEntity(d);
        sl.playSound(null, d.getX(), d.getY(), d.getZ(), ModSounds.DRONE_BEEP.get(), SoundSource.NEUTRAL, 0.6f, 1.0f);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        ItemStack controller = DroneControllerItem.find(player);
        if (controller.isEmpty()) {
            player.sendOverlayMessage(Component.literal("Drone deployed: use a Drone Controller to fly it"));
        } else {
            DroneControllerItem.pair(controller, d);
            DroneControl.startPiloting(sp, d);
        }
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) { // 26.x: server only
        if (level.getGameTime() % 20 != 0) return;
        float b = battery(stack);
        if (b < 1) stack.set(ModDataComponents.DRONE_BATTERY.get(), Math.min(1, b + type.rechargePerTick() * 20));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return battery(stack) < 1;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * battery(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(battery(stack) / 3f, 1f, 1f);
    }

    @SuppressWarnings("deprecation") // deprecated in 26.3 but still called by ItemStack when building tooltips
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.accept(Component.literal(type.description()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal(String.format("Battery %d%%  ·  %.0f min  ·  %.0f m/s  ·  %.1f kg",
                Math.round(battery(stack) * 100), type.enduranceSeconds() / 60, type.maxSpeed(), type.massKg())).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("Right-click a block to set it down, the air to hand-launch. Pick up: right-click it.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- GeckoLib (tools/modelgen/guns/<id>.py: "item" is the carried pose)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<DroneItem>("main", 2, state -> state.setAndContinue(ITEM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private com.modernarmory.client.renderer.@Nullable DeviceItemRenderer<DroneItem> renderer;

            @Override
            public @Nullable GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) renderer = new com.modernarmory.client.renderer.DeviceItemRenderer<>(DroneItem.this, type.id());
                return renderer;
            }
        });
    }
}
