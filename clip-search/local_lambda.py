"""Serve lambda/handler.py locally behind the real Lambda Invoke API path.

photo-webapp's LambdaClient points here via clip.lambda.endpoint=http://localhost:3001,
so local testing exercises exactly the same Java code path as production.
Request signatures are ignored.

  .venv/bin/python local_lambda.py [port]
"""
import json
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "lambda"))
import handler as lambda_handler  # noqa: E402  (loads the model, like a cold start)

INVOKE_PATH = re.compile(r"^/2015-03-31/functions/([^/]+)/invocations")


class InvokeHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        match = INVOKE_PATH.match(self.path)
        if not match:
            self.send_error(404)
            return
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)) or 0)
        started = time.perf_counter()
        result = lambda_handler.handler(json.loads(body or b"{}"))
        payload = json.dumps(result).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("X-Amz-Executed-Version", "$LATEST")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)
        self.log_message("%s %s %.0f ms", match.group(1), "warmup" if "warmup" in result else "encode",
                         (time.perf_counter() - started) * 1000)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 3001
    print(f"local Lambda Invoke API on http://localhost:{port}")
    ThreadingHTTPServer(("127.0.0.1", port), InvokeHandler).serve_forever()
