package com.procel.ingestion.integration.cobalto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "procel.cobalto.rooms")
public class CobaltoProperties {

    private String url;
    private int timeoutMs = 10000;
    private String phpSessid;
    private Integer pageSize = 1000;

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public String getPhpSessid() { return phpSessid; }
    public void setPhpSessid(String phpSessid) { this.phpSessid = phpSessid; }
}