package com.shouyun.bosslandlord.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** The vanilla dragon parts in a chair pose, in model pixels relative to the cushion. */
final class SeatedDragonPose {
    private static final Vec3[] NECK = {
            new Vec3(0, -63, -10), new Vec3(0, -72, -13), new Vec3(0, -81, -16),
            new Vec3(0, -88, -22), new Vec3(0, -94, -31)
    };
    private static final Vec3[] TAIL = {
            new Vec3(0, -13, 13), new Vec3(7, -12, 18), new Vec3(17, -10, 19),
            new Vec3(27, -6, 19), new Vec3(36, 1, 18), new Vec3(41, 10, 15),
            new Vec3(43, 20, 10), new Vec3(43, 31, 4), new Vec3(41, 41, -2),
            new Vec3(36, 45, -9), new Vec3(27, 46, -11)
    };

    private final ModelPart spine;
    private final List<ModelPart> posedParts = new ArrayList<>();
    private final List<Segment> spineSegments = new ArrayList<>();

    SeatedDragonPose(ModelPart root) {
        spine = root.getChild("neck");
        ModelPart body = root.getChild("body");
        body.setPos(0, -53, 4);
        body.xRot = -1.28F; // Rotate the shoulders up while the rump rests on the cushion.
        posedParts.add(body);

        ModelPart head = root.getChild("head");
        head.setPos(0, -94, -31);
        head.xRot = .12F;
        head.yRot = .14F;
        head.getChild("jaw").xRot = .04F;
        posedParts.add(head);

        poseSide(root, "left", 1);
        poseSide(root, "right", -1);
        addSegments(NECK, 1F, .9F);
        addSegments(TAIL, 1F, .36F);
    }

    private void poseSide(ModelPart root, String side, int direction) {
        ModelPart wing = root.getChild(side + "_wing");
        wing.setPos(direction * 12, -62, -3);
        wing.xRot = .32F;
        wing.yRot = direction * -.40F;
        wing.zRot = direction * 1.38F;
        wing.xScale = .43F;
        wing.yScale = .65F;
        wing.zScale = .32F;
        ModelPart wingTip = wing.getChild(side + "_wing_tip");
        wingTip.xRot = .20F;
        wingTip.yRot = direction * .60F;
        wingTip.zRot = direction * .16F;
        posedParts.add(wing);

        ModelPart arm = root.getChild(side + "_front_leg");
        arm.setPos(direction * 11, -60.25F, -20);
        arm.xRot = -.62F;
        ModelPart forearm = arm.getChild(side + "_front_leg_tip");
        forearm.xRot = -.70F;
        forearm.getChild(side + "_front_foot").xRot = 1.32F;
        posedParts.add(arm);

        ModelPart thigh = root.getChild(side + "_hind_leg");
        thigh.setPos(direction * 13.5F, -12, -7);
        thigh.xRot = -1.50F;
        ModelPart shin = thigh.getChild(side + "_hind_leg_tip");
        shin.xRot = 1.15F;
        shin.getChild(side + "_hind_foot").xRot = .35F;
        posedParts.add(thigh);
    }

    private void addSegments(Vec3[] points, float startGirth, float endGirth) {
        int count = points.length - 1;
        for (int i = 0; i < count; i++) {
            Vec3 start = points[i];
            Vec3 end = points[i + 1];
            Vec3 direction = end.subtract(start);
            float pitch = (float)Math.atan2(direction.y, direction.horizontalDistance());
            float yaw = (float)Math.atan2(-direction.x, -direction.z);
            float girth = startGirth + (endGirth - startGirth) * (i + .5F) / count;
            // Overlap the ends of the vanilla ten-pixel neck cubes at each joint.
            float length = (float)(direction.length() / 10 + .16);
            spineSegments.add(new Segment(start.add(end).scale(.5 / 16), yaw, pitch, girth, length));
        }
    }

    void render(PoseStack pose, VertexConsumer vertices, int light) {
        for (ModelPart part : posedParts) part.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
        for (Segment segment : spineSegments) {
            pose.pushPose();
            pose.translate(segment.center.x, segment.center.y, segment.center.z);
            pose.mulPose(Axis.YP.rotation(segment.yaw));
            pose.mulPose(Axis.XP.rotation(segment.pitch));
            pose.scale(segment.girth, segment.girth, segment.length);
            spine.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private record Segment(Vec3 center, float yaw, float pitch, float girth, float length) {}
}
