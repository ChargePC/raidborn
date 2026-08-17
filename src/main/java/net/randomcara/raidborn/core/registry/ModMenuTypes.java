package net.randomcara.raidborn.core.registry;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.beast.menu.BeastInventoryMenu;
import net.randomcara.raidborn.transmutation.menu.TransmutationTableMenu;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Raidborn.MOD_ID);

    public static final RegistryObject<MenuType<TransmutationTableMenu>> TRANSMUTATION_TABLE_MENU =
            MENUS.register("transmutation_table_menu", () -> IForgeMenuType.create(TransmutationTableMenu::new));

    public static final RegistryObject<MenuType<BeastInventoryMenu>> BEAST_INVENTORY_MENU =
            MENUS.register("beast_inventory_menu", () -> IForgeMenuType.create(BeastInventoryMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
