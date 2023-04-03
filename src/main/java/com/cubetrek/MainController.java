package com.cubetrek;

import com.cubetrek.database.*;
import com.cubetrek.newsletter.NewsletterService;
import com.cubetrek.registration.UserDto_minimal;
import com.cubetrek.upload.*;
import com.cubetrek.viewer.ActivitityService;
import com.cubetrek.viewer.SlimTrackGeojson;
import com.cubetrek.viewer.TrackGeojson;
import com.cubetrek.viewer.TrackViewerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunlocator.topolibrary.LatLonBoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;


@Controller
public class MainController {
    @Autowired
    private StorageService storageService;

    @Autowired
    private GeographyService geographyService;

    @Autowired
    private TrackViewerService trackViewerService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private NewsletterService newsletterService;

    @Autowired
    private TrackDataRepository trackDataRepository;

    @Autowired
    private UserThirdpartyConnectRepository userThirdpartyConnectRepository;

    @Autowired
    private ActivitityService activitityService;

    Logger logger = LoggerFactory.getLogger(MainController.class);

    @GetMapping("/")
    public String index(Model model, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        response.addHeader("Cache-Control", "max-age=600, public");
        model.addAttribute("publicTracks", activitityService.getPublicActivitiesList(5));
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @RequestMapping(value="/logout", method = RequestMethod.GET)
    public String logoutPage (HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null){
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return "redirect:/login?logout";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        model.addAttribute("user", user);
        model.addAttribute("activityHeatmapJSON", activitityService.getActivityHeatmapAsJSON(user, user.getTimezone()));
        model.addAttribute("monthlyTotalJSON", activitityService.getMonthlyTotalAsJSON(user, user.getTimezone()));
        model.addAttribute("yearlyTotalJSON", activitityService.getYearlyTotalAsJSON(user, user.getTimezone()));
        model.addAttribute("topTracks", activitityService.getTopActivities(user));
        model.addAttribute("favoriteTracks",  activitityService.getFavoriteActivities(user));
        model.addAttribute("totalActivities", activitityService.getActivityCount(user));
        logger.info("View Dashboard by user id '"+user.getId()+"'; Name '"+user.getName()+"'");
        return "dashboard";
    }

    @ResponseBody
    @GetMapping("/activities_per_day")
    public List<TrackData.TrackMetadata> getActivitiesForGivenDay(@RequestParam("year") int year, @RequestParam("month") int month, @RequestParam("day") int day, @RequestParam("timezone") String timezone) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        String tz;
        if (Set.of(TimeZone.getAvailableIDs()).contains(timezone))
            tz = timezone;
        else
            tz = "Etc/UTC";
        return activitityService.getActivityOfDay(user, year, month, day, timezone);
    }

    @GetMapping("/activities")
    public String getActivitiesList(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        model.addAttribute("numberEntries", activitityService.countNumberOfEntries(user));
        model.addAttribute("activityCounts", activitityService.getActivityTypeCount(user));
        logger.info("View Activity List by user id '"+user.getId()+"'; Name '"+user.getName()+"'");
        return "activity_list";
    }

    @GetMapping("/trekmapper")
    public String getTrekMapper(Model model) throws JsonProcessingException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        model.addAttribute("numberEntries", activitityService.countNumberOfEntries(user));
        model.addAttribute("activityCounts", activitityService.getActivityTypeCount(user));
        model.addAttribute("activitiesPosition", (new ObjectMapper()).writeValueAsString(activitityService.getAllTracksPosition(user)));
        logger.info("View TrekMapper by user id '"+user.getId()+"'; Name '"+user.getName()+"'");
        return "trekmapper";
    }

    @ResponseBody
    @GetMapping(value="/activities_ajax", produces = "application/json")
    public List<TrackData.TrackMetadata> getActivitiesAjax(@RequestParam("size") int size, @RequestParam("page") int page, @RequestParam("sortby") String sort, @RequestParam("descending") boolean descending, @RequestParam(value = "filterby", required = false) Optional<String> activitytype) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        TrackData.Activitytype at = activitytype.map(TrackData.Activitytype::valueOf).orElse(null);
        return activitityService.getActivitiesList(user, size, page, sort, descending, at);
    }

    @GetMapping("/profile")
    public String showProfile(WebRequest request, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        UserDto_minimal userDto = new UserDto_minimal();
        userDto.setEmail(user.getEmail());
        userDto.setName(user.getName());
        userDto.setSharing(user.getSharing());
        model.addAttribute("user", userDto);
        UserThirdpartyConnect utc = userThirdpartyConnectRepository.findByUser(user);
        model.addAttribute("isGarminConnected", utc != null && utc.isGarminEnabled());
        model.addAttribute("isPolarConnected", utc != null && utc.isPolarEnabled());
        return "profile";
    }

    @PostMapping("/profile")
    public String saveProfileChanges(
            @ModelAttribute("user") @Valid UserDto_minimal userDto, BindingResult bindingResult, Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        if (bindingResult.hasErrors()) {
            UserThirdpartyConnect utc = userThirdpartyConnectRepository.findByUser(user);
            model.addAttribute("isGarminConnected", utc != null && utc.isGarminEnabled());
            model.addAttribute("user", userDto);
            userDto.setName(user.getName());
            userDto.setSharing(user.getSharing());
            userDto.setEmail(user.getEmail());
            model.addAttribute("isGarminConnected", utc != null && utc.isGarminEnabled());
            model.addAttribute("user", userDto);
            return "profile";
        }


        try {
            user.setSharing(userDto.getSharing());
            user.setName(userDto.getName());
            usersRepository.save(user);
        } catch (RuntimeException ex) {
            logger.error("Registration Error", ex);
            throw new ExceptionHandling.UnnamedException("Something went wrong :(", "Could not update user profile, please try again later or send an email to contact@cubetrek.com");
        }

        return "redirect:/profile";
    }

    @GetMapping("/upload")
    public String showUploadForm(WebRequest request, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        model.addAttribute("user", user);

        return "upload";
    }

    @ResponseBody
    @PostMapping(value = "/upload", produces = "application/json")
    public UploadResponse uploadFile(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        return storageService.store(user, file);
    }

    @Transactional
    @ResponseBody
    @PostMapping(value = "/upload_anonymous", produces = "application/json")
    public UploadResponse uploadFileAnonymously(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes, Model model) {
        Users user = usersRepository.getReferenceById(1L); //anonymous user
        return storageService.store(user, file);
    }

    @GetMapping(value="/view/{itemid}")
    public String viewTrack(@PathVariable("itemid") long trackid, Model model)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return trackViewerService.mapView3D(authentication, trackid, model);
    }

    @GetMapping(value="/view2d/{itemid}")
    public String viewTrack2D(@PathVariable("itemid") long trackid, Model model)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return trackViewerService.mapView2D(authentication, trackid, model);
    }

    @GetMapping(value="/download/{itemid}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public @ResponseBody byte[] downloadTrackfile(@PathVariable("itemid") long trackid, HttpServletResponse response)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return trackViewerService.downloadTrackfile(authentication, trackid, response);
    }

    @GetMapping(value="/matching/{groupid}")
    public String viewMatchingActivities(@PathVariable("groupid") long groupid, Model model)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        model.addAttribute("groupidstring", Long.toString(groupid));
        try {
            model.addAttribute("matches", (new ObjectMapper().writeValueAsString(activitityService.getMatchingActivities(user, groupid))));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return "matched_activities";
    }

    @ResponseBody
    @GetMapping(value = "/api/geojson/{itemid}.geojson", produces = "application/json")
    public TrackGeojson getTrackGeoJson(@PathVariable("itemid") long trackid, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        response.addHeader("Cache-Control", "max-age=86400, public");
        return trackViewerService.getTrackGeojson(authentication, trackid);
    }

    @ResponseBody
    @GetMapping(value = "/api/slim_geojson/{itemid}.geojson", produces = "application/json")
    public SlimTrackGeojson getSimplifiedTrackGeoJson(@PathVariable("itemid") long trackid, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        response.addHeader("Cache-Control", "max-age=86400, public");
        return trackViewerService.getSlimTrackGeojson(authentication, trackid, 100);
    }

    @ResponseBody
    @GetMapping(value = "/api/gltf/{itemid}.gltf", produces = "text/plain")
    public String getGLTF(@PathVariable("itemid") long trackid, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        response.addHeader("Cache-Control", "max-age=86400, public");
        return trackViewerService.getGLTF(authentication, trackid);
    }

    /**
    @ResponseBody
    @RequestMapping(value = "/api/gltf/map/{zoom}/{x}/{y}.png", produces = "image/png")
    public HttpEntity<byte[]> getGLTF(@PathVariable("zoom") int zoom, @PathVariable("x") int x, @PathVariable("y") int y, Model model) {

        byte[] image = null;
        try {
            //URL url = new URL(String.format("https://api.maptiler.com/maps/basic/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x,y)); //Sun Locator style map (no shading)
            URL url = new URL(String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x,y)); //Swiss Topo style map (shading)
            InputStream in = new BufferedInputStream(url.openStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n = 0;
            while (-1 != (n = in.read(buf))) {
                out.write(buf, 0, n);
            }
            out.close();
            in.close();
            image = out.toByteArray();
        } catch (IOException ioException) {

        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(image.length);

        return new HttpEntity<byte[]>(image, headers);

    }

    **/


    @RequestMapping(value = "/api/gltf/map/{type}/{zoom}/{x}/{y}.png", produces = "image/png")
    public void getGLTF(@PathVariable("type") String type, @PathVariable("zoom") int zoom, @PathVariable("x") int x, @PathVariable("y") int y, HttpServletResponse response) {
        //LatLonBoundingBox CHBox = new LatLonBoundingBox(47.9163, 45.6755, 5.7349, 10.6677);
        String mapaccession = switch (type) {
            case "winter" ->
                    String.format("https://api.maptiler.com/maps/winter/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x, y);
            case "satellite" ->
                    String.format("https://api.maptiler.com/tiles/satellite-v2/%d/%d/%d.jpg?key=Nq5vDCKAnSrurDLNgtSI", zoom, x, y);
            case "satellite_ch" ->
                    String.format("https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.swissimage/default/current/3857/%d/%d/%d.jpeg", zoom, x, y);
            case "standard" -> {
                //LatLon bla = MapTile.convertPixelXYtoLatLong(new MapTile.XY(x,y),zoom);
                //System.out.println(bla.toString());
                //if (CHBox.isPositionWithinBbox(MapTile.convertPixelXYtoLatLong(new MapTile.XY(x,y),zoom))) {//within CH bbox
                //    System.out.println("@@ within CH");
                    yield String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x, y);
                //}else {
                //    System.out.println("@@ outside CH");
                //    yield String.format("https://api.maptiler.com/maps/dae70481-0d42-4345-867d-216c14f6ead8/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x, y);
                //}
            }
            default ->
                    String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x, y);
        };

        response.setHeader("Location", mapaccession);
        response.addHeader("Cache-Control", "max-age=864000, public");
        response.setStatus(302);
    }

    @RequestMapping(value = "/api/static_map/{id}/{width}x{height}.png", produces = "image/png")
    public void getGLTF(@PathVariable("id") Long id, @PathVariable("width") Long width, @PathVariable("height") Long height, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String pathenc = trackViewerService.getEncodedPolyline(authentication, id, 50);
        final String color = "rgba(255,128,1,1)";
        String maptilerUrl = String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/static/auto/%dx%d.png?key=Nq5vDCKAnSrurDLNgtSI&attribution=false&scale=@2x&path=stroke:%s|width:3|fill:none|enc:%s",width, height, color, pathenc);
        response.setHeader("Location", maptilerUrl);
        response.addHeader("Cache-Control", "max-age=864000, public");
        response.setStatus(302);
    }

    @Cacheable("peaks")
    @ResponseBody
    @GetMapping(value = "/api/peaks/nbound={nbound}&sbound={sbound}&wbound={wbound}&ebound={ebound}", produces = "application/json")
    //@JsonSerialize(using = OsmPeaks.OsmPeaksListSerializer.class)
    public GeographyService.OsmPeakList getPeaksWithinBBox(@PathVariable("nbound") double nbound, @PathVariable("sbound") double sbound, @PathVariable("wbound") double wbound, @PathVariable("ebound") double ebound, HttpServletResponse response) {
        LatLonBoundingBox bbox = new LatLonBoundingBox(nbound, sbound, wbound, ebound);
        response.addHeader("Cache-Control", "max-age=864000, public");
        return geographyService.findPeaksWithinBBox(bbox);
    }

    @ResponseBody
    @RequestMapping(value="/api/modify")
    public UpdateTrackmetadataResponse updateTrackmetadata(@RequestBody EditTrackmetadataDto editTrackmetadataDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return storageService.editTrackmetadata(authentication, editTrackmetadataDto);
    }

    @Transactional
    @ResponseBody
    @GetMapping(value="/api/modify/id={id}&favorite={favorite}")
    public UpdateTrackmetadataResponse updateTrackFavorite(@PathVariable("id") long id, @PathVariable("favorite") boolean favorite) {
        isWriteAccessAllowed(SecurityContextHolder.getContext().getAuthentication(), id);
        trackDataRepository.updateTrackFavorite(id, favorite);
        if (favorite)
            trackDataRepository.updateTrackHidden(id, false);
        return new UpdateTrackmetadataResponse(true);
    }

    @Transactional
    @ResponseBody
    @GetMapping(value="/api/modify/id={id}&hidden={hidden}")
    public UpdateTrackmetadataResponse updateTrackHidden(@PathVariable("id") long id, @PathVariable("hidden") boolean hidden) {
        isWriteAccessAllowed(SecurityContextHolder.getContext().getAuthentication(), id);
        trackDataRepository.updateTrackHidden(id, hidden);
        if (hidden) //if hidden, it can't be favorited
            trackDataRepository.updateTrackFavorite(id, false);
        return new UpdateTrackmetadataResponse(true);
    }

    @Transactional
    @ResponseBody
    @GetMapping(value="/api/modify/id={id}&sharing={sharing}")
    public UpdateTrackmetadataResponse modifySharing(@PathVariable("id") long id, @PathVariable("sharing") TrackData.Sharing sharing) {
        isWriteAccessAllowed(SecurityContextHolder.getContext().getAuthentication(), id);
        trackDataRepository.updateTrackSharing(id, sharing);
        return new UpdateTrackmetadataResponse(true);
    }

    @Transactional
    @ResponseBody
    @DeleteMapping(value="/api/modify/id={id}")
    public UpdateTrackmetadataResponse deleteTrack(@PathVariable("id") long id) {
        isWriteAccessAllowed(SecurityContextHolder.getContext().getAuthentication(), id);
        trackDataRepository.deleteById(id);
        logger.info("Delete ID '"+id+"'");
        return new UpdateTrackmetadataResponse(true);
    }

    @ResponseBody
    @RequestMapping(value="/api/modify/matching/")
    public UpdateTrackmetadataResponse batchRenameMatchingactivities(@RequestBody EditTrackmetadataDto editTrackmetadataDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return storageService.batchRenameMatchingActivities(authentication, editTrackmetadataDto);
    }

    @ResponseBody
    @GetMapping(value = "/newslettersignup/{email}")
    public UpdateTrackmetadataResponse saveEmail(@PathVariable("email") String email) {
        return newsletterService.store(email);
    }

    private void isWriteAccessAllowed(Authentication authentication, long trackId) {
        boolean writeAccess;
        if (authentication instanceof AnonymousAuthenticationToken) //not logged in
            writeAccess = false;
        else {
            Users user = (Users) authentication.getPrincipal();
            long ownerid = trackDataRepository.getOwnerId(trackId);
            writeAccess = ownerid == user.getId();
        }
        if (!writeAccess)
            throw new ExceptionHandling.TrackViewerException(TrackViewerService.noAccessMessage);
    }

}