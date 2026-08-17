package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public class TemporalRelicItem extends Item implements ICurioItem {

    private static final String TAG_EXTRA_CD_COUNTER = "raidborn_temporal_relic_cd_counter";

    public TemporalRelicItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "charm".equals(slotContext.identifier());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Cooldowns recover 30% faster", 0xFFBBA6FF)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        if (!hasTemporalRelicEquipped(player)) {
            player.getPersistentData().putInt(TAG_EXTRA_CD_COUNTER, 0);
            return;
        }

        int counter = player.getPersistentData().getInt(TAG_EXTRA_CD_COUNTER) + 1;

        if (counter >= 10) {
            counter = 0;

            player.getCooldowns().tick();
            player.getCooldowns().tick();
            player.getCooldowns().tick();
        }

        player.getPersistentData().putInt(TAG_EXTRA_CD_COUNTER, counter);
    }

    private static boolean hasTemporalRelicEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(handler -> handler.findFirstCurio(stack -> stack.is(ModItems.TEMPORAL_RELIC.get())).isPresent())
                .orElse(false);
    }
}
