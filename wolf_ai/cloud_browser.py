"""Read-only, no-login ephemeral Playwright browser for GitHub Actions.

No cookies or credentials injected, no forms, uploads, cross-origin navigation,
arbitrary scripts, external code execution, purchases or posting. The selected
public HTTPS URL and up to four safe nav labels come from an approved user
command. Screenshots and a bounded report are stored as workflow artifacts.
"""
from __future__ import annotations

import argparse
import ipaddress
import json
import re
import socket
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

MAX_STEPS = 4
FORBIDDEN = re.compile(
    r"(?i)(password|passcode|token|api[_-]?key|secret|otp|"
    r"bearer|private[_-]?key|access[_-]?key)"
)
RISKY = (
    "парол", "password", "пин", "otp", "2fa", "отправ", "send",
    "опубли", "publish", "post now", "удал", "delete", "купить",
    "buy", "оплат", "pay", "checkout", "confirm", "подтверд",
    "разреш", "allow", "install", "withdraw", "wallet",
    "войти", "sign in", "login", "logout", "accept", "submit", "сохранить",
)


class BrowserSafetyError(ValueError):
    """An unsafe browser task; no internet action is attempted."""


def validate_url(raw: str) -> str:
    if not isinstance(raw, str) or len(raw) > 1000:
        raise BrowserSafetyError("URL must be a bounded public HTTPS address")
    parsed = urlsplit(raw)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or (
        parsed.password is not None
    ) or parsed.port is not None or parsed.fragment:
        raise BrowserSafetyError("Only regular HTTPS URLs without user info, ports or fragments")
    host = parsed.hostname.lower().rstrip(".")
    if host != parsed.hostname.lower() or "." not in host or host.startswith("www.www."):
        raise BrowserSafetyError("Unsupported host")
    if any(host.endswith("." + x) for x in ("local", "test", "internal", "localhost", "onion")):
        raise BrowserSafetyError("Local/private host not allowed")
    if host in {"localhost", "metadata.google.internal"}:
        raise BrowserSafetyError("Private host")
    try:
        ipaddress.ip_address(host)
    except ValueError:
        pass
    else:
        raise BrowserSafetyError("IP-literal destinations forbidden")
    if not re.fullmatch(r"[a-z0-9.-]{4,250}", host) or ".." in host:
        raise BrowserSafetyError("Malformed hostname")
    if FORBIDDEN.search(parsed.query):
        raise BrowserSafetyError("Do not submit URL credentials")
    return raw


def validate_dns(host: str) -> None:
    try:
        addresses = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
    except OSError as exc:
        raise BrowserSafetyError("Public DNS could not be resolved") from exc
    if not addresses:
        raise BrowserSafetyError("Empty DNS response")
    for address in addresses:
        if not ipaddress.ip_address(address[4][0]).is_global:
            raise BrowserSafetyError("DNS resolved to a restricted network")


def parse_steps(raw: str) -> list[str]:
    if raw is None or len(raw) > 320:
        raise BrowserSafetyError("Route too long")
    if not raw.strip():
        return []
    parts = [x.strip().strip('«»"') for x in raw.split(">")]
    if not 1 <= len(parts) <= MAX_STEPS:
        raise BrowserSafetyError("Only 1–4 preapproved navigation steps")
    seen: set[str] = set()
    for value in parts:
        label = " ".join(value.lower().split())
        if len(label) < 3 or len(label) > 70 or "://" in label or any(
            marker in label for marker in RISKY
        ):
            raise BrowserSafetyError("Unsafe navigation label")
        if label in seen:
            raise BrowserSafetyError("Repeated navigation label")
        seen.add(label)
    return parts


def safe_click_js(origin: str, label: str) -> str:
    # JavaScript is a FIXED program, user strings passed only as JSON literals.
    return (
        "() => { const origin = " + json.dumps(origin) + ";"
        "if(location.origin!==origin)return 'origin-changed';"
        "const want=" + json.dumps(" ".join(label.lower().split())) + ";"
        "const nodes=document.querySelectorAll('a,button,[role=button]');"
        "const matches=[];"
        "for(let i=0;i<nodes.length&&i<600;i++){"
        "const el=nodes[i];if(el.closest('form')||el.matches('[type=submit]'))continue;"
        "if(el.matches('a[href]')){"
        "try{if(new URL(el.href,location.href).origin!==origin)continue;}"
        "catch(e){continue;}}"
        "const rect=el.getBoundingClientRect();"
        "if(!rect.width||!rect.height||getComputedStyle(el).visibility==='hidden')continue;"
        "const text=(el.getAttribute('aria-label')||el.innerText||'').trim()"
        ".replace(/\\s+/g,' ').toLowerCase();"
        "if(text===want)matches.push(el);"
        "}if(matches.length!==1)return 'matches:'+matches.length;"
        "matches[0].click();return 'click-invoked';}"
    )


def run(url: str, steps: str, folder: Path) -> dict:
    approved_url = validate_url(url)
    plan = parse_steps(steps)
    origin = urlsplit(approved_url).scheme + "://" + urlsplit(approved_url).hostname
    validate_dns(urlsplit(approved_url).hostname)
    folder.mkdir(parents=True, exist_ok=True)
    report = {
        "schema": 1,
        "tool": "wolf-public-cloud-browser",
        "status": "running",
        "initial_url": approved_url,
        "origin": origin,
        "steps_requested": plan,
        "steps_executed": [],
        "started_utc": datetime.now(timezone.utc).isoformat(),
        "requires_manual_auth": False,
        "used_private_cookies": False,
        "extracted_passwords": False,
        "posted_or_uploaded": False,
        "notes": "Public read-only ephemeral browser; not an authenticated user session.",
    }
    # Defer import so zero-dependency URL/policy tests run locally.
    from playwright.sync_api import sync_playwright

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(
            accept_downloads=False, java_script_enabled=True,
            service_workers="block", locale="ru-RU",
        )
        page = context.new_page()
        cache: dict[str, bool] = {}
        def network_guard(route):
            try:
                requested = validate_url(route.request.url)
                host = urlsplit(requested).hostname
                if host not in cache:
                    validate_dns(host)
                    cache[host] = True
                if route.request.is_navigation_request() and host != urlsplit(
                    approved_url
                ).hostname:
                    route.abort()
                else:
                    route.continue_()
            except Exception:
                route.abort()

        context.route("**/*", network_guard)
        try:
            page.goto(approved_url, wait_until="domcontentloaded", timeout=30000)
            page.screenshot(path=str(folder / "browser_00.png"), full_page=False, timeout=10000)
            for i, label in enumerate(plan, 1):
                # Current URL may have moved; no cross-host escalation.
                if urlsplit(page.url).hostname != urlsplit(approved_url).hostname:
                    raise BrowserSafetyError("Unexpected navigation away from approved host")
                result = page.evaluate(safe_click_js(origin, label))
                if result != "click-invoked":
                    raise BrowserSafetyError(f"Step {i} not executed: {result}")
                report["steps_executed"].append({"number": i, "label": label,
                                                  "result": "click-invoked"})
                page.wait_for_timeout(1000)
                page.screenshot(path=str(folder / f"browser_{i:02d}.png"),
                                full_page=False, timeout=10000)
            if urlsplit(page.url).hostname != urlsplit(approved_url).hostname:
                raise BrowserSafetyError("Final browser page left approved host")
            title = page.title()
            body = page.locator("body").inner_text(timeout=8000)
            report["title"] = title[:150]
            report["final_url"] = page.url[:1000]
            report["visible_text_excerpt"] = body[:7500]
            links = page.locator("a[href]").evaluate_all(
                "(els)=>els.slice(0,100).map(el=>({text:(el.innerText||'').slice(0,90),url:el.href}))"
            )
            report["links"] = [
                item for item in links
                if isinstance(item, dict) and
                isinstance(item.get("url"), str) and
                item["url"].startswith("https://")
            ][:25]
            report["status"] = "success"
        except Exception as exc:
            # Report the class of failure; never leak whole browser traces
            # with query credentials or private HTML.
            report["status"] = "failed"
            report["error_type"] = exc.__class__.__name__
            report["error_note"] = "Navigation/read failed; review screenshots and URL."
        finally:
            context.close()
            browser.close()
    report["completed_utc"] = datetime.now(timezone.utc).isoformat()
    (folder / "browser_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="WOLF read-only public cloud browser")
    parser.add_argument("--url", required=True)
    parser.add_argument("--steps", default="")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    report = run(args.url, args.steps, Path(args.output))
    print("WOLF cloud browser finished with:", report["status"])
    return 0 if report["status"] == "success" else 1


if __name__ == "__main__":
    raise SystemExit(main())
