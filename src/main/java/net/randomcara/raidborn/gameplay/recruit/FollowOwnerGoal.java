package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.raidborn.core.compat.RaidbornCompatEntities;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.gameplay.banner.BannerSlot;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;

import java.util.EnumSet;
import java.util.UUID;

public class FollowOwnerGoal extends Goal {
    public static final String TAG_RECRUITED = "raidborn_recruited";
    public static final String TAG_OWNER = "raidborn_owner";
    private static final double START_FOLLOW_SQR = 8.0D * 8.0D;
    private static final double STOP_FOLLOW_SQR = 4.0D * 4.0D;
    private static final double FAST_MOVE_DIST_SQR = 18.0D * 18.0D;
    private static final double CLOSE_LOOK_DIST_SQR = 10.0D * 10.0D;

    private record FollowTuning(double nearSpeed, double farSpeed, int repathTicks) {
    }

    private static final FollowTuning DEFAULT_TUNING = new FollowTuning(0.35D, 0.40D, 5);
    private static final FollowTuning CABBAGER_TUNING = new FollowTuning(0.44D, 0.52D, 3);
    private static final double MIN_FOLLOW_SPEED_MODIFIER = 0.95D;
    private static final double MAX_FOLLOW_SPEED_MODIFIER = 2.10D;

    private static final int STUCK_CHECK_INTERVAL_TICKS = 10;
    private static final int STUCK_SIDESTEP_TICKS = 35;
    private static final int STUCK_TELEPORT_TICKS = 70;
    private static final int SIDESTEP_COOLDOWN_TICKS = 10;
    private static final double MIN_PROGRESS_SQR = 0.035D;
    private static final double STUCK_TELEPORT_MIN_DIST_SQR = 12.0D * 12.0D;
    private static final double SIDESTEP_FORWARD_OFFSET = 2.0D;
    private static final double SIDESTEP_SIDEWAYS_OFFSET = 1.75D;

    private final Mob mob;
    private ServerPlayer owner;
    private int repathCooldown;

    private int stuckTicks;
    private int stuckCheckCooldown;
    private int sidestepCooldown;
    private Vec3 lastProgressPos;

    public FollowOwnerGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static double teleportDistSqr() {
        double distance = RaidbornServerConfig.getSquadFollowTeleportDistance();
        return distance * distance;
    }

    private boolean isRecruited() {
        return this.mob.getPersistentData().getBoolean(TAG_RECRUITED) && this.mob.getPersistentData().hasUUID(TAG_OWNER);
    }

    private UUID getOwnerUUID() {
        return this.mob.getPersistentData().hasUUID(TAG_OWNER) ? this.mob.getPersistentData().getUUID(TAG_OWNER) : null;
    }

    private ServerPlayer findOwner() {
        UUID uuid = getOwnerUUID();
        if (uuid == null) return null;

        return this.mob.level().getPlayerByUUID(uuid) instanceof ServerPlayer player ? player : null;
    }

    private boolean ownerCanLeadSquad(ServerPlayer player) {
        return BannerSlot.isWearingBanner(player) && ModEffects.hasAllianceEffect(player);
    }

    private boolean isFollowing() {
        return !this.mob.level().isClientSide && !WarbellVillageData.isVillageMode(this.mob) && isRecruited() && SquadOrders.getOrder(this.mob) == SquadOrder.FOLLOW;
    }

    private boolean isSquadmate(LivingEntity entity) {
        if (!(entity instanceof Mob otherMob)) return false;

        if (!otherMob.getPersistentData().getBoolean(TAG_RECRUITED) || !otherMob.getPersistentData().hasUUID(TAG_OWNER)) {
            return false;
        }

        UUID myOwner = getOwnerUUID();
        UUID otherOwner = otherMob.getPersistentData().getUUID(TAG_OWNER);
        return myOwner != null && myOwner.equals(otherOwner);
    }

    private boolean hasBlockingCombatTarget() {
        LivingEntity target = this.mob.getTarget();
        if (target != null && (!target.isAlive() || target.isRemoved() || target == this.owner || isSquadmate(target))) {
            this.mob.setTarget(null);
            target = null;
        }

        if (target == null && this.mob instanceof Monster monster) {
            monster.setAggressive(false);
        }

        return target != null;
    }

    private FollowTuning getTuning() {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(this.mob.getType());
        return RaidbornCompatEntities.RNC_CABBAGER.equals(id) ? CABBAGER_TUNING : DEFAULT_TUNING;
    }

    private double getFollowSpeed(double distSqr) {
        FollowTuning tuning = getTuning();
        double wantedSpeed = distSqr > FAST_MOVE_DIST_SQR ? tuning.farSpeed() : tuning.nearSpeed();
        double baseSpeed = Math.max(0.001D, this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        return Mth.clamp(wantedSpeed / baseSpeed, MIN_FOLLOW_SPEED_MODIFIER, MAX_FOLLOW_SPEED_MODIFIER);
    }

    @Override
    public boolean canUse() {
        if (!isFollowing()) return false;

        ServerPlayer player = findOwner();
        if (player == null || !player.isAlive() || !ownerCanLeadSquad(player)) {
            return false;
        }

        this.owner = player;
        return !hasBlockingCombatTarget() && this.mob.distanceToSqr(this.owner) > START_FOLLOW_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        if (!isFollowing()) return false;

        if (this.owner == null || !this.owner.isAlive() || !ownerCanLeadSquad(this.owner)) {
            return false;
        }

        return !hasBlockingCombatTarget() && this.mob.distanceToSqr(this.owner) > STOP_FOLLOW_SQR;
    }

    @Override
    public void start() {
        this.mob.getNavigation().setCanFloat(true);

        this.repathCooldown = 0;
        resetStuckTracker();
    }

    @Override
    public void tick() {
        if (!isFollowing()) {
            this.mob.getNavigation().stop();
            return;
        }

        if (this.owner == null || !this.owner.isAlive()) {
            this.owner = findOwner();
            if (this.owner == null || !this.owner.isAlive()) {
                this.mob.getNavigation().stop();
                return;
            }
        }

        if (hasBlockingCombatTarget()) {
            this.mob.getNavigation().stop();
            resetStuckTracker();
            return;
        }

        double distSqr = this.mob.distanceToSqr(this.owner);
        if (distSqr >= teleportDistSqr()) {
            RecruitTeleport.tryTeleportNearOwner(this.mob, this.owner);
            resetStuckTracker();
            return;
        }

        updateLookControl(distSqr);

        if (distSqr <= STOP_FOLLOW_SQR) {
            this.mob.getNavigation().stop();
            resetStuckTracker();
            return;
        }

        boolean stuck = updateStuckTracker(distSqr);
        if (stuck) {
            this.mob.getNavigation().stop();
            this.repathCooldown = 0;

            if (this.stuckTicks >= STUCK_TELEPORT_TICKS && distSqr > STUCK_TELEPORT_MIN_DIST_SQR) {
                RecruitTeleport.tryTeleportNearOwner(this.mob, this.owner);
                resetStuckTracker();
                return;
            }

            if (this.stuckTicks >= STUCK_SIDESTEP_TICKS && sidestepTowardsOwner(distSqr)) {
                return;
            }
        }

        if (this.repathCooldown > 0 && this.mob.getNavigation().isInProgress() && !stuck) {
            this.repathCooldown--;
            return;
        }

        this.mob.getNavigation().moveTo(this.owner, getFollowSpeed(distSqr));
        this.repathCooldown = getTuning().repathTicks();
    }

    private void updateLookControl(double distSqr) {
        if (distSqr <= CLOSE_LOOK_DIST_SQR || !this.mob.getNavigation().isInProgress()) {
            this.mob.getLookControl().setLookAt(this.owner, 20.0F, 20.0F);
        }
    }

    private void resetStuckTracker() {
        this.stuckTicks = 0;
        this.stuckCheckCooldown = STUCK_CHECK_INTERVAL_TICKS;
        this.sidestepCooldown = 0;
        rememberPosition();
    }

    private void rememberPosition() {
        this.lastProgressPos = this.mob.position();
    }

    private boolean updateStuckTracker(double distSqr) {
        if (this.sidestepCooldown > 0) {
            this.sidestepCooldown--;
        }

        if (!this.mob.getNavigation().isInProgress()) {
            this.stuckTicks = 0;
            this.stuckCheckCooldown = STUCK_CHECK_INTERVAL_TICKS;
            rememberPosition();
            return false;
        }

        if (--this.stuckCheckCooldown > 0) {
            return this.stuckTicks > 0;
        }

        boolean walkingIntoWall = this.mob.horizontalCollision;
        boolean stalled = this.mob.position().distanceToSqr(this.lastProgressPos) < MIN_PROGRESS_SQR;
        if (distSqr > STOP_FOLLOW_SQR && (walkingIntoWall || stalled)) {
            this.stuckTicks += STUCK_CHECK_INTERVAL_TICKS;

            if (walkingIntoWall && this.mob.onGround()) {
                this.mob.getJumpControl().jump();
            }
        } else {
            this.stuckTicks = 0;
        }

        rememberPosition();
        this.stuckCheckCooldown = STUCK_CHECK_INTERVAL_TICKS;

        return this.stuckTicks > 0;
    }

    private boolean sidestepTowardsOwner(double distSqr) {
        if (this.owner == null || this.sidestepCooldown > 0) {
            return false;
        }

        double dx = this.owner.getX() - this.mob.getX();
        double dz = this.owner.getZ() - this.mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.001D) {
            return false;
        }

        double forwardX = dx / horizontal;
        double forwardZ = dz / horizontal;
        double sideX = -forwardZ;
        double sideZ = forwardX;

        double sideSign = this.mob.getRandom().nextBoolean() ? 1.0D : -1.0D;
        double targetX = this.mob.getX() + forwardX * SIDESTEP_FORWARD_OFFSET + sideX * SIDESTEP_SIDEWAYS_OFFSET * sideSign;
        double targetY = this.owner.getY();
        double targetZ = this.mob.getZ() + forwardZ * SIDESTEP_FORWARD_OFFSET + sideZ * SIDESTEP_SIDEWAYS_OFFSET * sideSign;
        this.mob.getNavigation().moveTo(targetX, targetY, targetZ, getFollowSpeed(distSqr));

        this.repathCooldown = 0;
        this.sidestepCooldown = SIDESTEP_COOLDOWN_TICKS;
        return true;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();

        if (this.mob.getTarget() == null && this.mob instanceof Monster monster) {
            monster.setAggressive(false);
        }

        this.owner = null;
        this.repathCooldown = 0;
        resetStuckTracker();
    }
}
