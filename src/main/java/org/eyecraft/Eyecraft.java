package org.eyecraft;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerRecipeBook;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class Eyecraft implements ModInitializer {

  @Override
  public void onInitialize() {
    PayloadTypeRegistry.playC2S().register(CraftRequestPayload.ID, CraftRequestPayload.CODEC);

    ServerPlayNetworking.registerGlobalReceiver(CraftRequestPayload.ID, (payload, context) -> {
      ServerPlayerEntity player = context.player();
      Item item = payload.item().value();

      Objects.requireNonNull(player.getServer()).execute(() -> {});
    });
  }
}
