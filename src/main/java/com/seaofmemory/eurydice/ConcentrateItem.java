package com.seaofmemory.eurydice;

import com.seaofmemory.cold.Cold;
import com.seaofmemory.sea.EurydiceRitual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Transmogrifier concentrate. "Залейте это в ванну. Потом откройте кран, да погорячее, и туман не замедлит окутать вас":
 * poured into a cauldron of water with fire under it, it starts the Eurydice ritual.
 */
public class ConcentrateItem extends Item {
    public ConcentrateItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(Blocks.WATER_CAULDRON)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        // Only the real world has a copy in the fog to be taken into.
        if (level.dimension() != Level.OVERWORLD) {
            player.sendOverlayMessage(Component.translatable("seaofmemory.eurydice.wrong_world"));
            return InteractionResult.FAIL;
        }
        if (!heated(level, pos)) {
            player.sendOverlayMessage(Component.translatable("seaofmemory.eurydice.cold_water"));
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        stack.consume(1, player);
        if (!player.hasInfiniteMaterials()) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (!player.getInventory().add(bottle)) {
                player.drop(bottle, false);
            }
        }
        // The water is spent: all of it has turned to fog.
        level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        serverLevel.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.2f, 0.6f);
        serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 0.6, 0.6, 0.6, 0.05);
        EurydiceRitual.begin(serverLevel, pos);
        player.sendOverlayMessage(Component.translatable("seaofmemory.eurydice.begun"));
        return InteractionResult.SUCCESS;
    }

    /**
     * "Да погорячее": something burning right under the cauldron.
     */
    private static boolean heated(Level level, BlockPos cauldron) {
        BlockState below = level.getBlockState(cauldron.below());
        return below.is(Cold.WARMTH_SOURCES) && below.getValueOrElse(BlockStateProperties.LIT, true);
    }
}
