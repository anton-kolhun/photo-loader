package com.photoloader.search;

import com.photoloader.service.bean.ImageCharacteristics;
import com.photoloader.service.bean.Quality;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** The search page and its API. Lives beside the slideshow (IndexPageController / ImageController), not inside it. */
@Controller
@ConditionalOnClipSearch
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("search")
    public String page() {
        return "search"; // static/html/search.html
    }

    @GetMapping("search/api")
    @ResponseBody
    public List<EmbeddingIndex.Hit> search(@RequestParam("q") String query,
                                           @RequestParam(value = "year", required = false) String year,
                                           @RequestParam(value = "limit", defaultValue = "30") int limit) {
        if (query.isBlank()) {
            return List.of();
        }
        return searchService.search(query, year, limit);
    }

    @GetMapping("search/similar")
    @ResponseBody
    public List<EmbeddingIndex.Hit> similar(@RequestParam("key") String key,
                                            @RequestParam(value = "year", required = false) String year,
                                            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return searchService.similar(key, year, limit);
    }

    /** Called when the search box gets focus, so a cold Lambda loads its model while the user types. */
    @GetMapping("search/warmup")
    public ResponseEntity<Void> warmup() {
        searchService.warmup();
        return ResponseEntity.noContent().build();
    }

    /** Same image as the slideshow: the page sends the screen size; phones get the shared mobile size. */
    @GetMapping("search/photo")
    public ResponseEntity<byte[]> photo(@RequestParam("key") String key,
                                        @RequestParam(value = "width", required = false) Integer width,
                                        @RequestParam(value = "height", required = false) Integer height,
                                        @RequestParam(value = "quality", required = false) Quality quality,
                                        @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return image(searchService.photo(key, ImageCharacteristics.forDevice(userAgent, width, height, quality)));
    }

    @GetMapping("search/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@RequestParam("key") String key) {
        return image(searchService.thumbnail(key));
    }

    private static ResponseEntity<byte[]> image(byte[] jpeg) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .body(jpeg);
    }
}
