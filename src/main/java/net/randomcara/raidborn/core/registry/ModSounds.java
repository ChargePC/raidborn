package net.randomcara.raidborn.core.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Raidborn.MOD_ID);

    public static final RegistryObject<SoundEvent> ILLAGER_ACCEPT1 = registerSoundEvent("illager_accept1");
    public static final RegistryObject<SoundEvent> ILLAGER_ACCEPT2 = registerSoundEvent("illager_accept2");
    public static final RegistryObject<SoundEvent> ILLAGER_ACCEPT3 = registerSoundEvent("illager_accept3");

    public static final RegistryObject<SoundEvent> ILLAGER_WARHORN_ATTACK = registerSoundEvent("illager_warhorn_attack");
    public static final RegistryObject<SoundEvent> ILLAGER_WARHORN_FOLLOW = registerSoundEvent("illager_warhorn_follow");
    public static final RegistryObject<SoundEvent> ILLAGER_WARHORN_RAID = registerSoundEvent("illager_warhorn_raid");
    public static final RegistryObject<SoundEvent> ILLAGER_BELL = registerSoundEvent("illager_bell");

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, name)
        ));
    }
}
