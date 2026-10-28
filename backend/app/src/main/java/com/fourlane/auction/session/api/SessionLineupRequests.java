package com.fourlane.auction.session.api;

import com.fourlane.global.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;
import tools.jackson.databind.JsonNode;

public final class SessionLineupRequests {
    private SessionLineupRequests() {}
    @Schema(name="SessionLineupRequest", requiredProperties={"itemIds"})
    public record Replace(@Schema(type="array", implementation=Long[].class) JsonNode itemIds) {
        public List<Long> ids() {
            if (itemIds == null || itemIds.isNull()) throw ApiException.invalidRequest("itemIds", "REQUIRED");
            if (!itemIds.isArray()) throw ApiException.invalidRequest("itemIds", "INVALID_FORMAT");
            var result = new ArrayList<Long>(); var seen = new HashSet<Long>();
            for (int i = 0; i < itemIds.size(); i++) {
                var value = itemIds.get(i); String field = "itemIds[" + i + "]";
                if (value.isNull()) throw ApiException.invalidRequest(field, "REQUIRED");
                if (!value.isIntegralNumber()) throw ApiException.invalidRequest(field, "INVALID_FORMAT");
                if (!value.canConvertToLong() || value.longValue() < 1 || value.longValue() > 9007199254740991L)
                    throw ApiException.invalidRequest(field, "OUT_OF_RANGE");
                long id = value.longValue();
                if (!seen.add(id)) throw ApiException.invalidRequest(field, "DUPLICATE");
                result.add(id);
            }
            return List.copyOf(result);
        }
    }
}
