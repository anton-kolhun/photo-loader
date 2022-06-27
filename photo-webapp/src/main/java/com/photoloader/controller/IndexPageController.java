package com.photoloader.controller;

import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping
public class IndexPageController {

  @GetMapping
  public String index(@RequestHeader Map<String, String> headers) {
    String indexPage = "index";
    if (headers.get("user-agent").toLowerCase().contains("mobile")) {
      indexPage = "index_mobile";
    }
    return indexPage;
  }

  @GetMapping("login")
  public String login() {
    return "login";
  }

  @GetMapping("policy")
  public String policy() {
    return "policy";
  }

  @GetMapping("body.html")
  public String body() {
    return "body";
  }
}
