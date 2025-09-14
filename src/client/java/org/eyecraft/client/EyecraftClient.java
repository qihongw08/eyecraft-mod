package org.eyecraft.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

public class EyecraftClient implements ClientModInitializer {

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

  private void handleCommands(MinecraftClient mc) {
    ClientPlayerEntity player = mc.player;
    if (player == null) return;

    if (walking) moveForward(player, 0.2);
    if (jumping && player.isOnGround()) jump(player);

    if (yawDelta >= 20f || pitchDelta >= 20f) {
      rotate(player, yawDelta, pitchDelta);
      yawDelta = 0f;
      pitchDelta = 0f;
    }

//    if (leftClick) {
//      if (mc.crosshairTarget instanceof net.minecraft.world.entity.EntityHitResult entityHit) {
//        assert mc.interactionManager != null;
//        mc.interactionManager.attackEntity(player, entityHit.getEntity());
//      } else {
//        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND); // just swing arm
//      }
//    }
//
//    if (rightClick) {
//      assert mc.interactionManager != null;
//      mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, BlockHitResult)
//    }

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

  private void rotate(ClientPlayerEntity player, float deltaYaw, float deltaPitch) {
    // Update yaw
    float newYaw = player.getYaw() + deltaYaw;
    player.setYaw(newYaw);
    player.setHeadYaw(newYaw);

    // Update pitch with clamp
    float newPitch = player.getPitch() + deltaPitch;
    newPitch = Math.max(-90.0f, Math.min(90.0f, newPitch));
    player.setPitch(newPitch);
  }

  private void startPythonListener() {
    System.out.println("Starting Python listener...");
    try {
      ProcessBuilder pb = new ProcessBuilder(
          "/Users/qihongwu/eyecraft/.venv/bin/python3.12",
          "/Users/qihongwu/eyecraft/message.py"
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

          // Directly set the deltas, do NOT accumulate
          pitchDelta = Float.parseFloat(parts[4]) * 0.1f;
          yawDelta   = Float.parseFloat(parts[5]) * 0.1f;
        }
      }

      process.waitFor();
    } catch (Exception e) {
      System.out.println("Python listener error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}