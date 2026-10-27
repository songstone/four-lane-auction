package com.fourlane.auction.session.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum SessionStatus {
    SCHEDULED, RUNNING, FINISHED
}
