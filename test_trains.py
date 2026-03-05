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


# Real Darwin SOAP responses
MOCK_RESPONSE_SUR_TO_WAT = '''<?xml version="1.0" encoding="utf-8"?><soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soap:Body><GetDepBoardWithDetailsResponse xmlns="http://thalesgroup.com/RTTI/2021-11-01/ldb/"><GetStationBoardResult xmlns:lt="http://thalesgroup.com/RTTI/2012-01-13/ldb/types" xmlns:lt8="http://thalesgroup.com/RTTI/2021-11-01/ldb/types" xmlns:lt6="http://thalesgroup.com/RTTI/2017-02-02/ldb/types" xmlns:lt7="http://thalesgroup.com/RTTI/2017-10-01/ldb/types" xmlns:lt4="http://thalesgroup.com/RTTI/2015-11-27/ldb/types" xmlns:lt5="http://thalesgroup.com/RTTI/2016-02-16/ldb/types" xmlns:lt2="http://thalesgroup.com/RTTI/2014-02-20/ldb/types" xmlns:lt3="http://thalesgroup.com/RTTI/2015-05-14/ldb/types"><lt4:generatedAt>2026-03-05T14:36:54.4278554+00:00</lt4:generatedAt><lt4:locationName>Surbiton</lt4:locationName><lt4:crs>SUR</lt4:crs><lt4:filterLocationName>London Waterloo</lt4:filterLocationName><lt4:filtercrs>WAT</lt4:filtercrs><lt4:platformAvailable>true</lt4:platformAvailable><lt8:trainServices><lt8:service><lt4:std>14:38</lt4:std><lt4:etd>On time</lt4:etd><lt4:platform>1</lt4:platform><lt4:operator>South Western Railway</lt4:operator><lt4:operatorCode>SW</lt4:operatorCode><lt4:serviceType>train</lt4:serviceType><lt4:serviceID>883929SURBITN_</lt4:serviceID><lt5:origin><lt4:location><lt4:locationName>Alton</lt4:locationName><lt4:crs>AON</lt4:crs></lt4:location></lt5:origin><lt5:destination><lt4:location><lt4:locationName>London Waterloo</lt4:locationName><lt4:crs>WAT</lt4:crs></lt4:location></lt5:destination><lt8:subsequentCallingPoints><lt8:callingPointList><lt8:callingPoint><lt8:locationName>Clapham Junction</lt8:locationName><lt8:crs>CLJ</lt8:crs><lt8:st>14:49</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>London Waterloo</lt8:locationName><lt8:crs>WAT</lt8:crs><lt8:st>14:58</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint></lt8:callingPointList></lt8:subsequentCallingPoints></lt8:service></lt8:trainServices></GetStationBoardResult></GetDepBoardWithDetailsResponse></soap:Body></soap:Envelope>'''

MOCK_RESPONSE_WAT_TO_SUR = '''<?xml version="1.0" encoding="utf-8"?><soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soap:Body><GetDepBoardWithDetailsResponse xmlns="http://thalesgroup.com/RTTI/2021-11-01/ldb/"><GetStationBoardResult xmlns:lt="http://thalesgroup.com/RTTI/2012-01-13/ldb/types" xmlns:lt8="http://thalesgroup.com/RTTI/2021-11-01/ldb/types" xmlns:lt6="http://thalesgroup.com/RTTI/2017-02-02/ldb/types" xmlns:lt7="http://thalesgroup.com/RTTI/2017-10-01/ldb/types" xmlns:lt4="http://thalesgroup.com/RTTI/2015-11-27/ldb/types" xmlns:lt5="http://thalesgroup.com/RTTI/2016-02-16/ldb/types" xmlns:lt2="http://thalesgroup.com/RTTI/2014-02-20/ldb/types" xmlns:lt3="http://thalesgroup.com/RTTI/2015-05-14/ldb/types"><lt4:generatedAt>2026-03-05T14:36:54.5798573+00:00</lt4:generatedAt><lt4:locationName>London Waterloo</lt4:locationName><lt4:crs>WAT</lt4:crs><lt4:filterLocationName>Surbiton</lt4:filterLocationName><lt4:filtercrs>SUR</lt4:filtercrs><lt4:platformAvailable>true</lt4:platformAvailable><lt8:trainServices><lt8:service><lt4:std>14:36</lt4:std><lt4:etd>On time</lt4:etd><lt4:platform>3</lt4:platform><lt4:operator>South Western Railway</lt4:operator><lt4:operatorCode>SW</lt4:operatorCode><lt4:serviceType>train</lt4:serviceType><lt4:serviceID>886054WATRLMN_</lt4:serviceID><lt5:origin><lt4:location><lt4:locationName>London Waterloo</lt4:locationName><lt4:crs>WAT</lt4:crs></lt4:location></lt5:origin><lt5:destination><lt4:location><lt4:locationName>Hampton Court</lt4:locationName><lt4:crs>HMC</lt4:crs></lt4:location></lt5:destination><lt8:subsequentCallingPoints><lt8:callingPointList><lt8:callingPoint><lt8:locationName>Vauxhall</lt8:locationName><lt8:crs>VXH</lt8:crs><lt8:st>14:39</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>Clapham Junction</lt8:locationName><lt8:crs>CLJ</lt8:crs><lt8:st>14:44</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>Earlsfield</lt8:locationName><lt8:crs>EAD</lt8:crs><lt8:st>14:48</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>Wimbledon</lt8:locationName><lt8:crs>WIM</lt8:crs><lt8:st>14:52</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>Raynes Park</lt8:locationName><lt8:crs>RAY</lt8:crs><lt8:st>14:55</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>New Malden</lt8:locationName><lt8:crs>NEM</lt8:crs><lt8:st>14:58</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>Berrylands</lt8:locationName><lt8:crs>BRS</lt8:crs><lt8:st>15:01</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>Surbiton</lt8:locationName><lt8:crs>SUR</lt8:crs><lt8:st>15:05</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>Thames Ditton</lt8:locationName><lt8:crs>THD</lt8:crs><lt8:st>15:10</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>Hampton Court</lt8:locationName><lt8:crs>HMC</lt8:crs><lt8:st>15:13</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint></lt8:callingPointList></lt8:subsequentCallingPoints></lt8:service></lt8:trainServices></GetStationBoardResult></GetDepBoardWithDetailsResponse></soap:Body></soap:Envelope>'''

MOCK_RESPONSE_WITH_DELAY = '''<?xml version="1.0" encoding="utf-8"?><soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soap:Body><GetDepBoardWithDetailsResponse xmlns="http://thalesgroup.com/RTTI/2021-11-01/ldb/"><GetStationBoardResult xmlns:lt="http://thalesgroup.com/RTTI/2012-01-13/ldb/types" xmlns:lt8="http://thalesgroup.com/RTTI/2021-11-01/ldb/types" xmlns:lt6="http://thalesgroup.com/RTTI/2017-02-02/ldb/types" xmlns:lt7="http://thalesgroup.com/RTTI/2017-10-01/ldb/types" xmlns:lt4="http://thalesgroup.com/RTTI/2015-11-27/ldb/types" xmlns:lt5="http://thalesgroup.com/RTTI/2016-02-16/ldb/types" xmlns:lt2="http://thalesgroup.com/RTTI/2014-02-20/ldb/types" xmlns:lt3="http://thalesgroup.com/RTTI/2015-05-14/ldb/types"><lt4:generatedAt>2026-03-05T09:58:00.0000000+00:00</lt4:generatedAt><lt4:locationName>Surbiton</lt4:locationName><lt4:crs>SUR</lt4:crs><lt4:filterLocationName>London Waterloo</lt4:filterLocationName><lt4:filtercrs>WAT</lt4:filtercrs><lt4:platformAvailable>true</lt4:platformAvailable><lt8:trainServices><lt8:service><lt4:std>10:00</lt4:std><lt4:etd>10:08</lt4:etd><lt4:platform>2</lt4:platform><lt4:operator>South Western Railway</lt4:operator><lt4:operatorCode>SW</lt4:operatorCode><lt4:serviceType>train</lt4:serviceType><lt4:serviceID>883929DELAYED__</lt4:serviceID><lt5:origin><lt4:location><lt4:locationName>Alton</lt4:locationName><lt4:crs>AON</lt4:crs></lt4:location></lt5:origin><lt5:destination><lt4:location><lt4:locationName>London Waterloo</lt4:locationName><lt4:crs>WAT</lt4:crs></lt4:location></lt5:destination><lt8:subsequentCallingPoints><lt8:callingPointList><lt8:callingPoint><lt8:locationName>Clapham Junction</lt8:locationName><lt8:crs>CLJ</lt8:crs><lt8:st>10:11</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>London Waterloo</lt8:locationName><lt8:crs>WAT</lt8:crs><lt8:st>10:20</lt8:st><lt8:et>On time</lt8:et></lt8:callingPoint></lt8:callingPointList></lt8:subsequentCallingPoints></lt8:service></lt8:trainServices></GetStationBoardResult></GetDepBoardWithDetailsResponse></soap:Body></soap:Envelope>'''

MOCK_RESPONSE_CANCELLED = '''<?xml version="1.0" encoding="utf-8"?><soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soap:Body><GetDepBoardWithDetailsResponse xmlns="http://thalesgroup.com/RTTI/2021-11-01/ldb/"><GetStationBoardResult xmlns:lt="http://thalesgroup.com/RTTI/2012-01-13/ldb/types" xmlns:lt8="http://thalesgroup.com/RTTI/2021-11-01/ldb/types" xmlns:lt6="http://thalesgroup.com/RTTI/2017-02-02/ldb/types" xmlns:lt7="http://thalesgroup.com/RTTI/2017-10-01/ldb/types" xmlns:lt4="http://thalesgroup.com/RTTI/2015-11-27/ldb/types" xmlns:lt5="http://thalesgroup.com/RTTI/2016-02-16/ldb/types" xmlns:lt2="http://thalesgroup.com/RTTI/2014-02-20/ldb/types" xmlns:lt3="http://thalesgroup.com/RTTI/2015-05-14/ldb/types"><lt4:generatedAt>2026-03-05T11:13:00.0000000+00:00</lt4:generatedAt><lt4:locationName>Surbiton</lt4:locationName><lt4:crs>SUR</lt4:crs><lt4:filterLocationName>London Waterloo</lt4:filterLocationName><lt4:filtercrs>WAT</lt4:filtercrs><lt4:platformAvailable>true</lt4:platformAvailable><lt8:trainServices><lt8:service><lt4:std>11:15</lt4:std><lt4:etd>Cancelled</lt4:etd><lt4:platform>1</lt4:platform><lt4:operator>South Western Railway</lt4:operator><lt4:operatorCode>SW</lt4:operatorCode><lt4:serviceType>train</lt4:serviceType><lt4:serviceID>883929CANCEL__</lt4:serviceID><lt5:origin><lt4:location><lt4:locationName>Alton</lt4:locationName><lt4:crs>AON</lt4:crs></lt4:location></lt5:origin><lt5:destination><lt4:location><lt4:locationName>London Waterloo</lt4:locationName><lt4:crs>WAT</lt4:crs></lt4:location></lt5:destination><lt8:subsequentCallingPoints><lt8:callingPointList><lt8:callingPoint><lt8:locationName>Clapham Junction</lt8:locationName><lt8:crs>CLJ</lt8:crs><lt8:st>11:26</lt8:st><lt8:et>Cancelled</lt8:et></lt8:callingPoint><lt8:callingPoint><lt8:locationName>London Waterloo</lt8:locationName><lt8:crs>WAT</lt8:crs><lt8:st>11:35</lt8:st><lt8:et>Cancelled</lt8:et></lt8:callingPoint></lt8:callingPointList></lt8:subsequentCallingPoints></lt8:service></lt8:trainServices></GetStationBoardResult></GetDepBoardWithDetailsResponse></soap:Body></soap:Envelope>'''


class TestStopCounting:
    """Tests for the stop counting fix (main issue we fixed)"""

    def test_surbiton_to_waterloo_stop_count(self):
        """Surbiton→Waterloo should have 1 intermediate stop (CLJ), not more"""
        departures = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        assert len(departures) == 1
        assert departures[0]['stops'] == 1  # CLJ, not counting Waterloo
        assert departures[0]['arrivalTime'] == '1458'

    def test_waterloo_to_surbiton_stop_count(self):
        """Waterloo→Surbiton should stop counting at Surbiton"""
        departures = parse_darwin_response(MOCK_RESPONSE_WAT_TO_SUR, destination_crs='SUR')
        assert len(departures) == 1
        assert departures[0]['stops'] == 7  # VXH, CLJ, EAD, WIM, RAY, NEM, BRS (stops before SUR)
        assert departures[0]['arrivalTime'] == '1505'  # Arrival at SUR

    def test_stops_not_including_destination(self):
        """Stop count should NOT include the destination station itself"""
        departures = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        # Should be 1 (CLJ), not 2 (CLJ + WAT)
        assert departures[0]['stops'] == 1


class TestArrivalTimes:
    """Tests for arrival time extraction"""

    def test_arrival_time_extraction_sur_to_wat(self):
        """Should extract correct arrival time at destination"""
        departures = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        assert departures[0]['arrivalTime'] == '1458'

    def test_arrival_time_extraction_wat_to_sur(self):
        """Should extract correct arrival time at Surbiton"""
        departures = parse_darwin_response(MOCK_RESPONSE_WAT_TO_SUR, destination_crs='SUR')
        assert departures[0]['arrivalTime'] == '1505'


class TestDelays:
    """Tests for delay handling"""

    def test_delay_calculation(self):
        """Should calculate delays correctly"""
        departures = parse_darwin_response(MOCK_RESPONSE_WITH_DELAY, destination_crs='WAT')
        assert len(departures) == 1
        assert departures[0]['delayMinutes'] == 8  # ETD 10:08 vs STD 10:00
        assert departures[0]['status'] == '10:08'
        assert departures[0]['expectedDeparture'] == '1008'

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
        # 14:38 to 14:58 = 20 minutes
        assert departures[0]['journeyMins'] == 20

    def test_journey_duration_with_delay(self):
        """Should calculate journey time even with delays"""
        departures = parse_darwin_response(MOCK_RESPONSE_WITH_DELAY, destination_crs='WAT')
        # 10:00 to 10:20 = 20 minutes (scheduled times used, not ETD)
        assert departures[0]['journeyMins'] == 20
        assert departures[0]['delayMinutes'] == 8


class TestDirectionHandling:
    """Tests for handling different directions"""

    def test_parse_with_destination_crs_param(self):
        """parse_darwin_response should respect destination CRS parameter"""
        # Same response, different destinations = different stop counts
        departures_to_wat = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='WAT')
        departures_to_clj = parse_darwin_response(MOCK_RESPONSE_SUR_TO_WAT, destination_crs='CLJ')

        # Stopping at CLJ should have 0 stops (CLJ is the first calling point)
        assert departures_to_clj[0]['stops'] == 0
        # Stopping at WAT should have 1 stop (CLJ)
        assert departures_to_wat[0]['stops'] == 1


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
