package com.uav.alarm.core;

/**
 * 气象快照 - 告警引擎用
 */
public class WeatherSnapshot {
    private double windSpeed;     // m/s
    private double windGust;      // m/s
    private int windDirection;    // 角度
    private double temperature;   // 摄氏度
    private double humidity;      // %
    private double visibility;    // 米
    private double precipitation; // mm/h
    private boolean thunderstorm; // 雷暴
    private String weatherCode;   // WMO 天气代码
    private long timestamp;

    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }
    public double getWindGust() { return windGust; }
    public void setWindGust(double windGust) { this.windGust = windGust; }
    public int getWindDirection() { return windDirection; }
    public void setWindDirection(int windDirection) { this.windDirection = windDirection; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public double getHumidity() { return humidity; }
    public void setHumidity(double humidity) { this.humidity = humidity; }
    public double getVisibility() { return visibility; }
    public void setVisibility(double visibility) { this.visibility = visibility; }
    public double getPrecipitation() { return precipitation; }
    public void setPrecipitation(double precipitation) { this.precipitation = precipitation; }
    public boolean isThunderstorm() { return thunderstorm; }
    public void setThunderstorm(boolean thunderstorm) { this.thunderstorm = thunderstorm; }
    public String getWeatherCode() { return weatherCode; }
    public void setWeatherCode(String weatherCode) { this.weatherCode = weatherCode; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
