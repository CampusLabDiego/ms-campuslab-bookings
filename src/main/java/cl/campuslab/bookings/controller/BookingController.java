package cl.campuslab.bookings.controller;

import cl.campuslab.bookings.dto.BookingRequest;
import cl.campuslab.bookings.dto.BookingResponse;
import cl.campuslab.bookings.enums.BookingStatus;
import cl.campuslab.bookings.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;


    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }


    @GetMapping
    public ResponseEntity<List<BookingResponse>> findAll() {

        return ResponseEntity.ok(
                bookingService.findAll()
        );
    }


    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                bookingService.findById(id)
        );
    }


    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody BookingRequest request) {


        BookingResponse booking =
                bookingService.create(request);


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(booking);
    }



    @PatchMapping("/{id}/status")
    public ResponseEntity<BookingResponse> changeStatus(
            @PathVariable Long id,
            @RequestParam BookingStatus status) {


        return ResponseEntity.ok(
                bookingService.changeStatus(id, status)
        );
    }
}