package eu.maksimov.demo.spring.versioning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
public class SessionV1Controller {

  @GetMapping
  public String getAll() {
    return "answer from SessionV1Controller.getAll";
  }

  @GetMapping("{id}")
  public String getById(@PathVariable String id) {
    return "answer from SessionV1Controller.getById(" + id + ")";
  }

}
