package net.randomcara.raidborn.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.randomcara.raidborn.core.registry.ModEffects;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public enum IllagerAlliance {
    LOYALTY(ModEffects.ILLAGER_LOYALTY, "raidborn_had_loyalty_before_bad_omen", BadOmenRule.SUSPENDS, Betrayal.ON_HURT),
    HONOR(ModEffects.ILLAGER_HONOR, "raidborn_had_honor_before_bad_omen", BadOmenRule.SUSPENDS, Betrayal.ON_KILL),
    HERO_OF_THE_RAID(ModEffects.HERO_OF_THE_RAID, null, BadOmenRule.BLOCKS, Betrayal.NEVER);

    public enum BadOmenRule {
        SUSPENDS,
        BLOCKS
    }

    public enum Betrayal {
        NEVER,
        ON_HURT,
        ON_KILL
    }

    public static final int PERMANENT = Integer.MAX_VALUE;
    private static final IllagerAlliance[] ALL = values();

    static IllagerAlliance[] all() {
        return ALL;
    }

    private final Supplier<MobEffect> effect;
    private final String suspendTag;
    private final BadOmenRule badOmenRule;
    private final Betrayal betrayal;

    IllagerAlliance(Supplier<MobEffect> effect,
                    @Nullable String suspendTag,
                    BadOmenRule badOmenRule,
                    Betrayal betrayal) {
        this.effect = effect;
        this.suspendTag = suspendTag;
        this.badOmenRule = badOmenRule;
        this.betrayal = betrayal;
    }

    public MobEffect effect() {
        return this.effect.get();
    }

    public BadOmenRule badOmenRule() {
        return this.badOmenRule;
    }

    public Betrayal betrayal() {
        return this.betrayal;
    }

    public String suspendTag() {
        if (this.suspendTag == null) {
            throw new IllegalStateException(this + " is never suspended by Bad Omen");
        }

        return this.suspendTag;
    }

    public boolean isOn(Player player) {
        return player.hasEffect(effect());
    }

    public void grant(Player player) {
        player.addEffect(new MobEffectInstance(effect(), PERMANENT, 0, false, true, true));
    }

    public void revoke(Player player) {
        player.removeEffect(effect());
    }

    @Nullable
    public static IllagerAlliance of(Player player) {
        for (IllagerAlliance alliance : ALL) {
            if (alliance.isOn(player)) return alliance;
        }

        return null;
    }

    @Nullable
    public static IllagerAlliance forEffect(@Nullable MobEffect effect) {
        if (effect == null) return null;

        for (IllagerAlliance alliance : ALL) {
            if (alliance.effect() == effect) return alliance;
        }

        return null;
    }
}
