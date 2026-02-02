#!/usr/bin/env python3
"""
Test script for trains API - run locally to debug RTT integration.

Usage:
  RTT_USERNAME=xxx RTT_PASSWORD=xxx python3 test_trains.py
"""

import os
import sys

# Add parent dir so we can import trains.py
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from trains import fetch_departures, format_json

def main():
    username = os.environ.get('RTT_USERNAME')
    password = os.environ.get('RTT_PASSWORD')

    if not username or not password:
        print("ERROR: Set RTT_USERNAME and RTT_PASSWORD environment variables")
        print("\nUsage:")
        print("  RTT_USERNAME=xxx RTT_PASSWORD=xxx python3 test_trains.py")
        sys.exit(1)

    print(f"Fetching trains from Surbiton to Waterloo...")
    print(f"Using credentials: {username[:3]}***")
    print()

    departures, error = fetch_departures(username, password)

    if error:
        print(f"ERROR: {error}")
        sys.exit(1)

    if not departures:
        print("No departures found (service may be unavailable at this time)")
    else:
        print(f"Found {len(departures)} departures:\n")
        for d in departures:
            status = "CANCELLED" if d['cancelled'] else f"+{d['delayMinutes']}min" if d['delayMinutes'] > 0 else "On time"
            print(f"  {d['scheduledDeparture'][:2]}:{d['scheduledDeparture'][2:]} - Platform {d['platform'] or '?'} - {status}")

    print("\n--- Raw JSON output ---")
    print(format_json(departures))

if __name__ == '__main__':
    main()
