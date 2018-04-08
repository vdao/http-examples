package hello;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
public class MyController {

    private static final Random RANDOM = new Random();

    @RequestMapping("/ping")
    public String ping() {
        return "pong";
    }

    @RequestMapping("/realWorldPing")
    public String realWorldPing(@RequestParam(value = "maxConcurrency", defaultValue = "5") Integer maxConcurrency,
                                @RequestParam(value = "mLatency", defaultValue = "500") Double mLatency,
                                @RequestParam(value = "sigmaLatency", defaultValue = "300") Double sigmaLatency,
                                @RequestParam(value = "failProb", defaultValue = "0.0") Double failProb,
                                @RequestParam(value = "maxWait", defaultValue = "2000") Long maxWaitMillis) {

        if (RANDOM.nextDouble() < failProb) {
            throw new RuntimeException("Oops!");
        }

        double lattency = RANDOM.nextGaussian() * sigmaLatency + mLatency;

        return LimitedResource.instance(maxConcurrency).get(maxWaitMillis, Math.round(lattency), TimeUnit.MILLISECONDS);
    }
}