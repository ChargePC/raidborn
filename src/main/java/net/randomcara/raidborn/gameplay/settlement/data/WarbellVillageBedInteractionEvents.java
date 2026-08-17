package net.randomcara.raidborn.gameplay.settlement.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class WarbellVillageBedInteractionEvents {
    private static final double BED_SLEEPER_SEARCH_RADIUS = 3.0D;

    private WarbellVillageBedInteractionEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(target instanceof Mob mob) || !isWakeableSleepingVillageMob(mob)) return;

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (!mob.level().isClientSide) {
            WarbellVillageBedData.wakeUpAndStand(mob);
        }
    }

    @SubscribeEvent
    public static void onRightClickBed(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();
        BlockState clickedState = level.getBlockState(clickedPos);

        if (!(clickedState.getBlock() instanceof BedBlock)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        Mob sleepingMob = findSleepingVillageMobOnBed(serverLevel, clickedPos);
        if (sleepingMob == null) return;

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        WarbellVillageBedData.wakeUpAndStand(sleepingMob);
    }

    private static boolean isWakeableSleepingVillageMob(Mob mob) {
        return mob != null
                && mob.isAlive()
                && mob.isSleeping()
                && WarbellVillageData.isVillageMode(mob)
                && WarbellVillageBedData.hasBed(mob)
                && WarbellVillageBedData.isBedValid(mob);
    }

    private static Mob findSleepingVillageMobOnBed(ServerLevel level, BlockPos clickedPos) {
        AABB searchBox = new AABB(clickedPos).inflate(BED_SLEEPER_SEARCH_RADIUS);

        return level.getEntitiesOfClass(
                Mob.class,
                searchBox,
                mob -> isWakeableSleepingVillageMob(mob) && WarbellVillageBedData.isSameBed(mob, clickedPos)
        ).stream().findFirst().orElse(null);
    }
}
