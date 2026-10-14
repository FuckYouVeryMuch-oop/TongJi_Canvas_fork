"""Small local-only HTTP service; terminate HTTPS at Caddy."""

import hashlib
import hmac
import json
import os
import re
import sqlite3
import time
from contextlib import closing
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


DB = Path(os.environ.get("SYNC_DB", "/data/events.sqlite3"))
SECRET = os.environ.get("SYNC_HMAC_KEY", "")
SESSION_RE = re.compile(r"^[^\s;]{20,4096}$")
STUDENT_RE = re.compile(r"^[0-9A-Za-z]{4,20}$")
COURSE_RE = re.compile(r"^[0-9A-Za-z_-]{1,64}$")
DIGEST_RE = re.compile(r"^[0-9a-f]{64}$")
WINDOW = {}


def session_key(session):
    if not isinstance(session, str) or not SESSION_RE.fullmatch(session):
        raise ValueError("invalid session")
    return hmac.new(SECRET.encode(), session.encode(), hashlib.sha256).hexdigest()


def connect():
    db = sqlite3.connect(DB, timeout=5)
    db.row_factory = sqlite3.Row
    return db


def initialize():
    if len(SECRET) < 32:
        raise RuntimeError("SYNC_HMAC_KEY must contain at least 32 characters")
    DB.parent.mkdir(parents=True, exist_ok=True)
    with closing(connect()) as db, db:
        db.execute("PRAGMA journal_mode=WAL")
        db.execute("""CREATE TABLE IF NOT EXISTS events (
            session_key TEXT NOT NULL,
            student_id TEXT NOT NULL,
            course_code TEXT NOT NULL,
            roll_call_digest TEXT NOT NULL,
            status TEXT NOT NULL,
            confirmed_at INTEGER NOT NULL,
            received_at INTEGER NOT NULL,
            PRIMARY KEY(session_key, course_code, roll_call_digest)
        )""")
        db.execute("CREATE INDEX IF NOT EXISTS events_recent ON events(session_key, received_at DESC)")


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Never log URLs or credential-bearing request data.

    def respond(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def allowed(self):
        now = time.monotonic()
        peer = self.client_address[0]
        recent = [t for t in WINDOW.get(peer, []) if now - t < 60]
        if len(recent) >= 120:
            return False
        recent.append(now)
        WINDOW[peer] = recent
        return True

    def do_POST(self):
        if urlsplit(self.path).path != "/v1/events":
            return self.respond(404, {"error": "not found"})
        if not self.allowed():
            return self.respond(429, {"error": "rate limited"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 1 or length > 4096:
                return self.respond(413, {"error": "invalid size"})
            data = json.loads(self.rfile.read(length))
            key = session_key(data.get("session"))
            student = data.get("studentId")
            course = data.get("courseCode")
            digest = data.get("rollCallDigest")
            status = data.get("status")
            confirmed = data.get("confirmedAt")
            if (not isinstance(student, str) or not STUDENT_RE.fullmatch(student)
                or not isinstance(course, str) or not COURSE_RE.fullmatch(course)
                or not isinstance(digest, str) or not DIGEST_RE.fullmatch(digest)
                or status not in ("SUCCESS", "ALREADY_SIGNED")
                or type(confirmed) is not int
                or not int(time.time() * 1000) - 30 * 86400000 <= confirmed <= int(time.time() * 1000) + 300000):
                raise ValueError("invalid event")
        except (ValueError, TypeError, KeyError, UnicodeDecodeError, json.JSONDecodeError):
            return self.respond(400, {"error": "invalid event"})
        now = int(time.time() * 1000)
        with closing(connect()) as db, db:
            db.execute("DELETE FROM events WHERE received_at < ?", (now - 30 * 86400000,))
            db.execute("""INSERT INTO events VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(session_key, course_code, roll_call_digest) DO UPDATE SET
                status = CASE WHEN events.status = 'SUCCESS' THEN 'SUCCESS' ELSE excluded.status END
                """, (key, student, course, digest, status, confirmed, now))
        self.respond(200, {"ok": True})

    def do_GET(self):
        parsed = urlsplit(self.path)
        if parsed.path == "/v1/health":
            return self.respond(200, {"ok": True})
        if parsed.path != "/v1/me/events":
            return self.respond(404, {"error": "not found"})
        if not self.allowed():
            return self.respond(429, {"error": "rate limited"})
        try:
            key = session_key(self.headers.get("X-Canvas-Session"))
            limit = int(parse_qs(parsed.query).get("limit", ["20"])[0])
            if not 1 <= limit <= 20:
                raise ValueError("invalid limit")
        except (ValueError, TypeError):
            return self.respond(400, {"error": "invalid request"})
        with closing(connect()) as db, db:
            rows = db.execute("""SELECT course_code, status, confirmed_at, received_at
                FROM events WHERE session_key = ? AND received_at >= ?
                ORDER BY received_at DESC LIMIT ?""",
                (key, int(time.time() * 1000) - 30 * 86400000, limit)).fetchall()
        self.respond(200, {"events": [{"courseCode": r[0], "status": r[1],
            "confirmedAt": r[2], "receivedAt": r[3]} for r in rows]})


if __name__ == "__main__":
    initialize()
    ThreadingHTTPServer((os.environ.get("SYNC_BIND", "127.0.0.1"), 8787), Handler).serve_forever()
