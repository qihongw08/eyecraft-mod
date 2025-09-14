import sys
import pyautogui

def move_cursor(x, y, duration=0.0):
    """Move the cursor to (x, y) over duration seconds."""
    pyautogui.moveTo(x, y, duration=duration)

def main():
    if len(sys.argv) != 3:
        sys.exit(1)

    try:
        x = int(sys.argv[1])
        y = int(sys.argv[2])
    except ValueError:
        sys.exit(1)

    move_cursor(x, y)
# Write in bash ?
if __name__ == "__main__":
    main()
