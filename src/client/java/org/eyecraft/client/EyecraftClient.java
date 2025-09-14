package org.eyecraft.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.context.ContextParameterMap;
import org.eyecraft.Eyecraft;

public class EyecraftClient implements ClientModInitializer {
  private boolean isInventoryOpen = false;

  // State variables controlled by Python
  private volatile boolean walking = false;
  private volatile boolean jumping = false;
  private volatile boolean leftClick = false;
  private volatile boolean rightClick = false;
  private volatile float pitchDelta = 0f;
  private volatile float yawDelta = 0f;

  @Override
  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(this::handleCommands);
    ClientTickEvents.END_CLIENT_TICK.register(this::handleCrafting); // test
    ScreenEvents.AFTER_INIT.register(this::handleNewScreen);
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      Screen current = MinecraftClient.getInstance().currentScreen;
      if (current instanceof InventoryScreen) {
        isInventoryOpen = true;
//        clickSlot(10);
      }
    });

    // Optional test commands
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
      dispatcher.register(ClientCommandManager.literal("testforward")
          .executes(context -> {
            walking = true;
            return 1;
          }));
      dispatcher.register(ClientCommandManager.literal("jump")
          .executes(context -> {
            jumping = true;
            return 1;
          }));
      dispatcher.register(ClientCommandManager.literal("checkinv")
          .executes(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Screen current = client.currentScreen;
            if (current instanceof HandledScreen<?>) {
              System.out.println("Inventory (or container) is open!");
            } else {
              System.out.println("No inventory open.");
            }
            return 1;
          }));
    });

    new Thread(this::startPythonListener, "PythonListener").start();
  }

//  private Item getItemFromName(String name) {
//    String path = name.trim().toLowerCase(java.util.Locale.ROOT)
//        .replace(' ', '_')
//        .replace('-', '_');
//
//    // Build an Identifier safely for both "minecraft:oak_log" and "oak_log"
//    Identifier id = path.indexOf(':') >= 0
//        ? Identifier.tryParse(path)         // null if invalid
//        : Identifier.ofVanilla(path);       // "minecraft" namespace
//
//    if (id == null) return null;
//
//    // 1.21.0–1.21.1
//    return Registries.ITEM.getOrEmpty(id).orElse(null);
//  }

  private void handleNewScreen(MinecraftClient mc, Screen screen, int i, int i1) {

  }

  private void handleCrafting(MinecraftClient mc) {
    if (mc.player == null || mc.interactionManager == null) return;

    if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler)
        && !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return; // must be in 3x3; inventory screen works for 2x2

    NetworkRecipeId netId = findCraftableByOutput(mc, Items.OAK_PLANKS);
    if (netId == null) return;

    int syncId = mc.player.currentScreenHandler.syncId;
    boolean craftAll = false; // set true for shift-craft behavior
    mc.interactionManager.clickRecipe(syncId, netId, craftAll);
  }

  // Returns a craftable NetworkRecipeId for a given output Item, or null if none.
  public static NetworkRecipeId findCraftableByOutput(MinecraftClient mc, Item target) {
    ClientRecipeBook book = mc.player.getRecipeBook(); // visible in UI, includes ordered results
    List<RecipeResultCollection> groups = book.getOrderedResults(); // what the book renders
    RecipeFinder finder = fromPlayerInventory(mc);

    for (RecipeResultCollection group : groups) {
      // Optionally refresh craftable/displayable flags against current inventory
      group.populateRecipes(finder, display -> true);

      for (var entry : group.getAllRecipes()) {
        // Each entry carries a runtime NetworkRecipeId and a display describing output
        var stacks = entry.getStacks(RecipeContexts.EMPTY_CTX); // output/preview stacks
        boolean matchesOutput = !stacks.isEmpty() && stacks.get(0).getItem() == target;
        if (!matchesOutput) continue;

        NetworkRecipeId netId = entry.id();
        if (group.isCraftable(netId)) {
          return netId;
        }
      }
    }
    return null;
  }

  private static RecipeFinder fromPlayerInventory(MinecraftClient mc) {
    RecipeFinder finder = new RecipeFinder();
    var inv = mc.player.getInventory();
    for (int i = 0; i < inv.size(); i++) {
      ItemStack stack = inv.getStack(i);
      if (!stack.isEmpty()) {
        finder.addInputIfUsable(stack);
      }
    }
    return finder;
  }

  private void handleCommands(MinecraftClient mc) {
    ClientPlayerEntity player = mc.player;
    if (player == null) return;

    if (walking) moveForward(player, 0.2);
    if (jumping && player.isOnGround()) jump(player);

    if (yawDelta >= 20f || pitchDelta >= 20f) {
      rotate(player, yawDelta * 0.1f, pitchDelta * 0.1f);
      yawDelta = 0f;
      pitchDelta = 0f;
    }

    if (mc.interactionManager == null) return;

    if (leftClick) {
      if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
        mc.interactionManager.attackEntity(player, entityHit.getEntity());
      } else if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
        mc.interactionManager.updateBlockBreakingProgress(blockHit.getBlockPos(), blockHit.getSide());
        player.swingHand(Hand.MAIN_HAND);
      } else {
        player.swingHand(Hand.MAIN_HAND);
      }
    }

    if (rightClick) {
      // Get item in hand
      ItemStack stack = player.getMainHandStack();

      if (stack.contains(DataComponentTypes.CONSUMABLE)) {
        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
      } else if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
        mc.interactionManager.interactEntity(player, entityHit.getEntity(), Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
      } else if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
        player.swingHand(Hand.MAIN_HAND);
      } else {
        player.setCurrentHand(Hand.MAIN_HAND);
        mc.options.useKey.setPressed(true);
      }
    } else {
      mc.options.useKey.setPressed(false);
    }

    jumping = false;
  }

  private void moveForward(ClientPlayerEntity player, double speed) {
    double yawRad = Math.toRadians(player.getYaw());
    double currentYVel = player.getVelocity().y;
    Vec3d vec3d = new Vec3d(
        -Math.sin(yawRad) * speed,
        currentYVel,
        Math.cos(yawRad) * speed
    );
    player.setVelocity(vec3d);
  }

  private void strafe(ClientPlayerEntity player, double speed) {
    double yawRad = Math.toRadians(player.getYaw());
    double currentYVel = player.getVelocity().y;
    Vec3d vec3d = new Vec3d(
        Math.cos(yawRad) * speed,
        currentYVel,
        Math.sin(yawRad) * speed
    );
    player.setVelocity(vec3d);
  }

  private void jump(ClientPlayerEntity player) {
    Vec3d currentVel = player.getVelocity();
    Vec3d vec3d = new Vec3d(currentVel.x, 0.42, currentVel.z);
    player.setVelocity(vec3d);
  }

  private void clickSlot(int slotId) {
    MinecraftClient client = MinecraftClient.getInstance();
    ClientPlayerEntity player = client.player;

    if (player != null && player.currentScreenHandler != null) {
      client.interactionManager.clickSlot(
              player.currentScreenHandler.syncId, // the container ID
              slotId,                             // the slot index
              0,                                  // mouse button (0 = left, 1 = right)
              SlotActionType.PICKUP,              // action type (PICKUP, QUICK_MOVE, etc.)
              player
      );
    }
  }

  private void rotate(ClientPlayerEntity player, float deltaYaw, float deltaPitch) {
    float newYaw = player.getYaw() + deltaYaw;
    player.setYaw(newYaw);
    player.setHeadYaw(newYaw);

    float newPitch = player.getPitch() + deltaPitch;
    newPitch = Math.max(-90.0f, Math.min(90.0f, newPitch));
    player.setPitch(newPitch);
  }

  private void startPythonListener() {
    try {
      ProcessBuilder pb = new ProcessBuilder(
          "C:/Users/troyh/AppData/Local/Programs/Python/Python312/python.exe",
          "C:/Users/troyh/Documents/dev/eyecraft-script/message.py"
      );
      pb.redirectErrorStream(true);
      Process process = pb.start();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream())
      );

      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        if (parts.length == 6) {
          leftClick = parts[0].equalsIgnoreCase("True");
          rightClick = parts[1].equalsIgnoreCase("True");
          jumping = parts[2].equalsIgnoreCase("True");
          walking = parts[3].equalsIgnoreCase("True");

          pitchDelta = Float.parseFloat(parts[4]);
          yawDelta   = Float.parseFloat(parts[5]);
        }
      }

      process.waitFor();
    } catch (Exception e) {
      System.out.println("Python listener error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}