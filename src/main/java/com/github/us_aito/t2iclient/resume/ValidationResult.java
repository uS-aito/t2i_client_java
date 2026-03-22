package com.github.us_aito.t2iclient.resume;

import java.util.List;

public record ValidationResult(
    boolean valid,
    List<String> warnings
) {}
