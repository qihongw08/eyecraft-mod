package org.eyecraft.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class EyecraftClient implements ClientModInitializer {

  private final ConcurrentLinkedQueue<String> commandQueue = new ConcurrentLinkedQueue<>();

  @Override
  public void onInitializeClient() {
    // Register tick handler
    ClientTickEvents.END_CLIENT_TICK.register(this::handleCommands);

    // Optional test commands
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
      dispatcher.register(ClientCommandManager.literal("testforward")
          .executes(context -> {
            sendCommand("forward");
            return 1;
          }));
      dispatcher.register(ClientCommandManager.literal("jump")
          .executes(context -> {
            sendCommand("jump");
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

    // Start Python listener thread
    new Thread(this::startPythonListener, "PythonListener").start();
  }

  private void handleCommands(MinecraftClient mc) {
    ClientPlayerEntity player = mc.player;
    if (player == null) return;

    while (!commandQueue.isEmpty()) {
      String command = commandQueue.poll();

      if (command.startsWith("rotate")) {
        // rotate,yawDelta,pitchDelta
        String[] parts = command.split(",");
        float yawDelta = Float.parseFloat(parts[1]);
        float pitchDelta = Float.parseFloat(parts[2]);
        rotate(player, yawDelta, pitchDelta);
        continue;
      }

      switch (command) {
        case "forward" -> moveForward(player, 0.2);
        case "back" -> moveForward(player, -0.2);
        case "strafeLeft" -> strafe(player, 0.2);
        case "strafeRight" -> strafe(player, -0.2);
        case "jump" -> jump(player);
      }
    }
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

  public void sendCommand(String command) {
    commandQueue.add(command);
  }

  private void startPythonListener() {
    try {
      ProcessBuilder pb = new ProcessBuilder("python3", "/Users/.../your_script.py");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream())
      );

      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        boolean isWalking = parts[0].equals("1");
        boolean isJumping = parts[1].equals("1");
        boolean isLeftClick = parts[2].equals("1");
        boolean isRightClick = parts[3].equals("1");
        float pitchDelta = Float.parseFloat(parts[4]);
        float yawDelta = Float.parseFloat(parts[5]);

        // Queue commands for tick handler
        if (isWalking) commandQueue.add("forward");
        if (isJumping) commandQueue.add("jump");
        if (isLeftClick) commandQueue.add("attack");
        if (isRightClick) commandQueue.add("use");

        // Queue rotation
        commandQueue.add("rotate," + yawDelta + "," + pitchDelta);
      }

      process.waitFor();
    } catch (Exception e) {
      System.out.println("Python listener error: " + e.getMessage());
    }
  }
}
