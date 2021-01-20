package com.miskatonicmysteries.common.feature.recipe.rite;

import com.miskatonicmysteries.client.render.RenderHelper;
import com.miskatonicmysteries.client.render.ResourceHandler;
import com.miskatonicmysteries.common.block.blockentity.OctagramBlockEntity;
import com.miskatonicmysteries.common.item.IncantationYogItem;
import com.miskatonicmysteries.common.lib.Constants;
import com.miskatonicmysteries.common.lib.ModObjects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;

public class TeleportRite extends Rite {
    private final int ticksNeeded;

    public TeleportRite() { //todo proper ingredient for this
        super(new Identifier(Constants.MOD_ID, "teleport"), null, 0, Ingredient.ofItems(ModObjects.INCANTATION_YOG), Ingredient.ofItems(Items.ENDER_PEARL), Ingredient.ofItems(Items.ENDER_EYE), Ingredient.ofItems(ModObjects.OCEANIC_GOLD));
        ticksNeeded = 60;
    }

    @Override
    public boolean canCast(OctagramBlockEntity octagram) {
        if (super.canCast(octagram)) {
            if (octagram.getWorld().isClient) {
                return true;
            }
            PlayerEntity caster = octagram.getOriginalCaster();
            ItemStack incantation = octagram.getStack(ModObjects.INCANTATION_YOG);
            if (!incantation.isEmpty() && IncantationYogItem.getPosition(incantation) != null && IncantationYogItem.getWorld((ServerWorld) octagram.getWorld(), incantation) != null) {
                BlockPos octagramPos = IncantationYogItem.getPosition(incantation);
                ServerWorld boundWorld = IncantationYogItem.getWorld((ServerWorld) octagram.getWorld(), incantation);
                if (!(boundWorld.getBlockEntity(octagramPos) instanceof OctagramBlockEntity)) {
                    caster.sendMessage(new TranslatableText("message.miskatonicmysteries.invalid_octagram.not_present"), true);
                    return false;
                }
                if (((OctagramBlockEntity) boundWorld.getBlockEntity(octagramPos)).getAffiliation(false) != octagram.getAffiliation(false)) {
                    caster.sendMessage(new TranslatableText("message.miskatonicmysteries.invalid_octagram.bad_affiliation"), true);
                    return false;
                }
                if (((OctagramBlockEntity) boundWorld.getBlockEntity(octagramPos)).boundPos != null) {
                    caster.sendMessage(new TranslatableText("message.miskatonicmysteries.invalid_octagram.already_bound"), true);
                    return false;
                }
                return true;
            } else if (caster != null) {
                caster.sendMessage(new TranslatableText("message.miskatonicmysteries.invalid_incantation"), true);
                return false;
            }
        }
        return false;
    }


    @Override
    public void tick(OctagramBlockEntity octagram) {
        if (!isFinished(octagram) && !octagram.permanentRiteActive) {
            super.tick(octagram);
        }
    }

    @Override
    public void onFinished(OctagramBlockEntity octagram) {
        if (!octagram.getWorld().isClient) {
            ServerWorld world = (ServerWorld) octagram.getWorld();
            octagram.tickCount = 0;
            ItemStack incantation = octagram.getStack(ModObjects.INCANTATION_YOG);
            if (!incantation.isEmpty()) {
                BlockPos octagramPos = IncantationYogItem.getPosition(incantation);
                ServerWorld boundWorld = IncantationYogItem.getWorld(world, incantation);
                if (boundWorld.getBlockEntity(octagramPos) instanceof OctagramBlockEntity) {
                    octagram.bind(boundWorld, octagramPos);
                    OctagramBlockEntity otherOctagram = (OctagramBlockEntity) boundWorld.getBlockEntity(octagramPos);
                    otherOctagram.bind(world, octagram.getPos());
                    otherOctagram.permanentRiteActive = true;
                    otherOctagram.currentRite = this;
                    otherOctagram.tickCount = 0;
                    otherOctagram.markDirty();
                }
            }
        }
        super.onFinished(octagram);
    }

    @Override
    public void onCancelled(OctagramBlockEntity octagram) {
        OctagramBlockEntity otherOctagram = getBoundOctagram(octagram);
        if (!octagram.getWorld().isClient && otherOctagram != null) {
            otherOctagram.permanentRiteActive = false;
            otherOctagram.currentRite = null;
            otherOctagram.tickCount = 0;
            octagram.boundPos = null;
            otherOctagram.boundPos = null;
            otherOctagram.markDirty();
            octagram.markDirty();
        }
        super.onCancelled(octagram);
    }


    public static OctagramBlockEntity getBoundOctagram(OctagramBlockEntity octagram) {
        BlockPos octagramPos = octagram.getBoundPos();
        ServerWorld boundWorld = octagram.getBoundDimension();
        if (octagramPos != null && boundWorld != null) {
            BlockEntity be = boundWorld.getBlockEntity(octagramPos);
            if (be instanceof OctagramBlockEntity) {
                return (OctagramBlockEntity) be;
            }
        }
        return null;
    }

    @Override
    public boolean shouldContinue(OctagramBlockEntity octagram) {
        return super.shouldContinue(octagram);
    }

    @Override
    public boolean isFinished(OctagramBlockEntity octagram) {
        return octagram.tickCount >= ticksNeeded;
    }

    @Override
    public boolean isPermanent(OctagramBlockEntity octagram) {
        return true;
    }

    @Override
    public byte beforeRender(OctagramBlockEntity entity, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light, int overlay, BlockEntityRenderDispatcher dispatcher) {
        return super.beforeRender(entity, tickDelta, matrixStack, vertexConsumers, light, overlay, dispatcher);
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void renderRite(OctagramBlockEntity entity, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light, int overlay, BlockEntityRenderDispatcher dispatcher) {
        Sprite sprite = ResourceHandler.getOctagramMaskTextureFor(entity).getSprite();
        float alpha = entity.permanentRiteActive ? 1 : entity.tickCount / (float) ticksNeeded;
        float[] origColors = entity.getAffiliation(true).getColor();
        float[] colors = {origColors[0], origColors[1], origColors[2], alpha};
        matrixStack.push();
        matrixStack.translate(0, 0.001F, 0);
        RenderHelper.renderTexturedPlane(3, sprite, matrixStack, sprite.getTextureSpecificVertexConsumer(vertexConsumers.getBuffer(alpha < 1 ? RenderHelper.getTransparency() : RenderLayer.getCutout())), light, overlay, new float[]{1, 1, 1, alpha});
        matrixStack.push();
        Matrix4f matrix4f = matrixStack.peek().getModel();
        double distance = entity.getPos().getSquaredDistance(dispatcher.camera.getPos(), true);
        int renderDepth = Math.max(RenderHelper.getDepthFromDistance(distance) - 14, 1);
        matrixStack.translate(1.5, 0, 1.5);
        matrixStack.multiply(Vector3f.NEGATIVE_Y.getDegreesQuaternion(45));
        matrixStack.translate(-0.4, 0.001F, -0.4);
        for (int i = 0; i < renderDepth; i++) {
            RenderHelper.renderPortalLayer(11 + i, entity.getWorld(), matrix4f, vertexConsumers, 0.8F, colors);
        }
        matrixStack.pop();
        matrixStack.translate(1.5, 0, 1.5);
        matrixStack.multiply(Vector3f.POSITIVE_Y.getRadialQuaternion(((float) entity.getWorld().getTime() + tickDelta) / 20.0F));
        matrixStack.translate(-1.5F, 0.0025F, -1.5F);
        RenderHelper.renderTexturedPlane(3, ResourceHandler.AURA_SPRITE.getSprite(), matrixStack, vertexConsumers.getBuffer(RenderHelper.getAuraGlowLayer()), light, overlay, colors);
        matrixStack.pop();
    }
}
