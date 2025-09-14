package org.eyecraft.client;

import java.io.IOException;

public class CursorMover {
  private static final int startX = 470;
  private static final int startY = 690;
  private static final int step = 70;
  private static final int rows = 4;
  private static final int cols = 9;

  public int currentIndex = 0;

  // Move to a specific index in the grid by calling move_mouse.py
  public void moveTo(int index) {
    if (index < 0 || index >= rows * cols) {
      throw new IllegalArgumentException("Index out of bounds: " + index);
    }

    int row = index / cols;
    int col = index % cols;

    int x = startX + col * step;
    int y = startY + row * step;

    try {
      // Call the Python script with x and y as arguments
      ProcessBuilder pb = new ProcessBuilder(
              "python3", "/Users/tomasdavola/IdeaProjects/eyecraft-mod1/scripts/move_mouse.py", String.valueOf(x), String.valueOf(y));
      pb.inheritIO(); // optional: prints Python output to console
      Process process = pb.start();
      process.waitFor(); // wait for Python script to finish
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }

    currentIndex = index;
    System.out.printf("Moved to index %d (row=%d, col=%d) → (%d,%d)%n",
            index, row, col, x, y);
  }

  public void next() {
    if (currentIndex < rows * cols - 1) {
      moveTo(currentIndex + 1);
    } else {
      System.out.println("Already at last cell.");
    }
  }


  public void prev() {
    if (currentIndex > 0) {
      moveTo(currentIndex - 1);
    } else {
      System.out.println("Already at first cell.");
    }
  }
}
