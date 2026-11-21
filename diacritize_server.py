#!/usr/bin/env python3
"""
National Sounds - Arabic tashkeel (diacritization) server.

A tiny HTTP server the Android app calls to add diacritics to the script.
Only the Python standard library is needed for the server itself; the actual
diacritization engine is one of:

  * pylibtashkeel  (https://github.com/mush42/libtashkeel - build the wheel from source)
  * mishkal        (pip install mishkal)

Endpoints
  POST /diacritize   {"text": "..."}  ->  {"text": "...", "backend": "..."}
  GET  /health                         ->  {"ok": true, "backend": "..."}

Usage
  python diacritize_server.py --host 0.0.0.0 --port 8765
  # then in the app:  http://<this-computer-ip>:8765/diacritize
"""
import argparse
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 1_000_000  # bytes


def load_backend(name):
    """Return (backend_name, callable(text) -> str)."""
    if name in ("auto", "libtashkeel"):
        try:
            from pylibtashkeel import tashkeel  # type: ignore

            return "libtashkeel", tashkeel
        except ImportError:
            if name == "libtashkeel":
                raise SystemExit("pylibtashkeel is not installed (see server/README.md)")

    if name in ("auto", "mishkal"):
        try:
            import mishkal.tashkeel  # type: ignore

            vocalizer = mishkal.tashkeel.TashkeelClass()
            return "mishkal", vocalizer.tashkeel
        except ImportError:
            if name == "mishkal":
                raise SystemExit("mishkal is not installed: pip install mishkal")

    if name == "echo":  # for testing the app/server connection only
        return "echo", lambda text: text

    raise SystemExit(
        "No diacritization backend found. Install one of: pylibtashkeel, mishkal "
        "(see server/README.md)."
    )


def make_handler(backend_name, diacritize):
    lock = threading.Lock()  # the engines are not guaranteed to be thread-safe

    class Handler(BaseHTTPRequestHandler):
        def _send(self, status, payload):
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == "/health":
                self._send(200, {"ok": True, "backend": backend_name})
            else:
                self._send(404, {"error": "not found"})

        def do_POST(self):
            if self.path != "/diacritize":
                self._send(404, {"error": "not found"})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length <= 0 or length > MAX_BODY:
                    self._send(413, {"error": "bad request size"})
                    return
                data = json.loads(self.rfile.read(length).decode("utf-8"))
                text = data["text"]
                if not isinstance(text, str):
                    raise ValueError("text must be a string")
            except KeyError:
                self._send(400, {"error": "missing 'text' field"})
                return
            except (ValueError, json.JSONDecodeError) as exc:
                self._send(400, {"error": str(exc)})
                return

            try:
                with lock:
                    result = diacritize(text)
            except Exception as exc:  # engine failure
                self._send(500, {"error": str(exc)})
                return
            self._send(200, {"text": result, "backend": backend_name})

        def log_message(self, fmt, *args):  # quieter logs
            print("%s - %s" % (self.address_string(), fmt % args))

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument(
        "--backend",
        default="auto",
        choices=["auto", "libtashkeel", "mishkal", "echo"],
        help="diacritization engine (default: first one installed)",
    )
    args = parser.parse_args()

    name, fn = load_backend(args.backend)
    server = ThreadingHTTPServer((args.host, args.port), make_handler(name, fn))
    print(f"National Sounds tashkeel server ({name}) on http://{args.host}:{args.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
