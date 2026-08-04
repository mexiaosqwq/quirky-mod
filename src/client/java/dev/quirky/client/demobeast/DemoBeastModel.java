package dev.quirky.client.demobeast;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

public class DemoBeastModel extends EntityModel<DemoBeastRenderState> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(Identifier.fromNamespaceAndPath("quirky", "demo_beast"), "main");

	private final KeyframeAnimation walkAnimation;
	private final KeyframeAnimation idleAnimation;
	private final KeyframeAnimation tailWagAnimation;

	public DemoBeastModel(final ModelPart root) {
		super(root.getChild("root"));
		ModelPart modelRoot = root.getChild("root");
		this.walkAnimation = DemoBeastAnimations.WALK.bake(modelRoot);
		this.idleAnimation = DemoBeastAnimations.IDLE.bake(modelRoot);
		this.tailWagAnimation = DemoBeastAnimations.TAIL_WAG.bake(modelRoot);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition partroot = root.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		PartDefinition partbody = partroot.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, -4.0F, 0.0F));
		PartDefinition parthead = partbody.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, -4.0F, -5.0F));
		parthead.addOrReplaceChild("head_box", CubeListBuilder.create().texOffs(38, 6).addBox(-3.0F, -8.0F, -5.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
		parthead.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(34, 12).addBox(-2.5F, -11.0F, -4.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);
		parthead.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(34, 12).addBox(0.5F, -11.0F, -4.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);
		PartDefinition partleg_front_l = partbody.addOrReplaceChild("leg_front_l", CubeListBuilder.create(), PartPose.offset(3.5F, 0.0F, -4.0F));
		partleg_front_l.addOrReplaceChild("leg_front_l_box", CubeListBuilder.create().texOffs(2, 18).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);
		PartDefinition partleg_front_r = partbody.addOrReplaceChild("leg_front_r", CubeListBuilder.create(), PartPose.offset(-3.5F, 0.0F, -4.0F));
		partleg_front_r.addOrReplaceChild("leg_front_r_box", CubeListBuilder.create().texOffs(10, 18).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);
		PartDefinition partleg_back_l = partbody.addOrReplaceChild("leg_back_l", CubeListBuilder.create(), PartPose.offset(3.5F, 0.0F, 4.0F));
		partleg_back_l.addOrReplaceChild("leg_back_l_box", CubeListBuilder.create().texOffs(2, 22).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);
		PartDefinition partleg_back_r = partbody.addOrReplaceChild("leg_back_r", CubeListBuilder.create(), PartPose.offset(-3.5F, 0.0F, 4.0F));
		partleg_back_r.addOrReplaceChild("leg_back_r_box", CubeListBuilder.create().texOffs(10, 22).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);
		PartDefinition parttail = partbody.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(0.0F, -2.0F, 6.0F));
		parttail.addOrReplaceChild("tail_box", CubeListBuilder.create().texOffs(44, 4).addBox(-0.5F, -3.0F, 0.0F, 1.0F, 4.0F, 4.0F), PartPose.ZERO);
		partbody.addOrReplaceChild("body_box", CubeListBuilder.create().texOffs(12, 12).addBox(-4.0F, -8.0F, -6.0F, 8.0F, 8.0F, 12.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(final DemoBeastRenderState state) {
		super.setupAnim(state);
		this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.5F);
		this.idleAnimation.apply(state.idleAnimationState, state.ageInTicks);
		this.tailWagAnimation.apply(state.tailWagAnimationState, state.ageInTicks);
	}
}
