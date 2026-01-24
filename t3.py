#!/usr/bin/env python3
"""
t3.py - Terse Transport Times
A minimal Lambda function that returns expected bus arrival intervals.
"""

import os
import json
import urllib.request
from datetime import datetime, timezone


TFL_API_BASE = "https://api.tfl.gov.uk"
ROUTE = "K2"


def fetch_arrivals(api_key=None):
    """
    Fetch bus arrivals from TfL API.
    Returns list of (stop_name, direction, minutes_until_arrival) tuples.
    """
    url = f"{TFL_API_BASE}/Line/{ROUTE}/arrivals"
    if api_key:
        url += f"?app_key={api_key}"

    try:
        req = urllib.request.Request(url)
        req.add_header('User-Agent', 't3-terse-transport-times/1.0')
        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode())
    except Exception as e:
        return [], str(e)

    # Group by stop and get nearest bus per direction
    stops = {}
    now = datetime.now(timezone.utc)

    for arrival in data:
        stop_name = arrival.get('stationName', 'Unknown')
        direction = arrival.get('direction', 'unknown')
        time_to_station = arrival.get('timeToStation', 0)

        key = (stop_name, direction)
        if key not in stops or time_to_station < stops[key]:
            stops[key] = time_to_station

    # Convert to sorted list
    results = []
    for (stop_name, direction), seconds in sorted(stops.items(), key=lambda x: x[1]):
        minutes = seconds // 60
        results.append((stop_name, direction, minutes))

    return results, None


def format_text(arrivals):
    """Format arrivals as plain text."""
    if not arrivals:
        return "No buses found"

    lines = [f"K2 Bus Times - {datetime.now().strftime('%H:%M')}", ""]
    for stop_name, direction, minutes in arrivals:
        dir_short = direction[0].upper() if direction else "?"
        lines.append(f"{minutes:3}m  {dir_short}  {stop_name}")

    return "\n".join(lines)


def format_html(arrivals):
    """Format arrivals as HTML."""
    if not arrivals:
        return "<html><body><h1>No buses found</h1></body></html>"

    rows = []
    for stop_name, direction, minutes in arrivals:
        dir_short = direction[0].upper() if direction else "?"
        rows.append(f"<tr><td>{minutes}m</td><td>{dir_short}</td><td>{stop_name}</td></tr>")

    return f"""<html>
<head>
<title>K2 Bus Times</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body {{ font-family: monospace; background: #1a1a1a; color: #fff; padding: 1rem; margin: 0; }}
h1 {{ font-size: 1.2rem; margin-bottom: 1rem; }}
table {{ border-collapse: collapse; width: 100%; }}
td {{ padding: 0.3rem 0.5rem; border-bottom: 1px solid #333; }}
td:first-child {{ color: #4a9eff; text-align: right; }}
</style>
</head>
<body>
<h1>K2 - {datetime.now().strftime('%H:%M')}</h1>
<table>{''.join(rows)}</table>
</body>
</html>"""


def lambda_handler(event, context):
    """AWS Lambda entry point."""
    api_key = os.environ.get('TFL_API_KEY')
    arrivals, error = fetch_arrivals(api_key)

    if error:
        return {
            'statusCode': 500,
            'body': json.dumps({'error': error}),
            'headers': {'Content-Type': 'application/json'}
        }

    # Check Accept header for format preference
    headers = event.get('headers', {}) or {}
    accept = headers.get('Accept', headers.get('accept', 'text/html'))

    if 'text/plain' in accept:
        return {
            'statusCode': 200,
            'body': format_text(arrivals),
            'headers': {'Content-Type': 'text/plain'}
        }

    return {
        'statusCode': 200,
        'body': format_html(arrivals),
        'headers': {'Content-Type': 'text/html'}
    }


if __name__ == '__main__':
    # Test locally
    arrivals, error = fetch_arrivals()
    if error:
        print(f"Error: {error}")
    else:
        print(format_text(arrivals))
