package com.photoloader.service.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ImageSessionCursor {
    String sessionId;
    List<String> seenImages;
    int currentCursor;
    String filter;
}
