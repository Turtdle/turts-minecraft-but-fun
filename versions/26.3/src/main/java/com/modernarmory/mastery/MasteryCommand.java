package com.modernarmory.mastery;

import java.util.Collection;

import com.modernarmory.ModernArmory;
import com.modernarmory.registry.ModPlayerData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Admin command: {@code /gunmastery get <player> <gun>} and {@code /gunmastery set <player> <gun> <xp>}
 * (gun = item id path, e.g. m4, m2010, or "all" for every gun: {@code /gunmastery set @s all 4000} maxes them all).
 * Requires permission level 2 (cheats / op).
 */
@EventBusSubscriber(modid = ModernArmory.MODID)
public final class MasteryCommand {
    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gunmastery")
                // 26.x: permission levels became permission checks (level 2 = gamemaster)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("gun", StringArgumentType.word()).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    String gun = StringArgumentType.getString(ctx, "gun");
                                    int xp = MasteryHandler.data(p).xp(gun);
                                    ctx.getSource().sendSuccess(() -> Component.literal(p.getScoreboardName() + " " + gun
                                            + ": " + xp + " XP, mastery " + Mastery.roman(Mastery.level(xp))), false);
                                    return xp;
                                }))))
                .then(Commands.literal("set")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("gun", StringArgumentType.word())
                                        .then(Commands.argument("xp", IntegerArgumentType.integer(0)).executes(ctx -> {
                                            Collection<ServerPlayer> ps = EntityArgument.getPlayers(ctx, "players");
                                            String gun = StringArgumentType.getString(ctx, "gun");
                                            int xp = IntegerArgumentType.getInteger(ctx, "xp");
                                            java.util.List<String> guns = gun.equals("all")
                                                    ? com.modernarmory.gun.Guns.all().stream().map(com.modernarmory.gun.GunDefinition::id).toList()
                                                    : java.util.List.of(gun);
                                            for (ServerPlayer p : ps) {
                                                GunMasteryData d = MasteryHandler.data(p);
                                                for (String g : guns) d = d.add(g, xp - d.xp(g));
                                                p.setData(ModPlayerData.MASTERY, d);
                                                MasteryHandler.sync(p);
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal("Set " + gun + " mastery XP to " + xp
                                                    + " for " + ps.size() + " player(s)"), true);
                                            return ps.size();
                                        }))))));
    }

    private MasteryCommand() {}
}
