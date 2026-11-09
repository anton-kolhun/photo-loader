package com.photoloader.service.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageCharacteristics {

    private Integer height;

    private Integer width;

    private Quality quality;

    /**
     * What a device asked for. Phones (user agent contains "mobile") ignore the screen size and get the
     * 900x1500 default, so all phones share one cache bucket; desktops get a size for their screen.
     */
    public static ImageCharacteristics forDevice(String userAgent, Integer width, Integer height, Quality quality) {
        boolean mobile = userAgent != null && userAgent.toLowerCase().contains("mobile");
        return ImageCharacteristics.builder()
                .width(mobile ? null : width)
                .height(mobile ? null : height)
                .quality(quality)
                .build();
    }

    public Optional<Quality> getQuality() {
        return Optional.ofNullable(quality);
    }

    public Optional<Integer> getHeight() {
        return Optional.ofNullable(height);
    }

    public Optional<Integer> getWidth() {
        return Optional.ofNullable(width);
    }
}
