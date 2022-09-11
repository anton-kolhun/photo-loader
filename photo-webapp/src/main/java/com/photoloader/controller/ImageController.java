package com.photoloader.controller;

import com.photoloader.controller.dto.Direction;
import com.photoloader.service.ImageProcessorService;
import com.photoloader.service.bean.ImageCharacteristics;
import com.photoloader.service.bean.ImageSessionCursor;
import com.photoloader.service.bean.Quality;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("image")
@AllArgsConstructor
public class ImageController {

    private final ImageProcessorService imageProcessorService;

    @GetMapping
    public String getImage(
            @RequestParam(value = "direction", required = false, defaultValue = "NEXT") Direction direction,
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "height", required = false) Integer height,
            @RequestParam(value = "width", required = false) Integer width,
            @RequestParam(value = "quality", required = false) Quality quality,
            HttpSession httpSession, @RequestHeader Map<String, String> headers) {
        ImageCharacteristics imageMetaData;
        if (!headers.get("user-agent").toLowerCase().contains("mobile")) {
            imageMetaData = ImageCharacteristics.builder()
                    .height(height)
                    .width(width)
                    .quality(quality)
                    .build();
        } else {
            imageMetaData = ImageCharacteristics.builder()
                    .quality(quality)
                    .build();
        }
        ImageSessionCursor cursor = (ImageSessionCursor) httpSession.getAttribute("sessionInfo");
        if (cursor == null) {
            cursor = new ImageSessionCursor(httpSession.getId(), new ArrayList<>(), 0, year);
            httpSession.setAttribute("sessionInfo", cursor);
        }
        if (cursor.getFilter() != null && !cursor.getFilter().equals(year)) {
            cursor.setFilter(year);
            cursor.getSeenImages().clear();
            cursor.setCurrentCursor(0);
        }
        if (direction == Direction.NEXT) {
            cursor.setCurrentCursor(cursor.getCurrentCursor() + 1);
        } else if (direction == Direction.PREVIOUS) {
            cursor.setCurrentCursor(cursor.getCurrentCursor() - 1);
        }
        byte[] image = imageProcessorService.fetchRandomImage(cursor, year, imageMetaData);
        return Base64.getEncoder().encodeToString(image);
    }

}
