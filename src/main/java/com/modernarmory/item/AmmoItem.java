package com.modernarmory.item;

import com.modernarmory.ballistics.Cartridge;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Loose rounds of one cartridge (registered for every entry of the Cartridges catalog). */
public class AmmoItem extends Item {
    private final Cartridge cartridge;
    private final boolean tracer;

    public AmmoItem(Cartridge cartridge, Properties properties) {
        this(cartridge, false, properties);
    }

    /** {@code tracer}: the cartridge's tracer variant (Cartridge.tracer()). */
    public AmmoItem(Cartridge cartridge, boolean tracer, Properties properties) {
        super(properties);
        this.cartridge = cartridge;
        this.tracer = tracer;
    }

    public boolean isTracer() {
        return tracer;
    }

    public Cartridge cartridge() {
        return cartridge;
    }

    /** Lang entry if there is one, else the cartridge's name. */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatableWithFallback(getDescriptionId(stack), tracer ? cartridge.tracer().name() : cartridge.name());
    }
}
