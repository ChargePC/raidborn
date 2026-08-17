package net.randomcara.raidborn.content.entity.juggernaut;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.EnumSet;

/**
 * Retaliation, filtered through {@link Juggernaut#isValidTarget}.
 *
 * <p>Vanilla {@code HurtByTargetGoal} would have the Juggernaut turn on villagers and other
 * defenders caught in a crossfire.
 */
class JuggernautHurtByTargetGoal extends TargetGoal {

    private final Juggernaut juggernaut;

    JuggernautHurtByTargetGoal(Juggernaut juggernaut) {
        super(juggernaut, false);
        this.juggernaut = juggernaut;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity attacker = this.juggernaut.getLastHurtByMob();
        return attacker != null && this.juggernaut.isValidTarget(attacker);
    }

    @Override
    public void start() {
        LivingEntity attacker = this.juggernaut.getLastHurtByMob();

        if (attacker != null) {
            this.juggernaut.setTarget(attacker);
        }

        super.start();
    }
}
