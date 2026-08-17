package net.randomcara.raidborn.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class RaidbornClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Values VALUES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        VALUES = new Values(builder);
        SPEC = builder.build();
    }

    public enum RecruitTooltipPosition {
        RIGHT,
        LEFT,
        TOP,
        HIDDEN
    }

    public static class Values {
        public final ForgeConfigSpec.BooleanValue showArtifactHud;
        public final ForgeConfigSpec.BooleanValue useDefaultArtifactHudPosition;
        public final ForgeConfigSpec.EnumValue<RecruitTooltipPosition> recruitTooltipPosition;
        public final ForgeConfigSpec.BooleanValue showTotemAreaVisual;

        Values(ForgeConfigSpec.Builder builder) {
            builder.push("hud");

            showArtifactHud = builder
                    .comment("Shows the equipped activatable artifact on the HUD.")
                    .define("showArtifactHud", true);

            useDefaultArtifactHudPosition = builder
                    .comment(
                            "Uses Raidborn's default artifact HUD position.",
                            "Turn this off to place the artifact slot near the offhand slot."
                    )
                    .define("useDefaultArtifactHudPosition", true);

            recruitTooltipPosition = builder
                    .comment(
                            "Position of the recruitable entity tooltip shown while holding Shift and looking at a recruitable mob.",
                            "RIGHT = right side of the crosshair.",
                            "LEFT = left side of the crosshair.",
                            "TOP = centered at the top of the screen.",
                            "HIDDEN = disables the recruitable tooltip."
                    )
                    .defineEnum("recruitTooltipPosition", RecruitTooltipPosition.RIGHT);

            builder.pop();

            builder.push("visuals");

            showTotemAreaVisual = builder
                    .comment("Shows the area preview when a totem artifact is activated.")
                    .define("showTotemAreaVisual", true);

            builder.pop();
        }
    }

    public static boolean showArtifactHud() {
        return VALUES.showArtifactHud.get();
    }

    public static boolean useDefaultArtifactHudPosition() {
        return VALUES.useDefaultArtifactHudPosition.get();
    }

    public static RecruitTooltipPosition recruitTooltipPosition() {
        return VALUES.recruitTooltipPosition.get();
    }

    public static boolean showTotemAreaVisual() {
        return VALUES.showTotemAreaVisual.get();
    }
}
