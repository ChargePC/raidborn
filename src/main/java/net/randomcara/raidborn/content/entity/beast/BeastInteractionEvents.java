package net.randomcara.raidborn.content.entity.beast;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.NameTagItem;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.beast.menu.BeastInventoryMenu;
import net.randomcara.raidborn.core.registry.ModItems;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class BeastInteractionEvents {
    private static final float SOUL_HEAL_AMOUNT = 25.0F;

    private BeastInteractionEvents() {
    }

    @SubscribeEvent
    public static void onInteractBeast(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Beast beast)) {
            return;
        }

        Player player = event.getEntity();

        // SHIFT + right click is left to the recruitment system; do not cancel the event here.
        if (player.isShiftKeyDown()) {
            return;
        }

        ItemStack stack = player.getItemInHand(event.getHand());

        if (stack.is(ModItems.VILLAGER_SOUL.get())
                && beast.getHealth() < beast.getMaxHealth()) {

            if (!player.level().isClientSide) {
                healBeast(beast);

                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }

            cancelInteraction(event, player);
            return;
        }

        if (stack.is(Items.LEAD)
                || stack.getItem() instanceof NameTagItem) {
            return;
        }

        if (!player.level().isClientSide) {
            if (!beast.isCreator(player)
                    || beast.isInCombat()) {

                cancelInteraction(event, player);
                return;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                MenuProvider provider = new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable(
                                "entity.raidborn.beast"
                        );
                    }

                    @Override
                    public AbstractContainerMenu createMenu(
                            int containerId,
                            Inventory inventory,
                            Player menuPlayer
                    ) {
                        return new BeastInventoryMenu(
                                containerId,
                                inventory,
                                beast
                        );
                    }
                };

                NetworkHooks.openScreen(
                        serverPlayer,
                        provider,
                        buffer -> buffer.writeVarInt(beast.getId())
                );
            }
        }

        cancelInteraction(event, player);
    }

    private static void cancelInteraction(
            PlayerInteractEvent.EntityInteract event,
            Player player
    ) {
        event.setCanceled(true);

        event.setCancellationResult(
                InteractionResult.sidedSuccess(
                        player.level().isClientSide
                )
        );
    }

    private static void healBeast(Beast beast) {
        float missingHealth =
                beast.getMaxHealth() - beast.getHealth();

        beast.heal(
                Math.min(
                        SOUL_HEAL_AMOUNT,
                        missingHealth
                )
        );

        beast.playSound(
                SoundEvents.IRON_GOLEM_REPAIR,
                1.0F,
                0.8F
        );
    }
}