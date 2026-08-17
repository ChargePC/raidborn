package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.raidborn.core.compat.RaidbornCompatEntities;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;

import java.util.EnumSet;
import java.util.UUID;

public class FollowOwnerGoal extends Goal {
    public static final String TAG_RECRUITED = "raidborn_recruited";
    public static final String TAG_OWNER = "raidborn_owner";

    /** Starts following at 8 blocks and settles at 4, the hysteresis vanilla uses for tamed animals. */
    private static final double START_FOLLOW_SQR = 8.0D * 8.0D;
    private static final double STOP_FOLLOW_SQR = 4.0D * 4.0D;

    /** Past 18 blocks the recruit is falling behind and switches to the faster of its two speeds. */
    private static final double FAST_MOVE_DIST_SQR = 18.0D * 18.0D;

    private static final double CLOSE_LOOK_DIST_SQR = 10.0D * 10.0D;

    /**
     * How fast a recruit should actually travel, in blocks per tick, and how often it may repath.
     *
     * <p>The goal speed is a multiplier over {@link Attributes#MOVEMENT_SPEED}, and recruits are
     * every illager in the game plus whatever other mods add, so a single multiplier makes the fast
     * ones sprint and leaves the slow ones behind. Stating the wanted speed and dividing it out in
     * {@link #getFollowSpeed} keeps the whole squad moving together.
     */
    private record FollowTuning(double nearSpeed, double farSpeed, int repathTicks) {
    }

    private static final FollowTuning DEFAULT_TUNING = new FollowTuning(0.35D, 0.40D, 5);

    /** Ravage &amp; Cabbage's Cabbager rides a vehicle and needs the extra margin to keep up. */
    private static final FollowTuning CABBAGER_TUNING = new FollowTuning(0.44D, 0.52D, 3);

    private static final double MIN_FOLLOW_SPEED_MODIFIER = 0.95D;
    private static final double MAX_FOLLOW_SPEED_MODIFIER = 2.10D;

    /*
     * Recovery when the navigation stops making headway, which in a village means a doorway, a
     * fence corner or another recruit in the gap. Progress is sampled every
     * STUCK_CHECK_INTERVAL_TICKS ticks; each failed sample adds that many ticks to stuckTicks.
     *
     *   immediately  stop and repath, and hop if the mob is walking into a wall
     *   at 35 ticks   sidestep: aim past the obstacle instead of through it
     *   at 70 ticks   give up and teleport, but only from far enough away that it is not visible
     *                 as a pop next to the player
     */
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
        return this.mob.getPersistentData().getBoolean(TAG_RECRUITED)
                && this.mob.getPersistentData().hasUUID(TAG_OWNER);
    }

    private UUID getOwnerUUID() {
        return this.mob.getPersistentData().hasUUID(TAG_OWNER)
                ? this.mob.getPersistentData().getUUID(TAG_OWNER)
                : null;
    }

    private ServerPlayer findOwner() {
        UUID uuid = getOwnerUUID();
        if (uuid == null) return null;

        return this.mob.level().getPlayerByUUID(uuid) instanceof ServerPlayer player ? player : null;
    }

    /** A squad only forms behind a player wearing a banner and carrying the alliance effect. */
    private boolean ownerCanLeadSquad(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return !chest.isEmpty() && chest.getItem() instanceof BannerItem && ModEffects.hasAllianceEffect(player);
    }

    /** Village members answer to the bell, and the other squad orders park the mob somewhere. */
    private boolean isFollowing() {
        return !this.mob.level().isClientSide
                && !WarbellVillageData.isVillageMode(this.mob)
                && isRecruited()
                && SquadOrders.getOrder(this.mob) == SquadOrder.FOLLOW;
    }

    private boolean isSquadmate(LivingEntity entity) {
        if (!(entity instanceof Mob otherMob)) return false;

        if (!otherMob.getPersistentData().getBoolean(TAG_RECRUITED)
                || !otherMob.getPersistentData().hasUUID(TAG_OWNER)) {
            return false;
        }

        UUID myOwner = getOwnerUUID();
        UUID otherOwner = otherMob.getPersistentData().getUUID(TAG_OWNER);
        return myOwner != null && myOwner.equals(otherOwner);
    }

    /**
     * True when the mob has a fight worth staying for, so the follow stands aside.
     *
     * <p>Targets that died, or that turned out to be the owner or a squadmate, are dropped here:
     * they would otherwise block the follow for as long as the reference lived.
     */
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
        // canUse() has just cleared any stale target, so there is nothing to check here.
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

    /** Samples progress once every {@link #STUCK_CHECK_INTERVAL_TICKS} and reports whether it stalled. */
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

            // A single step or a fence gate is the common case, and a hop clears both.
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

    /** Heads diagonally past whatever is in the way instead of straight at the owner again. */
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
