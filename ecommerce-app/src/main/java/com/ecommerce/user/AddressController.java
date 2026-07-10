package com.ecommerce.user;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping
    public AddressResponse create(@AuthenticationPrincipal UserDetails principal,
                                   @Valid @RequestBody AddressRequest request) {
        return addressService.create(principal.getUsername(), request);
    }

    @GetMapping
    public List<AddressResponse> getMine(@AuthenticationPrincipal UserDetails principal) {
        return addressService.getForUser(principal.getUsername());
    }
}
