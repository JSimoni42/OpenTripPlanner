package org.opentripplanner.api.common;

import org.opentripplanner.api.model.Place;
import org.opentripplanner.api.parameter.QualifiedMode;
import org.opentripplanner.api.parameter.QualifiedModeSet;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.error.TransportationNetworkCompanyAvailabilityException;
import org.opentripplanner.routing.request.BannedStopSet;
import org.opentripplanner.routing.transportation_network_company.ArrivalTime;
import org.opentripplanner.routing.transportation_network_company.TransportationNetworkCompanyService;
import org.opentripplanner.standalone.OTPServer;
import org.opentripplanner.standalone.Router;
import org.opentripplanner.util.ResourceBundleSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

/**
 * This class defines all the JAX-RS query parameters for a path search as fields, allowing them to 
 * be inherited by other REST resource classes (the trip planner and the Analyst WMS or tile 
 * resource). They will be properly included in API docs generated by Enunciate. This implies that
 * the concrete REST resource subclasses will be request-scoped rather than singleton-scoped.
 *
 * All defaults should be specified in the RoutingRequest, NOT as annotations on the query parameters.
 * JSON router configuration can then overwrite those built-in defaults, and only the fields of the resulting prototype
 * routing request for which query parameters are found are overwritten here. This establishes a priority chain:
 * RoutingRequest field initializers, then JSON router config, then query parameters.
 *
 * @author abyrd
 */
public abstract class RoutingResource { 

    private static final Logger LOG = LoggerFactory.getLogger(RoutingResource.class);

    /**
     * The routerId selects between several graphs on the same server. The routerId is pulled from
     * the path, not the query parameters. However, the class RoutingResource is not annotated with
     * a path because we don't want it to be instantiated as an endpoint. Instead, the {routerId}
     * path parameter should be included in the path annotations of all its subclasses.
     */
    @PathParam("routerId") 
    public String routerId;

    /** The start location -- either latitude, longitude pair in degrees or a Vertex
     *  label. For example, <code>40.714476,-74.005966</code> or
     *  <code>mtanyctsubway_A27_S</code>.  */
    @QueryParam("fromPlace")
    protected String fromPlace;

    /** The end location (see fromPlace for format). */
    @QueryParam("toPlace")
    protected String toPlace;

    /** An ordered list of intermediate locations to be visited (see the fromPlace for format). Parameter can be specified multiple times. */
    @QueryParam("intermediatePlaces")
    protected List<String> intermediatePlaces;

    /** The date that the trip should depart (or arrive, for requests where arriveBy is true). */
    @QueryParam("date")
    protected String date;
    
    /** The time that the trip should depart (or arrive, for requests where arriveBy is true). */
    @QueryParam("time")
    protected String time;
    
    /** Whether the trip should depart or arrive at the specified date and time. */
    @QueryParam("arriveBy")
    protected Boolean arriveBy;
    
    /** Whether the trip must be wheelchair accessible. */
    @QueryParam("wheelchair")
    protected Boolean wheelchair;

    /** The maximum distance (in meters) the user is willing to walk. Defaults to unlimited. */
    @QueryParam("maxWalkDistance")
    protected Double maxWalkDistance;

    /**
     * The maximum time (in seconds) of pre-transit travel when using drive-to-transit (park and
     * ride or kiss and ride). Defaults to unlimited.
     */
    @QueryParam("maxPreTransitTime")
    protected Integer maxPreTransitTime;

    /**
     * A multiplier for how bad walking is, compared to being in transit for equal lengths of time.
     * Defaults to 2. Empirically, values between 10 and 20 seem to correspond well to the concept
     * of not wanting to walk too much without asking for totally ridiculous itineraries, but this
     * observation should in no way be taken as scientific or definitive. Your mileage may vary.
     */
    @QueryParam("walkReluctance")
    protected Double walkReluctance;

    /**
     * How much worse is waiting for a transit vehicle than being on a transit vehicle, as a
     * multiplier. The default value treats wait and on-vehicle time as the same.
     *
     * It may be tempting to set this higher than walkReluctance (as studies often find this kind of
     * preferences among riders) but the planner will take this literally and walk down a transit
     * line to avoid waiting at a stop. This used to be set less than 1 (0.95) which would make
     * waiting offboard preferable to waiting onboard in an interlined trip. That is also
     * undesirable.
     *
     * If we only tried the shortest possible transfer at each stop to neighboring stop patterns,
     * this problem could disappear.
     */
    @QueryParam("waitReluctance")
    protected Double waitReluctance;

    /** How much less bad is waiting at the beginning of the trip (replaces waitReluctance) */
    @QueryParam("waitAtBeginningFactor")
    protected Double waitAtBeginningFactor;

    /** The user's walking speed in meters/second. Defaults to approximately 3 MPH. */
    @QueryParam("walkSpeed")
    protected Double walkSpeed;

    /** The user's biking speed in meters/second. Defaults to approximately 11 MPH, or 9.5 for bikeshare. */
    @QueryParam("bikeSpeed")
    protected Double bikeSpeed;

    /** The time it takes the user to fetch their bike and park it again in seconds.
     *  Defaults to 0. */
    @QueryParam("bikeSwitchTime")
    protected Integer bikeSwitchTime;

    /** The cost of the user fetching their bike and parking it again.
     *  Defaults to 0. */
    @QueryParam("bikeSwitchCost")
    protected Integer bikeSwitchCost;

    /** For bike triangle routing, how much safety matters (range 0-1). */
    @QueryParam("triangleSafetyFactor")
    protected Double triangleSafetyFactor;
    
    /** For bike triangle routing, how much slope matters (range 0-1). */
    @QueryParam("triangleSlopeFactor")
    protected Double triangleSlopeFactor;
    
    /** For bike triangle routing, how much time matters (range 0-1). */            
    @QueryParam("triangleTimeFactor")
    protected Double triangleTimeFactor;

    /** The set of characteristics that the user wants to optimize for. @See OptimizeType */
    @QueryParam("optimize")
    protected OptimizeType optimize;
    
    /**
     * <p>The set of modes that a user is willing to use, with qualifiers stating whether vehicles should be parked, rented, etc.</p>
     * <p>The possible values of the comma-separated list are:</p>
     *
     * <ul>
     *  <li>WALK</li>
     *  <li>TRANSIT: General catch-all for all public transport modes.</li>
     *  <li>BICYCLE: Taking a bicycle onto the public transport and cycling from the arrival station to the destination.</li>
     *  <li>BICYCLE_RENT: Taking a rented, shared-mobility bike for part or the entirety of the route.
     *      <br>
     *      <em>Prerequisite:</em> Vehicle positions need to be added to OTP either as static stations or dynamic data feeds.
     *      For static stations check the graph building documentation for the property <code>staticBikeRental</code>.
     *  </li>
     *  <li>BICYCLE_PARK: Leaving the bicycle at the departure station and walking from the arrival station to the destination.
     *      <br>
     *      <em>Prerequisite:</em> Bicycle parking stations near the station and visible to OTP by enabling the property <code>staticBikeParkAndRide</code>
     *      during graph build.
     *   </li>
     *  <li>CAR</li>
     *  <li>CAR_PARK: Driving a car to the park-and-ride facilities near a station and taking public transport.
     *      <br>
     *      <em>Prerequisite:</em> Park-and-ride areas near the station and visible to OTP by enabling the property <code>staticParkAndRide</code>
     *      during graph build.
     *  </li>
     *  <li>TRAM</li>
     *  <li>SUBWAY</li>
     *  <li>RAIL</li>
     *  <li>BUS</li>
     *  <li>FERRY</li>
     *  <li>CABLE_CAR</li>
     *  <li>GONDOLA</li>
     *  <li>AIRPLANE</li>
     * </ul>
     */
    @QueryParam("mode")
    protected QualifiedModeSet modes;

    /** The minimum time, in seconds, between successive trips on different vehicles.
     *  This is designed to allow for imperfect schedule adherence.  This is a minimum;
     *  transfers over longer distances might use a longer time. */
    @QueryParam("minTransferTime")
    protected Integer minTransferTime;

    /** The maximum number of possible itineraries to return. */
    @QueryParam("numItineraries")
    protected Integer numItineraries;

    /**
     * The list of preferred routes. The format is agency_[routename][_routeid], so TriMet_100 (100 is route short name)
     * or Trimet__42 (two underscores, 42 is the route internal ID).
     */
    @QueryParam("preferredRoutes")
    protected String preferredRoutes;

    /** Penalty added for using every route that is not preferred if user set any route as preferred, i.e. number of seconds that we are willing
     * to wait for preferred route. */
    @QueryParam("otherThanPreferredRoutesPenalty")
    protected Integer otherThanPreferredRoutesPenalty;
    
    /** The comma-separated list of preferred agencies. */
    @QueryParam("preferredAgencies")
    protected String preferredAgencies;
    
    /**
     * The list of unpreferred routes. The format is agency_[routename][_routeid], so TriMet_100 (100 is route short name) or Trimet__42 (two
     * underscores, 42 is the route internal ID).
     */
    @QueryParam("unpreferredRoutes")
    protected String unpreferredRoutes;
    
    /** The comma-separated list of unpreferred agencies. */
    @QueryParam("unpreferredAgencies")
    protected String unpreferredAgencies;

    /** Whether intermediate stops -- those that the itinerary passes in a vehicle, but 
     *  does not board or alight at -- should be returned in the response.  For example,
     *  on a Q train trip from Prospect Park to DeKalb Avenue, whether 7th Avenue and
     *  Atlantic Avenue should be included. */
    @QueryParam("showIntermediateStops")
    protected Boolean showIntermediateStops;

    /**
     * Prevents unnecessary transfers by adding a cost for boarding a vehicle. This is the cost that
     * is used when boarding while walking.
     */
    @QueryParam("walkBoardCost")
    protected Integer walkBoardCost;
    
    /**
     * Prevents unnecessary transfers by adding a cost for boarding a vehicle. This is the cost that
     * is used when boarding while cycling. This is usually higher that walkBoardCost.
     */
    @QueryParam("bikeBoardCost")
    protected Integer bikeBoardCost;
    
    /**
     * The comma-separated list of banned routes. The format is agency_[routename][_routeid], so TriMet_100 (100 is route short name) or Trimet__42
     * (two underscores, 42 is the route internal ID).
     */
    @QueryParam("bannedRoutes")
    protected String bannedRoutes;

    /**
     * Functions the same as bannnedRoutes, except only the listed routes are allowed.
     */
    @QueryParam("whiteListedRoutes")
    protected String whiteListedRoutes;
    
    /** The comma-separated list of banned agencies. */
    @QueryParam("bannedAgencies")
    protected String bannedAgencies;

    /**
     * Functions the same as banned agencies, except only the listed agencies are allowed.
     */
    @QueryParam("whiteListedAgencies")
    protected String whiteListedAgencies;
    
    /** The comma-separated list of banned trips.  The format is agency_trip[:stop*], so:
     * TriMet_24601 or TriMet_24601:0:1:2:17:18:19
     */
    @QueryParam("bannedTrips")
    protected String bannedTrips;

    /** A comma-separated list of banned stops. A stop is banned by ignoring its 
     * pre-board and pre-alight edges. This means the stop will be reachable via the
     * street network. Also, it is still possible to travel through the stop. Just
     * boarding and alighting is prohibited.
     * The format is agencyId_stopId, so: TriMet_2107
     */
    @QueryParam("bannedStops")
    protected String bannedStops;
    
    /** A comma-separated list of banned stops. A stop is banned by ignoring its 
     * pre-board and pre-alight edges. This means the stop will be reachable via the
     * street network. It is not possible to travel through the stop.
     * For example, this parameter can be used when a train station is destroyed, such
     * that no trains can drive through the station anymore.
     * The format is agencyId_stopId, so: TriMet_2107
     */
    @QueryParam("bannedStopsHard")
    protected String bannedStopsHard;
    
    /**
     * An additional penalty added to boardings after the first.  The value is in OTP's
     * internal weight units, which are roughly equivalent to seconds.  Set this to a high
     * value to discourage transfers.  Of course, transfers that save significant
     * time or walking will still be taken.
     */
    @QueryParam("transferPenalty")
    protected Integer transferPenalty;
    
    /**
     * An additional penalty added to boardings after the first when the transfer is not
     * preferred. Preferred transfers also include timed transfers. The value is in OTP's
     * internal weight units, which are roughly equivalent to seconds. Set this to a high
     * value to discourage transfers that are not preferred. Of course, transfers that save
     * significant time or walking will still be taken.
     * When no preferred or timed transfer is defined, this value is ignored.
     */
    @QueryParam("nonpreferredTransferPenalty")
    protected Integer nonpreferredTransferPenalty;
    
    /** The maximum number of transfers (that is, one plus the maximum number of boardings)
     *  that a trip will be allowed.  Larger values will slow performance, but could give
     *  better routes.  This is limited on the server side by the MAX_TRANSFERS value in
     *  org.opentripplanner.api.ws.Planner. */
    @QueryParam("maxTransfers")
    protected Integer maxTransfers;

    /** If true, goal direction is turned off and a full path tree is built (specify only once) */
    @QueryParam("batch")
    protected Boolean batch;

    /** A transit stop required to be the first stop in the search (AgencyId_StopId) */
    @QueryParam("startTransitStopId")
    protected String startTransitStopId;

    /** A transit trip acting as a starting "state" for depart-onboard routing (AgencyId_TripId) */
    @QueryParam("startTransitTripId")
    protected String startTransitTripId;

    /**
     * When subtracting initial wait time, do not subtract more than this value, to prevent overly
     * optimistic trips. Reasoning is that it is reasonable to delay a trip start 15 minutes to 
     * make a better trip, but that it is not reasonable to delay a trip start 15 hours; if that
     * is to be done, the time needs to be included in the trip time. This number depends on the
     * transit system; for transit systems where trips are planned around the vehicles, this number
     * can be much higher. For instance, it's perfectly reasonable to delay one's trip 12 hours if
     * one is taking a cross-country Amtrak train from Emeryville to Chicago. Has no effect in
     * stock OTP, only in Analyst.
     *
     * A value of 0 means that initial wait time will not be subtracted out (will be clamped to 0).
     * A value of -1 (the default) means that clamping is disabled, so any amount of initial wait 
     * time will be subtracted out.
     */
    @QueryParam("clampInitialWait")
    protected Long clampInitialWait;

    /**
     * If true, this trip will be reverse-optimized on the fly. Otherwise, reverse-optimization
     * will occur once a trip has been chosen (in Analyst, it will not be done at all).
     */
    @QueryParam("reverseOptimizeOnTheFly")
    protected Boolean reverseOptimizeOnTheFly;
        
    @QueryParam("boardSlack")
    private Integer boardSlack;
    
    @QueryParam("alightSlack")
    private Integer alightSlack;

    @QueryParam("locale")
    private String locale;

    /**
     * If true, realtime updates are ignored during this search.
     */
    @QueryParam("ignoreRealtimeUpdates")
    protected Boolean ignoreRealtimeUpdates;

    /**
     * If true, the remaining weight heuristic is disabled. Currently only implemented for the long
     * distance path service.
     */
    @QueryParam("disableRemainingWeightHeuristic")
    protected Boolean disableRemainingWeightHeuristic;

    @QueryParam("maxHours")
    private Double maxHours;

    @QueryParam("useRequestedDateTimeInMaxHours")
    private Boolean useRequestedDateTimeInMaxHours;

    @QueryParam("disableAlertFiltering")
    private Boolean disableAlertFiltering;

    /**
     * If true, the Graph's ellipsoidToGeoidDifference is applied to all elevations returned by this query.
     */
    @QueryParam("geoidElevation")
    private Boolean geoidElevation;

    /*
     * A comma separated list of TNC companies to use in the routing request
     */
    @QueryParam("companies")
    protected String companies;

    /**
     * If request date is invalid, apply the provided strategy to come up with a valid date.
     */
    @QueryParam("invalidDateStrategy")
    protected String invalidDateStrategy;

    @QueryParam("minTransitDistance")
    private String minTransitDistance;

    @QueryParam("searchTimeout")
    protected Long searchTimeout;

    /**
     * Set the method of sorting itineraries in the response. Right now, the only supported value is "duration";
     * otherwise it uses default sorting. More sorting methods may be added in the future.
     */
    @QueryParam("pathComparator")
    private String pathComparator;

    @QueryParam("onlyTransitTrips")
    private Boolean onlyTransitTrips;

    /**
     * The amount of watts a Micromobility vehicle can sustainably output
     */
    @QueryParam("watts")
    private Double watts;

    /**
     * The weight of the Micromobility vehicle and all things transported by the vehicle including the rider
     */
    @QueryParam("weight")
    private Double weight;

    /**
     * The minimum speed of a personal micromobility vehicle. This should only be used to avoid unreasonably slow times
     * on hills. If it is desired to model effectively impossible travel uphill (ie the vehicle can't reasonably be
     * transported up a steep enough grade) enter 0. Value in m/s. If this parameter is not provided, a default of
     * 0.8 m/s is set in the RoutingRequest class.
     * TODO: A future refactor of the code will update StateData data with this value if using a personal micromobility
     *   vehicle or with data describing the rental vehicle characteristics.
     */
    @QueryParam("minimumMicromobilitySpeed")
    private Double minimumMicromobilitySpeed;

    /**
     * The maximum speed of a personal micromobility vehicle. This will cap all speeds on declines to this value even if
     * the physics of the downslope would naturally result in the vehicle traveling faster than this value (ie, the user
     * or the vehicle itself is assumed to be braking). Value in m/s. If this parameter is not provided, a default of
     * 5 m/s is set in the RoutingRequest class.
     * TODO: A future refactor of the code will update StateData data with this value if using a personal micromobility
     *   vehicle or with data describing the rental vehicle characteristics.
     */
    @QueryParam("maximumMicromobilitySpeed")
    private Double maximumMicromobilitySpeed;

    /* 
     * somewhat ugly bug fix: the graphService is only needed here for fetching per-graph time zones. 
     * this should ideally be done when setting the routing context, but at present departure/
     * arrival time is stored in the request as an epoch time with the TZ already resolved, and other
     * code depends on this behavior. (AMB)
     * Alternatively, we could eliminate the separate RoutingRequest objects and just resolve
     * vertices and timezones here right away, but just ignore them in semantic equality checks.
     */
    @Context
    protected OTPServer otpServer;

    /**
     * Range/sanity check the query parameter fields and build a Request object from them.
     *
     * @throws ParameterException when there is a problem interpreting a query parameter
     */
    protected RoutingRequest buildRequest() throws ParameterException {
        Router router = otpServer.getRouter(routerId);
        RoutingRequest request = router.defaultRoutingRequest.clone();
        request.routerId = routerId;
        // The routing request should already contain defaults, which are set when it is initialized or in the JSON
        // router configuration and cloned. We check whether each parameter was supplied before overwriting the default.
        if (fromPlace != null)
            request.setFromString(fromPlace);

        if (toPlace != null)
            request.setToString(toPlace);

        // NOTE: This query parameter dictates how OTP handles a bad date/time value. It must be set on the request
        // before setDateTime is called, otherwise the strategy will not be applied.
        if (invalidDateStrategy != null)
            request.invalidDateStrategy = invalidDateStrategy;

        {
            //FIXME: move into setter method on routing request
            TimeZone tz;
            tz = router.graph.getTimeZone();
            if (date == null && time != null) { // Time was provided but not date
                LOG.debug("parsing ISO datetime {}", time);
                try {
                    // If the time query param doesn't specify a timezone, use the graph's default. See issue #1373.
                    DatatypeFactory df = javax.xml.datatype.DatatypeFactory.newInstance();
                    XMLGregorianCalendar xmlGregCal = df.newXMLGregorianCalendar(time);
                    GregorianCalendar gregCal = xmlGregCal.toGregorianCalendar();
                    if (xmlGregCal.getTimezone() == DatatypeConstants.FIELD_UNDEFINED) {
                        gregCal.setTimeZone(tz);
                    }
                    Date d2 = gregCal.getTime();
                    request.setDateTime(d2);
                } catch (DatatypeConfigurationException e) {
                    request.setDateTime(date, time, tz);
                }
            } else {
                request.setDateTime(date, time, tz);
            }
        }

        if (wheelchair != null)
            request.setWheelchairAccessible(wheelchair);

        if (numItineraries != null)
            request.setNumItineraries(numItineraries);

        if (maxWalkDistance != null) {
            request.setMaxWalkDistance(maxWalkDistance);
            request.maxTransferWalkDistance = maxWalkDistance;
        }

        if (maxPreTransitTime != null)
            request.setMaxPreTransitTime(maxPreTransitTime);

        if (walkReluctance != null)
            request.setWalkReluctance(walkReluctance);

        if (waitReluctance != null)
            request.setWaitReluctance(waitReluctance);

        if (waitAtBeginningFactor != null)
            request.setWaitAtBeginningFactor(waitAtBeginningFactor);

        if (walkSpeed != null)
            request.walkSpeed = walkSpeed;

        if (bikeSpeed != null)
            request.bikeSpeed = bikeSpeed;

        if (bikeSwitchTime != null)
            request.bikeSwitchTime = bikeSwitchTime;

        if (bikeSwitchCost != null)
            request.bikeSwitchCost = bikeSwitchCost;

        if (optimize != null) {
            // Optimize types are basically combined presets of routing parameters, except for triangle
            request.setOptimize(optimize);
            if (optimize == OptimizeType.TRIANGLE) {
                if (triangleSafetyFactor == null || triangleSlopeFactor == null || triangleTimeFactor == null) {
                    throw new ParameterException(Message.UNDERSPECIFIED_TRIANGLE);
                }
                if (triangleSafetyFactor == null && triangleSlopeFactor == null && triangleTimeFactor == null) {
                    throw new ParameterException(Message.TRIANGLE_VALUES_NOT_SET);
                }
                // FIXME couldn't this be simplified by only specifying TWO of the values?
                if (Math.abs(triangleSafetyFactor+ triangleSlopeFactor + triangleTimeFactor - 1) > Math.ulp(1) * 3) {
                    throw new ParameterException(Message.TRIANGLE_NOT_AFFINE);
                }
                request.setTriangleSafetyFactor(triangleSafetyFactor);
                request.setTriangleSlopeFactor(triangleSlopeFactor);
                request.setTriangleTimeFactor(triangleTimeFactor);
            }
        }

        if (arriveBy != null)
            request.setArriveBy(arriveBy);

        if (showIntermediateStops != null)
            request.showIntermediateStops = showIntermediateStops;

        if (intermediatePlaces != null)
            request.setIntermediatePlacesFromStrings(intermediatePlaces);

        if (preferredRoutes != null)
            request.setPreferredRoutes(preferredRoutes);

        if (otherThanPreferredRoutesPenalty != null)
            request.setOtherThanPreferredRoutesPenalty(otherThanPreferredRoutesPenalty);

        if (preferredAgencies != null)
            request.setPreferredAgencies(preferredAgencies);

        if (unpreferredRoutes != null)
            request.setUnpreferredRoutes(unpreferredRoutes);

        if (unpreferredAgencies != null)
            request.setUnpreferredAgencies(unpreferredAgencies);

        if (walkBoardCost != null)
            request.setWalkBoardCost(walkBoardCost);

        if (bikeBoardCost != null)
            request.setBikeBoardCost(bikeBoardCost);

        if (bannedRoutes != null)
            request.setBannedRoutes(bannedRoutes);

        if (whiteListedRoutes != null)
            request.setWhiteListedRoutes(whiteListedRoutes);

        if (bannedAgencies != null)
            request.setBannedAgencies(bannedAgencies);

        if (whiteListedAgencies != null)
            request.setWhiteListedAgencies(whiteListedAgencies);

        HashMap<FeedScopedId, BannedStopSet> bannedTripMap = makeBannedTripMap(bannedTrips);
      
        if (bannedTripMap != null)
            request.bannedTrips = bannedTripMap;

        if (bannedStops != null)
            request.setBannedStops(bannedStops);

        if (bannedStopsHard != null)
            request.setBannedStopsHard(bannedStopsHard);
        
        // The "Least transfers" optimization is accomplished via an increased transfer penalty.
        // See comment on RoutingRequest.transferPentalty.
        if (transferPenalty != null) request.transferPenalty = transferPenalty;
        if (optimize == OptimizeType.TRANSFERS) {
            optimize = OptimizeType.QUICK;
            request.transferPenalty += 1800;
        }

        if (batch != null)
            request.batch = batch;

        if (optimize != null)
            request.setOptimize(optimize);

        /* Temporary code to get bike/car parking and renting working. */
        if (modes != null) {
            modes.applyToRoutingRequest(request);
            request.setModes(request.modes);
        }

        if (request.allowBikeRental && bikeSpeed == null) {
            //slower bike speed for bike sharing, based on empirical evidence from DC.
            request.bikeSpeed = 4.3;
        }

        if (boardSlack != null)
            request.boardSlack = boardSlack;

        if (alightSlack != null)
            request.alightSlack = alightSlack;

        if (minTransferTime != null)
            request.transferSlack = minTransferTime; // TODO rename field in routingrequest

        if (nonpreferredTransferPenalty != null)
            request.nonpreferredTransferPenalty = nonpreferredTransferPenalty;

        if (request.boardSlack + request.alightSlack > request.transferSlack) {
            throw new RuntimeException("Invalid parameters: " +
                    "transfer slack must be greater than or equal to board slack plus alight slack");
        }

        if (maxTransfers != null)
            request.maxTransfers = maxTransfers;

        final long NOW_THRESHOLD_MILLIS = 15 * 60 * 60 * 1000;
        boolean tripPlannedForNow = Math.abs(request.getDateTime().getTime() - new Date().getTime()) < NOW_THRESHOLD_MILLIS;
        request.useBikeRentalAvailabilityInformation = (tripPlannedForNow); // TODO the same thing for GTFS-RT
        request.useCarRentalAvailabilityInformation = (tripPlannedForNow);
        request.useVehicleRentalAvailabilityInformation = (tripPlannedForNow);

        if (startTransitStopId != null && !startTransitStopId.isEmpty())
            request.startingTransitStopId = FeedScopedId.convertFromString(startTransitStopId);

        if (startTransitTripId != null && !startTransitTripId.isEmpty())
            request.startingTransitTripId = FeedScopedId.convertFromString(startTransitTripId);

        if (clampInitialWait != null)
            request.clampInitialWait = clampInitialWait;

        if (reverseOptimizeOnTheFly != null)
            request.reverseOptimizeOnTheFly = reverseOptimizeOnTheFly;

        if (ignoreRealtimeUpdates != null)
            request.ignoreRealtimeUpdates = ignoreRealtimeUpdates;

        if (disableRemainingWeightHeuristic != null)
            request.disableRemainingWeightHeuristic = disableRemainingWeightHeuristic;

        if (maxHours != null)
            request.maxHours = maxHours;

        if (useRequestedDateTimeInMaxHours != null)
            request.useRequestedDateTimeInMaxHours = useRequestedDateTimeInMaxHours;

        if (disableAlertFiltering != null)
            request.disableAlertFiltering = disableAlertFiltering;

        if (geoidElevation != null)
            request.geoidElevation = geoidElevation;

        if (minTransitDistance != null)
            request.minTransitDistance = minTransitDistance;

        if (searchTimeout != null)
            request.searchTimeout = searchTimeout;

        // If using Transportation Network Companies, make sure service exists at origin.
        // This is not a future-proof solution as TNC coverage areas could be different in the future.  For example, a
        // trip planned months in advance may not take into account a TNC company deciding to no longer provide service
        // on that particular date in the future.  The current ETA estimate is only valid for perhaps 30 minutes into
        // the future.
        //
        // Also, if "depart at" and leaving soonish, save earliest departure time for use later use when boarding the
        // first TNC before transit.  (See StateEditor.boardHailedCar)
        if (this.modes != null && this.modes.qModes.contains(new QualifiedMode("CAR_HAIL"))) {
            if (companies == null) {
                throw new ParameterException(Message.TRANSPORTATION_NETWORK_COMPANY_REQUEST_INVALID);
            }

            request.companies = companies;

            TransportationNetworkCompanyService service =
                router.graph.getService(TransportationNetworkCompanyService.class);
            if (service == null) {
                LOG.error("Unconfigured Transportation Network Company service for router with id: " + routerId);
                throw new ParameterException(Message.TRANSPORTATION_NETWORK_COMPANY_CONFIG_INVALID);
            }

            List<ArrivalTime> arrivalEstimates;

            try {
                arrivalEstimates = service.getArrivalTimes(
                    companies,
                    new Place(
                        request.from.lng,
                        request.from.lat,
                        request.from.name
                    )
                );
            } catch (Exception e) {
                e.printStackTrace();
                throw new UnsupportedOperationException(
                    "Unable to verify availability of Transportation Network Company service due to error: " +
                        e.getMessage()
                );
            }

            /**
             * iterate through results and find earliest ETA of an acceptable ride type
             * this also checks if any of the ride types are wheelchair accessible or not
             * if the request requires a wheelchair accessible ride and no arrival estimates are
             * found, then the TransportationNetworkCompanyAvailabilityException will be thrown.
             */
            int earliestEta = Integer.MAX_VALUE;
            for (ArrivalTime arrivalEstimate : arrivalEstimates) {
                if (
                    arrivalEstimate.estimatedSeconds < earliestEta &&
                        request.wheelchairAccessible == arrivalEstimate.wheelchairAccessible
                ) {
                    earliestEta = arrivalEstimate.estimatedSeconds;
                }
            }

            if (earliestEta == Integer.MAX_VALUE) {
                // no acceptable ride types found
                throw new TransportationNetworkCompanyAvailabilityException();
            }

            // store the earliest ETA if planning a "depart at" trip that begins soonish (within + or - 30 minutes)
            long now = (new Date()).getTime() / 1000;
            long departureTimeWindow = 1800;
            if (
                this.arriveBy == false &&
                    request.dateTime < now + departureTimeWindow &&
                    request.dateTime > now - departureTimeWindow
            ) {
                request.transportationNetworkCompanyEtaAtOrigin = earliestEta;
            }
        }

        if (
            companies != null &&
                this.modes != null &&
                (this.modes.qModes.contains(new QualifiedMode("CAR_RENT")) ||
                    this.modes.qModes.contains(new QualifiedMode("MICROMOBILITY_RENT")))
        ) {
            request.companies = companies;
        }

        if (pathComparator != null)
            request.pathComparator = pathComparator;

        if (onlyTransitTrips != null)
            request.onlyTransitTrips = onlyTransitTrips;

        if (watts != null)
            request.watts = watts;

        if (weight != null)
            request.weight = weight;

        if (minimumMicromobilitySpeed != null)
            request.minimumMicromobilitySpeed = minimumMicromobilitySpeed;

        if (maximumMicromobilitySpeed != null)
            request.maximumMicromobilitySpeed = maximumMicromobilitySpeed;

        //getLocale function returns defaultLocale if locale is null
        request.locale = ResourceBundleSingleton.INSTANCE.getLocale(locale);
        return request;
    }

    /**
     * Take a string in the format agency:id or agency:id:1:2:3:4.
     * TODO Improve Javadoc. What does this even mean? Why are there so many colons and numbers?
     * Convert to a Map from trip --> set of int.
     */
    private HashMap<FeedScopedId, BannedStopSet> makeBannedTripMap(String banned) {
        if (banned == null) {
            return null;
        }
        
        HashMap<FeedScopedId, BannedStopSet> bannedTripMap = new HashMap<FeedScopedId, BannedStopSet>();
        String[] tripStrings = banned.split(",");
        for (String tripString : tripStrings) {
            // TODO this apparently allows banning stops within a trip with integers. Why?
            String[] parts = tripString.split(":");
            if (parts.length < 2) continue; // throw exception?
            String agencyIdString = parts[0];
            String tripIdString = parts[1];
            FeedScopedId tripId = new FeedScopedId(agencyIdString, tripIdString);
            BannedStopSet bannedStops;
            if (parts.length == 2) {
                bannedStops = BannedStopSet.ALL;
            } else {
                bannedStops = new BannedStopSet();
                for (int i = 2; i < parts.length; ++i) {
                    bannedStops.add(Integer.parseInt(parts[i]));
                }
            }
            bannedTripMap.put(tripId, bannedStops);
        }
        return bannedTripMap;
    }

}
