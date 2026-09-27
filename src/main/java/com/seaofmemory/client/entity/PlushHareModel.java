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
 * A balding stuffed hare: a fat body on four stubby legs, a head with long ears.
 * Eight spider legs are hidden inside it until it tears open.
 */
public class PlushHareModel extends EntityModel<PlushHareRenderState> {
    private static final float QUARTER = (float) (Math.PI / 4);
    private static final float EIGHTH = (float) (Math.PI / 8);
    private static final String[] SPIDER_LEGS = {
            "right_hind_leg", "left_hind_leg", "right_middle_hind_leg", "left_middle_hind_leg",
            "right_middle_front_leg", "left_middle_front_leg", "right_front_leg", "left_front_leg"};

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightEar;
    private final ModelPart leftEar;
    private final ModelPart[] stubs;
    private final ModelPart[] spiderLegs;

    public PlushHareModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = root.getChild("head");
        rightEar = head.getChild("right_ear");
        leftEar = head.getChild("left_ear");
        stubs = new ModelPart[] {
                root.getChild("right_front_stub"), root.getChild("left_front_stub"),
                root.getChild("right_hind_stub"), root.getChild("left_hind_stub")};
        spiderLegs = new ModelPart[SPIDER_LEGS.length];
        for (int i = 0; i < SPIDER_LEGS.length; i++) {
            spiderLegs[i] = root.getChild(SPIDER_LEGS[i]);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -5, -6, 10, 10, 12), PartPose.offset(0, 14, 0));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 22).addBox(-4, -7, -7, 8, 7, 7), PartPose.offset(0, 9, -5));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(30, 22).addBox(-1, -9, -0.5f, 2, 9, 1),
                PartPose.offsetAndRotation(-2, -7, -3.5f, 0, 0, -0.15f));
        // One ear has lost its wire and flops over.
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(36, 22).addBox(-1, -9, -0.5f, 2, 9, 1),
                PartPose.offsetAndRotation(2, -7, -3.5f, -0.3f, 0, 1.1f));

        // Attached right at the underside of the body (y = 19) and reaching the ground (y = 24).
        CubeListBuilder stub = CubeListBuilder.create().texOffs(44, 0).addBox(-1.5f, 0, -1.5f, 3, 5, 3);
        root.addOrReplaceChild("right_front_stub", stub, PartPose.offset(-3.5f, 19, -4));
        root.addOrReplaceChild("left_front_stub", stub, PartPose.offset(3.5f, 19, -4));
        root.addOrReplaceChild("right_hind_stub", stub, PartPose.offset(-3.5f, 19, 4));
        root.addOrReplaceChild("left_hind_stub", stub, PartPose.offset(3.5f, 19, 4));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(44, 8).addBox(-1.5f, -1.5f, 0, 3, 3, 2), PartPose.offset(0, 12, 6));

        // Same layout as a spider's legs, bursting out of the sides of the body.
        CubeListBuilder right = CubeListBuilder.create().texOffs(0, 40).addBox(-15, -1, -1, 16, 2, 2);
        CubeListBuilder left = CubeListBuilder.create().texOffs(0, 40).mirror().addBox(-1, -1, -1, 16, 2, 2);
        float y = 14;
        root.addOrReplaceChild("right_hind_leg", right, PartPose.offsetAndRotation(-5, y, 4, 0, QUARTER, -QUARTER));
        root.addOrReplaceChild("left_hind_leg", left, PartPose.offsetAndRotation(5, y, 4, 0, -QUARTER, QUARTER));
        root.addOrReplaceChild("right_middle_hind_leg", right, PartPose.offsetAndRotation(-5, y, 1.5f, 0, EIGHTH, -0.58f));
        root.addOrReplaceChild("left_middle_hind_leg", left, PartPose.offsetAndRotation(5, y, 1.5f, 0, -EIGHTH, 0.58f));
        root.addOrReplaceChild("right_middle_front_leg", right, PartPose.offsetAndRotation(-5, y, -1.5f, 0, -EIGHTH, -0.58f));
        root.addOrReplaceChild("left_middle_front_leg", left, PartPose.offsetAndRotation(5, y, -1.5f, 0, EIGHTH, 0.58f));
        root.addOrReplaceChild("right_front_leg", right, PartPose.offsetAndRotation(-5, y, -4, 0, -QUARTER, -QUARTER));
        root.addOrReplaceChild("left_front_leg", left, PartPose.offsetAndRotation(5, y, -4, 0, QUARTER, QUARTER));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(PlushHareRenderState state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;

        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        // Stubby legs paddle; the whole toy rocks from side to side as it goes.
        for (int i = 0; i < stubs.length; i++) {
            stubs[i].xRot = Mth.cos(pos * 0.6662f + (i % 2 == 0 ? 0 : Mth.PI)) * 1.2f * speed;
        }
        body.zRot = Mth.sin(pos * 0.33f) * 0.12f * speed;
        rightEar.xRot = Mth.sin(state.ageInTicks * 0.08f) * 0.1f;
        leftEar.zRot += Mth.sin(state.ageInTicks * 0.11f) * 0.08f;

        for (ModelPart leg : spiderLegs) {
            leg.visible = state.transformed;
        }
        if (state.transformed) {
            animateSpiderLegs(pos, speed);
        }
    }

    private void animateSpiderLegs(float pos, float speed) {
        float cycle = pos * 0.6662f * 2;
        for (int pair = 0; pair < 4; pair++) {
            float phase = pair * Mth.HALF_PI;
            float swing = -Mth.cos(cycle + phase) * 0.4f * speed;
            float step = Math.abs(Mth.sin(pos * 0.6662f + phase)) * 0.4f * speed;
            ModelPart right = spiderLegs[pair * 2];
            ModelPart left = spiderLegs[pair * 2 + 1];
            right.yRot += swing;
            left.yRot -= swing;
            right.zRot += step;
            left.zRot -= step;
        }
    }
}
