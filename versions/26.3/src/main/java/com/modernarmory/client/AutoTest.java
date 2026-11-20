package com.modernarmory.client;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modernarmory.ModernArmory;
import com.modernarmory.gun.GunDefinition;
import com.modernarmory.gun.Guns;
import com.modernarmory.gun.ViewClass;
import com.modernarmory.item.GunItem;
import com.modernarmory.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
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
 * Dev-only scripted tests (run with -Dmodernarmory.autotest=true, i.e. ./gradlew runAutotest).
 * Creates a fresh superflat world and runs one scenario (-Pscenario=..., default "core"), then quits.
 * Screenshots land in runs/autotest/screenshots/autotest_*.png.
 * <ul>
 *   <li>{@code core} ({@link AutoTestCore}): the regression baseline (screenshots 01..35, scope, mastery)</li>
 *   <li>{@code tour} ({@link AutoTestTour}): every catalog gun, or -Pguns=a,b, through hip / aim / fire / reload /
 *       third person</li>
 *   <li>{@code -Pscenario=gunsmith,ui}: only those sections of the core script (AutoTestCore.SECTIONS: range, auto,
 *       m2010, attachments, gunsmith, mastery, enderman, ui, f5aim, tracers, crosshair, grenades), each after its own
 *       setup; run just the sections a change touches</li>
 * </ul>
 * The scenarios only use the script API below ({@link #at}, {@link #cmd}, {@link #shot}, ...).
 * <p>
 * 26.3 differences from the 1.21.1 runner: mods are constructed before the Minecraft instance exists (use
 * {@link #mc()} inside steps); game rules are no longer part of LevelSettings (set with /gamerule once in the
 * world); Options#hideGui became Hud#toggle()/isHidden() ({@link #setHudHidden}); the item frame summon needs
 * block_pos; screenshots take the render target from the GameRenderer; the catalog check reads the item models'
 * display transforms from the resource JSON (26.x item models don't expose their baked transforms).
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
        // window calls must run on the render thread; in 26.x even client setup's enqueued work runs elsewhere,
        // so the window is hidden from the first screen / tick (see hideWindow)
        NeoForge.EVENT_BUS.addListener(AutoTest::onScreen);
        NeoForge.EVENT_BUS.addListener(AutoTest::onTick);
        at(1, () -> {
            mc().options.pauseOnLostFocus = false;
            mc().getTutorial().setStep(TutorialSteps.NONE); // no "Move with WASD" toast over the screenshots
        });
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

    private static boolean hidden, hideFailedLogged;
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
            // 26.x windows are SDL3 (not GLFW), and LWJGL is off the mod compile classpath: call SDL_HideWindow
            // reflectively, through Minecraft's Window class loader
            Class.forName("org.lwjgl.sdl.SDLVideo", true, w.getClass().getClassLoader())
                    .getMethod("SDL_HideWindow", long.class).invoke(null, w.handle());
            hidden = true;
            ModernArmory.LOGGER.info("[autotest] window hidden (rendering off screen)");
        } catch (ReflectiveOperationException e) {
            if (!hideFailedLogged) ModernArmory.LOGGER.warn("[autotest] could not hide the window: {}", e.toString());
            hideFailedLogged = true;
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
        String name = "autotest-" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc().createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1L, false, false),
                reg -> reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value()
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

    /** 26.x constructs mods before the Minecraft instance exists, so steps look it up when they run. */
    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Run {@code r} on client tick {@code t} after the world has loaded. */
    static void at(int t, Runnable r) {
        STEPS.add(new Step(t, r));
    }

    static void cmd(String c) {
        mc().player.connection.sendCommand(c);
    }

    /** Screenshot runs/autotest/screenshots/autotest_&lt;name&gt;.png. */
    static void shot(String name) {
        Minecraft mc = mc();
        mc.gui.hud.getChat().clearMessages(false);
        Screenshot.grab(mc.gameDirectory, "autotest_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                msg -> ModernArmory.LOGGER.info("[autotest] screenshot {}", name));
    }

    /** 1.21.1's Options#hideGui (also hides the first-person hand). */
    static void setHudHidden(boolean hidden) {
        var hud = mc().gui.hud;
        if (hud.isHidden() != hidden) hud.toggle();
    }

    static void logScope() {
        double fov = ClientState.scopedFov();
        double ppm = (mc().getWindow().getHeight() / 2.0) / Math.tan(Math.toRadians(fov / 2)) / 1000;
        ModernArmory.LOGGER.info("[autotest] scope {}x fov {} deg, {} px/mil, 20 mil = {} px",
                ClientState.scopeZoom(), fov, ppm, 20 * ppm);
    }

    static void look(float yaw, float pitch) {
        var p = mc().player;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
    }

    /** Look through the first invisible armour stand matching {@code which} (e.g. the side-view stand). */
    static void cameraFromStand(java.util.function.Predicate<net.minecraft.world.entity.Entity> which) {
        Minecraft mc = mc();
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
        // 26.x: game rules are not part of LevelSettings any more
        cmd("gamerule send_command_feedback false");
        cmd("gamerule advance_time false");
        cmd("gamerule advance_weather false");
        cmd("gamerule spawn_mobs false");
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
        // 26.x block-attached entities read their wall position from block_pos, checked against Pos while the
        // NBT is loaded (before /summon moves the entity), else an error is logged
        cmd("summon minecraft:item_frame 4 -59 3 {Pos:[4.5d,-58.5d,3.5d],block_pos:[I;4,-59,3],Facing:2b,"
                + "Item:{id:\"modernarmory:m4\",count:1}}");
        cmd("summon minecraft:item 5.5 -60 2.5 {Item:{id:\"modernarmory:m4\",count:1},PickupDelay:32767,Age:-32768}");
        cmd("summon minecraft:armor_stand 4.5 -60 0.2 {Rotation:[90f,0f],Invisible:1b,NoGravity:1b,Marker:0b}");
        cmd("summon minecraft:armor_stand 0.5 -55 -0.3 {Rotation:[180f,90f],Invisible:1b,NoGravity:1b}");
    }

    static void finish() {
        Minecraft mc = mc();
        ModernArmory.LOGGER.info("[autotest] done, screenshots in {}", new File(mc.gameDirectory, "screenshots"));
        mc.stop();
    }

    // ---- catalog checks ---------------------------------------------------------------

    /**
     * For every catalog gun: its item model's first / third person transforms must equal its view
     * class's framing (view_classes.json; models/item/*.json are generated from it by
     * tools/modelgen/display.py), a lang entry, if any, must equal the catalog name, every sound of
     * its set must be defined in sounds.json, and every factory-fitted attachment must fit the gun.
     */
    private static void checkCatalog() {
        Minecraft mc = mc();
        int bad = 0;
        for (GunDefinition def : Guns.all()) {
            GunItem item = ModItems.gun(def);
            ViewClass.Framing f = def.spec().viewClass().framing();
            JsonObject display = display("item/" + def.id());
            bad += compare(def.id() + " firstperson", display, "firstperson_righthand", f.firstPerson(), def);
            bad += compare(def.id() + " thirdperson", display, "thirdperson_righthand", f.thirdPerson(), def);
            String key = item.getDescriptionId();
            if (!Language.getInstance().has(key)) { // 26.x: I18n#exists is gone
                ModernArmory.LOGGER.info("[autotest] lang: {} has no entry, shows catalog name '{}'", key, def.displayName());
            } else if (!I18n.get(key).equals(def.displayName())) {
                ModernArmory.LOGGER.warn("[autotest] lang MISMATCH {}: lang '{}' vs catalog '{}'", key, I18n.get(key), def.displayName());
                bad++;
            }
            // every sound of its set must exist in sounds.json (tools/soundgen/gun_sounds.py)
            var ss = def.spec().sounds();
            for (var sup : java.util.Arrays.asList(ss.fire(), ss.fireSuppressed(), ss.reload(), ss.reloadEmpty(), ss.shellInsert(), ss.cycle())) {
                if (sup == null) continue;
                var loc = sup.get().location();
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

    /**
     * The merged "display" block of models/&lt;path&gt;.json, following modernarmory parents (e.g. m4a1 -> m4), or
     * null if the model can't be read. 26.x: the item model system doesn't expose baked display transforms
     * (1.21.1 used ItemRenderer#getModel(...).getTransforms()), so the check reads what is shipped.
     */
    private static JsonObject display(String modelPath) {
        JsonObject merged = new JsonObject();
        String path = modelPath;
        for (int depth = 0; path != null && depth < 8; depth++) {
            var res = mc().getResourceManager().getResource(ModernArmory.id("models/" + path + ".json"));
            if (res.isEmpty()) return depth == 0 ? null : merged;
            try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject model = JsonParser.parseReader(reader).getAsJsonObject();
                if (model.has("display")) {
                    for (var e : model.getAsJsonObject("display").entrySet()) {
                        if (!merged.has(e.getKey())) merged.add(e.getKey(), e.getValue());
                    }
                }
                String parent = model.has("parent") ? model.get("parent").getAsString() : null;
                Identifier pid = parent == null ? null : Identifier.tryParse(parent);
                path = pid != null && pid.getNamespace().equals(ModernArmory.MODID) ? pid.getPath() : null;
            } catch (Exception ex) {
                return null;
            }
        }
        return merged;
    }

    private static int compare(String what, JsonObject display, String key, ViewClass.Transform want, GunDefinition def) {
        Consumer<String> fail = msg -> ModernArmory.LOGGER.warn("[autotest] framing MISMATCH {} ({}): {} -- run python tools/modelgen/display.py "
                + "and check display_items.json", what, def.spec().viewClass(), msg);
        if (display == null || !display.has(key)) {
            fail.accept("no " + key + " display transform in the item model");
            return 1;
        }
        JsonObject t = display.getAsJsonObject(key);
        float[] rot = vec(t, "rotation", 0), trans = vec(t, "translation", 0), scale = vec(t, "scale", 1);
        if (!near(rot, want.rotation()) || !near(trans, want.translation()) || !near(scale, want.scale())) {
            fail.accept("model rot " + Arrays.toString(rot) + " trans " + Arrays.toString(trans) + " scale " + Arrays.toString(scale));
            return 1;
        }
        return 0;
    }

    private static float[] vec(JsonObject t, String key, float dflt) {
        if (!t.has(key)) return new float[] {dflt, dflt, dflt};
        JsonArray a = t.getAsJsonArray(key);
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static boolean near(float[] v, float[] w) {
        return Math.abs(v[0] - w[0]) < 1e-4 && Math.abs(v[1] - w[1]) < 1e-4 && Math.abs(v[2] - w[2]) < 1e-4;
    }

    private AutoTest() {}
}
