import os
from PIL import Image

# Configuration
SOURCE_IMAGE_PATH = r"C:\Users\Tobias Schnizer\.gemini\antigravity\brain\8faed81f-6a73-4ee1-9251-fe640879c94c\eggplant_desk_mic_curved_1768567741991.png"
ANDROID_RES_DIR = r"f:\Repos\Personal\Big-DicTaphone\android\app\src\main\res"
PLAYSTORE_ICON_PATH = r"f:\Repos\Personal\Big-DicTaphone\android\app\src\main\ic_launcher-playstore.png"

ICON_CONFIGS = [
    {"dir": "mipmap-mdpi", "size": (48, 48)},
    {"dir": "mipmap-hdpi", "size": (72, 72)},
    {"dir": "mipmap-xhdpi", "size": (96, 96)},
    {"dir": "mipmap-xxhdpi", "size": (144, 144)},
    {"dir": "mipmap-xxxhdpi", "size": (192, 192)},
]

FILENAMES = ["ic_launcher.png", "ic_launcher_round.png"]

def main():
    print(f"Processing icon from: {SOURCE_IMAGE_PATH}")
    
    try:
        if not os.path.exists(SOURCE_IMAGE_PATH):
             print(f"Error: Source image not found at {SOURCE_IMAGE_PATH}")
             return

        # Open the source image
        with Image.open(SOURCE_IMAGE_PATH) as img:
            # Convert to RGBA to ensure transparency support
            img = img.convert("RGBA")

            # 1. Process standard launcher icons
            for config in ICON_CONFIGS:
                target_dir = os.path.join(ANDROID_RES_DIR, config["dir"])
                os.makedirs(target_dir, exist_ok=True)
                
                # Resize
                resized_img = img.resize(config["size"], Image.Resampling.LANCZOS)
                
                for filename in FILENAMES:
                    target_path = os.path.join(target_dir, filename)
                    resized_img.save(target_path, "PNG")
                    print(f"Saved {config['size']} icon to: {target_path}")

            # 2. Process Play Store icon (512x512)
            playstore_size = (512, 512)
            playstore_img = img.resize(playstore_size, Image.Resampling.LANCZOS)
            playstore_img.save(PLAYSTORE_ICON_PATH, "PNG")
            print(f"Saved Play Store icon to: {PLAYSTORE_ICON_PATH}")

        print("Icon generation complete.")

    except Exception as e:
        print(f"An error occurred: {e}")

if __name__ == "__main__":
    main()
