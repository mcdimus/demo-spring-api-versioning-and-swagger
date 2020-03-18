package eu.maksimov.demo.spring.versioning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/person")
public class PersonV2Controller {

  @GetMapping
  public String getAll() {
    return "answer from PersonV2Controller.getAll";
  }

  @GetMapping("{id}")
  public String getById(@PathVariable String id) {
    return "answer from PersonV2Controller.getById(" + id + ")";
  }

  @GetMapping("1")
  public String getById() {
    return "answer from PersonV2Controller.getById(HARDCODED 1)";
  }

}
