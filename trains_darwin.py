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


DARWIN_WSDL = "https://lite.realtime.nationalrail.co.uk/OpenLDBWS/ldb11.asmx"


def soap_request(api_key, from_station, to_station, num_services=6):
    """Make SOAP request to Darwin API."""
    soap_body = f'''<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:typ="http://thalesgroup.com/RTTI/2013-11-28/Token/types"
               xmlns:ldb="http://thalesgroup.com/RTTI/2017-10-01/ldb/">
  <soap:Header>
    <typ:AccessToken>
      <typ:TokenValue>{api_key}</typ:TokenValue>
    </typ:AccessToken>
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
        DARWIN_WSDL,
        data=soap_body.encode('utf-8'),
        headers={
            'Content-Type': 'text/xml; charset=utf-8',
            'SOAPAction': 'http://thalesgroup.com/RTTI/2017-10-01/ldb/GetDepBoardWithDetails'
        }
    )

    with urllib.request.urlopen(req, timeout=10) as response:
        return response.read().decode('utf-8')


def parse_darwin_response(xml_data):
    """Parse Darwin SOAP XML response."""
    # Register namespaces
    namespaces = {
        'soap': 'http://schemas.xmlsoap.org/soap/envelope/',
        'lt7': 'http://thalesgroup.com/RTTI/2017-10-01/ldb/',
        'lt4': 'http://thalesgroup.com/RTTI/2015-11-27/ldb/types',
        'lt': 'http://thalesgroup.com/RTTI/2012-01-13/ldb/types'
    }

    root = ET.fromstring(xml_data)

    # Find train services
    services = root.findall('.//lt7:trainServices/lt7:service', namespaces)

    departures = []
    for service in services:
        try:
            # Get basic departure info
            std = service.find('.//lt4:std', namespaces)
            etd = service.find('.//lt4:etd', namespaces)
            platform = service.find('.//lt4:platform', namespaces)
            operator = service.find('.//lt4:operator', namespaces)

            std_time = std.text if std is not None else ''
            etd_time = etd.text if etd is not None else 'On time'

            # Check if cancelled
            cancelled = etd_time == 'Cancelled' if etd is not None else False

            # Get subsequent calling points for stops and arrival time
            calling_points = service.findall('.//lt7:subsequentCallingPoints/lt7:callingPointList/lt7:callingPoint', namespaces)

            stops = len(calling_points) - 1 if calling_points else 0
            arrival_time = ''

            # Get destination arrival time (last calling point)
            if calling_points:
                last_point = calling_points[-1]
                st = last_point.find('.//lt4:st', namespaces)
                if st is not None:
                    arrival_time = st.text

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
        departures = parse_darwin_response(xml_response)
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
    import os

    cors_headers = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type,Accept',
        'Access-Control-Allow-Methods': 'GET,OPTIONS'
    }

    # Get Darwin API key from environment
    api_key = os.environ.get('DARWIN_API_KEY')
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
    import os

    # For local testing - set DARWIN_API_KEY environment variable
    api_key = os.environ.get('DARWIN_API_KEY')
    if not api_key:
        print("Error: Set DARWIN_API_KEY environment variable")
        print("Get your key from: https://realtime.nationalrail.co.uk/OpenLDBWSRegistration/")
        sys.exit(1)

    origin = sys.argv[1] if len(sys.argv) > 1 else 'sur'
    destination = sys.argv[2] if len(sys.argv) > 2 else 'wat'

    departures, error = fetch_departures(origin, destination, api_key)
    if error:
        print(f"Error: {error}")
    else:
        print(format_json(departures, origin, destination))
