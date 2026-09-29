package com.finance.app.financeapp.resources;

import com.finance.app.financeapp.entities.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/users")
public class UserResource {

	@GetMapping
	public ResponseEntity<User> findAll() {
		User u = new User("Maria", "maria@gmail.com", "senha123", 26);
		return ResponseEntity.ok().body(u);
	}
}
