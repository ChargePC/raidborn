package net.randomcara.raidborn.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.randomcara.raidborn.core.registry.ModEffects;

import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * The three pacts a player can hold with the illagers. Mutually exclusive.
 *
 * <p>Base rules are the same for all of them (illagers ignore you, villagers won't trade, golems
 * still attack) and live in {@link IllagerAllianceEvents}. Only the bad omen handling and the break
 * condition differ, which is what's below.
 */
public enum IllagerAlliance {
    LOYALTY(
            ModEffects.ILLAGER_LOYALTY,
            "raidborn_had_loyalty_before_bad_omen",
            BadOmenRule.SUSPENDS,
            Betrayal.ON_HURT
    ),
    HONOR(
            ModEffects.ILLAGER_HONOR,
            "raidborn_had_honor_before_bad_omen",
            BadOmenRule.SUSPENDS,
            Betrayal.ON_KILL
    ),
    HERO_OF_THE_RAID(
            ModEffects.HERO_OF_THE_RAID,
            null,
            BadOmenRule.BLOCKS,
            Betrayal.NEVER
    );

    /** How the pact reacts to Bad Omen. */
    public enum BadOmenRule {
        /** Put on hold while Bad Omen lasts, then handed back. Raiding is a temporary betrayal. */
        SUSPENDS,
        /** Bad Omen never lands. The pact outranks it. */
        BLOCKS
    }

    /** What costs the player the pact. */
    public enum Betrayal {
        NEVER,
        /** Dealing any damage to the illager side. */
        ON_HURT,
        /** Killing anything on the illager side; wounding is forgiven. */
        ON_KILL
    }

    /** Long enough to never expire on its own; only these rules take the effect away. */
    public static final int PERMANENT = Integer.MAX_VALUE;

    /** Cached because the player tick walks this every tick and {@code values()} copies the array. */
    private static final IllagerAlliance[] ALL = values();

    /** Package-private: the array is shared, not copied, so it must not escape the package. */
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

    /** Player persistent-data key remembering that Bad Omen suspended this pact. */
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

    /** The pact this player currently holds, or {@code null} for none. */
    @Nullable
    public static IllagerAlliance of(Player player) {
        for (IllagerAlliance alliance : ALL) {
            if (alliance.isOn(player)) return alliance;
        }

        return null;
    }

    /** The pact carried by this effect, or {@code null} if the effect is not one of them. */
    @Nullable
    public static IllagerAlliance forEffect(@Nullable MobEffect effect) {
        if (effect == null) return null;

        for (IllagerAlliance alliance : ALL) {
            if (alliance.effect() == effect) return alliance;
        }

        return null;
    }
}
