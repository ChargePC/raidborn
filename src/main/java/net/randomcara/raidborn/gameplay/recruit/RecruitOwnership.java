package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

public final class RecruitOwnership {
    public static boolean isRecruited(Mob mob) {
        return mob.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED)
                && mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER);
    }

    public static UUID getOwnerUUID(Mob mob) {
        return mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER)
                ? mob.getPersistentData().getUUID(FollowOwnerGoal.TAG_OWNER)
                : null;
    }

    public static boolean isYours(ServerPlayer player, Mob mob) {
        UUID owner = getOwnerUUID(mob);
        return owner != null && owner.equals(player.getUUID()) && isRecruited(mob);
    }

    public static boolean isSameSquad(Mob a, Mob b) {
        UUID ownerA = getOwnerUUID(a);
        UUID ownerB = getOwnerUUID(b);

        return ownerA != null
                && ownerB != null
                && ownerA.equals(ownerB)
                && isRecruited(a)
                && isRecruited(b);
    }

    private RecruitOwnership() {
    }
}
