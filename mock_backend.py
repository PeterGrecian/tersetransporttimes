#!/usr/bin/env python3
"""
Mock backend for testing the Android app locally.
Simulates the Lambda function's JSON API without needing AWS.
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
from datetime import datetime
import random

class MockBusTimesHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        """Handle GET requests - return mock bus times."""

        # Generate realistic mock data
        mock_arrivals = [
            {"stop": "Bethnal Green, Wellington Row", "direction": "inbound", "minutes": random.randint(2, 5)},
            {"stop": "Shoreditch High Street", "direction": "inbound", "minutes": random.randint(6, 10)},
            {"stop": "Liverpool Street Station", "direction": "inbound", "minutes": random.randint(8, 12)},
            {"stop": "Aldgate", "direction": "inbound", "minutes": random.randint(10, 15)},
            {"stop": "Bank", "direction": "outbound", "minutes": random.randint(3, 7)},
            {"stop": "Old Street Station", "direction": "outbound", "minutes": random.randint(9, 14)},
            {"stop": "Essex Road", "direction": "outbound", "minutes": random.randint(12, 18)},
        ]

        # Sort by minutes
        mock_arrivals.sort(key=lambda x: x["minutes"])

        response_data = {
            "timestamp": datetime.now().strftime('%H:%M'),
            "route": "K2",
            "arrivals": mock_arrivals
        }

        # Send response
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')  # Allow CORS
        self.end_headers()

        self.wfile.write(json.dumps(response_data, indent=2).encode())

        # Log request
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Served mock bus times")

    def log_message(self, format, *args):
        """Suppress default logging, we'll do our own."""
        pass

def run_mock_server(port=8000):
    """Start the mock server."""
    server_address = ('', port)
    httpd = HTTPServer(server_address, MockBusTimesHandler)

    print(f"""
╔════════════════════════════════════════════════════════════╗
║  🚌 Mock K2 Bus Times Server Running                       ║
╠════════════════════════════════════════════════════════════╣
║  Local:   http://localhost:{port}                            ║
║  Network: http://<your-ip>:{port}                            ║
║                                                            ║
║  Test it: curl http://localhost:{port}                       ║
║                                                            ║
║  Press Ctrl+C to stop                                      ║
╚════════════════════════════════════════════════════════════╝
    """)

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n\n👋 Mock server stopped")
        httpd.shutdown()

if __name__ == '__main__':
    import sys
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    run_mock_server(port)
