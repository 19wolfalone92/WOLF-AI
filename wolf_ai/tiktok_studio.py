"""Owner-operated TikTok Studio Chromium uploader (NO Metricool).

On the user's own PC with a persistent Playwright profile only. It does not
run on GitHub Actions, store/export tokens, defeat captchas, scrape accounts,
or bypass TikTok's permissions. Public posting is individually confirmed.

The official TikTok Content Posting API remains the more stable path
when developer approvals and video.publish scope are available.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

from wolf_ai.cloud_browser import BrowserSafetyError
from wolf_ai.private_browser import prohibit_ci, profile_directory

STUDIO_URL = "https://www.tiktok.com/tiktokstudio/upload"
ALLOWED_HOSTS = {"www.tiktok.com", "tiktok.com", "studio.tiktok.com"}
MAX_VIDEO_BYTES = 1_500_000_000
MAX_CAPTION = 2200


def valid_video(video: str) -> Path:
    path = Path(video).expanduser().resolve(strict=True)
    if not path.is_file() or path.suffix.lower() != ".mp4":
        raise BrowserSafetyError("Требуется собственный файл .mp4")
    if not (1024 < path.stat().st_size <= MAX_VIDEO_BYTES):
        raise BrowserSafetyError("Размер MP4 вне диапазона 1 КБ–1.5 ГБ")
    with path.open("rb") as f:
        # MP4 brand must be near the beginning of an actual file.
        first = f.read(32)
    if len(first) < 12 or first[4:8] != b"ftyp":
        raise BrowserSafetyError("Файл не похож на контейнер MP4 (нет ftyp).")
    return path


def valid_caption(caption: str) -> str:
    if not isinstance(caption, str) or len(caption) > MAX_CAPTION:
        raise BrowserSafetyError("Подпись не длиннее 2200 символов")
    if "\x00" in caption or any(ord(x) < 32 and x not in "\n\r\t" for x in caption):
        raise BrowserSafetyError("Недопустимый управляющий символ в подписи")
    return caption.strip()


def assert_studio(url: str) -> None:
    parsed = urlsplit(url)
    if (parsed.scheme != "https" or parsed.hostname not in ALLOWED_HOSTS
            or parsed.port is not None or "studio" not in parsed.path.lower()):
        raise BrowserSafetyError("Не официальная страница загрузки TikTok Studio.")


def unique_video_input(page):
    nodes = page.locator('input[type="file"]')
    count = nodes.count()
    if count == 1:
        return nodes.first
    # Some versions have image and video upload widgets.
    matches = page.locator('input[type="file"][accept*="video"]')
    if matches.count() == 1:
        return matches.first
    raise BrowserSafetyError("У TikTok Studio нет однозначного поля MP4. " +
                             "Открой вкладку загрузки вручную.")


def fill_caption_if_clear(page, caption: str) -> bool:
    if not caption:
        return True
    # TikTok Studio UI varies: do not guess when multiple editors exist.
    candidates = [
        page.locator('[contenteditable="true"][role="textbox"]'),
        page.locator('[contenteditable="true"]'),
        page.locator('textarea[placeholder*="caption" i]'),
        page.locator('textarea[placeholder*="описан" i]'),
    ]
    for candidate in candidates:
        visible = candidate.filter(visible=True)
        if visible.count() == 1:
            visible.first.fill(caption)
            return True
    return False


def post_button(page):
    buttons = page.get_by_role(
        "button", name=re.compile(r"^(?:Post|Publish|Опубликовать)$", re.I)
    )
    return buttons if buttons.count() == 1 else None


def operate(video: str, caption: str, profile: str,
            request_publish: bool, dry_run: bool = False) -> int:
    prohibit_ci()
    file = valid_video(video)
    caption = valid_caption(caption)
    if dry_run:
        print("PREVIEW ONLY: verified local MP4:", file.name)
        print("File bytes:", file.stat().st_size)
        print("Caption:", caption or "(без подписи)")
        print("Actual posting and upload NOT attempted.")
        return 0

    # Import late: unit tests and CI will not launch chromium/session.
    from playwright.sync_api import sync_playwright
    folder = profile_directory(profile)
    print("Local TikTok Studio profile:", folder)
    print("MP4:", file.name, "caption:", caption[:100])
    print("No login cookies, screenshots or account data leave this computer.")
    with sync_playwright() as pw:
        browser = pw.chromium.launch_persistent_context(
            user_data_dir=str(folder),
            headless=False,
            accept_downloads=False,
            service_workers="block",
            viewport={"width": 1260, "height": 850},
        )
        try:
            page = browser.pages[0] if browser.pages else browser.new_page()
            page.goto(STUDIO_URL, wait_until="domcontentloaded", timeout=45_000)
            if "studio" not in urlsplit(page.url).path.lower():
                print("Login or redirect required. Log in manually in the open window.")
                print("Finish login, then re-run the command; no automatic login.")
                input("Press Enter to close after manual login: ")
                return 2
            assert_studio(page.url)
            selected = unique_video_input(page)
            print("Type UPLOAD", file.name, "to approve transfer to TikTok Studio:")
            if input("Confirm upload (UPLOAD <filename>): ").strip() != (
                    "UPLOAD " + file.name):
                print("Cancelled. No file selected for upload.")
                return 3
            assert_studio(page.url)
            selected.set_input_files(str(file), timeout=30_000)
            print("File submitted to TikTok Studio's upload control.")
            print("This does NOT mean TikTok accepted or published the video.")
            page.wait_for_timeout(2500)
            assert_studio(page.url)
            if caption:
                if fill_caption_if_clear(page, caption):
                    print("Caption entered; inspect preview in the browser.")
                else:
                    print("Caption field ambiguous; enter caption MANUALLY in Studio.")
            if not request_publish:
                print("Publishing NOT requested. Finish review/publish yourself.")
                input("Press Enter when you finish review; browser will close: ")
                return 0

            print("Review account, cover, sound, AI disclosure, privacy and")
            print("TikTok Studio's actual upload completion status.")
            if input("After review, type PUBLISH " + file.name + ": ").strip() != (
                    "PUBLISH " + file.name):
                print("Final publish cancelled; file may remain as a draft.")
                return 3
            assert_studio(page.url)
            button = post_button(page)
            if button is None or not button.is_visible() or not button.is_enabled():
                print("No unique ready-to-post control. Post manually in Studio.")
                return 4
            button.click(timeout=5000)
            print("Publish button was clicked in the visible TikTok Studio.")
            print("⚠️ Publish is NOT verified; confirm success in TikTok Studio.")
            input("Check actual account status, then press Enter to close: ")
            return 0
        finally:
            browser.close()


def main() -> int:
    parser = argparse.ArgumentParser(description="Direct TikTok Studio upload, user-owned desktop")
    parser.add_argument("--file", required=True, help="Local MP4 path")
    parser.add_argument("--caption", default="")
    parser.add_argument("--profile", default="tiktok-studio")
    parser.add_argument("--publish", action="store_true",
                        help="Allow final button only after second typed explicit consent")
    parser.add_argument("--dry-run", action="store_true",
                        help="Validate/preview only, no browser/upload")
    args = parser.parse_args()
    try:
        return operate(args.file, args.caption, args.profile,
                       args.publish, args.dry_run)
    except (BrowserSafetyError, ValueError, OSError) as error:
        print("TikTok Studio operation stopped:", str(error), file=sys.stderr)
        return 5


if __name__ == "__main__":
    raise SystemExit(main())
