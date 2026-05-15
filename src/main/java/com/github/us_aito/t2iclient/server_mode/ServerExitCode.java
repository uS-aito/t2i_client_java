package com.github.us_aito.t2iclient.server_mode;

public enum ServerExitCode {
    SUCCESS(0),
    CONFIG_ERROR(1),
    ARGUMENT_ERROR(2),
    AUTH_ERROR(3),
    COMFYUI_ERROR(4),
    S3_ERROR(5),
    UNEXPECTED(9);

    private final int code;

    ServerExitCode(int code) {
        this.code = code;
    }

    public int numeric() {
        return code;
    }
}
