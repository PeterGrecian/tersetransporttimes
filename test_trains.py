#!/usr/bin/env python3
"""
pytest tests for trains.py Darwin API parsing

Tests focus on:
1. Stop count accuracy (the bug we just fixed)
2. Direction handling (Surbiton→Waterloo and Waterloo→Surbiton)
3. Arrival time extraction
4. Edge cases (cancelled trains, missing data)

Run with: pytest test_trains.py -v
"""

import pytest
import json
from trains import parse_darwin_response, format_json


# Mock Darwin SOAP responses
MOCK_RESPONSE_SUR_TO_WAT = '''<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetDepBoardWithDetailsResponse xmlns="http://thalesgroup.com/RTTI/2021-11-01/ldb/">
      <GetDepBoardWithDetailsResult>
        <trainServices xmlns:lt8="http://thalesgroup.com/RTTI/2021-11-01/ldb/types"
                       xmlns:lt4="http://thalesgroup.com/RTTI/2015-11-27/ldb/types">
          <lt8:service>
            <lt4:std>09:15</lt4:std>
            <lt4:etd>On time</lt4:etd>
            <lt4:platform>2</lt4:platform>
            <lt4:operator>South Western Railway</lt4:operator>
            <lt8:subsequentCallingPoints>
              <lt8:callingPointList>
                <lt8:callingPoint>
                  <lt8:crs>VAX</lt8:crs>
                  <lt8:st>09:21</lt8:st>
                </lt8:callingPoint>
                <lt8:callingPoint>
                  <lt8:crs>CLJ</lt8:crs>
                  <lt8:st>09:28</lt8:st>
                </lt8:callingPoint>
                <lt8:callingPoint>
                  <lt8:crs>WAT</lt8:crs>
                  <lt8:st>09:42</lt8:st>
                </lt8:callingPoint>
              </lt8:callingPointList>
            </lt8:subsequentCallingPoints>
          </lt8:service>
        </trainServices>
      </GetDepBoardWithDetailsResult>
    </GetDepBoardWithDetailsResponse>
  </soap:Body>
</soap:Envelope>'''

MOCK_RESPONSE_WAT_TO_SUR = '''<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetDepBoardWithDetailsResponse xmlns="http://thalesgroup.com/RTTI/2021-11-01/ldb/">
      <GetDepBoardWithDetailsResult>
        <trainServices xmlns:lt8="http://thalesgroup.com/RTTI/2021-11-01/ldb/types"
                       xmlns:lt4="http://thalesgroup.com/RTTI/2015-11-27/ldb/types">
          <lt8:service>
            <lt4:std>17:30</lt4:std>
            <lt4:etd>On time</lt4:etd>
            <lt4:platform>10</lt4:platform>
            <lt4:operator>South Western Railway</lt4:operator>
            <lt8:subsequentCallingPoints>
              <lt8:callingPointList>
                <lt8:callingPoint>
                  <lt8:crs>VAX</lt8:crs>
                  <lt8:st>17:36</lt8:st>
                </lt8:callingPoint>
                <lt8:callingPoint>
                  <lt8:crs>CLJ</lt8:crs>
                  <lt8:st>17:43</lt8:st>
                </lt8:callingPoint>
                <lt8:callingPoint>
                  <lt8:crs>SUR</lt8:crs>
                  <lt8:st>17:52</lt8:st>
                </lt8:callingPoint>
              </lt8:callingPointList>
            </lt8:subsequentCallingPoints>
          </lt8:service>
        </trainServices>
      </GetDepBoardWithDetailsResult>
    </GetDepBoardWithDetailsResponse>
  </soap:Body>
</soap:Envelope>'''

MOCK_RESPONSE_WITH_DELAY = '''<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetDepBoardWithDetailsResponse xmlns="http://thalesgroup.com/RTTI/2021-11-01/ldb/">
      <GetDepBoardWithDetailsResult>
        <trainServices xmlns:lt8="http://thalesgroup.com/RTTI/2021-11-01/ldb/types"
                       xmlns:lt4="http://thalesgroup.com/RTTI/2015-11-27/ldb/types">
          <lt8:service>
            <lt4:std>10:00</lt4:std>
            <lt4:etd>10:08</lt4:etd>
            <lt4:platform>3</lt4:platform>
            <lt4:operator>South Western Railway</lt4:operator>
            <lt8:subsequentCallingPoints>
              <lt8:callingPointList>
                <lt8:callingPoint>
                  <lt8:crs>VAX</lt8:crs>
                  <lt8:st>10:06</lt8:st>
                </lt8:callingPoint>
                <lt8:callingPoint>
                  <lt8:crs>WAT</lt8:crs>
                  <lt8:st>10:20</lt8:st>
                </lt8:callingPoint>
              </lt8:callingPointList>
            </lt8:subsequentCallingPoints>
          </lt8:service>
        </trainServices>
      </GetDepBoardWithDetailsResult>
    </GetDepBoardWithDetailsResponse>
  </soap:Body>
</soap:Envelope>'''

MOCK_RESPONSE_CANCELLED = '''<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetDepBoardWithDetailsResponse xmlns="http://thalesgroup.com/RTTI/2021-11-01/ldb/">
      <GetDepBoardWithDetailsResult>
        <trainServices xmlns:lt8="http://thalesgroup.com/RTTI/2021-11-01/ldb/types"
                       xmlns:lt4="http://thalesgroup.com/RTTI/2015-11-27/ldb/types">
          <lt8:service>
            <lt4:std>11:15</lt4:std>
            <lt4:etd>Cancelled</lt4:etd>
            <lt8:subsequentCallingPoints>
              <lt8:callingPointList>
                <lt8:callingPoint>
                  <lt8:crs>WAT</lt8:crs>
                  <lt8:st>11:30</lt8:st>
                </lt8:callingPoint>
              </lt8:callingPointList>
            </lt8:subsequentCallingPoints>
          </lt8:service>
        </trainServices>
      </GetDepBoardWithDetailsResult>
    </GetDepBoardWithDetailsResponse>
  </soap:Body>
</soap:Envelope>'''


class TestStopCounting:
    """Tests for the stop counting fix (main issue we fixed)"""

    def test_surbiton_to_waterloo_stop_count(self):
        """Surbiton→Waterloo should have 2 intermediate stops (VAX, CLJ), not more"""
        departures = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        assert len(departures) == 1
        assert departures[0]['stops'] == 2  # VAX and CLJ, not counting Waterloo
        assert departures[0]['arrivalTime'] == '0942'

    def test_waterloo_to_surbiton_stop_count(self):
        """Waterloo→Surbiton should stop counting at Surbiton"""
        departures = parse_darwin_response(MOCK_RESPONSE_WAT_TO_SUR, destination_crs='SUR')
        assert len(departures) == 1
        assert departures[0]['stops'] == 2  # VAX, CLJ (stops before SUR)
        assert departures[0]['arrivalTime'] == '1752'  # Arrival at SUR

    def test_stops_not_including_destination(self):
        """Stop count should NOT include the destination station itself"""
        departures = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        # Should be 2 (VAX + CLJ), not 3 (VAX + CLJ + WAT)
        assert departures[0]['stops'] == 2


class TestArrivalTimes:
    """Tests for arrival time extraction"""

    def test_arrival_time_extraction_sur_to_wat(self):
        """Should extract correct arrival time at destination"""
        departures = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        assert departures[0]['arrivalTime'] == '0942'

    def test_arrival_time_extraction_wat_to_sur(self):
        """Should extract correct arrival time at Surbiton"""
        departures = parse_darwin_response(MOCK_RESPONSE_WAT_TO_SUR, destination_crs='SUR')
        assert departures[0]['arrivalTime'] == '1752'


class TestDelays:
    """Tests for delay handling"""

    def test_delay_calculation(self):
        """Should calculate delays correctly"""
        departures = parse_darwin_response(MOCK_RESPONSE_WITH_DELAY, destination_crs='WAT')
        assert len(departures) == 1
        assert departures[0]['delayMinutes'] == 8  # ETD 10:08 vs STD 10:00
        assert departures[0]['status'] == '1008'

    def test_on_time_status(self):
        """Should show 'On time' for trains without delays"""
        departures = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        assert departures[0]['status'] == 'On time'


class TestCancelledTrains:
    """Tests for cancelled train handling"""

    def test_cancelled_train_detection(self):
        """Should mark cancelled trains"""
        departures = parse_darwin_response(MOCK_RESPONSE_CANCELLED, destination_crs='WAT')
        assert len(departures) == 1
        assert departures[0]['cancelled'] is True
        assert departures[0]['status'] == 'Cancelled'


class TestJourneyDuration:
    """Tests for journey time calculation"""

    def test_journey_duration_calculation(self):
        """Should calculate journey time in minutes"""
        departures = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        # 09:15 to 09:42 = 27 minutes
        assert departures[0]['journeyMins'] == 27

    def test_journey_duration_with_delay(self):
        """Should calculate journey time even with delays"""
        departures = parse_darwin_response(MOCK_RESPONSE_WITH_DELAY, destination_crs='WAT')
        # 10:00 to 10:20 = 20 minutes (scheduled time used, not ETD)
        assert departures[0]['journeyMins'] == 20


class TestDirectionHandling:
    """Tests for handling different directions"""

    def test_parse_with_destination_crs_param(self):
        """parse_darwin_response should respect destination CRS parameter"""
        # Same response, different destinations = different stop counts
        departures_to_wat = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        departures_to_clj = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='CLJ')

        # Stopping at CLJ should have 1 stop (just VAX)
        assert departures_to_clj[0]['stops'] == 1
        # Stopping at WAT should have 2 stops (VAX + CLJ)
        assert departures_to_wat[0]['stops'] == 2


class TestJSONFormatting:
    """Tests for JSON response formatting"""

    def test_format_json_structure(self):
        """JSON response should have correct structure"""
        departures = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        json_str = format_json(departures, 'sur', 'wat')

        data = json.loads(json_str)

        assert 'originName' in data
        assert 'destinationName' in data
        assert 'timestamp' in data
        assert 'departures' in data
        assert data['originName'] == 'Surbiton'
        assert data['destinationName'] == 'London Waterloo'


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
