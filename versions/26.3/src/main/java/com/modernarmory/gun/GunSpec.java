package com.modernarmory.gun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.ballistics.Cartridge;
import com.modernarmory.ballistics.DamageModel;
import com.modernarmory.ballistics.ProjectileSpec;

/**
 * Everything that makes one firearm behave like the real thing. Build with {@link #builder()}
 * or, normally, start from a family template in {@link GunFamilies} and set what differs.
 *
 * @param cartridge          ammunition (projectile physics, shot load, ammo item)
 * @param muzzleVelocity     m/s from this gun's barrel length
 * @param rpm                cyclic rate of fire (manual actions: 60 / cycle time)
 * @param fireModes          selector positions / action, in switch order
 * @param burstCount         rounds per burst ({@link FireMode#BURST})
 * @param feed               how it is fed and reloaded (magazine / belt / tube)
 * @param magazineSize       rounds per magazine / belt / tube
 * @param reloadTicks        tactical reload time (round still chambered); TUBE: per shell
 * @param emptyReloadTicks   reload from empty (bolt release / charging handle); TUBE: chambering a shell
 * @param baseDamage         Minecraft damage per projectile (bullet or pellet) at muzzle energy
 *                           (default: {@link DamageModel#forEnergy} of one projectile)
 * @param mechanicalMoa      inherent accuracy of gun + ammo, minutes of angle
 * @param hipSpreadDeg       extra spread when firing from the hip
 * @param adsSpreadDeg       extra spread when aiming (shooter wobble)
 * @param massKg             loaded weapon mass, for recoil
 * @param support            how it is braced (shoulder / integral bipod / tripod): recoil and spread by stance
 * @param adsSeconds         time to raise the sights from the hip
 * @param viewClass          first / third person framing class
 * @param sights             the gun's own sight line / eye position / scope
 * @param sounds             every sound the gun makes
 * @param slots              attachment slots this gun has (the Gunsmith Table X's out the rest)
 * @param defaultAttachments what a new gun comes fitted with (slot -> attachment id)
 * @param parts              model bones shown only in some situations (bone name -> rule), e.g. a folding BUIS
 * @param issuedScopePower   power range of the issued scope when it differs from the fitted scope
 *                           attachment's own range; null = the attachment's range
 * @param category           what kind of gun (its creative tab), from its family
 */
public record GunSpec(Cartridge cartridge, double muzzleVelocity, double rpm, List<FireMode> fireModes, int burstCount,
                      Feed feed, int magazineSize, int reloadTicks, int emptyReloadTicks, float baseDamage,
                      double mechanicalMoa, double hipSpreadDeg, double adsSpreadDeg, double massKg,
                      Support support, double adsSeconds, ViewClass viewClass, GunView sights, SoundSet sounds, Set<AttachmentType> slots,
                      Map<AttachmentType, String> defaultAttachments, Map<String, PartVisibility> parts,
                      ScopePower issuedScopePower, GunCategory category) {
    /** The selector position a new gun starts on: full auto when it has it (the user's pick, 2026-11-19), else the first. */
    public int defaultFireModeIndex() {
        int i = fireModes.indexOf(FireMode.AUTO);
        return i >= 0 ? i : 0;
    }


    /** Seconds between rounds. */
    public double cycleTime() {
        return 60.0 / rpm;
    }

    /** Muzzle energy of the whole payload, J. */
    public double muzzleEnergy() {
        return cartridge.energy(muzzleVelocity);
    }

    /** Free-recoil energy in joules: E = J^2 / (2 m). */
    public double recoilEnergy() {
        double j = cartridge.recoilImpulse(muzzleVelocity);
        return j * j / (2.0 * massKg);
    }

    /** One projectile (bullet or pellet) as fired from this gun. */
    public ProjectileSpec projectile() {
        return ProjectileSpec.of(cartridge, muzzleVelocity, baseDamage);
    }

    /** Single bullet or pellets, from the cartridge. */
    public ProjectileLoad projectileLoad() {
        return ProjectileLoad.of(cartridge);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder pre-filled with this spec (for variants, e.g. the M4A1 from the M4). */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.cartridge = cartridge;
        b.muzzleVelocity = muzzleVelocity;
        b.rpm = rpm;
        b.fireModes = new ArrayList<>(fireModes);
        b.burstCount = burstCount;
        b.feed = feed;
        b.magazineSize = magazineSize;
        b.reloadTicks = reloadTicks;
        b.emptyReloadTicks = emptyReloadTicks;
        b.baseDamage = baseDamage;
        b.mechanicalMoa = mechanicalMoa;
        b.hipSpreadDeg = hipSpreadDeg;
        b.adsSpreadDeg = adsSpreadDeg;
        b.massKg = massKg;
        b.support = support;
        b.adsSeconds = adsSeconds;
        b.viewClass = viewClass;
        b.sights = sights;
        b.sounds = sounds;
        b.slots = slots.isEmpty() ? EnumSet.noneOf(AttachmentType.class) : EnumSet.copyOf(slots);
        b.defaults = new EnumMap<>(AttachmentType.class);
        b.defaults.putAll(defaultAttachments);
        b.parts = new LinkedHashMap<>(parts);
        b.issuedScopePower = issuedScopePower;
        b.category = category;
        return b;
    }

    /**
     * Fluent builder. Every setter overrides what a family template pre-filled. {@link #build()}
     * checks that the gun-specific values (cartridge, velocity, rate, magazine, reload times,
     * damage, mass, sights, sounds) were set.
     */
    public static final class Builder {
        private Cartridge cartridge;
        private double muzzleVelocity = Double.NaN, rpm = Double.NaN;
        private List<FireMode> fireModes = new ArrayList<>();
        private int burstCount = 3;
        private Feed feed = Feed.MAGAZINE;
        private int magazineSize, reloadTicks, emptyReloadTicks;
        private float baseDamage = Float.NaN;
        private double mechanicalMoa = Double.NaN, hipSpreadDeg = Double.NaN, adsSpreadDeg = Double.NaN, massKg = Double.NaN;
        private Support support = Support.SHOULDER;
        private double adsSeconds = 0.2;
        private ViewClass viewClass = ViewClass.RIFLE;
        private GunCategory category;
        private GunView sights;
        private SoundSet sounds;
        private Report report;
        private EnumSet<AttachmentType> slots = EnumSet.noneOf(AttachmentType.class);
        private EnumMap<AttachmentType, String> defaults = new EnumMap<>(AttachmentType.class);
        private Map<String, PartVisibility> parts = new LinkedHashMap<>();
        private ScopePower issuedScopePower;

        private Builder() {}

        public Builder cartridge(Cartridge c) { this.cartridge = c; return this; }
        /** Muzzle velocity from this gun's barrel, m/s. */
        public Builder muzzleVelocity(double ms) { this.muzzleVelocity = ms; return this; }
        /** Cyclic rate, rounds per minute. */
        public Builder rpm(double rpm) { this.rpm = rpm; return this; }
        /** Manual actions: seconds to fire and work the action (match the "fire" animation). */
        public Builder cycleSeconds(double s) { this.rpm = 60.0 / s; return this; }
        /** Selector positions in switch order (the first is the default). */
        public Builder fireModes(FireMode... modes) { this.fireModes = new ArrayList<>(List.of(modes)); return this; }
        public Builder burst(int rounds) { this.burstCount = rounds; return this; }
        public Builder feed(Feed f) { this.feed = f; return this; }
        public Builder magazine(int rounds) { this.magazineSize = rounds; return this; }
        /** Reload times in ticks (20 per second): tactical / from empty (TUBE: per shell / chambering). */
        public Builder reloadTicks(int tactical, int empty) { this.reloadTicks = tactical; this.emptyReloadTicks = empty; return this; }
        /** Reload times in seconds (rounded to ticks): tactical / from empty (TUBE: per shell / chambering). */
        public Builder reloadSeconds(double tactical, double empty) {
            return reloadTicks((int) Math.round(tactical * 20), (int) Math.round(empty * 20));
        }
        /**
         * Minecraft damage per projectile at muzzle energy. Normally left unset: it then follows the
         * mod-wide energy scale ({@link DamageModel#forEnergy}) from the cartridge and velocity.
         */
        public Builder damage(float d) { this.baseDamage = d; return this; }
        /** Inherent accuracy, MOA. */
        public Builder accuracyMoa(double moa) { this.mechanicalMoa = moa; return this; }
        /** Shooter spread, degrees: from the hip / aimed. */
        public Builder spread(double hipDeg, double adsDeg) { this.hipSpreadDeg = hipDeg; this.adsSpreadDeg = adsDeg; return this; }
        /** Loaded weapon mass, kg. */
        public Builder mass(double kg) { this.massKg = kg; return this; }
        /** Shoulder / integral bipod / tripod (recoil and spread by stance). */
        public Builder support(Support s) { this.support = s; return this; }
        /** Seconds to bring the sights up from the hip. */
        public Builder adsSeconds(double s) { this.adsSeconds = s; return this; }
        public Builder view(ViewClass vc) { this.viewClass = vc; return this; }
        /** What kind of gun it is (its creative tab). */
        public Builder category(GunCategory c) { this.category = c; return this; }
        public Builder sights(GunView v) { this.sights = v; return this; }
        /** An explicit sound set (replaces a {@link #report(Report)}). */
        public Builder sounds(SoundSet s) { this.sounds = s; this.report = null; return this; }
        /**
         * The calibre's report; the gun's other sounds follow the naming convention of
         * {@link SoundSets#forGun} (replaces a sound set the family pre-filled).
         */
        public Builder report(Report r) { this.report = r; this.sounds = null; return this; }
        /** Attachment slots (replaces the family's). */
        public Builder slots(AttachmentType... types) {
            this.slots = types.length == 0 ? EnumSet.noneOf(AttachmentType.class) : EnumSet.copyOf(List.of(types));
            return this;
        }
        public Builder allSlots() { this.slots = EnumSet.allOf(AttachmentType.class); return this; }
        /** Factory-fitted attachment in a slot. */
        public Builder fitted(AttachmentType slot, String attachmentId) { this.defaults.put(slot, attachmentId); return this; }
        /** When the model bone {@code bone} is shown (a model without that bone ignores it). */
        public Builder part(String bone, PartVisibility rule) { this.parts.put(bone, rule); return this; }
        /**
         * Power range of the scope this gun is really issued with, applied to its factory-fitted
         * optic (the shared scope attachment stands in for it); see {@link ScopePower}.
         */
        public Builder issuedScopePower(float min, float max) { this.issuedScopePower = new ScopePower(min, max); return this; }

        /** Builds a spec whose sounds are given explicitly ({@link #sounds(SoundSet)}). */
        public GunSpec build() {
            return build(null);
        }

        /**
         * Builds the spec of gun {@code gunId}; with a {@link #report(Report)} instead of a sound
         * set, its sounds are the conventional ones of that id ({@link SoundSets#forGun}).
         */
        public GunSpec build(String gunId) {
            if (sounds == null && report != null && gunId != null && !fireModes.isEmpty()) {
                sounds = SoundSets.forGun(gunId, report, fireModes.stream().anyMatch(FireMode::manualCycle), feed == Feed.TUBE);
            }
            if (Float.isNaN(baseDamage) && cartridge != null && !Double.isNaN(muzzleVelocity)) {
                baseDamage = DamageModel.forEnergy(cartridge.projectileEnergy(muzzleVelocity));
            }
            List<String> missing = new ArrayList<>();
            if (cartridge == null) missing.add("cartridge");
            if (Double.isNaN(muzzleVelocity)) missing.add("muzzleVelocity");
            if (Double.isNaN(rpm)) missing.add("rpm/cycleSeconds");
            if (fireModes.isEmpty()) missing.add("fireModes");
            if (magazineSize <= 0) missing.add("magazine");
            if (reloadTicks <= 0 || emptyReloadTicks <= 0) missing.add("reload");
            if (Float.isNaN(baseDamage)) missing.add("damage");
            if (Double.isNaN(mechanicalMoa)) missing.add("accuracyMoa");
            if (Double.isNaN(hipSpreadDeg) || Double.isNaN(adsSpreadDeg)) missing.add("spread");
            if (Double.isNaN(massKg)) missing.add("mass");
            if (sights == null) missing.add("sights");
            if (sounds == null) missing.add("sounds/report");
            if (adsSeconds <= 0) missing.add("adsSeconds");
            if (category == null) missing.add("category");
            if (!missing.isEmpty()) throw new IllegalStateException("gun spec incomplete, missing: " + String.join(", ", missing));
            for (AttachmentType t : defaults.keySet()) {
                if (!slots.contains(t)) throw new IllegalStateException("default attachment in slot " + t + " the gun doesn't have");
            }
            if (issuedScopePower != null && !defaults.containsKey(AttachmentType.OPTIC)) {
                throw new IllegalStateException("issued scope power without a factory-fitted optic");
            }
            return new GunSpec(cartridge, muzzleVelocity, rpm, List.copyOf(fireModes), burstCount, feed, magazineSize,
                    reloadTicks, emptyReloadTicks, baseDamage, mechanicalMoa, hipSpreadDeg, adsSpreadDeg, massKg,
                    support, adsSeconds, viewClass, sights, sounds, Collections.unmodifiableSet(EnumSet.copyOf(slots)),
                    Map.copyOf(defaults), Collections.unmodifiableMap(new LinkedHashMap<>(parts)), issuedScopePower, category);
        }
    }
}
