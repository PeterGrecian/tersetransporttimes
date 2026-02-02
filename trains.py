#!/usr/bin/env python3
"""
trains.py - Train times from Surbiton to Waterloo via Huxley2 (Darwin proxy)

Uses the free Huxley2 demo server - no API key required.
https://huxley2.azurewebsites.net/
"""

import json
import urllib.request
from datetime import datetime, timezone


HUXLEY2_BASE = "https://huxley2.azurewebsites.net"


def fetch_service_details(service_id):
    """Fetch detailed service info including calling points."""
    url = f"{HUXLEY2_BASE}/service/{service_id}"
    try:
        req = urllib.request.Request(url)
        req.add_header('User-Agent', 't3-trains/1.0')
        req.add_header('Accept', 'application/json')
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.loads(response.read().decode())
    except Exception as e:
        print(f"Error fetching service details for {service_id}: {e}")
        return None


def parse_time_mins(time_str):
    """Convert HH:MM to minutes since midnight."""
    try:
        h, m = time_str.split(':')
        return int(h) * 60 + int(m)
    except:
        return 0


def fetch_departures(origin="sur", destination="wat"):
    """
    Fetch train departures via Huxley2.
    origin/destination are CRS codes: sur=Surbiton, wat=Waterloo
    Returns list of departure dicts and optional error message.
    """
    url = f"{HUXLEY2_BASE}/departures/{origin}/to/{destination}"

    try:
        req = urllib.request.Request(url)
        req.add_header('User-Agent', 't3-trains/1.0')
        req.add_header('Accept', 'application/json')

        print(f"Fetching trains from: {url}")
        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode())
            print(f"Got response, trainServices count: {len(data.get('trainServices', []))}")
    except Exception as e:
        print(f"Error fetching departures: {type(e).__name__}: {e}")
        return [], str(e)

    services = data.get('trainServices') or []
    results = []

    for service in services[:6]:  # Limit to next 6 trains
        # Get scheduled departure time
        scheduled = service.get('std', '')
        etd = service.get('etd', '')

        # Check if cancelled
        cancelled = service.get('isCancelled', False) or etd == 'Cancelled'

        # Get service details for stops and arrival time
        service_id = service.get('serviceIdUrlSafe', '')
        stops = 0
        journey_mins = 0
        arrival_time = ''

        if service_id and not cancelled:
            details = fetch_service_details(service_id)
            if details:
                # Count stops and find destination arrival
                for group in details.get('subsequentCallingPoints', []):
                    for cp in group.get('callingPoint', []):
                        if cp.get('crs', '').upper() == destination.upper():
                            arrival_time = cp.get('st', '')
                            if scheduled and arrival_time:
                                dep_mins = parse_time_mins(scheduled)
                                arr_mins = parse_time_mins(arrival_time)
                                journey_mins = arr_mins - dep_mins
                                if journey_mins < 0:
                                    journey_mins += 1440  # Handle midnight
                        else:
                            stops += 1

        # Calculate delay
        delay_mins = 0
        if etd and etd not in ('On time', 'Delayed', 'Cancelled'):
            try:
                sched_mins = parse_time_mins(scheduled)
                exp_mins = parse_time_mins(etd)
                delay_mins = exp_mins - sched_mins
                if delay_mins < -720:
                    delay_mins += 1440
            except:
                pass

        # Calculate ETA (arrival + delay)
        eta = ''
        if arrival_time:
            arr_mins = parse_time_mins(arrival_time)
            eta_mins = arr_mins + delay_mins
            eta = f"{(eta_mins // 60) % 24:02d}{eta_mins % 60:02d}"

        results.append({
            'scheduledDeparture': scheduled.replace(':', ''),
            'expectedDeparture': etd if etd not in ('On time', 'Delayed', 'Cancelled') else scheduled.replace(':', ''),
            'arrivalTime': arrival_time.replace(':', '') if arrival_time else '',
            'eta': eta,
            'journeyMins': journey_mins,
            'stops': stops,
            'delayMinutes': delay_mins,
            'cancelled': cancelled,
            'status': etd
        })

    return results, None


STATION_NAMES = {
    'sur': 'Surbiton',
    'wat': 'London Waterloo'
}


def format_json(departures, origin, destination):
    """Format departures as JSON for API consumers."""
    return json.dumps({
        'originName': STATION_NAMES.get(origin.lower(), origin.upper()),
        'destinationName': STATION_NAMES.get(destination.lower(), destination.upper()),
        'timestamp': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
        'departures': departures
    })


def lambda_handler(event, context):
    """AWS Lambda entry point."""
    cors_headers = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type,Accept',
        'Access-Control-Allow-Methods': 'GET,OPTIONS'
    }

    # Get direction from query params (default: Surbiton to Waterloo)
    params = event.get('queryStringParameters') or {}
    origin = params.get('from', 'sur')
    destination = params.get('to', 'wat')

    departures, error = fetch_departures(origin, destination)

    if error:
        return {
            'statusCode': 500,
            'body': json.dumps({'error': error}),
            'headers': {'Content-Type': 'application/json', **cors_headers}
        }

    return {
        'statusCode': 200,
        'body': format_json(departures, origin, destination),
        'headers': {'Content-Type': 'application/json', **cors_headers}
    }


if __name__ == '__main__':
    # For local testing
    import sys
    origin = sys.argv[1] if len(sys.argv) > 1 else 'sur'
    destination = sys.argv[2] if len(sys.argv) > 2 else 'wat'
    departures, error = fetch_departures(origin, destination)
    if error:
        print(f"Error: {error}")
    else:
        print(format_json(departures, origin, destination))
