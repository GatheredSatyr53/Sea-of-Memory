package com.seaofmemory.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A person-shaped thing that has forgotten how to stand: hunched from the waist, head lolling on a broken neck,
 * arms hanging dead, one leg dragging. Every so often the head jerks.
 * <p>
 * Same texture layout as a player, but the torso bends at the waist and carries the head and arms with it,
 * which a vanilla humanoid model cannot do.
 */
public class SnowPersonModel extends EntityModel<SnowPersonRenderState> {
    private static final float HUNCH = 0.35f;
    private static final float NECK_TILT = 0.35f;
    private static final float WALK_CYCLE = 0.6662f;
    // Head twitches: one chance per this many ticks, lasting a fraction of it.
    private static final int TWITCH_PERIOD = 30;
    private static final float TWITCH_LENGTH = 0.15f;

    private final ModelPart waist;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public SnowPersonModel(ModelPart root) {
        super(root);
        waist = root.getChild("waist");
        head = waist.getChild("head");
        rightArm = waist.getChild("right_arm");
        leftArm = waist.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Pivots at the hips instead of the neck, so bending the torso bends the whole upper body.
        PartDefinition waist = root.addOrReplaceChild("waist", CubeListBuilder.create().texOffs(16, 16).addBox(-4, -12, -2, 8, 12, 4), PartPose.offset(0, 12, 0));
        waist.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.offset(0, -12, 0));
        waist.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4), PartPose.offset(-5, -10, 0));
        waist.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, -10, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(-1.9f, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(1.9f, 12, 0));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(SnowPersonRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        float pos = state.walkAnimationPos * WALK_CYCLE;
        float speed = state.walkAnimationSpeed;

        // Hunched and listing, breathing without breathing.
        waist.xRot = HUNCH + Mth.sin(age * 0.05f) * 0.03f;
        waist.zRot = 0.08f;

        // The head juts forward and hangs to one side; it only half follows what it looks at.
        head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5f - HUNCH * 0.6f;
        head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.6f;
        head.zRot = NECK_TILT;
        twitch(state, age);

        if (state.aggressive) {
            // Reaching for whoever it wants, fingers trembling.
            rightArm.xRot = -1.35f - HUNCH + Mth.sin(age * 1.7f) * 0.05f;
            leftArm.xRot = -1.25f - HUNCH + Mth.sin(age * 1.9f + 1f) * 0.05f;
            rightArm.zRot = -0.05f;
            leftArm.zRot = 0.1f;
        } else {
            // Dead weight: the arms hang straight down whatever the torso does, swinging loosely and out of step.
            rightArm.xRot = -HUNCH * 0.8f + Mth.cos(pos + Mth.PI) * 0.35f * speed + Mth.sin(age * 0.07f) * 0.04f;
            leftArm.xRot = -HUNCH * 0.8f + Mth.cos(pos + 0.4f) * 0.25f * speed + Mth.sin(age * 0.06f + 2f) * 0.04f;
            rightArm.zRot = 0.08f;
            leftArm.zRot = -0.12f;
        }

        // A long stride and a dragged one.
        rightLeg.xRot = Mth.cos(pos) * 0.9f * speed;
        leftLeg.xRot = Mth.cos(pos + Mth.PI) * 0.55f * speed;
        leftLeg.zRot = -0.06f;
    }

    /**
     * Now and then the head snaps to one side and settles back. Each snow person twitches at its own moments.
     */
    private void twitch(SnowPersonRenderState state, float age) {
        int slot = Mth.floor(age / TWITCH_PERIOD);
        int hash = Mth.murmurHash3Mixer(state.seed * 31 + slot);
        if ((hash & 3) != 0) {
            return;
        }
        float phase = (age - slot * TWITCH_PERIOD) / TWITCH_PERIOD;
        if (phase > TWITCH_LENGTH) {
            return;
        }
        float strength = 1f - phase / TWITCH_LENGTH;
        float direction = (hash & 4) == 0 ? 1f : -1f;
        head.zRot += direction * 0.6f * strength;
        head.yRot += direction * 0.4f * strength;
    }
}
