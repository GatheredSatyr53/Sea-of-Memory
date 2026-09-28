package com.seaofmemory.sea;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a memory grave keeps: the belongings, and the guardians it woke to defend them.
 */
public class MemoryGraveBlockEntity extends BlockEntity {
    private final List<ItemStack> items = new ArrayList<>();
    private final List<UUID> guardians = new ArrayList<>();
    private boolean woken;

    public MemoryGraveBlockEntity(BlockPos pos, BlockState state) {
        super(SeaOfMemory.MEMORY_GRAVE_ENTITY.get(), pos, state);
    }

    void fill(List<ItemStack> belongings) {
        items.clear();
        items.addAll(belongings);
        setChanged();
    }

    List<ItemStack> items() {
        return items;
    }

    int itemCount() {
        return items.size();
    }

    boolean isWoken() {
        return woken;
    }

    void wake(List<UUID> guardianIds) {
        woken = true;
        guardians.clear();
        guardians.addAll(guardianIds);
        setChanged();
    }

    /**
     * Whether any guardian is still out there. They rise a few blocks from the grave and are checked by someone
     * standing at it, so their chunks are loaded: a guardian that cannot be found is dead or gone.
     */
    boolean guarded(ServerLevel level) {
        if (guardians.removeIf(id -> {
            Entity guardian = level.getEntity(id);
            return guardian == null || !guardian.isAlive();
        })) {
            setChanged();
        }
        return !guardians.isEmpty();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("items", ItemStack.CODEC.listOf(), List.copyOf(items));
        output.store("guardians", UUIDUtil.CODEC.listOf(), List.copyOf(guardians));
        output.putBoolean("woken", woken);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        input.read("items", ItemStack.CODEC.listOf()).ifPresent(items::addAll);
        guardians.clear();
        input.read("guardians", UUIDUtil.CODEC.listOf()).ifPresent(guardians::addAll);
        woken = input.getBooleanOr("woken", false);
    }
}
