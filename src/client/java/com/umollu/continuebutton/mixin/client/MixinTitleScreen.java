package com.umollu.continuebutton.mixin.client;

import com.umollu.continuebutton.ContinueButtonClient;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
// import net.minecraft.client.multiplayer.CookieStorage;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TitleScreen.class, priority = 1001)
public class MixinTitleScreen extends Screen {
    private final ServerStatusPinger serverListPinger = new ServerStatusPinger();
    private ServerData serverInfo = null;
    private boolean isFirstRender = true;
    private Button continueButton;
    Minecraft client = Minecraft.getInstance();

    protected MixinTitleScreen(Component title) {
        super(title);
    }

    @Inject(at = @At("HEAD"), method = "createNormalMenuOptions")
    public void drawMenuButton(int y, int spacingY, CallbackInfoReturnable<Integer> cir) {
        Tooltip tooltip = null;
        if (ContinueButtonClient.lastLocal) {
            if (ContinueButtonClient.serverAddress.isEmpty()) {
                tooltip = Tooltip.create(Component.translatable("selectWorld.create"));
            } else {
                tooltip = Tooltip.create(Component.translatable("menu.singleplayer").append(Component.literal(" " + ContinueButtonClient.serverName)));
            }
        }

        continueButton = this.addDrawableChild(Button.builder(Component.translatable("continuebutton.continueButtonTitle"), button -> {
            if(ContinueButtonClient.lastLocal) {
                if (!client.getLevelSource().levelExists(ContinueButtonClient.serverAddress)) {
                    client.setScreen(new SelectWorldScreen(new TitleScreen()));
                } else {
                    client.createIntegratedServerLoader().start(ContinueButtonClient.serverAddress, () -> {
                        client.setScreen(new TitleScreen());
                    });
                }
            }
            else {
                ServerAddress serverAddress2 = ServerAddress.parse(ContinueButtonClient.serverAddress);
                ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client, serverAddress2, serverInfo, true, (CookieStorage)null);
            }
        }).dimensions(this.width / 2 - 100, y, 98, 20).tooltip(tooltip).build());
    }


    @Inject(at = @At("HEAD"), method = "init()V")
    public void initAtHead(CallbackInfo info) {
        isFirstRender = true;
    }

    @Inject(at = @At("TAIL"), method = "init()V")
    public void init(CallbackInfo info) {
        for (AbstractWidget button : Screens.getButtons(this)) {
            if(button.visible && !button.getMessage().equals(Component.translatable("continuebutton.continueButtonTitle"))) {
                button.setX(this.width / 2 + 2);
                button.setWidth(98);
                break;
            }
        }
    }

    private void atFirstRender() {
        new Thread(() -> {
            if (!ContinueButtonClient.lastLocal) {
                ServerList serverList = new ServerList(this.client);
                serverList.loadFile();
                ServerData serverInList = null;

                for(int i = 0; i < serverList.size(); i++) {
                    if(serverList.get(i).address.equalsIgnoreCase(ContinueButtonClient.serverAddress)) {
                        serverInList = serverList.get(i);
                        break;
                    }
                }

                if(serverInList == null) {
                    ContinueButtonClient.lastLocal = true;
                    ContinueButtonClient.serverName = "";
                    ContinueButtonClient.serverAddress = "";
                    ContinueButtonClient.saveConfig();
                }
                else {
                    serverInfo = serverInList;

                    ContinueButtonClient.lastLocal = false;
                    ContinueButtonClient.serverName = serverInfo.name;
                    ContinueButtonClient.serverAddress = serverInfo.address;
                    ContinueButtonClient.saveConfig();

                    serverInfo.label = Component.translatable("multiplayer.status.pinging");
                    ServerAddress address = ServerAddress.parse(serverInfo.address);
                    try {
                        serverListPinger.add(
                                serverInfo,
                                null,
                                () -> {},
                                null
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @Inject(at = @At("HEAD"), method = "render")
    public void renderAtHead(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if(isFirstRender) {
            isFirstRender = false;
            atFirstRender();
        }
    }

    @Inject(at = @At("TAIL"), method = "render")
    public void renderAtTail(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (continueButton.isHovered()) {
            if (!ContinueButtonClient.lastLocal) {
                List<FormattedText> list = new ArrayList<>(this.client.textRenderer.wrapLines(serverInfo.label, 270));
                list.add(0, Component.literal(serverInfo.name).formatted(ChatFormatting.GRAY).asOrderedText());
                context.drawOrderedTooltip(this.textRenderer, list, mouseX, mouseY);
            }
        }
    }

    @Inject(at = @At("RETURN"), method = "tick()V")
    public void tick(CallbackInfo info) {
        serverListPinger.tick();
    }
    @Inject(at = @At("RETURN"), method = "removed()V")
    public void removed(CallbackInfo info) {
        serverListPinger.cancel();
    }
}