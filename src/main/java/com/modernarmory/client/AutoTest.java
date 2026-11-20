package com.modernarmory.client;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.modernarmory.ModernArmory;
import com.modernarmory.gun.GunDefinition;
import com.modernarmory.gun.Guns;
import com.modernarmory.gun.ViewClass;
import com.modernarmory.item.GunItem;
import com.modernarmory.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev-only scripted test runner (./gradlew runAutotest, which sets -Dmodernarmory.autotest=true):
 * creates a fresh superflat world, runs one scenario's tick script (screenshots into
 * runs/autotest/screenshots/autotest_*.png, "[autotest] ..." log lines), then quits.
 * <ul>
 *   <li>{@code -Dmodernarmory.autotest.scenario=core} (default): {@link AutoTestCore}</li>
 *   <li>{@code -Dmodernarmory.autotest.scenario=tour -Dmodernarmory.autotest.guns=m4,m2010}:
 *       {@link AutoTestTour}, the same shots for each listed gun (all guns when empty)</li>
 * </ul>
 *   <li>{@code -Pscenario=gunsmith,ui}: only those sections of the core script (AutoTestCore.SECTIONS:
 *       range, auto, m2010, attachments, gunsmith, mastery, enderman, ui, f5aim, tracers, crosshair, grenades, drones), each after its own
 *       setup; run just the sections a change touches</li>
 * Gradle: {@code ./gradlew runAutotest -Pscenario=tour -Pguns=m4,m2010}. Every scenario also logs
 * the catalog consistency checks (framing vs. item models, lang names).
 */
public final class AutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("modernarmory.autotest");
    private static final Map<String, Runnable> SCENARIOS = Map.of(
            "core", AutoTestCore::script,
            "tour", () -> AutoTestTour.script(gunsProperty()),
            "mp", AutoTestMultiplayer::script); // joins a dedicated server (the autotestMp run)

    private static boolean worldRequested;
    private static int tick = -1;
    private static final List<Step> STEPS = new ArrayList<>();

    private record Step(int at, Runnable action) {}

    /** A named slice [start, end) of a scenario's ticks, with what it needs set up to run on its own. */
    record Section(String name, int start, int end, Runnable setup) {}

    static void init(IEventBus modBus) {
        if (!ENABLED) return;
        String scenario = System.getProperty("modernarmory.autotest.scenario", "core").trim();
        if (scenario.isEmpty()) scenario = "core";
        ModernArmory.LOGGER.info("[autotest] enabled, scenario {}", scenario);
        // GLFW window calls must run on the main thread (calling from this loader thread deadlocks):
        // the earliest main-thread slot is client setup's work queue, still during loading
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(AutoTest::hideWindow));
        NeoForge.EVENT_BUS.addListener(AutoTest::onScreen);
        NeoForge.EVENT_BUS.addListener(AutoTest::onTick);
        Minecraft mc = Minecraft.getInstance();
        at(1, () -> mc.options.pauseOnLostFocus = false);
        at(40, AutoTest::checkCatalog);
        Runnable script = SCENARIOS.get(scenario);
        List<String> sections = Arrays.stream(scenario.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
        java.util.Set<String> known = new java.util.HashSet<>();
        AutoTestCore.SECTIONS.forEach(x -> known.add(x.name()));
        if (script != null) {
            script.run();
        } else if (!sections.isEmpty() && known.containsAll(sections)) {
            runSections(sections);
        } else {
            ModernArmory.LOGGER.error("[autotest] unknown scenario '{}' (have {} or core sections {})", scenario,
                    SCENARIOS.keySet(), known);
            at(41, AutoTest::finish);
        }
    }

    /** Run only these sections of the core script, back to back, each after its own setup. */
    private static void runSections(List<String> names) {
        int before = STEPS.size();
        AutoTestCore.script();
        List<Step> core = new ArrayList<>(STEPS.subList(before, STEPS.size()));
        STEPS.subList(before, STEPS.size()).clear();
        int cursor = 20;
        for (Section sec : AutoTestCore.SECTIONS) {
            if (!names.contains(sec.name())) continue;
            at(cursor, sec.setup());
            int offset = cursor + 8 - sec.start();
            for (Step st : core) if (st.at >= sec.start() && st.at < sec.end()) at(st.at + offset, st.action);
            cursor = sec.end() + offset;
            ModernArmory.LOGGER.info("[autotest] section {} at ticks {}-{}", sec.name(), sec.start() + offset, cursor);
        }
        at(cursor + 4, AutoTest::finish);
    }

    private static boolean hidden;
    private static boolean muted;

    /**
     * Test runs render off screen: the game draws into its own framebuffer (what screenshots read), so the
     * window can be hidden (NOT minimised: that shrinks the framebuffer to 1x1) and never takes focus or the
     * mouse. runs/autotest/config/fml.toml turns the early loading window off for the same reason.
     * -Dmodernarmory.autotest.visible=true keeps it on screen for debugging. Main thread only.
     */
    static void hideWindow() {
        if (hidden || Boolean.getBoolean("modernarmory.autotest.visible")) return;
        // window calls off the render (main) thread hang the game: only ever from the render thread
        if (!"Render thread".equals(Thread.currentThread().getName())) return;
        try {
            var w = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getWindow();
            if (w == null) return;
            org.lwjgl.glfw.GLFW.glfwHideWindow(w.getWindow());
            hidden = true;
            ModernArmory.LOGGER.info("[autotest] window hidden (rendering off screen)");
        } catch (RuntimeException e) {
            // window not created yet: try again on the next screen / tick
        }
    }

    private static List<String> gunsProperty() {
        String s = System.getProperty("modernarmory.autotest.guns", "").trim();
        if (s.isEmpty()) return Guns.all().stream().map(GunDefinition::id).toList();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /** Test runs are silent (the master volume of the test profile, runs/autotest); -Dmodernarmory.autotest.sound=true keeps sound. */
    private static void mute() {
        if (muted || Boolean.getBoolean("modernarmory.autotest.sound")) return;
        var volume = Minecraft.getInstance().options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER);
        if (volume.get() > 0) volume.set(0.0);
        muted = true;
        ModernArmory.LOGGER.info("[autotest] sound muted");
    }

    private static void onScreen(ScreenEvent.Init.Post event) {
        hideWindow();
        mute();
        if (worldRequested || !(event.getScreen() instanceof TitleScreen)) return;
        // multiplayer runs join a dedicated server (--quickPlayMultiplayer) and never make a world of their own
        if (System.getProperty("modernarmory.autotest.server") != null) return;
        worldRequested = true;
        Minecraft mc = Minecraft.getInstance();
        String name = "autotest-" + System.currentTimeMillis();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1L, false, false),
                reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(), event.getScreen());
    }

    private static void onTick(ClientTickEvent.Post event) {
        hideWindow();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        tick++;
        for (Step s : STEPS) if (s.at == tick) s.action.run();
    }

    // ---- script API (used by the scenarios) -------------------------------------------

    /** Run {@code r} on client tick {@code t} after the world has loaded. */
    static void at(int t, Runnable r) {
        STEPS.add(new Step(t, r));
    }

    static void cmd(String c) {
        Minecraft.getInstance().player.connection.sendCommand(c);
    }

    /** Screenshot runs/autotest/screenshots/autotest_&lt;name&gt;.png. */
    static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().clearMessages(false);
        Screenshot.grab(mc.gameDirectory, "autotest_" + name + ".png", mc.getMainRenderTarget(),
                msg -> ModernArmory.LOGGER.info("[autotest] screenshot {}", name));
    }

    static void logScope() {
        double fov = ClientState.scopedFov();
        double ppm = (Minecraft.getInstance().getWindow().getHeight() / 2.0) / Math.tan(Math.toRadians(fov / 2)) / 1000;
        ModernArmory.LOGGER.info("[autotest] scope {}x fov {} deg, {} px/mil, 20 mil = {} px",
                ClientState.scopeZoom(), fov, ppm, 20 * ppm);
    }

    static void look(float yaw, float pitch) {
        var p = Minecraft.getInstance().player;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
    }

    /** Look through the first invisible armour stand matching {@code which} (e.g. the side-view stand). */
    static void cameraFromStand(java.util.function.Predicate<net.minecraft.world.entity.Entity> which) {
        Minecraft mc = Minecraft.getInstance();
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.decoration.ArmorStand && which.test(e)) mc.setCameraEntity(e);
        }
    }

    /**
     * The firing range (superflat surface is y=-61; the shooter stands at y=-60 facing north, -Z):
     * stone backstop 20 m out with glass panes and planks in front, a door at 12 m, sugar cane at
     * 6 m, an item frame / dropped item behind the line, and two invisible armour stands used as
     * cameras (side view at x=4.5, top view above the shooter).
     */
    static void buildRange() {
        cmd("time set 6000");
        cmd("weather clear");
        cmd("tp @s 0.5 -60 0.5 180 0");
        cmd("fill -6 -60 -20 6 -56 -20 minecraft:stone");                 // backstop wall 20 m out
        cmd("fill -6 -60 -19 -3 -57 -19 minecraft:glass_pane");          // glass panes in front (left)
        cmd("fill 2 -60 -19 3 -57 -19 minecraft:oak_planks");             // wood (right)
        cmd("setblock 0 -60 -12 minecraft:oak_door[half=lower,facing=south]");
        cmd("setblock 0 -59 -12 minecraft:oak_door[half=upper,facing=south]");
        // real sugar cane (sand + water) in the line of fire: bullets must pass through it
        cmd("setblock 1 -61 -6 minecraft:water");
        cmd("setblock 0 -61 -6 minecraft:sand");
        cmd("setblock 0 -60 -6 minecraft:sugar_cane");
        cmd("setblock 0 -59 -6 minecraft:sugar_cane");
        // display checks behind the firing line: item frame + dropped item
        cmd("fill 3 -60 4 6 -58 4 minecraft:smooth_stone");
        cmd("summon minecraft:item_frame 4 -59 3 {Facing:2b,Item:{id:\"modernarmory:m4\",count:1}}");
        cmd("summon minecraft:item 5.5 -60 2.5 {Item:{id:\"modernarmory:m4\",count:1},PickupDelay:32767,Age:-32768}");
        cmd("summon minecraft:armor_stand 4.5 -60 0.2 {Rotation:[90f,0f],Invisible:1b,NoGravity:1b,Marker:0b}");
        cmd("summon minecraft:armor_stand 0.5 -55 -0.3 {Rotation:[180f,90f],Invisible:1b,NoGravity:1b}");
    }

    static void finish() {
        Minecraft mc = Minecraft.getInstance();
        ModernArmory.LOGGER.info("[autotest] done, screenshots in {}", new File(mc.gameDirectory, "screenshots"));
        mc.stop();
    }

    // ---- catalog checks ---------------------------------------------------------------

    /**
     * For every catalog gun: the baked item model's first / third person transforms must equal its
     * view class's framing (view_classes.json; models/item/*.json are generated from it by
     * tools/modelgen/display.py), a lang entry, if any, must equal the catalog name, every sound of
     * its set must be defined in sounds.json, and every factory-fitted attachment must fit the gun.
     */
    private static void checkCatalog() {
        Minecraft mc = Minecraft.getInstance();
        int bad = 0;
        for (GunDefinition def : Guns.all()) {
            GunItem item = ModItems.gun(def);
            // the item model wraps the 3D model (GUIs show a sprite, NeoForge separate_transforms) and reports no
            // transforms itself: a non-GUI context hands back the 3D model, whose transforms are the ones drawn
            var transforms = mc.getItemRenderer().getModel(new ItemStack(item), mc.level, mc.player, 0)
                    .applyTransform(ItemDisplayContext.NONE, new PoseStack(), false).getTransforms();
            ViewClass.Framing f = def.spec().viewClass().framing();
            bad += compare(def.id() + " firstperson", transforms.firstPersonRightHand, f.firstPerson(), def);
            bad += compare(def.id() + " thirdperson", transforms.thirdPersonRightHand, f.thirdPerson(), def);
            String key = item.getDescriptionId();
            if (!I18n.exists(key)) {
                ModernArmory.LOGGER.info("[autotest] lang: {} has no entry, shows catalog name '{}'", key, def.displayName());
            } else if (!I18n.get(key).equals(def.displayName())) {
                ModernArmory.LOGGER.warn("[autotest] lang MISMATCH {}: lang '{}' vs catalog '{}'", key, I18n.get(key), def.displayName());
                bad++;
            }
            // every sound of its set must exist in sounds.json (tools/soundgen/gun_sounds.py)
            var ss = def.spec().sounds();
            for (var sup : java.util.Arrays.asList(ss.fire(), ss.fireSuppressed(), ss.reload(), ss.reloadEmpty(), ss.shellInsert(), ss.cycle())) {
                if (sup == null) continue;
                var loc = sup.get().getLocation();
                if (mc.getSoundManager().getSoundEvent(loc) == null) {
                    ModernArmory.LOGGER.warn("[autotest] sound MISSING {}: {} has no sounds.json entry", def.id(), loc);
                    bad++;
                }
            }
            // factory-fitted parts must be ones this gun can wear (slot, calibre class, modelled)
            for (var e : def.spec().defaultAttachments().entrySet()) {
                var att = ModItems.ATTACHMENTS.get(e.getValue());
                if (att == null || !att.get().fits(def)) {
                    ModernArmory.LOGGER.warn("[autotest] default attachment MISMATCH {}: {} = {} does not fit", def.id(), e.getKey(), e.getValue());
                    bad++;
                }
            }
        }
        ModernArmory.LOGGER.info("[autotest] catalog check: {} guns, {}", Guns.all().size(),
                bad == 0 ? "framing + lang + sounds + default attachments ok" : bad + " problem(s)");
    }

    private static int compare(String what, ItemTransform baked, ViewClass.Transform want, GunDefinition def) {
        Consumer<String> fail = msg -> ModernArmory.LOGGER.warn("[autotest] framing MISMATCH {} ({}): {} -- run python tools/modelgen/display.py "
                + "and check display_items.json", what, def.spec().viewClass(), msg);
        float[] t = want.translation();
        if (!near(baked.rotation, want.rotation(), 1) || !near(baked.translation, new float[] {t[0] / 16f, t[1] / 16f, t[2] / 16f}, 1)
                || !near(baked.scale, want.scale(), 1)) {
            fail.accept("model rot " + baked.rotation + " trans " + baked.translation + " scale " + baked.scale);
            return 1;
        }
        return 0;
    }

    private static boolean near(org.joml.Vector3f v, float[] w, float scale) {
        return Math.abs(v.x - w[0] * scale) < 1e-4 && Math.abs(v.y - w[1] * scale) < 1e-4 && Math.abs(v.z - w[2] * scale) < 1e-4;
    }

    private AutoTest() {}
}
