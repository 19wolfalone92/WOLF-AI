"""WOLF Private Browser: authenticated, owner-operated desktop companion.

Sessions exist ONLY on the owner's own computer, never in GitHub Actions,
artifacts, public repositories, remote inference, WOLF cloud browser or logs.

The local user signs in manually with a visible Chromium window; WOLF
can subsequently perform up to four exact reviewed navigation clicks on
the same public HTTPS origin. No headless hidden login or form submit.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

from wolf_ai.cloud_browser import (
    BrowserSafetyError,
    parse_steps,
    safe_click_js,
    validate_url,
)


def prohibit_ci() -> None:
    if os.environ.get("GITHUB_ACTIONS") == "true" or os.environ.get("CI") == "true":
        raise BrowserSafetyError("Private authenticated browser never runs in CI/cloud")


def profile_directory(label: str) -> Path:
    if not re.fullmatch(r"[a-z][a-z0-9_-]{2,29}", label or ""):
        raise BrowserSafetyError("Profile name must be 3–30 lower-case letters/digits/-/_")
    base = Path.home() / ".wolf-ai" / "private-browser"
    folder = base / label
    # Prevent accidental symlinks that could redirect sensitive browser data.
    for target in (base, folder):
        if target.is_symlink():
            raise BrowserSafetyError("Symlink profile folders are forbidden")
    folder.mkdir(mode=0o700, parents=True, exist_ok=True)
    if os.name == "posix":
        os.chmod(folder, 0o700)
    return folder


def assert_same_origin(initial: str, current: str) -> None:
    original = urlsplit(validate_url(initial))
    location = urlsplit(current)
    if location.scheme != "https" or location.hostname != original.hostname or (
        location.port is not None
    ):
        raise BrowserSafetyError(
            "The browser left the approved HTTPS host; route stopped."
        )


def run_local(mode: str, url: str, profile: str, steps: str) -> int:
    prohibit_ci()
    if mode not in {"login", "navigate", "inspect"}:
        raise BrowserSafetyError("Unsupported operation")
    start = validate_url(url)
    plan = parse_steps(steps)
    if mode == "login" and plan:
        raise BrowserSafetyError("Login is always manual; no clicks provided")
    if mode == "inspect" and plan:
        raise BrowserSafetyError("Inspect cannot click")
    folder = profile_directory(profile)
    # Note: this is the only point at which Playwright is imported.
    from playwright.sync_api import sync_playwright

    print("🐺 Private browser opened on YOUR computer.")
    print("Browser account state remains only in:", folder)
    print("No private screenshots or session cookies are uploaded.")
    with sync_playwright() as p:
        context = p.chromium.launch_persistent_context(
            user_data_dir=str(folder),
            headless=False,
            accept_downloads=False,
            service_workers="block",
            viewport={"width": 1200, "height": 800},
        )
        try:
            page = context.pages[0] if context.pages else context.new_page()
            page.goto(start, wait_until="domcontentloaded", timeout=30000)
            if mode == "login":
                print("Sign in MANUALLY in the opened browser window.")
                print("When finished (including any 2FA), come back and press Enter.")
                input()
                print("Session retained locally. Close this terminal to exit.")
                return 0
            assert_same_origin(start, page.url)
            if mode == "inspect":
                # No DOM content, password values, cookies, screenshots or
                # full page HTML are ever emitted to the terminal.
                print("Current browser title:", page.title()[:120])
                print("The session may be authenticated. No page data exported.")
                return 0
            print("Preapproved navigation:", " → ".join(plan) or "(no clicks)")
            if plan:
                print("Confirm this exact safe route in your own browser.")
                approved = input("Type YES to execute: ").strip()
                if approved != "YES":
                    print("Cancelled; no navigation clicks.")
                    return 2
            origin = "https://" + urlsplit(start).hostname
            for index, label in enumerate(plan, 1):
                assert_same_origin(start, page.url)
                result = page.evaluate(safe_click_js(origin, label))
                if result != "click-invoked":
                    print("Stopped at step", index, ": no unique visible nav element.")
                    return 3
                page.wait_for_timeout(1100)
                assert_same_origin(start, page.url)
                print("Navigation step", index, "invoked. Verify state in browser.")
            print("All approved navigation steps were invoked; no form submitted.")
            return 0
        finally:
            context.close()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Manual-auth persistent browser on your own computer; never in cloud."
    )
    parser.add_argument("mode", choices=["login", "navigate", "inspect"])
    parser.add_argument("--url", required=True, help="Public HTTPS target")
    parser.add_argument("--profile", required=True, help="Local profile name")
    parser.add_argument("--steps", default="", help="Up to 4 safe labels separated by >")
    args = parser.parse_args()
    try:
        return run_local(args.mode, args.url, args.profile, args.steps)
    except (BrowserSafetyError, ValueError) as error:
        print("Private browser refused task:", str(error), file=sys.stderr)
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
