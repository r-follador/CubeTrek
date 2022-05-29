package com.cubetrek;

import com.cubetrek.database.*;
import com.cubetrek.registration.UserDto;
import com.cubetrek.upload.*;
import com.cubetrek.registration.UserRegistrationService;
import com.cubetrek.viewer.TrackGeojson;
import com.cubetrek.viewer.TrackViewerService;
import com.sunlocator.topolibrary.LatLonBoundingBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@Controller
public class MainController {
    @Autowired
    private UserRegistrationService userRegistrationService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private GeographyService geographyService;


    @Autowired
    private TrackViewerService trackViewerService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private TrackMetadataRepository trackMetadataRepository;

    @Autowired
    private OsmPeaksRepository osmPeaksRepository;


    @GetMapping("/registration")
    public String showRegistrationForm(WebRequest request, Model model) {
        System.out.println("get registration");
        UserDto userDto = new UserDto();
        model.addAttribute("user", userDto);
        return "registration";
    }

    @PostMapping("/registration")
    public ModelAndView registerUserAccount(
            @ModelAttribute("user") UserDto userDto,
            HttpServletRequest request, Errors errors) {
        Users registered = userRegistrationService.register(userDto);

        return new ModelAndView("successRegister", "user", userDto);
    }

    @GetMapping("/successRegister")
    public String successRegister() {
        return "successRegister";
    }


    @GetMapping("/index")
    public String index() {
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
        model.addAttribute("tracks", trackMetadataRepository.findByOwner(user));

        return "dashboard";
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

    @GetMapping(value="/view/{itemid}")
    public String viewTrack(@PathVariable("itemid") long trackid, Model model)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        return trackViewerService.mapView3D(user, trackid, model);
    }

    @GetMapping(value="/view_2d/{itemid}")
    public String viewTrack_2d(@PathVariable("itemid") long trackid, Model model)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        return trackViewerService.mapView2D(user, trackid, model);
    }

    @ResponseBody
    @GetMapping(value = "/api/simplifiedtrack/{itemid}.geojson", produces = "application/json")
    public TrackGeojson getSimplifiedTrackGeoJson(@PathVariable("itemid") long trackid, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();

        return trackViewerService.getTrackGeojson(user, trackid);
    }

    @ResponseBody
    @GetMapping(value = "/api/gltf/{itemid}.gltf", produces = "text/plain")
    public String getGLTF(@PathVariable("itemid") long trackid, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        return trackViewerService.getGLTF(user, trackid);
    }

    /**
    @ResponseBody
    @RequestMapping(value = "/api/gltf/map/{zoom}/{x}/{y}.png", produces = "image/png")
    public HttpEntity<byte[]> getGLTF(@PathVariable("zoom") int zoom, @PathVariable("x") int x, @PathVariable("y") int y, Model model) {

        byte[] image = null;
        try {
            //URL url = new URL(String.format("https://api.maptiler.com/maps/basic/%d/%d/%d.png?key=j2l5mrAxnWdu6xX99JQp", zoom, x,y)); //Sun Locator style map (no shading)
            URL url = new URL(String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/%d/%d/%d.png?key=j2l5mrAxnWdu6xX99JQp", zoom, x,y)); //Swiss Topo style map (shading)
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
        String mapaccession = switch (type) {
            case "winter" ->
                    String.format("https://api.maptiler.com/maps/winter/%d/%d/%d.png?key=j2l5mrAxnWdu6xX99JQp", zoom, x, y);
            case "satellite" ->
                    String.format("https://api.maptiler.com/tiles/satellite-v2/%d/%d/%d.jpg?key=j2l5mrAxnWdu6xX99JQp", zoom, x, y);
            case "standard" ->
                    String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/%d/%d/%d.png?key=j2l5mrAxnWdu6xX99JQp", zoom, x, y);
            default ->
                    String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/%d/%d/%d.png?key=j2l5mrAxnWdu6xX99JQp", zoom, x, y);
        };

        response.setHeader("Location", mapaccession);

        //response.setHeader("Location", String.format("https://api.maptiler.com/tiles/satellite-v2/%d/%d/%d.jpg?key=j2l5mrAxnWdu6xX99JQp", zoom, x,y));

        response.setStatus(302);
    }

    @ResponseBody
    @GetMapping(value = "/api/peaks/nbound={nbound}&sbound={sbound}&wbound={wbound}&ebound={ebound}", produces = "application/json")
    //@JsonSerialize(using = OsmPeaks.OsmPeaksListSerializer.class)
    public GeographyService.OsmPeakList getPeaksWithinBBox(@PathVariable("nbound") double nbound, @PathVariable("sbound") double sbound, @PathVariable("wbound") double wbound, @PathVariable("ebound") double ebound) {
        LatLonBoundingBox bbox = new LatLonBoundingBox(nbound, sbound, wbound, ebound);
        return geographyService.findPeaksWithinBBox(bbox);
    }

    @ResponseBody
    @PostMapping(value="/api/modify")
    public EditTrackmetadataResponse editTrackmetadata(EditTrackmetadataDto editTrackmetadataDto) {
        if (!editTrackmetadataDto.check())
            throw new ExceptionHandling.EditTrackmetadataException(editTrackmetadataDto.getErrorMessage());

        return new EditTrackmetadataResponse(true);

    }


}