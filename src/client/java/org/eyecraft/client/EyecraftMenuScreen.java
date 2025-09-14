package org.eyecraft.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

public class EyecraftMenuScreen extends Screen {
  public EyecraftMenuScreen() {
    super(Text.of("Custom Menu"));
  }

  @Override
  protected void init() {
    this.addDrawableChild(
            ButtonWidget.builder(Text.of("Click Me"), button -> {
                      // Example: close screen
                      MinecraftClient.getInstance().setScreen(null);
                    })
                    .dimensions(this.width / 2 - 100, this.height / 4, 200, 20)
                    .build()
    );
  }

  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    this.renderBackground(context, mouseX, mouseY, delta); // ✅ fixed
    super.render(context, mouseX, mouseY, delta);
    context.drawCenteredTextWithShadow(
            this.textRenderer, this.title,
            this.width / 2, 20, 0xFFFFFF
    );
  }
}
