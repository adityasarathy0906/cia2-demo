
package com.example.cia2_demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
       return "CIA2 CI/CD Pipeline deployed successfully on Azure Kubernetes Service!";
    }
}
