package com.umollu.continuebutton.mixin.client;

import com.umollu.continuebutton.ContinueButtonClient;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TitleScreen.class, priority = 1001)
public class MixinTitleScreen extends Screen {
    private final MultiplayerServerListPinger serverListPinger = new MultiplayerServerListPinger();
    private ServerInfo serverInfo = null;
    private boolean isFirstRender = true;
    private ButtonWidget continueButton;

    protected MixinTitleScreen(Text title) {
        super(title);
    }

    @Inject(at = @At("HEAD"), method = "addNormalWidgets(II)I")
    public void drawMenuButton(int y, int spacingY, CallbackInfoReturnable<Integer> cir) {
        Tooltip tooltip = null;
        if (ContinueButtonClient.lastLocal) {
            if (ContinueButtonClient.serverAddress.isEmpty()) {
                tooltip = Tooltip.of(Text.translatable("selectWorld.create"));
            } else {
                tooltip = Tooltip.of(Text.translatable("menu.singleplayer").append(Text.literal(" " + ContinueButtonClient.serverName)));
            }
        }

        continueButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("continuebutton.continueButtonTitle"), button -> {
            if(ContinueButtonClient.lastLocal) {
                if (!client.getLevelStorage().levelExists(ContinueButtonClient.serverAddress)) {
                    client.setScreen(new SelectWorldScreen(new TitleScreen()));
                } else {
                    client.createIntegratedServerLoader().start(ContinueButtonClient.serverAddress, () -> {
                        client.setScreen(new TitleScreen());
                    });
                }
            }
            else {
                ServerAddress serverAddress2 = ServerAddress.parse(ContinueButtonClient.serverAddress);
                ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), client, serverAddress2, serverInfo, true, (CookieStorage)null);
            }

        }).dimensions(this.width / 2 - 100, y, 98, 20).tooltip(tooltip).build());
    }


    @Inject(at = @At("HEAD"), method = "init()V")
    public void initAtHead(CallbackInfo info) {
        isFirstRender = true;
    }

    @Inject(at = @At("TAIL"), method = "init()V")
    public void init(CallbackInfo info) {
        for (ClickableWidget button : Screens.getButtons(this)) {
            if(button.visible && !button.getMessage().equals(Text.translatable("continuebutton.continueButtonTitle"))) {
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
                ServerInfo serverInList = null;

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

                    serverInfo.label = Text.translatable("multiplayer.status.pinging");
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
    public void renderAtHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if(isFirstRender) {
            isFirstRender = false;
            atFirstRender();
        }
    }

    @Inject(at = @At("TAIL"), method = "render")
    public void renderAtTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (continueButton.isHovered()) {
            if (!ContinueButtonClient.lastLocal) {
                List<OrderedText> list = new ArrayList<>(this.client.textRenderer.wrapLines(serverInfo.label, 270));
                list.add(0, Text.literal(serverInfo.name).formatted(Formatting.GRAY).asOrderedText());
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