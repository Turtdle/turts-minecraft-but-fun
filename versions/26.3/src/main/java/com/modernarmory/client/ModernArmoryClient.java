package com.modernarmory.client;

import com.modernarmory.ModernArmory;
import com.modernarmory.client.renderer.BulletRenderer;
import com.modernarmory.network.LocalSoundPayload;
import com.modernarmory.network.MasterySyncPayload;
import com.modernarmory.registry.ModEntities;
import com.modernarmory.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ModernArmory.MODID, dist = Dist.CLIENT)
public class ModernArmoryClient {
    public ModernArmoryClient(IEventBus modBus, ModContainer container) {
        // Mods > Modern Armory > Config: the crosshair editor and every other setting
        container.registerExtensionPoint(IConfigScreenFactory.class, SettingsScreen::new);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, CrosshairConfig.SPEC);

        modBus.addListener(KeyBindings::register);
        // guns' inventory icons show their fitted attachments (GunIcons; item definitions' GUI case)
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent e) ->
                e.register(ModernArmory.id("gun_icon"), GunIconSpecialRenderer.Unbaked.MAP_CODEC));
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(ModMenus.GUNSMITH.get(), GunsmithScreen::new));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(ModEntities.BULLET.get(), BulletRenderer::new));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(ModEntities.GRENADE.get(), com.modernarmory.client.renderer.GrenadeEntityRenderer::new));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(ModEntities.DRONE.get(), com.modernarmory.client.renderer.DroneEntityRenderer::new));
        modBus.addListener((RegisterGuiLayersEvent e) -> {
            e.registerAboveAll(ModernArmory.id("ammo"), GunHud::render);
            e.registerAboveAll(ModernArmory.id("drone_hud"), DroneHud::render); // the drone camera's HUD while piloting
            e.registerAboveAll(ModernArmory.id("flashbang"), FlashbangOverlay::render); // an M84's white-out covers everything
            e.registerAbove(VanillaGuiLayers.CROSSHAIR, ModernArmory.id("crosshair"), GunCrosshair::render);
            e.registerAbove(VanillaGuiLayers.CROSSHAIR, ModernArmory.id("reflex"), AttachmentEffects::renderReflex);
            e.registerBelow(VanillaGuiLayers.HOTBAR, ModernArmory.id("scope"), ScopeOverlay::render);
        });
        // 26.x: client-bound payload handlers are registered on the client (see ModNetwork)
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(com.modernarmory.network.ShotAckPayload.TYPE, (payload, ctx) -> ShotPredictor.onAck(payload));
            e.register(com.modernarmory.network.GunStatePayload.TYPE, (payload, ctx) -> ShotPredictor.onState(payload));
            e.register(com.modernarmory.network.GunAnimPayload.TYPE, (payload, ctx) -> ShotPredictor.onOthersAnim(payload));
            e.register(LocalSoundPayload.TYPE, (payload, ctx) -> ClientState.onLocalSound(payload));
            e.register(MasterySyncPayload.TYPE, (payload, ctx) -> ClientMastery.onSync(payload));
            e.register(com.modernarmory.network.FlashbangPayload.TYPE, (payload, ctx) -> FlashbangOverlay.onFlash(payload));
            e.register(com.modernarmory.network.DroneViewPayload.TYPE, (payload, ctx) -> DronePilot.onView(payload));
            e.register(com.modernarmory.network.DroneSpotPayload.TYPE, (payload, ctx) -> DronePilot.onSpot(payload));
            e.register(com.modernarmory.network.DroneCorrectPayload.TYPE, (payload, ctx) -> DronePilot.onCorrect(payload));
            e.register(com.modernarmory.network.DroneBlastPayload.TYPE, (payload, ctx) -> DronePilot.onBlast(payload));
        });

        NeoForge.EVENT_BUS.addListener(ClientState::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientState::onInteraction);
        NeoForge.EVENT_BUS.addListener(GrenadeInput::onInteraction);
        NeoForge.EVENT_BUS.addListener(GrenadeInput::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientState::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(AimBob::onFramePre);   // mastery: less view bob while aiming
        NeoForge.EVENT_BUS.addListener(AimBob::onFramePost);
        NeoForge.EVENT_BUS.addListener(ClientState::onFov);
        NeoForge.EVENT_BUS.addListener(ClientState::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ClientState::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(ClientState::onRenderGuiLayer);
        // third person with a gun: two-handed hold, body to the look (set before the render state is built)
        NeoForge.EVENT_BUS.addListener(GunPose::onFramePre);
        modBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) ->
                e.registerItem(GunPose.EXTENSIONS, com.modernarmory.registry.ModItems.guns().values().stream()
                        .map(i -> (net.minecraft.world.item.Item) i.get()).toArray(net.minecraft.world.item.Item[]::new)));
        NeoForge.EVENT_BUS.addListener(ClientState::onComputeFov);
        NeoForge.EVENT_BUS.addListener(ClientState::onScroll);
        NeoForge.EVENT_BUS.addListener(ClientState::onTurn);
        NeoForge.EVENT_BUS.addListener(ClientMastery::onTooltip);
        NeoForge.EVENT_BUS.addListener(MasteryTab::onScreenInit);
        NeoForge.EVENT_BUS.addListener(GunSlotHighlight::onForeground); // hovering a gun outlines the others carried
        // 26.x: the laser dot is submitted as custom world geometry (1.21.1: RenderLevelStageEvent AFTER_PARTICLES)
        NeoForge.EVENT_BUS.addListener(AttachmentEffects::submitLaserDots);
        com.modernarmory.entity.BulletImpacts.setListener(BulletHoles::add); // bullet holes (client.BulletHoles)
        NeoForge.EVENT_BUS.addListener(BulletHoles::submit);
        NeoForge.EVENT_BUS.addListener(BulletHoles::tick);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(ModEntities.TURRET_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
            e.registerBlockEntityRenderer(com.modernarmory.registry.ModBlockEntities.LASER_TURRET.get(),
                    com.modernarmory.client.renderer.LaserTurretRenderer::new);
        });
        DronePilot.register(modBus);
        LaserOperator.register(modBus); // the laser turret: sensor view, drone boxes, beam, placement hologram
        AutoTest.init(modBus);
    }
}
