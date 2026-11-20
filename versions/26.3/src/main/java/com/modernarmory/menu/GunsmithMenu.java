package com.modernarmory.menu;

import com.modernarmory.attachment.Attachment;
import com.modernarmory.attachment.AttachmentItem;
import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.item.GunItem;
import com.modernarmory.registry.ModBlocks;
import com.modernarmory.registry.ModItems;
import com.modernarmory.registry.ModMenus;

import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Gunsmith Table: put a gun in the middle slot and its attachments come off into the part slots
 * around it (each where that part mounts on a gun). Change the parts, then take the modded gun from
 * the result on the right. Taking the result uses up the gun and the parts. Taking the gun back out
 * of the middle slot, or closing the table, fits the parts back onto it, so nothing is lost.
 *
 * <p>While a gun is in the table the part slots are the truth: the gun in the middle is bare and
 * the result is that gun with the part slots fitted. Parts the gun has no slot for are X'd out.
 * Changing the magazine type empties the gun; the rounds come back as ammo.
 *
 * <p>Layout (GUI pixels, item top-left). tools/texgen/gunsmith_textures.py draws the same layout.
 */
public class GunsmithMenu extends AbstractContainerMenu {
    public static final int WIDTH = 252, HEIGHT = 196;
    /** Part slot positions, indexed like AttachmentType.values(): where each part sits on a gun. */
    public static final int[][] ATTACHMENT_POS = {
            {68, 22},   // OPTIC: on top of the receiver
            {124, 50},  // MUZZLE: out at the end of the barrel
            {100, 78},  // UNDERBARREL: under the handguard
            {100, 28},  // SIDE: side rail on the handguard
            {14, 50},   // STOCK: behind the receiver
            {62, 78},   // MAGAZINE: under the receiver
    };
    public static final int GUN_X = 68, GUN_Y = 50;
    /** The result: an invisible 16x16 slot under the big preview (the screen makes the whole preview clickable). */
    public static final int RESULT_X = 200, RESULT_Y = 30;
    public static final int INV_X = 45, INV_Y = 114, HOTBAR_Y = 172;

    public static final int GUN_SLOT = 0, ATT_START = 1, ATT_END = ATT_START + AttachmentType.values().length,
            RESULT_SLOT = ATT_END, INV_START = RESULT_SLOT + 1, INV_END = INV_START + 36;

    private final ContainerLevelAccess access;
    private final Player player;
    private final SimpleContainer input = new Changing(1);
    private final SimpleContainer parts = new Changing(AttachmentType.values().length);
    private final SimpleContainer result = new SimpleContainer(1);
    /** True while the gun in the middle has had its parts moved into the part slots. */
    private boolean loaded;
    /** The magazine the gun had when it went in (changing it empties the gun). */
    private String originalMag;

    public GunsmithMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public GunsmithMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(ModMenus.GUNSMITH.get(), id);
        this.access = access;
        this.player = inv.player;
        addSlot(new GunSlot());
        for (AttachmentType t : AttachmentType.values()) {
            int[] p = ATTACHMENT_POS[t.ordinal()];
            addSlot(new PartSlot(t, p[0], p[1]));
        }
        addSlot(new ResultSlot());
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    /** The (bare) gun in the middle slot. */
    public ItemStack gun() {
        return input.getItem(0);
    }

    /** The modded gun on offer: the gun in the middle with the part slots fitted. */
    public ItemStack result() {
        return result.getItem(0);
    }

    /** Whether the gun in the table has this slot (false = draw an X). */
    public boolean supports(AttachmentType type) {
        return gun().getItem() instanceof GunItem g && g.spec().slots().contains(type);
    }

    // ---- the gun going in and coming out ---------------------------------------------

    /** The gun goes in: move its fitted parts into the part slots, leaving it bare. */
    private void strip(ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem g)) return;
        GunAttachments atts = g.attachments(gun);
        for (AttachmentType t : AttachmentType.values()) {
            Attachment a = atts.get(t);
            parts.setItem(t.ordinal(), a != null && g.spec().slots().contains(t)
                    ? new ItemStack(ModItems.ATTACHMENTS.get(a.id()).get()) : ItemStack.EMPTY);
        }
        Attachment mag = atts.get(AttachmentType.MAGAZINE);
        originalMag = mag == null ? null : mag.id();
        g.setAttachments(gun, GunAttachments.EMPTY);
        loaded = true;
    }

    /** The parts in the slots, as a gun's attachment component. */
    private GunAttachments fittedParts() {
        GunAttachments atts = GunAttachments.EMPTY;
        for (AttachmentType t : AttachmentType.values())
            if (parts.getItem(t.ordinal()).getItem() instanceof AttachmentItem ai) atts = atts.with(t, ai.attachment());
        return atts;
    }

    /** Fit the part slots onto a gun leaving the table (empties it if the magazine type changed). */
    private void fitParts(ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem g)) return;
        GunAttachments atts = fittedParts();
        g.setAttachments(gun, atts);
        Attachment mag = atts.get(AttachmentType.MAGAZINE);
        if (!java.util.Objects.equals(mag == null ? null : mag.id(), originalMag)) unload(g, gun);
    }

    /** The gun in the middle leaves the table: it takes the parts with it. Safe to call twice. */
    private void finish(ItemStack gun) {
        if (!loaded) return;
        fitParts(gun);
        parts.clearContent();
        loaded = false;
        originalMag = null;
    }

    /** Give the loaded rounds back as ammo items. */
    private void unload(GunItem g, ItemStack gun) {
        int rounds = g.ammo(gun);
        g.setAmmo(gun, 0);
        if (rounds <= 0 || player.level().isClientSide()) return;
        ItemStack ammo = new ItemStack(g.loadedAmmoItem(gun), rounds);
        if (!player.getInventory().add(ammo)) player.drop(ammo, false, Prediction.SERVER_ONLY); // 26.x: drop takes a Prediction
    }

    /** The modded gun was taken: the gun and the parts are used up. */
    private void takeResult(ItemStack taken) {
        fitParts(taken); // refunds the rounds if the magazine type changed
        loaded = false;
        originalMag = null;
        parts.clearContent();
        input.setItem(0, ItemStack.EMPTY);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        ItemStack gun = gun();
        if (!(gun.getItem() instanceof GunItem g)) {
            result.setItem(0, ItemStack.EMPTY);
            return;
        }
        ItemStack out = gun.copy();
        g.setAttachments(out, fittedParts());
        result.setItem(0, out);
    }

    // ---- slots -----------------------------------------------------------------------

    private final class Changing extends SimpleContainer {
        Changing(int size) {
            super(size);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    }

    private final class GunSlot extends Slot {
        GunSlot() {
            super(input, 0, GUN_X, GUN_Y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof GunItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            // a swap hands the old gun straight back to the cursor without onTake
            if (!oldStack.isEmpty() && newStack != oldStack) finish(oldStack);
            if (!newStack.isEmpty() && newStack != oldStack) strip(newStack);
            super.setByPlayer(newStack, oldStack);
        }

        @Override
        public void onTake(Player p, ItemStack stack) {
            finish(stack);
            super.onTake(p, stack);
        }
    }

    private final class PartSlot extends Slot {
        private final AttachmentType type;

        PartSlot(AttachmentType type, int x, int y) {
            super(parts, type.ordinal(), x, y);
            this.type = type;
        }

        @Override
        public boolean isActive() {
            return supports(type);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return gun().getItem() instanceof GunItem g && stack.getItem() instanceof AttachmentItem a
                    && a.attachment().type() == type && a.fits(g.definition());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private final class ResultSlot extends Slot {
        ResultSlot() {
            super(result, 0, RESULT_X, RESULT_Y);
        }

        @Override
        public boolean isHighlightable() {
            return false; // the screen highlights the whole preview instead
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player p, ItemStack stack) {
            takeResult(stack);
            super.onTake(p, stack);
        }
    }

    // ---- shift-click --------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (index == RESULT_SLOT) {
            ItemStack out = stack.copy();
            result.setItem(0, ItemStack.EMPTY);
            takeResult(out);
            if (!p.getInventory().add(out)) p.drop(out, false, Prediction.SERVER_ONLY);
            return ItemStack.EMPTY;
        }
        if (index == GUN_SLOT) {
            if (p.getInventory().getFreeSlot() < 0) return ItemStack.EMPTY; // no room: the gun stays
            ItemStack out = stack.copy();
            finish(out);
            input.setItem(0, ItemStack.EMPTY);
            moveItemStackTo(out, INV_START, INV_END, true);
            return ItemStack.EMPTY;
        }
        if (index < INV_START) { // a part -> player inventory
            ItemStack copy = stack.copy();
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
            slot.set(ItemStack.EMPTY);
            return copy;
        }
        ItemStack copy = stack.copy();
        if (stack.getItem() instanceof GunItem) {
            if (!moveItemStackTo(stack, GUN_SLOT, GUN_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof AttachmentItem a) {
            Slot target = slots.get(ATT_START + a.attachment().type().ordinal());
            if (!target.isActive() || !target.mayPlace(stack) || target.hasItem()) return ItemStack.EMPTY;
            target.set(stack.split(1));
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return stillValid(access, p, ModBlocks.GUNSMITH_TABLE.get());
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        access.execute((level, pos) -> {
            finish(gun()); // the gun leaves with whatever parts are in the slots
            result.setItem(0, ItemStack.EMPTY);
            clearContainer(p, input);
            clearContainer(p, parts); // only non-empty if something went wrong; never lose items
        });
    }
}
