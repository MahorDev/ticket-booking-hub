package com.tickethub.event.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.event.dto.request.*;
import com.tickethub.event.enums.*;
import com.tickethub.event.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickethub_event_test_db;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "management.tracing.enabled=false"
})
class EventServiceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ScreenRepository screenRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void cleanDatabase() {
        showSeatRepository.deleteAll();
        showRepository.deleteAll();
        seatRepository.deleteAll();
        screenRepository.deleteAll();
        venueRepository.deleteAll();
        eventRepository.deleteAll();
    }

    // =========================================================================
    // 1. VenueController Tests
    // =========================================================================

    @Test
    void testVenueControllerEndpoints() throws Exception {
        // 1. POST /api/venues (201 Created)
        VenueRequest createReq = new VenueRequest();
        createReq.setName("PVR Sathyam");
        createReq.setAddress("Royapettah High Road");
        createReq.setCity("Chennai");
        createReq.setState("Tamil Nadu");
        createReq.setCountry("India");

        MvcResult createResult = mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.venueId").isNumber())
                .andExpect(jsonPath("$.name").value("PVR Sathyam"))
                .andExpect(jsonPath("$.address").value("Royapettah High Road"))
                .andExpect(jsonPath("$.city").value("Chennai"))
                .andReturn();

        Long venueId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("venueId").asLong();

        // 2. GET /api/venues (200 OK)
        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].venueId").value(venueId))
                .andExpect(jsonPath("$.content[0].name").value("PVR Sathyam"));

        // 3. GET /api/venues/{venueId} (200 OK)
        mockMvc.perform(get("/api/venues/{venueId}", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venueId").value(venueId))
                .andExpect(jsonPath("$.name").value("PVR Sathyam"));

        // GET /api/venues/{venueId} (404 Not Found when not found)
        mockMvc.perform(get("/api/venues/{venueId}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 4. GET /api/venues/{venueId}/screens (200 OK)
        mockMvc.perform(get("/api/venues/{venueId}/screens", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // 5. PUT /api/venues/{venueId} (200 OK)
        VenueRequest updateReq = new VenueRequest();
        updateReq.setName("PVR Sathyam Cinemas");
        updateReq.setAddress("8 Thiruvika Road, Royapettah");
        updateReq.setCity("Chennai");
        updateReq.setState("Tamil Nadu");
        updateReq.setCountry("India");

        mockMvc.perform(put("/api/venues/{venueId}", venueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venueId").value(venueId))
                .andExpect(jsonPath("$.name").value("PVR Sathyam Cinemas"))
                .andExpect(jsonPath("$.address").value("8 Thiruvika Road, Royapettah"));

        // 6. DELETE /api/venues/{venueId} (204 No Content)
        mockMvc.perform(delete("/api/venues/{venueId}", venueId))
                .andExpect(status().isNoContent());

        // Verify deleted -> 404
        mockMvc.perform(get("/api/venues/{venueId}", venueId))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 2. ScreenController Tests
    // =========================================================================

    @Test
    void testScreenControllerEndpoints() throws Exception {
        Long venueId = createTestVenue("AGS Cinemas", "T Nagar", "Chennai");

        // 1. POST /api/screens (201 Created)
        ScreenRequest screenReq = new ScreenRequest();
        screenReq.setScreenName("Screen 1 - Dolby Atmos");
        screenReq.setVenueId(venueId);

        MvcResult createScreenRes = mockMvc.perform(post("/api/screens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(screenReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.screenId").isNumber())
                .andExpect(jsonPath("$.screenName").value("Screen 1 - Dolby Atmos"))
                .andExpect(jsonPath("$.venueId").value(venueId))
                .andExpect(jsonPath("$.venueName").value("AGS Cinemas"))
                .andReturn();

        Long screenId = objectMapper.readTree(createScreenRes.getResponse().getContentAsString())
                .get("screenId").asLong();

        // Also verify GET /api/venues/{venueId}/screens returns the created screen
        mockMvc.perform(get("/api/venues/{venueId}/screens", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].screenId").value(screenId));

        // 2. GET /api/screens (200 OK)
        mockMvc.perform(get("/api/screens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].screenId").value(screenId));

        // 3. GET /api/screens/{screenId} (200 OK)
        mockMvc.perform(get("/api/screens/{screenId}", screenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value(screenId))
                .andExpect(jsonPath("$.screenName").value("Screen 1 - Dolby Atmos"))
                .andExpect(jsonPath("$.venueName").value("AGS Cinemas"));

        // 4. GET /api/screens/{screenId}/seats (200 OK)
        mockMvc.perform(get("/api/screens/{screenId}/seats", screenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // 5. GET /api/screens/{screenId}/shows (200 OK)
        mockMvc.perform(get("/api/screens/{screenId}/shows", screenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // 6. PUT /api/screens/{screenId} (200 OK)
        ScreenRequest updateScreenReq = new ScreenRequest();
        updateScreenReq.setScreenName("Screen 1 - IMAX");
        updateScreenReq.setVenueId(venueId);

        mockMvc.perform(put("/api/screens/{screenId}", screenId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateScreenReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value(screenId))
                .andExpect(jsonPath("$.screenName").value("Screen 1 - IMAX"));

        // 7. DELETE /api/screens/{screenId} (204 No Content)
        mockMvc.perform(delete("/api/screens/{screenId}", screenId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/screens/{screenId}", screenId))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 3. SeatController Tests
    // =========================================================================

    @Test
    void testSeatControllerEndpoints() throws Exception {
        Long venueId = createTestVenue("Rohini Silver Screens", "Koyambedu", "Chennai");
        Long screenId = createTestScreen(venueId, "Main Screen");

        // 1. POST /api/seats (201 Created)
        SeatRequest seatReq = new SeatRequest();
        seatReq.setSeatNumber("A1");
        seatReq.setRowName("A");
        seatReq.setSeatType(SeatType.VIP);
        seatReq.setSection(SeatSection.UPPER);
        seatReq.setBasePrice(new BigDecimal("350.00"));
        seatReq.setScreenId(screenId);

        MvcResult createSeatRes = mockMvc.perform(post("/api/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.seatId").isNumber())
                .andExpect(jsonPath("$.seatNumber").value("A1"))
                .andExpect(jsonPath("$.rowName").value("A"))
                .andExpect(jsonPath("$.seatType").value("VIP"))
                .andExpect(jsonPath("$.section").value("UPPER"))
                .andExpect(jsonPath("$.basePrice").value(350.00))
                .andExpect(jsonPath("$.screenId").value(screenId))
                .andExpect(jsonPath("$.screenName").value("Main Screen"))
                .andReturn();

        Long seatId = objectMapper.readTree(createSeatRes.getResponse().getContentAsString())
                .get("seatId").asLong();

        // Verify GET /api/screens/{screenId}/seats returns the seat
        mockMvc.perform(get("/api/screens/{screenId}/seats", screenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].seatId").value(seatId));

        // 2. GET /api/seats (200 OK)
        mockMvc.perform(get("/api/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].seatId").value(seatId));

        // 3. GET /api/seats/{seatId} (200 OK)
        mockMvc.perform(get("/api/seats/{seatId}", seatId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatId").value(seatId))
                .andExpect(jsonPath("$.seatNumber").value("A1"))
                .andExpect(jsonPath("$.section").value("UPPER"));

        // 4. PUT /api/seats/{seatId} (200 OK)
        SeatRequest updateSeatReq = new SeatRequest();
        updateSeatReq.setSeatNumber("A2");
        updateSeatReq.setRowName("A");
        updateSeatReq.setSeatType(SeatType.PREMIUM);
        updateSeatReq.setSection(SeatSection.MIDDLE);
        updateSeatReq.setBasePrice(new BigDecimal("280.00"));
        updateSeatReq.setScreenId(screenId);

        mockMvc.perform(put("/api/seats/{seatId}", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateSeatReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatId").value(seatId))
                .andExpect(jsonPath("$.seatNumber").value("A2"))
                .andExpect(jsonPath("$.seatType").value("PREMIUM"))
                .andExpect(jsonPath("$.section").value("MIDDLE"))
                .andExpect(jsonPath("$.basePrice").value(280.00));

        // 5. DELETE /api/seats/{seatId} (204 No Content)
        mockMvc.perform(delete("/api/seats/{seatId}", seatId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/seats/{seatId}", seatId))
                .andExpect(status().isNotFound());

        // 6. POST /api/seats with RECLINER (201 Created)
        SeatRequest reclinerReq = new SeatRequest();
        reclinerReq.setSeatNumber("R1");
        reclinerReq.setRowName("R");
        reclinerReq.setSeatType(SeatType.RECLINER);
        reclinerReq.setSection(SeatSection.UPPER);
        reclinerReq.setBasePrice(new BigDecimal("500.00"));
        reclinerReq.setScreenId(screenId);

        mockMvc.perform(post("/api/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reclinerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatType").value("RECLINER"));

        // 7. POST /api/seats/bulk with RECLINER (201 Created)
        SeatRequest bulk1 = new SeatRequest();
        bulk1.setSeatNumber("R2");
        bulk1.setRowName("R");
        bulk1.setSeatType(SeatType.RECLINER);
        bulk1.setSection(SeatSection.UPPER);
        bulk1.setBasePrice(new BigDecimal("500.00"));
        bulk1.setScreenId(screenId);

        mockMvc.perform(post("/api/seats/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(bulk1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].seatType").value("RECLINER"));
    }

    // =========================================================================
    // 4. EventController & MediaController Tests
    // =========================================================================

    @Test
    void testEventAndMediaControllerEndpoints() throws Exception {
        // 1. POST /api/events (201 Created, starts as DRAFT)
        EventRequest eventReq = new EventRequest();
        eventReq.setTitle("Vikram");
        eventReq.setDescription("High-octane action thriller");
        eventReq.setEventType(EventType.MOVIE);
        eventReq.setLanguage("Tamil");
        eventReq.setDuration(175);
        eventReq.setRating("UA");
        eventReq.setGenre("Action");

        MvcResult createEventRes = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.eventId").isNumber())
                .andExpect(jsonPath("$.title").value("Vikram"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        Long eventId = objectMapper.readTree(createEventRes.getResponse().getContentAsString())
                .get("eventId").asLong();

        // 2. GET /api/events with X-Authenticated-Role: ADMIN vs CUSTOMER
        // ADMIN sees DRAFT events
        mockMvc.perform(get("/api/events")
                        .header("X-Authenticated-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].eventId").value(eventId))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));

        // CUSTOMER does NOT see DRAFT events
        mockMvc.perform(get("/api/events")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // 3. GET /api/events/{eventId} with ADMIN (200) vs CUSTOMER (404 while DRAFT)
        mockMvc.perform(get("/api/events/{eventId}", eventId)
                        .header("X-Authenticated-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId));

        mockMvc.perform(get("/api/events/{eventId}", eventId)
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());

        // 4. PUT /api/events/{eventId} (200 OK)
        EventRequest updateEventReq = new EventRequest();
        updateEventReq.setTitle("Vikram: Hitlist");
        updateEventReq.setDescription("Updated action thriller");
        updateEventReq.setEventType(EventType.MOVIE);
        updateEventReq.setLanguage("Tamil");
        updateEventReq.setDuration(172);
        updateEventReq.setRating("UA");
        updateEventReq.setGenre("Action Thriller");

        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateEventReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.title").value("Vikram: Hitlist"))
                .andExpect(jsonPath("$.duration").value(172));

        // 5. PATCH /api/events/{eventId}/status -> PUBLISHED
        EventStatusRequest publishReq = new EventStatusRequest();
        publishReq.setStatus(EventStatus.PUBLISHED);

        mockMvc.perform(patch("/api/events/{eventId}/status", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Now CUSTOMER can see the PUBLISHED event via GET /api/events and GET /api/events/{eventId}
        mockMvc.perform(get("/api/events")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].eventId").value(eventId))
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"));

        mockMvc.perform(get("/api/events/{eventId}", eventId)
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 6. GET /api/events/{eventId}/shows (200 OK)
        mockMvc.perform(get("/api/events/{eventId}/shows", eventId)
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // 7. POST /api/events/{eventId}/poster (multipart file upload)
        byte[] posterBytes = "fake-png-poster-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile posterFile = new MockMultipartFile(
                "file", "vikram_poster.png", MediaType.IMAGE_PNG_VALUE, posterBytes);

        MvcResult posterRes = mockMvc.perform(multipart("/api/events/{eventId}/poster", eventId)
                        .file(posterFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(String.valueOf(eventId)))
                .andExpect(jsonPath("$.posterUrl").value(startsWith("/api/media/events/" + eventId + "/")))
                .andReturn();

        String posterUrl = objectMapper.readTree(posterRes.getResponse().getContentAsString())
                .get("posterUrl").asText();

        // Verify event now has posterUrl populated and the poster URL is served by MediaController
        mockMvc.perform(get("/api/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterUrl").value(posterUrl));

        mockMvc.perform(get(posterUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(posterBytes));

        // 8. POST /api/events/media/upload
        byte[] bannerBytes = "event-banner-image-bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile bannerFile = new MockMultipartFile(
                "file", "banner.jpg", MediaType.IMAGE_JPEG_VALUE, bannerBytes);

        MvcResult eventMediaRes = mockMvc.perform(multipart("/api/events/media/upload")
                        .file(bannerFile)
                        .param("folder", "banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("/api/media/banners/")))
                .andExpect(jsonPath("$.filename").value("banner.jpg"))
                .andReturn();

        String bannerUrl = objectMapper.readTree(eventMediaRes.getResponse().getContentAsString())
                .get("url").asText();

        // 9. POST /api/media/upload
        byte[] galleryBytes = "gallery-asset-bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile galleryFile = new MockMultipartFile(
                "file", "gallery.png", MediaType.IMAGE_PNG_VALUE, galleryBytes);

        MvcResult mediaRes = mockMvc.perform(multipart("/api/media/upload")
                        .file(galleryFile)
                        .param("folder", "gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("/api/media/gallery/")))
                .andExpect(jsonPath("$.filename").value("gallery.png"))
                .andReturn();

        String galleryUrl = objectMapper.readTree(mediaRes.getResponse().getContentAsString())
                .get("url").asText();

        // 10. GET /api/media/{folder}/{filename} (verify uploaded files are served back with 200 OK!)
        mockMvc.perform(get(bannerUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(bannerBytes));

        mockMvc.perform(get(galleryUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(galleryBytes));

        // 11. PATCH /api/events/{eventId}/status -> CANCELLED
        EventStatusRequest cancelReq = new EventStatusRequest();
        cancelReq.setStatus(EventStatus.CANCELLED);

        mockMvc.perform(patch("/api/events/{eventId}/status", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 12. DELETE /api/events/{eventId} (204 No Content)
        mockMvc.perform(delete("/api/events/{eventId}", eventId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{eventId}", eventId)
                        .header("X-Authenticated-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 5. ShowController Tests
    // =========================================================================

    @Test
    void testShowControllerEndpoints() throws Exception {
        Long venueId = createTestVenue("INOX Marina Mall", "OMR Egattur", "Chennai");
        Long screenId = createTestScreen(venueId, "Audi 1");
        Long seat1Id = createTestSeat(screenId, "A1", "A", SeatType.REGULAR, SeatSection.LOWER, new BigDecimal("200.00"));
        Long seat2Id = createTestSeat(screenId, "B1", "B", SeatType.VIP, SeatSection.UPPER, new BigDecimal("400.00"));
        Long eventId = createAndPublishEvent("Leo", 160);

        LocalDate showDate = LocalDate.now().plusDays(10);

        // 1. POST /api/shows (verify ShowSeats are auto-created and venueName + venueAddress are returned)
        ShowRequest showReq = new ShowRequest();
        showReq.setEventId(eventId);
        showReq.setScreenId(screenId);
        showReq.setShowDate(showDate);
        showReq.setStartTime(LocalTime.of(14, 0));
        showReq.setEndTime(LocalTime.of(17, 0));
        showReq.setTicketPrice(new BigDecimal("250.00"));
        showReq.setCurrency("INR");

        MvcResult createShowRes = mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(showReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.showId").isNumber())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.screenId").value(screenId))
                .andExpect(jsonPath("$.venueId").value(venueId))
                .andExpect(jsonPath("$.venueName").value("INOX Marina Mall"))
                .andExpect(jsonPath("$.venueAddress").value("OMR Egattur, Chennai"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.ticketPrice").value(250.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andReturn();

        Long showId = objectMapper.readTree(createShowRes.getResponse().getContentAsString())
                .get("showId").asLong();

        // 2. GET /api/shows (200 OK)
        mockMvc.perform(get("/api/shows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].showId").value(showId))
                .andExpect(jsonPath("$.content[0].venueName").value("INOX Marina Mall"))
                .andExpect(jsonPath("$.content[0].venueAddress").value("OMR Egattur, Chennai"));

        // 3. GET /api/shows/{showId} (200 OK)
        mockMvc.perform(get("/api/shows/{showId}", showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value(showId))
                .andExpect(jsonPath("$.venueName").value("INOX Marina Mall"))
                .andExpect(jsonPath("$.venueAddress").value("OMR Egattur, Chennai"));

        // Also check GET /api/events/{eventId}/shows and GET /api/screens/{screenId}/shows
        mockMvc.perform(get("/api/events/{eventId}/shows", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].showId").value(showId));

        mockMvc.perform(get("/api/screens/{screenId}/shows", screenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].showId").value(showId));

        // 4. GET /api/shows/{showId}/seats (verify ShowSeats auto-created and categoryName is populated)
        mockMvc.perform(get("/api/shows/{showId}/seats", showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].seatId").value(seat1Id))
                .andExpect(jsonPath("$[0].seatNumber").value("A1"))
                .andExpect(jsonPath("$[0].categoryName").value("LOWER"))
                .andExpect(jsonPath("$[0].price").value(200.00))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].seatId").value(seat2Id))
                .andExpect(jsonPath("$[1].seatNumber").value("B1"))
                .andExpect(jsonPath("$[1].categoryName").value("UPPER"))
                .andExpect(jsonPath("$[1].price").value(400.00))
                .andExpect(jsonPath("$[1].status").value("AVAILABLE"));

        // 4b. GET /api/shows/{showId}/seats/page (paginated show seats)
        mockMvc.perform(get("/api/shows/{showId}/seats/page", showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].categoryName").value("LOWER"))
                .andExpect(jsonPath("$.content[1].categoryName").value("UPPER"));

        // 5. PUT /api/shows/{showId} (200 OK)
        ShowRequest updateShowReq = new ShowRequest();
        updateShowReq.setEventId(eventId);
        updateShowReq.setScreenId(screenId);
        updateShowReq.setShowDate(showDate);
        updateShowReq.setStartTime(LocalTime.of(18, 0));
        updateShowReq.setEndTime(LocalTime.of(21, 30));
        updateShowReq.setTicketPrice(new BigDecimal("300.00"));
        updateShowReq.setCurrency("INR");

        mockMvc.perform(put("/api/shows/{showId}", showId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateShowReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value(showId))
                .andExpect(jsonPath("$.ticketPrice").value(300.00))
                .andExpect(jsonPath("$.venueName").value("INOX Marina Mall"))
                .andExpect(jsonPath("$.venueAddress").value("OMR Egattur, Chennai"));

        // 6. PATCH /api/shows/{showId}/status -> OPEN
        ShowStatusRequest openReq = new ShowStatusRequest();
        openReq.setStatus(ShowStatus.OPEN);

        mockMvc.perform(patch("/api/shows/{showId}/status", showId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value(showId))
                .andExpect(jsonPath("$.status").value("OPEN"));

        // Deleting an OPEN show should be rejected (409 Conflict)
        mockMvc.perform(delete("/api/shows/{showId}", showId))
                .andExpect(status().isConflict());

        // Move show to CANCELLED, then verify DELETE /api/shows/{showId} succeeds with auto-generated ShowSeats
        ShowStatusRequest cancelShowReq = new ShowStatusRequest();
        cancelShowReq.setStatus(ShowStatus.CANCELLED);

        mockMvc.perform(patch("/api/shows/{showId}/status", showId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelShowReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 7. DELETE /api/shows/{showId} (on CANCELLED show with auto-generated ShowSeats)
        mockMvc.perform(delete("/api/shows/{showId}", showId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/shows/{showId}", showId))
                .andExpect(status().isNotFound());
        assertEquals(0, showSeatRepository.findAllByShowShowIdOrderByShowSeatId(showId).size());

        // Also verify deleting a SCHEDULED show with auto-generated ShowSeats succeeds!
        MvcResult createShow2Res = mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(showReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Long show2Id = objectMapper.readTree(createShow2Res.getResponse().getContentAsString())
                .get("showId").asLong();
        assertEquals(2, showSeatRepository.findAllByShowShowIdOrderByShowSeatId(show2Id).size());

        mockMvc.perform(delete("/api/shows/{showId}", show2Id))
                .andExpect(status().isNoContent());
        assertEquals(0, showSeatRepository.findAllByShowShowIdOrderByShowSeatId(show2Id).size());
    }

    // =========================================================================
    // 6. ShowSeatController Tests
    // =========================================================================

    @Test
    void testShowSeatControllerEndpoints() throws Exception {
        Long venueId = createTestVenue("SPI Palazzo", "Vadapalani", "Chennai");
        Long screenId = createTestScreen(venueId, "Screen 2");
        Long seat1Id = createTestSeat(screenId, "C1", "C", SeatType.REGULAR, SeatSection.LOWER, new BigDecimal("180.00"));
        Long eventId = createAndPublishEvent("Jailer", 165);

        ShowRequest showReq = new ShowRequest();
        showReq.setEventId(eventId);
        showReq.setScreenId(screenId);
        showReq.setShowDate(LocalDate.now().plusDays(12));
        showReq.setStartTime(LocalTime.of(10, 0));
        showReq.setEndTime(LocalTime.of(13, 0));
        showReq.setTicketPrice(new BigDecimal("200.00"));
        showReq.setCurrency("INR");

        MvcResult createShowRes = mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(showReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Long showId = objectMapper.readTree(createShowRes.getResponse().getContentAsString())
                .get("showId").asLong();

        // Add a new seat to the screen after the show was created so we can POST /api/show-seats for it
        Long seat2Id = createTestSeat(screenId, "C2", "C", SeatType.PREMIUM, SeatSection.MIDDLE, new BigDecimal("250.00"));
        showSeatRepository.deleteAll(showSeatRepository.findAllByShowShowIdOrderByShowSeatId(showId));

        // 1. POST /api/show-seats (201 Created)
        ShowSeatRequest createSsReq = new ShowSeatRequest();
        createSsReq.setShowId(showId);
        createSsReq.setSeatId(seat2Id);
        createSsReq.setPrice(new BigDecimal("260.00"));
        createSsReq.setStatus(ShowSeatStatus.AVAILABLE);

        MvcResult createSsRes = mockMvc.perform(post("/api/show-seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSsReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.showSeatId").isNumber())
                .andExpect(jsonPath("$.showId").value(showId))
                .andExpect(jsonPath("$.seatId").value(seat2Id))
                .andExpect(jsonPath("$.seatNumber").value("C2"))
                .andExpect(jsonPath("$.categoryName").value("MIDDLE"))
                .andExpect(jsonPath("$.price").value(260.00))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn();

        Long showSeatId = objectMapper.readTree(createSsRes.getResponse().getContentAsString())
                .get("showSeatId").asLong();

        // 2. GET /api/show-seats/{showSeatId} (200 OK)
        mockMvc.perform(get("/api/show-seats/{showSeatId}", showSeatId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showSeatId").value(showSeatId))
                .andExpect(jsonPath("$.seatId").value(seat2Id))
                .andExpect(jsonPath("$.categoryName").value("MIDDLE"))
                .andExpect(jsonPath("$.price").value(260.00))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        // 3. PUT /api/show-seats/{showSeatId} (200 OK)
        ShowSeatRequest updateSsReq = new ShowSeatRequest();
        updateSsReq.setShowId(showId);
        updateSsReq.setSeatId(seat2Id);
        updateSsReq.setPrice(new BigDecimal("300.00"));
        updateSsReq.setStatus(ShowSeatStatus.BLOCKED);

        mockMvc.perform(put("/api/show-seats/{showSeatId}", showSeatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateSsReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showSeatId").value(showSeatId))
                .andExpect(jsonPath("$.price").value(300.00))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        // 4. DELETE /api/show-seats/{showSeatId} (204 No Content)
        mockMvc.perform(delete("/api/show-seats/{showSeatId}", showSeatId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/show-seats/{showSeatId}", showSeatId))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Long createTestVenue(String name, String address, String city) throws Exception {
        VenueRequest req = new VenueRequest();
        req.setName(name);
        req.setAddress(address);
        req.setCity(city);
        req.setState("Tamil Nadu");
        req.setCountry("India");

        MvcResult res = mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("venueId").asLong();
    }

    private Long createTestScreen(Long venueId, String screenName) throws Exception {
        ScreenRequest req = new ScreenRequest();
        req.setVenueId(venueId);
        req.setScreenName(screenName);

        MvcResult res = mockMvc.perform(post("/api/screens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("screenId").asLong();
    }

    private Long createTestSeat(Long screenId, String seatNumber, String rowName,
                                SeatType seatType, SeatSection section, BigDecimal basePrice) throws Exception {
        SeatRequest req = new SeatRequest();
        req.setScreenId(screenId);
        req.setSeatNumber(seatNumber);
        req.setRowName(rowName);
        req.setSeatType(seatType);
        req.setSection(section);
        req.setBasePrice(basePrice);

        MvcResult res = mockMvc.perform(post("/api/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("seatId").asLong();
    }

    private Long createAndPublishEvent(String title, int duration) throws Exception {
        EventRequest req = new EventRequest();
        req.setTitle(title);
        req.setDescription("Description for " + title);
        req.setEventType(EventType.MOVIE);
        req.setLanguage("Tamil");
        req.setDuration(duration);
        req.setRating("UA");
        req.setGenre("Action");

        MvcResult res = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Long eventId = objectMapper.readTree(res.getResponse().getContentAsString()).get("eventId").asLong();

        EventStatusRequest publishReq = new EventStatusRequest();
        publishReq.setStatus(EventStatus.PUBLISHED);
        mockMvc.perform(patch("/api/events/{eventId}/status", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishReq)))
                .andExpect(status().isOk());

        return eventId;
    }
}
