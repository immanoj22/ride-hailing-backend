package com.ridehail.commonservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.commonservice.dto.GetEstimatedTimeRequest;
import com.ridehail.commonservice.dto.GetRouteDetailsRequest;
import com.ridehail.commonservice.dto.LocationDto;
import com.ridehail.commonservice.util.GoogleApiUrls;

/**
 * Direct port of controllers/geoLocation.controller.js. Index-based access into
 * {@code address_components} (streetname/locality/city/postalcode) is preserved exactly as the
 * original, fragile as it is - Google doesn't guarantee component ordering, but changing it would
 * change behaviour, and the brief is to preserve behaviour, not to fix pre-existing bugs.
 */
@Service
public class GeoLocationService {

    private static final Pattern TOLL_STEP_PATTERN = Pattern.compile("toll", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiKey;

    public GeoLocationService(@Value("${ridehail.google.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public Map<String, Object> getAddressByLatLng(double lat, double lng, String from) {
        String url = GoogleApiUrls.GET_ADDRESS + lat + " " + lng + "&key=" + apiKey;
        JsonNode data = restTemplate.getForObject(url, JsonNode.class);

        if (data == null || !"OK".equals(data.path("status").asText()) || !data.path("results").isArray()
                || data.path("results").isEmpty()) {
            throw new ApiException(400, "Unable to fetch address for the provided coordinates.");
        }

        JsonNode result = data.path("results").get(0);
        JsonNode components = result.path("address_components");

        Map<String, Object> droppingData = new LinkedHashMap<>();
        droppingData.put("coordinates", List.of(lat, lng));
        droppingData.put("streetname", longNameAt(components, 1));
        droppingData.put("locality", longNameAt(components, 2));
        droppingData.put("city", longNameAt(components, 3));
        droppingData.put("postalcode", longNameAt(components, 6));
        droppingData.put("formatedaddress", result.path("formatted_address").asText(null));

        String state = null;
        if (components.isArray()) {
            for (JsonNode component : components) {
                JsonNode types = component.path("types");
                if ((types.size() > 0 && "administrative_area_level_1".equals(types.get(0).asText()))
                        || (types.size() > 1 && "administrative_area_level_1".equals(types.get(1).asText()))) {
                    state = component.path("long_name").asText(null);
                    break;
                }
            }
        }

        if (from != null && !from.isBlank()) {
            Map<String, Object> stateResult = new LinkedHashMap<>();
            stateResult.put("state", state);
            return stateResult;
        }
        return droppingData;
    }

    public Map<String, Object> getEstimatedTime(GetEstimatedTimeRequest req) {
        try {
            String origin = req.getUserLocation().getLatitude() + "," + req.getUserLocation().getLongitude();
            String dest = req.getDestination().getLatitude() + "," + req.getDestination().getLongitude();

            String url = UriComponentsBuilder.fromHttpUrl(GoogleApiUrls.DIRECTIONS)
                    .queryParam("origin", origin)
                    .queryParam("destination", dest)
                    .queryParam("mode", "driving")
                    .queryParam("key", apiKey)
                    .toUriString();

            JsonNode data = restTemplate.getForObject(url, JsonNode.class);
            JsonNode routes = data == null ? null : data.path("routes");
            if (routes == null || !routes.isArray() || routes.isEmpty()
                    || !routes.get(0).path("legs").isArray() || routes.get(0).path("legs").isEmpty()) {
                throw new ApiException(400, "Unable to fetch address for the provided coordinates.");
            }

            JsonNode firstLeg = routes.get(0).path("legs").get(0);
            long duration = firstLeg.path("duration").path("value").asLong();
            long isToll = countOccurrences(routes.toString().toLowerCase(), "toll road");

            List<String> states = new ArrayList<>();
            if (Boolean.TRUE.equals(req.getDesiredLocation())) {
                String pickupState = normalizeState(getAddressByLatLng(req.getUserLocation().getLatitude(),
                        req.getUserLocation().getLongitude(), "state").get("state"));
                String dropState = normalizeState(getAddressByLatLng(req.getDestination().getLatitude(),
                        req.getDestination().getLongitude(), "state").get("state"));

                if (!pickupState.equals(dropState)) {
                    Set<String> uniqueStates = new LinkedHashSet<>();
                    for (JsonNode route : routes) {
                        for (JsonNode leg : route.path("legs")) {
                            for (JsonNode step : leg.path("steps")) {
                                JsonNode start = step.path("start_location");
                                String stepState = normalizeState(getAddressByLatLng(
                                        start.path("lat").asDouble(), start.path("lng").asDouble(), "state").get("state"));
                                uniqueStates.add(stepState);
                            }
                        }
                    }
                    uniqueStates.remove(pickupState);
                    states = new ArrayList<>(uniqueStates);
                }
            }

            Map<String, Object> resData = new LinkedHashMap<>();
            if ("user".equals(req.getFrom())) {
                resData.put("duration", duration);
                resData.put("distance", firstLeg.path("distance").path("value").asDouble() / 1000);
                resData.put("isToll", isToll);
                resData.put("states", states);
            } else {
                resData.put("duration", duration);
            }
            return resData;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(400, "Unable to fetch address for the provided coordinates.");
        }
    }

    public Map<String, Object> getRouteDetails(GetRouteDetailsRequest req) {
        try {
            LocationDto userLocation = req.getUserLocation();
            LocationDto destination = req.getDestination();
            boolean isTwoWay = Boolean.TRUE.equals(req.getIsTwoWay());

            String origin = userLocation.getLatitude() + "," + userLocation.getLongitude();
            String drop = destination.getLatitude() + "," + destination.getLongitude();
            String finalDestination = isTwoWay ? origin : drop;

            String waypoints = null;
            List<LocationDto> multi = req.getMulti();
            if (multi != null && !multi.isEmpty()) {
                List<String> points = new ArrayList<>();
                for (LocationDto p : multi) points.add(p.getLatitude() + "," + p.getLongitude());
                if (isTwoWay) points.add(drop);
                waypoints = "optimize:true|" + String.join("|", points);
            } else if (isTwoWay) {
                waypoints = "optimize:true|" + drop;
            }

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(GoogleApiUrls.DIRECTIONS)
                    .queryParam("origin", origin)
                    .queryParam("destination", finalDestination)
                    .queryParam("mode", "driving")
                    .queryParam("key", apiKey);
            if (waypoints != null) {
                builder.queryParam("waypoints", waypoints);
            }

            JsonNode data = restTemplate.getForObject(builder.toUriString(), JsonNode.class);
            JsonNode routes = data == null ? null : data.path("routes");
            if (routes == null || !routes.isArray() || routes.isEmpty()) {
                return Map.of("status", true, "message", "No datas found.");
            }

            JsonNode route = routes.get(0);
            JsonNode legs = route.path("legs");

            long totalDuration = 0;
            long totalMeters = 0;
            long tollStepCount = 0;
            for (JsonNode leg : legs) {
                totalDuration += leg.path("duration").path("value").asLong();
                totalMeters += leg.path("distance").path("value").asLong();
                for (JsonNode step : leg.path("steps")) {
                    if (TOLL_STEP_PATTERN.matcher(step.path("html_instructions").asText("")).find()) {
                        tollStepCount++;
                    }
                }
            }
            double distanceKm = Math.round((totalMeters / 1000.0) * 100) / 100.0;
            long isToll = route.path("routeWarnings").size() + tollStepCount;

            List<String> states = new ArrayList<>();
            if (Boolean.TRUE.equals(req.getDesiredLocation())) {
                String pickupState = normalizeState(getAddressByLatLng(userLocation.getLatitude(), userLocation.getLongitude(), "state").get("state"));

                Map<String, JsonNode> uniqueCoords = new LinkedHashMap<>();
                for (JsonNode leg : legs) {
                    JsonNode start = leg.path("start_location");
                    JsonNode end = leg.path("end_location");
                    uniqueCoords.put(start.path("lat").asText() + "," + start.path("lng").asText(), start);
                    uniqueCoords.put(end.path("lat").asText() + "," + end.path("lng").asText(), end);
                }

                Set<String> stateSet = new LinkedHashSet<>();
                for (JsonNode loc : uniqueCoords.values()) {
                    String state = normalizeState(getAddressByLatLng(loc.path("lat").asDouble(), loc.path("lng").asDouble(), "state").get("state"));
                    if (!state.isEmpty() && !state.equals(pickupState)) {
                        stateSet.add(state);
                    }
                }
                states = new ArrayList<>(stateSet);
            }

            Map<String, Object> resData = new LinkedHashMap<>();
            if ("user".equals(req.getFrom())) {
                resData.put("duration", totalDuration);
                resData.put("distance", distanceKm);
                resData.put("isToll", isToll);
                resData.put("states", states);
            } else {
                resData.put("totalDuration", totalDuration);
            }
            return resData;
        } catch (Exception e) {
            throw new ApiException(400, "Unable to fetch address for the provided coordinates.");
        }
    }

    private String longNameAt(JsonNode components, int index) {
        if (components == null || !components.isArray() || components.size() <= index) {
            return null;
        }
        JsonNode node = components.get(index).path("long_name");
        return node.isMissingNode() ? null : node.asText(null);
    }

    private String normalizeState(Object state) {
        return String.valueOf(state).replaceAll("\\s", "").toLowerCase();
    }

    private long countOccurrences(String haystack, String needle) {
        if (haystack.isEmpty()) return 0;
        long count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
