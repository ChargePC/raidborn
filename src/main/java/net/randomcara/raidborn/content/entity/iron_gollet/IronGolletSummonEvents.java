package net.randomcara.raidborn.content.entity.iron_gollet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModEntities;

import java.util.List;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class IronGolletSummonEvents {
    private IronGolletSummonEvents() {
    }

    /** Manual Iron Gollet totem: carved pumpkin over an iron block, right-clicked with a poppy. */
    @SubscribeEvent
    public static void onPlayerCreateIronGollet(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldStack = player.getItemInHand(event.getHand());

        if (!heldStack.is(Items.POPPY)) {
            return;
        }

        BlockPos pumpkinPos = event.getPos();
        BlockState pumpkinState = event.getLevel().getBlockState(pumpkinPos);
        BlockPos ironPos = pumpkinPos.below();
        BlockState ironState = event.getLevel().getBlockState(ironPos);

        if (!pumpkinState.is(Blocks.CARVED_PUMPKIN)
                || !ironState.is(Blocks.IRON_BLOCK)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(
                InteractionResult.sidedSuccess(event.getLevel().isClientSide)
        );

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (tryCreatePlayerIronGollet(level, pumpkinPos, ironPos, player)
                && !player.getAbilities().instabuild) {
            heldStack.shrink(1);
        }
    }

    private static boolean tryCreatePlayerIronGollet(
            ServerLevel level,
            BlockPos pumpkinPos,
            BlockPos ironPos,
            Player owner
    ) {
        if (!level.getWorldBorder().isWithinBounds(ironPos)) {
            return false;
        }

        BlockState pumpkinState = level.getBlockState(pumpkinPos);
        BlockState ironState = level.getBlockState(ironPos);

        if (!pumpkinState.is(Blocks.CARVED_PUMPKIN)
                || !ironState.is(Blocks.IRON_BLOCK)) {
            return false;
        }

        IronGollet gollet = ModEntities.IRON_GOLLET.get().create(level);
        if (gollet == null) {
            return false;
        }

        clearCreationBlock(level, pumpkinPos, pumpkinState);
        clearCreationBlock(level, ironPos, ironState);

        gollet.moveTo(
                ironPos.getX() + 0.5D,
                ironPos.getY(),
                ironPos.getZ() + 0.5D,
                owner.getYRot(),
                0.0F
        );

        gollet.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(ironPos),
                MobSpawnType.MOB_SUMMONED,
                null,
                null
        );

        gollet.setOwner(owner);
        gollet.setPersistenceRequired();

        if (!level.noCollision(gollet) || !level.addFreshEntity(gollet)) {
            level.setBlock(ironPos, ironState, 3);
            level.setBlock(pumpkinPos, pumpkinState, 3);
            return false;
        }

        level.sendParticles(
                ParticleTypes.POOF,
                ironPos.getX() + 0.5D,
                ironPos.getY() + 0.8D,
                ironPos.getZ() + 0.5D,
                24,
                0.35D,
                0.55D,
                0.35D,
                0.03D
        );

        level.playSound(
                null,
                ironPos,
                SoundEvents.IRON_GOLEM_REPAIR,
                SoundSource.NEUTRAL,
                1.0F,
                0.9F + level.random.nextFloat() * 0.1F
        );

        return true;
    }

    private static void clearCreationBlock(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        level.levelEvent(2001, pos, Block.getId(state));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @SubscribeEvent
    public static void onVillageProtectorHurt(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity victim = event.getEntity();

        boolean victimIsVillager = victim instanceof Villager;
        boolean victimIsIronGolem = victim instanceof IronGolem
                && !(victim instanceof IronGollet);
        boolean victimIsLinkedGollet = victim instanceof IronGollet gollet
                && gollet.isVillageLinked();

        if (!victimIsVillager
                && !victimIsIronGolem
                && !victimIsLinkedGollet) {
            return;
        }

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }

        if (!isValidVillageAggressor(attacker)) {
            return;
        }

        notifyLinkedGollets(level, victim, attacker);

        if (victimIsLinkedGollet) {
            notifyIronGolems(level, victim, attacker);
        }
    }

    private static void notifyLinkedGollets(
            ServerLevel level,
            LivingEntity victim,
            LivingEntity attacker
    ) {
        List<IronGollet> gollets = level.getEntitiesOfClass(
                IronGollet.class,
                victim.getBoundingBox().inflate(32.0D, 16.0D, 32.0D),
                gollet -> gollet.isAlive()
                        && gollet.isVillageLinked()
                        && gollet.canAttackThreat(attacker)
        );

        for (IronGollet gollet : gollets) {
            gollet.setTarget(attacker);
        }
    }

    private static void notifyIronGolems(
            ServerLevel level,
            LivingEntity victim,
            LivingEntity attacker
    ) {
        List<IronGolem> golems = level.getEntitiesOfClass(
                IronGolem.class,
                victim.getBoundingBox().inflate(32.0D, 16.0D, 32.0D),
                golem -> golem.isAlive()
                        && !(golem instanceof IronGollet)
        );

        for (IronGolem golem : golems) {
            golem.setTarget(attacker);
        }
    }

    private static boolean isValidVillageAggressor(LivingEntity attacker) {
        if (!attacker.isAlive()) {
            return false;
        }

        if (attacker instanceof Creeper) {
            return false;
        }

        if (attacker instanceof Villager
                || attacker instanceof IronGolem
                || attacker instanceof IronGollet) {
            return false;
        }

        if (attacker instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }

        return true;
    }
}
