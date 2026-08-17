package net.randomcara.raidborn.content.entity.juggernaut;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.randomcara.raidborn.content.entity.VillageSide;

import javax.annotation.Nullable;

/**
 * The shockwaves a Juggernaut sends through the ground: the two-armed slam, and the thud when it
 * lands from a height.
 *
 * <p>Neither deals damage or breaks blocks. Both use {@code push} rather than {@code knockback}
 * because it adds straight to deltaMovement and therefore still moves knockback-resistant targets;
 * {@code hurtMarked} is what makes the server send the new velocity to the client.
 */
final class JuggernautImpact {

    private static final double SLAM_RADIUS = 4.5D;
    private static final double SLAM_VERTICAL_POWER = 0.75D;
    private static final double SLAM_HORIZONTAL_POWER = 0.12D;

    private static final double FALL_RADIUS = 3.5D;
    private static final double FALL_PUSH = 0.22D;

    private JuggernautImpact() {
    }

    /** Launches nearby enemies upwards. The main target of the swing is skipped: it was already hit. */
    static void slam(Juggernaut juggernaut, @Nullable LivingEntity mainTarget) {
        AABB box = juggernaut.getBoundingBox().inflate(SLAM_RADIUS, 2.0D, SLAM_RADIUS);

        for (LivingEntity victim : juggernaut.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                living -> living != juggernaut
                        && living != mainTarget
                        && living.isAlive()
                        && isVictim(juggernaut, living)
                        && juggernaut.hasLineOfSight(living))) {
            push(juggernaut, victim, SLAM_HORIZONTAL_POWER, SLAM_VERTICAL_POWER);
        }

        playEffects(juggernaut);
    }

    /** Landing nudges whoever is standing around, horizontally only. */
    static void landing(Juggernaut juggernaut) {
        playEffects(juggernaut);

        AABB box = juggernaut.getBoundingBox().inflate(FALL_RADIUS, 1.0D, FALL_RADIUS);

        for (LivingEntity nearby : juggernaut.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                living -> living != juggernaut && living.isAlive() && isVictim(juggernaut, living))) {
            push(juggernaut, nearby, FALL_PUSH, 0.0D);
        }
    }

    /** Same filter for both: whoever the Juggernaut would attack, plus anyone raiding the village. */
    private static boolean isVictim(Juggernaut juggernaut, LivingEntity living) {
        return juggernaut.isValidTarget(living) || VillageSide.isAttackingVillage(living);
    }

    private static void push(Juggernaut juggernaut, LivingEntity victim, double horizontal, double vertical) {
        Vec3 away = victim.position().subtract(juggernaut.position());
        double length = away.horizontalDistance();

        if (length < 1.0E-4D) {
            if (vertical > 0.0D) {
                victim.push(0.0D, vertical, 0.0D);
                victim.hurtMarked = true;
            }

            return;
        }

        victim.push((away.x / length) * horizontal, vertical, (away.z / length) * horizontal);
        victim.hurtMarked = true;
    }

    private static void playEffects(Juggernaut juggernaut) {
        juggernaut.level().playSound(
                null,
                juggernaut.getX(),
                juggernaut.getY(),
                juggernaut.getZ(),
                SoundEvents.IRON_GOLEM_DAMAGE,
                SoundSource.HOSTILE,
                1.0F,
                0.55F
        );

        if (!(juggernaut.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos below = juggernaut.blockPosition().below();
        BlockState state = serverLevel.getBlockState(below);

        if (state.isAir()) {
            return;
        }

        serverLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                juggernaut.getX(),
                juggernaut.getY() + 0.1D,
                juggernaut.getZ(),
                30,
                1.4D,
                0.15D,
                1.4D,
                0.15D
        );
    }
}
