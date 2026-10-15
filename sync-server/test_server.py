import importlib.util
import json
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from contextlib import closing
from http.server import ThreadingHTTPServer
from pathlib import Path


spec = importlib.util.spec_from_file_location("sign_sync_server", Path(__file__).with_name("server.py"))
server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)


class SignSyncTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        server.DB = Path(self.directory.name) / "events.sqlite3"
        server.SECRET = "a-local-test-key-with-at-least-32-chars"
        server.WINDOW.clear()
        server.initialize()
        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.httpd.server_port}/v1"

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join()
        self.directory.cleanup()

    def request(self, path, body=None, session=None):
        headers = {"Content-Type": "application/json"}
        if session:
            headers["X-Canvas-Session"] = session
        req = urllib.request.Request(self.url + path,
            data=json.dumps(body).encode() if body is not None else None, headers=headers)
        try:
            with urllib.request.urlopen(req) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    def test_session_isolation_and_idempotence(self):
        session_a = "session-for-student-a-123456789"
        session_b = "session-for-student-b-123456789"
        event = {"studentId": "12345678", "session": session_a,
            "courseCode": "undefined", "courseName": "高等数学", "rollCallDigest": "a" * 64,
            "status": "SUCCESS", "confirmedAt": int(time.time() * 1000)}
        self.assertEqual(self.request("/events", event)[0], 200)
        event["status"] = "ALREADY_SIGNED"
        self.assertEqual(self.request("/events", event)[0], 200)
        code, result = self.request("/me/events", session=session_a)
        self.assertEqual(code, 200)
        self.assertEqual(len(result["events"]), 1)
        self.assertEqual(result["events"][0]["status"], "SUCCESS")
        self.assertEqual(result["events"][0]["courseName"], "高等数学")
        self.assertEqual(self.request("/me/events", session=session_b)[1]["events"], [])
        self.assertEqual(self.request("/me/events")[0], 400)
        with closing(server.connect()) as db:
            row = db.execute("SELECT session_key FROM events").fetchone()
            self.assertNotEqual(row[0], session_a)

    def test_existing_database_gains_course_name_column(self):
        with closing(server.connect()) as db, db:
            db.execute("DROP TABLE events")
            db.execute("""CREATE TABLE events (
                session_key TEXT NOT NULL, student_id TEXT NOT NULL,
                course_code TEXT NOT NULL, roll_call_digest TEXT NOT NULL,
                status TEXT NOT NULL, confirmed_at INTEGER NOT NULL,
                received_at INTEGER NOT NULL,
                PRIMARY KEY(session_key, course_code, roll_call_digest))""")
        server.initialize()
        with closing(server.connect()) as db:
            columns = {row[1] for row in db.execute("PRAGMA table_info(events)")}
        self.assertIn("course_name", columns)


if __name__ == "__main__":
    unittest.main()
