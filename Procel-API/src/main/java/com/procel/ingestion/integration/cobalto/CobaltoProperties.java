package com.procel.ingestion.integration.cobalto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "procel.cobalto.rooms")
public class CobaltoProperties {

    private String url;
    private String scheduleUrl;
    private int timeoutMs = 10000;
    private String phpSessid;
    private Integer pageSize = 800;

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getScheduleUrl() { return scheduleUrl; }
    public void setScheduleUrl(String scheduleUrl) { this.scheduleUrl = scheduleUrl; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public String getPhpSessid() { return phpSessid; }
    public void setPhpSessid(String phpSessid) { this.phpSessid = phpSessid; }
}
