package me.geza3d.toldi.mixin;

import me.geza3d.toldi.ToldiPlus;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mojang.blaze3d.systems.RenderSystem.blendFunc;

@Mixin(ToldiPlusMixins.class)
public class ToldiPlusMixins {

    @Mixin(BufferBuilder.class)
    public abstract static class BufferBuilderMixin {

        @Inject(method = "color", at = @At("HEAD"), cancellable = true)
        public void onVertexColor(int red, int green, int blue, int alpha, CallbackInfoReturnable<VertexConsumer> info) {
            if(ToldiPlus.RenderUtil.hasRenderColor() && Thread.currentThread().getName().equals("Render thread")) {
                if(ToldiPlus.RenderUtil.redOverride != null) red = ToldiPlus.RenderUtil.redOverride;
                if(ToldiPlus.RenderUtil.greenOverride != null) green = ToldiPlus.RenderUtil.greenOverride;
                if(ToldiPlus.RenderUtil.blueOverride != null) blue = ToldiPlus.RenderUtil.blueOverride;
                if(ToldiPlus.RenderUtil.alphaOverride != null) alpha = ToldiPlus.RenderUtil.alphaOverride;
                BufferVertexConsumer This = (BufferVertexConsumer)(Object) this;
                VertexFormatElement vertexFormatElement = This.getCurrentElement();
                if (vertexFormatElement.getType() != VertexFormatElement.Type.COLOR) {
                    info.setReturnValue(This);
                    info.cancel();
                }
                This.putByte(0, (byte)red);
                This.putByte(1, (byte)green);
                This.putByte(2, (byte)blue);
                This.putByte(3, (byte)alpha);
                This.nextElement();
                info.setReturnValue(This);
                info.cancel();
            }
        }
    }

    @Mixin(ClientConnection.class)
    public static class ClientConnectionMixin {

        @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true)
        private static void onPacketIn(Packet<?> packet, PacketListener listener, CallbackInfo info) {
            ActionResult result = ToldiPlus.PacketCallback.IN.invoker().packet(packet);

            if(result == ActionResult.FAIL) {
                info.cancel();
            }
        }

        @Inject(method = "send", at = @At("HEAD"), cancellable = true)
        private void onPacketOut(Packet<?> packet, CallbackInfo info) {
            ActionResult result = ToldiPlus.PacketCallback.OUT.invoker().packet(packet);

            if(result == ActionResult.FAIL) {
                info.cancel();
            }
        }
    }

    @Mixin(targets = "net.minecraft.client.world.ClientWorld$ClientEntityHandler")
    public static class ClientEntityHandlerMixin {

        @Inject(method = "stopTicking", at = @At("HEAD"), cancellable = true)
        public void onStopTicking(Entity entity, CallbackInfo info) {
            ActionResult result = ToldiPlus.EntityCallback.STOP_TICKING.invoker().tick(entity);

            if(result == ActionResult.FAIL) {
                info.cancel();
            }
        }
    }

    @Mixin(ClientPlayerEntity.class)
    public static class ClientPlayerEntityMixin {

        @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
        private void onSendMovementPacketsPre(CallbackInfo info) {
            ActionResult result = ToldiPlus.SendMovementPacketsCallback.PRE.invoker().sendPackets((ClientPlayerEntity)(Object)this);
            if(result == ActionResult.FAIL) info.cancel();
        }

        @Inject(method = "sendMovementPackets", at = @At("RETURN"))
        private void onSendMovementPacketsPost(CallbackInfo info) {
            ToldiPlus.SendMovementPacketsCallback.POST.invoker().sendPackets((ClientPlayerEntity)(Object)this);
        }
    }

    @Mixin(Entity.class)
    public static class EntityMixin {

        @Inject(method = "tick()V", at = @At("HEAD"))
        public void onTick(CallbackInfo info) {
            ToldiPlus.EntityCallback.TICK.invoker().tick((Entity) (Object) this);
        }

        @Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"), cancellable = true)
        public void onChangeLookDirection(double x, double y, CallbackInfo info) {
            ActionResult result = ToldiPlus.EntityChangeLookDirectionCallback.EVENT.invoker().tick((Entity) (Object) this,x,y);

            if(result == ActionResult.FAIL) {
                info.cancel();
            }
        }
    }

    @Mixin(EntityRenderDispatcher.class)
    public static class EntityRenderDispatcherMixin {

        @Inject(method = "render", at = @At("HEAD"), cancellable = true)
        public void onRenderPre(Entity entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo info) {
            ActionResult result = ToldiPlus.RenderEntityCallback.PRE.invoker().render((EntityRenderDispatcher) (Object) this, entity, x, y, z, tickDelta, matrices, vertexConsumers, light);
            if(result == ActionResult.FAIL) info.cancel();
        }

        @Inject(method = "render", at = @At("RETURN"))
        public void onRenderPost(Entity entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo info) {
            ToldiPlus.RenderEntityCallback.POST.invoker().render((EntityRenderDispatcher) (Object) this, entity, x, y, z, tickDelta, matrices, vertexConsumers, light);
        }
    }

    @Mixin(GameRenderer.class)
    public static class GameRendererMixin {

        @ModifyVariable(method = "shouldRenderBlockOutline", at = @At(value = "STORE", ordinal = 0))
        public Entity onBlockOutline(Entity entity) {
            if(ToldiPlus.CameraSpoofCallback.EVENT.invoker().spoof() == ActionResult.FAIL) {
                return ToldiPlus.CLIENT.player;
            }
            return entity;
        }
    }

    @Mixin(InGameHud.class)
    public static class InGameHudMixin {

        @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
        private void onGetCameraPlayer(CallbackInfoReturnable<PlayerEntity> info) {
            ActionResult result = ToldiPlus.CameraSpoofCallback.CAMERAPLAYER.invoker().spoof();

            if(result == ActionResult.FAIL) {
                info.setReturnValue(ToldiPlus.CLIENT.player);
                info.cancel();
            }
        }
    }

    @Mixin(Keyboard.class)
    public static class KeyboardMixin {

        @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
        public void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info) {
            if(ToldiPlus.KeyCallback.EVENT.invoker().press(key, action) == ActionResult.FAIL) {
                info.cancel();
            }
        }
    }

    @Mixin(LightmapTextureManager.class)
    public static class LightmapTextureManagerMixin {

        @Inject(method = "getBrightness", at = @At(value = "HEAD"), cancellable = true)
        private void onGetBrightness(World world, int lightLevel, CallbackInfoReturnable<Float> info) {
            if(ToldiPlus.BrightnessCallback.EVENT.invoker().spoof() == ActionResult.FAIL) {
                info.setReturnValue(1000f);
                info.cancel();
            }

        }
    }

    @Mixin(MinecraftClient.class)
    public static class MinecraftClientMixin {

        @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
        public void onDisconnection(Screen screen, CallbackInfo info) {
            if(ToldiPlus.CLIENT.world != null) ToldiPlus.JoinLeaveCallback.LEAVE.invoker().handle();
        }

        @Inject(method = "<init>", at = @At("TAIL"))
        public void onInit(RunArgs args, CallbackInfo info) {
            ToldiPlus.InitCallback.EVENT.invoker().init();
        }
    }

    @Mixin(PlayerEntity.class)
    public static class PlayerEntityMixin {

        @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
        public void onTravel(Vec3d movementInput, CallbackInfo info) {
            Entity This = (Entity)(Object)this;
            ActionResult result = ToldiPlus.EntityMoveCallback.PLAYER_FIRST.invoker().move(This, movementInput);
            if(result == ActionResult.FAIL) {
                info.cancel();
            }
        }
    }

    @Mixin(RenderLayer.class)
    public static class RenderLayerMixin {

        @Inject(method = "getEntityCutoutNoCull", at = @At("HEAD"), cancellable = true)
        private static void getEntityCutoutNoCull(Identifier texture, boolean affectsOutline, CallbackInfoReturnable<RenderLayer> info) {
            if(ToldiPlus.RenderUtil.entityCutoutNoCullOverride != null) {
                info.setReturnValue(ToldiPlus.RenderUtil.entityCutoutNoCullOverride);
                info.cancel();
            }
        }

        @Inject(method = "getOutline", at = @At("HEAD"), cancellable = true)
        private static void getOutline(Identifier texture, CallbackInfoReturnable<RenderLayer> info) {
            if(ToldiPlus.RenderUtil.outlineOverride != null) {
                info.setReturnValue(ToldiPlus.RenderUtil.outlineOverride);
                info.cancel();
            }
        }

        @Inject(method = "getEntityTranslucent", at = @At("HEAD"), cancellable = true)
        private static void getEntityTranslucent(Identifier texture, boolean affectsOutline, CallbackInfoReturnable<RenderLayer> info) {
            if(ToldiPlus.RenderUtil.entityTranslucentOverride != null) {
                info.setReturnValue(ToldiPlus.RenderUtil.entityTranslucentOverride);
                info.cancel();
            }
        }

        @Inject(method = "getItemEntityTranslucentCull", at = @At("HEAD"), cancellable = true)
        private static void getItemEntityTranslucentCull(Identifier texture, CallbackInfoReturnable<RenderLayer> info) {
            if(ToldiPlus.RenderUtil.itemEntityTranslucentCull != null) {
                info.setReturnValue(ToldiPlus.RenderUtil.itemEntityTranslucentCull);
                info.cancel();
            }
        }
    }

    @Mixin(RenderLayers.class)
    public static class RenderLayersMixin {

        @Inject(method = "getItemLayer", at = @At("HEAD"), cancellable = true)
        private static void getItemLayer(ItemStack stack, boolean direct, CallbackInfoReturnable<RenderLayer> info) {
            if(ToldiPlus.RenderUtil.itemEntityTranslucentCull != null) {
                info.setReturnValue(ToldiPlus.RenderUtil.itemEntityTranslucentCull);
                info.cancel();
            }
        }
    }

    @Mixin(ServerPlayerEntity.class)
    public static class ServerPlayerEntityMixin {

        @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
        public void onTick(CallbackInfo info) {
            ActionResult result = ToldiPlus.ServerPlayerEntityCallback.TICK.invoker().tick();

            if(result == ActionResult.FAIL) {
                info.cancel();
            }
        }

    }

    @Mixin(WorldRenderer.class)
    public static class WorldRendererMixin {

        @Inject(method = "render", at = @At("HEAD"))
        public void onRenderFirst(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix, CallbackInfo info) {
            ToldiPlus.RenderCallback.FIRST.invoker().render(matrices, tickDelta, camera);
        }

        @Inject(method = "render", at = @At("TAIL"))
        public void onRenderLast(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix, CallbackInfo info) {
            ToldiPlus.RenderCallback.LAST.invoker().render(matrices, tickDelta, camera);
        }

        @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;drawCurrentLayer()V"))
        public void onRenderEntities(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix, CallbackInfo info) {
            ToldiPlus.RenderCallback.ENTITIES.invoker().render(matrices, tickDelta, camera);
        }
    }

}
