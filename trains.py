#!/usr/bin/env python3
"""
trains.py - Train times from Surbiton to Waterloo via RTT API
"""

import os
import json
import urllib.request
import base64
from datetime import datetime, timezone


RTT_API_BASE = "https://api.rtt.io/api/v1"


def fetch_departures(username, password):
    """
    Fetch train departures from Surbiton to Waterloo via RTT API.
    Returns list of departure dicts and optional error message.
    """
    url = f"{RTT_API_BASE}/json/search/SUR/to/WAT"

    # HTTP BASIC auth
    credentials = base64.b64encode(f"{username}:{password}".encode()).decode()

    try:
        req = urllib.request.Request(url)
        req.add_header('User-Agent', 't3-trains/1.0')
        req.add_header('Authorization', f'Basic {credentials}')

        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode())
    except Exception as e:
        return [], str(e)

    services = data.get('services', [])
    results = []

    for service in services[:6]:  # Limit to next 6 trains
        location_detail = service.get('locationDetail', {})

        # Skip non-stopping services
        display_as = location_detail.get('displayAs', '')
        if display_as == 'PASS':
            continue

        # Get scheduled and real-time departure
        scheduled_departure = location_detail.get('gbttBookedDeparture', '')
        realtime_departure = location_detail.get('realtimeDeparture', scheduled_departure)

        # Calculate delay in minutes
        delay_mins = 0
        if scheduled_departure and realtime_departure:
            try:
                sched = int(scheduled_departure[:2]) * 60 + int(scheduled_departure[2:])
                real = int(realtime_departure[:2]) * 60 + int(realtime_departure[2:])
                delay_mins = real - sched
                # Handle midnight crossover
                if delay_mins < -720:
                    delay_mins += 1440
            except:
                pass

        # Get platform
        platform = location_detail.get('platform', '')

        # Check if cancelled
        cancelled = display_as == 'CANCELLED_CALL'

        results.append({
            'scheduledDeparture': scheduled_departure,
            'expectedDeparture': realtime_departure,
            'platform': platform,
            'delayMinutes': delay_mins,
            'cancelled': cancelled
        })

        if len(results) >= 6:
            break

    return results, None


def format_json(departures):
    """Format departures as JSON for API consumers."""
    return json.dumps({
        'originName': 'Surbiton',
        'destinationName': 'London Waterloo',
        'timestamp': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
        'departures': departures
    })


def lambda_handler(event, context):
    """AWS Lambda entry point."""
    username = os.environ.get('RTT_USERNAME')
    password = os.environ.get('RTT_PASSWORD')

    cors_headers = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type,Accept',
        'Access-Control-Allow-Methods': 'GET,OPTIONS'
    }

    if not username or not password:
        return {
            'statusCode': 500,
            'body': json.dumps({'error': 'RTT credentials not configured'}),
            'headers': {'Content-Type': 'application/json', **cors_headers}
        }

    departures, error = fetch_departures(username, password)

    if error:
        return {
            'statusCode': 500,
            'body': json.dumps({'error': error}),
            'headers': {'Content-Type': 'application/json', **cors_headers}
        }

    return {
        'statusCode': 200,
        'body': format_json(departures),
        'headers': {'Content-Type': 'application/json', **cors_headers}
    }


if __name__ == '__main__':
    # For local testing
    username = os.environ.get('RTT_USERNAME')
    password = os.environ.get('RTT_PASSWORD')
    if username and password:
        departures, error = fetch_departures(username, password)
        if error:
            print(f"Error: {error}")
        else:
            print(format_json(departures))
    else:
        print("Set RTT_USERNAME and RTT_PASSWORD environment variables")
