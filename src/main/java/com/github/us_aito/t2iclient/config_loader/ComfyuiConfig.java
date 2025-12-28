package com.github.us_aito.t2iclient.config_loader;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ComfyuiConfig(
  @JsonProperty("server_address") String serverAddress,
  @JsonProperty("client_id") String clientId
) {}
