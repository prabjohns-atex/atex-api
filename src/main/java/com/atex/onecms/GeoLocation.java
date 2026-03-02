package com.atex.onecms;

/**
 * Bean class representing a geographical location by latitude /
 * longitude coordinates along with a descriptive label.
 */
public class GeoLocation {
    private double latitude;
    private double longitude;
    private String label = null;

    public GeoLocation() {}

    public double getLatitude() { return latitude; }
    public void setLatitude(final double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(final double longitude) { this.longitude = longitude; }
    public String getLabel() { return label; }
    public void setLabel(final String label) { this.label = label; }

    @Override
    public String toString() {
        return String.format("GeoLocation [latitude: '%f', longitude: '%f', label: '%s']",
                latitude, longitude, label);
    }
}

