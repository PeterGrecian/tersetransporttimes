#!/usr/bin/env python3
"""
t3.py - Terse Transport Times
A minimal Lambda function that returns expected bus arrival intervals for a specific stop.
"""

import json
import urllib.request
from datetime import datetime, timezone

TFL_API_BASE = "https://api.tfl.gov.uk"
ROUTE = "K2"
TFL_PARAMETER_NAME = "/berrylands/tfl-api-key"
REGION = "eu-west-1"

_cached_api_key = None


def get_tfl_api_key():
    """Get TfL API key from Parameter Store."""
    global _cached_api_key
    if _cached_api_key:
        return _cached_api_key
    try:
        import boto3
        client = boto3.client('ssm', region_name=REGION)
        response = client.get_parameter(Name=TFL_PARAMETER_NAME, WithDecryption=True)
        _cached_api_key = response['Parameter']['Value']
        return _cached_api_key
    except Exception as e:
        print(f"Error fetching TfL API key from Parameter Store: {e}")
        return None

# Stop configurations: single direction per location for simplified commute
# Morning: Parklands inbound → Surbiton
# Evening: Surbiton outbound → Home (Hook direction)
STOPS = {
    "parklands": {
        "naptan_id": "490010781S",   # Parklands inbound (towards Kingston/Surbiton)
        "name": "Parklands",
        "destination": "Surbiton"
    },
    "surbiton": {
        "naptan_id": "490015165B",   # Surbiton Stop NK outbound (towards Hook/Home)
        "name": "Surbiton Station",
        "destination": "Home"
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
    Simplified to single direction per location.
    """
    stop_config = STOPS.get(stop_key, STOPS["parklands"])

    seconds = []

    # Fetch arrivals for the configured stop
    try:
        data = fetch_arrivals_from_naptan(stop_config["naptan_id"], api_key)
        for arrival in data:
            if arrival.get('lineName') == ROUTE:
                seconds.append(arrival.get('timeToStation', 0))
    except Exception as e:
        return None, f"Failed to fetch arrivals: {e}"

    if not seconds:
        return {"stop": stop_config["name"], "destination": stop_config["destination"], "seconds": []}, None

    # Sort by time and limit to first 3
    seconds.sort()
    seconds = seconds[:2]

    return {
        "stop": stop_config["name"],
        "destination": stop_config["destination"],
        "seconds": seconds
    }, None


def lambda_handler(event, context):
    """AWS Lambda entry point."""
    api_key = get_tfl_api_key()

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
