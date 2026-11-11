package com.shouyun.bosslandlord.client;

import com.shouyun.bosslandlord.world.SeatEntity;
import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WardenRenderer;
import net.minecraft.world.entity.monster.warden.Warden;

public final class SeatedWardenRenderer extends WardenRenderer {
    public SeatedWardenRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new WardenModel<Warden>(context.bakeLayer(ModelLayers.WARDEN)) {
            @Override public void setupAnim(Warden e, float stride, float speed, float age, float yaw, float pitch) {
                if (!(e.getVehicle() instanceof SeatEntity)) { super.setupAnim(e, stride, speed, age, yaw, pitch); return; }
                root().getAllParts().forEach(ModelPart::resetPose);
                leftLeg.xRot = -1.48F; rightLeg.xRot = -1.48F;
                leftLeg.yRot = -.15F; rightLeg.yRot = .15F;
                leftArm.xRot = -.7F; rightArm.xRot = -.7F;
                leftArm.zRot = -.12F; rightArm.zRot = .12F;
                head.xRot = .19F;
            }
        };
    }
}
