package com.modernarmory.ballistics;

/**
 * Bore-size class of a cartridge: what calibre-specific attachments (suppressors, compensators,
 * brakes, magazines) are matched against. Real muzzle devices are made for a bore diameter and a
 * pressure class, not for one exact load, so one .30-cal suppressor fits every cartridge of its
 * class (7.62x51 M80A1 and M118LR, 6.8x51, .300 Win Mag), as the real ones are rated.
 */
public enum CaliberClass {
    /** 9x19mm and .45 ACP pistol / SMG. */
    PISTOL_9MM("9mm / .45 ACP"),
    /** Magnum handgun rounds: .44 Magnum, .50 Action Express. */
    MAGNUM_PISTOL(".44 / .50 AE magnum"),
    /** .223 / 5.56x45mm NATO. */
    RIFLE_556("5.56mm"),
    /** .30 cal and smaller-bore full-power rifle rounds a .30 can is rated for: 7.62x51, 6.8x51 (.277), .300 Win Mag. */
    RIFLE_30(".30 cal (7.62 / 6.8 / .300)"),
    /** .338 Lapua / Norma Magnum. */
    MAGNUM_338(".338 Magnum"),
    /** .50 BMG (12.7x99mm). */
    BMG_50(".50 BMG"),
    /** 12 gauge shotshells. */
    GAUGE_12("12 gauge");

    private final String label;

    CaliberClass(String label) {
        this.label = label;
    }

    /** Display name, e.g. ".30 cal (7.62 / 6.8 / .300)". */
    public String label() {
        return label;
    }
}
