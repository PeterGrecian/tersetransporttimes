#!/usr/bin/env python3
"""
t3.py - Terse Transport Times
A minimal Lambda function that returns expected bus arrival intervals for a specific stop.
"""

import os
import json
import urllib.request
from datetime import datetime, timezone


TFL_API_BASE = "https://api.tfl.gov.uk"
ROUTE = "K2"

# Stop configurations: naptan_ids for both directions
STOPS = {
    "parklands": {
        "inbound_naptan": "490010781S",   # Parklands towards Kingston
        "outbound_naptan": "490010781N",  # Parklands towards Hook
        "name": "Parklands",
        "inbound_dest": "Kingston",
        "outbound_dest": "Hook"
    },
    "surbiton": {
        "inbound_naptan": "490015165A",   # Claremont Rd Stop NC - towards Kingston
        "outbound_naptan": "490015165B",  # Stop NK - towards Hook
        "name": "Surbiton Station",
        "inbound_dest": "Kingston",
        "outbound_dest": "Hook"
    }
}


def fetch_arrivals_from_naptan(naptan_id, api_key=None):
    """Fetch arrivals from a single NaPTAN stop."""
    url = f"{TFL_API_BASE}/StopPoint/{naptan_id}/arrivals"
    if api_key:
        url += f"?app_key={api_key}"

    req = urllib.request.Request(url)
    req.add_header('User-Agent', 't3-terse-transport-times/1.0')
    with urllib.request.urlopen(req, timeout=10) as response:
        return json.loads(response.read().decode())


def fetch_arrivals_for_stop(stop_key, api_key=None):
    """
    Fetch bus arrivals for a specific stop from TfL API.
    Queries both direction stops and returns combined results.
    """
    stop_config = STOPS.get(stop_key, STOPS["parklands"])

    inbound_seconds = []
    outbound_seconds = []
    errors = []

    # Fetch inbound arrivals
    try:
        data = fetch_arrivals_from_naptan(stop_config["inbound_naptan"], api_key)
        for arrival in data:
            if arrival.get('lineName') == ROUTE:
                inbound_seconds.append(arrival.get('timeToStation', 0))
    except Exception as e:
        errors.append(f"inbound: {e}")

    # Fetch outbound arrivals
    try:
        data = fetch_arrivals_from_naptan(stop_config["outbound_naptan"], api_key)
        for arrival in data:
            if arrival.get('lineName') == ROUTE:
                outbound_seconds.append(arrival.get('timeToStation', 0))
    except Exception as e:
        errors.append(f"outbound: {e}")

    if errors and not inbound_seconds and not outbound_seconds:
        return None, "; ".join(errors)

    # Sort by time
    inbound_seconds.sort()
    outbound_seconds.sort()

    # Limit to first 2 of each
    inbound_seconds = inbound_seconds[:2]
    outbound_seconds = outbound_seconds[:2]

    return {
        "stop": stop_config["name"],
        "inbound": {
            "seconds": inbound_seconds,
            "destination": stop_config["inbound_dest"]
        } if inbound_seconds else None,
        "outbound": {
            "seconds": outbound_seconds,
            "destination": stop_config["outbound_dest"]
        } if outbound_seconds else None
    }, None


def lambda_handler(event, context):
    """AWS Lambda entry point."""
    api_key = os.environ.get('TFL_API_KEY')

    # Get stop from query params
    params = event.get('queryStringParameters') or {}
    stop = params.get('stop', 'parklands')

    result, error = fetch_arrivals_for_stop(stop, api_key)

    cors_headers = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type,Accept',
        'Access-Control-Allow-Methods': 'GET,OPTIONS'
    }

    if error:
        return {
            'statusCode': 500,
            'body': json.dumps({'error': error}),
            'headers': {'Content-Type': 'application/json', **cors_headers}
        }

    return {
        'statusCode': 200,
        'body': json.dumps(result),
        'headers': {'Content-Type': 'application/json', **cors_headers}
    }


if __name__ == '__main__':
    import sys
    stop = sys.argv[1] if len(sys.argv) > 1 else 'parklands'
    result, error = fetch_arrivals_for_stop(stop)
    if error:
        print(f"Error: {error}")
    else:
        print(json.dumps(result, indent=2))
