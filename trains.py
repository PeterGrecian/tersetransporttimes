#!/usr/bin/env python3
"""
trains_darwin.py - Train times from Surbiton to Waterloo via National Rail Darwin API

Uses the official National Rail Darwin OpenLDBWS SOAP API.
Requires API key from: https://realtime.nationalrail.co.uk/OpenLDBWSRegistration/
"""

import json
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

DARWIN_ENDPOINT = "https://lite.realtime.nationalrail.co.uk/OpenLDBWS/ldb12.asmx"
DARWIN_PARAMETER_NAME = "/berrylands/darwin-api-key"
REGION = "eu-west-1"

# Cache API key (Lambda cold start only)
_cached_api_key = None


def get_darwin_api_key():
    """Get Darwin API key from Parameter Store (FREE!)."""
    global _cached_api_key

    if _cached_api_key:
        return _cached_api_key

    try:
        import boto3
        client = boto3.client('ssm', region_name=REGION)
        response = client.get_parameter(
            Name=DARWIN_PARAMETER_NAME,
            WithDecryption=True
        )
        _cached_api_key = response['Parameter']['Value']
        print(f"Darwin API key loaded from Parameter Store (FREE!)")
        return _cached_api_key
    except Exception as e:
        print(f"Error fetching Darwin API key from Parameter Store: {e}")
        # Fallback to environment variable for local testing
        import os
        env_key = os.environ.get('DARWIN_API_KEY')
        if env_key:
            print("Using DARWIN_API_KEY from environment variable")
            return env_key
        return None


def soap_request(api_key, from_station, to_station, num_services=6):
    """Make SOAP request to Darwin API (ldb12, WSDL version 2021-11-01)."""
    soap_body = f'''<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:tok="http://thalesgroup.com/RTTI/2013-11-28/Token/types"
               xmlns:ldb="http://thalesgroup.com/RTTI/2021-11-01/ldb/">
  <soap:Header>
    <tok:AccessToken>
      <tok:TokenValue>{api_key}</tok:TokenValue>
    </tok:AccessToken>
  </soap:Header>
  <soap:Body>
    <ldb:GetDepBoardWithDetailsRequest>
      <ldb:numRows>{num_services}</ldb:numRows>
      <ldb:crs>{from_station}</ldb:crs>
      <ldb:filterCrs>{to_station}</ldb:filterCrs>
      <ldb:filterType>to</ldb:filterType>
      <ldb:timeOffset>0</ldb:timeOffset>
      <ldb:timeWindow>120</ldb:timeWindow>
    </ldb:GetDepBoardWithDetailsRequest>
  </soap:Body>
</soap:Envelope>'''

    req = urllib.request.Request(
        DARWIN_ENDPOINT,
        data=soap_body.encode('utf-8'),
        headers={
            'Content-Type': 'text/xml; charset=utf-8',
            'SOAPAction': 'http://thalesgroup.com/RTTI/2015-05-14/ldb/GetDepBoardWithDetails'
        }
    )

    with urllib.request.urlopen(req, timeout=10) as response:
        return response.read().decode('utf-8')


def parse_darwin_response(xml_data, destination_crs='WAT'):
    """Parse Darwin SOAP XML response (ldb12 / 2021-11-01).

    Args:
        xml_data: SOAP response XML
        destination_crs: Destination station CRS code (to count stops only up to there)
    """
    # Response uses multiple versioned namespaces
    ns = {
        'lt4': 'http://thalesgroup.com/RTTI/2015-11-27/ldb/types',   # std, etd, platform, operator
        'lt8': 'http://thalesgroup.com/RTTI/2021-11-01/ldb/types',   # trainServices, callingPoints
    }

    root = ET.fromstring(xml_data)

    # Find train services
    services = root.findall('.//lt8:trainServices/lt8:service', ns)

    departures = []
    for service in services:
        try:
            # Get basic departure info
            std = service.find('lt4:std', ns)
            etd = service.find('lt4:etd', ns)
            platform = service.find('lt4:platform', ns)
            operator = service.find('lt4:operator', ns)

            std_time = std.text if std is not None else ''
            etd_time = etd.text if etd is not None else 'On time'

            # Check if cancelled
            cancelled = etd_time == 'Cancelled' if etd is not None else False

            # Get subsequent calling points for stops and arrival time
            calling_points = service.findall('.//lt8:subsequentCallingPoints/lt8:callingPointList/lt8:callingPoint', ns)

            # Count stops only up to destination, get arrival time at destination
            stops = 0
            arrival_time = ''
            found_destination = False

            if calling_points:
                for cp in calling_points:
                    crs_elem = cp.find('lt8:crs', ns)
                    st_elem = cp.find('lt8:st', ns)

                    if crs_elem is not None and st_elem is not None:
                        crs = crs_elem.text
                        st = st_elem.text

                        # Stop counting once we reach the destination
                        if crs == destination_crs.upper():
                            arrival_time = st
                            found_destination = True
                            break

                        # Count intermediate stops (not the destination)
                        stops += 1

            # If we didn't find the destination in calling points, use the last one
            if not found_destination and calling_points:
                last_cp = calling_points[-1]
                st_elem = last_cp.find('lt8:st', ns)
                if st_elem is not None:
                    arrival_time = st_elem.text
                stops = len(calling_points) - 1

            # Calculate journey minutes
            journey_mins = 0
            if std_time and arrival_time:
                try:
                    std_mins = int(std_time[:2]) * 60 + int(std_time[3:5])
                    arr_mins = int(arrival_time[:2]) * 60 + int(arrival_time[3:5])
                    journey_mins = arr_mins - std_mins
                    if journey_mins < 0:
                        journey_mins += 1440  # Handle midnight crossing
                except:
                    pass

            # Calculate delay
            delay_mins = 0
            expected_dep = std_time
            if etd_time not in ('On time', 'Delayed', 'Cancelled', ''):
                try:
                    std_mins = int(std_time[:2]) * 60 + int(std_time[3:5])
                    etd_mins = int(etd_time[:2]) * 60 + int(etd_time[3:5])
                    delay_mins = etd_mins - std_mins
                    if delay_mins < -720:
                        delay_mins += 1440
                    expected_dep = etd_time
                except:
                    pass

            # Calculate ETA
            eta = ''
            if arrival_time:
                try:
                    arr_mins = int(arrival_time[:2]) * 60 + int(arrival_time[3:5])
                    eta_mins = arr_mins + delay_mins
                    eta = f"{(eta_mins // 60) % 24:02d}{eta_mins % 60:02d}"
                except:
                    pass

            departures.append({
                'scheduledDeparture': std_time.replace(':', ''),
                'expectedDeparture': expected_dep.replace(':', ''),
                'arrivalTime': arrival_time.replace(':', '') if arrival_time else '',
                'eta': eta,
                'journeyMins': journey_mins,
                'stops': stops,
                'delayMinutes': delay_mins,
                'cancelled': cancelled,
                'status': etd_time
            })

        except Exception as e:
            print(f"Error parsing service: {e}")
            continue

    return departures


def fetch_departures(origin="sur", destination="wat", api_key=None):
    """
    Fetch train departures via Darwin API.
    origin/destination are CRS codes: sur=Surbiton, wat=Waterloo
    """
    if not api_key:
        return [], "No Darwin API key provided"

    origin_upper = origin.upper()
    destination_upper = destination.upper()

    try:
        print(f"Fetching Darwin data: {origin_upper} to {destination_upper}")
        xml_response = soap_request(api_key, origin_upper, destination_upper)
        print("Got Darwin response, parsing...")
        departures = parse_darwin_response(xml_response, destination_crs=destination_upper)
        print(f"Parsed {len(departures)} departures")
        return departures, None
    except Exception as e:
        print(f"Error fetching Darwin data: {type(e).__name__}: {e}")
        return [], str(e)


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

    # Get Darwin API key from Parameter Store (FREE!)
    api_key = get_darwin_api_key()
    if not api_key:
        return {
            'statusCode': 500,
            'body': json.dumps({'error': 'Darwin API key not configured'}),
            'headers': {'Content-Type': 'application/json', **cors_headers}
        }

    # Get direction from query params
    params = event.get('queryStringParameters') or {}
    origin = params.get('from', 'sur')
    destination = params.get('to', 'wat')

    departures, error = fetch_departures(origin, destination, api_key)

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
    import sys
    import argparse

    # Test mode with direction flags
    parser = argparse.ArgumentParser(
        description='Test Darwin train API',
        epilog='Examples:\n'
               '  python trains.py                    # Surbiton → Waterloo (default)\n'
               '  python trains.py --from sur --to wat  # Surbiton → Waterloo (explicit)\n'
               '  python trains.py --reverse          # Waterloo → Surbiton (reversed)\n'
               '  python trains.py --from wat --to sur  # Waterloo → Surbiton (explicit)',
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument('--from', '-f', dest='from_station', default='sur',
                        help='Origin station CRS code (default: sur=Surbiton)')
    parser.add_argument('--to', '-t', dest='to_station', default='wat',
                        help='Destination station CRS code (default: wat=Waterloo)')
    parser.add_argument('--reverse', '-r', action='store_true',
                        help='Reverse direction (swap from/to)')

    args = parser.parse_args()

    # Swap if reverse flag is set
    from_station = args.to_station if args.reverse else args.from_station
    to_station = args.from_station if args.reverse else args.to_station

    # For local testing - tries Parameter Store first, then environment variable
    api_key = get_darwin_api_key()
    if not api_key:
        print("Error: Darwin API key not found")
        print("Set DARWIN_API_KEY environment variable or ensure Parameter Store is configured")
        print("Get your key from: https://realtime.nationalrail.co.uk/OpenLDBWSRegistration/")
        sys.exit(1)

    print(f"Testing: {from_station.upper()} → {to_station.upper()}")
    print()

    departures, error = fetch_departures(from_station, to_station, api_key)
    if error:
        print(f"Error: {error}")
    else:
        result = json.loads(format_json(departures, from_station, to_station))
        print(f"Origin: {result['originName']}")
        print(f"Destination: {result['destinationName']}")
        print(f"Departures: {len(departures)}")
        print()
        for i, dep in enumerate(departures[:3], 1):
            print(f"{i}. {dep['scheduledDeparture']} → {dep['arrivalTime']} "
                  f"({dep['journeyMins']}min, {dep['stops']} stops)")
            if dep['delayMinutes']:
                print(f"   Delay: {dep['delayMinutes']}min ({dep['status']})")
            if dep['cancelled']:
                print(f"   ❌ CANCELLED")
        print()
        print("Full response (JSON):")
        print(format_json(departures, from_station, to_station))
