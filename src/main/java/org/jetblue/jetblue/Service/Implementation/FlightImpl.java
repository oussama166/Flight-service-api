package org.jetblue.jetblue.Service.Implementation;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetblue.jetblue.Mapper.Flight.FlightMapper;
import org.jetblue.jetblue.Mapper.Flight.FlightResponse;
import org.jetblue.jetblue.Models.DAO.Airplane;
import org.jetblue.jetblue.Models.DAO.Airport;
import org.jetblue.jetblue.Models.DAO.Flight;
import org.jetblue.jetblue.Models.DAO.FlightStatus;
import org.jetblue.jetblue.Repositories.*;
import org.jetblue.jetblue.Service.FlightService;
import org.jetblue.jetblue.Utils.FlightUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class FlightImpl implements FlightService {

    private final AirportRepo airportRepo;
    private final AirlineRepo airlineRepo;
    private final FlightStatusRepo flightStatusRepo;
    private final AirplaneRepo airplaneRepo;
    private FlightRepo flightRepo;

    private final Logger LOG = LoggerFactory.getLogger(this.getClass());


    @Override
    public FlightResponse setFlight(
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            double price,
            int maxSeat,
            String departure,
            String arrival,
            String airline,
            String airplane,
            int maxFirst,
            int maxSecond,
            int maxThird,
            String flightStatus
    ) {
        // Find necessary entities
        Airport departureAirport = airportRepo.findByCodeOrLocation(departure).orElseThrow(
                () -> new IllegalArgumentException("Departure airport not found"));
        Airport arrivalAirport = airportRepo.findByCodeOrLocation(arrival).orElseThrow(
                () -> new IllegalArgumentException("Arrival airport not found"));
        Airplane targetAirplane = airplaneRepo.findByName(airplane).orElseThrow(
                () -> new IllegalArgumentException("Airplane not found"));

        // Validate business logic
        if (departureAirport.equals(arrivalAirport)) {
            throw new DataIntegrityViolationException("Departure and arrival airports cannot be the same");
        }
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("Arrival time must be after departure time");
        }
        if (targetAirplane.getMaxSeat() < (maxFirst + maxSecond + maxThird)) {
            throw new IllegalArgumentException("Total seats exceed the airplane's capacity");
        }

        // Initialize flight object
        Flight flight = new Flight();
        double distance = FlightUtils.calculateDistance(departureAirport.getLatitude(), departureAirport.getLongitude(), arrivalAirport.getLatitude(), arrivalAirport.getLongitude());
        flight.setDeparture(departureAirport);
        flight.setArrival(arrivalAirport);
        flight.setAirplane(targetAirplane);
        flight.setAirline(airlineRepo.findByAirlineCode(airline).orElseThrow(
                () -> new IllegalArgumentException("Airline not found")));
        flight.setStatus(flightStatusRepo.findByStatus(flightStatus).orElseThrow(
                () -> new IllegalArgumentException("Flight status not found")));
        flight.setDepartureTime(departureTime);
        flight.setArrivalTime(arrivalTime);
        flight.setDistance(distance);
        flight.setDuration(FlightUtils.estimateTimeToReachDes(distance));
        flight.setPrice(price);
        flight.setMaxFirstClass(maxFirst);
        flight.setMaxSecondClass(maxSecond);
        flight.setMaxThirdClass(maxThird);
        flight.setMaxSeats(maxSeat);

        // Save flight with proper exception handling
        try {
            Flight res = flightRepo.save(flight);
            return FlightMapper.toFlightResponse(res);
        } catch (DataIntegrityViolationException e) {
            System.err.println("Failed to save flight: " + e.getMessage());
            throw new RuntimeException("Could not save flight due to unique constraint violation", e);
        }
    }

    @Override
    public Flight getFlight(String numberFlight) {
        return flightRepo.findByFlightNumber(numberFlight).orElse(null);
    }

    @Override
    public Flight getFlight(String numberFlight, String airline) {
        return flightRepo.findByFlightNumberOrAirline_AirlineCode(numberFlight, airline).orElse(null);

    }

    @Override
    public List<FlightResponse> getFlight(String departure, String arrival, String flightStatus) throws Exception {
        // Validate departure airport
        LOG.info(departure);
        Airport departureAirport = airportRepo.findByCodeOrLocation(departure)
                .orElseThrow(() -> new IllegalArgumentException("Departure airport not found"));

        // Validate arrival airport
        Airport arrivalAirport = airportRepo.findByCodeOrLocation(arrival)
                .orElseThrow(() -> new IllegalArgumentException("Arrival airport not found"));

        // Check if departure and arrival airports are the same
        if (departureAirport.equals(arrivalAirport)) {
            throw new DataIntegrityViolationException("Departure and arrival airports cannot be the same");
        }

        // Getting the flight status
        FlightStatus flightStatusEntity = flightStatusRepo.findByStatus(flightStatus)
                .orElseGet(() -> {
                    try {
                        return flightStatusRepo.findByStatus("On Time")
                                .orElseThrow(() -> new Exception("Status flight invalid !!!"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Fetch and return flights
        return flightRepo.findByDeparture_CodeAndArrival_CodeAndStatus(departureAirport.getCode(), arrivalAirport.getCode(), flightStatusEntity).stream().map(FlightMapper::toFlightResponse).collect(Collectors.toList());
    }

    @Override
    public List<Flight> getFlights(String departure, String arrival) {

        // TRYING TO GET THE FLIGHTS
        List<Flight> flights = new ArrayList<>();
        flights = flightRepo.findByFlight(departure, arrival).orElse(null);
        if (flights == null) {
            return List.of();
        }
        return flights;
    }

    @Override
    public Flight updateFlight(String numberFlight, Flight flight) {
        // getting the flight
        Flight fl = flightRepo.findByFlightNumber(numberFlight).orElse(null);
        assert fl != null;
        fl.setDeparture(flight.getDeparture());
        fl.setArrival(flight.getArrival());
        fl.setDepartureTime(flight.getDepartureTime());
        fl.setArrivalTime(flight.getArrivalTime());
        fl.setPrice(flight.getPrice());
        fl.setAirline(flight.getAirline());
        fl.setAirplane(flight.getAirplane());
        fl.setStatus(flight.getStatus());
        return flightRepo.save(fl);

    }
}
