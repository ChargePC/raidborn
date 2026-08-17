package net.randomcara.raidborn.gameplay.trade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.bentoslib.curio.CurioActivationHelper;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.compat.RaidbornCompat;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.core.registry.ModItems;
import net.randomcara.raidborn.gameplay.settlement.ai.WarbellVillageRoutine;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageWorkstationData;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public class IllagerTradeEvents {
    public static final long RESTOCK_TICKS = 24000L;
    public static final String TAG_LAST = "raidborn_trade_last";
    public static final String TAG_OFFERS = "raidborn_trade_offers";
    public static final String TAG_LEVEL = "raidborn_trade_level";
    public static final String TAG_XP = "raidborn_trade_xp";
    public static final int[] LEVEL_XP = {0, 10, 65, 155, 280};
    public static final int MAX_TRADE_LEVEL = 5;
    public static final int GIGA_EMERALD_REPUTATION_BONUS = 100;

    private static final String TRADE_BUCKET_PREFIX = "raidborn_trades_";
    private static final String TAG_GIGA_SNAPSHOT = "raidborn_giga_trade_snapshot";
    private static final String TAG_GIGA_SNAPSHOT_CONTAINER = "ContainerId";
    private static final String TAG_GIGA_SNAPSHOT_DIFFS = "Diffs";
    private static final String TAG_LAST_COMPLETED_WORKDAY = "raidborn_trade_last_completed_workday";
    private static final long WORK_PERIOD_END_TIME = 6000L;

    private static final String SANDR_MODID = RaidbornCompat.SAVAGE_AND_RAVAGE;
    private static final String IINV_MODID = RaidbornCompat.ILLAGER_INVASION;
    private static final String TAP_MODID = RaidbornCompat.TAKES_A_PILLAGE;
    private static final String GI_MODID = RaidbornCompat.GUARD_ILLAGERS;
    private static final String HR_MODID = RaidbornCompat.HUNTERS_RETURN;
    private static final String CONJ_MODID = RaidbornCompat.CONJURER_ILLAGER;
    private static final String RNC_MODID = RaidbornCompat.RAVAGE_AND_CABBAGE;
    private static final String EWM_MODID = RaidbornCompat.ENCHANT_WITH_MOB;

    // trade-only integrations, no compat switch of their own so they just live here
    private static final String FNF_MODID = "friendsandfoes";
    private static final String WANDERING_BAGS_MODID = "wandering_bags";

    private static final ResourceLocation MC_PILLAGER = id("minecraft", "pillager");
    private static final ResourceLocation MC_VINDICATOR = id("minecraft", "vindicator");
    private static final ResourceLocation MC_EVOKER = id("minecraft", "evoker");
    private static final ResourceLocation MC_WITCH = id("minecraft", "witch");
    private static final ResourceLocation MC_ILLUSIONER = id("minecraft", "illusioner");
    private static final ResourceLocation SANDR_EXECUTIONER = id(SANDR_MODID, "executioner");
    private static final ResourceLocation SANDR_GRIEFER = id(SANDR_MODID, "griefer");
    private static final ResourceLocation SANDR_ICEOLOGER = id(SANDR_MODID, "iceologer");
    private static final ResourceLocation SANDR_TRICKSTER = id(SANDR_MODID, "trickster");
    private static final ResourceLocation IINV_PROVOKER = id(IINV_MODID, "provoker");
    private static final ResourceLocation IINV_BASHER = id(IINV_MODID, "basher");
    private static final ResourceLocation IINV_SORCERER = id(IINV_MODID, "sorcerer");
    private static final ResourceLocation IINV_ARCHIVIST = id(IINV_MODID, "archivist");
    private static final ResourceLocation IINV_INQUISITOR = id(IINV_MODID, "inquisitor");
    private static final ResourceLocation IINV_MARAUDER = id(IINV_MODID, "marauder");
    private static final ResourceLocation IINV_INVOKER = id(IINV_MODID, "invoker");
    private static final ResourceLocation IINV_ALCHEMIST = id(IINV_MODID, "alchemist");
    private static final ResourceLocation IINV_FIRECALLER = id(IINV_MODID, "firecaller");
    private static final ResourceLocation IINV_NECROMANCER = id(IINV_MODID, "necromancer");
    private static final ResourceLocation IINV_LOST_CANDLE = id(IINV_MODID, "lost_candle");
    private static final ResourceLocation TAP_ARCHER = id(TAP_MODID, "archer");
    private static final ResourceLocation TAP_LEGIONER = id(TAP_MODID, "legioner");
    private static final ResourceLocation TAP_SKIRMISHER = id(TAP_MODID, "skirmisher");
    private static final ResourceLocation GI_GUARD = id(GI_MODID, "guard_illager");
    private static final ResourceLocation HR_HUNTER = id(HR_MODID, "hunter");
    private static final ResourceLocation CONJ_CONJURER = id(CONJ_MODID, "conjurer");
    private static final ResourceLocation CONJ_CONJURER_HAT = id(CONJ_MODID, "conjurer_hat");
    private static final ResourceLocation FNF_TOTEM_OF_ILLUSION = id(FNF_MODID, "totem_of_illusion");
    private static final ResourceLocation RNC_CABBAGER = id(RNC_MODID, "cabbager");
    private static final ResourceLocation EWM_ENCHANTER = id(EWM_MODID, "enchanter");
    private static final ResourceLocation EWM_ENCHANTER_CLOTHES = id(EWM_MODID, "enchanter_clothes");
    private static final ResourceLocation EWM_ENCHANTER_HAT = id(EWM_MODID, "enchanter_hat");
    private static final ResourceLocation EWM_ENCHANTER_BOOTS = id(EWM_MODID, "enchanter_boots");
    private static final ResourceLocation RAIDBORN_NECKLACE = id(Raidborn.MOD_ID, "raidborn_necklace");
    private static final ResourceLocation RAIDBORN_OMINOUS_DAGGER = id(Raidborn.MOD_ID, "ominous_dagger");
    private static final ResourceLocation RAIDBORN_VOODOO_VILLAGER_DOLL = id(Raidborn.MOD_ID, "voodoo_villager_doll");
    private static final ResourceLocation RAIDBORN_VILLAGER_SOUL = id(Raidborn.MOD_ID, "villager_soul");
    private static final ResourceLocation WB_PILLAGER_BAG = id(WANDERING_BAGS_MODID, "pillager_bag");
    private static final ResourceLocation WB_WITCH_BAG = id(WANDERING_BAGS_MODID, "witch_bag");

    private record TradeProfile(String type, String title) {
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static boolean hasTradeEffect(ServerPlayer player) {
        return ModEffects.hasAllianceEffect(player);
    }

    public static boolean hasGigaEmeraldEquipped(Player player) {
        return CurioActivationHelper.isEquipped(player, ModItems.GIGA_EMERALD.get());
    }

    @Nullable
    private static ResourceLocation getEntityId(Entity entity) {
        return ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
    }

    private static boolean tradesEnabledFor(Entity entity) {
        ResourceLocation id = getEntityId(entity);
        return id != null && RaidbornServerConfig.isTradesEnabledFor(id);
    }

    private static boolean isIllagerBusyInCombat(Mob mob) {
        if (mob == null || !mob.isAlive()) return true;

        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) return true;

        LivingEntity lastHurtBy = mob.getLastHurtByMob();
        return lastHurtBy != null && lastHurtBy.isAlive() && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100;
    }

    private static boolean isModLoaded(String modid) {
        return RaidbornCompat.isEnabled(modid);
    }

    private static boolean specialRecruitsEnabled(Entity entity) {
        ResourceLocation id = getEntityId(entity);
        if (id == null) return true;
        if (id.equals(SANDR_ICEOLOGER)) return RaidbornServerConfig.isIceologerRecruitable();
        if (id.equals(SANDR_TRICKSTER)) return RaidbornServerConfig.isTricksterRecruitable();
        if (id.equals(IINV_ARCHIVIST)) return RaidbornServerConfig.isArchivistRecruitable();
        if (id.equals(IINV_FIRECALLER)) return RaidbornServerConfig.isFirecallerRecruitable();
        return true;
    }

    private static boolean isRecruitableForTradeRules(Entity entity) {
        ResourceLocation id = getEntityId(entity);
        if (id == null || !RaidbornServerConfig.isRecruitmentEnabledFor(id)) return false;
        if (entity instanceof net.minecraft.world.entity.monster.Ravager) return false;
        if (entity instanceof Witch) return RaidbornServerConfig.isWitchRecruitable();
        if (!specialRecruitsEnabled(entity)) return false;
        if (isModLoaded(CONJ_MODID) && id.equals(CONJ_CONJURER)) return false;
        if (isModLoaded(EWM_MODID) && id.equals(EWM_ENCHANTER)) return true;

        if (isModLoaded(SANDR_MODID)) {
            if (id.equals(SANDR_ICEOLOGER)) return true;
            if (id.equals(SANDR_TRICKSTER)) return true;
            if (id.equals(SANDR_EXECUTIONER)) return true;
            if (id.equals(SANDR_GRIEFER)) return true;
        }

        if (isModLoaded(IINV_MODID) && IINV_MODID.equals(id.getNamespace())) {
            if (id.equals(IINV_PROVOKER)) return true;
            if (id.equals(IINV_BASHER)) return true;
            if (id.equals(IINV_INQUISITOR)) return true;
            if (id.equals(IINV_MARAUDER)) return true;
            if (id.equals(IINV_ALCHEMIST)) return true;
            if (id.equals(IINV_ARCHIVIST)) return true;
            if (id.equals(IINV_FIRECALLER)) return true;
            return false;
        }

        if (isModLoaded(TAP_MODID) && TAP_MODID.equals(id.getNamespace())) {
            if (id.equals(TAP_ARCHER)) return true;
            if (id.equals(TAP_LEGIONER)) return true;
            if (id.equals(TAP_SKIRMISHER)) return true;
            return false;
        }

        if (isModLoaded(GI_MODID) && id.equals(GI_GUARD)) return true;
        if (isModLoaded(HR_MODID) && id.equals(HR_HUNTER)) return true;
        return entity instanceof AbstractIllager;
    }

    private static boolean isTradeOnlyIllager(Mob mob) {
        return mob != null && mob.isAlive() && getTradeProfile(mob) != null && !isRecruitableForTradeRules(mob);
    }

    public static boolean canTradeWithIllager(Mob mob) {
        if (mob == null || !mob.isAlive() || mob.isSleeping()) return false;
        if (isTradeOnlyIllager(mob)) return true;
        if (!WarbellVillageData.isVillageMode(mob) || !WarbellVillageData.isBellValid(mob)) return false;
        return WarbellVillageWorkstationData.hasWorkstation(mob) && WarbellVillageWorkstationData.isWorkstationValid(mob);
    }

    @Nullable
    private static TradeProfile getTradeProfile(Entity entity) {
        ResourceLocation id = getEntityId(entity);
        if (id == null) return null;

        if (id.equals(MC_ILLUSIONER)) return new TradeProfile("illusioner", "Illusioner Illusions");
        if (id.equals(MC_PILLAGER)) return new TradeProfile("pillager", "Pillager Armory");
        if (id.equals(MC_VINDICATOR)) return new TradeProfile("vindicator", "Vindicator Arsenal");
        if (id.equals(MC_EVOKER)) return new TradeProfile("evoker", "Evoker Relics");
        if (id.equals(MC_WITCH)) return new TradeProfile("witch", "Witch Brews");
        if (id.equals(SANDR_EXECUTIONER)) return new TradeProfile("sandr_executioner", "Executioner Wares");
        if (id.equals(SANDR_GRIEFER)) return new TradeProfile("sandr_griefer", "Griefer Contraband");
        if (id.equals(SANDR_ICEOLOGER)) return new TradeProfile("sandr_iceologer", "Iceologer Relics");
        if (id.equals(SANDR_TRICKSTER)) return new TradeProfile("sandr_trickster", "Trickster Curios");
        if (id.equals(IINV_PROVOKER)) return new TradeProfile("iinv_provoker", "Provoker Draughts");
        if (id.equals(IINV_BASHER)) return new TradeProfile("iinv_basher", "Basher Armory");
        if (id.equals(IINV_SORCERER)) return new TradeProfile("iinv_sorcerer", "Sorcerer Relics");
        if (id.equals(IINV_ARCHIVIST)) return new TradeProfile("iinv_archivist", "Archivist Catalog");
        if (id.equals(IINV_INQUISITOR)) return new TradeProfile("iinv_inquisitor", "Inquisitor Arsenal");
        if (id.equals(IINV_MARAUDER)) return new TradeProfile("iinv_marauder", "Marauder Spoils");
        if (id.equals(IINV_INVOKER)) return new TradeProfile("iinv_invoker", "Invoker Arcana");
        if (id.equals(IINV_ALCHEMIST)) return new TradeProfile("iinv_alchemist", "Alchemist Brews");
        if (id.equals(IINV_FIRECALLER)) return new TradeProfile("iinv_firecaller", "Firecaller Flames");
        if (id.equals(IINV_NECROMANCER)) return new TradeProfile("iinv_necromancer", "Necromancer Rites");
        if (id.equals(TAP_ARCHER)) return new TradeProfile("tap_archer", "Archer Gear");
        if (id.equals(TAP_LEGIONER)) return new TradeProfile("tap_legioner", "Legioner Armory");
        if (id.equals(TAP_SKIRMISHER)) return new TradeProfile("tap_skirmisher", "Skirmisher Wares");
        if (id.equals(GI_GUARD)) return new TradeProfile("gi_guard", "Guard Supplies");
        if (id.equals(HR_HUNTER)) return new TradeProfile("hr_hunter", "Hunter Stock");
        if (id.equals(CONJ_CONJURER)) return new TradeProfile("conj_conjurer", "Conjurer Curios");
        if (id.equals(RNC_CABBAGER)) return new TradeProfile("rnc_cabbager", "Cabbager Produce");
        if (id.equals(EWM_ENCHANTER)) return new TradeProfile("ewm_enchanter_v2", "Enchanter Arcana");
        return null;
    }

    private static String getTradeBucketKey(String type) {
        return TRADE_BUCKET_PREFIX + type;
    }

    private static void ensureTradeData(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (getLevel(mob) <= 0) data.putInt(TAG_LEVEL, 1);
        if (!data.contains(TAG_XP)) data.putInt(TAG_XP, 0);
    }

    public static boolean isUniqueOffer(MerchantOffer offer) {
        if (offer == null || offer.getResult().isEmpty()) return false;

        ItemStack result = offer.getResult();
        ResourceLocation resultId = ForgeRegistries.ITEMS.getKey(result.getItem());
        if (resultId == null) return false;

        if (result.getItem() == Items.TOTEM_OF_UNDYING) return true;
        if (resultId.equals(RAIDBORN_NECKLACE)) return true;
        if (resultId.equals(RAIDBORN_OMINOUS_DAGGER)) return true;
        if (resultId.equals(RAIDBORN_VOODOO_VILLAGER_DOLL)) return true;
        if (resultId.equals(RAIDBORN_VILLAGER_SOUL)) return true;
        if (resultId.equals(WB_PILLAGER_BAG)) return true;
        if (resultId.equals(WB_WITCH_BAG)) return true;
        if (resultId.equals(CONJ_CONJURER_HAT)) return true;
        if (resultId.equals(IINV_LOST_CANDLE)) return true;
        if (resultId.equals(FNF_TOTEM_OF_ILLUSION)) return true;
        if (resultId.equals(EWM_ENCHANTER_CLOTHES)) return true;
        if (resultId.equals(EWM_ENCHANTER_HAT)) return true;
        if (resultId.equals(EWM_ENCHANTER_BOOTS)) return true;

        return resultId.equals(id(SANDR_MODID, "conch_of_conjuring"))
                || resultId.equals(id(SANDR_MODID, "wand_of_freezing"))
                || resultId.equals(id(SANDR_MODID, "wand_of_ice"))
                || resultId.equals(id(SANDR_MODID, "cleaver_of_beheading"))
                || resultId.equals(id(SANDR_MODID, "mask_of_dishonesty"));
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide || player.isShiftKeyDown() || !hasTradeEffect(player)) return;
        if (!(event.getTarget() instanceof Mob mob)) return;
        if (!tradesEnabledFor(mob)) return;

        TradeProfile profile = getTradeProfile(mob);
        if (profile == null || !canTradeWithIllager(mob) || isIllagerBusyInCombat(mob)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        openIllager(player, mob, profile.type(), profile.title());
    }

    @SubscribeEvent
    public static void onVanillaMerchantOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        AbstractContainerMenu container = event.getContainer();
        if (!(container instanceof MerchantMenu menu) || container instanceof IllagerMerchantMenu) return;
        if (!hasGigaEmeraldEquipped(player)) return;

        MerchantOffers offers = menu.getOffers();
        if (offers == null || offers.isEmpty()) return;

        storeSpecialPriceSnapshot(player, container.containerId, offers);
        applyGigaEmeraldReputationDiscounts(offers);
    }

    @SubscribeEvent
    public static void onVanillaMerchantClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        AbstractContainerMenu container = event.getContainer();
        if (!(container instanceof MerchantMenu menu) || container instanceof IllagerMerchantMenu) return;

        MerchantOffers offers = menu.getOffers();
        if (offers == null || offers.isEmpty()) {
            player.getPersistentData().remove(TAG_GIGA_SNAPSHOT);
            return;
        }

        restoreSpecialPriceSnapshot(player, container.containerId, offers);
    }

    private static void storeSpecialPriceSnapshot(ServerPlayer player, int containerId, MerchantOffers offers) {
        int[] diffs = new int[offers.size()];
        for (int i = 0; i < offers.size(); i++) {
            diffs[i] = offers.get(i).getSpecialPriceDiff();
        }

        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt(TAG_GIGA_SNAPSHOT_CONTAINER, containerId);
        snapshot.putIntArray(TAG_GIGA_SNAPSHOT_DIFFS, diffs);
        player.getPersistentData().put(TAG_GIGA_SNAPSHOT, snapshot);
    }

    private static void restoreSpecialPriceSnapshot(ServerPlayer player, int containerId, MerchantOffers offers) {
        CompoundTag playerData = player.getPersistentData();
        if (!playerData.contains(TAG_GIGA_SNAPSHOT, Tag.TAG_COMPOUND)) return;

        CompoundTag snapshot = playerData.getCompound(TAG_GIGA_SNAPSHOT);
        playerData.remove(TAG_GIGA_SNAPSHOT);
        if (snapshot.getInt(TAG_GIGA_SNAPSHOT_CONTAINER) != containerId) return;

        int[] diffs = snapshot.getIntArray(TAG_GIGA_SNAPSHOT_DIFFS);
        int restoreCount = Math.min(diffs.length, offers.size());
        for (int i = 0; i < restoreCount; i++) {
            offers.get(i).setSpecialPriceDiff(diffs[i]);
        }
    }

    private static void openIllager(ServerPlayer player, Mob mob, String type, String titleBase) {
        ensureTradeData(mob);

        int level = getLevel(mob);
        MerchantOffers baseOffers = getOrCreateOffers(mob, player, type, level);
        MerchantOffers sessionOffers = copyOffers(baseOffers);

        if (hasGigaEmeraldEquipped(player)) {
            applyGigaEmeraldReputationDiscounts(sessionOffers);
        }

        openMerchant(player, Component.literal(titleBase), sessionOffers, mob, type);
    }

    private static long getCurrentDay(Mob mob) {
        return Math.max(0L, mob.level().getDayTime() / 24000L);
    }

    private static long getCurrentDayTime(Mob mob) {
        long dayTime = mob.level().getDayTime() % 24000L;
        return dayTime < 0L ? dayTime + 24000L : dayTime;
    }

    private static long getLatestCompletedWorkday(Mob mob) {
        long currentDay = getCurrentDay(mob);
        long dayTime = getCurrentDayTime(mob);

        if (!WarbellVillageRoutine.isEmployed(mob)) return currentDay;
        if (dayTime >= WORK_PERIOD_END_TIME && WarbellVillageRoutine.getCurrentActivity(mob) != WarbellVillageRoutine.Activity.WORK) return currentDay;
        return currentDay - 1L;
    }

    private static MerchantOffers getOrCreateOffers(Mob mob, ServerPlayer player, String type, int level) {
        CompoundTag data = mob.getPersistentData();
        long now = player.level().getGameTime();
        String bucketKey = getTradeBucketKey(type);
        CompoundTag bucket = data.getCompound(bucketKey);
        MerchantOffers offers = new MerchantOffers();

        if (bucket.contains(TAG_OFFERS, Tag.TAG_LIST)) {
            offers = loadOffers(bucket.getList(TAG_OFFERS, Tag.TAG_COMPOUND));
            normalizeOffersForStorage(offers);
        }

        long latestCompletedWorkday = getLatestCompletedWorkday(mob);
        if (offers.isEmpty()) {
            MerchantOffers generated = generateOffers(type, player, level);
            saveOffersToBucket(mob, type, generated, now, latestCompletedWorkday);
            return generated;
        }

        if (!bucket.contains(TAG_LAST_COMPLETED_WORKDAY, Tag.TAG_LONG)) {
            saveOffersToBucket(mob, type, offers, now, latestCompletedWorkday);
            return offers;
        }

        long lastCompletedWorkday = bucket.getLong(TAG_LAST_COMPLETED_WORKDAY);
        if (latestCompletedWorkday > lastCompletedWorkday) {
            restockOffers(offers);
            saveOffersToBucket(mob, type, offers, now, latestCompletedWorkday);
        }

        return offers;
    }

    private static void restockOffers(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            if (!isUniqueOffer(offer)) {
                offer.resetUses();
            }
        }
        normalizeOffersForStorage(offers);
    }

    public static MerchantOffers generateOffers(String type, ServerPlayer player, int level) {
        MerchantOffers allOffers = new MerchantOffers();
        int clampedLevel = Mth.clamp(level, 1, MAX_TRADE_LEVEL);

        for (int currentLevel = 1; currentLevel <= clampedLevel; currentLevel++) {
            MerchantOffers levelOffers = generateOffersForLevel(type, player, currentLevel);
            for (MerchantOffer offer : levelOffers) {
                allOffers.add(new MerchantOffer(offer.createTag()));
            }
        }
        return allOffers;
    }

    public static MerchantOffers generateOffersForLevel(String type, ServerPlayer player, int level) {
        return switch (type) {
            case "pillager" -> IllagerTradeOffers.pillagerOffersForLevel(player.getRandom(), level);
            case "vindicator" -> IllagerTradeOffers.vindicatorOffersForLevel(player.getRandom(), level);
            case "evoker" -> IllagerTradeOffers.evokerOffersForLevel(player.getRandom(), level);
            case "witch" -> IllagerTradeOffers.witchOffersForLevel(player.getRandom(), level);
            case "illusioner" -> IllagerTradeOffers.illusionerOffersForLevel(player.getRandom(), level);
            case "sandr_executioner" -> IllagerTradeOffers.sandrExecutionerOffersForLevel(player.getRandom(), level);
            case "sandr_griefer" -> IllagerTradeOffers.sandrGrieferOffersForLevel(player.getRandom(), level);
            case "sandr_iceologer" -> IllagerTradeOffers.sandrIceologerOffersForLevel(player.getRandom(), level);
            case "sandr_trickster" -> IllagerTradeOffers.sandrTricksterOffersForLevel(player.getRandom(), level);
            case "iinv_provoker" -> IllagerTradeOffers.iinvProvokerOffersForLevel(player.getRandom(), level);
            case "iinv_basher" -> IllagerTradeOffers.iinvBasherOffersForLevel(player.getRandom(), level);
            case "iinv_sorcerer" -> IllagerTradeOffers.iinvSorcererOffersForLevel(player.getRandom(), level);
            case "iinv_archivist" -> IllagerTradeOffers.iinvArchivistOffersForLevel(player.getRandom(), level);
            case "iinv_inquisitor" -> IllagerTradeOffers.iinvInquisitorOffersForLevel(player.getRandom(), level);
            case "iinv_marauder" -> IllagerTradeOffers.iinvMarauderOffersForLevel(player.getRandom(), level);
            case "iinv_invoker" -> IllagerTradeOffers.iinvInvokerOffersForLevel(player.getRandom(), level);
            case "iinv_alchemist" -> IllagerTradeOffers.iinvAlchemistOffersForLevel(player.getRandom(), level);
            case "iinv_firecaller" -> IllagerTradeOffers.iinvFirecallerOffersForLevel(player.getRandom(), level);
            case "iinv_necromancer" -> IllagerTradeOffers.iinvNecromancerOffersForLevel(player.getRandom(), level);
            case "tap_archer" -> IllagerTradeOffers.tapArcherOffersForLevel(player.getRandom(), level);
            case "tap_legioner" -> IllagerTradeOffers.tapLegionerOffersForLevel(player.getRandom(), level);
            case "tap_skirmisher" -> IllagerTradeOffers.tapSkirmisherOffersForLevel(player.getRandom(), level);
            case "gi_guard" -> IllagerTradeOffers.guardIllagerOffersForLevel(player.getRandom(), level);
            case "hr_hunter" -> IllagerTradeOffers.hunterOffersForLevel(player.getRandom(), level);
            case "conj_conjurer" -> IllagerTradeOffers.conjurerOffersForLevel(player.getRandom(), level);
            case "rnc_cabbager" -> IllagerTradeOffers.cabbagerOffersForLevel(player.getRandom(), level);
            case "ewm_enchanter", "ewm_enchanter_v2" -> IllagerTradeOffers.ewmEnchanterOffersForLevel(player, level);
            default -> new MerchantOffers();
        };
    }

    public static int getLevel(Mob mob) {
        int level = mob.getPersistentData().getInt(TAG_LEVEL);
        return level <= 0 ? 1 : Mth.clamp(level, 1, MAX_TRADE_LEVEL);
    }

    public static int getXp(Mob mob) {
        return Math.max(0, mob.getPersistentData().getInt(TAG_XP));
    }

    public static boolean addXpAndMaybeLevelUp(Mob mob, int xpGained) {
        CompoundTag data = mob.getPersistentData();
        int currentLevel = getLevel(mob);
        int xp = getXp(mob) + Math.max(0, xpGained);
        data.putInt(TAG_XP, xp);

        int newLevel = currentLevel;
        while (newLevel < MAX_TRADE_LEVEL && xp >= LEVEL_XP[newLevel]) {
            newLevel++;
        }

        if (newLevel != currentLevel) {
            data.putInt(TAG_LEVEL, newLevel);
            return true;
        }
        return false;
    }

    public static MerchantOffers appendNewLevelOffers(Mob mob, String type, MerchantOffers currentOffers, ServerPlayer player, int oldLevel, int newLevel) {
        MerchantOffers result = copyOffers(currentOffers);
        int clampedOldLevel = Mth.clamp(oldLevel, 1, MAX_TRADE_LEVEL);
        int clampedNewLevel = Mth.clamp(newLevel, 1, MAX_TRADE_LEVEL);

        if (clampedNewLevel <= clampedOldLevel) {
            normalizeOffersForStorage(result);
            return result;
        }

        for (int level = clampedOldLevel + 1; level <= clampedNewLevel; level++) {
            MerchantOffers addedOffers = generateOffersForLevel(type, player, level);
            for (MerchantOffer offer : addedOffers) {
                result.add(new MerchantOffer(offer.createTag()));
            }
        }

        normalizeOffersForStorage(result);
        return result;
    }

    public static void saveOffersToBucket(Mob mob, String type, MerchantOffers offers, long now) {
        saveOffersToBucket(mob, type, offers, now, null);
    }

    private static void saveOffersToBucket(Mob mob, String type, MerchantOffers offers, long now, @Nullable Long completedWorkdayOverride) {
        CompoundTag data = mob.getPersistentData();
        String bucketKey = getTradeBucketKey(type);
        CompoundTag bucket = data.getCompound(bucketKey);
        MerchantOffers cleanOffers = copyOffers(offers);
        normalizeOffersForStorage(cleanOffers);

        bucket.put(TAG_OFFERS, saveOffers(cleanOffers));
        bucket.putLong(TAG_LAST, now);

        if (completedWorkdayOverride != null) {
            bucket.putLong(TAG_LAST_COMPLETED_WORKDAY, completedWorkdayOverride);
        } else if (!bucket.contains(TAG_LAST_COMPLETED_WORKDAY, Tag.TAG_LONG)) {
            bucket.putLong(TAG_LAST_COMPLETED_WORKDAY, getLatestCompletedWorkday(mob));
        }

        data.put(bucketKey, bucket);
    }

    private static ListTag saveOffers(MerchantOffers offers) {
        ListTag list = new ListTag();
        for (MerchantOffer offer : offers) {
            list.add(offer.createTag());
        }
        return list;
    }

    private static MerchantOffers loadOffers(ListTag list) {
        MerchantOffers offers = new MerchantOffers();
        for (int i = 0; i < list.size(); i++) {
            offers.add(new MerchantOffer(list.getCompound(i)));
        }
        return offers;
    }

    private static void openMerchant(ServerPlayer player, Component title, MerchantOffers offers, Mob mob, String type) {
        IllagerMerchant merchant = new IllagerMerchant(offers, mob, type);
        merchant.setTradingPlayer(player);

        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player menuPlayer) {
                merchant.setTradingPlayer(menuPlayer);
                return new IllagerMerchantMenu(id, inventory, merchant);
            }
        };

        NetworkHooks.openScreen(player, provider);

        if (player.containerMenu instanceof MerchantMenu menu) {
            player.sendMerchantOffers(menu.containerId, merchant.getOffers(), getLevel(mob), getXp(mob), true, false);
        }
    }

    public static MerchantOffers copyOffers(MerchantOffers original) {
        MerchantOffers copy = new MerchantOffers();
        if (original == null) return copy;

        for (MerchantOffer offer : original) {
            copy.add(new MerchantOffer(offer.createTag()));
        }
        return copy;
    }

    public static void applyGigaEmeraldReputationDiscounts(MerchantOffers offers) {
        if (offers == null || offers.isEmpty()) return;
        for (MerchantOffer offer : offers) {
            applyGigaEmeraldReputationDiscount(offer);
        }
    }

    public static void applyGigaEmeraldReputationDiscount(MerchantOffer offer) {
        if (offer == null || GIGA_EMERALD_REPUTATION_BONUS <= 0) return;

        ItemStack baseCost = offer.getBaseCostA();
        if (baseCost.isEmpty() || baseCost.getItem() != Items.EMERALD) return;

        int originalCost = baseCost.getCount();
        if (originalCost <= 1 || offer.getPriceMultiplier() <= 0.0F) return;

        int discount = Mth.floor((float) GIGA_EMERALD_REPUTATION_BONUS * offer.getPriceMultiplier());
        discount = Math.min(discount, originalCost - 1);
        if (discount > 0) {
            offer.addToSpecialPriceDiff(-discount);
        }
    }

    private static void normalizeOffersForStorage(MerchantOffers offers) {
        if (offers == null) return;
        for (MerchantOffer offer : offers) {
            normalizeOfferForStorage(offer);
        }
    }

    private static void normalizeOfferForStorage(MerchantOffer offer) {
        if (offer != null && offer.getSpecialPriceDiff() < 0) {
            offer.setSpecialPriceDiff(0);
        }
    }
}
