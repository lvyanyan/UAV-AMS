package com.uav.flightplan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 服务间调用配置：放行检查需要跨服务查询实名登记（uav-registry）与驾驶员（uav-pilot）。
 * base-url 走 application.yml，默认本机端口，便于 docker/物理机两种部署覆盖。
 */
@Configuration
public class IntegrationConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /** uav-registry 服务根地址 */
    @Bean
    public String registryBaseUrl(@Value("${uav.services.registry-base-url:http://localhost:8086}") String baseUrl) {
        return trimTrailingSlash(baseUrl);
    }

    /** uav-pilot 服务根地址 */
    @Bean
    public String pilotBaseUrl(@Value("${uav.services.pilot-base-url:http://localhost:8087}") String baseUrl) {
        return trimTrailingSlash(baseUrl);
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
