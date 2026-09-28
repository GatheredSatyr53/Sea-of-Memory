package com.seaofmemory.eurydice;

import java.util.function.Supplier;

import com.seaofmemory.cold.Cold;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * "Приложите пальчик вот сюда, к этому кристаллу": held to yourself, the hollow crystal draws out some of your warmth
 * and becomes the catalyst for transmogrifier concentrate. The cold it leaves stays in your heart for good.
 */
public class HollowCrystalItem extends Item {
    // Permanent: the player's cold can never drop below what all their catalysts took.
    private static final float COLD_TAKEN = 10f;
    private static final float CHILL_NOW = 20f;

    private final Supplier<? extends Item> catalyst;

    public HollowCrystalItem(Properties properties, Supplier<? extends Item> catalyst) {
        super(properties);
        this.catalyst = catalyst;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel serverLevel) {
            Cold.raiseFloor(player, COLD_TAKEN);
            Cold.set(player, Cold.get(player) + CHILL_NOW);
            player.getItemInHand(hand).consume(1, player);
            ItemStack filled = new ItemStack(catalyst.get());
            if (!player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
            player.sendOverlayMessage(Component.translatable("seaofmemory.eurydice.cold_forever"));
        }
        return InteractionResult.SUCCESS;
    }
}
