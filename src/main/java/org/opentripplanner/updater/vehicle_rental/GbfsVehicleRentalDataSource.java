package org.opentripplanner.updater.vehicle_rental;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.vehicle_rental.VehicleRentalRegion;
import org.opentripplanner.routing.vehicle_rental.VehicleRentalStation;
import org.opentripplanner.updater.JsonConfigurable;
import org.opentripplanner.util.NonLocalizedString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Evan Siroky on 2019-05-20.
 */
public class GbfsVehicleRentalDataSource implements VehicleRentalDataSource, JsonConfigurable {

    private static final Logger LOG = LoggerFactory.getLogger(GbfsVehicleRentalDataSource.class);

    private GbfsStationDataSource stationInformationSource;  // station_information.json required by GBFS spec
    private GbfsStationStatusDataSource stationStatusSource; // station_status.json required by GBFS spec
    private GbfsFloatingVehicleDataSource floatingVehicleSource;   // free_vehicle_status.json declared OPTIONAL by GBFS spec

    private String baseUrl;

    private String networkName;

    public GbfsVehicleRentalDataSource(String networkName) {
        stationInformationSource = new GbfsStationDataSource();
        stationStatusSource = new GbfsStationStatusDataSource();
        floatingVehicleSource = new GbfsFloatingVehicleDataSource();
        if (networkName != null && !networkName.isEmpty()) {
            this.networkName = networkName;
        } else {
            this.networkName = "GBFS";
        }
    }

    public void setBaseUrl (String url) {
        baseUrl = url;
        if (!baseUrl.endsWith("/")) baseUrl += "/";
        stationInformationSource.setUrl(baseUrl + "station_information.json");
        stationStatusSource.setUrl(baseUrl + "station_status.json");
        floatingVehicleSource.setUrl(baseUrl + "free_bike_status.json");
    }

    @Override
    public void update() {
        // These first two GBFS files are required.
        // stationInformationSource.update(); // commented out temporarily because Lime has a random station listed
        // stationStatusSource.update(); // commented out temporarily because Lime has a random station listed
        // This floating-vehicles file is optional, and does not appear in all GBFS feeds.
        floatingVehicleSource.update();
    }

    @Override
    public boolean regionsUpdated() {
        return stationInformationSource.regionsUpdated() ||
            stationStatusSource.regionsUpdated() ||
            floatingVehicleSource.regionsUpdated();
    }

    @Override
    public boolean stationsUpdated() {
        return stationInformationSource.stationsUpdated() ||
            stationStatusSource.stationsUpdated() ||
            floatingVehicleSource.stationsUpdated();
    }

    @Override
    public List<VehicleRentalStation> getStations() {

        // Index all the station status entries on their station ID.
        Map<String, VehicleRentalStation> statusLookup = new HashMap<>();
        for (VehicleRentalStation station : stationStatusSource.getStations()) {
            statusLookup.put(station.id, station);
        }

        // Iterate over all known stations, and if we have any status information add it to those station objects.
        for (VehicleRentalStation station : stationInformationSource.getStations()) {
            if (!statusLookup.containsKey(station.id)) continue;
            VehicleRentalStation status = statusLookup.get(station.id);
            station.vehiclesAvailable = status.vehiclesAvailable;
            station.spacesAvailable = status.spacesAvailable;
        }

        // Copy the full list of station objects (with status updates) into a List, appending the floating vehicle stations.
        List<VehicleRentalStation> stations = new LinkedList<>(stationInformationSource.getStations());
        stations.addAll(floatingVehicleSource.getStations());

        // Set identical network ID on all stations
        Set<String> networkIdSet = Sets.newHashSet(this.networkName);
        for (VehicleRentalStation station : stations) station.networks = networkIdSet;

        return stations;
    }

    @Override
    public List<VehicleRentalRegion> getRegions() {
        // TODO: implement
        return new ArrayList<>();
    }

    /**
     * Note that the JSON being passed in here is for configuration of the OTP component, it's completely separate
     * from the JSON coming in from the update source.
     */
    @Override
    public void configure (Graph graph, JsonNode jsonNode) {
        // path() returns MissingNode not null, allowing chained function calls.
        String url = jsonNode.path("url").asText();
        if (url == null) {
            throw new IllegalArgumentException("Missing mandatory 'url' configuration.");
        }
        this.setBaseUrl(url);
    }

    class GbfsStationDataSource extends GenericJsonVehicleRentalDataSource {

        public GbfsStationDataSource () {
            super("data/stations");
        }

        @Override
        public VehicleRentalStation makeStation(JsonNode stationNode) {
            VehicleRentalStation station = new VehicleRentalStation();
            station.id = stationNode.path("station_id").toString();
            station.x = stationNode.path("lon").asDouble();
            station.y = stationNode.path("lat").asDouble();
            station.name =  new NonLocalizedString(stationNode.path("name").asText());
            return station;
        }
    }

    class GbfsStationStatusDataSource extends GenericJsonVehicleRentalDataSource {

        public GbfsStationStatusDataSource () {
            super("data/stations");
        }

        @Override
        public VehicleRentalStation makeStation(JsonNode stationNode) {
            VehicleRentalStation station = new VehicleRentalStation();
            station.id = stationNode.path("station_id").toString();
            station.vehiclesAvailable = stationNode.path("num_bikes_available").asInt();
            station.spacesAvailable = stationNode.path("num_docks_available").asInt();
            return station;
        }
    }

    class GbfsFloatingVehicleDataSource extends GenericJsonVehicleRentalDataSource {

        public GbfsFloatingVehicleDataSource () {
            super("data/bikes");
        }

        @Override
        public VehicleRentalStation makeStation(JsonNode stationNode) {
            String vehicleType = stationNode.get("vehicle_type").asText();
            if (!"scooter".equals(vehicleType)) {
                return null;
            }
            VehicleRentalStation station = new VehicleRentalStation();
            station.id = stationNode.path("bike_id").asText();
            station.name = new NonLocalizedString(stationNode.path("bike_id").asText());
            station.x = stationNode.path("lon").asDouble();
            station.y = stationNode.path("lat").asDouble();
            station.vehiclesAvailable = 1;
            station.spacesAvailable = 0;
            station.allowDropoff = false;
            station.isFloatingVehicle = true;
            return station;
        }
    }
}
