package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import com.fourlane.global.error.ApiException;
import com.fourlane.item.application.*;
import com.fourlane.support.ImageFixtures;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemValidationTest {
    final List<ImageUpload> images = List.of(new ImageUpload(ImageFixtures.PNG.length, () -> new ByteArrayInputStream(ImageFixtures.PNG)));
    @ParameterizedTest @ValueSource(longs = {12345, 50001})
    void rejectsNonThousandPrices(long price) {
        for (var command : List.of(new ItemRegistrationService.Command("상품", null, price, 100000),
                new ItemRegistrationService.Command("상품", null, 1000, price))) {
            var error = catchThrowableOfType(ApiException.class, () -> ItemRegistrationService.validate(command, images));
            assertThat(error.getFields().getFirst().reason()).isEqualTo("NOT_MULTIPLE_OF_1000");
        }
    }
    @Test void rejectsReserveBelowStart() {
        var error = catchThrowableOfType(ApiException.class, () -> ItemRegistrationService.validate(
                new ItemRegistrationService.Command("상품", null, 50000, 49000), images));
        assertThat(error.getFields().getFirst().reason()).isEqualTo("LESS_THAN_START_PRICE");
    }
    @ParameterizedTest @ValueSource(longs = {-1000, 0, 999, 1000001000, Long.MAX_VALUE})
    void rejectsOutOfRangePrices(long price) {
        assertThatThrownBy(() -> ItemRegistrationService.validate(new ItemRegistrationService.Command("상품", null, price, price), images))
                .isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getFields().getFirst().reason()).isEqualTo("OUT_OF_RANGE"));
    }
    @Test void usesCodePointsForTextBounds() {
        ItemRegistrationService.validate(new ItemRegistrationService.Command("😀".repeat(100), "😀".repeat(2000), 1000, 1000), images);
        for (var command : List.of(new ItemRegistrationService.Command("😀".repeat(101), null, 1000, 1000),
                new ItemRegistrationService.Command("상품", "😀".repeat(2001), 1000, 1000), new ItemRegistrationService.Command(" \t", null, 1000, 1000)))
            assertThatThrownBy(() -> ItemRegistrationService.validate(command, images)).isInstanceOf(ApiException.class);
    }
    @Test void validatesImageCountAndSize() {
        var command = new ItemRegistrationService.Command("상품", null, 1000, 1000);
        assertThatThrownBy(() -> ItemRegistrationService.validate(command, List.of())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ItemRegistrationService.validate(command, java.util.Collections.nCopies(11, images.getFirst()))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ItemRegistrationService.validate(command, List.of(new ImageUpload(10000001, () -> null))))
                .isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getErrorCode().name()).isEqualTo("IMAGE_TOO_LARGE"));
    }
}
