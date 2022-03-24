package com.photoloader.service.bean;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResolution {

  private Integer height;

  private Integer width;

  public Optional<Integer> getHeight() {
    return Optional.ofNullable(height);
  }

  public Optional<Integer> getWidth() {
    return Optional.ofNullable(width);
  }
}
