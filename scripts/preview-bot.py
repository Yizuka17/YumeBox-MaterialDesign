import requests
import os
import glob

BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = os.environ.get("CHAT_ID")
MESSAGE_THREAD_ID = os.environ.get("MESSAGE_THREAD_ID")


def check_environ():
    if BOT_TOKEN is None:
        print("[-] Invalid BOT_TOKEN")
        exit(1)
    if CHAT_ID is None:
        print("[-] Invalid CHAT_ID")
        exit(1)


def find_preview_files():
    patterns = [
        "screenshots/preview.png",
        "./screenshots/preview.png",
        "/github/workspace/screenshots/preview.png"
    ]

    files = []
    for pattern in patterns:
        found = glob.glob(pattern)
        if found:
            files.extend(found)
            print(f"[+] Found {len(found)} files matching {pattern}")

    if not files:
        print("[-] No preview files found!")
        exit(1)

    files = list(set(files))
    print(f"[+] Total unique files: {len(files)}")
    for f in files:
        print(f"    - {f}")

    return files


def send_files_via_bot_api():
    print("[+] Starting Telegram upload")
    check_environ()

    files = find_preview_files()

    bot_url = f"https://api.telegram.org/bot{BOT_TOKEN}"

    file_path = files[0]
    print(f"[+] Uploading {file_path}...")

    with open(file_path, 'rb') as f:
        data = {
            'chat_id': CHAT_ID,
        }

        if MESSAGE_THREAD_ID:
            data['message_thread_id'] = MESSAGE_THREAD_ID

        files_data = {
            'photo': f
        }

        response = requests.post(
            f"{bot_url}/sendPhoto",
            data=data,
            files=files_data,
            timeout=120
        )

    if response.status_code == 200:
        print(f"[+] {file_path} uploaded successfully!")
        return True
    else:
        print(f"[-] Failed to upload {file_path}: {response.text}")
        return False


if __name__ == "__main__":
    try:
        if not send_files_via_bot_api():
            raise SystemExit(1)
    except Exception as e:
        print(f"[-] Error: {e}")
        raise SystemExit(1)
