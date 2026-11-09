"""Shared, versioned task-command contract; stdlib only, fail closed.

This module validates *metadata* for approved tasks. It never interprets
user goal text as shell code and does not create/retry GitHub dispatches.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

SCHEMA_FILE = Path(__file__).resolve().parents[1] / "contracts/agent_dispatch_v1.json"


def schema() -> dict:
    data = json.loads(SCHEMA_FILE.read_text(encoding="utf-8"))
    if data.get("additionalProperties") is not False or data.get("type") != "object":
        raise ValueError("Contract schema is not strict")
    if data.get("required") != ["project", "mode", "client_task_id"]:
        raise ValueError("Unexpected task schema")
    return data


def validate_dispatch(command: dict) -> dict:
    """Validate allowlist and field types; reject unknown fields/ID/goal."""
    definitions = schema()
    if not isinstance(command, dict) or set(command) - set(definitions["properties"]):
        raise ValueError("Unsupported agent dispatch field")
    if set(definitions["required"]) - set(command):
        raise ValueError("Missing mandatory agent dispatch field")
    for name, prop in definitions["properties"].items():
        if name not in command:
            continue
        value = command[name]
        if prop["type"] == "string":
            if not isinstance(value, str) or not value:
                raise ValueError("Invalid task text type")
            if value not in prop.get("enum", [value]):
                raise ValueError("Unrecognized task routing value")
            if "pattern" in prop and not re.fullmatch(prop["pattern"], value):
                raise ValueError("Invalid task identifier")
            if not prop.get("minLength", 0) <= len(value) <= prop.get("maxLength", float("inf")):
                raise ValueError("Invalid task text length")
        elif prop["type"] == "boolean" and not isinstance(value, bool):
            raise ValueError("Invalid task flag")
    if command.get("safe_fix", False) and (
        command["project"] != "wolf" or command["mode"] != "test"
    ):
        raise ValueError("Safe fix only for WOLF test tasks")
    # Return a copy. The caller remains responsible for secret screening.
    return dict(command)
