package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class RecruitRoster extends SavedData {
    private static final String DATA_NAME = Raidborn.MOD_ID + "_recruit_roster";

    private final Map<UUID, Entry> recruits = new HashMap<>();

    private record Entry(UUID owner, int cost) {
    }

    public static RecruitRoster get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RecruitRoster::load, RecruitRoster::new, DATA_NAME);
    }

    public static RecruitRoster load(CompoundTag tag) {
        RecruitRoster roster = new RecruitRoster();
        ListTag list = tag.getList("Recruits", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            if (!entryTag.hasUUID("Recruit") || !entryTag.hasUUID("Owner")) continue;

            roster.recruits.put(entryTag.getUUID("Recruit"), new Entry(entryTag.getUUID("Owner"), Math.max(1, entryTag.getInt("Cost"))));
        }

        return roster;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> recruit : this.recruits.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Recruit", recruit.getKey());
            entryTag.putUUID("Owner", recruit.getValue().owner());
            entryTag.putInt("Cost", recruit.getValue().cost());
            list.add(entryTag);
        }

        tag.put("Recruits", list);
        return tag;
    }

    public void track(Mob mob) {
        UUID owner = RecruitOwnership.isRecruited(mob) ? RecruitOwnership.getOwnerUUID(mob) : null;
        if (owner == null) {
            forget(mob);
            return;
        }

        Entry entry = new Entry(owner, RecruitSlots.getRecruitCost(mob));
        if (!entry.equals(this.recruits.put(mob.getUUID(), entry))) {
            this.setDirty();
        }
    }

    public void forget(Mob mob) {
        if (this.recruits.remove(mob.getUUID()) != null) {
            this.setDirty();
        }
    }

    public int countSlots(ServerPlayer owner) {
        int slots = 0;

        Iterator<Map.Entry<UUID, Entry>> iterator = this.recruits.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> recruit = iterator.next();
            if (!recruit.getValue().owner().equals(owner.getUUID())) continue;

            Mob loaded = findLoaded(owner.server, recruit.getKey());
            if (loaded == null) {
                slots += recruit.getValue().cost();
            } else if (RecruitOwnership.isYours(owner, loaded)) {
                slots += RecruitSlots.getRecruitCost(loaded);
            } else {
                iterator.remove();
                this.setDirty();
            }
        }

        return slots;
    }

    @Nullable
    private static Mob findLoaded(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Mob mob) return mob;
        }

        return null;
    }

    @Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
    public static class Events {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onEntityJoin(EntityJoinLevelEvent event) {
            if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Mob mob) {
                get(level.getServer()).track(mob);
            }
        }

        @SubscribeEvent
        public static void onEntityLeave(EntityLeaveLevelEvent event) {
            if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;

            Entity.RemovalReason reason = mob.getRemovalReason();
            if (reason != null && reason.shouldDestroy()) {
                get(level.getServer()).forget(mob);
            } else {
                get(level.getServer()).track(mob);
            }
        }
    }
}
