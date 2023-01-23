package me.geza3d.toldi;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import static com.mojang.blaze3d.systems.RenderSystem.*;
import static com.mojang.blaze3d.systems.RenderSystem.blendFunc;
import static org.lwjgl.opengl.GL11.*;

public class ToldiPlus implements ClientModInitializer {

    private static final String MODID = "toldiplus";
    private static final String NAME = "Toldi+";
    private static final Logger LOGGER = LogManager.getLogger(NAME);
    public static MinecraftClient CLIENT;
    private static TextRenderer TEXTRENDERER;

    @Override
    public void onInitializeClient() {
        CLIENT = MinecraftClient.getInstance();
        TEXTRENDERER = new TextRenderer(id -> {
            return CLIENT.textRenderer.getFontStorage(new Identifier(MODID, "code_new_roman"));
        });
        Modules.registerModules();
        ConfigHandler.initConfigHandler();
        KeyBindHandler.registerKeyBinds();
        HudHandler.initHudHandler();
        RotationHandler.initRotationHandler();
        ToldiShaders.loadShader(CLIENT);
    }

    public static interface BrightnessCallback {

        /**
         * Cancelling this will make the brightness 1000f, without changing its actual value.
         */
        Event<BrightnessCallback> EVENT = EventFactory.createArrayBacked(BrightnessCallback.class,
                listeners -> () -> {
                    for (BrightnessCallback listener : listeners) {
                        ActionResult result = listener.spoof();
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult spoof();
    }

    public static interface CameraSpoofCallback {

        /**
         * If its cancelled it will make the BlockOutline render regardless if the player is the camera or not.
         */
        public static final Event<CameraSpoofCallback> EVENT = EventFactory.createArrayBacked(CameraSpoofCallback.class,
                listeners -> () -> {
                    for (CameraSpoofCallback listener : listeners) {
                        ActionResult result = listener.spoof();
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        /**
         * Cancelling this will spoof the CameraPlayer to be the player and not the camera entity if the camera entity is a player.
         */
        Event<CameraSpoofCallback> CAMERAPLAYER = EventFactory.createArrayBacked(CameraSpoofCallback.class,
                listeners -> () -> {
                    for (CameraSpoofCallback listener : listeners) {
                        ActionResult result = listener.spoof();
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult spoof();
    }

    public static interface ChamsOverrideCallback {

        /**
         * If its cancelled it will help the rendering of Chams by disabling some render features of the game.
         */
        public static final Event<ChamsOverrideCallback> EVENT = EventFactory.createArrayBacked(ChamsOverrideCallback.class,
                listeners -> () -> {
                    for (ChamsOverrideCallback listener : listeners) {
                        ActionResult result = listener.override();
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult override();
    }

    public static interface EntityCallback {

        public static final Event<EntityCallback> TICK = EventFactory.createArrayBacked(EntityCallback.class,
                listeners -> entity -> {
                    for (EntityCallback listener : listeners) {
                        ActionResult result = listener.tick(entity);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        public static final Event<EntityCallback> STOP_TICKING = EventFactory.createArrayBacked(EntityCallback.class,
                listeners -> entity -> {
                    for (EntityCallback listener : listeners) {
                        ActionResult result = listener.tick(entity);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult tick(Entity entity);
    }

    public static interface EntityChangeLookDirectionCallback {

        public static final Event<EntityChangeLookDirectionCallback> EVENT = EventFactory.createArrayBacked(EntityChangeLookDirectionCallback.class,
                listeners -> (entity, x, y) -> {
                    for (EntityChangeLookDirectionCallback listener : listeners) {
                        ActionResult result = listener.tick(entity, x, y);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult tick(Entity entity, double cursorDeltaX, double cursorDeltaY);
    }

    public static interface EntityMoveCallback {

        public static final Event<EntityMoveCallback> PLAYER_FIRST = EventFactory.createArrayBacked(EntityMoveCallback.class,
                listeners -> (entity, input) -> {
                    for (EntityMoveCallback listener : listeners) {
                        ActionResult result = listener.move(entity, input);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        public static final Event<EntityMoveCallback> PLAYER_LAST = EventFactory.createArrayBacked(EntityMoveCallback.class,
                listeners -> (entity, input) -> {
                    return ActionResult.SUCCESS;
                });

        ActionResult move(Entity entity, Vec3d input);
    }

    public static interface InitCallback {

        Event<InitCallback> EVENT = EventFactory.createArrayBacked(InitCallback.class,
                listeners -> () -> {
                    for (InitCallback listener : listeners) {
                        listener.init();
                    }
                });

        void init();
    }

    public static interface JoinLeaveCallback {

        public static final Event<JoinLeaveCallback> JOIN = EventFactory.createArrayBacked(JoinLeaveCallback.class,
                listeners -> () -> {
                    for (JoinLeaveCallback listener : listeners) {
                        ActionResult result = listener.handle();
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        public static final Event<JoinLeaveCallback> LEAVE = EventFactory.createArrayBacked(JoinLeaveCallback.class,
                listeners -> () -> {
                    for (JoinLeaveCallback listener : listeners) {
                        ActionResult result = listener.handle();
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult handle();
    }

    public static interface KeyCallback {

        public static final Event<KeyCallback> EVENT = EventFactory.createArrayBacked(KeyCallback.class,
                listeners -> (key, action) -> {
                    for (KeyCallback listener : listeners) {
                        ActionResult result = listener.press(key, action);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult press(int key, int action);
    }

    public static interface PacketCallback {

        public static final Event<PacketCallback> IN = EventFactory.createArrayBacked(PacketCallback.class,
                listeners -> packet -> {
                    for (PacketCallback listener : listeners) {
                        ActionResult result = listener.packet(packet);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        public static final Event<PacketCallback> OUT = EventFactory.createArrayBacked(PacketCallback.class,
                listeners -> packet -> {
                    for (PacketCallback listener : listeners) {
                        ActionResult result = listener.packet(packet);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult packet(Packet<?> packet);
    }

    public static interface RenderCallback {

        public static final Event<RenderCallback> FIRST = EventFactory.createArrayBacked(RenderCallback.class,
                listeners -> (matrices, tickDelta, camera) -> {
                    for (RenderCallback listener : listeners) {
                        ActionResult result = listener.render(matrices, tickDelta, camera);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        public static final Event<RenderCallback> LAST = EventFactory.createArrayBacked(RenderCallback.class,
                listeners -> (matrices, tickDelta, camera) -> {
                    for (RenderCallback listener : listeners) {
                        ActionResult result = listener.render(matrices, tickDelta, camera);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        public static final Event<RenderCallback> ENTITIES = EventFactory.createArrayBacked(RenderCallback.class,
                listeners -> (matrices, tickDelta, camera) -> {
                    for (RenderCallback listener : listeners) {
                        ActionResult result = listener.render(matrices, tickDelta, camera);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult render(MatrixStack matrices, float tickDelta, Camera camera);
    }

    public static interface RenderEntityCallback {

        public static final Event<RenderEntityCallback> PRE = EventFactory.createArrayBacked(RenderEntityCallback.class,
                listeners -> (dispatcher, entity, x, y, z, delta, matrices, consumer, light) -> {
                    for (RenderEntityCallback listener : listeners) {
                        ActionResult result = listener.render(dispatcher, entity, x, y, z, delta, matrices, consumer, light);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        public static final Event<RenderEntityCallback> POST = EventFactory.createArrayBacked(RenderEntityCallback.class,
                listeners -> (dispatcher, entity, x, y, z, delta, matrices, consumer, light) -> {
                    for (RenderEntityCallback listener : listeners) {
                        listener.render(dispatcher, entity, x, y, z, delta, matrices, consumer, light);
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult render(EntityRenderDispatcher dispatcher, Entity entity, double x, double y, double z, float delta, MatrixStack matrices, VertexConsumerProvider consumer, int light);
    }

    public static interface RotatingModuleCallback {

        public static final Event<RotatingModuleCallback> DISABLE = EventFactory.createArrayBacked(RotatingModuleCallback.class,
                listeners -> module -> {
                    for (RotatingModuleCallback listener : listeners) {
                        listener.handle(module);
                    }
                });

        void handle(ToldiRotatingModule module);
    }

    public static interface SendMovementPacketsCallback {

        public static final Event<SendMovementPacketsCallback> PRE = EventFactory.createArrayBacked(SendMovementPacketsCallback.class,
                listeners -> player -> {
                    for (SendMovementPacketsCallback listener : listeners) {
                        ActionResult result = listener.sendPackets(player);
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        public static final Event<SendMovementPacketsCallback> POST = EventFactory.createArrayBacked(SendMovementPacketsCallback.class,
                listeners -> player -> {
                    for (SendMovementPacketsCallback listener : listeners) {
                        listener.sendPackets(player);
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult sendPackets(ClientPlayerEntity player);
    }

    public static interface ServerPlayerEntityCallback {

        public static final Event<ServerPlayerEntityCallback> TICK = EventFactory.createArrayBacked(ServerPlayerEntityCallback.class,
                listeners -> () -> {
                    for (ServerPlayerEntityCallback listener : listeners) {
                        ActionResult result = listener.tick();
                        if (result == ActionResult.FAIL) return result;
                    }
                    return ActionResult.SUCCESS;
                });

        ActionResult tick();
    }

    public abstract static class ButtonPanel extends Panel {

        protected MainPanel main;
        protected String text;

        public ButtonPanel(MainPanel main, int x, int y, int width, int height, String text) {
            super(x, y, width, height);
            this.main = main;
            this.text = text;
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            y = unOffsettedY + main.scrollOffset;
            super.render(matrices, mouseX, mouseY, delta);
        }
    }

    public static class MainPanel extends Panel {

        boolean scrollable;
        public boolean renderBackground = true;
        protected int scrollOffset = 0;
        protected int desiredScrollOffset = 0;

        protected List<Panel> buttons = new ArrayList<>();

        public MainPanel(int x, int y, int width, int height, boolean scrollable) {
            super(x, y, width, height);
            this.scrollable = scrollable;
        }

        public MainPanel(int x, int y, int width, int height) {
            super(x, y, width, height);
            scrollable = false;
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            if (renderBackground)
                DrawableHelper.fill(matrices, x, unOffsettedY, x + width, unOffsettedY + height, new Color(0x360094, false).getRGB());
            for (Panel button : buttons) {
                button.render(matrices, mouseX, mouseY, delta);
            }
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            if (scrollOffset - desiredScrollOffset < -1) {
                scrollOffset += 2;
            } else if (scrollOffset - desiredScrollOffset > 1) {
                scrollOffset -= 2;
            }
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderUtil.glScissors(x, unOffsettedY, width, height);
            super.render(matrices, mouseX, mouseY, delta);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        public void handleScroll(int mouseX, int mouseY, int amount) {
            if (scrollable) {
                if (isMouseOver(mouseX, mouseY)) {
                    desiredScrollOffset += amount * 5;
                    desiredScrollOffset = MathHelper.clamp(desiredScrollOffset, Integer.MIN_VALUE, 0);
                }
            }
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            for (Panel button : buttons) {
                if (button.click(mouseX, mouseY)) break;
            }
        }

        @Override
        protected void onRightClick(int mouseX, int mouseY) {
            for (Panel button : buttons) {
                button.rightClick(mouseX, mouseY);
            }
        }

        @Override
        protected void onReleaseMouse(int mouseX, int mouseY) {
            for (Panel button : buttons) {
                button.releaseMouse(mouseX, mouseY);
            }
        }

        public void addButton(ButtonPanel button) {
            buttons.add(button);
        }

        @Override
        protected void onCharTyped(char chr, int modifiers) {
            for (Panel button : buttons) {
                button.charTyped(chr, modifiers);
            }
        }

        @Override
        protected void onKeyPressed(int keyCode, int scanCode, int modifiers) {
            for (Panel button : buttons) {
                button.keyPressed(keyCode, scanCode, modifiers);
            }
        }

        @Override
        protected void onKeyReleased(int keyCode, int scanCode, int modifiers) {
            for (Panel button : buttons) {
                button.keyReleased(keyCode, scanCode, modifiers);
            }
        }

    }

    public static class Panel extends DrawableHelper implements Drawable {

        protected int x;
        protected int unOffsettedY;
        protected int y;
        protected int width;
        protected int height;
        public boolean visible = true;

        public Panel(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.unOffsettedY = y;
            this.width = width;
            this.height = height;
        }

        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            if (visible) {
                onRender(matrices, mouseX, mouseY, delta);
                if (getDescription() != null && isMouseOver(mouseX, mouseY)) {
                    GuiValues.hoveredPanel = this;
                }
            }
        }

        public String getDescription() {
            return null;
        }

        protected void onClick(int mouseX, int mouseY) {
        }

        public boolean click(int mouseX, int mouseY) {
            if (isMouseOver(mouseX, mouseY) && visible) {
                onClick(mouseX, mouseY);
                return true;
            }
            return false;
        }

        protected void onRightClick(int mouseX, int mouseY) {
        }

        public void rightClick(int mouseX, int mouseY) {
            if (isMouseOver(mouseX, mouseY) && visible) {
                onRightClick(mouseX, mouseY);
            }
        }

        protected void onReleaseMouse(int mouseX, int mouseY) {
        }

        public void releaseMouse(int mouseX, int mouseY) {
            onReleaseMouse(mouseX, mouseY);
        }

        protected void onKeyPressed(int keyCode, int scanCode, int modifiers) {

        }

        public void keyPressed(int keyCode, int scanCode, int modifiers) {
            onKeyPressed(keyCode, scanCode, modifiers);
        }

        protected void onKeyReleased(int keyCode, int scanCode, int modifiers) {

        }

        public void keyReleased(int keyCode, int scanCode, int modifiers) {
            onKeyReleased(keyCode, scanCode, modifiers);
        }

        protected void onCharTyped(char chr, int modifiers) {

        }

        public void charTyped(char chr, int modifiers) {
            onCharTyped(chr, modifiers);
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            return x <= mouseX && x + width > mouseX && y <= mouseY && y + height > mouseY;
        }
    }

    public static class BooleanSettingButton extends ButtonPanel {

        Setting.BooleanSetting setting;
        int switchX;
        int switchY;
        int leverX;
        int desiredLeverX;

        public BooleanSettingButton(Setting.BooleanSetting setting, MainPanel main, int x, int y, int width, int height) {
            super(main, x, y, width, height, setting.getName());
            this.setting = setting;
            this.switchX = x + width - 32;
            this.leverX = switchX + (setting.getValue() ? 15 : 0);
            this.desiredLeverX = leverX;
            this.switchY = y + height / 2 - 5;
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            switchY = y + height / 2 - 5;
            fill(matrices, x, y, x + width, y + height, GuiValues.c5);
            drawTextWithShadow(matrices, TEXTRENDERER, new LiteralText(text), x, y + height / 2 - 4, 0xffffffff);
            fill(matrices, switchX, switchY + 2, switchX + 25, switchY + 8, GuiValues.c6);
            if (leverX < desiredLeverX) {
                leverX++;
            } else if (leverX > desiredLeverX) {
                leverX--;
            }
            fill(matrices, leverX, switchY, leverX + 10, switchY + 10, GuiValues.c7);
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            setting.setValue(!setting.getValue());
            if (setting.getValue()) {
                desiredLeverX = switchX + 15;
            } else {
                desiredLeverX = switchX;
            }
        }

        @Override
        public String getDescription() {
            return setting.getDesc();
        }
    }

    public static class CategoryButton extends ButtonPanel {

        EnumModuleType type;
        Item item;
        int r;

        public CategoryButton(EnumModuleType type, Item item, MainPanel main, int x, int y) {
            super(main, x, y, 30, 30, "");
            this.type = type;
            this.item = item;
            this.r = new Random().nextInt(360);
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            int rgb = GuiValues.c3;
            if (main.isMouseOver(mouseX, mouseY) && isMouseOver(mouseX, mouseY) || GuiValues.selectedType == type) {
                rgb = GuiValues.c4;
                r++;
                r %= 360;
            }
            RenderUtil.drawPolygon(matrices, 0 + r, 360 + r, 5, 15, x, y, GuiValues.c2);
            RenderUtil.drawPolygon(matrices, 0 + r, 360 + r, 5, 13, x + 2, y + 2, rgb);
            CLIENT.getItemRenderer().renderGuiItemIcon(new ItemStack(item), x + 7, y + 7);
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            GuiValues.selectedType = type;
            GuiValues.selectedModule = null;
        }

        @Override
        public String getDescription() {
            return type.getDesc();
        }

    }

    public static class ColorButton extends ButtonPanel {

        Setting.ColorSetting setting;

        public ColorButton(Setting.ColorSetting setting, MainPanel main, int x, int y, int width, int height) {
            super(main, x, y, width, height, setting.getName());
            this.setting = setting;
        }

        protected void setColorFromInput() {

        }

        public static class ColorSettingButton extends ColorButton {

            protected List<ButtonPanel> buttons = new ArrayList<>();
            public boolean forceSphere = true;

            public ColorSettingButton(Setting.ColorSetting setting, MainPanel main, int x, int y, int width, int height) {
                super(setting, main, x, y, width, height);
                buttons.add(new ColorButton.HueSphereButton(this, setting, main, x, y + 14, height - 14, height - 14));
                buttons.add(new ColorButton.PreviewButton(this, setting, main, x + height - 10, y + 14, width - height + 8, (height - 14) / 3));
                buttons.add(new ColorButton.BrightnessSliderButton(setting, main, x + height - 10, y + 14 + (height - 14) / 3, width - height + 8, (height - 14) / 3));
                buttons.add(new ColorButton.AlphaSliderButton(setting, main, x + height - 10, y + 14 + 2 * (height - 14) / 3, width - height + 8, (height - 14) / 3));
            }

            @Override
            protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                fill(matrices, x, y, x + width, y + height, GuiValues.c5);
                drawStringWithShadow(matrices, TEXTRENDERER, setting.getName(), x, y + 3, 0xFFFFFFFF);
                for (ButtonPanel button : buttons) {
                    button.render(matrices, mouseX, mouseY, delta);
                }
                super.onRender(matrices, mouseX, mouseY, delta);
            }

            @Override
            protected void onClick(int mouseX, int mouseY) {
                for (ButtonPanel button : buttons) {
                    button.click(mouseX, mouseY);
                }
                super.onClick(mouseX, mouseY);
            }

            @Override
            protected void onReleaseMouse(int mouseX, int mouseY) {
                for (ButtonPanel button : buttons) {
                    button.releaseMouse(mouseX, mouseY);
                }
                super.onReleaseMouse(mouseX, mouseY);
            }

            @Override
            protected void onKeyPressed(int keyCode, int scanCode, int modifiers) {
                for (ButtonPanel button : buttons) {
                    button.keyPressed(keyCode, scanCode, modifiers);
                }
                super.onKeyPressed(keyCode, scanCode, modifiers);
            }

            @Override
            protected void onKeyReleased(int keyCode, int scanCode, int modifiers) {
                for (ButtonPanel button : buttons) {
                    button.keyReleased(keyCode, scanCode, modifiers);
                }
                super.onKeyReleased(keyCode, scanCode, modifiers);
            }

            @Override
            protected void onCharTyped(char chr, int modifiers) {
                for (ButtonPanel button : buttons) {
                    button.charTyped(chr, modifiers);
                }
                super.onCharTyped(chr, modifiers);
            }

        }

        private class HueSphereButton extends ColorButton {

            public HueSphereButton(ColorSettingButton button, Setting.ColorSetting setting, MainPanel main, int x, int y, int width, int height) {
                super(setting, main, x, y, width, height);
                this.button = button;
                float[] hsb = {0, 0, 0};
                hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), hsb);
                pointX = (int) (radius - Math.cos(Math.toRadians(hsb[0] * 360f)) * hsb[1] * radius);
                pointY = (int) (radius - Math.sin(Math.toRadians(hsb[0] * 360f)) * hsb[1] * radius);
            }

            ColorSettingButton button;

            boolean clicked = false;

            int pointX = 0;
            int pointY = 0;
            int radius = width / 2;

            @Override
            protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                RenderUtil.drawColorCircle(matrices, radius, x, y);
                float[] hsb = {0, 0, 0};
                hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), hsb);
                if (clicked) {
                    float difX = mouseX - x - radius;
                    float difY = mouseY - y - radius;
                    double l = Math.sqrt(difX * difX + difY * difY);
                    if (l > radius) {
                        difX /= l;
                        difY /= l;
                        pointX = (int) (radius + difX * radius);
                        pointY = (int) (radius + difY * radius);
                    } else {
                        pointX = mouseX - x;
                        pointY = mouseY - y;
                    }
                } else if (!button.forceSphere) {
                    pointX = (int) (radius - Math.cos(Math.toRadians(hsb[0] * 360f)) * hsb[1] * radius);
                    pointY = (int) (radius - Math.sin(Math.toRadians(hsb[0] * 360f)) * hsb[1] * radius);
                }
                if (button.forceSphere) {
                    setColorFromInput();
                }
                drawHorizontalLine(matrices, x + pointX - 2, x + pointX + 2, y + pointY, 0xFF000000);
                drawVerticalLine(matrices, x + pointX, y + pointY - 3, y + pointY + 3, 0xFF000000);
                super.onRender(matrices, mouseX, mouseY, delta);
            }

            @Override
            protected void onClick(int mouseX, int mouseY) {
                if (!clicked) {
                    clicked = true;
                }
                super.onClick(mouseX, mouseY);
            }

            @Override
            protected void onReleaseMouse(int mouseX, int mouseY) {
                if (clicked) {
                    clicked = false;
                    setColorFromInput();
                }
                super.onReleaseMouse(mouseX, mouseY);
            }

            @Override
            protected void setColorFromInput() {
                float cX = pointX - radius;
                float cY = pointY - radius;
                double l = Math.sqrt(cX * cX + cY * cY);
                cX /= l;
                cY /= l;
                float[] hsb = {0, 0, 0};
                hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), hsb);
                double twopi = (2 * Math.PI);
                hsb[0] = (float) ((Math.atan2(cY, cX) + Math.PI) / twopi);
                hsb[1] = MathHelper.clamp((float) l / (float) radius, 0, 1);
                Color color = new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
                setting.setRed(color.getRed());
                setting.setBlue(color.getBlue());
                setting.setGreen(color.getGreen());
            }
        }

        private class BrightnessSliderButton extends ColorButton {

            public BrightnessSliderButton(Setting.ColorSetting setting, MainPanel main, int x, int y, int width, int height) {
                super(setting, main, x, y, width, height);
            }

            int sliderX = 0;
            boolean clicked = false;

            @Override
            protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                float[] hsb = {0, 0, 0};
                hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), hsb);
                RenderUtil.fillHGradient(matrices, x, y + 2, width, height - 4, 0xFF000000, Color.HSBtoRGB(hsb[0], hsb[1], 1f));
                if (clicked) {
                    sliderX = MathHelper.clamp(mouseX - x, 0, width);
                    setColorFromInput();
                } else {
                    sliderX = (int) (width * hsb[2]);
                }
                fill(matrices, x + sliderX, y, x + sliderX + 2, y + height, GuiValues.c7);
                super.onRender(matrices, mouseX, mouseY, delta);
            }

            @Override
            protected void onClick(int mouseX, int mouseY) {
                if (!clicked) {
                    clicked = true;
                }
                super.onClick(mouseX, mouseY);
            }

            @Override
            protected void onReleaseMouse(int mouseX, int mouseY) {
                if (clicked) {
                    clicked = false;
                    setColorFromInput();
                }
                super.onReleaseMouse(mouseX, mouseY);
            }

            @Override
            protected void setColorFromInput() {
                float[] hsb = {0, 0, 0};
                hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), hsb);
                Color color = new Color(Color.HSBtoRGB(hsb[0], hsb[1], (float) sliderX / (float) width));
                setting.setRed(color.getRed());
                setting.setBlue(color.getBlue());
                setting.setGreen(color.getGreen());
            }
        }

        private class AlphaSliderButton extends ColorButton {

            public AlphaSliderButton(Setting.ColorSetting setting, MainPanel main, int x, int y, int width, int height) {
                super(setting, main, x, y, width, height);
            }

            int sliderX = 0;
            boolean clicked = false;

            @Override
            protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                RenderUtil.drawRowBasedCheckerBoard(matrices, 5, x, y + 2, width, height - 4, 0xFFFFFFFF, 0xFFBFBFBF);
                RenderUtil.fillHGradient(matrices, x, y + 2, width, height - 4,
                        new Color(setting.getRed(), setting.getGreen(), setting.getBlue(), 0).getRGB(),
                        new Color(setting.getRed(), setting.getGreen(), setting.getBlue(), 255).getRGB());
                if (clicked) {
                    sliderX = MathHelper.clamp(mouseX - x, 0, width);
                    setColorFromInput();
                } else {
                    sliderX = (int) (width * setting.getAlpha() / 255f);
                }
                fill(matrices, x + sliderX, y, x + sliderX + 2, y + height, GuiValues.c7);
                super.onRender(matrices, mouseX, mouseY, delta);
            }

            @Override
            protected void onClick(int mouseX, int mouseY) {
                if (!clicked) {
                    clicked = true;
                }
                super.onClick(mouseX, mouseY);
            }

            @Override
            protected void onReleaseMouse(int mouseX, int mouseY) {
                if (clicked) {
                    clicked = false;
                    setColorFromInput();
                }
                super.onReleaseMouse(mouseX, mouseY);
            }

            @Override
            protected void setColorFromInput() {
                setting.setAlpha((int) (sliderX / (float) width * 255));
            }
        }

        private class PreviewButton extends ColorButton {

            public PreviewButton(ColorSettingButton button, Setting.ColorSetting setting, MainPanel main, int x, int y, int width, int height) {
                super(setting, main, x, y, width, height);
                this.button = button;
                textField = new TextFieldWidget(TEXTRENDERER, x + 2, y + height / 2 - 5, width, 9, new LiteralText(getHexColor()));
                textField.setEditable(true);
                textField.setDrawsBackground(false);
            }

            ColorSettingButton button;
            TextFieldWidget textField;

            @Override
            protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                fill(matrices, x, y + 2, x + width, y + height - 2, setting.getValue());
                textField.y = y + height / 2 - 5;
                textField.render(matrices, mouseX, mouseY, delta);
                if (textField.isFocused()) {
                    setting.setValue(parseHexColor(textField.getText()));
                } else {
                    textField.setText(getHexColor());
                }
                super.onRender(matrices, mouseX, mouseY, delta);
            }

            @Override
            public boolean click(int mouseX, int mouseY) {
                textField.mouseClicked(mouseX, mouseY, 0);
                if (textField.isFocused()) {
                    button.forceSphere = false;
                } else {
                    button.forceSphere = true;
                }
                return super.click(mouseX, mouseY);
            }

            @Override
            protected void onKeyPressed(int keyCode, int scanCode, int modifiers) {
                textField.keyPressed(keyCode, scanCode, modifiers);
                super.onKeyPressed(keyCode, scanCode, modifiers);
            }

            @Override
            protected void onKeyReleased(int keyCode, int scanCode, int modifiers) {
                textField.keyReleased(keyCode, scanCode, modifiers);
                super.onKeyReleased(keyCode, scanCode, modifiers);
            }

            @Override
            protected void onCharTyped(char chr, int modifiers) {
                textField.charTyped(chr, modifiers);
                super.onCharTyped(chr, modifiers);
            }

            private String getHexColor() {
                String string = Integer.toHexString(new Color(setting.getRed(), setting.getGreen(), setting.getBlue()).getRGB());
                string = string.substring(2);
                return "#" + string;
            }

            private int parseHexColor(String color) {
                ;
                color = color.replaceFirst("#", "");
                int c;
                try {
                    c = Integer.parseInt(color, 16);
                } catch (NumberFormatException e) {
                    return new Color(0, 0, 0, setting.getAlpha()).getRGB();
                }
                c = MathHelper.clamp(c, 0, 0xFFFFFF);
                Color rgb = new Color(c);
                return new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), setting.getAlpha()).getRGB();
            }

        }

        @Override
        public String getDescription() {
            return setting.getDesc();
        }
    }

    public static class KeyBindButton extends ButtonPanel {

        ToldiModule module;
        boolean clicked;

        public KeyBindButton(ToldiModule module, MainPanel main, int x, int y, int width, int height) {
            super(main, x, y, width, height, getKeyName(module.keybindSetting.getValue()));
            this.module = module;
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            fill(matrices, x, unOffsettedY, x + width, unOffsettedY + height, GuiValues.c1);
            String text = "";
            try {
                text = new TranslatableText("setting.keybind.key").parse(null, null, 0).getString() + ": " + this.text;
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
            drawCenteredText(matrices, TEXTRENDERER, text, x + width / 2, unOffsettedY + height / 2 - 4, GuiValues.c7);
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            clicked = true;
            text = "...";
        }

        @Override
        protected void onRightClick(int mouseX, int mouseY) {
            module.keybindSetting.setValue(-1);
            text = getKeyName(module.keybindSetting.getValue());
        }

        @Override
        protected void onKeyPressed(int keyCode, int scanCode, int modifiers) {
            if (clicked) {
                if (keyCode == InputUtil.GLFW_KEY_ESCAPE) {
                    module.keybindSetting.setValue(-1);
                    text = getKeyName(module.keybindSetting.getValue());
                } else {
                    module.keybindSetting.setValue(keyCode);
                    text = getKeyName(module.keybindSetting.getValue());
                }
                clicked = false;
            }
        }

        private static String getKeyName(int key) {
            if (key == -1) {
                return "";
            }
            return InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
        }
    }

    public static class KeyBindModeButton extends ButtonPanel {

        ToldiModule module;

        public KeyBindModeButton(ToldiModule module, MainPanel main, int x, int y, int width, int height) {
            super(main, x, y, width, height, "");
            this.module = module;
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            fill(matrices, x, unOffsettedY, x + width, unOffsettedY + height, GuiValues.c1);
            String text = "";
            if (module.keybindSetting.getMode() == 0) {
                text = "press";
            } else {
                text = "hold";
            }
            try {
                text = new TranslatableText("setting.keybind.mode." + text).parse(null, null, 0).getString();
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
            fill(matrices, x, y, x + width, y + height, GuiValues.c1);
            fill(matrices, x + width / 2 - 5 - TEXTRENDERER.getWidth(text) / 2 - 3, y + height / 2 - 5, x + width / 2 - 5 + TEXTRENDERER.getWidth(text) / 2 + 3, y + height / 2 + 6, GuiValues.c6);
            drawCenteredText(matrices, TEXTRENDERER, text, x + width / 2 - 5, y + height / 2 - 4, GuiValues.c7);
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            module.keybindSetting.incrementMode();
        }

        @Override
        protected void onRightClick(int mouseX, int mouseY) {
            module.keybindSetting.incrementMode();
        }
    }

    public static class ModeSettingButton extends ButtonPanel {

        Setting.ModeSetting setting;

        public ModeSettingButton(Setting.ModeSetting setting, MainPanel main, int x, int y, int width, int height) {
            super(main, x, y, width, height, setting.getMode());
            this.setting = setting;
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            fill(matrices, x, y, x + width, y + height, GuiValues.c5);
            fill(matrices, x + width - TEXTRENDERER.getWidth("<" + text + ">") - 5, y + height / 2 - 5, x + width - 5, y + height / 2 + 6, GuiValues.c6);
            drawTextWithShadow(matrices, TEXTRENDERER, new LiteralText(setting.getName()), x, y + height / 2 - 4, 0xffffffff);
            drawTextWithShadow(matrices, TEXTRENDERER, new LiteralText("<" + text + ">"), x + width - TEXTRENDERER.getWidth("<" + text + ">") - 5, y + height / 2 - 4, 0xffffffff);
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            setting.increment();
            text = setting.getMode();
        }

        @Override
        protected void onRightClick(int mouseX, int mouseY) {
            setting.decrement();
            text = setting.getMode();
        }

        @Override
        public String getDescription() {
            return setting.getDesc();
        }

    }

    public static class ModuleButton extends ButtonPanel {

        ToldiModule module;

        public ModuleButton(ToldiModule module, MainPanel main, int x, int y, int width, int height) {
            super(main, x, y, width, height, module.getRawName());
            this.module = module;
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            module.toggle();
        }

        @Override
        protected void onRightClick(int mouseX, int mouseY) {
            GuiValues.selectedModule = module;
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            int rgb = GuiValues.c3;
            if (main.isMouseOver(mouseX, mouseY) && isMouseOver(mouseX, mouseY) || GuiValues.selectedModule == module)
                rgb = GuiValues.c4;
            DrawableHelper.fill(matrices, x, y, x + width, y + height, rgb);
            drawCenteredText(matrices, TEXTRENDERER, text, x + width / 2, y + (height - 10) / 2, module.getRawStatus() ? new Color(0x00ffd9, false).getRGB() : Color.WHITE.getRGB());
        }

        public ToldiModule getModule() {
            return module;
        }

        @Override
        public String getDescription() {
            return module.getDescription();
        }

    }

    public static class NumberSettingButton extends ButtonPanel {

        NumberSetting<?> setting;
        TextFieldWidget textField;
        boolean selected;

        public NumberSettingButton(NumberSetting<?> setting, MainPanel main, int x, int y, int width, int height) {
            super(main, x, y, width, height, setting.getName());
            this.setting = setting;
            textField = new TextFieldWidget(TEXTRENDERER, x + width - 38, y + height / 2 - 3, 38, 9, new LiteralText(String.valueOf(setting.getValue())));
            textField.setEditable(true);
            textField.setDrawsBackground(false);
            textField.setMaxLength(5);
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            fill(matrices, x, y, x + width, y + height, GuiValues.c5);
            textField.y = y + height / 2 - 4;
            double max = 1;
            double min = 1;
            double value = 1;
            int width = this.width - 40;
            if (setting instanceof NumberSetting.IntegerSetting) {
                max = (double) (int) setting.getMax();
                min = (double) (int) setting.getMin();
                value = (double) (int) setting.getValue();
            } else {
                max = (double) setting.getMax();
                min = (double) setting.getMin();
                value = (double) setting.getValue();
            }
            int sliderWidth = (int) (width * MathHelper.clamp((value - min) / (max - min), 0, 1));
            drawTextWithShadow(matrices, TEXTRENDERER, new LiteralText(text), x, y, 0xffffffff);
            textField.render(matrices, mouseX, mouseY, delta);
            fill(matrices, x, y + 10, x + width, y + height - 1, GuiValues.c6);
            fill(matrices, x, y + 10, x + sliderWidth, y + height - 1, GuiValues.c7);
            fill(matrices, x + sliderWidth, y + 9, x + sliderWidth + 2, y + height, GuiValues.c7);
            if (selected) {
                if (setting instanceof NumberSetting.IntegerSetting) {
                    ((NumberSetting.IntegerSetting) setting).setValue(Integer.valueOf((int) (min + MathHelper.clamp((mouseX - x) / (double) width, 0, 1) * (max - min))));
                } else {
                    ((NumberSetting.DoubleSetting) setting).setValue(Double.valueOf(min + MathHelper.clamp((mouseX - x) / (double) width, 0, 1) * (max - min)));
                }
            }
            if (!textField.isFocused()) {
                if (setting instanceof NumberSetting.IntegerSetting) {
                    textField.setText(String.valueOf((int) setting.getValue()));
                } else {
                    textField.setText(String.valueOf(((int) ((double) setting.getValue() * 100d)) / 100d));
                }
            } else if (!textField.getText().isEmpty()) {
                if (setting instanceof NumberSetting.IntegerSetting) {
                    ((NumberSetting.IntegerSetting) setting).setValue((int) Double.parseDouble(textField.getText()));
                } else {
                    ((NumberSetting.DoubleSetting) setting).setValue(Double.parseDouble(textField.getText()));
                }
            }
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            if (mouseY - y > 9) selected = true;
        }

        @Override
        public boolean click(int mouseX, int mouseY) {
            textField.mouseClicked(mouseX, mouseY, 0);
            return super.click(mouseX, mouseY);
        }

        @Override
        protected void onReleaseMouse(int mouseX, int mouseY) {
            selected = false;
        }

        @Override
        protected void onKeyPressed(int keyCode, int scanCode, int modifiers) {
            textField.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        protected void onKeyReleased(int keyCode, int scanCode, int modifiers) {
            textField.keyReleased(keyCode, scanCode, modifiers);
        }

        @Override
        protected void onCharTyped(char chr, int modifiers) {
            if ((chr >= '0' && chr <= '9') || chr == '.') {
                textField.charTyped(chr, modifiers);
            }
        }

        @Override
        public String getDescription() {
            return setting.getDesc();
        }

    }

    public static class SimpleButton extends ButtonPanel {

        Runnable run;

        public SimpleButton(MainPanel main, int x, int y, int width, int height, String text, Runnable run) {
            super(main, x, y, width, height, text);
            this.run = run;
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            int rgb = GuiValues.c3;
            if (main.isMouseOver(mouseX, mouseY) && isMouseOver(mouseX, mouseY))
                rgb = GuiValues.c4;
            fill(matrices, x, y, x + width, y + height, rgb);
            drawCenteredText(matrices, TEXTRENDERER, text, x + width / 2, y + (height - 10) / 2, Color.WHITE.getRGB());
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            run.run();
        }

    }

    public static class ActiveModulesHudPanel extends HudPanel {

        public ActiveModulesHudPanel(ToldiHudModule module, float xp, float yp, int width, int height) {
            super(module, xp, yp, width, height);
        }

        int orientation = 0;

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            if (drawBackground) fill(matrices, x, y, x + width, y + height, 0x77000000);
            if (clicked) {
                x = mouseX - mDifX;
                y = mouseY - mDifY;
            }
            int mx = RenderUtil.getWindowWidth() / 2;
            int my = RenderUtil.getWindowHeight() / 2;
            int pmx = x + width / 2;
            int pmy = y + height / 2;
            if (mx >= pmx && my > pmy) {
                orientation = 0;
            } else if (mx < pmx && my >= pmy) {
                orientation = 1;
            } else if (mx < pmx && my < pmy) {
                orientation = 2;
            } else {
                orientation = 3;
            }

            int h = 2;
            Modules.ACTIVE.sort((o1, o2) -> TEXTRENDERER.getWidth(o2.getRawName()) - TEXTRENDERER.getWidth(o1.getRawName()));
            for (ToldiModule module : Modules.ACTIVE) {
                if (!(module instanceof ToldiHudModule)) {
                    if (orientation < 2) {
                        switch (orientation) {
                            case 0:
                                drawStringWithShadow(matrices, TEXTRENDERER, module.getName(), x, y + h, this.module.color.getValue());
                                break;
                            case 1:
                                drawStringWithShadow(matrices, TEXTRENDERER, module.getName(), x + width - TEXTRENDERER.getWidth(module.getName()), y + h, this.module.color.getValue());
                        }
                    } else {
                        switch (orientation) {
                            case 2:
                                drawStringWithShadow(matrices, TEXTRENDERER, module.getName(), x + width - TEXTRENDERER.getWidth(module.getName()), y + height - h - 10, this.module.color.getValue());
                                break;
                            case 3:
                                drawStringWithShadow(matrices, TEXTRENDERER, module.getName(), x, y + height - h - 10, this.module.color.getValue());
                        }
                    }
                    h += 10;
                }
            }
            if (Modules.ACTIVE.isEmpty()) {
                height = 200;
            } else {
                int prevHeight = height;
                height = h;
                if (orientation > 1) {
                    y = y + prevHeight - height;
                    resetYPercentage();
                }
            }
            if (x == -999 || y == -999) {
                resetLocation();
            }
        }

    }

    public static class HudPanel extends Panel {

        ToldiHudModule module;
        boolean clicked = false;
        int mDifX = 0;
        int mDifY = 0;
        float xp;
        float yp;
        public boolean drawBackground = false;

        public HudPanel(ToldiHudModule module, float xp, float yp, int width, int height) {
            super(-999, -999, width, height);
            this.module = module;
            this.xp = xp;
            this.yp = yp;
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            if (drawBackground) fill(matrices, x, y, x + width, y + height, 0x77000000);
            //This might seem dumb to not load during the game initializes, however, there's no window then...
            if (x == -999 || y == -999) {
                resetLocation();
            }
            if (clicked) {
                x = mouseX - mDifX;
                y = mouseY - mDifY;
            }
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            visible = module.getRawStatus();
            super.render(matrices, mouseX, mouseY, delta);
        }

        @Override
        protected void onClick(int mouseX, int mouseY) {
            clicked = true;
            mDifX = mouseX - x;
            mDifY = mouseY - y;
            GuiValues.movingHudPanel = true;
        }

        @Override
        protected void onReleaseMouse(int mouseX, int mouseY) {
            clicked = false;
            resetXPercentage();
            resetYPercentage();
            resetLocation();
            GuiValues.movingHudPanel = false;
        }

        public void setXPercentage(float x) {
            this.xp = MathHelper.clamp(x, 0f, 1f);
        }

        public void setYPercentage(float y) {
            this.yp = MathHelper.clamp(y, 0f, 1f);
        }

        public float getXPercentage() {
            return x / (float) (RenderUtil.getWindowWidth() - width);
        }

        public float getYPercentage() {
            return y / (float) (RenderUtil.getWindowHeight() - height);
        }

        public void resetXPercentage() {
            setXPercentage(getXPercentage());
        }

        public void resetYPercentage() {
            setYPercentage(getYPercentage());
        }

        //In case of the window size changing...
        public void resetLocation() {
            x = (int) ((RenderUtil.getWindowWidth() - width) * xp);
            y = (int) ((RenderUtil.getWindowHeight() - height) * yp);
        }

        @Override
        public String getDescription() {
            return module.getDescription();
        }

    }

    public static class DescriptionMainPanel extends MainPanel {

        public DescriptionMainPanel(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            fill(matrices, x, y, x + width, y + height, GuiValues.c2);
            if (GuiValues.hoveredPanel != null) {
                drawCenteredText(matrices, TEXTRENDERER, GuiValues.hoveredPanel.getDescription(), x + width / 2, y + height / 2 - 5, 0xFFFFFFFF);
            }
        }


    }

    public static class KeyBindMainPanel extends MainPanel {

        ToldiModule prevModule;

        public KeyBindMainPanel(int x, int y, int width, int height) {
            super(x, y, width, height, false);
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            DrawableHelper.fill(matrices, x, unOffsettedY, x + width, unOffsettedY + height, GuiValues.c2);
            if (prevModule != GuiValues.selectedModule) {
                prevModule = GuiValues.selectedModule;
                scrollOffset = 0;
                desiredScrollOffset = 0;
                buttons.clear();
                if (prevModule != null) {
                    addButton(new KeyBindButton(prevModule, this, x + 5, y + height - 18, 120, 16));
                    addButton(new KeyBindModeButton(prevModule, this, x + 130, y + height - 18, width - 135, 16));
                }
            }
            for (Panel button : buttons) {
                button.render(matrices, mouseX, mouseY, delta);
            }
        }
    }

    public static class ModulesMainPanel extends MainPanel {

        EnumModuleType prevType;

        public ModulesMainPanel(int x, int y, int width, int height) {
            super(x, y, width, height, true);
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            DrawableHelper.fill(matrices, x, unOffsettedY, x + width, unOffsettedY + height, GuiValues.c1);
            if (prevType != GuiValues.selectedType) {
                prevType = GuiValues.selectedType;
                scrollOffset = 0;
                desiredScrollOffset = 0;
                buttons.clear();
                List<ToldiModule> modules = Modules.MODULESBYTYPE.get(GuiValues.selectedType);
                for (int i = 0; i < modules.size(); i++) {
                    addButton(new ModuleButton(modules.get(i), this, x + 2, y + 16 + 16 * i, width - 4, 14));
                }
            }
            for (Panel button : buttons) {
                button.render(matrices, mouseX, mouseY, delta);
            }
            fill(matrices, x, y, x + width, y + 14, GuiValues.c2);
            drawCenteredText(matrices, TEXTRENDERER, GuiValues.selectedType.getName(), x + width / 2, unOffsettedY + 2, Color.WHITE.getRGB());
        }

        @Override
        public boolean isMouseOver(int mouseX, int mouseY) {
            return x <= mouseX && x + width > mouseX && y + 14 <= mouseY && y + height > mouseY;
        }
    }

    public static class SettingMainPanel extends MainPanel {

        ToldiModule prevModule;

        public SettingMainPanel(int x, int y, int width, int height) {
            super(x, y, width, height, true);
        }

        @Override
        protected void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            DrawableHelper.fill(matrices, x, unOffsettedY, x + width, unOffsettedY + height, GuiValues.c1);
            if (prevModule != GuiValues.selectedModule) {
                prevModule = GuiValues.selectedModule;
                scrollOffset = 0;
                desiredScrollOffset = 0;
                buttons.clear();
                if (prevModule != null) {
                    int height = 0;
                    for (int i = 0; i < prevModule.settings.size(); i++) {
                        Setting<?> setting = prevModule.settings.get(i);
                        if (setting instanceof NumberSetting<?>) {
                            addButton(new NumberSettingButton((NumberSetting<?>) setting, this, x + 2, y + 16 + height, width - 4, 18));
                            height += 19;
                        } else if (setting instanceof Setting.BooleanSetting) {
                            addButton(new BooleanSettingButton((Setting.BooleanSetting) setting, this, x + 2, y + 16 + height, width - 4, 16));
                            height += 17;
                        } else if (setting instanceof Setting.ModeSetting) {
                            addButton(new ModeSettingButton((Setting.ModeSetting) setting, this, x + 2, y + 16 + height, width - 4, 16));
                            height += 17;
                        } else if (setting instanceof Setting.ColorSetting) {
                            addButton(new ColorButton.ColorSettingButton((Setting.ColorSetting) setting, this, x + 2, y + 16 + height, width - 4, 74));
                            height += 75;
                        }
                    }
                }
            }
            for (Panel button : buttons) {
                button.render(matrices, mouseX, mouseY, delta);
            }
            fill(matrices, x, y, x + width, y + 14, GuiValues.c2);
            try {
                drawCenteredText(matrices, TEXTRENDERER, GuiValues.selectedModule != null ? GuiValues.selectedModule.getRawName() : new TranslatableText("module." + MODID + ".null.name").parse(null, null, 0).asString(), x + width / 2, unOffsettedY + 2, Color.WHITE.getRGB());
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
        }

        @Override
        public boolean isMouseOver(int mouseX, int mouseY) {
            return x <= mouseX && x + width > mouseX && y + 14 <= mouseY && y + height > mouseY;
        }
    }

    public abstract static class SettingHolder {

        public List<Setting<?>> settings = new ArrayList<>();

        public abstract String getHolderName();
    }

    /***
     * This is for generating a accesswidener for every fields/methods/inner classes of certain classes automatically when needed.
     */
    public static class AccessWidenerUtil {

        static File f = new File(ConfigHandler.DIR + "\\accesswidenergen.txt");

        static class MethodWidener {
            String className;
            String methodName;
            String descriptor;

            public MethodWidener(Class<?> clazz, Method method) {
                this.className = clazz.getName().replace(".", "/");
                this.methodName = method.getName();

                String desc = "(";

                for (Parameter arg : method.getParameters()) {
                    desc += arg.getType().descriptorString();
                }

                desc += ")" + method.getReturnType().descriptorString();

                this.descriptor = desc;
            }

            @Override
            public String toString() {
                return "accessible   method   " + className + "   " + methodName + "   " + descriptor;
            }


        }

        static class FieldWidener {
            String className;
            String fieldName;
            String descriptor;

            public FieldWidener(Class<?> clazz, Field field) {
                this.className = clazz.getName().replace(".", "/");
                this.fieldName = field.getName();
                this.descriptor = field.getType().descriptorString();
            }

            @Override
            public String toString() {
                return "accessible   field   " + className + "   " + fieldName + "   " + descriptor;
            }


        }

        static class ClassWidener {
            String className;

            public ClassWidener(Class<?> clazz) {
                this.className = clazz.getName().replace(".", "/");
            }

            @Override
            public String toString() {
                return "accessible   class   " + className;
            }


        }

        public static void generateFullAccessWidenerForClass(Class<?> clazz) {
            List<Object> toWiden = new ArrayList<>();
            toWiden.addAll(generateMethodWideners(clazz));
            toWiden.addAll(generateFieldWideners(clazz));
            toWiden.addAll(generateClassWideners(clazz));
            FileWriter writer;
            try {
                writer = new FileWriter(f);
                for (Object o : toWiden)
                    writer.write(o.toString() + "\n");
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static List<AccessWidenerUtil.MethodWidener> generateMethodWideners(Class<?> clazz) {
            List<AccessWidenerUtil.MethodWidener> methods = new ArrayList<>();
            for (Method method : clazz.getMethods()) {
                if (!Arrays.asList(Object.class.getMethods()).contains(method))
                    methods.add(new AccessWidenerUtil.MethodWidener(clazz, method));
            }
            return methods;
        }

        private static List<AccessWidenerUtil.FieldWidener> generateFieldWideners(Class<?> clazz) {
            List<AccessWidenerUtil.FieldWidener> fields = new ArrayList<>();
            for (Field field : clazz.getFields()) {
                fields.add(new AccessWidenerUtil.FieldWidener(clazz, field));
            }
            return fields;
        }

        private static List<AccessWidenerUtil.ClassWidener> generateClassWideners(Class<?> clazzIn) {
            List<AccessWidenerUtil.ClassWidener> classes = new ArrayList<>();
            for (Class<?> clazz : clazzIn.getClasses()) {
                classes.add(new AccessWidenerUtil.ClassWidener(clazz));
            }
            return classes;
        }
    }

    @ToldiModule.Type(EnumModuleType.WORLD)
    public static class Timer extends ToldiModule {

        NumberSetting.DoubleSetting speed = new NumberSetting.DoubleSetting(this, "speed", 1d, 0d, 5d);

        @Override
        public void disable() {
            getMC().renderTickCounter.tickTime = 50f;
            super.disable();
        }

        @Listener
        public void onTick() {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (getStatus()) {
                    client.renderTickCounter.tickTime = (float) (50f / speed.getValue());
                }
            });
        }
    }

    public static class MathUtil {

        public static double getSideX(double hyp, float yaw) {
            return -Math.sin(Math.toRadians(yaw)) * hyp;
        }

        public static double getSideZ(double hyp, float yaw) {
            return Math.cos(Math.toRadians(yaw)) * hyp;
        }

        public static double getSideY(double hyp, float pitch) {
            return -Math.sin(Math.toRadians(pitch)) * hyp;
        }

        public static double calculateX(LivingEntity e, double hyp) {
            return getSideX(hyp, shiftYawIntoDirection(e));
        }

        public static double calculateZ(LivingEntity e, double hyp) {
            return getSideZ(hyp, shiftYawIntoDirection(e));
        }

        public static double calculateX(float yaw, double hyp) {
            return getSideX(hyp, shiftYawIntoDirection(yaw));
        }

        public static double calculateZ(float yaw, double hyp) {
            return getSideZ(hyp, shiftYawIntoDirection(yaw));
        }

        public static float shiftYawIntoDirection(LivingEntity e) {
            float yaw = e.getHeadYaw();
            float forward = e.forwardSpeed;
            float strafe = e.sidewaysSpeed;
            if (forward == 0) {
                if (strafe == 0) {
                    return 0;
                } else {
                    if (strafe > 0) {
                        yaw -= 90;
                    } else {
                        yaw += 90;
                    }
                }
            } else {
                if (strafe == 0) {
                    if (forward < 0) {
                        yaw += 180;
                    }
                } else {
                    if (forward < 0) {
                        if (strafe > 0) {
                            yaw += 225;
                        } else {
                            yaw -= 225;
                        }
                    } else {
                        if (strafe > 0) {
                            yaw -= 45;
                        } else {
                            yaw += 45;
                        }
                    }
                }
            }
            return yaw;
        }

        public static float shiftYawIntoDirection(float yaw) {
            if (!CLIENT.options.keyForward.isPressed() && !CLIENT.options.keyBack.isPressed()) {
                if (!CLIENT.options.keyRight.isPressed() && !CLIENT.options.keyLeft.isPressed()) {
                    return 0;
                } else {
                    if (CLIENT.options.keyLeft.isPressed()) {
                        yaw -= 90;
                    } else {
                        yaw += 90;
                    }
                }
            } else {
                if (!CLIENT.options.keyRight.isPressed() && !CLIENT.options.keyLeft.isPressed()) {
                    if (CLIENT.options.keyBack.isPressed()) {
                        yaw += 180;
                    }
                } else {
                    if (CLIENT.options.keyBack.isPressed()) {
                        if (CLIENT.options.keyLeft.isPressed()) {
                            yaw += 225;
                        } else {
                            yaw -= 225;
                        }
                    } else {
                        if (CLIENT.options.keyLeft.isPressed()) {
                            yaw -= 45;
                        } else {
                            yaw += 45;
                        }
                    }
                }
            }
            return yaw;
        }

        public static boolean hasNoBlockInAngle(ClientPlayerEntity player, double distance, float rotationYaw, float rotationPitch) {
            float yaw = player.getYaw();
            float pitch = player.getPitch();
            player.setYaw(rotationYaw);
            player.setPitch(rotationPitch);
            HitResult result = player.raycast(distance, 0f, false);
            player.setYaw(yaw);
            player.setPitch(pitch);
            if (result.getType() == HitResult.Type.BLOCK) {
                return false;
            }
            return true;
        }
    }

    public static class CombatUtil {

        public static boolean attackPassive;
        public static boolean attackNeutral;
        public static boolean attackAggressive;
        public static boolean attackInanimate;
        public static boolean attackPlayer;
        public static boolean rayTrace;

        public static enum TargetMode {
            DISTANCE, HEALTH
        }

        public static enum AimMode {
            LEG, CENTER, HEAD
        }

        public static Entity getKillAuraTarget(ClientWorld world, ClientPlayerEntity player, double distance, CombatUtil.TargetMode mode, CombatUtil.AimMode aim) {
            Entity target = null;
            float best = Float.MAX_VALUE;
            for (Entity entity : world.getEntities()) {
                if (entity == player) continue;
                if (getDistanceBetweenEntities(entity, player) > distance) continue;
                if (entity instanceof LivingEntity) {
                    if (((LivingEntity) entity).getHealth() <= 0 || ((LivingEntity) entity).isDead()) {
                        continue;
                    }
                }
                boolean canBeAttacked = false;
                if (attackPassive) {
                    if (entity instanceof PassiveEntity || entity instanceof AmbientEntity) {
                        canBeAttacked = true;
                    }
                }
                if (attackAggressive) {
                    if (entity instanceof HostileEntity) {
                        canBeAttacked = true;
                    }
                }
                if (attackInanimate) {
                    if (entity instanceof BoatEntity ||
                            entity instanceof AbstractMinecartEntity) {
                        canBeAttacked = true;
                    }
                }
                if (attackPlayer) {
                    if (entity instanceof PlayerEntity) {
                        canBeAttacked = true;
                    }
                }

                if (!attackNeutral) {
                    if (entity instanceof PiglinEntity ||
                            entity instanceof IronGolemEntity ||
                            entity instanceof EndermanEntity) {
                        canBeAttacked = false;
                    }
                }
                if (rayTrace) {
                    float[] rotation;
                    switch (aim) {
                        case LEG:
                            rotation = RotationUtil.getRotationForVector(entity.getPos(), player);
                            break;
                        case CENTER:
                            rotation = RotationUtil.getRotationForVector(entity.getPos().add(0, entity.getHeight() / 2, 0), player);
                            break;
                        default:
                            rotation = RotationUtil.getRotationForVector(entity.getPos().add(0, entity.getHeight(), 0), player);
                            break;
                    }
                    if (!MathUtil.hasNoBlockInAngle(player, getDistanceBetweenEntities(entity, player), rotation[0], rotation[1])) {
                        canBeAttacked = false;
                    }
                }
                if (canBeAttacked) {
                    float value = getValueOfEntity(entity, player, mode);
                    if (value <= best) {
                        target = entity;
                        best = value;
                    }
                }
            }
            return target;
        }

        public static float getValueOfEntity(Entity entity, ClientPlayerEntity player, CombatUtil.TargetMode mode) {
            if (entity instanceof LivingEntity) {
                switch (mode) {
                    case DISTANCE:
                        return (float) getDistanceBetweenEntities(entity, player);
                    case HEALTH:
                        return ((LivingEntity) entity).getHealth() + ((LivingEntity) entity).getAbsorptionAmount();
                }
            }
            return Float.MAX_VALUE;
        }

        public static double getDistanceBetweenEntities(Entity entity1, Entity entity2) {
            double x = entity1.getX() - entity2.getX();
            double y = entity1.getY() - entity2.getY();
            double z = entity1.getZ() - entity2.getZ();
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    public static class RenderUtil extends DrawableHelper {

        //Change these to change rendering colors directly in the BufferBuilder
        public static RenderLayer entityCutoutNoCullOverride = null;
        public static RenderLayer itemEntityTranslucentCull = null;
        public static RenderLayer outlineOverride = null;
        public static RenderLayer entityTranslucentOverride = null;
        public static Integer redOverride = null;
        public static Integer greenOverride = null;
        public static Integer blueOverride = null;
        public static Integer alphaOverride = null;
        public static boolean shaderIdentifierOverride = false;

        public static void setRenderColor(int r, int g, int b, int a) {
            redOverride = r;
            greenOverride = g;
            blueOverride = b;
            alphaOverride = a;
        }

        public static void clearRenderColor() {
            redOverride = null;
            greenOverride = null;
            blueOverride = null;
            alphaOverride = null;
        }

        public static boolean hasRenderColor() {
            return redOverride != null || greenOverride != null || blueOverride != null || alphaOverride != null;
        }

        public static void glScissors(int x, int y, int width, int height) {
            net.minecraft.client.util.Window window = CLIENT.getWindow();
            int scale = CLIENT.options.guiScale;
            GL11.glScissor(x * scale, (window.getScaledHeight() - y - height) * scale, width * scale, (height - 1) * scale);
        }

        public static int getWindowWidth() {
            net.minecraft.client.util.Window window = CLIENT.getWindow();
            return window.getScaledWidth();
        }

        public static int getWindowHeight() {
            return CLIENT.getWindow().getScaledHeight();
        }

        public static void drawPolygon(MatrixStack matrices, double startDegree, double endDegree, int corners, int radius, int x, int y, int color) {
            drawPolygon(matrices.peek().getPositionMatrix(), startDegree, endDegree, corners, radius, x, y, color);
        }

        private static void drawPolygon(Matrix4f matrix, double startDegree, double endDegree, int corners, int radius, int x, int y, int color) {
            double increment = 360 / (double) corners;
            x += radius;
            y += radius;
            float a = (float) (color >> 24 & 0xFF) / 255.0f;
            float r = (float) (color >> 16 & 0xFF) / 255.0f;
            float g = (float) (color >> 8 & 0xFF) / 255.0f;
            float b = (float) (color & 0xFF) / 255.0f;
            BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
            RenderSystem.enableBlend();
            RenderSystem.disableTexture();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            bufferBuilder.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
            for (double i = endDegree; i > startDegree; i -= increment) {
                bufferBuilder.vertex(x - Math.cos(Math.toRadians(i)) * radius, y - Math.sin(Math.toRadians(i)) * radius, 0.0D).color(r, g, b, a).next();
            }
            bufferBuilder.end();
            BufferRenderer.draw(bufferBuilder);
            RenderSystem.enableTexture();
            RenderSystem.disableBlend();
        }

        public static void drawColorCircle(MatrixStack matrix, int radius, int x, int y) {
            drawColorCircle(matrix.peek().getPositionMatrix(), radius, x, y);
        }

        private static void drawColorCircle(Matrix4f matrix, int radius, int x, int y) {
            x += radius;
            y += radius;
            BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
            RenderSystem.enableBlend();
            RenderSystem.disableTexture();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            bufferBuilder.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
            bufferBuilder.vertex(x, y, 0f).color(1f, 1f, 1f, 1f).next();
            Color c = new Color(255, 0, 0, 255);
            for (double i = 360; i > -1; i -= 1) {
                int color = c.getRGB();
                float[] hsb = {0, 0, 0};
                hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsb);
                hsb[0] = (float) i / 360f;
                c = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
                float a = (float) (color >> 24 & 0xFF) / 255.0f;
                float r = (float) (color >> 16 & 0xFF) / 255.0f;
                float g = (float) (color >> 8 & 0xFF) / 255.0f;
                float b = (float) (color & 0xFF) / 255.0f;
                bufferBuilder.vertex(x - Math.cos(Math.toRadians(i)) * radius, y - Math.sin(Math.toRadians(i)) * radius, 0.0D).color(r, g, b, a).next();
            }
            bufferBuilder.end();
            BufferRenderer.draw(bufferBuilder);
            RenderSystem.enableTexture();
            RenderSystem.disableBlend();
        }

        public static void fillHGradient(MatrixStack matrix, int x, int y, int w, int h, int c1, int c2) {
            fillHGradient(matrix.peek().getPositionMatrix(), x, y, w, h, c1, c2);
        }

        private static void fillHGradient(Matrix4f matrix, int x, int y, int w, int h, int c1, int c2) {
            BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
            RenderSystem.enableBlend();
            RenderSystem.disableTexture();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            float a1 = (float) (c1 >> 24 & 0xFF) / 255.0f;
            float r1 = (float) (c1 >> 16 & 0xFF) / 255.0f;
            float g1 = (float) (c1 >> 8 & 0xFF) / 255.0f;
            float b1 = (float) (c1 & 0xFF) / 255.0f;
            float a2 = (float) (c2 >> 24 & 0xFF) / 255.0f;
            float r2 = (float) (c2 >> 16 & 0xFF) / 255.0f;
            float g2 = (float) (c2 >> 8 & 0xFF) / 255.0f;
            float b2 = (float) (c2 & 0xFF) / 255.0f;
            bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            bufferBuilder.vertex(x + w, y, 0f).color(r2, g2, b2, a2).next();
            bufferBuilder.vertex(x, y, 0f).color(r1, g1, b1, a1).next();
            bufferBuilder.vertex(x, y + h, 0f).color(r1, g1, b1, a1).next();
            bufferBuilder.vertex(x + w, y + h, 0f).color(r2, g2, b2, a2).next();
            bufferBuilder.end();
            BufferRenderer.draw(bufferBuilder);
            RenderSystem.enableTexture();
            RenderSystem.disableBlend();
        }

        public static void drawRowBasedCheckerBoard(MatrixStack matrix, int rows, int x, int y, int w, int h, int c1, int c2) {
            int cellsize = h / rows;
            if (cellsize < 1) {
                cellsize = 1;
            }
            boolean s1 = true;
            boolean s2 = true;
            int color = c1;
            for (int i = 0; i + cellsize < h; i += cellsize) {
                for (int j = 0; j + cellsize < w; j += cellsize) {
                    fill(matrix, x + j, y + i, x + j + cellsize, y + i + cellsize, color);
                    if (s1) {
                        color = c2;
                    } else {
                        color = c1;
                    }
                    s1 = !s1;
                }
                if (s2) {
                    color = c2;
                    s1 = false;
                } else {
                    color = c1;
                    s1 = true;
                }
                s2 = !s2;
            }
        }

        public static void drawBoxOutline(Matrix4f matrix, BlockPos pos, Camera cam, int rgb, float a) {
            drawBoxOutline(matrix, pos.getX() - cam.getPos().x, pos.getY() - cam.getPos().y, pos.getZ() - cam.getPos().z, pos.getX() + 1 - cam.getPos().x, pos.getY() + 1 - cam.getPos().y, pos.getZ() + 1 - cam.getPos().z, rgb, a);
        }

        public static void drawBoxOutline(Matrix4f matrix, BlockPos pos, Camera cam, int rgba) {
            drawBoxOutline(matrix, pos.getX() - cam.getPos().x, pos.getY() - cam.getPos().y, pos.getZ() - cam.getPos().z, pos.getX() + 1 - cam.getPos().x, pos.getY() + 1 - cam.getPos().y, pos.getZ() + 1 - cam.getPos().z, rgba);
        }

        public static void drawBoxOutline(Matrix4f matrix, BlockPos pos, Camera cam, float r, float g, float b, float a) {
            drawBoxOutline(matrix, pos.getX() - cam.getPos().x, pos.getY() - cam.getPos().y, pos.getZ() - cam.getPos().z, pos.getX() + 1 - cam.getPos().x, pos.getY() + 1 - cam.getPos().y, pos.getZ() + 1 - cam.getPos().z, r, g, b, a);
        }

        public static void drawBoxOutline(Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, int rgba) {
            float a = (float) (rgba >> 24 & 0xFF) / 255.0f;
            drawBoxOutline(matrix, x1, y1, z1, x2, y2, z2, rgba, a);
        }

        public static void drawBoxOutline(Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, int rgb, float a) {
            float r = (float) (rgb >> 16 & 0xFF) / 255.0f;
            float g = (float) (rgb >> 8 & 0xFF) / 255.0f;
            float b = (float) (rgb & 0xFF) / 255.0f;
            drawBoxOutline(matrix, x1, y1, z1, x2, y2, z2, r, g, b, a);
        }

        public static void drawBoxOutline(Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
            drawBoxOutline(matrix, (float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, r, g, b, a);
        }

        public static void drawBoxOutline(Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
            buffer.end();
            BufferRenderer.draw(buffer);
        }

        public static void drawFilledBox(Matrix4f matrix, BlockPos pos, Camera cam, int rgb, float a) {
            drawFilledBox(matrix, pos.getX() - cam.getPos().x, pos.getY() - cam.getPos().y, pos.getZ() - cam.getPos().z, pos.getX() + 1 - cam.getPos().x, pos.getY() + 1 - cam.getPos().y, pos.getZ() + 1 - cam.getPos().z, rgb, a);
        }

        public static void drawFilledBox(Matrix4f matrix, BlockPos pos, Camera cam, int rgba) {
            drawFilledBox(matrix, pos.getX() - cam.getPos().x, pos.getY() - cam.getPos().y, pos.getZ() - cam.getPos().z, pos.getX() + 1 - cam.getPos().x, pos.getY() + 1 - cam.getPos().y, pos.getZ() + 1 - cam.getPos().z, rgba);
        }

        public static void drawFilledBox(Matrix4f matrix, BlockPos pos, Camera cam, float r, float g, float b, float a) {
            drawFilledBox(matrix, pos.getX() - cam.getPos().x, pos.getY() - cam.getPos().y, pos.getZ() - cam.getPos().z, pos.getX() + 1 - cam.getPos().x, pos.getY() + 1 - cam.getPos().y, pos.getZ() + 1 - cam.getPos().z, r, g, b, a);
        }

        public static void drawFilledBox(Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, int rgba) {
            float a = (float) (rgba >> 24 & 0xFF) / 255.0f;
            drawFilledBox(matrix, x1, y1, z1, x2, y2, z2, rgba, a);
        }

        public static void drawFilledBox(Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, int rgb, float a) {
            float r = (float) (rgb >> 16 & 0xFF) / 255.0f;
            float g = (float) (rgb >> 8 & 0xFF) / 255.0f;
            float b = (float) (rgb & 0xFF) / 255.0f;
            drawFilledBox(matrix, x1, y1, z1, x2, y2, z2, r, g, b, a);
        }

        public static void drawFilledBox(Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
            drawFilledBox(matrix, (float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, r, g, b, a);
        }

        public static void drawFilledBox(Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
            buffer.end();
            BufferRenderer.draw(buffer);
        }
        /*
        public static void drawPolygonOutline(double startDegree, double endDegree, int corners, int x, int y, int radius, float width, int color) {
            double increment = 360 / (double) corners;
            x += radius;
            y += radius;
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.disableDepth();
            GlStateManager.tryBlendFuncSeparate(770, 771, 0, 1);
            GlStateManager.disableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.depthMask(false);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);
            GL11.glLineWidth(width);

            float a = (float)(color >> 24 & 255) / 255.0F;
            float r = (float)(color >> 16 & 255) / 255.0F;
            float g = (float)(color >> 8 & 255) / 255.0F;
            float b = (float)(color & 255) / 255.0F;

            final Tessellator tessellator = Tessellator.getInstance();
            final BufferBuilder bufferbuilder = tessellator.getBuffer();
            bufferbuilder.begin(GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for(double i = startDegree; i <= endDegree; i+=increment) {
                bufferbuilder.pos(x-Math.cos(Math.toRadians(i))*radius, y-Math.sin(Math.toRadians(i))*radius, 0.0D).color(r, g, b, a).endVertex();
            }
            bufferbuilder.pos(x-Math.cos(Math.toRadians(endDegree))*radius, y-Math.sin(Math.toRadians(endDegree))*radius, 0.0D).color(r, g, b, a).endVertex();
            tessellator.draw();
            GL11.glDisable(GL_LINE_SMOOTH);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }*/
    }

    public static class Stopper {

        private long checkTime = 0;

        /**
         * It'll start a stopper, and return a boolean value if it was successful or not.
         * NOTE: You must make multiple Stopper objects in order to have multiple stoppers at once! The stopper have to be reseted to use this method!
         */
        public boolean startStopper() {
            if (checkTime == 0) {
                checkTime = System.currentTimeMillis();
                return true;
            }
            return false;
        }

        /**
         * It'll check if the stopper reached the amount of time you give in @param sec.
         * <p>
         * If you want to reset the stopper set @param reset to true. This way, you can start a new stopper in this object.
         */
        public boolean checkStopper(int sec, boolean reset) {
            boolean noStopper = checkTime == 0;
            if (!noStopper) {
                boolean check = checkTime + (sec * 1000) < System.currentTimeMillis();
                if (check) {
                    if (reset)
                        checkTime = 0;
                    return true;
                }
            }

            return noStopper;

        }

        /**
         * It'll check if the stopper reached the amount of time you give in @param milisec.
         * <p>
         * If you want to reset the stopper set @param reset to true. This way, you can start a new stopper in this object.
         */
        public boolean checkStopper(long milisec, boolean reset) {
            boolean noStopper = checkTime == 0;
            if (!noStopper) {
                boolean check = checkTime + milisec < System.currentTimeMillis();
                if (check) {
                    if (reset)
                        checkTime = 0;
                    return true;
                }
            }

            return noStopper;

        }

        /**
         * It resets the stopper, so you can start a new stopper in this object.
         */
        public void resetStopper() {
            checkTime = 0;
        }

        /**
         * Tells you if the stopper has been started or not.
         */
        public boolean isStopperStarted() {
            return checkTime != 0;
        }

        public int getTimePassedSec() {
            return (int) (getTimePassedMilliSec() / 1000);
        }

        public long getTimePassedMilliSec() {
            if (checkTime != 0) {
                return System.currentTimeMillis() - checkTime;
            }
            return 0;
        }

        public void restartStopper() {
            resetStopper();
            startStopper();
        }
    }

    public static class NumberSetting<T extends Number> extends Setting<T> {

        T min;
        T max;

        public NumberSetting(SettingHolder holder, String name, T defaultValue, T min, T max) {
            super(holder, name, defaultValue);
            this.min = min;
            this.max = max;
        }

        public T getMin() {
            return min;
        }

        public T getMax() {
            return max;
        }

        public static class IntegerSetting extends NumberSetting<Integer> {
            public IntegerSetting(SettingHolder holder, String name, Integer defaultValue, Integer min, Integer max) {
                super(holder, name, defaultValue, min, max);
            }
        }

        public static class DoubleSetting extends NumberSetting<Double> {
            public DoubleSetting(SettingHolder holder, String name, Double defaultValue, Double min, Double max) {
                super(holder, name, defaultValue, min, max);
            }
        }

    }

    public static class RotationUtil {

        public static int currentPriority = -1;

        public static float[] getRotationForVector(Vec3d vector, ClientPlayerEntity player) {
            Vec3d eyesPos = new Vec3d(player.getPos().x + player.getVelocity().x,
                    player.getEyeY() + player.getVelocity().y + 0.0784000015258789,
                    player.getPos().z + player.getVelocity().z);

            double diffX = vector.x - eyesPos.x;
            double diffY = vector.y - eyesPos.y;
            double diffZ = vector.z - eyesPos.z;

            if (diffX == 0 && diffZ == 0) {
                diffX = 0.1;
                diffZ = 0.1;
            }

            double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

            float yaw = wrapYaw((float) -Math.toDegrees(Math.atan2(diffX, diffZ)));
            float pitch = wrapPitch((float) -(Math.toDegrees(Math.atan2(diffY, diffXZ))));


            float[] returned = {yaw, pitch};
            return returned;
        }

        public static float wrapYaw(float yaw) {
            if (yaw >= 180) {
                return -180 + (yaw % 180);
            } else if (yaw < -180) {
                return 180 + (yaw % 180);
            }

            return yaw;
        }

        public static float wrapPitch(float pitch) {
            if (pitch >= 90) {
                return 90 - (pitch % 90);
            } else if (pitch < -90) {
                return -90 - (pitch % 90);
            }

            return pitch;
        }

        public static void rotate(float yaw, float pitch) {
            RotationHandler.rotate(yaw, pitch);
        }

        public static void rotate(float yaw, float pitch, int ticks) {
            RotationHandler.rotate(yaw, pitch, ticks);
        }
    }

    @ToldiModule.Type(EnumModuleType.RENDER)
    public static class Fullbright extends ToldiModule {

        @Listener
        public void onGamma() {
            BrightnessCallback.EVENT.register(() -> {
                if (getStatus()) {
                    return ActionResult.FAIL;
                }
                return ActionResult.SUCCESS;
            });
        }

    }

    @ToldiModule.Type(EnumModuleType.RENDER)
    public static class StorageESP extends ToldiModule {

        Setting.BooleanSetting dynamicColor = new Setting.BooleanSetting(this, "dynamiccolor", true);
        Setting.ColorSetting color = new Setting.ColorSetting(this, "color", Color.CYAN.getRGB());

        @Listener
        public void onRender() {
            RenderCallback.LAST.register((matrices, tickDelta, camera) -> {
                if (getStatus()) {
                    for (int i = 0; i < getMC().world.getChunkManager().chunks.chunks.length(); i++) {
                        WorldChunk chunk = getMC().world.getChunkManager().chunks.chunks.getPlain(i);
                        if (chunk == null) continue;
                        chunk.blockEntities.forEach((pos, entity) -> {
                            if (entity instanceof LockableContainerBlockEntity) {
                                int color;
                                if (dynamicColor.getValue()) {
                                    Color c = new Color(getWorld().getBlockState(pos).getMapColor(getWorld(), pos).color);
                                    color = new Color(c.getRed(), c.getGreen(), c.getBlue(), 255).getRGB();
                                } else {
                                    color = this.color.getValue();
                                }
                                Matrix4f matrix = matrices.peek().getPositionMatrix();
                                enableBlend();
                                blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                                depthFunc(GL11.GL_ALWAYS);
                                RenderUtil.drawFilledBox(matrix, pos, camera, color, 0.5f);
                                RenderUtil.drawBoxOutline(matrix, pos, camera, color);
                                depthFunc(GL11.GL_LEQUAL);
                                disableBlend();
                            }
                        });
                    }
                }
                return ActionResult.SUCCESS;
            });
        }
    }

    public static class Setting<T> {

        T value;
        protected String name;
        protected String desc;
        protected SettingHolder holder;

        public Setting(SettingHolder holder, String name, T defaultValue) {
            this.name = name;
            this.desc = "setting." + MODID + "." + holder.getHolderName() + "." + name + ".desc";
            this.value = defaultValue;
            this.holder = holder;
            holder.settings.add(this);
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }

        @SuppressWarnings("unchecked")
        public T cast(Object o) {
            return (T) o;
        }

        public String getUntranslatedName() {
            return name;
        }

        public String getName() {
            try {
                return new TranslatableText("setting." + MODID + "." + holder.getHolderName() + "." + name + ".name").parse(null, null, 0).getString();
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
            return name;
        }

        public String getDesc() {
            try {
                return new TranslatableText(desc).parse(null, null, 0).getString();
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
            return desc;
        }

        public static class BooleanSetting extends Setting<Boolean> {

            public BooleanSetting(SettingHolder holder, String name, Boolean defaultValue) {
                super(holder, name, defaultValue);
            }

        }

        public static class ModeSetting extends Setting<Integer> {

            String[] modes;

            public ModeSetting(SettingHolder holder, String name, Integer defaultValue, String... modes) {
                super(holder, name, defaultValue);
                for (int i = 0; i < modes.length; i++) {
                    modes[i] = "setting." + MODID + "." + holder.getHolderName() + "." + name + "." + modes[i].toLowerCase();
                }
                this.modes = modes;
            }

            public String getMode() {
                try {
                    return new TranslatableText(modes[value]).parse(null, null, 0).getString();
                } catch (CommandSyntaxException e) {
                    e.printStackTrace();
                }
                return modes[value];
            }

            public void increment() {
                value++;
                value %= modes.length;
            }

            public void decrement() {
                value--;
                if (value < 0) value = modes.length - 1;
            }

        }

        public static class KeyBindSetting extends Setting<Integer> {

            ModuleKeyBind bind;

            public KeyBindSetting(ToldiModule module, String name, Integer defaultValue) {
                super(module, name, defaultValue);
                bind = new ModuleKeyBind(module);
                setValue(defaultValue);
            }

            public void setMode(int mode) {
                value = getValue() | mode << 12;
                bind.changeMode(mode);
            }

            @Override
            public void setValue(Integer value) {
                if (value == -1) value = 0;
                if (this.value == -1) this.value = 0;
                this.value = this.value & 0xF000 | value;
                bind.changeKey(value);
            }

            @Override
            public Integer getValue() {
                int n = super.getValue() & 0x0FFF;
                if (n == 0) {
                    return GLFW.GLFW_KEY_UNKNOWN;
                }
                return n;
            }

            public int getMode() {
                return value >> 12;
            }

            public void incrementMode() {
                int mode = getMode();
                mode++;
                mode %= 2;
                setMode(mode);
            }
        }

        public static class ColorSetting extends Setting<Integer> {

            public ColorSetting(SettingHolder holder, String name, Integer defaultValue) {
                super(holder, name, defaultValue);
            }

            public int getAlpha() {
                return (value >> 24) & 0xFF;
            }

            public void setAlpha(int a) {
                value = new Color(getRed(), getGreen(), getBlue(), a).getRGB();
            }

            public int getRed() {
                return (value >> 16) & 0xFF;
            }

            public void setRed(int r) {
                value = new Color(r, getGreen(), getBlue(), getAlpha()).getRGB();
            }

            public int getGreen() {
                return (value >> 8) & 0xFF;
            }

            public void setGreen(int g) {
                value = new Color(getRed(), g, getBlue(), getAlpha()).getRGB();
            }

            public int getBlue() {
                return (value >> 0) & 0xFF;
            }

            public void setBlue(int b) {
                value = new Color(getRed(), getGreen(), b, getAlpha()).getRGB();
            }
        }
    }

    @ToldiModule.Type(EnumModuleType.RENDER)
    public static class Chams extends ToldiModule {

        Setting.ColorSetting hidden = new Setting.ColorSetting(this, "hidden", Color.CYAN.getRGB());
        Setting.ColorSetting visible = new Setting.ColorSetting(this, "visible", Color.CYAN.getRGB());
        Setting.ModeSetting mode = new Setting.ModeSetting(this, "mode", 0, "hidden", "visible", "both");

        Frustum frustum;
        RenderLayer.MultiPhaseParameters.Builder builderLequal = RenderLayer.MultiPhaseParameters.builder().shader(ToldiShaders.OUTLINE_ALPHA).depthTest(RenderPhase.DepthTest.LEQUAL_DEPTH_TEST).cull(RenderPhase.Cull.DISABLE_CULLING).transparency(RenderPhase.Transparency.TRANSLUCENT_TRANSPARENCY);
        RenderLayer.MultiPhaseParameters.Builder builderAlways = RenderLayer.MultiPhaseParameters.builder().shader(ToldiShaders.OUTLINE_ALPHA).depthTest(RenderPhase.DepthTest.ALWAYS_DEPTH_TEST).cull(RenderPhase.Cull.DISABLE_CULLING).transparency(RenderPhase.Transparency.TRANSLUCENT_TRANSPARENCY);

        private static boolean doChamsOverride = false;

        @Listener
        public void onRenderLast() {
            RenderCallback.LAST.register((matrices, tickDelta, camera) -> {
                if (getStatus()) {
                    for (Entity entity : getWorld().getEntities()) {
                        EntityRenderDispatcher dispatcher = getMC().getEntityRenderDispatcher();
                        EntityRenderer<? super Entity> renderer = dispatcher.getRenderer(entity);
                        WorldRenderer wr = getMC().worldRenderer;
                        VertexConsumerProvider.Immediate immediate = wr.bufferBuilders.getEntityVertexConsumers();
                        if (entity == getPlayer() || entity == Modules.FREECAM.cam) continue;

                        RenderPhase.Texture texture = new RenderPhase.Texture(renderer.getTexture(entity), false, false);
                        double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX()) - camera.getPos().x;
                        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY()) - camera.getPos().y;
                        double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ()) - camera.getPos().z;
                        float yaw = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw());
                        matrices.push();
                        matrices.translate(x, y, z);
                        //doChamsOverride = true;
                        //Hidden
                        if (mode.getValue() != 1) {
                            RenderLayer entityesp = RenderLayer.of("entityesp_always", VertexFormats.POSITION_COLOR_TEXTURE, VertexFormat.DrawMode.QUADS, 256, builderAlways.texture(texture).build(false));
                            renderCham(hidden, entityesp, renderer, entity, tickDelta, matrices, immediate);
                            immediate.draw();
                            renderer.render(entity, yaw, tickDelta, matrices, immediate, 1);
                            immediate.draw();
                        }
                        //Visible
                        if (mode.getValue() != 0) {
                            RenderLayer entityesp = RenderLayer.of("entityesp_lequal", VertexFormats.POSITION_COLOR_TEXTURE, VertexFormat.DrawMode.QUADS, 256, builderLequal.texture(texture).build(false));
                            renderCham(visible, entityesp, renderer, entity, tickDelta, matrices, immediate);
                            immediate.draw();
                        }
                        //doChamsOverride = false;
                        matrices.pop();
                    }
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onChamsOverride() {
            ChamsOverrideCallback.EVENT.register(() -> {
                if (getStatus() && doChamsOverride) {
                    return ActionResult.FAIL;
                }
                return ActionResult.SUCCESS;
            });
        }

        private static void renderCham(Setting.ColorSetting color, RenderLayer layer, EntityRenderer<? super Entity> renderer, Entity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate immediate) {
            RenderUtil.setRenderColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            RenderUtil.entityCutoutNoCullOverride = layer;
            RenderUtil.itemEntityTranslucentCull = layer;
            RenderUtil.outlineOverride = layer;
            RenderUtil.entityTranslucentOverride = layer;
            renderer.render(entity, MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()), tickDelta, matrices, immediate, 1);
            RenderUtil.entityTranslucentOverride = null;
            RenderUtil.outlineOverride = null;
            RenderUtil.itemEntityTranslucentCull = null;
            RenderUtil.entityCutoutNoCullOverride = null;
            RenderUtil.clearRenderColor();
        }

        public void setupFrustum(MatrixStack matrices, Vec3d pos, Matrix4f projectionMatrix) {
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();
            double d = pos.getX();
            double e = pos.getY();
            double f = pos.getZ();
            this.frustum = new Frustum(matrix4f, projectionMatrix);
            this.frustum.setPosition(d, e, f);
        }
    }

    public static class ToldiShaders {

        private static Shader outline_alpha;

        public static final RenderPhase.Shader OUTLINE_ALPHA = new RenderPhase.Shader(ToldiShaders::getOutlineAlphaShader);

        public static void loadShader(MinecraftClient client) {
            InitCallback.EVENT.register(() -> {
                try {
                    outline_alpha = new Shader(client.getResourceManager(), "rendertype_outline_alpha", VertexFormats.POSITION_COLOR_TEXTURE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        public static Shader getOutlineAlphaShader() {
            return outline_alpha;
        }
    }

    @ToldiModule.Type(EnumModuleType.RENDER)
    public static class EntityESP extends ToldiModule {

        Setting.ColorSetting color = new Setting.ColorSetting(this, "color", Color.CYAN.getRGB());
        Setting.ModeSetting mode = new Setting.ModeSetting(this, "mode", 0, "box", "line", "cross");
        Setting.BooleanSetting mobs = new Setting.BooleanSetting(this, "mobs", true);
        Setting.BooleanSetting players = new Setting.BooleanSetting(this, "players", true);
        Setting.BooleanSetting inanimate = new Setting.BooleanSetting(this, "inanimate", true);

        //I'm going to learn OpenGL and get back to this later... I won't give up on making a custom shader ESP that easily... TODO
        @Listener
        public void onRender() {
            RenderCallback.LAST.register((matrices, tickDelta, camera) -> {
                if (getStatus()) {
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    CLIENT.getFramebuffer().beginWrite(false);
                    enableBlend();
                    blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                    depthFunc(GL_ALWAYS);
                    for (Entity entity : getWorld().getEntities()) {
                        if (entity == getPlayer() || entity == Modules.FREECAM.cam) continue;
                        if (entity instanceof LivingEntity && mobs.getValue() && !(entity instanceof PlayerEntity)
                                || entity instanceof PlayerEntity && players.getValue()
                                || inanimate.getValue() && !(entity instanceof LivingEntity) && !(entity instanceof PlayerEntity)) {
                            double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX()) - camera.getPos().x;
                            double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY()) - camera.getPos().y;
                            double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ()) - camera.getPos().z;
                            matrices.push();
                            switch (mode.getValue()) {
                                case 0:
                                    matrices.translate(x, y, z);
                                    if (entity instanceof LivingEntity) {
                                        matrices.multiply(new Quaternion(0, -MathHelper.lerp(tickDelta, ((LivingEntity) entity).prevBodyYaw, ((LivingEntity) entity).bodyYaw), 0, true));
                                    } else {
                                        matrices.multiply(new Quaternion(0, -MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()), 0, true));
                                    }
                                    RenderUtil.drawBoxOutline(matrices.peek().getPositionMatrix(), -entity.getWidth() / 2, 0, -entity.getWidth() / 2, entity.getWidth() / 2, entity.getHeight(), entity.getWidth() / 2, color.getValue());
                                    break;
                                case 1:
                                    matrices.translate(x, y, z);
                                    RenderUtil.drawBoxOutline(matrices.peek().getPositionMatrix(), 0, 0, 0, 0, entity.getHeight(), 0, color.getValue());
                                    break;
                                case 2:
                                    matrices.translate(x, y, z);
                                    if (entity instanceof LivingEntity) {
                                        matrices.multiply(new Quaternion(0, -MathHelper.lerp(tickDelta, ((LivingEntity) entity).prevBodyYaw, ((LivingEntity) entity).bodyYaw), 0, true));
                                    } else {
                                        matrices.multiply(new Quaternion(0, -MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()), 0, true));
                                    }
                                    RenderUtil.drawBoxOutline(matrices.peek().getPositionMatrix(), 0, 0, 0, 0, entity.getHeight(), 0, color.getValue());
                                    RenderUtil.drawBoxOutline(matrices.peek().getPositionMatrix(), -entity.getWidth() / 2, entity.getHeight() * 0.7, 0, entity.getWidth() / 2, entity.getHeight() * 0.7, 0, color.getValue());
                                    break;
                            }
                            matrices.pop();
                        }
                    }
                    depthFunc(GL_LEQUAL);
                    disableBlend();
                }
                return ActionResult.SUCCESS;
            });
        }
    }

    @ToldiModule.Type(EnumModuleType.RENDER)
    public static class Freecam extends ToldiModule {

        NumberSetting.DoubleSetting speed = new NumberSetting.DoubleSetting(this, "speed", 3d, 0d, 5d);

        //Don't ask why, I was lazy...
        public VillagerEntity cam;

        @Override
        public void enable() {
            cam = new VillagerEntity(EntityType.VILLAGER, getWorld());
            cam.noClip = true;
            cam.setNoGravity(true);
            cam.setInvisible(true);
            cam.setPosition(getPlayer().getPos());
            cam.setYaw(getPlayer().getHeadYaw());
            cam.setHeadYaw(getPlayer().getHeadYaw());
            cam.setPitch(getPlayer().getPitch());
            cam.setId(-1337);
            getWorld().addEntity(-1337, cam);
            getMC().setCameraEntity(cam);
            getMC().chunkCullingEnabled = false;
            super.enable();
        }

        @Override
        public void disable() {
            super.disable();
            getMC().setCameraEntity(getPlayer());
            getWorld().removeEntity(cam.getId(), Entity.RemovalReason.DISCARDED);
            getMC().chunkCullingEnabled = true;
        }

        @Listener
        public void onDisconnect() {
            JoinLeaveCallback.LEAVE.register(() -> {
                if (getRawStatus()) disable();
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onEntityTick() {
            EntityCallback.TICK.register(entity -> {
                if (getStatus() && entity == cam && cam.age > 1) {
                    if (getMC().options.keyJump.isPressed()) {

                        cam.getVelocity().y = speed.getValue();

                    } else if (getMC().options.keySneak.isPressed()) {

                        cam.getVelocity().y = -speed.getValue();

                    } else {

                        cam.getVelocity().y = 0;

                    }

                    if (getMC().options.keyForward.isPressed() ||
                            getMC().options.keyBack.isPressed() ||
                            getMC().options.keyRight.isPressed() ||
                            getMC().options.keyLeft.isPressed()) {

                        cam.getVelocity().x = MathUtil.calculateX(cam.getYaw(), speed.getValue());

                        cam.getVelocity().z = MathUtil.calculateZ(cam.getYaw(), speed.getValue());

                    } else {

                        cam.getVelocity().x = 0;

                        cam.getVelocity().z = 0;

                    }

                    cam.move(MovementType.SELF, cam.getVelocity());
                }

                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onStopTicking() {
            EntityCallback.STOP_TICKING.register((entity) -> {
                if (getStatus() && entity == cam) {
                    return ActionResult.FAIL;
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onChangeLookDirection() {
            EntityChangeLookDirectionCallback.EVENT.register((entity, x, y) -> {
                if (getStatus() && entity == getPlayer()) {
                    cam.changeLookDirection(x, y);
                    cam.setHeadYaw(cam.getYaw());
                    return ActionResult.FAIL;
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onRenderLast() {
            RenderCallback.ENTITIES.register((matrices, tickDelta, camera) -> {
                if (getStatus()) {
                    Vec3d vec3d = camera.getPos();
                    double x = vec3d.getX();
                    double y = vec3d.getY();
                    double z = vec3d.getZ();
                    VertexConsumerProvider.Immediate immediate = getMC().worldRenderer.bufferBuilders.getEntityVertexConsumers();
                    getMC().worldRenderer.renderEntity(getPlayer(), x, y, z, tickDelta, matrices, immediate);
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onGetCameraPlayer() {
            CameraSpoofCallback.CAMERAPLAYER.register(() -> {
                if (getStatus()) {
                    return ActionResult.FAIL;
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onBlockOutline() {
            CameraSpoofCallback.EVENT.register(() -> {
                if (getStatus()) {
                    return ActionResult.FAIL;
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onPacketOut() {
            PacketCallback.OUT.register(packet -> {
                if (getStatus()) {
                    if (packet instanceof PlayerInteractEntityC2SPacket) {
                        PlayerInteractEntityC2SPacket p = (PlayerInteractEntityC2SPacket) packet;
                        if (p.entityId == getPlayer().getId()) {
                            return ActionResult.FAIL;
                        }
                    }
                }
                return ActionResult.SUCCESS;
            });
        }
    }

    @ToldiModule.Type(EnumModuleType.PLAYER)
    public static class FarThrow extends ToldiModule {

        NumberSetting.IntegerSetting strength = new NumberSetting.IntegerSetting(this, "strength", 30, 1, 300);
        Setting.BooleanSetting arrow = new Setting.BooleanSetting(this, "arrow", true);
        Setting.BooleanSetting potion = new Setting.BooleanSetting(this, "potion", true);
        Setting.BooleanSetting pearl = new Setting.BooleanSetting(this, "pearl", true);
        Setting.BooleanSetting trident = new Setting.BooleanSetting(this, "trident", true);
        Setting.BooleanSetting other = new Setting.BooleanSetting(this, "other", true);

        @Listener
        public void onPacketOut() {
            PacketCallback.OUT.register(packet -> {
                if (getStatus()) {
                    if (packet instanceof PlayerActionC2SPacket) {
                        PlayerActionC2SPacket.Action action = ((PlayerActionC2SPacket) packet).getAction();
                        if (action == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
                            if (arrow.getValue() && getPlayer().getMainHandStack().getItem() == Items.BOW
                                    || trident.getValue() && getPlayer().getMainHandStack().getItem() == Items.TRIDENT) {
                                doExploit();
                            }
                        }
                    }
                    if (packet instanceof PlayerInteractItemC2SPacket) {
                        if (potion.getValue() && (getPlayer().getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE || getPlayer().getMainHandStack().getItem() instanceof ThrowablePotionItem)
                                || pearl.getValue() && getPlayer().getMainHandStack().getItem() == Items.ENDER_PEARL
                                || other.getValue() && (getPlayer().getMainHandStack().getItem() == Items.SNOWBALL || getPlayer().getMainHandStack().getItem() == Items.EGG)) {
                            doExploit();
                        }
                    }
                }
                return ActionResult.SUCCESS;
            });
        }

        private void doExploit() {
            getWorld().sendPacket(new ClientCommandC2SPacket(getPlayer(), ClientCommandC2SPacket.Mode.START_SPRINTING));
            for (int i = 0; i < strength.getValue(); i++) {
                getWorld().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(getPlayer().getPos().x, getPlayer().getPos().y + 1e-10, getPlayer().getPos().z, false));
                getWorld().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(getPlayer().getPos().x, getPlayer().getPos().y, getPlayer().getPos().z, true));
            }
        }
    }

    @ToldiModule.Type(EnumModuleType.PLAYER)
    public static class Debug extends ToldiModule {

        NumberSetting.IntegerSetting test1 = new NumberSetting.IntegerSetting(this, "test1", 5, 0, 10);
        NumberSetting.DoubleSetting test2 = new NumberSetting.DoubleSetting(this, "test2", 5d, 0d, 10d);
        Setting.BooleanSetting test3 = new Setting.BooleanSetting(this, "test3", false);
        Setting.ModeSetting test4 = new Setting.ModeSetting(this, "test4", 0, "A", "B", "C");
        Setting.ColorSetting test5 = new Setting.ColorSetting(this, "test5", Color.BLUE.getRGB());

        @Listener
        public void onTick() {
            ClientTickEvents.START_CLIENT_TICK.register(client -> {
                if (getStatus()) {
                    BlockPos pos = new BlockPos(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
                    long l = pos.asLong();
                    BlockPos pos2 = BlockPos.fromLong(l);
                    getPlayer().sendMessage(Text.of(pos2.getX() + ", " + pos2.getY() + ", " + pos2.getZ()), false);
                }
            });
        }
    }

    @ToldiModule.Type(EnumModuleType.MOVEMENT)
    public static class BoatFly extends ToldiModule {

        NumberSetting.DoubleSetting hspeed = new NumberSetting.DoubleSetting(this, "hspeed", 3d, 0d, 5d);
        NumberSetting.DoubleSetting vspeed = new NumberSetting.DoubleSetting(this, "vspeed", 3d, 0d, 5d);
        Setting.BooleanSetting bypass = new Setting.BooleanSetting(this, "bypass", false);
        Setting.BooleanSetting noclip = new Setting.BooleanSetting(this, "noclip", false);

        BoatEntity boat;
        double yLevel;
        Stopper kickTimer = new Stopper();

        @Override
        public void disable() {
            if (boat != null) {
                boat.setNoGravity(false);
                boat.noClip = false;
                boat = null;
            }
            kickTimer.resetStopper();
            super.disable();
        }

        @Listener
        public void onPlayerTick() {
            EntityCallback.TICK.register(entity -> {
                if (getStatus() && entity == getPlayer()) {
                    if (entity.getVehicle() instanceof BoatEntity && entity.getVehicle().getFirstPassenger() == getPlayer() && (boat == null || boat != entity.getVehicle())) {
                        boat = (BoatEntity) entity.getVehicle();
                        boat.setNoGravity(true);
                        yLevel = boat.getY();
                    } else if ((!(entity.getVehicle() instanceof BoatEntity) || entity.getVehicle().getFirstPassenger() != getPlayer()) && boat != null) {
                        boat.setNoGravity(false);
                        boat.noClip = false;
                        boat = null;
                    }
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onBoatTick() {
            EntityCallback.TICK.register(entity -> {
                if (getStatus() && entity == boat) {
                    entity.setYaw(getPlayer().getHeadYaw());
                    entity.noClip = noclip.getValue();
                    if (getMC().options.keyJump.isPressed() &&
                            !Modules.FREECAM.getStatus()) {
                        kickTimer.startStopper();
                        if (kickTimer.checkStopper(3000l, false)) {
                            entity.getVelocity().y = -0.04;
                            kickTimer.checkStopper(3500l, true);
                        } else {
                            entity.getVelocity().y = vspeed.getValue();
                        }

                        yLevel = entity.getY();


                    } else if (getMC().options.keySprint.isPressed() &&
                            !Modules.FREECAM.getStatus()) {

                        kickTimer.resetStopper();
                        entity.getVelocity().y = -vspeed.getValue();
                        yLevel = entity.getY();

                    } else {

                        kickTimer.resetStopper();

                        if (getPlayer().age % 4 == 0) {
                            entity.getVelocity().y = 0;
                            entity.setPosition(entity.getX(), yLevel, entity.getZ());
                        } else {
                            entity.getVelocity().y = -0.1;
                        }


                    }

                    if ((getMC().options.keyForward.isPressed() ||
                            getMC().options.keyBack.isPressed() ||
                            getMC().options.keyRight.isPressed() ||
                            getMC().options.keyLeft.isPressed()) &&
                            !Modules.FREECAM.getStatus()) {
                        entity.getVelocity().x = MathUtil.calculateX(getPlayer().getYaw(), hspeed.getValue());
                        entity.getVelocity().z = MathUtil.calculateZ(getPlayer().getYaw(), hspeed.getValue());
                    } else {
                        entity.getVelocity().x = 0;
                        entity.getVelocity().z = 0;
                    }
                    if (getPlayer().age % 10 == 0)
                        getWorld().sendPacket(PlayerInteractEntityC2SPacket.interact(boat, false, Hand.MAIN_HAND));
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onPacketIn() {
            PacketCallback.IN.register(packet -> {
                if (getStatus() && bypass.getValue() && boat != null) {
                    if (packet instanceof VehicleMoveS2CPacket) {
                        getWorld().sendPacket(PlayerInteractEntityC2SPacket.interact(boat, false, Hand.MAIN_HAND));
                    }
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onPacketOut() {
            PacketCallback.OUT.register(packet -> {
                if (getStatus() && boat != null) {
                    if (packet instanceof PlayerInputC2SPacket) {
                        PlayerInputC2SPacket p = (PlayerInputC2SPacket) packet;
                        p.sneaking = false;

                    }
                }
                return ActionResult.SUCCESS;
            });
        }
    }

    @ToldiModule.Type(EnumModuleType.MOVEMENT)
    public static class Speed extends ToldiModule {

        Setting.ModeSetting mode = new Setting.ModeSetting(this, "mode", 0, "strafe");

        @Listener
        public void strafe() {
            EntityMoveCallback.PLAYER_FIRST.register((entity, input) -> {
                if (getStatus()) {
                    PlayerEntity player = (PlayerEntity) entity;
                    player.getVelocity().multiply(2);
                }
                return ActionResult.SUCCESS;
            });
        }
    }

    @ToldiModule.Type(EnumModuleType.HUD)
    public static class ActiveModules extends ToldiHudModule {

        @Override
        protected HudPanel createPanel() {
            return new ActiveModulesHudPanel(this, 0f, 0f, 100, 200);
        }


    }

    @ToldiModule.Type(EnumModuleType.EXPLOIT)
    public static class PortalGodmode extends ToldiModule {

        TeleportConfirmC2SPacket packet = null;

        @Override
        public void disable() {
            if (getPlayer() != null && packet != null) {
                getMC().world.sendPacket(packet);
            }
            super.disable();
        }

        @Listener
        public void onLeave() {
            JoinLeaveCallback.LEAVE.register(() -> {
                if (getStatus()) {
                    disable();
                }
                return ActionResult.SUCCESS;
            });
        }

        @Listener
        public void onPacketSent() {
            PacketCallback.OUT.register(packet -> {
                if (getStatus()) {
                    if (packet instanceof TeleportConfirmC2SPacket && getPlayer().hasNetherPortalCooldown()) {
                        this.packet = (TeleportConfirmC2SPacket) packet;
                        return ActionResult.FAIL;
                    }
                }
                return ActionResult.SUCCESS;
            });
        }
    }

    @ToldiModule.Type(EnumModuleType.COMBAT)
    public static class KillAura extends ToldiRotatingModule {

        Setting.BooleanSetting rotate = new Setting.BooleanSetting(this, "rotate", true);
        Setting.BooleanSetting raytrace = new Setting.BooleanSetting(this, "raytrace", true);
        NumberSetting.DoubleSetting distance = new NumberSetting.DoubleSetting(this, "distance", 4d, 1d, 6d);
        Setting.ModeSetting priority = new Setting.ModeSetting(this, "priority", 0, "distance", "health");
        Setting.ModeSetting aim = new Setting.ModeSetting(this, "aim", 1, "leg", "center", "head");
        Setting.BooleanSetting attackPassive = new Setting.BooleanSetting(this, "attackpassive", true);
        Setting.BooleanSetting attackAggressive = new Setting.BooleanSetting(this, "attackaggressive", true);
        Setting.BooleanSetting attackNeutral = new Setting.BooleanSetting(this, "attackneutral", true);
        Setting.BooleanSetting attackInanimate = new Setting.BooleanSetting(this, "attackananimate", true);
        Setting.BooleanSetting attackPlayer = new Setting.BooleanSetting(this, "attackplayer", true);

        @Listener
        public void onTick() {
            ClientTickEvents.START_CLIENT_TICK.register(client -> {
                if (getStatus()) {
                    Entity target = null;
                    float best = Float.MAX_VALUE;
                    float[] rotation = {0, 0};
                    float[] targetRotation = {0, 0};
                    for (Entity entity : getWorld().getEntities()) {
                        if (entity == getPlayer() || entity == Modules.FREECAM.cam) continue;
                        if (CombatUtil.getDistanceBetweenEntities(entity, getPlayer()) > distance.getValue())
                            continue;
                        if (entity instanceof LivingEntity) {
                            if (((LivingEntity) entity).getHealth() <= 0 || ((LivingEntity) entity).isDead()) {
                                continue;
                            }
                        }
                        boolean canBeAttacked = false;
                        if (attackPassive.getValue()) {
                            if (entity instanceof PassiveEntity || entity instanceof AmbientEntity || entity instanceof WaterCreatureEntity) {
                                canBeAttacked = true;
                            }
                        }
                        if (attackAggressive.getValue()) {
                            if (entity instanceof HostileEntity) {
                                canBeAttacked = true;
                            }
                        }
                        if (attackInanimate.getValue()) {
                            if (entity instanceof BoatEntity ||
                                    entity instanceof AbstractMinecartEntity) {
                                canBeAttacked = true;
                            }
                        }
                        if (attackPlayer.getValue()) {
                            if (entity instanceof PlayerEntity) {
                                canBeAttacked = true;
                            }
                        }

                        if (!attackNeutral.getValue()) {
                            if (entity instanceof PiglinEntity ||
                                    entity instanceof IronGolemEntity ||
                                    entity instanceof EndermanEntity) {
                                canBeAttacked = false;
                            }
                        }
                        switch (aim.getValue()) {
                            case 0:
                                rotation = RotationUtil.getRotationForVector(entity.getPos(), getPlayer());
                                break;
                            case 1:
                                rotation = RotationUtil.getRotationForVector(entity.getPos().add(0, entity.getHeight() / 2, 0), getPlayer());
                                break;
                            default:
                                rotation = RotationUtil.getRotationForVector(entity.getPos().add(0, entity.getHeight(), 0), getPlayer());
                                break;
                        }
                        if (raytrace.getValue()) {
                            if (!MathUtil.hasNoBlockInAngle(getPlayer(), CombatUtil.getDistanceBetweenEntities(entity, getPlayer()), rotation[0], rotation[1])) {
                                canBeAttacked = false;
                            }
                        }
                        if (canBeAttacked) {
                            float value = CombatUtil.getValueOfEntity(entity, getPlayer(), CombatUtil.TargetMode.values()[priority.getValue()]);
                            if (value <= best) {
                                target = entity;
                                targetRotation = rotation;
                                best = value;
                            }
                        }
                    }
                    if (target != null) {
                        if (rotate.getValue()) {
                            super.priority = 2;
                            if (isOnTurn()) {
                                RotationUtil.rotate(targetRotation[0], targetRotation[1]);
                                attackTarget(target);
                            }
                            return;
                        } else {
                            attackTarget(target);
                        }
                    }
                    super.priority = -1;
                }
            });
        }

        private void attackTarget(Entity target) {
            if (getPlayer().getAttackCooldownProgress(0f) >= 1) {
                getWorld().sendPacket(PlayerInteractEntityC2SPacket.attack(target, getPlayer().isSneaking()));
                getPlayer().swingHand(Hand.MAIN_HAND);
                getPlayer().resetLastAttackedTicks();
            }
        }

    }

    public enum EnumModuleType implements IModuleType {
        RENDER {
            @Override
            public String getTypeName() {
                return "render";
            }
        },
        EXPLOIT {
            @Override
            public String getTypeName() {
                return "exploit";
            }
        },
        MOVEMENT {
            @Override
            public String getTypeName() {
                return "movement";
            }
        },
        COMBAT {
            @Override
            public String getTypeName() {
                return "combat";
            }
        },
        WORLD {
            @Override
            public String getTypeName() {
                return "world";
            }
        },
        PLAYER {
            @Override
            public String getTypeName() {
                return "player";
            }
        },
        ALL {
            @Override
            public String getTypeName() {
                return "all";
            }
        },
        HUD {
            @Override
            public String getTypeName() {
                return "hud";
            }
        };

        private static final String PREFIX = "type." + MODID + ".";

        public String getName() {
            try {
                return new TranslatableText(PREFIX + getTypeName() + ".name").parse(null, null, 0).getString();
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
            return getTypeName();
        }

        public String getDesc() {
            try {
                return new TranslatableText(PREFIX + getTypeName() + ".desc").parse(null, null, 0).getString();
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
            return getTypeName();
        }
    }

    public static interface IModuleType {

        public String getTypeName();
    }

    public abstract static class ToldiHudModule extends ToldiModule {

        protected HudPanel panel;

        public Setting.ColorSetting color = new Setting.ColorSetting(this, "color", Color.CYAN.getRGB());

        public ToldiHudModule() {
            this.panel = createPanel();
        }

        protected abstract HudPanel createPanel();

        public HudPanel getPanel() {
            return panel;
        }

    }

    public static class ToldiModule extends SettingHolder {

        protected EnumModuleType type;
        protected String name;
        public String info = "";
        protected boolean status = false;

        public Setting.KeyBindSetting keybindSetting = new Setting.KeyBindSetting(this, "keybind", -1);

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        public static @interface Type {
            EnumModuleType value();
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        public static @interface Listener {
        }

        public void initListeners() {
            for (Method method : this.getClass().getMethods()) {
                if (method.getAnnotation(ToldiModule.Listener.class) != null) {
                    try {
                        method.invoke(this);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @SuppressWarnings("incomplete-switch")
        public ToldiModule() {
            ToldiModule.Type type = this.getClass().getAnnotation(ToldiModule.Type.class);
            if (type == null) {
                throw new RuntimeException("Unregistered module! Register " + this.getClass().toString() + " using the @ModuleReg annotiation");
            }
            name = this.getClass().getSimpleName().toLowerCase();
            this.type = type.value();
            switch (this.type) {
                case RENDER:
                    Modules.RENDER.add(this);
                    break;
                case EXPLOIT:
                    Modules.EXPLOIT.add(this);
                    break;
                case MOVEMENT:
                    Modules.MOVEMENT.add(this);
                    break;
                case COMBAT:
                    Modules.COMBAT.add(this);
                    break;
                case WORLD:
                    Modules.WORLD.add(this);
                    break;
                case PLAYER:
                    Modules.PLAYER.add(this);
                    break;
                case HUD:
                    Modules.HUD.add(this);
                    break;
            }
            Modules.ALL.add(this);
            Modules.MODULESBYNAME.put(name, this);
            initListeners();
        }

        public void enable() {
            if (!status) {
                Modules.ACTIVE.add(this);
                status = true;
            }
        }

        public void disable() {
            if (status) {
                Modules.ACTIVE.remove(this);
                status = false;
            }
        }

        public void toggle() {
            if (status) {
                disable();
            } else {
                enable();
            }
        }

        public boolean getStatus() {
            return status && getPlayer() != null;
        }

        public boolean getRawStatus() {
            return status;
        }

        public String getRawName() {
            try {
                return new TranslatableText("module." + MODID + "." + name + ".name").parse(null, null, 0).getString();
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
            return name;
        }

        public String getName() {
            return getRawName() + (info.isEmpty() ? "" : " " + info);
        }

        public String getDescription() {
            try {
                return new TranslatableText("module." + MODID + "." + name + ".desc").parse(null, null, 0).getString();
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
            return name;
        }

        public EnumModuleType getType() {
            return type;
        }

        @Override
        public String getHolderName() {
            return name;
        }

        public String getUntranslatedName() {
            return name;
        }

        //Utils
        protected MinecraftClient getMC() {
            return MinecraftClient.getInstance();
        }

        protected ClientPlayerEntity getPlayer() {
            return getMC().player;
        }

        protected ClientWorld getWorld() {
            return getMC().world;
        }
    }

    public static class ToldiRotatingModule extends ToldiModule {

        protected int priority;

        public ToldiRotatingModule() {
            ClientTickEvents.START_CLIENT_TICK.register(client -> {
                if (getRawStatus()) {
                    updatePriority();
                }
            });
        }

        public void updatePriority() {
            if (priority == -1) {
                info = "";
            } else {
                if (RotationUtil.currentPriority <= priority) {
                    RotationUtil.currentPriority = priority;
                    info = "[ON]";
                } else {
                    info = "[HOLD]";
                }
            }
        }

        @Override
        public void enable() {
            updatePriority();
            super.enable();
        }

        @Override
        public void disable() {
            super.disable();
            RotationUtil.currentPriority = -1;
            RotatingModuleCallback.DISABLE.invoker().handle(this);
        }

        public boolean isOnTurn() {
            return RotationUtil.currentPriority == priority;
        }
    }

    public static class ModuleKeyBind {

        public static Multimap<Integer, ModuleKeyBind> keyMap = ArrayListMultimap.create();
        public static Map<ToldiModule, ModuleKeyBind> keyByModuleMap = new HashMap<>();

        private boolean pressed;
        private boolean pushed;

        private ToldiModule module;
        private int currentKey;
        private int mode;

        public ModuleKeyBind(ToldiModule module) {
            this.module = module;
            this.currentKey = GLFW.GLFW_KEY_UNKNOWN;
            keyMap.put(currentKey, this);
            keyByModuleMap.put(module, this);
        }

        public static void pushKeybinds(int key) {
            Object[] binds;
            if ((binds = keyMap.get(key).toArray()) != null) {
                for (Object bind : binds) {
                    ((ModuleKeyBind) bind).push();
                }
            }
        }

        public static void releaseKeybinds(int key) {
            Object[] binds;
            if ((binds = keyMap.get(key).toArray()) != null) {
                for (Object bind : binds) {
                    ((ModuleKeyBind) bind).release();
                }
            }
        }

        public void changeKey(int newKey) {
            keyMap.remove(currentKey, this);
            currentKey = newKey;
            keyMap.put(currentKey, this);
        }

        public void changeMode(int mode) {
            this.mode = MathHelper.clamp(mode, 0, 1);
        }

        public static ModuleKeyBind getKeyByModule(ToldiModule module) {
            return keyByModuleMap.get(module);
        }

        public void push() {
            if (!pushed) {
                pressed = true;
                pushed = true;
            }
        }

        public void release() {
            pressed = false;
            pushed = false;
        }

        public boolean isPressed() {
            if (pressed) {
                pressed = false;
                return true;
            }
            return false;
        }

        public boolean isPushed() {
            return pushed;
        }

        public ToldiModule getModule() {
            return module;
        }

        public int getMode() {
            return mode;
        }
    }

    public static class Modules {

        public static final List<ToldiModule> RENDER = new ArrayList<>();
        public static final List<ToldiModule> EXPLOIT = new ArrayList<>();
        public static final List<ToldiModule> MOVEMENT = new ArrayList<>();
        public static final List<ToldiModule> COMBAT = new ArrayList<>();
        public static final List<ToldiModule> WORLD = new ArrayList<>();
        public static final List<ToldiModule> PLAYER = new ArrayList<>();
        public static final List<ToldiModule> HUD = new ArrayList<>();

        public static final List<ToldiModule> ALL = new ArrayList<>();

        public static final List<ToldiModule> ACTIVE = new ArrayList<>();

        public static final Map<EnumModuleType, List<ToldiModule>> MODULESBYTYPE = new HashMap<>();

        public static final Map<String, ToldiModule> MODULESBYNAME = new HashMap<>();

        //Render
        public static Freecam FREECAM;
        public static Fullbright FULLBRIGHT;
        public static StorageESP STORAGEESP;
        public static EntityESP ENTITYESP;
        public static Chams CHAMS;

        //Exploit
        public static PortalGodmode PORTALGODMODE;

        //Movement
        public static BoatFly BOATFLY;
        public static Speed SPEED;

        //Combat
        public static KillAura KILLAURA;

        //Player
        public static Debug DEBUG;
        public static FarThrow FARTHROW;

        //World
        public static Timer TIMER;

        //Hud
        public static ActiveModules ACTIVEMODULES;

        public static void registerModules() {
            MODULESBYTYPE.put(EnumModuleType.RENDER, RENDER);
            MODULESBYTYPE.put(EnumModuleType.EXPLOIT, EXPLOIT);
            MODULESBYTYPE.put(EnumModuleType.MOVEMENT, MOVEMENT);
            MODULESBYTYPE.put(EnumModuleType.COMBAT, COMBAT);
            MODULESBYTYPE.put(EnumModuleType.WORLD, WORLD);
            MODULESBYTYPE.put(EnumModuleType.PLAYER, PLAYER);
            MODULESBYTYPE.put(EnumModuleType.HUD, HUD);
            MODULESBYTYPE.put(EnumModuleType.ALL, ALL);

            for (Field field : Modules.class.getFields()) {
                if (ToldiModule.class.isAssignableFrom(field.getType())) {
                    try {
                        field.set(Modules.class, field.getType().getDeclaredConstructor().newInstance());
                    } catch (IllegalArgumentException | IllegalAccessException | InstantiationException
                             | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        public static ToldiModule getModuleByName(String name) {
            return MODULESBYNAME.get(name);
        }
    }

    public static class RotationHandler {

        public static void initRotationHandler() {
            initPacketSpoofer();
            initRenderSpoofer();
        }

        private static Float rotationYaw = null;
        private static Float rotationPitch = null;
        private static Float realYaw = null;
        private static Float realPitch = null;
        private static int ticks = 0;

        public static void rotate(float yaw, float pitch, int ticks) {
            RotationHandler.rotationYaw = yaw;
            RotationHandler.rotationPitch = pitch;
            RotationHandler.ticks = ticks;
        }

        public static void rotate(float yaw, float pitch) {
            rotate(yaw, pitch, 1);
        }

        public static void resetTicks() {
            ticks = 0;
        }

        private static void initPacketSpoofer() {
            SendMovementPacketsCallback.PRE.register(player -> {
                if (rotationYaw != null && rotationPitch != null) {
                    realYaw = player.getYaw();
                    realPitch = player.getPitch();
                    player.setYaw(rotationYaw);
                    player.setPitch(rotationPitch);
                }
                return ActionResult.SUCCESS;
            });
            SendMovementPacketsCallback.POST.register(player -> {
                if (realYaw != null && realPitch != null) {
                    player.setYaw(realYaw);
                    player.setPitch(realPitch);
                    if (ticks == 0) {
                        rotationYaw = null;
                        rotationPitch = null;
                        realYaw = null;
                        realPitch = null;
                    } else {
                        ticks--;
                    }
                }
                return ActionResult.SUCCESS;
            });
        }

        private static Float yaw = null;
        private static Float pitch = null;
        private static Float prevYaw = null;
        private static Float prevPitch = null;

        private static void initRenderSpoofer() {
            RenderEntityCallback.PRE.register((dispatcher, entity, x, y, z, delta, matrices, consumer, light) -> {
                if (entity instanceof ClientPlayerEntity && rotationYaw != null && rotationPitch != null) {
                    ClientPlayerEntity player = (ClientPlayerEntity) entity;
                    yaw = player.getYaw();
                    pitch = player.getPitch();
                    prevYaw = player.prevHeadYaw;
                    prevPitch = player.prevPitch;
                    player.headYaw = rotationYaw;
                    player.setYaw(rotationYaw);
                    player.setPitch(rotationPitch);
                    player.bodyYaw = rotationYaw;
                    player.prevHeadYaw = rotationYaw;
                    player.prevPitch = rotationPitch;
                    player.prevBodyYaw = rotationYaw;
                }
                return ActionResult.SUCCESS;
            });
            RenderEntityCallback.POST.register((dispatcher, entity, x, y, z, delta, matrices, consumer, light) -> {
                if (entity instanceof ClientPlayerEntity && yaw != null && pitch != null && prevYaw != null && prevPitch != null) {
                    ClientPlayerEntity player = (ClientPlayerEntity) entity;
                    player.headYaw = yaw;
                    player.setYaw(yaw);
                    player.setPitch(pitch);
                    player.bodyYaw = yaw;
                    player.prevHeadYaw = prevYaw;
                    player.prevPitch = prevPitch;
                    player.prevBodyYaw = prevYaw;
                    yaw = null;
                    pitch = null;
                    prevYaw = null;
                    prevPitch = null;
                }
                return ActionResult.SUCCESS;
            });
        }
    }

    public static class KeyBindHandler {

        public static KeyBinding clickGuiKey;

        public static void registerKeyBinds() {
            clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key." + MODID + ".clickgui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, NAME));
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                while (clickGuiKey.wasPressed()) {
                    CLIENT.setScreen(new ClickGui());
                }
                ModuleKeyBind.keyByModuleMap.forEach((module, bind) -> {
                    switch (bind.getMode()) {
                        case 0:
                            if (bind.isPressed()) bind.getModule().toggle();
                            break;
                        case 1:
                            if (bind.isPushed() && !module.getStatus()) {
                                module.enable();
                            } else if (!bind.isPushed() && module.getStatus()) {
                                module.disable();
                            }
                    }
                });
            });
            KeyCallback.EVENT.register((key, action) -> {
                if (CLIENT.currentScreen == null) {
                    if (action == 1) {
                        ModuleKeyBind.pushKeybinds(key);
                    } else if (action == 0) {
                        ModuleKeyBind.releaseKeybinds(key);
                    }
                }
                return ActionResult.SUCCESS;
            });
        }
    }

    public static class HudHandler {

        public static void initHudHandler() {
            HudRenderCallback.EVENT.register((matrices, delta) -> {
                if (!(CLIENT.currentScreen instanceof HudEditor)) {
                    for (ToldiModule module : Modules.HUD) {
                        if (module instanceof ToldiHudModule) {
                            ((ToldiHudModule) module).getPanel().render(matrices, -999, -999, delta);
                            ((ToldiHudModule) module).getPanel().resetLocation();
                        }
                    }
                }
            });
        }

    }

    public static class ConfigHandler {

        public static final String DIR = CLIENT.runDirectory.getAbsolutePath() + "\\toldi\\";
        public static String currentProfile = "default";

        public static void initConfigHandler() {
            loadModuleStatus();
            loadModules();
            JoinLeaveCallback.LEAVE.register(() -> {
                saveModuleStatus();
                saveModules();
                return ActionResult.SUCCESS;
            });
        }

        private static void loadModuleStatus() {
            File f = new File(DIR + currentProfile + "\\modulestatus.json");
            String json = readFileIfExists(f);
            if (json == null) return;
            Gson gson = new Gson();
            String[] modules = gson.fromJson(json, String[].class);
            for (String name : modules) {
                ToldiModule module = Modules.getModuleByName(name);
                if (module != null) module.enable();
            }
        }

        private static void saveModuleStatus() {
            List<String> activeModules = new ArrayList<>();
            for (ToldiModule module : Modules.ALL) {
                if (module.getRawStatus()) {
                    activeModules.add(module.getUntranslatedName());
                }
            }
            Gson gson = new Gson();
            String moduleStatus = gson.toJson(activeModules.toArray());
            writeIntoFile(DIR + currentProfile, "modulestatus.json", moduleStatus);
        }

        private static void loadModules() {
            Gson gson = new Gson();
            for (ToldiModule module : Modules.ALL) {
                File f = new File(DIR + currentProfile + "\\" + module.getType().getTypeName() + "\\" + module.getUntranslatedName() + ".json");
                String json = readFileIfExists(f);
                if (json == null) continue;
                Map<String, Object> map = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
                }.getType());
                for (Setting<?> setting : module.settings) {
                    Object o = map.get(setting.getUntranslatedName());
                    if (o != null) {
                        if (setting instanceof NumberSetting.IntegerSetting && o instanceof Double) {
                            ((NumberSetting.IntegerSetting) setting).setValue((int) (double) (Double) o);
                        } else if (setting instanceof NumberSetting.DoubleSetting && o instanceof Double) {
                            ((NumberSetting.DoubleSetting) setting).setValue((Double) o);
                        } else if (setting instanceof Setting.BooleanSetting && o instanceof Boolean) {
                            ((Setting.BooleanSetting) setting).setValue((Boolean) o);
                        } else if (setting instanceof Setting.ModeSetting && o instanceof Double) {
                            ((Setting.ModeSetting) setting).setValue((int) (double) (Double) o);
                        } else if (setting instanceof Setting.KeyBindSetting && o instanceof Double) {
                            ((Setting.KeyBindSetting) setting).setValue((int) (double) (Double) o);
                        } else if (setting instanceof Setting.ColorSetting && o instanceof Double) {
                            ((Setting.ColorSetting) setting).setValue((int) (double) (Double) o);
                        }
                    }
                }
                if (module instanceof ToldiHudModule) {
                    Double x = (Double) map.get("panelxpercentage");
                    Double y = (Double) map.get("panelypercentage");
                    if (x != null) {
                        ((ToldiHudModule) module).getPanel().setXPercentage((float) (double) x);
                    }
                    if (y != null) {
                        ((ToldiHudModule) module).getPanel().setYPercentage((float) (double) y);
                    }
                }
            }
        }

        private static void saveModules() {
            Gson gson = new Gson();
            for (ToldiModule module : Modules.ALL) {
                Map<String, Object> map = new HashMap<>();
                for (Setting<?> setting : module.settings) {
                    map.put(setting.getUntranslatedName(), setting.getValue());
                }
                if (module instanceof ToldiHudModule) {
                    map.put("panelxpercentage", ((ToldiHudModule) module).getPanel().getXPercentage());
                    map.put("panelypercentage", ((ToldiHudModule) module).getPanel().getYPercentage());
                }
                String settings = gson.toJson(map);
                writeIntoFile(DIR + currentProfile + "\\" + module.getType().getTypeName(), module.getUntranslatedName() + ".json", settings);
            }
        }

        private static String readFileIfExists(File file) {
            if (file.exists()) {
                try {
                    return new String(Files.readAllBytes(Paths.get(file.getPath())), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        private static void writeIntoFile(String dir, String fileName, String data) {
            File file = new File(dir + "\\" + fileName);
            file.delete();
            Path path = Paths.get(dir);
            if (!Files.exists(path)) {
                try {
                    Files.createDirectories(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (file.exists()) {
                try {
                    FileWriter w = new FileWriter(file);
                    data = data.replace(",", ",\n\t");
                    data = data.replace("{", "{\n\t");
                    data = data.replace("}", "\n}");
                    data = data.replace("[", "[\n\t");
                    data = data.replace("]", "\n]");
                    w.write(data);
                    w.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static class ClickGui extends Window {

        public ClickGui() {
            super(new LiteralText("clickgui"));
        }

        @Override
        protected void init() {
            panels.clear();
            if (GuiValues.selectedType.ordinal() > 6)
                GuiValues.selectedType = EnumModuleType.RENDER;
            if (GuiValues.selectedModule != null && GuiValues.selectedModule.getType().ordinal() > 6)
                GuiValues.selectedModule = null;

            int width = this.width / 2;
            int height = this.height / 2;

            int categX = width - 200;
            int categY = height - 120;
            MainPanel categories = new MainPanel(categX, categY, 30, 210);
            categories.renderBackground = false;

            categories.addButton(new CategoryButton(EnumModuleType.RENDER, Items.ENDER_EYE, categories, categX, categY));
            categories.addButton(new CategoryButton(EnumModuleType.EXPLOIT, Items.BEDROCK, categories, categX, categY + 30));
            categories.addButton(new CategoryButton(EnumModuleType.MOVEMENT, Items.SADDLE, categories, categX, categY + 60));
            categories.addButton(new CategoryButton(EnumModuleType.COMBAT, Items.END_CRYSTAL, categories, categX, categY + 90));
            categories.addButton(new CategoryButton(EnumModuleType.WORLD, Items.FILLED_MAP, categories, categX, categY + 120));
            categories.addButton(new CategoryButton(EnumModuleType.PLAYER, Items.DIAMOND_HELMET, categories, categX, categY + 150));
            categories.addButton(new CategoryButton(EnumModuleType.ALL, Items.EGG, categories, categX, categY + 180));

            panels.add(categories);

            ModulesMainPanel modules = new ModulesMainPanel(width - 170, height - 120, 168, 210);

            panels.add(modules);

            SettingMainPanel settings = new SettingMainPanel(width + 2, height - 120, 198, 191);

            panels.add(settings);

            KeyBindMainPanel keybind = new KeyBindMainPanel(width + 2, height + 70, 198, 20);

            panels.add(keybind);

            DescriptionMainPanel description = new DescriptionMainPanel(width - 170, height + 92, 370, 16);

            panels.add(description);

            MainPanel windowPanel = new MainPanel(width + 202, height - 120, 50, 226);
            windowPanel.renderBackground = false;
            windowPanel.addButton(new SimpleButton(windowPanel, width + 204, height - 118, 46, 20, "Hud", new Runnable() {
                @Override
                public void run() {
                    CLIENT.setScreen(new HudEditor());
                }
            }));

            panels.add(windowPanel);
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            renderBackground(matrices);
            super.render(matrices, mouseX, mouseY, delta);
        }
    }

    public static class GuiValues {

        public static ToldiModule selectedModule = null;
        public static Panel hoveredPanel = null;
        /**
         * Main
         */
        public static int c1 = 0xff384266;
        /**
         * Secondary
         */
        public static int c2 = 0xff4a5785;
        /**
         * Button
         */
        public static int c3 = 0xff2c3659;
        /**
         * Button Hovered
         */
        public static int c4 = 0xff6882de;
        /**
         * Setting Main
         */
        public static int c5 = 0xff45507a;
        /**
         * Setting Secondary
         */
        public static int c6 = 0xff272d45;
        /**
         * Setting elements
         */
        public static int c7 = 0xff33a0ff;
        public static EnumModuleType selectedType = EnumModuleType.RENDER;
        public static boolean movingHudPanel = false;


    }

    public static class Window extends Screen {

        protected Window(Text title) {
            super(title);
        }

        List<Panel> panels = new ArrayList<>();

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            for (Panel panel : panels) {
                panel.render(matrices, mouseX, mouseY, delta);
            }
            GuiValues.hoveredPanel = null;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (Panel panel : panels) {
                if (button == 0) {
                    panel.click((int) mouseX, (int) mouseY);
                } else if (button == 1) {
                    panel.rightClick((int) mouseX, (int) mouseY);
                }
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            for (Panel panel : panels) {
                panel.releaseMouse((int) mouseX, (int) mouseY);
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            for (Panel panel : panels) {
                if (panel instanceof MainPanel) {
                    ((MainPanel) panel).handleScroll((int) mouseX, (int) mouseY, (int) amount);
                }
            }
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            for (Panel panel : panels) {
                panel.keyPressed(keyCode, scanCode, modifiers);
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            for (Panel panel : panels) {
                panel.keyReleased(keyCode, scanCode, modifiers);
            }
            return false;
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            for (Panel panel : panels) {
                panel.charTyped(chr, modifiers);
            }
            return false;
        }


    }

    public static class HudEditor extends Window {

        protected HudEditor() {
            super(new LiteralText("hudeditor"));
        }

        @Override
        protected void init() {
            panels.clear();
            GuiValues.movingHudPanel = false;
            GuiValues.selectedType = EnumModuleType.HUD;
            GuiValues.selectedModule = null;

            int w = this.width / 2;
            int h = this.height / 2;

            for (ToldiModule module : Modules.HUD) {
                if (module instanceof ToldiHudModule) {
                    ((ToldiHudModule) module).getPanel().resetLocation();
                    ((ToldiHudModule) module).getPanel().drawBackground = true;
                    panels.add(((ToldiHudModule) module).getPanel());
                }
            }

            ModulesMainPanel modules = new ModulesMainPanel(w - 170, h - 120, 168, 210);

            panels.add(modules);

            SettingMainPanel settings = new SettingMainPanel(w + 2, h - 120, 198, 191);

            panels.add(settings);

            KeyBindMainPanel keybind = new KeyBindMainPanel(w + 2, h + 70, 198, 20);

            panels.add(keybind);

            DescriptionMainPanel description = new DescriptionMainPanel(w - 170, h + 92, 370, 16);

            panels.add(description);
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            for (Panel panel : panels) {
                if (panel instanceof HudPanel || !GuiValues.movingHudPanel)
                    panel.render(matrices, mouseX, mouseY, delta);
            }
            GuiValues.hoveredPanel = null;
        }

        @Override
        public void onClose() {
            for (ToldiModule module : Modules.HUD) {
                ((ToldiHudModule) module).getPanel().drawBackground = false;
            }
            super.onClose();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (Panel panel : panels) {
                if (button == 0) {
                    if (panel.click((int) mouseX, (int) mouseY)) break;
                } else if (button == 1) {
                    panel.rightClick((int) mouseX, (int) mouseY);
                }
            }
            return false;
        }
    }
}
